package dev.elementalcircuits.client.editor;

/**
 * Angular selection with a centre cancellation zone; independent of mouse polling and rendering.
 */
public final class SpellWheelModel {
    private SpellWheelModel() {}

    public static int hover(double dx, double dy, int count) {
        if (!Double.isFinite(dx) || !Double.isFinite(dy) || count < 1 || count > 8)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.client.text.UiText.string(
                            "text.elementalcircuits.client.editor._spell_wheel_model.001"));
        if (Math.hypot(dx, dy) < 20) return -1;
        double angle = Math.atan2(dy, dx) + Math.PI / 2, step = Math.PI * 2 / count;
        return Math.floorMod((int) Math.floor((angle + step / 2) / step), count);
    }

    public static double radius(int width, int height) {
        if (width < 1 || height < 1)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.client.text.UiText.string(
                            "text.elementalcircuits.client.editor._spell_wheel_model.002"));
        return Math.max(24, Math.min(100, Math.min(width, height) * .28));
    }
}
