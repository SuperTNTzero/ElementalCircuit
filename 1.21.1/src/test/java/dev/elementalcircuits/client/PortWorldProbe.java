package dev.elementalcircuits.client;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.*;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.AssemblyContracts;
import dev.elementalcircuits.network.CircuitNetwork;
import dev.elementalcircuits.server.*;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import java.util.UUID;

/** Exercises both directions of the native 1.21 payload transport in an isolated world. */
final class PortWorldProbe {
    private static final Blueprint BOARD=AssemblyContracts.line(false,Route.Payload.MANA,Rune.Kind.INPUT,Rune.Kind.CONVERT,Rune.Kind.DIRECTED,Rune.Kind.BEAM,Rune.Kind.OUTPUT);
    private static volatile UUID identity;
    private static volatile boolean complete;
    private static boolean received,submitted,started,reported;
    private static long appliedTick;
    static boolean complete(){return complete;}
    static void server(ServerLevel level){
        if(level.players().isEmpty())return;var player=level.players().get(0);
        if(identity==null){
            var wand=new ItemStack(ElementalCircuits.WAND.get());player.getInventory().selected=0;player.getInventory().setItem(0,wand);
            var table=player.blockPosition().offset(2,0,0);level.setBlockAndUpdate(table,ElementalCircuits.TABLE.get().defaultBlockState());
            InscriptionAccess.interact(player,table);
            if(!InscriptionAccess.allowed(player,wand,null))throw new AssertionError("网络验证必须先建立实体工作台刻印会话");
            identity=CarrierService.identity(wand);return;
        }
        if(complete)return;var wand=player.getMainHandItem();var a=CarrierService.assembly(wand);
        if(!received && a.circuit(1).definition().equals(BOARD)){received=true;appliedTick=level.getGameTime();CarrierEditor.sync(player,wand,true,"1.21实际网络刻印已确认");}
        if(received && level.getGameTime()>appliedTick+2 && a.circuit(1).snapshot().delivered()>0 && wand.getDamageValue()>0 && PlayerMana.get(player).snapshot().spent()>0)complete=true;
    }
    static boolean ready(Minecraft mc){
        if(identity==null || mc.player==null)return false;
        if(!submitted){submitted=true;String json=JsonCodecs.blueprint(BOARD)+" ".repeat(40000);if(json.length()>65536)throw new AssertionError("测试载荷超过协议字符边界");CircuitNetwork.request(identity,1,0,json);}
        if(!started && mc.screen instanceof CircuitScreen screen && screen.draftForExport().equals(BOARD)){started=true;screen.onClose();CircuitNetwork.request(identity,1,1,"");}
        if(complete && !reported){reported=true;org.slf4j.LoggerFactory.getLogger("PortWorldProbe").info("PORT_WORLD_NETWORK_PASS: >40KB client payload + server edit + editor reply + client start + finite supply + actual cast wear");}
        return complete;
    }
}

