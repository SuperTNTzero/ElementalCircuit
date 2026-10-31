package dev.elementalcircuits.domain;

public final class ManaPool implements ManaSource {
    public record State(int version, long capacity, long amount, int waitTicks, int remainder, long spent) { }
    private final long capacity, regeneration;
    private final int delay;
    private long amount, spent;
    private int waitTicks, remainder;
    public ManaPool(long capacity, long regeneration, int delay) {
        Fixed.checked(capacity); Fixed.checked(regeneration);
        if (capacity == 0 || delay < 0 || delay > 12000) throw new IllegalArgumentException("非法魔力槽规则");
        this.capacity = capacity; this.regeneration = regeneration; this.delay = delay; amount = capacity;
    }
    public static ManaPool restore(State s, long regeneration, int delay) {
        if (s == null || s.version != 1 || s.amount < 0 || s.amount > s.capacity || s.waitTicks < 0 || s.waitTicks > delay
            || s.remainder < 0 || s.remainder >= 20 || s.spent < 0 || s.waitTicks>0 && (s.remainder!=0 || s.amount==s.capacity)) throw new IllegalArgumentException("魔力槽现场损坏");
        ManaPool pool = new ManaPool(s.capacity,regeneration,delay);
        pool.amount=s.amount; pool.waitTicks=s.waitTicks; pool.remainder=s.remainder; pool.spent=s.spent;
        return pool;
    }
    public long capacity() { return capacity; }
    @Override public long available() { return amount; }
    @Override public boolean debit(long value) {
        Fixed.checked(value);
        if (value > amount || spent > Long.MAX_VALUE-value) return false;
        if (value > 0) { amount -= value; spent += value; waitTicks=delay; remainder=0; }
        return true;
    }
    public void tick() {
        if (waitTicks > 0) { waitTicks--; return; }
        if (amount == capacity) { remainder=0; return; }
        long numerator = regeneration+remainder;
        amount = Math.min(capacity,amount+numerator/20); remainder=(int)(numerator%20);
    }
    public State snapshot() { return new State(1,capacity,amount,waitTicks,remainder,spent); }
}
