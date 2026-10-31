package dev.elementalcircuits.client;

import com.mojang.blaze3d.audio.OggAudioStream;
import dev.elementalcircuits.application.observation.*;
import dev.elementalcircuits.domain.Element;
import dev.elementalcircuits.server.SpellSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEventListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import java.util.*;

/** Test-only: real native packet reception, Vorbis decoding and sound-engine voice lifecycle. */
final class AudioWorldProbe {
    private static final List<SpellSound> EVENTS=new ArrayList<>();
    private static final Set<String> HEARD=new HashSet<>();
    private static final List<SoundInstance> VOICES=new ArrayList<>();
    private static int sent,serverTicks,decoded,active;
    private static boolean initialized;
    private static final SoundEventListener LISTENER=(sound,event)->{
        if(!sound.getLocation().getNamespace().equals("elementalcircuits"))return;
        if(sound.getSource()!=SoundSource.PLAYERS || sound.isLooping() || sound.isRelative() || sound.getAttenuation()!=SoundInstance.Attenuation.LINEAR)throw new AssertionError("施法音效未使用原生空间声音");
        HEARD.add(sound.getLocation().getPath());VOICES.add(sound);
    };
    private static boolean enabled(){return Boolean.getBoolean("elementalcircuits.audioSmoke");}
    static void prepare(Minecraft mc){
        if(!enabled() || initialized)return;initialized=true;
        var seen=new HashSet<String>();
        for(var kind:SpellSound.Kind.values())for(var element:Element.values()){
            var s=new SpellSound(kind,element,new SpellVisual.Point(0,0,0));if(!seen.add(s.resource()))continue;EVENTS.add(s);
            var id=new ResourceLocation("elementalcircuits",s.resource());
            if(mc.getSoundManager().getSoundEvent(id)==null)throw new AssertionError("原生声音资源未注册："+id);
            try(var input=mc.getResourceManager().open(new ResourceLocation("elementalcircuits","sounds/spell/"+s.resource()+".ogg"));var ogg=new OggAudioStream(input)){
                var format=ogg.getFormat();var pcm=ogg.readAll();if(format.getChannels()!=1 || format.getSampleRate()!=24000 || pcm.remaining()<10000)throw new AssertionError("游戏真实解码格式错误："+id);decoded++;
            }catch(java.io.IOException e){throw new AssertionError(e);}
        }
        mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(1.0);mc.options.getSoundSourceOptionInstance(SoundSource.PLAYERS).set(.5);
        mc.getSoundManager().addListener(LISTENER);
    }
    static void server(ServerLevel level){
        if(!enabled() || level.players().isEmpty() || ++serverTicks%12!=0 || sent>=EVENTS.size())return;
        var e=EVENTS.get(sent++);var p=level.players().get(0).position().add(0,1,3);
        SpellSounds.observe(level,e.kind(),e.element(),p);
    }
    static boolean ready(Minecraft mc){
        if(!enabled())return true;
        for(var s:VOICES)if(mc.getSoundManager().isActive(s))active++;
        return HEARD.size()==28 && active>0;
    }
    static void expired(Minecraft mc){
        if(!enabled())return;
        if(VOICES.stream().anyMatch(mc.getSoundManager()::isActive))throw new AssertionError("短音效未结束");
        mc.getSoundManager().removeListener(LISTENER);
        org.slf4j.LoggerFactory.getLogger("AudioWorldProbe").info("AUDIO_WORLD_SMOKE_PASS: decoded={} heard={} active_samples={} native packets + positional players category + finite voices",decoded,HEARD.size(),active);
    }
}
