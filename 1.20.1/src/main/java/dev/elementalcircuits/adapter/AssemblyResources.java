package dev.elementalcircuits.adapter;

import dev.elementalcircuits.domain.AssemblyRules;
import java.nio.charset.StandardCharsets;

public final class AssemblyResources {
    private static final AssemblyRules RULES=load();
    private AssemblyResources(){ }
    public static AssemblyRules rules(){return RULES;}
    private static AssemblyRules load(){try(var stream=AssemblyResources.class.getResourceAsStream("/data/elementalcircuits/rules/assembly_v1.json")){
        if(stream==null)throw new IllegalArgumentException("缺少法阵组规则");return JsonCodecs.readAssemblyRules(new String(stream.readAllBytes(),StandardCharsets.UTF_8));
    }catch(java.io.IOException e){throw new IllegalStateException(e);}}
}
