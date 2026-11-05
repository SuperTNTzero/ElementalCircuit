package dev.elementalcircuits.item;

import dev.elementalcircuits.adapter.config.ClientServerPolicy;
import dev.elementalcircuits.adapter.config.ServerSettings;
import dev.elementalcircuits.domain.Fixed;
import dev.elementalcircuits.server.mana.PlayerMana;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

import java.util.List;

/** Native eating animation; only the server may apply the permanent upgrade. */
public final class ManaFruitItem extends Item {
    public ManaFruitItem(Properties properties) {
        super(properties);
    }

    private static boolean permitted(ServerPlayer player) {
        try {
            if (PlayerMana.get(player).permanentCapacity()
                    < ServerSettings.mana("player.fruitMaximum")) return true;
            player.displayClientMessage(
                    Component.translatable("message.elementalcircuits.mana_full"), true);
        } catch (RuntimeException e) {
            player.displayClientMessage(
                    Component.translatable("message.elementalcircuits.mana_invalid"), true);
        }
        return false;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(
            Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer server && !permitted(server))
            return InteractionResultHolder.fail(player.getItemInHand(hand));
        return super.use(level, player, hand);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity eater) {
        if (!level.isClientSide) {
            if (!(eater instanceof ServerPlayer player) || !permitted(player)) return stack;
            if (!PlayerMana.consumeFruit(player)) return stack;
            player.displayClientMessage(
                    Component.translatable(
                            "message.elementalcircuits.mana_grown",
                            Fixed.display(PlayerMana.get(player).capacity())),
                    true);
        }
        return super.finishUsingItem(stack, level, eater);
    }

    @Override
    public void appendHoverText(
            ItemStack stack, TooltipContext context, List<Component> text, TooltipFlag flag) {

        text.add(
                Component.translatable(
                                "tooltip.elementalcircuits.mana_fruit",
                                Fixed.display(ClientServerPolicy.fruit()),
                                Fixed.display(ClientServerPolicy.maximum()))
                        .withStyle(ChatFormatting.AQUA));
        text.add(
                Component.translatable("tooltip.elementalcircuits.mana_fruit_recovery")
                        .withStyle(ChatFormatting.GRAY));
    }
}
