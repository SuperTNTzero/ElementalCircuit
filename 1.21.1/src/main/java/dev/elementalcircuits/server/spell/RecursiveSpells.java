package dev.elementalcircuits.server.spell;

import dev.elementalcircuits.adapter.codec.JsonCodecs;
import dev.elementalcircuits.adapter.config.ServerSettings;
import dev.elementalcircuits.adapter.persistence.JsonNbt;
import dev.elementalcircuits.adapter.resources.ShapeResources;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.casting.CastContext;
import dev.elementalcircuits.domain.casting.RecursionRules;
import dev.elementalcircuits.domain.casting.SpellSpec;
import dev.elementalcircuits.domain.casting.TriggerBinding;
import dev.elementalcircuits.domain.casting.TriggerCondition;
import dev.elementalcircuits.domain.casting.TriggeredCircuit;
import dev.elementalcircuits.domain.circuit.Blueprint;
import dev.elementalcircuits.domain.circuit.CircuitAssembly;
import dev.elementalcircuits.domain.circuit.InvocationProgram;
import dev.elementalcircuits.domain.mana.Mixture;
import dev.elementalcircuits.domain.mana.Packet;
import dev.elementalcircuits.server.mana.WorldManaRefunds;

import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * World-owned paid continuations. Serialized inside WorldSpells, never in a second SavedData file.
 */
final class RecursiveSpells {
    record Cursor(UUID chain, int slot, int depth) {}

    record Prepared(Packet packet, UUID arm) {}

    private record Frozen(InvocationProgram program, String encoded, int bytes) {}

    private static final class Chain {
        final UUID program, owner;
        final long strength;
        final CastContext context;
        int left;

        Chain(UUID program, UUID owner, long strength, int left, CastContext context) {
            this.context = context;
            this.program = program;
            this.owner = owner;
            this.strength = strength;
            this.left = left;
        }
    }

    private record Arm(UUID chain, int slot, int depth, int delay, Mixture mana) {}

    private record Job(
            UUID identity,
            UUID chain,
            int slot,
            int depth,
            Vec3 position,
            Vec3 direction,
            TriggeredCircuit circuit) {}

    private record Cached(
            int root,
            List<Blueprint> definitions,
            List<TriggerBinding> bindings,
            RecursionRules rules,
            Frozen frozen) {}

    private final Map<UUID, Frozen> programs = new LinkedHashMap<>();
    private final Map<UUID, Chain> chains = new LinkedHashMap<>();
    private final Map<UUID, Arm> arms = new LinkedHashMap<>();
    private final Deque<Job> jobs = new ArrayDeque<>();
    private final Map<CircuitAssembly, Cached> cache = new WeakHashMap<>();
    private static final int PROGRAM_BYTES = 33554432;
    private int activeUnits;
    private long nextArm;

    boolean empty() {
        return chains.isEmpty() && arms.isEmpty() && jobs.isEmpty();
    }

    int jobs() {
        return jobs.size();
    }

    int arms() {
        return arms.size();
    }

    private int units(Blueprint b) {
        return b.runes().size() + b.routes().size() * 2;
    }

