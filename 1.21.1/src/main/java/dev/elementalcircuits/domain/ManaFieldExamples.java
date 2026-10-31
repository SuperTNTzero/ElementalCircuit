package dev.elementalcircuits.domain;

import java.util.*;

public final class ManaFieldExamples {
    private ManaFieldExamples() {}
    public static Blueprint beam(Rules rules){
        List<Rune> runes=new ArrayList<>();Rune.Kind[] kinds={Rune.Kind.ABSORB,Rune.Kind.CONVERT,Rune.Kind.DIRECTED,Rune.Kind.BEAM,Rune.Kind.OUTPUT};
        for(int i=0;i<kinds.length;i++)runes.add(Rune.create(i+1,kinds[i],new Hex(i-2,0)));
        List<Route> routes=new ArrayList<>();for(int i=0;i<kinds.length-1;i++)routes.add(new Route(i+1,i+2,Route.Payload.MANA,List.of(runes.get(i).cell(),runes.get(i+1).cell())));
        return new Blueprint(1,rules,runes,routes,true);
    }
}
