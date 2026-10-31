package dev.elementalcircuits.client.editor;

import dev.elementalcircuits.domain.*;
import java.util.*;

/** Semantic tree and labels, independent from Minecraft widgets and painting. */
public final class RuneCatalog {
    public enum Group {
        FLOW("魔力流转",Rune.Kind.INPUT,Rune.Kind.SPLIT,Rune.Kind.MERGE,Rune.Kind.OUTPUT,Rune.Kind.MANA_RECEIVE,Rune.Kind.MANA_SEND),
        ELEMENT("元素转化"),
        LOGIC("逻辑与控制",Rune.Kind.AND,Rune.Kind.OR,Rune.Kind.NOT,Rune.Kind.SIGNAL,Rune.Kind.SWITCH,Rune.Kind.SENSOR,Rune.Kind.SIGNAL_RECEIVE,Rune.Kind.SIGNAL_SEND,Rune.Kind.COUNTER,Rune.Kind.CLOCK,Rune.Kind.TOGGLE),
        DELIVERY("施法方式",Rune.Kind.AREA,Rune.Kind.DIRECTED),
        SHAPE("法术形状",Rune.Kind.SPHERE,Rune.Kind.BEAM,Rune.Kind.SHIELD),
        EFFECT("派生效果",Rune.Kind.BURST,Rune.Kind.BOUNCE,Rune.Kind.HOMING,Rune.Kind.FRAGMENT,Rune.Kind.CHAIN,Rune.Kind.IGNITE,Rune.Kind.SLOW,Rune.Kind.KNOCKBACK),
        PARAMETER("参数调控",Rune.Kind.AMOUNT,Rune.Kind.RANGE,Rune.Kind.RADIUS,Rune.Kind.AIM);
        public final String title;public final List<Rune.Kind> children;
        Group(String title,Rune.Kind... children){this.title=title;this.children=List.of(children);}
    }
    public record Row(Group group,Rune.Kind kind,Element element){public boolean parent(){return kind==null;}public String title(){return element==null?RuneCatalog.title(kind):element.title+"转化";}}
    private RuneCatalog(){ }
    public static List<Row> rows(Set<Group> expanded){List<Row> rows=new ArrayList<>();for(Group group:Group.values()){rows.add(new Row(group,null,null));if(expanded.contains(group)){if(group==Group.ELEMENT)for(Element element:Element.values())rows.add(new Row(group,Rune.Kind.CONVERT,element));else for(Rune.Kind kind:group.children)rows.add(new Row(group,kind,null));}}return List.copyOf(rows);}
    public static String title(Rune.Kind k){return switch(k){case INPUT->"魔力输入";case CONVERT->"元素转化";case AND->"与门";case OR->"或门";case NOT->"反相";case SIGNAL->"常量信号";case SWITCH->"通路开关";case SENSOR->"魔力感应";case SPLIT->"加权分流";case MERGE->"魔力汇聚";case AREA->"范围施法";case DIRECTED->"指向施法";case SPHERE->"球状";case BEAM->"光束";case SHIELD->"盾牌";case AMOUNT->"作用量";case RANGE->"射程";case RADIUS->"半径";case AIM->"精度";case OUTPUT->"法术输出";case MANA_RECEIVE->"魔力接收";case MANA_SEND->"魔力发送";case SIGNAL_RECEIVE->"信号接收";case SIGNAL_SEND->"信号发送";case COUNTER->"计数器";case CLOCK->"节拍器";case TOGGLE->"翻转锁存";case BURST->"爆裂";case BOUNCE->"弹跳";case HOMING->"追踪";case FRAGMENT->"命中分裂";case CHAIN->"连锁";case IGNITE->"附加灼烧";case SLOW->"附加迟缓";case KNOCKBACK->"附加击退";};}
    public static String description(Rune.Kind kind){return switch(kind){
        case INPUT->"从玩家魔力槽取得有限供给";case OUTPUT->"交付法术并终止阵内魔力流";case CONVERT->"把纯净魔力转为选定本源";
        case AND->"等待每条魔力入线；信号取与";case OR->"接受可用魔力；信号取或";case NOT->"元素反相；信号逻辑取反";
        case SIGNAL->"按逻辑步付费的常量信号";case SWITCH->"手动或上步信号控制通路";case SENSOR->"检测本地阈值，不消耗被测魔力";
        case SPLIT->"按出口顺序和权重守恒分配";case MERGE->"合并兼容包并结算互斥损耗";case AREA->"以施法者为中心投送";case DIRECTED->"沿玩家视线投送";
        case SPHERE->"球形作用域或有限射程弹道";case BEAM->"首次接触结算，地形阻挡";case SHIELD->"有限寿命与容量的前向屏障";
        case AMOUNT->"指定本次实际魔力支付量";case RANGE->"指定射程，扩大范围提高代价";case RADIUS->"指定作用半径并支付体积代价";case AIM->"调整散布，精度提高会增加代价";
        case COUNTER->"每N次有效上升沿输出一步脉冲；长高电平不重复计数";case CLOCK->"每T个使能逻辑步输出一步脉冲；可接信号暂停计时";case TOGGLE->"每次有效上升沿翻转存储位；初态与当前态独立";
        case BURST->"命中释放球形元素作用；半径1–4格，共享原作用量";case BOUNCE->"球状投射物碰到实体方块反射1–4次，每次耗散25%";case HOMING->"球状投射物寻找前方可见目标，范围2–12格；预付一半作用量，有限转向";
        case FRAGMENT->"命中后分出2–4个子投射物，均分预留预算，仅一代";case CHAIN->"命中后向可见新目标继续1–4跳，排除已命中对象";case IGNITE->"预留命中预算附加灼烧，参数为1–5秒上限";case SLOW->"预留命中预算附加迟缓I，参数为1–5秒上限";case KNOCKBACK->"预留命中预算附加击退，强度1–4级";
        case MANA_RECEIVE->"接收互联线路中的魔力，不从玩家槽自动注入";case MANA_SEND->"保留魔力与修饰，发送到管理页配置的目标";
        case SIGNAL_RECEIVE->"接收上个世界步发布的信号，多线取或";case SIGNAL_SEND->"将阵内信号发布至互联线路，仍需按步支付控制成本";
    };}
}
