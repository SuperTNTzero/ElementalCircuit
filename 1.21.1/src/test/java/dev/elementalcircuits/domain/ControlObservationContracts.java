package dev.elementalcircuits.domain;

import com.google.gson.JsonParser;

import dev.elementalcircuits.adapter.codec.JsonCodecs;
import dev.elementalcircuits.adapter.resources.RuleResources;
import dev.elementalcircuits.client.editor.*;
import dev.elementalcircuits.domain.casting.*;
import dev.elementalcircuits.domain.circuit.*;
import dev.elementalcircuits.domain.combat.Element;
import dev.elementalcircuits.domain.logic.*;
import dev.elementalcircuits.domain.mana.*;

import java.util.*;

/** Exercises actual transport, named outlets and complete delayed control recovery. */
public final class ControlObservationContracts {
    private static int checks;

    private static void check(boolean ok, String why) {
        checks++;
        if (!ok) throw new AssertionError(why);
    }

    private static void rejects(Runnable action, String why) {
        boolean rejected = false;
        try {
            action.run();
        } catch (IllegalArgumentException e) {
            rejected = true;
        }
        check(rejected, why);
    }

    private static Rune rune(int id, Rune.Kind kind, Hex at, long value, boolean on) {
        return new Rune(id, kind, at, Element.FIRE, value, on, false, List.of(1));
    }

    private static Route wire(Rune a, Rune b, Route.Payload payload, int port) {
        return new Route(a.id(), b.id(), payload, List.of(a.cell(), b.cell()), port);
    }

    private static void ledger(TickCircuit circuit) {
        check(
                circuit.supplied()
                        == circuit.held()
                                + circuit.snapshot().loss()
                                + circuit.snapshot().delivered()
                                + circuit.refunded(),
                "分流与控制必须保持来源账本守恒");
    }

    private static Blueprint router(Rune.Kind kind, boolean high, boolean both) {
        var input = Rune.create(1, Rune.Kind.MANA_RECEIVE, new Hex(-1, 0));
        var router = rune(2, kind, new Hex(0, 0), 0, true);
        var a = Rune.create(3, Rune.Kind.MANA_SEND, new Hex(1, 0));
        var b = Rune.create(4, Rune.Kind.MANA_SEND, new Hex(0, 1));
        var signal = rune(5, Rune.Kind.SIGNAL, new Hex(0, -1), 0, high);
        var wires =
                new ArrayList<>(
                        List.of(
                                wire(input, router, Route.Payload.MANA, 0),
                                wire(router, a, Route.Payload.MANA, 0)));
        if (both) wires.add(wire(router, b, Route.Payload.MANA, 1));
        if (kind == Rune.Kind.SELECT) wires.add(wire(signal, router, Route.Payload.SIGNAL, 0));
        return new Blueprint(
                5, RuleResources.defaults(), List.of(input, router, a, b, signal), wires, true);
    }