    Cursor root(
            CircuitAssembly assembly,
            int slot,
            UUID owner,
            long strength,
            SpellSpec spec,
            CastContext context) {
        var layers = assembly.layers(slot);
        if (layers.trigger(1) == null
                || !InvocationProgram.projectile(spec)
                || chains.size() >= ServerSettings.count("recursion.maxChains")) return null;
        List<Blueprint> definitions = layers.ids().stream().map(layers::definition).toList();
        var bindings = layers.state().bindings();
        var rules = ServerSettings.recursionRules();
        int depth =
                ServerSettings.layerRules()
                        .depth(assembly.circuit(slot).definition().rules().material());
        rules =
                new RecursionRules(
                        1, depth, rules.activations(), rules.jobTicks(), rules.retention());
        var cached = cache.get(assembly);
        boolean same =
                cached != null
                        && cached.root == slot
                        && cached.rules.equals(rules)
                        && cached.bindings.equals(bindings)
                        && cached.definitions.size() == definitions.size();
        if (same)
            for (int i = 0; i < definitions.size(); i++)
                same &= cached.definitions.get(i) == definitions.get(i);
        if (!same) {
            var p = InvocationProgram.freeze(assembly, slot, rules, depth);
            String encoded = JsonCodecs.program(p);
            int bytes = encoded.getBytes(StandardCharsets.UTF_8).length;
            if (bytes > 16777216) return null;
            cached = new Cached(slot, definitions, bindings, rules, new Frozen(p, encoded, bytes));
            cache.put(assembly, cached);
        }
        Frozen frozen = cached.frozen;
        UUID program = null;
        for (var e : programs.entrySet())
            if (e.getValue() == frozen) {
                program = e.getKey();
                break;
            }
        if (program == null) {
            if (programs.values().stream().mapToLong(Frozen::bytes).sum() + frozen.bytes
                    > PROGRAM_BYTES) return null;
            program = UUID.randomUUID();
            programs.put(program, frozen);
        }
        UUID chain = UUID.randomUUID();
        chains.put(
                chain,
                new Chain(
                        program,
                        owner,
                        strength,
                        rules.activations(),
                        context.withSpeed(Fixed.ONE)));
        return new Cursor(chain, 1, 0);
    }

    Prepared arm(Cursor cursor, Packet packet) {
        if (cursor == null
                || nextArm == Long.MAX_VALUE
                || !InvocationProgram.projectile(packet.spell())) return new Prepared(packet, null);
        Chain chain = chains.get(cursor.chain);
        if (chain == null) return new Prepared(packet, null);
        var program = programs.get(chain.program).program;
        var binding = program.binding(cursor.slot);
        if (binding == null || cursor.depth >= program.rules().maxDepth() || chain.left <= 0)
            return new Prepared(packet, null);
        long amount = packet.mana().total() * binding.percent() / 100;
        if (amount == 0) return new Prepared(packet, null);
        Mixture reserved = packet.mana().take(amount);
        UUID id =
                new UUID(
                        cursor.chain.getMostSignificantBits()
                                ^ cursor.chain.getLeastSignificantBits(),
                        nextArm++);
        arms.put(id, new Arm(cursor.chain, cursor.slot, cursor.depth, binding.delay(), reserved));
        return new Prepared(new Packet(packet.mana().minus(reserved), packet.spell()), id);
    }

    long reserved(UUID id) {
        var arm = arms.get(id);
        return arm == null ? 0 : arm.mana.total();
    }

    void absorb(UUID id, long amount) {
        var arm = arms.get(id);
        if (arm == null) {
            if (amount != 0)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.server.spell._recursive_spells.001"));
            return;
        }
        Mixture next = arm.mana.minus(arm.mana.take(amount));
        if (next.empty()) arms.remove(id);
        else arms.put(id, new Arm(arm.chain, arm.slot, arm.depth, arm.delay, next));
    }

    void cancel(UUID id) {
        if (id != null) arms.remove(id);
    }

