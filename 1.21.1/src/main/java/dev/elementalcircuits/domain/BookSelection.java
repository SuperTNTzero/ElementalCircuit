package dev.elementalcircuits.domain;
import java.util.*;
/** Selects a stable slot without touching any instance, resource, clock or control state. */
public final class BookSelection {
    private BookSelection(){ }
    public static int cycle(CircuitAssembly a,int delta){if(delta!=-1 && delta!=1)throw new IllegalArgumentException("翻页方向错误");var ids=a.slots();int next=ids.get(Math.floorMod(ids.indexOf(a.selected())+delta,ids.size()));a.select(next);return next;}
}
