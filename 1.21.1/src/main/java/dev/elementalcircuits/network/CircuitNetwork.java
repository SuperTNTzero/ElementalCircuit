package dev.elementalcircuits.network;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.client.ClientHooks;
import dev.elementalcircuits.server.CarrierService;
import dev.elementalcircuits.domain.WandParts;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.UUID;

public final class CircuitNetwork {
    public record Request(UUID identity,int slot,int action,String payload,UUID token) implements CustomPacketPayload {
        public static final Type<Request> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(ElementalCircuits.ID,"request"));
        @Override public Type<Request> type(){return TYPE;}
        public Request(UUID identity,int slot,int action,String payload){this(identity,slot,action,payload,new UUID(0,0));}
    }
    public record Editor(UUID identity,String blueprint,boolean powered,long held,boolean open,String message,WandParts.Assembly assembly,int wear,int slot,boolean fixed,String management,String logic,boolean book,UUID token,boolean draft) implements CustomPacketPayload {
        public static final Type<Editor> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(ElementalCircuits.ID,"editor"));
        @Override public Type<Editor> type(){return TYPE;}
        public Editor(UUID identity,String blueprint,boolean powered,long held,boolean open,String message,WandParts.Assembly assembly,int wear,int slot,boolean fixed,String management,String logic,boolean book,UUID token){this(identity,blueprint,powered,held,open,message,assembly,wear,slot,fixed,management,logic,book,token,false);}
        public Editor(UUID identity,String blueprint,boolean powered,long held,boolean open,String message,WandParts.Assembly assembly,int wear,int slot,boolean fixed,String management,String logic,boolean book){this(identity,blueprint,powered,held,open,message,assembly,wear,slot,fixed,management,logic,book,new UUID(0,0));}
        public Editor(UUID identity,String blueprint,boolean powered,long held,boolean open,String message,WandParts.Assembly assembly,int wear,int slot,boolean fixed,String management,String logic){this(identity,blueprint,powered,held,open,message,assembly,wear,slot,fixed,management,logic,false);}
        public Editor(UUID identity,String blueprint,boolean powered,long held,boolean open,String message,WandParts.Assembly assembly,int wear){this(identity,blueprint,powered,held,open,message,assembly,wear,1,false,"","");}
        public Editor(UUID identity,String blueprint,boolean powered,long held,boolean open,String message,WandParts.Assembly assembly,int wear,int slot,boolean fixed,String management){this(identity,blueprint,powered,held,open,message,assembly,wear,slot,fixed,management,"");}
    }
    public record Mana(long amount,long capacity) implements CustomPacketPayload {
        public static final Type<Mana> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(ElementalCircuits.ID,"mana"));
        @Override public Type<Mana> type(){return TYPE;}
    }
    private CircuitNetwork(){ }
    public static void register(RegisterPayloadHandlersEvent event){
        PayloadRegistrar registrar=event.registrar("1.21.1-8");
        registrar.playToServer(Request.TYPE,StreamCodec.of((b,p)->writeRequest(p,b),CircuitNetwork::readRequest),(p,c)->{if(c.player() instanceof ServerPlayer player)dev.elementalcircuits.server.CarrierCommands.request(player,p.identity,p.slot,p.action,p.payload,p.token);});
        registrar.playToClient(Editor.TYPE,StreamCodec.of((b,p)->writeEditor(p,b),CircuitNetwork::readEditor),(p,c)->ClientHooks.editor(p));
        registrar.playToClient(Mana.TYPE,StreamCodec.of((b,p)->{b.writeLong(p.amount);b.writeLong(p.capacity);},b->new Mana(b.readLong(),b.readLong())),(p,c)->ClientHooks.mana(p));
        BookNetwork.register(registrar);VisualNetwork.register(registrar);FeedbackNetwork.register(registrar);
    }
    public static void writeRequest(Request p,FriendlyByteBuf b){b.writeUUID(p.identity);b.writeVarInt(p.slot);b.writeVarInt(p.action);b.writeUtf(p.payload,65536);b.writeUUID(p.token);}
    public static Request readRequest(FriendlyByteBuf b){return new Request(b.readUUID(),b.readVarInt(),b.readVarInt(),b.readUtf(65536),b.readUUID());}
    public static void writeEditor(Editor p,FriendlyByteBuf b){b.writeUUID(p.identity);b.writeUtf(p.blueprint,65536);b.writeBoolean(p.powered);b.writeLong(p.held);b.writeBoolean(p.open);b.writeUtf(p.message,512);b.writeUtf(p.assembly.head(),40);b.writeUtf(p.assembly.body(),40);b.writeUtf(p.assembly.tail(),40);b.writeVarInt(p.wear);b.writeVarInt(p.slot);b.writeBoolean(p.fixed);b.writeUtf(p.management,131072);b.writeUtf(p.logic,16384);b.writeBoolean(p.book);b.writeUUID(p.token);b.writeBoolean(p.draft);}
    public static Editor readEditor(FriendlyByteBuf b){return new Editor(b.readUUID(),b.readUtf(65536),b.readBoolean(),b.readLong(),b.readBoolean(),b.readUtf(512),new WandParts.Assembly(b.readUtf(40),b.readUtf(40),b.readUtf(40)),b.readVarInt(),b.readVarInt(),b.readBoolean(),b.readUtf(131072),b.readUtf(16384),b.readBoolean(),b.readUUID(),b.readBoolean());}
    public static void request(UUID identity,int action,String payload){request(identity,0,action,payload);}
    public static void request(UUID identity,int slot,int action,String payload){PacketDistributor.sendToServer(new Request(identity,slot,action,payload));}
    public static void request(UUID identity,int slot,int action,String payload,UUID token){PacketDistributor.sendToServer(new Request(identity,slot,action,payload,token));}
    public static void sendEditor(ServerPlayer player,Editor packet){PacketDistributor.sendToPlayer(player,packet);}
    public static void sendEditor(ServerPlayer player,UUID id,String blueprint,boolean powered,long held,boolean open,String message,WandParts.Assembly assembly,int wear){PacketDistributor.sendToPlayer(player,new Editor(id,blueprint,powered,held,open,message,assembly,wear));}
    public static void sendMana(ServerPlayer player,long amount,long capacity){PacketDistributor.sendToPlayer(player,new Mana(amount,capacity));}
}
