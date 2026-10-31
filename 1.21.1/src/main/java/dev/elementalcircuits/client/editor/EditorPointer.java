package dev.elementalcircuits.client.editor;

import dev.elementalcircuits.domain.*;
import java.util.*;

/** Shared normalized tool artwork and native pointer style; no window or renderer dependency. */
public record EditorPointer(Kind kind,Rune.Kind rune,Element element,boolean invalid) {
    public enum Kind { SELECT,ROUTE,RUNE }
    public EditorPointer {
        Objects.requireNonNull(kind);if(kind==Kind.RUNE && (rune==null || element==null))throw new IllegalArgumentException("符文指针缺少符形");
        if(kind==Kind.RUNE && rune!=Rune.Kind.CONVERT)element=Element.PURE;
    }
    public static EditorPointer of(EditorModel.View view){
        boolean invalid=view.ghost()!=null && !view.ghostValid();
        if(view.dragging()>=0 && view.ghost()!=null)return new EditorPointer(Kind.RUNE,view.ghost().kind(),view.ghost().element(),invalid);
        if(view.tool()==EditorModel.Tool.PLACE)return new EditorPointer(Kind.RUNE,view.kind(),view.element(),invalid);
        return new EditorPointer(view.tool()==EditorModel.Tool.ROUTE?Kind.ROUTE:Kind.SELECT,null,null,false);
    }
    public List<RuneShapes.Stroke> strokes(){return switch(kind){case SELECT->RuneShapes.glyph("action/SELECT").strokes();case ROUTE->RuneShapes.glyph("action/ROUTE").strokes();case RUNE->RuneShapes.of(rune,element);};}
    public int color(){return invalid?0xffff8882:kind==Kind.RUNE && rune==Rune.Kind.CONVERT?0xff000000|element.color:0xffe9c98a;}
}
