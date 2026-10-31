package dev.elementalcircuits.client.effect;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.FeedbackConfig;
import dev.elementalcircuits.domain.Element;
import dev.elementalcircuits.network.FeedbackNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** Owner confirmation only. Does not modify player rotation, velocity, FOV or hit timing. */
@EventBusSubscriber(modid=ElementalCircuits.ID,value=Dist.CLIENT)
public final class FeedbackClient {
    private static final FeedbackPulse PULSE=new FeedbackPulse();
    private static ClientLevel world;
    private static Element element=Element.PURE;
    private static volatile long confirmations;
    private FeedbackClient(){}
    private static void bind(){var next=Minecraft.getInstance().level;if(world!=next){PULSE.clear();world=next;}}
    public static void accept(FeedbackNetwork.Pulse p){bind();if(world==null)return;element=p.element();PULSE.accept(p.cast(),p.hit(),p.power());if(p.hit())confirmations++;}
    public static long confirmations(){return confirmations;}
    @SubscribeEvent public static void tick(ClientTickEvent.Post e){bind();if(world!=null && !Minecraft.getInstance().isPaused())PULSE.tick();}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){PULSE.clear();world=null;confirmations=0;}
    @SubscribeEvent public static void camera(ViewportEvent.ComputeCameraAngles e){
        var mc=Minecraft.getInstance();if(mc.level==null || mc.screen!=null || mc.isPaused() || !mc.options.getCameraType().isFirstPerson() || !FeedbackConfig.CAMERA.get())return;
        float p=(float)mc.getTimer().getGameTimeDeltaPartialTick(false),scale=FeedbackConfig.MOTION.get().floatValue();
        e.setPitch(e.getPitch()-PULSE.recoil(p)*.8f*scale);e.setRoll(e.getRoll()+PULSE.roll(p)*.55f*scale);
    }
    @SubscribeEvent public static void hud(RenderGuiEvent.Post e){
        var mc=Minecraft.getInstance();if(mc.player==null || mc.screen!=null || mc.options.hideGui || !FeedbackConfig.HIT_MARKER.get())return;
        float alpha=PULSE.marker(mc.getTimer().getGameTimeDeltaPartialTick(false));if(alpha<=0)return;
        int x=mc.getWindow().getGuiScaledWidth()/2,y=mc.getWindow().getGuiScaledHeight()/2,color=((int)(alpha*255)<<24)|VisualStyles.of(element).core(),gap=5+Math.round((1-alpha)*3);
        var g=e.getGuiGraphics();g.drawManaged(()->{for(int signX:new int[]{-1,1})for(int signY:new int[]{-1,1})for(int i=0;i<4;i++){int px=x+signX*(gap+i),py=y+signY*(gap+i);g.fill(px,py,px+2,py+1,color);}});
    }
}
