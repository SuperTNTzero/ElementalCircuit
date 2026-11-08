package dev.elementalcircuits.server.spell;

import dev.elementalcircuits.adapter.codec.JsonCodecs;
import dev.elementalcircuits.adapter.config.ServerSettings;
import dev.elementalcircuits.adapter.persistence.JsonNbt;
import dev.elementalcircuits.application.observation.SpellSound;
import dev.elementalcircuits.application.observation.SpellVisual;
import dev.elementalcircuits.domain.Fixed;
import dev.elementalcircuits.domain.casting.*;
import dev.elementalcircuits.domain.circuit.MagicCircuit;
import dev.elementalcircuits.domain.combat.Element;
import dev.elementalcircuits.domain.mana.Packet;

import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.util.TriState;

import java.util.*;

/**
 * Persistent prepaid links. Live authority is re-established by each host at every world boundary.
 */
public final class WorldConnections extends SavedData {
    public record Key(UUID carrier, int slot, int output) {
        public Key {
            Objects.requireNonNull(carrier);
            if (slot < 1 || slot > 65535 || output < 1)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.server.spell._world_connections.001"));
        }
    }

    private record Source(UUID owner, Vec3 origin, Vec3 direction, long tick) {}

    private static final class Link {
        final Key key;
        final UUID owner, target;
        final BlockPos block;
        final String blockType;
        final double distance;
        Packet packet;
        final ConnectionBudget budget;
        boolean pending;
        long strength = Fixed.ONE;
        Vec3 reference;

        Link(
                Key key,
                UUID owner,
                UUID target,
                BlockPos block,
                String blockType,
                double distance,
                Packet packet,
                ConnectionBudget budget,
                boolean pending) {
            this.key = key;
            this.owner = owner;
            this.target = target;
            this.block = block;
            this.blockType = blockType;
            this.distance = distance;
            this.packet = packet;
            this.budget = budget;
            this.pending = pending;
        }
    }

    // Insertion order is persisted, giving stable payment and impulse arbitration.
    private final Map<Key, Link> links = new LinkedHashMap<>();
    private final Map<Key, Source> live = new HashMap<>();
    private CompoundTag invalid;

    public static WorldConnections get(ServerLevel level) {
        return level.getDataStorage()
                .computeIfAbsent(
                        new SavedData.Factory<>(WorldConnections::new, (t, r) -> load(t)),
                        "elementalcircuits_connections");
    }

    public int size() {
        return links.size();
    }

    public boolean contains(Key key) {
        return links.containsKey(key);
    }

    public void release(Key key) {
        live.remove(key);
        if (links.remove(key) != null) setDirty();
    }

    public void releaseCarrier(UUID carrier) {
        live.keySet().removeIf(k -> k.carrier.equals(carrier));
        if (links.keySet().removeIf(k -> k.carrier.equals(carrier))) setDirty();
    }

    public void releaseSlot(UUID carrier, int slot) {
        live.keySet().removeIf(k -> k.carrier.equals(carrier) && k.slot == slot);
        if (links.keySet().removeIf(k -> k.carrier.equals(carrier) && k.slot == slot)) setDirty();
    }

    public long allowance(Key key) {
        var link = links.get(key);
        return link == null ? 0 : link.budget.remaining();
    }

    public UUID target(Key key) {
        var link = links.get(key);
        return link == null ? null : link.target;
    }

    public boolean active(UUID carrier, int slot) {
        return links.keySet().stream()
                .anyMatch(k -> k.carrier().equals(carrier) && k.slot() == slot);
    }

    /** A release signal addresses one contact rune, rather than every output on the carrier. */
    public void releaseControl(UUID carrier, int slot, int control) {
        for (var link : List.copyOf(links.values()))
            if (link.key.carrier().equals(carrier)
                    && link.key.slot() == slot
                    && link.packet.spell().connection().control() == control) release(link.key);
    }

