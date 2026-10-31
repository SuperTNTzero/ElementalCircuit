package dev.elementalcircuits.client.render;

import dev.elementalcircuits.client.editor.*;
import dev.elementalcircuits.domain.*;
import net.minecraft.client.gui.*;
import java.util.*;

/** Read-only painting adapter. No network calls, draft mutations, inventory writes or VM advancement. */
public final class EditorRenderer {
    public static final int GOLD=WorkshopSkin.GOLD,TEXT=WorkshopSkin.TEXT,MUTED=WorkshopSkin.MUTED,BLUE=WorkshopSkin.TEAL;
    private EditorRenderer(){ }
    public static void line(GuiGraphics g,double x1,double y1,double x2,double y2,int color){
        double dx=x2-x1,dy=y2-y1,length=Math.hypot(dx,dy);if(length<.001){g.fill((int)x1,(int)y1,(int)x1+1,(int)y1+1,color);return;}
        float nx=(float)(-.5*dy/length),ny=(float)(.5*dx/length);var m=g.pose().last().pose();var out=g.bufferSource().getBuffer(net.minecraft.client.renderer.RenderType.gui());
        int r=color>>16&255,green=color>>8&255,b=color&255,a=color>>>24;
        out.addVertex(m,(float)x1+nx,(float)y1+ny,0).setColor(r,green,b,a);out.addVertex(m,(float)x2+nx,(float)y2+ny,0).setColor(r,green,b,a);
        out.addVertex(m,(float)x2-nx,(float)y2-ny,0).setColor(r,green,b,a);out.addVertex(m,(float)x1-nx,(float)y1-ny,0).setColor(r,green,b,a);
    }
    public static void hexagon(GuiGraphics g,double x,double y,double radius,int color){GlyphAtlas.draw(g,"shape/HEX",x,y,radius,color);}
    public static void rune(GuiGraphics g,Rune.Kind kind,double x,double y,double radius,int color){GlyphAtlas.draw(g,RuneShapes.key(kind,null),x,y,radius,color);}
    public static void rune(GuiGraphics g,Rune node,double x,double y,double radius,int color){GlyphAtlas.draw(g,RuneShapes.key(node.kind(),node.element()),x,y,radius,color);}
    public static void icon(GuiGraphics g,EditorIcon icon,double x,double y,double r,int c){GlyphAtlas.draw(g,icon.rune()!=null?RuneShapes.key(icon.rune(),icon.element()==null?Element.FIRE:icon.element()):"action/"+icon.action().name(),x,y,r,c);}
    private static void route(GuiGraphics g,EditorLayout l,List<Hex> cells,int color){
        for(int i=1;i<cells.size();i++)line(g,l.x(cells.get(i-1)),l.y(cells.get(i-1)),l.x(cells.get(i)),l.y(cells.get(i)),color);
        if(cells.size()<2)return;Hex a=cells.get(cells.size()-2),b=cells.get(cells.size()-1);double x=(l.x(a)+l.x(b))/2,y=(l.y(a)+l.y(b))/2,angle=Math.atan2(l.y(b)-l.y(a),l.x(b)-l.x(a));
        for(double d:new double[]{-.5,.5})line(g,x,y,x-Math.cos(angle+d)*4,y-Math.sin(angle+d)*4,color);
    }
    public static void paint(GuiGraphics g,Font font,EditorLayout l,EditorModel.View v,String status,String logicStatus){
        WorkshopSkin.background(g,l.width(),l.height());
        WorkshopSkin.panel(g,4,l.top()-2,l.left()-8,l.bottom()-l.top()+3,WorkshopSkin.PANEL);
        WorkshopSkin.panel(g,l.width()-l.right()+2,l.top()-2,l.right()-6,l.bottom()-l.top()+3,WorkshopSkin.PANEL);
        WorkshopSkin.panel(g,l.left(),l.top()-2,l.width()-l.left()-l.right(),l.bottom()-l.top()+3,WorkshopSkin.BOARD);
        WorkshopSkin.title(g,font,"法阵刻印",10,9);
        g.drawString(font,font.plainSubstrByWidth(status,l.width()-66),10,l.height()-13,MUTED,false);
        g.enableScissor(l.left(),l.top(),l.width()-l.right(),l.bottom());
        WorkshopSkin.seal(g,l.cx(),l.cy(),Math.min((l.width()-l.left()-l.right())/2.0-5,(l.bottom()-l.top())/2.0-5));
        for(int q=-l.radius();q<=l.radius();q++)for(int r=-l.radius();r<=l.radius();r++){Hex h=new Hex(q,r);if(h.inside(l.radius()))hexagon(g,l.x(h),l.y(h),l.size()*.94,0xff34423e);}
        for(Route wire:v.board().routes())route(g,l,wire.path(),wire.payload()==Route.Payload.MANA?0xffbca16d:BLUE);
        route(g,l,v.preview(),v.valid()?0xfff1dfab:0xffdf7977);
        for(Rune node:v.board().runes()){
            double x=l.x(node.cell()),y=l.y(node.cell());int color=node.kind()==Rune.Kind.CONVERT?0xff000000|node.element().color:TEXT;
            boolean dragging=node.id()==v.dragging();if(dragging)color=(color&0xffffff)|0x55000000;
            // Board and palette share the same pre-baked atlas sprite.
            g.fill((int)(x-l.size()*.55),(int)(y-l.size()*.55),(int)(x+l.size()*.55+1),(int)(y+l.size()*.55+1),0xff181f21);
            if(node.id()==v.selected() && !dragging){g.fill((int)(x-l.size()*.6),(int)(y-l.size()*.6),(int)(x+l.size()*.6),(int)(y+l.size()*.6),0x443c718a);hexagon(g,x,y,l.size()*.95,GOLD);}
            hexagon(g,x,y,l.size()*.8,dragging?0x557c8779:node.id()==v.selected()?GOLD:0xff92957c);rune(g,node,x,y,l.size()*.52,color);
        }
        if(v.ghost()!=null){
            Rune ghost=v.ghost();double x=l.x(ghost.cell()),y=l.y(ghost.cell());int color=v.ghostValid()?(ghost.kind()==Rune.Kind.CONVERT?0xff000000|ghost.element().color:BLUE):0xfff27c79;
            if(v.moved()!=null)for(Route wire:v.moved().routes())if(wire.source()==v.dragging() || wire.target()==v.dragging())route(g,l,wire.path(),0x887dc7df);
            g.fill((int)(x-l.size()*.6),(int)(y-l.size()*.6),(int)(x+l.size()*.6),(int)(y+l.size()*.6),(color&0xffffff)|0x33000000);
            hexagon(g,x,y,l.size()*.91,(color&0xffffff)|0x99000000);rune(g,ghost,x,y,l.size()*.63,(color&0xffffff)|0xaa000000);
        }
        g.disableScissor();
        Rune selected=v.board().runes().stream().filter(r->r.id()==v.selected()).findFirst().orElse(null);
        if(selected!=null && !v.components()){
            int x=l.width()-l.right()+6,y=l.top();WorkshopSkin.panel(g,x,y,l.right()-12,35,WorkshopSkin.CATEGORY);
            int c=selected.kind()==Rune.Kind.CONVERT?0xff000000|selected.element().color:GOLD;
            hexagon(g,x+15,y+17,11,GOLD);rune(g,selected,x+15,y+17,8,c);
            String title=selected.kind()==Rune.Kind.CONVERT?selected.element().title+"转化":RuneCatalog.title(selected.kind());
            g.drawString(font,font.plainSubstrByWidth(title,l.right()-46),x+32,y+6,TEXT,false);
            g.drawString(font,font.plainSubstrByWidth(logicStatus.isEmpty()?"符文 "+selected.id():logicStatus,l.right()-46),x+32,y+20,MUTED,false);
        }
        g.drawString(font,"符文目录 · 滚轮",12,l.bottom()-8,MUTED,false);
        g.drawString(font,"设置 · 滚轮",l.width()-l.right()+8,l.bottom()-8,MUTED,false);
        String hint=switch(v.tool()){case SELECT->"点击选择 · 拖动移动";case PLACE->(v.kind()==Rune.Kind.CONVERT?v.element().title+"转化":RuneCatalog.title(v.kind()))+" · 点击放置";case ROUTE->"点击起点与终点 · 点击空白格转折 · 右键取消";};
        if(!v.ghostLabel().isEmpty())hint=v.ghostLabel()+ (v.ghostValid()?v.dragging()>=0?" · 松开移动":" · 点击放置":"");
        if(v.components())hint="更换部件会回收旧件，法杖磨损保留";
        g.drawString(font,font.plainSubstrByWidth(v.routeHint().isEmpty()?hint:v.routeHint(),l.width()-16),8,l.height()-39,BLUE,false);
        g.drawString(font,font.plainSubstrByWidth(v.message()==null?"":v.message(),l.width()-16),8,l.height()-26,GOLD,false);
    }
    public static void pointerHint(GuiGraphics g,Font font,EditorModel.View v,int x,int y){
        if(v.tool()!=EditorModel.Tool.PLACE && v.dragging()<0)return;
        g.pose().pushPose();g.pose().translate(0,0,500);
        String title=v.dragging()>=0?"移动 "+v.ghostLabel():v.tool()==EditorModel.Tool.PLACE?(v.kind()==Rune.Kind.CONVERT?v.element().title+"转化":RuneCatalog.title(v.kind())):v.tool()==EditorModel.Tool.ROUTE?"布线":"选择 / 拖动";
        if(v.tool()==EditorModel.Tool.PLACE || v.dragging()>=0){g.fill(x+16,y+13,x+22+font.width(title),y+26,0xe5101c29);g.drawString(font,title,x+19,y+16,TEXT,false);}
        g.pose().popPose();
    }
    public static void wand(GuiGraphics g,int x,int y,int height,WandParts.Assembly assembly,WandParts definitions){
        WandPreview.render(g,x,y,height,assembly);
    }
    public static void equipment(GuiGraphics g,Font font,EditorLayout l,WandParts.Assembly assembly,WandParts definitions){
        int x=l.left()+6,y=l.top()+4,w=l.width()-l.left()-l.right()-12,h=l.bottom()-l.top()-8;
        g.fill(x,y,x+w,y+h,0xff172431);g.drawString(font,"法杖结构",x+8,y+8,GOLD,false);
        int stick=Math.min(100,h-31);wand(g,x+18,y+24,stick,assembly,definitions);
        int at=y+26;
        for(WandParts.Slot slot:WandParts.Slot.values()){
            String title=definitions.get(assembly.at(slot)).title();
            g.drawString(font,font.plainSubstrByWidth(title,Math.max(1,w-42)),x+37,at,TEXT,false);at+=Math.max(22,(stick-10)/2);
        }
    }
}
