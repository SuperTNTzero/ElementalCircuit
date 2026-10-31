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
    @Override protected void init(){layout=BookLayout.of(width,height);for(int i=0;i<view.slots().size();i++){var s=view.slots().get(i);button(layout.left(),36+i*24,layout.list(),"阵 "+s.id()+(s.powered()?" · 运行":" · 停供"),preview==s.id(),()->{preview=s.id();rebuildWidgets();});}int left=layout.left(),span=layout.span(),half=(span-4)/2;button(left,height-76,half,"切换到预览法阵",false,()->request(1,preview));button(left+half+4,height-76,span-half-4,view.current().powered()?"停止当前法阵":"启动当前法阵",false,()->request(4,view.selected()));button(left,height-51,half,"刻印 / 管理本书法阵",false,()->request(3,0));button(left+half+4,height-51,span-half-4,"关闭",false,this::onClose);}
    @Override public void render(GuiGraphics g,int x,int y,float p){var slot=view.slots().stream().filter(s->s.id()==preview).findFirst().orElse(view.current());g.drawManaged(()->{BookRenderer.frame(g,font,width,height,"法术书 / 当前阵 "+view.selected()+" / "+view.slots().size()+" 个独立法阵",view.message());BookRenderer.preview(g,font,layout,slot.blueprint());g.drawString(font,"缓存 "+Fixed.display(slot.held())+" · 预览阵 "+slot.id(),layout.previewX()+5,height-89,EditorRenderer.MUTED,false);});super.render(g,x,y,p);}
    @Override public void tick(){if(minecraft.player!=null && ++refresh>=20){refresh=0;request(5,0);}}
    @Override public boolean isPauseScreen(){return false;}
}
