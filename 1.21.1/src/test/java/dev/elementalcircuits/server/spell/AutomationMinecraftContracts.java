package dev.elementalcircuits.server.spell;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.block.CircuitBlockEntity;
import dev.elementalcircuits.domain.Fixed;
import dev.elementalcircuits.domain.casting.*;
import dev.elementalcircuits.domain.combat.Element;
import dev.elementalcircuits.domain.mana.Mixture;

import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.*;

import java.util.*;
import java.util.function.Consumer;

@GameTestHolder(ElementalCircuits.ID)
@PrefixGameTestTemplate(false)
public final class AutomationMinecraftContracts {
    @GameTest(template = "empty")
    public static void unaffordableConfiguredHardnessDoesNotFault(GameTestHelper h) {
        var rate =
                (net.neoforged.neoforge.common.ModConfigSpec.DoubleValue)
                        dev.elementalcircuits.adapter.config.ServerSettings.VALUES
                                .get("automation.hardnessCost")
                                .value();
        var maximum =
                (net.neoforged.neoforge.common.ModConfigSpec.DoubleValue)
                        dev.elementalcircuits.adapter.config.ServerSettings.VALUES
                                .get("automation.maxHardness")
                                .value();
        double oldRate = rate.get(), oldMaximum = maximum.get();
        try (var f = fixture(h)) {
            rate.set(1024.0);
            maximum.set(1000000.0);
            h.getLevel().setBlockAndUpdate(f.target(), Blocks.OBSIDIAN.defaultBlockState());
            var at = Vec3.atCenterOf(f.target());
            ElementInteractions.beam(
                    h.getLevel(),
                    f.player().getUUID(),
                    at.add(0, 0, -2),
                    new SpellEffects.Trace(
                            at,
                            null,
                            new BlockHitResult(at, Direction.UP, f.target(), false),
                            true),
                    Mixture.pure(Fixed.MAX).convert(Element.EARTH, Fixed.ONE).inverse(Fixed.ONE),
                    Fixed.ONE / 4,
                    ContactMagic.NEUTRAL);
            h.assertTrue(
                    h.getLevel().getBlockState(f.target()).is(Blocks.OBSIDIAN),
                    "配置报价乘弱法杖强度后超过包上限，视为不可支付而非损坏法阵");
        } finally {
            rate.set(oldRate);
            maximum.set(oldMaximum);
        }
        h.succeed();
    }

    private record Fixture(
            ServerPlayer player,
            CircuitBlockEntity anchor,
            ChestBlockEntity chest,
            BlockPos target,
            MaterialPorts ports)
            implements AutoCloseable {
        public void close() {
            player.serverLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
        }
    }

