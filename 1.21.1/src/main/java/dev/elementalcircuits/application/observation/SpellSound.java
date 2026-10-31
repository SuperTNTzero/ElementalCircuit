package dev.elementalcircuits.application.observation;

import dev.elementalcircuits.domain.Element;
import java.util.*;

/** Disposable audio observation; never saved or used as a simulation input. */
public record SpellSound(Kind kind,Element element,SpellVisual.Point position) {
    public enum Kind { START, CAST, IMPACT, SHIELD }
    public SpellSound { Objects.requireNonNull(kind);Objects.requireNonNull(element);Objects.requireNonNull(position); }
    public String resource(){return kind==Kind.START?"circuit_start":kind.name().toLowerCase(Locale.ROOT)+"_"+element.name().toLowerCase(Locale.ROOT);}
}
