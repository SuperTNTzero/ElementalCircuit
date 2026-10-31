package dev.elementalcircuits.client;
import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.block.*;
import dev.elementalcircuits.client.render.WandPreview;
import dev.elementalcircuits.client.render.WandDeskPose;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Static desk mesh is chunk-baked; only an occupied desk submits one existing wand model. */
@EventBusSubscriber(modid=ElementalCircuits.ID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class WorkstationRendering {
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event){event.registerBlockEntityRenderer(ElementalCircuits.WORKSTATION_ENTITY.get(),context->new Renderer());}
    private static final class Renderer implements BlockEntityRenderer<WorkstationBlockEntity> {
        @Override public void render(WorkstationBlockEntity b,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
            if(b.parts()==null || !b.getBlockState().is(ElementalCircuits.WAND_WORKBENCH.get()))return;
            var mc=Minecraft.getInstance();var stack=WandPreview.stack(b.parts());var model=mc.getItemRenderer().getModel(stack,b.getLevel(),null,0);var placement=WandDeskPose.of(model);
            if(placement.scale()<=0)return;
            pose.pushPose();pose.translate(.5,0,.5);pose.mulPose(Axis.YP.rotationDegrees(-b.getBlockState().getValue(WandWorkbenchBlock.FACING).toYRot()+180));pose.translate(-.5,0,-.5);
            placement.apply(pose);
            mc.getItemRenderer().renderStatic(stack,ItemDisplayContext.NONE,light,overlay,pose,buffers,b.getLevel(),0);pose.popPose();
        }
        @Override public AABB getRenderBoundingBox(WorkstationBlockEntity b){return new AABB(b.getBlockPos()).inflate(1);}
    }
}
