package dev.elementalcircuits.client;
import dev.elementalcircuits.adapter.BlueprintTextCode;
import dev.elementalcircuits.client.editor.BookLayout;
import dev.elementalcircuits.client.render.*;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.network.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Physical library navigation and draft import. The server owns saved pages. */
public final class NotebookScreen extends Screen {
    private final CircuitScreen parent;
    private NotebookInspection view;
    private BookLayout layout;
    private EditBox name;
    private String error="";
    private int offset,deleteId;
    public NotebookScreen(CircuitScreen parent,NotebookInspection v){super(Component.literal("蓝图簿"));this.parent=parent;view=v;for(int i=0;i<v.pages().size();i++)if(v.pages().get(i).id()==v.selected())offset=i;}
    public void accept(NotebookInspection v){if(!view.identity().equals(v.identity()))return;boolean selection=view.selected()!=v.selected();view=v;deleteId=0;error="";if(selection)offset=Math.max(0,v.pages().indexOf(v.pages().stream().filter(p->p.id()==v.selected()).findFirst().orElse(null))-Math.max(1,(height-132)/23)+1);rebuildWidgets();}
    public void acceptEditor(CircuitNetwork.Editor p){if(parent!=null)parent.accept(p);}
    void request(int action,int id,String title,String text){BookNetwork.send(new BookNetwork.NotebookRequest(view.identity(),action,id,title,text));}
    private void button(int x,int y,int w,String text,Runnable action){var b=new CircuitButton(x,y,w,21,text,false,null,false,()->{try{error="";action.run();}catch(RuntimeException e){error=e.getMessage()==null?"操作失败":e.getMessage();}});b.setTooltip(Tooltip.create(Component.literal(text)));addRenderableWidget(b);}
    @Override protected void init(){layout=BookLayout.of(width,height);int left=layout.left(),span=layout.span(),visible=Math.max(1,(height-132)/23);offset=Math.max(0,Math.min(offset,Math.max(0,view.pages().size()-visible)));
        for(int i=offset;i<Math.min(view.pages().size(),offset+visible);i++){var page=view.pages().get(i);addRenderableWidget(new CircuitButton(left,36+(i-offset)*23,layout.list(),21,page.title(),page.id()==view.selected(),null,false,()->request(0,page.id(),"","")));}
        button(width-63,4,55,"返回",this::onClose);
        int y=height-89;name=addRenderableWidget(new EditBox(font,left,y,span-60,19,Component.literal("蓝图名称")));name.setMaxLength(32);name.setValue(view.pages().stream().filter(p->p.id()==view.selected()).map(NotebookInspection.Page::title).findFirst().orElse("新蓝图"));
        button(left+span-56,y,56,"改名",()->{requirePage();request(2,view.selected(),name.getValue(),"");});
        int half=(span-4)/2;
        button(left,y+24,half,parent==null?"从文本码新增":"保存当前草稿",()->{if(parent==null)minecraft.setScreen(new NotebookImportScreen(this,name.getValue()));else request(1,0,name.getValue(),BlueprintTextCode.encode(parent.draftForExport()));});
        button(left+half+4,y+24,span-half-4,"复制选页文本码",()->{requirePage();minecraft.keyboardHandler.setClipboard(BlueprintTextCode.encode(view.blueprint()));error="已复制选页文本码";});
        button(left,y+48,half,parent==null?"导入文本码":"载入选页到草稿",()->{if(parent==null)minecraft.setScreen(new NotebookImportScreen(this,name.getValue()));else{requirePage();var source=view.blueprint();var target=new Blueprint(source.version(),parent.draftForExport().rules(),source.runes(),source.routes(),source.continuous());parent.acceptImported(target);onClose();}});
        button(left+half+4,y+48,span-half-4,deleteId==view.selected() && deleteId!=0?"确认删除此页":"删除选页",()->{requirePage();if(deleteId==view.selected()){request(3,view.selected(),"","");deleteId=0;}else{deleteId=view.selected();rebuildWidgets();error="再次点击确认删除选页";}});
    }
    private void requirePage(){if(view.blueprint()==null)throw new IllegalArgumentException("请先保存或导入一页蓝图");}
    @Override public boolean mouseScrolled(double x,double y,double d){offset+=d>0?-1:1;rebuildWidgets();return true;}
    @Override public void tick(){if(name!=null)name.tick();if(parent!=null)parent.tick();}
    @Override public void render(GuiGraphics g,int x,int y,float p){g.drawManaged(()->{BookRenderer.frame(g,font,width,height,"蓝图簿 / "+view.pages().size()+" / 16 页",error.isEmpty()?view.message():error);BookRenderer.preview(g,font,layout,view.blueprint());});super.render(g,x,y,p);}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
