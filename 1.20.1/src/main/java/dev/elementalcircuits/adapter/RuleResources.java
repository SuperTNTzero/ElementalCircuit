package dev.elementalcircuits.adapter;

import dev.elementalcircuits.domain.Rules;
import java.io.*;
import java.nio.charset.StandardCharsets;

public final class RuleResources {
    private static Rules defaults;
    private RuleResources(){ }
    public static synchronized Rules defaults(){
        if(defaults==null)try(InputStream in=RuleResources.class.getResourceAsStream("/data/elementalcircuits/rules/defaults.json")){
            if(in==null)throw new IllegalStateException("缺少法阵规则资源");
            defaults=JsonCodecs.readRules(new String(in.readAllBytes(),StandardCharsets.UTF_8));
        }catch(IOException e){throw new IllegalStateException("无法加载法阵规则",e);}
        return defaults;
    }
}
