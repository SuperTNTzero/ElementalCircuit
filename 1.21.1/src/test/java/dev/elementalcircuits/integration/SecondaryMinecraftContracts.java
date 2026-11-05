package dev.elementalcircuits.integration;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.codec.JsonCodecs;
import dev.elementalcircuits.adapter.config.ServerSettings;
import dev.elementalcircuits.adapter.resources.RuleResources;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.casting.ProjectileEffects;
import dev.elementalcircuits.domain.casting.SpellSpec;
import dev.elementalcircuits.domain.circuit.MagicCircuit;
import dev.elementalcircuits.domain.combat.Element;
import dev.elementalcircuits.domain.mana.Mixture;
import dev.elementalcircuits.domain.mana.Packet;
import dev.elementalcircuits.server.*;
import dev.elementalcircuits.server.spell.SpellEffects;
import dev.elementalcircuits.server.spell.WorldSpells;

import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;

import java.util.*;

@GameTestHolder(ElementalCircuits.ID)
@PrefixGameTestTemplate(false)
public final class SecondaryMinecraftContracts {
    private static void prepare(GameTestHelper h) {
        for (int x = -16; x <= 16; x += 16)
            for (int z = -16; z <= 16; z += 16)
                h.getLevel().getChunkAt(h.absolutePos(new BlockPos(4, 2, 4)).offset(x, 0, z));
        for (int x = 0; x <= 8; x++)
            for (int z = 0; z <= 8; z++)
                for (int y = 1; y <= 6; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
    }

    private static Mixture mana(long n) {
        long[] a = new long[17];
        a[Element.LIGHTNING.component(false)] = n;
        return new Mixture(a);
    }

    private static SpellSpec sphere(ProjectileEffects e) {
        return new SpellSpec(SpellSpec.Method.DIRECTED, SpellSpec.Shape.SPHERE, 0, 0, 0, 0, e);
    }

    private static Cow cow(GameTestHelper h, BlockPos pos) {
        var c = h.spawn(EntityType.COW, pos);
        c.setNoAi(true);
        c.setNoGravity(true);
        return c;
    }

    private static ListTag flights(WorldSpells state) {
        return state.save(new CompoundTag(), null).getList("Flights", 10);
    }

    private static void launch(
            GameTestHelper h, WorldSpells state, ProjectileEffects effects, long amount) {
        state.launch(
                null,
                h.absoluteVec(new Vec3(2.5, 2.8, 1.5)),
                new Vec3(0, 0, 1),
                12,
                1,
                new Packet(mana(amount), sphere(effects)));
    }

    private static void hit(GameTestHelper h, WorldSpells state) {
        for (int i = 0;
                i < 8
                        && flights(state).size() == 1
                        && flights(state).getCompound(0).getList("Visited", 11).isEmpty();
                i++) state.tick(h.getLevel());
    }

    @GameTest(template = "empty")
    public static void fragmentsAreDelayedFiniteAndConservative(GameTestHelper h) {
        prepare(h);
        var target = cow(h, new BlockPos(2, 2, 4));
        var state = new WorldSpells();
        launch(h, state, new ProjectileEffects(0, 0, 0, 3, 0, 0, 0, 0), 4 * Fixed.ONE);
        hit(h, state);
        var children = flights(state);
        h.assertTrue(children.size() == 3, "一次真实命中产生三个子投射物");
        long held = 0;
        for (int i = 0; i < children.size(); i++) {
            var f = children.getCompound(i);
            held += JsonCodecs.readPacket(f.getString("Packet")).mana().total();
            h.assertTrue(
                    !f.getBoolean("SplitReady")
                            && f.getInt("ChainLeft") == 0
                            && f.getList("Visited", 11).size() == 1
                            && f.getDouble("Remaining") > 5.9,
                    "子代从命中处排队，未在同一步飞行，不能再次分裂");
        }
        h.assertTrue(
                held == 3 * Fixed.ONE / 2 && Math.abs(target.getHealth() - 6) < .001,
                "直接命中支付2点，分裂预留2点扣损后仅剩1.5点");
        var copy = WorldSpells.load(state.save(new CompoundTag(), h.getLevel().registryAccess()));
        for (int i = 0; i < 3; i++) {
            state.tick(h.getLevel());
            copy.tick(h.getLevel());
            h.assertTrue(
                    state.save(new CompoundTag(), h.getLevel().registryAccess())
                            .equals(copy.save(new CompoundTag(), h.getLevel().registryAccess())),
                    "子代保存后方向、名单与进度同轨迹");
        }
        for (int i = 0; i < 15; i++) state.tick(h.getLevel());
        h.assertTrue(
                flights(state).isEmpty() && Math.abs(target.getHealth() - 6) < .001,
                "一代子弹最终耗尽，不重新命中原目标或无限繁殖");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void chainFindsNewTargetsAndPersistsProgress(GameTestHelper h) {
        prepare(h);
        var first = cow(h, new BlockPos(2, 2, 3));
        var second = cow(h, new BlockPos(5, 2, 3));
        var third = cow(h, new BlockPos(5, 2, 6));
        var state = new WorldSpells();
        launch(h, state, new ProjectileEffects(0, 0, 0, 0, 3, 0, 0, 0), 8 * Fixed.ONE);
        hit(h, state);
        h.assertTrue(
                flights(state).size() == 1
                        && flights(state).getCompound(0).getInt("ChainLeft") == 2
                        && second.getHealth() == 10,
                "首跳完成后剩余跳数递减，下一目标尚未同一步受伤");
        float initial = first.getHealth();
        var restored =
                WorldSpells.load(state.save(new CompoundTag(), h.getLevel().registryAccess()));
        int maxVisited = 0;
        for (int i = 0; i < 24; i++) {
            restored.tick(h.getLevel());
            for (int j = 0; j < flights(restored).size(); j++)
                maxVisited =
                        Math.max(
                                maxVisited,
                                flights(restored).getCompound(j).getList("Visited", 11).size());
        }
        h.assertTrue(
                second.getHealth() < 10
                        && third.getHealth() < 10
                        && first.getHealth() == initial
                        && maxVisited >= 2,
                "恢复后连锁命中新目标，并跳过已命中对象");
        h.assertTrue(
                (10 - first.getHealth()) + (10 - second.getHealth()) + (10 - third.getHealth())
                                <= 16.001
                        && flights(restored).isEmpty(),
                "总元素伤害不超过原预算，连锁最终结束");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void chainObeysOcclusionAndBudget(GameTestHelper h) {
        prepare(h);
        var first = cow(h, new BlockPos(2, 2, 3));
        var hidden = cow(h, new BlockPos(5, 2, 3));
        for (int z = 0; z <= 8; z++)
            for (int y = 1; y <= 6; y++) h.setBlock(new BlockPos(4, y, z), Blocks.STONE);
        var state = new WorldSpells();
        launch(h, state, new ProjectileEffects(0, 0, 0, 0, 4, 0, 0, 0), 4 * Fixed.ONE);
        hit(h, state);
        h.assertTrue(
                hidden.getHealth() == 10
                        && flights(state).isEmpty()
                        && Math.abs(first.getHealth() - 6) < .001,
                "墙后不能连锁，无目标的预留份额耗散而非返还成直接伤害");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void independentHitModifiersSharePaidBudget(GameTestHelper h) {
        prepare(h);
        var target = cow(h, new BlockPos(2, 2, 4));
        var spec =
                new SpellSpec(
                        SpellSpec.Method.DIRECTED,
                        SpellSpec.Shape.BEAM,
                        0,
                        0,
                        0,
                        0,
                        new ProjectileEffects(0, 0, 0, 0, 0, 5, 5, 4));
        SpellEffects.cast(
                h.getLevel(),
                null,
                h.absoluteVec(new Vec3(2.5, 2.8, 1.5)),
                new Vec3(0, 0, 1),
                new UUID(29, 901),
                new MagicCircuit.Cast(0, mana(3 * Fixed.ONE / 2), spec),
                RuleResources.defaults());
        h.assertTrue(Math.abs(target.getHealth() - 8.5) < .001, "非火元素的附加效果预留一半，不再获得完整基础伤害");
        h.assertTrue(
                target.getRemainingFireTicks() == 20
                        && target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)
                        && target.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getDuration() == 20,
                "灼烧和迟缓由各自0.25点预算得到1秒，而非免费获得5秒");
        h.assertTrue(
                target.getDeltaMovement().z > 0 && target.getDeltaMovement().length() < .3,
                "击退也使用独立0.25点份额，有界且沿命中方向");
        var nbt = target.saveWithoutId(new CompoundTag());
        h.assertTrue(
                nbt.getShort("Fire") == 20 && !nbt.getList("active_effects", 10).isEmpty(),
                "状态交由原版实体NBT持续保存");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void beamCreatesSecondaryFlightsAtContact(GameTestHelper h) {
        prepare(h);
        var first = cow(h, new BlockPos(2, 2, 3));
        var second = cow(h, new BlockPos(5, 2, 3));
        var world = WorldSpells.get(h.getLevel());
        var before = flights(world).size();
        var effects = new ProjectileEffects(0, 0, 0, 2, 2, 0, 0, 0);
        var spec =
                new SpellSpec(SpellSpec.Method.DIRECTED, SpellSpec.Shape.BEAM, 0, 0, 0, 0, effects);
        SpellEffects.cast(
                h.getLevel(),
                null,
                h.absoluteVec(new Vec3(2.5, 2.8, 1.5)),
                new Vec3(0, 0, 1),
                new UUID(29, 902),
                new MagicCircuit.Cast(0, mana(3 * Fixed.ONE), spec),
                RuleResources.defaults());
        h.assertTrue(
                flights(world).size() == before + 3
                        && first.getHealth() < 10
                        && second.getHealth() == 10,
                "光束主命中与两枚分裂/一枚连锁弹分开，二次弹不会同一调用瞬时命中");
        var saved = world.save(new CompoundTag(), h.getLevel().registryAccess());
        var copy = WorldSpells.load(saved);
        h.assertTrue(
                copy.save(new CompoundTag(), h.getLevel().registryAccess()).equals(saved),
                "光束子代排队边界也保存恢复");
        var pending = new java.util.HashMap<Integer, Vec3>();
        for (int i = 0; i < saved.getList("Flights", 10).size(); i++) {
            var f = saved.getList("Flights", 10).getCompound(i);
            if (f.hasUUID("Owner") && f.getUUID("Owner").equals(new UUID(29, 902))) {
                h.assertTrue(f.getBoolean("Queued"), "光束子代先入队");
                pending.put(i, new Vec3(f.getDouble("PX"), f.getDouble("PY"), f.getDouble("PZ")));
            }
        }
        copy.tick(h.getLevel());
        var promoted = flights(copy);
        for (var entry : pending.entrySet()) {
            var f = promoted.getCompound(entry.getKey());
            h.assertTrue(
                    !f.getBoolean("Queued")
                            && new Vec3(f.getDouble("PX"), f.getDouble("PY"), f.getDouble("PZ"))
                                    .equals(entry.getValue()),
                    "该世界边界只发布子代，不推进其飞行");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void childCapacityAndShieldCannotDuplicate(GameTestHelper h) {
        prepare(h);
        var target = cow(h, new BlockPos(2, 2, 4));
        var state = new WorldSpells();
        launch(h, state, new ProjectileEffects(0, 0, 0, 4, 4, 0, 0, 0), 4 * Fixed.ONE);
        for (int i = 0; i < ServerSettings.count("spell.maxProjectiles") - 1; i++)
            state.launch(
                    null,
                    new Vec3(2100000, 100, 2100000),
                    new Vec3(0, 0, 1),
                    8,
                    1,
                    new Packet(mana(Fixed.ONE), SpellSpec.EMPTY));
        for (int i = 0; i < 6; i++) state.tick(h.getLevel());
        h.assertTrue(
                flights(state).size() <= ServerSettings.count("spell.maxProjectiles")
                        && target.getHealth() < 10,
                "世界容量满时丢弃超额已分配子代，不超上限或复制预算");
        var shielded = new WorldSpells();
        launch(h, shielded, new ProjectileEffects(0, 0, 0, 4, 4, 2, 2, 1), 4 * Fixed.ONE);
        shielded.shield(
                new UUID(29, 903),
                h.absoluteVec(new Vec3(2.5, 2.8, 3)),
                new Vec3(0, 0, -1),
                2,
                4 * Fixed.ONE);
        float health = target.getHealth();
        for (int i = 0; i < 6; i++) shielded.tick(h.getLevel());
        h.assertTrue(
                flights(shielded).isEmpty() && target.getHealth() == health,
                "盾牌完整吸收不会再产生分裂、连锁和附加效果");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void expiresWithoutContactAndLegacyV5(GameTestHelper h) {
        prepare(h);
        var state = new WorldSpells();
        state.launch(
                null,
                h.absoluteVec(new Vec3(2.5, 4.5, 1.5)),
                new Vec3(0, 0, 1),
                1,
                1,
                new Packet(mana(Fixed.ONE), sphere(new ProjectileEffects(0, 0, 0, 4, 4, 0, 0, 0))));
        state.tick(h.getLevel());
        state.tick(h.getLevel());
        h.assertTrue(flights(state).isEmpty(), "仅射程结束而无接触时不触发命中二次派生");
        var legacy = new WorldSpells();
        legacy.launch(
                null,
                h.absoluteVec(new Vec3(2.5, 4.5, 1.5)),
                new Vec3(0, 0, 1),
                8,
                1,
                new Packet(mana(Fixed.ONE), sphere(new ProjectileEffects(0, 2, 0))));
        var saved = legacy.save(new CompoundTag(), h.getLevel().registryAccess());
        saved.putInt("Version", 5);
        var old = saved.getList("Flights", 10).getCompound(0);
        old.putInt("Effects", 4);
        for (var k : List.of("SplitReady", "ChainLeft", "Visited", "SecondaryRules")) old.remove(k);
        var packet =
                com.google.gson.JsonParser.parseString(old.getString("Packet")).getAsJsonObject();
        var e = packet.getAsJsonObject("spell").getAsJsonObject("effects");
        for (var k : List.of("fragments", "chains", "ignite", "slow", "knockback")) e.remove(k);
        old.putString("Packet", packet.toString());
        var restored = WorldSpells.load(saved);
        var f = flights(restored).getCompound(0);
        h.assertTrue(
                f.getInt("Effects") == 4
                        && !f.getBoolean("SplitReady")
                        && f.getInt("ChainLeft") == 0
                        && f.getInt("Bounces") == 2
                        && JsonCodecs.readPacket(f.getString("Packet")).mana().total() == Fixed.ONE,
                "历史v5明确无新派生，不重扣已有弹跳/追踪预算");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void unloadedSecondaryStateFreezes(GameTestHelper h) {
        var state = new WorldSpells();
        state.launch(
                null,
                new Vec3(2100000, 100, 2100000),
                new Vec3(0, 0, 1),
                8,
                1,
                new Packet(mana(Fixed.ONE), sphere(new ProjectileEffects(0, 0, 0, 4, 4, 5, 5, 4))));
        var before = state.save(new CompoundTag(), h.getLevel().registryAccess());
        state.tick(h.getLevel());
        var copy = WorldSpells.load(before);
        h.assertTrue(
                state.save(new CompoundTag(), h.getLevel().registryAccess()).equals(before)
                        && copy.save(new CompoundTag(), h.getLevel().registryAccess())
                                .equals(before)
                        && !h.getLevel().hasChunkAt(new BlockPos(2100000, 100, 2100000)),
                "卸载二次派生冻结完整名单、次数、规则与资源，不强加载");
        h.succeed();
    }
}
