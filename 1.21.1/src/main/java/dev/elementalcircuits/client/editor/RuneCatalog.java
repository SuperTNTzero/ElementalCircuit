package dev.elementalcircuits.client.editor;

import dev.elementalcircuits.domain.*;
import java.util.*;

/** Semantic tree and labels, independent from Minecraft widgets and painting. */
public final class RuneCatalog {
    public enum Group {
        FLOW("魔力流转",Rune.Kind.INPUT,Rune.Kind.ABSORB,Rune.Kind.SPLIT,Rune.Kind.MERGE,Rune.Kind.OUTPUT,Rune.Kind.MANA_RECEIVE,Rune.Kind.MANA_SEND),
        ELEMENT("元素转化"),
        LOGIC("逻辑与控制",Rune.Kind.AND,Rune.Kind.OR,Rune.Kind.NOT,Rune.Kind.SIGNAL,Rune.Kind.SWITCH,Rune.Kind.SENSOR,Rune.Kind.SIGNAL_RECEIVE,Rune.Kind.SIGNAL_SEND,Rune.Kind.COUNTER,Rune.Kind.CLOCK,Rune.Kind.TOGGLE),
        DELIVERY("施法方式",Rune.Kind.AREA,Rune.Kind.DIRECTED),
        SHAPE("法术形状",Rune.Kind.SPHERE,Rune.Kind.BEAM,Rune.Kind.SHIELD,Rune.Kind.FAN,Rune.Kind.WALL,Rune.Kind.RING,Rune.Kind.COLUMN,Rune.Kind.CROSS,Rune.Kind.DOME),
        EFFECT("派生效果",Rune.Kind.BURST,Rune.Kind.BOUNCE,Rune.Kind.HOMING,Rune.Kind.FRAGMENT,Rune.Kind.CHAIN,Rune.Kind.IGNITE,Rune.Kind.SLOW,Rune.Kind.KNOCKBACK),
        PARAMETER("法术参数",Rune.Kind.AMOUNT,Rune.Kind.RANGE,Rune.Kind.RADIUS,Rune.Kind.AIM);
        public final String title;public final List<Rune.Kind> children;
        Group(String title,Rune.Kind... children){this.title=title;this.children=List.of(children);}
    }
    public record Row(Group group,Rune.Kind kind,Element element){public boolean parent(){return kind==null;}public String title(){return element==null?RuneCatalog.title(kind):element.title+"转化";}}
    private RuneCatalog(){ }
    public static List<Row> rows(Set<Group> expanded){List<Row> rows=new ArrayList<>();for(Group group:Group.values()){rows.add(new Row(group,null,null));if(expanded.contains(group)){if(group==Group.ELEMENT)for(Element element:Element.values())rows.add(new Row(group,Rune.Kind.CONVERT,element));else for(Rune.Kind kind:group.children)rows.add(new Row(group,kind,null));}}return List.copyOf(rows);}
    public static String title(Rune.Kind k){return switch(k){case INPUT->"魔力输入";case ABSORB->"环境汲取";case CONVERT->"元素转化";case AND->"与门";case OR->"或门";case NOT->"反相";case SIGNAL->"常量信号";case SWITCH->"通路开关";case SENSOR->"魔力感应";case SPLIT->"加权分流";case MERGE->"魔力汇聚";case AREA->"范围施法";case DIRECTED->"指向施法";case SPHERE->"球状";case BEAM->"光束";case SHIELD->"盾牌";case FAN->"扇形喷洒";case WALL->"元素墙";case RING->"环带";case COLUMN->"立柱";case CROSS->"十字";case DOME->"穹顶冲击";case AMOUNT->"作用量";case RANGE->"射程";case RADIUS->"半径";case AIM->"精度";case OUTPUT->"法术输出";case MANA_RECEIVE->"魔力接收";case MANA_SEND->"魔力发送";case SIGNAL_RECEIVE->"信号接收";case SIGNAL_SEND->"信号发送";case COUNTER->"计数器";case CLOCK->"节拍器";case TOGGLE->"翻转锁存";case BURST->"爆裂";case BOUNCE->"弹跳";case HOMING->"追踪";case FRAGMENT->"命中分裂";case CHAIN->"连锁";case IGNITE->"附加灼烧";case SLOW->"附加迟缓";case KNOCKBACK->"附加击退";};}
    public static String description(Rune.Kind kind){return switch(kind){
        case INPUT->"引入魔力，供法阵使用";case ABSORB->"从所在位置的魔力场汲取纯净魔力；受场强、供给速率和缓存容量限制";case OUTPUT->"将流入的魔力释放为法术";case CONVERT->"将纯净魔力转为选定元素";
        case AND->"等待每条魔力入线；信号取与";case OR->"接受可用魔力；信号取或";case NOT->"元素反相；信号逻辑取反";
        case SIGNAL->"持续输出开启信号，需要魔力维持";case SWITCH->"手动开关通路，或用信号控制";case SENSOR->"魔力达到设定量时发出信号，不消耗被测魔力";
        case SPLIT->"按设定比例分配到各条出线";case MERGE->"汇合多路魔力，互斥元素会产生损耗";case AREA->"以自身为中心施法";case DIRECTED->"向瞄准方向施法";
        case SPHERE->"球形作用域或有限射程弹道";case BEAM->"沿直线发射光束，接触目标或地形时生效";case SHIELD->"在前方展开屏障，耗尽容量或时间后消失";
        case FAN->"指向施法：向前喷洒70°扇面；半径控制宽度与距离，最远为半径的两倍，不叠加射程。持续法阵可形成喷流";
        case WALL->"指向施法：在瞄准位置释放竖直元素墙；半径控制高宽，射程控制位置。持续法阵可重复释放";
        case RING->"空心环带，中心留空；半径控制外沿，范围施法环绕自身，指向施法在瞄准位置展开";
        case COLUMN->"竖直元素柱，高度为半径两倍，横截面直径等于半径；范围或指向施法决定中心";
        case CROSS->"沿前后左右四条通道释放元素；半径控制臂长，斜角区域留空";
        case DOME->"向上半球壳释放元素，内部与下方留空；适合覆盖上方目标，半径控制外沿";
        case AMOUNT->"调整作用量，越大越耗魔力";case RANGE->"调整射程，越远越耗魔力";case RADIUS->"调整半径，范围越大越耗魔力";case AIM->"收拢弹道散布，提高精度需要更多魔力";
        case COUNTER->"每收到N次开启信号，输出一次脉冲；持续开启只计一次";case CLOCK->"每T拍输出一次脉冲，可接信号暂停计时";case TOGGLE->"每收到一次开启信号就切换开关；持续开启不会反复切换";
        case BURST->"命中释放球形元素作用；半径1–4格，共享原作用量";case BOUNCE->"球状投射物碰到实体方块反射1–4次，每次耗散25%";case HOMING->"球状投射物寻找前方可见目标，范围2–12格；预付一半作用量，有限转向";
        case FRAGMENT->"命中后分出2–4枚子弹，共享魔力，不会再次分裂";case CHAIN->"命中后跳向附近可见目标，最多1–4跳，不重复命中";case IGNITE->"消耗部分魔力使目标燃烧，最多1–5秒";case SLOW->"消耗部分魔力使目标迟缓，最多1–5秒";case KNOCKBACK->"消耗部分魔力击退目标，强度1–4级";
        case MANA_RECEIVE->"接收其他法阵送来的魔力";case MANA_SEND->"将魔力和已有修饰送往连接的法阵";
        case SIGNAL_RECEIVE->"接收上个世界步发布的信号，多线取或";case SIGNAL_SEND->"将阵内信号发布至互联线路，仍需按步支付控制成本";
    };}
}
