package dev.elementalcircuits.domain.casting;

import dev.elementalcircuits.domain.Fixed;
import dev.elementalcircuits.domain.circuit.CircuitBounds;
import dev.elementalcircuits.domain.circuit.Rune;

/** Independent modifier dimensions; zero/UNSET means not yet specified. */
public record SpellSpec(
        Method method,
        Shape shape,
        long amount,
        int range,
        int radius,
        int aim,
        ProjectileEffects effects,
        long damageMultiplier,
        boolean reactive,
        EffectScale effectScale,
        MaterialPorts materials,
        ConnectionEffects connection,
        SpatialParameters spatial) {
    public SpellSpec(
            Method method,
            Shape shape,
            long amount,
            int range,
            int radius,
            int aim,
            ProjectileEffects effects,
            long damageMultiplier,
            boolean reactive,
            EffectScale effectScale,
            MaterialPorts materials,
            ConnectionEffects connection) {
        this(
                method,
                shape,
                amount,
                range,
                radius,
                aim,
                effects,
                damageMultiplier,
                reactive,
                effectScale,
                materials,
                connection,
                SpatialParameters.NONE);
    }

    public SpellSpec withSpatial(SpatialParameters value) {
        return new SpellSpec(
                method,
                shape,
                amount,
                range,
                radius,
                aim,
                effects,
                damageMultiplier,
                reactive,
                effectScale,
                materials,
                connection,
                value);
    }

    public SpellSpec(
            Method method,
            Shape shape,
            long amount,
            int range,
            int radius,
            int aim,
            ProjectileEffects effects,
            long damageMultiplier,
            boolean reactive,
            EffectScale effectScale,
            MaterialPorts materials) {
        this(
                method,
                shape,
                amount,
                range,
                radius,
                aim,
                effects,
                damageMultiplier,
                reactive,
                effectScale,
                materials,
                ConnectionEffects.NONE);
    }

    public SpellSpec withConnection(ConnectionEffects value) {
        return new SpellSpec(
                method,
                shape,
                amount,
                range,
                radius,
                aim,
                effects,
                damageMultiplier,
                reactive,
                effectScale,
                materials,
                value,
                spatial);
    }

    public SpellSpec(
            Method method,
            Shape shape,
            long amount,
            int range,
            int radius,
            int aim,
            ProjectileEffects effects,
            long damageMultiplier,
            boolean reactive,
            EffectScale effectScale) {
        this(
                method,
                shape,
                amount,
                range,
                radius,
                aim,
                effects,
                damageMultiplier,
                reactive,
                effectScale,
                MaterialPorts.NONE);
    }

    public SpellSpec withMaterials(MaterialPorts ports) {
        return new SpellSpec(
                method,
                shape,
                amount,
                range,
                radius,
                aim,
                effects,
                damageMultiplier,
                reactive,
                effectScale,
                ports,
                connection,
                spatial);
    }

    public SpellSpec(
            Method method,
            Shape shape,
            long amount,
            int range,
            int radius,
            int aim,
            ProjectileEffects effects,
            long damageMultiplier,
            boolean reactive) {
        this(
                method,
                shape,
                amount,
                range,
                radius,
                aim,
                effects,
                damageMultiplier,
                reactive,
                EffectScale.IDENTITY);
    }

    public SpellSpec withEffectScale(EffectScale scale) {
        return new SpellSpec(
                method,
                shape,
                amount,
                range,
                radius,
                aim,
                effects,
                damageMultiplier,
                reactive,
                scale,
                materials,
                connection,
                spatial);
    }

    public SpellSpec(
            Method method,
            Shape shape,
            long amount,
            int range,
            int radius,
            int aim,
            ProjectileEffects effects,
            long damageMultiplier) {
        this(method, shape, amount, range, radius, aim, effects, damageMultiplier, false);
    }

    public SpellSpec withReactive(boolean value) {
        return new SpellSpec(
                method,
                shape,
                amount,
                range,
                radius,
                aim,
                effects,
                damageMultiplier,
                value,
                effectScale,
                materials,
                connection,
                spatial);
    }

    public SpellSpec(
            Method method,
            Shape shape,
            long amount,
            int range,
            int radius,
            int aim,
            ProjectileEffects effects) {
        this(method, shape, amount, range, radius, aim, effects, Fixed.ONE);
    }

    public SpellSpec withDamageMultiplier(long multiplier) {
        return new SpellSpec(
                method,
                shape,
                amount,
                range,
                radius,
                aim,
                effects,
                multiplier,
                reactive,
                effectScale,
                materials,
                connection,
                spatial);
    }

    public SpellSpec(Method method, Shape shape, long amount, int range, int radius, int aim) {
        this(method, shape, amount, range, radius, aim, ProjectileEffects.NONE);
    }

    public enum Method {
        UNSET,
        AREA,
        DIRECTED,
        CONTACT
    }

    public enum Shape {
        UNSET,
        SPHERE,
        BEAM,
        SHIELD,
        FAN,
        WALL,
        RING,
        COLUMN,
        CROSS,
        DOME
    }

    public static final SpellSpec EMPTY = new SpellSpec(Method.UNSET, Shape.UNSET, 0, 0, 0, -1);

    public SpellSpec {
        if (spatial == null
                || connection == null
                || materials == null
                || effectScale == null
                || damageMultiplier < Fixed.ONE
                || damageMultiplier > 16 * Fixed.ONE
                || effects == null
                || method == null
                || shape == null
                || amount < 0
                || amount > Fixed.MAX
                || range < 0
                || range > CircuitBounds.RANGE
                || radius < 0
                || radius > CircuitBounds.RADIUS
                || aim < -1
                || aim > Fixed.ONE)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.casting._spell_spec.001"));
    }

    private static long combine(long a, long b, long unset) {
        if (a == unset) return b;
        if (b == unset || a == b) return a;
        throw new IllegalArgumentException(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.casting._spell_spec.002"));
    }

    public SpellSpec merge(SpellSpec b) {
        return new SpellSpec(
                Method.values()[(int) combine(method.ordinal(), b.method.ordinal(), 0)],
                Shape.values()[(int) combine(shape.ordinal(), b.shape.ordinal(), 0)],
                combine(amount, b.amount, 0),
                (int) combine(range, b.range, 0),
                (int) combine(radius, b.radius, 0),
                (int) combine(aim, b.aim, -1),
                effects.merge(b.effects),
                combine(damageMultiplier, b.damageMultiplier, Fixed.ONE),
                reactive || b.reactive,
                effectScale.equals(EffectScale.IDENTITY)
                        ? b.effectScale
                        : b.effectScale.equals(EffectScale.IDENTITY)
                                        || effectScale.equals(b.effectScale)
                                ? effectScale
                                : incompatibleScale(),
                materials.merge(b.materials),
                connection.merge(b.connection),
                spatial.merge(b.spatial));
    }

    private static EffectScale incompatibleScale() {
        throw new IllegalArgumentException(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.casting._spell_spec.003"));
    }

    public SpellSpec modify(Rune rune) {
        if (rune.kind() == Rune.Kind.MATERIAL_FILTER)
            return withMaterials(materials.merge(new MaterialPorts(-1, -1, null, rune.filter())));
        if (rune.kind() == Rune.Kind.ANCHOR || rune.kind() == Rune.Kind.TARGET)
            return withConnection(
                    connection.merge(
                            new ConnectionEffects(
                                    0,
                                    0,
                                    false,
                                    false,
                                    rune.kind() == Rune.Kind.ANCHOR,
                                    rune.kind() == Rune.Kind.TARGET
                                            ? (int) (rune.value() / Fixed.ONE)
                                            : 0,
                                    0)));
        if (rune.kind() == Rune.Kind.ANGLE
                || rune.kind() == Rune.Kind.YAW
                || rune.kind() == Rune.Kind.PITCH
                || rune.kind() == Rune.Kind.DISTANCE)
            return withSpatial(
                    spatial.merge(
                            new SpatialParameters(
                                    rune.kind() == Rune.Kind.ANGLE ? rune.value() : 0,
                                    rune.kind() == Rune.Kind.YAW ? rune.value() : 0,
                                    rune.kind() == Rune.Kind.PITCH ? rune.value() : 0,
                                    rune.kind() == Rune.Kind.DISTANCE ? rune.value() : 0)));
        if (rune.kind() == Rune.Kind.FORCE
                || rune.kind() == Rune.Kind.CONSTRAINT
                || rune.kind() == Rune.Kind.INTERACT) {
            return withConnection(
                    connection.merge(
                            new ConnectionEffects(
                                    rune.kind() == Rune.Kind.FORCE ? rune.value() : 0,
                                    rune.kind() == Rune.Kind.CONSTRAINT ? rune.value() : 0,
                                    rune.kind() == Rune.Kind.INTERACT,
                                    false)));
        }
        if (rune.kind() == Rune.Kind.ITEM_INPUT || rune.kind() == Rune.Kind.ITEM_OUTPUT) {
            int side = (int) (rune.value() / Fixed.ONE);
            return withMaterials(
                    materials.merge(
                            new MaterialPorts(
                                    rune.kind() == Rune.Kind.ITEM_INPUT ? side : -1,
                                    rune.kind() == Rune.Kind.ITEM_OUTPUT ? side : -1,
                                    null)));
        }
        SpellSpec m =
                switch (rune.kind()) {
                    case AREA -> new SpellSpec(Method.AREA, Shape.UNSET, 0, 0, 0, -1);
                    case DIRECTED -> new SpellSpec(Method.DIRECTED, Shape.UNSET, 0, 0, 0, -1);
                    case CONTACT ->
                            new SpellSpec(Method.CONTACT, Shape.UNSET, 0, 0, 0, -1)
                                    .withConnection(
                                            new ConnectionEffects(
                                                    0, 0, false, false, false, 0, rune.id()));
                    case SPHERE -> new SpellSpec(Method.UNSET, Shape.SPHERE, 0, 0, 0, -1);
                    case BEAM -> new SpellSpec(Method.UNSET, Shape.BEAM, 0, 0, 0, -1);
                    case SHIELD -> new SpellSpec(Method.UNSET, Shape.SHIELD, 0, 0, 0, -1);
                    case FAN -> new SpellSpec(Method.UNSET, Shape.FAN, 0, 0, 0, -1);
                    case WALL -> new SpellSpec(Method.UNSET, Shape.WALL, 0, 0, 0, -1);
                    case RING -> new SpellSpec(Method.UNSET, Shape.RING, 0, 0, 0, -1);
                    case COLUMN -> new SpellSpec(Method.UNSET, Shape.COLUMN, 0, 0, 0, -1);
                    case CROSS -> new SpellSpec(Method.UNSET, Shape.CROSS, 0, 0, 0, -1);
                    case DOME -> new SpellSpec(Method.UNSET, Shape.DOME, 0, 0, 0, -1);
                    case AMOUNT -> new SpellSpec(Method.UNSET, Shape.UNSET, rune.value(), 0, 0, -1);
                    case RANGE ->
                            new SpellSpec(
                                    Method.UNSET,
                                    Shape.UNSET,
                                    0,
                                    (int) (rune.value() / Fixed.ONE),
                                    0,
                                    -1);
                    case RADIUS ->
                            new SpellSpec(
                                    Method.UNSET,
                                    Shape.UNSET,
                                    0,
                                    0,
                                    (int) (rune.value() / Fixed.ONE),
                                    -1);
                    case AIM ->
                            new SpellSpec(Method.UNSET, Shape.UNSET, 0, 0, 0, (int) rune.value());
                    case BURST, BOUNCE, HOMING, FRAGMENT, CHAIN, IGNITE, SLOW, KNOCKBACK ->
                            new SpellSpec(
                                    Method.UNSET,
                                    Shape.UNSET,
                                    0,
                                    0,
                                    0,
                                    -1,
                                    ProjectileEffects.from(rune));
                    default -> EMPTY;
                };
        return merge(m);
    }

    public boolean compatibleOutput() {
        if (spatial.angle() > 0 && shape != Shape.FAN) return false;
        if (spatial.distance() > 0 && method != Method.CONTACT) return false;
        if (method == Method.CONTACT)
            return shape == Shape.UNSET
                    && radius == 0
                    && effects.empty()
                    && materials.equals(MaterialPorts.NONE);
        if (!connection.empty()) return false;
        return (!ShapeVolume.shaped(shape) || effects.empty())
                && (!(shape == Shape.FAN || shape == Shape.WALL) || method == Method.DIRECTED)
                && !(shape == Shape.FAN && range != 0)
                && !(method != Method.DIRECTED && (shape == Shape.BEAM || shape == Shape.SHIELD))
                && !(shape == Shape.BEAM && radius != 0)
                && !(shape == Shape.SHIELD && range != 0)
                && (effects.empty() || method == Method.DIRECTED && shape != Shape.SHIELD)
                && (effects.burst() == 0 || radius == 0 || radius == effects.burst())
                && (effects.bounces() == 0 && effects.homing() == 0
                        || shape == Shape.SPHERE
                        || shape == Shape.UNSET);
    }
}
