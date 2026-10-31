package dev.elementalcircuits.domain;

/** Defaults and costs for bounded secondary casts and independent hit modifiers. */
public record SecondaryRules(int version,int fragments,int chains,int ignite,int slow,int knockback,int childRange,int chainRange,int fanDegrees,long retention,long statusCost) {
    public SecondaryRules {if(version!=1 || fragments<2 || fragments>4 || chains<1 || chains>4 || ignite<1 || ignite>5 || slow<1 || slow>5 || knockback<1 || knockback>4 || childRange<1 || childRange>16 || chainRange<1 || chainRange>12 || fanDegrees<10 || fanDegrees>60 || retention<1 || retention>Fixed.ONE || statusCost<Fixed.ONE/16 || statusCost>4*Fixed.ONE)throw new IllegalArgumentException("非法二次派生规则");}
    public static SecondaryRules protocolV1(){return new SecondaryRules(1,3,2,2,2,1,6,6,30,49152,16384);}
    public long defaultValue(Rune.Kind k){return switch(k){case FRAGMENT->fragments*Fixed.ONE;case CHAIN->chains*Fixed.ONE;case IGNITE->ignite*Fixed.ONE;case SLOW->slow*Fixed.ONE;case KNOCKBACK->knockback*Fixed.ONE;default->0;};}
}
