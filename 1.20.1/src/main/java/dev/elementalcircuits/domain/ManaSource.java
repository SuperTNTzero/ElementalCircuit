package dev.elementalcircuits.domain;

/** Server-owned source. Debit must be all-or-nothing, including its recovery delay. */
public interface ManaSource {
    long available();
    boolean debit(long amount);
}
