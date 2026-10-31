package dev.elementalcircuits.network;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.client.ClientHooks;
import dev.elementalcircuits.server.CarrierService;
import dev.elementalcircuits.domain.WandParts;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.UUID;

public final class CircuitNetwork {
    private static final String PROTOCOL="10";
    public static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation(ElementalCircuits.ID,"main"),()->PROTOCOL,PROTOCOL::equals,PROTOCOL::equals);
    public record Request(UUID identity,int slot,int action,String payload,UUID token){
        public Request(UUID identity,int slot,int action,String payload){this(identity,slot,action,payload,new UUID(0,0));}
    }
    public record Editor(UUID identity,String blueprint,boolean powered,long held,boolean open,String message,WandParts.Assembly assembly,int wear,int slot,boolean fixed,String management,String logic,boolean book,UUID token){
        public Editor(UUID identity,String blueprint,boolean powered,long held,boolean open,String message,WandParts.Assembly assembly,int wear,int slot,boolean fixed,String management,String logic,boolean book){this(identity,blueprint,powered,held,open,message,assembly,wear,slot,fixed,management,logic,book,new UUID(0,0));}
        public Editor(UUID identity,String blueprint,boolean powered,long held,boolean open,String message,WandParts.Assembly assembly,int wear,int slot,boolean fixed,String management,String logic){this(identity,blueprint,powered,held,open,message,assembly,wear,slot,fixed,management,logic,false);}
        public Editor(UUID identity,String blueprint,boolean powered,long held,boolean open,String message,WandParts.Assembly assembly,int wear){this(identity,blueprint,powered,held,open,message,assembly,wear,1,false,"","");}
        public Editor(UUID identity,String blueprint,boolean powered,long held,boolean open,String message,WandParts.Assembly assembly,int wear,int slot,boolean fixed,String management){this(identity,blueprint,powered,held,open,message,assembly,wear,slot,fixed,management,"");}
    }
    public record Mana(long amount,long capacity){ }
    private CircuitNetwork(){ }
    public static void register(){
        CHANNEL.messageBuilder(Request.class,0,NetworkDirection.PLAY_TO_SERVER)
            .encoder(CircuitNetwork::writeRequest)
            .decoder(CircuitNetwork::readRequest)
            .consumerMainThread((p,c)->{ServerPlayer player=c.get().getSender();if(player!=null)dev.elementalcircuits.server.CarrierCommands.request(player,p.identity,p.slot,p.action,p.payload,p.token);}).add();
        CHANNEL.messageBuilder(Editor.class,1,NetworkDirection.PLAY_TO_CLIENT)
            .encoder(CircuitNetwork::writeEditor)
            .decoder(CircuitNetwork::readEditor)
            .consumerMainThread((p,c)->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->ClientHooks.editor(p))).add();
        CHANNEL.messageBuilder(Mana.class,2,NetworkDirection.PLAY_TO_CLIENT)
            .encoder((p,b)->{b.writeLong(p.amount);b.writeLong(p.capacity);})
            .decoder(b->new Mana(b.readLong(),b.readLong()))
            .consumerMainThread((p,c)->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->ClientHooks.mana(p))).add();
        BookNetwork.register();VisualNetwork.register();
    }
    public static void writeRequest(Request p,FriendlyByteBuf b){b.writeUUID(p.identity);b.writeVarInt(p.slot);b.writeVarInt(p.action);b.writeUtf(p.payload,65536);b.writeUUID(p.token);}
    public static Request readRequest(FriendlyByteBuf b){return new Request(b.readUUID(),b.readVarInt(),b.readVarInt(),b.readUtf(65536),b.readUUID());}
    public static void writeEditor(Editor p,FriendlyByteBuf b){b.writeUUID(p.identity);b.writeUtf(p.blueprint,65536);b.writeBoolean(p.powered);b.writeLong(p.held);b.writeBoolean(p.open);b.writeUtf(p.message,512);b.writeUtf(p.assembly.head(),40);b.writeUtf(p.assembly.body(),40);b.writeUtf(p.assembly.tail(),40);b.writeVarInt(p.wear);b.writeVarInt(p.slot);b.writeBoolean(p.fixed);b.writeUtf(p.management,131072);b.writeUtf(p.logic,16384);b.writeBoolean(p.book);b.writeUUID(p.token);}
    public static Editor readEditor(FriendlyByteBuf b){return new Editor(b.readUUID(),b.readUtf(65536),b.readBoolean(),b.readLong(),b.readBoolean(),b.readUtf(512),new WandParts.Assembly(b.readUtf(40),b.readUtf(40),b.readUtf(40)),b.readVarInt(),b.readVarInt(),b.readBoolean(),b.readUtf(131072),b.readUtf(16384),b.readBoolean(),b.readUUID());}
    public static void request(UUID identity,int action,String payload){request(identity,0,action,payload);}
    public static void request(UUID identity,int slot,int action,String payload){CHANNEL.sendToServer(new Request(identity,slot,action,payload));}
    public static void request(UUID identity,int slot,int action,String payload,UUID token){CHANNEL.sendToServer(new Request(identity,slot,action,payload,token));}
    public static void sendEditor(ServerPlayer player,Editor packet){CHANNEL.send(PacketDistributor.PLAYER.with(()->player),packet);}
    public static void sendEditor(ServerPlayer player,UUID id,String blueprint,boolean powered,long held,boolean open,String message,WandParts.Assembly assembly,int wear){CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new Editor(id,blueprint,powered,held,open,message,assembly,wear));}
    public static void sendMana(ServerPlayer player,long amount,long capacity){CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new Mana(amount,capacity));}
}
