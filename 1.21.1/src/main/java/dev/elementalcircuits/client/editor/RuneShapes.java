package dev.elementalcircuits.client.editor;

import com.google.gson.*;
import dev.elementalcircuits.domain.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Immutable normalized art loaded from a checked-in vector resource; no generated rune artwork. */
public final class RuneShapes {
    public record Stroke(double x1,double y1,double x2,double y2) { }
    public record Glyph(int index,List<Stroke> strokes) {public Glyph{strokes=List.copyOf(strokes);}}
    private static final Map<String,Glyph> ART;
    public static final int CELL,COLUMNS,WIDTH,HEIGHT,PADDING;
    static {
        try(var in=RuneShapes.class.getResourceAsStream("/assets/elementalcircuits/art/glyphs.json")){
            if(in==null)throw new IllegalArgumentException("缺少符形资源");
            var root=JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
            if(root.get("version").getAsInt()!=1)throw new IllegalArgumentException("未知符形资源版本");
            CELL=root.get("cell").getAsInt();COLUMNS=root.get("columns").getAsInt();WIDTH=root.get("atlasWidth").getAsInt();HEIGHT=root.get("atlasHeight").getAsInt();PADDING=root.get("padding").getAsInt();
            if(CELL<16 || CELL>128 || COLUMNS<1 || WIDTH!=CELL*COLUMNS || HEIGHT<1 || HEIGHT>2048 || PADDING<1 || PADDING>=CELL/2)throw new IllegalArgumentException("符形图集规格非法");
            var art=new LinkedHashMap<String,Glyph>();int index=0;
            for(var entry:root.getAsJsonObject("shapes").entrySet()){
                var strokes=new ArrayList<Stroke>();
                for(var value:entry.getValue().getAsJsonArray()){
                    var row=value.getAsJsonArray();if(row.size()!=4)throw new IllegalArgumentException("符形线段规格非法");double[] c=new double[4];
                    for(int i=0;i<4;i++){c[i]=row.get(i).getAsDouble();if(!Double.isFinite(c[i]) || Math.abs(c[i])>1)throw new IllegalArgumentException("符形越界");}strokes.add(new Stroke(c[0],c[1],c[2],c[3]));
                }
                if(strokes.isEmpty() || strokes.size()>256)throw new IllegalArgumentException("符形笔画数量非法");art.put(entry.getKey(),new Glyph(index++,strokes));
            }
            if(index>COLUMNS*(HEIGHT/CELL))throw new IllegalArgumentException("符形超出图集");ART=Collections.unmodifiableMap(art);
            for(var kind:Rune.Kind.values())glyph("rune/"+kind.name());for(var element:Element.values())glyph("element/"+element.name());
        }catch(IOException e){throw new ExceptionInInitializerError(e);}
    }
    private RuneShapes() { }
    public static Glyph glyph(String name){return Objects.requireNonNull(ART.get(name),"缺少符形："+name);}
    public static Set<String> names(){return ART.keySet();}
    public static String key(Rune.Kind kind,Element element){return kind==Rune.Kind.CONVERT && element!=null?"element/"+element.name():"rune/"+kind.name();}
    public static List<Stroke> of(Rune.Kind kind){return glyph(key(kind,null)).strokes();}
    public static List<Stroke> of(Rune.Kind kind,Element element){return glyph(key(kind,element)).strokes();}
    public static List<Stroke> element(Element element){return glyph("element/"+element.name()).strokes();}
}
