package dev.elementalcircuits.item;

import dev.elementalcircuits.server.CarrierService;
import dev.elementalcircuits.server.WandEquipment;
import dev.elementalcircuits.adapter.WandResources;
import dev.elementalcircuits.domain.WandParts;
import com.google.common.collect.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import java.util.List;

public final class CircuitWandItem extends Item {
    public CircuitWandItem(Properties properties){super(properties.component(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS,net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY.withTooltip(false)));}
    @Override public int getMaxDamage(ItemStack stack){try{return WandEquipment.stats(stack).durability();}catch(RuntimeException e){return 192;}}
    @Override public boolean isDamageable(ItemStack stack){return true;}
    @Override public boolean shouldCauseReequipAnimation(ItemStack oldStack,ItemStack newStack,boolean slotChanged){return dev.elementalcircuits.adapter.persistence.ItemAppearance.reequipWand(oldStack,newStack,slotChanged);}
    @Override public net.minecraft.world.item.component.ItemAttributeModifiers getDefaultAttributeModifiers(ItemStack stack){
        try{return net.minecraft.world.item.component.ItemAttributeModifiers.builder()
            .add(Attributes.ATTACK_DAMAGE,new AttributeModifier(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("elementalcircuits","tail_damage"),WandEquipment.broken(stack)?0:WandEquipment.stats(stack).melee()-1,AttributeModifier.Operation.ADD_VALUE),net.minecraft.world.entity.EquipmentSlotGroup.MAINHAND)
            .add(Attributes.ATTACK_SPEED,new AttributeModifier(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("elementalcircuits","attack_speed"),-2.4,AttributeModifier.Operation.ADD_VALUE),net.minecraft.world.entity.EquipmentSlotGroup.MAINHAND).build();}
        catch(RuntimeException e){return net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY;}
    }
    @Override public boolean hurtEnemy(ItemStack stack,LivingEntity target,LivingEntity attacker){if(!attacker.level().isClientSide)WandEquipment.wear(stack);return true;}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        if(!level.isClientSide && player instanceof ServerPlayer server && hand==InteractionHand.MAIN_HAND){
            if(player.isShiftKeyDown())server.displayClientMessage(Component.literal("持法杖右键法阵工作台刻印，或法杖工作台组装"),true);else CarrierService.start(server,dev.elementalcircuits.server.CastingEquipment.carrier(server,player.getItemInHand(hand)));
        }
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }
    @Override public void inventoryTick(ItemStack stack,Level level,Entity entity,int slot,boolean selected){
        if(!level.isClientSide && entity instanceof ServerPlayer player){
            dev.elementalcircuits.server.WandCooldown.tick(stack);
            if(selected && player.getOffhandItem().is(dev.elementalcircuits.ElementalCircuits.SPELL_BOOK.get())){try{var a=CarrierService.assembly(stack);if(a.powered()){a.stopAll();CarrierService.persist(stack);}}catch(RuntimeException e){CarrierService.corrupt(stack,player);}}
            else CarrierService.tick(stack,player);
        }
    }
    @Override public boolean onEntityItemUpdate(ItemStack stack,ItemEntity entity){
        if(!entity.level().isClientSide){dev.elementalcircuits.server.WandCooldown.tick(stack);CarrierService.tickDropped(stack,entity);}
        return false;
    }
    @Override public void appendHoverText(ItemStack stack,Item.TooltipContext context,List<Component> tooltip,TooltipFlag flag){
        try{
            WandParts.Assembly a=WandEquipment.assembly(stack);WandParts.Stats stats=WandEquipment.stats(stack);
            tooltip.add(Component.translatable("tooltip.elementalcircuits.wand_power",String.format(java.util.Locale.ROOT,"%.2f",stats.strength()/65536.0),String.format(java.util.Locale.ROOT,"%.2f",dev.elementalcircuits.adapter.CooldownResources.rules().ticks(stats.speed())/20.0)).withStyle(net.minecraft.ChatFormatting.AQUA));
            tooltip.add(Component.translatable("tooltip.elementalcircuits.wand_condition",Math.max(0,stats.durability()-stack.getDamageValue()),stats.durability(),String.format(java.util.Locale.ROOT,"%.0f",stats.melee())).withStyle(net.minecraft.ChatFormatting.GRAY));
            if(flag.isAdvanced())for(WandParts.Slot slot:WandParts.Slot.values())tooltip.add(Component.literal(WandResources.definitions().get(a.at(slot)).title()).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        }catch(RuntimeException e){tooltip.add(Component.literal("部件数据异常，已停用"));}
        tooltip.add(Component.translatable("tooltip.elementalcircuits.wand_use").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
    }
}
