package dev.elementalcircuits.domain;
import java.util.*;
public record SpellbookInspection(UUID identity,int selected,List<Slot> slots,String message) {
    public record Slot(int id,Blueprint blueprint,boolean powered,long held){public Slot{if(id<1 || id>65534 || blueprint==null)throw new IllegalArgumentException("非法法术书页");Fixed.checked(held);}}
    public SpellbookInspection {slots=List.copyOf(slots);if(identity==null || slots.isEmpty() || slots.size()>4 || slots.stream().map(Slot::id).distinct().count()!=slots.size() || slots.stream().noneMatch(s->s.id()==selected) || message==null || message.length()>512)throw new IllegalArgumentException("非法法术书观察");}
    public static SpellbookInspection inspect(CircuitAssembly a,String message){return new SpellbookInspection(a.identity(),a.selected(),a.slots().stream().map(id->{var c=a.circuit(id);return new Slot(id,c.definition(),c.powered(),c.held());}).toList(),message);}
    public Slot current(){return slots.stream().filter(s->s.id()==selected).findFirst().orElseThrow();}
}
