package dev.elementalcircuits.domain.mana;

import dev.elementalcircuits.domain.Fixed;

public final class ManaPool implements ManaSource {
    public record State(
            int version,
            long capacity,
            long amount,
            int waitTicks,
            int remainder,
            long spent,
            long equipmentBonus) {
        public State(
                int version, long capacity, long amount, int waitTicks, int remainder, long spent) {
            this(version, capacity, amount, waitTicks, remainder, spent, 0);
        }
    }

    private long capacity, equipmentBonus;
    private long regeneration;
    private int delay;
    private long amount, spent;
    private int waitTicks, remainder;

    public ManaPool(long capacity, long regeneration, int delay) {
        Fixed.checked(capacity);
        Fixed.checked(regeneration);
        if (capacity == 0 || delay < 0 || delay > Integer.MAX_VALUE - 1)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.mana._mana_pool.001"));
        this.capacity = capacity;
        this.regeneration = regeneration;
        this.delay = delay;
        amount = capacity;
    }

    public static ManaPool restore(State s, long regeneration, int delay) {
        if (s == null)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.mana._mana_pool.002"));
        Fixed.checked(s.capacity);
        if ((s.version != 1 && s.version != 2)
                || s.equipmentBonus < 0
                || s.equipmentBonus > Fixed.MAX - s.capacity
                || s.version == 1 && s.equipmentBonus != 0
                || s.amount < 0
                || s.amount > s.capacity + s.equipmentBonus
                || s.waitTicks < 0
                || s.waitTicks > delay
                || s.remainder < 0
                || s.remainder >= 20
                || s.spent < 0
                || s.waitTicks > 0
                        && (s.remainder != 0 || s.amount == s.capacity + s.equipmentBonus))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.mana._mana_pool.003"));
        ManaPool pool = new ManaPool(s.capacity, regeneration, delay);
        pool.equipmentBonus = s.equipmentBonus;
        pool.amount = s.amount;
        pool.waitTicks = s.waitTicks;
        pool.remainder = s.remainder;
        pool.spent = s.spent;
        return pool;
    }

    public long capacity() {
        return capacity + equipmentBonus;
    }

    public long permanentCapacity() {
        return capacity;
    }

    public void equipmentBonus(long bonus) {
        Fixed.checked(bonus);
        equipmentBonus = Math.min(bonus, Fixed.MAX - capacity);
        amount = Math.min(amount, capacity());
        if (amount == capacity()) {
            waitTicks = 0;
            remainder = 0;
        }
    }

    /**
     * Permanent capacity only: never refills, erases recovery delay, or changes the spending
     * ledger.
     */
    public boolean increaseCapacity(long increment, long maximum) {
        Fixed.checked(increment);
        Fixed.checked(maximum);
        if (increment == 0 || capacity >= maximum) return false;
        capacity += Math.min(increment, maximum - capacity);
        equipmentBonus = Math.min(equipmentBonus, Fixed.MAX - capacity);
        return true;
    }

    public long regeneration() {
        return regeneration;
    }

    public int recoveryDelay() {
        return delay;
    }

    /**
     * Administrative adjustment, separate from resource spending. No refill on capacity changes.
     */
    public void setCapacity(long value) {
        Fixed.checked(value);
        if (value == 0)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.mana._mana_pool.004"));
        capacity = value;
        equipmentBonus = Math.min(equipmentBonus, Fixed.MAX - capacity);
        amount = Math.min(amount, capacity());
        if (amount == capacity()) {
            waitTicks = 0;
            remainder = 0;
        }
    }

    public void setAmount(long value) {
        Fixed.checked(value);
        if (value > capacity())
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.mana._mana_pool.005"));
        amount = value;
        if (amount == capacity()) {
            waitTicks = 0;
            remainder = 0;
        }
    }

    /**
     * External consumable energy; does not rewrite spending/refund history or skip recovery delay.
     */
    public long restore(long offered) {
        Fixed.checked(offered);
        long accepted = Math.min(offered, capacity() - amount);
        amount += accepted;
        if (amount == capacity()) {
            waitTicks = 0;
            remainder = 0;
        }
        return accepted;
    }

    public void configureRecovery(long rate, int ticks) {
        Fixed.checked(rate);
        if (ticks < 0 || ticks > Integer.MAX_VALUE - 1)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.mana._mana_pool.006"));
        regeneration = rate;
        delay = ticks;
        waitTicks = Math.min(waitTicks, ticks);
    }

    @Override
    public long available() {
        return amount;
    }

    @Override
    public boolean debit(long value) {
        Fixed.checked(value);
        if (value > amount || spent > Long.MAX_VALUE - value) return false;
        if (value > 0) {
            amount -= value;
            spent += value;
            waitTicks = delay;
            remainder = 0;
        }
        return true;
    }

    public void tick() {
        if (waitTicks > 0) {
            waitTicks--;
            return;
        }
        if (amount == capacity()) {
            remainder = 0;
            return;
        }
        long numerator = regeneration + remainder;
        amount = Math.min(capacity(), amount + numerator / 20);
        remainder = (int) (numerator % 20);
    }

    /**
     * Return only previously spent resource; a full source retains its pending refund elsewhere.
     */
    public long refund(long offered) {
        Fixed.checked(offered);
        long accepted = Math.min(offered, Math.min(spent, capacity() - amount));
        if (accepted > 0) {
            amount += accepted;
            spent -= accepted;
            if (amount == capacity()) {
                waitTicks = 0;
                remainder = 0;
            }
        }
        return accepted;
    }

    public State snapshot() {
        return new State(2, capacity, amount, waitTicks, remainder, spent, equipmentBonus);
    }
}
