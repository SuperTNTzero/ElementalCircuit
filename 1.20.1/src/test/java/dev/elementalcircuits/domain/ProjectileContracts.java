package dev.elementalcircuits.domain;

import dev.elementalcircuits.adapter.*;
import dev.elementalcircuits.client.*;
import dev.elementalcircuits.client.editor.*;
import com.google.gson.*;
import java.util.*;
import java.nio.file.*;

public final class ProjectileContracts {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static void rejects(Runnable action,String why){boolean rejected=false;try{action.run();}catch(RuntimeException e){rejected=true;}check(rejected,why);}
    private static Rune effect(Rune.Kind kind,int n){return new Rune(20,kind,new Hex(0,1),Element.FIRE,n*Fixed.ONE,true,false,List.of(1));}
    private static void definitions(){
        var rules=ProjectileResources.rules();check(rules.equals(ProjectileRules.protocolV1()),"外置版本化派生规则");check(JsonCodecs.readProjectileRules(JsonCodecs.projectileRules(rules)).equals(rules),"飞行冻结规则往返");
        for(var kind:List.of(Rune.Kind.BURST,Rune.Kind.BOUNCE,Rune.Kind.HOMING)){
            var rune=Rune.create(1,kind,new Hex(0,0),LogicResources.defaults(),rules);check(kind.manaIn() && kind.manaOut() && !kind.signalIn() && !kind.signalOut(),"派生符文显式处理魔力载荷");
            check(RuneCatalog.Group.EFFECT.children.contains(kind) && !RuneShapes.of(kind).isEmpty(),"派生效果有独立分类与符形");
            var editor=new EditorDraft(Blueprint.empty(RuleResources.defaults()));editor.place(kind,new Hex(0,0));check(editor.get().rune(1).value()==rune.value(),"实际落笔使用外置默认参数");
            rejects(()->new Rune(1,kind,new Hex(0,0),Element.FIRE,rune.value()+1,true,false,List.of(1)),"拒绝小数派生参数");
        }
        rejects(()->effect(Rune.Kind.BURST,5),"爆裂半径有界");rejects(()->effect(Rune.Kind.BOUNCE,0),"弹跳次数非零");rejects(()->effect(Rune.Kind.HOMING,1),"追踪范围最小两格");
        rejects(()->new ProjectileEffects(1,5,0),"状态参数同样受约束");
        var spec=new SpellSpec(SpellSpec.Method.DIRECTED,SpellSpec.Shape.SPHERE,0,0,0,0).modify(effect(Rune.Kind.BURST,3)).modify(effect(Rune.Kind.BOUNCE,2)).modify(effect(Rune.Kind.HOMING,8));
        check(spec.compatibleOutput() && spec.effects().equals(new ProjectileEffects(3,2,8)),"三个独立派生效果可组合");
        check(spec.modify(effect(Rune.Kind.BURST,3)).equals(spec),"同值重复幂等，不叠层放大");rejects(()->spec.modify(effect(Rune.Kind.BURST,2)),"不同阈值冲突，不覆盖既有修饰");
        check(!new SpellSpec(SpellSpec.Method.AREA,SpellSpec.Shape.SPHERE,0,0,0,0,spec.effects()).compatibleOutput(),"范围方式拒绝投射派生");
        check(!new SpellSpec(SpellSpec.Method.DIRECTED,SpellSpec.Shape.SHIELD,0,0,0,0,spec.effects()).compatibleOutput(),"盾牌拒绝投射派生");
        check(!new SpellSpec(SpellSpec.Method.DIRECTED,SpellSpec.Shape.BEAM,0,0,0,0,new ProjectileEffects(0,1,0)).compatibleOutput(),"瞬时光束拒绝弹跳");
        check(new SpellSpec(SpellSpec.Method.DIRECTED,SpellSpec.Shape.BEAM,0,0,0,0,new ProjectileEffects(3,0,0)).compatibleOutput(),"光束可在首次命中处爆裂");
        check(!new SpellSpec(SpellSpec.Method.DIRECTED,SpellSpec.Shape.SPHERE,0,0,2,0,new ProjectileEffects(3,0,0)).compatibleOutput(),"显式半径冲突不静默覆盖");
        var packet=new Packet(Mixture.pure(4*Fixed.ONE),spec);check(JsonCodecs.readPacket(JsonCodecs.packetText(packet)).equals(packet),"派生修饰随包完整编解码");
        var old=JsonParser.parseString(JsonCodecs.packetText(packet)).getAsJsonObject();old.getAsJsonObject("spell").remove("effects");check(JsonCodecs.readPacket(old.toString()).spell().effects().empty(),"历史包显式初始化无派生效果");
        old.getAsJsonObject("spell").add("effects",JsonNull.INSTANCE);rejects(()->JsonCodecs.readPacket(old.toString()),"不能把损坏效果当作旧包");
    }
    private static void budgets(){
        var rules=ProjectileResources.rules();var random=new Random(29108);
        for(int i=0;i<240;i++){
            long[] values=new long[17];for(int n=0;n<17;n++)values[n]=random.nextInt(50000);var mana=new Mixture(values);
            var spec=new SpellSpec(SpellSpec.Method.DIRECTED,SpellSpec.Shape.SPHERE,0,0,0,0,new ProjectileEffects(3,4,8));var packet=new Packet(mana,spec);
            var paid=ProjectilePayloads.guidance(packet,rules);check(paid.total()<=mana.total()/2,"追踪预付不扩大任何总量");
            for(int b=0;b<4;b++){var next=ProjectilePayloads.bounce(paid,rules);check(next.total()<=paid.total()*3/4,"每次反射扣除剩余预算");paid=next;}
            var parts=SpellPayloads.split(packet);check(parts.stream().mapToLong(p->p.mana().total()).sum()==mana.total() && parts.stream().allMatch(p->p.spell().equals(spec)),"混合奥术分包保留同一修饰且逐成分守恒");
        }
    }
    private static void runtime(){
        var board=ProjectileExamples.combined(RuleResources.defaults(),LogicResources.defaults(),ProjectileResources.rules());check(board.runes().size()==8 && board.routes().size()==7,"可编辑木质八符文示例");
        var circuit=new TickCircuit(board);var pool=new ManaPool(64*Fixed.ONE,0,0);circuit.start();int casts=0;
        for(int i=0;i<44;i++){
            var clone=JsonCodecs.readSnapshot(JsonCodecs.snapshot(circuit));var twin=ManaPool.restore(pool.snapshot(),0,0);
            var result=circuit.tick(pool);var copy=clone.tick(twin);check(result.equals(copy) && JsonCodecs.snapshot(circuit).equals(JsonCodecs.snapshot(clone)),"任意边界恢复保持修饰及后续运行轨迹");
            for(var cast:result){casts++;check(cast.spell().effects().equals(new ProjectileEffects(3,2,8)),"真实VM输出完整三种效果");}
            var state=circuit.snapshot();check(state.supplied()==state.loss()+state.delivered()+circuit.held(),"修饰没有复制魔力，VM账本仍守恒");
        }
        check(casts==1,"普通单次投射不隐式派生额外法术");
        check(BlueprintTextCode.decode(BlueprintTextCode.encode(board)).equals(board),"文本码保存配置");
        var invalid=AssemblyContracts.line(false,Route.Payload.MANA,Rune.Kind.INPUT,Rune.Kind.DIRECTED,Rune.Kind.SHIELD,Rune.Kind.BOUNCE,Rune.Kind.OUTPUT);var blocked=new TickCircuit(invalid);var supply=new ManaPool(64*Fixed.ONE,0,0);blocked.start();for(int i=0;i<30;i++)check(blocked.tick(supply).isEmpty(),"不兼容形状保持输入而不产生错误派生");check(blocked.held()>0,"不兼容输出不吞掉缓存");
        // Effects cross the same ordinary network used by all other modifiers.
        var source=new CircuitAssembly(UUID.randomUUID(),AssemblyResources.rules(),new TickCircuit(AssemblyContracts.line(false,Route.Payload.MANA,Rune.Kind.INPUT,Rune.Kind.BOUNCE,Rune.Kind.MANA_SEND)));
        source.add(RuleResources.defaults());source.replace(2,AssemblyContracts.line(true,Route.Payload.MANA,Rune.Kind.MANA_RECEIVE,Rune.Kind.DIRECTED,Rune.Kind.SPHERE,Rune.Kind.OUTPUT));source.links().connect(new CircuitAddress(source.identity(),1,3),new CircuitAddress(source.identity(),2,1),Route.Payload.MANA,id->source);source.circuit(1).start();source.circuit(2).start();var bank=new ManaPool(64*Fixed.ONE,0,0);int linked=0;
        for(int i=0;i<30;i++)for(var cast:source.tick(bank,1)){linked++;check(cast.cast().spell().effects().bounces()==2,"派生修饰跨法阵传输不丢失");}
        check(linked==1,"跨阵不额外复制输出");
        try{var dir=Path.of("build/projectile-examples");Files.createDirectories(dir);Files.writeString(dir.resolve("派生投射.json"),JsonCodecs.blueprint(board));Files.writeString(dir.resolve("派生投射.ec1.txt"),BlueprintTextCode.encode(board));}catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
    public static int run(){definitions();budgets();runtime();return checks;}
}
