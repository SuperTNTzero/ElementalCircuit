package dev.elementalcircuits.client.editor;

import dev.elementalcircuits.client.EditorDraft;
import dev.elementalcircuits.domain.*;
import java.util.*;

/** Detached interaction state. Renderers receive an immutable View and cannot edit this model. */
public final class EditorModel {
    public enum Tool { SELECT,PLACE,ROUTE }
    public final EditorDraft draft;
    public final EnumSet<RuneCatalog.Group> expanded=EnumSet.of(RuneCatalog.Group.FLOW);
    public Tool tool=Tool.SELECT;
    public Rune.Kind kind=Rune.Kind.INPUT;
    public Element element=Element.FIRE;
    private int dragging=-1;
    public Route.Payload payload=Route.Payload.MANA;
    public int selected=-1,paletteOffset,parameterScroll;
    public boolean components;
    private int source=-1;
    private final List<Hex> path=new ArrayList<>();
    public String message="";
    private View cachedView;
    private Hex cachedHover;
    private int cachedSource;
    private List<Hex> cachedPath=List.of();
    public EditorModel(Blueprint blueprint){draft=new EditorDraft(blueprint);}
    public Rune selected(){return draft.get().runes().stream().filter(r->r.id()==selected).findFirst().orElse(null);}
    public void cancel(){source=-1;path.clear();dragging=-1;}
    public boolean drawing(){return source>=0;}
    public void choose(Rune.Kind kind){this.kind=kind;tool=Tool.PLACE;components=false;parameterScroll=0;cancel();}
    public void choose(Rune.Kind kind,Element element){choose(kind);if(element!=null)this.element=element;}
    public void beginDrag(){if(tool==Tool.SELECT && selected>0)dragging=selected;}
    public boolean dragging(){return dragging>=0;}
    public void drop(Hex target){int id=dragging;dragging=-1;if(id>=0 && target!=null){draft.move(id,target);message="符文已移动，连线已调整。记得保存";}}
    public void toggle(RuneCatalog.Group group){if(!expanded.remove(group))expanded.add(group);paletteOffset=0;}
    public void click(Hex cell){
        Rune rune=draft.at(cell);
        if(tool==Tool.PLACE){if(rune!=null)throw new IllegalArgumentException("目标格已有符文");draft.place(kind,element,cell);selected=draft.at(cell).id();return;}
        if(tool==Tool.SELECT){int next=rune==null?-1:rune.id();if(next!=selected)parameterScroll=0;selected=next;return;}
        if(source<0){if(rune==null)throw new IllegalArgumentException("从源符文开始布线");source=rune.id();path.clear();path.add(cell);return;}
        if(path.get(path.size()-1).equals(cell))return;
        List<Hex> completed=preview(cell);
        if(rune!=null){draft.route(source,rune.id(),payload,completed);Route route=draft.get().routes().get(draft.get().routes().size()-1);message=routeHint(route,draft.get().rules());cancel();}
        else {path.clear();path.addAll(completed);}
    }
    public List<Hex> preview(Hex target){
        if(!drawing() || target==null || target.equals(path.get(path.size()-1)))return List.copyOf(path);
        List<Hex> result=new ArrayList<>(path);List<Hex> segment=HexRouter.segment(draft.get(),path.get(path.size()-1),target,path);
        result.addAll(segment.subList(1,segment.size()));if(result.size()>128)throw new IllegalArgumentException("线路超过128格");return List.copyOf(result);
    }
    public View view(Hex hover){
        Hex relevantHover=drawing() || !components && (tool==Tool.PLACE || dragging())?hover:null;
        if(cachedView!=null && cachedView.board()==draft.get() && cachedView.tool()==tool && cachedView.kind()==kind
            && cachedView.element()==element && cachedView.payload()==payload && cachedView.selected()==selected
            && cachedView.components()==components && cachedView.dragging()==dragging && Objects.equals(cachedView.message(),message)
            && Objects.equals(cachedHover,relevantHover) && cachedSource==source && cachedPath.equals(path))return cachedView;
        List<Hex> preview=List.copyOf(path);boolean valid=true;String hint="";
        if(drawing() && hover!=null)try{preview=preview(hover);if(preview.size()>1)hint=routeHint(new Route(source,1025,payload,preview),draft.get().rules());}catch(IllegalArgumentException e){valid=false;hint=e.getMessage();}
        Rune ghost=null;Blueprint moved=null;boolean ghostValid=true;String label="";
        if(!components && hover!=null && (tool==Tool.PLACE || dragging())){
            Rune original=dragging()?draft.get().rune(dragging):Rune.create(1024,kind,hover,dev.elementalcircuits.adapter.LogicResources.defaults(),dev.elementalcircuits.adapter.ProjectileResources.rules(),dev.elementalcircuits.adapter.SecondaryResources.rules());
            ghost=new Rune(original.id(),original.kind(),hover,dragging()?original.element():element,original.value(),original.on(),original.signalControl(),original.weights());
            label=ghost.kind()==Rune.Kind.CONVERT?ghost.element().title+"转化":RuneCatalog.title(ghost.kind());
            try{if(dragging())moved=draft.moved(dragging,hover);else draft.placed(kind,element,hover);}
            catch(IllegalArgumentException e){ghostValid=false;label+=" · "+e.getMessage();}
        }
        cachedHover=relevantHover;cachedSource=source;cachedPath=List.copyOf(path);
        return cachedView=new View(draft.get(),tool,kind,element,payload,selected,preview,valid,hint,message,components,ghost,ghostValid,dragging,moved,label);
    }
    public static String routeHint(Route route,Rules rules){
        if(route.payload()==Route.Payload.SIGNAL)return "信号线路 · "+route.length()+" 格 · 下一逻辑步生效";
        double loss=100*(1-route.transmit(Mixture.pure(Fixed.ONE),rules).total()/(double)Fixed.ONE);
        return String.format(Locale.ROOT,"魔力线路 · %d 格 · 1点参考损耗 %.2f%%（含两端耦合）",route.length(),loss);
    }
    public record View(Blueprint board,Tool tool,Rune.Kind kind,Element element,Route.Payload payload,int selected,List<Hex> preview,boolean valid,String routeHint,String message,boolean components,Rune ghost,boolean ghostValid,int dragging,Blueprint moved,String ghostLabel){public View{preview=List.copyOf(preview);}}
}
