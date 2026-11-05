package dev.elementalcircuits.server.mana;

import dev.elementalcircuits.adapter.config.ServerSettings;

import net.minecraft.nbt.*;

/**
 * Receiver-side cumulative receipts share the recipient's persistence unit with the credited mana.
 */
public final class RefundReceipts {
    public static final String KEY = "elementalcircuits_refund_receipts";

    private RefundReceipts() {}

    public static long credit(
            CompoundTag owner,
            String id,
            long total,
            java.util.function.LongUnaryOperator deposit) {
        if (owner.contains(KEY) && !owner.contains(KEY, Tag.TAG_COMPOUND))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.server.mana._refund_receipts.001"));
        var receipts = owner.getCompound(KEY);
        if (receipts.contains(id) && !receipts.contains(id, Tag.TAG_LONG))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.server.mana._refund_receipts.002"));
        long old = receipts.getLong(id);
        if (old < 0 || old > total)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.server.mana._refund_receipts.003"));
        if (old == total
                || !receipts.contains(id)
                        && receipts.size() >= ServerSettings.count("casting.refundClaimBudget"))
            return old;
        long accepted = deposit.applyAsLong(total - old);
        if (accepted < 0 || accepted > total - old)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.server.mana._refund_receipts.004"));
        if (accepted > 0) {
            receipts.putLong(id, old + accepted);
            owner.put(KEY, receipts);
        }
        return old + accepted;
    }
}