    private static void routing() {
        var origin = new ManaOrigin("player/" + UUID.randomUUID());
        var mixture =
                Mixture.ordered(
                        List.of(
                                new Mixture.Portion(
                                        Element.WATER.component(false), 2 * Fixed.ONE, 0, origin),
                                new Mixture.Portion(
                                        Element.FIRE.component(false), 3 * Fixed.ONE, 0, origin),
                                new Mixture.Portion(
                                        Element.FIRE.component(true), Fixed.ONE, 0, origin)));
        var circuit = new TickCircuit(router(Rune.Kind.FILTER, false, true));
        circuit.start();
        var supply = new ManaPool(Fixed.MAX, 0, 0);
        check(circuit.receive(1, new Packet(mixture, SpellSpec.EMPTY)), "真实外来包可送入筛分");
        for (int i = 0; i < 5; i++) {
            circuit.tick(supply);
            ledger(circuit);
        }
        var selected = circuit.offered(3);
        var remaining = circuit.offered(4);
        check(
                selected != null
                        && selected.mana().order().stream()
                                .allMatch(p -> p.component() == Element.FIRE.component(false)),
                "仅正相火进入选中出口");
        check(
                remaining != null
                        && remaining.mana().order().stream()
                                .noneMatch(p -> p.component() == Element.FIRE.component(false)),
                "其余出口保留水和反相火");
        check(
                remaining.mana().order().getFirst().component() == Element.WATER.component(false)
                        && remaining.mana().order().getLast().component()
                                == Element.FIRE.component(true),
                "筛分保留实际到达顺序");
        check(
                selected.mana().order().stream().allMatch(p -> p.origin().equals(origin)),
                "筛分保留真实供给来源");
        var blocked = new TickCircuit(router(Rune.Kind.FILTER, false, false));
        blocked.start();
        blocked.receive(1, new Packet(mixture, SpellSpec.EMPTY));
        for (int i = 0; i < 5; i++) blocked.tick(supply);
        check(blocked.offered(3) == null && blocked.held() > 0, "缺少余量出口时整批阻塞而不是销毁或部分偷放");
        ledger(blocked);
        var saved = JsonCodecs.snapshot(blocked);
        check(JsonCodecs.snapshot(JsonCodecs.readSnapshot(saved)).equals(saved), "多出口在途阻塞完整恢复");
        for (boolean high : List.of(false, true)) {
            var route = new TickCircuit(router(Rune.Kind.SELECT, high, true));
            var routeSupply = new ManaPool(1024 * Fixed.ONE, 0, 0);
            route.start();
            for (int i = 0; i < 3; i++) route.tick(routeSupply);
            route.receive(1, new Packet(Mixture.pure(Fixed.ONE), SpellSpec.EMPTY));
            for (int i = 0; i < 5; i++) {
                route.tick(routeSupply);
                ledger(route);
            }
            check(
                    route.offered(high ? 4 : 3) != null && route.offered(high ? 3 : 4) == null,
                    "择路只送往信号选定出口");
        }
        var draft = new EditorModel(router(Rune.Kind.FILTER, false, true));
        draft.selected = 2;
        draft.beginRoute(2, 1);
        check(draft.drawing() && draft.sourcePort == 1, "具名出口启动真实布线模型");
        draft.cancel();
        draft.draft.move(2, new Hex(0, -2));
        check(draft.draft.get().routes().get(2).sourcePort() == 1, "移动和自动重布线保留出口编号");
        var text = JsonCodecs.blueprint(router(Rune.Kind.FILTER, false, true));
        check(
                JsonCodecs.readBlueprint(text).equals(router(Rune.Kind.FILTER, false, true)),
                "蓝图保存多出口");
        rejects(
                () ->
                        JsonCodecs.readBlueprint(
                                text.replace("\"sourcePort\":1", "\"sourcePort\":2")),
                "拒绝不存在的筛分出口");
        rejects(
                () -> JsonCodecs.readBlueprint(text.replaceFirst("\"version\":5", "\"version\":4")),
                "当前协议明确拒绝旧版蓝图");
    }

