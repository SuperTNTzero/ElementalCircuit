package dev.elementalcircuits.domain.equipment;

import dev.elementalcircuits.domain.Fixed;

import java.util.*;

/**
 * Versioned component definitions. Resource transport quantities are never multiplied by equipment.
 */
public record WandParts(int version, List<Part> parts) {
    public enum Slot {
        HEAD(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.equipment._wand_parts.001")),
        BODY(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.equipment._wand_parts.002")),
        TAIL(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.equipment._wand_parts.003"));
        public final String title;

        Slot(String title) {
            this.title = title;
        }
    }

    public record Part(
            String id,
            Slot slot,
            String title,
            long strength,
            long speed,
            int durability,
            double melee,
            int color) {
        public Part {
            if (id == null
                    || !id.matches("[a-z_]{1,40}")
                    || slot == null
                    || title == null
                    || title.isBlank()
                    || title.length() > 32
                    || strength < Fixed.ONE / 4
                    || strength > 2 * Fixed.ONE
                    || speed < Fixed.ONE / 4
                    || speed > 2 * Fixed.ONE
                    || durability < 0
                    || durability > 2048
                    || !Double.isFinite(melee)
                    || melee < 0
                    || melee > 16)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.equipment._wand_parts.004"));
            if (slot != Slot.HEAD && (strength != Fixed.ONE || speed != Fixed.ONE)
                    || slot == Slot.TAIL && durability != 0
                    || slot != Slot.TAIL && melee != 0
                    || slot != Slot.TAIL && durability == 0)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.equipment._wand_parts.005"));
        }
    }

    public WandParts {
        if (version != 1 || parts == null || parts.size() != 9)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.equipment._wand_parts.006"));
        parts = List.copyOf(parts);
        Set<String> ids = new HashSet<>();
        for (Part part : parts)
            if (!ids.add(part.id()))
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.equipment._wand_parts.007"));
        for (Slot slot : Slot.values())
            if (parts.stream().filter(p -> p.slot() == slot).count() != 3)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.equipment._wand_parts.008"));
    }

    public Part get(String id) {
        return parts.stream()
                .filter(p -> p.id().equals(id))
                .findFirst()
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        dev.elementalcircuits.text.Texts.get(
                                                        "text.elementalcircuits.domain.equipment._wand_parts.009")
                                                + id));
    }

    public List<Part> in(Slot slot) {
        return parts.stream().filter(p -> p.slot() == slot).toList();
    }

    public record Assembly(String head, String body, String tail) {
        public Assembly {
            Objects.requireNonNull(head);
            Objects.requireNonNull(body);
            Objects.requireNonNull(tail);
        }

        public String at(Slot slot) {
            return switch (slot) {
                case HEAD -> head;
                case BODY -> body;
                case TAIL -> tail;
            };
        }

        public Assembly replace(Part part) {
            return new Assembly(
                    part.slot() == Slot.HEAD ? part.id() : head,
                    part.slot() == Slot.BODY ? part.id() : body,
                    part.slot() == Slot.TAIL ? part.id() : tail);
        }

        public Stats stats(WandParts definitions) {
            Part h = definitions.get(head), b = definitions.get(body), t = definitions.get(tail);
            if (h.slot() != Slot.HEAD || b.slot() != Slot.BODY || t.slot() != Slot.TAIL)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.equipment._wand_parts.010"));
            return new Stats(h.strength(), h.speed(), h.durability() + b.durability(), t.melee());
        }
    }

    public record Stats(long strength, long speed, int durability, double melee) {}

    public Assembly basic() {
        return new Assembly(
                in(Slot.HEAD).get(0).id(), in(Slot.BODY).get(0).id(), in(Slot.TAIL).get(0).id());
    }
}