    public void touch(ServerLevel level, Key key, UUID owner, Vec3 origin, Vec3 direction) {
        if (invalid != null || ServerSettings.count("contact.enabled") == 0) return;
        if (!links.containsKey(key) && live.size() >= ServerSettings.count("contact.maxLinks"))
            return;
        if (!finite(origin) || !finite(direction) || direction.lengthSqr() < .0001) return;
        live.put(key, new Source(owner, origin, direction.normalize(), level.getGameTime()));
    }

    /** Keep prepaid links anchored while their owning circuit remains powered. */
    public void refreshSlot(
            ServerLevel level, UUID carrier, int slot, UUID owner, Vec3 origin, Vec3 direction) {
        for (Key key : links.keySet().stream()
                .filter(k -> k.carrier().equals(carrier) && k.slot() == slot)
                .toList()) touch(level, key, owner, origin, direction);
    }

    public void offer(ServerLevel level, Key key, MagicCircuit.Cast cast) {
        offer(level, key, cast, Fixed.ONE);
    }

    public void offer(ServerLevel level, Key key, MagicCircuit.Cast cast, long strength) {
        if (strength < Fixed.ONE / 4 || strength > 2 * Fixed.ONE)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.server.spell._world_connections.002"));
        Source source = live.get(key);
        SpellSpec spec = cast.spell();
        if (invalid != null
                || source == null
                || source.tick != level.getGameTime()
                || spec.method() != SpellSpec.Method.CONTACT
                || !spec.compatibleOutput()) return;
        var mana =
                dev.elementalcircuits.domain.combat.ElementReactionQueue.reduce(
                                cast.mana(),
                                dev.elementalcircuits.adapter.resources.ElementalCombatResources
                                        .rules()
                                        .reactions())
                        .remaining();
        long[] usable = mana.values();
        usable[Element.ARCANE.component(true)] = 0; // Residual negation cannot fund physical work.
        mana = mana.select(usable);
        if (mana.empty()) return;
        // Arrival order has already been consumed by reactions. Ongoing work keeps a small
        // surviving signature and no refundable supply/provenance history.
        mana = new dev.elementalcircuits.domain.mana.Mixture(mana.values());
        source = oriented(source, spec);
        Link link = links.get(key);
        boolean created = false;
        if (link != null
                && (!Objects.equals(link.owner, source.owner)
                        || !sameEffects(link.packet.spell(), spec))) {
            links.remove(key);
            link = null;
        }
        if (link == null) {
            UUID owner = source.owner;
            if (links.size() >= ServerSettings.count("contact.maxLinks")
                    || links.values().stream().filter(l -> Objects.equals(l.owner, owner)).count()
                            >= ServerSettings.count("contact.perOwner")) return;
            int range = range(spec);
            if (!SpellEffects.loadedBetween(
                    level, source.origin, source.origin.add(source.direction.scale(range)))) return;
            // OUTLINE includes buttons and levers, which have no collision shape.
            var trace =
                    SpellEffects.trace(
                            level,
                            source.owner,
                            source.origin,
                            source.origin.add(source.direction.scale(range)),
                            true,
                            true,
                            false,
                            true,
                            Set.of(),
                            e -> matches(spec.connection().target(), e));
            if (!trace.blocked() || trace.entity() == null && trace.block() == null) return;
            Entity entity = trace.entity();
            BlockPos block = trace.block() == null ? null : trace.block().getBlockPos().immutable();
            if (entity == null
                    && spec.connection().target() != 0
                    && spec.connection().target() != 3) return;
            if (entity != null && !allowed(level, source.owner, entity)
                    || block != null && !CastPermissions.mayChangeBlock(level, source.owner, block))
                return;
            double distance =
                    entity == null
                            ? source.origin.distanceTo(trace.position())
                            : source.origin.distanceTo(entity.getBoundingBox().getCenter());
            link =
                    new Link(
                            key,
                            source.owner,
                            entity == null ? null : entity.getUUID(),
                            block,
                            block == null
                                    ? ""
                                    : BuiltInRegistries.BLOCK
                                            .getKey(level.getBlockState(block).getBlock())
                                            .toString(),
                            distance,
                            new Packet(mana, spec),
                            new ConnectionBudget(0, false),
                            false);
            if (spec.connection().anchored()) {
                double hold =
                        Math.min(
                                range(spec),
                                spec.spatial().distance() > 0
                                        ? spec.spatial().distance() / (double) Fixed.ONE
                                        : distance);
                link.reference = source.origin.add(source.direction.scale(hold));
            }
            links.put(key, link);
            created = true;
        }
        // Delivery has already paid circuit losses/costs. This allowance cannot be withdrawn or
        // refunded.
        link.budget.add(spec.effectScale().apply(mana).total());
        link.packet = new Packet(mana, spec);
        link.strength = strength;
        if (link.budget.edge(spec.connection().interact() && spec.connection().high()))
            link.pending = true;
        if (created) {
            Vec3 target =
                    link.target == null
                            ? Vec3.atCenterOf(link.block)
                            : Optional.ofNullable(level.getEntity(link.target))
                                    .map(Entity::getBoundingBox)
                                    .map(AABB::getCenter)
                                    .orElse(source.origin.add(source.direction));
            Element element = link.packet.mana().dominant();
            SpellVisuals.observe(
                    level,
                    SpellVisual.Kind.CAST,
                    element,
                    source.origin,
                    source.direction,
                    .9f,
                    2,
                    spec.customEffectId());
            SpellVisuals.observe(
                    level,
                    SpellVisual.Kind.IMPACT,
                    element,
                    target,
                    source.direction,
                    1.15f,
                    3,
                    spec.customEffectId());
            SpellSounds.observe(level, SpellSound.Kind.CAST, element, source.origin);
            SpellSounds.observe(level, SpellSound.Kind.IMPACT, element, target);
        }
        setDirty();
    }

