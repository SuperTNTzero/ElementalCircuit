package dev.elementalcircuits.domain;

import dev.elementalcircuits.adapter.*;
import dev.elementalcircuits.client.effect.*;
import dev.elementalcircuits.client.editor.*;
import dev.elementalcircuits.application.observation.*;
import java.util.*;

/** Geometry, real VM output, exact continuation, inter-circuit packets and disposable meshes. */
public final class ShapeContracts {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static void rejects(Runnable r,String why){boolean rejected=false;try{r.run();}catch(RuntimeException e){rejected=true;}check(rejected,why);}
    public static final Rune.Kind[] KINDS={Rune.Kind.FAN,Rune.Kind.WALL,Rune.Kind.RING,Rune.Kind.COLUMN,Rune.Kind.CROSS,Rune.Kind.DOME};
    private static ShapeVolume volume(Rune.Kind kind){return new ShapeVolume(SpellSpec.Shape.valueOf(kind.name()),2,ShapeResources.rules());}
    public static int run(){
        for(var kind:KINDS){
            var s=SpellSpec.EMPTY.modify(Rune.create(1,kind,new Hex(0,0)));
            check(s.shape().name().equals(kind.name()),"新符文改变形状维度："+kind);
            var directed=s.modify(Rune.create(2,Rune.Kind.DIRECTED,new Hex(1,0)));
            check(directed.compatibleOutput(),"六种指向形状均可释放："+kind);
            check(s.compatibleOutput()==(kind!=Rune.Kind.FAN && kind!=Rune.Kind.WALL),"喷洒/墙明确要求指向："+kind);
            check(!directed.modify(Rune.create(3,Rune.Kind.CHAIN,new Hex(2,0))).compatibleOutput(),"不静默忽略弹道派生："+kind);
            rejects(()->s.modify(Rune.create(3,Rune.Kind.SPHERE,new Hex(2,0))),"两种形状冲突保留原包："+kind);
            check(RuneCatalog.Group.SHAPE.children.contains(kind) && !RuneShapes.of(kind).isEmpty(),"分类、文本和静态符印齐全");
            var v=volume(kind);check(!v.contains(Double.NaN,0,0) && !v.contains(0,Double.POSITIVE_INFINITY,0) && !v.contains(40,40,40),"非法坐标/远处不命中");
            rejects(()->new ShapeVolume(s.shape(),0,ShapeResources.rules()),"拒绝零规格体积");
            runtime(kind);mesh(kind);
        }
        var fan=volume(Rune.Kind.FAN);check(fan.contains(0,0,3.9) && fan.contains(1,0,2),"扇面覆盖前方与侧翼");
        check(!fan.contains(0,0,-1) && !fan.contains(3,0,3) && !fan.contains(0,1,2),"扇面排除后方、圆弧外和高度外");
        check(!new SpellSpec(SpellSpec.Method.DIRECTED,SpellSpec.Shape.FAN,0,4,0,0).compatibleOutput(),"喷洒不吞掉射程参数");
        var wall=volume(Rune.Kind.WALL);check(wall.contains(1.8,1.8,0) && !wall.contains(0,0,1),"墙为薄竖直矩形");
        var ring=volume(Rune.Kind.RING);check(ring.contains(1.8,0,0) && !ring.contains(0,0,0) && !ring.contains(1.8,1,0),"环带中心空且有高度约束");
        var column=volume(Rune.Kind.COLUMN);check(column.contains(.8,1.8,0) && !column.contains(1.2,0,0),"立柱高而窄");
        var cross=volume(Rune.Kind.CROSS);check(cross.contains(1.8,0,0) && cross.contains(0,0,-1.8) && !cross.contains(1,0,1),"十字保留四臂且排除斜角");
        var dome=volume(Rune.Kind.DOME);check(dome.contains(0,1.8,0) && !dome.contains(0,-1.8,0) && !dome.contains(0,.5,0),"穹顶排除下半球及壳内");
        receive();return checks;
    }
    private static void runtime(Rune.Kind kind){
        var b=ShapeExamples.create(RuleResources.defaults(),kind);
        check(JsonCodecs.readBlueprint(JsonCodecs.blueprint(b)).equals(b),"蓝图纯配置往返："+kind);
        var c=new TickCircuit(b);var pool=new ManaPool(64*Fixed.ONE,0,0);c.start();long output=0;
        for(int i=0;i<45;i++){
            String text=JsonCodecs.snapshot(c);check(c.snapshot().version()==6,"扩展形状采用现场v6");
            var a=JsonCodecs.readSnapshot(text);var p=JsonCodecs.readPool(JsonCodecs.pool(pool),0,0);
            var result=c.tick(pool);check(result.equals(a.tick(p)) && JsonCodecs.snapshot(c).equals(JsonCodecs.snapshot(a)),"任意边界恢复输出和缓存完全一致");
            for(var cast:result){check(cast.spell().shape().name().equals(kind.name()),"真实VM输出具体形状");output+=cast.mana().total();}
            var state=c.snapshot();check(state.supplied()==state.loss()+state.delivered()+c.held(),"逐步账本守恒");
        }
        check(output>0 && output<pool.snapshot().spent(),"新形状仅使用真实支付的输出量");
        var tree=com.google.gson.JsonParser.parseString(JsonCodecs.snapshot(c)).getAsJsonObject();tree.addProperty("version",5);
        rejects(()->JsonCodecs.readSnapshot(tree.toString()),"拒绝将新符文伪装为旧现场");
    }
    private static void receive(){
        for(var kind:KINDS){
            var c=new TickCircuit(AssemblyContracts.line(true,Route.Payload.MANA,Rune.Kind.MANA_RECEIVE,Rune.Kind.OUTPUT));c.start();
            var spell=new SpellSpec(SpellSpec.Method.DIRECTED,SpellSpec.Shape.valueOf(kind.name()),0,0,0,0);
            check(c.receive(1,new Packet(Mixture.pure(Fixed.ONE).convert(Element.WATER,Fixed.ONE),spell)),"接收跨阵已修饰包");
            check(c.snapshot().version()==6,"没有形状符文的接收阵也保存v6包");
            var restored=JsonCodecs.readSnapshot(JsonCodecs.snapshot(c));var pool=new ManaPool(64*Fixed.ONE,0,0);long amount=0;
            for(int i=0;i<8;i++)for(var cast:restored.tick(pool)){check(cast.spell().shape()==spell.shape(),"接收后不丢失形状");amount+=cast.mana().total();}
            check(amount>0 && amount<=Fixed.ONE,"跨阵传递仍有限额");
            var tree=com.google.gson.JsonParser.parseString(JsonCodecs.snapshot(c)).getAsJsonObject();tree.addProperty("version",5);
            rejects(()->JsonCodecs.readSnapshot(tree.toString()),"旧现场也拒绝缓存里的新形状");
        }
    }
    private static void mesh(Rune.Kind kind){
        var points=new ArrayList<SpellGeometry.V>();
        VolumeGeometry.emit(volume(kind),(a,b)->{points.add(a);points.add(b);},(a,b,c,d)->{});
        var b=volume(kind).bounds();for(var p:points)check(p.x()>=b.minX()-1e-8 && p.x()<=b.maxX()+1e-8 && p.y()>=b.minY()-1e-8 && p.y()<=b.maxY()+1e-8 && p.z()>=b.minZ()-1e-8 && p.z()<=b.maxZ()+1e-8,"轮廓位于规则包围盒内："+kind);
        rejects(()->new SpellVisual(SpellVisual.Kind.valueOf(kind.name()),Element.WATER,new SpellVisual.Point(0,0,-8),new SpellVisual.Point(0,0,1),.2f,1),"网络观察拒绝非整数体积，不能使渲染器创建零半径");
        var event=new SpellVisual(SpellVisual.Kind.valueOf(kind.name()),Element.WATER,new SpellVisual.Point(0,0,-8),new SpellVisual.Point(0,0,1),2,1);
        int count=SpellGeometry.emit(List.of(new VisualTimeline.Frame(event,0,10)),new SpellGeometry.V(0,0,0),new SpellGeometry.V(1,0,0),new SpellGeometry.V(0,1,0),0,(a,c,d,e,s,color,alpha)->check(Double.isFinite(a.length()+c.length()+d.length()+e.length()),"形状网格有限"));
        check(count>20 && count<=518,"每次观察使用有限且独立的轮廓网格："+kind);
    }
}
