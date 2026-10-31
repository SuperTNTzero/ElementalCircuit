package dev.elementalcircuits.server;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.AudioResources;
import dev.elementalcircuits.application.observation.*;
import dev.elementalcircuits.domain.Element;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;
import java.util.*;

/** Server observation adapter. Native Minecraft playback owns mixing, attenuation, reload and logout. */
public final class SpellSounds {
    private static final DeferredRegister<SoundEvent> SOUNDS=DeferredRegister.create(net.minecraft.core.registries.Registries.SOUND_EVENT,ElementalCircuits.ID);
    private static final Map<String,DeferredHolder<SoundEvent,SoundEvent>> EVENTS=new LinkedHashMap<>();
    private static final Map<ServerLevel,SoundBudget> OUTBOX=new WeakHashMap<>();
    static {for(var kind:SpellSound.Kind.values())for(var element:Element.values()){
        String name=new SpellSound(kind,element,new SpellVisual.Point(0,0,0)).resource();
        EVENTS.computeIfAbsent(name,n->SOUNDS.register(n,()->SoundEvent.createFixedRangeEvent(ResourceLocation.fromNamespaceAndPath(ElementalCircuits.ID,n),AudioResources.policy().range())));
    }}
    private SpellSounds(){}
    public static void register(IEventBus bus){SOUNDS.register(bus);}
    public static SoundEvent event(SpellSound observation){return EVENTS.get(observation.resource()).get();}
    public static void observe(ServerLevel level,SpellSound.Kind kind,Element element,Vec3 p){
        OUTBOX.computeIfAbsent(level,l->new SoundBudget(AudioResources.policy())).add(level.getGameTime(),new SpellSound(kind,element,new SpellVisual.Point(p.x,p.y,p.z)));
    }
    public static List<SpellSound> pending(ServerLevel level){var q=OUTBOX.get(level);return q==null?List.of():q.snapshot();}
    public static ClientboundSoundPacket packet(SpellSound s,long tick){
        long seed=tick*31+s.hashCode();float pitch=.96f+Math.floorMod(seed,9)*.01f;
        var p=s.position();return new ClientboundSoundPacket(Holder.direct(event(s)),SoundSource.PLAYERS,p.x(),p.y(),p.z(),AudioResources.policy().volumes().get(s.kind()),pitch,seed);
    }
    public static void flush(ServerLevel level){
        var q=OUTBOX.get(level);if(q==null)return;
        for(var player:level.players())for(var s:q.audible(new SpellVisual.Point(player.getX(),player.getY(),player.getZ())))player.connection.send(packet(s,level.getGameTime()));
        q.finish(level.getGameTime());
    }
    public static void unload(ServerLevel level){OUTBOX.remove(level);}
}
