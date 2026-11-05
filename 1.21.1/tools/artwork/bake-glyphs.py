"""Export the canonical Inkscape SVG. Requires Pillow and fontTools 4.60.1."""
from pathlib import Path
import argparse, copy, hashlib, json, math, os, re, shutil, subprocess, tempfile
import xml.etree.ElementTree as ET
from fontTools.misc.transform import Identity, Transform
from fontTools.pens.basePen import BasePen
from fontTools.pens.transformPen import TransformPen
from fontTools.svgLib.path import SVGPath
from PIL import Image

PROJECT = Path(__file__).resolve().parents[2]
RES = PROJECT / "src/main/resources/assets/elementalcircuits"
SOURCE = PROJECT / "art/inkscape/runes.svg"
SVG = "http://www.w3.org/2000/svg"
ET.register_namespace("", SVG)


def inkscape_binary(explicit):
    candidates = [explicit, os.environ.get("INKSCAPE_BINARY"),
                  shutil.which("inkscape.com"), shutil.which("inkscape")]
    for base in (os.environ.get("ProgramFiles"), os.environ.get("ProgramFiles(x86)"),
                 "D:/Inkscape", "E:/Inkscape"):
        if base:
            candidates += [str(Path(base) / "Inkscape/bin/inkscape.com"),
                           str(Path(base) / "bin/inkscape.com")]
    for candidate in candidates:
        if candidate and Path(candidate).is_file():
            return str(Path(candidate).resolve())
    raise SystemExit("找不到本地 Inkscape；请使用 --inkscape 指定 inkscape.com 或 inkscape.exe。")


def svg_transform(text):
    result = Identity
    for match in re.finditer(r"([A-Za-z]+)\s*\(([^)]*)\)", text):
        name, raw = match.groups()
        v = [float(n) for n in re.findall(r"[-+]?(?:\d*\.\d+|\d+\.?\d*)(?:[eE][-+]?\d+)?", raw)]
        if name == "matrix" and len(v) == 6:
            step = Transform(*v)
        elif name == "translate" and 1 <= len(v) <= 2:
            step = Identity.translate(v[0], v[1] if len(v) == 2 else 0)
        elif name == "scale" and 1 <= len(v) <= 2:
            step = Identity.scale(v[0], v[-1])
        elif name == "rotate" and len(v) in (1, 3):
            step = Identity
            if len(v) == 3:
                step = step.translate(v[1], v[2])
            step = step.rotate(math.radians(v[0]))
            if len(v) == 3:
                step = step.translate(-v[1], -v[2])
        else:
            raise ValueError("不支持的SVG变换，请在Inkscape中应用变换：" + text)
        result = result.transform(step)
    return result


class OutlinePen(BasePen):
    """Geometry contracts only. Inkscape renders the actual curves, never this polyline."""
    def __init__(self):
        super().__init__(None)
        self.strokes = []
        self.start = self.current = None

    def _moveTo(self, point):
        self.start = self.current = point

    def _lineTo(self, point):
        if self.current != point:
            self.strokes.append([round(v, 8) for v in (*self.current, *point)])
        self.current = point

    def _curveToOne(self, b, c, d):
        a = self.current
        for i in range(1, 9):
            t = i / 8
            self._lineTo(tuple((1-t)**3*a[k] + 3*(1-t)**2*t*b[k]
                               + 3*(1-t)*t*t*c[k] + t**3*d[k] for k in (0, 1)))

    def _closePath(self):
        self._lineTo(self.start)

    def _endPath(self):
        pass


def geometry(group):
    pen = OutlinePen()
    def walk(node, transform):
        if node.get("transform"):
            transform = transform.transform(svg_transform(node.get("transform")))
        if node.tag.rsplit("}", 1)[-1] in {"path", "ellipse", "circle", "rect", "line", "polyline", "polygon"}:
            node = copy.deepcopy(node)
            node.attrib.pop("transform", None)
            SVGPath.fromstring(ET.tostring(node)).draw(TransformPen(pen, transform))
        else:
            for child in node:
                walk(child, transform)
    for child in group:
        walk(child, Identity)
    if not pen.strokes or len(pen.strokes) > 256:
        raise ValueError("符形轮廓数量非法：" + group.get("data-ec-key"))
    if any(not math.isfinite(v) or abs(v) > 1 for row in pen.strokes for v in row):
        raise ValueError("符形超出标准边界：" + group.get("data-ec-key"))
    return pen.strokes


