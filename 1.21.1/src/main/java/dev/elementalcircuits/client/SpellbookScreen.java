package dev.elementalcircuits.client;
import dev.elementalcircuits.client.editor.BookLayout;
import dev.elementalcircuits.client.render.*;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.network.BookNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class SpellbookScreen extends Screen {
    private SpellbookInspection view;
    private BookLayout layout;
    private int preview,refresh;
    public SpellbookScreen(SpellbookInspection v){super(Component.literal("法术书"));view=v;preview=v.selected();}
    public void accept(SpellbookInspection v){if(!view.identity().equals(v.identity()))return;boolean changed=view.selected()!=v.selected();view=v;if(changed || v.slots().stream().noneMatch(s->s.id()==preview))preview=v.selected();rebuildWidgets();}
    private void request(int action,int value){BookNetwork.send(new BookNetwork.SpellRequest(view.identity(),action,value));}
    private void button(int x,int y,int w,String text,boolean selected,Runnable action){addRenderableWidget(new CircuitButton(x,y,w,22,text,selected,null,false,action));}
    @Override protected void init(){layout=BookLayout.of(width,height);for(int i=0;i<view.slots().size();i++){var s=view.slots().get(i);button(layout.left(),36+i*24,layout.list(),"阵 "+s.id()+(s.powered()?" · 运行":" · 停供"),preview==s.id(),()->{preview=s.id();rebuildWidgets();});}int left=layout.left(),span=layout.span(),half=(span-4)/2;button(left,height-76,half,"选用这张法阵",false,()->request(1,preview));button(left+half+4,height-76,span-half-4,view.current().powered()?"停止供给":"起阵",false,()->request(4,view.selected()));var hint=addRenderableWidget(new CircuitButton(left,height-51,half,22,"在法阵工作台刻印",false,null,false,()->{}));hint.active=false;button(left+half+4,height-51,span-half-4,"关闭",false,this::onClose);}
    // Screen.render invokes the vanilla background in 1.21; our renderer already painted it.
    @Override public void renderBackground(GuiGraphics g,int x,int y,float partial){}
    @Override public void render(GuiGraphics g,int x,int y,float p){var slot=view.slots().stream().filter(s->s.id()==preview).findFirst().orElse(view.current());g.drawManaged(()->{BookRenderer.bookFrame(g,font,width,height,"法术书 · 当前法阵 "+view.selected(),view.message(),false);BookRenderer.preview(g,font,layout,slot.blueprint());g.drawString(font,"法阵 "+slot.id()+" · 阵内魔力 "+Fixed.display(slot.held()),layout.previewX()+5,height-89,EditorRenderer.MUTED,false);});super.render(g,x,y,p);}
    @Override public void tick(){if(minecraft.player!=null && ++refresh>=20){refresh=0;request(5,0);}}
    @Override public boolean isPauseScreen(){return false;}
}
