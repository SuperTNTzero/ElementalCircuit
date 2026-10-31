package dev.elementalcircuits.domain;

import dev.elementalcircuits.adapter.*;
import dev.elementalcircuits.application.runtime.*;
import com.google.gson.*;
import dev.elementalcircuits.client.editor.EditorRequests;
import java.util.*;

/** Lifecycle transition contracts, including interrupted and restored states. */
public final class StateMachineContracts {
    private static int checks;
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    private static void rejects(Runnable action,String message){boolean rejected=false;try{action.run();}catch(RuntimeException e){rejected=true;}check(rejected,message);}
    private static void restart(){
        var c=new TickCircuit(AssemblyContracts.line(true,Route.Payload.SIGNAL,Rune.Kind.SIGNAL,Rune.Kind.SIGNAL_SEND));var pool=new ManaPool(64*Fixed.ONE,0,0);c.start();c.tick(pool);c.tick(pool);check(c.validSignal(2) && c.signal(2),"运行后存在真实有效高信号");
        c.stop();c.start();check(!c.validSignal(2),"重新起阵不能立即复活旧有效信号");
        c.tick(pool);check(c.validSignal(2),"重新支付控制成本后才重新发布有效信号");
    }
    private static void sleepingReserve(){
        var c=new TickCircuit(AssemblyContracts.line(true,Route.Payload.MANA,Rune.Kind.INPUT,Rune.Kind.OUTPUT));c.start();c.tick(new ManaPool(64*Fixed.ONE,0,0));c.stop();c=c.reconfigure(Blueprint.empty(c.definition().rules()));var a=new CircuitAssembly(UUID.randomUUID(),AssemblyResources.rules(),c);long before=c.snapshot().clock();String state=JsonCodecs.assembly(a);check(c.held()>0,"配置回收拥有真实储量");
        for(int i=0;i<10;i++)a.advance(new ManaPool(64*Fixed.ONE,0,0),1);check(c.snapshot().clock()==before && JsonCodecs.assembly(a).equals(state),"只有回收储备的停止法阵休眠，不空转时钟");
    }
    private static void invalidLifecycle(){
        var c=new TickCircuit(AssemblyContracts.line(false,Route.Payload.MANA,Rune.Kind.INPUT,Rune.Kind.OUTPUT));c.start();var json=JsonParser.parseString(JsonCodecs.snapshot(c)).getAsJsonObject();json.addProperty("remaining",0);rejects(()->JsonCodecs.readSnapshot(json.toString()),"运行中且窗口耗尽属于非法现场");
        var continuous=new TickCircuit(AssemblyContracts.line(true,Route.Payload.MANA,Rune.Kind.INPUT,Rune.Kind.OUTPUT));continuous.start();var j=JsonParser.parseString(JsonCodecs.snapshot(continuous)).getAsJsonObject();j.addProperty("injectionCountdown",-1);rejects(()->JsonCodecs.readSnapshot(j.toString()),"持续供给不能处于单次已注入状态");
    }
    private static void requestCorrelation(){
        var requests=new EditorRequests(3);var refresh=requests.begin(5);var command=requests.begin(0);
        check(requests.complete(refresh)==EditorRequests.Reply.STALE && requests.waiting(),"修改取代旧刷新，旧回复不能覆盖新状态或解除等待");
        rejects(()->requests.begin(5),"修改等待期间不插入另一份刷新观察");
        check(requests.complete(UUID.randomUUID())==EditorRequests.Reply.STALE && requests.waiting(),"其他会话回复不改变等待状态");
        check(requests.complete(command)==EditorRequests.Reply.COMMAND && !requests.waiting(),"保存回复独立完成");
        check(requests.complete(command)==EditorRequests.Reply.STALE,"重复回复不重复完成");
        refresh=requests.begin(5);command=requests.begin(0);requests.complete(command);check(requests.complete(refresh)==EditorRequests.Reply.STALE,"修改先完成时迟到的旧刷新也不能回滚观察");
        refresh=requests.begin(5);check(requests.complete(refresh)==EditorRequests.Reply.REFRESH,"没有被替代的独立刷新可以完成");
        check(requests.complete(EditorRequests.PUSH)==EditorRequests.Reply.PUSH,"独立服务器推送仍可显示");
        command=requests.begin(4);rejects(()->requests.begin(8),"同一时间只允许一个改动请求");check(!requests.tick() && !requests.tick() && requests.tick() && !requests.waiting(),"超时退出等待且不自动重发修改");
        check(requests.complete(command)==EditorRequests.Reply.STALE,"超时后的迟到回复不冒充新请求");
        refresh=requests.begin(5);requests.tick();requests.tick();check(!requests.tick() && !requests.waiting(),"刷新超时不会伪造修改失败");
        for(int i=0;i<100;i++){var one=requests.begin(i%2==0?5:0);check(requests.complete(one)==(i%2==0?EditorRequests.Reply.REFRESH:EditorRequests.Reply.COMMAND),"请求循环可重复回到空闲态");}
        var p=new ManaPool(64*Fixed.ONE,Fixed.ONE,40);rejects(()->ManaPool.restore(new ManaPool.State(1,64*Fixed.ONE,63*Fixed.ONE,2,1,Fixed.ONE),Fixed.ONE,40),"恢复等待期不能同时存在恢复余数");
    }
    private static void transitionSequences(){
        var random=new Random(20261029);List<Blueprint> boards=List.of(AssemblyContracts.line(false,Route.Payload.MANA,Rune.Kind.INPUT,Rune.Kind.SWITCH,Rune.Kind.OUTPUT),AssemblyContracts.line(true,Route.Payload.MANA,Rune.Kind.INPUT,Rune.Kind.SWITCH,Rune.Kind.OUTPUT),AssemblyContracts.line(true,Route.Payload.SIGNAL,Rune.Kind.CLOCK,Rune.Kind.COUNTER,Rune.Kind.TOGGLE,Rune.Kind.SIGNAL_SEND));
        for(var board:boards)for(int run=0;run<12;run++){
            var c=new TickCircuit(board);var pool=new ManaPool(64*Fixed.ONE,4*Fixed.ONE,40);
            for(int step=0;step<120;step++){
                switch(random.nextInt(7)){
                    case 0->c.start();case 1->c.stop();case 2->{if(board.runes().get(1).kind()==Rune.Kind.SWITCH)c.toggleManual(2);else c.resetLogic(2);}
                    case 3->{if(c.editable())c=c.reconfigure(board);}
                    default->{String before=JsonCodecs.snapshot(c);boolean idle=!c.needsStep();c.tick(pool);if(idle)check(before.equals(JsonCodecs.snapshot(c)),"完全静止的推进为空操作");}
                }
                pool.tick();var state=c.snapshot();check(state.supplied()==c.held()+state.loss()+state.delivered() && pool.snapshot().spent()==state.supplied(),"任意起停/恢复/重配序列仍与玩家支出守恒");
                String encoded=JsonCodecs.snapshot(c);var restored=JsonCodecs.readSnapshot(encoded);check(JsonCodecs.snapshot(restored).equals(encoded) && restored.phase()==c.phase(),"每次合法转换都可完整恢复当前生命周期");
                var restoredPool=JsonCodecs.readPool(JsonCodecs.pool(pool),4*Fixed.ONE,40);check(restoredPool.snapshot().equals(pool.snapshot()),"供给恢复等待和余数与法阵一起保持");c=restored;pool=restoredPool;
            }
        }
    }
    private static final class Host implements CircuitHost {
        final CircuitAssembly a=new CircuitAssembly(UUID.randomUUID(),AssemblyResources.rules(),new TickCircuit(AssemblyContracts.line(true,Route.Payload.MANA,Rune.Kind.INPUT,Rune.Kind.OUTPUT)));boolean broken;int commits;
        Host(boolean broken){this.broken=broken;a.circuit(1).start();}
        public CircuitAssembly assembly(){return a;}
        public boolean prepare(){if(broken)throw new IllegalStateException("prepare failed");return true;}
        public ManaSource supply(){return new ManaPool(64*Fixed.ONE,0,0);}
        public int steps(){return 1;}
        public void emit(CircuitAssembly.Output out){}
        public void afterAdvance(){}
        public void commit(){commits++;}
        public void failed(RuntimeException error){throw new IllegalStateException("report also failed");}
    }
    private static void reportingFailure(){
        var good=new Host(false);var bad=new Host(true);var exchange=new CircuitExchange(){public void deliver(java.util.function.Function<UUID,CircuitAssembly> r){}public void publish(java.util.function.Function<UUID,CircuitAssembly> r){}};
        new CircuitScheduler().tick(List.of(bad,good),exchange);check(good.commits==1 && good.a.circuit(1).snapshot().clock()==1,"故障报告再次失败也不能中断其他宿主");check(!bad.a.powered(),"发生运行故障的宿主当步即停止");
    }
    public static int run(){List<String> failures=new ArrayList<>();for(Runnable test:List.<Runnable>of(StateMachineContracts::restart,StateMachineContracts::sleepingReserve,StateMachineContracts::invalidLifecycle,StateMachineContracts::requestCorrelation,StateMachineContracts::transitionSequences,StateMachineContracts::reportingFailure))try{test.run();}catch(Throwable error){failures.add(error.toString());}if(!failures.isEmpty())throw new AssertionError(String.join("\n",failures));return checks;}
}
