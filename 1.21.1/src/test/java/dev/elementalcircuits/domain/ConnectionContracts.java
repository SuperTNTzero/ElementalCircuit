package dev.elementalcircuits.domain;

import dev.elementalcircuits.adapter.codec.JsonCodecs;
import dev.elementalcircuits.adapter.resources.*;
import dev.elementalcircuits.client.editor.*;
import dev.elementalcircuits.domain.casting.*;
import dev.elementalcircuits.domain.circuit.*;
import dev.elementalcircuits.domain.mana.*;

import java.util.*;

public final class ConnectionContracts {
    private static int checks;

    private static void check(boolean ok, String why) {
        checks++;
        if (!ok) throw new AssertionError(why);
    }

    private static Rune rune(Rune.Kind kind) {
        return Rune.create(1, kind, new Hex(0, 0));
    }

    public static int run() {
        checks = 0;
        var spec =
                SpellSpec.EMPTY
                        .modify(rune(Rune.Kind.CONTACT))
                        .modify(rune(Rune.Kind.FORCE))
                        .modify(rune(Rune.Kind.CONSTRAINT))
                        .modify(rune(Rune.Kind.INTERACT));
        check(spec.compatibleOutput() && spec.shape() == SpellSpec.Shape.UNSET, "联系独立于形状");
        check(!spec.modify(rune(Rune.Kind.SPHERE)).compatibleOutput(), "联系不隐式创建几何作用域");
        check(!SpellSpec.EMPTY.modify(rune(Rune.Kind.FORCE)).compatibleOutput(), "效果不自动获得施法方式");
        check(!spec.modify(rune(Rune.Kind.ITEM_INPUT)).compatibleOutput(), "联系不授予容器能力");
        var rules = RuleResources.defaults();
        var resolved = CastBudget.resolve(spec, rules);
        check(resolved.shape() == SpellSpec.Shape.UNSET && resolved.radius() == 0, "联系不补齐球形默认值");
        check(resolved.connection().equals(spec.connection()), "默认参数保留独立效果");
        var quote = CastBudget.quote(spec, rules, CastContext.NEUTRAL, ShapeRules.protocolV1());
        check(quote.footprint() == 0, "联系按后续实际维护计费，无虚构体积费用");
        var spatial = new SpatialParameters(0, -30 * Fixed.ONE, 15 * Fixed.ONE, 3 * Fixed.ONE);
        spec = spec.withSpatial(spatial).withConnection(spec.connection().withSignal(true));
        var changed =
                spec.withMaterials(MaterialPorts.NONE)
                        .withReactive(true)
                        .withEffectScale(new EffectScale(2, 1))
                        .withDamageMultiplier(2 * Fixed.ONE);
        check(
                changed.spatial().equals(spatial) && changed.connection().equals(spec.connection()),
                "所有拷贝操作保留修饰维度");
        Packet p = new Packet(Mixture.pure(4 * Fixed.ONE), changed);
        check(JsonCodecs.readPacket(JsonCodecs.packetText(p)).equals(p), "方向、联系效果和采样信号可完整恢复");
        var old =
                com.google.gson.JsonParser.parseString(JsonCodecs.packetText(p)).getAsJsonObject();
        old.getAsJsonObject("spell").remove("connection");
        old.getAsJsonObject("spell").remove("spatial");
        var restored = JsonCodecs.readPacket(old.toString()).spell();
        check(
                restored.connection().empty() && restored.spatial().equals(SpatialParameters.NONE),
                "历史数据不凭空获得新效果");
        var allowance = new ConnectionBudget(2 * Fixed.ONE, false);
        check(allowance.edge(true) && !allowance.edge(true), "持续高电平只触发一次");
        var resumed = new ConnectionBudget(allowance.remaining(), allowance.high());
        check(
                !resumed.edge(true) && !resumed.edge(false) && resumed.edge(true),
                "恢复保持边沿锁存，低后再高可触发");
        check(!allowance.pay(3 * Fixed.ONE) && allowance.remaining() == 2 * Fixed.ONE, "支付失败无部分扣款");
        check(allowance.pay(Fixed.ONE) && allowance.remaining() == Fixed.ONE, "维护按实际结算扣款");
        allowance.add(Fixed.MAX);
        check(allowance.remaining() == Fixed.MAX, "超量供能不会数值溢出");
        check(
                ConnectionBudget.cost(65537) > Fixed.MAX
                        && !allowance.pay(ConnectionBudget.cost(65537)),
                "超出资源域的维护费不能饱和后低价支付");
        var fast = new ConnectionMotion.Vector(0, 0, 2);
        var braking =
                ConnectionMotion.impulse(fast, new ConnectionMotion.Vector(0, 0, .1), .15, .8);
        check(
                braking.length() <= .150000001 && fast.plus(braking).z() > 1.8,
                "速度上限不能瞬间抹去外来速度，所有制动力均受冲量限额约束");
        double[] v = spatial.direction(0, 0, 1);
        check(
                v[0] > 0
                        && v[1] > 0
                        && Math.abs(v[0] * v[0] + v[1] * v[1] + v[2] * v[2] - 1) < 1e-10,
                "方向旋转保持单位长度和正向俯仰");
        check(RuneParameterValue.parse(Rune.Kind.PITCH, "-12.125") == -794624, "俯仰允许负数连续设置");
        check(
                RuneParameterValue.parse(Rune.Kind.ANGLE, "85.123456789") > 85 * Fixed.ONE,
                "角度小数在固定点边界量化");
        var fan =
                new SpellSpec(
                        SpellSpec.Method.DIRECTED, SpellSpec.Shape.FAN, Fixed.ONE, 0, 4, 8192);
        var narrow = fan.withSpatial(new SpatialParameters(30 * Fixed.ONE, 0, 0, 0));
        var broad = fan.withSpatial(new SpatialParameters(150 * Fixed.ONE, 0, 0, 0));
        check(
                ShapeFootprint.blocks(broad, ShapeRules.protocolV1())
                        > ShapeFootprint.blocks(narrow, ShapeRules.protocolV1()),
                "张角改变实际方块足迹，缓存不能串用");
        var modern =
                rules.withCasting(
                        new CastingRules(
                                1,
                                655,
                                131,
                                Fixed.ONE,
                                Collections.nCopies(17, 0L),
                                0,
                                0,
                                Fixed.ONE));
        check(
                CastBudget.quote(broad, modern, CastContext.NEUTRAL, ShapeRules.protocolV1())
                                .required()
                        > CastBudget.quote(
                                        narrow,
                                        modern,
                                        CastContext.NEUTRAL,
                                        ShapeRules.protocolV1())
                                .required(),
                "张角增大时按实际足迹增加成本，不能只放大视觉");
        var a = new ShapeVolume(SpellSpec.Shape.FAN, 4, ShapeRules.protocolV1(), 30);
        var b = new ShapeVolume(SpellSpec.Shape.FAN, 4, ShapeRules.protocolV1(), 150);
        check(!a.contains(3, 0, 3) && b.contains(3, 0, 3), "张角影响权威体积归属");
        check(
                new ShapeVolume(SpellSpec.Shape.FAN, 4, ShapeRules.protocolV1(), 180)
                        .contains(4, 0, 0),
                "180度边界仍有完整半圆");
        for (var kind :
                List.of(
                        Rune.Kind.CONTACT,
                        Rune.Kind.FORCE,
                        Rune.Kind.CONSTRAINT,
                        Rune.Kind.INTERACT,
                        Rune.Kind.ANGLE,
                        Rune.Kind.YAW,
                        Rune.Kind.PITCH,
                        Rune.Kind.DISTANCE)) {
            check(
                    !RuneShapes.of(kind).isEmpty() && !RuneCatalog.title(kind).isBlank(),
                    "新符文有矢量资源与可读名称");
        }
        vm();
        return checks;
    }

