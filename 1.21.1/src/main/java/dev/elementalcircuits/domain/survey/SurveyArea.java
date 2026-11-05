package dev.elementalcircuits.domain.survey;

import java.util.*;

/** Block-coordinate sphere; candidate chunks intersect its horizontal projection. */
public record SurveyArea(int x, int y, int z, int radius) {
    public record Chunk(int x, int z) {}

    public SurveyArea {
        if (Math.abs((long) x) > 30000000
                || Math.abs((long) y) > 30000000
                || Math.abs((long) z) > 30000000
                || radius < 1
                || radius > 2048)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.survey._survey_area.001"));
    }

    public boolean contains(int px, int py, int pz) {
        long dx = (long) px - x, dy = (long) py - y, dz = (long) pz - z;
        return Math.abs(dx) <= radius
                && Math.abs(dy) <= radius
                && Math.abs(dz) <= radius
                && dx * dx + dy * dy + dz * dz <= (long) radius * radius;
    }

    public List<Chunk> chunks() {
        List<Chunk> result = new ArrayList<>();
        int loX = Math.floorDiv(Math.max(-30000000, x - radius), 16),
                hiX = Math.floorDiv(Math.min(30000000, x + radius), 16),
                loZ = Math.floorDiv(Math.max(-30000000, z - radius), 16),
                hiZ = Math.floorDiv(Math.min(30000000, z + radius), 16);
        for (int cx = loX; cx <= hiX; cx++)
            for (int cz = loZ; cz <= hiZ; cz++) {
                int px = Math.max(cx * 16, Math.min(x, cx * 16 + 15)),
                        pz = Math.max(cz * 16, Math.min(z, cz * 16 + 15));
                long dx = (long) px - x, dz = (long) pz - z;
                if (dx * dx + dz * dz <= (long) radius * radius) result.add(new Chunk(cx, cz));
            }
        return List.copyOf(result);
    }
}
