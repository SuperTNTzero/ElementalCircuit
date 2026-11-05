package dev.elementalcircuits.server.spell;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.config.ServerSettings;
import dev.elementalcircuits.adapter.resources.ElementalCombatResources;
import dev.elementalcircuits.adapter.resources.RadianceResources;
import dev.elementalcircuits.adapter.resources.WorldInteractionResources;
import dev.elementalcircuits.application.observation.SpellVisual;
import dev.elementalcircuits.block.DimmedLightBlock;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.casting.EffectDistribution;
import dev.elementalcircuits.domain.casting.SecondaryBudget;
import dev.elementalcircuits.domain.casting.SpellPayloads;
import dev.elementalcircuits.domain.casting.WorldInteractionRules;
import dev.elementalcircuits.domain.combat.Element;
import dev.elementalcircuits.domain.combat.ElementReactionQueue;
import dev.elementalcircuits.domain.combat.ElementalCombat;
import dev.elementalcircuits.domain.mana.Mixture;

import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;

import java.util.*;

/**
 * World contacts, finite allocation, then authoritative responses. No inventory creation or client
 * writes.
 */
public final class ElementInteractions {
    private static final WorldInteractionRules RULES = WorldInteractionResources.rules();

    private record Contact(
            String key,
            Entity entity,
            BlockPos pos,
            BlockState before,
            Direction face,
            List<Long> minimums,
            boolean radiance) {}

    private record Change(
            BlockState after, long cost, boolean rod, boolean steam, int light, int material) {
        Change(BlockState after, long cost, boolean rod, boolean steam, int light) {
            this(after, cost, rod, steam, light, 0);
        }
    }

    private ElementInteractions() {}

    static void beam(
            ServerLevel level,
            UUID owner,
            Vec3 origin,
            SpellEffects.Trace trace,
            Mixture mana,
            long strength) {
        beam(level, owner, origin, trace, mana, strength, ContactMagic.NEUTRAL);
    }

    static void beam(
            ServerLevel level,
            UUID owner,
            Vec3 origin,
            SpellEffects.Trace trace,
            Mixture mana,
            long strength,
            ContactMagic magic) {
        mana = magic.effectScale().apply(mana);
        magic = magic.unscaled();
        mana = prepare(level, trace.position(), mana);
        if (mana.empty()) return;
        List<Contact> contacts = new ArrayList<>();
        if (trace.entity() != null) contacts.add(entity(trace.entity()));
        else if (trace.block() != null) {
            addBlock(
                    level,
                    owner,
                    trace.block().getBlockPos(),
                    trace.block().getDirection(),
                    mana,
                    strength,
                    contacts,
                    true,
                    magic);
            addBlock(
                    level,
                    owner,
                    trace.block().getBlockPos().relative(trace.block().getDirection()),
                    trace.block().getDirection(),
                    mana,
                    strength,
                    contacts,
                    true,
                    magic);
        } else if (mana.values()[Element.LIGHT.component(false)] > 0)
            addBlock(
                    level,
                    owner,
                    BlockPos.containing(trace.position()),
                    Direction.UP,
                    mana,
                    strength,
                    contacts,
                    true,
                    magic);
        resolve(level, owner, origin, mana, strength, contacts, magic);
    }

    static void area(
            ServerLevel level,
            UUID owner,
            Vec3 center,
            int radius,
            Mixture mana,
            long strength,
            List<LivingEntity> living,
            boolean radiance) {
        area(level, owner, center, radius, mana, strength, living, radiance, ContactMagic.NEUTRAL);
    }

