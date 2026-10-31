package dev.elementalcircuits.client;

import dev.elementalcircuits.adapter.BlueprintTextCode;
import dev.elementalcircuits.client.editor.*;
import dev.elementalcircuits.client.render.*;
import dev.elementalcircuits.network.CircuitNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Input/clipboard adapter. Import validates fully before changing the parent draft. */
public final class BlueprintScreen extends Screen {
    private final CircuitScreen parent;
    private final BlueprintTransferModel model;
    private BlueprintTransferLayout layout;
    private MultiLineEditBox code;
    public BlueprintScreen(CircuitScreen parent,BlueprintTransferModel model){super(Component.literal("法阵蓝图"));this.parent=parent;this.model=model;}
    private void button(int x,int y,int w,String title,boolean selected,EditorIcon.Action icon,Runnable action){
        addRenderableWidget(new CircuitButton(x,y,w,22,title,selected,null,false,()->{
            try{action.run();}catch(RuntimeException e){model.message=e.getMessage()==null?"蓝图码操作失败":e.getMessage();}
        }).icon(EditorIcon.action(icon)));
    }
    private void mode(boolean export){model.exporting=export;model.message=export?"复制这张法阵的蓝图码":"粘贴蓝图码，载入法阵";rebuildWidgets();}
    @Override protected void init(){
        layout=BlueprintTransferLayout.of(width,height);int x=layout.left(),w=layout.boxWidth(),tab=(w-4)/2;
        button(x,32,tab,"导入",!model.exporting,EditorIcon.Action.IMPORT,()->mode(false));
        button(x+tab+4,32,w-tab-4,"导出",model.exporting,EditorIcon.Action.EXPORT,()->mode(true));
        button(x+w-56,5,56,"返回",false,EditorIcon.Action.CLOSE,this::onClose);
        code=addRenderableWidget(new MultiLineEditBox(font,x,layout.top(),w,layout.boxHeight(),Component.literal(model.exporting?"点击下方按钮生成蓝图码":"在此输入蓝图码，支持 Ctrl+V"),Component.literal("蓝图蓝图码")));
        code.setCharacterLimit(BlueprintTextCode.LIMIT);code.setValue(model.text());code.setValueListener(model::text);
        int y=layout.actions();
        if(model.exporting)button(x,y,w,"复制蓝图码",false,EditorIcon.Action.EXPORT,()->{
            String text=model.export(parent.draftForExport());code.setValue(text);minecraft.keyboardHandler.setClipboard(text);model.message="蓝图码已复制";
        });
        else{
            button(x,y,tab,"粘贴蓝图码",false,EditorIcon.Action.IMPORT,()->{
                String text=minecraft.keyboardHandler.getClipboard();if(text.length()>BlueprintTextCode.LIMIT)throw new IllegalArgumentException("蓝图码过长");code.setValue(text);model.message="已粘贴，点击载入蓝图";
            });
            button(x+tab+4,y,w-tab-4,"载入蓝图",false,EditorIcon.Action.APPLY,()->{
                var blueprint=model.imported(parent.draftForExport().rules());parent.acceptImported(blueprint);onClose();
            });
        }
    }
    public void accept(CircuitNetwork.Editor packet){parent.accept(packet);}
    @Override public void tick(){super.tick();parent.tick();}
    // Screen.render invokes the vanilla background in 1.21; our renderer already painted it.
    @Override public void renderBackground(GuiGraphics g,int x,int y,float partial){}
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){BlueprintRenderer.paint(g,font,layout,model.exporting,model.message);super.render(g,mouseX,mouseY,partial);}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
