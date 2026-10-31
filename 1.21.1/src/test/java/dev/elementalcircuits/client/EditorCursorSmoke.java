package dev.elementalcircuits.client;

import com.google.gson.*;
import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.*;
import dev.elementalcircuits.client.editor.*;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.network.CircuitNetwork;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.event.tick.*;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import java.nio.file.*;
import java.util.*;

/** Opt-in, isolated GPU timing fixture. Never creates a world or ships in the mod JAR. */
@net.neoforged.fml.common.EventBusSubscriber(modid=ElementalCircuits.ID,value=Dist.CLIENT)
public final class EditorCursorSmoke {
    private static final String[] PHASES={"select","place","route","drag"};
    private static final JsonArray measurements=new JsonArray();
    private static final List<Long> times=new ArrayList<>();
    private static int phase=-1,frames;private static long start;private static boolean next;
    private static EditorModel model;
    private static Object field(Object target,String name){try{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
    private static boolean enabled(){return Boolean.getBoolean("elementalcircuits.cursorSmoke");}
    private static EditorModel model(CircuitScreen screen){try{var f=CircuitScreen.class.getDeclaredField("model");f.setAccessible(true);return (EditorModel)f.get(screen);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
    private static void pointer(Minecraft mc,Hex cell){
        var l=EditorLayout.of(mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight(),8);double scale=mc.getWindow().getGuiScale();
        try{var x=MouseHandler.class.getDeclaredField("xpos");var y=MouseHandler.class.getDeclaredField("ypos");x.setAccessible(true);y.setAccessible(true);x.setDouble(mc.mouseHandler,l.x(cell)*scale);y.setDouble(mc.mouseHandler,l.y(cell)*scale);}
        catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!enabled())return;Minecraft mc=Minecraft.getInstance();
        if(phase<0){
            if(!(mc.screen instanceof TitleScreen))return;
            GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),1280,800);mc.options.guiScale().set(2);mc.options.enableVsync().set(false);mc.options.framerateLimit().set(120);mc.resizeDisplay();
            var b=dev.elementalcircuits.domain.DFlipFlopContracts.blueprint().withMaterial(Rules.Material.CRYSTAL);
            var screen=new CircuitScreen(new CircuitNetwork.Editor(new UUID(30,1),JsonCodecs.blueprint(b),false,0,true,"217格与D触发器性能采样",WandResources.definitions().basic(),0));mc.setScreen(screen);model=model(screen);phase=0;next=true;
        }
        if(!(mc.screen instanceof CircuitScreen screen))return;
        if(next){
            next=false;frames=0;times.clear();model.cancel();model.tool=EditorModel.Tool.SELECT;
            if(phase==1)model.choose(Rune.Kind.CONVERT,Element.LIGHT);
            if(phase==2){model.tool=EditorModel.Tool.ROUTE;model.payload=Route.Payload.SIGNAL;model.click(model.draft.get().rune(1).cell());}
            if(phase==3){model.click(model.draft.get().rune(17).cell());model.beginDrag();}
            screen.resize(mc,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight());
        }
        pointer(mc,phase==2?model.draft.get().rune(16).cell():phase==3?new Hex(-2,3):new Hex(0,2));
    }
    @SubscribeEvent public static void before(ScreenEvent.Render.Pre event){
        if(!enabled() || phase<0 || next || !(event.getScreen() instanceof CircuitScreen))return;
        event.getGuiGraphics().flush();GL11.glFinish();start=System.nanoTime();
    }
    @SubscribeEvent public static void after(ScreenEvent.Render.Post event){
        if(!enabled() || start==0 || !(event.getScreen() instanceof CircuitScreen))return;
        event.getGuiGraphics().flush();GL11.glFinish();long elapsed=System.nanoTime()-start;start=0;
        if(++frames>60)times.add(elapsed);if(frames<220)return;
        Minecraft mc=Minecraft.getInstance();
        if(!Boolean.getBoolean("elementalcircuits.cursorBaseline") && GLFW.glfwGetInputMode(mc.getWindow().getWindow(),GLFW.GLFW_CURSOR)!=GLFW.GLFW_CURSOR_NORMAL)throw new AssertionError("绘制界面仍隐藏系统指针");
        if(!Boolean.getBoolean("elementalcircuits.cursorBaseline")){
            var cursors=(Map<?,?>)field(field(event.getScreen(),"nativeCursor"),"cursors");
            if(cursors.isEmpty() || cursors.values().stream().anyMatch(handle->((Long)handle)==0))throw new AssertionError("工具原生指针创建失败");
            var bitmap=new PointerBitmap(EditorPointer.of(model.view(phase==2?model.draft.get().rune(16).cell():phase==3?new Hex(-2,3):new Hex(0,2))),2);
            var image=new java.awt.image.BufferedImage(bitmap.size(),bitmap.size(),java.awt.image.BufferedImage.TYPE_INT_ARGB);image.setRGB(0,0,bitmap.size(),bitmap.size(),bitmap.pixels(),0,bitmap.size());
            try{javax.imageio.ImageIO.write(image,"png",mc.gameDirectory.toPath().resolve("native-pointer-"+PHASES[phase]+".png").toFile());}catch(java.io.IOException e){throw new IllegalStateException(e);}
        }
        times.sort(Long::compare);JsonObject row=new JsonObject();row.addProperty("phase",PHASES[phase]);row.addProperty("samples",times.size());row.addProperty("medianMs",times.get(times.size()/2)/1e6);row.addProperty("p95Ms",times.get((times.size()*95)/100)/1e6);measurements.add(row);
        org.slf4j.LoggerFactory.getLogger("EditorCursorSmoke").info("CURSOR PROFILE {}",row);
        Screenshot.grab(mc.gameDirectory,"cursor-"+PHASES[phase]+".png",mc.getMainRenderTarget(),message->{});
        if(++phase<PHASES.length){next=true;return;}
        try{Files.writeString(mc.gameDirectory.toPath().resolve(Boolean.getBoolean("elementalcircuits.cursorBaseline")?"cursor-before.json":"cursor-after.json"),new GsonBuilder().setPrettyPrinting().create().toJson(measurements));}
        catch(java.io.IOException e){throw new IllegalStateException(e);}
        if(!Boolean.getBoolean("elementalcircuits.cursorBaseline")){
            var parent=(CircuitScreen)event.getScreen();Object cursors=field(parent,"nativeCursor");
            parent.mouseMoved(1,1);if(field(cursors,"current")!=null)throw new AssertionError("离开格盘未恢复普通指针");
            parent.openBlueprints();if(!((Map<?,?>)field(cursors,"cursors")).isEmpty())throw new AssertionError("进入子界面未释放原生指针");
            mc.screen.onClose();if(!(mc.screen instanceof CircuitScreen))throw new AssertionError("子页返回编辑器失败");
        }
        mc.setScreen(new TitleScreen());
        if(GLFW.glfwGetInputMode(mc.getWindow().getWindow(),GLFW.GLFW_CURSOR)!=GLFW.GLFW_CURSOR_NORMAL)throw new AssertionError("退出编辑器没有恢复系统指针");
        org.slf4j.LoggerFactory.getLogger("EditorCursorSmoke").info("CURSOR SMOKE COMPLETE: 4 phases, 640 GPU-synchronized samples");mc.stop();
    }
}
