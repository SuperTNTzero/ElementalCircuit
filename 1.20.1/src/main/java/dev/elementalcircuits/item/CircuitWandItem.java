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
    public CircuitWandItem(Properties properties){super(properties);}
    @Override public int getMaxDamage(ItemStack stack){try{return WandEquipment.stats(stack).durability();}catch(RuntimeException e){return 192;}}
    @Override public boolean isDamageable(ItemStack stack){return true;}
    @Override public Multimap<Attribute,AttributeModifier> getAttributeModifiers(EquipmentSlot slot,ItemStack stack){
        if(slot!=EquipmentSlot.MAINHAND)return ImmutableMultimap.of();
        try{return ImmutableMultimap.of(Attributes.ATTACK_DAMAGE,new AttributeModifier(BASE_ATTACK_DAMAGE_UUID,"Wand tail damage",WandEquipment.broken(stack)?0:WandEquipment.stats(stack).melee()-1,AttributeModifier.Operation.ADDITION),
            Attributes.ATTACK_SPEED,new AttributeModifier(BASE_ATTACK_SPEED_UUID,"Wand attack speed",-2.4,AttributeModifier.Operation.ADDITION));}
        catch(RuntimeException e){return ImmutableMultimap.of();}
    }
    @Override public boolean hurtEnemy(ItemStack stack,LivingEntity target,LivingEntity attacker){if(!attacker.level().isClientSide)WandEquipment.wear(stack);return true;}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        if(!level.isClientSide && player instanceof ServerPlayer server && hand==InteractionHand.MAIN_HAND){
            if(player.isShiftKeyDown())CarrierService.open(server);else CarrierService.start(server,dev.elementalcircuits.server.CastingEquipment.carrier(server,player.getItemInHand(hand)));
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand),level.isClientSide);
    }
    @Override public void inventoryTick(ItemStack stack,Level level,Entity entity,int slot,boolean selected){
        if(!level.isClientSide && entity instanceof ServerPlayer player){
            if(selected && player.getOffhandItem().is(dev.elementalcircuits.ElementalCircuits.SPELL_BOOK.get())){try{var a=CarrierService.assembly(stack);if(a.powered()){a.stopAll();CarrierService.persist(stack);}}catch(RuntimeException e){CarrierService.corrupt(stack,player);}}
            else CarrierService.tick(stack,player);
        }
    }
    @Override public boolean onEntityItemUpdate(ItemStack stack,ItemEntity entity){
        if(!entity.level().isClientSide)CarrierService.tickDropped(stack,entity);
        return false;
    }
    @Override public void appendHoverText(ItemStack stack,Level level,List<Component> tooltip,TooltipFlag flag){
        try{
            WandParts.Assembly a=WandEquipment.assembly(stack);WandParts.Stats stats=WandEquipment.stats(stack);
            for(WandParts.Slot slot:WandParts.Slot.values())tooltip.add(Component.literal(slot.title+"："+WandResources.definitions().get(a.at(slot)).title()));
            tooltip.add(Component.literal(String.format(java.util.Locale.ROOT,"强度 %.2f× · 节奏 %.2f× · 近战 %.1f",stats.strength()/65536.0,stats.speed()/65536.0,stats.melee())));
            tooltip.add(Component.literal("耐久 "+Math.max(0,stats.durability()-stack.getDamageValue())+" / "+stats.durability()));
        }catch(RuntimeException e){tooltip.add(Component.literal("部件数据异常，已停用"));}
        tooltip.add(Component.translatable("hint.elementalcircuits.wand"));
        tooltip.add(Component.translatable("hint.elementalcircuits.wand_edit"));
        tooltip.add(Component.translatable("hint.elementalcircuits.no_default_spell"));
    }
}
