package dev.elementalcircuits.server.enchantment;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.compat.CuriosEquipment;
import dev.elementalcircuits.adapter.config.ServerSettings;
import dev.elementalcircuits.adapter.persistence.CarrierRepository;
import dev.elementalcircuits.adapter.persistence.ItemData;
import dev.elementalcircuits.domain.Fixed;
import dev.elementalcircuits.domain.combat.DamageAffinity;
import dev.elementalcircuits.server.circuit.CarrierFaults;
import dev.elementalcircuits.server.equipment.WizardEquipment;
import dev.elementalcircuits.server.mana.PlayerMana;
import dev.elementalcircuits.server.spell.ElementalDamageSource;
import dev.elementalcircuits.server.spell.SpellDamage;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.level.*;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.math.BigInteger;
import java.util.*;

/** Native data-driven enchantments; custom adapters only for circuit-specific behavior. */
public final class MagicEnchantments {
    public static int level(ServerLevel world, ItemStack stack, String name) {
        if (stack.isEmpty()) return 0;
        var enchantment =
                world.registryAccess()
                        .registryOrThrow(Registries.ENCHANTMENT)
                        .getHolder(
                                ResourceKey.create(
                                        Registries.ENCHANTMENT,
                                        ResourceLocation.fromNamespaceAndPath(
                                                ElementalCircuits.ID, name)));
        return enchantment
                .map(e -> Math.min(255, EnchantmentHelper.getItemEnchantmentLevel(e, stack)))
                .orElse(0);
    }

    public static long speed(ServerLevel level, ItemStack stack, long base) {
        return Math.clamp(
                Math.round(
                        base
                                * (1
                                        + level(level, stack, "swift_casting")
                                                * ServerSettings.get("enchant.speedPerLevel"))),
                Fixed.ONE / 4,
                2 * Fixed.ONE);
    }

    public static double boardWear(ServerLevel level, ItemStack stack) {
        return Math.max(
                .1,
                1
                        - level(level, stack, "board_preservation")
                                * ServerSettings.get("enchant.boardReductionPerLevel"));
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(MagicEnchantments::damage);
        NeoForge.EVENT_BUS.addListener(MagicEnchantments::tick);
    }

    private static List<ItemStack> equipped(LivingEntity target) {
        var result = new ArrayList<ItemStack>();
        var seen = Collections.newSetFromMap(new IdentityHashMap<ItemStack, Boolean>());
        for (var slot : EquipmentSlot.values()) {
            var stack = target.getItemBySlot(slot);
            if (!stack.isEmpty() && seen.add(stack)) result.add(stack);
        }
        for (var stack : CuriosEquipment.enchantedAccessories(target))
            if (seen.add(stack)) result.add(stack);
        return result;
    }

    public static double reduction(LivingEntity target, DamageAffinity affinity) {
        if (!(target.level() instanceof ServerLevel level) || affinity == DamageAffinity.MIXED)
            return 0;
        int ranks = 0;
        for (var stack : equipped(target)) {
            if (stack == target.getMainHandItem() || stack == target.getOffhandItem()) continue;
            ranks += level(level, stack, "ward_" + affinity.name().toLowerCase(Locale.ROOT));
        }
        return Math.min(
                ServerSettings.get("enchant.guardMaximum"),
                ranks * ServerSettings.get("enchant.guardPerLevel"));
    }

