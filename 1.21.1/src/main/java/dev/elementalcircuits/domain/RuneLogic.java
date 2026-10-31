package dev.elementalcircuits.domain;

/** Pure one-step transitions for counting, periodic pulses and a toggle latch. */
public final class RuneLogic {
    public record Result(RuneLogicState state,boolean value,boolean valid){ }
    private RuneLogic(){ }
    public static boolean sequential(Rune.Kind kind){return kind==Rune.Kind.COUNTER || kind==Rune.Kind.CLOCK || kind==Rune.Kind.TOGGLE;}
    public static Result step(Rune rune,RuneLogicState previous,boolean high,boolean valid,boolean connected){
        if(!sequential(rune.kind()))throw new IllegalArgumentException("不是时序符文");
        if(!valid)return new Result(previous.suspended(),false,false);
        if(rune.kind()==Rune.Kind.CLOCK){
            int phase=previous.phase();boolean pulse=false;
            if(!connected || high){phase++;if(phase==rune.value()/Fixed.ONE){phase=0;pulse=true;}}
            return new Result(new RuneLogicState(0,phase,false,false,false),pulse,true);
        }
        boolean rise=connected && previous.armed() && !previous.last() && high;
        int count=previous.count();boolean latched=previous.latched(),pulse=false;
        if(rune.kind()==Rune.Kind.COUNTER && rise){count++;if(count==rune.value()/Fixed.ONE){count=0;pulse=true;}}
        if(rune.kind()==Rune.Kind.TOGGLE && rise)latched=!latched;
        return new Result(new RuneLogicState(count,0,latched,high,true),rune.kind()==Rune.Kind.COUNTER?pulse:latched,true);
    }
}
