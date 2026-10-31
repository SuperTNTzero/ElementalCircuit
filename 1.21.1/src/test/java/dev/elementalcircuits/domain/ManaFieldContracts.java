package dev.elementalcircuits.domain;

import dev.elementalcircuits.adapter.*;
import java.util.*;

/** Conservation, local causality, persistence, shared finite supply and real VM intake. */
public final class ManaFieldContracts {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static void rejects(Runnable action,String message){boolean bad=false;try{action.run();}catch(RuntimeException expected){bad=true;}check(bad,message);}
    private static void ledger(ManaField field){var s=field.snapshot();long held=s.cells().stream().mapToLong(ManaField.Sample::amount).sum();check(s.ambientCreated()+s.injected()==held+s.extracted()+s.dissipated(),"场的全部注入、汲取和耗散严格守恒");}
    private static Blueprint line(boolean continuous,Rune.Kind...kinds){return AssemblyContracts.line(continuous,Route.Payload.MANA,kinds);}
    private static void diffusion(){
        var rules=ManaFieldResources.defaults();var f=new ManaField(rules);var c=new ManaField.Cell(0,0,0);var east=c.offset(1,0,0);var far=c.offset(2,0,0);
        check(f.amount(c)==0 && f.snapshot().cells().isEmpty(),"只读查询不生成环境资源");
        f.discover(c);f.discover(east);f.discover(far);var before=f.snapshot();check(!f.discover(c) && before.equals(f.snapshot()),"重复激活不补充魔力");
        f.inject(c,64*Fixed.ONE);long originalFar=f.amount(far);
        for(int i=0;i<rules.stepTicks();i++)f.tick(List.of(c,east,far));
        check(f.amount(c)<72*Fixed.ONE && f.amount(east)>rules.ambient(),"高浓度向相邻低浓度单元扩散");
        check(f.amount(far)<=originalFar,"同步读取旧场值，不允许一步跨过中间单元");ledger(f);
        long frozen=f.amount(far);for(int i=0;i<40;i++)f.tick(List.of(c,east));check(f.amount(far)==frozen,"未加载格点同时冻结扩散与耗散");
        var restored=ManaField.restore(f.snapshot());check(restored.snapshot().equals(f.snapshot()),"完整保存场量、相位与账本");
        for(int i=0;i<100;i++){f.tick(List.of(far,c,east));restored.tick(List.of(east,far,c));check(f.snapshot().equals(restored.snapshot()),"恢复后轨迹不受容器迭代顺序影响");ledger(f);}
        var stable=f.snapshot();check(!f.debit(c,f.amount(c)+1) && f.snapshot().equals(stable),"余额不足原子拒绝");
        rejects(()->new ManaField.Cell(Integer.MIN_VALUE,0,0),"外部坐标边界");
        check(ManaField.Cell.at(-1,-1,-9).equals(new ManaField.Cell(-1,-1,-2)),"负坐标向下取整，不错接世界单元");
        var edge=new ManaField(rules);var limit=new ManaField.Cell(3750000,511,3750000);edge.discover(limit);for(int i=0;i<rules.stepTicks();i++)edge.tick(List.of(limit));ledger(edge);
        long exhausted=Long.MAX_VALUE-rules.ambient();var counted=ManaField.restore(new ManaField.Snapshot(1,rules,0,rules.ambient(),exhausted,exhausted,0,List.of(new ManaField.Sample(c,rules.ambient()))));
        var end=counted.snapshot();check(counted.inject(c,1)==0 && !counted.discover(east) && counted.snapshot().equals(end),"总收支计数耗尽时拒绝新资源，现场不变且不回绕");
        var bad=f.snapshot();rejects(()->ManaField.restore(new ManaField.Snapshot(1,bad.rules(),bad.phase(),bad.ambientCreated(),bad.injected()+1,bad.extracted(),bad.dissipated(),bad.cells())),"不守恒的存档拒绝");
        var duplicate=new ArrayList<>(bad.cells());duplicate.add(bad.cells().get(0));rejects(()->ManaField.restore(new ManaField.Snapshot(1,bad.rules(),bad.phase(),bad.ambientCreated(),bad.injected(),bad.extracted(),bad.dissipated(),duplicate)),"重复格点不能复制资源");
    }
    private static void nodes(){
        var r=ManaFieldResources.defaults();var natural=new ManaNode(r,ManaNode.Kind.NATURAL);var made=new ManaNode(r,ManaNode.Kind.ARTIFICIAL);
        check(natural.remaining()==4096*Fixed.ONE && made.remaining()==0,"天然有限地脉，人工节点初始空载");check(!natural.insert(),"天然节点不接受人造燃料");
        check(made.insert() && made.remaining()==64*Fixed.ONE,"一枚真实水晶加入64点储量");
        var f=new ManaField(r);var c=new ManaField.Cell(0,0,0);f.discover(c);long total=made.remaining();
        for(int i=0;i<80;i++){long accepted=f.inject(c,made.emission());made.emitted(accepted);var state=made.snapshot();check(state.initial()+state.received()==state.remaining()+state.emitted(),"节点守恒");ledger(f);}
        check(made.remaining()==0 && f.snapshot().injected()==total,"耗尽后不能继续凭空释放");
        for(int i=0;i<8;i++)check(made.insert(),"空余容量允许完整水晶");var before=made.snapshot();check(!made.insert() && made.snapshot().equals(before),"满载拒绝不消费燃料");
        check(ManaNode.restore(r,before).snapshot().equals(before),"节点恢复不重新初始化储量");
        rejects(()->ManaNode.restore(r,new ManaNode.State(1,ManaNode.Kind.ARTIFICIAL,0,64*Fixed.ONE,0,65*Fixed.ONE)),"节点不守恒存档拒绝");
        var full=new ManaField(r);full.discover(c);full.inject(c,r.capacity());var n=new ManaNode(r,ManaNode.Kind.NATURAL);long accepted=full.inject(c,n.emission());n.emitted(accepted);check(accepted==0 && n.remaining()==r.naturalCapacity(),"场满时节点保留燃料");
    }
    private static void intake(){
        var r=ManaFieldResources.defaults();var f=new ManaField(r);var cell=new ManaField.Cell(0,0,0);f.discover(cell);
        ManaSource tap=new ManaSource(){public long available(){return f.amount(cell);}public boolean debit(long q){return f.debit(cell,q);}};
        var vm=new TickCircuit(line(true,Rune.Kind.ABSORB,Rune.Kind.CONVERT,Rune.Kind.OUTPUT));var player=new ManaPool(64*Fixed.ONE,0,0);check(vm.start(),"汲取源能够手动起阵");long output=0;
        for(int i=0;i<20;i++){for(var cast:vm.tick(player,ReleasePermit.UNLIMITED,tap))output+=cast.mana().total();var s=vm.snapshot();check(s.supplied()==vm.held()+s.loss()+s.delivered(),"汲取经VM转换后账本守恒");ledger(f);}
        check(output>0 && player.snapshot().spent()==0 && f.snapshot().extracted()==vm.snapshot().supplied(),"环境取能不偷用玩家槽，场的提取等于法阵收入");
        var saved=JsonCodecs.readSnapshot(JsonCodecs.snapshot(vm));check(saved.snapshot().version()==5 && saved.definition().equals(vm.definition()),"新现场协议保留专用汲取源");
        check(JsonCodecs.snapshot(saved).equals(JsonCodecs.snapshot(vm)),"汲取源完整文本往返");
        var waiting=new TickCircuit(line(true,Rune.Kind.ABSORB,Rune.Kind.OUTPUT));waiting.start();for(int i=0;i<10;i++)waiting.tick(player,ReleasePermit.UNLIMITED,ManaSource.NONE);
        check(waiting.powered() && waiting.held()==0 && waiting.snapshot().supplied()==0,"贫瘠场等待，不退回玩家供给");
        var supply=new ManaPool(4*Fixed.ONE,0,0);check(!waiting.tick(player,ReleasePermit.UNLIMITED,supply).isEmpty() || waiting.held()>0,"新的场供给能够被等待的法阵领取");
        waiting.stop();long spent=supply.snapshot().spent();for(int i=0;i<10;i++)waiting.tick(player,ReleasePermit.UNLIMITED,supply);check(supply.snapshot().spent()==spent,"停供残余可流动但不能继续汲取");
        var blocked=new TickCircuit(line(true,Rune.Kind.ABSORB));blocked.start();var pool=new ManaPool(128*Fixed.ONE,0,0);
        for(int i=0;i<100;i++)blocked.tick(player,ReleasePermit.UNLIMITED,pool);check(blocked.held()==blocked.definition().rules().nodeCapacity() && pool.snapshot().spent()==blocked.held(),"孤立汲取源满缓存后不再扣场量");
        blocked.stop();var next=blocked.reconfigure(line(true,Rune.Kind.ABSORB,Rune.Kind.OUTPUT));next.start();next.tick(player,ReleasePermit.UNLIMITED,ManaSource.NONE);check(next.snapshot().supplied()==blocked.snapshot().supplied(),"重配回收的资源可复用，不再收费");
        rejects(()->JsonCodecs.readSnapshot(JsonCodecs.snapshot(vm).replace("\"version\":5","\"version\":4")),"不能用旧VM版本伪装汲取现场");
        var a=Rune.create(1,Rune.Kind.OR,new Hex(0,0));var b=Rune.create(2,Rune.Kind.ABSORB,new Hex(1,0));
        rejects(()->new Blueprint(1,vm.definition().rules(),List.of(a,b),List.of(new Route(1,2,Route.Payload.MANA,List.of(a.cell(),b.cell()))),true),"汲取符文不接受反向魔力入线");
    }
    public static int run(){diffusion();nodes();intake();return checks;}
}