    static void area(
            ServerLevel level,
            UUID owner,
            Vec3 center,
            int radius,
            Mixture mana,
            long strength,
            List<LivingEntity> living,
            boolean radiance,
            ContactMagic magic) {
        mana = magic.effectScale().apply(mana);
        magic = magic.unscaled();
        mana = prepare(level, center, mana);
        if (mana.empty()) return;
        List<Contact> contacts = new ArrayList<>();
        living.forEach(e -> contacts.add(entity(e)));
        if (hasWind(mana)) {
            List<ItemEntity> items =
                    level.getEntitiesOfClass(
                            ItemEntity.class,
                            new AABB(center, center).inflate(radius),
                            e ->
                                    e.isAlive()
                                            && e.position().distanceToSqr(center) <= radius * radius
                                            && visible(
                                                    level,
                                                    center,
                                                    e.getBoundingBox().getCenter(),
                                                    null));
            items.forEach(e -> contacts.add(entity(e)));
        }
        BlockPos base = BlockPos.containing(center);
        long[] components = mana.values();
        boolean blocks = false;
        for (Element element :
                new Element[] {Element.EARTH, Element.FIRE, Element.WATER, Element.LIGHTNING})
            blocks |=
                    components[element.component(false)] > 0
                            || components[element.component(true)] > 0;
        if (radiance)
            blocks |=
                    components[Element.LIGHT.component(false)] > 0
                            || components[Element.SHADOW.component(false)] > 0;
        int side = radius * 2 + 1, step = scanStride(side, side, side);
        if (blocks)
            for (int x = -radius; x <= radius; x += step)
                for (int y = -radius; y <= radius; y += step)
                    for (int z = -radius; z <= radius; z += step) {
                        BlockPos pos = base.offset(x, y, z);
                        if (Vec3.atCenterOf(pos).distanceToSqr(center) > radius * radius
                                || !level.hasChunkAt(pos)) continue;
                        BlockState state = level.getBlockState(pos);
                        // Area creation uses a surface, avoiding an entire floating volume of
                        // sources.
                        if (state.isAir()
                                && !(radiance && components[Element.LIGHT.component(false)] > 0)
                                && (!level.hasChunkAt(pos.below())
                                        || !level.getBlockState(pos.below())
                                                .isFaceSturdy(level, pos.below(), Direction.UP)))
                            continue;
                        int before = contacts.size();
                        addBlock(
                                level,
                                owner,
                                pos,
                                Direction.UP,
                                mana,
                                strength,
                                contacts,
                                radiance,
                                magic);
                        if (contacts.size() > before
                                && !visible(level, center, Vec3.atCenterOf(pos), pos))
                            contacts.remove(contacts.size() - 1);
                    }
        resolve(level, owner, center, mana, strength, contacts, magic);
    }

    /** Shared finite allocator; geometry only changes which contacts are eligible. */
    static void volume(
            ServerLevel level, UUID owner, ShapeFrame frame, Mixture mana, long strength) {
        volume(level, owner, frame, mana, strength, ContactMagic.NEUTRAL);
    }

