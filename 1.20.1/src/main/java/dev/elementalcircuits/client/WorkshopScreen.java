package dev.elementalcircuits.client;
import dev.elementalcircuits.adapter.WandResources;
import dev.elementalcircuits.client.render.*;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.network.BookNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class WorkshopScreen extends Screen {
    private WorkshopInspection view;
    private WandParts.Slot tab=WandParts.Slot.HEAD;
    private String chosen="",error="";
    private int left,span,offset;
    public WorkshopScreen(WorkshopInspection v){super(Component.literal("法杖工作台"));view=v;}
    public void accept(WorkshopInspection v){if(!view.identity().equals(v.identity()) || view.position()!=v.position())return;view=v;error="";rebuildWidgets();}
    private void button(int x,int y,int w,String text,boolean selected,Runnable action){var b=new CircuitButton(x,y,w,21,text,selected,null,false,()->{try{action.run();}catch(RuntimeException e){error=e.getMessage()==null?"操作失败":e.getMessage();}});b.setTooltip(Tooltip.create(Component.literal(text)));addRenderableWidget(b);}
    private void request(int action,String part){BookNetwork.send(new BookNetwork.WorkshopRequest(view.identity(),view.position(),action,part));}
    @Override protected void init(){left=Math.max(8,(width-530)/2);span=width-2*left;int tw=(span-8)/3;for(var s:WandParts.Slot.values())button(left+s.ordinal()*(tw+4),76,tw,s.title,tab==s,()->{tab=s;chosen="";offset=0;rebuildWidgets();});var defs=WandResources.definitions();int visible=Math.max(1,(height-158)/23);offset=Math.max(0,Math.min(offset,3-visible));for(int i=offset;i<Math.min(3,offset+visible);i++){var part=defs.in(tab).get(i);String title=part.title()+" ×"+view.stock().getOrDefault(part.id(),0)+(part.id().equals(view.assembly().at(tab))?" / 已装配":" / 预览");button(left,102+(i-offset)*23,span,title,part.id().equals(chosen.isEmpty()?view.assembly().at(tab):chosen),()->{chosen=part.id();rebuildWidgets();});}button(left,height-51,span/2-2,"安装预览部件",false,()->{if(chosen.isEmpty())throw new IllegalArgumentException("先选择目标部件");request(0,chosen);});button(left+span/2+2,height-51,span-span/2-2,"修缮 / 木基板 ×1",false,()->request(1,""));button(width-63,4,55,"关闭",false,this::onClose);}
    @Override public boolean mouseScrolled(double x,double y,double d){offset+=d>0?-1:1;rebuildWidgets();return true;}
    @Override public void render(GuiGraphics g,int x,int y,float p){var defs=WandResources.definitions();var assembly=chosen.isEmpty()?view.assembly():view.assembly().replace(defs.get(chosen));g.drawManaged(()->{BookRenderer.workshop(g,font,width,height,left,span,view,defs,assembly);if(!error.isEmpty())g.drawString(font,font.plainSubstrByWidth(error,width-20),10,height-28,0xffed9685,false);});super.render(g,x,y,p);}
    @Override public boolean isPauseScreen(){return false;}
}
