package dev.elementalcircuits.client.render;

import dev.elementalcircuits.client.editor.BlueprintTransferLayout;
import net.minecraft.client.gui.*;
import net.minecraft.network.chat.Component;

/** Read-only subpage painting, separate from validation, clipboard and network. */
public final class BlueprintRenderer {
    private BlueprintRenderer(){ }
    public static void paint(GuiGraphics g,Font font,BlueprintTransferLayout l,boolean exporting,String message){
        int x=l.left(),w=l.boxWidth();WorkshopSkin.background(g,l.width(),l.height());
        WorkshopSkin.panel(g,x-4,28,w+8,l.height()-71,WorkshopSkin.PANEL);
        WorkshopSkin.title(g,font,"分享蓝图",x,10);
        String hint=exporting?"复制这张法阵的蓝图码，分享给其他玩家。":"粘贴蓝图码，载入这张法阵。";
        g.drawString(font,font.plainSubstrByWidth(hint,w),x,61,EditorRenderer.TEXT,false);
        g.drawString(font,font.plainSubstrByWidth("载入后记得保存。蓝图不携带魔力或运行进度。",w),x,75,EditorRenderer.MUTED,false);
        int y=l.top()+l.boxHeight()+18;
        var lines=font.split(Component.literal(message==null?"":message),w);
        for(int i=0;i<Math.min(2,lines.size());i++)g.drawString(font,lines.get(i),x,y+i*10,EditorRenderer.BLUE,false);
    }
}
