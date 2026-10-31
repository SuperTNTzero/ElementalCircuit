package dev.elementalcircuits.network;
import dev.elementalcircuits.client.BookClient;
import dev.elementalcircuits.server.*;
import net.minecraft.server.level.ServerPlayer;
import dev.elementalcircuits.ElementalCircuits;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.UUID;

public final class BookNetwork {
    public record WorkshopRequest(UUID identity,long position,int action,String part) implements CustomPacketPayload {
        public static final Type<WorkshopRequest> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(ElementalCircuits.ID,"workshop_request"));
        @Override public Type<WorkshopRequest> type(){return TYPE;}
    }
    public record WorkshopState(String json,boolean open) implements CustomPacketPayload {
        public static final Type<WorkshopState> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(ElementalCircuits.ID,"workshop_state"));
        @Override public Type<WorkshopState> type(){return TYPE;}
    }
    public record NotebookRequest(UUID identity,int action,int page,String title,String text) implements CustomPacketPayload {
        public static final Type<NotebookRequest> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(ElementalCircuits.ID,"notebook_request"));
        @Override public Type<NotebookRequest> type(){return TYPE;}
    }
    public record NotebookState(String json,boolean open) implements CustomPacketPayload {
        public static final Type<NotebookState> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(ElementalCircuits.ID,"notebook_state"));
        @Override public Type<NotebookState> type(){return TYPE;}
    }
    public record SpellRequest(UUID identity,int action,int value) implements CustomPacketPayload {
        public static final Type<SpellRequest> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(ElementalCircuits.ID,"spell_request"));
        @Override public Type<SpellRequest> type(){return TYPE;}
    }
    public record SpellState(String json,boolean open) implements CustomPacketPayload {
        public static final Type<SpellState> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(ElementalCircuits.ID,"spell_state"));
        @Override public Type<SpellState> type(){return TYPE;}
    }
    public record SpellHud(UUID identity,int selected,int pages,boolean powered) implements CustomPacketPayload {
        public static final Type<SpellHud> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(ElementalCircuits.ID,"spell_hud"));
        @Override public Type<SpellHud> type(){return TYPE;}
    }
    private BookNetwork(){ }
    public static void register(PayloadRegistrar registrar){
        registrar.playToServer(WorkshopRequest.TYPE,StreamCodec.of((b,p)->{b.writeUUID(p.identity);b.writeLong(p.position);b.writeVarInt(p.action);b.writeUtf(p.part,40);},b->new WorkshopRequest(b.readUUID(),b.readLong(),b.readVarInt(),b.readUtf(40))),(p,x)->{var player=x.player();if(player instanceof ServerPlayer server)WorkshopService.request(server,p.identity,p.position,p.action,p.part);});
        registrar.playToClient(WorkshopState.TYPE,StreamCodec.of((b,p)->{b.writeUtf(p.json,8192);b.writeBoolean(p.open);},b->new WorkshopState(b.readUtf(8192),b.readBoolean())),(p,x)->BookClient.workshop(p));
        registrar.playToServer(NotebookRequest.TYPE,StreamCodec.of((b,p)->{b.writeUUID(p.identity);b.writeVarInt(p.action);b.writeVarInt(p.page);b.writeUtf(p.title,32);b.writeUtf(p.text,dev.elementalcircuits.adapter.BlueprintTextCode.LIMIT);},b->new NotebookRequest(b.readUUID(),b.readVarInt(),b.readVarInt(),b.readUtf(32),b.readUtf(dev.elementalcircuits.adapter.BlueprintTextCode.LIMIT))),(p,x)->{var player=x.player();if(player instanceof ServerPlayer server)BookService.notebookRequest(server,p.identity,p.action,p.page,p.title,p.text);});
        registrar.playToClient(NotebookState.TYPE,StreamCodec.of((b,p)->{b.writeUtf(p.json,131072);b.writeBoolean(p.open);},b->new NotebookState(b.readUtf(131072),b.readBoolean())),(p,x)->BookClient.notebook(p));
        registrar.playToServer(SpellRequest.TYPE,StreamCodec.of((b,p)->{b.writeUUID(p.identity);b.writeVarInt(p.action);b.writeVarInt(p.value);},b->new SpellRequest(b.readUUID(),b.readVarInt(),b.readVarInt())),(p,x)->{var player=x.player();if(player instanceof ServerPlayer server)BookService.spellRequest(server,p.identity,p.action,p.value);});
        registrar.playToClient(SpellState.TYPE,StreamCodec.of((b,p)->{b.writeUtf(p.json,524288);b.writeBoolean(p.open);},b->new SpellState(b.readUtf(524288),b.readBoolean())),(p,x)->BookClient.spellbook(p));
        registrar.playToClient(SpellHud.TYPE,StreamCodec.of((b,p)->{b.writeUUID(p.identity);b.writeVarInt(p.selected);b.writeVarInt(p.pages);b.writeBoolean(p.powered);},b->new SpellHud(b.readUUID(),b.readVarInt(),b.readVarInt(),b.readBoolean())),(p,x)->BookClient.hud(p));
    }
    public static void send(CustomPacketPayload packet){PacketDistributor.sendToServer(packet);}
    private static void to(ServerPlayer p,CustomPacketPayload packet){PacketDistributor.sendToPlayer(p,packet);}
    public static void workshop(ServerPlayer p,String json,boolean open){to(p,new WorkshopState(json,open));}
    public static void notebook(ServerPlayer p,String json,boolean open){to(p,new NotebookState(json,open));}
    public static void spellbook(ServerPlayer p,String json,boolean open){to(p,new SpellState(json,open));}
    public static void hud(ServerPlayer p,UUID id,int selected,int pages,boolean powered){to(p,new SpellHud(id,selected,pages,powered));}
}
