package dev.elementalcircuits.domain;

/** Immutable, complete sequential state; no world clock or renderer participates. */
public record RuneLogicState(int count,int phase,boolean latched,boolean last,boolean armed) {
    public static final RuneLogicState EMPTY=new RuneLogicState(0,0,false,false,false);
    public static RuneLogicState initial(Rune rune){return rune.kind()==Rune.Kind.TOGGLE?new RuneLogicState(0,0,rune.on(),false,false):EMPTY;}
    public RuneLogicState suspended(){return new RuneLogicState(count,phase,latched,false,false);}
    public void validate(Rune rune){
        if(count<0 || phase<0 || !armed && last)throw new IllegalArgumentException("时序状态非法");
        boolean ok=switch(rune.kind()){
            case COUNTER -> count<rune.value()/Fixed.ONE && phase==0 && !latched;
            case CLOCK -> count==0 && phase<rune.value()/Fixed.ONE && !latched && !last && !armed;
            case TOGGLE -> count==0 && phase==0;
            default -> equals(EMPTY);
        };
        if(!ok)throw new IllegalArgumentException("时序状态与符文或参数不匹配");
    }
}
