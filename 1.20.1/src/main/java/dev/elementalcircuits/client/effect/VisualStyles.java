package dev.elementalcircuits.client.effect;

import com.google.gson.*;
import dev.elementalcircuits.application.observation.SpellVisual;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Visual assets only. A reload replaces the full validated style set at once. */
public final class VisualStyles {
    public record Style(int sprite,int ticks,float width,float opacity,int copies){
        public Style{if(sprite<0 || sprite>3 || ticks<1 || ticks>20 || !Float.isFinite(width) || width<=0 || width>2 || !Float.isFinite(opacity) || opacity<0 || opacity>1 || copies<1 || copies>16)throw new IllegalArgumentException("特效美术样式非法");}
    }
    private static Map<SpellVisual.Kind,Style> styles;
    static{try(var in=VisualStyles.class.getResourceAsStream("/assets/elementalcircuits/art/effects.json")){if(in==null)throw new IllegalArgumentException("缺少特效样式");load(new InputStreamReader(in,StandardCharsets.UTF_8));}catch(IOException e){throw new ExceptionInInitializerError(e);}}
    private VisualStyles(){}
    public static void load(Reader reader){
        var root=JsonParser.parseReader(reader).getAsJsonObject();if(root.get("version").getAsInt()!=1)throw new IllegalArgumentException("未知特效美术版本");
        var next=new EnumMap<SpellVisual.Kind,Style>(SpellVisual.Kind.class);for(var kind:SpellVisual.Kind.values()){var s=root.getAsJsonObject("styles").getAsJsonObject(kind.name());next.put(kind,new Style(s.get("sprite").getAsInt(),s.get("ticks").getAsInt(),s.get("width").getAsFloat(),s.get("opacity").getAsFloat(),s.get("copies").getAsInt()));}styles=Map.copyOf(next);
    }
    public static Style of(SpellVisual.Kind kind){return styles.get(kind);}
}
