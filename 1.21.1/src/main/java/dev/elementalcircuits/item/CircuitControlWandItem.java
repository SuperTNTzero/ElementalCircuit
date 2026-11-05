package dev.elementalcircuits.item;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.block.CircuitBlockEntity;
import dev.elementalcircuits.server.circuit.FixedCircuitControl;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;

import java.util.List;

public final class CircuitControlWandItem extends Item {
    public CircuitControlWandItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel().getBlockEntity(context.getClickedPos())
                instanceof CircuitBlockEntity block)) return InteractionResult.PASS;
        if (context.getPlayer() instanceof ServerPlayer player) {
            try {
                var action =
                        player.isShiftKeyDown()
                                ? FixedCircuitControl.Action.NEXT_SLOT
                                : player.getOffhandItem().is(ElementalCircuits.MANA_CRYSTAL.get())
                                        ? FixedCircuitControl.Action.CHARGE
                                        : FixedCircuitControl.Action.TOGGLE;
                player.displayClientMessage(FixedCircuitControl.apply(player, block, action), true);
            } catch (RuntimeException error) {
                player.displayClientMessage(
                        Component.literal(
                                        error.getMessage() == null
                                                ? dev.elementalcircuits.text.Texts.get(
                                                        "text.elementalcircuits.item._circuit_control_wand_item.001")
                                                : error.getMessage())
                                .withStyle(ChatFormatting.RED),
                        true);
            }
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    @Override
    public void appendHoverText(
            ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        for (String key : new String[] {"toggle", "slot", "charge"})
            lines.add(
                    Component.translatable("tooltip.elementalcircuits.control." + key)
                            .withStyle(ChatFormatting.GRAY));
    }
}
