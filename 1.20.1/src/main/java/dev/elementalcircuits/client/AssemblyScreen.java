package dev.elementalcircuits.client;

import dev.elementalcircuits.application.observation.AssemblyInspection;
import dev.elementalcircuits.adapter.JsonCodecs;
import dev.elementalcircuits.client.editor.*;
import dev.elementalcircuits.client.render.*;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.network.CircuitNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Widget/network adapter for slots, links and explicit material installation. */
public final class AssemblyScreen extends Screen {
    private record Row(String text,boolean selected,EditorIcon icon,Runnable action){ }
    private final CircuitScreen parent;
    private final AssemblyPageModel model;
    private int left,span,visible,total;
    private String hint,error="";
    public AssemblyScreen(CircuitScreen parent,int page){super(Component.literal("法阵管理"));this.parent=parent;model=new AssemblyPageModel(page);}
    private void button(int x,int y,int w,String text,boolean selected,EditorIcon icon,Runnable action){
        CircuitButton b=new CircuitButton(x,y,w,22,font.plainSubstrByWidth(text,Math.max(5,w-(icon==null?11:32))),selected,null,false,()->{try{error="";action.run();}catch(RuntimeException e){error=e.getMessage()==null?"操作失败":e.getMessage();}}).icon(icon);
        b.setTooltip(Tooltip.create(Component.literal(text)));addRenderableWidget(b);
    }
    private void row(List<Row> rows,String text,EditorIcon icon,Runnable action){rows.add(new Row(text,false,icon,action));}
    @Override protected void init(){
        left=Math.max(8,(width-560)/2);span=width-2*left;visible=Math.max(1,(height-142)/24);
        String[] tabs={"法阵","互联","基板"};int tab=(span-8)/3;
        for(int i=0;i<3;i++){final int page=i;button(left+i*(tab+4),31,tab,tabs[i],model.page==i,null,()->{model.page=page;model.offset=0;rebuildWidgets();});}
        var data=parent.inspection();List<Row> rows=new ArrayList<>();
        if(model.page==0){
            hint="每个法阵独立起停；滚轮浏览。法杖右键起停选中阵。";
            for(var s:data.slots()){
                rows.add(new Row("选择法阵 "+s.id()+" / "+material(s.material())+(s.powered()?" / 运行":" / 停供")+" / 缓存 "+Fixed.display(s.held()),s.id()==data.selected(),null,()->parent.managementRequest(parent.slot(),8,Integer.toString(s.id()))));
                row(rows,"法阵 "+s.id()+"："+(s.powered()?"停止供给":"手动起阵"),EditorIcon.action(EditorIcon.Action.APPLY),()->parent.managementRequest(s.id(),s.powered()?2:1,""));
            }
            if(data.slots().size()<data.maxSlots())for(var m:Rules.Material.values())row(rows,"新增 / "+material(m)+"基板 ×1（持有 "+stock(data,m)+"）",EditorIcon.action(EditorIcon.Action.PLATE),()->parent.managementRequest(parent.slot(),9,m.name()));
            if(data.fixed())row(rows,"充能 +16 / 储量 "+Fixed.display(data.charge())+" / "+Fixed.display(data.capacity()),EditorIcon.rune(Rune.Kind.INPUT),()->parent.managementRequest(parent.slot(),12,""));
        }else if(model.page==1){
            hint="先保存收发符文并停止两端；魔力在途时不可断线。";
            row(rows,"载荷 / "+(model.payload==Route.Payload.MANA?"魔力":"信号"),EditorIcon.action(EditorIcon.Action.ROUTE),()->{model.toggle();rebuildWidgets();});
            var from=model.selected(data,true);var to=model.selected(data,false);
            row(rows,"发送 / "+(from==null?"未放置发送符文":from.label()),EditorIcon.rune(model.payload==Route.Payload.MANA?Rune.Kind.MANA_SEND:Rune.Kind.SIGNAL_SEND),()->{model.cycle(data,true);rebuildWidgets();});
            row(rows,"接收 / "+(to==null?"未放置接收符文":to.label()),EditorIcon.rune(model.payload==Route.Payload.MANA?Rune.Kind.MANA_RECEIVE:Rune.Kind.SIGNAL_RECEIVE),()->{model.cycle(data,false);rebuildWidgets();});
            row(rows,"建立连接 / 魔力保留99%，至少延后一世界步",EditorIcon.action(EditorIcon.Action.APPLY),()->{
                if(from==null || to==null)throw new IllegalArgumentException("请先在两个法阵中放置并保存对应收发符文");
                parent.managementRequest(parent.slot(),10,JsonCodecs.linkRequest(new JsonCodecs.LinkRequest(from.address(),to.address(),model.payload)));
            });
            for(var l:data.links())row(rows,"断开 / "+(l.external()?"外部 ":"内部 ")+l.label()+" / 在途 "+Fixed.display(l.held()),EditorIcon.action(EditorIcon.Action.DELETE),()->parent.managementRequest(parent.slot(),11,(l.external()?"W:":"I:")+l.id()));
        }else{
            hint="直接选择目标基板；保存当前草稿并回收旧板。";
            for(var m:Rules.Material.values()){
                boolean installed=parent.draftForExport().rules().material()==m;
                rows.add(new Row((installed?"当前 / ":"安装 / ")+material(m)+"基板（持有 "+stock(data,m)+"）",installed,EditorIcon.action(EditorIcon.Action.PLATE),()->parent.installMaterial(m)));
            }
        }
        total=rows.size();model.offset=Math.max(0,Math.min(model.offset,Math.max(0,total-visible)));
        for(int i=0;i<visible && i+model.offset<total;i++){var row=rows.get(i+model.offset);button(left,76+i*24,span-24,row.text,row.selected,row.icon,row.action);}
        if(total>visible){button(left+span-20,76,20,"↑",false,null,()->{model.offset--;rebuildWidgets();});button(left+span-20,height-90,20,"↓",false,null,()->{model.offset++;rebuildWidgets();});}
        button(left+span-58,height-31,58,"返回",false,null,this::onClose);
    }
    public void accept(CircuitNetwork.Editor packet){parent.accept(packet);if(minecraft!=null)rebuildWidgets();}
    @Override public void tick(){parent.tick();}
    @Override public boolean mouseScrolled(double x,double y,double delta){model.offset+=delta>0?-1:1;rebuildWidgets();return true;}
    @Override public void render(GuiGraphics g,int x,int y,float partial){g.drawManaged(()->AssemblyRenderer.paint(g,font,width,height,left,span,hint,error.isEmpty()?parent.message():error));super.render(g,x,y,partial);}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
    private static String material(Rules.Material m){return switch(m){case WOOD->"木头";case BRASS->"黄铜";case CRYSTAL->"水晶";};}
    private static int stock(AssemblyInspection d,Rules.Material m){return d.stock().stream().filter(s->s.material()==m).mapToInt(AssemblyInspection.Stock::count).sum();}
}
