package dev.elementalcircuits.item;
import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.server.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import java.util.List;
public final class SpellbookItem extends Item {
    public SpellbookItem(Properties p){super(p);}
    @Override public InteractionResultHolder<ItemStack> use(Level l,Player p,InteractionHand hand){if(!l.isClientSide && p instanceof ServerPlayer server){if(p.isShiftKeyDown())CarrierService.open(server);else BookService.openSpellbook(server,p.getItemInHand(hand));}return InteractionResultHolder.sidedSuccess(p.getItemInHand(hand),l.isClientSide);}
    @Override public void inventoryTick(ItemStack s,Level l,Entity e,int slot,boolean selected){if(!l.isClientSide && e instanceof ServerPlayer p){try{CarrierService.identity(s);if(p.getOffhandItem()==s && p.getMainHandItem().is(ElementalCircuits.WAND.get()))CarrierService.tick(s,p);else {var a=CarrierService.assembly(s);if(a.powered()){a.stopAll();CarrierService.persist(s);}}}catch(RuntimeException bad){CarrierService.corrupt(s,p);}}}
    @Override public boolean onEntityItemUpdate(ItemStack s,ItemEntity e){if(!e.level().isClientSide)CarrierService.tickDropped(s,e);return false;}
    @Override public void appendHoverText(ItemStack s,Level l,List<Component> t,TooltipFlag f){t.add(Component.literal("独立保存最多4个法阵及完整运行状态"));t.add(Component.literal("主手法杖＋副手本书：Z/X切阵，B查看"));t.add(Component.literal("主手持书蹲下右键：刻印；新增槽位消耗基板"));}
}
