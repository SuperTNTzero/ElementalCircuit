package dev.elementalcircuits.client.editor;

import dev.elementalcircuits.application.observation.AssemblyInspection;
import dev.elementalcircuits.domain.*;
import java.util.*;

/** Page state and compatible endpoint selection are independent of Minecraft widgets. */
public final class AssemblyPageModel {
    public int page,offset;
    public Route.Payload payload=Route.Payload.MANA;
    private CircuitAddress from,to;
    public AssemblyPageModel(int page){this.page=page;}
    public List<AssemblyInspection.Port> ports(AssemblyInspection data,boolean sending){
        Rune.Kind kind=payload==Route.Payload.MANA?(sending?Rune.Kind.MANA_SEND:Rune.Kind.MANA_RECEIVE):(sending?Rune.Kind.SIGNAL_SEND:Rune.Kind.SIGNAL_RECEIVE);
        return data.ports().stream().filter(p->p.kind()==kind).toList();
    }
    public AssemblyInspection.Port selected(AssemblyInspection data,boolean sending){var ports=ports(data,sending);CircuitAddress address=sending?from:to;return ports.stream().filter(p->p.address().equals(address)).findFirst().orElse(ports.isEmpty()?null:ports.get(0));}
    public void cycle(AssemblyInspection data,boolean sending){var ports=ports(data,sending);if(ports.isEmpty())return;int index=ports.indexOf(selected(data,sending));var next=ports.get((index+1)%ports.size()).address();if(sending)from=next;else to=next;}
    public void toggle(){payload=payload==Route.Payload.MANA?Route.Payload.SIGNAL:Route.Payload.MANA;from=null;to=null;}
}
