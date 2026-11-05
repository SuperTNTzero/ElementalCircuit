package dev.elementalcircuits.domain;

import dev.elementalcircuits.adapter.codec.JsonCodecs;
import dev.elementalcircuits.application.observation.*;
import dev.elementalcircuits.client.editor.*;
import dev.elementalcircuits.client.effect.*;
import dev.elementalcircuits.domain.circuit.CircuitBounds;
import dev.elementalcircuits.domain.circuit.Route;
import dev.elementalcircuits.domain.circuit.Rune;
import dev.elementalcircuits.domain.circuit.TickCircuit;
import dev.elementalcircuits.domain.combat.Element;
import dev.elementalcircuits.domain.mana.ManaPool;

import java.util.*;

import javax.imageio.ImageIO;

/** Asset coverage, bounded observations, lifetime and exact visual/authority separation. */
public final class VisualContracts {
    private static int checks;

    private static void check(boolean ok, String why) {
        checks++;
        if (!ok) throw new AssertionError(why);
    }

    private static void rejects(Runnable r, String why) {
        boolean rejected = false;
        try {
            r.run();
        } catch (RuntimeException e) {
            rejected = true;
        }
        check(rejected, why);
    }

    private static SpellVisual event(SpellVisual.Kind kind) {
        return new SpellVisual(
                kind,
                Element.FIRE,
                new SpellVisual.Point(0, 0, 0),
                new SpellVisual.Point(0, 0, 1),
                1,
                8);
    }

