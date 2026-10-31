package dev.elementalcircuits.client;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.application.observation.*;
import dev.elementalcircuits.client.effect.*;
import dev.elementalcircuits.domain.Element;
import dev.elementalcircuits.network.VisualNetwork;
import dev.elementalcircuits.server.SpellVisuals;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.event.tick.*;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import java.util.*;

/** Isolated integrated-world check of the actual network receiver and AFTER_PARTICLES event. */
@net.neoforged.fml.common.EventBusSubscriber(modid=ElementalCircuits.ID,value=Dist.CLIENT)
public final class VisualWorldSmoke {
    private static volatile int stage=-1;private static int ticks,receivedFrames;private static boolean prepared;
    private static boolean enabled(){return Boolean.getBoolean("elementalcircuits.vfxWorldSmoke");}
    private static VisualTimeline timeline(){try{var f=SpellVisualClient.class.getDeclaredField("TIMELINE");f.setAccessible(true);return (VisualTimeline)f.get(null);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
    @SubscribeEvent public static void world(LevelTickEvent.Post e){
        if(!enabled() || !(e.getLevel() instanceof net.minecraft.server.level.ServerLevel level) || level.dimension()!=Level.OVERWORLD || level.players().isEmpty())return;
        var p=level.players().get(0);if(!prepared){prepared=true;p.connection.teleport(.5,-59,.5,0,0);level.setDayTime(6000);}
        if(stage!=0)return;Vec3 origin=new Vec3(.5,-57.4,5.5);
        PortWorldProbe.server(level);ImpactWorldProbe.server(level);if(ImpactWorldProbe.done())AudioWorldProbe.server(level);
        for(int i=0;i<3;i++){
            Element element=new Element[]{Element.FIRE,Element.WATER,Element.LIGHTNING}[i];Vec3 a=origin.add((i-1)*1.8,-.5,0),b=a.add(0,1.4,0);
            SpellVisuals.observe(level,SpellVisual.Kind.BEAM,element,a,b,1,1);SpellVisuals.observe(level,SpellVisual.Kind.IMPACT,element,b,b,1,6);
        }
        SpellVisuals.observe(level,SpellVisual.Kind.SHIELD,Element.ARCANE,origin.add(0,.2,1),new Vec3(0,0,-1),2,1);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post e){
        if(!enabled())return;var mc=Minecraft.getInstance();
        if(stage<0){if(!(mc.screen instanceof TitleScreen)){if(++ticks%100==0)org.slf4j.LoggerFactory.getLogger("VisualWorldSmoke").info("VISUAL_WORLD_WAIT_SCREEN: {}",mc.screen==null?"none":mc.screen.getClass().getSimpleName());if(ticks>600)throw new AssertionError("测试客户端未到达标题界面");return;}AudioWorldProbe.prepare(mc);stage=0;ticks=0;mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(4);mc.options.simulationDistance().set(5);mc.options.guiScale().set(2);GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),1280,800);mc.resizeDisplay();
            var settings=new LevelSettings("批量特效隔离验证",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT);
            mc.createWorldOpenFlows().createFreshLevel("visual-world-"+System.currentTimeMillis(),settings,new WorldOptions(20261030,false,false),access->access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),new TitleScreen());return;
        }
        if(++ticks>1200)throw new AssertionError("隔离世界特效验证超时");
        if(stage==0){
            if(mc.level==null || mc.player==null)return;if(mc.screen!=null){if(mc.screen instanceof TitleScreen)return;}
            if(SpellVisualRenderer.lastDraws()==1 && timeline().frames().size()>0)receivedFrames++;
            if(ticks%200==0)org.slf4j.LoggerFactory.getLogger("VisualWorldSmoke").info("WORLD_PROGRESS frames={} timeline={} draws={} audio={}",receivedFrames,timeline().frames().size(),SpellVisualRenderer.lastDraws(),AudioWorldProbe.status());
            if(!PortWorldProbe.ready(mc) || !ImpactWorldProbe.ready(mc) || receivedFrames<40 || !AudioWorldProbe.ready(mc))return;
            int before=timeline().frames().size();SpellVisualClient.accept(new VisualNetwork.Batch(ResourceLocation.fromNamespaceAndPath("minecraft","the_nether"),1,List.of(new SpellVisual(SpellVisual.Kind.IMPACT,Element.FIRE,new SpellVisual.Point(0,0,0),new SpellVisual.Point(0,0,0),1,1))));if(timeline().frames().size()!=before)throw new AssertionError("其他维度的数据进入当前世界时间线");
            Screenshot.grab(mc.gameDirectory,"visual-world-active.png",mc.getMainRenderTarget(),message->{});stage=1;ticks=0;return;
        }
        if(stage==1){
            if(ticks<30)return;if(!timeline().frames().isEmpty() || SpellVisualRenderer.lastDraws()!=0)throw new AssertionError("停止同步后的真实世界特效未按期清理");
            AudioWorldProbe.expired(mc);
            Screenshot.grab(mc.gameDirectory,"visual-world-expired.png",mc.getMainRenderTarget(),message->{});stage=2;ticks=0;mc.level.disconnect();mc.disconnect(new TitleScreen());return;
        }
        if(stage==2 && ticks>5){if(mc.level!=null || !timeline().frames().isEmpty())throw new AssertionError("退出世界后残留特效或世界引用");stage=3;org.slf4j.LoggerFactory.getLogger("VisualWorldSmoke").info("VISUAL_WORLD_SMOKE_PASS: received frames={}, actual channel + world render + dimension rejection + expiry + logout",receivedFrames);mc.stop();}
    }
}
