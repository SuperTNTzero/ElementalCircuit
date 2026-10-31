package dev.elementalcircuits.domain;

/** Integer simulation ticks only. Independent of circuit clock, supply and rendering. */
public record CastCooldown(int remaining, int duration) {
    public static final CastCooldown READY = new CastCooldown(0, 0);
    public CastCooldown {
        if (remaining < 0 || duration < 0 || duration > 1200 || remaining > duration)
            throw new IllegalArgumentException("法杖冷却现场非法");
    }
    public record Rules(int version, int baseTicks) {
        public Rules {
            if (version != 1 || baseTicks < 1 || baseTicks > 300)
                throw new IllegalArgumentException("法杖冷却规则非法");
        }
        public int ticks(long speed) {
            if (speed < Fixed.ONE / 4 || speed > Fixed.ONE * 2)
                throw new IllegalArgumentException("法杖速度非法");
            return Math.toIntExact((baseTicks * Fixed.ONE + speed - 1) / speed);
        }
    }
    public boolean ready() { return remaining == 0; }
    public CastCooldown tick() { return ready() ? this : new CastCooldown(remaining - 1, duration); }
    public CastCooldown release(Rules rules, long speed) {
        if (!ready()) throw new IllegalStateException("法杖尚在冷却");
        int ticks = rules.ticks(speed);
        return new CastCooldown(ticks, ticks);
    }
}