    private static Fixture fixture(GameTestHelper h) {
        var level = h.getLevel();
        var player =
                net.neoforged.neoforge.common.util.FakePlayerFactory.get(
                        level,
                        new com.mojang.authlib.GameProfile(UUID.randomUUID(), "material-test"));
        player.getAbilities().mayBuild = true;
        level.addNewPlayer(player);
        BlockPos pos = h.absolutePos(new BlockPos(2, 2, 2));
        player.setPos(Vec3.atCenterOf(pos).add(0, 3, 0));
        for (int x = -16; x <= 16; x += 16)
            for (int z = -16; z <= 16; z += 16) level.getChunkAt(pos.offset(x, 0, z));
        level.setBlockAndUpdate(pos, ElementalCircuits.CIRCUIT_BLOCK.get().defaultBlockState());
        var anchor = (CircuitBlockEntity) level.getBlockEntity(pos);
        anchor.placed(new ItemStack(ElementalCircuits.CIRCUIT_ITEM.get()), player);
        dev.elementalcircuits.server.circuit.WorldCircuits.register(anchor);
        level.setBlockAndUpdate(pos.east(), Blocks.CHEST.defaultBlockState());
        var chest = (ChestBlockEntity) level.getBlockEntity(pos.east());
        BlockPos target = h.absolutePos(new BlockPos(6, 2, 6));
        level.setBlockAndUpdate(target, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(target.below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(target.above(), Blocks.AIR.defaultBlockState());
        return new Fixture(
                player,
                anchor,
                chest,
                target,
                new MaterialPorts(5, 5, anchor.assembly().identity()));
    }

    private static void hit(GameTestHelper h, Fixture f, boolean inverse, long amount) {
        hit(h, f, inverse, amount, f.ports());
    }

    private static void hit(
            GameTestHelper h, Fixture f, boolean inverse, long amount, MaterialPorts materials) {
        // Real placement strikes a supporting solid face rather than an impossible air hit.
        BlockPos contact = inverse ? f.target() : f.target().below();
        var at = Vec3.atCenterOf(contact);
        var trace =
                new SpellEffects.Trace(
                        at, null, new BlockHitResult(at, Direction.UP, contact, false), true);
        var mana = Mixture.pure(amount).convert(Element.EARTH, Fixed.ONE);
        if (inverse) mana = mana.inverse(Fixed.ONE);
        ElementInteractions.beam(
                h.getLevel(),
                f.player().getUUID(),
                at.add(0, 0, -2),
                trace,
                mana,
                Fixed.ONE,
                ContactMagic.of(SpellSpec.EMPTY.withMaterials(materials)));
    }

    @GameTest(template = "empty")
    public static void materialFiltersGateContainerInputAndLootOutput(GameTestHelper h) {
        try (var f = fixture(h)) {
            var logs = new MaterialPorts(5, 5, f.anchor().assembly().identity(), "#minecraft:logs");
            f.chest().setItem(0, new ItemStack(Items.STONE, 2));
            hit(h, f, false, 4 * Fixed.ONE, logs);
            h.assertTrue(
                    h.getLevel().getBlockState(f.target()).isAir()
                            && f.chest().getItem(0).getCount() == 2,
                    "物料不匹配时不取走库存或放置方块");

            f.chest().clearContent();
            f.chest().setItem(0, new ItemStack(Items.OAK_LOG, 1));
            hit(h, f, false, 4 * Fixed.ONE, logs);
            h.assertTrue(
                    h.getLevel().getBlockState(f.target()).is(Blocks.OAK_LOG)
                            && f.chest().getItem(0).isEmpty(),
                    "标签筛选允许匹配的方块物料进入放置事务");

            f.chest().clearContent();
            h.getLevel().setBlockAndUpdate(f.target(), Blocks.STONE.defaultBlockState());
            hit(h, f, true, 8 * Fixed.ONE, logs);
            var drops =
                    h.getLevel()
                            .getEntitiesOfClass(
                                    ItemEntity.class,
                                    new AABB(f.target()).inflate(1),
                                    e -> e.getItem().is(Items.COBBLESTONE));
            h.assertTrue(
                    f.chest().isEmpty()
                            && drops.stream().mapToInt(e -> e.getItem().getCount()).sum() == 1,
                    "输出筛选不匹配时掉落物留在世界中");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void earthConsumesPhysicalStockAndInverseReturnsLoot(GameTestHelper h) {
        try (var f = fixture(h)) {
            f.player().getAbilities().instabuild = true;
            f.chest().setItem(0, new ItemStack(Items.STONE, 3));
            hit(h, f, false, Fixed.ONE);
            h.assertTrue(
                    h.getLevel().getBlockState(f.target()).is(Blocks.STONE)
                            && f.chest().getItem(0).getCount() == 2,
                    "正相土一次放一块，创造所有者也消费真实物料");
            hit(h, f, true, Fixed.ONE);
            h.assertTrue(h.getLevel().getBlockState(f.target()).is(Blocks.STONE), "不足硬度成本时不拆解");
            hit(h, f, true, 4 * Fixed.ONE);
            h.assertTrue(h.getLevel().getBlockState(f.target()).isAir(), "反相土拆解石头");
            h.assertTrue(
                    f.chest().getItem(1).is(Items.COBBLESTONE)
                            && f.chest().getItem(1).getCount() == 1,
                    "掉落按普通无附魔钻石镐规则回收到容器");
            f.chest().clearContent();
            hit(h, f, false, 4 * Fixed.ONE);
            h.assertTrue(h.getLevel().getBlockState(f.target()).isAir(), "空容器不能无中生有");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void cancellationsAndStaleAnchorKeepInventory(GameTestHelper h) {
        try (var f = fixture(h)) {
            f.chest().setItem(0, new ItemStack(Items.STONE, 3));
            Consumer<ElementBlockChangeEvent> cancel =
                    e -> {
                        if (e.getPos().equals(f.target())) e.setCanceled(true);
                    };
            NeoForge.EVENT_BUS.addListener(cancel);
            try {
                hit(h, f, false, 4 * Fixed.ONE);
            } finally {
                NeoForge.EVENT_BUS.unregister(cancel);
            }
            h.assertTrue(
                    h.getLevel().getBlockState(f.target()).isAir()
                            && f.chest().getItem(0).getCount() == 3,
                    "放置事件取消不提取库存");
            h.getLevel().setBlockAndUpdate(f.target(), Blocks.STONE.defaultBlockState());
            Consumer<BlockEvent.BreakEvent> deny =
                    e -> {
                        if (e.getPos().equals(f.target())) e.setCanceled(true);
                    };
            NeoForge.EVENT_BUS.addListener(deny);
            try {
                hit(h, f, true, 8 * Fixed.ONE);
            } finally {
                NeoForge.EVENT_BUS.unregister(deny);
            }
            h.assertTrue(h.getLevel().getBlockState(f.target()).is(Blocks.STONE), "拆解遵守原生破坏事件取消");
            h.getLevel().setBlockAndUpdate(f.target(), Blocks.AIR.defaultBlockState());
            h.getLevel()
                    .setBlockAndUpdate(f.anchor().getBlockPos(), Blocks.AIR.defaultBlockState());
            hit(h, f, false, 4 * Fixed.ONE);
            h.assertTrue(
                    h.getLevel().getBlockState(f.target()).isAir()
                            && f.chest().getItem(0).getCount() == 3,
                    "发射后锚点移除不能继续访问旧容器");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void fullContainerLeavesLootAndProtectedBlocksStay(GameTestHelper h) {
        try (var f = fixture(h)) {
            for (int i = 0; i < f.chest().getContainerSize(); i++)
                f.chest().setItem(i, new ItemStack(Items.DIRT, 64));
            h.getLevel().setBlockAndUpdate(f.target(), Blocks.STONE.defaultBlockState());
            hit(h, f, true, 8 * Fixed.ONE);
            var loot =
                    h.getLevel()
                            .getEntitiesOfClass(
                                    ItemEntity.class,
                                    new AABB(f.target()).inflate(1),
                                    e -> e.getItem().is(Items.COBBLESTONE));
            h.assertTrue(
                    loot.stream().mapToInt(e -> e.getItem().getCount()).sum() == 1,
                    "满容器的剩余掉落留在原地，数量不复制");
            for (var block : List.of(Blocks.BEDROCK, Blocks.CHEST)) {
                h.getLevel().setBlockAndUpdate(f.target(), block.defaultBlockState());
                hit(h, f, true, 100 * Fixed.ONE);
                h.assertTrue(h.getLevel().getBlockState(f.target()).is(block), "不可破坏或有独立状态的方块拒绝拆解");
            }
            f.player().getAbilities().mayBuild = false;
            h.getLevel().setBlockAndUpdate(f.target(), Blocks.STONE.defaultBlockState());
            hit(h, f, true, 100 * Fixed.ONE);
            h.assertTrue(h.getLevel().getBlockState(f.target()).is(Blocks.STONE), "没有建造权限不能拆解");
        }
        h.succeed();
    }
}
