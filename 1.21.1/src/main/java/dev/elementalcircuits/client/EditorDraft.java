package dev.elementalcircuits.client;

import dev.elementalcircuits.domain.*;
import java.util.*;

/** Detached editor configuration, bounded history, no references to a live VM. */
public final class EditorDraft {
    private Blueprint blueprint;
    private final Deque<Blueprint> undo=new ArrayDeque<>(),redo=new ArrayDeque<>();
    public EditorDraft(Blueprint blueprint){this.blueprint=blueprint;}
    public Blueprint get(){return blueprint;}
    public void set(Blueprint next){if(next.equals(blueprint))return;undo.addLast(blueprint);if(undo.size()>32)undo.removeFirst();redo.clear();blueprint=next;}
    public void undo(){if(!undo.isEmpty()){redo.addLast(blueprint);blueprint=undo.removeLast();}}
    public void redo(){if(!redo.isEmpty()){undo.addLast(blueprint);blueprint=redo.removeLast();}}
    public void place(Rune.Kind kind,Hex cell){
        place(kind,Element.FIRE,cell);
    }
    public void place(Rune.Kind kind,Element element,Hex cell){
        set(placed(kind,element,cell));
    }
    public Blueprint placed(Rune.Kind kind,Element element,Hex cell){
        if(at(cell)!=null)throw new IllegalArgumentException("格子已被占用");
        List<Rune> runes=new ArrayList<>(blueprint.runes());int id=runes.stream().mapToInt(Rune::id).max().orElse(0)+1;
        Rune rune=Rune.create(id,kind,cell,dev.elementalcircuits.adapter.LogicResources.defaults(),dev.elementalcircuits.adapter.ProjectileResources.rules(),dev.elementalcircuits.adapter.SecondaryResources.rules());runes.add(new Rune(id,kind,cell,element,rune.value(),rune.on(),rune.signalControl(),rune.weights()));return new Blueprint(1,blueprint.rules(),runes,blueprint.routes(),blueprint.continuous());
    }
    /** Move and connected-wire rerouting are one validated undo step. Failure leaves the draft intact. */
    public Blueprint moved(int id,Hex cell){
        Rune old=blueprint.rune(id);if(old.cell().equals(cell))return blueprint;
        if(at(cell)!=null)throw new IllegalArgumentException("目标格已有符文");
        List<Rune> runes=blueprint.runes().stream().map(r->r.id()!=id?r:new Rune(id,r.kind(),cell,r.element(),r.value(),r.on(),r.signalControl(),r.weights())).toList();
        Blueprint positions=new Blueprint(1,blueprint.rules(),runes,List.of(),blueprint.continuous());List<Route> wires=new ArrayList<>();
        for(Route route:blueprint.routes()){
            boolean touched=route.source()==id || route.target()==id || route.path().contains(cell);
            wires.add(!touched?route:new Route(route.source(),route.target(),route.payload(),HexRouter.segment(positions,positions.rune(route.source()).cell(),positions.rune(route.target()).cell(),List.of())));
        }
        return new Blueprint(1,blueprint.rules(),runes,wires,blueprint.continuous());
    }
    public void move(int id,Hex cell){set(moved(id,cell));}
    public Rune at(Hex cell){return blueprint.runes().stream().filter(r->r.cell().equals(cell)).findFirst().orElse(null);}
    public void delete(int id){set(new Blueprint(1,blueprint.rules(),blueprint.runes().stream().filter(r->r.id()!=id).toList(),blueprint.routes().stream().filter(e->e.source()!=id && e.target()!=id).toList(),blueprint.continuous()));}
    public void replace(Rune r){set(new Blueprint(1,blueprint.rules(),blueprint.runes().stream().map(old->old.id()==r.id()?r:old).toList(),blueprint.routes(),blueprint.continuous()));}
    public void route(int source,int target,Route.Payload payload,List<Hex> path){List<Route> routes=new ArrayList<>(blueprint.routes());routes.add(new Route(source,target,payload,path));set(new Blueprint(1,blueprint.rules(),blueprint.runes(),routes,blueprint.continuous()));}
}
