package dev.elementalcircuits.item;

import dev.elementalcircuits.domain.Fixed;
import dev.elementalcircuits.server.WorldManaField;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import java.util.List;

public final class ManaCrystalItem extends Item {
    public ManaCrystalItem(Properties p){super(p);}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        if(level instanceof ServerLevel server)try{
            var data=WorldManaField.get(server);var c=data.discover(server,player.blockPosition());
            player.displayClientMessage(Component.literal(String.format(java.util.Locale.ROOT,"场强 %.1f / %.0f",data.field().amount(c)/(double)Fixed.ONE,data.field().rules().capacity()/(double)Fixed.ONE)),true);
        }catch(RuntimeException error){player.displayClientMessage(Component.literal("此处魔力场暂不可用"),true);}
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand),level.isClientSide);
    }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag){
        lines.add(Component.literal("置入魔力节点，为周围魔力场供能").withStyle(net.minecraft.ChatFormatting.GRAY));
        lines.add(Component.literal("右键察看当地场强").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
    }
}
