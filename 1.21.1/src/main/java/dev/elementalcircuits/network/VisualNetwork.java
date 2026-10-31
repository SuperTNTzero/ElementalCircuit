package dev.elementalcircuits.network;

import dev.elementalcircuits.application.observation.*;
import dev.elementalcircuits.domain.Element;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** One bounded message per batch of shapes, no particles or per-vertex packets. */
public final class VisualNetwork {
    public record Batch(ResourceLocation dimension,long tick,List<SpellVisual> events) implements CustomPacketPayload {
        public static final Type<Batch> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("elementalcircuits","visual_batch"));
        @Override public Type<Batch> type(){return TYPE;}
        public Batch{Objects.requireNonNull(dimension);events=List.copyOf(events);if(tick<0 || events.isEmpty() || events.size()>VisualBudget.BATCH_LIMIT)throw new IllegalArgumentException("特效批次非法");}
    }
    private VisualNetwork(){}
    public static void register(PayloadRegistrar registrar){registrar.playToClient(Batch.TYPE,StreamCodec.of((b,p)->write(p,b),VisualNetwork::read),(p,c)->dev.elementalcircuits.client.effect.SpellVisualClient.accept(p));}
    private static void point(FriendlyByteBuf b,SpellVisual.Point p){b.writeDouble(p.x());b.writeDouble(p.y());b.writeDouble(p.z());}
    private static SpellVisual.Point point(FriendlyByteBuf b){return new SpellVisual.Point(b.readDouble(),b.readDouble(),b.readDouble());}
    public static void write(Batch p,FriendlyByteBuf b){b.writeResourceLocation(p.dimension);b.writeLong(p.tick);b.writeVarInt(p.events.size());for(var e:p.events){b.writeEnum(e.kind());b.writeEnum(e.element());point(b,e.from());point(b,e.to());b.writeFloat(e.size());b.writeVarInt(e.intensity());}}
    public static Batch read(FriendlyByteBuf b){
        var dimension=b.readResourceLocation();long tick=b.readLong();int count=b.readVarInt();if(count<1 || count>VisualBudget.BATCH_LIMIT)throw new IllegalArgumentException("特效批次数量超限");
        var events=new ArrayList<SpellVisual>(count);for(int i=0;i<count;i++)events.add(new SpellVisual(b.readEnum(SpellVisual.Kind.class),b.readEnum(Element.class),point(b),point(b),b.readFloat(),b.readVarInt()));return new Batch(dimension,tick,events);
    }
    public static void send(ServerPlayer player,Batch packet){PacketDistributor.sendToPlayer(player,packet);}
}
