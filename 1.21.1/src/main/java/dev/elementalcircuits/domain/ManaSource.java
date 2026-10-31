package dev.elementalcircuits.domain;

/** Server-owned source. Debit must be all-or-nothing, including its recovery delay. */
public interface ManaSource {
    ManaSource NONE=new ManaSource(){public long available(){return 0;}public boolean debit(long amount){Fixed.checked(amount);return amount==0;}};
    long available();
    boolean debit(long amount);
}
