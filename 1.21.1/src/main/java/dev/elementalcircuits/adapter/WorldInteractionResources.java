package dev.elementalcircuits.adapter;

import com.google.gson.*;
import dev.elementalcircuits.domain.WorldInteractionRules;
import java.io.*;
import java.nio.charset.StandardCharsets;

public final class WorldInteractionResources {
    private static final WorldInteractionRules RULES=load();
    private WorldInteractionResources(){ }
    public static WorldInteractionRules rules(){return RULES;}
    private static long number(JsonObject o,String key){return o.get(key).getAsBigDecimal().longValueExact();}
    private static WorldInteractionRules load(){
        try(InputStream in=WorldInteractionResources.class.getResourceAsStream("/data/elementalcircuits/rules/world_interactions_v1.json")){
            if(in==null)throw new IllegalStateException("缺少世界交互规则");
            JsonObject o=JsonParser.parseString(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
            return new WorldInteractionRules(Math.toIntExact(number(o,"version")),number(o,"blockCost"),number(o,"sourceCost"),
                number(o,"impulsePerUnit"),number(o,"maxImpulse"),Math.toIntExact(number(o,"maxTargets")),Math.toIntExact(number(o,"frostedIceDelay")));
        }catch(IOException|RuntimeException e){throw new IllegalStateException("世界交互规则加载失败",e);}
    }
}
