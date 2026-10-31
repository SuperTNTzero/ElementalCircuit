package dev.elementalcircuits.domain;

import java.util.*;

/** Synchronous data flow. Only completed previous-step transports can be consumed. */
public final class TickCircuit extends MagicCircuit {
    private final Blueprint blueprint;
    private final List<Rune> nodes;
    private final boolean equipmentInput,fieldInput;
    private final int[][] incoming, outgoing;
    private Packet[] local, inbox, transit, pending;
    private boolean[] signals, gates;
    private Packet[] ports;
    private boolean[] drives,emitted,routeValidity,driveValidity,emittedValidity;
    private RuneLogicState[] logic;
    private long clock, sequence, supplied, loss, delivered;
    private boolean powered;
    private Mixture reserve=Mixture.EMPTY;
    private int remaining, injectionCountdown;
    private Object changeToken=new Object();
    public Object changeToken(){return changeToken;}
    private void changed(){changeToken=new Object();}

    public TickCircuit(Blueprint blueprint) {
        this.blueprint = Objects.requireNonNull(blueprint);
        nodes = blueprint.runes().stream().sorted(Comparator.comparingInt(Rune::id)).toList();
        equipmentInput=nodes.stream().anyMatch(r->r.kind()==Rune.Kind.INPUT);
        fieldInput=nodes.stream().anyMatch(r->r.kind()==Rune.Kind.ABSORB);
        int n=nodes.size(), e=blueprint.routes().size();
        logic=initialLogic(blueprint).toArray(RuneLogicState[]::new);
        local=new Packet[n]; inbox=new Packet[e]; transit=new Packet[e]; pending=new Packet[e];
        signals=new boolean[e]; gates=new boolean[n]; incoming=new int[n][]; outgoing=new int[n][];
        ports=new Packet[n];drives=new boolean[n];emitted=new boolean[n];driveValidity=new boolean[n];emittedValidity=new boolean[n];routeValidity=new boolean[e];Arrays.fill(routeValidity,true);
        for(int edge=0;edge<e;edge++)if(blueprint.rune(blueprint.routes().get(edge).source()).kind()==Rune.Kind.SIGNAL_RECEIVE)routeValidity[edge]=false;
        for (int i=0;i<n;i++) {
            int id=nodes.get(i).id();
            incoming[i]=indices(id,false); outgoing[i]=indices(id,true); gates[i]=nodes.get(i).on();
        }
    }
    private static List<RuneLogicState> initialLogic(Blueprint b){return b.runes().stream().sorted(Comparator.comparingInt(Rune::id)).map(RuneLogicState::initial).toList();}
    private int[] indices(int id, boolean source) {
        return java.util.stream.IntStream.range(0,blueprint.routes().size())
            .filter(e -> (source ? blueprint.routes().get(e).source() : blueprint.routes().get(e).target()) == id).toArray();
    }
    public static TickCircuit restore(CircuitSnapshot s) {
        if (s == null || s.version()<1 || s.version()>6 || s.blueprint()==null) throw new IllegalArgumentException("未知法阵现场版本");
        if(s.version()<5 && s.blueprint().runes().stream().anyMatch(r->r.kind()==Rune.Kind.ABSORB))throw new IllegalArgumentException("旧法阵协议不支持汲取符文");
        if(s.version()<6 && s.blueprint().runes().stream().anyMatch(r->ShapeVolume.rune(r.kind())))throw new IllegalArgumentException("旧现场协议不能包含扩展形状符文");
        TickCircuit c=new TickCircuit(s.blueprint()); int n=c.nodes.size(), e=c.inbox.length;
        Packet[] savedLocal=s.local();
        Packet[] savedInbox=s.inbox();
        Packet[] savedTransit=s.transit();
        Packet[] savedPending=s.pending();
        boolean[] savedSignals=s.signals();
        boolean[] savedGates=s.gates();
        Packet[] savedPorts=s.ports();
        boolean[] savedDrives=s.drives();
        boolean[] savedEmitted=s.emitted();
        boolean[] savedRouteValidity=s.routeValidity();
        boolean[] savedDriveValidity=s.driveValidity();
        boolean[] savedEmittedValidity=s.emittedValidity();
        if (savedLocal.length!=n || savedInbox.length!=e || savedTransit.length!=e || savedPending.length!=e || savedSignals.length!=e || savedGates.length!=n
            || s.clock()<0 || s.sequence()<0 || s.supplied()<0 || s.loss()<0 || s.delivered()<0 || s.remaining()<0 || s.remaining()>c.blueprint.rules().window()
            || s.injectionCountdown()< -1 || s.injectionCountdown()>c.blueprint.rules().interval()) throw new IllegalArgumentException("法阵现场大小或计数损坏");
        if(s.powered() && (s.remaining()==0 || c.nodes.stream().noneMatch(r->r.kind()==Rune.Kind.INPUT || r.kind()==Rune.Kind.ABSORB || r.kind()==Rune.Kind.MANA_RECEIVE || r.kind().signalOut()))
            || s.blueprint().continuous() && s.injectionCountdown()<0 || !s.blueprint().continuous() && s.injectionCountdown()>0)throw new IllegalArgumentException("运行生命周期状态矛盾");
        if(s.reserve()==null || s.reserve().total()>c.blueprint.rules().nodeCapacity())throw new IllegalArgumentException("回收储量超限");
        if(savedPorts.length!=n || savedDrives.length!=n || savedEmitted.length!=n || savedRouteValidity.length!=e || savedDriveValidity.length!=n || savedEmittedValidity.length!=n)throw new IllegalArgumentException("接口现场大小损坏");
        if(s.logic().size()!=n)throw new IllegalArgumentException("时序状态数量不匹配");
        for(int i=0;i<n;i++){if(s.version()<3 && RuneLogic.sequential(c.nodes.get(i).kind()))throw new IllegalArgumentException("旧协议不能包含时序符文");s.logic().get(i).validate(c.nodes.get(i));}
        long held=s.reserve().total();
        for (Packet[] array : new Packet[][]{savedLocal,savedInbox,savedTransit,savedPending,savedPorts}) for (Packet p : array) if (p!=null) {
            if(s.version()<6 && ShapeVolume.shaped(p.spell().shape()))throw new IllegalArgumentException("旧现场协议不能携带扩展形状");
            if (p.mana().total()>c.blueprint.rules().nodeCapacity()) throw new IllegalArgumentException("缓存超出容量");
            held=Math.addExact(held,p.mana().total());
        }
        for (int i=0;i<n;i++) {
            Rune.Kind kind=c.nodes.get(i).kind();
            if(savedLocal[i]!=null && kind!=Rune.Kind.INPUT && kind!=Rune.Kind.ABSORB && kind!=Rune.Kind.MANA_RECEIVE || savedPorts[i]!=null && kind!=Rune.Kind.MANA_SEND
                || savedDrives[i] && kind!=Rune.Kind.SIGNAL_RECEIVE || savedEmitted[i] && kind!=Rune.Kind.SIGNAL_SEND || savedDriveValidity[i] && kind!=Rune.Kind.SIGNAL_RECEIVE || savedEmittedValidity[i] && kind!=Rune.Kind.SIGNAL_SEND)throw new IllegalArgumentException("非法接口缓存");
        }
        for (int i=0;i<e;i++) if (c.blueprint.routes().get(i).payload()==Route.Payload.SIGNAL && (savedInbox[i]!=null || savedTransit[i]!=null || savedPending[i]!=null))
            throw new IllegalArgumentException("信号线不能持有魔力");
        if (Math.addExact(Math.addExact(held,s.loss()),s.delivered())!=s.supplied()) throw new IllegalArgumentException("法阵账本不守恒");
        c.local=savedLocal; c.inbox=savedInbox; c.transit=savedTransit; c.pending=savedPending; c.signals=savedSignals; c.gates=savedGates;
        c.clock=s.clock(); c.sequence=s.sequence(); c.powered=s.powered(); c.remaining=s.remaining(); c.injectionCountdown=s.injectionCountdown();
        c.supplied=s.supplied(); c.loss=s.loss(); c.delivered=s.delivered();
        c.reserve=s.reserve();c.logic=s.logic().toArray(RuneLogicState[]::new);
        c.ports=savedPorts;c.drives=savedDrives;c.emitted=savedEmitted;c.routeValidity=savedRouteValidity;c.driveValidity=savedDriveValidity;c.emittedValidity=savedEmittedValidity;
        return c;
    }
    @Override public Blueprint definition() { return blueprint; }
    @Override public boolean powered() { return powered; }
    @Override public boolean start() {
        if (powered || nodes.stream().noneMatch(r -> r.kind()==Rune.Kind.INPUT || r.kind()==Rune.Kind.ABSORB || r.kind()==Rune.Kind.MANA_RECEIVE || r.kind().signalOut())) return false;
        changed();suspendLogic();Arrays.fill(emittedValidity,false);clearDrives();powered=true; remaining=blueprint.rules().window(); injectionCountdown=0; return true;
    }
    private void suspendLogic(){for(int i=0;i<logic.length;i++)if(RuneLogic.sequential(nodes.get(i).kind())){var next=logic[i].suspended();if(!next.equals(logic[i])){changed();logic[i]=next;}}}
    @Override public void stop(){if(powered){changed();powered=false;}suspendLogic();}
    public RuneLogicState logic(int rune){for(int i=0;i<nodes.size();i++)if(nodes.get(i).id()==rune)return logic[i];throw new IllegalArgumentException("符文不存在");}
    public void resetLogic(int rune){
        for(int i=0;i<nodes.size();i++)if(nodes.get(i).id()==rune && RuneLogic.sequential(nodes.get(i).kind())){
            Rune r=nodes.get(i);RuneLogicState state=RuneLogicState.initial(r);
            if(r.kind()!=Rune.Kind.CLOCK){boolean high=false,valid=true,connected=false;for(int e:incoming[i])if(!manaEdge(e)){high|=signals[e];valid&=routeValidity[e];connected=true;}if(powered && valid && connected)state=new RuneLogicState(0,0,state.latched(),high,true);}
            if(!state.equals(logic[i])){changed();logic[i]=state;}return;
        }
        throw new IllegalArgumentException("只能复位计数器、节拍器或翻转锁存");
    }
    public long held() {
        long total=reserve.total();
        for (Packet[] array : new Packet[][]{local,inbox,transit,pending,ports}) for (Packet p : array) if (p!=null) total+=p.mana().total();
        return total;
    }
    public enum Phase { RUNNING, DRAINING, QUIESCENT }
    /** Stored reserve and offered ports cannot advance internally without new input or a manual start. */
    public Phase phase(){
        if(powered)return Phase.RUNNING;
        for(Packet[] array:new Packet[][]{local,inbox,transit,pending})for(Packet packet:array)if(packet!=null)return Phase.DRAINING;
        return Phase.QUIESCENT;
    }
    public boolean needsStep(){return phase()!=Phase.QUIESCENT;}
    @Override public boolean editable() { return !powered && held()<=blueprint.rules().nodeCapacity(); }
    public void toggleManual(int id) {
        for (int i=0;i<nodes.size();i++) if (nodes.get(i).id()==id && nodes.get(i).kind()==Rune.Kind.SWITCH && !nodes.get(i).signalControl()) {changed();gates[i]=!gates[i];}
    }
    public String status() { return powered ? "运行中" : held()>0 ? "已停供 · 残余魔力保留" : "待机"; }
    public TickCircuit reconfigure(Blueprint definition) {
        if(!editable())throw new IllegalStateException("请停供；残余超出回收规格时须先释放");
        TickCircuit next=new TickCircuit(definition);
        Mixture recovered=reserve;
        for(Packet[] array:new Packet[][]{local,inbox,transit,pending,ports})for(Packet p:array)if(p!=null)recovered=recovered.plus(p.mana());
        if(recovered.total()>definition.rules().nodeCapacity())throw new IllegalStateException("回收储量超出目标规格");
        next.reserve=recovered;
        next.clock=clock;next.sequence=sequence;next.supplied=supplied;next.loss=loss;next.delivered=delivered;
        return next;
    }
    private boolean expandedShapes(){
        if(nodes.stream().anyMatch(r->ShapeVolume.rune(r.kind())))return true;
        for(var array:new Packet[][]{local,inbox,transit,pending,ports})for(var p:array)if(p!=null && ShapeVolume.shaped(p.spell().shape()))return true;return false;
    }
    @Override public CircuitSnapshot snapshot() { return new CircuitSnapshot(expandedShapes()?6:fieldInput?5:4,blueprint,clock,sequence,powered,remaining,injectionCountdown,supplied,loss,delivered,reserve,local,inbox,transit,pending,signals,gates,ports,drives,emitted,routeValidity,driveValidity,emittedValidity,Arrays.asList(logic)); }
    private int node(int id,Rune.Kind kind){for(int i=0;i<nodes.size();i++)if(nodes.get(i).id()==id && nodes.get(i).kind()==kind)return i;throw new IllegalArgumentException("接口类型或编号不符");}
    @Override public boolean receive(int rune,Packet packet){int i=node(rune,Rune.Kind.MANA_RECEIVE);if(!powered || packet==null || !fits(local[i],packet) || supplied>Long.MAX_VALUE-packet.mana().total())return false;changed();local[i]=local[i]==null?packet:local[i].merge(packet);supplied+=packet.mana().total();return true;}
    @Override public Packet offered(int rune){return ports[node(rune,Rune.Kind.MANA_SEND)];}
    @Override public void export(int rune,Packet expected){int i=node(rune,Rune.Kind.MANA_SEND);if(expected==null || !expected.equals(ports[i]) || delivered>Long.MAX_VALUE-expected.mana().total())throw new IllegalArgumentException("发送接口状态已变");changed();ports[i]=null;delivered+=expected.mana().total();}
    @Override public void clearDrives(){for(int i=0;i<drives.length;i++)if(drives[i] || driveValidity[i]){changed();break;}Arrays.fill(drives,false);Arrays.fill(driveValidity,false);}
    @Override public void drive(int rune,boolean value){int i=node(rune,Rune.Kind.SIGNAL_RECEIVE);if(!driveValidity[i] || value && !drives[i])changed();drives[i]|=value;driveValidity[i]=true;}
    @Override public boolean validSignal(int rune){return powered && emittedValidity[node(rune,Rune.Kind.SIGNAL_SEND)];}
    @Override public boolean signal(int rune){return validSignal(rune) && emitted[node(rune,Rune.Kind.SIGNAL_SEND)];}
    private boolean manaEdge(int e) { return blueprint.routes().get(e).payload()==Route.Payload.MANA; }
    private boolean fits(Packet a, Packet b) {
        if (b==null) return true;
        try { return (a==null ? b : a.merge(b)).mana().total()<=blueprint.rules().nodeCapacity(); }
        catch (IllegalArgumentException ex) { return false; }
    }
    @Override public List<Cast> tick(ManaSource source) {
        return tick(source,ReleasePermit.UNLIMITED);
    }
    public List<Cast> tick(ManaSource source,ReleasePermit release) {
        return tick(source,release,ManaSource.NONE);
    }
    public List<Cast> tick(ManaSource source,ReleasePermit release,ManaSource ambient) {
        Objects.requireNonNull(release);
        Objects.requireNonNull(ambient);
        if(!needsStep())return List.of();
        // Refuse exhausted counters before any ownership transfer or world effect.
        if (clock==Long.MAX_VALUE || sequence>Long.MAX_VALUE-64 || supplied>Long.MAX_VALUE-Fixed.MAX*128
            || loss>Long.MAX_VALUE-Fixed.MAX*128 || delivered>Long.MAX_VALUE-Fixed.MAX*128) { stop(); return List.of(); }
        changed();clock++;
        for (int e=0;e<transit.length;e++) if (transit[e]!=null && fits(inbox[e],transit[e])) {
            inbox[e]=inbox[e]==null ? transit[e] : inbox[e].merge(transit[e]); transit[e]=null;
        }
        boolean due=powered && injectionCountdown==0;
        long cost=0, injectionTotal=0; boolean canInject=true;
        if (powered) {
            for (int i=0;i<nodes.size();i++) {
                Rune r=nodes.get(i);
                if (r.kind().signalOut() || r.kind()==Rune.Kind.SIGNAL_SEND || r.kind()==Rune.Kind.SWITCH && r.signalControl()) cost+=blueprint.rules().signalCost();
                if (due && r.kind()==Rune.Kind.INPUT && fits(local[i],new Packet(Mixture.pure(blueprint.rules().injection()),SpellSpec.EMPTY))) injectionTotal+=blueprint.rules().injection();
                else if (due && r.kind()==Rune.Kind.INPUT) canInject=false;
            }
            // Capacity backpressure stalls the entire due injection, never drains unusable supply.
            if (!canInject) {
                injectionTotal=0;
            }
            long externalInjection=Math.max(0,injectionTotal-reserve.total());
            cost+=externalInjection;
            // A field-only circuit maintains its control from the same field; mixed circuits use equipment.
            ManaSource controlSource=equipmentInput?source:fieldInput?ambient:source;
            if (!controlSource.debit(cost)) stop();
            else {
                supplied+=cost;
                if (due && canInject) {
                    for (int i=0;i<nodes.size();i++) if (nodes.get(i).kind()==Rune.Kind.INPUT) {
                        long reused=Math.min(reserve.total(),blueprint.rules().injection());
                        Mixture m=Mixture.EMPTY;
                        if(reused>0){
                            if(reused==reserve.total()){m=reserve;reserve=Mixture.EMPTY;}
                            else {List<Mixture> parts=proportionalTake(reserve,reused);m=parts.get(0);reserve=parts.get(1);}
                        }
                        m=m.plus(Mixture.pure(blueprint.rules().injection()-reused));
                        Packet p=new Packet(m,SpellSpec.EMPTY);
                        local[i]=local[i]==null ? p : local[i].merge(p);
                    }
                    injectionCountdown=blueprint.continuous() ? blueprint.rules().interval() : -1;
                }
                loss+=cost-externalInjection;
            }
        }
        // Harvest only while explicitly powered. Refused/blocked outputs retain their owned resource.
        if(powered && (blueprint.continuous() || due))for(int i=0;i<nodes.size();i++)if(nodes.get(i).kind()==Rune.Kind.ABSORB){
            long held=local[i]==null?0:local[i].mana().total();
            long desired=Math.min(blueprint.rules().injection(),blueprint.rules().nodeCapacity()-held);
            long reused=Math.min(desired,reserve.total()),amount=Math.min(desired-reused,ambient.available());
            if(reused>0 || amount>0){
                if(!ambient.debit(amount))continue;
                Mixture mana=Mixture.pure(amount);
                if(reused>0){if(reused==reserve.total()){mana=mana.plus(reserve);reserve=Mixture.EMPTY;}
                    else {var parts=proportionalTake(reserve,reused);mana=mana.plus(parts.get(0));reserve=parts.get(1);}}
                Packet p=new Packet(mana,SpellSpec.EMPTY);local[i]=local[i]==null?p:local[i].merge(p);supplied+=amount;
            }
            if(injectionCountdown==0)injectionCountdown=blueprint.continuous()?blueprint.rules().interval():-1;
        }
        boolean[] next=new boolean[signals.length],nextValidity=new boolean[signals.length];
        List<Cast> casts=new ArrayList<>();
        for (int i=0;i<nodes.size();i++) {
            Rune rune=nodes.get(i); boolean any=false, all=true, hasSignal=false,valid=true;
            for (int e:incoming[i]) if (!manaEdge(e)) { any|=signals[e]; all&=signals[e]; hasSignal=true;valid&=routeValidity[e]; }
            if (powered) {
                if (rune.kind()==Rune.Kind.SWITCH && rune.signalControl() && valid) gates[i]=any;
                boolean value=switch (rune.kind()) {
                    case SIGNAL -> rune.on(); case AND -> hasSignal && all; case OR -> any; case NOT -> !any;
                    case SENSOR -> inputTotal(i)>=Math.max(1,rune.value()); default -> false;
                };
                if(RuneLogic.sequential(rune.kind())){var result=RuneLogic.step(rune,logic[i],any,valid,hasSignal);logic[i]=result.state();value=result.value();valid=result.valid();}
                if(rune.kind()==Rune.Kind.SIGNAL_RECEIVE){value=drives[i];valid=driveValidity[i];}
                if(rune.kind()==Rune.Kind.SIGNAL_SEND){emittedValidity[i]=hasSignal && valid;if(valid)emitted[i]=any;}
                for (int e:outgoing[i]) if (!manaEdge(e)){next[e]=value;nextValidity[e]=valid;}
            }
            // Already owned branch shares drain separately; new batches wait until all are allocated.
            boolean busy=false; for (int e:outgoing[i]) if (pending[e]!=null) busy=true;
            if (busy || rune.kind()==Rune.Kind.SWITCH && !gates[i]) continue;
            Packet batch=local[i]; boolean hasMana=false, complete=true;
            try {
                for (int e:incoming[i]) if (manaEdge(e)) {
                    hasMana=true; complete&=inbox[e]!=null;
                    if (inbox[e]!=null) batch=batch==null ? inbox[e] : batch.merge(inbox[e]);
                }
                if (batch==null || rune.kind()==Rune.Kind.AND && hasMana && !complete || batch.mana().total()>blueprint.rules().nodeCapacity()) continue;
                if(rune.kind()==Rune.Kind.MANA_SEND){if(ports[i]!=null)continue;consume(i);ports[i]=batch;continue;}
                if (rune.kind()==Rune.Kind.OUTPUT) {
                    if (!batch.spell().compatibleOutput()) continue;
                    long request=batch.spell().amount();
                    if (request>batch.mana().total()) continue;
                    // Amount is a payment request; unspent components remain at the same inputs.
                    Mixture payment=batch.mana();
                    Mixture remainder=Mixture.EMPTY;
                    if (request>0 && request<batch.mana().total()) {
                        List<Mixture> parts=proportionalTake(batch.mana(),request);
                        payment=parts.get(0);
                        remainder=parts.get(1);
                    }
                    long divisor=1;
                    if (batch.spell().range()>0) divisor*=Math.max(1,batch.spell().range());
                    if (batch.spell().radius()>0) { long d=2L*batch.spell().radius()+1; divisor*=d*d*d; }
                    if (batch.spell().aim()>=0) divisor*=1+(Fixed.ONE-batch.spell().aim())*4/Fixed.ONE;
                    Mixture effect=payment.retain(Fixed.ONE/divisor);
                    if(effect.empty())continue;
                    // Pure mana has no world spell. Only a real elemental release uses the wand.
                    if(effect.total()>effect.values()[0] && !release.tryAcquire())continue;
                    consume(i);
                    if(!remainder.empty()){
                        int first=Arrays.stream(incoming[i]).filter(this::manaEdge).findFirst().orElseThrow();
                        inbox[first]=new Packet(remainder,batch.spell());
                    }
                    loss+=payment.total()-effect.total(); delivered+=effect.total();
                    casts.add(new Cast(sequence++,effect,batch.spell()));
                    continue;
                }
                int[] edges=Arrays.stream(outgoing[i]).filter(this::manaEdge).toArray();
                if (edges.length==0) continue;
                Mixture mana=batch.mana();
                if (rune.kind()==Rune.Kind.CONVERT) mana=mana.convert(rune.element(),blueprint.rules().conversion());
                if (rune.kind()==Rune.Kind.NOT) mana=mana.inverse(blueprint.rules().inversion());
                if (rune.kind()==Rune.Kind.SPLIT || rune.kind()==Rune.Kind.MERGE) {
                    mana=mana.retain(blueprint.rules().routingRetention());
                    if (rune.kind()==Rune.Kind.MERGE && mana.opposed()) mana=mana.retain(blueprint.rules().oppositionRetention());
                }
                SpellSpec spec=batch.spell().modify(rune);
                int[] weights=new int[edges.length]; Arrays.fill(weights,1);
                if (rune.kind()==Rune.Kind.SPLIT) for (int j=0;j<weights.length && j<rune.weights().size();j++) weights[j]=rune.weights().get(j);
                List<Mixture> shares=mana.split(weights);
                consume(i); loss+=batch.mana().total()-mana.total();
                for (int j=0;j<edges.length;j++) {
                    int e=edges[j]; Mixture before=shares.get(j), after=blueprint.routes().get(e).transmit(before,blueprint.rules());
                    loss+=before.total()-after.total();
                    if (!after.empty()) pending[e]=new Packet(after,spec);
                }
            } catch (IllegalArgumentException conflict) { /* Keep original inputs on an incompatible batch. */ }
        }
        for (int e=0;e<pending.length;e++) if (transit[e]==null && pending[e]!=null) { transit[e]=pending[e]; pending[e]=null; }
        // Internal control state freezes on power loss; signal() separately gates external validity.
        if (powered){signals=next;routeValidity=nextValidity;}
        if (powered && !blueprint.continuous() && --remaining==0) stop();
        if (powered && injectionCountdown>0) injectionCountdown--;
        return List.copyOf(casts);
    }
    private long inputTotal(int i) { long t=local[i]==null?0:local[i].mana().total(); for (int e:incoming[i]) if(inbox[e]!=null)t+=inbox[e].mana().total(); return t; }
    private void consume(int i) { local[i]=null; for(int e:incoming[i]) if(manaEdge(e))inbox[e]=null; }
    private static List<Mixture> proportionalTake(Mixture mana,long amount) {
        long[] take=mana.values(); long total=mana.total(), left=amount;
        for(int i=0;i<take.length;i++){take[i]=take[i]*amount/total;left-=take[i];}
        long[] original=mana.values();
        for(int i=0;left>0;i=(i+1)%take.length)if(take[i]<original[i]){take[i]++;left--;}
        Mixture paid=new Mixture(take); return List.of(paid,mana.minus(paid));
    }
}
