package dev.elementalcircuits.client.book;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import dev.elementalcircuits.ElementalCircuits;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.*;
import net.minecraft.client.resources.model.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;

import java.util.List;

/** Shared static baked OBJ parts; animation only changes rigid-part transforms. */
public final class BookItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static ItemStack spellIcon, noteIcon;
    public static final List<String> PARTS =
            List.of(
                    "spine",
                    "front_cover",
                    "back_cover",
                    "front_pages",
                    "back_pages",
                    "turning_leaf");

    public BookItemRenderer() {
        super(
                Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                Minecraft.getInstance().getEntityModels());
    }

    public static ModelResourceLocation model(boolean notebook, String part) {
        return ModelResourceLocation.standalone(
                ResourceLocation.fromNamespaceAndPath(
                        ElementalCircuits.ID,
                        "item/book3d/" + (notebook ? "notebook" : "spellbook") + "_" + part));
    }

    private static void part(
            ItemStack stack,
            boolean notebook,
            String part,
            PoseStack pose,
            MultiBufferSource buffers,
            int light,
            int overlay) {
        var mc = Minecraft.getInstance();
        var baked = mc.getModelManager().getModel(model(notebook, part));
        // ItemRenderer subtracts the model centre even for NONE; cancel that once.
        pose.pushPose();
        pose.translate(.5, .5, .5);
        mc.getItemRenderer()
                .render(
                        stack,
                        ItemDisplayContext.NONE,
                        false,
                        pose,
                        buffers,
                        light,
                        overlay,
                        baked);
        pose.popPose();
    }

    private static void hinged(
            ItemStack stack,
            boolean notebook,
            String part,
            float angle,
            PoseStack pose,
            MultiBufferSource buffers,
            int light,
            int overlay) {
        pose.pushPose();
        pose.translate(.2, .5, .5);
        pose.mulPose(Axis.ZP.rotationDegrees(angle));
        pose.translate(-.2, -.5, -.5);
        part(stack, notebook, part, pose, buffers, light, overlay);
        pose.popPose();
    }

    public static void draw(
            ItemStack stack,
            boolean notebook,
            BookMotion.Pose motion,
            PoseStack pose,
            MultiBufferSource buffers,
            int light,
            int overlay) {
        float angle = 180 * motion.open();
        pose.pushPose();
        pose.translate(.30 * motion.open(), 0, 0);
        // The binding relaxes with the opened covers instead of standing above the pages.
        pose.pushPose();
        pose.translate(0, .5 - .065 * motion.open(), 0);
        pose.scale(1, 1 - .5f * motion.open(), 1);
        pose.translate(0, -.5, 0);
        part(stack, notebook, "spine", pose, buffers, light, overlay);
        pose.popPose();
        part(stack, notebook, "back_cover", pose, buffers, light, overlay);
        part(stack, notebook, "back_pages", pose, buffers, light, overlay);
        hinged(stack, notebook, "front_cover", angle, pose, buffers, light, overlay);
        hinged(stack, notebook, "front_pages", angle, pose, buffers, light, overlay);
        if (motion.turn() >= 0 && motion.open() > .6f) {
            float phase = motion.direction() > 0 ? motion.turn() : 1 - motion.turn();
            hinged(stack, notebook, "turning_leaf", angle * phase, pose, buffers, light, overlay);
        }
        pose.popPose();
    }

    @Override
    public void renderByItem(
            ItemStack stack,
            ItemDisplayContext context,
            PoseStack pose,
            MultiBufferSource buffers,
            int light,
            int overlay) {
        var mc = Minecraft.getInstance();
        boolean notebook = stack.is(ElementalCircuits.NOTEBOOK.get());
        float partial = mc.isPaused() ? 0 : mc.getTimer().getGameTimeDeltaPartialTick(false);
        var motion = BookAnimationClient.pose(stack, context, partial);
        if (motion.open() == 0) part(stack, notebook, "closed", pose, buffers, light, overlay);
        else draw(stack, notebook, motion, pose, buffers, light, overlay);
    }

    public static void preview(GuiGraphics g, int x, int y, int size, boolean notebook) {
        var mc = Minecraft.getInstance();
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x + size / 2f, y + size / 2f, 200);
        pose.scale(size, -size, size);
        pose.mulPose(Axis.XP.rotationDegrees(55));
        pose.mulPose(Axis.YP.rotationDegrees(-15));
        pose.translate(-.5, -.5, -.5);
        if (spellIcon == null) {
            spellIcon = new ItemStack(ElementalCircuits.SPELL_BOOK.get());
            noteIcon = new ItemStack(ElementalCircuits.NOTEBOOK.get());
        }
        var stack = notebook ? noteIcon : spellIcon;
        draw(
                stack,
                notebook,
                BookAnimationClient.ui(
                        mc.isPaused() ? 0 : mc.getTimer().getGameTimeDeltaPartialTick(false)),
                pose,
                g.bufferSource(),
                LightTexture.FULL_BRIGHT,
                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
        pose.popPose();
        g.flush();
    }
}