def cursor_source(groups, columns, rows):
    root = ET.Element(f"{{{SVG}}}svg", {"width": str(columns*24), "height": str(rows*24),
                          "viewBox": f"0 0 {columns*24} {rows*24}"})
    for index, source in enumerate(groups):
        key = source.get("data-ec-key")
        cell = ET.SubElement(root, f"{{{SVG}}}g", {
            "transform": f"translate({index%columns*24} {index//columns*24})"})
        for color, width in (("#081018", ".28"), ("#ffffff", ".11")):
            group = copy.deepcopy(source)
            group.set("transform", "translate(12 12) scale(9)")
            group.set("stroke", color)
            group.set("stroke-width", width)
            for child in group.iter():
                child.attrib.pop("id", None)
                child.attrib.pop("style", None)
                if "stroke" in child.attrib:
                    child.set("stroke", color)
                if "stroke-width" in child.attrib:
                    child.set("stroke-width", width)
                if child.get("fill", "none") != "none":
                    child.set("fill", color)
            cell.append(group)
        if key != "action/SELECT":
            hx, hy = (4, 20) if key == "action/ROUTE" else (12, 12)
            ET.SubElement(cell, f"{{{SVG}}}circle", {"cx": str(hx+.5), "cy": str(hy+.5),
                "r": "1.7", "fill": "#081018"})
            ET.SubElement(cell, f"{{{SVG}}}rect", {"x": str(hx), "y": str(hy),
                "width": "1", "height": "1", "fill": "#ffffff"})
    return root


def check_png(path, cell, columns, count):
    with Image.open(path) as image:
        rows = math.ceil(count / columns)
        if image.size != (cell*columns, cell*rows):
            raise ValueError("导出尺寸错误：" + str(path))
        alpha = image.convert("RGBA").getchannel("A")
        for index in range(count):
            x, y = index % columns*cell, index // columns*cell
            bounds = alpha.crop((x, y, x+cell, y+cell)).getbbox()
            if not bounds or min(bounds[:2]) < 1 or max(bounds[2:]) >= cell:
                raise ValueError(f"符形为空或越出单元边界：{index} {path}")
        if not any(0 < value < 255 for value in alpha.getdata()):
            raise ValueError("缺少矢量抗锯齿边缘")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--inkscape", help="本地 inkscape.com / inkscape.exe 完整路径")
    args = parser.parse_args()
    binary = inkscape_binary(args.inkscape)
    version = subprocess.run([binary, "--version"], capture_output=True, text=True,
                             encoding="utf-8", errors="replace", check=True).stdout.strip()
    source = ET.parse(SOURCE).getroot()
    if source.findall(f".//{{{SVG}}}image"):
        raise ValueError("SVG不得嵌入位图")
    groups = sorted([g for g in source if g.get("data-ec-key")],
                    key=lambda g: int(g.get("data-ec-index")))
    if [int(g.get("data-ec-index")) for g in groups] != list(range(len(groups))):
        raise ValueError("符形索引必须连续且稳定")
    keys = [g.get("data-ec-key") for g in groups]
    if len(set(keys)) != len(keys) or any(not g.get("id") for g in groups):
        raise ValueError("符形标识缺失或重复")
    columns = int(source.get("data-ec-columns"))
    rows = math.ceil(len(groups) / columns)
    metadata = {"version": 2, "source": "art/inkscape/runes.svg",
                "sourceSha256": hashlib.sha256(SOURCE.read_bytes()).hexdigest(),
                "shapes": {g.get("data-ec-key"): geometry(g) for g in groups},
                "cell": 128, "columns": columns, "atlasWidth": columns*128,
                "atlasHeight": rows*128, "padding": 12}
    build = PROJECT / "build"
    build.mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="glyph-export-", dir=build) as tmp:
        tmp = Path(tmp)
        def export(svg, dest, cell):
            subprocess.run([binary, str(svg), "--export-area-page", "--export-type=png",
                            "--export-background-opacity=0", f"--export-width={cell*columns}",
                            f"--export-height={cell*rows}", f"--export-filename={dest}"],
                           check=True, capture_output=True)
            check_png(dest, cell, columns, len(groups))
        export(SOURCE, tmp / "glyphs.png", 128)
        cursor = tmp / "cursors.svg"
        ET.ElementTree(cursor_source(groups, columns, rows)).write(cursor, encoding="utf-8", xml_declaration=True)
        for scale in (1, 2, 3):
            export(cursor, tmp / f"glyph-cursors-{scale}.png", 24*scale)
        # Publish after all exports and geometry checks succeed.
        for name in ("glyphs.png", *(f"glyph-cursors-{s}.png" for s in (1, 2, 3))):
            dest = RES / "textures/gui" / name
            shutil.copyfile(tmp / name, dest)
            dest.with_suffix(".png.mcmeta").write_text(
                '{"texture":{"blur":true,"clamp":true}}\n', encoding="utf-8")
        (RES / "art/glyphs.json").write_text(json.dumps(metadata, ensure_ascii=False, indent=2)+"\n", encoding="utf-8")
    print(f"{version}: {len(groups)} SVG glyphs -> 1024x{rows*128} atlas + 24/48/72px cursors")


if __name__ == "__main__":
    main()
