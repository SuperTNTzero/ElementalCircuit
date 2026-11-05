package dev.elementalcircuits.application.observation;

import java.util.*;

/** Coalesces nearby repeats before native sound packets are sent; all containers are bounded. */
public final class SoundBudget {
    private record Cell(SpellSound.Kind kind, int x, int y, int z) {}

    private final AudioPolicy policy;
    private final List<SpellSound> queue = new ArrayList<>();
    private final Map<Cell, Long> recent = new HashMap<>();
    private long tick = -1;
    private int accepted;

    public SoundBudget(AudioPolicy policy) {
        this.policy = Objects.requireNonNull(policy);
    }

    private void advance(long now) {
        if (now < 0)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.application.observation._sound_budget.001"));
        if (now == tick) return;
        // Player commands may arrive after the previous world's END event. Keep them until flush.
        accepted = 0;
        if (now < tick) {
            recent.clear();
            queue.clear();
        } else recent.entrySet().removeIf(e -> now - e.getValue() >= policy.maxCooldown());
        tick = now;
    }

    public boolean add(long now, SpellSound event) {
        Objects.requireNonNull(event);
        advance(now);
        var p = event.position();
        int size = policy.cellSize();
        var key =
                new Cell(
                        event.kind(),
                        (int) Math.floor(p.x() / size),
                        (int) Math.floor(p.y() / size),
                        (int) Math.floor(p.z() / size));
        var last = recent.get(key);
        if (accepted >= policy.worldLimit()
                || queue.size() >= policy.worldLimit()
                || last != null && now - last < policy.cooldowns().get(event.kind())) return false;
        accepted++;
        recent.put(key, now);
        queue.add(event);
        return true;
    }

    public List<SpellSound> snapshot() {
        return List.copyOf(queue);
    }

    public List<SpellSound> audible(SpellVisual.Point listener) {
        return queue.stream()
                .filter(
                        s ->
                                s.position().distanceSquared(listener)
                                        <= policy.range() * policy.range())
                .sorted(Comparator.comparingDouble(s -> s.position().distanceSquared(listener)))
                .limit(policy.playerLimit())
                .toList();
    }

    public void finish(long now) {
        advance(now);
        queue.clear();
    }

    public int remembered() {
        return recent.size();
    }
}
