package dev.elementalcircuits.domain.casting;

/** Pure trigger policy: authority supplies events; the policy never applies world effects. */
public interface TriggerCondition {
    enum Event {
        FLIGHT_TICK,
        TERMINAL
    }

    enum Decision {
        WAIT,
        COUNTDOWN,
        FIRE
    }

    Decision evaluate(Event event, int remainingTicks);
}