    /**
     * One authority callback per loaded flight step. True clears the flight's capsule reference.
     */
    boolean advance(UUID id, Vec3 at, Vec3 direction, boolean terminal) {
        if (id == null) return false;
        Arm arm = arms.get(id);
        if (arm == null) return true;
        Chain chain = chains.get(arm.chain);
        var program = programs.get(chain.program).program;
        var binding = program.binding(arm.slot);
        var decision =
                binding.when()
                        .evaluate(
                                terminal
                                        ? TriggerCondition.Event.TERMINAL
                                        : TriggerCondition.Event.FLIGHT_TICK,
                                arm.delay);
        if (decision != TriggerCondition.Decision.FIRE) {
            if (decision == TriggerCondition.Decision.COUNTDOWN)
                arms.put(id, new Arm(arm.chain, arm.slot, arm.depth, arm.delay - 1, arm.mana));
            return false;
        }
        arms.remove(id);
        Blueprint definition = program.definition(binding.target());
        int cost = units(definition);
        if (chain.left <= 0
                || arm.depth >= program.rules().maxDepth()
                || jobs.size() >= ServerSettings.count("recursion.maxJobs")
                || activeUnits + cost > ServerSettings.count("recursion.activeUnits")) return true;
        long budget = Fixed.mul(arm.mana.total(), program.rules().retention());
        if (budget == 0) return true;
        try {
            var circuit =
                    new TriggeredCircuit(
                            definition,
                            arm.mana.retain(program.rules().retention()),
                            program.rules());
            jobs.addLast(
                    new Job(
                            id,
                            arm.chain,
                            binding.target(),
                            arm.depth + 1,
                            at.subtract(direction.scale(.06)),
                            direction,
                            circuit));
            activeUnits += cost;
            chain.left--;
        } catch (IllegalArgumentException insufficient) {
            /* The paid capsule dissipates; never debit the player after impact. */
        }
        return true;
    }

    void tick(ServerLevel level, WorldSpells destination) {
        int ready = jobs.size(), work = 0, budget = ServerSettings.count("recursion.workPerTick");
        for (int i = 0; i < ready && work < budget; i++) {
            Job job = jobs.removeFirst();
            if (!level.hasChunkAt(net.minecraft.core.BlockPos.containing(job.position))) {
                jobs.addLast(job);
                continue;
            }
            work++;
            Chain chain = chains.get(job.chain);
            InvocationProgram program = programs.get(chain.program).program;
            for (var cast : job.circuit.step(chain.context, ShapeResources.rules()))
                SpellEffects.castOwned(
                        level,
                        chain.owner,
                        job.position,
                        job.direction,
                        job.chain,
                        cast,
                        program.definition(job.slot).rules(),
                        chain.strength,
                        new Cursor(job.chain, job.slot, job.depth),
                        destination);
            for (var refund : job.circuit.refunds())
                if (WorldManaRefunds.get(level)
                        .accept(
                                job.identity + "/" + job.slot + "/" + refund.sequence(),
                                refund.mana())) job.circuit.acknowledgeRefund(refund.sequence());
            boolean settled = job.circuit.refunds().isEmpty();
            if (job.circuit.done() && settled) {
                var unspent = job.circuit.unspentRefund();
                if (!unspent.empty())
                    settled =
                            WorldManaRefunds.get(level)
                                    .accept(job.identity + "/" + job.slot + "/unspent", unspent);
            }
            if (job.circuit.done() && settled) activeUnits -= units(program.definition(job.slot));
            else jobs.addLast(job);
        }
    }

    void prune() {
        Set<UUID> live = new HashSet<>();
        for (var a : arms.values()) live.add(a.chain);
        for (var j : jobs) live.add(j.chain);
        chains.keySet().retainAll(live);
        Set<UUID> used = new HashSet<>();
        for (var c : chains.values()) used.add(c.program);
        programs.keySet().retainAll(used);
    }

    void validateFlightReferences(List<UUID> references) {
        Set<UUID> ids = new HashSet<>();
        for (var id : references)
            if (!ids.add(id) || !arms.containsKey(id))
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.server.spell._recursive_spells.002"));
        if (!ids.equals(arms.keySet()))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.server.spell._recursive_spells.003"));
    }

    private static void vector(CompoundTag t, String key, Vec3 v) {
        t.putDouble(key + "X", v.x);
        t.putDouble(key + "Y", v.y);
        t.putDouble(key + "Z", v.z);
    }

    private static Vec3 vector(CompoundTag t, String key) {
        double[] a = new double[3];
        for (int i = 0; i < 3; i++) {
            String k = key + "XYZ".charAt(i);
            if (!t.contains(k, Tag.TAG_DOUBLE)
                    || !Double.isFinite(a[i] = t.getDouble(k))
                    || Math.abs(a[i]) > 30000100)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.server.spell._recursive_spells.004"));
        }
        return new Vec3(a[0], a[1], a[2]);
    }

