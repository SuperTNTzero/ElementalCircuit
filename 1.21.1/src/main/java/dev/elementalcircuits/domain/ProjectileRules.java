package dev.elementalcircuits.domain;

/** Versioned defaults and prepaid/flight costs; frozen into each new flight. */
public record ProjectileRules(int version,int burst,int bounces,int homing,long bounceRetention,int homingDivisor,int turnDegrees) {
    public ProjectileRules {if(version!=1 || burst<1 || burst>4 || bounces<1 || bounces>4 || homing<2 || homing>12 || bounceRetention<1 || bounceRetention>Fixed.ONE || homingDivisor<2 || homingDivisor>8 || turnDegrees<1 || turnDegrees>30)throw new IllegalArgumentException("非法派生效果规则");}
    public static ProjectileRules protocolV1(){return new ProjectileRules(1,3,2,8,49152,2,15);}
    public long defaultValue(Rune.Kind kind){return switch(kind){case BURST->burst*Fixed.ONE;case BOUNCE->bounces*Fixed.ONE;case HOMING->homing*Fixed.ONE;default->0;};}
}
