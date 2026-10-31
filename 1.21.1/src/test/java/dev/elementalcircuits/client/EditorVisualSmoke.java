package dev.elementalcircuits.client;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.*;
import dev.elementalcircuits.client.editor.EditorLayout;
import dev.elementalcircuits.client.editor.BlueprintTransferLayout;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.network.CircuitNetwork;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.event.tick.*;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import java.util.*;

/** Explicit opt-in client harness; never bundled in the production jar or connects to a world. */
@net.neoforged.fml.common.EventBusSubscriber(modid=ElementalCircuits.ID,value=Dist.CLIENT)
public final class EditorVisualSmoke {
    private static int ticks,stage=-1;
    private static Hex pointer;
    private static Blueprint exported;
    private static void verifyReplies(CircuitScreen screen,CircuitNetwork.Editor initial){
        try{
            var field=CircuitScreen.class.getDeclaredField("requests");field.setAccessible(true);var requests=(dev.elementalcircuits.client.editor.EditorRequests)field.get(screen);
            UUID refresh=requests.begin(5),command=requests.begin(0);Blueprint draft=screen.draftForExport();
            var stale=new CircuitNetwork.Editor(initial.identity(),JsonCodecs.blueprint(Blueprint.empty(draft.rules())),false,0,false,"迟到的旧刷新",initial.assembly(),initial.wear(),initial.slot(),initial.fixed(),initial.management(),initial.logic(),initial.book(),refresh);
            screen.accept(stale);if(!requests.waiting() || !screen.draftForExport().equals(draft))throw new AssertionError("旧刷新覆盖了修改等待中的草稿");
            screen.accept(new CircuitNetwork.Editor(initial.identity(),initial.blueprint(),initial.powered(),initial.held(),initial.open(),"修改已确认",initial.assembly(),initial.wear(),initial.slot(),initial.fixed(),initial.management(),initial.logic(),initial.book(),command));
            screen.accept(stale);if(requests.waiting() || !screen.message().equals("修改已确认") || !screen.draftForExport().equals(draft))throw new AssertionError("迟到的旧刷新回滚了已确认状态");
            command=requests.begin(4);for(int i=0;i<100;i++)screen.tick();if(requests.waiting() || !screen.message().contains("结果未知"))throw new AssertionError("修改超时没有回到可刷新状态");
            screen.accept(new CircuitNetwork.Editor(initial.identity(),initial.blueprint(),false,0,false,"过期回复",initial.assembly(),0,initial.slot(),initial.fixed(),"","",initial.book(),command));if(!screen.message().contains("结果未知"))throw new AssertionError("过期回复冒充新请求完成");
            screen.accept(initial);org.slf4j.LoggerFactory.getLogger("ElementalCircuits").info("EDITOR_REQUEST_STATE_SMOKE_PASS");
        }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    private static dev.elementalcircuits.client.editor.EditorModel editor(CircuitScreen screen){try{var f=CircuitScreen.class.getDeclaredField("model");f.setAccessible(true);return (dev.elementalcircuits.client.editor.EditorModel)f.get(screen);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
    private static net.minecraft.client.gui.components.EditBox parameter(CircuitScreen screen){try{var f=CircuitScreen.class.getDeclaredField("parameter");f.setAccessible(true);return (net.minecraft.client.gui.components.EditBox)f.get(screen);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
    private static void select(CircuitScreen screen,int id){var l=EditorLayout.of(screen.width,screen.height,4);var h=screen.draftForExport().rune(id).cell();screen.mouseClicked(l.x(h),l.y(h),0);screen.mouseReleased(l.x(h),l.y(h),0);}
    private static void beginLogic(Minecraft mc){
        GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),1280,800);mc.options.guiScale().set(2);mc.resizeDisplay();
        Blueprint board=LogicExamples.alternating(RuleResources.defaults(),LogicResources.defaults());var vm=new TickCircuit(board);vm.start();var source=new ManaPool(1024*Fixed.ONE,0,0);for(int i=0;i<43;i++)vm.tick(source);
        var screen=new CircuitScreen(new CircuitNetwork.Editor(new UUID(28,4),JsonCodecs.blueprint(board),true,vm.held(),true,"时序符文验证：4拍计数后交替火/水",WandResources.definitions().basic(),0,1,false,"",JsonCodecs.logicObservation(vm)));
        mc.setScreen(screen);var model=editor(screen);model.expanded.clear();model.expanded.add(dev.elementalcircuits.client.editor.RuneCatalog.Group.LOGIC);model.paletteOffset=5;model.selected=2;model.parameterScroll=0;screen.resize(mc,screen.width,screen.height);
    }

    private static void beginEffects(Minecraft mc){
        pointer=null;GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),1280,800);mc.options.guiScale().set(2);mc.resizeDisplay();
        var board=ProjectileExamples.combined(RuleResources.defaults(),LogicResources.defaults(),ProjectileResources.rules());
        var screen=new CircuitScreen(new CircuitNetwork.Editor(new UUID(29,8),JsonCodecs.blueprint(board),false,0,false,"派生效果：爆裂、弹跳、追踪可独立组合",WandResources.definitions().basic(),0));mc.setScreen(screen);
        var model=editor(screen);model.expanded.clear();model.expanded.add(dev.elementalcircuits.client.editor.RuneCatalog.Group.EFFECT);model.paletteOffset=4;model.selected=5;model.parameterScroll=0;screen.resize(mc,screen.width,screen.height);
    }

    private static void beginSecondary(Minecraft mc){
        pointer=null;GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),1280,800);mc.options.guiScale().set(2);mc.resizeDisplay();
        var board=ProjectileExamples.secondary(RuleResources.defaults(),LogicResources.defaults(),ProjectileResources.rules(),SecondaryResources.rules());
        var screen=new CircuitScreen(new CircuitNetwork.Editor(new UUID(29,9),JsonCodecs.blueprint(board),false,0,false,"命中派生与附加效果：共享同一预算",WandResources.definitions().basic(),0));mc.setScreen(screen);var model=editor(screen);model.expanded.clear();model.expanded.add(dev.elementalcircuits.client.editor.RuneCatalog.Group.EFFECT);model.paletteOffset=8;model.selected=5;model.parameterScroll=0;screen.resize(mc,screen.width,screen.height);
    }
    private static CircuitScreen assemblyParent;
    private static String management;
    private static CircuitNetwork.Editor assemblyPacket(int slot,Blueprint board){return new CircuitNetwork.Editor(new UUID(28,3),JsonCodecs.blueprint(board),false,0,false,"管理页视觉验证：四槽、互联与基板安装",WandResources.definitions().basic(),0,slot,true,management);}
    private static void beginAssembly(Minecraft mc,CircuitScreen parent){
        assemblyParent=parent;var id=new UUID(28,3);
        var data=new dev.elementalcircuits.application.observation.AssemblyInspection(1,4,true,16*Fixed.ONE,128*Fixed.ONE,
            List.of(new dev.elementalcircuits.application.observation.AssemblyInspection.Slot(1,Rules.Material.WOOD,false,0),new dev.elementalcircuits.application.observation.AssemblyInspection.Slot(2,Rules.Material.CRYSTAL,false,0),new dev.elementalcircuits.application.observation.AssemblyInspection.Slot(3,Rules.Material.BRASS,true,Fixed.ONE),new dev.elementalcircuits.application.observation.AssemblyInspection.Slot(4,Rules.Material.WOOD,false,0)),
            List.of(new dev.elementalcircuits.application.observation.AssemblyInspection.Port(new CircuitAddress(id,1,2),Rune.Kind.MANA_SEND,"本载体 / 阵1 / #2"),new dev.elementalcircuits.application.observation.AssemblyInspection.Port(new CircuitAddress(id,2,1),Rune.Kind.MANA_RECEIVE,"本载体 / 阵2 / #1"),new dev.elementalcircuits.application.observation.AssemblyInspection.Port(new CircuitAddress(id,3,1),Rune.Kind.SIGNAL_SEND,"本载体 / 阵3 / #1"),new dev.elementalcircuits.application.observation.AssemblyInspection.Port(new CircuitAddress(id,4,1),Rune.Kind.SIGNAL_RECEIVE,"本载体 / 阵4 / #1")),
            List.of(new dev.elementalcircuits.application.observation.AssemblyInspection.Link(1,false,"魔力 本1#2 → 本2#1",2*Fixed.ONE)),
            List.of(new dev.elementalcircuits.application.observation.AssemblyInspection.Stock(Rules.Material.WOOD,3),new dev.elementalcircuits.application.observation.AssemblyInspection.Stock(Rules.Material.BRASS,0),new dev.elementalcircuits.application.observation.AssemblyInspection.Stock(Rules.Material.CRYSTAL,2)));
        management=JsonCodecs.inspection(data);parent.accept(assemblyPacket(1,board()));
        Blueprint first=board();parent.acceptImported(first.withMaterial(Rules.Material.BRASS));parent.accept(assemblyPacket(2,Blueprint.empty(RuleResources.defaults().withMaterial(Rules.Material.CRYSTAL))));
        parent.accept(assemblyPacket(1,first));if(parent.draftForExport().rules().material()!=Rules.Material.BRASS)throw new AssertionError("切换槽位丢失未保存草稿");
        parent.manage(0);
    }
    private static void text(BlueprintScreen screen,String value){
        try{var field=BlueprintScreen.class.getDeclaredField("code");field.setAccessible(true);((net.minecraft.client.gui.components.MultiLineEditBox)field.get(screen)).setValue(value);}
        catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    private static Blueprint board(){
        var nodes=new ArrayList<>(List.of(Rune.create(1,Rune.Kind.INPUT,new Hex(-3,0)),Rune.create(2,Rune.Kind.CONVERT,new Hex(-1,0)),Rune.create(3,Rune.Kind.DIRECTED,new Hex(0,1)),Rune.create(4,Rune.Kind.BEAM,new Hex(2,0)),Rune.create(5,Rune.Kind.OUTPUT,new Hex(3,-2))));
        Hex[] cells={new Hex(-2,-2),new Hex(0,-2),new Hex(2,-2),new Hex(-3,2),new Hex(-1,2),new Hex(1,2),new Hex(3,-1),new Hex(0,3)};int at=0;
        for(Element element:Element.values())if(element!=Element.FIRE){Rune node=Rune.create(6+at,Rune.Kind.CONVERT,cells[at++]);nodes.add(new Rune(node.id(),node.kind(),node.cell(),element,node.value(),node.on(),node.signalControl(),node.weights()));}
        Blueprint b=new Blueprint(1,RuleResources.defaults(),nodes,List.of(),false);List<Route> routes=new ArrayList<>();
        for(int i=0;i<4;i++)routes.add(new Route(i+1,i+2,Route.Payload.MANA,HexRouter.segment(b,nodes.get(i).cell(),nodes.get(i+1).cell(),List.of())));
        return new Blueprint(1,b.rules(),nodes,routes,false);
    }
    private static void point(Minecraft mc,EditorLayout l,Hex cell){
        pointer=cell;double scale=mc.getWindow().getGuiScale();
        // The smoke window may be unfocused. Inject only test mouse observation fields, not game state.
        try{var x=MouseHandler.class.getDeclaredField("xpos");var y=MouseHandler.class.getDeclaredField("ypos");x.setAccessible(true);y.setAccessible(true);x.setDouble(mc.mouseHandler,l.x(cell)*scale);y.setDouble(mc.mouseHandler,l.y(cell)*scale);}
        catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    private static CircuitScreen libraryParent;
    private static void click(Minecraft mc,String label){
        for(var child:mc.screen.children())if(child instanceof net.minecraft.client.gui.components.Button b && b.getMessage().getString().equals(label)){if(b.getX()<0 || b.getY()<0 || b.getX()+b.getWidth()>mc.screen.width || b.getY()+b.getHeight()>mc.screen.height)throw new AssertionError("控件越界："+label);mc.screen.mouseClicked(b.getX()+b.getWidth()/2.0,b.getY()+10,0);mc.screen.mouseReleased(b.getX()+b.getWidth()/2.0,b.getY()+10,0);return;}throw new AssertionError("缺少按钮："+label);
    }
    private static void beginBooks(Minecraft mc){
        pointer=null;GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),1280,800);mc.options.guiScale().set(2);mc.resizeDisplay();var defs=WandResources.definitions();var stock=new HashMap<String,Integer>();for(var part:defs.parts())stock.put(part.id(),2);mc.setScreen(new WorkshopScreen(new WorkshopInspection(new UUID(29,10),7,defs.basic(),23,stock,3,"选择部件预览，点击安装后服务器扣除库存",true)));
    }
    private static NotebookInspection notebookView(){var v=BlueprintVolume.empty(new UUID(29,11));for(int i=0;i<16;i++)v=v.add("蓝图 "+(i+1),i%2==0?board():LogicExamples.alternating(RuleResources.defaults(),LogicResources.defaults()));return NotebookInspection.inspect(v,"实体簿保存设计配置；选页可复制或载入草稿");}
    /** Clean UI reference captures only: detached examples, never advertised as survival footage. */
    private static void showcase(Minecraft mc,int page){
        var defs=WandResources.definitions();var rules=RuleResources.defaults();
        var fire=AssemblyContracts.line(false,Route.Payload.MANA,Rune.Kind.INPUT,Rune.Kind.CONVERT,Rune.Kind.DIRECTED,Rune.Kind.SPHERE,Rune.Kind.OUTPUT);
        var alternate=LogicExamples.alternating(rules,LogicResources.defaults());
        var secondary=ProjectileExamples.secondary(rules,LogicResources.defaults(),ProjectileResources.rules(),SecondaryResources.rules());
        switch(page){
            case 70->{var screen=new CircuitScreen(new CircuitNetwork.Editor(new UUID(30,17),JsonCodecs.blueprint(fire),false,0,true,"选符文、画连线，再保存起阵",defs.basic(),12));mc.setScreen(screen);var m=editor(screen);m.expanded.clear();m.expanded.add(dev.elementalcircuits.client.editor.RuneCatalog.Group.ELEMENT);screen.resize(mc,screen.width,screen.height);}
            case 71->{var stock=new HashMap<String,Integer>();for(var part:defs.parts())stock.put(part.id(),2);mc.setScreen(new WorkshopScreen(new WorkshopInspection(new UUID(30,18),7,defs.basic(),12,stock,3,"选择部件查看变化，安装时会退回旧件",true)));click(mc,"紫晶杖端 ×2 · 可选");}
            case 72->{var volume=BlueprintVolume.empty(new UUID(30,19)).add("火球",fire).add("火水交替",alternate).add("分裂连锁",secondary);mc.setScreen(new NotebookScreen(null,NotebookInspection.inspect(volume,"复制蓝图码，即可把这张法阵分享给朋友")));}
            case 73->{var a=new CircuitAssembly(new UUID(30,20),AssemblyResources.rules(),new TickCircuit(fire));a.add(rules);a.replace(2,alternate);a.add(rules);a.replace(3,secondary);a.select(2);mc.setScreen(new SpellbookScreen(SpellbookInspection.inspect(a,"将法术书放在副手，右键主手法杖即可起阵")));}
            case 74->{var screen=new CircuitScreen(new CircuitNetwork.Editor(new UUID(30,21),JsonCodecs.blueprint(alternate),false,0,true,"火与水每4拍切换一次。保存后起阵",defs.basic(),12));mc.setScreen(screen);var m=editor(screen);m.expanded.clear();m.expanded.add(dev.elementalcircuits.client.editor.RuneCatalog.Group.LOGIC);m.paletteOffset=5;m.selected=2;screen.resize(mc,screen.width,screen.height);}
            default->throw new AssertionError("未知界面参考页");
        }
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!Boolean.getBoolean("elementalcircuits.editorSmoke"))return;
        Minecraft mc=Minecraft.getInstance();
        if(stage<0){if(!(mc.screen instanceof TitleScreen))return;
            if(Boolean.getBoolean("elementalcircuits.showcaseCapture")){GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),1280,800);mc.options.guiScale().set(2);mc.resizeDisplay();showcase(mc,70);stage=70;ticks=0;return;}
            if(Boolean.getBoolean("elementalcircuits.bookSmoke")){beginBooks(mc);stage=46;ticks=0;return;}
            GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),1280,800);mc.options.guiScale().set(2);mc.resizeDisplay();
            var initial=new CircuitNetwork.Editor(new UUID(28,3),JsonCodecs.blueprint(board()),false,0,true,"视觉验证：非相邻法阵示例",WandResources.definitions().basic(),12);var screen=new CircuitScreen(initial);mc.setScreen(screen);verifyReplies(screen,initial);stage=0;ticks=0;return;
        }
        if(stage>=70 && stage<=74){
            if(mc.getOverlay()!=null){ticks=0;return;}
            if(++ticks<30)return;
            for(var child:mc.screen.children())if(child instanceof net.minecraft.client.gui.components.AbstractWidget b && (b.getX()<0 || b.getY()<0 || b.getX()+b.getWidth()>mc.screen.width || b.getY()+b.getHeight()>mc.screen.height))throw new AssertionError("界面参考页控件越界");
            Screenshot.grab(mc.gameDirectory,"showcase-"+stage+".png",mc.getMainRenderTarget(),message->{});
            if(stage==74){org.slf4j.LoggerFactory.getLogger("EditorVisualSmoke").info("UI_SHOWCASE_CAPTURE_PASS: 5 clean detached interface references, not gameplay footage");mc.stop();stage=75;return;}
            showcase(mc,++stage);ticks=0;return;
        }
        if(stage>=46 && stage<=60){
            if(++ticks<35)return;Screenshot.grab(mc.gameDirectory,"books-"+stage+".png",mc.getMainRenderTarget(),message->{});
            for(var child:mc.screen.children())if(child instanceof net.minecraft.client.gui.components.AbstractWidget b && (b.getX()<0 || b.getY()<0 || b.getX()+b.getWidth()>mc.screen.width || b.getY()+b.getHeight()>mc.screen.height))throw new AssertionError("书籍或工作台控件越界");
            switch(stage){
                case 46->click(mc,"杖身");
                case 47->click(mc,"杖尾");
                case 48->{GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),960,720);mc.options.guiScale().set(3);mc.resizeDisplay();click(mc,"杖端");}
                case 49->{var part=WandResources.definitions().in(WandParts.Slot.HEAD).get(1);click(mc,part.title()+" ×2 · 可选");}
                case 50->{libraryParent=new CircuitScreen(new CircuitNetwork.Editor(new UUID(29,12),JsonCodecs.blueprint(board()),false,0,true,"父草稿",WandResources.definitions().basic(),0));mc.setScreen(libraryParent);mc.setScreen(new NotebookScreen(libraryParent,notebookView()));}
                case 51->{if(mc.screen.children().stream().noneMatch(c->c instanceof net.minecraft.client.gui.components.Button b && b.getMessage().getString().equals("蓝图 16")))throw new AssertionError("当前选页不在初始可见目录内");click(mc,"复制蓝图码");if(!BlueprintTextCode.decode(mc.keyboardHandler.getClipboard()).equals(notebookView().blueprint()))throw new AssertionError("实体蓝图簿选页复制失败");for(int i=0;i<20;i++)mc.screen.mouseScrolled(20,110,0,-1);}
                case 52->{GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),1280,800);mc.options.guiScale().set(2);mc.resizeDisplay();}
                case 53->{click(mc,"载入蓝图");if(mc.screen!=libraryParent || !libraryParent.draftForExport().equals(notebookView().blueprint()))throw new AssertionError("蓝图簿载入草稿没有保留完整配置");var empty=NotebookInspection.inspect(BlueprintVolume.empty(new UUID(29,13)),"空簿：新增蓝图或在刻印界面保存草稿");mc.setScreen(new NotebookScreen(null,empty));}
                case 54->click(mc,"新增蓝图");
                case 55->{if(!(mc.screen instanceof NotebookImportScreen))throw new AssertionError("实体蓝图簿独立文本导入页未打开");mc.screen.onClose();var a=new CircuitAssembly(new UUID(29,14),AssemblyResources.rules(),new TickCircuit(board()));for(int i=0;i<3;i++)a.add(RuleResources.defaults());a.replace(4,LogicExamples.alternating(RuleResources.defaults(),LogicResources.defaults()));a.select(1);mc.setScreen(new SpellbookScreen(SpellbookInspection.inspect(a,"本书拥有4个独立法阵；主手法杖＋副手本书施法")));}
                case 56->click(mc,"阵 4 · 停供");
                case 57->{GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),960,720);mc.options.guiScale().set(3);mc.resizeDisplay();}
                case 58->{var a=new CircuitAssembly(new UUID(29,14),AssemblyResources.rules(),new TickCircuit(board()));for(int i=0;i<3;i++)a.add(RuleResources.defaults());a.replace(4,LogicExamples.alternating(RuleResources.defaults(),LogicResources.defaults()));a.circuit(4).start();a.select(4);((SpellbookScreen)mc.screen).accept(SpellbookInspection.inspect(a,"翻页后返回服务器观察；此前法阵状态完整保留"));}
                case 59->{mc.setScreen(new NotebookScreen(null,notebookView()));for(int i=0;i<30;i++)mc.screen.mouseScrolled(20,110,0,-1);}
                case 60->{org.slf4j.LoggerFactory.getLogger("EditorVisualSmoke").info("ELEMENT EDITOR VISUAL COMPLETE: {} screenshots; workbench, physical library import/export/preview, spellbook four slots and compact bounds verified",Boolean.getBoolean("elementalcircuits.bookSmoke")?15:61);VisualBatchSmoke.begin(mc);}
                default->throw new AssertionError();
            }
            stage++;ticks=0;return;
        }
        if(stage>=37 && stage<=45){
            if(!(mc.screen instanceof CircuitScreen page))throw new AssertionError("命中派生编辑器丢失");if(++ticks<40)return;
            Screenshot.grab(mc.gameDirectory,"secondary-"+stage+".png",mc.getMainRenderTarget(),message->{});
            if(stage>=37 && stage<=40)select(page,stage-31);
            if(stage==41){GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),960,720);mc.options.guiScale().set(3);mc.resizeDisplay();editor(page).parameterScroll=0;page.resize(mc,page.width,page.height);}
            if(stage==42){var box=parameter(page);if(!page.children().contains(box))throw new AssertionError("附加效果参数框不可见");box.setValue("5");page.mouseClicked(box.getX()+10,box.getY()+34,0);if(page.draftForExport().rune(9).value()!=Fixed.ONE)throw new AssertionError("越界击退等级未拒绝");}
            if(stage==43){var box=parameter(page);box.setValue("3");page.mouseClicked(box.getX()+10,box.getY()+34,0);if(page.draftForExport().rune(9).value()!=3*Fixed.ONE)throw new AssertionError("击退参数未写入草稿");}
            if(stage==44){editor(page).choose(Rune.Kind.FRAGMENT);point(mc,EditorLayout.of(page.width,page.height,4),new Hex(0,1));}
            if(stage==45)beginBooks(mc);
            stage++;ticks=0;return;
        }
        if(stage>=30 && stage<=36){
            if(!(mc.screen instanceof CircuitScreen page))throw new AssertionError("派生编辑器丢失");if(++ticks<40)return;
            Screenshot.grab(mc.gameDirectory,"projectiles-"+stage+".png",mc.getMainRenderTarget(),message->{});
            if(stage==30)select(page,6);
            if(stage==31)select(page,7);
            if(stage==32){GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),960,720);mc.options.guiScale().set(3);mc.resizeDisplay();editor(page).parameterScroll=0;page.resize(mc,page.width,page.height);}
            if(stage==33){var box=parameter(page);if(!page.children().contains(box))throw new AssertionError("派生参数框不可见");box.setValue("13");page.mouseClicked(box.getX()+10,box.getY()+34,0);if(page.draftForExport().rune(7).value()!=8*Fixed.ONE)throw new AssertionError("越界追踪范围未被拒绝");}
            if(stage==34){var box=parameter(page);box.setValue("6");page.mouseClicked(box.getX()+10,box.getY()+34,0);if(page.draftForExport().rune(7).value()!=6*Fixed.ONE)throw new AssertionError("追踪参数无法应用");}
            if(stage==35){editor(page).choose(Rune.Kind.BURST);point(mc,EditorLayout.of(page.width,page.height,4),new Hex(0,-1));}
            if(stage==36)beginSecondary(mc);
            stage++;ticks=0;return;
        }
        if(stage>=22 && stage<=29){
            if(!(mc.screen instanceof CircuitScreen page))throw new AssertionError("时序编辑器丢失");if(++ticks<40)return;
            Screenshot.grab(mc.gameDirectory,"logic-"+stage+".png",mc.getMainRenderTarget(),message->{});
            if(stage==22)select(page,1);
            if(stage==23){select(page,3);int x=page.width-EditorLayout.of(page.width,page.height,4).right()+20;click(mc,"初始："+(page.draftForExport().rune(3).on()?"开":"关"));if(!page.draftForExport().rune(3).on())throw new AssertionError("锁存初态按钮未编辑草稿");}
            if(stage==24){GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),960,720);mc.options.guiScale().set(3);mc.resizeDisplay();}
            if(stage==25){select(page,2);editor(page).parameterScroll=0;page.resize(mc,page.width,page.height);}
            if(stage==26){var box=parameter(page);if(!page.children().contains(box))throw new AssertionError("紧凑窗口参数框不可见");box.setValue("3.5");page.mouseClicked(box.getX()+10,box.getY()+34,0);if(page.draftForExport().rune(2).value()!=4*Fixed.ONE)throw new AssertionError("小数计数阈值未被拒绝");}
            if(stage==27){var box=parameter(page);box.setValue("3");page.mouseClicked(box.getX()+10,box.getY()+34,0);if(page.draftForExport().rune(2).value()!=3*Fixed.ONE)throw new AssertionError("紧凑窗口无法应用整数阈值");}
            if(stage==28){editor(page).choose(Rune.Kind.COUNTER);point(mc,EditorLayout.of(page.width,page.height,4),new Hex(-2,0));}
            if(stage==29)beginEffects(mc);
            stage++;ticks=0;return;
        }
        if(stage>=16 && stage<=21){
            if(!(mc.screen instanceof AssemblyScreen page))throw new AssertionError("法阵管理子页丢失");if(++ticks<40)return;
            Screenshot.grab(mc.gameDirectory,"assemblies-"+stage+".png",mc.getMainRenderTarget(),message->{});
            int width=mc.getWindow().getGuiScaledWidth(),height=mc.getWindow().getGuiScaledHeight(),left=Math.max(8,(width-560)/2),span=width-2*left,tab=(span-8)/3;
            if(stage==16)page.mouseClicked(left+tab+15,42,0);
            if(stage==17)page.mouseClicked(left+2*(tab+4)+15,42,0);
            if(stage==18){GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),960,720);mc.options.guiScale().set(3);mc.resizeDisplay();}
            if(stage==19){page.mouseClicked(left+12,42,0);for(int i=0;i<12;i++)page.mouseScrolled(left+20,110,0,-1);}
            if(stage==20){page.mouseClicked(left+tab+15,42,0);for(int i=0;i<10;i++)page.mouseScrolled(left+20,110,0,-1);}
            if(stage==21){page.onClose();if(mc.screen!=assemblyParent)throw new AssertionError("管理页返回对象错误");beginLogic(mc);}
            stage++;ticks=0;return;
        }
        if(stage>=10 && stage<=14){
            if(!(mc.screen instanceof BlueprintScreen page))throw new IllegalStateException("蓝图子页面丢失");
            if(++ticks<50)return;var b=BlueprintTransferLayout.of(mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight());
            Screenshot.grab(mc.gameDirectory,"blueprints-"+stage+".png",mc.getMainRenderTarget(),message->org.slf4j.LoggerFactory.getLogger("EditorVisualSmoke").info("Screenshot {}",message.getString()));
            switch(stage){
                case 10->{text(page,"损坏的文本码");page.mouseClicked(b.left()+b.boxWidth()-40,b.actions()+10,0);}
                case 11->{page.mouseClicked(b.left()+b.boxWidth()-40,42,0);page.mouseClicked(b.left()+50,b.actions()+10,0);if(!mc.keyboardHandler.getClipboard().startsWith("EC1."))throw new AssertionError("导出按钮没有生成复制文本码");}
                case 12->{GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),1280,800);mc.options.guiScale().set(2);mc.resizeDisplay();}
                case 13->{page.mouseClicked(b.left()+40,42,0);text(page,mc.keyboardHandler.getClipboard());}
                case 14->{page.mouseClicked(b.left()+b.boxWidth()-40,b.actions()+10,0);if(!(mc.screen instanceof CircuitScreen parent) || !parent.draftForExport().equals(exported))throw new AssertionError("导入没有完整返回草稿");}
                default->throw new AssertionError();
            }
            stage++;ticks=0;return;
        }
        if(!(mc.screen instanceof CircuitScreen screen))return;
        if(pointer!=null)point(mc,EditorLayout.of(mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight(),4),pointer);
        if(++ticks<50)return;
        int w=mc.getWindow().getGuiScaledWidth(),h=mc.getWindow().getGuiScaledHeight();EditorLayout l=EditorLayout.of(w,h,4);
        Screenshot.grab(mc.gameDirectory,"elements-"+stage+".png",mc.getMainRenderTarget(),message->org.slf4j.LoggerFactory.getLogger("EditorVisualSmoke").info("Screenshot {}",message.getString()));
        switch(stage){
            case 0->{screen.mouseClicked(20,68,0);screen.mouseClicked(20,92,0);}
            case 1->{screen.mouseClicked(35,164,0);point(mc,l,new Hex(-2,1));}
            case 2->{screen.mouseClicked(l.x(new Hex(-2,1)),l.y(new Hex(-2,1)),0);screen.mouseClicked(20,40,0);screen.mouseClicked(l.x(new Hex(-2,1)),l.y(new Hex(-2,1)),0);screen.mouseReleased(l.x(new Hex(-2,1)),l.y(new Hex(-2,1)),0);}
            case 3->{screen.mouseClicked(l.x(new Hex(-2,1)),l.y(new Hex(-2,1)),0);screen.mouseDragged(l.x(new Hex(-1,1)),l.y(new Hex(-1,1)),0,10,0);point(mc,l,new Hex(-1,1));}
            case 4->point(mc,l,new Hex(-1,0));
            case 5->{screen.keyPressed(GLFW.GLFW_KEY_ESCAPE,0,0);screen.mouseClicked(75,40,0);screen.mouseClicked(l.x(new Hex(-3,0)),l.y(new Hex(-3,0)),0);point(mc,l,new Hex(3,-2));}
            case 6->{screen.keyPressed(GLFW.GLFW_KEY_ESCAPE,0,0);screen.mouseClicked(20,40,0);screen.mouseClicked(l.x(new Hex(-1,0)),l.y(new Hex(-1,0)),0);screen.mouseDragged(l.x(new Hex(-1,-1)),l.y(new Hex(-1,-1)),0,10,-10);point(mc,l,new Hex(-1,-1));}
            case 7->{screen.mouseReleased(l.x(new Hex(-1,-1)),l.y(new Hex(-1,-1)),0);GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),960,720);mc.options.guiScale().set(3);mc.resizeDisplay();}
            case 8->{screen.mouseClicked(w-30,40,0);}
            case 9->{pointer=null;exported=screen.draftForExport();screen.openBlueprints();}
            case 15->{GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),1280,800);mc.options.guiScale().set(2);mc.resizeDisplay();beginAssembly(mc,screen);}
            default->throw new IllegalStateException("未知视觉验证阶段");
        }
        stage++;ticks=0;
    }
}
