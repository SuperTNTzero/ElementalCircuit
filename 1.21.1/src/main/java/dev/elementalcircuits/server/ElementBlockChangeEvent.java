package dev.elementalcircuits.server;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;


/** Placement-compatible preflight. The snapshot/world still contain the old state until approved. */
public final class ElementBlockChangeEvent extends BlockEvent.EntityPlaceEvent {
    private final BlockState intended;
    public ElementBlockChangeEvent(BlockSnapshot before,BlockState against,Entity owner,BlockState intended){
        super(before,against,owner);this.intended=intended;
    }
    @Override public BlockState getPlacedBlock(){return intended;}
    @Override public BlockState getState(){return intended;}
}
