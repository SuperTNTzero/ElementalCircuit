package dev.elementalcircuits.domain;

/** Creation defaults only; chosen parameters are stored in each rune's blueprint. */
public record LogicDefaults(int version,int counterThreshold,int clockPeriod) {
    public static final int MAX_COUNT=1024,MAX_PERIOD=1200;
    public LogicDefaults {
        if(version!=1 || counterThreshold<1 || counterThreshold>MAX_COUNT || clockPeriod<2 || clockPeriod>MAX_PERIOD)throw new IllegalArgumentException("时序符文默认值非法");
    }
    public static LogicDefaults protocolV1(){return new LogicDefaults(1,4,20);}
}
