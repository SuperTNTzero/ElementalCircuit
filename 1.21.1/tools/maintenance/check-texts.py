"""Check language-resource parity and prevent Java UI text from being hardcoded."""
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
LANG = ROOT / 'src/main/resources/assets/elementalcircuits/lang'
JAVA = ROOT / 'src/main/java'
HAN = re.compile(r'[\u3400-\u9fff]')
LOOKUP = re.compile(
    r'(?:Texts\.get|UiText\.(?:string|component)|Component\.translatable)\(\s*"([^"]+)"'
)


def untranslated_paths(value, path='$'):
    if isinstance(value, dict):
        return [entry for key, child in value.items() for entry in untranslated_paths(child, f'{path}.{key}')]
    if isinstance(value, list):
        return [entry for index, child in enumerate(value) for entry in untranslated_paths(child, f'{path}[{index}]')]
    return [path] if isinstance(value, str) and HAN.search(value) else []


def main():
    zh = json.loads((LANG / 'zh_cn.json').read_text(encoding='utf-8'))
    en = json.loads((LANG / 'en_us.json').read_text(encoding='utf-8'))
    failed = False
    for locale, other in (("en_us", en), ("zh_cn", zh)):
        missing = set(zh) - set(other)
        if missing:
            print(f"{locale}: missing {len(missing)} keys: {sorted(missing)[:8]}")
            failed = True

    assets = ROOT / 'src/main/resources/assets/elementalcircuits'
    for path in assets.rglob('en_us.json'):
        resource = json.loads(path.read_text(encoding='utf-8'))
        untranslated = untranslated_paths(resource)
        if untranslated:
            print(f"{path.relative_to(ROOT)} contains {len(untranslated)} untranslated Chinese values: {untranslated[:12]}")
            failed = True

    refs = set()
    literals = []
    for path in JAVA.rglob('*.java'):
        source = path.read_text(encoding='utf-8')
        refs.update(key for key in LOOKUP.findall(source) if not key.endswith('.'))
        if HAN.search(source):
            literals.append(str(path.relative_to(ROOT)))
    missing_refs = refs - set(zh)
    if missing_refs:
        print(f"Missing statically referenced keys: {sorted(missing_refs)[:12]}")
        failed = True
    if literals:
        print("Chinese text remains in production Java:")
        print("\n".join(literals[:20]))
        failed = True

    rune_source = (JAVA / 'dev/elementalcircuits/domain/circuit/Rune.java').read_text(encoding='utf-8')
    rune_kinds = re.findall(r'^\s{8}([A-Z][A-Z_]+)\("', rune_source, re.MULTILINE)
    missing_runes = [
        f"rune.elementalcircuits.{kind.lower()}.{field}"
        for kind in rune_kinds
        for field in ("title", "description")
        if f"rune.elementalcircuits.{kind.lower()}.{field}" not in zh
    ]
    if missing_runes:
        print(f"Missing rune text keys: {missing_runes[:12]}")
        failed = True
    if failed:
        return 1
    print(f"Text resources valid: {len(zh)} keys per locale, {len(refs)} static references")
    return 0


if __name__ == '__main__':
    sys.exit(main())
