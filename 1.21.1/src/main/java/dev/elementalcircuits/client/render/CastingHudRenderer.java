package dev.elementalcircuits.client.render;

import dev.elementalcircuits.domain.Fixed;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Stateless HUD drawing. Static textures provide the artwork; progress only clips the blue ring.
 */
public final class CastingHudRenderer {
    private static final ResourceLocation FRAME = texture("mana_frame");
    private static final RenderType TRACK = RenderType.text(texture("cooldown_track"));
    private static final RenderType RING = RenderType.text(texture("cooldown_blue"));
    private static final int SEGMENTS = 96, BLUE = 0xff73cfff, MANA = 0xff378fca;
    private static final float[] X = new float[SEGMENTS + 1], Y = new float[SEGMENTS + 1];

    static {
        for (int i = 0; i <= SEGMENTS; i++) {
            double angle = i * Math.PI * 2 / SEGMENTS - Math.PI / 2;
            X[i] = (float) Math.cos(angle);
            Y[i] = (float) Math.sin(angle);
        }
    }

    private CastingHudRenderer() {}

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(
                "elementalcircuits", "textures/gui/hud/" + name + ".png");
    }

    public static void cooldown(GuiGraphics g, int cx, int cy, double progress) {
        var track = g.bufferSource().getBuffer(TRACK);
        var matrix = g.pose().last().pose();
        vertex(track, matrix, cx, cy, -1, 1);
        vertex(track, matrix, cx, cy, 1, 1);
        vertex(track, matrix, cx, cy, 1, -1);
        vertex(track, matrix, cx, cy, -1, -1);
        progress = Math.max(0, Math.min(1, progress));
        if (progress <= 0) return;
        var out = g.bufferSource().getBuffer(RING);
        // A textured circular fan is clipped clockwise from twelve o'clock. The texture owns AA
        // edges.
        double count = progress * SEGMENTS;
        for (int i = 0; i < count; i++) {
            double fraction = Math.min(1, count - i);
            float ex, ey;
            if (fraction == 1) {
                ex = X[i + 1];
                ey = Y[i + 1];
            } else {
                double angle = (i + fraction) * Math.PI * 2 / SEGMENTS - Math.PI / 2;
                ex = (float) Math.cos(angle);
                ey = (float) Math.sin(angle);
            }
            vertex(out, matrix, cx, cy, 0, 0);
            vertex(out, matrix, cx, cy, ex, ey);
            vertex(out, matrix, cx, cy, X[i], Y[i]);
            vertex(out, matrix, cx, cy, 0, 0);
        }
    }

    public static void charge(
            GuiGraphics g, Font font, int cx, int cy, double progress, double maximum) {
        // A second, smaller ring keeps charging visually distinct from the cooldown ring.
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().scale(.72f, .72f, 1);
        cooldown(g, 0, 0, progress);
        g.pose().popPose();
        String text =
                progress >= 1
                        ? dev.elementalcircuits.client.text.UiText.string(
                                "text.elementalcircuits.client.render._casting_hud_renderer.001")
                        : String.format(
                                java.util.Locale.ROOT,
                                dev.elementalcircuits.client.text.UiText.string(
                                        "text.elementalcircuits.client.render._casting_hud_renderer.002"),
                                1 + (maximum - 1) * progress);
        g.drawCenteredString(font, text, cx, cy + 22, BLUE);
    }

    private static void vertex(
            com.mojang.blaze3d.vertex.VertexConsumer out,
            org.joml.Matrix4f m,
            int cx,
            int cy,
            float x,
            float y) {
        out.addVertex(m, cx + x * 17, cy + y * 17, 0)
                .setColor(0xffffffff)
                .setUv(.5f + x * .5f, .5f + y * .5f)
                .setLight(15728880);
    }

    private static String compact(long value) {
        return value % Fixed.ONE == 0
                ? Long.toString(value / Fixed.ONE)
                : String.format(java.util.Locale.ROOT, "%.1f", value / (double) Fixed.ONE);
    }

    public static void mana(
            GuiGraphics g, Font font, int screenHeight, long amount, long capacity) {
        int x = 10, y = screenHeight - 62, w = 144, h = 34;
        g.blit(FRAME, x, y, 0, 0, w, h, w, h);
        int barX = x + 8, barY = y + 23, barWidth = w - 16;
        g.fill(barX, barY, barX + barWidth, barY + 4, 0xff172a35);
        int filled = (int) Math.round(barWidth * (amount / (double) capacity));
        if (filled > 0) {
            g.fill(barX, barY, barX + filled, barY + 4, MANA);
            g.fill(barX, barY, barX + filled, barY + 1, BLUE);
        }
        Component label =
                Component.translatable("hud.elementalcircuits.mana").withStyle(ChatFormatting.BOLD);
        Component value =
                Component.literal(compact(amount) + " / " + compact(capacity))
                        .withStyle(ChatFormatting.BOLD);
        g.drawString(font, label, x + 8, y + 8, BLUE, true);
        g.drawString(font, value, x + w - 8 - font.width(value), y + 8, BLUE, true);
    }
}
