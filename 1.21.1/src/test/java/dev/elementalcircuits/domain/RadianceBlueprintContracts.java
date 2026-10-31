package dev.elementalcircuits.domain;

import dev.elementalcircuits.adapter.*;
import dev.elementalcircuits.client.editor.*;
import java.util.*;
import java.util.zip.*;
import java.io.ByteArrayOutputStream;

public final class RadianceBlueprintContracts {
    private static int count;
    private static void check(boolean ok,String message){count++;if(!ok)throw new AssertionError(message);}
    private static void rejects(Runnable action,String message){try{action.run();}catch(IllegalArgumentException expected){check(true,message);return;}throw new AssertionError(message);}
    private static String packed(byte[] bytes){
        Deflater d=new Deflater();d.setInput(bytes);d.finish();ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[1024];
        while(!d.finished()){int n=d.deflate(buf);out.write(buf,0,n);}d.end();CRC32 crc=new CRC32();crc.update(bytes);
        return "EC1."+Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray())+"."+String.format(Locale.ROOT,"%08x",crc.getValue());
    }
    public static int run(){
        count=0;Blueprint board=DFlipFlopContracts.blueprint();Rules rules=board.rules();
        String code=BlueprintTextCode.encode(board);
        check(code.startsWith("EC1.") && BlueprintTextCode.decode(code).equals(board),"D触发器蓝图文本码往返保留节点与线路顺序");
        check(code.equals(BlueprintTextCode.encode(board)),"生成稳定文本码");
        check(BlueprintTextCode.decode(" \n"+code+"\r\n").equals(board),"首尾粘贴空白允许");
        check(BlueprintTextCode.decode(JsonCodecs.blueprint(board)).equals(board),"兼容此前JSON蓝图");
        rejects(()->BlueprintTextCode.decode(code.substring(0,code.length()-1)+(code.endsWith("0")?"1":"0")),"校验值错误拒绝");
        rejects(()->BlueprintTextCode.decode(code.replace("EC1.","EC2.")),"未知文本码版本拒绝");
        rejects(()->BlueprintTextCode.decode(code+".extra"),"多余片段拒绝");
        rejects(()->BlueprintTextCode.decode("EC1.AAAA.00000000"),"伪造压缩流拒绝");
        rejects(()->BlueprintTextCode.decode("x".repeat(BlueprintTextCode.LIMIT+1)),"输入大小有上限");
        rejects(()->BlueprintTextCode.decode(packed(new byte[65537])),"解压炸弹在JSON解析前拒绝");
        rejects(()->BlueprintTextCode.decode(packed(new byte[]{(byte)0xc0,(byte)0xaf})),"非法UTF8拒绝");
        BlueprintTransferModel transfer=new BlueprintTransferModel();transfer.input=code;
        check(transfer.imported(rules).equals(board),"导入先纯校验，不依赖剪贴板或游戏会话");
        var large=new Blueprint(1,rules.withMaterial(Rules.Material.CRYSTAL),List.of(Rune.create(1,Rune.Kind.INPUT,new Hex(7,0))),List.of(),true);
        transfer.input=BlueprintTextCode.encode(large);rejects(()->transfer.imported(rules.withMaterial(Rules.Material.WOOD)),"导入不能扩大当前基板格盘");
        check(transfer.input.equals(BlueprintTextCode.encode(large)),"导入失败保留输入便于修改");
        var lightRules=RadianceResources.rules();check(lightRules.lightLife()==200 && lightRules.trailLife()==3,"光照规则独立资源加载");
        Random random=new Random(20261029);
        for(int i=0;i<300;i++){
            long[] v=new long[17];for(int c=0;c<17;c++)v[c]=random.nextInt(1000000);
            Packet p=new Packet(new Mixture(v),SpellSpec.EMPTY);List<Packet> parts=SpellPayloads.split(p);Mixture sum=Mixture.pure(0);
            for(Packet part:parts)sum=sum.plus(part.mana());check(sum.equals(p.mana()),"穿透分包逐成分守恒");
            check(parts.size()==2 && SpellPayloads.phased(parts.get(1).mana()) && !SpellPayloads.phased(parts.get(0).mana()),"混合包只有正奥术支路穿透");
            for(long strength:new long[]{Fixed.ONE/4,Fixed.ONE,98304,2*Fixed.ONE}){
                var lit=SpellPayloads.illumination(p,strength,lightRules);long paid=p.mana().total()-lit.impact().mana().total();
                check(paid<=v[Element.LIGHT.component(false)]/4 && paid>=0,"弹道照明最多使用四分之一光预算");
                check(paid*strength/Fixed.ONE>=lit.ticks()*lightRules.trailCost() && lit.ticks()<=128,"照明寿命实际预付且有界");
                long[] after=lit.impact().mana().values();for(int c=0;c<17;c++)if(c!=Element.LIGHT.component(false))check(after[c]==v[c],"照明不消费其他元素");
            }
        }
        return count;
    }
}
