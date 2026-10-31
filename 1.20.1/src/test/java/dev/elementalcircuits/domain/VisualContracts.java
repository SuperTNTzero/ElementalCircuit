package dev.elementalcircuits.domain;

import dev.elementalcircuits.application.observation.*;
import dev.elementalcircuits.client.editor.*;
import dev.elementalcircuits.client.effect.*;
import java.util.*;
import javax.imageio.ImageIO;

/** Asset coverage, bounded observations, lifetime and exact visual/authority separation. */
public final class VisualContracts {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static void rejects(Runnable r,String why){boolean rejected=false;try{r.run();}catch(RuntimeException e){rejected=true;}check(rejected,why);}
    private static SpellVisual event(SpellVisual.Kind kind){return new SpellVisual(kind,Element.FIRE,new SpellVisual.Point(0,0,0),new SpellVisual.Point(0,0,1),1,8);}
    public static int run(){
        try{
            var atlas=ImageIO.read(VisualContracts.class.getResourceAsStream("/assets/elementalcircuits/textures/gui/glyphs.png"));
            check(atlas.getWidth()==RuneShapes.WIDTH && atlas.getHeight()==RuneShapes.HEIGHT,"图集与静态布局完全匹配");
            for(String key:RuneShapes.names()){
                var glyph=RuneShapes.glyph(key);int x=glyph.index()%RuneShapes.COLUMNS*RuneShapes.CELL,y=glyph.index()/RuneShapes.COLUMNS*RuneShapes.CELL,ink=0;
                for(int r=0;r<RuneShapes.CELL;r++)for(int c=0;c<RuneShapes.CELL;c++)if((atlas.getRGB(x+c,y+r)>>>24)>0)ink++;
                check(ink>4,"每个符形都有非空预烘焙像素："+key);
            }
            var effects=ImageIO.read(VisualContracts.class.getResourceAsStream("/assets/elementalcircuits/textures/effect/spells.png"));check(effects.getWidth()==256 && effects.getHeight()==64,"特效共用四格静态材质");
        }catch(java.io.IOException e){throw new AssertionError(e);}
        var line=new SpellVisual(SpellVisual.Kind.BEAM,Element.LIGHT,new SpellVisual.Point(0,0,0),new SpellVisual.Point(64,0,0),1,1);
        check(line.near(new SpellVisual.Point(65,0,0),2) && line.near(new SpellVisual.Point(-1,0,0),2),"长光束按线段而非中点筛选观察者");check(!line.near(new SpellVisual.Point(20,3,0),2),"线段筛选拒绝远处观察者");
        var q=new VisualBudget();for(int i=0;i<VisualBudget.WORLD_LIMIT+20;i++)q.add(line);check(q.snapshot().size()==VisualBudget.WORLD_LIMIT && q.dropped()==20,"世界特效队列有硬上限");
        var batches=q.batches(new SpellVisual.Point(65,0,0));check(batches.size()==4 && batches.stream().allMatch(b->b.size()==128),"按玩家预算分成有界批次");check(q.batches(new SpellVisual.Point(1000,0,0)).isEmpty(),"无观察者范围的特效不发送");q.clear();check(q.snapshot().isEmpty() && q.dropped()==0,"世界边界清除一次性观察");
        rejects(()->new SpellVisual.Point(Double.NaN,0,0),"拒绝非有限坐标");rejects(()->new SpellVisual(SpellVisual.Kind.SHIELD,Element.ARCANE,line.from(),line.from(),1,1),"屏障法线须单位长度");
        rejects(()->new SpellVisual(SpellVisual.Kind.BEAM,Element.FIRE,line.from(),new SpellVisual.Point(100,0,0),1,1),"拒绝超长特效段");rejects(()->new VisualStyles.Style(4,3,1,1,1),"拒绝不存在的图集格");
        var timeline=new VisualTimeline();for(var kind:SpellVisual.Kind.values())timeline.add(List.of(event(kind)));check(timeline.frames().size()==5,"独立样式进入客户端时间线");
        for(int i=0;i<20;i++)timeline.tick();check(timeline.frames().isEmpty(),"所有特效按本地寿命清理，不推进游戏状态");
        for(int i=0;i<VisualBudget.CLIENT_LIMIT+50;i++)timeline.add(List.of(line));check(timeline.frames().size()==VisualBudget.CLIENT_LIMIT,"客户端长期积压有界");timeline.clear();check(timeline.frames().isEmpty(),"退出或切换世界清空特效");
        var c=new TickCircuit(AssemblyContracts.line(true,Route.Payload.MANA,Rune.Kind.INPUT,Rune.Kind.OUTPUT));var pool=new ManaPool(64*Fixed.ONE,0,0);c.start();String saved=dev.elementalcircuits.adapter.JsonCodecs.snapshot(c);timeline.add(List.of(line));for(int i=0;i<20;i++)timeline.tick();check(saved.equals(dev.elementalcircuits.adapter.JsonCodecs.snapshot(c)) && pool.snapshot().spent()==0,"播放和过期不会回写VM或玩家供给");
        return checks;
    }
}
