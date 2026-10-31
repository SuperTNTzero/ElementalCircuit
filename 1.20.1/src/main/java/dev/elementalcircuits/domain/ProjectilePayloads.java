package dev.elementalcircuits.domain;

/** Derivative costs spend the already delivered packet; they never inject new mana. */
public final class ProjectilePayloads {
    private ProjectilePayloads(){ }
    public static Mixture guidance(Packet packet,ProjectileRules rules){return packet.spell().effects().homing()==0?packet.mana():packet.mana().retain(Fixed.ONE/rules.homingDivisor());}
    public static Mixture bounce(Mixture mana,ProjectileRules rules){return mana.retain(rules.bounceRetention());}
}
