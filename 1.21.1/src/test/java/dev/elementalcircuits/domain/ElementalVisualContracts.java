package dev.elementalcircuits.domain;

import com.google.gson.*;

import dev.elementalcircuits.adapter.resources.ElementalComboResources;
import dev.elementalcircuits.application.observation.*;
import dev.elementalcircuits.client.effect.*;
import dev.elementalcircuits.domain.casting.ProjectileEffects;
import dev.elementalcircuits.domain.casting.SpellSpec;
import dev.elementalcircuits.domain.combat.Element;
import dev.elementalcircuits.domain.combat.ElementalCombos;
import dev.elementalcircuits.domain.mana.Mixture;
import dev.elementalcircuits.domain.mana.Packet;

import java.security.MessageDigest;
import java.util.*;

import javax.imageio.ImageIO;

/** Material geometry, animation continuity, provenance and paid chain budgets. */
public final class ElementalVisualContracts {
    private static int checks;

    private static void check(boolean value, String why) {
        checks++;
        if (!value) throw new AssertionError(why);
    }

    private static Packet packet(Mixture mana, ProjectileEffects effects) {
        return new Packet(
                mana,
                new SpellSpec(
                        SpellSpec.Method.DIRECTED, SpellSpec.Shape.BEAM, 0, 0, 0, 0, effects));
    }

    private static Mixture mana(Element e, long n) {
        return Mixture.pure(n).convert(e, Fixed.ONE);
    }

    private static SpellVisual event(SpellVisual.Kind kind, Element element) {
        return new SpellVisual(
                kind,
                element,
                new SpellVisual.Point(0, 0, -8),
                kind == SpellVisual.Kind.BEAM || kind == SpellVisual.Kind.TRAIL
                        ? new SpellVisual.Point(0, 0, -13)
                        : new SpellVisual.Point(0, 0, 1),
                kind == SpellVisual.Kind.BEAM || kind == SpellVisual.Kind.TRAIL ? 1 : 2,
                6);
    }

    private static List<Object> mesh(SpellVisual e, int age) {
        var out = new ArrayList<Object>();
        SpellGeometry.emit(
                List.of(new VisualTimeline.Frame(e, 0, 12, age)),
                new SpellGeometry.V(0, 0, 0),
                new SpellGeometry.V(1, 0, 0),
                new SpellGeometry.V(0, 1, 0),
                .25f,
                (a, b, c, d, tile, color, alpha) -> {
                    check(
                            tile >= 0
                                    && tile < 64
                                    && alpha > 0
                                    && alpha <= 255
                                    && Double.isFinite(
                                            a.length() + b.length() + c.length() + d.length()),
                            "组合几何必须有限且使用有效图集");
                    out.add(
                            List.of(
                                    a, b, c, d,
                                    tile)); // Deliberately exclude tint: color alone cannot satisfy
                    // differences.
                });
        return out;
    }

