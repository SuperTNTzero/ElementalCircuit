package dev.elementalcircuits.client.effect;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.network.VisualNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Owns one client world's disposable timeline. No client visuals are persisted or replayed offline. */
@Mod.EventBusSubscriber(modid=ElementalCircuits.ID,value=Dist.CLIENT)
public final class SpellVisualClient {
    private static final VisualTimeline TIMELINE=new VisualTimeline();
    private static ClientLevel world;private static long latestTick=-1;
    private SpellVisualClient(){}
    private static void bind(){var next=Minecraft.getInstance().level;if(next!=world){TIMELINE.clear();world=next;latestTick=-1;}}
    public static void accept(VisualNetwork.Batch batch){bind();if(world==null || !world.dimension().location().equals(batch.dimension()) || batch.tick()<latestTick-20)return;latestTick=Math.max(latestTick,batch.tick());TIMELINE.add(batch.events());}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){if(event.phase==TickEvent.Phase.END){bind();if(world!=null && !Minecraft.getInstance().isPaused())TIMELINE.tick();}}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){TIMELINE.clear();world=null;latestTick=-1;}
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;bind();if(world==null)return;
        SpellVisualRenderer.render(event.getPoseStack(),event.getCamera().getPosition(),event.getCamera().rotation(),TIMELINE.frames(),event.getPartialTick());
    }
    public static void reload(){TIMELINE.clear();}
}
