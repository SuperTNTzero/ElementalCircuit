package dev.elementalcircuits.application.observation;

import java.util.*;

/** Presentation-only bounded selection, independent of the authoritative simulation. */
public final class VisualBudget {
    public static final int WORLD_LIMIT=1024,PLAYER_LIMIT=512,BATCH_LIMIT=128,CLIENT_LIMIT=2048;
    private final List<SpellVisual> events=new ArrayList<>();
    private int dropped;
    public void add(SpellVisual event){if(events.size()<WORLD_LIMIT)events.add(Objects.requireNonNull(event));else dropped++;}
    public List<SpellVisual> snapshot(){return List.copyOf(events);}
    public int dropped(){return dropped;}
    public List<List<SpellVisual>> batches(SpellVisual.Point observer){
        List<SpellVisual> nearby=events.stream().filter(e->e.near(observer,64)).sorted(Comparator.comparingDouble(e->e.from().distanceSquared(observer))).limit(PLAYER_LIMIT).toList();
        var result=new ArrayList<List<SpellVisual>>();for(int i=0;i<nearby.size();i+=BATCH_LIMIT)result.add(List.copyOf(nearby.subList(i,Math.min(nearby.size(),i+BATCH_LIMIT))));return List.copyOf(result);
    }
    public void clear(){events.clear();dropped=0;}
}
