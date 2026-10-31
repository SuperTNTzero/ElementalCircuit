package dev.elementalcircuits.client.effect;

import dev.elementalcircuits.application.observation.*;
import java.util.*;

/** Local lifetime only: cannot collide, select targets, apply damage or advance authority. */
public final class VisualTimeline {
    public record Frame(SpellVisual event,int age,int life){}
    private final ArrayDeque<Frame> frames=new ArrayDeque<>();
    public void add(Collection<SpellVisual> events){for(var event:events){if(frames.size()==VisualBudget.CLIENT_LIMIT)frames.removeFirst();frames.addLast(new Frame(event,0,VisualStyles.of(event.kind()).ticks()));}}
    public void tick(){int count=frames.size();for(int i=0;i<count;i++){var f=frames.removeFirst();if(f.age+1<f.life)frames.addLast(new Frame(f.event,f.age+1,f.life));}}
    public List<Frame> frames(){return List.copyOf(frames);}
    public void clear(){frames.clear();}
}
