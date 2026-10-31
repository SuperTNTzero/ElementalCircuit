package dev.elementalcircuits.server;

import dev.elementalcircuits.*;
import dev.elementalcircuits.adapter.*;
import dev.elementalcircuits.adapter.persistence.CarrierRepository;
import dev.elementalcircuits.application.observation.AssemblyInspection;
import dev.elementalcircuits.block.CircuitBlockEntity;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.network.CircuitNetwork;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import java.util.*;
import static dev.elementalcircuits.server.MaterialInventory.*;

/** Builds detached observations and sends them through the editor transport adapter. */
public final class CarrierEditor {
    private CarrierEditor(){}
    public static void open(ServerPlayer player){var stack=player.getMainHandItem();if(stack.is(ElementalCircuits.WAND.get()))stack=CastingEquipment.carrier(player,stack);if(!InscriptionAccess.allowed(player,stack,null)){player.displayClientMessage(Component.literal("持物品右键法阵工作台刻印"),true);return;}sync(player,stack,null,true,"刻印后保存");}
    public static void open(ServerPlayer player,CircuitBlockEntity block){if(block.allowed(player) && InscriptionAccess.allowed(player,block.carrier(),block))sync(player,block.carrier(),block,true,"固定法阵刻印");}
    public static void sync(ServerPlayer player,ItemStack stack,boolean open,String message){sync(player,stack,null,open,message);}
    public static void sync(ServerPlayer player,ItemStack stack,CircuitBlockEntity block,boolean open,String message){sync(player,stack,block,open,message,new UUID(0,0));}
    public static void sync(ServerPlayer player,ItemStack stack,CircuitBlockEntity block,boolean open,String message,UUID token){
        var a=CarrierRepository.load(stack);var c=a.circuit(a.selected());String text=message==null?"操作失败":message;
        CircuitNetwork.sendEditor(player,new CircuitNetwork.Editor(a.identity(),JsonCodecs.blueprint(c.definition()),c.powered(),c.held(),open,text.substring(0,Math.min(256,text.length())),block==null && !stack.is(ElementalCircuits.SPELL_BOOK.get())?WandEquipment.assembly(stack):player.getMainHandItem().is(ElementalCircuits.WAND.get())?WandEquipment.assembly(player.getMainHandItem()):WandResources.definitions().basic(),stack.is(ElementalCircuits.SPELL_BOOK.get())?player.getMainHandItem().getDamageValue():stack.getDamageValue(),a.selected(),block!=null || stack.is(ElementalCircuits.CIRCUIT_ITEM.get()),JsonCodecs.inspection(inspect(player,a,block)),JsonCodecs.logicObservation(c),stack.is(ElementalCircuits.SPELL_BOOK.get()),token,InscriptionAccess.isDraft(player,stack)));
    }
    private static boolean port(Rune.Kind k){return k==Rune.Kind.MANA_SEND || k==Rune.Kind.MANA_RECEIVE || k==Rune.Kind.SIGNAL_SEND || k==Rune.Kind.SIGNAL_RECEIVE;}
    private static void ports(List<AssemblyInspection.Port> result,CircuitAssembly a,String label){for(int slot:a.slots())for(Rune r:a.circuit(slot).definition().runes())if(port(r.kind()))result.add(new AssemblyInspection.Port(new CircuitAddress(a.identity(),slot,r.id()),r.kind(),label+" / 阵"+slot+" / #"+r.id()));}
    private static AssemblyInspection inspect(ServerPlayer player,CircuitAssembly a,CircuitBlockEntity block){
        List<AssemblyInspection.Port> ports=new ArrayList<>();ports(ports,a,"本载体");List<AssemblyInspection.Link> links=new ArrayList<>();
        for(var l:a.links().connections())links.add(new AssemblyInspection.Link(l.id(),false,label(l,a.identity()),l.transit()==null?0:l.transit().mana().total()));
        if(block!=null){
            for(var other:WorldCircuits.blocks(player.serverLevel()).stream().sorted(Comparator.comparingLong(b->b.getBlockPos().asLong())).toList())if(other!=block && other.owns(player) && other.getBlockPos().distSqr(block.getBlockPos())<=a.rules().range()*a.rules().range())try{if(ports.size()<384){ports(ports,other.assembly(),other.getBlockPos().toShortString());if(ports.size()>384)ports.subList(384,ports.size()).clear();}}catch(RuntimeException ignored){}
            for(var l:WorldCircuitLinks.get(player.serverLevel()).graph().connections())if(l.from().carrier().equals(a.identity()) || l.to().carrier().equals(a.identity()))links.add(new AssemblyInspection.Link(l.id(),true,label(l,a.identity()),l.transit()==null?0:l.transit().mana().total()));
        }
        return new AssemblyInspection(a.selected(),a.rules().maxSlots(),block!=null,block==null?0:block.available(),a.rules().blockCapacity(),a.slots().stream().map(id->{var c=a.circuit(id);return new AssemblyInspection.Slot(id,c.definition().rules().material(),c.powered(),c.held());}).toList(),ports,links,Arrays.stream(Rules.Material.values()).map(m->new AssemblyInspection.Stock(m,count(player,plate(m)))).toList());
    }
    private static String label(CircuitLinks.Connection l,UUID own){return (l.payload()==Route.Payload.MANA?"魔力 ":"信号 ")+(l.from().carrier().equals(own)?"本":"外")+l.from().slot()+"#"+l.from().rune()+" → "+(l.to().carrier().equals(own)?"本":"外")+l.to().slot()+"#"+l.to().rune();}
}