    private static boolean sameEffects(SpellSpec a, SpellSpec b) {
        var x = a.connection();
        var y = b.connection();
        return a.spatial().equals(b.spatial())
                && a.range() == b.range()
                && x.force() == y.force()
                && x.constraint() == y.constraint()
                && x.anchored() == y.anchored()
                && x.target() == y.target()
                && x.control() == y.control()
                && x.interact() == y.interact();
    }

    public void tick(ServerLevel level) {
        if (invalid != null) {
            live.clear();
            return;
        }
        Map<UUID, Double> impulses = new HashMap<>();
        Map<UUID, Integer> owners = new HashMap<>();
        int retained = 0;
        for (Link link : List.copyOf(links.values())) {
            Source source = live.get(link.key);
            if (ServerSettings.count("contact.enabled") == 0
                    || retained >= ServerSettings.count("contact.maxLinks")
                    || owners.getOrDefault(link.owner, 0)
                            >= ServerSettings.count("contact.perOwner")
                    || source == null
                    || source.tick != level.getGameTime()
                    || !Objects.equals(source.owner, link.owner)
                    || !step(level, source, link, impulses)) links.remove(link.key, link);
            else if (links.get(link.key) == link) {
                retained++;
                owners.merge(link.owner, 1, Integer::sum);
            }
            setDirty();
        }
        live.clear();
    }

