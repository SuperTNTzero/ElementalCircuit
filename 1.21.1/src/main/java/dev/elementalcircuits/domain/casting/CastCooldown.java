package dev.elementalcircuits.domain.casting;

import dev.elementalcircuits.domain.Fixed;
import dev.elementalcircuits.domain.circuit.Rules;

/** Integer simulation ticks only. Independent of circuit clock, supply and rendering. */
public record CastCooldown(int remaining, int duration) {
    public static final CastCooldown READY = new CastCooldown(0, 0);

    public CastCooldown {
        if (remaining < 0 || duration < 0 || duration > 1200 || remaining > duration)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.casting._cast_cooldown.001"));
    }

    public record Rules(int version, int baseTicks) {
        public Rules {
            if (version != 1 || baseTicks < 1 || baseTicks > 300)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.casting._cast_cooldown.002"));
        }

        public int ticks(long speed) {
            if (speed < Fixed.ONE / 4 || speed > Fixed.ONE * 2)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.casting._cast_cooldown.003"));
            return Math.toIntExact((baseTicks * Fixed.ONE + speed - 1) / speed);
        }
    }

    public boolean ready() {
        return remaining == 0;
    }

    public CastCooldown tick() {
        return ready() ? this : new CastCooldown(remaining - 1, duration);
    }

    public CastCooldown release(Rules rules, long speed) {
        if (!ready())
            throw new IllegalStateException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.casting._cast_cooldown.004"));
        int ticks = rules.ticks(speed);
        return new CastCooldown(ticks, ticks);
    }
}
