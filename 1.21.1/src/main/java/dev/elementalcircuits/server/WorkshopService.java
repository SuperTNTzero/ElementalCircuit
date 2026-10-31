package dev.elementalcircuits.server;
import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.*;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.network.BookNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import java.util.*;

/** The server owns a specific wand/workbench session and revalidates it before each mutation. */
public final class WorkshopService {
    private record Session(UUID wand,ResourceKey<Level> dimension,BlockPos position,ItemStack held){ }
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();
    private static final Map<UUID,Long> LAST=new HashMap<>();
    private WorkshopService(){ }
    private static ItemStack valid(ServerPlayer p,Session s){var wand=p.getMainHandItem();if(s==null || p.serverLevel().dimension()!=s.dimension || !p.serverLevel().hasChunkAt(s.position) || !p.serverLevel().getBlockState(s.position).is(ElementalCircuits.WAND_WORKBENCH.get()) || p.position().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(s.position))>64 || !dev.elementalcircuits.block.WandWorkbenchBlock.complete(p.serverLevel(),s.position) || !p.isAlive() || !p.getAbilities().mayBuild || !p.serverLevel().mayInteract(p,s.position) || wand!=s.held || !wand.is(ElementalCircuits.WAND.get()) || !CarrierService.identity(wand).equals(s.wand))throw new IllegalArgumentException("请持原法杖回到该法杖工作台旁");return wand;}
    public static void open(ServerPlayer p,BlockPos pos){try{if(!p.getMainHandItem().is(ElementalCircuits.WAND.get()))throw new IllegalArgumentException("请主手持有法杖");if(!dev.elementalcircuits.block.WandWorkbenchBlock.ensureComplete(p,pos))throw new IllegalArgumentException("法杖工作台需要两个连续空格，请清出侧面的空间");var s=new Session(CarrierService.identity(p.getMainHandItem()),p.serverLevel().dimension(),pos.immutable(),p.getMainHandItem());var wand=valid(p,s);SESSIONS.put(p.getUUID(),s);sync(p,wand,s,true,"选择部件查看变化，安装时会退回旧件");}catch(RuntimeException e){p.displayClientMessage(Component.literal(e.getMessage()),true);}}
    public static void request(ServerPlayer p,UUID wandId,long position,int action,String value){try{var s=SESSIONS.get(p.getUUID());var wand=valid(p,s);if(!s.wand.equals(wandId) || s.position.asLong()!=position)throw new IllegalArgumentException("工作台目标已改变");if(action==3){if(p.serverLevel().getBlockEntity(s.position) instanceof dev.elementalcircuits.block.WorkstationBlockEntity b)b.clear();SESSIONS.remove(p.getUUID());return;}if(action!=2){long now=p.serverLevel().getGameTime();if(LAST.containsKey(p.getUUID()) && now-LAST.get(p.getUUID())<2)throw new IllegalArgumentException("操作过快");LAST.put(p.getUUID(),now);if(CarrierService.assembly(wand).powered() || CastingEquipment.pairedBookPowered(p))throw new IllegalArgumentException("请先停止法杖和副手法术书的全部法阵供给");if(action==0)WandEquipment.install(p,wand,value);else if(action==1)WandEquipment.repair(p,wand);else throw new IllegalArgumentException("未知工作台操作");}sync(p,wand,s,false,action==2?"工作台状态已更新":"已完成");}catch(RuntimeException e){var s=SESSIONS.get(p.getUUID());try{sync(p,valid(p,s),s,false,"拒绝："+e.getMessage());}catch(RuntimeException invalid){p.displayClientMessage(Component.literal("工作台已失效："+e.getMessage()),true);SESSIONS.remove(p.getUUID());}}}
    private static void sync(ServerPlayer p,ItemStack wand,Session s,boolean open,String message){if(p.serverLevel().getBlockEntity(s.position) instanceof dev.elementalcircuits.block.WorkstationBlockEntity b)b.show(p,wand);var stock=new HashMap<String,Integer>();for(var part:WandResources.definitions().parts())stock.put(part.id(),count(p,ElementalCircuits.PARTS.get(part.id()).get()));var v=new WorkshopInspection(s.wand,s.position.asLong(),WandEquipment.assembly(wand),wand.getDamageValue(),stock,count(p,ElementalCircuits.WOOD_PLATE.get()),message,true);BookNetwork.workshop(p,JsonCodecs.workshop(v),open);}
    private static int count(ServerPlayer p,net.minecraft.world.item.Item item){int n=0;for(int i=0;i<p.getInventory().getContainerSize();i++)if(p.getInventory().getItem(i).is(item))n+=p.getInventory().getItem(i).getCount();return n;}
    public static void forget(UUID p){SESSIONS.remove(p);LAST.remove(p);}
}
