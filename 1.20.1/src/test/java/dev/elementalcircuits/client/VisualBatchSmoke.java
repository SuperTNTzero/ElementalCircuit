package dev.elementalcircuits.client;

import com.google.gson.*;
import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.application.observation.*;
import dev.elementalcircuits.client.effect.*;
import dev.elementalcircuits.domain.Element;
import dev.elementalcircuits.network.VisualNetwork;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import java.nio.file.Files;
import java.util.*;

/** Opt-in actual GPU batch exercise, without opening a player world or shipping test classes. */
@Mod.EventBusSubscriber(modid=ElementalCircuits.ID,value=Dist.CLIENT)
public final class VisualBatchSmoke {
    private static int stage=-1,ticks;
    private static final List<Long> samples=new ArrayList<>();
    private static final JsonArray results=new JsonArray();
    private static final VisualTimeline timeline=new VisualTimeline();
    private static SpellVisual.Point p(double x,double y,double z){return new SpellVisual.Point(x,y,z);}
    public static void begin(Minecraft mc){
        stage=0;ticks=0;GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),1280,800);mc.options.guiScale().set(2);mc.resizeDisplay();load();mc.setScreen(new Preview());
        // A packet received without a matching world must not create effects or a world.
        SpellVisualClient.accept(new VisualNetwork.Batch(new ResourceLocation("minecraft","the_nether"),1,List.of(new SpellVisual(SpellVisual.Kind.IMPACT,Element.FIRE,p(0,0,0),p(0,0,0),1,1))));
    }
    private static void load(){
        if(stage==2){for(int i=0;i<20;i++)timeline.tick();if(!timeline.frames().isEmpty())throw new AssertionError("特效过期后仍留在客户端时间线");samples.clear();return;}
        timeline.clear();samples.clear();var events=new ArrayList<SpellVisual>();
        if(stage==0){
            for(int i=0;i<Element.values().length;i++){double x=(i%3-1)*4.4,y=(i/3-1)*2.3;var element=Element.values()[i];events.add(new SpellVisual(SpellVisual.Kind.BEAM,element,p(x-1.7,y,-10),p(x+1.7,y,-10),1,1));events.add(new SpellVisual(SpellVisual.Kind.IMPACT,element,p(x,y+.5,-10),p(x,y+.5,-10),1,8));}
            events.add(new SpellVisual(SpellVisual.Kind.SHIELD,Element.ARCANE,p(0,0,-10),p(0,0,1),1.6f,1));events.add(new SpellVisual(SpellVisual.Kind.STEAM,Element.WATER,p(-5,3,-10),p(-5,3,-10),1,6));
        }else if(stage==1){
            for(int i=0;i<VisualBudget.CLIENT_LIMIT+500;i++){double x=(i%64-32)*.18,y=(i/64-16)*.12;events.add(new SpellVisual(SpellVisual.Kind.IMPACT,Element.values()[i%9],p(x,y,-10),p(x,y,-10),.1f,12));}
        }
        timeline.add(events);if(stage==1 && timeline.frames().size()!=VisualBudget.CLIENT_LIMIT)throw new AssertionError("真实渲染前客户端预算未限制积压");
    }
    private static final class Preview extends Screen {
        Preview(){super(Component.literal("特效静态材质与批量渲染验证"));}
        @Override public void render(GuiGraphics g,int x,int y,float partial){
            g.fill(0,0,width,height,0xff0b1622);g.drawString(font,"静态材质 / 客户端批量特效 / 阶段 "+stage,12,12,0xffe9c98a,false);
            g.drawString(font,"服务器仅提供形状观察；播放与过期不参与命中或魔力结算",12,height-32,0xffb8d7e7,false);g.flush();
            g.drawManaged(()->dev.elementalcircuits.client.render.EditorRenderer.line(g,20,40.5,100,40.5,0xff38e27a));g.flush();
            if(ticks==2){
                double scale=minecraft.getWindow().getGuiScale();var pixel=org.lwjgl.BufferUtils.createByteBuffer(4);
                GL11.glReadPixels((int)(60*scale),minecraft.getMainRenderTarget().height-(int)(40.5*scale)-1,1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixel);
                if((pixel.get(1)&255)<150 || (pixel.get(0)&255)>100)throw new AssertionError("动态线路四边形被背面剔除或未实际绘制");
            }
            g.pose().pushPose();g.pose().translate(width/2.0,height/2.0,1000);g.pose().scale(34,34,34);
            GL11.glFinish();long start=System.nanoTime();SpellVisualRenderer.render(g.pose(),Vec3.ZERO,new Quaternionf(),timeline.frames(),.2f);GL11.glFinish();
            long elapsed=System.nanoTime()-start;if(ticks>30)samples.add(elapsed);g.pose().popPose();
            if(stage==0 && SpellVisualRenderer.lastDraws()!=1 || stage==1 && (SpellVisualRenderer.lastDraws()!=1 || SpellVisualRenderer.lastQuads()!=8192) || stage==2 && SpellVisualRenderer.lastDraws()!=0)throw new AssertionError("实际特效合批／过期绘制数量错误");
            g.drawString(font,"Quads: "+SpellVisualRenderer.lastQuads()+" / Draws: "+SpellVisualRenderer.lastDraws(),12,height-18,0xff80cbe5,false);
        }
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();
        if(stage<0){if(Boolean.getBoolean("elementalcircuits.vfxSmoke") && mc.screen instanceof TitleScreen)begin(mc);return;}
        if(!(mc.screen instanceof Preview))return;
        if(++ticks<100)return;Screenshot.grab(mc.gameDirectory,"visual-batch-"+stage+".png",mc.getMainRenderTarget(),message->{});
        if(samples.isEmpty())throw new AssertionError("没有GPU绘制样本");samples.sort(Long::compare);var row=new JsonObject();row.addProperty("stage",stage);row.addProperty("samples",samples.size());row.addProperty("medianMs",samples.get(samples.size()/2)/1e6);row.addProperty("p95Ms",samples.get(samples.size()*95/100)/1e6);row.addProperty("quads",SpellVisualRenderer.lastQuads());row.addProperty("draws",SpellVisualRenderer.lastDraws());results.add(row);
        if(++stage<3){ticks=0;load();return;}
        try{Files.writeString(mc.gameDirectory.toPath().resolve("visual-batch-profile.json"),new GsonBuilder().setPrettyPrinting().create().toJson(results));}catch(java.io.IOException e){throw new IllegalStateException(e);}
        org.slf4j.LoggerFactory.getLogger("VisualBatchSmoke").info("VISUAL_BATCH_SMOKE_PASS: 3 screenshots; all elements, 8192 quads in one draw, expired zero draw");mc.stop();
    }
}
