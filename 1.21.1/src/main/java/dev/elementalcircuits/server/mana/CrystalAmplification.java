package dev.elementalcircuits.server.mana;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.config.ServerSettings;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.casting.CastContext;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Exactly one adjacent block below; bounded lookup, no towers, chunk loads or hidden energy source.
 */
public final class CrystalAmplification {
    private CrystalAmplification() {}

    public static boolean active(Level level, BlockPos target) {
        return level != null
                && target.getY() > level.getMinBuildHeight()
                && level.hasChunkAt(target.below())
                && level.getBlockState(target.below())
                        .is(ElementalCircuits.MANA_CRYSTAL_BLOCK.get());
    }

    public static long scale(Level level, BlockPos target, long value, String key) {
        return active(level, target)
                ? Math.min(Fixed.MAX, Math.round(value * ServerSettings.get(key)))
                : value;
    }

    public static CastContext context(Level level, BlockPos target) {
        if (!active(level, target)) return CastContext.NEUTRAL;
        long power = ServerSettings.mana("crystal.circuitPowerMultiplier");
        return new CastContext(
                power, power, false, ServerSettings.mana("crystal.circuitSpeedMultiplier"));
    }
}
