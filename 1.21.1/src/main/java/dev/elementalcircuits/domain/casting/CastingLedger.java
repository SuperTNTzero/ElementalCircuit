package dev.elementalcircuits.domain.casting;

import dev.elementalcircuits.domain.mana.RefundTicket;

import java.util.*;

/** Persisted outbox: until source acceptance these resources remain owned by this circuit. */
public record CastingLedger(long refunded, long failures, List<RefundTicket> pending) {
    public static final CastingLedger EMPTY = new CastingLedger(0, 0, List.of());

    public CastingLedger {
        if (refunded < 0 || failures < 0 || pending == null || pending.size() > 1024)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.casting._casting_ledger.001"));
        pending = List.copyOf(pending);
        var ids = new HashSet<Long>();
        for (var t : pending)
            if (!ids.add(t.sequence()))
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.casting._casting_ledger.002"));
    }
}
