package dev.elementalcircuits.adapter;
import dev.elementalcircuits.domain.SecondaryRules;
import java.io.*;
import java.nio.charset.StandardCharsets;
public final class SecondaryResources {
    private static final SecondaryRules RULES=load();
    private SecondaryResources(){ }
    private static SecondaryRules load(){try(InputStream in=SecondaryResources.class.getResourceAsStream("/data/elementalcircuits/rules/secondary_effects_v1.json")){
        if(in==null)throw new IllegalStateException("缺少二次派生规则");return JsonCodecs.readSecondaryRules(new String(in.readAllBytes(),StandardCharsets.UTF_8));
    }catch(IOException e){throw new IllegalStateException("无法读取二次派生规则",e);}}
    public static SecondaryRules rules(){return RULES;}
}
