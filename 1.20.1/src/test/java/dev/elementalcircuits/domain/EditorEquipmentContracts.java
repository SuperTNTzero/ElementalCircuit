package dev.elementalcircuits.domain;

import dev.elementalcircuits.adapter.*;
import dev.elementalcircuits.client.editor.*;
import java.util.*;

/** Observable contracts for detached editor interaction, path physics and equipment scheduling. */
final class EditorEquipmentContracts {
    private static int count;
    private static void check(boolean b,String text){count++;if(!b)throw new AssertionError(text);}
    private static void rejects(Runnable r){boolean rejected=false;try{r.run();}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"非法操作应明确拒绝");}
    static int run(){
        Rules rules=RuleResources.defaults();
        Rune source=Rune.create(1,Rune.Kind.INPUT,new Hex(-3,0)),target=Rune.create(2,Rune.Kind.OUTPUT,new Hex(3,0)),obstacle=Rune.create(3,Rune.Kind.CONVERT,new Hex(0,0));
        Blueprint board=new Blueprint(1,rules,List.of(source,target,obstacle),List.of(),false);
        List<Hex> path=HexRouter.segment(board,source.cell(),target.cell(),List.of());
        check(path.size()==8 && !path.contains(obstacle.cell()),"最短路径绕开中间符文");
        check(path.equals(HexRouter.segment(board,source.cell(),target.cell(),List.of())),"路径选择稳定可复现");
        Route wire=new Route(1,2,Route.Payload.MANA,path);new Blueprint(1,rules,board.runes(),List.of(wire),false);
        check(wire.length()==7,"损耗长度是实际跨格步数");
        EditorModel model=new EditorModel(board);model.tool=EditorModel.Tool.ROUTE;model.click(source.cell());
        EditorModel.View view=model.view(target.cell());check(view.preview().equals(path) && view.routeHint().contains("7 格"),"预览路径及格数与落笔相同");
        model.click(target.cell());check(model.draft.get().routes().get(0).path().equals(view.preview()),"远端点击自动完成路线");
        model.draft.undo();check(model.draft.get().routes().isEmpty(),"自动连线可撤销");model.draft.redo();check(model.draft.get().routes().size()==1,"自动连线可重做");
        for(Rules.Material material:Rules.Material.values()){
            Mixture input=Mixture.pure(4*Fixed.ONE);Rules r=rules.withMaterial(material);
            Route shortWire=new Route(1,2,Route.Payload.MANA,List.of(new Hex(0,0),new Hex(1,0)));
            check(wire.transmit(input,r).total()<shortWire.transmit(input,r).total(),"相同材料远距离损耗更高");
            TickCircuit c=new TickCircuit(new Blueprint(1,r,board.runes(),List.of(wire),false));c.start();long delivered=0;
            for(int i=0;i<4;i++)for(var cast:c.tick(new ManaPool(64*Fixed.ONE,0,0)))delivered+=cast.mana().total();
            check(delivered==wire.transmit(input,r).total(),"VM与预览使用同一逐格损耗算法");
        }
        List<Hex> manual=HexRouter.segment(board,source.cell(),new Hex(0,-2),List.of());
        check(HexRouter.segment(board,manual.get(manual.size()-1),target.cell(),manual).stream().skip(1).noneMatch(manual::contains),"手动转折后的自动段不重复经过旧路径");
        rejects(()->HexRouter.segment(board,new Hex(0,-2),source.cell(),manual));
        List<Rune> wall=new ArrayList<>();wall.add(source);wall.add(target);
        for(int r=-4;r<=4;r++)wall.add(Rune.create(10+r+4,Rune.Kind.SWITCH,new Hex(0,r)));
        rejects(()->HexRouter.segment(new Blueprint(1,rules,wall,List.of(),false),source.cell(),target.cell(),List.of()));
        var collapsed=RuneCatalog.rows(Set.of());check(collapsed.size()==7 && collapsed.stream().allMatch(RuneCatalog.Row::parent),"全部折叠仅保留父项");
        var rows=RuneCatalog.rows(EnumSet.allOf(RuneCatalog.Group.class));Set<Rune.Kind> kinds=EnumSet.noneOf(Rune.Kind.class);
        Set<Element> elements=EnumSet.noneOf(Element.class);
        for(var row:rows)if(!row.parent()){if(row.kind()==Rune.Kind.CONVERT){check(row.group()==RuneCatalog.Group.ELEMENT && elements.add(row.element()),"元素转化独立分类且每元素独立入口");kinds.add(row.kind());}else check(kinds.add(row.kind()),"符文不重复出现在类别中");}
        check(elements.size()==Element.values().length,"纯净及八元素全部有独立入口");
        check(kinds.size()==Rune.Kind.values().length,"所有可用符文都有分类入口");
        model.toggle(RuneCatalog.Group.FLOW);check(!model.expanded.contains(RuneCatalog.Group.FLOW),"父项折叠");model.toggle(RuneCatalog.Group.FLOW);check(model.expanded.contains(RuneCatalog.Group.FLOW),"父项展开");
        for(Rune.Kind kind:Rune.Kind.values()){
            check(!RuneShapes.of(kind).isEmpty(),"所有符文都有真实图形");
            for(var stroke:RuneShapes.of(kind))check(Math.max(Math.max(Math.abs(stroke.x1()),Math.abs(stroke.x2())),Math.max(Math.abs(stroke.y1()),Math.abs(stroke.y2())))<=1,"图形在统一预览边界内");
        }
        Set<List<RuneShapes.Stroke>> shapes=new HashSet<>();
        for(Element element:Element.values()){
            check(shapes.add(RuneShapes.element(element)),"各元素形状独立且可区分");
            for(var stroke:RuneShapes.element(element))check(Math.max(Math.max(Math.abs(stroke.x1()),Math.abs(stroke.x2())),Math.max(Math.abs(stroke.y1()),Math.abs(stroke.y2())))<=1,"元素图形遵守预览边界");
        }
        EditorModel placement=new EditorModel(Blueprint.empty(rules));placement.choose(Rune.Kind.CONVERT,Element.WATER);
        var emptyPreview=placement.view(new Hex(0,0));check(emptyPreview.ghost().element()==Element.WATER && emptyPreview.ghostValid() && placement.draft.get().runes().isEmpty(),"放置虚影包含确切元素且不改变草稿");
        placement.click(new Hex(0,0));check(placement.draft.at(new Hex(0,0)).element()==Element.WATER,"目录选中元素直接落笔");
        check(!placement.view(new Hex(0,0)).ghostValid(),"已占用格的虚影标记无效");
        EditorModel crossingPlacement=new EditorModel(new Blueprint(1,rules,board.runes(),List.of(wire),false));crossingPlacement.choose(Rune.Kind.CONVERT,Element.FIRE);
        check(!crossingPlacement.view(path.get(1)).ghostValid(),"线路经过格上的落笔虚影遵守同一穿符校验");
        EditorModel movement=new EditorModel(new Blueprint(1,rules,board.runes(),List.of(wire),false));movement.click(source.cell());movement.beginDrag();
        String untouched=JsonCodecs.blueprint(movement.draft.get());var moving=movement.view(new Hex(-2,-1));
        check(moving.ghostValid() && moving.moved()!=null && moving.dragging()==source.id() && JsonCodecs.blueprint(movement.draft.get()).equals(untouched),"拖动只生成独立图形和线路预览");
        check(!movement.view(target.cell()).ghostValid(),"不能拖到已占用符文");
        movement.drop(new Hex(-2,-1));check(movement.draft.get().rune(source.id()).cell().equals(new Hex(-2,-1)),"松开提交移动");
        check(movement.draft.get().routes().get(0).path().get(0).equals(new Hex(-2,-1)),"移动原子重算关联线端点");
        movement.draft.undo();check(JsonCodecs.blueprint(movement.draft.get()).equals(untouched),"一次撤销恢复符文和完整线路");
        movement.beginDrag();movement.cancel();movement.drop(new Hex(-2,-1));check(JsonCodecs.blueprint(movement.draft.get()).equals(untouched),"取消拖动不移动符文");
        movement.beginDrag();rejects(()->movement.drop(target.cell()));check(JsonCodecs.blueprint(movement.draft.get()).equals(untouched),"失败移动原子保留草稿");
        for(int[] size:new int[][]{{320,240},{480,270},{640,360},{1280,720}})for(int radius:new int[]{4,6,8}){
            var layout=EditorLayout.of(size[0],size[1],radius);Hex zero=new Hex(0,0);
            check(layout.size()>0 && layout.cell(layout.x(zero),layout.y(zero)).equals(zero),"不同GUI缩放的格子命中一致");
        }
        WandParts parts=WandResources.definitions();var basic=parts.basic();var initial=basic.stats(parts);
        check(initial.durability()==192 && initial.strength()==Fixed.ONE && initial.melee()==2,"基础部件属性稳定");
        check(basic.replace(parts.get("amethyst_head")).stats(parts).strength()>initial.strength(),"杖端决定强度");
        check(basic.replace(parts.get("iron_body")).stats(parts).durability()>initial.durability(),"杖身贡献耐久");
        check(basic.replace(parts.get("diamond_tail")).stats(parts).durability()==initial.durability(),"杖尾不提供耐久");
        check(basic.replace(parts.get("diamond_tail")).stats(parts).melee()==7,"杖尾决定近战伤害");
        rejects(()->new WandParts.Assembly(basic.body(),basic.head(),basic.tail()).stats(parts));
        for(WandParts.Part head:parts.in(WandParts.Slot.HEAD)){
            long remainder=0;int steps=0;
            for(int tick=0;tick<80;tick++){var next=WandParts.advance(remainder,head.speed());steps+=next.steps();remainder=next.remainder();}
            check(steps==80*head.speed()/Fixed.ONE && remainder==80*head.speed()%Fixed.ONE,"固定步施法节奏精确累计");
        }
        rejects(()->WandParts.advance(Fixed.ONE,Fixed.ONE));return count;
    }
}
