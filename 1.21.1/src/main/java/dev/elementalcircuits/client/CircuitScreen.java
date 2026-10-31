package dev.elementalcircuits.client;

import dev.elementalcircuits.application.observation.AssemblyInspection;
import dev.elementalcircuits.adapter.*;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.client.editor.*;
import dev.elementalcircuits.client.render.*;
import dev.elementalcircuits.network.CircuitNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.math.BigDecimal;
import java.util.*;

/** Minecraft input/network/widget assembly. Rule edits live in EditorModel; painting in EditorRenderer. */
public final class CircuitScreen extends Screen {
    private final UUID identity;
    private EditorModel model;
    private int slot=1;
    private boolean fixed,book,engraving;
    private AssemblyInspection inspection;
    private LogicObservation logic=new LogicObservation(1,List.of());
    private final Map<Integer,EditorModel> models=new HashMap<>();
    private final Map<Integer,Blueprint> serverBoards=new HashMap<>();
    public AssemblyInspection inspection(){return inspection;}
    public String message(){return model.message;}
    public int slot(){return slot;}
    public void manage(int page){model.cancel();boardPress=false;minecraft.setScreen(new AssemblyScreen(this,page));}
    public void managementRequest(int slot,int action,String payload){UUID token=requests.begin(action);CircuitNetwork.request(identity,slot,action,payload,token);if(action!=5)model.message="正在处理…";}
    public void installMaterial(Rules.Material material){managementRequest(slot,4,JsonCodecs.blueprint(model.draft.get().withMaterial(material)));}

