package dev.elementalcircuits.adapter;

import com.google.gson.*;
import dev.elementalcircuits.domain.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** All untrusted JSON is decoded and validated before an active carrier can be changed. */
public final class JsonCodecs {
    private static final Gson GSON=new GsonBuilder().disableHtmlEscaping().create();
    public static final int BLUEPRINT_LIMIT=65536, SNAPSHOT_LIMIT=262144;
    private JsonCodecs() { }
    private static JsonElement parse(String text,int max) {
        if (text==null || text.getBytes(StandardCharsets.UTF_8).length>max) throw new IllegalArgumentException("文本过长");
        int depth=0; boolean quoted=false, escaped=false;
        for(char c:text.toCharArray()) {
            if(quoted){if(escaped)escaped=false;else if(c=='\\')escaped=true;else if(c=='"')quoted=false;}
            else if(c=='"')quoted=true;
            else if(c=='{' || c=='['){if(++depth>32)throw new IllegalArgumentException("JSON 嵌套过深");}
            else if(c=='}' || c==']')depth--;
        }
        JsonElement value=JsonParser.parseString(text); checkIntegers(value); return value;
    }
    private static void checkIntegers(JsonElement e) {
        if(e.isJsonArray())for(JsonElement child:e.getAsJsonArray())checkIntegers(child);
        else if(e.isJsonObject())for(var entry:e.getAsJsonObject().entrySet())checkIntegers(entry.getValue());
        else if(e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
            String number=e.getAsString();
            if(!number.matches("-?(0|[1-9][0-9]{0,18})"))throw new IllegalArgumentException("仅接受有界整数，不接受小数或指数");
            try{Long.parseLong(number);}catch(NumberFormatException ex){throw new IllegalArgumentException("整数越界");}
        }
    }
    private static long number(JsonObject o,String key){JsonElement e=o.get(key);if(e==null||!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("缺少整数 "+key);return e.getAsLong();}
    private static int integer(JsonObject o,String key){return Math.toIntExact(number(o,key));}
    private static boolean bool(JsonObject o,String key){JsonElement e=o.get(key);if(e==null||!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("缺少布尔值 "+key);return e.getAsBoolean();}
    private static String string(JsonObject o,String key){JsonElement e=o.get(key);if(e==null||!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isString())throw new IllegalArgumentException("缺少文本 "+key);return e.getAsString();}
    private static JsonArray array(JsonObject o,String key,int limit){JsonArray a=o.getAsJsonArray(key);if(a==null||a.size()>limit)throw new IllegalArgumentException("数组过长或缺失 "+key);return a;}
    private static <T extends Enum<T>> T enumeration(Class<T> cls,JsonObject o,String key){return Enum.valueOf(cls,string(o,key));}
    private static Hex hex(JsonElement e){JsonObject o=e.getAsJsonObject();return new Hex(integer(o,"q"),integer(o,"r"));}
    private static Rules rules(JsonObject r){return new Rules(integer(r,"version"),enumeration(Rules.Material.class,r,"material"),number(r,"conversion"),number(r,"inversion"),number(r,"coupling"),number(r,"nodeCapacity"),number(r,"injection"),integer(r,"interval"),integer(r,"window"),number(r,"signalCost"),number(r,"playerCapacity"),number(r,"playerRegen"),integer(r,"playerDelay"),number(r,"range"),number(r,"radius"),number(r,"routingRetention"),number(r,"oppositionRetention"));}
    public static SecondaryRules readSecondaryRules(String text){var o=parse(text,2048).getAsJsonObject();return new SecondaryRules(integer(o,"version"),integer(o,"fragments"),integer(o,"chains"),integer(o,"ignite"),integer(o,"slow"),integer(o,"knockback"),integer(o,"childRange"),integer(o,"chainRange"),integer(o,"fanDegrees"),number(o,"retention"),number(o,"statusCost"));}
    public static String secondaryRules(SecondaryRules rules){return GSON.toJson(rules);}
    public static ProjectileRules readProjectileRules(String text){var o=parse(text,2048).getAsJsonObject();return new ProjectileRules(integer(o,"version"),integer(o,"burst"),integer(o,"bounces"),integer(o,"homing"),number(o,"bounceRetention"),integer(o,"homingDivisor"),integer(o,"turnDegrees"));}
    public static String projectileRules(ProjectileRules rules){return GSON.toJson(rules);}
    private static ProjectileEffects projectileEffects(JsonObject s){if(!s.has("effects"))return ProjectileEffects.NONE;var e=s.getAsJsonObject("effects");if(!e.has("fragments") && !e.has("chains") && !e.has("ignite") && !e.has("slow") && !e.has("knockback"))return new ProjectileEffects(integer(e,"burst"),integer(e,"bounces"),integer(e,"homing"));return new ProjectileEffects(integer(e,"burst"),integer(e,"bounces"),integer(e,"homing"),integer(e,"fragments"),integer(e,"chains"),integer(e,"ignite"),integer(e,"slow"),integer(e,"knockback"));}
    public static LogicDefaults readLogicDefaults(String text){var o=parse(text,2048).getAsJsonObject();return new LogicDefaults(integer(o,"version"),integer(o,"counterThreshold"),integer(o,"clockPeriod"));}
    public static Rules readRules(String text){return rules(parse(text,8192).getAsJsonObject());}
    public static String blueprint(Blueprint b){return GSON.toJson(b);}
    public static Blueprint readBlueprint(String text){return blueprint(parse(text,BLUEPRINT_LIMIT).getAsJsonObject());}
    private static Blueprint blueprint(JsonObject root) {
        List<Rune> runes=new ArrayList<>();List<Route> routes=new ArrayList<>();
        for(JsonElement e:array(root,"runes",64)){
            JsonObject r=e.getAsJsonObject();List<Integer>w=new ArrayList<>();
            for(JsonElement v:array(r,"weights",64)){if(!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("非法权重");w.add(Math.toIntExact(v.getAsLong()));}
            runes.add(new Rune(integer(r,"id"),enumeration(Rune.Kind.class,r,"kind"),hex(r.get("cell")),enumeration(Element.class,r,"element"),number(r,"value"),bool(r,"on"),bool(r,"signalControl"),w));
        }
        for(JsonElement e:array(root,"routes",64)){
            JsonObject r=e.getAsJsonObject();List<Hex> path=new ArrayList<>();for(JsonElement h:array(r,"path",128))path.add(hex(h));
            routes.add(new Route(integer(r,"source"),integer(r,"target"),enumeration(Route.Payload.class,r,"payload"),path));
        }
        return new Blueprint(integer(root,"version"),rules(root.getAsJsonObject("rules")),runes,routes,bool(root,"continuous"));
    }
    private static Mixture mixture(JsonObject root){
        long[] values=new long[Mixture.COMPONENTS];JsonArray a=array(root,"values",Mixture.COMPONENTS);
        if(a.size()!=values.length)throw new IllegalArgumentException("成分数量错误");
        for(int i=0;i<values.length;i++){if(!a.get(i).isJsonPrimitive()||!a.get(i).getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("非法成分");values[i]=a.get(i).getAsLong();}
        return new Mixture(values);
    }
    private static Packet packet(JsonElement e){
        if(e.isJsonNull())return null;JsonObject p=e.getAsJsonObject(),s=p.getAsJsonObject("spell");
        return new Packet(mixture(p.getAsJsonObject("mana")),new SpellSpec(enumeration(SpellSpec.Method.class,s,"method"),enumeration(SpellSpec.Shape.class,s,"shape"),number(s,"amount"),integer(s,"range"),integer(s,"radius"),integer(s,"aim"),projectileEffects(s)));
    }
    private static Packet[] packets(JsonObject o,String key,int size){JsonArray a=array(o,key,64);if(a.size()!=size)throw new IllegalArgumentException("缓存数量错误");Packet[] p=new Packet[size];for(int i=0;i<size;i++)p[i]=packet(a.get(i));return p;}
    private static boolean[] booleans(JsonObject o,String key,int size){JsonArray a=array(o,key,64);if(a.size()!=size)throw new IllegalArgumentException("信号数量错误");boolean[] v=new boolean[size];for(int i=0;i<size;i++){if(!a.get(i).isJsonPrimitive()||!a.get(i).getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("非法信号");v[i]=a.get(i).getAsBoolean();}return v;}
    private static boolean[] legacyValidity(int n){boolean[] valid=new boolean[n];Arrays.fill(valid,true);return valid;}
    public static String snapshot(TickCircuit circuit){return GSON.toJson(circuit.snapshot());}
    private static List<RuneLogicState> logicStates(JsonObject o,Blueprint b,int version){
        if(version<3)return b.runes().stream().sorted(Comparator.comparingInt(Rune::id)).map(RuneLogicState::initial).toList();
        List<RuneLogicState> states=new ArrayList<>();for(var value:array(o,"logic",64)){var s=value.getAsJsonObject();states.add(new RuneLogicState(integer(s,"count"),integer(s,"phase"),bool(s,"latched"),bool(s,"last"),bool(s,"armed")));}return states;
    }
    public static TickCircuit readSnapshot(String text){
        JsonObject o=parse(text,SNAPSHOT_LIMIT).getAsJsonObject();Blueprint b=blueprint(o.getAsJsonObject("blueprint"));int n=b.runes().size(),e=b.routes().size();
        int v=integer(o,"version");
        return TickCircuit.restore(new CircuitSnapshot(v,b,number(o,"clock"),number(o,"sequence"),bool(o,"powered"),integer(o,"remaining"),integer(o,"injectionCountdown"),number(o,"supplied"),number(o,"loss"),number(o,"delivered"),mixture(o.getAsJsonObject("reserve")),packets(o,"local",n),packets(o,"inbox",e),packets(o,"transit",e),packets(o,"pending",e),booleans(o,"signals",e),booleans(o,"gates",n),v==1?new Packet[n]:packets(o,"ports",n),v==1?new boolean[n]:booleans(o,"drives",n),v==1?new boolean[n]:booleans(o,"emitted",n),v==1?legacyValidity(e):booleans(o,"routeValidity",e),v==1?new boolean[n]:booleans(o,"driveValidity",n),v==1?new boolean[n]:booleans(o,"emittedValidity",n),logicStates(o,b,v)));
    }
    public static String pool(ManaPool pool){return GSON.toJson(pool.snapshot());}
    public static String packetText(Packet packet){return GSON.toJson(packet);}
    public static Packet readPacket(String text){return packet(parse(text,4096));}
    public static ManaPool readPool(String text,long regen,int delay){JsonObject o=parse(text,2048).getAsJsonObject();return ManaPool.restore(new ManaPool.State(integer(o,"version"),number(o,"capacity"),number(o,"amount"),integer(o,"waitTicks"),integer(o,"remainder"),number(o,"spent")),regen,delay);}
    private static AssemblyRules assemblyRules(JsonObject o){return new AssemblyRules(integer(o,"version"),integer(o,"maxSlots"),integer(o,"maxLinks"),number(o,"retention"),integer(o,"externalSlots"),integer(o,"range"),number(o,"blockCapacity"),number(o,"chargeStep"));}
    public static AssemblyRules readAssemblyRules(String text){return assemblyRules(parse(text,4096).getAsJsonObject());}
    private static CircuitAddress address(JsonObject o){return new CircuitAddress(UUID.fromString(string(o,"carrier")),integer(o,"slot"),integer(o,"rune"));}
    private static CircuitLinks.Snapshot links(JsonObject o){
        List<CircuitLinks.Connection> links=new ArrayList<>();for(JsonElement e:array(o,"connections",32)){JsonObject c=e.getAsJsonObject();links.add(new CircuitLinks.Connection(integer(c,"id"),address(c.getAsJsonObject("from")),address(c.getAsJsonObject("to")),enumeration(Route.Payload.class,c,"payload"),c.has("transit")?packet(c.get("transit")):null,bool(c,"signal"),bool(c,"valid")));}
        return new CircuitLinks.Snapshot(integer(o,"version"),integer(o,"nextId"),assemblyRules(o.getAsJsonObject("rules")),number(o,"accepted"),number(o,"delivered"),number(o,"loss"),links);
    }
    public static String links(CircuitLinks links){return GSON.toJson(links.snapshot());}
    public static CircuitLinks readLinks(String text){return CircuitLinks.restore(links(parse(text,262144).getAsJsonObject()));}
    public static String assembly(CircuitAssembly assembly){return GSON.toJson(assembly.snapshot());}
    public static CircuitAssembly readAssembly(String text){
        JsonObject o=parse(text,1048576).getAsJsonObject();List<CircuitAssembly.Slot> slots=new ArrayList<>();
        for(JsonElement e:array(o,"slots",4)){JsonObject s=e.getAsJsonObject();slots.add(new CircuitAssembly.Slot(integer(s,"id"),readSnapshot(GSON.toJson(s.get("circuit"))).snapshot()));}
        return CircuitAssembly.restore(new CircuitAssembly.Snapshot(integer(o,"version"),UUID.fromString(string(o,"identity")),assemblyRules(o.getAsJsonObject("rules")),integer(o,"selected"),integer(o,"nextSlot"),slots,links(o.getAsJsonObject("links"))));
    }
    public record LinkRequest(CircuitAddress from,CircuitAddress to,Route.Payload payload){ }
    public static String linkRequest(LinkRequest request){return GSON.toJson(request);}
    public static LinkRequest readLinkRequest(String text){JsonObject o=parse(text,2048).getAsJsonObject();return new LinkRequest(address(o.getAsJsonObject("from")),address(o.getAsJsonObject("to")),enumeration(Route.Payload.class,o,"payload"));}
    public static String logicObservation(TickCircuit circuit){return GSON.toJson(LogicObservation.inspect(circuit));}
    public static LogicObservation readLogicObservation(String text){var o=parse(text,16384).getAsJsonObject();List<LogicObservation.Node> nodes=new ArrayList<>();for(var e:array(o,"nodes",64)){var n=e.getAsJsonObject();nodes.add(new LogicObservation.Node(integer(n,"id"),enumeration(Rune.Kind.class,n,"kind"),integer(n,"count"),integer(n,"phase"),bool(n,"latched"),integer(n,"parameter")));}return new LogicObservation(integer(o,"version"),nodes);}
    public static final int VOLUME_LIMIT=524288;
    public static String volume(BlueprintVolume volume){for(var p:volume.pages())if(blueprint(p.blueprint()).getBytes(StandardCharsets.UTF_8).length>BLUEPRINT_LIMIT)throw new IllegalArgumentException("单页蓝图过长");String s=GSON.toJson(volume);if(s.getBytes(StandardCharsets.UTF_8).length>VOLUME_LIMIT)throw new IllegalArgumentException("蓝图簿文本总量超限");return s;}
    public static BlueprintVolume readVolume(String text){var o=parse(text,VOLUME_LIMIT).getAsJsonObject();List<BlueprintVolume.Page> pages=new ArrayList<>();for(var e:array(o,"pages",16)){var p=e.getAsJsonObject();pages.add(new BlueprintVolume.Page(integer(p,"id"),string(p,"title"),blueprint(p.getAsJsonObject("blueprint"))));}var v=new BlueprintVolume(integer(o,"version"),UUID.fromString(string(o,"identity")),integer(o,"nextId"),integer(o,"selected"),pages);volume(v);return v;}
    public static String notebook(NotebookInspection view){return GSON.toJson(view);}
    public static NotebookInspection readNotebook(String text){var o=parse(text,131072).getAsJsonObject();List<NotebookInspection.Page> pages=new ArrayList<>();for(var e:array(o,"pages",16)){var p=e.getAsJsonObject();pages.add(new NotebookInspection.Page(integer(p,"id"),string(p,"title")));}return new NotebookInspection(UUID.fromString(string(o,"identity")),integer(o,"selected"),pages,!o.has("blueprint") || o.get("blueprint").isJsonNull()?null:blueprint(o.getAsJsonObject("blueprint")),string(o,"message"));}
    public static String spellbook(SpellbookInspection view){return GSON.toJson(view);}
    public static SpellbookInspection readSpellbook(String text){var o=parse(text,524288).getAsJsonObject();List<SpellbookInspection.Slot> slots=new ArrayList<>();for(var e:array(o,"slots",4)){var p=e.getAsJsonObject();slots.add(new SpellbookInspection.Slot(integer(p,"id"),blueprint(p.getAsJsonObject("blueprint")),bool(p,"powered"),number(p,"held")));}return new SpellbookInspection(UUID.fromString(string(o,"identity")),integer(o,"selected"),slots,string(o,"message"));}
    public static String workshop(WorkshopInspection view){return GSON.toJson(view);}
    public static WorkshopInspection readWorkshop(String text){var o=parse(text,8192).getAsJsonObject();var p=o.getAsJsonObject("assembly");var stock=new HashMap<String,Integer>();for(var e:o.getAsJsonObject("stock").entrySet())stock.put(e.getKey(),integer(o.getAsJsonObject("stock"),e.getKey()));return new WorkshopInspection(UUID.fromString(string(o,"identity")),number(o,"position"),new WandParts.Assembly(string(p,"head"),string(p,"body"),string(p,"tail")),integer(o,"wear"),stock,integer(o,"plates"),string(o,"message"),bool(o,"valid"));}
    public static String inspection(dev.elementalcircuits.application.observation.AssemblyInspection view){return GSON.toJson(view);}
    public static dev.elementalcircuits.application.observation.AssemblyInspection readInspection(String text){parse(text,131072);return GSON.fromJson(text,dev.elementalcircuits.application.observation.AssemblyInspection.class);}
}