    private static void delay() {
        Rune rune = rune(2, Rune.Kind.DELAY, new Hex(0, 0), 7 * Fixed.ONE, true);
        var state = RuneLogicState.initial(rune);
        var random = new Random(741);
        var values = new ArrayList<Boolean>();
        var validity = new ArrayList<Boolean>();
        for (int tick = 0; tick < 2500; tick++) {
            boolean high = random.nextBoolean(), valid = tick % 19 != 0;
            values.add(valid && high);
            validity.add(valid);
            var result = RuneLogic.step(rune, state, high, valid, true);
            state = result.state();
            state.validate(rune);
            check(result.valid() == (tick >= 7 && validity.get(tick - 7)), "延时保持信号有效性，不把失效变成有效低电平");
            if (tick >= 7) check(result.value() == values.get(tick - 7), "随机脉冲逐一按固定时间到达");
        }
        var input = Rune.create(1, Rune.Kind.SIGNAL_RECEIVE, new Hex(-1, 0));
        var output = Rune.create(3, Rune.Kind.SIGNAL_SEND, new Hex(1, 0));
        var bp =
                new Blueprint(
                        5,
                        RuleResources.defaults(),
                        List.of(input, rune, output),
                        List.of(
                                wire(input, rune, Route.Payload.SIGNAL, 0),
                                wire(rune, output, Route.Payload.SIGNAL, 0)),
                        true);
        var original = new TickCircuit(bp);
        original.start();
        var supply = new ManaPool(Fixed.MAX, 0, 0);
        for (int t = 0; t < 80; t++) {
            original.clearDrives();
            original.drive(1, t % 3 == 0);
            original.tick(supply);
        }
        var saved = JsonCodecs.snapshot(original);
        var restored = JsonCodecs.readSnapshot(saved);
        check(
                saved.equals(JsonCodecs.snapshot(restored)) && !restored.logic(2).queue().isEmpty(),
                "完整读档保留未到期脉冲和已输出状态");
        var supply2 = new ManaPool(Fixed.MAX, 0, 0);
        for (int t = 0; t < 80; t++) {
            for (var c : List.of(original, restored)) {
                c.clearDrives();
                c.drive(1, t % 5 < 2);
                c.tick(c == original ? supply : supply2);
            }
            check(
                    JsonCodecs.snapshot(original).equals(JsonCodecs.snapshot(restored)),
                    "读档后的每步结果与不中断运行完全相同");
        }
        restored.stop();
        var frozen = restored.logic(2);
        for (int i = 0; i < 8; i++) restored.tick(supply2);
        check(frozen.equals(restored.logic(2)), "停阵冻结延时队列，不越过暂停时间");
        restored.resetLogic(2);
        check(
                restored.logic(2).queue().isEmpty() && restored.logic(2).elapsed() == 0,
                "复位彻底清除延时队列");
        var malicious = JsonParser.parseString(saved).getAsJsonObject();
        malicious
                .getAsJsonArray("logic")
                .get(1)
                .getAsJsonObject()
                .getAsJsonArray("queue")
                .get(0)
                .getAsJsonObject()
                .addProperty("due", 0);
        rejects(() -> JsonCodecs.readSnapshot(malicious.toString()), "拒绝损坏到期时间，不能读档制造额外脉冲");
    }

