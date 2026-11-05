package dev.elementalcircuits.client.editor;

import java.util.UUID;

/** Correlated UI requests. Mutations supersede older refreshes; expired work is never retried. */
public final class EditorRequests {
    public static final UUID PUSH = new UUID(0, 0);

    public enum Reply {
        PUSH,
        REFRESH,
        COMMAND,
        STALE
    }

    private record Pending(UUID token, int action, int ticks) {
        Pending next() {
            return new Pending(token, action, ticks + 1);
        }
    }

    private Pending pending;
    private final int timeout;

    public EditorRequests() {
        this(100);
    }

    public EditorRequests(int timeout) {
        if (timeout < 1 || timeout > 1200)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.client.text.UiText.string(
                            "text.elementalcircuits.client.editor._editor_requests.001"));
        this.timeout = timeout;
    }

    public UUID begin(int action) {
        if (pending != null && (pending.action != 5 || action == 5))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.client.text.UiText.string(
                            "text.elementalcircuits.client.editor._editor_requests.002"));
        UUID token;
        do {
            token = UUID.randomUUID();
        } while (token.equals(PUSH) || pending != null && token.equals(pending.token));
        pending = new Pending(token, action, 0);
        return token;
    }

    public Reply complete(UUID token) {
        if (PUSH.equals(token)) return Reply.PUSH;
        if (pending != null && pending.token.equals(token)) {
            Reply result = pending.action == 5 ? Reply.REFRESH : Reply.COMMAND;
            pending = null;
            return result;
        }
        return Reply.STALE;
    }

    public boolean waiting() {
        return pending != null;
    }

    /**
     * Command timeout means unknown result; callers may refresh but must never retry mutations
     * automatically.
     */
    public boolean tick() {
        if (pending == null) return false;
        pending = pending.next();
        if (pending.ticks < timeout) return false;
        boolean command = pending.action != 5;
        pending = null;
        return command;
    }
}
