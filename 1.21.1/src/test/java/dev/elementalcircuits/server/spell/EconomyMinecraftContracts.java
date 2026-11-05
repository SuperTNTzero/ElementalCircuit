package dev.elementalcircuits.server.spell;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.codec.JsonCodecs;
import dev.elementalcircuits.adapter.config.ServerSettings;
import dev.elementalcircuits.adapter.persistence.*;
import dev.elementalcircuits.adapter.resources.ManaFieldResources;
import dev.elementalcircuits.adapter.resources.SecondaryResources;
import dev.elementalcircuits.block.CircuitBlockEntity;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.casting.ProjectileEffects;
import dev.elementalcircuits.domain.casting.SpellSpec;
import dev.elementalcircuits.domain.circuit.Route;
import dev.elementalcircuits.domain.circuit.Rune;
import dev.elementalcircuits.domain.combat.Element;
import dev.elementalcircuits.domain.mana.ManaNode;
import dev.elementalcircuits.domain.mana.ManaOrigin;
import dev.elementalcircuits.domain.mana.ManaPool;
import dev.elementalcircuits.domain.mana.Mixture;
import dev.elementalcircuits.domain.mana.Packet;
import dev.elementalcircuits.server.circuit.WorldCircuits;
import dev.elementalcircuits.server.equipment.SpellAttributes;
import dev.elementalcircuits.server.equipment.WandEquipment;
import dev.elementalcircuits.server.mana.PlayerMana;
import dev.elementalcircuits.server.mana.RefundReceipts;
import dev.elementalcircuits.server.mana.WorldManaField;
import dev.elementalcircuits.server.mana.WorldManaRefunds;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.*;

import java.util.*;
import java.util.function.Consumer;

@GameTestHolder(ElementalCircuits.ID)
@PrefixGameTestTemplate(false)
public final class EconomyMinecraftContracts {
    private static final long F = Fixed.ONE;

