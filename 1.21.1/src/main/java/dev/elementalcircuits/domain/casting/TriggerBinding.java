package dev.elementalcircuits.domain.casting;

/** One output slot arms one paid continuation; cycles are allowed and bounded per root chain. */
public record TriggerBinding(int source, int target, When when, int delay, int percent) {
    public enum When implements TriggerCondition {
        IMPACT {
            @Override
            protected Decision decide(Event event, int remainingTicks) {
                return event == Event.TERMINAL ? Decision.FIRE : Decision.WAIT;
            }
        },
        TIMER {
            @Override
            protected Decision decide(Event event, int remainingTicks) {
                return event == Event.TERMINAL || remainingTicks <= 1
                        ? Decision.FIRE
                        : Decision.COUNTDOWN;
            }

            @Override
            public boolean usesDelay() {
                return true;
            }
        };

        protected abstract Decision decide(Event event, int remainingTicks);

        @Override
        public final Decision evaluate(Event event, int remainingTicks) {
            java.util.Objects.requireNonNull(event);
            if (remainingTicks < 1 || remainingTicks > 72000)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.casting._trigger_binding.001"));
            return decide(event, remainingTicks);
        }

        public boolean usesDelay() {
            return false;
        }
    }

    public TriggerBinding {
        if (source < 1
                || source > 65534
                || target < 1
                || target > 65534
                || when == null
                || delay < 1
                || delay > 72000
                || percent < 5
                || percent > 95)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.casting._trigger_binding.002"));
    }

    public static TriggerBinding hit(int source, int target) {
        return new TriggerBinding(source, target, When.IMPACT, 20, 75);
    }
}