    public static float protectedDamage(LivingEntity target, DamageSource source, float amount) {
        if (amount <= 0 || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return amount;
        if (source instanceof ElementalDamageSource elemental) {
            double total = 0, surviving = 0;
            for (var entry : elemental.components().entrySet()) {
                total += entry.getValue();
                surviving += entry.getValue() * (1 - reduction(target, entry.getKey()));
            }
            return total == 0 ? amount : (float) (amount * surviving / total);
        }
        for (var affinity : DamageAffinity.values())
            if (affinity != DamageAffinity.MIXED && source.is(SpellDamage.type(affinity)))
                return (float) (amount * (1 - reduction(target, affinity)));
        return amount;
    }

    private static void damage(LivingIncomingDamageEvent event) {
        event.setAmount(protectedDamage(event.getEntity(), event.getSource(), event.getAmount()));
    }

    private static void tick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player
                && player.tickCount % 20 == 0
                && player.isAlive()) repair(player);
    }

    public static void repair(ServerPlayer player) {
        try {
            PlayerMana.get(player);
        } catch (RuntimeException corruptSource) {
            // The normal mana tick reports the damaged source. Do not mark its items as corrupt.
            return;
        }
        for (var stack : equipped(player)) {
            try {
                repairStack(player, stack);
            } catch (RuntimeException malformed) {
                CarrierFaults.report(stack, player);
            }
        }
        PlayerMana.save(player);
    }

    private static void repairStack(ServerPlayer player, ItemStack stack) {
        int ranks = level(player.serverLevel(), stack, "mana_mending");
        if (ranks == 0) return;
        long remaining =
                Math.min(4096, ranks * ServerSettings.count("enchant.repairPerLevel")) * Fixed.ONE;
        long price = ServerSettings.mana("enchant.repairManaPerPoint");
        var pool = PlayerMana.get(player);
        int itemPoints =
                stack.isDamageableItem()
                        ? (int)
                                Math.min(
                                        stack.getDamageValue(),
                                        Math.min(remaining / Fixed.ONE, pool.available() / price))
                        : 0;
        if (itemPoints > 0 && pool.debit(itemPoints * price)) {
            stack.setDamageValue(stack.getDamageValue() - itemPoints);
            remaining -= itemPoints * Fixed.ONE;
        }
        if (remaining == 0 || !ItemData.read(stack).contains("ElementalCircuit")) return;
        if (!(stack.is(ElementalCircuits.WAND.get())
                || stack.is(ElementalCircuits.SPELL_BOOK.get())
                || WizardEquipment.carrier(stack))) return;
        var assembly = CarrierRepository.load(stack);
        boolean changed = false;
        for (int slot : assembly.slots()) {
            var board = assembly.board(slot);
            if (board == null || board.melted() || board.spent() == 0) continue;
            long affordable =
                    BigInteger.valueOf(pool.available())
                            .multiply(BigInteger.valueOf(Fixed.ONE))
                            .divide(BigInteger.valueOf(price))
                            .min(BigInteger.valueOf(remaining))
                            .longValueExact();
            long fix = Math.min(board.spent(), affordable);
            if (fix == 0) break;
            long cost =
                    BigInteger.valueOf(fix)
                            .multiply(BigInteger.valueOf(price))
                            .add(BigInteger.valueOf(Fixed.ONE - 1))
                            .divide(BigInteger.valueOf(Fixed.ONE))
                            .longValueExact();
            if (!pool.debit(cost)) break;
            assembly.repairBoard(slot, fix);
            changed = true;
            remaining -= fix;
            if (remaining == 0) break;
        }
        if (changed) CarrierRepository.commit(stack);
    }

    public static void creative(
            CreativeModeTab.ItemDisplayParameters params, CreativeModeTab.Output output) {
        var registry = params.holders().lookupOrThrow(Registries.ENCHANTMENT);
        for (String name :
                List.of(
                        "mana_capacity",
                        "mana_recovery",
                        "swift_casting",
                        "board_preservation",
                        "spell_power",
                        "mana_mending",
                        "ward_earth",
                        "ward_water",
                        "ward_fire",
                        "ward_wind",
                        "ward_lightning",
                        "ward_light",
                        "ward_shadow",
                        "ward_arcane",
                        "ward_frost")) {
            var holder =
                    registry.getOrThrow(
                            ResourceKey.create(
                                    Registries.ENCHANTMENT,
                                    ResourceLocation.fromNamespaceAndPath(
                                            ElementalCircuits.ID, name)));
            output.accept(
                    EnchantedBookItem.createForEnchantment(
                            new EnchantmentInstance(holder, holder.value().getMaxLevel())));
        }
    }

    private MagicEnchantments() {}
}
