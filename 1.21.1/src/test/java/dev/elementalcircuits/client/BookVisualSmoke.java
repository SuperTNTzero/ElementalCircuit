package dev.elementalcircuits.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.*;
import dev.elementalcircuits.adapter.persistence.*;
import dev.elementalcircuits.client.book.*;
import dev.elementalcircuits.client.render.*;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.server.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.renderer.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import java.util.*;

/** Actual baked meshes, hand extension, independent slot CD and server-acknowledged book opening/turning. */
@EventBusSubscriber(modid=ElementalCircuits.ID,value=Dist.CLIENT)
public final class BookVisualSmoke {
    private static int stage=-1,ticks,steadyChecks;
    private static volatile int command,ack;
    private static volatile boolean initialized;
    private static boolean used,busy,turned,opened,noteOpened,reloadRequested;
    private static volatile boolean reloaded;
    private static UUID bookId;
    private static boolean enabled(){return Boolean.getBoolean("elementalcircuits.bookSmoke");}
    private static void capture(Minecraft mc,String name){Screenshot.grab(mc.gameDirectory,name+".png",mc.getMainRenderTarget(),m->{});}
    private static final class Gallery extends Screen {
        Gallery(){super(Component.literal("法术书与蓝图簿 / 三维装帧"));}
        @Override public void renderBackground(GuiGraphics g,int x,int y,float partial){}
        @Override public void render(GuiGraphics g,int x,int y,float partial){
            WorkshopSkin.background(g,width,height);g.drawString(font,title,14,10,WorkshopSkin.GOLD,false);
            String[] labels={"合起","翻开中","展开","翻页"};
            for(int row=0;row<2;row++)for(int col=0;col<4;col++){
                int cx=width*(col+1)/5,cy=92+row*(height-112)/2,size=Math.min(103,width/6);
                var pose=g.pose();pose.pushPose();pose.translate(cx,cy,200);pose.scale(size,-size,size);pose.mulPose(Axis.XP.rotationDegrees(55));pose.mulPose(Axis.YP.rotationDegrees(-12));pose.translate(-.5,-.5,-.5);
                var item=new ItemStack(row==0?ElementalCircuits.SPELL_BOOK.get():ElementalCircuits.NOTEBOOK.get());
                BookItemRenderer.draw(item,row==1,new BookMotion.Pose(col==0?0:col==1?.45f:1,col==3?.48f:-1,1),pose,g.bufferSource(),LightTexture.FULL_BRIGHT,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);pose.popPose();g.flush();
                String label=(row==0?"法术书 · ":"蓝图簿 · ")+labels[col];g.drawString(font,label,cx-font.width(label)/2,cy+size/2+10,WorkshopSkin.TEXT,false);
            }
        }
    }
    private static void validate(Minecraft mc){
        int faces=0;
        for(boolean notebook:new boolean[]{false,true}){
            var item=new ItemStack(notebook?ElementalCircuits.NOTEBOOK.get():ElementalCircuits.SPELL_BOOK.get());
            if(!mc.getItemRenderer().getModel(item,null,null,0).isCustomRenderer())throw new AssertionError("书籍未使用原生动态物品入口");
            for(String part:BookItemRenderer.PARTS){
                var model=mc.getModelManager().getModel(BookItemRenderer.model(notebook,part));if(model==mc.getModelManager().getMissingModel())throw new AssertionError("缺少书籍部件："+part);
                var quads=model.getQuads(null,null,RandomSource.create(0));if(quads.size()<6)throw new AssertionError("书籍部件没有实体厚度："+part);faces+=quads.size();
                for(var q:quads)if(q.getSprite()==mc.getModelManager().getMissingModel().getParticleIcon())throw new AssertionError("书籍贴图未烘焙");
            }
        }
        org.slf4j.LoggerFactory.getLogger("BookVisualSmoke").info("BOOK_MODELS_GPU_PASS: 12 native articulated parts, {} actual quads and original stitched atlas",faces);
    }
    @SubscribeEvent public static void world(LevelTickEvent.Post event){
        if(!enabled() || !(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level) || level.dimension()!=Level.OVERWORLD || level.players().isEmpty())return;
        var p=level.players().get(0);
        if(!initialized){
            var wand=new ItemStack(ElementalCircuits.WAND.get());CarrierRepository.load(wand);p.getInventory().selected=0;p.getInventory().setItem(0,wand);
            var book=new ItemStack(ElementalCircuits.SPELL_BOOK.get());var a=CarrierRepository.load(book);
            var board=AssemblyContracts.line(true,Route.Payload.MANA,Rune.Kind.INPUT,Rune.Kind.CONVERT,Rune.Kind.DIRECTED,Rune.Kind.BEAM,Rune.Kind.OUTPUT);a.replace(1,board);a.add(board.rules());a.replace(2,board);a.select(1);CarrierRepository.commit(book);p.setItemSlot(EquipmentSlot.OFFHAND,book);bookId=a.identity();
            var note=new ItemStack(ElementalCircuits.NOTEBOOK.get());var v=NotebookRepository.load(note).add("火焰光束",board).add("另一张法阵",board);NotebookRepository.save(note,v.select(1));p.getInventory().setItem(1,note);
            p.connection.teleport(.5,-60,.5,0,0);level.setDayTime(6000);initialized=true;
        }
        if(command<=ack)return;
        if(command==1){BookService.forget(p.getUUID());BookService.spellRequest(p,CarrierService.identity(p.getOffhandItem()),0,1);}
        if(command==2){BookService.openSpellbook(p,p.getOffhandItem());}
        if(command==3){CarrierRepository.load(p.getOffhandItem()).stopAll();CarrierRepository.commit(p.getOffhandItem());p.getInventory().selected=1;}
        if(command==4){BookService.openNotebook(p,p.getMainHandItem());}
        if(command==5){var note=p.getMainHandItem();BookService.forget(p.getUUID());BookService.notebookRequest(p,NotebookRepository.load(note).identity(),0,2,"","");}
        ack=command;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!enabled())return;var mc=Minecraft.getInstance();
        if(stage<0){
            if(mc.screen instanceof AccessibilityOnboardingScreen){mc.options.onboardAccessibility=false;mc.setScreen(new TitleScreen());}
            if(++ticks>600)throw new AssertionError("书籍验证未到达标题页");
            if(!(mc.screen instanceof TitleScreen) || mc.getOverlay()!=null)return;
            mc.options.pauseOnLostFocus=false;mc.options.bobView().set(false);mc.options.guiScale().set(2);mc.options.renderDistance().set(4);mc.options.simulationDistance().set(5);FeedbackConfig.CAMERA.set(false);
            org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),1280,800);mc.resizeDisplay();validate(mc);mc.getLanguageManager().setSelected("zh_cn");mc.options.languageCode="zh_cn";mc.reloadResourcePacks();mc.setScreen(new Gallery());stage=0;ticks=0;return;
        }
        if(mc.getOverlay()!=null)return;
        if(++ticks>1000)throw new AssertionError("三维书籍 / 法杖持握验证超时，stage="+stage+", command="+command+", ack="+ack);
        if(stage==0 && ticks>35){capture(mc,"books-gallery");stage=1;ticks=0;var settings=new LevelSettings("书籍与法杖持握隔离验证",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT);mc.createWorldOpenFlows().createFreshLevel("books-"+System.currentTimeMillis(),settings,new WorldOptions(20261031,false,false),a->a.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),new TitleScreen());return;}
        if(stage==1){
            if(mc.player==null || mc.level==null || mc.screen!=null || !initialized || ticks<40)return;
            var wand=mc.player.getMainHandItem();var book=mc.player.getOffhandItem();if(!wand.is(ElementalCircuits.WAND.get()) || !book.is(ElementalCircuits.SPELL_BOOK.get()))return;
            var motion=BookAnimationClient.pose(book,ItemDisplayContext.FIRST_PERSON_LEFT_HAND,1);if(motion.open()==1)opened=true;
            if(!used && opened){capture(mc,"wand-steady-ready-book-open");var result=mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);if(result.shouldSwing() || !result.consumesAction())throw new AssertionError("施法仍请求原版挥动物品");mc.gameRenderer.itemInHandRenderer.itemUsed(InteractionHand.MAIN_HAND);used=true;}
            if(!used)return;
            var ext=IClientItemExtensions.of(wand);var reference=new PoseStack();if(!ext.applyForgeHandTransform(reference,mc.player,mc.player.getMainArm(),wand,1,0,0))return;
            for(float equip:new float[]{.25f,.7f,1}){var pose=new PoseStack();if(!ext.applyForgeHandTransform(pose,mc.player,mc.player.getMainArm(),wand,1,equip,0) || !pose.last().pose().equals(reference.last().pose(),.00001f))throw new AssertionError("冷却导致持杖位置收缩");steadyChecks++;}
            if(WandCooldown.state(wand).remaining()>2 && !busy){capture(mc,"wand-steady-busy-slot-cooldown");busy=true;command=1;}
            if(motion.turn()>.3 && motion.turn()<.8 && !turned){capture(mc,"spellbook-page-turn");turned=true;}
            if(busy && turned && steadyChecks>=60){stage=2;ticks=0;command=2;}
            return;
        }
        if(stage==2){if(!(mc.screen instanceof SpellbookScreen) || ticks<20)return;capture(mc,"spellbook-screen-3d");mc.setScreen(null);mc.options.mainHand().set(HumanoidArm.LEFT);stage=3;ticks=0;command=3;return;}
        if(stage==3){if(mc.player==null || !mc.player.getMainHandItem().is(ElementalCircuits.NOTEBOOK.get()))return;var pose=BookAnimationClient.pose(mc.player.getMainHandItem(),ItemDisplayContext.FIRST_PERSON_LEFT_HAND,1);if(pose.open()>0 && pose.open()<.95 && !noteOpened){capture(mc,"notebook-opening-left-hand");noteOpened=true;}if(pose.open()==1 && ticks>25){capture(mc,"notebook-open-left-hand");stage=4;ticks=0;command=4;}return;}
        if(stage==4){if(!(mc.screen instanceof NotebookScreen) || ticks<20)return;capture(mc,"notebook-screen-3d");stage=5;ticks=0;command=5;return;}
        if(stage==5){if(ticks<25 || ack!=5)return;mc.setScreen(new Gallery());if(!reloadRequested){reloadRequested=true;mc.reloadResourcePacks().thenRun(()->reloaded=true);}stage=6;ticks=0;return;}
        if(stage==6){if(!reloaded || mc.getOverlay()!=null || ticks<25)return;validate(mc);capture(mc,"books-after-resource-reload");mc.setScreen(null);mc.level.disconnect();mc.disconnect(new TitleScreen());stage=7;ticks=0;return;}
        if(stage==7 && ticks>8){if(mc.level!=null || !opened || !busy || !turned || !noteOpened)throw new AssertionError("未覆盖开书、翻页、独立冷却或退出");org.slf4j.LoggerFactory.getLogger("BookVisualSmoke").info("BOOK_HAND_WORLD_PASS: {} steady-pose checks; real server VM + itemUsed reset + native slot decorator + opening + acknowledged page turn + left hand + UI + resource reload + logout",steadyChecks);mc.stop();stage=8;}
    }
}
