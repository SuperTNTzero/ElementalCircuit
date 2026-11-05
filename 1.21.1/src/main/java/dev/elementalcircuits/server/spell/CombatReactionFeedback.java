package dev.elementalcircuits.server.spell;

import dev.elementalcircuits.application.observation.SpellVisual;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.combat.Element;
import dev.elementalcircuits.domain.combat.ElementalCombat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

/** One disposable reaction observation, sharing the existing distance and batch budgets. */
final class CombatReactionFeedback {
    private static final ElementalCombat.Reaction[] PRIORITY = {
        ElementalCombat.Reaction.SHATTER,
        ElementalCombat.Reaction.STEAM,
        ElementalCombat.Reaction.CONDUCTION,
        ElementalCombat.Reaction.FREEZE,
        ElementalCombat.Reaction.MUD,
        ElementalCombat.Reaction.FAN,
        ElementalCombat.Reaction.PURIFY,
        ElementalCombat.Reaction.NEUTRALIZE
    };

    private CombatReactionFeedback() {}

    static Element observe(ServerLevel level, Vec3 at, Set<ElementalCombat.Reaction> reactions) {
        for (var r : PRIORITY)
            if (reactions.contains(r)) {
                var kind =
                        r == ElementalCombat.Reaction.STEAM
                                        || r == ElementalCombat.Reaction.NEUTRALIZE
                                ? SpellVisual.Kind.STEAM
                                : SpellVisual.Kind.IMPACT;
                Element e =
                        switch (r) {
                            case STEAM, FREEZE, NEUTRALIZE -> Element.WATER;
                            case SHATTER, MUD -> Element.EARTH;
                            case CONDUCTION -> Element.LIGHTNING;
                            case FAN -> Element.FIRE;
                            case PURIFY -> Element.LIGHT;
                        };
                SpellVisuals.observe(level, kind, e, at, at, .75f, 4);
                return e;
            }
        return null;
    }
}
