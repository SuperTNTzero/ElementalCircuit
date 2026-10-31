package dev.elementalcircuits.domain;

import java.util.*;

/** Split phase traversal and prepaid illumination without copying any component. */
public final class SpellPayloads {
    public record Lit(Packet impact,int ticks) { }
    private SpellPayloads(){ }
    public static boolean phased(Mixture mana){
        long[] values=mana.values();long arcane=values[Element.ARCANE.component(false)];return arcane>0 && mana.total()==arcane;
    }
    public static List<Packet> split(Packet packet){
        long[] values=packet.mana().values();long arcane=values[Element.ARCANE.component(false)];
        if(arcane==0 || arcane==packet.mana().total())return List.of(packet);
        long[] phase=new long[17];phase[Element.ARCANE.component(false)]=arcane;values[Element.ARCANE.component(false)]=0;
        return List.of(new Packet(new Mixture(values),packet.spell()),new Packet(new Mixture(phase),packet.spell()));
    }
    public static Lit illumination(Packet packet,long strength,RadianceRules rules){
        if(strength<Fixed.ONE/4 || strength>2*Fixed.ONE)throw new IllegalArgumentException("非法照明强度");
        long[] values=packet.mana().values();long reserved=values[Element.LIGHT.component(false)]/4;
        int ticks=(int)Math.min(128,reserved*strength/Fixed.ONE/rules.trailCost());
        if(ticks==0)return new Lit(packet,0);
        long paid=(ticks*rules.trailCost()*Fixed.ONE+strength-1)/strength;
        values[Element.LIGHT.component(false)]-=paid;
        return new Lit(new Packet(new Mixture(values),packet.spell()),ticks);
    }
}
