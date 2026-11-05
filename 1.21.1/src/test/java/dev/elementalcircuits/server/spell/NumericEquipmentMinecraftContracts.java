package dev.elementalcircuits.server.spell;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.codec.JsonCodecs;
import dev.elementalcircuits.adapter.compat.CuriosEquipment;
import dev.elementalcircuits.adapter.config.ServerSettings;
import dev.elementalcircuits.domain.Fixed;
import dev.elementalcircuits.domain.casting.*;
import dev.elementalcircuits.domain.circuit.*;
import dev.elementalcircuits.domain.combat.Element;
import dev.elementalcircuits.domain.examples.StandardCircuit;
import dev.elementalcircuits.domain.mana.*;
import dev.elementalcircuits.server.equipment.*;
import dev.elementalcircuits.server.mana.*;

import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;

import java.util.*;

@GameTestHolder(ElementalCircuits.ID)
@PrefixGameTestTemplate(false)
public final class NumericEquipmentMinecraftContracts {
    private static final long F = Fixed.ONE;

    private static ServerPlayer player(GameTestHelper h) {
        return net.neoforged.neoforge.common.util.FakePlayerFactory.get(
                h.getLevel(),
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "numeric-test"));
    }

    @GameTest(template = "empty")
    public static void monocleHasTwoFunctionalSlotsAndCosmeticIsVisualOnly(GameTestHelper h) {
        var p = player(h);
        var item = new ItemStack(WizardEquipment.MONOCLE.get());
        h.assertTrue(!CuriosEquipment.monocleWorn(p), "背包或未佩戴不生效");
        p.setItemSlot(EquipmentSlot.HEAD, item);
        h.assertTrue(CuriosEquipment.monocleWorn(p), "原版头盔槽生效");
        p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(WizardEquipment.HAT.get()));
        var head =
                top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(p)
                        .orElseThrow()
                        .getStacksHandler("head")
                        .orElseThrow();
        h.assertTrue(head.getStacks().getSlots() >= 1, "玩家实际拥有头部饰品槽");
        head.getStacks().setStackInSlot(0, item);
        h.assertTrue(
                CuriosEquipment.monocleWorn(p)
                        && p.getItemBySlot(EquipmentSlot.HEAD).is(WizardEquipment.HAT.get()),
                "单片镜可与巫师帽共存");
        h.assertTrue(
                item.is(
                        net.minecraft.tags.TagKey.create(
                                net.minecraft.core.registries.Registries.ITEM,
                                net.minecraft.resources.ResourceLocation.parse("curios:head"))),
                "饰品标签允许原生槽位装入");
        head.getStacks().setStackInSlot(0, ItemStack.EMPTY);
        head.getCosmeticStacks().setStackInSlot(0, item);
        h.assertTrue(!CuriosEquipment.monocleWorn(p), "装饰槽不授予观测能力");
        var enchantments =
                h.getLevel()
                        .registryAccess()
                        .registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
        var ward =
                enchantments.getHolderOrThrow(
                        net.minecraft.resources.ResourceKey.create(
                                net.minecraft.core.registries.Registries.ENCHANTMENT,
                                net.minecraft.resources.ResourceLocation.parse(
                                        "elementalcircuits:ward_fire")));
        h.assertTrue(
                ward.value().canEnchant(new ItemStack(WizardEquipment.ROBE.get()))
                        && ward.value().canEnchant(item),
                "法袍与观阵镜可以正常接受元素防护附魔");
        item.enchant(ward, 4);
        head.getCosmeticStacks().setStackInSlot(0, ItemStack.EMPTY);
        head.getStacks().setStackInSlot(0, item);
        h.assertTrue(
                Math.abs(
                                dev.elementalcircuits.server.enchantment.MagicEnchantments
                                                .reduction(
                                                        p,
                                                        dev.elementalcircuits.domain.combat
                                                                .DamageAffinity.FIRE)
                                        - .16)
                        < .00001,
                "头部饰品防护按实际佩戴计入一次");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void playerAttributesAreFiniteAndConsumableFullStateReloads(GameTestHelper h) {
        var p = player(h);
        for (var attribute :
                List.of(
                        SpellAttributes.POWER,
                        SpellAttributes.MANA_COST,
                        SpellAttributes.MANA_REDUCTION,
                        SpellAttributes.MANA_REGENERATION,
                        SpellAttributes.MANA_CAPACITY))
            p.getAttribute(attribute).setBaseValue(Double.NaN);
        var context = SpellAttributes.context(p);
        h.assertTrue(
                context.damageMultiplier() == F && context.manaMultiplier() == F,
                "非有限属性不会产生免费施法或非法强度");
        var pool = PlayerMana.get(p);
        h.assertTrue(pool.capacity() == 64 * F, "异常容量加成不改变永久容量");
        pool.debit(4 * F);
        InstantMana.restore(p, 4 * F);
        PlayerMana.forget(p);
        h.assertTrue(PlayerMana.get(p).available() == 64 * F, "瞬间补满后真实玩家保存可恢复");
        var raw = p.getPersistentData().getString("elementalcircuits_mana");
        p.getPersistentData().putString("elementalcircuits_mana", "corrupt");
        PlayerMana.forget(p);
        dev.elementalcircuits.server.enchantment.MagicEnchantments.repair(p);
        h.assertTrue(
                p.getPersistentData().getString("elementalcircuits_mana").equals("corrupt"),
                "坏玩家槽不被附魔计时器覆盖或抛出到世界循环");
        p.getPersistentData().putString("elementalcircuits_mana", raw);
        PlayerMana.forget(p);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void castingWearUsesActualFastReleaseCadence(GameTestHelper h) {
        var p = player(h);
        var normal = new ItemStack(ElementalCircuits.WAND.get());
        var fast = normal.copy();
        var timing = new net.minecraft.nbt.CompoundTag();
        timing.putLong("Tick", p.server.overworld().getGameTime());
        timing.putLong("Fraction", 0);
        dev.elementalcircuits.adapter.persistence.ItemData.put(fast, "CastWear", timing);
        WandEquipment.castingWear(normal, p, F);
        WandEquipment.castingWear(fast, p, F);
        long normalWear =
                normal.getDamageValue() * F
                        + dev.elementalcircuits.adapter.persistence.ItemData.read(normal)
                                .getCompound("CastWear")
                                .getLong("Fraction");
        long fastWear =
                fast.getDamageValue() * F
                        + dev.elementalcircuits.adapter.persistence.ItemData.read(fast)
                                .getCompound("CastWear")
                                .getLong("Fraction");
        h.assertTrue(fastWear > normalWear, "同一拍多次释放按实际频率加剧磨损");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void discountedCastPaysLessWithoutLosingDamage(GameTestHelper h) {
        var p = player(h);
        p.getAttribute(SpellAttributes.MANA_COST).setBaseValue(.2);
        p.getAttribute(SpellAttributes.MANA_REDUCTION).setBaseValue(80);
        var vm =
                new TickCircuit(
                        StandardCircuit.create(ServerSettings.circuitRules(), false)
                                .withInputAmount(1, 32 * F));
        vm.start(SupplyMode.PULSE, F);
        var casts = new ArrayList<MagicCircuit.Cast>();
        for (int i = 0; i < 40; i++)
            casts.addAll(
                    vm.tick(
                            PlayerMana.get(p),
                            ReleasePermit.UNLIMITED,
                            ManaSource.NONE,
                            SpellAttributes.context(p),
                            ShapeRules.protocolV1()));
        h.assertTrue(casts.size() == 1, "高减免可真实施法，不再支取越界");
        var cast = casts.getFirst();
        h.assertTrue(
                cast.mana().total() < F
                        && cast.spell().effectScale().apply(cast.mana()).total() == F,
                "已付魔力小于名义效果且折算准确");
        var target = h.spawn(EntityType.PIG, new BlockPos(2, 2, 2));
        target.setNoAi(true);
        var saved =
                JsonCodecs.readOrderedPacket(
                        JsonCodecs.packetText(new Packet(cast.mana(), cast.spell())));
        ElementalCombatAdapter.apply(
                h.getLevel(),
                null,
                target,
                saved.mana(),
                new Vec3(0, 0, 1),
                F,
                ContactMagic.of(saved.spell()));
        h.assertTrue(Math.abs(target.getHealth() - 8) < .0001, "减免后仍按一单位火元素造成两点伤害");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void discountedWorldInteractionAndFlightPersistence(GameTestHelper h) {
        var solid = h.absolutePos(new BlockPos(2, 1, 3));
        h.getLevel().setBlockAndUpdate(solid, Blocks.STONE.defaultBlockState());
        var water = Mixture.pure(F).convert(Element.WATER, F);
        var spell =
                new SpellSpec(SpellSpec.Method.DIRECTED, SpellSpec.Shape.BEAM, 4 * F, 4, 0, 0)
                        .withEffectScale(new EffectScale(4, 1));
        var hit = new BlockHitResult(Vec3.atCenterOf(solid), Direction.NORTH, solid, false);
        ElementInteractions.beam(
                h.getLevel(),
                null,
                Vec3.atCenterOf(solid.north(2)),
                new SpellEffects.Trace(hit.getLocation(), null, hit, true),
                water,
                F,
                ContactMagic.of(spell));
        h.assertTrue(h.getLevel().getBlockState(solid.north()).is(Blocks.WATER), "折扣不会削弱水源创造效果");
        var fragments = new ProjectileEffects(0, 0, 0, 3, 0, 0, 0, 0);
        var fragmentSpec =
                new SpellSpec(
                                SpellSpec.Method.DIRECTED,
                                SpellSpec.Shape.BEAM,
                                F,
                                8,
                                0,
                                0,
                                fragments)
                        .withEffectScale(new EffectScale(4, 1));
        var spawned =
                SecondarySpells.contact(
                        h.getLevel(),
                        null,
                        hit.getLocation(),
                        new Vec3(0, 0, 1),
                        8,
                        new SpellEffects.Trace(hit.getLocation(), null, hit, true),
                        new Packet(water, fragmentSpec),
                        new DerivationState(true, 0, List.of()),
                        SecondaryRules.protocolV1());
        h.assertTrue(
                spawned.children().size() == 3
                        && spawned.children().stream()
                                .allMatch(
                                        c ->
                                                c.packet()
                                                        .spell()
                                                        .effectScale()
                                                        .equals(fragmentSpec.effectScale())),
                "命中分裂的子投射物保留折扣效果比");
        h.assertTrue(
                spawned.children().stream().mapToLong(c -> c.packet().mana().total()).sum()
                                + spawned.primary().mana().total()
                        <= water.total(),
                "分裂不复制实际魔力");
        var world = new WorldSpells();
        var projectile =
                new SpellSpec(SpellSpec.Method.DIRECTED, SpellSpec.Shape.SPHERE, F, 8, 1, 0)
                        .withEffectScale(new EffectScale(5, 1));
        world.launch(
                null,
                h.absoluteVec(new Vec3(1, 3, 1)),
                new Vec3(0, 0, 1),
                8,
                1,
                new Packet(water, projectile));
        var nbt = world.save(new net.minecraft.nbt.CompoundTag(), h.getLevel().registryAccess());
        h.assertTrue(nbt.getInt("Version") == 12, "输出效率使用世界法术协议12");
        var restored = WorldSpells.load(nbt.copy());
        h.assertTrue(
                restored.save(new net.minecraft.nbt.CompoundTag(), h.getLevel().registryAccess())
                        .equals(nbt),
                "折扣投射物完整恢复");
        restored.tick(h.getLevel());
        h.assertTrue(
                !restored.save(new net.minecraft.nbt.CompoundTag(), h.getLevel().registryAccess())
                        .equals(nbt),
                "合法折扣投射物确实继续推进");
        nbt.putInt("Version", 11);
        var rejected = WorldSpells.load(nbt);
        rejected.tick(h.getLevel());
        h.assertTrue(
                rejected.save(new net.minecraft.nbt.CompoundTag(), h.getLevel().registryAccess())
                        .equals(nbt),
                "旧世界法术协议拒绝伪装新效率字段并保留原始记录");
        h.succeed();
    }
}