    private boolean step(ServerLevel level, Source source, Link link, Map<UUID, Double> impulses) {
        SpellSpec spec = link.packet.spell();
        source = oriented(source, spec);
        Entity entity = link.target == null ? null : level.getEntity(link.target);
        if (link.target != null
                && (entity == null
                        || !allowed(level, link.owner, entity)
                        || !finite(entity.position())
                        || !finite(entity.getDeltaMovement()))) return false;
        if (link.block != null
                && (!level.hasChunkAt(link.block)
                        || !CastPermissions.mayChangeBlock(level, link.owner, link.block)
                        || !BuiltInRegistries.BLOCK
                                .getKey(level.getBlockState(link.block).getBlock())
                                .toString()
                                .equals(link.blockType))) return false;
        Vec3 target =
                entity == null ? Vec3.atCenterOf(link.block) : entity.getBoundingBox().getCenter();
        double distance = target.distanceTo(source.origin);
        if (distance > range(spec) + .5
                || !SpellEffects.loadedBetween(level, source.origin, target)) return false;
        var obstruction =
                level.clip(
                        new ClipContext(
                                source.origin,
                                target,
                                ClipContext.Block.COLLIDER,
                                ClipContext.Fluid.NONE,
                                net.minecraft.world.phys.shapes.CollisionContext.empty()));
        if (obstruction.getType() != HitResult.Type.MISS
                && (link.block == null || !obstruction.getBlockPos().equals(link.block)))
            return false;
        double load =
                entity == null
                        ? 1
                        : Math.max(
                                .25,
                                entity.getBbWidth() * entity.getBbWidth() * entity.getBbHeight());
        if (!link.budget.pay(
                ConnectionBudget.cost(
                        ServerSettings.get("contact.maintenance")
                                + distance * distance * ServerSettings.get("contact.distanceCost")
                                + load * ServerSettings.get("contact.loadCost")))) return false;
        var effect = spec.connection();
        double potency = spec.damageMultiplier() / (double) Fixed.ONE * link.strength / Fixed.ONE;
        if (entity != null && (effect.force() > 0 || effect.constraint() > 0)) {
            long[] mana = link.packet.mana().values();
            double normal = mana[0], inverse = 0;
            for (int c = 1; c < mana.length; c++) {
                if (c % 2 == 1) normal += mana[c];
                else inverse += mana[c];
            }
            double sign = (normal - inverse) / Math.max(1, normal + inverse);
            Vec3 force =
                    source.direction.scale(
                            .05
                                    * Math.min(32, effect.force() / (double) Fixed.ONE * potency)
                                    * sign);
            if (effect.constraint() > 0) {
                double hold =
                        Math.min(
                                range(spec),
                                spec.spatial().distance() > 0
                                        ? spec.spatial().distance() / (double) Fixed.ONE
                                        : link.distance);
                Vec3 reference =
                        effect.anchored()
                                ? link.reference
                                : source.origin.add(source.direction.scale(hold));
                if (!SpellEffects.loadedBetween(level, source.origin, reference)) return false;
                var clip =
                        level.clip(
                                new ClipContext(
                                        source.origin,
                                        reference,
                                        ClipContext.Block.COLLIDER,
                                        ClipContext.Fluid.NONE,
                                        net.minecraft.world.phys.shapes.CollisionContext.empty()));
                if (clip.getType() != HitResult.Type.MISS)
                    reference =
                            clip.getLocation()
                                    .subtract(
                                            reference
                                                    .subtract(source.origin)
                                                    .normalize()
                                                    .scale(entity.getBbWidth() / 2 + .15));
                double spring =
                        Math.min(1, effect.constraint() / (double) Fixed.ONE * potency * .12);
                force =
                        force.add(reference.subtract(target).scale(spring))
                                .subtract(entity.getDeltaMovement().scale(.35))
                                .add(
                                        0,
                                        entity.isNoGravity()
                                                ? 0
                                                : entity instanceof ItemEntity ? .04 : .08,
                                        0);
            }
            double available =
                    Math.max(
                            0,
                            ServerSettings.get("contact.maxImpulse")
                                    - impulses.getOrDefault(entity.getUUID(), 0.0));
            var v = entity.getDeltaMovement();
            var actual =
                    ConnectionMotion.impulse(
                            new ConnectionMotion.Vector(v.x, v.y, v.z),
                            new ConnectionMotion.Vector(force.x, force.y, force.z),
                            available / Math.max(1, load),
                            ServerSettings.get("contact.maxSpeed"));
            force = new Vec3(actual.x(), actual.y(), actual.z());
            double impulse = force.length();
            if (impulse > .000001
                    && link.budget.pay(
                            ConnectionBudget.cost(
                                    impulse * load * ServerSettings.get("contact.workCost")))) {
                entity.setDeltaMovement(entity.getDeltaMovement().add(force));
                entity.hurtMarked = true;
                impulses.merge(entity.getUUID(), impulse, Double::sum);
            }
        }
        if (link.pending) {
            link.pending = false;
            if (link.block != null
                    && effect.interact()
                    && link.budget.pay(
                            ConnectionBudget.cost(ServerSettings.get("contact.interactionCost"))))
                interact(level, link, source);
        }
        if (level.getGameTime() % 2 == 0)
            SpellVisuals.observe(
                    level,
                    SpellVisual.Kind.BEAM,
                    link.packet.mana().dominant(),
                    source.origin,
                    target,
                    .62f,
                    3);
        return true;
    }

