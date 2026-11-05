package dev.elementalcircuits.integration;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.resources.RuleResources;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.casting.SpellSpec;
import dev.elementalcircuits.domain.circuit.MagicCircuit;
import dev.elementalcircuits.domain.combat.Element;
import dev.elementalcircuits.domain.mana.Mixture;
import dev.elementalcircuits.server.*;
import dev.elementalcircuits.server.spell.SpellEffects;
import dev.elementalcircuits.server.spell.WorldSpells;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;

import java.util.*;

@GameTestHolder(ElementalCircuits.ID)
@PrefixGameTestTemplate(false)
public final class ElementalComboMinecraftContracts {
    private static void prepare(GameTestHelper h) {
        for (int x = -16; x <= 16; x += 16)
            for (int z = -16; z <= 16; z += 16)
                h.getLevel().getChunkAt(h.absolutePos(new BlockPos(4, 2, 4)).offset(x, 0, z));
        for (int x = 0; x <= 10; x++)
            for (int z = 0; z <= 10; z++)
                for (int y = 1; y <= 6; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
    }

    private static Cow cow(GameTestHelper h, int x, int z) {
        var c = h.spawn(EntityType.COW, new BlockPos(x, 2, z));
        c.setNoAi(true);
        c.setNoGravity(true);
        return c;
    }

    private static void cast(GameTestHelper h, UUID owner, int range) {
        var spec = new SpellSpec(SpellSpec.Method.DIRECTED, SpellSpec.Shape.BEAM, 0, range, 0, 0);
        SpellEffects.castOwned(
                h.getLevel(),
                owner,
                h.absoluteVec(new Vec3(2.5, 2.8, 1.5)),
                new Vec3(0, 0, 1),
                new UUID(901, 8),
                new MagicCircuit.Cast(
                        0, Mixture.pure(4 * Fixed.ONE).convert(Element.LIGHTNING, Fixed.ONE), spec),
                RuleResources.defaults(),
                Fixed.ONE);
    }

    @GameTest(template = "empty")
    public static void lightningActuallyChainsAndConserves(GameTestHelper h) {
        prepare(h);
        var a = cow(h, 2, 3);
        var b = cow(h, 5, 3);
        var c = cow(h, 5, 6);
        cast(h, null, 16);
        h.assertTrue(
                a.getHealth() < 10 && b.getHealth() < 10 && c.getHealth() < 10,
                "原生雷束在同一权威调用真实连锁三个目标");
        float damage = 30 - a.getHealth() - b.getHealth() - c.getHealth();
        h.assertTrue(damage > 0 && damage <= 8.001, "三个接触共享四点魔力，损耗后总伤害不超过输入");
        h.assertTrue(
                Math.abs(a.getHealth() - 6) < .001 && b.getHealth() > a.getHealth(),
                "首目标只得到分配份额，后跳衰减且不回击已访问目标");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void lightningCannotJumpThroughWalls(GameTestHelper h) {
        prepare(h);
        var a = cow(h, 2, 3);
        var hidden = cow(h, 5, 3);
        for (int y = 1; y <= 6; y++)
            for (int z = 0; z <= 8; z++) h.setBlock(new BlockPos(4, y, z), Blocks.STONE);
        cast(h, null, 16);
        h.assertTrue(
                hidden.getHealth() == 10 && Math.abs(a.getHealth() - 2) < .001,
                "墙后目标不可连锁，没有后跳时首目标得到现有完整预算");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void lightningTotalRangeIsNotMultiplied(GameTestHelper h) {
        prepare(h);
        var a = cow(h, 2, 3);
        var b = cow(h, 5, 3);
        cast(h, null, 3);
        h.assertTrue(a.getHealth() < 10 && b.getHealth() == 10, "每次跳转扣除原总射程，不能每跳重新获得射程");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void lightningExcludesOwner(GameTestHelper h) {
        prepare(h);
        var a = cow(h, 2, 3);
        var owner = cow(h, 5, 3);
        cast(h, owner.getUUID(), 16);
        h.assertTrue(a.getHealth() < 10 && owner.getHealth() == 10, "初始及跳转共同应用所有者权限");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void coloredShieldRoundtripAndLegacy(GameTestHelper h) {
        var state = new WorldSpells();
        state.shield(
                null,
                h.absoluteVec(new Vec3(3, 3, 3)),
                new Vec3(0, 0, 1),
                2,
                Fixed.ONE,
                Element.WATER);
        var nbt = state.save(new CompoundTag(), h.getLevel().registryAccess());
        h.assertTrue(
                nbt.getInt("Version") == 12
                        && nbt.getList("Shields", 10)
                                .getCompound(0)
                                .getString("Element")
                                .equals("WATER"),
                "屏障不再固定成奥术外观");
        h.assertTrue(
                WorldSpells.load(nbt)
                        .save(new CompoundTag(), h.getLevel().registryAccess())
                        .equals(nbt),
                "元素与容量寿命完整往返");
        var old = nbt.copy();
        old.putInt("Version", 7);
        old.remove("Recursion");
        old.getList("Shields", 10).getCompound(0).remove("Element");
        var migrated = WorldSpells.load(old).save(new CompoundTag(), h.getLevel().registryAccess());
        h.assertTrue(
                migrated.getList("Shields", 10).getCompound(0).getString("Element").equals("ARCANE")
                        && migrated.getList("Shields", 10).getCompound(0).getLong("Capacity")
                                == 8 * Fixed.ONE,
                "历史屏障显式保持原奥术外观且不补能");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void invalidShieldArtPreservesRawState(GameTestHelper h) {
        var state = new WorldSpells();
        state.shield(
                null,
                h.absoluteVec(new Vec3(3, 3, 3)),
                new Vec3(0, 0, 1),
                2,
                Fixed.ONE,
                Element.FIRE);
        var raw = state.save(new CompoundTag(), h.getLevel().registryAccess());
        raw.getList("Shields", 10).getCompound(0).putString("Element", "INVALID");
        var restored = WorldSpells.load(raw);
        restored.tick(h.getLevel());
        h.assertTrue(
                restored.save(new CompoundTag(), h.getLevel().registryAccess()).equals(raw),
                "非法新增字段停用并保留原始文件，不能丢弃现场");
        h.succeed();
    }
}
