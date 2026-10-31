package dev.elementalcircuits.domain;

public record RadianceRules(int version,int lightLife,int shadowLife,int trailLife,long trailCost,int maxLights,int orphanDelay) {
    public RadianceRules {
        if(version!=1 || lightLife<1 || lightLife>1200 || shadowLife<1 || shadowLife>1200 || trailLife<1 || trailLife>20
            || trailCost<1 || trailCost>Fixed.ONE || maxLights<1 || maxLights>2048 || orphanDelay<1 || orphanDelay>1200)throw new IllegalArgumentException("非法光暗规则");
    }
}
