package dev.elementalcircuits.client.render;
import dev.elementalcircuits.client.editor.BookLayout;
import dev.elementalcircuits.domain.*;
import net.minecraft.client.gui.*;

/** Book and workbench painting only; never modifies a draft or running circuit. */
public final class BookRenderer {
    private BookRenderer(){ }
    public static void frame(GuiGraphics g,Font f,int width,int height,String title,String message){g.fill(0,0,width,height,0xfa0b111a);g.fill(8,27,width-8,28,0xff354a5d);g.drawString(f,title,10,10,EditorRenderer.GOLD,false);g.drawString(f,f.plainSubstrByWidth(message==null?"":message,width-20),10,height-14,EditorRenderer.BLUE,false);}
    public static void preview(GuiGraphics g,Font f,BookLayout l,Blueprint b){
        int x=l.previewX(),y=l.previewY(),w=l.previewWidth(),h=l.previewHeight();g.fill(x,y,x+w,y+h,0xff121f2b);g.enableScissor(x,y,x+w,y+h);
        if(b==null){g.drawString(f,"尚未保存蓝图",x+9,y+12,EditorRenderer.MUTED,false);g.disableScissor();return;}
        int r=b.rules().boardRadius();double s=l.size(r);
        for(int q=-r;q<=r;q++)for(int t=-r;t<=r;t++){Hex cell=new Hex(q,t);if(cell.inside(r))EditorRenderer.hexagon(g,l.x(cell,r),l.y(cell,r),s*.93,0xff2a3b4a);}
        for(var route:b.routes()){var path=route.path();for(int i=1;i<path.size();i++)EditorRenderer.line(g,l.x(path.get(i-1),r),l.y(path.get(i-1),r),l.x(path.get(i),r),l.y(path.get(i),r),route.payload()==Route.Payload.MANA?EditorRenderer.GOLD:EditorRenderer.BLUE);}
        for(var n:b.runes()){double nx=l.x(n.cell(),r),ny=l.y(n.cell(),r);g.fill((int)(nx-s*.6),(int)(ny-s*.6),(int)(nx+s*.6+1),(int)(ny+s*.6+1),0xff121f2b);EditorRenderer.hexagon(g,nx,ny,s*.8,0xff6e8b9e);EditorRenderer.rune(g,n,nx,ny,s*.54,n.kind()==Rune.Kind.CONVERT?0xff000000|n.element().color:EditorRenderer.TEXT);}
        g.disableScissor();
    }
    public static void workshop(GuiGraphics g,Font f,int width,int height,int left,int span,WorkshopInspection v,WandParts defs,WandParts.Assembly preview){
        frame(g,f,width,height,"法杖工作台 / 部件组装",v.message());int stick=24;EditorRenderer.wand(g,left+13,45,stick,preview,defs);
        var stats=preview.stats(defs);String[] text={"强度 ×"+Fixed.display(stats.strength())+" / 速度 ×"+Fixed.display(stats.speed()),"耐久 "+Math.max(0,stats.durability()-v.wear())+" / "+stats.durability()+" / 近战 +"+stats.melee()};
        for(int i=0;i<text.length;i++)g.drawString(f,f.plainSubstrByWidth(text[i],span-40),left+32,42+i*13,EditorRenderer.TEXT,false);
    }
}
