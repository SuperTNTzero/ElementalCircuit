package dev.elementalcircuits.integration;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.resources.RuleResources;
import dev.elementalcircuits.adapter.resources.ShapeResources;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.casting.ShapeVolume;
import dev.elementalcircuits.domain.casting.SpellSpec;
import dev.elementalcircuits.domain.circuit.MagicCircuit;
import dev.elementalcircuits.domain.combat.Element;
import dev.elementalcircuits.domain.mana.Mixture;
import dev.elementalcircuits.domain.mana.Packet;
import dev.elementalcircuits.server.*;
import dev.elementalcircuits.server.spell.ShapeFrame;
import dev.elementalcircuits.server.spell.SpellEffects;
import dev.elementalcircuits.server.spell.WorldSpells;

import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;

import java.util.*;

@GameTestHolder(ElementalCircuits.ID)
@PrefixGameTestTemplate(false)
public final class ShapeWorldContracts {
    private static Mixture mana(Element e, long n) {
        long[] values = new long[17];
        values[e.component(false)] = n;
        return new Mixture(values);
    }

    private static void prepare(GameTestHelper h) {
        var p = h.absolutePos(new BlockPos(4, 3, 4));
        for (int x = -16; x <= 16; x += 16)
            for (int z = -16; z <= 16; z += 16) h.getLevel().getChunkAt(p.offset(x, 0, z));
        for (int x = 1; x <= 10; x++)
            for (int z = 1; z <= 10; z++)
                for (int y = 1; y <= 8; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
    }

    private static Cow cow(GameTestHelper h, Vec3 center) {
        var c = h.spawn(EntityType.COW, BlockPos.containing(center));
        var p = h.absoluteVec(center);
        c.setPos(p.x, p.y - c.getBbHeight() / 2, p.z);
        c.setNoAi(true);
        c.setNoGravity(true);
        return c;
    }

    private static void cast(
            GameTestHelper h,
            SpellSpec.Shape shape,
            SpellSpec.Method method,
            Vec3 origin,
            Element e,
            long n) {
        var spec = new SpellSpec(method, shape, 0, shape == SpellSpec.Shape.FAN ? 0 : 8, 2, 0);
        SpellEffects.cast(
                h.getLevel(),
                null,
                h.absoluteVec(origin),
                new Vec3(0, 0, 1),
                new UUID(900, shape.ordinal()),
                new MagicCircuit.Cast(0, mana(e, n), spec),
                RuleResources.defaults());
    }

    private static void membership(
            GameTestHelper h, SpellSpec.Shape shape, Vec3 inside, Vec3 outside) {
        prepare(h);
        Vec3 center = new Vec3(4.5, 4.5, 4.5);
        var a = cow(h, center.add(inside));
        var b = cow(h, center.add(outside));
        var all = new ArrayList<Cow>();
        all.add(a);
        all.add(b);
        var method =
                shape == SpellSpec.Shape.FAN || shape == SpellSpec.Shape.WALL
                        ? SpellSpec.Method.DIRECTED
                        : SpellSpec.Method.AREA;
        Vec3 origin = center;
        if (shape == SpellSpec.Shape.WALL) {
            all.add(cow(h, center));
            origin = center.add(0, 0, -3);
        } else if (shape == SpellSpec.Shape.RING
                || shape == SpellSpec.Shape.CROSS
                || shape == SpellSpec.Shape.DOME)
            origin = center.add(0, ShapeResources.rules().groundOffset() / (double) Fixed.ONE, 0);
        cast(h, shape, method, origin, Element.ARCANE, Fixed.ONE);
        h.assertTrue(
                a.getHealth() < a.getMaxHealth() && b.getHealth() == b.getMaxHealth(),
                "实际命中必须符合形状内外：" + shape + "/" + a.getHealth() + "/" + b.getHealth());
        double damage = all.stream().mapToDouble(c -> c.getMaxHealth() - c.getHealth()).sum();
        h.assertTrue(damage > 0 && damage <= 2.00001, "多个接触共享一份魔力，不复制伤害：" + damage);
        all.forEach(Entity::discard);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void fanRegion(GameTestHelper h) {
        membership(h, SpellSpec.Shape.FAN, new Vec3(.5, 0, 2), new Vec3(0, 0, -1));
    }

    @GameTest(template = "empty")
    public static void wallRegion(GameTestHelper h) {
        membership(h, SpellSpec.Shape.WALL, new Vec3(1.6, 1.6, 0), new Vec3(0, 0, 1.3));
    }

    @GameTest(template = "empty")
    public static void ringLeavesCenterEmpty(GameTestHelper h) {
        membership(h, SpellSpec.Shape.RING, new Vec3(1.6, 0, 0), Vec3.ZERO);
    }

    @GameTest(template = "empty")
    public static void columnRegion(GameTestHelper h) {
        membership(h, SpellSpec.Shape.COLUMN, new Vec3(.7, 1.6, 0), new Vec3(1.4, 0, 0));
    }

    @GameTest(template = "empty")
    public static void crossLeavesCornersEmpty(GameTestHelper h) {
        membership(h, SpellSpec.Shape.CROSS, new Vec3(1.6, 0, 0), new Vec3(1, 0, 1));
    }

    @GameTest(template = "empty")
    public static void domeLeavesBottomEmpty(GameTestHelper h) {
        membership(h, SpellSpec.Shape.DOME, new Vec3(0, 1.7, 0), new Vec3(0, -1.7, 0));
    }

    @GameTest(template = "empty")
    public static void shapedWaterIsFiniteAndActual(GameTestHelper h) {
        prepare(h);
        for (int x = 1; x <= 9; x++)
            for (int z = 1; z <= 9; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        Vec3 origin = new Vec3(4.5, 2.5, 2.5);
        cast(
                h,
                SpellSpec.Shape.FAN,
                SpellSpec.Method.DIRECTED,
                origin,
                Element.WATER,
                2 * Fixed.ONE);
        var frame =
                ShapeFrame.of(
                        h.absoluteVec(origin),
                        new Vec3(0, 0, 1),
                        new ShapeVolume(SpellSpec.Shape.FAN, 2, ShapeResources.rules()));
        int sources = 0;
        for (int x = 1; x <= 9; x++)
            for (int z = 1; z <= 9; z++) {
                var p = new BlockPos(x, 2, z);
                if (h.getBlockState(p).is(Blocks.WATER)) {
                    sources++;
                    h.assertTrue(frame.contains(Vec3.atCenterOf(h.absolutePos(p))), "水源只能出现在扇面内");
                }
            }
        h.assertTrue(sources > 0 && sources <= 2, "两点魔力最多生成两个真实水源：" + sources);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void wallTouchesAimedSurface(GameTestHelper h) {
        prepare(h);
        h.setBlock(new BlockPos(4, 3, 6), Blocks.ICE);
        h.setBlock(new BlockPos(5, 3, 6), Blocks.ICE);
        cast(
                h,
                SpellSpec.Shape.WALL,
                SpellSpec.Method.DIRECTED,
                new Vec3(4.5, 3.5, 2.5),
                Element.FIRE,
                8 * Fixed.ONE);
        h.assertTrue(
                h.getBlockState(new BlockPos(4, 3, 6)).is(Blocks.WATER)
                        && h.getBlockState(new BlockPos(5, 3, 6)).is(Blocks.WATER),
                "元素墙影响瞄准面和相邻面，不因采样落在方块边界而落空");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void normalVolumesRespectOcclusionAndArcanePhases(GameTestHelper h) {
        prepare(h);
        var target = cow(h, new Vec3(4.5, 3.5, 6));
        h.setBlock(new BlockPos(4, 3, 4), Blocks.STONE);
        var origin = new Vec3(4.5, 3.5, 2.5);
        cast(h, SpellSpec.Shape.FAN, SpellSpec.Method.DIRECTED, origin, Element.FIRE, Fixed.ONE);
        h.assertTrue(target.getHealth() == target.getMaxHealth(), "普通扇面不能穿墙命中");
        cast(h, SpellSpec.Shape.FAN, SpellSpec.Method.DIRECTED, origin, Element.ARCANE, Fixed.ONE);
        h.assertTrue(target.getHealth() < target.getMaxHealth(), "纯奥术扇面保留穿透世界方块的元素规则");
        target.discard();
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void shapedLightAndDarkUseRealLeases(GameTestHelper h) {
        prepare(h);
        Vec3 origin = new Vec3(4.5, 3.5, 4.5);
        cast(
                h,
                SpellSpec.Shape.COLUMN,
                SpellSpec.Method.AREA,
                origin,
                Element.LIGHT,
                2 * Fixed.ONE);
        int lights = 0;
        for (int x = 3; x <= 5; x++)
            for (int y = 1; y <= 5; y++)
                for (int z = 3; z <= 5; z++)
                    if (h.getBlockState(new BlockPos(x, y, z))
                            .is(ElementalCircuits.SPELL_LIGHT.get())) lights++;
        h.assertTrue(lights > 0 && lights <= 2, "立柱光共享预算并生成真实临时光：" + lights);
        cast(
                h,
                SpellSpec.Shape.COLUMN,
                SpellSpec.Method.AREA,
                origin,
                Element.SHADOW,
                8 * Fixed.ONE);
        for (int x = 3; x <= 5; x++)
            for (int y = 1; y <= 5; y++)
                for (int z = 3; z <= 5; z++)
                    h.assertTrue(
                            !h.getBlockState(new BlockPos(x, y, z))
                                    .is(ElementalCircuits.SPELL_LIGHT.get()),
                            "同一柱体内的暗熄灭光源");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void volumesNeverBecomePersistentFlights(GameTestHelper h) {
        prepare(h);
        var spells = new WorldSpells();
        boolean rejected = false;
        try {
            spells.launch(
                    null,
                    h.absoluteVec(new Vec3(4.5, 3.5, 4.5)),
                    new Vec3(0, 0, 1),
                    8,
                    2,
                    new Packet(
                            mana(Element.FIRE, Fixed.ONE),
                            new SpellSpec(
                                    SpellSpec.Method.DIRECTED, SpellSpec.Shape.FAN, 0, 0, 0, 0)));
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        h.assertTrue(rejected, "体积法术不能进入投射物寿命状态机");
        var remote = new BlockPos(2200000, 100, 2200000);
        var spec = new SpellSpec(SpellSpec.Method.DIRECTED, SpellSpec.Shape.FAN, 0, 0, 8, 0);
        SpellEffects.cast(
                h.getLevel(),
                null,
                Vec3.atCenterOf(remote),
                new Vec3(0, 0, 1),
                new UUID(1, 1),
                new MagicCircuit.Cast(0, mana(Element.WATER, Fixed.ONE), spec),
                RuleResources.defaults());
        h.assertTrue(!h.getLevel().hasChunkAt(remote), "大范围查询不加载远端区块");
        h.succeed();
    }
}
