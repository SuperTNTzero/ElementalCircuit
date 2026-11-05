package dev.elementalcircuits.entity;

import net.minecraft.core.particles.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.level.Level;

/** Native slime AI, size, split, persistence and monster cap; only its ecology and loot differ. */
public final class ManaSlime extends Slime {
    public ManaSlime(EntityType<? extends Slime> type, Level level) {
        super(type, level);
    }

    @Override
    protected ParticleOptions getParticleType() {
        return ParticleTypes.END_ROD;
    }
}
