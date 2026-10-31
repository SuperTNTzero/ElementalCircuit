package dev.elementalcircuits.client;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.*;
import dev.elementalcircuits.application.observation.SpellVisual;
import dev.elementalcircuits.client.effect.*;
import dev.elementalcircuits.client.editor.*;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.network.CircuitNetwork;
import dev.elementalcircuits.server.SpellEffects;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import java.util.*;

/** Real VM -> server effect -> batched network -> GPU; test code is excluded from the release. */
@EventBusSubscriber(modid=ElementalCircuits.ID,value=Dist.CLIENT)
public final class ShapeVisualSmoke {
    private static final Rune.Kind[] KINDS=ShapeContracts.KINDS;
    private static final Element[] ELEMENTS={Element.FIRE,Element.WATER,Element.LIGHTNING,Element.WIND,Element.LIGHT,Element.ARCANE};
    private static volatile int command=-1,ack=-2;
    private static volatile boolean initialized;
    private static int stage=-1,ticks,readyTicks;
    private static boolean requested;
    private static TickCircuit circuit;
    private static ManaPool supply;
    private static boolean enabled(){return Boolean.getBoolean("elementalcircuits.shapeSmoke");}
    private static Blueprint board(int index){
        var kind=KINDS[index];var b=AssemblyContracts.line(true,Route.Payload.MANA,Rune.Kind.INPUT,Rune.Kind.CONVERT,kind==Rune.Kind.FAN || kind==Rune.Kind.WALL?Rune.Kind.DIRECTED:Rune.Kind.AREA,kind,Rune.Kind.OUTPUT).withMaterial(Rules.Material.CRYSTAL);
        return new Blueprint(1,b.rules(),b.runes().stream().map(r->r.kind()!=Rune.Kind.CONVERT?r:new Rune(r.id(),r.kind(),r.cell(),ELEMENTS[index],r.value(),r.on(),r.signalControl(),r.weights())).toList(),b.routes(),true);
    }
    private static VisualTimeline timeline(){try{var f=SpellVisualClient.class.getDeclaredField("TIMELINE");f.setAccessible(true);return (VisualTimeline)f.get(null);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
    @SubscribeEvent public static void world(LevelTickEvent.Post event){
        if(!enabled() || !(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level) || level.dimension()!=Level.OVERWORLD || level.players().isEmpty())return;
        var p=level.players().get(0);
        if(!initialized){level.setDayTime(1000);level.setWeatherParameters(100000,0,false,false);p.connection.teleport(4,-56,9,146.31f,26.6f);p.getAbilities().mayfly=true;p.getAbilities().flying=true;p.onUpdateAbilities();
            for(int x=-6;x<=6;x++)for(int z=-3;z<=10;z++)level.setBlock(new BlockPos(x,-61,z),Blocks.DEEPSLATE_BRICKS.defaultBlockState(),3);initialized=true;}
        if(command<0)return;
        if(command!=ack){
            if(circuit!=null)circuit.stop();
            if(command<6){circuit=new TickCircuit(board(command));supply=new ManaPool(64*Fixed.ONE,0,0);circuit.start();}else circuit=null;
            ack=command;
        }
        if(circuit==null)return;
        Vec3 origin=new Vec3(0,-58,3);
        if(command==0)origin=origin.add(0,0,-1);
        if(command==1)origin=origin.add(0,0,-circuit.definition().rules().range());
        if(command==2 || command==4 || command==5)origin=origin.add(0,ShapeResources.rules().groundOffset()/(double)Fixed.ONE,0);
        for(var cast:circuit.tick(supply))SpellEffects.castOwned(level,p.getUUID(),origin,new Vec3(0,0,1),new UUID(29,2026),cast,circuit.definition().rules(),Fixed.ONE);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!enabled())return;var mc=Minecraft.getInstance();
        if(stage<0){
            if(mc.screen instanceof AccessibilityOnboardingScreen){mc.options.onboardAccessibility=false;mc.setScreen(new TitleScreen());}
            if(!(mc.screen instanceof TitleScreen) || mc.getOverlay()!=null)return;
            mc.options.pauseOnLostFocus=false;mc.options.guiScale().set(2);mc.options.renderDistance().set(4);mc.options.simulationDistance().set(5);org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),1440,900);mc.resizeDisplay();mc.getLanguageManager().setSelected("zh_cn");mc.options.languageCode="zh_cn";mc.reloadResourcePacks();stage=0;ticks=0;return;
        }
        if(mc.getOverlay()!=null)return;if(++ticks>1500)throw new AssertionError("形状 GPU 验证超时："+stage);
        if(stage==0 && ticks>40 && !requested){requested=true;var settings=new LevelSettings("形状隔离验证",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT);mc.createWorldOpenFlows().createFreshLevel("shapes-"+System.currentTimeMillis(),settings,new WorldOptions(20261031,false,false),a->a.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),new TitleScreen());return;}
        if(stage==9 && ticks>10){mc.stop();return;}
        if(!initialized || mc.level==null || mc.player==null)return;
        if(stage==0){if(mc.screen!=null || mc.player.tickCount<80 || ++readyTicks<30)return;mc.options.hideGui=true;stage=1;ticks=0;command=0;return;}
        if(stage>=1 && stage<=6){
            int index=stage-1;var kind=SpellVisual.Kind.valueOf(KINDS[index].name());
            if(ack!=index || ticks<55 || SpellVisualRenderer.lastDraws()!=1 || timeline().frames().stream().noneMatch(f->f.event().kind()==kind && f.age()<3))return;
            Screenshot.grab(mc.gameDirectory,"shape-"+KINDS[index].name().toLowerCase(Locale.ROOT)+".png",mc.getMainRenderTarget(),m->{});
            org.slf4j.LoggerFactory.getLogger("ShapeVisualSmoke").info("SHAPE_GPU_CAPTURE: {} actual VM + network + {} quads in {} draw",kind,SpellVisualRenderer.lastQuads(),SpellVisualRenderer.lastDraws());
            command=stage;stage++;ticks=0;return;
        }
        if(stage==7 && ack==6 && ticks>25){
            if(!timeline().frames().isEmpty() || SpellVisualRenderer.lastDraws()!=0)throw new AssertionError("停止施法后残留形状特效");
            var b=board(0);var runes=new ArrayList<>(b.runes());for(int i=1;i<6;i++)runes.add(Rune.create(5+i,KINDS[i],new Hex(i-3,2)));
            b=new Blueprint(1,b.rules(),runes,b.routes(),true);
            var screen=new CircuitScreen(new CircuitNetwork.Editor(new UUID(29,2026),JsonCodecs.blueprint(b),false,0,true,"扇形喷洒 · 元素墙 · 环带 · 立柱 · 十字 · 穹顶冲击",WandResources.definitions().basic(),0));
            try{var field=CircuitScreen.class.getDeclaredField("model");field.setAccessible(true);var model=(EditorModel)field.get(screen);model.expanded.clear();model.expanded.add(RuneCatalog.Group.SHAPE);model.paletteOffset=4;}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
            mc.options.hideGui=false;mc.setScreen(screen);stage=8;ticks=0;return;
        }
        if(stage==8 && ticks>35 && mc.screen instanceof CircuitScreen screen){
            for(var kind:KINDS)if(screen.children().stream().noneMatch(c->c instanceof net.minecraft.client.gui.components.Button b && b.getMessage().getString().equals(RuneCatalog.title(kind))))throw new AssertionError("侧栏未显示新形状："+kind);
            Screenshot.grab(mc.gameDirectory,"shape-editor.png",mc.getMainRenderTarget(),m->{});mc.setScreen(null);mc.level.disconnect();mc.disconnect(new TitleScreen());stage=9;ticks=0;org.slf4j.LoggerFactory.getLogger("ShapeVisualSmoke").info("SHAPE_GPU_PASS: six real circuit casts, one batch draw, expiry, Chinese editor and atlas coverage");
        }
    }
}
