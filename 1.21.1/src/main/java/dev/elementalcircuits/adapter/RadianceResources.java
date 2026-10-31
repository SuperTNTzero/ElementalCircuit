package dev.elementalcircuits.adapter;

import com.google.gson.*;
import dev.elementalcircuits.domain.RadianceRules;
import java.io.*;
import java.nio.charset.StandardCharsets;

public final class RadianceResources {
    private static final RadianceRules RULES=load();
    private RadianceResources(){ }
    public static RadianceRules rules(){return RULES;}
    private static int number(JsonObject o,String key){return o.get(key).getAsBigDecimal().intValueExact();}
    private static RadianceRules load(){
        try(InputStream in=RadianceResources.class.getResourceAsStream("/data/elementalcircuits/rules/radiance_v1.json")){
            if(in==null)throw new IllegalStateException("缺少光暗规则");
            JsonObject o=JsonParser.parseString(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
            return new RadianceRules(number(o,"version"),number(o,"lightLife"),number(o,"shadowLife"),number(o,"trailLife"),number(o,"trailCost"),number(o,"maxLights"),number(o,"orphanDelay"));
        }catch(IOException|RuntimeException e){throw new IllegalStateException("光暗规则加载失败",e);}
    }
}
