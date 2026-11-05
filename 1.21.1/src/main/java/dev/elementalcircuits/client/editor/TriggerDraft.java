package dev.elementalcircuits.client.editor;

import dev.elementalcircuits.domain.casting.TriggerBinding;

/** An unfinished UI choice is distinct from a valid, persistable trigger binding. */
public record TriggerDraft(
        int source, int target, TriggerBinding.When when, int delay, int percent) {
    public static TriggerDraft empty(int source, int target) {
        return new TriggerDraft(source, target, null, 20, 75);
    }

    public static TriggerDraft from(TriggerBinding b) {
        return new TriggerDraft(b.source(), b.target(), b.when(), b.delay(), b.percent());
    }

    public boolean ready() {
        return when != null;
    }

    public TriggerBinding binding() {
        if (!ready())
            throw new IllegalArgumentException(
                    dev.elementalcircuits.client.text.UiText.string(
                            "text.elementalcircuits.client.editor._trigger_draft.001"));
        return new TriggerBinding(source, target, when, delay, percent);
    }

    public TriggerDraft target(int target) {
        return new TriggerDraft(source, target, when, delay, percent);
    }

    public TriggerDraft condition(TriggerBinding.When when) {
        return new TriggerDraft(
                source, target, java.util.Objects.requireNonNull(when), delay, percent);
    }

    public TriggerDraft delay(int delay) {
        return new TriggerDraft(source, target, when, delay, percent);
    }

    public TriggerDraft percent(int percent) {
        return new TriggerDraft(source, target, when, delay, percent);
    }
}