    private static void contact() {
        var contact = rune(1, Rune.Kind.CONTACT, new Hex(0, 0), 0, true);
        var spec =
                SpellSpec.EMPTY
                        .modify(contact)
                        .modify(rune(2, Rune.Kind.ANCHOR, new Hex(1, 0), 0, true))
                        .modify(rune(3, Rune.Kind.TARGET, new Hex(2, 0), Fixed.ONE, true));
        check(
                spec.compatibleOutput()
                        && spec.connection().anchored()
                        && spec.connection().target() == 1,
                "锚定和筛选是可组合联系修饰");
        check(
                !spec.modify(rune(4, Rune.Kind.SPHERE, new Hex(3, 0), 0, true)).compatibleOutput(),
                "新联系参数不会暗中获得几何形状");
        check(
                JsonCodecs.readPacket(
                                JsonCodecs.packetText(new Packet(Mixture.pure(Fixed.ONE), spec)))
                        .spell()
                        .equals(spec),
                "联系控制和参考规则完整保存到魔力包");
        for (var kind :
                List.of(
                        Rune.Kind.FILTER,
                        Rune.Kind.SELECT,
                        Rune.Kind.DELAY,
                        Rune.Kind.ANCHOR,
                        Rune.Kind.TARGET,
                        Rune.Kind.PROBE)) {
            check(
                    !RuneShapes.of(kind).isEmpty() && !RuneCatalog.description(kind).isBlank(),
                    "新符文统一具备静态符形和说明");
        }
        var base =
                AssemblyContracts.line(
                        true,
                        Route.Payload.MANA,
                        Rune.Kind.INPUT,
                        Rune.Kind.CONTACT,
                        Rune.Kind.CONSTRAINT,
                        Rune.Kind.OUTPUT);
        var nodes = new ArrayList<>(base.runes());
        var signal = Rune.create(5, Rune.Kind.SIGNAL_RECEIVE, new Hex(-1, -1));
        nodes.add(signal);
        var routes = new ArrayList<>(base.routes());
        routes.add(wire(signal, base.rune(2), Route.Payload.SIGNAL, 0));
        var c = new TickCircuit(new Blueprint(5, base.rules(), nodes, routes, true));
        var supply = new ManaPool(Fixed.MAX, 0, 0);
        c.start();
        int emitted = 0;
        for (int t = 0; t < 20; t++) {
            c.clearDrives();
            c.drive(5, true);
            emitted += c.tick(supply).size();
            ledger(c);
        }
        check(c.contactReleased(2) && emitted == 0, "持续高电平阻止联系重新建立而不反复浪费释放");
        c = JsonCodecs.readSnapshot(JsonCodecs.snapshot(c));
        for (int t = 0; t < 20; t++) {
            c.clearDrives();
            c.drive(5, false);
            emitted += c.tick(supply).size();
        }
        check(!c.contactReleased(2) && emitted > 0, "解除信号回低且经过因果边界后可再次建立联系");
    }

    private static void storageSignals() {
        var standard =
                new StandardState(1, 0, 0, 2, 1200, Fixed.ONE, 6554, 32768, 2 * Fixed.ONE, 484968);
        var rules =
                RuleResources.defaults()
                        .withCasting(
                                new CastingRules(
                                        2,
                                        0,
                                        0,
                                        Fixed.ONE,
                                        Collections.nCopies(17, 0L),
                                        0,
                                        0,
                                        Fixed.ONE,
                                        standard));
        for (boolean open : List.of(false, true)) {
            var input = Rune.create(1, Rune.Kind.INPUT, new Hex(-1, 0));
            var storage =
                    new Rune(
                            2,
                            Rune.Kind.STORAGE,
                            new Hex(0, 0),
                            Element.FIRE,
                            0,
                            open,
                            false,
                            List.of(1),
                            new StorageSettings(Fixed.ONE, Fixed.ONE, Fixed.ONE));
            var full = Rune.create(3, Rune.Kind.SIGNAL_SEND, new Hex(1, 0));
            var ready = Rune.create(4, Rune.Kind.SIGNAL_SEND, new Hex(0, 1));
            var empty = Rune.create(5, Rune.Kind.SIGNAL_SEND, new Hex(0, -1));
            var wires =
                    List.of(
                            wire(input, storage, Route.Payload.MANA, 0),
                            wire(storage, full, Route.Payload.SIGNAL, 0),
                            wire(storage, ready, Route.Payload.SIGNAL, 1),
                            wire(storage, empty, Route.Payload.SIGNAL, 2));
            var c =
                    new TickCircuit(
                            new Blueprint(
                                    5,
                                    rules,
                                    List.of(input, storage, full, ready, empty),
                                    wires,
                                    true));
            var supply = new ManaPool(Fixed.MAX, 0, 0);
            c.start();
            for (int t = 0; t < 6; t++) {
                c.tick(supply);
                ledger(c);
            }
            check(
                    c.validSignal(3) && c.signal(3) && c.signal(4) == open && !c.signal(5),
                    "存储的已满、可释放、已空状态独立布线，释放许可不等于容量已满");
            var restored = JsonCodecs.readSnapshot(JsonCodecs.snapshot(c));
            check(restored.signal(3) && restored.signal(4) == open, "恢复保留存储状态线路和存量");
        }
    }

