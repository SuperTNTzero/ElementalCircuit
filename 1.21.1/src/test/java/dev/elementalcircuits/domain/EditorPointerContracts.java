package dev.elementalcircuits.domain;

import dev.elementalcircuits.adapter.RuleResources;
import dev.elementalcircuits.client.editor.*;
import java.util.*;

public final class EditorPointerContracts {
    private static int count;
    private static void check(boolean condition,String message){count++;if(!condition)throw new AssertionError(message);}
    private static Hex reference(EditorLayout l,double x,double y){
        if(!l.onBoard(x,y))return null;double best=l.size()*l.size();Hex result=null;
        for(int q=-l.radius();q<=l.radius();q++)for(int r=-l.radius();r<=l.radius();r++){
            Hex h=new Hex(q,r);if(!h.inside(l.radius()))continue;double distance=Math.pow(x-l.x(h),2)+Math.pow(y-l.y(h),2);
            if(distance<best){best=distance;result=h;}
        }
        return result;
    }
    public static int run(){
        count=0;Random random=new Random(20261030);
        for(int[] size:new int[][]{{320,240},{640,400},{1280,720},{1920,1080}})for(int radius:new int[]{4,6,8}){
            var l=EditorLayout.of(size[0],size[1],radius);
            for(int i=0;i<400;i++){double x=random.nextDouble()*size[0],y=random.nextDouble()*size[1];check(Objects.equals(l.cell(x,y),reference(l,x,y)),"常量邻域命中与原逐格命中一致");}
            for(int q=-radius;q<=radius;q++)for(int r=-radius;r<=radius;r++)if(new Hex(q,r).inside(radius)){
                Hex h=new Hex(q,r);check(h.equals(l.cell(l.x(h),l.y(h))),"全部格心命中正确");
            }
        }
        var board=DFlipFlopContracts.blueprint().withMaterial(Rules.Material.CRYSTAL);var model=new EditorModel(board);
        var select=model.view(new Hex(0,2));check(select==model.view(new Hex(-2,3)),"选择时跨空格移动不重建无关观察");
        model.choose(Rune.Kind.CONVERT,Element.LIGHT);var place=model.view(new Hex(0,2));
        check(place==model.view(new Hex(0,2)),"同格放置预览复用校验结果");
        model.element=Element.WATER;check(model.view(new Hex(0,2)).ghost().element()==Element.WATER,"元素切换不会沿用旧符形");
        model.element=Element.LIGHT;var pointer=EditorPointer.of(model.view(new Hex(0,2)));
        check(pointer.rune()==Rune.Kind.CONVERT && pointer.element()==Element.LIGHT,"原生指针保留所选元素形状");
        model.tool=EditorModel.Tool.ROUTE;model.cancel();model.payload=Route.Payload.SIGNAL;model.click(board.rune(1).cell());
        var route=model.view(board.rune(16).cell());check(route==model.view(board.rune(16).cell()),"同格布线不重复寻路或损耗计算");
        model.message="新的状态";check(model.view(board.rune(16).cell()).message().equals("新的状态"),"消息刷新不被缓存吞掉");
        model.cancel();check(model.view(board.rune(16).cell()).preview().isEmpty(),"取消清除旧路线预览");
        model.tool=EditorModel.Tool.SELECT;model.click(board.rune(17).cell());model.beginDrag();
        var drag=model.view(new Hex(-2,3));check(drag==model.view(new Hex(-2,3)),"同格拖动不重复重排线路");
        check(model.view(board.rune(1).cell())==model.view(board.rune(1).cell()) && !model.view(board.rune(1).cell()).ghostValid(),"无效预览及错误也复用，不每帧抛异常");
        check(EditorPointer.of(model.view(board.rune(1).cell())).invalid(),"无效落点有红色原生指针");
        model.cancel();model.choose(Rune.Kind.CONVERT,Element.FIRE);var cached=model.view(new Hex(0,2));
        model.draft.set(Blueprint.empty(board.rules()));var fresh=model.view(new Hex(0,2));check(fresh!=cached && fresh.board().runes().isEmpty(),"导入或替换草稿即时失效");
        model.draft.undo();check(model.view(new Hex(0,2)).board().equals(board),"撤销后预览使用恢复的草稿");
        for(Element e:Element.values())for(int scale=1;scale<=3;scale++){
            var style=new EditorPointer(EditorPointer.Kind.RUNE,Rune.Kind.CONVERT,e,false);var bitmap=new PointerBitmap(style,scale);int[] pixels=bitmap.pixels();
            check(bitmap.hotX()>=0 && bitmap.hotY()>=0 && bitmap.hotX()<bitmap.size() && bitmap.hotY()<bitmap.size(),"点击热点在图片内");
            check(pixels[bitmap.hotY()*bitmap.size()+bitmap.hotX()]==style.color(),"热点标记与原生点击位置一致");
            check(Arrays.stream(pixels).anyMatch(p->p==0) && Arrays.stream(pixels).anyMatch(p->p==style.color()),"光标含透明背景及对应元素颜色");
            Arrays.fill(pixels,0);check(Arrays.stream(bitmap.pixels()).anyMatch(p->p!=0),"位图只读快照不可反写");
        }
        for(Rune.Kind kind:Rune.Kind.values())check(RuneShapes.of(kind)==RuneShapes.of(kind),"常量符形不每帧重建");
        return count;
    }
}
