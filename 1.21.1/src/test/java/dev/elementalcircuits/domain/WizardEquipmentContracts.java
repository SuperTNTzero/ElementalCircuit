package dev.elementalcircuits.domain;

import dev.elementalcircuits.adapter.codec.JsonCodecs;
import dev.elementalcircuits.adapter.resources.RuleResources;
import dev.elementalcircuits.adapter.resources.ShapeResources;
import dev.elementalcircuits.client.editor.EquipmentWheelModel;
import dev.elementalcircuits.domain.casting.CastBudget;
import dev.elementalcircuits.domain.casting.CastContext;
import dev.elementalcircuits.domain.casting.SpellSpec;
import dev.elementalcircuits.domain.circuit.Rune;
import dev.elementalcircuits.domain.mana.ManaPool;
import dev.elementalcircuits.domain.mana.Mixture;
import dev.elementalcircuits.domain.mana.Packet;

/** Resource conservation and invocation provenance across equipment changes and persistence. */
public final class WizardEquipmentContracts {
    private static int checks;

    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }

    private static void rejects(Runnable action) {
        boolean rejected = false;
        try {
            action.run();
        } catch (RuntimeException e) {
            rejected = true;
        }
        check(rejected, "拒绝损坏的装备容量现场");
    }

    public static int run() {
        long f = Fixed.ONE;
        var p = new ManaPool(64 * f, 4 * f, 40);
        p.equipmentBonus(88 * f);
        check(
                p.capacity() == 152 * f
                        && p.permanentCapacity() == 64 * f
                        && p.available() == 64 * f,
                "穿戴只扩大容量，不凭空补能");
        p.setAmount(140 * f);
        p.debit(40 * f);
        var restored = JsonCodecs.readPool(JsonCodecs.pool(p), 4 * f, 40);
        check(
                restored.capacity() == 152 * f
                        && restored.available() == 100 * f
                        && restored.permanentCapacity() == 64 * f,
                "装备额外储量和永久容量分别恢复");
        restored.equipmentBonus(0);
        check(restored.available() == 64 * f && restored.capacity() == 64 * f, "脱装截断超额魔力");
        for (int i = 0; i < 100; i++) {
            restored.equipmentBonus(88 * f);
            restored.equipmentBonus(0);
        }
        check(
                restored.available() == 64 * f && restored.permanentCapacity() == 64 * f,
                "反复穿脱不增长魔力");
        restored.equipmentBonus(88 * f);
        for (int i = 0; i < 100; i++) restored.increaseCapacity(16 * f, 1024 * f);
        check(
                restored.permanentCapacity() == 1024 * f && restored.capacity() == 1112 * f,
                "果实上限只约束永久成长");
        var old = ManaPool.restore(new ManaPool.State(1, 64 * f, 32 * f, 0, 0, 32 * f), 4 * f, 40);
        check(old.capacity() == 64 * f && old.available() == 32 * f, "v1迁移不补能");
        rejects(() -> ManaPool.restore(new ManaPool.State(1, 64 * f, 32 * f, 0, 0, 0, f), 0, 0));
        rejects(() -> ManaPool.restore(new ManaPool.State(2, Fixed.MAX, 0, 0, 0, 0, 1), 0, 0));
        p.setCapacity(Fixed.MAX);
        p.equipmentBonus(Fixed.MAX);
        check(p.capacity() == Fixed.MAX, "最终容量防溢出");
        var rules = RuleResources.defaults();
        var ordinary = new SpellSpec(SpellSpec.Method.DIRECTED, SpellSpec.Shape.BEAM, f, 12, 0, 0);
        var reactive =
                ordinary.withReactive(true).modify(Rune.create(1, Rune.Kind.BEAM, new Hex(0, 0)));
        check(reactive.reactive(), "修饰不抹去反击来源");
        var packet = new Packet(Mixture.pure(f), reactive);
        check(JsonCodecs.readPacket(JsonCodecs.packetText(packet)).spell().reactive(), "反击来源随弹道存档");
        var normalQuote =
                CastBudget.quote(ordinary, rules, CastContext.NEUTRAL, ShapeResources.rules());
        var reactiveQuote =
                CastBudget.quote(
                        ordinary,
                        rules,
                        CastContext.NEUTRAL.withReactive(true),
                        ShapeResources.rules());
        check(
                reactiveQuote.resolved().reactive()
                        && normalQuote.required() == reactiveQuote.required(),
                "反击保留常规成本和参数规则");
        check(
                EquipmentWheelModel.hover(0, 0, 0) == -1
                        && EquipmentWheelModel.hover(0, 0, 8) == -1,
                "空轮盘和中心取消区");
        for (int count = 1; count <= 8; count++)
            for (int i = 0; i < count; i++) {
                double angle = -Math.PI / 2 + Math.PI * 2 * i / count;
                check(
                        EquipmentWheelModel.hover(Math.cos(angle) * 90, Math.sin(angle) * 90, count)
                                == i,
                        "八向分页角度选择");
            }
        return checks;
    }
}
