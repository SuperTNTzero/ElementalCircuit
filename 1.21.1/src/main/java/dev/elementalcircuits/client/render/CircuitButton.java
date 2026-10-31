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
    public enum Role { ACTION, PRIMARY, DANGER }
    private Role role=Role.ACTION;
    public CircuitButton(int x,int y,int width,int height,String text,boolean selected,Rune.Kind icon,boolean parent,Runnable action){
        super(x,y,width,height,Component.literal(text),b->action.run(),DEFAULT_NARRATION);this.selected=selected;this.icon=EditorIcon.rune(icon);this.parent=parent;
        if(!text.isEmpty())setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(text)));
    }
    public CircuitButton icon(EditorIcon icon){this.icon=icon;return this;}
    public CircuitButton role(Role role){this.role=role;return this;}
    @Override public void renderWidget(GuiGraphics g,int mouseX,int mouseY,float partial){
        g.drawManaged(()->paint(g));
    }
    private void paint(GuiGraphics g){
        int x=getX(),y=getY();boolean hover=active && isHoveredOrFocused();int accent=role==Role.DANGER?0xffd79882:selected || role==Role.PRIMARY?WorkshopSkin.GOLD:WorkshopSkin.TEAL;
        WorkshopSkin.panel(g,x,y,width,height,!active?WorkshopSkin.DISABLED:selected || role==Role.PRIMARY?WorkshopSkin.SELECTED:hover?WorkshopSkin.HOVER:parent?WorkshopSkin.CATEGORY:WorkshopSkin.BUTTON);
        if(selected)g.fill(x+2,y+4,x+3,y+height-4,accent);
        if(isFocused())g.renderOutline(x,y,width,height,accent);
        int inset=icon==null?7:26;
        if(icon!=null){EditorRenderer.icon(g,icon,x+13,y+height/2.0,6,icon.element()!=null?0xff000000|icon.element().color:active?accent:0xff70796d);}
        var font=Minecraft.getInstance().font;
        if(width>inset+4)g.drawString(font,font.plainSubstrByWidth(getMessage().getString(),width-inset-4),x+inset,y+(height-8)/2,active?(selected || role==Role.PRIMARY?WorkshopSkin.GOLD:role==Role.DANGER?accent:WorkshopSkin.TEXT):0xff747e72,false);
    }
}
