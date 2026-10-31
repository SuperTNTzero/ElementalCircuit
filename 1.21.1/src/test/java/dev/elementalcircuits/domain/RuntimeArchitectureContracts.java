package dev.elementalcircuits.domain;

import dev.elementalcircuits.adapter.*;
import dev.elementalcircuits.application.runtime.*;
import java.util.*;
import java.nio.file.*;

/** Causal behavior, aggregate mutation ownership, and executable dependency rules. */
public final class RuntimeArchitectureContracts {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static CircuitAssembly assembly(UUID id,Rune.Kind... kinds){return new CircuitAssembly(id,AssemblyResources.rules(),new TickCircuit(AssemblyContracts.line(true,Route.Payload.MANA,kinds)));}
    private static final class Host implements CircuitHost {
        final CircuitAssembly a;final ManaPool pool;final List<String> events;boolean enabled=true,fail;int commits,errors,outputs;
        Host(CircuitAssembly a,ManaPool pool,List<String> events){this.a=a;this.pool=pool;this.events=events;}
        public CircuitAssembly assembly(){return a;}
        public boolean prepare(){if(!enabled)a.stopAll();return enabled;}
        public ManaSource supply(){return pool;}
        public int steps(){if(fail)throw new IllegalStateException("isolated host failure");events.add("advance:"+a.identity());return 1;}
        public void emit(CircuitAssembly.Output out){outputs++;}
        public void afterAdvance(){}
        public void commit(){commits++;events.add("commit");}
        public void failed(RuntimeException error){errors++;}
    }
    private static void causal(){
        var a=assembly(new UUID(0,1),Rune.Kind.INPUT,Rune.Kind.MANA_SEND);var b=assembly(new UUID(0,2),Rune.Kind.MANA_RECEIVE,Rune.Kind.OUTPUT);
        var links=new CircuitLinks(AssemblyResources.rules());var assemblies=Map.of(a.identity(),a,b.identity(),b);
        links.connect(new CircuitAddress(a.identity(),1,2),new CircuitAddress(b.identity(),1,1),Route.Payload.MANA,assemblies::get);
        List<String> events=new ArrayList<>();var pool=new ManaPool(64*Fixed.ONE,0,0);var one=new Host(a,pool,events);var two=new Host(b,pool,events);
        CircuitExchange exchange=new CircuitExchange(){public void deliver(java.util.function.Function<UUID,CircuitAssembly> resolve){events.add("deliver");links.deliver(resolve);}public void publish(java.util.function.Function<UUID,CircuitAssembly> resolve){events.add("publish");links.publish(resolve);}};
        a.circuit(1).start();b.circuit(1).start();var scheduler=new CircuitScheduler();
        scheduler.tick(List.of(two,one),exchange);check(b.circuit(1).snapshot().supplied()==0,"第一世界步不穿透多阵");
        check(events.indexOf("deliver")<events.indexOf("advance:"+a.identity()) && events.indexOf("advance:"+a.identity())<events.indexOf("advance:"+b.identity()) && events.indexOf("publish")<events.indexOf("commit"),"稳定身份顺序与统一阶段边界");
        scheduler.tick(List.of(two,one),exchange);check(b.circuit(1).snapshot().supplied()==0 && links.connections().get(0).transit()!=null,"第二步仅发布运输");
        scheduler.tick(List.of(two,one),exchange);check(b.circuit(1).snapshot().supplied()>0 && two.outputs==0,"下一步接收仍遵守阵内运输延迟");
        scheduler.tick(List.of(two,one),exchange);check(two.outputs>0,"阵内运输到达后输出");
        var snapshot=links.snapshot();check(snapshot.accepted()==snapshot.loss()+snapshot.delivered(),"跨宿主账本守恒");
        // Duplicate identities must not get either VM or transport authority.
        var duplicate=new Host(CircuitAssembly.restore(a.snapshot()),pool,events);long before=a.circuit(1).snapshot().clock();
        scheduler.tick(List.of(one,duplicate),exchange);check(one.errors==1 && duplicate.errors==1 && a.circuit(1).snapshot().clock()==before,"身份重复双方拒绝推进");
        check(one.commits==5 && duplicate.commits==1 && !a.powered() && !duplicate.a.powered(),"身份冲突双方的停止现场都进入提交阶段");
        one.enabled=false;scheduler.tick(List.of(one),exchange);check(!a.powered() && one.commits==6,"解除装备条件停止并保存");
        one.enabled=true;a.circuit(1).start();one.fail=true;before=b.circuit(1).snapshot().clock();scheduler.tick(List.of(one,two),exchange);
        check(one.errors==2 && b.circuit(1).snapshot().clock()==before+1,"宿主异常不阻断其他宿主");
    }
    private static void stamps(){
        var a=assembly(UUID.randomUUID(),Rune.Kind.INPUT,Rune.Kind.MANA_SEND);var c=a.circuit(1);var stamp=a.changeStamp();
        a.clearDrives();a.publish();c.stop();check(stamp.equals(a.changeStamp()),"待机和空通道操作不产生保存请求");
        c.start();check(!stamp.equals(a.changeStamp()),"嵌套VM起阵改变聚合标记");stamp=a.changeStamp();c.tick(new ManaPool(64*Fixed.ONE,0,0));check(!stamp.equals(a.changeStamp()),"供给与运输推进改变标记");
        c.tick(new ManaPool(64*Fixed.ONE,0,0));Packet offered=c.offered(2);check(offered!=null,"发送口持有真实资源");stamp=a.changeStamp();c.export(2,offered);check(!stamp.equals(a.changeStamp()),"外部导出独立改变标记");
        c.stop();stamp=a.changeStamp();a.replace(1,AssemblyContracts.line(true,Route.Payload.MANA,Rune.Kind.MANA_RECEIVE,Rune.Kind.OUTPUT));check(!stamp.equals(a.changeStamp()),"更换VM不能与旧标记碰撞");
        c=a.circuit(1);c.start();stamp=a.changeStamp();c.receive(1,new Packet(Mixture.pure(Fixed.ONE),SpellSpec.EMPTY));check(!stamp.equals(a.changeStamp()),"外部收包独立改变标记");
        var signal=assembly(UUID.randomUUID(),Rune.Kind.SIGNAL_RECEIVE);c=signal.circuit(1);stamp=signal.changeStamp();c.drive(1,false);check(!stamp.equals(signal.changeStamp()),"低电平有效性也改变状态");stamp=signal.changeStamp();c.clearDrives();check(!stamp.equals(signal.changeStamp()),"撤销外部驱动被保存");
        var sw=assembly(UUID.randomUUID(),Rune.Kind.SWITCH);stamp=sw.changeStamp();sw.circuit(1).toggleManual(1);check(!stamp.equals(sw.changeStamp()),"手动门态改变标记");
        stamp=a.changeStamp();a.add(RuleResources.defaults());check(!stamp.equals(a.changeStamp()),"新增槽位改变标记");stamp=a.changeStamp();a.select(1);check(!stamp.equals(a.changeStamp()),"选择槽位改变标记");
        var restored=CircuitAssembly.restore(a.snapshot());check(!restored.changeStamp().equals(a.changeStamp()),"恢复实例不复用瞬态变更标记");
    }
    private static void completeTransportLoss(){
        var rules=AssemblyResources.rules();var lossy=new AssemblyRules(1,rules.maxSlots(),rules.maxLinks(),1,rules.externalSlots(),rules.range(),rules.blockCapacity(),rules.chargeStep());
        var a=assembly(UUID.randomUUID(),Rune.Kind.MANA_SEND);var b=assembly(UUID.randomUUID(),Rune.Kind.MANA_RECEIVE);var carriers=new HashMap<UUID,CircuitAssembly>();carriers.put(a.identity(),a);carriers.put(b.identity(),b);
        var graph=new CircuitLinks(lossy);graph.connect(new CircuitAddress(a.identity(),1,1),new CircuitAddress(b.identity(),1,1),Route.Payload.MANA,carriers::get);
        var c=a.circuit(1).snapshot();var ports=c.ports();ports[0]=new Packet(Mixture.pure(1),SpellSpec.EMPTY);
        var offered=new CircuitSnapshot(3,c.blueprint(),c.clock(),c.sequence(),false,c.remaining(),c.injectionCountdown(),1,0,0,c.reserve(),c.local(),c.inbox(),c.transit(),c.pending(),c.signals(),c.gates(),ports,c.drives(),c.emitted(),c.routeValidity(),c.driveValidity(),c.emittedValidity(),c.logic());
        carriers.put(a.identity(),new CircuitAssembly(a.identity(),rules,TickCircuit.restore(offered)));Object token=graph.changeToken();graph.publish(carriers::get);
        check(token!=graph.changeToken() && graph.snapshot().accepted()==1 && graph.snapshot().loss()==1 && graph.connections().get(0).transit()==null,"全部衰减为零时仍标记网络账本变更");
        check(CircuitLinks.restore(graph.snapshot()).snapshot().loss()==1,"全损耗现场可独立恢复");
    }
    private static void boundaries(){
        Path root=Path.of("src/main/java/dev/elementalcircuits");if(!Files.isDirectory(root))root=Path.of("minecraft-neoforge").resolve(root);
        for(String layer:List.of("domain","application"))try(var files=Files.walk(root.resolve(layer))){for(Path file:files.filter(p->p.toString().endsWith(".java")).toList()){
            String source=Files.readString(file);for(String forbidden:List.of("net.minecraft","net.neoforged.neoforge","dev.elementalcircuits.client","dev.elementalcircuits.server","dev.elementalcircuits.adapter","com.google.gson"))check(!source.contains(forbidden),"核心依赖越界："+file+" -> "+forbidden);
        }}catch(java.io.IOException error){throw new AssertionError(error);}
        try{String contract=Files.readString(root.resolve("domain/MagicCircuit.java"));check(!contract.contains("TickCircuit"),"抽象契约不依赖具体VM快照");String codecs=Files.readString(root.resolve("adapter/JsonCodecs.java"));check(!codecs.contains("dev.elementalcircuits.client"),"持久化编解码不依赖客户端类型");}catch(java.io.IOException error){throw new AssertionError(error);}
    }
    public static int run(){causal();stamps();completeTransportLoss();boundaries();return checks;}
}
