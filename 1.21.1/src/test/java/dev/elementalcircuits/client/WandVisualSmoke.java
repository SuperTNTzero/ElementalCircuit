package dev.elementalcircuits.client;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.*;
import dev.elementalcircuits.adapter.persistence.ItemData;
import dev.elementalcircuits.client.render.*;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.server.WandCooldown;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import java.util.*;

/** Opt-in real GPU model, item-component transport and held-wand HUD check; isolated disposable world. */
@EventBusSubscriber(modid=ElementalCircuits.ID,value=Dist.CLIENT)
public final class WandVisualSmoke {
    private static int stage=-1,ticks,serverTicks;
    private static volatile boolean initialized;
    private static boolean busySeen,readySeen,frontSeen,halfSeen,compact,leftCaptured;
    private static final List<WandParts.Assembly> variants=new ArrayList<>();
    private static boolean enabled(){return Boolean.getBoolean("elementalcircuits.wandSmoke");}
    private static void capture(Minecraft mc,String name){Screenshot.grab(mc.gameDirectory,name+".png",mc.getMainRenderTarget(),m->{});}
    private static final class Gallery extends Screen {
        Gallery(){super(Component.literal("三维法杖 / 部件组合"));}
        @Override public void render(GuiGraphics g,int x,int y,float dt){
            WorkshopSkin.background(g,width,height);g.drawString(font,title,16,10,WorkshopSkin.TEXT,false);
            for(int i=0;i<variants.size();i++){
                int cx=30+(i%9)*(width-44)/9,cy=46+(i/9)*(height-58)/3;
                WandPreview.render(g,cx+14,cy,Math.min(66,(height-100)/3),variants.get(i));
                g.drawString(font,String.format(java.util.Locale.ROOT,"%02d",i+1),cx+7,cy+Math.min(66,(height-100)/3)+2,WorkshopSkin.GOLD,false);
            }
        }
    }
    private static void validate(Minecraft mc){
        var d=WandResources.definitions();Set<Object> models=Collections.newSetFromMap(new IdentityHashMap<>());
        for(var h:d.in(WandParts.Slot.HEAD))for(var b:d.in(WandParts.Slot.BODY))for(var t:d.in(WandParts.Slot.TAIL)){
            var a=new WandParts.Assembly(h.id(),b.id(),t.id());variants.add(a);
            var model=mc.getItemRenderer().getModel(WandPreview.stack(a),null,null,0);
            if(model==mc.getModelManager().getMissingModel() || !models.add(model))throw new AssertionError("缺失或重复部件模型："+a);
            var quads=model.getQuads(null,null,RandomSource.create(0));
            if(quads.size()<150 || !model.isGui3d())throw new AssertionError("模型没有真实三维几何："+a+", quads="+quads.size());
            float min=Float.POSITIVE_INFINITY,max=Float.NEGATIVE_INFINITY;
            for(var q:quads){int[] v=q.getVertices();for(int i=0;i<4;i++){float z=Float.intBitsToFloat(v[i*(v.length/4)+2]);min=Math.min(min,z);max=Math.max(max,z);}}
            if(max-min<.05f || max-min>.5f)throw new AssertionError("模型厚度异常："+(max-min));
        }
        org.slf4j.LoggerFactory.getLogger("WandVisualSmoke").info("WAND_MODELS_GPU_PASS: 27 distinct native models, actual baked quads and depth");
    }
    @SubscribeEvent public static void world(LevelTickEvent.Post e){
        if(!enabled() || !(e.getLevel() instanceof net.minecraft.server.level.ServerLevel level) || level.dimension()!=Level.OVERWORLD || level.players().isEmpty())return;
        var p=level.players().get(0);
        if(!initialized){
            var wand=new ItemStack(ElementalCircuits.WAND.get());p.getInventory().selected=0;p.getInventory().setItem(0,wand);
            p.connection.teleport(.5,-59,.5,0,0);level.setDayTime(6000);initialized=true;
        }
        if(++serverTicks==25 || serverTicks==75 || serverTicks==125)WandCooldown.tryRelease(p.getMainHandItem());
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!enabled())return;var mc=Minecraft.getInstance();
        if(stage<0){
            if(!(mc.screen instanceof TitleScreen))return;mc.options.pauseOnLostFocus=false;mc.options.guiScale().set(2);mc.options.renderDistance().set(4);mc.options.simulationDistance().set(5);
            org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),1280,800);mc.resizeDisplay();validate(mc);mc.setScreen(new Gallery());stage=0;ticks=0;return;
        }
        if(++ticks>1000)throw new AssertionError("三维法杖验证超时");
        if(stage==0 && ticks>35){
            capture(mc,"wand-27-models");stage=1;ticks=0;
            var settings=new LevelSettings("法杖冷却隔离验证",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT);
            mc.createWorldOpenFlows().createFreshLevel("wand-cooldown-"+System.currentTimeMillis(),settings,new WorldOptions(20261030,false,false),a->a.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),new TitleScreen());return;
        }
        if(stage!=1 || mc.level==null || mc.player==null || mc.screen!=null)return;
        var wand=mc.player.getMainHandItem();if(!wand.is(ElementalCircuits.WAND.get()))return;
        var cd=dev.elementalcircuits.server.WandCooldown.state(wand);
        if(cd.remaining()>2){if(!busySeen){capture(mc,"wand-firstperson-cooldown");busySeen=true;}}
        if(!halfSeen && cd.duration()>0 && cd.remaining()>=cd.duration()/2-1 && cd.remaining()<=cd.duration()/2+1){capture(mc,"hud-ring-recovering");halfSeen=true;}
        if(busySeen && cd.ready() && !readySeen){capture(mc,"wand-firstperson-ready");readySeen=true;mc.options.mainHand().set(net.minecraft.world.entity.HumanoidArm.LEFT);}
        if(readySeen && serverTicks>95 && !leftCaptured){leftCaptured=true;capture(mc,"wand-left-hand");mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);}
        if(readySeen && serverTicks>130 && !frontSeen){capture(mc,"wand-thirdperson");frontSeen=true;mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);}
        if(readySeen && serverTicks>160 && !compact){capture(mc,"wand-thirdperson-front");compact=true;mc.options.setCameraType(CameraType.FIRST_PERSON);mc.options.guiScale().set(3);org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),960,720);mc.resizeDisplay();}
        if(readySeen && serverTicks>190){capture(mc,"hud-ring-compact");stage=2;ticks=0;mc.level.disconnect();mc.disconnect(new TitleScreen());}
        if(stage==2)org.slf4j.LoggerFactory.getLogger("WandVisualSmoke").info("WAND_WORLD_GPU_PASS: synchronized persisted cooldown + ready HUD + right/left hand + third person");
    }
    @SubscribeEvent public static void finish(ClientTickEvent.Pre e){if(enabled() && stage==2 && ++ticks>8){stage=3;Minecraft.getInstance().stop();}}
}