    static void volume(
            ServerLevel level,
            UUID owner,
            ShapeFrame frame,
            Mixture mana,
            long strength,
            ContactMagic magic) {
        mana = magic.effectScale().apply(mana);
        magic = magic.unscaled();
        mana = prepare(level, frame.center(), mana);
        if (mana.empty()) return;
        List<Contact> contacts = new ArrayList<>();
        boolean phase = SpellPayloads.phased(mana);
        var living =
                level.getEntitiesOfClass(
                        LivingEntity.class,
                        frame.bounds(),
                        e ->
                                SpellEffects.eligible(level, owner, e)
                                        && frame.contains(e.getBoundingBox().getCenter())
                                        && (phase
                                                || visible(
                                                        level,
                                                        frame.center(),
                                                        e.getBoundingBox().getCenter(),
                                                        null)));
        living.forEach(e -> contacts.add(entity(e)));
        if (hasWind(mana))
            for (var item :
                    level.getEntitiesOfClass(
                            ItemEntity.class,
                            frame.bounds(),
                            e ->
                                    e.isAlive()
                                            && frame.contains(e.getBoundingBox().getCenter())
                                            && visible(
                                                    level,
                                                    frame.center(),
                                                    e.getBoundingBox().getCenter(),
                                                    null))) contacts.add(entity(item));
        long[] components = mana.values();
        boolean blocks = false;
        for (Element element :
                new Element[] {
                    Element.EARTH,
                    Element.FIRE,
                    Element.WATER,
                    Element.LIGHTNING,
                    Element.LIGHT,
                    Element.SHADOW
                })
            blocks |=
                    components[element.component(false)] > 0
                            || components[element.component(true)] > 0;
        if (!phase && blocks) {
            var b = frame.bounds();
            List<BlockPos> positions = new ArrayList<>();
            int minX = net.minecraft.util.Mth.floor(b.minX),
                    minY = net.minecraft.util.Mth.floor(b.minY),
                    minZ = net.minecraft.util.Mth.floor(b.minZ);
            int maxX = net.minecraft.util.Mth.floor(b.maxX),
                    maxY = net.minecraft.util.Mth.floor(b.maxY),
                    maxZ = net.minecraft.util.Mth.floor(b.maxZ);
            int stride = scanStride(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);
            for (int x = minX; x <= maxX; x += stride)
                for (int y = minY; y <= maxY; y += stride)
                    for (int z = minZ; z <= maxZ; z += stride) {
                        var p = new BlockPos(x, y, z);
                        if (level.hasChunkAt(p) && frame.contains(Vec3.atCenterOf(p)))
                            positions.add(p);
                    }

            positions.sort(
                    Comparator.comparingDouble(
                                    (BlockPos p) ->
                                            Vec3.atCenterOf(p).distanceToSqr(frame.center()))
                            .thenComparingInt(BlockPos::getX)
                            .thenComparingInt(BlockPos::getY)
                            .thenComparingInt(BlockPos::getZ));
            int count = 0;
            for (var p : positions) {
                if (count++ >= frame.volume().rules().blockProbes()) break;
                var state = level.getBlockState(p);
                if (state.isAir()
                        && components[Element.LIGHT.component(false)] == 0
                        && (!level.hasChunkAt(p.below())
                                || !level.getBlockState(p.below())
                                        .isFaceSturdy(level, p.below(), Direction.UP))) continue;
                int before = contacts.size();
                addBlock(level, owner, p, Direction.UP, mana, strength, contacts, true, magic);
                if (contacts.size() > before
                        && !visible(level, frame.center(), surfacePoint(frame.center(), p), p))
                    contacts.remove(contacts.size() - 1);
            }
        }
        resolve(level, owner, frame.center(), mana, strength, contacts, magic);
    }

    private static Mixture prepare(ServerLevel level, Vec3 at, Mixture mana) {
        var result =
                ElementReactionQueue.reduce(mana, ElementalCombatResources.rules().reactions());
        if (!result.collisions().isEmpty()) {
            var events = EnumSet.noneOf(ElementalCombat.Reaction.class);
            for (var c : result.collisions())
                events.add(
                        c.steam()
                                ? ElementalCombat.Reaction.STEAM
                                : ElementalCombat.Reaction.NEUTRALIZE);
            CombatReactionFeedback.observe(level, at, events);
        }
        return result.remaining();
    }

    private static int scanStride(int x, int y, int z) {
        int step = 1, budget = ServerSettings.count("spell.blockScanBudget");
        while ((long) ((x + step - 1) / step) * ((y + step - 1) / step) * ((z + step - 1) / step)
                > budget) step++;
        return step;
    }

    /** The nearest voxel surface keeps a thin wall's neighboring front faces visible. */
    private static Vec3 surfacePoint(Vec3 center, BlockPos p) {
        return new Vec3(
                Math.clamp(center.x, p.getX(), p.getX() + 1),
                Math.clamp(center.y, p.getY(), p.getY() + 1),
                Math.clamp(center.z, p.getZ(), p.getZ() + 1));
    }

    static boolean hasWind(Mixture mana) {
        long[] v = mana.values();
        return v[Element.WIND.component(false)] > 0 || v[Element.WIND.component(true)] > 0;
    }

