package dev.elementalcircuits.server.mana;

import dev.elementalcircuits.adapter.config.ServerSettings;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.circuit.CircuitBounds;
import dev.elementalcircuits.domain.mana.ManaField;
import dev.elementalcircuits.domain.mana.ManaOrigin;
import dev.elementalcircuits.domain.mana.Mixture;
import dev.elementalcircuits.server.circuit.WorldCircuits;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

/**
 * Persistent transfer outbox. Full, offline or unloaded sources retain their claims without
 * redirection.
 */
public final class WorldManaRefunds extends SavedData {
    private record Claim(String id, ManaOrigin origin, long amount, long paid) {}

    private final LinkedHashMap<String, List<Claim>> claims = new LinkedHashMap<>();
    private CompoundTag invalid;
    // Completed claims remain as idempotency tombstones, but never consume delivery work.
    // This insertion order is derived entirely from persisted claims on restore.
    private final List<String> order = new ArrayList<>();

    public WorldManaRefunds() {}

    public static WorldManaRefunds get(ServerLevel level) {
        return level.getServer()
                .overworld()
                .getDataStorage()
                .computeIfAbsent(
                        new Factory<>(WorldManaRefunds::new, (t, r) -> load(t)),
                        "elementalcircuits_mana_refunds");
    }

    public boolean accept(String id, Mixture mana) {
        if (invalid != null) return false;
        if (id == null || !id.matches("[a-zA-Z0-9/-]{1,100}") || mana == null || mana.empty())
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.server.mana._world_mana_refunds.001"));
        var sums = new TreeMap<String, Long>();
        for (var p : mana.order()) {
            if (!p.origin().refundable())
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.server.mana._world_mana_refunds.002"));
            sums.merge(p.origin().key(), p.amount(), Math::addExact);
        }
        var offered =
                sums.entrySet().stream()
                        .map(e -> new Claim(id, new ManaOrigin(e.getKey()), e.getValue(), 0))
                        .toList();
        var previous = claims.get(id);
        if (previous != null) {
            if (previous.size() != offered.size())
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.server.mana._world_mana_refunds.003"));
            for (int i = 0; i < previous.size(); i++)
                if (!previous.get(i).origin.equals(offered.get(i).origin)
                        || previous.get(i).amount != offered.get(i).amount)
                    throw new IllegalArgumentException(
                            dev.elementalcircuits.text.Texts.get(
                                    "text.elementalcircuits.server.mana._world_mana_refunds.004"));
            return true;
        }
        if (claims.size() >= ServerSettings.count("casting.refundClaimBudget")) return false;
        claims.put(id, offered);
        order.add(id);
        setDirty();
        return true;
    }

    public long pending() {
        return claims.values().stream()
                .flatMap(List::stream)
                .mapToLong(c -> c.amount - c.paid)
                .sum();
    }

    public void tick(ServerLevel level) {
        if (invalid != null || level != level.getServer().overworld() || order.isEmpty()) return;
        var keys = order;
        Set<String> completed = new HashSet<>();
        int work = Math.min(ServerSettings.count("casting.refundWorkPerTick"), keys.size());
        int start =
                (int) (Math.floorMod(level.getGameTime(), (long) keys.size()) * work % keys.size());
        for (int n = 0; n < work; n++) {
            String key = keys.get((start + n) % keys.size());
            var list = claims.get(key);
            ArrayList<Claim> updated = null;
            boolean done = true;
            for (int i = 0; i < list.size(); i++) {
                var claim = list.get(i);
                if (claim.paid == claim.amount) continue;
                long paid = claim.paid;
                paid =
                        Math.max(
                                paid,
                                deliver(
                                        level,
                                        claim.id
                                                + "/"
                                                + Base64.getUrlEncoder()
                                                        .withoutPadding()
                                                        .encodeToString(
                                                                claim.origin
                                                                        .key()
                                                                        .getBytes(
                                                                                java.nio.charset
                                                                                        .StandardCharsets
                                                                                        .UTF_8)),
                                        claim.origin,
                                        claim.amount));
                if (paid != claim.paid) {
                    if (updated == null) updated = new ArrayList<>(list);
                    updated.set(i, new Claim(key, claim.origin, claim.amount, paid));
                    setDirty();
                }
                done &= paid == claim.amount;
            }
            if (updated != null) claims.put(key, List.copyOf(updated));
            if (done) completed.add(key);
        }
        if (!completed.isEmpty()) order.removeIf(completed::contains);
    }

