package dev.elementalcircuits.domain;

import com.google.gson.*;
import dev.elementalcircuits.adapter.AudioResources;
import dev.elementalcircuits.application.observation.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class AudioContracts {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static SpellSound sound(int x){return new SpellSound(SpellSound.Kind.CAST,Element.FIRE,new SpellVisual.Point(x,0,0));}
    public static int run(){
        var policy=AudioResources.policy();
        var boundary=new SoundBudget(policy);boundary.add(0,new SpellSound(SpellSound.Kind.START,Element.PURE,new SpellVisual.Point(0,0,0)));boundary.add(1,sound(4));
        check(boundary.snapshot().size()==2,"上一世界步结束后的玩家起阵声保留到下一次发布，不被新步显化覆盖");boundary.finish(1);check(boundary.snapshot().isEmpty(),"跨边界观察仅发布一次");
        var q=new SoundBudget(policy);check(q.add(0,sound(0)),"首个发射声接收");
        check(!q.add(0,new SpellSound(SpellSound.Kind.CAST,Element.WATER,new SpellVisual.Point(1,0,0))),"同区域混合元素和同一步分裂合并音效");
        q.finish(0);check(q.snapshot().isEmpty() && q.remembered()==1,"发件箱释放但保留冷却");
        check(!q.add(3,sound(0)) && q.add(4,sound(0)),"冷却按世界步边界恢复");q.finish(4);
        for(int i=0;i<policy.worldLimit()+100;i++)q.add(5,sound(i*4+4));check(q.snapshot().size()==policy.worldLimit(),"世界队列有界");
        check(q.audible(new SpellVisual.Point(100,0,0)).size()==policy.playerLimit(),"每位观察者有独立声音预算");
        check(q.audible(new SpellVisual.Point(10000,0,0)).isEmpty(),"远处玩家不收到声音包");
        q.finish(5);check(!q.add(5,sound(10000)),"同一步反复排空也不能重置世界预算");
        for(int tick=6;tick<200;tick++){q.finish(tick);for(int i=0;i<policy.worldLimit();i++)q.add(tick,sound((tick*policy.worldLimit()+i)*4));check(q.remembered()<=policy.worldLimit()*policy.maxCooldown(),"持续施法的冷却表始终有界");}
        q.finish(201+policy.maxCooldown());check(q.remembered()==0 && q.snapshot().isEmpty(),"静默后回收全部冷却记录");
        q.add(500,sound(0));check(q.add(1,sound(0)),"更换时间线不会保留未来冷却");
        var circuit=new TickCircuit(AssemblyContracts.line(true,Route.Payload.MANA,Rune.Kind.INPUT,Rune.Kind.OUTPUT));circuit.start();String before=dev.elementalcircuits.adapter.JsonCodecs.snapshot(circuit);
        for(int i=1;i<100;i++){q.add(i,sound(i*4));q.finish(i);}check(before.equals(dev.elementalcircuits.adapter.JsonCodecs.snapshot(circuit)),"丢弃、冷却和发送声音不修改法阵现场");
        try(var input=AudioContracts.class.getResourceAsStream("/assets/elementalcircuits/sounds.json");var r=new InputStreamReader(input,StandardCharsets.UTF_8)){
            var json=JsonParser.parseReader(r).getAsJsonObject();var names=new HashSet<String>();
            for(var kind:SpellSound.Kind.values())for(var element:Element.values())names.add(new SpellSound(kind,element,new SpellVisual.Point(0,0,0)).resource());
            check(json.keySet().equals(names) && names.size()==28,"全部声音事件与静态资源一一对应");
            var resources=new HashSet<String>();
            var provenance=JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(AudioContracts.class.getResourceAsStream("/assets/elementalcircuits/audio/sources.json")),StandardCharsets.UTF_8)).getAsJsonObject();
            var declared=new HashMap<String,JsonObject>();for(var asset:provenance.getAsJsonArray("assets")){var a=asset.getAsJsonObject();declared.put(a.get("name").getAsString(),a);}
            for(String name:names){var style=json.getAsJsonObject(name);check(style.has("subtitle"),"声音提供字幕");
                var variants=style.getAsJsonArray("sounds");check(variants.size()==(name.startsWith("cast_")||name.startsWith("impact_")?3:2),"释放与命中三变体，起阵与屏障两变体");
                for(var entry:variants){var clip=entry.getAsJsonObject();check(clip.get("attenuation_distance").getAsInt()==policy.range(),"素材衰减距离与服务器筛选一致");
                check(clip.get("preload").getAsBoolean() && resources.add(clip.get("name").getAsString()),"短音效预载且各变体唯一");
                String path="/assets/elementalcircuits/sounds/"+clip.get("name").getAsString().split(":")[1]+".ogg";
                try(var ogg=Objects.requireNonNull(AudioContracts.class.getResourceAsStream(path))){byte[] bytes=ogg.readAllBytes();String header=new String(bytes,0,Math.min(bytes.length,128),StandardCharsets.ISO_8859_1);check(header.startsWith("OggS") && header.contains("vorbis"),"资源为真实Vorbis文件");check(bytes.length>1000 && bytes.length<64000,"短期静态声音体积受控");
                    int offset=header.indexOf("vorbis");var binary=java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN);check(bytes[offset+10]==1 && binary.getInt(offset+11)==44100,"所有变体均为44100Hz定位单声道");
                    String key=clip.get("name").getAsString().substring("elementalcircuits:spell/".length());var meta=Objects.requireNonNull(declared.remove(key));
                    try{check(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)).equals(meta.get("sha256").getAsString()),"授权清单绑定实际发行素材");}catch(java.security.NoSuchAlgorithmException e){throw new AssertionError(e);}
                    for(var source:meta.getAsJsonArray("sources")){var src=provenance.getAsJsonObject("sources").getAsJsonObject(source.getAsString());var pack=provenance.getAsJsonObject("packs").getAsJsonObject(src.get("pack").getAsString());check(pack.has("author")&&pack.has("license_url")&&pack.has("url"),"各层素材均有作者来源和授权");}
                }}
            }
            check(resources.size()==74 && declared.isEmpty(),"74个变体全部覆盖且无悬空授权记录");
            for(String file:List.of("THIRD_PARTY_NOTICES.md","licenses/CC-BY-3.0.txt","licenses/CC0-1.0.txt"))try(var license=Objects.requireNonNull(AudioContracts.class.getResourceAsStream("/META-INF/"+file))){check(license.readAllBytes().length>1000,"发行包保留署名及许可原文");}
        }catch(IOException e){throw new AssertionError(e);}
        return checks;
    }
}
