package dev.elementalcircuits.client.effect;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/** GPU adapter for the pure cosmetic geometry planner. One atlas, one bounded draw. */
public final class SpellVisualRenderer {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(
                    "elementalcircuits", "textures/effect/elemental_spells.png");
    private static int lastQuads, lastDraws;

    private SpellVisualRenderer() {}

    public static int lastQuads() {
        return lastQuads;
    }

    public static int lastDraws() {
        return lastDraws;
    }

    public static void render(
            PoseStack poses,
            Vec3 camera,
            Quaternionf rotation,
            List<VisualTimeline.Frame> frames,
            float partial) {
        lastQuads = lastDraws = 0;
        if (frames.isEmpty()) return;
        var out =
                Tesselator.getInstance()
                        .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, TEXTURE);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        try {
            var matrix = poses.last().pose();
            lastQuads =
                    SpellGeometry.emit(
                            frames,
                            new SpellGeometry.V(camera.x, camera.y, camera.z),
                            vector(new Vector3f(1, 0, 0).rotate(rotation)),
                            vector(new Vector3f(0, 1, 0).rotate(rotation)),
                            partial,
                            (a, b, c, d, sprite, color, alpha) -> {
                                float cell = 1f / SpellAtlas.COLUMNS,
                                        u = (sprite % SpellAtlas.COLUMNS) * cell,
                                        v = (sprite / SpellAtlas.COLUMNS) * cell,
                                        inset = .5f / SpellAtlas.PIXELS;
                                vertex(out, matrix, a, u + inset, v + cell - inset, color, alpha);
                                vertex(
                                        out,
                                        matrix,
                                        b,
                                        u + cell - inset,
                                        v + cell - inset,
                                        color,
                                        alpha);
                                vertex(out, matrix, c, u + cell - inset, v + inset, color, alpha);
                                vertex(out, matrix, d, u + inset, v + inset, color, alpha);
                            });
            var mesh = out.build();
            if (mesh != null) {
                BufferUploader.drawWithShader(mesh);
                lastDraws = 1;
            }
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            RenderSystem.setShaderColor(1, 1, 1, 1);
        }
        renderImported(poses, camera, rotation, frames, partial);
    }

    private static void renderImported(
            PoseStack poses,
            Vec3 camera,
            Quaternionf rotation,
            List<VisualTimeline.Frame> frames,
            float partial) {
        var right = new Vector3f(1, 0, 0).rotate(rotation);
        var up = new Vector3f(0, 1, 0).rotate(rotation);
        var matrix = poses.last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        try {
            for (var profile : CustomEffectProfiles.all()) {
                BufferBuilder out = null;
                int emitted = 0;
                for (var frame : frames) {
                    var event = frame.event();
                    boolean hit = event.kind() ==
                            dev.elementalcircuits.application.observation.SpellVisual.Kind.IMPACT;
                    if (!hit && event.kind() !=
                            dev.elementalcircuits.application.observation.SpellVisual.Kind.CAST) continue;
                    if (!profile.id().equals(event.customEffectId()) || !event.near(
                            new dev.elementalcircuits.application.observation.SpellVisual.Point(
                                    camera.x, camera.y, camera.z), 80)) continue;
                    var animation = hit ? profile.hit() : profile.cast();
                    float progress = Math.min(1, Math.max(0,
                            (frame.age() + Math.max(0, Math.min(1, partial))) / frame.life()));
                    double age = frame.animationAge() + partial;
                    double pulse = 1 + animation.pulse() * Math.sin(age * .25);
                    double size = event.size() * animation.scale() * pulse;
                    double alpha = animation.opacity() * (1 - progress * .9);
                    var center = new Vector3f((float) (event.from().x() - camera.x),
                            (float) (event.from().y() - camera.y),
                            (float) (event.from().z() - camera.z));
                    var r = new Vector3f(right).mul((float) size);
                    var u = new Vector3f(up).mul((float) size);
                    Vector3f a = new Vector3f(center).sub(r).sub(u);
                    Vector3f b = new Vector3f(center).add(r).sub(u);
                    Vector3f c = new Vector3f(center).add(r).add(u);
                    Vector3f d = new Vector3f(center).sub(r).add(u);
                    int sprite = animation.frame(frame.animationAge());
                    float cellU = (sprite % profile.columns()) * profile.frameWidth()
                                    / (float) profile.textureWidth(),
                            cellV = (sprite / profile.columns()) * profile.frameHeight()
                                    / (float) profile.textureHeight(),
                            cellWidth = profile.frameWidth() / (float) profile.textureWidth(),
                            cellHeight = profile.frameHeight() / (float) profile.textureHeight();
                    if (out == null)
                        out = Tesselator.getInstance().begin(
                                VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                    vertex(out, matrix, new SpellGeometry.V(a.x, a.y, a.z), cellU,
                            cellV + cellHeight, 0xffffff, (int) (alpha * 255));
                    vertex(out, matrix, new SpellGeometry.V(b.x, b.y, b.z), cellU + cellWidth,
                            cellV + cellHeight, 0xffffff, (int) (alpha * 255));
                    vertex(out, matrix, new SpellGeometry.V(c.x, c.y, c.z), cellU + cellWidth,
                            cellV, 0xffffff, (int) (alpha * 255));
                    vertex(out, matrix, new SpellGeometry.V(d.x, d.y, d.z), cellU,
                            cellV, 0xffffff, (int) (alpha * 255));
                    emitted++;
                }
                if (out != null && emitted > 0) {
                    RenderSystem.setShaderTexture(0, profile.texture());
                    var mesh = out.build();
                    if (mesh != null) {
                        BufferUploader.drawWithShader(mesh);
                        lastQuads += emitted;
                        lastDraws++;
                    }
                }
            }
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.setShaderTexture(0, TEXTURE);
        }
    }

    private static SpellGeometry.V vector(Vector3f v) {
        return new SpellGeometry.V(v.x, v.y, v.z);
    }

    private static void vertex(
            BufferBuilder out,
            Matrix4f m,
            SpellGeometry.V p,
            float u,
            float v,
            int color,
            int alpha) {
        out.addVertex(m, (float) p.x(), (float) p.y(), (float) p.z())
                .setColor(color >> 16 & 255, color >> 8 & 255, color & 255, alpha)
                .setUv(u, v);
    }
}
