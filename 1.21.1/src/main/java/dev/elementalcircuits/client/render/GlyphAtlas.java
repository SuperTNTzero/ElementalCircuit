package dev.elementalcircuits.client.render;

import dev.elementalcircuits.client.editor.RuneShapes;
import dev.elementalcircuits.domain.WandParts;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** One shared texture/render type. Tint and transforms are per-quad, without texture rebinding. */
public final class GlyphAtlas {
    private static final RenderType TYPE=RenderType.text(ResourceLocation.fromNamespaceAndPath("elementalcircuits","textures/gui/glyphs.png"));
    private static final RenderType WANDS=RenderType.text(ResourceLocation.fromNamespaceAndPath("elementalcircuits","textures/gui/wands.png"));
    private GlyphAtlas(){}
    public static void draw(GuiGraphics g,String key,double x,double y,double radius,int color){
        var glyph=RuneShapes.glyph(key);int cell=RuneShapes.CELL;
        float u=(glyph.index()%RuneShapes.COLUMNS)*cell/(float)RuneShapes.WIDTH,v=(glyph.index()/RuneShapes.COLUMNS)*cell/(float)RuneShapes.HEIGHT;
        float du=cell/(float)RuneShapes.WIDTH,dv=cell/(float)RuneShapes.HEIGHT;
        float half=(float)(radius*cell/(cell-2.0*RuneShapes.PADDING));
        quad(g,TYPE,(float)x-half,(float)y-half,(float)x+half,(float)y+half,u,v,du,dv,color);
    }
    public static void wand(GuiGraphics g,int x,int y,int height,WandParts.Assembly a,WandParts d){
        int index=d.in(WandParts.Slot.HEAD).indexOf(d.get(a.head()))*9+d.in(WandParts.Slot.BODY).indexOf(d.get(a.body()))*3+d.in(WandParts.Slot.TAIL).indexOf(d.get(a.tail()));
        // Preserve the sprite's square aspect ratio, including enlarged assembly previews.
        quad(g,WANDS,x-height/2f,y,x+height/2f,y+height,(index%8)/8f,(index/8)/4f,1/8f,1/4f,0xffffffff);
    }
    private static void quad(GuiGraphics g,RenderType type,float x1,float y1,float x2,float y2,float u,float v,float du,float dv,int color){
        var matrix=g.pose().last().pose();var out=g.bufferSource().getBuffer(type);
        int r=color>>16&255,b=color&255,green=color>>8&255,a=color>>>24;
        out.addVertex(matrix,x1,y2,0).setColor(r,green,b,a).setUv(u,v+dv).setLight(15728880);
        out.addVertex(matrix,x2,y2,0).setColor(r,green,b,a).setUv(u+du,v+dv).setLight(15728880);
        out.addVertex(matrix,x2,y1,0).setColor(r,green,b,a).setUv(u+du,v).setLight(15728880);
        out.addVertex(matrix,x1,y1,0).setColor(r,green,b,a).setUv(u,v).setLight(15728880);
    }
}
