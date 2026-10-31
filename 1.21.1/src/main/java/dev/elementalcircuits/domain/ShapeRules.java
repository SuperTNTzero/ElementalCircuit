package dev.elementalcircuits.domain;

/** Versioned spatial profile and bounded world-contact work for one-shot volumes. */
public record ShapeRules(int version,int fanDegrees,int fanReach,int fanHeight,int wallThickness,int slabHeight,
                         int ringInner,int columnRadius,int crossWidth,int domeInner,int groundOffset,int blockProbes) {
    public ShapeRules {
        if(version!=1 || fanDegrees<20 || fanDegrees>100 || fanReach<Fixed.ONE || fanReach>3*Fixed.ONE || blockProbes<32 || blockProbes>1024)throw new IllegalArgumentException("形状规则版本或预算非法");
        for(int v:new int[]{fanHeight,wallThickness,slabHeight,ringInner,columnRadius,crossWidth,domeInner,groundOffset})if(v<=0 || v>=Fixed.ONE)throw new IllegalArgumentException("形状比例非法");
    }
}
