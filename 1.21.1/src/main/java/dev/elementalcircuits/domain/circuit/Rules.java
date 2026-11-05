package dev.elementalcircuits.domain.circuit;

import dev.elementalcircuits.domain.Fixed;
import dev.elementalcircuits.domain.casting.CastingRules;

/** Frozen into each blueprint and runtime. Changing the resource affects new carriers only. */
public record Rules(
        int version,
        Material material,
        long conversion,
        long inversion,
        long coupling,
        long nodeCapacity,
        long injection,
        int interval,
        int window,
        long signalCost,
        long playerCapacity,
        long playerRegen,
        int playerDelay,
        long range,
        long radius,
        long routingRetention,
        long oppositionRetention,
        BoardSizes boards,
        int manaSteps,
        CastingRules casting) {
    public record BoardSizes(int wood, int brass, int crystal) {
        public BoardSizes {
            for (int n : new int[] {wood, brass, crystal})
                if (n < 1 || n > CircuitBounds.BOARD)
                    throw new IllegalArgumentException(
                            dev.elementalcircuits.text.Texts.get(
                                    "text.elementalcircuits.domain.circuit._rules.001"));
        }

        public static BoardSizes legacy() {
            return new BoardSizes(4, 6, 8);
        }
    }

    public Rules(
            int version,
            Material material,
            long conversion,
            long inversion,
            long coupling,
            long nodeCapacity,
            long injection,
            int interval,
            int window,
            long signalCost,
            long playerCapacity,
            long playerRegen,
            int playerDelay,
            long range,
            long radius,
            long routingRetention,
            long oppositionRetention) {
        this(
                version,
                material,
                conversion,
                inversion,
                coupling,
                nodeCapacity,
                injection,
                interval,
                window,
                signalCost,
                playerCapacity,
                playerRegen,
                playerDelay,
                range,
                radius,
                routingRetention,
                oppositionRetention,
                BoardSizes.legacy());
    }

    /** Legacy rules retain their timing until explicitly retimed by server policy. */
    public Rules(
            int version,
            Material material,
            long conversion,
            long inversion,
            long coupling,
            long nodeCapacity,
            long injection,
            int interval,
            int window,
            long signalCost,
            long playerCapacity,
            long playerRegen,
            int playerDelay,
            long range,
            long radius,
            long routingRetention,
            long oppositionRetention,
            BoardSizes boards) {
        this(
                version,
                material,
                conversion,
                inversion,
                coupling,
                nodeCapacity,
                injection,
                interval,
                window,
                signalCost,
                playerCapacity,
                playerRegen,
                playerDelay,
                range,
                radius,
                routingRetention,
                oppositionRetention,
                boards,
                1);
    }

    public Rules(
            int version,
            Material material,
            long conversion,
            long inversion,
            long coupling,
            long nodeCapacity,
            long injection,
            int interval,
            int window,
            long signalCost,
            long playerCapacity,
            long playerRegen,
            int playerDelay,
            long range,
            long radius,
            long routingRetention,
            long oppositionRetention,
            BoardSizes boards,
            int manaSteps) {
        this(
                version,
                material,
                conversion,
                inversion,
                coupling,
                nodeCapacity,
                injection,
                interval,
                window,
                signalCost,
                playerCapacity,
                playerRegen,
                playerDelay,
                range,
                radius,
                routingRetention,
                oppositionRetention,
                boards,
                manaSteps,
                CastingRules.legacy());
    }

    public enum Material {
        WOOD,
        BRASS,
        CRYSTAL
    }

    public Rules {
        if ((version < 1 || version > 4)
                || material == null
                || boards == null
                || casting == null
                || version < 4 && casting.modern()
                || manaSteps < 1
                || manaSteps > 64
                || version < 3 && manaSteps != 1
                || version == 1 && !boards.equals(BoardSizes.legacy()))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._rules.002"));
        for (long rate :
                new long[] {conversion, inversion, coupling, routingRetention, oppositionRetention})
            if (rate <= 0 || rate > Fixed.ONE)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.circuit._rules.003"));
        for (long amount : new long[] {nodeCapacity, injection, playerCapacity, playerRegen})
            Fixed.checked(amount);
        if (nodeCapacity == 0
                || injection == 0
                || !casting.explicitStorage() && injection > nodeCapacity
                || playerCapacity == 0
                || interval < 1
                || interval > Integer.MAX_VALUE - 1
                || window < 1
                || window > Integer.MAX_VALUE - 1
                || playerDelay < 0
                || playerDelay > Integer.MAX_VALUE - 1
                || signalCost < 0
                || signalCost > Fixed.ONE
                || range < 1
                || range > CircuitBounds.RANGE
                || radius < 1
                || radius > CircuitBounds.RADIUS)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.circuit._rules.004"));
    }

    public int boardRadius() {
        return switch (material) {
            case WOOD -> boards.wood();
            case BRASS -> boards.brass();
            case CRYSTAL -> boards.crystal();
        };
    }

    public long retention() {
        return switch (material) {
            case WOOD -> 62259;
            case BRASS -> 64880;
            case CRYSTAL -> 65405;
        };
    }

    public Rules withMaterial(Material m) {
        return new Rules(
                version,
                m,
                conversion,
                inversion,
                coupling,
                nodeCapacity,
                injection,
                interval,
                window,
                signalCost,
                playerCapacity,
                playerRegen,
                playerDelay,
                range,
                radius,
                routingRetention,
                oppositionRetention,
                boards,
                manaSteps,
                casting);
    }

    public Rules withBoards(BoardSizes sizes) {
        return new Rules(
                Math.max(2, version),
                material,
                conversion,
                inversion,
                coupling,
                nodeCapacity,
                injection,
                interval,
                window,
                signalCost,
                playerCapacity,
                playerRegen,
                playerDelay,
                range,
                radius,
                routingRetention,
                oppositionRetention,
                sizes,
                manaSteps,
                casting);
    }

    public Rules withManaSteps(int steps) {
        return new Rules(
                Math.max(3, version),
                material,
                conversion,
                inversion,
                coupling,
                nodeCapacity,
                injection,
                interval,
                window,
                signalCost,
                playerCapacity,
                playerRegen,
                playerDelay,
                range,
                radius,
                routingRetention,
                oppositionRetention,
                boards,
                steps,
                casting);
    }

    public Rules withCasting(CastingRules policy) {
        return new Rules(
                4,
                material,
                conversion,
                inversion,
                coupling,
                nodeCapacity,
                injection,
                interval,
                window,
                signalCost,
                playerCapacity,
                playerRegen,
                playerDelay,
                range,
                radius,
                routingRetention,
                oppositionRetention,
                boards,
                manaSteps,
                policy);
    }
}
