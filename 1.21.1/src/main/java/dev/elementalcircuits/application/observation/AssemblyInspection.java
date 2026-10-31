package dev.elementalcircuits.application.observation;

import dev.elementalcircuits.domain.*;
import java.util.*;

/** Detached management observation, never a live assembly or mutable VM. */
public record AssemblyInspection(int selected,int maxSlots,boolean fixed,long charge,long capacity,List<Slot> slots,List<Port> ports,List<Link> links,List<Stock> stock) {
    public record Slot(int id,Rules.Material material,boolean powered,long held){ }
    public record Port(CircuitAddress address,Rune.Kind kind,String label){ }
    public record Link(int id,boolean external,String label,long held){ }
    public record Stock(Rules.Material material,int count){ }
    public AssemblyInspection {slots=List.copyOf(slots);ports=List.copyOf(ports);links=List.copyOf(links);stock=List.copyOf(stock);}
}
