package dev.elementalcircuits.domain;
import java.util.*;

/** Immutable configuration collection. No resource stock or runtime snapshot is accepted. */
public record BlueprintVolume(int version,UUID identity,int nextId,int selected,List<Page> pages) {
    public static final int CAPACITY=16;
    public record Page(int id,String title,Blueprint blueprint){public Page{if(id<1 || id>65534 || blueprint==null)throw new IllegalArgumentException("非法蓝图页");title=validTitle(title);}}
    public BlueprintVolume {
        pages=List.copyOf(pages);if(version!=1 || identity==null || nextId<1 || nextId>65535 || pages.size()>CAPACITY)throw new IllegalArgumentException("蓝图簿版本或容量错误");
        var ids=new HashSet<Integer>();for(var p:pages)if(p.id()>=nextId || !ids.add(p.id()))throw new IllegalArgumentException("蓝图页编号重复或越界");
        if(pages.isEmpty()?selected!=0:!ids.contains(selected))throw new IllegalArgumentException("蓝图选页错误");
    }
    public static String validTitle(String s){if(s==null || s.isBlank() || s.length()>32 || s.chars().anyMatch(c->Character.isISOControl(c) || c==167))throw new IllegalArgumentException("名称须为1–32个普通字符");return s;}
    public static BlueprintVolume empty(UUID id){return new BlueprintVolume(1,id,1,0,List.of());}
    public Page page(int id){return pages.stream().filter(p->p.id()==id).findFirst().orElseThrow(()->new IllegalArgumentException("蓝图页不存在"));}
    public BlueprintVolume select(int id){page(id);return new BlueprintVolume(1,identity,nextId,id,pages);}
    public BlueprintVolume add(String title,Blueprint blueprint){if(pages.size()==CAPACITY || nextId==65535)throw new IllegalArgumentException("蓝图簿已满");var next=new ArrayList<>(pages);next.add(new Page(nextId,title,blueprint));return new BlueprintVolume(1,identity,nextId+1,nextId,next);}
    public BlueprintVolume rename(int id,String title){page(id);validTitle(title);return new BlueprintVolume(1,identity,nextId,selected,pages.stream().map(p->p.id()==id?new Page(id,title,p.blueprint()):p).toList());}
    public BlueprintVolume remove(int id){page(id);var next=pages.stream().filter(p->p.id()!=id).toList();return new BlueprintVolume(1,identity,nextId,selected==id?(next.isEmpty()?0:next.get(0).id()):selected,next);}
}
