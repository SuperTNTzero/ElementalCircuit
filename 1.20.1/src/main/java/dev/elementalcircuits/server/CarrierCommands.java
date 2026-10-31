package dev.elementalcircuits.server;

import dev.elementalcircuits.*;
import dev.elementalcircuits.adapter.*;
import dev.elementalcircuits.adapter.persistence.CarrierRepository;
import dev.elementalcircuits.block.CircuitBlockEntity;
import dev.elementalcircuits.domain.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import java.util.*;
import static dev.elementalcircuits.server.MaterialInventory.*;

/** Validated player intents; the only editor command handler. */
public final class CarrierCommands {
    private static final Map<UUID,Long> LAST_EDIT=new HashMap<>();
    private CarrierCommands(){}
    public static void start(ServerPlayer player,ItemStack stack){try{
        var a=CarrierRepository.load(stack);TickCircuit c=a.circuit(a.selected());if(c.powered()){c.stop();player.displayClientMessage(Component.literal("已停止当前槽位供给，残余继续流转"),true);}
        else if(WandEquipment.broken(CastingEquipment.wand(player,stack)))throw new IllegalArgumentException("法杖耐久耗尽，请先修缮");
        else if(!c.start())throw new IllegalArgumentException("请先刻印输入或接收符文");CarrierRepository.commit(stack);
        if(c.powered())SpellSounds.observe(player.serverLevel(),dev.elementalcircuits.application.observation.SpellSound.Kind.START,Element.PURE,player.position());
    }catch(RuntimeException e){player.displayClientMessage(Component.literal(e.getMessage()==null?"法阵数据异常":e.getMessage()),true);}}
    public static void request(ServerPlayer player,UUID id,int action,String payload){request(player,id,0,action,payload);}
    public static void request(ServerPlayer player,UUID id,int slot,int action,String payload){request(player,id,slot,action,payload,new UUID(0,0));}
    public static void request(ServerPlayer player,UUID id,int slot,int action,String payload,UUID token){
        ItemStack stack=player.getMainHandItem();CircuitBlockEntity block=null;
        try{
        if(player.getOffhandItem().is(ElementalCircuits.SPELL_BOOK.get()) && CarrierRepository.identity(player.getOffhandItem()).equals(id))stack=player.getOffhandItem();
        if((!stack.is(ElementalCircuits.WAND.get()) && !stack.is(ElementalCircuits.SPELL_BOOK.get())) || !CarrierRepository.identity(stack).equals(id)){block=WorldCircuits.block(player.serverLevel(),id);if(block==null || !block.allowed(player))return;stack=block.carrier();}
            var a=CarrierRepository.load(stack);if(slot==0)slot=a.selected();TickCircuit old=a.circuit(slot);String message="已应用";
            long now=player.serverLevel().getGameTime();Long last=LAST_EDIT.get(player.getUUID());
            if(action!=5){if(last!=null && now-last<2)throw new IllegalArgumentException("操作过快，请稍后重试");LAST_EDIT.put(player.getUUID(),now);}
            switch(action){
                case 0,4 -> {
                    if(block!=null && WorldCircuitLinks.get(player.serverLevel()).references(id,slot))throw new IllegalArgumentException("请先断开该槽位的外部线路");
                    Blueprint draft=action==4 && !payload.startsWith("{")?old.definition().withMaterial(Rules.Material.valueOf(payload)):JsonCodecs.readBlueprint(payload);
                    var previous=old.definition().rules().material();var material=draft.rules().material();
                    if(action==0 && material!=previous)throw new IllegalArgumentException("更换基板请使用基板安装页");
                    if(action==4 && material==previous)throw new IllegalArgumentException("已安装该材料");
                    Blueprint checked=new Blueprint(1,action==4?old.definition().rules().withMaterial(material):old.definition().rules(),draft.runes(),draft.routes(),draft.continuous());
                    int inventory=action==4?inventory(player,plate(material)):-1;
                    a.replace(slot,checked);
                    if(action==4){player.getInventory().getItem(inventory).shrink(1);give(player,new ItemStack(plate(previous)));message="已安装所选基板并回收旧板，草稿同时保存";}
                }
                case 1 -> {if(block==null && WandEquipment.broken(CastingEquipment.wand(player,stack)))throw new IllegalArgumentException("法杖耐久耗尽");if(!old.start())throw new IllegalArgumentException("无法起阵：需要输入或接收符文，且尚未运行");}
                case 2 -> {old.stop();message="已停供，残余继续流转";}
                case 3 -> old.toggleManual(Integer.parseInt(payload));
                case 5 -> message=old.status();
                case 6,7 -> throw new IllegalArgumentException("部件安装和修缮已移至法杖工作台，请右键工作台");
                case 8 -> {a.select(Integer.parseInt(payload));message="已选择法阵 "+a.selected()+"；法杖右键起停此阵";}
                case 9 -> {var material=Rules.Material.valueOf(payload);int inventory=inventory(player,plate(material));a.add(RuleResources.defaults().withMaterial(material));player.getInventory().getItem(inventory).shrink(1);message="已消费一张基板，新法阵无额外魔力";}
                case 10 -> {
                    var r=JsonCodecs.readLinkRequest(payload);
                    if(r.from().carrier().equals(id) && r.to().carrier().equals(id)){
                        if(block!=null && WorldCircuitLinks.get(player.serverLevel()).graph().connections().stream().anyMatch(c->c.from().equals(r.from())))throw new IllegalArgumentException("该发送口已有外部线路，请另放发送符文");
                        a.links().connect(r.from(),r.to(),r.payload(),k->k.equals(id)?a:null);
                    }else{if(block==null)throw new IllegalArgumentException("移动法杖只能连接自己的槽位；跨载体请用固定法阵");WorldCircuitLinks.get(player.serverLevel()).connect(player.serverLevel(),player,id,r);}
                    message="接口已连接，传输至少延后一世界步";
                }
                case 11 -> {String[] parts=payload.split(":",-1);if(parts.length!=2)throw new IllegalArgumentException("线路编号错误");int link=Integer.parseInt(parts[1]);if(parts[0].equals("I"))a.links().disconnect(link,k->k.equals(id)?a:null);else if(parts[0].equals("W") && block!=null)WorldCircuitLinks.get(player.serverLevel()).disconnect(player.serverLevel(),player,id,link);else throw new IllegalArgumentException("线路类型错误");message="线路已断开";}
                case 13 -> {old.resetLogic(Integer.parseInt(payload));message="时序符文已复位；初态配置未改变";}
                case 12 -> {if(block==null)throw new IllegalArgumentException("仅固定法阵可充能");message="已充入 "+Fixed.display(block.charge(player))+" 魔力";}
                default -> throw new IllegalArgumentException("未知操作");
            }
            CarrierRepository.commit(stack);if(block!=null)block.setChanged();
            if(action==1)SpellSounds.observe(player.serverLevel(),dev.elementalcircuits.application.observation.SpellSound.Kind.START,Element.PURE,block==null?player.position():net.minecraft.world.phys.Vec3.atCenterOf(block.getBlockPos()));
            CarrierEditor.sync(player,stack,block,false,message,token);
        }catch(RuntimeException e){try{CarrierEditor.sync(player,stack,block,false,"拒绝："+(e.getMessage()==null?"无效数据":e.getMessage()),token);}catch(RuntimeException broken){CarrierFaults.report(stack,player);}}
    }
    public static void forget(UUID player){LAST_EDIT.remove(player);}
}
