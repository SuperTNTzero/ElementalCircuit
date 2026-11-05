package dev.elementalcircuits.domain.book;

import dev.elementalcircuits.domain.equipment.WandParts;

import java.util.*;

public record WorkshopInspection(
        UUID identity,
        long position,
        WandParts.Assembly assembly,
        int wear,
        Map<String, Integer> stock,
        int plates,
        String message,
        boolean valid) {
    public WorkshopInspection {
        stock = Map.copyOf(stock);
        if (identity == null
                || assembly == null
                || wear < 0
                || wear > 10000
                || plates < 0
                || plates > 4096
                || stock.size() > 16
                || stock.values().stream().anyMatch(n -> n == null || n < 0 || n > 4096)
                || message == null
                || message.length() > 512)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.book._workshop_inspection.001"));
    }
}
