package dev.elementalcircuits.client.editor;

/** Eight entries per page, with a dead zone for cancelling selection. */
public final class EquipmentWheelModel {
    public static int hover(double dx, double dy, int count) {
        if (!Double.isFinite(dx) || !Double.isFinite(dy) || count < 0 || count > 8)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.client.text.UiText.string(
                            "text.elementalcircuits.client.editor._equipment_wheel_model.001"));
        if (count == 0 || Math.hypot(dx, dy) < 20) return -1;
        double step = Math.PI * 2 / count;
        return Math.floorMod(
                (int) Math.floor((Math.atan2(dy, dx) + Math.PI / 2 + step / 2) / step), count);
    }

    private EquipmentWheelModel() {}
}
