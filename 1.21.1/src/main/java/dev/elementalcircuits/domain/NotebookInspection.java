package dev.elementalcircuits.domain;
import java.util.*;
public record NotebookInspection(UUID identity,int selected,List<Page> pages,Blueprint blueprint,String message) {
    public record Page(int id,String title){public Page{if(id<1 || id>65534)throw new IllegalArgumentException("非法页码");BlueprintVolume.validTitle(title);}}
    public NotebookInspection {pages=List.copyOf(pages);if(identity==null || pages.size()>16 || pages.stream().map(Page::id).distinct().count()!=pages.size() || message==null || message.length()>512 || (pages.isEmpty()?selected!=0 || blueprint!=null:pages.stream().noneMatch(p->p.id()==selected) || blueprint==null))throw new IllegalArgumentException("非法蓝图观察");}
    public static NotebookInspection inspect(BlueprintVolume v,String message){return new NotebookInspection(v.identity(),v.selected(),v.pages().stream().map(p->new Page(p.id(),p.title())).toList(),v.selected()==0?null:v.page(v.selected()).blueprint(),message);}
}
