package dev.elementalcircuits.adapter;
import dev.elementalcircuits.domain.ShapeRules;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.io.*;
/** Frozen classpath rules; geometry changes require a new protocol profile. */
public final class ShapeResources {
    private static final ShapeRules RULES=load();
    private ShapeResources(){}
    public static ShapeRules rules(){return RULES;}
    private static ShapeRules load(){
        try(var in=ShapeResources.class.getResourceAsStream("/data/elementalcircuits/rules/shapes_v1.json")){
            if(in==null)throw new IllegalArgumentException("缺少形状规则");
            var o=JsonParser.parseString(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
            return new ShapeRules(o.get("version").getAsBigDecimal().intValueExact(),o.get("fanDegrees").getAsBigDecimal().intValueExact(),o.get("fanReach").getAsBigDecimal().intValueExact(),o.get("fanHeight").getAsBigDecimal().intValueExact(),o.get("wallThickness").getAsBigDecimal().intValueExact(),o.get("slabHeight").getAsBigDecimal().intValueExact(),o.get("ringInner").getAsBigDecimal().intValueExact(),o.get("columnRadius").getAsBigDecimal().intValueExact(),o.get("crossWidth").getAsBigDecimal().intValueExact(),o.get("domeInner").getAsBigDecimal().intValueExact(),o.get("groundOffset").getAsBigDecimal().intValueExact(),o.get("blockProbes").getAsBigDecimal().intValueExact());
        }catch(IOException|RuntimeException e){throw new IllegalStateException("形状规则加载失败",e);}
    }
}
