package dev.elementalcircuits.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.persistence.CooldownComponent;
import dev.elementalcircuits.client.book.BookAnimationClient;
import dev.elementalcircuits.client.render.BookItemRenderer;
import dev.elementalcircuits.domain.CastCooldown;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.*;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.extensions.common.*;

@EventBusSubscriber(modid=ElementalCircuits.ID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class HeldItemRendering {
    private HeldItemRendering(){}
    @SubscribeEvent public static void models(ModelEvent.RegisterAdditional event){
        for(boolean notebook:new boolean[]{false,true}){for(String part:BookItemRenderer.PARTS)event.register(BookItemRenderer.model(notebook,part));event.register(BookItemRenderer.model(notebook,"closed"));}
    }
    @SubscribeEvent public static void extensions(RegisterClientExtensionsEvent event){
        var books=new BookItemRenderer();event.registerItem(new IClientItemExtensions(){@Override public BlockEntityWithoutLevelRenderer getCustomRenderer(){return books;}},ElementalCircuits.SPELL_BOOK.get(),ElementalCircuits.NOTEBOOK.get());
        event.registerItem(new IClientItemExtensions(){
            @Override public boolean applyForgeHandTransform(PoseStack pose,LocalPlayer player,HumanoidArm arm,ItemStack stack,float partial,float equip,float swing){
                // Preserve actual equip and melee motion; steady held casting ignores itemUsed's reset.
                if(!BookAnimationClient.stableWand(stack,arm) || player.isHandsBusy() || player.getAttackStrengthScale(partial)<.999f)return false;
                applySteadyWand(pose,arm,swing);return true;
            }
        },ElementalCircuits.WAND.get());
    }
    public static void applySteadyWand(PoseStack pose,HumanoidArm arm,float swing){
        int side=arm==HumanoidArm.RIGHT?1:-1;float root=Mth.sqrt(swing),pi=(float)Math.PI;
        pose.translate(side*(-.4f*Mth.sin(root*pi)),.2f*Mth.sin(root*pi*2),-.2f*Mth.sin(swing*pi));
        pose.translate(side*.56f,-.52f,-.72f);
        pose.mulPose(Axis.YP.rotationDegrees(side*(45-20*Mth.sin(swing*swing*pi))));
        pose.mulPose(Axis.ZP.rotationDegrees(side*(-20*Mth.sin(root*pi))));pose.mulPose(Axis.XP.rotationDegrees(-80*Mth.sin(root*pi)));pose.mulPose(Axis.YP.rotationDegrees(side*-45));
    }
    @SubscribeEvent public static void decorations(RegisterItemDecorationsEvent event){
        event.register(ElementalCircuits.WAND.get(),(g,font,stack,x,y)->{
            CastCooldown cd=stack.getOrDefault(CooldownComponent.TYPE.get(),CastCooldown.READY);if(cd.ready())return false;
            var mc=Minecraft.getInstance();float partial=mc.isPaused()?0:mc.getTimer().getGameTimeDeltaPartialTick(false);
            double remaining=Math.max(0,cd.remaining()-partial)/(double)cd.duration();
            g.pose().pushPose();g.pose().translate(0,0,200);g.fill(RenderType.guiOverlay(),x,y+(int)Math.floor(16*(1-remaining)),x+16,y+16,0x66ffffff);g.pose().popPose();return true;
        });
    }
}
