package dev.elementalcircuits.client.effect;

import dev.elementalcircuits.application.observation.*;

import java.util.*;

/** Local lifetime only: cannot collide, select targets, apply damage or advance authority. */
public final class VisualTimeline {
    public record Frame(SpellVisual event, int age, int life, int animationAge) {
        public Frame(SpellVisual event, int age, int life) {
            this(event, age, life, age);
        }
    }

    private final LinkedHashMap<SpellVisual, Frame> frames = new LinkedHashMap<>();
    private List<Frame> published = List.of();
    private boolean dirty;

    public void add(Collection<SpellVisual> events) {
        for (var event : events) {
            var existing = frames.get(event);
            // Refresh stationary beams/barriers rather than stacking identical translucent
            // surfaces.
            // Identical contacts retain their current burst for six steps, avoiding a permanent
            // flash.
            if (existing != null && event.kind() == SpellVisual.Kind.IMPACT && existing.age() < 6)
                continue;
            if (existing == null && frames.size() == VisualBudget.CLIENT_LIMIT)
                frames.remove(frames.keySet().iterator().next());
            frames.put(
                    event,
                    new Frame(
                            event,
                            0,
                            lifetime(event),
                            existing == null ? 0 : existing.animationAge));
            dirty = true;
        }
    }

    private static int lifetime(SpellVisual event) {
        boolean hit = event.kind() == SpellVisual.Kind.IMPACT;
        var animation = CustomEffectProfiles.get(event.customEffectId(), hit);
        if (animation == null) return VisualStyles.of(event.kind()).ticks();
        if (animation.loop()) return VisualStyles.of(event.kind()).ticks();
        return Math.max(1, Math.min(40,
                (int) Math.ceil(animation.frames().length * 20f / animation.fps())));
    }

    public void tick() {
        if (!frames.isEmpty()) dirty = true;
        var it = frames.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            var f = entry.getValue();
            if (f.age + 1 >= f.life) it.remove();
            else
                entry.setValue(new Frame(f.event, f.age + 1, f.life, (f.animationAge + 1) % 24000));
        }
    }

    public List<Frame> frames() {
        if (dirty) {
            published = List.copyOf(frames.values());
            dirty = false;
        }
        return published;
    }

    public void clear() {
        frames.clear();
        published = List.of();
        dirty = false;
    }
}
