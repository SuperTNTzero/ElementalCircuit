package dev.elementalcircuits.domain;

import java.util.*;

/** Editable ordinary single-shot blueprint; all effects use the same delivered packet. */
public final class ProjectileExamples {
    private ProjectileExamples(){ }
    public static Blueprint combined(Rules rules,LogicDefaults logic,ProjectileRules effects){
        var kinds=List.of(Rune.Kind.INPUT,Rune.Kind.CONVERT,Rune.Kind.DIRECTED,Rune.Kind.SPHERE,Rune.Kind.BURST,Rune.Kind.BOUNCE,Rune.Kind.HOMING,Rune.Kind.OUTPUT);
        var cells=List.of(new Hex(-3,0),new Hex(-2,0),new Hex(-1,0),new Hex(0,0),new Hex(1,0),new Hex(2,0),new Hex(2,-1),new Hex(3,-1));
        var nodes=new ArrayList<Rune>();for(int i=0;i<kinds.size();i++)nodes.add(Rune.create(i+1,kinds.get(i),cells.get(i),logic,effects));
        var board=new Blueprint(1,rules,nodes,List.of(),false);var routes=new ArrayList<Route>();
        for(int i=1;i<nodes.size();i++)routes.add(new Route(i,i+1,Route.Payload.MANA,HexRouter.segment(board,cells.get(i-1),cells.get(i),List.of())));
        return new Blueprint(1,rules,nodes,routes,false);
    }
    public static Blueprint secondary(Rules rules,LogicDefaults logic,ProjectileRules effects,SecondaryRules secondary){
        var kinds=List.of(Rune.Kind.INPUT,Rune.Kind.CONVERT,Rune.Kind.DIRECTED,Rune.Kind.SPHERE,Rune.Kind.FRAGMENT,Rune.Kind.CHAIN,Rune.Kind.IGNITE,Rune.Kind.SLOW,Rune.Kind.KNOCKBACK,Rune.Kind.OUTPUT);
        var cells=List.of(new Hex(-3,0),new Hex(-2,0),new Hex(-1,0),new Hex(0,0),new Hex(1,0),new Hex(2,0),new Hex(2,-1),new Hex(1,-1),new Hex(0,-1),new Hex(-1,-1));
        var nodes=new ArrayList<Rune>();for(int i=0;i<kinds.size();i++)nodes.add(Rune.create(i+1,kinds.get(i),cells.get(i),logic,effects,secondary));
        var board=new Blueprint(1,rules,nodes,List.of(),false);var routes=new ArrayList<Route>();
        for(int i=1;i<nodes.size();i++)routes.add(new Route(i,i+1,Route.Payload.MANA,HexRouter.segment(board,cells.get(i-1),cells.get(i),List.of())));
        return new Blueprint(1,rules,nodes,routes,false);
    }

}
