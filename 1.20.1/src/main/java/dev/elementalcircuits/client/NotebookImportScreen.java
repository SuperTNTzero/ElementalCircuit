package dev.elementalcircuits.client;
import dev.elementalcircuits.adapter.BlueprintTextCode;
import dev.elementalcircuits.client.render.*;
import dev.elementalcircuits.domain.NotebookInspection;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class NotebookImportScreen extends Screen {
    private final NotebookScreen parent;
    private final String pageName;
    private MultiLineEditBox code;
    private String text="",message="粘贴或输入 EC1 文本码，校验通过后新增一页";
    public NotebookImportScreen(NotebookScreen parent,String pageName){super(Component.literal("新增蓝图页"));this.parent=parent;this.pageName=pageName;}
    @Override protected void init(){int left=Math.max(8,(width-580)/2),span=width-left*2;code=addRenderableWidget(new MultiLineEditBox(font,left,36,span,height-100,Component.literal("输入文本码，支持 Ctrl+V"),Component.literal("蓝图文本码")));code.setCharacterLimit(BlueprintTextCode.LIMIT);code.setValue(text);code.setValueListener(v->text=v);addRenderableWidget(new CircuitButton(left,height-55,span/2-2,22,"校验并保存为新页",false,null,false,()->{try{BlueprintTextCode.decode(code.getValue());parent.request(1,0,pageName,code.getValue());message="已提交，请等待服务器保存";}catch(RuntimeException e){message=e.getMessage();}}));addRenderableWidget(new CircuitButton(left+span/2+2,height-55,span/2-2,22,"返回蓝图簿",false,null,false,this::onClose));}
    public void acceptEditor(dev.elementalcircuits.network.CircuitNetwork.Editor p){parent.acceptEditor(p);}
    public void accept(NotebookInspection v){parent.accept(v);minecraft.setScreen(parent);}
    @Override public void tick(){code.tick();parent.tick();}
    @Override public void render(GuiGraphics g,int x,int y,float p){g.drawManaged(()->BookRenderer.frame(g,font,width,height,"蓝图簿 / 导入文本码",message));super.render(g,x,y,p);}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
