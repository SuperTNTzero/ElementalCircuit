package dev.elementalcircuits.adapter;

import dev.elementalcircuits.domain.ProjectileRules;
import java.io.*;
import java.nio.charset.StandardCharsets;

public final class ProjectileResources {
    private static final ProjectileRules RULES=load();
    private ProjectileResources(){ }
    private static ProjectileRules load(){try(InputStream in=ProjectileResources.class.getResourceAsStream("/data/elementalcircuits/rules/projectile_effects_v1.json")){
        if(in==null)throw new IllegalStateException("缺少派生效果规则");return JsonCodecs.readProjectileRules(new String(in.readAllBytes(),StandardCharsets.UTF_8));
    }catch(IOException e){throw new IllegalStateException("无法读取派生效果规则",e);}}
    public static ProjectileRules rules(){return RULES;}
}