    private static void interact(ServerLevel level, Link link, Source source) {
        // Narrow native adapter, extensible without giving links generic container capabilities.
        var state = level.getBlockState(link.block);
        if (!(state.getBlock() instanceof LeverBlock
                || state.getBlock() instanceof ButtonBlock
                || state.getBlock() instanceof BellBlock)) return;
        if (link.owner == null
                || !(level.getEntity(link.owner) instanceof ServerPlayer player)
                || player.isSpectator()
                || !CastPermissions.mayChangeBlock(level, link.owner, link.block)) return;
        var hit =
                new BlockHitResult(
                        Vec3.atCenterOf(link.block),
                        Direction.getNearest(
                                -source.direction.x, -source.direction.y, -source.direction.z),
                        link.block,
                        false);
        var event =
                CommonHooks.onRightClickBlock(player, InteractionHand.MAIN_HAND, link.block, hit);
        if (!event.isCanceled() && event.getUseBlock() != TriState.FALSE)
            state.useWithoutItem(level, player, hit);
    }

    private static boolean allowed(ServerLevel level, UUID owner, Entity entity) {
        if (!entity.isAlive()
                || entity.isRemoved()
                || entity.isSpectator()
                || entity.getUUID().equals(owner)
                || entity.isPassenger()
                || entity.isVehicle()) return false;
        if (entity instanceof LivingEntity living)
            return CastPermissions.mayAffect(level, owner, living);
        return entity instanceof ItemEntity item
                && (item.getTarget() == null || item.getTarget().equals(owner))
                && CastPermissions.mayChangeBlock(level, owner, item.blockPosition());
    }

    private static int range(SpellSpec spec) {
        return Math.min(
                ServerSettings.count("contact.range"), spec.range() > 0 ? spec.range() : 12);
    }

    private static Source oriented(Source source, SpellSpec spec) {
        double[] v =
                spec.spatial()
                        .direction(source.direction.x, source.direction.y, source.direction.z);
        return new Source(source.owner, source.origin, new Vec3(v[0], v[1], v[2]), source.tick);
    }

    private static boolean finite(Vec3 v) {
        return Double.isFinite(v.x)
                && Double.isFinite(v.y)
                && Double.isFinite(v.z)
                && Math.abs(v.x) <= 30000100
                && Math.abs(v.y) <= 30000100
                && Math.abs(v.z) <= 30000100;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (invalid != null) return invalid.copy();
        tag.putInt("Version", 2);
        ListTag list = new ListTag();
        for (Link link : links.values()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("Carrier", link.key.carrier);
            t.putInt("Slot", link.key.slot);
            t.putInt("Output", link.key.output);
            if (link.owner != null) t.putUUID("Owner", link.owner);
            if (link.target != null) t.putUUID("Target", link.target);
            else {
                t.putLong("Block", link.block.asLong());
                t.putString("Type", link.blockType);
            }
            t.putDouble("Distance", link.distance);
            if (link.reference != null) {
                t.putDouble("AnchorX", link.reference.x);
                t.putDouble("AnchorY", link.reference.y);
                t.putDouble("AnchorZ", link.reference.z);
            }
            t.putLong("Allowance", link.budget.remaining());
            t.putLong("Strength", link.strength);
            t.putBoolean("High", link.budget.high());
            t.putBoolean("Pending", link.pending);
            t.put("Packet", JsonNbt.encode(JsonCodecs.packetText(link.packet), 16384));
            list.add(t);
        }
        tag.put("Links", list);
        return tag;
    }

