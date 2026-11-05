package dev.elementalcircuits.adapter.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Local presentation preferences. Never queried by authoritative gameplay. */
public final class FeedbackConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue CAMERA, HIT_MARKER;
    public static final ModConfigSpec.DoubleValue MOTION;

    static {
        var b = new ModConfigSpec.Builder();
        CAMERA =
                b.comment(
                                dev.elementalcircuits.text.Texts.get(
                                        "text.elementalcircuits.adapter.config._feedback_config.001"))
                        .define("cameraMotion", true);
        HIT_MARKER =
                b.comment(
                                dev.elementalcircuits.text.Texts.get(
                                        "text.elementalcircuits.adapter.config._feedback_config.002"))
                        .define("hitMarker", true);
        MOTION =
                b.comment(
                                dev.elementalcircuits.text.Texts.get(
                                        "text.elementalcircuits.adapter.config._feedback_config.003"))
                        .defineInRange("motionStrength", .65, 0, 1);
        SPEC = b.build();
    }

    private FeedbackConfig() {}
}
