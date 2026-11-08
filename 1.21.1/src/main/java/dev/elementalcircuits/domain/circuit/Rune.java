package dev.elementalcircuits.domain.circuit;

import dev.elementalcircuits.domain.Fixed;
import dev.elementalcircuits.domain.Hex;
import dev.elementalcircuits.domain.casting.ProjectileEffects;
import dev.elementalcircuits.domain.casting.ProjectileRules;
import dev.elementalcircuits.domain.casting.SecondaryRules;
import dev.elementalcircuits.domain.combat.Element;
import dev.elementalcircuits.domain.logic.LogicDefaults;
import dev.elementalcircuits.domain.logic.RuneLogic;
import dev.elementalcircuits.domain.mana.StorageSettings;

import java.util.List;

public record Rune(
        int id,
        Kind kind,
        Hex cell,
        Element element,
        long value,
        boolean on,
        boolean signalControl,
        List<Integer> weights,
        StorageSettings storage,
        String filter) {
    public Rune(
            int id,
            Kind kind,
            Hex cell,
            Element element,
            long value,
            boolean on,
            boolean signalControl,
            List<Integer> weights,
            StorageSettings storage) {
        this(id, kind, cell, element, value, on, signalControl, weights, storage, "");
    }

    public Rune(
            int id,
            Kind kind,
            Hex cell,
            Element element,
            long value,
            boolean on,
            boolean signalControl,
            List<Integer> weights) {
        this(
                id,
                kind,
                cell,
                element,
                value,
                on,
                signalControl,
                weights,
                kind == Kind.STORAGE ? StorageSettings.DEFAULT : StorageSettings.NONE,
                "");
    }

    public enum Kind {
        INPUT(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.circuit._rune.001")),
        ABSORB(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.circuit._rune.002")),
        STORAGE(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.circuit._rune.003")),
        CONVERT(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.circuit._rune.004")),
        AND(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.circuit._rune.005")),
        OR(dev.elementalcircuits.text.Texts.get("text.elementalcircuits.domain.circuit._rune.006")),
        NOT(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.circuit._rune.007")),
        SIGNAL(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.circuit._rune.008")),
        SWITCH(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.circuit._rune.009")),
        SENSOR(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.circuit._rune.010")),
        SPLIT(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.circuit._rune.011")),
        MERGE(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.circuit._rune.012")),
        AREA(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.circuit._rune.013")),
        DIRECTED(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.circuit._rune.014")),
        SPHERE(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.circuit._rune.015")),
        BEAM(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.circuit._rune.016")),
        SHIELD(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.circuit._rune.017")),
        AMOUNT(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.circuit._rune.018")),
        RANGE(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.circuit._rune.019")),
        RADIUS(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.circuit._rune.020")),
        AIM(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.circuit._rune.021")),
        OUTPUT(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.circuit._rune.022")),
        MANA_RECEIVE(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.circuit._rune.023")),
        MANA_SEND(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.circuit._rune.024")),
        SIGNAL_RECEIVE(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.circuit._rune.025")),
        SIGNAL_SEND(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.circuit._rune.026")),
        COUNTER(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.circuit._rune.027")),
        CLOCK(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.circuit._rune.028")),
        TOGGLE(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.circuit._rune.029")),
        BURST(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.circuit._rune.030")),
        BOUNCE(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.circuit._rune.031")),
        HOMING(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.circuit._rune.032")),
        FRAGMENT(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.circuit._rune.033")),
        CHAIN(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.circuit._rune.034")),
        IGNITE(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.circuit._rune.035")),
        SLOW(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.circuit._rune.036")),
        KNOCKBACK(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.circuit._rune.037")),
        FAN(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.circuit._rune.038")),
        WALL(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.circuit._rune.039")),
        RING(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.circuit._rune.040")),
        COLUMN(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.circuit._rune.041")),
        CROSS(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.circuit._rune.042")),
        DOME(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.circuit._rune.043")),
        ITEM_INPUT(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.circuit._rune.044")),
        ITEM_OUTPUT(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.circuit._rune.045")),
        REDSTONE(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.circuit._rune.046")),
        CONTACT(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.circuit._rune.047")),
        FORCE(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.circuit._rune.048")),
        CONSTRAINT(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.circuit._rune.049")),
        INTERACT(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.circuit._rune.050")),
        ANGLE(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.circuit._rune.051")),
        YAW(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.circuit._rune.052")),
        PITCH(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.circuit._rune.053")),
        DISTANCE(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.circuit._rune.054")),
        FILTER(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.circuit._rune.055")),
        SELECT(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.circuit._rune.056")),
        DELAY(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.circuit._rune.057")),
        ANCHOR(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.circuit._rune.058")),
        TARGET(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.circuit._rune.059")),
        PROBE(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.circuit._rune.060")),
        EDGE(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.circuit._rune.061")),
        THROTTLE(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.circuit._rune.062")),
        MATERIAL_FILTER(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.circuit._rune.063")),
        CUSTOM_EFFECT(
                dev.elementalcircuits.text.Texts.get(
                        "rune.elementalcircuits.custom_effect.title"));
        public final String glyph;

        Kind(String glyph) {
            this.glyph = glyph;
        }

        public boolean manaIn() {
            return !RuneLogic.sequential(this)
                    && this != INPUT
                    && this != ABSORB
                    && this != SIGNAL
                    && this != PROBE
                    && this != REDSTONE
                    && this != MANA_RECEIVE
                    && this != SIGNAL_RECEIVE
                    && this != SIGNAL_SEND;
        }

        public boolean manaOut() {
            return !RuneLogic.sequential(this)
                    && this != OUTPUT
                    && this != SIGNAL
                    && this != PROBE
                    && this != REDSTONE
                    && this != MANA_SEND
                    && this != SIGNAL_RECEIVE
                    && this != SIGNAL_SEND;
        }

        public boolean signalIn() {
            return RuneLogic.sequential(this)
                    || this == AND
                    || this == OR
                    || this == NOT
                    || this == SWITCH
                    || this == STORAGE
                    || this == INTERACT
                    || this == CONTACT
                    || this == SELECT
                    || this == SIGNAL_SEND;
        }

        public boolean signalOut() {
            return RuneLogic.sequential(this)
                    || this == AND
                    || this == OR
                    || this == NOT
                    || this == SIGNAL
                    || this == REDSTONE
                    || this == SENSOR
                    || this == STORAGE
                    || this == PROBE
                    || this == SIGNAL_RECEIVE;
        }

        public int outputPorts(Route.Payload payload) {
            if (payload == Route.Payload.SIGNAL) return !signalOut() ? 0 : this == STORAGE ? 3 : 1;
            return !manaOut() ? 0 : this == FILTER || this == SELECT || this == THROTTLE ? 2 : 1;
        }

        public String outputLabel(Route.Payload payload, int port) {
            if (port < 0 || port >= outputPorts(payload))
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.circuit._rune.064"));
            if (payload == Route.Payload.SIGNAL && this == STORAGE)
                return new String[] {
                            dev.elementalcircuits.text.Texts.get(
                                    "text.elementalcircuits.domain.circuit._rune.065"),
                            dev.elementalcircuits.text.Texts.get(
                                    "text.elementalcircuits.domain.circuit._rune.066"),
                            dev.elementalcircuits.text.Texts.get(
                                    "text.elementalcircuits.domain.circuit._rune.067")
                        }
                        [port];
            if (this == FILTER)
                return port == 0
                        ? dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.circuit._rune.068")
                        : dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.circuit._rune.069");
            if (this == SELECT)
                return port == 0
                        ? dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.circuit._rune.070")
                        : dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.circuit._rune.071");
            if (this == THROTTLE)
                return port == 0
                        ? dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.circuit._rune.072")
                        : dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.circuit._rune.073");
            return payload == Route.Payload.MANA
                    ? dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._rune.074")
                    : dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._rune.075");
        }
    }

    public Rune {
        if (id < 1
                || kind == null
                || cell == null
                || element == null
                || weights == null
                || filter == null)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._rune.076"));
        if (kind == Kind.MATERIAL_FILTER && !filter.isEmpty()) {
            String key = filter.startsWith("#") ? filter.substring(1) : filter;
            if (!key.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.circuit._rune.077"));
        } else if (kind == Kind.CUSTOM_EFFECT && !filter.isEmpty()) {
            if (!filter.matches("[a-z0-9_.-]{1,48}"))
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.circuit._rune.078"));
        } else if (kind != Kind.MATERIAL_FILTER && kind != Kind.CUSTOM_EFFECT && !filter.isEmpty()) {
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._rune.078"));
        }
        if (kind != Kind.COUNTER
                && kind != Kind.CLOCK
                && kind != Kind.DELAY
                && kind != Kind.YAW
                && kind != Kind.PITCH) Fixed.checked(value);
        if (kind == Kind.DELAY
                && (value < Fixed.ONE
                        || value > CircuitBounds.DELAY_TICKS * Fixed.ONE
                        || value % Fixed.ONE != 0))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._rune.079"));
        if ((kind == Kind.TARGET || kind == Kind.PROBE)
                && (value < 0 || value > 3 * Fixed.ONE || value % Fixed.ONE != 0))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._rune.080"));
        if (kind == Kind.EDGE && (value < 0 || value > 2 || value % Fixed.ONE != 0))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._rune.081"));
        if (kind == Kind.THROTTLE && (value < 1 || value > Fixed.MAX))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._rune.082"));
        if (kind == Kind.SENSOR
                && (value < 1
                        || !weights.isEmpty()
                                && (weights.size() != 1
                                        || weights.getFirst() < 0
                                        || weights.getFirst() >= value
                                        || value > Integer.MAX_VALUE)))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._rune.083"));
        if (kind == Kind.YAW && (value < -180 * Fixed.ONE || value > 180 * Fixed.ONE)
                || kind == Kind.PITCH && (value < -90 * Fixed.ONE || value > 90 * Fixed.ONE)
                || kind == Kind.ANGLE && (value < Fixed.ONE || value > 180 * Fixed.ONE)
                || kind == Kind.DISTANCE && (value < Fixed.ONE / 2 || value > 256 * Fixed.ONE))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._rune.084"));
        if ((kind == Kind.ITEM_INPUT || kind == Kind.ITEM_OUTPUT)
                && (value % Fixed.ONE != 0 || value > 5 * Fixed.ONE))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._rune.085"));
        if (kind == Kind.REDSTONE
                && (value % Fixed.ONE != 0 || value < Fixed.ONE || value > 15 * Fixed.ONE))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._rune.086"));
        if ((kind == Kind.COUNTER
                        && (value < Fixed.ONE
                                || value > LogicDefaults.MAX_COUNT * Fixed.ONE
                                || value % Fixed.ONE != 0))
                || (kind == Kind.CLOCK
                        && (value < 2 * Fixed.ONE
                                || value > LogicDefaults.MAX_PERIOD * Fixed.ONE
                                || value % Fixed.ONE != 0)))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._rune.087"));
        if (ProjectileEffects.rune(kind)) {
            int min = kind == Kind.HOMING || kind == Kind.FRAGMENT ? 2 : 1;
            int max =
                    kind == Kind.HOMING
                            ? CircuitBounds.RANGE
                            : kind == Kind.BURST
                                    ? CircuitBounds.RADIUS
                                    : kind == Kind.IGNITE || kind == Kind.SLOW
                                            ? CircuitBounds.DURATION
                                            : CircuitBounds.DERIVATIVES;
            if (value % Fixed.ONE != 0 || value < min * Fixed.ONE || value > max * Fixed.ONE)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.circuit._rune.088"));
        }
        if (storage == null
                || kind == Kind.STORAGE && storage.equals(StorageSettings.NONE)
                || kind != Kind.STORAGE && !storage.equals(StorageSettings.NONE))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._rune.089"));
        if ((kind == Kind.RANGE || kind == Kind.RADIUS) && value % Fixed.ONE != 0)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._rune.090"));
        weights = List.copyOf(weights);
        int maxWeight = kind == Kind.SENSOR ? Integer.MAX_VALUE : 1000000;
        if (weights.size() > CircuitBounds.ROUTES
                || weights.stream().anyMatch(w -> w == null || w < 0 || w > maxWeight))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._rune.091"));
        if ((kind == Kind.RANGE && (value < Fixed.ONE || value > CircuitBounds.RANGE * Fixed.ONE))
                || (kind == Kind.RADIUS
                        && (value < Fixed.ONE || value > CircuitBounds.RADIUS * Fixed.ONE))
                || (kind == Kind.AIM && value > Fixed.ONE)
                || (kind == Kind.AMOUNT && (value == 0 || value > Fixed.MAX)))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._rune.092"));
    }

    public static Rune create(int id, Kind kind, Hex cell) {
        return create(id, kind, cell, LogicDefaults.protocolV1());
    }

    public static Rune create(int id, Kind kind, Hex cell, LogicDefaults defaults) {
        return create(id, kind, cell, defaults, ProjectileRules.protocolV1());
    }

    public static Rune create(
            int id, Kind kind, Hex cell, LogicDefaults defaults, ProjectileRules projectiles) {
        return create(id, kind, cell, defaults, projectiles, SecondaryRules.protocolV1());
    }

    public static Rune create(
            int id,
            Kind kind,
            Hex cell,
            LogicDefaults defaults,
            ProjectileRules projectiles,
            SecondaryRules secondary) {
        long v =
                switch (kind) {
                    case FRAGMENT, CHAIN, IGNITE, SLOW, KNOCKBACK -> secondary.defaultValue(kind);
                    case BURST, BOUNCE, HOMING -> projectiles.defaultValue(kind);
                    case COUNTER -> defaults.counterThreshold() * Fixed.ONE;
                    case CLOCK -> defaults.clockPeriod() * Fixed.ONE;
                    case DELAY -> 10 * Fixed.ONE;
                    case THROTTLE -> 4 * Fixed.ONE;
                    case RANGE -> 12 * Fixed.ONE;
                    case RADIUS -> 2 * Fixed.ONE;
                    case AIM -> 8192;
                    case ANGLE -> 70 * Fixed.ONE;
                    case EDGE -> 0;
                    case DISTANCE -> 3 * Fixed.ONE;
                    case AMOUNT, SENSOR, REDSTONE, FORCE, CONSTRAINT -> Fixed.ONE;
                    default -> 0;
                };
        return new Rune(
                id,
                kind,
                cell,
                Element.FIRE,
                v,
                kind != Kind.TOGGLE,
                false,
                kind == Kind.SENSOR ? List.of(0) : List.of(1));
    }
}
