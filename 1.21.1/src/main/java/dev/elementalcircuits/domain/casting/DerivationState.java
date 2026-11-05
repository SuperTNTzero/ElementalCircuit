package dev.elementalcircuits.domain.casting;

import dev.elementalcircuits.domain.circuit.CircuitBounds;

import java.util.*;

/** In-flight continuation only; never included in a blueprint. */
public record DerivationState(boolean splitReady, int chainLeft, List<UUID> visited) {
    public static final DerivationState NONE = new DerivationState(false, 0, List.of());

    public DerivationState {
        visited = List.copyOf(visited);
        if (chainLeft < 0
                || chainLeft > CircuitBounds.DERIVATIVES
                || visited.size() > CircuitBounds.DERIVATIVES + 1
                || new HashSet<>(visited).size() != visited.size())
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.casting._derivation_state.001"));
    }

    public static DerivationState initial(ProjectileEffects e) {
        return new DerivationState(e.fragments() > 0, e.chains(), List.of());
    }

    public void validate(ProjectileEffects e) {
        if (splitReady && e.fragments() == 0 || chainLeft > e.chains())
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.casting._derivation_state.002"));
    }

    public List<UUID> afterHit(UUID id) {
        if (id == null || visited.contains(id)) return visited;
        var list = new ArrayList<>(visited);
        list.add(id);
        return List.copyOf(list);
    }
}