    public static WorldConnections load(CompoundTag tag) {
        var data = new WorldConnections();
        try {
            if ((tag.getInt("Version") < 1 || tag.getInt("Version") > 2)
                    || !tag.contains("Links", Tag.TAG_LIST))
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.server.spell._world_connections.003"));
            ListTag list = (ListTag) tag.get("Links");
            if (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.server.spell._world_connections.004"));
            if (list.size() > 4096)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.server.spell._world_connections.005"));
            for (int i = 0; i < list.size(); i++) {
                var t = list.getCompound(i);
                if (!t.contains("Slot", Tag.TAG_INT)
                        || !t.contains("Output", Tag.TAG_INT)
                        || !t.contains("Distance", Tag.TAG_DOUBLE)
                        || !t.contains("High", Tag.TAG_BYTE)
                        || !t.contains("Pending", Tag.TAG_BYTE)
                        || t.hasUUID("Target") == t.contains("Block", Tag.TAG_LONG))
                    throw new IllegalArgumentException(
                            dev.elementalcircuits.text.Texts.get(
                                    "text.elementalcircuits.server.spell._world_connections.006"));
                var key = new Key(t.getUUID("Carrier"), t.getInt("Slot"), t.getInt("Output"));
                var packet = JsonCodecs.readPacket(JsonNbt.decode(t.get("Packet"), 16384));
                double distance = t.getDouble("Distance");
                if (!Double.isFinite(distance)
                        || distance < 0
                        || distance > 257
                        || packet.spell().method() != SpellSpec.Method.CONTACT
                        || !packet.spell().compatibleOutput()
                        || packet.mana().empty()
                        || !t.contains("Allowance", Tag.TAG_LONG))
                    throw new IllegalArgumentException(
                            dev.elementalcircuits.text.Texts.get(
                                    "text.elementalcircuits.server.spell._world_connections.007"));
                var link =
                        new Link(
                                key,
                                t.hasUUID("Owner") ? t.getUUID("Owner") : null,
                                t.hasUUID("Target") ? t.getUUID("Target") : null,
                                t.hasUUID("Target") ? null : BlockPos.of(t.getLong("Block")),
                                t.getString("Type"),
                                distance,
                                packet,
                                new ConnectionBudget(t.getLong("Allowance"), t.getBoolean("High")),
                                t.getBoolean("Pending"));
                link.strength =
                        t.contains("Strength", Tag.TAG_LONG) ? t.getLong("Strength") : Fixed.ONE;
                if (packet.spell().connection().anchored()) {
                    if (tag.getInt("Version") < 2
                            || !t.contains("AnchorX", Tag.TAG_DOUBLE)
                            || !t.contains("AnchorY", Tag.TAG_DOUBLE)
                            || !t.contains("AnchorZ", Tag.TAG_DOUBLE))
                        throw new IllegalArgumentException(
                                dev.elementalcircuits.text.Texts.get(
                                        "text.elementalcircuits.server.spell._world_connections.008"));
                    link.reference =
                            new Vec3(
                                    t.getDouble("AnchorX"),
                                    t.getDouble("AnchorY"),
                                    t.getDouble("AnchorZ"));
                    if (!finite(link.reference))
                        throw new IllegalArgumentException(
                                dev.elementalcircuits.text.Texts.get(
                                        "text.elementalcircuits.server.spell._world_connections.009"));
                }
                if (link.strength < Fixed.ONE / 4 || link.strength > 2 * Fixed.ONE)
                    throw new IllegalArgumentException(
                            dev.elementalcircuits.text.Texts.get(
                                    "text.elementalcircuits.server.spell._world_connections.010"));
                if (data.links.putIfAbsent(key, link) != null)
                    throw new IllegalArgumentException(
                            dev.elementalcircuits.text.Texts.get(
                                    "text.elementalcircuits.server.spell._world_connections.011"));
            }
        } catch (RuntimeException error) {
            data.links.clear();
            data.invalid = tag.copy();
        }
        return data;
    }

    private static boolean matches(int filter, Entity entity) {
        return switch (filter) {
            case 0 -> true;
            case 1 -> entity instanceof ItemEntity;
            case 2 -> entity instanceof LivingEntity;
            default -> false;
        };
    }
}
