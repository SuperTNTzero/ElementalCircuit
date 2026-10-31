package dev.elementalcircuits.domain;

import java.util.*;
import java.util.function.Function;

/** Owns in-flight payloads. Deliver, simulate, publish are distinct world phases. */
public final class CircuitLinks {
    public record Connection(int id,CircuitAddress from,CircuitAddress to,Route.Payload payload,Packet transit,boolean signal,boolean valid){
        public Connection {if(id<1 || from==null || to==null || payload==null || from.equals(to) || payload==Route.Payload.SIGNAL && transit!=null)throw new IllegalArgumentException("非法互联");}
    }
    public record Snapshot(int version,int nextId,AssemblyRules rules,long accepted,long delivered,long loss,List<Connection> connections){public Snapshot{connections=List.copyOf(connections);}}
    private final AssemblyRules rules;
    private final List<Connection> links=new ArrayList<>();
    private int nextId=1;
    private long accepted,delivered,loss;
    private Object changeToken=new Object();
    public Object changeToken(){return changeToken;}
    private void changed(){changeToken=new Object();}
    private void update(int index,Connection next){if(!links.get(index).equals(next)){links.set(index,next);changed();}}
    public CircuitLinks(AssemblyRules rules){this.rules=Objects.requireNonNull(rules);}
    public static CircuitLinks restore(Snapshot s){
        if(s.version!=1 || s.nextId<1 || s.nextId>1000000 || s.accepted<0 || s.delivered<0 || s.loss<0 || s.connections.size()>s.rules.maxLinks())throw new IllegalArgumentException("互联现场超限");
        CircuitLinks graph=new CircuitLinks(s.rules);long held=0;Set<Integer> ids=new HashSet<>();Set<String> pairs=new HashSet<>();
        for(Connection c:s.connections){if(c.id>=s.nextId || !ids.add(c.id) || !pairs.add(c.from+"->"+c.to+":"+c.payload) || c.payload==Route.Payload.MANA && (c.signal || c.valid))throw new IllegalArgumentException("互联重复或计数非法");if(c.transit!=null){if(c.transit.mana().total()>64*Fixed.ONE)throw new IllegalArgumentException("运输缓存超限");held=Math.addExact(held,c.transit.mana().total());}graph.links.add(c);}
        if(Math.addExact(Math.addExact(held,s.delivered),s.loss)!=s.accepted)throw new IllegalArgumentException("互联账本不守恒");
        graph.nextId=s.nextId;graph.accepted=s.accepted;graph.delivered=s.delivered;graph.loss=s.loss;graph.quota();return graph;
    }
    public Snapshot snapshot(){return new Snapshot(1,nextId,rules,accepted,delivered,loss,links);}
    public List<Connection> connections(){return List.copyOf(links);}
    public boolean references(UUID carrier,int slot){return links.stream().anyMatch(c->c.from.carrier().equals(carrier) && (slot==0 || c.from.slot()==slot) || c.to.carrier().equals(carrier) && (slot==0 || c.to.slot()==slot));}
    public static MagicCircuit resolve(CircuitAddress address,Function<UUID,CircuitAssembly> carriers){CircuitAssembly a=carriers.apply(address.carrier());MagicCircuit c=a==null?null:a.find(address.slot());return c==null || c.definition().runes().stream().noneMatch(r->r.id()==address.rune())?null:c;}
    private static void validate(CircuitAddress a,MagicCircuit c,Rune.Kind kind){if(c==null || c.definition().rune(a.rune()).kind()!=kind)throw new IllegalArgumentException("接口不存在或类型不匹配");}
    private static Rune.Kind send(Route.Payload p){return p==Route.Payload.MANA?Rune.Kind.MANA_SEND:Rune.Kind.SIGNAL_SEND;}
    private static Rune.Kind receive(Route.Payload p){return p==Route.Payload.MANA?Rune.Kind.MANA_RECEIVE:Rune.Kind.SIGNAL_RECEIVE;}
    private void idle(CircuitAddress address,Function<UUID,CircuitAssembly> carriers){
        MagicCircuit c=resolve(address,carriers);if(c==null || c.powered())throw new IllegalArgumentException("请先停止两端法阵");
        if(c.definition().rune(address.rune()).kind()==Rune.Kind.MANA_SEND && c.offered(address.rune())!=null)throw new IllegalArgumentException("发送口仍有待发魔力，请先让接收端释放");
        if(links.stream().anyMatch(l->l.from.equals(address) && l.transit!=null))throw new IllegalArgumentException("线路仍有在途魔力");
    }
    private void quota(){Map<UUID,Set<Integer>> outputs=new HashMap<>();for(Connection c:links)if(!c.from.carrier().equals(c.to.carrier()))outputs.computeIfAbsent(c.from.carrier(),id->new HashSet<>()).add(c.from.slot());for(var slots:outputs.values())if(slots.size()>rules.externalSlots())throw new IllegalArgumentException("每个承载者仅一个槽位可向外输出");}
    public void connect(CircuitAddress from,CircuitAddress to,Route.Payload payload,Function<UUID,CircuitAssembly> carriers){
        if(links.size()>=rules.maxLinks() || nextId>=1000000)throw new IllegalArgumentException("线路数量或编号已达上限");
        validate(from,resolve(from,carriers),send(payload));validate(to,resolve(to,carriers),receive(payload));idle(from,carriers);idle(to,carriers);
        if(links.stream().anyMatch(c->c.from.equals(from) && c.to.equals(to) && c.payload==payload))throw new IllegalArgumentException("该线路已存在");
        Connection c=new Connection(nextId,from,to,payload,null,false,false);links.add(c);try{quota();}catch(RuntimeException e){links.remove(links.size()-1);throw e;}nextId++;changed();
    }
    public void disconnect(int id,Function<UUID,CircuitAssembly> carriers){
        Connection c=links.stream().filter(l->l.id==id).findFirst().orElseThrow(()->new IllegalArgumentException("线路不存在"));
        if(c.transit!=null)throw new IllegalArgumentException("线路仍有在途魔力");idle(c.from,carriers);idle(c.to,carriers);links.remove(c);changed();
    }
    /** Abandon a transport whose endpoint is confirmed destroyed. No refund or resource creation. */
    public void abandon(int id){
        Connection c=links.stream().filter(l->l.id==id).findFirst().orElseThrow(()->new IllegalArgumentException("线路不存在"));
        long lost=c.transit==null?0:c.transit.mana().total();
        long nextLoss=Math.addExact(loss,lost);links.remove(c);loss=nextLoss;changed();
    }
    public void deliver(Function<UUID,CircuitAssembly> carriers){
        for(int i=0;i<links.size();i++){
            Connection c=links.get(i);MagicCircuit target=resolve(c.to,carriers);if(target==null || target.definition().rune(c.to.rune()).kind()!=receive(c.payload))continue;
            if(c.payload==Route.Payload.SIGNAL){MagicCircuit source=resolve(c.from,carriers);if(c.valid && source!=null && source.definition().rune(c.from.rune()).kind()==send(c.payload) && source.validSignal(c.from.rune()))target.drive(c.to.rune(),c.signal);}
            else if(c.transit!=null && target.receive(c.to.rune(),c.transit)){
                delivered=Math.addExact(delivered,c.transit.mana().total());update(i,new Connection(c.id,c.from,c.to,c.payload,null,false,false));
            }
        }
    }
    public void publish(Function<UUID,CircuitAssembly> carriers){
        Map<CircuitAddress,List<Integer>> groups=new LinkedHashMap<>();
        for(int i=0;i<links.size();i++)groups.computeIfAbsent(links.get(i).from,id->new ArrayList<>()).add(i);
        for(var group:groups.entrySet()){
            CircuitAddress address=group.getKey();MagicCircuit source=resolve(address,carriers);List<Integer> indices=group.getValue();if(source==null || source.definition().rune(address.rune()).kind()!=send(links.get(indices.get(0)).payload))continue;
            if(links.get(indices.get(0)).payload==Route.Payload.SIGNAL){boolean signal=source.signal(address.rune());for(int i:indices){Connection c=links.get(i);update(i,new Connection(c.id,c.from,c.to,c.payload,null,signal,source.validSignal(address.rune())));}continue;}
            if(indices.stream().anyMatch(i->links.get(i).transit!=null))continue;
            Packet packet=source.offered(address.rune());if(packet==null || accepted>Long.MAX_VALUE-packet.mana().total())continue;
            int[] weights=new int[indices.size()];Arrays.fill(weights,1);List<Mixture> shares=packet.mana().split(weights);List<Packet> after=new ArrayList<>();long lost=0;
            for(Mixture share:shares){Mixture m=share.retain(rules.retention());lost+=share.total()-m.total();after.add(m.empty()?null:new Packet(m,packet.spell()));}
            source.export(address.rune(),packet);accepted+=packet.mana().total();loss+=lost;changed();
            for(int j=0;j<indices.size();j++){int i=indices.get(j);Connection c=links.get(i);update(i,new Connection(c.id,c.from,c.to,c.payload,after.get(j),false,false));}
        }
    }
}
