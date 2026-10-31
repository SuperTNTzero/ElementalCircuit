package dev.elementalcircuits.client.render;

import dev.elementalcircuits.client.editor.BlueprintTransferLayout;
import net.minecraft.client.gui.*;
import net.minecraft.network.chat.Component;

/** Read-only subpage painting, separate from validation, clipboard and network. */
public final class BlueprintRenderer {
    private BlueprintRenderer(){ }
    public static void paint(GuiGraphics g,Font font,BlueprintTransferLayout l,boolean exporting,String message){
        int x=l.left(),w=l.boxWidth();g.fill(0,0,l.width(),l.height(),0xff0b1018);
        g.fill(x-4,28,x+w+4,l.height()-43,0xff121e2b);
        g.drawString(font,"刻印工坊  /  蓝图",x,10,EditorRenderer.GOLD,false);
        String hint=exporting?"生成当前草稿的文本码，并复制到剪贴板。":"输入或粘贴文本码，校验后载入当前草稿。";
        g.drawString(font,font.plainSubstrByWidth(hint,w),x,61,EditorRenderer.TEXT,false);
        g.drawString(font,font.plainSubstrByWidth("蓝图只含法阵配置；导入后需返回保存。",w),x,75,EditorRenderer.MUTED,false);
        int y=l.top()+l.boxHeight()+18;
        var lines=font.split(Component.literal(message==null?"":message),w);
        for(int i=0;i<Math.min(2,lines.size());i++)g.drawString(font,lines.get(i),x,y+i*10,EditorRenderer.BLUE,false);
    }
}
