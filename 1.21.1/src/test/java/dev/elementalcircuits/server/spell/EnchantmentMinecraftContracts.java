package dev.elementalcircuits.server.spell;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.config.ServerSettings;
import dev.elementalcircuits.adapter.persistence.*;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.circuit.*;
import dev.elementalcircuits.domain.combat.DamageAffinity;
import dev.elementalcircuits.server.casting.*;
import dev.elementalcircuits.server.enchantment.MagicEnchantments;
import dev.elementalcircuits.server.equipment.*;
import dev.elementalcircuits.server.mana.PlayerMana;

import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.neoforged.neoforge.gametest.*;

import top.theillusivec4.curios.api.CuriosApi;

import java.util.*;

@GameTestHolder(ElementalCircuits.ID)
@PrefixGameTestTemplate(false)
public final class EnchantmentMinecraftContracts {
    private static final long F = Fixed.ONE;

    private static ServerPlayer player(GameTestHelper h) {
        var p =
                net.neoforged.neoforge.common.util.FakePlayerFactory.get(
                        h.getLevel(),
                        new com.mojang.authlib.GameProfile(UUID.randomUUID(), "enchant-test"));
        p.getAbilities().instabuild = false;
        p.getAbilities().invulnerable = false;
        return p;
    }

    private static net.minecraft.core.Holder.Reference<Enchantment> enchant(
            GameTestHelper h, String name) {
        return h.getLevel()
                .registryAccess()
                .registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(
                        ResourceKey.create(
                                Registries.ENCHANTMENT,
                                ResourceLocation.fromNamespaceAndPath(ElementalCircuits.ID, name)));
    }