    private EditorLayout layout;
    private WandParts.Assembly assembly;
    private int wear;
    private boolean powered;
    private long held;
    private EditBox parameter;
    private int contentHeight;
    private Blueprint serverBlueprint;
    private final EditorRequests requests=new EditorRequests();
    private int refreshTicks;
    private boolean boardPress;
    private NativeEditorCursor nativeCursor;
    private double pressX,pressY;
    private final BlueprintTransferModel blueprints=new BlueprintTransferModel();
    public Blueprint draftForExport(){return model.draft.get();}
    public void acceptImported(Blueprint blueprint){model.draft.set(blueprint);model.cancel();model.selected=-1;model.message=engraving?"蓝图已载入，点击「完成刻印」制成固定法阵":"蓝图已载入，保存后即可起阵";}
    public void openBlueprints(){model.cancel();boardPress=false;minecraft.setScreen(new BlueprintScreen(this,blueprints));}
    public CircuitScreen(CircuitNetwork.Editor packet){super(Component.literal("法阵刻印"));identity=packet.identity();accept(packet);}
    public void accept(CircuitNetwork.Editor packet){
        if(!identity.equals(packet.identity()))return;
        EditorRequests.Reply reply=requests.complete(packet.token());
        if(reply==EditorRequests.Reply.STALE)return;
        if(engraving && !packet.draft()){
            if(minecraft!=null && minecraft.player!=null){minecraft.player.displayClientMessage(Component.literal(packet.message()),true);minecraft.setScreen(null);}return;
        }
        engraving=packet.draft();
        Blueprint incoming=JsonCodecs.readBlueprint(packet.blueprint());
        int previousSlot=slot;
        slot=packet.slot();fixed=packet.fixed();book=packet.book();model=models.get(slot);serverBlueprint=serverBoards.get(slot);
        boolean changed=!incoming.equals(serverBlueprint);
        if(model==null){model=new EditorModel(incoming);models.put(slot,model);}
        else if(!incoming.equals(serverBlueprint)){model.draft.set(incoming);model.cancel();model.selected=-1;}
        serverBlueprint=incoming;serverBoards.put(slot,incoming);
        if(!packet.management().isEmpty())inspection=JsonCodecs.readInspection(packet.management());
        else inspection=new AssemblyInspection(slot,4,false,0,0,List.of(new AssemblyInspection.Slot(slot,incoming.rules().material(),packet.powered(),packet.held())),List.of(),List.of(),List.of());
        logic=packet.logic().isEmpty()?new LogicObservation(1,List.of()):JsonCodecs.readLogicObservation(packet.logic());
        powered=packet.powered();held=packet.held();assembly=packet.assembly();wear=packet.wear();assembly.stats(WandResources.definitions());
        boolean refresh=reply==EditorRequests.Reply.REFRESH;
        if(!refresh)model.message=packet.message();
        if(minecraft!=null && minecraft.screen==this && (!refresh || changed || previousSlot!=slot))rebuildWidgets();
    }
    private void attempt(Runnable action){try{action.run();}catch(RuntimeException e){model.message=e.getMessage()==null?"操作失败":e.getMessage();}}
    private CircuitButton widget(int x,int y,int w,int h,String text,boolean selected,Rune.Kind icon,boolean parent,Runnable action){
        CircuitButton button=new CircuitButton(x,y,w,h,text,selected,icon,parent,()->attempt(action));addRenderableWidget(button);return button;
    }
    private void button(int x,int y,int w,String text,EditorIcon icon,Runnable action){
        if(x>=width-layout.right()+6 && (y<parameterTop() || y+22>layout.bottom()-14))return;
        widget(x,y,w,22,text,false,null,false,action).icon(icon);
    }
    private int parameterTop(){return layout.top()+(!model.components && model.selected()!=null?40:0);}
    @Override protected void init(){
        layout=EditorLayout.of(width,height,model.draft.get().rules().boardRadius());
        if(nativeCursor==null)nativeCursor=new NativeEditorCursor(minecraft.getWindow().getWindow());
        widget(8,31,50,21,"选择",model.tool==EditorModel.Tool.SELECT,null,false,()->{model.tool=EditorModel.Tool.SELECT;model.cancel();rebuildWidgets();}).icon(EditorIcon.action(EditorIcon.Action.SELECT));
        widget(61,31,50,21,"连线",model.tool==EditorModel.Tool.ROUTE,null,false,()->{model.tool=EditorModel.Tool.ROUTE;model.cancel();rebuildWidgets();}).icon(EditorIcon.action(EditorIcon.Action.ROUTE));
        widget(114,31,40,21,model.payload==Route.Payload.MANA?"魔力":"信号",false,null,false,()->{model.payload=model.payload==Route.Payload.MANA?Route.Payload.SIGNAL:Route.Payload.MANA;model.cancel();rebuildWidgets();});
        widget(157,31,24,21,"",false,null,false,()->{model.draft.undo();model.cancel();model.selected=-1;rebuildWidgets();}).icon(EditorIcon.action(EditorIcon.Action.UNDO)).setTooltip(Tooltip.create(Component.literal("撤销 / Ctrl+Z")));
        widget(184,31,24,21,"",false,null,false,()->{model.draft.redo();model.cancel();model.selected=-1;rebuildWidgets();}).icon(EditorIcon.action(EditorIcon.Action.REDO)).setTooltip(Tooltip.create(Component.literal("重做 / Ctrl+Y")));
        int tab=width-layout.right()+6,tabWidth=(layout.right()-15)/2;
        widget(tab,31,tabWidth,21,"设置",!model.components,null,false,()->{model.components=false;model.parameterScroll=0;rebuildWidgets();});
        widget(tab+tabWidth+3,31,tabWidth,21,"蓝图簿",false,null,false,()->{model.cancel();BookClient.openNotebook();});
        widget(width-48,5,40,18,"关闭",false,null,false,this::onClose);
        buildPalette();
        buildParameters(tab,layout.top()+(model.selected()==null?0:40)-model.parameterScroll,layout.right()-12);
        int maxScroll=Math.max(0,contentHeight-(layout.bottom()-parameterTop()-14));
        if(model.parameterScroll>maxScroll){model.parameterScroll=maxScroll;rebuildWidgets();return;}
        int y=height-65,w=Math.min(76,(width-28)/5);
        widget(8,y,w,20,engraving?"完成刻印":"保存",false,null,false,()->send(0,JsonCodecs.blueprint(model.draft.get()))).role(CircuitButton.Role.PRIMARY);
        if(!engraving){widget(11+w,y,w,20,"起阵",false,null,false,()->send(1,"")).role(CircuitButton.Role.PRIMARY);widget(14+2*w,y,w,20,"停供",false,null,false,()->send(2,""));}
        widget(17+3*w,y,w,20,"分享",false,null,false,this::openBlueprints).icon(EditorIcon.action(EditorIcon.Action.EXPORT));
        if(!engraving)widget(20+4*w,y,w,20,"法阵管理",false,null,false,()->manage(0));
    }
    private void buildPalette(){
        List<RuneCatalog.Row> rows=RuneCatalog.rows(model.expanded);int visible=layout.visibleRows();
        model.paletteOffset=Math.max(0,Math.min(model.paletteOffset,Math.max(0,rows.size()-visible)));
        for(int i=0;i<visible && i+model.paletteOffset<rows.size();i++){
            RuneCatalog.Row row=rows.get(i+model.paletteOffset);int y=layout.top()+i*24;
            if(row.parent())widget(8,y,layout.left()-16,22,(model.expanded.contains(row.group())?"▾ ":"▸ ")+row.group().title,false,null,true,()->{model.toggle(row.group());rebuildWidgets();});
            else {
                CircuitButton b=widget(12,y,layout.left()-20,22,row.title(),model.tool==EditorModel.Tool.PLACE && model.kind==row.kind() && (row.element()==null || model.element==row.element()),row.kind(),false,()->{model.choose(row.kind(),row.element());rebuildWidgets();}).icon(EditorIcon.rune(row.kind(),row.element()));
                b.setTooltip(Tooltip.create(Component.literal(row.title()+"\n"+RuneCatalog.description(row.kind())+"\n点击选择，在格盘空格放置")));
            }
        }
    }
    private void buildParameters(int x,int y,int w){
        Rune rune=model.selected();if(rune==null){
        button(x,y,w,model.draft.get().continuous()?"供给：持续":"供给：单次",EditorIcon.rune(Rune.Kind.INPUT),()->{Blueprint b=model.draft.get();model.draft.set(new Blueprint(1,b.rules(),b.runes(),b.routes(),!b.continuous()));rebuildWidgets();});
        if(!engraving)button(x,y+24,w,"基板："+materialTitle(model.draft.get().rules().material()),EditorIcon.action(EditorIcon.Action.PLATE),()->manage(2));
        button(x,y+48,w,"火焰光束",EditorIcon.rune(Rune.Kind.BEAM),()->example(Rune.Kind.BEAM));button(x,y+72,w,"火球",EditorIcon.rune(Rune.Kind.SPHERE),()->example(Rune.Kind.SPHERE));button(x,y+96,w,"火焰屏障",EditorIcon.rune(Rune.Kind.SHIELD),()->example(Rune.Kind.SHIELD));
        button(x,y+120,w,"火水交替",EditorIcon.rune(Rune.Kind.COUNTER),()->{model.draft.set(LogicExamples.alternating(model.draft.get().rules(),LogicResources.defaults()));model.selected=-1;model.cancel();model.message="火与水每4拍切换一次。保存后起阵";rebuildWidgets();});
        contentHeight=144;
        button(x,y+144,w,"追踪弹跳",EditorIcon.rune(Rune.Kind.BURST),()->{model.draft.set(ProjectileExamples.combined(model.draft.get().rules(),LogicResources.defaults(),ProjectileResources.rules()));model.cancel();model.message="会追踪、弹跳的爆裂弹。选中符文可修改效果";rebuildWidgets();});button(x,y+168,w,"分裂连锁",EditorIcon.rune(Rune.Kind.FRAGMENT),()->{model.draft.set(ProjectileExamples.secondary(model.draft.get().rules(),LogicResources.defaults(),ProjectileResources.rules(),SecondaryResources.rules()));model.cancel();model.message="命中后分裂并连锁，附带灼烧、迟缓和击退";rebuildWidgets();});button(x,y+192,w,"环境光束",EditorIcon.rune(Rune.Kind.ABSORB),()->{model.draft.set(ManaFieldExamples.beam(model.draft.get().rules()));model.selected=-1;model.cancel();model.message="从周围魔力场汲取供能，节点附近更充裕";rebuildWidgets();});contentHeight=216;
        var shapes=new Rune.Kind[]{Rune.Kind.FAN,Rune.Kind.WALL,Rune.Kind.RING,Rune.Kind.COLUMN,Rune.Kind.CROSS,Rune.Kind.DOME};
        for(int i=0;i<shapes.length;i++){var shape=shapes[i];button(x,y+216+i*24,w,RuneCatalog.title(shape),EditorIcon.rune(shape),()->example(shape));}contentHeight=360;return;}int p=y;
        if(rune.kind()==Rune.Kind.CONVERT){button(x,p,w,"元素："+rune.element().title,EditorIcon.rune(Rune.Kind.CONVERT,rune.element()),()->{Element[] elements=Element.values();model.draft.replace(new Rune(rune.id(),rune.kind(),rune.cell(),elements[(rune.element().ordinal()+1)%elements.length],rune.value(),rune.on(),rune.signalControl(),rune.weights()));rebuildWidgets();});p+=24;}
        if(rune.kind()==Rune.Kind.SWITCH || rune.kind()==Rune.Kind.SIGNAL || rune.kind()==Rune.Kind.TOGGLE){button(x,p,w,"初始："+(rune.on()?"开":"关"),EditorIcon.rune(Rune.Kind.SWITCH),()->{model.draft.replace(new Rune(rune.id(),rune.kind(),rune.cell(),rune.element(),rune.value(),!rune.on(),rune.signalControl(),rune.weights()));rebuildWidgets();});p+=24;}
        if(rune.kind()==Rune.Kind.SWITCH){
            button(x,p,w,rune.signalControl()?"控制：信号":"控制：手动",EditorIcon.rune(Rune.Kind.SWITCH),()->{model.draft.replace(new Rune(rune.id(),rune.kind(),rune.cell(),rune.element(),rune.value(),rune.on(),!rune.signalControl(),rune.weights()));rebuildWidgets();});p+=24;
            button(x,p,w,"切换开关",EditorIcon.action(EditorIcon.Action.APPLY),()->send(3,Integer.toString(rune.id())));p+=24;
        }
        if(EnumSet.of(Rune.Kind.SENSOR,Rune.Kind.AMOUNT,Rune.Kind.RANGE,Rune.Kind.RADIUS,Rune.Kind.AIM,Rune.Kind.SPLIT,Rune.Kind.COUNTER,Rune.Kind.CLOCK,Rune.Kind.BURST,Rune.Kind.BOUNCE,Rune.Kind.HOMING,Rune.Kind.FRAGMENT,Rune.Kind.CHAIN,Rune.Kind.IGNITE,Rune.Kind.SLOW,Rune.Kind.KNOCKBACK).contains(rune.kind())){
            parameter=new EditBox(font,x,p,w,20,Component.literal("符文参数"));parameter.setMaxLength(256);
            parameter.setValue(rune.kind()==Rune.Kind.SPLIT?rune.weights().toString().replace("[","").replace("]","").replace(" ",""):BigDecimal.valueOf(rune.value()).divide(BigDecimal.valueOf(Fixed.ONE)).stripTrailingZeros().toPlainString());
            if(p>=parameterTop() && p+20<=layout.bottom()-14)addRenderableWidget(parameter);p+=24;
            button(x,p,w,rune.kind()==Rune.Kind.COUNTER?"应用计数阈值":rune.kind()==Rune.Kind.CLOCK?"设置周期（拍）":rune.kind()==Rune.Kind.BURST?"设置半径（格）":rune.kind()==Rune.Kind.BOUNCE?"应用弹跳次数":rune.kind()==Rune.Kind.HOMING?"设置追踪范围（格）":rune.kind()==Rune.Kind.FRAGMENT?"应用分裂数量":rune.kind()==Rune.Kind.CHAIN?"应用连锁跳数":rune.kind()==Rune.Kind.IGNITE || rune.kind()==Rune.Kind.SLOW?"设置时长（秒）":rune.kind()==Rune.Kind.KNOCKBACK?"应用击退等级":"应用参数",EditorIcon.action(EditorIcon.Action.APPLY),()->{
                long value=rune.value();List<Integer> weights=rune.weights();
                if(rune.kind()==Rune.Kind.SPLIT){weights=new ArrayList<>();for(String part:parameter.getValue().split(",",-1))weights.add(Integer.parseInt(part.trim()));}
                else value=new BigDecimal(parameter.getValue()).multiply(BigDecimal.valueOf(Fixed.ONE)).longValueExact();
                model.draft.replace(new Rune(rune.id(),rune.kind(),rune.cell(),rune.element(),value,rune.on(),rune.signalControl(),weights));model.message="参数已修改，记得保存";
            });p+=24;
        }
        if(RuneLogic.sequential(rune.kind())){button(x,p,w,"复位",EditorIcon.action(EditorIcon.Action.UNDO),()->send(13,Integer.toString(rune.id())));p+=24;}
        button(x,p,w,"删除符文",EditorIcon.action(EditorIcon.Action.DELETE),()->{model.draft.delete(model.selected);model.selected=-1;model.cancel();rebuildWidgets();});p+=24;
        button(x,p,w,"删除关联线路",EditorIcon.action(EditorIcon.Action.ROUTE),()->{Blueprint b=model.draft.get();model.draft.set(new Blueprint(1,b.rules(),b.runes(),b.routes().stream().filter(r->r.source()!=model.selected && r.target()!=model.selected).toList(),b.continuous()));model.message="已删除相关连线，记得保存";});
        contentHeight=p-y+24;
    }
    private void send(int action,String text){managementRequest(slot,action,text);}
    @Override public void tick(){super.tick();if(requests.tick())model.message="暂未收到回复，结果未知。请稍候刷新，避免重复消耗材料";if(minecraft.player!=null && !requests.waiting() && ++refreshTicks>=(model.selected()!=null && RuneLogic.sequential(model.selected().kind())?5:20)){refreshTicks=0;send(5,"");}}
    private void example(Rune.Kind shape){
        model.draft.set(ShapeExamples.create(model.draft.get().rules(),shape));model.selected=-1;model.cancel();model.message="示例已载入，保存后即可起阵";rebuildWidgets();
    }
    @Override public boolean mouseClicked(double x,double y,int mouse){
        if(super.mouseClicked(x,y,mouse))return true;
        if(mouse==1){model.cancel();boardPress=false;return true;}
        Hex cell=layout.cell(x,y);if(cell==null)return false;
        if(model.components)return true;
        if(mouse==0){boardPress=model.tool==EditorModel.Tool.SELECT;pressX=x;pressY=y;attempt(()->{model.click(cell);if(!boardPress)rebuildWidgets();});}return true;
    }
    @Override public void mouseMoved(double x,double y){super.mouseMoved(x,y);updateCursor(x,y);}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){
        if(boardPress && button==0 && !model.components){if(!model.dragging() && Math.hypot(x-pressX,y-pressY)>3)model.beginDrag();return true;}
        if(!model.components && model.tool==EditorModel.Tool.ROUTE && model.drawing() && button==0){Hex h=layout.cell(x,y);if(h!=null && model.draft.at(h)==null)attempt(()->model.click(h));return true;}
        return super.mouseDragged(x,y,button,dx,dy);
    }
    @Override public boolean mouseReleased(double x,double y,int button){
        if(button==0 && boardPress){boardPress=false;if(model.dragging())attempt(()->model.drop(layout.cell(x,y)));rebuildWidgets();return true;}
        return super.mouseReleased(x,y,button);
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double delta){
        if(x<layout.left()){model.paletteOffset+=delta>0?-1:1;rebuildWidgets();return true;}
        if(x>width-layout.right()){model.parameterScroll=Math.max(0,Math.min(Math.max(0,contentHeight-(layout.bottom()-layout.top()-14)),model.parameterScroll+(delta>0?-24:24)));rebuildWidgets();return true;}
        return super.mouseScrolled(x,y,horizontal,delta);
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(key==GLFW.GLFW_KEY_ESCAPE && (model.drawing() || model.dragging())){model.cancel();boardPress=false;return true;}
        if(!(getFocused() instanceof EditBox)){
            if(key==GLFW.GLFW_KEY_DELETE && model.selected>0){attempt(()->{model.draft.delete(model.selected);model.selected=-1;model.cancel();rebuildWidgets();});return true;}
            if(hasControlDown() && (key==GLFW.GLFW_KEY_Z || key==GLFW.GLFW_KEY_Y)){if(key==GLFW.GLFW_KEY_Z)model.draft.undo();else model.draft.redo();model.selected=-1;model.cancel();rebuildWidgets();return true;}
        }
        return super.keyPressed(key,scan,modifiers);
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){if(minecraft.player!=null)CircuitNetwork.request(identity,slot,14,"");super.onClose();}
    // Screen.render invokes the vanilla background in 1.21; our renderer already painted it.
    @Override public void renderBackground(GuiGraphics g,int x,int y,float partial){}
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){
        String status=(engraving?materialTitle(model.draft.get().rules().material())+"基板 · 刻印草稿":fixed?"固定法阵":book?"法术书":"法杖")+" / 阵 "+slot+" · "+(powered?"运行":"停供");
        if(held>0)status+=" · 余量 "+Fixed.display(held);
        if(!model.draft.get().equals(serverBlueprint))status+=" · 未保存";
        final String displayStatus=status;
        EditorModel.View view=model.view(layout.cell(mouseX,mouseY));
        // In 1.20.1 each unmanaged fill flushes its buffer. Batch board strokes together;
        // scissor changes inside paint flush at the required boundaries automatically.
        g.drawManaged(()->{
            EditorRenderer.paint(g,font,layout,view,displayStatus,logicStatus());
        });
        super.render(g,mouseX,mouseY,partial);
        boolean custom=!model.components && layout.onBoard(mouseX,mouseY);
        updateCursor(mouseX,mouseY);if(custom)EditorRenderer.pointerHint(g,font,view,mouseX,mouseY);
    }
    private String logicStatus(){
        Rune selected=model.selected();if(selected==null || !RuneLogic.sequential(selected.kind()))return "";
        var state=logic.find(selected.id());if(state==null || state.kind()!=selected.kind())return "未保存";
        return switch(state.kind()){case COUNTER->"计数 "+state.count()+"/"+state.parameter();case CLOCK->"相位 "+state.phase()+"/"+state.parameter();case TOGGLE->"当前 / "+(state.latched()?"开":"关");default->"";};
    }
    private void updateCursor(double x,double y){
        if(nativeCursor==null || layout==null)return;
        if(!model.components && layout.onBoard(x,y))nativeCursor.show(model.view(layout.cell(x,y)),(int)Math.ceil(minecraft.getWindow().getGuiScale()));
        else nativeCursor.restore();
    }
    @Override public void removed(){model.cancel();boardPress=false;if(nativeCursor!=null){nativeCursor.close();nativeCursor=null;}super.removed();}
    private static String materialTitle(Rules.Material m){return switch(m){case WOOD->"木质";case BRASS->"黄铜";case CRYSTAL->"水晶";};}
}
