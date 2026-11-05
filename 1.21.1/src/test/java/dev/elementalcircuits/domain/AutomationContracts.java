package dev.elementalcircuits.domain;

import dev.elementalcircuits.adapter.codec.JsonCodecs;
import dev.elementalcircuits.adapter.resources.RuleResources;
import dev.elementalcircuits.client.editor.*;
import dev.elementalcircuits.domain.casting.*;
import dev.elementalcircuits.domain.circuit.*;
import dev.elementalcircuits.domain.mana.*;

import java.util.*;

public final class AutomationContracts {
    private static int checks;

    private static void check(boolean ok, String why) {
        checks++;
        if (!ok) throw new AssertionError(why);
    }

    public static int run() {
        checks = 0;
        var input = Rune.create(1, Rune.Kind.ITEM_INPUT, new Hex(0, 0));
        var output =
                new Rune(
                        2,
                        Rune.Kind.ITEM_OUTPUT,
                        new Hex(1, 0),
                        dev.elementalcircuits.domain.combat.Element.EARTH,
                        5 * Fixed.ONE,
                        true,
                        false,
                        List.of(1));
        var spec = SpellSpec.EMPTY.modify(input).modify(output);
        check(spec.materials().input() == 0 && spec.materials().output() == 5, "物料端口随实际经过的符文组合");
        UUID anchor = UUID.randomUUID();
        spec = spec.withMaterials(spec.materials().bind(anchor));
        var scaled =
                spec.withReactive(true)
                        .withDamageMultiplier(2 * Fixed.ONE)
                        .withEffectScale(new EffectScale(2, 1));
        check(scaled.materials().equals(spec.materials()), "法强、效果比例与反应修饰保留物料端口");
        check(
                CastBudget.resolve(scaled, RuleResources.defaults())
                        .materials()
                        .equals(spec.materials()),
                "默认参数补齐不能丢失物料端口");
        var packet = new Packet(Mixture.pure(Fixed.ONE), scaled);
        check(JsonCodecs.readPacket(JsonCodecs.packetText(packet)).equals(packet), "在途法术保存恢复物料绑定");
        var old =
                com.google.gson.JsonParser.parseString(JsonCodecs.packetText(packet))
                        .getAsJsonObject();
        old.getAsJsonObject("spell").remove("materials");
        check(
                JsonCodecs.readPacket(old.toString())
                        .spell()
                        .materials()
                        .equals(MaterialPorts.NONE),
                "历史法术没有物料权限，显式迁移为空");
        boolean rejected = false;
        try {
            spec.merge(SpellSpec.EMPTY.withMaterials(new MaterialPorts(1, -1, null)));
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, "合流端口冲突拒绝，不能随机选容器");
        for (var kind : List.of(Rune.Kind.ITEM_INPUT, Rune.Kind.ITEM_OUTPUT, Rune.Kind.REDSTONE)) {
            check(
                    !RuneShapes.of(kind).isEmpty()
                            && RuneParameterEdit.supported(kind)
                            && RuneCatalog.Group.AUTOMATION.children.contains(kind),
                    "新符文具备资源、目录与现有参数入口");
        }
        var b =
                AssemblyContracts.line(
                        true, Route.Payload.SIGNAL, Rune.Kind.REDSTONE, Rune.Kind.SIGNAL_SEND);
        var circuit = new TickCircuit(b);
        check(circuit.start(), "红石源允许手动起阵");
        ManaPool supply = new ManaPool(100 * Fixed.ONE, 0, 0);
        for (int i = 0; i < 3; i++) {
            circuit.clearDrives();
            circuit.drive(1, true);
            circuit.tick(supply);
        }
        check(circuit.signal(2), "持续红石高电平经正常信号线传播");
        var resumed = JsonCodecs.readSnapshot(JsonCodecs.snapshot(circuit));
        check(resumed.signal(2), "红石驱动与时序现场完整恢复");
        for (int i = 0; i < 3; i++) {
            resumed.clearDrives();
            resumed.drive(1, false);
            resumed.tick(supply);
        }
        check(!resumed.signal(2) && resumed.validSignal(2), "无红石输出有效低电平，允许反相与锁存组合");
        return checks;
    }
}
