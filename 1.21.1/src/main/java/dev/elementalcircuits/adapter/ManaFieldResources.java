package dev.elementalcircuits.adapter;

import com.google.gson.*;
import dev.elementalcircuits.domain.ManaFieldRules;
import java.io.*;
import java.nio.charset.StandardCharsets;

public final class ManaFieldResources {
    private static final ManaFieldRules RULES=load();
    private ManaFieldResources() {}
    public static ManaFieldRules defaults(){return RULES;}
    public static ManaFieldRules parse(JsonObject j){return new ManaFieldRules(i(j,"version"),i(j,"cellSize"),i(j,"stepTicks"),n(j,"capacity"),n(j,"ambient"),i(j,"diffusionDivisor"),i(j,"decayDivisor"),n(j,"intakePerTick"),n(j,"crystal"),n(j,"naturalCapacity"),n(j,"artificialCapacity"),n(j,"naturalPerStep"),n(j,"artificialPerStep"));}
    private static long n(JsonObject j,String key){return j.get(key).getAsBigDecimal().longValueExact();}
    private static int i(JsonObject j,String key){return Math.toIntExact(n(j,key));}
    private static ManaFieldRules load(){try(var in=ManaFieldResources.class.getResourceAsStream("/data/elementalcircuits/rules/mana_field_v1.json")){
        if(in==null)throw new IllegalStateException("缺少魔力场规则");return parse(JsonParser.parseString(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject());
    }catch(IOException|RuntimeException e){throw new IllegalStateException("魔力场规则加载失败",e);}}
}
