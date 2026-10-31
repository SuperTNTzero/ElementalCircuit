package dev.elementalcircuits.server;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.persistence.CarrierRepository;
import dev.elementalcircuits.application.runtime.CircuitHost;
import dev.elementalcircuits.block.CircuitBlockEntity;
import dev.elementalcircuits.domain.*;
import net.minecraft.server.level.*;
import net.minecraft.world.item.ItemStack;
import java.util.UUID;

/** Maps Minecraft ownership, finite supply and effects onto the engine-free host contract. */
final class MinecraftCircuitHost implements CircuitHost {
    private final ServerLevel level;private final ItemStack stack;private final ServerPlayer player;private final CircuitBlockEntity block;
    private CircuitAssembly assembly;private ItemStack equipment;
    private MinecraftCircuitHost(ServerLevel level,ItemStack stack,ServerPlayer player,CircuitBlockEntity block){this.level=level;this.stack=stack;this.player=player;this.block=block;}
    static MinecraftCircuitHost carried(ServerLevel level,ItemStack stack,ServerPlayer player){return new MinecraftCircuitHost(level,stack,player,null);}
    static MinecraftCircuitHost fixed(ServerLevel level,CircuitBlockEntity block){return new MinecraftCircuitHost(level,block.carrier(),null,block);}
    @Override public CircuitAssembly assembly(){if(assembly==null)assembly=CarrierRepository.load(stack);return assembly;}
    @Override public boolean prepare(){
        if(dev.elementalcircuits.adapter.persistence.ItemData.read(stack).getBoolean("ElementalCircuitFault") || dev.elementalcircuits.adapter.persistence.ItemData.read(stack).getCompound("ElementalCircuit").getBoolean("Corrupt")){assembly.stopAll();return false;}
        if(block!=null){if(!WorldCircuitLinks.get(level).anchored(assembly.identity(),block.getBlockPos())){assembly.stopAll();return false;}return true;}
        boolean owned=false;for(int slot=0;slot<player.getInventory().getContainerSize();slot++)if(player.getInventory().getItem(slot)==stack){owned=true;break;}
        if(!owned || player.serverLevel()!=level || !player.isAlive() || player.isRemoved()){assembly.stopAll();return false;}
        if(stack.is(ElementalCircuits.SPELL_BOOK.get()) && (player.getOffhandItem()!=stack || !player.getMainHandItem().is(ElementalCircuits.WAND.get()))){assembly.stopAll();return false;}
        equipment=CastingEquipment.wand(player,stack);if(WandEquipment.broken(equipment)){assembly.stopAll();return false;}return true;
    }
    @Override public ManaSource supply(){return block==null?PlayerMana.get(player):block;}
    private ManaSource ambient;
    @Override public ManaSource ambient(){
        if(ambient==null){boolean needs=assembly.slots().stream().map(assembly::circuit).anyMatch(c->c.powered() && c.definition().runes().stream().anyMatch(r->r.kind()==Rune.Kind.ABSORB));
            ambient=needs?WorldManaField.get(level).tap(level,block==null?player.blockPosition():block.getBlockPos()):ManaSource.NONE;
        }return ambient;
    }
    @Override public int steps(){return 1;}
    @Override public ReleasePermit releasePermit(){return block==null?()->WandCooldown.tryRelease(equipment):ReleasePermit.UNLIMITED;}
    @Override public void emit(CircuitAssembly.Output output){
        if(output.cast().mana().total()==output.cast().mana().values()[0])return;
        UUID owner=block==null?player.getUUID():block.owner();
        SpellEffects.castOwned(level,owner,block==null?player.getEyePosition():block.origin(),block==null?player.getLookAngle():block.direction(),assembly.identity(),output.cast(),assembly.circuit(output.slot()).definition().rules(),block==null?WandEquipment.stats(equipment).strength():Fixed.ONE);
        if(block==null)WandEquipment.wear(equipment);
    }
    @Override public void afterAdvance(){if(block==null && WandEquipment.broken(equipment))assembly.stopAll();}
    @Override public void commit(){boolean changed=CarrierRepository.commit(stack);if(block!=null){if(changed)block.setChanged();}else PlayerMana.save(player);}
    @Override public void failed(RuntimeException error){CarrierFaults.report(stack,player);}
}
