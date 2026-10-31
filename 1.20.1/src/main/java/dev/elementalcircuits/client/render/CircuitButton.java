package dev.elementalcircuits.client.render;

import dev.elementalcircuits.domain.Rune;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Widget skin only. Actions are supplied by the screen; icons reuse board artwork. */
public final class CircuitButton extends Button {
    private final boolean selected;
    private EditorIcon icon;
    private final boolean parent;
    public CircuitButton(int x,int y,int width,int height,String text,boolean selected,Rune.Kind icon,boolean parent,Runnable action){
        super(x,y,width,height,Component.literal(text),b->action.run(),DEFAULT_NARRATION);this.selected=selected;this.icon=EditorIcon.rune(icon);this.parent=parent;
    }
    public CircuitButton icon(EditorIcon icon){this.icon=icon;return this;}
    @Override public void renderWidget(GuiGraphics g,int mouseX,int mouseY,float partial){
        g.drawManaged(()->paint(g));
    }
    private void paint(GuiGraphics g){
        int x=getX(),y=getY();boolean hover=isHoveredOrFocused();int accent=selected?EditorRenderer.GOLD:EditorRenderer.BLUE;
        g.fill(x,y,x+width,y+height,selected?0xff304354:hover?0xff26394a:parent?0xff1c2c3b:0xff182633);
        g.fill(x,y+height-1,x+width,y+height,hover || selected?accent:0xff304455);
        if(selected)g.fill(x,y,x+2,y+height,accent);
        if(isFocused())g.renderOutline(x,y,width,height,accent);
        int inset=icon==null?7:28;
        if(icon!=null){EditorRenderer.hexagon(g,x+14,y+height/2.0,9,selected?accent:0xff435c70);EditorRenderer.icon(g,icon,x+14,y+height/2.0,6,icon.element()!=null?0xff000000|icon.element().color:accent);}
        var font=Minecraft.getInstance().font;
        g.drawString(font,font.plainSubstrByWidth(getMessage().getString(),width-inset-4),x+inset,y+(height-8)/2,active?(selected?EditorRenderer.GOLD:EditorRenderer.TEXT):0xff627584,false);
    }
}