    private static void vm() {
        var line =
                AssemblyContracts.line(
                        true,
                        Route.Payload.MANA,
                        Rune.Kind.INPUT,
                        Rune.Kind.CONTACT,
                        Rune.Kind.INTERACT,
                        Rune.Kind.OUTPUT);
        check(SupplyMode.gesture(line) == SupplyMode.CONFIGURED, "持续联系法阵在手势释放后保留配置供给");
        check(
                SupplyMode.gesture(
                                new Blueprint(
                                        line.version(),
                                        line.rules(),
                                        line.runes(),
                                        line.routes(),
                                        false))
                        == SupplyMode.PULSE,
                "单次联系仍采用单次手势");
        check(
                SupplyMode.gesture(
                                AssemblyContracts.line(
                                        true,
                                        Route.Payload.MANA,
                                        Rune.Kind.INPUT,
                                        Rune.Kind.OUTPUT))
                        == SupplyMode.PULSE,
                "其他持续模板保留点击、蓄力单次施法行为");
        var runes = new ArrayList<>(line.runes());
        runes.add(Rune.create(5, Rune.Kind.REDSTONE, new Hex(0, -1)));
        var routes = new ArrayList<>(line.routes());
        routes.add(new Route(5, 3, Route.Payload.SIGNAL, List.of(new Hex(0, -1), new Hex(0, 0))));
        var circuit = new TickCircuit(new Blueprint(5, line.rules(), runes, routes, true));
        circuit.start();
        ManaPool mana = new ManaPool(256 * Fixed.ONE, 0, 0);
        boolean sawHigh = false;
        for (int i = 0; i < 30; i++) {
            circuit.clearDrives();
            circuit.drive(5, true);
            for (var cast : circuit.tick(mana)) {
                sawHigh |= cast.spell().connection().high();
                check(cast.output() == 4, "每次施法保留实际输出符文身份");
            }
        }
        check(sawHigh, "交互修饰携带实际信号采样");
        circuit = JsonCodecs.readSnapshot(JsonCodecs.snapshot(circuit));
        boolean sawLow = false;
        for (int i = 0; i < 30; i++) {
            circuit.clearDrives();
            circuit.drive(5, false);
            for (var cast : circuit.tick(mana)) sawLow |= !cast.spell().connection().high();
        }
        check(sawLow, "恢复后低电平仍沿实际流转解除交互锁存");
    }
}
