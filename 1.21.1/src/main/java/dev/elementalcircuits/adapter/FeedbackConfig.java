package dev.elementalcircuits.adapter;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Local presentation preferences. Never queried by authoritative gameplay. */
public final class FeedbackConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue CAMERA,HIT_MARKER;
    public static final ModConfigSpec.DoubleValue MOTION;
    static{
        var b=new ModConfigSpec.Builder();
        CAMERA=b.comment("启用施法轻微镜头后坐；只影响画面，不改变玩家朝向或弹道。").define("cameraMotion",true);
        HIT_MARKER=b.comment("成功伤害由服务器确认后显示命中准星。").define("hitMarker",true);
        MOTION=b.comment("镜头反馈幅度，0完全关闭；不包含全屏闪白或FOV缩放。").defineInRange("motionStrength",.65,0,1);
        SPEC=b.build();
    }
    private FeedbackConfig(){}
}
