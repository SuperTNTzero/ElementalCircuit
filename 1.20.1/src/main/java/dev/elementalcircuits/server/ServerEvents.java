package dev.elementalcircuits.server;

import dev.elementalcircuits.network.CircuitNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class ServerEvents {
    private static final java.util.Set<java.util.UUID> CORRUPT_MANA=new java.util.HashSet<>();
    private ServerEvents(){ }
    @SubscribeEvent public static void world(TickEvent.LevelTickEvent event){if(event.phase==TickEvent.Phase.END && event.level instanceof net.minecraft.server.level.ServerLevel level){WorldCircuits.tick(level);WorldSpells.get(level).tick(level);WorldIllumination.get(level).tick(level);SpellVisuals.flush(level);SpellSounds.flush(level);}}
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event){
        if(event.phase!=TickEvent.Phase.END || !(event.player instanceof ServerPlayer player))return;
        try{var pool=PlayerMana.get(player);pool.tick();PlayerMana.save(player);
            if(player.tickCount%10==0){CircuitNetwork.sendMana(player,pool.available(),pool.capacity());try{BookService.hud(player);}catch(RuntimeException ignored){}}
        }catch(RuntimeException e){if(CORRUPT_MANA.add(player.getUUID())){
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("玩家魔力槽数据校验失败，已停用供给并保留原数据。"));
            org.slf4j.LoggerFactory.getLogger("ElementalCircuits").error("玩家 {} 魔力槽校验失败：{}",player.getUUID(),e.getMessage());
        }}
    }
    @SubscribeEvent public static void unload(net.minecraftforge.event.level.LevelEvent.Unload event){if(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level){WorldCircuits.unload(level);SpellVisuals.unload(level);SpellSounds.unload(level);}}
    @SubscribeEvent public static void breaking(net.minecraftforge.event.level.BlockEvent.BreakEvent event){
        if(event.getLevel().getBlockEntity(event.getPos()) instanceof dev.elementalcircuits.block.CircuitBlockEntity block && event.getPlayer() instanceof ServerPlayer player){
            try{if(!block.owns(player))throw new IllegalArgumentException("只有所有者可以回收固定法阵");
                if(WorldCircuitLinks.get(player.serverLevel()).references(block.assembly().identity(),0))throw new IllegalArgumentException("请先停止两端并断开全部外部线路，再回收法阵");
                CarrierService.persist(block.carrier());
            }catch(RuntimeException e){event.setCanceled(true);player.displayClientMessage(net.minecraft.network.chat.Component.literal(e.getMessage()==null?"法阵数据异常，拒绝回收":e.getMessage()),true);}
        }
    }
    @SubscribeEvent public static void clone(PlayerEvent.Clone event){if(event.getOriginal() instanceof ServerPlayer oldPlayer && event.getEntity() instanceof ServerPlayer newPlayer)PlayerMana.clone(oldPlayer,newPlayer);}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(event.getEntity() instanceof ServerPlayer player){try{PlayerMana.save(player);}catch(RuntimeException ignored){}PlayerMana.forget(player);CarrierService.forget(player.getUUID());WorkshopService.forget(player.getUUID());BookService.forget(player.getUUID());CORRUPT_MANA.remove(player.getUUID());}}
}
