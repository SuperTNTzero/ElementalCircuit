package dev.elementalcircuits.server.equipment;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.config.ServerSettings;
import dev.elementalcircuits.adapter.persistence.CarrierRepository;
import dev.elementalcircuits.adapter.persistence.ItemData;
import dev.elementalcircuits.adapter.resources.CooldownResources;
import dev.elementalcircuits.adapter.resources.WandResources;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.equipment.WandParts;
import dev.elementalcircuits.domain.equipment.WandWear;

import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Authoritative component installation. Wear never resets on replacement. */
public final class WandEquipment {
    private static final String KEY = "WandAssembly";

    private WandEquipment() {}

    public static WandParts.Assembly assembly(ItemStack stack) {
        if (!ItemData.contains(stack, KEY))
            return WandResources.definitions().basic(); // explicit legacy defaults, no free items
        CompoundTag t = ItemData.section(stack, KEY);
        if (t.getInt("Version") != 1 || !t.contains("Remainder", Tag.TAG_LONG))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.server.equipment._wand_equipment.001"));
        WandParts.Assembly a =
                new WandParts.Assembly(
                        t.getString("Head"), t.getString("Body"), t.getString("Tail"));
        a.stats(WandResources.definitions());
        // v1 cadence remainder is validated for compatibility; it no longer changes VM speed.
        long r = t.getLong("Remainder");
        if (r < 0 || r >= Fixed.ONE)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.server.equipment._wand_equipment.002"));
        return a;
    }

    public static WandParts.Stats stats(ItemStack stack) {
        return assembly(stack).stats(WandResources.definitions());
    }

    private static void write(ItemStack stack, WandParts.Assembly a, long remainder) {
        CompoundTag t = new CompoundTag();
        t.putInt("Version", 1);
        t.putString("Head", a.head());
        t.putString("Body", a.body());
        t.putString("Tail", a.tail());
        t.putLong("Remainder", remainder);
        ItemData.put(stack, KEY, t);
    }

    public static boolean broken(ItemStack stack) {
        return stack.getDamageValue() >= stats(stack).durability();
    }

    public static void wear(ItemStack stack) {
        if (!broken(stack)) stack.setDamageValue(stack.getDamageValue() + 1);
    }

    /** Creative casting and melee share the same wear policy; fixed devices have no item wear. */
    public static void wear(ItemStack stack, net.minecraft.world.entity.LivingEntity user) {
        if (user instanceof net.minecraft.world.entity.player.Player player
                && player.getAbilities().instabuild) return;
        wear(stack);
    }

    public static void castingWear(ItemStack stack, ServerPlayer player, long effect) {
        long now = player.server.overworld().getGameTime();
        var data = ItemData.section(stack, "CastWear");
        int interval =
                CooldownResources.rules()
                        .ticks(
                                dev.elementalcircuits.server.enchantment.MagicEnchantments.speed(
                                        player.serverLevel(), stack, stats(stack).speed()));
        if (data.contains("Tick", Tag.TAG_LONG)) {
            long last = data.getLong("Tick");
            if (last >= 0 && now >= last)
                interval = (int) Math.max(1, Math.min(Integer.MAX_VALUE, now - last));
        }
        String head = assembly(stack).head().replace("_head", "");
        int cost =
                WandWear.cost(
                        effect,
                        stats(stack).strength(),
                        interval,
                        ServerSettings.get("wear." + head + "Intensity"),
                        ServerSettings.get("wear." + head + "Frequency"),
                        ServerSettings.get("wear.intensityCoefficient"),
                        ServerSettings.get("wear.speedCoefficient"));
        var tag = new CompoundTag();
        tag.putLong("Tick", now);
        long remainder = data.getLong("Fraction");
        if (remainder < 0 || remainder >= Fixed.ONE)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.server.equipment._wand_equipment.003"));
        tag.putLong("Fraction", remainder);
        if (!player.getAbilities().instabuild) {
            long wear =
                    Math.min(
                                    Integer.MAX_VALUE * Fixed.ONE,
                                    Math.round(
                                            cost
                                                    * ServerSettings.get("board.wandWearScale")
                                                    * Fixed.ONE))
                            + remainder;
            tag.putLong("Fraction", wear % Fixed.ONE);
            stack.setDamageValue(
                    (int)
                            Math.min(
                                    stats(stack).durability(),
                                    (long) stack.getDamageValue() + wear / Fixed.ONE));
        }
        ItemData.put(stack, "CastWear", tag);
    }

    public static void repair(ServerPlayer player, ItemStack wand) {
        if (wand.getDamageValue() == 0)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.server.equipment._wand_equipment.004"));
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack material = player.getInventory().getItem(i);
            if (material.is(ElementalCircuits.WOOD_PLATE.get())) {
                material.shrink(1);
                wand.setDamageValue(Math.max(0, wand.getDamageValue() - 48));
                return;
            }
        }
        throw new IllegalArgumentException(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.server.equipment._wand_equipment.005"));
    }

    public static void install(ServerPlayer player, ItemStack wand, String id) {
        if (CarrierRepository.load(wand).powered())
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.server.equipment._wand_equipment.006"));
        WandParts.Part next = WandResources.definitions().get(id);
        WandParts.Assembly current = assembly(wand);
        if (current.at(next.slot()).equals(id))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.server.equipment._wand_equipment.007"));
        int slot = -1;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++)
            if (player.getInventory().getItem(i).is(ElementalCircuits.PARTS.get(id).get())) {
                slot = i;
                break;
            }
        if (slot < 0)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                                    "text.elementalcircuits.server.equipment._wand_equipment.008")
                            + next.title());
        WandParts.Assembly replacement = current.replace(next);
        replacement.stats(WandResources.definitions());
        // Validate everything before the first inventory mutation; no tick or blueprint reset.
        player.getInventory().getItem(slot).shrink(1);
        ItemStack old = new ItemStack(ElementalCircuits.PARTS.get(current.at(next.slot())).get());
        write(wand, replacement, 0);
        if (!player.getInventory().add(old)) player.drop(old, false);
    }
}