    public static int run() {
        try {
            var atlas =
                    ImageIO.read(
                            VisualContracts.class.getResourceAsStream(
                                    "/assets/elementalcircuits/textures/gui/glyphs.png"));
            check(
                    atlas.getWidth() == RuneShapes.WIDTH && atlas.getHeight() == RuneShapes.HEIGHT,
                    "图集与静态布局完全匹配");
            for (String key : RuneShapes.names()) {
                var glyph = RuneShapes.glyph(key);
                int x = glyph.index() % RuneShapes.COLUMNS * RuneShapes.CELL,
                        y = glyph.index() / RuneShapes.COLUMNS * RuneShapes.CELL,
                        ink = 0,
                        soft = 0,
                        edgeInk = 0;
                for (int r = 0; r < RuneShapes.CELL; r++)
                    for (int c = 0; c < RuneShapes.CELL; c++) {
                        int alpha = atlas.getRGB(x + c, y + r) >>> 24;
                        if (alpha > 0) ink++;
                        if (alpha > 0 && alpha < 255) soft++;
                        if (r == 0
                                || c == 0
                                || r == RuneShapes.CELL - 1
                                || c == RuneShapes.CELL - 1) edgeInk += alpha;
                    }
                check(edgeInk == 0, "图集单元边界透明，缩放不会采到相邻符形：" + key);
                check(ink > 4, "每个符形都有非空预烘焙像素：" + key);
                check(soft > 0, "矢量曲线及斜线包含抗锯齿：" + key);
                for (int scale = 1; scale <= 3; scale++) {
                    var glyphPointer =
                            key.equals("action/SELECT")
                                    ? new EditorPointer(
                                            EditorPointer.Kind.SELECT, null, null, false)
                                    : key.equals("action/ROUTE")
                                            ? new EditorPointer(
                                                    EditorPointer.Kind.ROUTE, null, null, false)
                                            : null;
                    if (glyphPointer != null) {
                        var mask = new PointerBitmap(glyphPointer, scale);
                        check(
                                Arrays.stream(mask.pixels()).anyMatch(p -> (p >>> 24) > 0),
                                "工具指针采用非空矢量导出资源");
                    }
                }
            }
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
        var line =
                new SpellVisual(
                        SpellVisual.Kind.BEAM,
                        Element.LIGHT,
                        new SpellVisual.Point(0, 0, 0),
                        new SpellVisual.Point(64, 0, 0),
                        1,
                        1);
        check(
                line.near(new SpellVisual.Point(65, 0, 0), 2)
                        && line.near(new SpellVisual.Point(-1, 0, 0), 2),
                "长光束按线段而非中点筛选观察者");
        check(!line.near(new SpellVisual.Point(20, 3, 0), 2), "线段筛选拒绝远处观察者");
        var q = new VisualBudget();
        for (int i = 0; i < VisualBudget.WORLD_LIMIT + 20; i++) q.add(line);
        check(q.snapshot().size() == VisualBudget.WORLD_LIMIT && q.dropped() == 20, "世界特效队列有硬上限");
        var batches = q.batches(new SpellVisual.Point(65, 0, 0));
        check(
                batches.size() == 4 && batches.stream().allMatch(b -> b.size() == 128),
                "按玩家预算分成有界批次");
        check(q.batches(new SpellVisual.Point(1000, 0, 0)).isEmpty(), "无观察者范围的特效不发送");
        q.clear();
        check(q.snapshot().isEmpty() && q.dropped() == 0, "世界边界清除一次性观察");
        rejects(() -> new SpellVisual.Point(Double.NaN, 0, 0), "拒绝非有限坐标");
        rejects(
                () ->
                        new SpellVisual(
                                SpellVisual.Kind.SHIELD,
                                Element.ARCANE,
                                line.from(),
                                line.from(),
                                1,
                                1),
                "屏障法线须单位长度");
        rejects(
                () ->
                        new SpellVisual(
                                SpellVisual.Kind.BEAM,
                                Element.FIRE,
                                line.from(),
                                new SpellVisual.Point(CircuitBounds.RANGE + 2, 0, 0),
                                1,
                                1),
                "拒绝超长特效段");
        rejects(() -> new VisualStyles.Style(16, 3, 1, 1, 1), "拒绝不存在的图集格");
        var timeline = new VisualTimeline();
        for (var kind : SpellVisual.Kind.values()) timeline.add(List.of(event(kind)));
        check(timeline.frames().size() == SpellVisual.Kind.values().length, "独立样式进入客户端时间线");
        var frozen = timeline.frames();
        check(timeline.frames() == frozen, "相同逻辑步的多次渲染复用观察列表");
        timeline.tick();
        check(timeline.frames() != frozen && frozen.getFirst().age() == 0, "逻辑推进发布新快照，旧观察仍保持不变");
        rejects(() -> frozen.clear(), "外部不能修改时间线观察列表");
        for (int i = 0; i < 20; i++) timeline.tick();
        check(timeline.frames().isEmpty(), "所有特效按本地寿命清理，不推进游戏状态");
        for (int i = 0; i < VisualBudget.CLIENT_LIMIT + 50; i++) {
            var a = new SpellVisual.Point(i * .001, 0, 0);
            timeline.add(
                    List.of(
                            new SpellVisual(
                                    SpellVisual.Kind.BEAM,
                                    Element.LIGHT,
                                    a,
                                    new SpellVisual.Point(i * .001, 0, 64),
                                    1,
                                    1)));
        }
        check(timeline.frames().size() == VisualBudget.CLIENT_LIMIT, "客户端长期积压有界");
        timeline.clear();
        check(timeline.frames().isEmpty(), "退出或切换世界清空特效");
        timeline.add(List.of(event(SpellVisual.Kind.IMPACT)));
        timeline.tick();
        timeline.add(List.of(event(SpellVisual.Kind.IMPACT)));
        check(
                timeline.frames().size() == 1 && timeline.frames().get(0).age() == 1,
                "重复接触不堆叠冲击环，也不把旧爆闪不断重置");
        timeline.clear();
        var c =
                new TickCircuit(
                        AssemblyContracts.line(
                                true, Route.Payload.MANA, Rune.Kind.INPUT, Rune.Kind.OUTPUT));
        var pool = new ManaPool(64 * Fixed.ONE, 0, 0);
        c.start();
        String saved = JsonCodecs.snapshot(c);
        timeline.add(List.of(line));
        for (int i = 0; i < 20; i++) timeline.tick();
        check(
                saved.equals(JsonCodecs.snapshot(c)) && pool.snapshot().spent() == 0,
                "播放和过期不会回写VM或玩家供给");
        geometry();
        feedback();
        return checks;
    }

    private record Quad(
            SpellGeometry.V a,
            SpellGeometry.V b,
            SpellGeometry.V c,
            SpellGeometry.V d,
            int sprite,
            int color,
            int alpha) {}

    private static List<Quad> mesh(List<VisualTimeline.Frame> frames) {
        var quads = new ArrayList<Quad>();
        SpellGeometry.emit(
                frames,
                new SpellGeometry.V(0, 0, 0),
                new SpellGeometry.V(1, 0, 0),
                new SpellGeometry.V(0, 1, 0),
                .25f,
                (a, b, c, d, sprite, color, alpha) ->
                        quads.add(new Quad(a, b, c, d, sprite, color, alpha)));
        return quads;
    }

    private static void geometry() {
        var at = new SpellVisual.Point(0, 0, -8);
        var hashes = new HashSet<Integer>();
        for (var element : Element.values()) {
            var e = new SpellVisual(SpellVisual.Kind.IMPACT, element, at, at, 1, 12);
            var frames = List.of(new VisualTimeline.Frame(e, 2, 12));
            var quads = mesh(frames);
            check(quads.size() >= 15, "命中包含冲击环、核心和碎屑：" + element);
            check(quads.equals(mesh(frames)), "视觉重建不消耗随机源：" + element);
            hashes.add(
                    quads.stream()
                            .map(q -> List.of(q.a, q.b, q.c, q.d, q.sprite))
                            .toList()
                            .hashCode());
            for (var q : quads)
                check(
                        q.sprite >= 0
                                && q.sprite < SpellAtlas.SPRITES
                                && q.alpha >= 0
                                && q.alpha <= 255
                                && Double.isFinite(
                                        q.a.length() + q.b.length() + q.c.length() + q.d.length()),
                        "几何、图集索引和透明度始终有限");
        }
        check(hashes.size() == Element.values().length, "元素差异包含真实几何与运动，而非只有换色");
        var beam =
                new SpellVisual(
                        SpellVisual.Kind.BEAM,
                        Element.FIRE,
                        at,
                        new SpellVisual.Point(0, 0, -30),
                        1,
                        1);
        check(mesh(List.of(new VisualTimeline.Frame(beam, 0, 4))).size() >= 10, "光束包含外沿、主体、亮核与元素层");
        var many = new ArrayList<VisualTimeline.Frame>();
        for (int i = 0; i < 2048; i++) many.add(new VisualTimeline.Frame(beam, 0, 4));
        var impact = new SpellVisual(SpellVisual.Kind.IMPACT, Element.WATER, at, at, 1, 12);
        many.add(new VisualTimeline.Frame(impact, 0, 12));
        var saturated = mesh(many);
        check(saturated.size() == SpellGeometry.QUAD_LIMIT, "密集法术仍遵守8192四边形硬上限");
        check(
                saturated.get(0).color == VisualStyles.of(Element.WATER).color(),
                "命中层优先于拥挤尾迹，不被旧事件饿死");
        var far =
                new SpellVisual(
                        SpellVisual.Kind.IMPACT,
                        Element.FIRE,
                        new SpellVisual.Point(1000, 0, 0),
                        new SpellVisual.Point(1000, 0, 0),
                        1,
                        1);
        check(mesh(List.of(new VisualTimeline.Frame(far, 0, 12))).isEmpty(), "视距外完全不生成几何");
        String current;
        try (var in =
                VisualContracts.class.getResourceAsStream(
                        "/assets/elementalcircuits/art/effects.json")) {
            current = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
        var before = VisualStyles.of(Element.FIRE);
        rejects(
                () ->
                        VisualStyles.load(
                                new java.io.StringReader(
                                        current.replace(
                                                "\"particles\": 14", "\"particles\": 14.5"))),
                "资源重载拒绝小数粒子数");
        check(VisualStyles.of(Element.FIRE).equals(before), "失败重载保留完整旧样式");
    }

    private static void feedback() {
        var pulse = new FeedbackPulse();
        check(pulse.marker(0) == 0 && pulse.recoil(0) == 0, "进入世界没有虚假反馈");
        pulse.accept(true, true, 1);
        check(pulse.marker(0) == 1 && pulse.recoil(1) > 0, "确认命中与释放分别驱动短暂反馈");
        for (int i = 0; i < 3; i++) {
            pulse.tick();
            pulse.accept(true, true, 1);
        }
        check(pulse.marker(0) < 1, "密集反馈不会每步重置脉冲形成常驻闪烁");
        for (int i = 0; i < 12; i++) pulse.tick();
        check(pulse.marker(0) == 0 && pulse.recoil(0) == 0 && pulse.kick(0) == 0, "镜头和准星反馈都有有限寿命");
        pulse.accept(true, true, 2);
        pulse.clear();
        check(pulse.marker(0) == 0 && pulse.recoil(1) == 0, "退出和切换世界立即清空反馈");
        rejects(() -> pulse.accept(true, true, Float.NaN), "拒绝非法镜头幅度");
        pulse.accept(false, true, 1);
        check(
                pulse.recoil(1) == 0 && pulse.roll(1) == 0 && pulse.marker(0) > 0,
                "远程设备命中可以确认，但没有手持施法后坐时不能晃动镜头");
    }
}
