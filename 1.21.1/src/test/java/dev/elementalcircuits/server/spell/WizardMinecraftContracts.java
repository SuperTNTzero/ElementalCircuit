package dev.elementalcircuits.server.spell;

import dev.elementalcircuits.*;
import dev.elementalcircuits.adapter.config.ServerSettings;
import dev.elementalcircuits.adapter.persistence.*;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.circuit.Blueprint;
import dev.elementalcircuits.domain.circuit.Route;
import dev.elementalcircuits.domain.circuit.Rune;
import dev.elementalcircuits.domain.combat.DamageAffinity;
import dev.elementalcircuits.domain.combat.Element;
import dev.elementalcircuits.item.WizardArmorItem;
import dev.elementalcircuits.server.casting.WandCooldown;
import dev.elementalcircuits.server.circuit.WorldCircuits;
import dev.elementalcircuits.server.equipment.EquipmentCasting;
import dev.elementalcircuits.server.equipment.SpellAttributes;
import dev.elementalcircuits.server.equipment.WizardEquipment;
import dev.elementalcircuits.server.mana.PlayerMana;
import dev.elementalcircuits.server.workshop.InscriptionAccess;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;

import top.theillusivec4.curios.api.CuriosApi;

import java.util.*;

@GameTestHolder(ElementalCircuits.ID)
@PrefixGameTestTemplate(false)
public final class WizardMinecraftContracts {
    private static final long F = Fixed.ONE;

    private static ServerPlayer player(GameTestHelper h) {
        var p =
                new net.neoforged.neoforge.common.util.FakePlayer(
                        h.getLevel(),
                        new com.mojang.authlib.GameProfile(UUID.randomUUID(), "wizard-test")) {
                    @Override
                    public boolean isInvulnerableTo(
                            net.minecraft.world.damagesource.DamageSource source) {
                        return false;
                    }
                };
        p.setPos(Vec3.atCenterOf(h.absolutePos(new BlockPos(1, 2, 1))));
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        return p;
    }

    private static ItemStack robe(ServerPlayer p) {
        var s = new ItemStack(WizardEquipment.ROBE.get());
        p.setItemSlot(EquipmentSlot.CHEST, s);
        return s;
    }

    private static Blueprint line() {
        var kinds =
                new Rune.Kind[] {
                    Rune.Kind.INPUT,
                    Rune.Kind.CONVERT,
                    Rune.Kind.DIRECTED,
                    Rune.Kind.BEAM,
                    Rune.Kind.OUTPUT
                };
        List<Rune> runes = new ArrayList<>();
        List<Route> routes = new ArrayList<>();
        for (int i = 0; i < kinds.length; i++) {
            runes.add(Rune.create(i + 1, kinds[i], new Hex(i - 2, 0)));
            if (i > 0)
                routes.add(
                        new Route(
                                i,
                                i + 1,
                                Route.Payload.MANA,
                                List.of(runes.get(i - 1).cell(), runes.get(i).cell())));
        }
        return new Blueprint(5, ServerSettings.circuitRules(), runes, routes, false)
                .withInputAmount(1, 8 * F);
    }

    private static ItemStack ready(ServerPlayer p) {
        var s = robe(p);
        CarrierRepository.load(s).replace(1, line());
        CarrierRepository.commit(s);
        return s;
    }

