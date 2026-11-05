package dev.elementalcircuits.client.registration;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.block.ManaNodeBlockEntity;
import dev.elementalcircuits.server.ecology.ManaEcology;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Slime;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@net.neoforged.fml.common.EventBusSubscriber(modid = ElementalCircuits.ID, value = Dist.CLIENT)
public final class ManaEcologyRendering {
    private static final ResourceLocation SLIME =
            ResourceLocation.fromNamespaceAndPath(
                    ElementalCircuits.ID, "textures/entity/mana_slime.png");
    private static final ResourceLocation ORB =
            ResourceLocation.fromNamespaceAndPath(
                    ElementalCircuits.ID, "textures/effect/aerial_node.png");

    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                ManaEcology.SLIME.get(),
                context ->
                        new SlimeRenderer(context) {
                            @Override
                            public ResourceLocation getTextureLocation(Slime slime) {
                                return SLIME;
                            }

                            @Override
                            protected int getBlockLightLevel(
                                    Slime slime, net.minecraft.core.BlockPos pos) {
                                return 10;
                            }
                        });
        event.registerBlockEntityRenderer(
                ElementalCircuits.MANA_NODE_ENTITY.get(), context -> new OrbRenderer());
    }

    private static final class OrbRenderer implements BlockEntityRenderer<ManaNodeBlockEntity> {
        @Override
        public void render(
                ManaNodeBlockEntity node,
                float partial,
                PoseStack pose,
                MultiBufferSource buffers,
                int light,
                int overlay) {
            if (!node.aerial()) return;
            double time =
                    (node.getLevel().getGameTime() + partial) + node.getBlockPos().asLong() % 97;
            pose.pushPose();
            pose.translate(.5, .5 + Math.sin(time * .035) * .07, .5);
            pose.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
            pose.mulPose(Axis.ZP.rotationDegrees((float) (time * .25)));
            float size = .72f + (float) Math.sin(time * .055) * .025f;
            var out = buffers.getBuffer(RenderType.entityTranslucentEmissive(ORB));
            var m = pose.last();
            vertex(out, m, -size, -size, 0, 1);
            vertex(out, m, size, -size, 1, 1);
            vertex(out, m, size, size, 1, 0);
            vertex(out, m, -size, size, 0, 0);
            pose.popPose();
        }

        private void vertex(
                VertexConsumer out, PoseStack.Pose pose, float x, float y, float u, float v) {
            out.addVertex(pose.pose(), x, y, 0)
                    .setColor(255, 255, 255, 210)
                    .setUv(u, v)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(LightTexture.FULL_BRIGHT)
                    .setNormal(pose, 0, 0, 1);
        }
    }

    private ManaEcologyRendering() {}
}
