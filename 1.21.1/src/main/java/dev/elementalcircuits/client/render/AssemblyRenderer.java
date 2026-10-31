package dev.elementalcircuits.client.render;

import net.minecraft.client.gui.*;
import net.minecraft.network.chat.Component;

/** Read-only management page painting. */
public final class AssemblyRenderer {
    private AssemblyRenderer(){ }
    public static void paint(GuiGraphics g,Font font,int width,int height,int left,int span,String hint,String message){
        WorkshopSkin.background(g,width,height);WorkshopSkin.panel(g,left-4,28,span+8,height-91,WorkshopSkin.PANEL);
        WorkshopSkin.title(g,font,"法阵管理",left,10);
        g.drawString(font,font.plainSubstrByWidth(hint,span),left,59,EditorRenderer.MUTED,false);
        var lines=font.split(Component.literal(message==null?"":message),Math.max(40,span-62));
        for(int i=0;i<Math.min(3,lines.size());i++)g.drawString(font,lines.get(i),left,height-52+i*10,EditorRenderer.BLUE,false);
    }
}
