package dev.elementalcircuits.domain.casting;

/** Speed limiting may brake existing motion only within the paid impulse envelope. */
public final class ConnectionMotion {
    public record Vector(double x, double y, double z) {
        public Vector {
            if (!Double.isFinite(x)
                    || !Double.isFinite(y)
                    || !Double.isFinite(z)
                    || Math.abs(x) > 30000100
                    || Math.abs(y) > 30000100
                    || Math.abs(z) > 30000100)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.casting._connection_motion.001"));
        }

        public Vector plus(Vector b) {
            return new Vector(x + b.x, y + b.y, z + b.z);
        }

        public Vector minus(Vector b) {
            return new Vector(x - b.x, y - b.y, z - b.z);
        }

        public double length() {
            return Math.sqrt(x * x + y * y + z * z);
        }

        public Vector limit(double maximum) {
            double length = length();
            double scale = length > maximum ? maximum / length : 1;
            return new Vector(x * scale, y * scale, z * scale);
        }
    }

    private ConnectionMotion() {}

    public static Vector impulse(
            Vector velocity, Vector request, double maximumImpulse, double maximumSpeed) {
        if (!Double.isFinite(maximumImpulse)
                || !Double.isFinite(maximumSpeed)
                || maximumImpulse < 0
                || maximumSpeed <= 0)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.casting._connection_motion.002"));
        return velocity.plus(request.limit(maximumImpulse))
                .limit(maximumSpeed)
                .minus(velocity)
                .limit(maximumImpulse);
    }
}
