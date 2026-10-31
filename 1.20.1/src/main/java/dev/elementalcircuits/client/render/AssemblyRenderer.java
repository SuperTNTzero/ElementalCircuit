package dev.elementalcircuits.client.render;

import net.minecraft.client.gui.*;
import net.minecraft.network.chat.Component;

/** Read-only management page painting. */
public final class AssemblyRenderer {
    private AssemblyRenderer(){ }
    public static void paint(GuiGraphics g,Font font,int width,int height,int left,int span,String hint,String message){
        g.fill(0,0,width,height,0xff0b1018);g.fill(left-4,28,left+span+4,height-63,0xff121e2b);
        g.drawString(font,"刻印工坊  /  法阵管理",left,10,EditorRenderer.GOLD,false);
        g.drawString(font,font.plainSubstrByWidth(hint,span),left,59,EditorRenderer.MUTED,false);
        var lines=font.split(Component.literal(message==null?"":message),Math.max(40,span-62));
        for(int i=0;i<Math.min(3,lines.size());i++)g.drawString(font,lines.get(i),left,height-52+i*10,EditorRenderer.BLUE,false);
    }
}