    public static int run() {
        var rules = ElementalComboResources.rules();
        var lightning = packet(mana(Element.LIGHTNING, 8 * Fixed.ONE), ProjectileEffects.NONE);
        check(
                ElementalCombos.lightningBeam(lightning)
                        && ElementalCombos.jumps(lightning, rules) == 2,
                "纯正雷束原生两次跳转");
        check(
                !ElementalCombos.lightningBeam(
                        packet(mana(Element.FIRE, Fixed.ONE), ProjectileEffects.NONE)),
                "其他元素不能获得免费雷链");
        long[] inverse = new long[17];
        inverse[Element.LIGHTNING.component(true)] = Fixed.ONE;
        check(
                !ElementalCombos.lightningBeam(
                        packet(new Mixture(inverse), ProjectileEffects.NONE)),
                "反元素不猜测新的连锁规则");
        check(
                !ElementalCombos.lightningBeam(
                        packet(
                                mana(Element.LIGHTNING, Fixed.ONE)
                                        .plus(mana(Element.FIRE, Fixed.ONE)),
                                ProjectileEffects.NONE)),
                "混合运输保留原逐成分语义");
        check(
                !ElementalCombos.lightningBeam(
                                packet(
                                        lightning.mana(),
                                        new ProjectileEffects(0, 0, 0, 2, 3, 0, 0, 0)))
                        && !ElementalCombos.lightningBeam(
                                packet(lightning.mana(), new ProjectileEffects(2, 0, 0))),
                "显式分裂或爆破保留独立派生管线");
        check(
                ElementalCombos.jumps(
                                packet(
                                        lightning.mana(),
                                        new ProjectileEffects(0, 0, 0, 0, 4, 0, 0, 0)),
                                rules)
                        == 4,
                "连锁符文覆盖原生跳数");
        for (long amount = 1; amount <= 10000; amount += 37) {
            Mixture remaining = mana(Element.LIGHTNING, amount);
            long paid = 0;
            for (int hop = 0; hop < 4; hop++) {
                var share = ElementalCombos.share(remaining, true, rules);
                paid += share.contact().total();
                check(
                        share.contact().total() + share.onward().total() <= remaining.total(),
                        "分配与损耗不创造魔力");
                remaining = share.onward();
            }
            var last = ElementalCombos.share(remaining, false, rules);
            paid += last.contact().total();
            check(paid <= amount && last.onward().empty(), "有限链总接触预算不超输入，末跳结清");
        }
        var kinds =
                List.of(
                        SpellVisual.Kind.BEAM,
                        SpellVisual.Kind.TRAIL,
                        SpellVisual.Kind.SHIELD,
                        SpellVisual.Kind.FAN,
                        SpellVisual.Kind.WALL,
                        SpellVisual.Kind.RING,
                        SpellVisual.Kind.COLUMN,
                        SpellVisual.Kind.CROSS,
                        SpellVisual.Kind.DOME);
        for (var kind : kinds) {
            var hashes = new HashSet<Integer>();
            for (var e : Element.values())
                if (e != Element.PURE) {
                    var v = event(kind, e);
                    var a = mesh(v, 1);
                    check(!a.isEmpty() && a.equals(mesh(v, 1)), "元素形状纯几何可确定重建");
                    check(!a.equals(mesh(v, 5)), "各组合具备动画而非静态换色：" + e + "/" + kind);
                    hashes.add(a.hashCode());
                }
            check(hashes.size() == 8, "同一形状八元素均有非颜色差异：" + kind);
        }
        var timeline = new VisualTimeline();
        var shield = event(SpellVisual.Kind.SHIELD, Element.WATER);
        for (int tick = 0; tick < 120; tick++) {
            timeline.add(List.of(shield));
            timeline.tick();
        }
        check(
                timeline.frames().size() == 1
                        && timeline.frames().get(0).age() == 1
                        && timeline.frames().get(0).animationAge() == 120,
                "刷新保持单实例且不重置连续动画");
        var initialAlpha = new ArrayList<Integer>();
        var refreshedAlpha = new ArrayList<Integer>();
        for (int age = 0; age < 2; age++) {
            var destination = age == 0 ? initialAlpha : refreshedAlpha;
            SpellGeometry.emit(
                    List.of(new VisualTimeline.Frame(shield, age, 2, 0)),
                    new SpellGeometry.V(0, 0, 0),
                    new SpellGeometry.V(1, 0, 0),
                    new SpellGeometry.V(0, 1, 0),
                    .8f,
                    (a, b, c, d, tile, color, alpha) -> destination.add(alpha));
        }
        check(
                initialAlpha.equals(refreshedAlpha)
                        && initialAlpha.stream().mapToInt(Integer::intValue).max().orElse(0) > 150,
                "活跃屏障保持稳定可见，不随两步刷新寿命闪烁或变暗");
        var frozen = timeline.frames();
        check(timeline.frames().equals(frozen), "不提交逻辑步时动画不自行推进");
        timeline.tick();
        check(timeline.frames().isEmpty(), "刷新停止后屏障按原有限寿命过期");
        timeline.add(List.of(shield));
        check(timeline.frames().get(0).animationAge() == 0, "新事件重建独立动画");
        timeline.clear();
        check(timeline.frames().isEmpty(), "退出清理所有动画状态");
        assets();
        return checks;
    }

    private static void assets() {
        try {
            String path = "/assets/elementalcircuits/";
            byte[] png =
                    ElementalVisualContracts.class
                            .getResourceAsStream(path + "textures/effect/elemental_spells.png")
                            .readAllBytes();
            var image = ImageIO.read(new java.io.ByteArrayInputStream(png));
            check(image.getWidth() == 1024 && image.getHeight() == 1024, "64格单一图集");
            for (int tile = 0; tile < 64; tile++) {
                int visible = 0;
                for (int y = 8; y < 120; y++)
                    for (int x = 8; x < 120; x++)
                        if (image.getRGB(tile % 8 * 128 + x, tile / 8 * 128 + y) >>> 24 > 32)
                            visible++;
                check(visible > 30, "每格有可见内容：" + tile);
            }
            var root =
                    JsonParser.parseString(
                                    new String(
                                            ElementalVisualContracts.class
                                                    .getResourceAsStream(
                                                            path + "art/spell_sources.json")
                                                    .readAllBytes(),
                                            java.nio.charset.StandardCharsets.UTF_8))
                            .getAsJsonObject();
            check(
                    root.get("atlasSha256")
                            .getAsString()
                            .equals(
                                    HexFormat.of()
                                            .formatHex(
                                                    MessageDigest.getInstance("SHA-256")
                                                            .digest(png))),
                    "署名清单精确绑定发行图集");
            check(
                    root.getAsJsonArray("sources").size() == 6
                            && root.getAsJsonArray("recipes").size() == 64,
                    "六份来源和逐格改造记录完整");
            for (var source : root.getAsJsonArray("sources"))
                check(
                        source.getAsJsonObject().get("license").getAsString().equals("CC0-1.0"),
                        "只引入明确CC0资源");
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
