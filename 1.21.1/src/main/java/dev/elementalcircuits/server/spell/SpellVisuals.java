package dev.elementalcircuits.server.spell;

import dev.elementalcircuits.application.observation.*;
import dev.elementalcircuits.domain.combat.Element;
import dev.elementalcircuits.network.VisualNetwork;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Server-thread observation outbox. Disposable bounded visuals never gate authoritative effects.
 */
public final class SpellVisuals {
    private static final Map<ServerLevel, VisualBudget> OUTBOX = new WeakHashMap<>();

    private SpellVisuals() {}

    private static SpellVisual.Point point(Vec3 p) {
        return new SpellVisual.Point(p.x, p.y, p.z);
    }

    public static void observe(
            ServerLevel level,
            SpellVisual.Kind kind,
            Element element,
            Vec3 from,
            Vec3 to,
            float size,
            int intensity) {
        observe(level, kind, element, from, to, size, intensity, 0);
    }

    public static void observe(
            ServerLevel level,
            SpellVisual.Kind kind,
            Element element,
            Vec3 from,
            Vec3 to,
            float size,
            int intensity,
            float aperture) {
        OUTBOX.computeIfAbsent(level, l -> new VisualBudget())
                .add(
                        new SpellVisual(
                                kind, element, point(from), point(to), size, intensity, aperture));
    }

    public static void observe(
            ServerLevel level, SpellVisual.Kind kind, Element element, Vec3 from, Vec3 to,
            float size, int intensity, String customEffectId) {
        OUTBOX.computeIfAbsent(level, l -> new VisualBudget())
                .add(new SpellVisual(kind, element, point(from), point(to), size, intensity, 0,
                        customEffectId));
    }

    public static void observe(
            ServerLevel level, SpellVisual.Kind kind, Element element, Vec3 from, Vec3 to,
            float size, int intensity, float aperture, String customEffectId) {
        OUTBOX.computeIfAbsent(level, l -> new VisualBudget())
                .add(new SpellVisual(kind, element, point(from), point(to), size, intensity,
                        aperture, customEffectId));
    }

    public static List<SpellVisual> pending(ServerLevel level) {
        var q = OUTBOX.get(level);
        return q == null ? List.of() : q.snapshot();
    }

    public static void flush(ServerLevel level) {
        VisualBudget queue = OUTBOX.remove(level);
        if (queue == null) return;
        for (var player : level.players())
            for (var batch : queue.batches(point(player.position())))
                VisualNetwork.send(
                        player,
                        new VisualNetwork.Batch(
                                level.dimension().location(), level.getGameTime(), batch));
    }

    public static void unload(ServerLevel level) {
        OUTBOX.remove(level);
    }
}
