package dev.elementalcircuits.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.domain.Fixed;
import dev.elementalcircuits.network.RelationNetwork;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.*;

import org.joml.Matrix4f;

/** One bounded line batch. The server snapshot never participates in gameplay. */
@net.neoforged.fml.common.EventBusSubscriber(modid = ElementalCircuits.ID, value = Dist.CLIENT)
public final class FixedRelationRenderer {
    private static RelationNetwork.View view;
    private static long received;
    private static Object world;

    public static void accept(RelationNetwork.View next) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || !mc.level.dimension().location().equals(next.dimension())) return;
        view = next;
        world = mc.level;
        received = mc.level.getGameTime();
    }

    private static boolean visible() {
        var mc = Minecraft.getInstance();
        return view != null
                && mc.player != null
                && mc.level == world
                && !mc.options.hideGui
                && mc.level.getGameTime() - received <= 30
                && dev.elementalcircuits.adapter.compat.CuriosEquipment.monocleWorn(mc.player);
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        view = null;
        world = null;
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES
                || !visible()
                || view.nodes().isEmpty()) return;
        var camera = event.getCamera().getPosition();
        var matrix = event.getPoseStack().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        try {
            var out =
                    Tesselator.getInstance()
                            .begin(
                                    VertexFormat.Mode.DEBUG_LINES,
                                    DefaultVertexFormat.POSITION_COLOR);
            for (var n : view.nodes()) {
                var c = Vec3.atLowerCornerOf(n.pos()).add(.5, .35, .5).subtract(camera);
                int color = n.powered() ? 0x8ADDEC : 0xB39B69;
                double d = .26;
                var p =
                        new Vec3[] {
                            c.add(-d, 0, -d), c.add(d, 0, -d), c.add(d, 0, d), c.add(-d, 0, d)
                        };
                for (int i = 0; i < 4; i++) line(out, matrix, p[i], p[(i + 1) % 4], color, 210);
                line(out, matrix, c, c.add(0, .3, 0), color, 210);
            }
            for (var e : view.edges()) {
                var a =
                        Vec3.atCenterOf(view.nodes().get(e.from()).pos())
                                .add(0, .15, 0)
                                .subtract(camera);
                var b =
                        Vec3.atCenterOf(view.nodes().get(e.to()).pos())
                                .add(0, .15, 0)
                                .subtract(camera);
                // Separate opposing and parallel links by slot and payload for readability.
                double height = .10 * ((e.fromSlot() + e.toSlot()) % 4) + (e.signal() ? .15 : 0);
                a = a.add(0, height, 0);
                b = b.add(0, height, 0);
                int color = e.signal() ? 0xEBC36C : 0x56C8EF;
                int alpha = e.active() ? 245 : 100;
                line(out, matrix, a, b, color, alpha);
                var direction = b.subtract(a).normalize();
                var side = direction.cross(new Vec3(0, 1, 0)).normalize().scale(.15);
                var tip = a.lerp(b, .65);
                var base = tip.subtract(direction.scale(.3));
                line(out, matrix, tip, base.add(side), color, 220);
                line(out, matrix, tip, base.subtract(side), color, 220);
            }
            BufferUploader.drawWithShader(out.buildOrThrow());
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
        }
    }

    private static void line(BufferBuilder out, Matrix4f m, Vec3 a, Vec3 b, int color, int alpha) {
        int r = color >> 16 & 255, g = color >> 8 & 255, blue = color & 255;
        out.addVertex(m, (float) a.x, (float) a.y, (float) a.z).setColor(r, g, blue, alpha);
        out.addVertex(m, (float) b.x, (float) b.y, (float) b.z).setColor(r, g, blue, alpha);
    }

    @SubscribeEvent
    public static void hud(RenderGuiEvent.Post event) {
        if (!visible()) return;
        var mc = Minecraft.getInstance();
        var g = event.getGuiGraphics();
        int x = g.guiWidth() / 2 + 12, y = g.guiHeight() / 2 + 18;
        if (mc.hitResult instanceof BlockHitResult hit) {
            for (var n : view.nodes())
                if (n.pos().equals(hit.getBlockPos())) {
                    g.drawString(
                            mc.font,
                            Component.translatable(
                                    "sight.elementalcircuits.anchor",
                                    n.slot(),
                                    Component.translatable(
                                            n.powered()
                                                    ? "control.elementalcircuits.on"
                                                    : "control.elementalcircuits.off"),
                                    Fixed.display(n.charge())),
                            x,
                            y,
                            0xE8D4A2);
                    y += 12;
                    for (var e : view.edges())
                        if (view.nodes().get(e.from()).pos().equals(n.pos())
                                || view.nodes().get(e.to()).pos().equals(n.pos())) {
                            g.drawString(
                                    mc.font,
                                    Component.translatable(
                                            "sight.elementalcircuits.link",
                                            e.fromSlot(),
                                            e.toSlot(),
                                            Component.translatable(
                                                    e.signal()
                                                            ? "sight.elementalcircuits.signal"
                                                            : "sight.elementalcircuits.mana")),
                                    x,
                                    y,
                                    e.signal() ? 0xEBC36C : 0x56C8EF);
                            y += 10;
                            if (y > g.guiHeight() / 2 + 90) break;
                        }
                    break;
                }
        }
    }

    private FixedRelationRenderer() {}
}
