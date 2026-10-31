package dev.elementalcircuits.server;

import dev.elementalcircuits.*;
import dev.elementalcircuits.adapter.RuleResources;
import dev.elementalcircuits.block.*;
import dev.elementalcircuits.domain.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Server-owned physical workstation authorization, rechecked for every graph mutation. */
public final class InscriptionAccess {
    private record Session(ResourceKey<Level> dimension,BlockPos table,UUID carrier,ItemStack held,BlockPos fixed,ItemStack draft,Rules.Material material){}
    private record Selection(ResourceKey<Level> dimension,BlockPos fixed){}
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();
    private static final Map<UUID,Selection> SELECTED=new HashMap<>();
    private InscriptionAccess(){}
    private static boolean table(ServerPlayer p,BlockPos pos){return p.isAlive() && p.getAbilities().mayBuild && p.serverLevel().hasChunkAt(pos) && p.serverLevel().getBlockState(pos).is(ElementalCircuits.TABLE.get()) && p.serverLevel().mayInteract(p,pos) && p.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos))<=64;}
    public static void select(ServerPlayer p,CircuitBlockEntity b){if(!b.allowed(p))throw new IllegalArgumentException("仅所有者可选择固定法阵");SELECTED.put(p.getUUID(),new Selection(p.serverLevel().dimension(),b.getBlockPos().immutable()));p.displayClientMessage(Component.literal("已选定固定法阵，持法杖右键附近的法阵工作台刻印"),true);}
    public static void interact(ServerPlayer p,BlockPos pos){try{
        if(!table(p,pos))throw new IllegalArgumentException("请回到法阵工作台旁");
        var held=p.getMainHandItem();
        for(var material:Rules.Material.values())if(held.is(MaterialInventory.plate(material))){
            close(p);
            var draft=new ItemStack(ElementalCircuits.CIRCUIT_ITEM.get());var a=CarrierService.assembly(draft);
            a.replace(1,Blueprint.empty(RuleResources.defaults().withMaterial(material)));CarrierService.persist(draft);FixedCircuitAppearance.update(draft);
            SESSIONS.put(p.getUUID(),new Session(p.serverLevel().dimension(),pos.immutable(),CarrierService.identity(draft),held,null,draft,material));
            if(p.serverLevel().getBlockEntity(pos) instanceof WorkstationBlockEntity b)b.show(p,draft,held);
            CarrierEditor.sync(p,draft,null,true,"绘制法阵后点击「完成刻印」，消耗一张基板；关闭则取消");return;
        }
        boolean isWand=held.is(ElementalCircuits.WAND.get());
        if(!isWand && !held.is(ElementalCircuits.SPELL_BOOK.get()) && !held.is(ElementalCircuits.CIRCUIT_ITEM.get()))throw new IllegalArgumentException("手持法杖、法术书或基板与工作台互动");
        ItemStack target=isWand?CastingEquipment.carrier(p,held):held;CircuitBlockEntity fixed=null;
        var selected=SELECTED.remove(p.getUUID());
        if(isWand && selected!=null && selected.dimension()==p.serverLevel().dimension() && p.serverLevel().hasChunkAt(selected.fixed()) && p.serverLevel().getBlockEntity(selected.fixed()) instanceof CircuitBlockEntity b && b.allowed(p)){fixed=b;target=b.carrier();}
        close(p);SESSIONS.put(p.getUUID(),new Session(p.serverLevel().dimension(),pos.immutable(),CarrierService.identity(target),held,fixed==null?null:fixed.getBlockPos().immutable(),ItemStack.EMPTY,null));
        if(p.serverLevel().getBlockEntity(pos) instanceof WorkstationBlockEntity b)b.show(p,target,held);
        CarrierEditor.sync(p,target,fixed,true,"刻印后保存；离开工作台前完成修改");
    }catch(RuntimeException e){p.displayClientMessage(Component.literal(e.getMessage()==null?"工作台暂不可用":e.getMessage()),true);}}
    public static boolean allowed(ServerPlayer p,ItemStack target,CircuitBlockEntity fixed){
        var s=SESSIONS.get(p.getUUID());return s!=null && p.serverLevel().dimension()==s.dimension() && table(p,s.table()) && !s.held().isEmpty() && (p.getMainHandItem()==s.held() || p.getOffhandItem()==s.held()) && CarrierService.identity(target).equals(s.carrier()) && (fixed==null?s.fixed()==null:s.fixed()!=null && fixed.getBlockPos().equals(s.fixed()) && fixed.allowed(p));
    }
    /** Unpublished, non-running carrier owned only by this editing session. */
    public static ItemStack draft(ServerPlayer p){
        var s=SESSIONS.get(p.getUUID());if(s==null || s.draft().isEmpty())return ItemStack.EMPTY;
        if(!allowed(p,s.draft(),null) || !s.held().is(MaterialInventory.plate(s.material()))){close(p);return ItemStack.EMPTY;}
        return s.draft();
    }
    public static boolean isDraft(ServerPlayer p,ItemStack stack){var s=SESSIONS.get(p.getUUID());return s!=null && !s.draft().isEmpty() && s.draft()==stack;}
    /** Commit encoding first; consume the actual held plate only after all validation succeeds. */
    public static void finish(ServerPlayer p,ItemStack result){
        var s=SESSIONS.get(p.getUUID());require(p,result,null);
        if(!isDraft(p,result) || !s.held().is(MaterialInventory.plate(s.material())))throw new IllegalArgumentException("基板已更换，请重新开始刻印");
        var hand=p.getMainHandItem()==s.held()?net.minecraft.world.InteractionHand.MAIN_HAND:net.minecraft.world.InteractionHand.OFF_HAND;
        close(p);s.held().shrink(1);
        if(s.held().isEmpty())p.setItemInHand(hand,result);else MaterialInventory.give(p,result.copy());
    }
    public static void tick(ServerPlayer p){var s=SESSIONS.get(p.getUUID());if(s!=null && (p.serverLevel().dimension()!=s.dimension() || !table(p,s.table()) || s.held().isEmpty() || p.getMainHandItem()!=s.held() && p.getOffhandItem()!=s.held()))close(p);}
    public static void require(ServerPlayer p,ItemStack target,CircuitBlockEntity fixed){if(!allowed(p,target,fixed))throw new IllegalArgumentException("请持原物品与法阵工作台互动，且在工作台8格内完成刻印");}
    public static void close(ServerPlayer p,UUID carrier){var s=SESSIONS.get(p.getUUID());if(s!=null && s.carrier().equals(carrier))close(p);}
    public static void close(ServerPlayer p){var s=SESSIONS.remove(p.getUUID());if(s!=null && s.dimension()==p.serverLevel().dimension() && p.serverLevel().hasChunkAt(s.table()) && p.serverLevel().getBlockEntity(s.table()) instanceof WorkstationBlockEntity b)b.clear(p.getUUID());}
    public static void forget(UUID p){SESSIONS.remove(p);SELECTED.remove(p);}
}
