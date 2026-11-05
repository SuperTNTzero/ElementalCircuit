package dev.elementalcircuits.server.mana;

import dev.elementalcircuits.adapter.codec.JsonCodecs;
import dev.elementalcircuits.adapter.config.ServerSettings;
import dev.elementalcircuits.adapter.resources.RuleResources;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.circuit.Rules;
import dev.elementalcircuits.domain.mana.ManaPool;
import dev.elementalcircuits.network.CircuitNetwork;
import dev.elementalcircuits.server.equipment.SpellAttributes;

import net.minecraft.server.level.ServerPlayer;

import java.util.*;

/** Player owns one pool. Every carried wand shares it; no creative-mode bypass. */
public final class PlayerMana {
    private static final String POLICY = "elementalcircuits_mana_policy";
    private static final String KEY = "elementalcircuits_mana";
    private static final Map<ServerPlayer, ManaPool> POOLS = new WeakHashMap<>();
    private static final Map<ServerPlayer, ManaPool.State> SAVED = new WeakHashMap<>();

    private PlayerMana() {}

    public static ManaPool get(ServerPlayer player) {
        var result =
                POOLS.computeIfAbsent(
                        player,
                        p -> {
                            Rules rules = RuleResources.defaults();
                            if (p.getPersistentData().contains(KEY)) {
                                var pool =
                                        JsonCodecs.readPool(
                                                p.getPersistentData().getString(KEY),
                                                rules.playerRegen(),
                                                Math.max(rules.playerDelay(), readDelay(p)));
                                return pool;
                            }
                            return new ManaPool(
                                    ServerSettings.mana("player.initialCapacity"),
                                    ServerSettings.mana("player.regeneration"),
                                    ServerSettings.count("player.recoveryDelayTicks"));
                        });
        if (player.getPersistentData().contains(POLICY)
                && !player.getPersistentData().contains(POLICY, net.minecraft.nbt.Tag.TAG_COMPOUND))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.server.mana._player_mana.001"));
        var policy = player.getPersistentData().getCompound(POLICY);
        if (!policy.isEmpty()
                        && (!policy.contains("Version", net.minecraft.nbt.Tag.TAG_INT)
                                || policy.getInt("Version") != 1)
                || policy.contains("Regeneration")
                        && !policy.contains("Regeneration", net.minecraft.nbt.Tag.TAG_LONG)
                || policy.contains("Delay")
                        && !policy.contains("Delay", net.minecraft.nbt.Tag.TAG_INT))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.server.mana._player_mana.002"));
        long rate =
                policy.contains("Regeneration", net.minecraft.nbt.Tag.TAG_LONG)
                        ? policy.getLong("Regeneration")
                        : ServerSettings.mana("player.regeneration");
        int delay =
                policy.contains("Delay", net.minecraft.nbt.Tag.TAG_INT)
                        ? policy.getInt("Delay")
                        : ServerSettings.count("player.recoveryDelayTicks");
        result.equipmentBonus(
                Math.min(
                        Fixed.MAX,
                        Math.round(
                                SpellAttributes.value(player, SpellAttributes.MANA_CAPACITY)
                                        * Fixed.ONE)));
        rate =
                Math.min(
                        Fixed.MAX,
                        rate
                                + Math.round(
                                        SpellAttributes.value(
                                                        player, SpellAttributes.MANA_REGENERATION)
                                                * Fixed.ONE));
        var effect = player.getEffect(ManaBrewing.RECOVERY);
        if (effect != null)
            rate =
                    ServerSettings.recoveryRules()
                            .rate(rate, Math.clamp(effect.getAmplifier(), 0, 255));
        // Potion scales the fixed branch only. Capacity-based regeneration stays independent.
        long percent =
                java.math.BigInteger.valueOf(result.capacity())
                        .multiply(
                                java.math.BigInteger.valueOf(
                                        ServerSettings.mana("player.percentRegeneration")))
                        .divide(java.math.BigInteger.valueOf(Fixed.ONE))
                        .longValueExact();
        rate = Math.min(Fixed.MAX, rate + percent);
        result.configureRecovery(rate, delay);
        return result;
    }

    private static int readDelay(ServerPlayer p) {
        // Recover the historical wait before adopting a shorter policy; never refill during
        // migration.
        if (!p.getPersistentData().contains(KEY)) return 0;
        String raw = p.getPersistentData().getString(KEY);
        if (raw.length() > 2048)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.server.mana._player_mana.003"));
        var value = com.google.gson.JsonParser.parseString(raw).getAsJsonObject().get("waitTicks");
        if (value == null) return 0;
        return value.getAsBigDecimal().intValueExact();
    }

    public static void recovery(ServerPlayer p, Long rate, Integer delay, boolean reset) {
        var pool = get(p);
        var tag = p.getPersistentData().getCompound(POLICY).copy();
        if (reset) tag = new net.minecraft.nbt.CompoundTag();
        if (rate != null) {
            Fixed.checked(rate);
            tag.putLong("Regeneration", rate);
        }
        if (delay != null) {
            if (delay < 0 || delay > Integer.MAX_VALUE - 1)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.server.mana._player_mana.004"));
            tag.putInt("Delay", delay);
        }
        tag.putInt("Version", 1);
        p.getPersistentData().put(POLICY, tag);
        get(p);
        save(p);
    }

    public static void sync(ServerPlayer p) {
        save(p);
        var pool = get(p);
        CircuitNetwork.sendMana(p, pool.available(), pool.capacity());
    }

    public static boolean consumeFruit(ServerPlayer player) {
        var pool = get(player);
        if (!ServerSettings.growthRules().apply(pool)) return false;
        save(player);
        CircuitNetwork.sendMana(player, pool.available(), pool.capacity());
        return true;
    }

    public static void save(ServerPlayer player) {
        var pool = get(player);
        var state = pool.snapshot();
        if (state.equals(SAVED.get(player))) return;
        player.getPersistentData().putString(KEY, JsonCodecs.pool(pool));
        SAVED.put(player, state);
    }

    public static void clone(ServerPlayer oldPlayer, ServerPlayer newPlayer) {
        try {
            save(oldPlayer);
        } catch (RuntimeException ignored) {
            /* Preserve corrupt raw data as well; never refill on respawn. */
        }
        if (oldPlayer.getPersistentData().contains(KEY))
            newPlayer
                    .getPersistentData()
                    .putString(KEY, oldPlayer.getPersistentData().getString(KEY));
        if (oldPlayer.getPersistentData().contains(POLICY))
            newPlayer
                    .getPersistentData()
                    .put(POLICY, oldPlayer.getPersistentData().getCompound(POLICY).copy());
        if (oldPlayer.getPersistentData().contains(RefundReceipts.KEY))
            newPlayer
                    .getPersistentData()
                    .put(
                            RefundReceipts.KEY,
                            oldPlayer.getPersistentData().getCompound(RefundReceipts.KEY).copy());
        POOLS.remove(newPlayer);
        SAVED.remove(newPlayer);
    }

    public static void forget(ServerPlayer player) {
        POOLS.remove(player);
        SAVED.remove(player);
    }
}