    private long deliver(ServerLevel world, String id, ManaOrigin origin, long amount) {
        String text = origin.key();
        try {
            if (text.startsWith("player/")) {
                var player =
                        world.getServer()
                                .getPlayerList()
                                .getPlayer(UUID.fromString(text.substring(7)));
                if (player == null) return 0;
                long accepted =
                        RefundReceipts.credit(
                                player.getPersistentData(),
                                id,
                                amount,
                                n -> PlayerMana.get(player).refund(n));
                PlayerMana.save(player);
                return accepted;
            }
            int end = text.lastIndexOf('/');
            if (end < 1) return 0;
            String kind = text.substring(0, text.indexOf('/')),
                    dimension = text.substring(kind.length() + 1, end),
                    target = text.substring(end + 1);
            var level =
                    world.getServer()
                            .getLevel(
                                    ResourceKey.create(
                                            Registries.DIMENSION,
                                            ResourceLocation.parse(dimension)));
            if (level == null) return 0;
            if (kind.equals("fixed")) {
                var block = WorldCircuits.block(level, UUID.fromString(target));
                return block == null ? 0 : block.refund(id, amount);
            }
            if (kind.equals("field")) {
                String[] xyz = target.split(",");
                if (xyz.length != 3) return 0;
                var cell =
                        new ManaField.Cell(
                                Integer.parseInt(xyz[0]),
                                Integer.parseInt(xyz[1]),
                                Integer.parseInt(xyz[2]));
                if (!level.hasChunk(Math.floorDiv(cell.x(), 2), Math.floorDiv(cell.z(), 2)))
                    return 0;
                var source = WorldManaField.get(level);
                if (!source.valid()) return 0;
                return source.refund(id, cell, amount);
            }
        } catch (IllegalArgumentException unavailable) {
            return 0;
        }
        return 0;
    }

    private static ListTag list(CompoundTag root, String key) {
        if (!root.contains(key, Tag.TAG_LIST))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.server.mana._world_mana_refunds.005"));
        var list = (ListTag) root.get(key);
        if (list.getElementType() != Tag.TAG_END && list.getElementType() != Tag.TAG_COMPOUND)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.server.mana._world_mana_refunds.006"));
        return list;
    }

    public static WorldManaRefunds load(CompoundTag tag) {
        var result = new WorldManaRefunds();
        try {
            if (!tag.contains("Version", Tag.TAG_INT)
                    || tag.getInt("Version") != 1
                    || !tag.contains("Claims", Tag.TAG_LIST))
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.server.mana._world_mana_refunds.007"));
            var list = list(tag, "Claims");
            if (list.size() > 1048576)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.server.mana._world_mana_refunds.008"));
            for (var v : list) {
                var t = (CompoundTag) v;
                String id = t.getString("Id");
                if (!id.matches("[a-zA-Z0-9/-]{1,100}")
                        || result.claims.containsKey(id)
                        || !t.contains("Sources", Tag.TAG_LIST))
                    throw new IllegalArgumentException(
                            dev.elementalcircuits.text.Texts.get(
                                    "text.elementalcircuits.server.mana._world_mana_refunds.009"));
                var sources = list(t, "Sources");
                if (sources.isEmpty() || sources.size() > CircuitBounds.MANA_SEGMENTS)
                    throw new IllegalArgumentException(
                            dev.elementalcircuits.text.Texts.get(
                                    "text.elementalcircuits.server.mana._world_mana_refunds.010"));
                var entries = new ArrayList<Claim>();
                var unique = new HashSet<ManaOrigin>();
                for (var value : sources) {
                    var s = (CompoundTag) value;
                    var origin = new ManaOrigin(s.getString("Origin"));
                    if (!origin.refundable()
                            || !unique.add(origin)
                            || !s.contains("Amount", Tag.TAG_LONG)
                            || !s.contains("Paid", Tag.TAG_LONG))
                        throw new IllegalArgumentException(
                                dev.elementalcircuits.text.Texts.get(
                                        "text.elementalcircuits.server.mana._world_mana_refunds.011"));
                    long amount = s.getLong("Amount"), paid = s.getLong("Paid");
                    if (amount <= 0 || amount > Fixed.MAX || paid < 0 || paid > amount)
                        throw new IllegalArgumentException(
                                dev.elementalcircuits.text.Texts.get(
                                        "text.elementalcircuits.server.mana._world_mana_refunds.012"));
                    entries.add(new Claim(id, origin, amount, paid));
                }
                // accept() canonicalizes by origin. Restore must use the same order so a retry
                // does not mistake a valid reordered record for a conflicting refund.
                entries.sort(Comparator.comparing(c -> c.origin.key()));
                result.claims.put(id, List.copyOf(entries));
                if (entries.stream().anyMatch(c -> c.paid < c.amount)) result.order.add(id);
            }
        } catch (RuntimeException e) {
            result.claims.clear();
            result.order.clear();
            result.invalid = tag.copy();
            org.slf4j.LoggerFactory.getLogger("ElementalCircuits")
                    .error(
                            dev.elementalcircuits.text.Texts.get(
                                    "text.elementalcircuits.server.mana._world_mana_refunds.013"),
                            e);
        }
        return result;
    }

    @Override
    public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        if (invalid != null) {
            tag.merge(invalid);
            return tag;
        }
        tag.putInt("Version", 1);
        var list = new ListTag();
        for (var e : claims.entrySet()) {
            var t = new CompoundTag();
            t.putString("Id", e.getKey());
            var sources = new ListTag();
            for (var c : e.getValue()) {
                var s = new CompoundTag();
                s.putString("Origin", c.origin.key());
                s.putLong("Amount", c.amount);
                s.putLong("Paid", c.paid);
                sources.add(s);
            }
            t.put("Sources", sources);
            list.add(t);
        }
        tag.put("Claims", list);
        return tag;
    }
}
