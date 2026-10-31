package dev.elementalcircuits.domain;

import java.util.*;

/** Detached state for observers. Contains no VM or mutable state collection. */
public record LogicObservation(int version,List<Node> nodes) {
    public record Node(int id,Rune.Kind kind,int count,int phase,boolean latched,int parameter){ }
    public LogicObservation {if(version!=1 || nodes.size()>64)throw new IllegalArgumentException("时序观察格式非法");nodes=List.copyOf(nodes);}
    public static LogicObservation inspect(TickCircuit circuit){
        return new LogicObservation(1,circuit.definition().runes().stream().filter(r->RuneLogic.sequential(r.kind())).map(r->{var s=circuit.logic(r.id());return new Node(r.id(),r.kind(),s.count(),s.phase(),s.latched(),(int)(r.value()/Fixed.ONE));}).toList());
    }
    public Node find(int id){return nodes.stream().filter(n->n.id()==id).findFirst().orElse(null);}
}
