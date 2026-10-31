package dev.elementalcircuits.adapter;

import dev.elementalcircuits.domain.LogicDefaults;
import java.io.*;
import java.nio.charset.StandardCharsets;

public final class LogicResources {
    private static final LogicDefaults DEFAULTS=load();
    private LogicResources(){ }
    private static LogicDefaults load(){try(InputStream in=LogicResources.class.getResourceAsStream("/data/elementalcircuits/rules/logic_defaults_v1.json")){
        if(in==null)throw new IllegalStateException("缺少时序符文默认规则");return JsonCodecs.readLogicDefaults(new String(in.readAllBytes(),StandardCharsets.UTF_8));
    }catch(IOException e){throw new IllegalStateException("无法读取时序符文默认规则",e);}}
    public static LogicDefaults defaults(){return DEFAULTS;}
}
