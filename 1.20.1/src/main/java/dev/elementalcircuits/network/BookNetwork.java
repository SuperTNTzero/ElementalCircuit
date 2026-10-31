package dev.elementalcircuits.network;
import dev.elementalcircuits.client.BookClient;
import dev.elementalcircuits.server.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import java.util.UUID;

public final class BookNetwork {
    public record WorkshopRequest(UUID identity,long position,int action,String part){ }
    public record WorkshopState(String json,boolean open){ }
    public record NotebookRequest(UUID identity,int action,int page,String title,String text){ }
    public record NotebookState(String json,boolean open){ }
    public record SpellRequest(UUID identity,int action,int value){ }
    public record SpellState(String json,boolean open){ }
    public record SpellHud(UUID identity,int selected,int pages,boolean powered){ }
    private BookNetwork(){ }
    public static void register(){var c=CircuitNetwork.CHANNEL;
        c.messageBuilder(WorkshopRequest.class,3,NetworkDirection.PLAY_TO_SERVER).encoder((p,b)->{b.writeUUID(p.identity);b.writeLong(p.position);b.writeVarInt(p.action);b.writeUtf(p.part,40);}).decoder(b->new WorkshopRequest(b.readUUID(),b.readLong(),b.readVarInt(),b.readUtf(40))).consumerMainThread((p,x)->{var player=x.get().getSender();if(player!=null)WorkshopService.request(player,p.identity,p.position,p.action,p.part);}).add();
        c.messageBuilder(WorkshopState.class,4,NetworkDirection.PLAY_TO_CLIENT).encoder((p,b)->{b.writeUtf(p.json,8192);b.writeBoolean(p.open);}).decoder(b->new WorkshopState(b.readUtf(8192),b.readBoolean())).consumerMainThread((p,x)->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->BookClient.workshop(p))).add();
        c.messageBuilder(NotebookRequest.class,5,NetworkDirection.PLAY_TO_SERVER).encoder((p,b)->{b.writeUUID(p.identity);b.writeVarInt(p.action);b.writeVarInt(p.page);b.writeUtf(p.title,32);b.writeUtf(p.text,dev.elementalcircuits.adapter.BlueprintTextCode.LIMIT);}).decoder(b->new NotebookRequest(b.readUUID(),b.readVarInt(),b.readVarInt(),b.readUtf(32),b.readUtf(dev.elementalcircuits.adapter.BlueprintTextCode.LIMIT))).consumerMainThread((p,x)->{var player=x.get().getSender();if(player!=null)BookService.notebookRequest(player,p.identity,p.action,p.page,p.title,p.text);}).add();
        c.messageBuilder(NotebookState.class,6,NetworkDirection.PLAY_TO_CLIENT).encoder((p,b)->{b.writeUtf(p.json,131072);b.writeBoolean(p.open);}).decoder(b->new NotebookState(b.readUtf(131072),b.readBoolean())).consumerMainThread((p,x)->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->BookClient.notebook(p))).add();
        c.messageBuilder(SpellRequest.class,7,NetworkDirection.PLAY_TO_SERVER).encoder((p,b)->{b.writeUUID(p.identity);b.writeVarInt(p.action);b.writeVarInt(p.value);}).decoder(b->new SpellRequest(b.readUUID(),b.readVarInt(),b.readVarInt())).consumerMainThread((p,x)->{var player=x.get().getSender();if(player!=null)BookService.spellRequest(player,p.identity,p.action,p.value);}).add();
        c.messageBuilder(SpellState.class,8,NetworkDirection.PLAY_TO_CLIENT).encoder((p,b)->{b.writeUtf(p.json,524288);b.writeBoolean(p.open);}).decoder(b->new SpellState(b.readUtf(524288),b.readBoolean())).consumerMainThread((p,x)->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->BookClient.spellbook(p))).add();
        c.messageBuilder(SpellHud.class,9,NetworkDirection.PLAY_TO_CLIENT).encoder((p,b)->{b.writeUUID(p.identity);b.writeVarInt(p.selected);b.writeVarInt(p.pages);b.writeBoolean(p.powered);}).decoder(b->new SpellHud(b.readUUID(),b.readVarInt(),b.readVarInt(),b.readBoolean())).consumerMainThread((p,x)->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->BookClient.hud(p))).add();
    }
    public static void send(Object packet){CircuitNetwork.CHANNEL.sendToServer(packet);}
    private static void to(ServerPlayer p,Object packet){CircuitNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(()->p),packet);}
    public static void workshop(ServerPlayer p,String json,boolean open){to(p,new WorkshopState(json,open));}
    public static void notebook(ServerPlayer p,String json,boolean open){to(p,new NotebookState(json,open));}
    public static void spellbook(ServerPlayer p,String json,boolean open){to(p,new SpellState(json,open));}
    public static void hud(ServerPlayer p,UUID id,int selected,int pages,boolean powered){to(p,new SpellHud(id,selected,pages,powered));}
}
