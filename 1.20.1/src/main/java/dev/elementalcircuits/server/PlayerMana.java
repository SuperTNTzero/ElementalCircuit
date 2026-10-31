package dev.elementalcircuits.server;

import dev.elementalcircuits.adapter.*;
import dev.elementalcircuits.domain.*;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

/** Player owns one pool. Every carried wand shares it; no creative-mode bypass. */
public final class PlayerMana {
    private static final String KEY="elementalcircuits_mana";
    private static final Map<ServerPlayer,ManaPool> POOLS=new WeakHashMap<>();
    private static final Map<ServerPlayer,ManaPool.State> SAVED=new WeakHashMap<>();
    private PlayerMana(){ }
    public static ManaPool get(ServerPlayer player){
        return POOLS.computeIfAbsent(player,p->{
            Rules rules=RuleResources.defaults();
            if(p.getPersistentData().contains(KEY))return JsonCodecs.readPool(p.getPersistentData().getString(KEY),rules.playerRegen(),rules.playerDelay());
            return new ManaPool(rules.playerCapacity(),rules.playerRegen(),rules.playerDelay());
        });
    }
    public static void save(ServerPlayer player){var pool=get(player);var state=pool.snapshot();if(state.equals(SAVED.get(player)))return;player.getPersistentData().putString(KEY,JsonCodecs.pool(pool));SAVED.put(player,state);}
    public static void clone(ServerPlayer oldPlayer,ServerPlayer newPlayer){
        try{save(oldPlayer);}catch(RuntimeException ignored){ /* Preserve corrupt raw data as well; never refill on respawn. */ }
        if(oldPlayer.getPersistentData().contains(KEY))newPlayer.getPersistentData().putString(KEY,oldPlayer.getPersistentData().getString(KEY));POOLS.remove(newPlayer);SAVED.remove(newPlayer);
    }
    public static void forget(ServerPlayer player){POOLS.remove(player);SAVED.remove(player);}
}
