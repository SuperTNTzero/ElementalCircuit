package dev.elementalcircuits.domain;

import java.util.List;

public record Rune(int id, Kind kind, Hex cell, Element element, long value, boolean on, boolean signalControl, List<Integer> weights) {
    public enum Kind {
        INPUT("入"), ABSORB("汲"), CONVERT("元"), AND("与"), OR("或"), NOT("反"), SIGNAL("信"), SWITCH("阀"), SENSOR("感"),
        SPLIT("分"), MERGE("汇"), AREA("域"), DIRECTED("向"), SPHERE("球"), BEAM("束"), SHIELD("盾"),
        AMOUNT("量"), RANGE("程"), RADIUS("径"), AIM("准"), OUTPUT("出"),
        MANA_RECEIVE("收"), MANA_SEND("送"), SIGNAL_RECEIVE("听"), SIGNAL_SEND("传"), COUNTER("计"), CLOCK("拍"), TOGGLE("存"), BURST("爆"), BOUNCE("弹"), HOMING("追"), FRAGMENT("裂"), CHAIN("链"), IGNITE("灼"), SLOW("缓"), KNOCKBACK("推"), FAN("扇"), WALL("墙"), RING("环"), COLUMN("柱"), CROSS("十"), DOME("穹");
        public final String glyph;
        Kind(String glyph) { this.glyph = glyph; }
        public boolean manaIn() { return !RuneLogic.sequential(this) && this != INPUT && this != ABSORB && this != SIGNAL && this != MANA_RECEIVE && this != SIGNAL_RECEIVE && this != SIGNAL_SEND; }
        public boolean manaOut() { return !RuneLogic.sequential(this) && this != OUTPUT && this != SIGNAL && this != MANA_SEND && this != SIGNAL_RECEIVE && this != SIGNAL_SEND; }
        public boolean signalIn() { return RuneLogic.sequential(this) || this == AND || this == OR || this == NOT || this == SWITCH || this == SIGNAL_SEND; }
        public boolean signalOut() { return RuneLogic.sequential(this) || this == AND || this == OR || this == NOT || this == SIGNAL || this == SENSOR || this == SIGNAL_RECEIVE; }
    }
    public Rune {
        if (id < 1 || id > 1024 || kind == null || cell == null || element == null || weights == null)
            throw new IllegalArgumentException("非法符文");
        Fixed.checked(value);
        if((kind==Kind.COUNTER && (value<Fixed.ONE || value>LogicDefaults.MAX_COUNT*Fixed.ONE || value%Fixed.ONE!=0))
            || (kind==Kind.CLOCK && (value<2*Fixed.ONE || value>LogicDefaults.MAX_PERIOD*Fixed.ONE || value%Fixed.ONE!=0)))throw new IllegalArgumentException("计数阈值须为1–1024整数，节拍周期须为2–1200整数逻辑步");
        if(ProjectileEffects.rune(kind)){int min=kind==Kind.HOMING || kind==Kind.FRAGMENT?2:1;int max=kind==Kind.HOMING?12:kind==Kind.IGNITE || kind==Kind.SLOW?5:4;if(value%Fixed.ONE!=0 || value<min*Fixed.ONE || value>max*Fixed.ONE)throw new IllegalArgumentException("效果参数须为整数：分裂2–4，连锁1–4，灼烧/迟缓1–5秒，击退1–4级");}
        weights = List.copyOf(weights);
        if (weights.size() > 64 || weights.stream().anyMatch(w -> w == null || w < 0 || w > 1000)) throw new IllegalArgumentException("非法分流权重");
        if ((kind == Kind.RANGE && (value < Fixed.ONE || value > 64 * Fixed.ONE))
            || (kind == Kind.RADIUS && (value < Fixed.ONE || value > 8 * Fixed.ONE))
            || (kind == Kind.AIM && value > Fixed.ONE) || (kind == Kind.AMOUNT && (value == 0 || value > 64 * Fixed.ONE)))
            throw new IllegalArgumentException("符文参数越界");
    }
    public static Rune create(int id, Kind kind, Hex cell) {return create(id,kind,cell,LogicDefaults.protocolV1());}
    public static Rune create(int id,Kind kind,Hex cell,LogicDefaults defaults){
        return create(id,kind,cell,defaults,ProjectileRules.protocolV1());
    }
    public static Rune create(int id,Kind kind,Hex cell,LogicDefaults defaults,ProjectileRules projectiles){
        return create(id,kind,cell,defaults,projectiles,SecondaryRules.protocolV1());
    }
    public static Rune create(int id,Kind kind,Hex cell,LogicDefaults defaults,ProjectileRules projectiles,SecondaryRules secondary){
        long v = switch (kind) { case FRAGMENT,CHAIN,IGNITE,SLOW,KNOCKBACK->secondary.defaultValue(kind);case BURST,BOUNCE,HOMING->projectiles.defaultValue(kind);case COUNTER->defaults.counterThreshold()*Fixed.ONE;case CLOCK->defaults.clockPeriod()*Fixed.ONE;case RANGE -> 12 * Fixed.ONE; case RADIUS -> 2 * Fixed.ONE; case AIM -> 8192; case AMOUNT, SENSOR -> Fixed.ONE; default -> 0; };
        return new Rune(id, kind, cell, Element.FIRE, v, kind!=Kind.TOGGLE, false, List.of(1));
    }
}
