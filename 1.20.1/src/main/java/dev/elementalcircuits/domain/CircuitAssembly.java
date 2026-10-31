package dev.elementalcircuits.domain;

import java.util.*;

/** Stable slots share one supply but retain independent definitions and complete VM state. */
public final class CircuitAssembly {
    public record Slot(int id,CircuitSnapshot circuit){ }
    public record Snapshot(int version,UUID identity,AssemblyRules rules,int selected,int nextSlot,List<Slot> slots,CircuitLinks.Snapshot links){public Snapshot{slots=List.copyOf(slots);}}
    public record Output(int slot,MagicCircuit.Cast cast){ }
    private final UUID identity;
    private final AssemblyRules rules;
    private final NavigableMap<Integer,TickCircuit> slots=new TreeMap<>();
    private CircuitLinks links;
    private int selected=1,nextSlot=2;
    public CircuitAssembly(UUID identity,AssemblyRules rules,TickCircuit first){this.identity=Objects.requireNonNull(identity);this.rules=rules;slots.put(1,first);links=new CircuitLinks(rules);}
    public static CircuitAssembly restore(Snapshot s){
        if(s.version!=1 || s.identity==null || s.slots.isEmpty() || s.slots.size()>s.rules.maxSlots() || s.nextSlot<2 || s.nextSlot>65535)throw new IllegalArgumentException("法阵组现场非法");
        CircuitAssembly a=new CircuitAssembly(s.identity,s.rules,TickCircuit.restore(s.slots.get(0).circuit));a.slots.clear();
        for(Slot slot:s.slots)if(slot.id<1 || slot.id>=s.nextSlot || a.slots.put(slot.id,TickCircuit.restore(slot.circuit))!=null)throw new IllegalArgumentException("法阵槽位非法");
        if(!a.slots.containsKey(s.selected))throw new IllegalArgumentException("选中槽位不存在");a.selected=s.selected;a.nextSlot=s.nextSlot;a.links=CircuitLinks.restore(s.links);
        if(!a.links.snapshot().rules().equals(s.rules))throw new IllegalArgumentException("法阵组互联规则不一致");
        for(var link:a.links.connections()){
            if(!link.from().carrier().equals(s.identity) || !link.to().carrier().equals(s.identity))throw new IllegalArgumentException("内部线路含外部端点");
            var from=a.circuit(link.from().slot()).definition().rune(link.from().rune()).kind();var to=a.circuit(link.to().slot()).definition().rune(link.to().rune()).kind();
            if(from!=(link.payload()==Route.Payload.MANA?Rune.Kind.MANA_SEND:Rune.Kind.SIGNAL_SEND) || to!=(link.payload()==Route.Payload.MANA?Rune.Kind.MANA_RECEIVE:Rune.Kind.SIGNAL_RECEIVE))throw new IllegalArgumentException("内部线路接口非法");
        }
        return a;
    }
    /** Cheap immutable persistence stamp; never traverses rune arrays or builds a VM snapshot. */
    public record ChangeStamp(int selected,int nextSlot,List<Object> circuits,Object links){public ChangeStamp{circuits=List.copyOf(circuits);}}
    public ChangeStamp changeStamp(){return new ChangeStamp(selected,nextSlot,slots.values().stream().map(TickCircuit::changeToken).toList(),links.changeToken());}
    public UUID identity(){return identity;}
    public AssemblyRules rules(){return rules;}
    public int selected(){return selected;}
    public List<Integer> slots(){return List.copyOf(slots.keySet());}
    public TickCircuit find(int id){return slots.get(id);}
    public TickCircuit circuit(int id){TickCircuit c=find(id);if(c==null)throw new IllegalArgumentException("法阵槽位不存在");return c;}
    public CircuitLinks links(){return links;}
    public void select(int id){circuit(id);selected=id;}
    public int add(Rules rules){if(slots.size()>=this.rules.maxSlots() || nextSlot>=65535)throw new IllegalArgumentException("法阵槽位已满");int id=nextSlot++;slots.put(id,new TickCircuit(Blueprint.empty(rules)));selected=id;return id;}
    public void replace(int id,Blueprint definition){if(links.references(identity,id))throw new IllegalArgumentException("请先断开该槽位的内部线路");slots.put(id,circuit(id).reconfigure(definition));}
    public boolean active(){return slots.values().stream().anyMatch(TickCircuit::needsStep);}
    public boolean powered(){return slots.values().stream().anyMatch(TickCircuit::powered);}
    public void stopAll(){slots.values().forEach(TickCircuit::stop);}
    public void clearDrives(){slots.values().forEach(TickCircuit::clearDrives);}
    public void deliver(){links.deliver(id->id.equals(identity)?this:null);}
    public List<Output> advance(ManaSource source,int steps){if(steps<0 || steps>2)throw new IllegalArgumentException("法阵步数非法");List<Output> output=new ArrayList<>();for(int step=0;step<steps;step++)for(var entry:slots.entrySet())if(entry.getValue().needsStep())for(var cast:entry.getValue().tick(source))output.add(new Output(entry.getKey(),cast));return List.copyOf(output);}
    public void publish(){links.publish(id->id.equals(identity)?this:null);}
    public List<Output> tick(ManaSource source,int steps){clearDrives();deliver();var result=advance(source,steps);publish();return result;}
    public Snapshot snapshot(){return new Snapshot(1,identity,rules,selected,nextSlot,slots.entrySet().stream().map(e->new Slot(e.getKey(),e.getValue().snapshot())).toList(),links.snapshot());}
}
