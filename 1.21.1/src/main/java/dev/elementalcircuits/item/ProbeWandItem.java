package dev.elementalcircuits.item;

import dev.elementalcircuits.server.probe.ProbeService;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

public final class ProbeWandItem extends Item {
    public ProbeWandItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() instanceof ServerPlayer p)
            ProbeService.use(p, context.getHand(), context.getClickedPos());
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(
            Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer p) ProbeService.use(p, hand, null);
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public void appendHoverText(
            ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        for (String key : new String[] {"target", "scan", "offhand"})
            lines.add(
                    Component.translatable("hint.elementalcircuits.probe." + key)
                            .withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