    @GameTest(template = "empty")
    public static void equipmentNativeAttributes(GameTestHelper h) {
        var items =
                List.of(
                        WizardEquipment.HAT,
                        WizardEquipment.ROBE,
                        WizardEquipment.LEGGINGS,
                        WizardEquipment.BOOTS);
        double power = 1, capacity = 0, regen = 0;
        for (var item : items) {
            var armor = (WizardArmorItem) item.get();
            for (var e : armor.getDefaultAttributeModifiers().modifiers()) {
                if (!e.slot().test(armor.getEquipmentSlot())) throw new AssertionError("属性槽位错误");
                if (e.attribute().equals(SpellAttributes.POWER)) power += e.modifier().amount();
                if (e.attribute().equals(SpellAttributes.MANA_CAPACITY))
                    capacity += e.modifier().amount();
                if (e.attribute().equals(SpellAttributes.MANA_REGENERATION))
                    regen += e.modifier().amount();
            }
        }
        h.assertTrue(
                Math.abs(power - 1.55) < .00001 && capacity == 880 && regen == 2.75, "四件法袍属性独立相加");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void curiosRingIsRealEquippedHost(GameTestHelper h) {
        var p = player(h);
        var handler =
                CuriosApi.getCuriosInventory(p)
                        .orElseThrow()
                        .getStacksHandler("ring")
                        .orElseThrow()
                        .getStacks();
        h.assertTrue(handler.getSlots() >= 2, "提供真实Curios戒指槽");
        var ring = new ItemStack(WizardEquipment.RING.get());
        handler.setStackInSlot(0, ring);
        h.assertTrue(EquipmentCasting.worn(p, ring), "实际饰品槽作为承载");
        var id = CarrierRepository.identity(ring);
        h.assertTrue(EquipmentCasting.owned(p, id) == ring, "所有权解析实际饰品");
        handler.setStackInSlot(0, ItemStack.EMPTY);
        h.assertTrue(!EquipmentCasting.worn(p, ring), "卸下戒指立即失去施法资格");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void equipmentCapacityAndRecovery(GameTestHelper h) {
        var p = player(h);
        p.getAttribute(SpellAttributes.MANA_CAPACITY).setBaseValue(32);
        p.getAttribute(SpellAttributes.MANA_REGENERATION).setBaseValue(2);
        var pool = PlayerMana.get(p);
        h.assertTrue(
                pool.capacity() == 96 * F
                        && pool.permanentCapacity() == 64 * F
                        && pool.available() == 64 * F,
                "属性扩大容量不补能");
        h.assertTrue(
                pool.regeneration()
                        == 6 * F + 96 * ServerSettings.mana("player.percentRegeneration"),
                "装备回复进入固定分支");
        pool.setAmount(80 * F);
        PlayerMana.save(p);
        PlayerMana.forget(p);
        pool = PlayerMana.get(p);
        h.assertTrue(pool.available() == 80 * F && pool.capacity() == 96 * F, "重登保留额外储量");
        p.getAttribute(SpellAttributes.MANA_CAPACITY).setBaseValue(0);
        h.assertTrue(
                PlayerMana.get(p).capacity() == 64 * F && pool.available() == 64 * F, "脱装不写入永久成长");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void equipmentCastUsesSchedulerAndPlayerSupply(GameTestHelper h) {
        var p = player(h);
        var s = ready(p);
        long before = PlayerMana.get(p).available();
        h.assertTrue(EquipmentCasting.activate(p, s, 1, new Vec3(0, 0, 1)), "穿戴法袍可快捷施法");
        for (int t = 0; t < 20 && CarrierRepository.load(s).powered(); t++) {
            EquipmentCasting.tick(p);
            WorldCircuits.tick(h.getLevel());
        }
        h.assertTrue(
                PlayerMana.get(p).available() < before && !WandCooldown.state(s).ready(),
                "走权威调度并支付玩家魔力和独立冷却");
        h.assertTrue(!CarrierRepository.load(s).powered(), "释放后停止本次装备供给");
        h.assertTrue(!EquipmentCasting.activate(p, s, 1, new Vec3(0, 0, 1)), "冷却拒绝连发");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void unwornCastRejected(GameTestHelper h) {
        var p = player(h);
        var s = ready(p);
        p.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        h.assertTrue(
                !EquipmentCasting.activate(p, s, 1, new Vec3(0, 0, 1))
                        && !CarrierRepository.load(s).powered(),
                "持有未穿戴装备不可施法");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void hurtTriggerOnlyAcceptsDamage(GameTestHelper h) {
        var p = player(h);
        var s = ready(p);
        EquipmentCasting.trigger(s, 1, EquipmentCasting.Trigger.HURT);
        EquipmentCasting.hurt(p, p.damageSources().generic(), 0);
        h.assertTrue(!CarrierRepository.load(s).powered(), "零伤害不触发");
        EquipmentCasting.hurt(p, p.damageSources().generic(), 2);
        h.assertTrue(CarrierRepository.load(s).powered(), "有效受击只启动配置受击的法阵");
        h.assertTrue(ItemData.read(s).getBoolean("ReactiveCast"), "反击来源随装备保存");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void retaliationCannotRetaliateAgain(GameTestHelper h) {
        var p = player(h);
        var s = ready(p);
        EquipmentCasting.trigger(s, 1, EquipmentCasting.Trigger.HURT);
        var source =
                new ElementalDamageSource(
                        h.getLevel()
                                .registryAccess()
                                .registryOrThrow(
                                        net.minecraft.core.registries.Registries.DAMAGE_TYPE)
                                .getHolderOrThrow(SpellDamage.type(DamageAffinity.FIRE)),
                        p,
                        p.getUUID(),
                        Map.of(DamageAffinity.FIRE, F),
                        true);
        EquipmentCasting.hurt(p, source, 2);
        h.assertTrue(!CarrierRepository.load(s).powered(), "反击命中不能继续引发反击");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void workstationAuthorizesArmor(GameTestHelper h) {
        var p = player(h);
        var s = new ItemStack(WizardEquipment.HAT.get());
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, s);
        var pos = h.absolutePos(new BlockPos(2, 2, 2));
        h.getLevel().setBlock(pos, ElementalCircuits.TABLE.get().defaultBlockState(), 3);
        InscriptionAccess.interact(p, pos);
        h.assertTrue(InscriptionAccess.allowed(p, s, null), "帽子使用原有工作台刻印授权");
        InscriptionAccess.close(p);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void acceptedNativeHitTriggers(GameTestHelper h) {
        var p = player(h);
        var s = ready(p);
        EquipmentCasting.trigger(s, 1, EquipmentCasting.Trigger.HURT);
        boolean accepted = p.hurt(p.damageSources().genericKill(), 1);
        h.assertTrue(accepted && CarrierRepository.load(s).powered(), "原版已接受受击事件启动装备法阵");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void cancelledNativeHitDoesNotTrigger(GameTestHelper h) {
        var p = player(h);
        var s = ready(p);
        EquipmentCasting.trigger(s, 1, EquipmentCasting.Trigger.HURT);
        java.util.function.Consumer<
                        net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent>
                cancel =
                        e -> {
                            if (e.getEntity() == p) e.setCanceled(true);
                        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cancel);
        try {
            p.hurt(p.damageSources().genericKill(), 1);
        } finally {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(cancel);
        }
        h.assertTrue(!CarrierRepository.load(s).powered(), "取消伤害不触发装备法阵");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void malformedGearDoesNotBreakOtherGear(GameTestHelper h) {
        var p = player(h);
        var s = ready(p);
        var broken = new ItemStack(WizardEquipment.HAT.get());
        ItemData.update(broken, t -> t.putString("ElementalCircuit", "damaged"));
        p.setItemSlot(EquipmentSlot.HEAD, broken);
        EquipmentCasting.tick(p);
        h.assertTrue(ItemData.read(broken).getBoolean("ElementalCircuitFault"), "损坏装备独立停用");
        h.assertTrue(
                EquipmentCasting.inspect(p).stream()
                        .anyMatch(v -> v.identity().equals(CarrierRepository.identity(s))),
                "坏帽子不阻止好法袍被观察和施法");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void unwornCacheDecaysWithoutSupplyOrRelease(GameTestHelper h) {
        var p = player(h);
        var s = robe(p);
        var input = Rune.create(1, Rune.Kind.INPUT, new Hex(0, 0));
        var gate =
                new Rune(
                        2,
                        Rune.Kind.SWITCH,
                        new Hex(1, 0),
                        Element.PURE,
                        0,
                        false,
                        false,
                        List.of());
        var b =
                new Blueprint(
                        5,
                        ServerSettings.circuitRules(),
                        List.of(input, gate),
                        List.of(
                                new Route(
                                        1,
                                        2,
                                        Route.Payload.MANA,
                                        List.of(input.cell(), gate.cell()))),
                        false);
        CarrierRepository.load(s).replace(1, b);
        EquipmentCasting.activate(p, s, 1, new Vec3(0, 0, 1));
        WorldCircuits.tick(h.getLevel());
        var c = CarrierRepository.load(s).circuit(1);
        long held = c.held(), before = PlayerMana.get(p).available();
        h.assertTrue(held > 0, "关闭开关中存在缓存");
        p.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        p.getInventory().setItem(0, s);
        EquipmentCasting.tick(p);
        WorldCircuits.tick(h.getLevel());
        h.assertTrue(
                !c.powered() && c.held() < held && PlayerMana.get(p).available() == before,
                "卸下后不再供给或释放，背包中的缓存继续自然损耗");
        h.succeed();
    }
}