    @GameTest(template = "empty")
    public static void nativeBooksTableAndAnvil(GameTestHelper h) {
        var p = player(h);
        p.experienceLevel = 100;
        var wand = new ItemStack(ElementalCircuits.WAND.get());
        var capacity = enchant(h, "mana_capacity");
        h.assertTrue(capacity.value().canEnchant(wand) && wand.isEnchantable(), "法杖进入原版附魔规则");
        h.assertTrue(
                new ItemStack(ElementalCircuits.SPELL_BOOK.get()).isEnchantable()
                        && new ItemStack(WizardEquipment.RING.get()).isEnchantable(),
                "无耐久书和戒指也可使用附魔台");
        var possible =
                EnchantmentHelper.getAvailableEnchantmentResults(
                        40,
                        wand,
                        h.getLevel()
                                .registryAccess()
                                .registryOrThrow(Registries.ENCHANTMENT)
                                .holders()
                                .map(e -> (net.minecraft.core.Holder<Enchantment>) e));
        h.assertTrue(
                possible.stream().anyMatch(e -> e.enchantment.equals(capacity)), "数据驱动附魔可被附魔台选中");
        var menu = new net.minecraft.world.inventory.AnvilMenu(1, p.getInventory());
        menu.getSlot(0).set(wand);
        menu.getSlot(1)
                .set(EnchantedBookItem.createForEnchantment(new EnchantmentInstance(capacity, 3)));
        menu.createResult();
        var result = menu.getSlot(2).getItem();
        h.assertTrue(
                !result.isEmpty()
                        && MagicEnchantments.level(h.getLevel(), result, "mana_capacity") == 3,
                "真实铁砧接受附魔书");
        var guard = enchant(h, "ward_fire");
        h.assertTrue(!Enchantment.areCompatible(guard, enchant(h, "ward_water")), "同件装备的元素庇护互斥");
        h.assertTrue(
                !Enchantment.areCompatible(
                        enchant(h, "mana_mending"),
                        h.getLevel()
                                .registryAccess()
                                .registryOrThrow(Registries.ENCHANTMENT)
                                .getHolderOrThrow(Enchantments.MENDING)),
                "魔力维修与经验修补互斥");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeAttributesAndCuriosIdentities(GameTestHelper h) {
        var p = player(h);
        var ring = new ItemStack(WizardEquipment.RING.get());
        ring.enchant(enchant(h, "mana_capacity"), 3);
        ring.enchant(enchant(h, "mana_recovery"), 3);
        ring.enchant(enchant(h, "spell_power"), 3);
        var item = (dev.elementalcircuits.item.CircuitRingItem) ring.getItem();
        var context = new top.theillusivec4.curios.api.SlotContext("ring", p, 0, false, true);
        var modifiers =
                item.getAttributeModifiers(context, ResourceLocation.parse("curios:ring0"), ring);
        var second =
                item.getAttributeModifiers(context, ResourceLocation.parse("curios:ring1"), ring);
        h.assertTrue(modifiers.size() == 3 && second.size() == 3, "Curios解析原版附魔属性");
        h.assertTrue(
                Collections.disjoint(
                        modifiers.values().stream().map(v -> v.id()).toList(),
                        second.values().stream().map(v -> v.id()).toList()),
                "双戒指属性身份独立");
        modifiers.forEach(
                (attribute, modifier) -> p.getAttribute(attribute).addTransientModifier(modifier));
        var pool = PlayerMana.get(p);
        h.assertTrue(pool.capacity() == 160 * F && pool.available() == 64 * F, "容量增加96且不补魔");
        h.assertTrue(
                Math.abs(p.getAttributeValue(SpellAttributes.MANA_REGENERATION) - 1.5) < .00001
                        && Math.abs(p.getAttributeValue(SpellAttributes.POWER) - 1.3) < .00001,
                "回复与法强使用原版属性");
        modifiers.forEach(
                (attribute, modifier) -> p.getAttribute(attribute).removeModifier(modifier.id()));
        h.assertTrue(PlayerMana.get(p).capacity() == 64 * F, "脱装不写永久容量");
        var saved = ring.save(h.getLevel().registryAccess(), new net.minecraft.nbt.CompoundTag());
        h.assertTrue(
                MagicEnchantments.level(
                                h.getLevel(),
                                ItemStack.parseOptional(
                                        h.getLevel().registryAccess(),
                                        (net.minecraft.nbt.CompoundTag) saved),
                                "spell_power")
                        == 3,
                "原生物品保存附魔");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void releaseSpeedAndBoardSafety(GameTestHelper h) {
        var p = player(h);
        var ordinary = new ItemStack(ElementalCircuits.WAND.get());
        var swift = ordinary.copy();
        swift.enchant(enchant(h, "swift_casting"), 3);
        CastingEquipment.release(ordinary, p);
        CastingEquipment.release(swift, p);
        h.assertTrue(
                WandCooldown.state(swift).remaining() < WandCooldown.state(ordinary).remaining(),
                "迅咒缩短实际释放冷却");
        h.assertTrue(MagicEnchantments.speed(h.getLevel(), swift, F) > F, "同一速度用于法阵复杂度延迟");
        swift.enchant(enchant(h, "board_preservation"), 3);
        var policy = ServerSettings.boardSafety();
        var fresh = policy.fresh(Rules.Material.WOOD, 0);
        var normal = policy.assess(fresh, Rules.Material.WOOD, 256 * F, 4 * F, false);
        var preserved =
                policy.assess(
                        fresh,
                        Rules.Material.WOOD,
                        256 * F,
                        4 * F,
                        false,
                        MagicEnchantments.boardWear(h.getLevel(), swift));
        h.assertTrue(preserved.condition().spent() < normal.condition().spent(), "护板减少正常和过载磨损");
        h.assertTrue(
                policy.assess(fresh, Rules.Material.WOOD, 512 * F, 4 * F, false, .1).meltdown(),
                "护板不抬高熔毁阈值");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void elementalMixedProtectionAndVanillaHit(GameTestHelper h) {
        var target = h.spawn(EntityType.COW, new net.minecraft.core.BlockPos(2, 2, 2));
        target.setNoAi(true);
        var helmet = new ItemStack(Items.LEATHER_HELMET);
        helmet.enchant(enchant(h, "ward_fire"), 4);
        target.setItemSlot(EquipmentSlot.HEAD, helmet);
        var registry = h.getLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
        var mixed =
                new ElementalDamageSource(
                        registry.getHolderOrThrow(SpellDamage.type(DamageAffinity.MIXED)),
                        null,
                        null,
                        Map.of(DamageAffinity.FIRE, 8 * F, DamageAffinity.WATER, 2 * F));
        h.assertTrue(
                Math.abs(MagicEnchantments.protectedDamage(target, mixed, 10) - 8.72) < .0001,
                "混合命中按实际元素伤害占比减伤");
        var water = SpellDamage.source(h.getLevel(), null, DamageAffinity.WATER);
        h.assertTrue(MagicEnchantments.protectedDamage(target, water, 10) == 10, "火防护不抵御水");
        helmet.enchant(enchant(h, "ward_fire"), 255);
        h.assertTrue(
                Math.abs(MagicEnchantments.protectedDamage(target, mixed, 10) - 3.6) < .0001,
                "超等级保护仍遵守80%上限");
        target.hurt(mixed, 2);
        float health = target.getHealth();
        target.hurt(mixed, 2);
        h.assertTrue(
                health < 20 && target.getHealth() == health && target.invulnerableTime > 0,
                "真实伤害事件保留原版受击间隔");
        h.succeed();
    }

    private static void board(ItemStack stack, long spent, boolean melted) {
        var json =
                com.google.gson.JsonParser.parseString(
                                dev.elementalcircuits.adapter.codec.JsonCodecs.assembly(
                                        CarrierRepository.load(stack)))
                        .getAsJsonObject();
        var b = json.getAsJsonArray("slots").get(0).getAsJsonObject().getAsJsonObject("board");
        b.addProperty("spent", melted ? b.get("maximum").getAsLong() : spent);
        b.addProperty("melted", melted);
        var tag = ItemData.read(stack).getCompound("ElementalCircuit");
        tag.put("Assembly", JsonNbt.encode(json.toString(), 67108864));
        ItemData.put(stack, "ElementalCircuit", tag);
    }

    @GameTest(template = "empty")
    public static void mendingPaymentFractionalBoardsAndNoRefill(GameTestHelper h) {
        var p = player(h);
        var wand = new ItemStack(ElementalCircuits.WAND.get());
        wand.enchant(enchant(h, "mana_mending"), 2);
        p.setItemSlot(EquipmentSlot.MAINHAND, wand);
        board(wand, F / 2, false);
        wand.setDamageValue(1);
        long available = PlayerMana.get(p).available();
        MagicEnchantments.repair(p);
        h.assertTrue(
                wand.getDamageValue() == 0 && CarrierRepository.load(wand).board(1).spent() == 0,
                "先修杖体，再修基板的小数磨损");
        h.assertTrue(PlayerMana.get(p).available() == available - 6 * F, "修复1.5耐久实际扣6魔力");
        board(wand, F, false);
        PlayerMana.get(p).setAmount(F);
        MagicEnchantments.repair(p);
        h.assertTrue(
                CarrierRepository.load(wand).board(1).spent() == 3 * F / 4
                        && PlayerMana.get(p).available() == 0,
                "不足整点维修也可支付部分基板损耗，无透支");
        long wear = CarrierRepository.load(wand).board(1).spent();
        MagicEnchantments.repair(p);
        h.assertTrue(CarrierRepository.load(wand).board(1).spent() == wear, "无魔力不免费维修");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void mendingEquipmentScopeAndMeltdown(GameTestHelper h) {
        var p = player(h);
        var ring = new ItemStack(WizardEquipment.RING.get());
        ring.enchant(enchant(h, "mana_mending"), 1);
        var a = CarrierRepository.load(ring);
        board(ring, a.board(1).maximum(), false);
        var slots =
                CuriosApi.getCuriosInventory(p)
                        .orElseThrow()
                        .getStacksHandler("ring")
                        .orElseThrow()
                        .getStacks();
        slots.setStackInSlot(0, ring);
        MagicEnchantments.repair(p);
        h.assertTrue(
                !CarrierRepository.load(ring).boardBroken(1)
                        && !CarrierRepository.load(ring).circuit(1).powered(),
                "耗尽基板可在饰品槽维修，但不会自动起阵");
        board(ring, 0, true);
        long mana = PlayerMana.get(p).available();
        MagicEnchantments.repair(p);
        h.assertTrue(
                CarrierRepository.load(ring).board(1).melted()
                        && PlayerMana.get(p).available() == mana,
                "熔毁不能用附魔修复，也不扣魔");
        var sword = new ItemStack(Items.IRON_SWORD);
        sword.enchant(enchant(h, "mana_mending"), 1);
        sword.setDamageValue(10);
        p.getInventory().setItem(10, sword);
        MagicEnchantments.repair(p);
        h.assertTrue(sword.getDamageValue() == 10, "背包闲置装备不自动维修");
        p.setItemSlot(EquipmentSlot.OFFHAND, sword);
        MagicEnchantments.repair(p);
        h.assertTrue(
                sword.getDamageValue() == 9 && PlayerMana.get(p).available() == mana - 4 * F,
                "副手原版耐久物品正常支付维修");
        h.succeed();
    }
}