    private static int integer(CompoundTag t, String key) {
        if (!t.contains(key, Tag.TAG_INT))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.server.spell._recursive_spells.005"));
        return t.getInt(key);
    }

    private static long number(CompoundTag t, String key) {
        if (!t.contains(key, Tag.TAG_LONG))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.server.spell._recursive_spells.006"));
        return t.getLong(key);
    }

    private static UUID uuid(CompoundTag t, String key) {
        if (!t.hasUUID(key))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.server.spell._recursive_spells.007"));
        return t.getUUID(key);
    }

    private static ListTag list(CompoundTag t, String key, int max) {
        if (!t.contains(key, Tag.TAG_LIST))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.server.spell._recursive_spells.008"));
        ListTag l = (ListTag) t.get(key);
        if (l.size() > max
                || l.getElementType() != Tag.TAG_END && l.getElementType() != Tag.TAG_COMPOUND)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.server.spell._recursive_spells.009"));
        return l;
    }

    CompoundTag save() {
        CompoundTag root = new CompoundTag();
        root.putInt("Version", 3);
        root.putLong("NextArm", nextArm);
        ListTag p = new ListTag(), c = new ListTag(), a = new ListTag(), j = new ListTag();
        for (var e : programs.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("Id", e.getKey());
            t.put("Data", JsonNbt.encode(e.getValue().encoded, 16777216));
            p.add(t);
        }
        for (var e : chains.entrySet()) {
            var v = e.getValue();
            CompoundTag t = new CompoundTag();
            t.putUUID("Id", e.getKey());
            t.putUUID("Program", v.program);
            if (v.owner != null) t.putUUID("Owner", v.owner);
            t.putLong("Strength", v.strength);
            t.putLong("Power", v.context.damageMultiplier());
            t.putLong("ManaMultiplier", v.context.manaMultiplier());
            t.putBoolean("Reactive", v.context.reactive());
            t.putInt("Left", v.left);
            c.add(t);
        }
        for (var e : arms.entrySet()) {
            var v = e.getValue();
            CompoundTag t = new CompoundTag();
            t.putUUID("Id", e.getKey());
            t.putUUID("Chain", v.chain);
            t.putInt("Slot", v.slot);
            t.putInt("Depth", v.depth);
            t.putInt("Delay", v.delay);
            t.put(
                    "Mana",
                    JsonNbt.compact(
                            JsonCodecs.packetText(new Packet(v.mana, SpellSpec.EMPTY)), 2097152));
            a.add(t);
        }
        for (var v : jobs) {
            CompoundTag t = new CompoundTag();
            t.putUUID("Identity", v.identity);
            t.putUUID("Chain", v.chain);
            t.putInt("Slot", v.slot);
            t.putInt("Depth", v.depth);
            vector(t, "P", v.position);
            vector(t, "D", v.direction);
            t.put("State", JsonNbt.encode(JsonCodecs.triggered(v.circuit), 16777216));
            j.add(t);
        }
        root.put("Programs", p);
        root.put("Chains", c);
        root.put("Arms", a);
        root.put("Jobs", j);
        return root;
    }

