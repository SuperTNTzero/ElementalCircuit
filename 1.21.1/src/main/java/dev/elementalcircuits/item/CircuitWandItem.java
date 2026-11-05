package dev.elementalcircuits.item;

import com.google.common.collect.*;

import dev.elementalcircuits.adapter.persistence.BoardAppearance;
import dev.elementalcircuits.adapter.persistence.ItemAppearance;
import dev.elementalcircuits.adapter.resources.CooldownResources;
import dev.elementalcircuits.adapter.resources.WandResources;
import dev.elementalcircuits.domain.Fixed;
import dev.elementalcircuits.domain.equipment.WandParts;
import dev.elementalcircuits.server.casting.WandCasting;
import dev.elementalcircuits.server.casting.WandCooldown;
import dev.elementalcircuits.server.circuit.CarrierService;
import dev.elementalcircuits.server.equipment.WandEquipment;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

import java.util.List;

public final class CircuitWandItem extends Item {
    public CircuitWandItem(Properties properties) {
        super(
                properties.component(
                        net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS,
                        net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY.withTooltip(
                                false)));
    }

    @Override
    public int getEnchantmentValue() {
        return 15;
    }

    @Override
    public int getMaxDamage(ItemStack stack) {
        try {
            return WandEquipment.stats(stack).durability();
        } catch (RuntimeException e) {
            return 192;
        }
    }

    @Override
    public boolean isDamageable(ItemStack stack) {
        return true;
    }

    @Override
    public boolean shouldCauseReequipAnimation(
            ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return ItemAppearance.reequipWand(oldStack, newStack, slotChanged);
    }

    @Override
    public net.minecraft.world.item.component.ItemAttributeModifiers getDefaultAttributeModifiers(
            ItemStack stack) {
        try {
            return net.minecraft.world.item.component.ItemAttributeModifiers.builder()
                    .add(
                            Attributes.ATTACK_DAMAGE,
                            new AttributeModifier(
                                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                                            "elementalcircuits", "tail_damage"),
                                    WandEquipment.broken(stack)
                                            ? 0
                                            : WandEquipment.stats(stack).melee() - 1,
                                    AttributeModifier.Operation.ADD_VALUE),
                            net.minecraft.world.entity.EquipmentSlotGroup.MAINHAND)
                    .add(
                            Attributes.ATTACK_SPEED,
                            new AttributeModifier(
                                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                                            "elementalcircuits", "attack_speed"),
                                    -2.4,
                                    AttributeModifier.Operation.ADD_VALUE),
                            net.minecraft.world.entity.EquipmentSlotGroup.MAINHAND)
                    .build();
        } catch (RuntimeException e) {
            return net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY;
        }
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!attacker.level().isClientSide) WandEquipment.wear(stack, attacker);
        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(
            Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) return InteractionResultHolder.pass(stack);
        if (player.isShiftKeyDown()) {
            if (player instanceof ServerPlayer server)
                server.displayClientMessage(
                        Component.literal(
                                dev.elementalcircuits.text.Texts.get(
                                        "text.elementalcircuits.item._circuit_wand_item.001")),
                        true);
            return InteractionResultHolder.fail(stack);
        }
        if (player instanceof ServerPlayer server) WandCasting.begin(server, stack);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (entity instanceof ServerPlayer p)
            WandCasting.advance(p, stack, getUseDuration(stack, entity) - remaining);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remaining) {
        if (entity instanceof ServerPlayer p) WandCasting.release(p, stack);
    }

    @Override
    public void inventoryTick(
            ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!level.isClientSide && entity instanceof ServerPlayer player) {
            WandCooldown.tick(stack);
            CarrierService.tick(stack, player);
        }
    }

    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        if (!entity.level().isClientSide) {
            WandCooldown.tick(stack);
            CarrierService.tickDropped(stack, entity);
        }
        return false;
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            Item.TooltipContext context,
            List<Component> tooltip,
            TooltipFlag flag) {
        try {
            WandParts.Assembly a = WandEquipment.assembly(stack);
            WandParts.Stats stats = WandEquipment.stats(stack);
            var board = BoardAppearance.read(stack);
            if (board != null)
                tooltip.add(
                        Component.literal(
                                        (board.melted()
                                                        ? dev.elementalcircuits.text.Texts.get(
                                                                "text.elementalcircuits.item._circuit_wand_item.002")
                                                        : board.remaining() == 0
                                                                ? dev.elementalcircuits.text.Texts
                                                                        .get(
                                                                                "text.elementalcircuits.item._circuit_wand_item.003")
                                                                : dev.elementalcircuits.text.Texts
                                                                                .get(
                                                                                        "text.elementalcircuits.item._circuit_wand_item.004")
                                                                        + Fixed.display(
                                                                                board.remaining())
                                                                        + " / "
                                                                        + Fixed.display(
                                                                                board.maximum()))
                                                + dev.elementalcircuits.text.Texts.get(
                                                        "text.elementalcircuits.item._circuit_wand_item.005")
                                                + Fixed.display(board.capacity()))
                                .withStyle(
                                        board.remaining() == 0
                                                ? net.minecraft.ChatFormatting.RED
                                                : net.minecraft.ChatFormatting.DARK_AQUA));
            tooltip.add(
                    Component.translatable(
                                    "tooltip.elementalcircuits.wand_power",
                                    String.format(
                                            java.util.Locale.ROOT,
                                            "%.2f",
                                            stats.strength() / 65536.0),
                                    String.format(
                                            java.util.Locale.ROOT,
                                            "%.2f",
                                            CooldownResources.rules().ticks(stats.speed()) / 20.0))
                            .withStyle(net.minecraft.ChatFormatting.AQUA));
            tooltip.add(
                    Component.translatable(
                                    "tooltip.elementalcircuits.wand_condition",
                                    Math.max(0, stats.durability() - stack.getDamageValue()),
                                    stats.durability(),
                                    String.format(java.util.Locale.ROOT, "%.0f", stats.melee()))
                            .withStyle(net.minecraft.ChatFormatting.GRAY));
            if (flag.isAdvanced())
                for (WandParts.Slot slot : WandParts.Slot.values())
                    tooltip.add(
                            Component.literal(WandResources.definitions().get(a.at(slot)).title())
                                    .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        } catch (RuntimeException e) {
            tooltip.add(
                    Component.literal(
                            dev.elementalcircuits.text.Texts.get(
                                    "text.elementalcircuits.item._circuit_wand_item.006")));
        }
        tooltip.add(
                Component.translatable("tooltip.elementalcircuits.wand_use")
                        .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
    }
}
