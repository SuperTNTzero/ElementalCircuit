package dev.elementalcircuits.domain.mana;

public record RefundTicket(long sequence, Mixture mana) {
    public RefundTicket {
        if (sequence < 0 || mana == null || mana.empty())
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.mana._refund_ticket.001"));
    }
}