    private static void newRunes() {
        Rune edge = rune(1, Rune.Kind.EDGE, new Hex(0, 0), 0, true);
        var state = RuneLogicState.initial(edge);
        var result = RuneLogic.step(edge, state, true, true, true);
        state = result.state();
        check(!result.value(), "边沿检测先采样基准，不把启动电平变成脉冲");
        result = RuneLogic.step(edge, state, false, true, true);
        state = result.state();
        check(!result.value(), "上升沿模式忽略下降变化");
        result = RuneLogic.step(edge, state, true, true, true);
        state = result.state();
        check(result.value(), "上升沿产生一次脉冲");
        check(!RuneLogic.step(edge, state, true, true, true).value(), "持续高电平不重复触发");
        check(!RuneLogic.step(edge, state, false, false, true).valid(), "无效输入不制造边沿");

        Rune throttle = rune(2, Rune.Kind.THROTTLE, new Hex(0, 0), 4 * Fixed.ONE, true);
        var source = Rune.create(1, Rune.Kind.MANA_RECEIVE, new Hex(-1, 0));
        var accepted = Rune.create(3, Rune.Kind.MANA_SEND, new Hex(1, 0));
        var remainder = Rune.create(4, Rune.Kind.MANA_SEND, new Hex(0, 1));
        var board =
                new Blueprint(
                        5,
                        RuleResources.defaults(),
                        List.of(source, throttle, accepted, remainder),
                        List.of(
                                wire(source, throttle, Route.Payload.MANA, 0),
                                wire(throttle, accepted, Route.Payload.MANA, 0),
                                wire(throttle, remainder, Route.Payload.MANA, 1)),
                        true);
        var circuit = new TickCircuit(board);
        circuit.start();
        circuit.receive(1, new Packet(Mixture.pure(6 * Fixed.ONE), SpellSpec.EMPTY));
        var supply = new ManaPool(Fixed.MAX, 0, 0);
        for (int i = 0; i < 8; i++) circuit.tick(supply);
        check(circuit.offered(3) != null && circuit.offered(4) != null, "限流器连接双出口后显式转发两类余额");
        check(
                circuit.offered(3).mana().total() > 0 && circuit.offered(4).mana().total() > 0,
                "额度内与超额魔力均有去向");
        check(circuit.snapshot().version() == 14, "新蓝图对应独立法阵现场版本");
        check(
                JsonCodecs.readSnapshot(JsonCodecs.snapshot(circuit)).offered(4) != null,
                "读档保留限流后分支在途状态");

        Rune filter =
                new Rune(
                        5,
                        Rune.Kind.MATERIAL_FILTER,
                        new Hex(0, 0),
                        Element.FIRE,
                        0,
                        true,
                        false,
                        List.of(1),
                        dev.elementalcircuits.domain.mana.StorageSettings.NONE,
                        "#minecraft:logs");
        var spell =
                SpellSpec.EMPTY
                        .modify(filter)
                        .modify(Rune.create(6, Rune.Kind.ITEM_INPUT, new Hex(1, 0)));
        var packet = new Packet(Mixture.pure(Fixed.ONE), spell);
        check(
                JsonCodecs.readPacket(JsonCodecs.packetText(packet)).equals(packet)
                        && packet.spell().materials().filter().equals("#minecraft:logs"),
                "物料标签沿在途法术数据保存");
        rejects(
                () ->
                        new Rune(
                                7,
                                Rune.Kind.MATERIAL_FILTER,
                                new Hex(0, 0),
                                Element.FIRE,
                                0,
                                true,
                                false,
                                List.of(1),
                                dev.elementalcircuits.domain.mana.StorageSettings.NONE,
                                "invalid id"),
                "筛选符文拒绝非法资源位置");
    }

    public static int run() {
        checks = 0;
        routing();
        delay();
        contact();
        storageSignals();
        newRunes();
        return checks;
    }
}
