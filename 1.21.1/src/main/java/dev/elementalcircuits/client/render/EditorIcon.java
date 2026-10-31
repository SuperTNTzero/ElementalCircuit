package dev.elementalcircuits.client.render;

import dev.elementalcircuits.domain.*;

/** Shared icon vocabulary for toolbar and sidebars. Native cursors reuse the same normalized artwork. */
public record EditorIcon(Rune.Kind rune,Element element,Action action){
    public enum Action { SELECT,ROUTE,SAVE,START,STOP,IMPORT,EXPORT,DELETE,APPLY,PLATE,HEAD,BODY,TAIL,REPAIR,UNDO,REDO,CLOSE }
    public static EditorIcon rune(Rune.Kind kind){return kind==null?null:new EditorIcon(kind,null,null);}
    public static EditorIcon rune(Rune.Kind kind,Element element){return new EditorIcon(kind,element,null);}
    public static EditorIcon action(Action action){return new EditorIcon(null,null,action);}
}