    static RecursiveSpells load(CompoundTag root) {
        int version = integer(root, "Version");
        if (version < 1 || version > 3)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.server.spell._recursive_spells.010"));
        var result = new RecursiveSpells();
        result.nextArm = number(root, "NextArm");
        if (result.nextArm < 0)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.server.spell._recursive_spells.011"));
        long bytes = 0;
        for (var e : list(root, "Programs", 1024)) {
            var t = (CompoundTag) e;
            UUID id = uuid(t, "Id");
            String text = JsonNbt.decode(t.get("Data"), 16777216);
            int size = text.getBytes(StandardCharsets.UTF_8).length;
            bytes += size;
            if (bytes > PROGRAM_BYTES
                    || result.programs.put(id, new Frozen(JsonCodecs.readProgram(text), text, size))
                            != null)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.server.spell._recursive_spells.012"));
        }
        for (var e : list(root, "Chains", 1024)) {
            var t = (CompoundTag) e;
            UUID id = uuid(t, "Id"), program = uuid(t, "Program");
            if (t.contains("Reactive")
                    && (!t.contains("Reactive", Tag.TAG_BYTE)
                            || (t.getByte("Reactive") != 0 && t.getByte("Reactive") != 1)))
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.server.spell._recursive_spells.013"));
            var frozen = result.programs.get(program);
            long strength = number(t, "Strength");
            int left = integer(t, "Left");
            if (frozen == null
                    || strength < Fixed.ONE / 4
                    || strength > 2 * Fixed.ONE
                    || left < 0
                    || left > frozen.program.rules().activations()
                    || t.contains("Owner") && !t.hasUUID("Owner")
                    || result.chains.put(
                                    id,
                                    new Chain(
                                            program,
                                            t.hasUUID("Owner") ? t.getUUID("Owner") : null,
                                            strength,
                                            left,
                                            version >= 3
                                                    ? new CastContext(
                                                            number(t, "Power"),
                                                            number(t, "ManaMultiplier"),
                                                            t.getBoolean("Reactive"))
                                                    : CastContext.NEUTRAL))
                            != null)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.server.spell._recursive_spells.014"));
        }
        for (var e : list(root, "Arms", 4096)) {
            var t = (CompoundTag) e;
            UUID id = uuid(t, "Id"), chain = uuid(t, "Chain");
            var c = result.chains.get(chain);
            if (c == null)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.server.spell._recursive_spells.015"));
            var program = result.programs.get(c.program).program;
            int slot = integer(t, "Slot"), depth = integer(t, "Depth"), delay = integer(t, "Delay");
            var b = program.binding(slot);
            if (!t.contains("Mana", Tag.TAG_STRING)
                    && (version < 2 || !t.contains("Mana", Tag.TAG_COMPOUND)))
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.server.spell._recursive_spells.001"));
            Packet packet =
                    version >= 2
                            ? JsonCodecs.readOrderedPacket(JsonNbt.decode(t.get("Mana"), 2097152))
                            : JsonCodecs.readPacket(t.getString("Mana"));
            if (id.getMostSignificantBits()
                            != (chain.getMostSignificantBits() ^ chain.getLeastSignificantBits())
                    || id.getLeastSignificantBits() < 0
                    || id.getLeastSignificantBits() >= result.nextArm
                    || b == null
                    || depth < 0
                    || depth >= program.rules().maxDepth()
                    || delay < 1
                    || delay > b.delay()
                    || !packet.spell().equals(SpellSpec.EMPTY)
                    || packet.mana().empty()
                    || result.arms.put(id, new Arm(chain, slot, depth, delay, packet.mana()))
                            != null)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.server.spell._recursive_spells.016"));
        }
        for (var e : list(root, "Jobs", 1024)) {
            var t = (CompoundTag) e;
            UUID chain = uuid(t, "Chain");
            var c = result.chains.get(chain);
            if (c == null)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.server.spell._recursive_spells.017"));
            var p = result.programs.get(c.program).program;
            int slot = integer(t, "Slot"), depth = integer(t, "Depth");
            Vec3 pos = vector(t, "P"), direction = vector(t, "D");
            if (depth < 1
                    || depth > p.rules().maxDepth()
                    || Math.abs(direction.length() - 1) > .0001)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.server.spell._recursive_spells.018"));
            var definition = p.definition(slot);
            var runtime =
                    JsonCodecs.readTriggered(
                            JsonNbt.decode(t.get("State"), 16777216), definition, p.rules());
            result.activeUnits += result.units(definition);
            if (result.activeUnits > 65536)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.server.spell._recursive_spells.019"));
            result.jobs.addLast(
                    new Job(
                            version >= 3 ? uuid(t, "Identity") : UUID.randomUUID(),
                            chain,
                            slot,
                            depth,
                            pos,
                            direction,
                            runtime));
        }
        return result;
    }
}
