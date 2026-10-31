package dev.elementalcircuits.domain;

/** Finite source reservoir. Loading, placing and inspecting a node never add energy. */
public final class ManaNode {
    public enum Kind { NATURAL, ARTIFICIAL }
    public record State(int version,Kind kind,long initial,long received,long emitted,long remaining) {}
    private final ManaFieldRules rules;
    private final Kind kind;
    private final long initial;
    private long received,emitted,remaining;
    public ManaNode(ManaFieldRules rules,Kind kind){this.rules=java.util.Objects.requireNonNull(rules);this.kind=java.util.Objects.requireNonNull(kind);initial=kind==Kind.NATURAL?rules.naturalCapacity():0;remaining=initial;}
    public Kind kind(){return kind;}
    public long remaining(){return remaining;}
    public long capacity(){return kind==Kind.NATURAL?rules.naturalCapacity():rules.artificialCapacity();}
    public boolean canInsert(){return kind==Kind.ARTIFICIAL && remaining<=capacity()-rules.crystal() && received<=Long.MAX_VALUE-rules.crystal();}
    public boolean insert(){if(!canInsert())return false;remaining+=rules.crystal();received+=rules.crystal();return true;}
    public long emission(){return Math.min(remaining,kind==Kind.NATURAL?rules.naturalPerStep():rules.artificialPerStep());}
    public void emitted(long amount){Fixed.checked(amount);if(amount>emission() || emitted>Long.MAX_VALUE-amount)throw new IllegalArgumentException("节点释放量非法");remaining-=amount;emitted+=amount;}
    public State snapshot(){return new State(1,kind,initial,received,emitted,remaining);}
    public static ManaNode restore(ManaFieldRules rules,State state){
        if(state==null || state.version()!=1 || state.kind()==null)throw new IllegalArgumentException("节点状态版本非法");
        ManaNode node=new ManaNode(rules,state.kind());
        if(state.initial()!=node.initial || state.received()<0 || state.emitted()<0 || state.remaining()<0 || state.remaining()>node.capacity()
            || state.kind()==Kind.NATURAL && state.received()!=0 || Math.addExact(state.initial(),state.received())!=Math.addExact(state.emitted(),state.remaining()))throw new IllegalArgumentException("节点账本非法");
        node.received=state.received();node.emitted=state.emitted();node.remaining=state.remaining();return node;
    }
}