    private static ServerPlayer player(GameTestHelper h) {
        return net.neoforged.neoforge.common.util.FakePlayerFactory.get(
                h.getLevel(),
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "economy-test"));
    }

    private static LivingEntity pig(GameTestHelper h) {
        var p = h.spawn(EntityType.PIG, new BlockPos(2, 3, 2));
        p.setNoAi(true);
        p.setNoGravity(true);
        return p;
    }

    private static Mixture mana(Element e, long amount) {
        return Mixture.pure(amount).convert(e, F);
    }

    private static ContactMagic hitEffects(long power) {
        return new ContactMagic(
                power, new ProjectileEffects(0, 0, 0, 0, 0, 20, 20, 4), SecondaryResources.rules());
    }

    @GameTest(template = "empty")
    public static void nativeAttributeAndFrozenContact(GameTestHelper h) {
        var p = player(h);
        h.assertTrue(p.getAttributeValue(SpellAttributes.POWER) == 1, "原生玩家法强默认一");
        p.getAttribute(SpellAttributes.POWER).setBaseValue(2);
        var context = SpellAttributes.context(p);
        h.assertTrue(
                context.damageMultiplier() == 2 * F && context.manaMultiplier() == 2 * F,
                "原生法强和指数一成本");
        var e = pig(h);
        float before = e.getHealth();
        var spec =
                new SpellSpec(SpellSpec.Method.DIRECTED, SpellSpec.Shape.BEAM, F, 12, 0, 0)
                        .withDamageMultiplier(context.damageMultiplier());
        var packet =
                JsonCodecs.readOrderedPacket(
                        JsonCodecs.packetText(new Packet(mana(Element.FIRE, F), spec)));
        p.getAttribute(SpellAttributes.POWER).setBaseValue(8);
        ElementalCombatAdapter.apply(
                h.getLevel(),
                null,
                e,
                packet.mana(),
                new Vec3(0, 0, 1),
                F,
                ContactMagic.of(packet.spell()));
        h.assertTrue(e.getHealth() == before - 4, "飞行体使用付款时法强，换装不改旧伤害");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void cancelledDamageRejectsAttachedEffects(GameTestHelper h) {
        var e = pig(h);
        Consumer<LivingIncomingDamageEvent> cancel =
                event -> {
                    if (event.getEntity() == e) event.setCanceled(true);
                };
        NeoForge.EVENT_BUS.addListener(cancel);
        try {
            ElementalCombatAdapter.apply(
                    h.getLevel(),
                    null,
                    e,
                    mana(Element.ARCANE, 8 * F),
                    new Vec3(0, 0, 1),
                    F,
                    hitEffects(F));
        } finally {
            NeoForge.EVENT_BUS.unregister(cancel);
        }
        h.assertTrue(
                e.getHealth() == e.getMaxHealth()
                        && !e.isOnFire()
                        && !e.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN)
                        && e.getDeltaMovement().lengthSqr() == 0,
                "取消命中同时拒绝燃烧减速击退");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void oppositionCannotFundHitModifiers(GameTestHelper h) {
        var e = pig(h);
        ElementalCombatAdapter.apply(
                h.getLevel(),
                null,
                e,
                mana(Element.FIRE, 8 * F).plus(mana(Element.WATER, 8 * F)),
                new Vec3(0, 0, 1),
                F,
                hitEffects(F));
        h.assertTrue(
                e.getHealth() == e.getMaxHealth()
                        && !e.isOnFire()
                        && !e.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN)
                        && e.getDeltaMovement().lengthSqr() == 0,
                "相克抵消先于附加效果分账");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void vanillaInvulnerabilityAlsoGuardsHitModifiers(GameTestHelper h) {
        var e = pig(h);
        ElementalCombatAdapter.apply(
                h.getLevel(), null, e, mana(Element.ARCANE, 2 * F), new Vec3(0, 0, 1), F);
        float health = e.getHealth();
        ElementalCombatAdapter.apply(
                h.getLevel(),
                null,
                e,
                mana(Element.ARCANE, F),
                new Vec3(0, 0, 1),
                F,
                hitEffects(F));
        h.assertTrue(
                e.getHealth() == health
                        && !e.isOnFire()
                        && !e.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN),
                "原版受击间隔阻止更弱连击及其附加效果");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void receiverReceiptIsAtomicWithPlayerData(GameTestHelper h) {
        var p = player(h);
        var pool = PlayerMana.get(p);
        pool.debit(8 * F);
        long before = pool.available();
        String id = UUID.randomUUID() + "/1/2/source";
        long first = RefundReceipts.credit(p.getPersistentData(), id, 4 * F, pool::refund);
        PlayerMana.save(p);
        h.assertTrue(first == 4 * F && pool.available() == before + 4 * F, "返还抵消真实消费");
        PlayerMana.forget(p);
        var restored = PlayerMana.get(p);
        long again = RefundReceipts.credit(p.getPersistentData(), id, 4 * F, restored::refund);
        h.assertTrue(again == first && restored.available() == before + 4 * F, "重复发送和玩家恢复不重复入账");
        var clone = player(h);
        PlayerMana.clone(p, clone);
        long cloneBefore = PlayerMana.get(clone).available();
        RefundReceipts.credit(clone.getPersistentData(), id, 4 * F, PlayerMana.get(clone)::refund);
        h.assertTrue(PlayerMana.get(clone).available() == cloneBefore, "死亡克隆保存来源收据");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void fullSourceKeepsPartialRefund(GameTestHelper h) {
        var pool = new ManaPool(8 * F, 0, 0);
        pool.debit(4 * F);
        pool.setAmount(7 * F);
        var raw = new CompoundTag();
        String id = "partial";
        h.assertTrue(
                RefundReceipts.credit(raw, id, 3 * F, pool::refund) == F
                        && pool.available() == 8 * F,
                "满槽只入账真实空间");
        pool.debit(2 * F);
        h.assertTrue(
                RefundReceipts.credit(raw, id, 3 * F, pool::refund) == 3 * F
                        && pool.available() == 8 * F,
                "后续空位接收余下返还");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void worldRefundClaimsRoundTripWithoutRedirection(GameTestHelper h) {
        var service = new WorldManaRefunds();
        var id = UUID.randomUUID() + "/1/0";
        var origin = new ManaOrigin("player/" + UUID.randomUUID());
        var mana = mana(Element.FIRE, 2 * F).sourced(origin);
        h.assertTrue(
                service.accept(id, mana) && service.accept(id, mana) && service.pending() == 2 * F,
                "凭据重复接受不重复记账");
        var raw = service.save(new CompoundTag(), h.getLevel().registryAccess());
        var restored = WorldManaRefunds.load(raw);
        restored.tick(h.getLevel());
        h.assertTrue(
                restored.pending() == 2 * F
                        && restored.save(new CompoundTag(), h.getLevel().registryAccess())
                                .equals(raw),
                "离线来源保留返还，不转给旁边玩家");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void absorptionMouthsAndConcentration(GameTestHelper h) {
        var level = h.getLevel();
        var data = WorldManaField.get(level);
        var pos = h.absolutePos(new BlockPos(4, 3, 4));
        var cell = data.discover(level, pos);
        data.field().debit(cell, data.field().amount(cell));
        data.field().inject(cell, ServerSettings.mana("field.intakeReference"));
        long one = data.tap(level, pos, 1).available(), three = data.tap(level, pos, 3).available();
        h.assertTrue(three == 3 * one, "三个汲取口具有三倍吞吐额度");
        data.field().debit(cell, data.field().amount(cell) / 2);
        long low = data.tap(level, pos, 3).available();
        h.assertTrue(low <= three / 2 + 1, "浓度减半使吞吐约减半");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void naturalNodeRegenerationPersistence(GameTestHelper h) {
        var rules = ManaFieldResources.defaults();
        var node = new ManaNode(rules, ManaNode.Kind.NATURAL);
        node.emitted(F, 2 * F);
        node.regenerate(F / 2, 5);
        var restored = ManaFieldPersistence.node(ManaFieldPersistence.node(node, rules));
        restored.regenerate(F / 2, 15);
        h.assertTrue(
                restored.remaining() == rules.naturalCapacity() - F / 2
                        && restored.snapshot().regenerated() == F / 2,
                "天然节点余数跨保存恢复继续，累计守恒");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void fixedAndFieldRefundsPersistAtOriginalSources(GameTestHelper h) {
        var level = h.getLevel();
        var p = player(h);
        var relative = new BlockPos(3, 2, 3);
        h.setBlock(relative, ElementalCircuits.CIRCUIT_BLOCK.get().defaultBlockState());
        var block = (CircuitBlockEntity) level.getBlockEntity(h.absolutePos(relative));
        block.placed(new ItemStack(ElementalCircuits.CIRCUIT_ITEM.get()), p);
        WorldCircuits.register(block);
        p.moveTo(Vec3.atCenterOf(block.getBlockPos()));
        block.charge(p);
        block.debit(4 * F);
        long before = block.available();
        var service = new WorldManaRefunds();
        service.accept(
                UUID.randomUUID() + "/1/0",
                mana(Element.FIRE, 2 * F)
                        .sourced(
                                new ManaOrigin(
                                        "fixed/"
                                                + level.dimension().location()
                                                + "/"
                                                + block.assembly().identity())));
        service.tick(level);
        h.assertTrue(
                service.pending() == 0 && block.available() == before + 2 * F, "返还进入原固定阵，净消费同步抵消");
        var clone = new CircuitBlockEntity(block.getBlockPos(), block.getBlockState());
        clone.loadWithComponents(
                block.saveWithFullMetadata(level.registryAccess()), level.registryAccess());
        h.assertTrue(clone.available() == block.available(), "固定阵资源与返还收据一起保存");
        var data = WorldManaField.get(level);
        var pos = h.absolutePos(new BlockPos(5, 2, 5));
        var cell = data.discover(level, pos);
        data.field().debit(cell, Math.min(4 * F, data.field().amount(cell)));
        long fieldBefore = data.field().amount(cell);
        String id = UUID.randomUUID() + "/1/1";
        var origin =
                new ManaOrigin(
                        "field/"
                                + level.dimension().location()
                                + "/"
                                + cell.x()
                                + ","
                                + cell.y()
                                + ","
                                + cell.z());
        service.accept(id, mana(Element.WATER, F).sourced(origin));
        service.tick(level);
        h.assertTrue(
                service.pending() == 0 && data.field().amount(cell) == fieldBefore + F,
                "场返还回到原格点，不能补其他格点");
        var reloaded = WorldManaField.load(data.save(new CompoundTag(), level.registryAccess()));
        long stable = reloaded.field().amount(cell);
        String receipt =
                id
                        + "/"
                        + Base64.getUrlEncoder()
                                .withoutPadding()
                                .encodeToString(
                                        origin.key()
                                                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        reloaded.refund(receipt, cell, F);
        h.assertTrue(reloaded.field().amount(cell) == stable, "场加载后同一收据不能再注入");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void malformedRefundListIsPreserved(GameTestHelper h) {
        var bad = new CompoundTag();
        bad.putInt("Version", 1);
        var strings = new ListTag();
        strings.add(StringTag.valueOf("broken"));
        bad.put("Claims", strings);
        var disabled = WorldManaRefunds.load(bad);
        h.assertTrue(
                disabled.save(new CompoundTag(), h.getLevel().registryAccess()).equals(bad),
                "坏列表原样保留，不能静默恢复为空返还表");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void fixedBudgetAndCreativeWearThroughScheduler(GameTestHelper h) {
        var p = player(h);
        var wand = new ItemStack(ElementalCircuits.WAND.get());
        p.getInventory().setItem(0, wand);
        p.getInventory().selected = 0;
        p.getAbilities().instabuild = false;
        var a = CarrierRepository.load(wand);
        var b =
                AssemblyContracts.line(
                                false,
                                Route.Payload.MANA,
                                Rune.Kind.INPUT,
                                Rune.Kind.CONVERT,
                                Rune.Kind.OUTPUT)
                        .withInputAmount(1, F);
        a.replace(1, b);
        a.circuit(1).start();
        for (int i = 0; i < 5; i++) {
            WorldCircuits.enqueue(wand, p);
            WorldCircuits.tick(h.getLevel());
        }
        h.assertTrue(
                a.circuit(1).snapshot().delivered() == 0
                        && a.circuit(1).snapshot().castingLedger().failures() == 1
                        && wand.getDamageValue() == 0,
                "预算不足失败，不释放也不磨损");
        h.assertTrue(a.circuit(1).snapshot().castingLedger().refunded() > 0, "失败返还凭据交接世界服务");
        p.getAbilities().instabuild = true;
        int wear = wand.getDamageValue();
        WandEquipment.castingWear(wand, p, 64 * F);
        h.assertTrue(wand.getDamageValue() == wear, "创造模式即便超过强度阈值也不磨损");
        h.succeed();
    }
}
