package dev.elementalcircuits.server;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.persistence.CarrierRepository;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Equipment binding is an execution capability, independent of notebook/spellbook UI commands. */
public final class CastingEquipment {
    private CastingEquipment(){}
    public static ItemStack carrier(ServerPlayer player,ItemStack wand){return player.getMainHandItem()==wand && player.getOffhandItem().is(ElementalCircuits.SPELL_BOOK.get())?player.getOffhandItem():wand;}
    public static ItemStack wand(ServerPlayer player,ItemStack carrier){
        if(!carrier.is(ElementalCircuits.SPELL_BOOK.get()))return carrier;
        if(player.getOffhandItem()!=carrier || !player.getMainHandItem().is(ElementalCircuits.WAND.get()))throw new IllegalArgumentException("施法需要主手法杖与副手法术书");return player.getMainHandItem();
    }
    public static boolean pairedBookPowered(ServerPlayer player){return player.getOffhandItem().is(ElementalCircuits.SPELL_BOOK.get()) && CarrierRepository.load(player.getOffhandItem()).powered();}
}
