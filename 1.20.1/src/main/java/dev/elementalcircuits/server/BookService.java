package dev.elementalcircuits.server;
import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.*;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.network.BookNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Physical notebook owns immutable blueprints; physical spellbook owns a normal full assembly. */
public final class BookService {
    public static final UUID ZERO=new UUID(0,0);
    private static final Map<UUID,Long> LAST=new HashMap<>();
    private BookService(){ }
    public static BlueprintVolume volume(ItemStack stack){return dev.elementalcircuits.adapter.persistence.NotebookRepository.load(stack);}
    public static void write(ItemStack stack,BlueprintVolume v){dev.elementalcircuits.adapter.persistence.NotebookRepository.save(stack,v);}
    public static ItemStack findNotebook(ServerPlayer p,UUID id){ItemStack found=ItemStack.EMPTY;for(int i=0;i<p.getInventory().getContainerSize();i++){var s=p.getInventory().getItem(i);if(s.is(ElementalCircuits.NOTEBOOK.get()) && (id.equals(ZERO) || volume(s).identity().equals(id))){if(id.equals(ZERO))return s;if(!found.isEmpty())throw new IllegalArgumentException("蓝图簿身份重复");found=s;}}if(found.isEmpty())throw new IllegalArgumentException("请随身携带蓝图簿");return found;}
    public static void openNotebook(ServerPlayer p,ItemStack stack){try{BookNetwork.notebook(p,JsonCodecs.notebook(NotebookInspection.inspect(volume(stack),"选页预览；蓝图仅含配置")),true);}catch(RuntimeException e){message(p,e);}}
    private static void rate(ServerPlayer p){long now=p.serverLevel().getGameTime();if(LAST.containsKey(p.getUUID()) && now-LAST.get(p.getUUID())<2)throw new IllegalArgumentException("翻页或修改过快");LAST.put(p.getUUID(),now);}
    public static void notebookRequest(ServerPlayer p,UUID id,int action,int page,String title,String payload){ItemStack book=ItemStack.EMPTY;try{book=findNotebook(p,id);var v=volume(book);if(action==5 || action==6){openNotebook(p,book);return;}if(!v.identity().equals(id))throw new IllegalArgumentException("蓝图簿已改变");rate(p);var next=switch(action){case 0->v.select(page);case 1->v.add(title,BlueprintTextCode.decode(payload));case 2->v.rename(page,title);case 3->v.remove(page);case 4->v;default->throw new IllegalArgumentException("未知蓝图簿操作");};write(book,next);BookNetwork.notebook(p,JsonCodecs.notebook(NotebookInspection.inspect(next,"蓝图簿已保存")),false);}catch(RuntimeException e){try{if(!book.isEmpty())BookNetwork.notebook(p,JsonCodecs.notebook(NotebookInspection.inspect(volume(book),"拒绝："+e.getMessage())),false);else message(p,e);}catch(RuntimeException invalid){message(p,invalid);}}}
    public static ItemStack heldSpellbook(ServerPlayer p){var off=p.getOffhandItem();if(off.is(ElementalCircuits.SPELL_BOOK.get()))return off;var main=p.getMainHandItem();if(main.is(ElementalCircuits.SPELL_BOOK.get()))return main;throw new IllegalArgumentException("请持有法术书");}
    public static ItemStack castCarrier(ServerPlayer p,ItemStack wand){return CastingEquipment.carrier(p,wand);}
    public static ItemStack equipment(ServerPlayer p,ItemStack carrier){return CastingEquipment.wand(p,carrier);}
    public static boolean pairedBookPowered(ServerPlayer p){return CastingEquipment.pairedBookPowered(p);}
    public static void openSpellbook(ServerPlayer p,ItemStack book){try{if(!book.is(ElementalCircuits.SPELL_BOOK.get()))throw new IllegalArgumentException("需要法术书");var a=dev.elementalcircuits.adapter.persistence.CarrierRepository.load(book);BookNetwork.spellbook(p,JsonCodecs.spellbook(SpellbookInspection.inspect(a,"本书独立保存法阵；Z/X翻页，右键法杖起停当前阵")),true);hud(p);}catch(RuntimeException e){message(p,e);}}
    public static void spellRequest(ServerPlayer p,UUID id,int action,int value){try{var book=heldSpellbook(p);var a=dev.elementalcircuits.adapter.persistence.CarrierRepository.load(book);if(!a.identity().equals(id) && !(id.equals(ZERO) && action==2))throw new IllegalArgumentException("法术书已改变");if(action!=2 && action!=5)rate(p);switch(action){case 0->{CastingEquipment.wand(p,book);BookSelection.cycle(a,value);dev.elementalcircuits.adapter.persistence.CarrierRepository.commit(book);hud(p);return;}case 1->a.select(value);case 2->{openSpellbook(p,book);return;}case 3->{CarrierService.open(p);return;}case 5->{}case 4->{a.circuit(value);if(value!=a.selected())throw new IllegalArgumentException("当前法阵已改变");CastingEquipment.wand(p,book);CarrierService.start(p,book);}default->throw new IllegalArgumentException("未知法术书操作");}dev.elementalcircuits.adapter.persistence.CarrierRepository.commit(book);BookNetwork.spellbook(p,JsonCodecs.spellbook(SpellbookInspection.inspect(a,"当前法阵 "+a.selected()+"；切换保留全部运行状态")),false);hud(p);}catch(RuntimeException e){message(p,e);}}
    public static void hud(ServerPlayer p){if(p.getOffhandItem().is(ElementalCircuits.SPELL_BOOK.get())){var a=dev.elementalcircuits.adapter.persistence.CarrierRepository.load(p.getOffhandItem());BookNetwork.hud(p,a.identity(),a.selected(),a.slots().size(),a.circuit(a.selected()).powered());}}
    private static void message(ServerPlayer p,RuntimeException e){p.displayClientMessage(Component.literal(e.getMessage()==null?"物品数据异常，保留原记录":e.getMessage()),true);}
    public static void forget(UUID id){LAST.remove(id);}
}
