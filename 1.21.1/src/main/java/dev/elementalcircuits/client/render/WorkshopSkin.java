package dev.elementalcircuits.client.render;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** Static nine-slice artwork. Only draws; owns no widget, input or gameplay state. */
public final class WorkshopSkin {
    public static final int BACK=0, PANEL=1, BOARD=2, BUTTON=3, HOVER=4, SELECTED=5, CATEGORY=6, DISABLED=7, BOOK=8;
    public static final int GOLD=0xffd9ba7a, TEXT=0xffe4dfcf, MUTED=0xffa6aea3, TEAL=0xff9ac7ba;
    private static final RenderType ATLAS=RenderType.text(ResourceLocation.fromNamespaceAndPath("elementalcircuits","textures/gui/workshop.png"));
    private static final RenderType SEAL=RenderType.text(ResourceLocation.fromNamespaceAndPath("elementalcircuits","textures/gui/engraving.png"));
    private WorkshopSkin(){}
    public static void panel(GuiGraphics g,int x,int y,int w,int h,int tile){
        if(w<=0 || h<=0)return;
        int edge=Math.min(4,Math.min(w/2,h/2)),u=tile%4*64,v=tile/4*64;
        int[] xs={x,x+edge,x+w-edge,x+w},ys={y,y+edge,y+h-edge,y+h};
        int[] us={u,u+4,u+60,u+64},vs={v,v+4,v+60,v+64};
        for(int row=0;row<3;row++)for(int col=0;col<3;col++)
            quad(g,ATLAS,xs[col],ys[row],xs[col+1],ys[row+1],us[col]/256f,vs[row]/256f,us[col+1]/256f,vs[row+1]/256f,0xffffffff);
    }
    public static void background(GuiGraphics g,int w,int h){g.fill(0,0,w,h,0xff151a1b);g.fill(0,0,w,26,0xff1e2525);g.fill(8,25,w-8,26,0xff6c6046);}
    public static void title(GuiGraphics g,Font f,String text,int x,int y){
        GlyphAtlas.draw(g,"shape/HEX",x+6,y+4,5,GOLD);g.drawString(f,text,x+18,y,TEXT,false);
    }
    public static void seal(GuiGraphics g,double cx,double cy,double r){quad(g,SEAL,(float)(cx-r),(float)(cy-r),(float)(cx+r),(float)(cy+r),0,0,1,1,0xffffffff);}
    private static void quad(GuiGraphics g,RenderType type,float x1,float y1,float x2,float y2,float u1,float v1,float u2,float v2,int color){
        var m=g.pose().last().pose();var out=g.bufferSource().getBuffer(type);
        out.addVertex(m,x1,y2,0).setColor(color).setUv(u1,v2).setLight(15728880);
        out.addVertex(m,x2,y2,0).setColor(color).setUv(u2,v2).setLight(15728880);
        out.addVertex(m,x2,y1,0).setColor(color).setUv(u2,v1).setLight(15728880);
        out.addVertex(m,x1,y1,0).setColor(color).setUv(u1,v1).setLight(15728880);
    }
}