    private static boolean visible(ServerLevel level, Vec3 from, Vec3 to, BlockPos target) {
        if (!SpellEffects.loadedBetween(level, from, to)) return false;
        BlockHitResult hit =
                level.clip(
                        new ClipContext(
                                from,
                                to,
                                ClipContext.Block.COLLIDER,
                                ClipContext.Fluid.NONE,
                                net.minecraft.world.phys.shapes.CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS
                || target != null && hit.getBlockPos().equals(target);
    }

    private static Contact entity(Entity entity) {
        List<Long> minimums = new ArrayList<>(Collections.nCopies(Mixture.COMPONENTS, 0L));
        for (Element element : Element.values())
            if (element != Element.PURE
                    && (entity instanceof LivingEntity || element == Element.WIND)) {
                minimums.set(element.component(false), 1L);
                minimums.set(element.component(true), 1L);
            }
        return new Contact("E:" + entity.getUUID(), entity, null, null, null, minimums, false);
    }

    static boolean permitted(ServerLevel level, UUID owner, BlockPos pos) {
        return CastPermissions.mayChangeBlock(level, owner, pos);
    }

    private static void addBlock(
            ServerLevel level,
            UUID owner,
            BlockPos pos,
            Direction face,
            Mixture mana,
            long strength,
            List<Contact> contacts,
            boolean radiance,
            ContactMagic magic) {
        if (!permitted(level, owner, pos)
                || !SpellEffects.loadedBetween(
                        level,
                        Vec3.atCenterOf(pos).add(-1, 0, -1),
                        Vec3.atCenterOf(pos).add(1, 0, 1))) return;
        BlockState before = level.getBlockState(pos);
        long[] amounts = mana.values();
        List<Long> minimums = new ArrayList<>(Collections.nCopies(Mixture.COMPONENTS, 0L));
        boolean responds = false;
        for (Element element : Element.values())
            if (element != Element.PURE)
                for (int phase = 0; phase < 2; phase++) {
                    int c = element.component(phase == 1);
                    if (amounts[c] == 0) continue;
                    Change change =
                            change(
                                    level,
                                    pos,
                                    before,
                                    element,
                                    phase == 1,
                                    radiance,
                                    owner,
                                    face,
                                    magic);
                    if (change != null) {
                        long required = RULES.required(change.cost, strength);
                        if (required > Fixed.MAX) continue;
                        minimums.set(c, required);
                        responds = true;
                    }
                }
        if (responds)
            contacts.add(
                    new Contact(
                            "B:" + pos.getX() + ":" + pos.getY() + ":" + pos.getZ(),
                            null,
                            pos.immutable(),
                            before,
                            face,
                            minimums,
                            radiance));
    }

    private static boolean lampFire(BlockState s) {
        return s.is(BlockTags.CAMPFIRES)
                || s.getBlock() instanceof CandleBlock
                || s.getBlock() instanceof CandleCakeBlock;
    }

    private static Change change(
            ServerLevel level,
            BlockPos pos,
            BlockState s,
            Element element,
            boolean inverse,
            boolean radiance,
            UUID owner,
            Direction face,
            ContactMagic magic) {
        BlockState next = null;
        boolean rod = false, steam = false;
        long cost = RULES.blockCost();
        if (element == Element.EARTH
                && ServerSettings.count("automation.enabled") != 0
                && owner != null) {
            if (inverse) {
                long breaking = MaterialOperations.breakCost(level, pos, s);
                if (breaking > 0)
                    return new Change(Blocks.AIR.defaultBlockState(), breaking, false, false, 0, 1);
            } else {
                BlockState placed =
                        MaterialOperations.placement(level, owner, pos, face, magic.materials());
                if (placed != null)
                    return new Change(
                            placed,
                            ServerSettings.mana("automation.placeCost"),
                            false,
                            false,
                            0,
                            2);
            }
            return null;
        }
        if (radiance
                && !inverse
                && element == Element.LIGHT
                && (s.isAir() || s.is(ElementalCircuits.SPELL_LIGHT.get()))
                && WorldIllumination.get(level).canTrack(pos))
            return new Change(
                    ElementalCircuits.SPELL_LIGHT.get().defaultBlockState(),
                    RULES.sourceCost(),
                    false,
                    false,
                    1);
        if (radiance && !inverse && element == Element.SHADOW) {
            if (s.is(ElementalCircuits.SPELL_LIGHT.get()))
                return new Change(Blocks.AIR.defaultBlockState(), cost, false, false, 3);
            if (s.getBlock() instanceof BaseFireBlock)
                return new Change(Blocks.AIR.defaultBlockState(), cost, false, false, 0);
            if (lampFire(s) && s.getValue(BlockStateProperties.LIT))
                return new Change(
                        s.setValue(BlockStateProperties.LIT, false), cost, false, false, 0);
            BlockState dimmed =
                    s.is(ElementalCircuits.DIMMED_LIGHT.get()) ? s : DimmedLightBlock.mask(s);
            if (dimmed != null && WorldIllumination.get(level).canTrack(pos))
                return new Change(dimmed, cost, false, false, 2);
        }
        if (element == Element.FIRE) {
            if (!inverse) {
                if (lampFire(s)
                        && !s.getValue(BlockStateProperties.LIT)
                        && (!s.hasProperty(BlockStateProperties.WATERLOGGED)
                                || !s.getValue(BlockStateProperties.WATERLOGGED)))
                    next = s.setValue(BlockStateProperties.LIT, true);
                else if (s.is(Blocks.ICE) || s.is(Blocks.FROSTED_ICE))
                    next =
                            level.dimensionType().ultraWarm()
                                    ? Blocks.AIR.defaultBlockState()
                                    : Blocks.WATER.defaultBlockState();
                else if (s.isAir()) {
                    BlockState fire = BaseFireBlock.getState(level, pos);
                    if (fire.canSurvive(level, pos)) {
                        next = fire;
                        cost = RULES.sourceCost();
                    }
                }
            } else {
                if (s.getBlock() instanceof BaseFireBlock) next = Blocks.AIR.defaultBlockState();
                else if (lampFire(s) && s.getValue(BlockStateProperties.LIT))
                    next = s.setValue(BlockStateProperties.LIT, false);
                else if (s.is(Blocks.WATER) && s.getFluidState().isSource())
                    next = Blocks.FROSTED_ICE.defaultBlockState();
            }
        } else if (element == Element.WATER) {
            if (!inverse) {
                if (s.getBlock() instanceof BaseFireBlock) next = Blocks.AIR.defaultBlockState();
                else if (lampFire(s) && s.getValue(BlockStateProperties.LIT))
                    next = s.setValue(BlockStateProperties.LIT, false);
                else if (s.is(Blocks.FARMLAND) && s.getValue(FarmBlock.MOISTURE) < 7)
                    next = s.setValue(FarmBlock.MOISTURE, 7);
                else if (s.isAir()) {
                    cost = RULES.sourceCost();
                    steam = level.dimensionType().ultraWarm();
                    next =
                            steam
                                    ? Blocks.AIR.defaultBlockState()
                                    : Blocks.WATER.defaultBlockState();
                }
            } else if (s.is(Blocks.FARMLAND) && s.getValue(FarmBlock.MOISTURE) > 0)
                next = s.setValue(FarmBlock.MOISTURE, 0);
        } else if (element == Element.LIGHTNING) {
            if (s.is(Blocks.LIGHTNING_ROD)) {
                if (s.getValue(LightningRodBlock.POWERED) == inverse) {
                    next = s.setValue(LightningRodBlock.POWERED, !inverse);
                    rod = !inverse;
                }
            } else if (!inverse) next = WeatheringCopper.getPrevious(s).orElse(null);
        }
        return next == null ? null : new Change(next, cost, rod, steam, 0);
    }

    private static void resolve(
            ServerLevel level,
            UUID owner,
            Vec3 origin,
            Mixture mana,
            long strength,
            List<Contact> candidates,
            ContactMagic magic) {
        // Duplicate physical contacts (e.g. impact and adjacent cell) remain one recipient.
        Map<String, Contact> unique = new TreeMap<>();
        for (Contact c : candidates) unique.putIfAbsent(c.key, c);
        List<Contact> contacts =
                unique.values().stream()
                        .sorted(
                                Comparator.comparingDouble(
                                                (Contact c) ->
                                                        c.entity != null
                                                                ? c.entity
                                                                        .position()
                                                                        .distanceToSqr(origin)
                                                                : Vec3.atCenterOf(c.pos)
                                                                        .distanceToSqr(origin))
                                        .thenComparing(Contact::key))
                        .limit(ServerSettings.count("spell.maxTargets"))
                        .toList();
        Map<String, Contact> byKey = new HashMap<>();
        List<EffectDistribution.Target> targets = new ArrayList<>();
        for (Contact c : contacts) {
            byKey.put(c.key, c);
            targets.add(new EffectDistribution.Target(c.key, c.minimums));
        }
        EffectDistribution.Plan plan = EffectDistribution.allocate(mana, targets);
        for (var share : plan.shares()) {
            Contact c = byKey.get(share.key());
            if (c.entity instanceof LivingEntity living) {
                if (living.isAlive())
                    ElementalCombatAdapter.apply(
                            level,
                            owner,
                            living,
                            share.mana(),
                            living.position().subtract(origin).normalize(),
                            strength,
                            magic);
            } else if (c.entity instanceof ItemEntity item) {
                long[] v = share.mana().values();
                double net =
                        (v[Element.WIND.component(false)] - v[Element.WIND.component(true)])
                                / (double) Fixed.ONE
                                * strength
                                / Fixed.ONE;
                double impulse =
                        Math.max(
                                -RULES.maxImpulse() / (double) Fixed.ONE,
                                Math.min(
                                        RULES.maxImpulse() / (double) Fixed.ONE,
                                        net * RULES.impulsePerUnit() / Fixed.ONE));
                Vec3 direction = item.position().subtract(origin).normalize();
                item.push(direction.x * impulse, direction.y * impulse, direction.z * impulse);
                item.hurtMarked = true;
            } else if (c.pos != null)
                applyBlock(
                        level,
                        owner,
                        c,
                        SecondaryBudget.hit(share.mana(), magic.effects()).elemental(),
                        strength,
                        magic);
        }
        // Unresponsive, unaffordable or invalidated shares dissipate; never become mana refunds.
    }

    private static void applyBlock(
            ServerLevel level,
            UUID owner,
            Contact c,
            Mixture mana,
            long strength,
            ContactMagic magic) {
        if (!permitted(level, owner, c.pos) || !level.getBlockState(c.pos).equals(c.before)) return;
        long[] values = mana.values();
        for (var portion : mana.order()) {
            if (portion.component() == 0) continue;
            Element element = Element.values()[(portion.component() + 1) / 2];
            boolean inverse = portion.component() % 2 == 0;
            Change change =
                    change(
                            level,
                            c.pos,
                            c.before,
                            element,
                            inverse,
                            c.radiance,
                            owner,
                            c.face,
                            magic);
            if (change == null
                    || values[portion.component()] < RULES.required(change.cost, strength))
                continue;
            if (change.material != 0) {
                if (change.material == 1)
                    MaterialOperations.dismantle(level, owner, c.pos, c.before, magic.materials());
                else
                    MaterialOperations.place(
                            level, owner, c.pos, c.before, change.after, c.face, magic.materials());
                return;
            }
            Entity caster = owner == null ? null : level.getEntity(owner);
            BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, c.pos);
            // Check cancellation before placement: native water/lava reactions can happen inside
            // setBlock.
            if (NeoForge.EVENT_BUS
                    .post(
                            new ElementBlockChangeEvent(
                                    snapshot,
                                    level.getBlockState(c.pos.relative(c.face.getOpposite())),
                                    caster,
                                    change.after))
                    .isCanceled()) return;
            if (!permitted(level, owner, c.pos) || !level.getBlockState(c.pos).equals(c.before))
                return;
            if (!change.steam
                    && !change.after.equals(c.before)
                    && !level.setBlock(c.pos, change.after, 3)) return;
            if (change.light == 1)
                WorldIllumination.get(level)
                        .adopt(c.pos, change.after, RadianceResources.rules().lightLife());
            else if (change.light == 2)
                WorldIllumination.get(level)
                        .adopt(c.pos, change.after, RadianceResources.rules().shadowLife());
            else if (change.light == 3) WorldIllumination.get(level).forget(c.pos);
            if (change.steam) {
                Vec3 at = Vec3.atCenterOf(c.pos);
                SpellVisuals.observe(level, SpellVisual.Kind.STEAM, Element.WATER, at, at, 1, 8);
            } else {
                level.updateNeighborsAt(c.pos, change.after.getBlock());
                if (change.rod)
                    ((LightningRodBlock) change.after.getBlock())
                            .onLightningStrike(change.after, level, c.pos);
                if (change.after.is(Blocks.LIGHTNING_ROD))
                    level.updateNeighborsAt(
                            c.pos.relative(
                                    change.after.getValue(LightningRodBlock.FACING).getOpposite()),
                            change.after.getBlock());
                if (change.after.is(Blocks.FROSTED_ICE))
                    level.scheduleTick(c.pos, Blocks.FROSTED_ICE, RULES.frostedIceDelay());
            }
            return; // A block receives at most one state transition from this contact snapshot.
        }
    }
}
