package dev.elementalcircuits.server.spell;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.domain.Fixed;
import dev.elementalcircuits.domain.casting.*;
import dev.elementalcircuits.domain.circuit.MagicCircuit;
import dev.elementalcircuits.domain.combat.Element;
import dev.elementalcircuits.domain.mana.Mixture;

import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.*;

import java.util.*;
import java.util.function.Consumer;

@GameTestHolder(ElementalCircuits.ID)
@PrefixGameTestTemplate(false)
public final class ConnectionMinecraftContracts {
    @GameTest(template = "empty")
    public static void apertureSurvivesNativeVisualPacket(GameTestHelper h) {
        var point = new dev.elementalcircuits.application.observation.SpellVisual.Point(0, 0, 0);
        var event =
                new dev.elementalcircuits.application.observation.SpellVisual(
                        dev.elementalcircuits.application.observation.SpellVisual.Kind.FAN,
                        Element.FIRE,
                        point,
                        new dev.elementalcircuits.application.observation.SpellVisual.Point(
                                0, 0, 1),
                        4,
                        1,
                        125.5f);
        var packet =
                new dev.elementalcircuits.network.VisualNetwork.Batch(
                        h.getLevel().dimension().location(),
                        h.getLevel().getGameTime(),
                        List.of(event));
        var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            dev.elementalcircuits.network.VisualNetwork.write(packet, buffer);
            h.assertTrue(
                    dev.elementalcircuits.network.VisualNetwork.read(buffer).equals(packet)
                            && buffer.readableBytes() == 0,
                    "自定义连续张角在实际批量网络包中无损往返");
        } finally {
            buffer.release();
        }
        h.succeed();
    }

    private record Fixture(
            GameTestHelper h,
            ServerPlayer player,
            Vec3 origin,
            WorldConnections data,
            WorldConnections.Key key)
            implements AutoCloseable {
        void touch(Vec3 direction) {
            data.touch(h.getLevel(), key, player.getUUID(), origin, direction);
        }

        void offer(SpellSpec spec, long amount) {
            data.offer(
                    h.getLevel(),
                    key,
                    new MagicCircuit.Cast(
                            1, Mixture.pure(amount).convert(Element.WIND, Fixed.ONE), spec, 1));
        }

        void tick(Vec3 direction) {
            touch(direction);
            data.tick(h.getLevel());
        }

        ItemEntity item(double distance) {
            var at = origin.add(0, -.125, distance);
            var item = new ItemEntity(h.getLevel(), at.x, at.y, at.z, new ItemStack(Items.STONE));
            item.setNoGravity(true);
            item.setDeltaMovement(Vec3.ZERO);
            h.getLevel().addFreshEntity(item);
            return item;
        }

        public void close() {
            player.serverLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
        }
    }

    private static Fixture fixture(GameTestHelper h) {
        var level = h.getLevel();
        var player =
                net.neoforged.neoforge.common.util.FakePlayerFactory.get(
                        level,
                        new com.mojang.authlib.GameProfile(UUID.randomUUID(), "connection-test"));
        player.getAbilities().mayBuild = true;
        level.addNewPlayer(player);
        var origin = Vec3.atCenterOf(h.absolutePos(new BlockPos(2, 3, 2)));
        player.setPos(origin.add(-3, 1, 0));
        for (int x = -16; x <= 16; x += 16)
            for (int z = -16; z <= 16; z += 16)
                level.getChunkAt(BlockPos.containing(origin).offset(x, 0, z));
        return new Fixture(
                h,
                player,
                origin,
                new WorldConnections(),
                new WorldConnections.Key(UUID.randomUUID(), 1, 1));
    }

    private static SpellSpec spec(ConnectionEffects effects) {
        return new SpellSpec(
                        SpellSpec.Method.CONTACT, SpellSpec.Shape.UNSET, 4 * Fixed.ONE, 8, 0, 8192)
                .withConnection(effects);
    }

    @GameTest(template = "empty")
    public static void anchoredReferenceSurvivesAimAndSave(GameTestHelper h) {
        try (var f = fixture(h)) {
            var item = f.item(4);
            var spell =
                    spec(new ConnectionEffects(0, Fixed.ONE, false, false, true, 1, 7))
                            .withSpatial(new SpatialParameters(0, 0, 0, 2 * Fixed.ONE));
            f.touch(new Vec3(0, 0, 1));
            f.offer(spell, 4 * Fixed.ONE);
            var saved = f.data.save(new CompoundTag(), h.getLevel().registryAccess());
            var restored = WorldConnections.load(saved);
            restored.touch(h.getLevel(), f.key, f.player.getUUID(), f.origin, new Vec3(1, 0, 0));
            restored.tick(h.getLevel());
            h.assertTrue(
                    restored.contains(f.key)
                            && item.getDeltaMovement().z < 0
                            && Math.abs(item.getDeltaMovement().x) < .000001,
                    "读档并转向后约束仍拉向固定的世界锚点");
            var after =
                    restored.save(new CompoundTag(), h.getLevel().registryAccess())
                            .getList("Links", 10)
                            .getCompound(0);
            var before = saved.getList("Links", 10).getCompound(0);
            h.assertTrue(
                    after.getDouble("AnchorX") == before.getDouble("AnchorX")
                            && after.getDouble("AnchorZ") == before.getDouble("AnchorZ"),
                    "视线变化不会改写参考位置");
            before.remove("AnchorX");
            var bad = WorldConnections.load(saved);
            h.assertTrue(
                    bad.size() == 0
                            && bad.save(new CompoundTag(), h.getLevel().registryAccess())
                                    .equals(saved),
                    "损坏锚点现场不能猜测或覆盖原数据");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void targetFilterSkipsEntitiesWithoutPassingWalls(GameTestHelper h) {
        try (var f = fixture(h)) {
            var pig = net.minecraft.world.entity.EntityType.PIG.create(h.getLevel());
            pig.setPos(f.origin.add(0, -.5, 2));
            pig.setNoAi(true);
            h.getLevel().addFreshEntity(pig);
            var item = f.item(4);
            f.touch(new Vec3(0, 0, 1));
            f.offer(spec(new ConnectionEffects(0, 0, false, false, false, 1, 7)), Fixed.ONE);
            h.assertTrue(item.getUUID().equals(f.data.target(f.key)), "掉落物筛选跳过前方生物");
            f.touch(new Vec3(0, 0, 1));
            f.offer(spec(new ConnectionEffects(0, 0, false, false, false, 2, 7)), Fixed.ONE);
            h.assertTrue(pig.getUUID().equals(f.data.target(f.key)), "更改筛选后按生物身份重新建立");
            h.getLevel()
                    .setBlockAndUpdate(
                            BlockPos.containing(f.origin.add(0, 0, 1)),
                            Blocks.STONE.defaultBlockState());
            f.data.release(f.key);
            f.touch(new Vec3(0, 0, 1));
            f.offer(spec(new ConnectionEffects(0, 0, false, false, false, 1, 7)), Fixed.ONE);
            h.assertTrue(!f.data.contains(f.key), "跳过非目标实体也不能越过方块遮挡");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void releaseAddressesOnlyOneContactRune(GameTestHelper h) {
        try (var f = fixture(h)) {
            f.item(3);
            f.touch(new Vec3(0, 0, 1));
            f.offer(spec(new ConnectionEffects(0, 0, false, false, false, 0, 7)), Fixed.ONE);
            var second = new WorldConnections.Key(f.key.carrier(), 1, 2);
            f.data.touch(h.getLevel(), second, f.player.getUUID(), f.origin, new Vec3(0, 0, 1));
            f.data.offer(
                    h.getLevel(),
                    second,
                    new MagicCircuit.Cast(
                            2,
                            Mixture.pure(Fixed.ONE),
                            spec(new ConnectionEffects(0, 0, false, false, false, 0, 8)),
                            2));
            f.data.releaseControl(f.key.carrier(), 1, 7);
            h.assertTrue(!f.data.contains(f.key) && f.data.contains(second), "解除信号不会误清同基板其他联系");
            f.data.releaseControl(f.key.carrier(), 1, 7);
            h.assertTrue(f.data.contains(second), "持续高电平重复解除保持幂等");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void probeIsReadOnlyAndReportsRealInventoryCapacity(GameTestHelper h) {
        int[] calls = {0};
        var changing =
                new net.neoforged.neoforge.items.ItemStackHandler(1) {
                    @Override
                    public int getSlots() {
                        return ++calls[0] == 1 ? 1 : Integer.MAX_VALUE;
                    }

                    @Override
                    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                        throw new AssertionError("探知不得尝试插入");
                    }

                    @Override
                    public ItemStack extractItem(int slot, int amount, boolean simulate) {
                        throw new AssertionError("探知不得尝试提取");
                    }
                };
        changing.setStackInSlot(0, new ItemStack(Items.STONE, 64));
        h.assertTrue(WorldProbe.full(changing, 128) && calls[0] == 1, "冻结观察槽数，动态能力不能扩大检查预算");
        h.assertTrue(
                !WorldProbe.full(
                        new net.neoforged.neoforge.items.ItemStackHandler(1) {
                            @Override
                            public int getSlots() {
                                return -1;
                            }
                        },
                        128),
                "异常负槽数不误报满容器");
        try (var f = fixture(h)) {
            var level = h.getLevel();
            var pos = BlockPos.containing(f.origin.add(0, 0, 3));
            level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
            var chest =
                    (net.minecraft.world.level.block.entity.ChestBlockEntity)
                            level.getBlockEntity(pos);
            h.assertTrue(
                    WorldProbe.sample(level, f.key.carrier(), 1, f.origin, new Vec3(0, 0, 1), 0),
                    "真实方块产生存在信号");
            h.assertTrue(
                    !WorldProbe.sample(level, f.key.carrier(), 1, f.origin, new Vec3(0, 0, 1), 1),
                    "空容器不报满");
            for (int i = 0; i < chest.getContainerSize(); i++)
                chest.setItem(i, new ItemStack(Items.STONE, 64));
            h.assertTrue(
                    WorldProbe.sample(level, f.key.carrier(), 1, f.origin, new Vec3(0, 0, 1), 1),
                    "每格达到真实堆叠上限才报满");
            for (int i = 0; i < chest.getContainerSize(); i++)
                h.assertTrue(chest.getItem(i).getCount() == 64, "观察不取放任何物品");
            chest.setItem(0, new ItemStack(Items.STONE, 63));
            h.assertTrue(
                    !WorldProbe.sample(level, f.key.carrier(), 1, f.origin, new Vec3(0, 0, 1), 1),
                    "差一物品即非满状态");
            h.assertTrue(
                    WorldProbe.sample(level, f.key.carrier(), 1, f.origin, new Vec3(0, 0, 1), 3),
                    "未建立联系按无联系输出");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void keepsIdentityAndConsumesPrepaidMaintenance(GameTestHelper h) {
        try (var f = fixture(h)) {
            var first = f.item(3);
            var second = f.item(5);
            var s = spec(new ConnectionEffects(Fixed.ONE, 0, false, false));
            f.touch(new Vec3(0, 0, 1));
            f.offer(s, 4 * Fixed.ONE);
            h.assertTrue(first.getUUID().equals(f.data.target(f.key)), "联系锁定首先命中的实体身份");
            long before = f.data.allowance(f.key);
            f.tick(new Vec3(0, 0, 1));
            h.assertTrue(
                    f.data.allowance(f.key) < before && first.getDeltaMovement().z > 0,
                    "维护和真实冲量消耗预付魔力");
            first.setPos(first.position().add(.8, 0, 0));
            f.touch(new Vec3(0, 0, 1));
            f.offer(s, Fixed.ONE);
            h.assertTrue(
                    first.getUUID().equals(f.data.target(f.key))
                            && second.getDeltaMovement().lengthSqr() == 0,
                    "续供不重新射线选中后方实体");
            h.assertTrue(first.isNoGravity(), "联系不篡改原有重力状态");
            f.data.tick(h.getLevel()); // Last offer supplied one live heartbeat.
            f.data.tick(h.getLevel()); // No heartbeat: immediate release despite saved allowance.
            h.assertTrue(f.data.size() == 0, "停阵或宿主丢失后不能靠缓存继续操作");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void noBudgetNoWorkAndNoLink(GameTestHelper h) {
        try (var f = fixture(h)) {
            var item = f.item(3);
            f.touch(new Vec3(0, 0, 1));
            f.offer(spec(new ConnectionEffects(Fixed.ONE, 0, false, false)), 1);
            f.tick(new Vec3(0, 0, 1));
            h.assertTrue(
                    f.data.size() == 0 && item.getDeltaMovement().lengthSqr() == 0,
                    "不足维护费时解除，无免费冲量");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void constraintsUseForceAndExplicitDistance(GameTestHelper h) {
        try (var f = fixture(h)) {
            var item = f.item(4);
            var s =
                    spec(new ConnectionEffects(0, Fixed.ONE, false, false))
                            .withSpatial(new SpatialParameters(0, 0, 0, 2 * Fixed.ONE));
            f.touch(new Vec3(0, 0, 1));
            f.offer(s, 4 * Fixed.ONE);
            Vec3 before = item.position();
            f.tick(new Vec3(0, 0, 1));
            h.assertTrue(
                    item.position().equals(before) && item.getDeltaMovement().z < 0,
                    "联系距离通过真实速度向参考点牵引，不传送");
            h.assertTrue(item.getDeltaMovement().length() <= .150001, "约束受单实体冲量预算限制");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void obstructionAndOutOfRangeRelease(GameTestHelper h) {
        try (var f = fixture(h)) {
            var item = f.item(4);
            f.touch(new Vec3(0, 0, 1));
            f.offer(spec(ConnectionEffects.NONE), 4 * Fixed.ONE);
            var wall = BlockPos.containing(f.origin.add(0, 0, 2));
            h.getLevel().setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
            f.tick(new Vec3(0, 0, 1));
            h.assertTrue(f.data.size() == 0, "实体与源之间新出现实体墙时立即解除");
            h.getLevel().setBlockAndUpdate(wall, Blocks.AIR.defaultBlockState());
            f.touch(new Vec3(0, 0, 1));
            f.offer(spec(ConnectionEffects.NONE), 4 * Fixed.ONE);
            item.setPos(item.position().add(0, 0, 12));
            f.tick(new Vec3(0, 0, 1));
            h.assertTrue(f.data.size() == 0, "实体越过冻结射程后解除");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void completeSaveRequiresRenewedAuthority(GameTestHelper h) {
        try (var f = fixture(h)) {
            var item = f.item(3);
            f.touch(new Vec3(0, 0, 1));
            f.offer(spec(ConnectionEffects.NONE), 4 * Fixed.ONE);
            CompoundTag saved = f.data.save(new CompoundTag(), h.getLevel().registryAccess());
            var resumed = WorldConnections.load(saved);
            h.assertTrue(
                    item.getUUID().equals(resumed.target(f.key))
                            && resumed.allowance(f.key) == f.data.allowance(f.key),
                    "目标身份与预付额度完整恢复");
            resumed.touch(h.getLevel(), f.key, f.player.getUUID(), f.origin, new Vec3(0, 0, 1));
            resumed.tick(h.getLevel());
            h.assertTrue(resumed.size() == 1, "恢复后真实宿主可继续维护原目标");
            resumed.tick(h.getLevel());
            h.assertTrue(resumed.size() == 0, "恢复不凭空恢复活动宿主权限");
            saved.getList("Links", 10).getCompound(0).putLong("Allowance", -1);
            var invalid = WorldConnections.load(saved);
            h.assertTrue(
                    invalid.size() == 0
                            && invalid.save(new CompoundTag(), h.getLevel().registryAccess())
                                    .equals(saved),
                    "损坏现场停用并保留原数据");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void poweredCircuitRefreshesLinkUntilStopped(GameTestHelper h) {
        try (var f = fixture(h)) {
            f.item(4);
            f.touch(new Vec3(0, 0, 1));
            f.offer(spec(new ConnectionEffects(0, 0, false, false, false, 1, 7)), 4 * Fixed.ONE);
            long initial = f.data.allowance(f.key);
            f.data.refreshSlot(
                    h.getLevel(),
                    f.key.carrier(),
                    f.key.slot(),
                    f.player.getUUID(),
                    f.origin,
                    new Vec3(0, 0, 1));
            f.data.tick(h.getLevel());
            h.assertTrue(
                    f.data.contains(f.key) && f.data.allowance(f.key) < initial,
                    "运行中的法阵逐步续持联系，同时持续支付维护魔力");
            f.data.tick(h.getLevel());
            h.assertTrue(!f.data.contains(f.key), "法阵停止续持后联系自动解除");
        }
        h.succeed();
    }

    private static BlockPos lever(Fixture f) {
        var pos = BlockPos.containing(f.origin.add(0, 0, 4));
        f.h.getLevel().setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
        f.h.getLevel()
                .setBlockAndUpdate(
                        pos,
                        Blocks.LEVER
                                .defaultBlockState()
                                .setValue(LeverBlock.FACE, AttachFace.FLOOR));
        return pos;
    }

    private static Vec3 aim(Fixture f, BlockPos pos) {
        return Vec3.atCenterOf(pos).add(0, -.35, 0).subtract(f.origin).normalize();
    }

    @GameTest(template = "empty")
    public static void interactionUsesEdgesAndNativeCancellation(GameTestHelper h) {
        try (var f = fixture(h)) {
            var pos = lever(f);
            var direction = aim(f, pos);
            var high = spec(new ConnectionEffects(0, 0, true, true));
            f.touch(direction);
            f.offer(high, 4 * Fixed.ONE);
            f.tick(direction);
            h.assertTrue(
                    h.getLevel().getBlockState(pos).getValue(LeverBlock.POWERED), "首次信号上升沿使用原版拉杆");
            f.touch(direction);
            f.offer(high, Fixed.ONE);
            f.tick(direction);
            h.assertTrue(
                    h.getLevel().getBlockState(pos).getValue(LeverBlock.POWERED), "持续高电平不反复操作");
            var resumed =
                    WorldConnections.load(
                            f.data.save(new CompoundTag(), h.getLevel().registryAccess()));
            resumed.touch(h.getLevel(), f.key, f.player.getUUID(), f.origin, direction);
            resumed.offer(
                    h.getLevel(),
                    f.key,
                    new MagicCircuit.Cast(2, Mixture.pure(Fixed.ONE), high, 1));
            resumed.tick(h.getLevel());
            h.assertTrue(
                    h.getLevel().getBlockState(pos).getValue(LeverBlock.POWERED), "保存恢复不重放高电平");
            f.touch(direction);
            f.offer(high.withConnection(high.connection().withSignal(false)), Fixed.ONE);
            f.tick(direction);
            Consumer<PlayerInteractEvent.RightClickBlock> cancel =
                    e -> {
                        if (e.getPos().equals(pos)) e.setCanceled(true);
                    };
            NeoForge.EVENT_BUS.addListener(cancel);
            try {
                f.touch(direction);
                f.offer(high, Fixed.ONE);
                f.tick(direction);
            } finally {
                NeoForge.EVENT_BUS.unregister(cancel);
            }
            h.assertTrue(
                    h.getLevel().getBlockState(pos).getValue(LeverBlock.POWERED), "原版交互事件取消仍被尊重");
            f.touch(direction);
            f.offer(high.withConnection(high.connection().withSignal(false)), Fixed.ONE);
            f.tick(direction);
            f.touch(direction);
            f.offer(high, Fixed.ONE);
            f.tick(direction);
            h.assertTrue(
                    !h.getLevel().getBlockState(pos).getValue(LeverBlock.POWERED), "再次低后高只切换一次");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void ownershipAndBlockReplacementDoNotGrantActions(GameTestHelper h) {
        try (var f = fixture(h)) {
            var item = f.item(3);
            item.setTarget(UUID.randomUUID());
            f.touch(new Vec3(0, 0, 1));
            f.offer(spec(new ConnectionEffects(Fixed.ONE, 0, false, false)), 4 * Fixed.ONE);
            h.assertTrue(f.data.size() == 0, "不可牵引其他玩家拥有的掉落物");
            item.discard();
            var pos = lever(f);
            var direction = aim(f, pos);
            f.touch(direction);
            f.offer(spec(ConnectionEffects.NONE), 4 * Fixed.ONE);
            h.getLevel().setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
            f.tick(direction);
            h.assertTrue(f.data.size() == 0, "方块替换不能劫持旧联系来获取容器权限");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void multipleLinksShareImpulseBudget(GameTestHelper h) {
        try (var f = fixture(h)) {
            var item = f.item(3);
            var s = spec(new ConnectionEffects(32 * Fixed.ONE, 0, false, false));
            f.touch(new Vec3(0, 0, 1));
            f.offer(s, 4 * Fixed.ONE);
            var other = new WorldConnections.Key(f.key.carrier(), 1, 2);
            f.data.touch(h.getLevel(), other, f.player.getUUID(), f.origin, new Vec3(0, 0, 1));
            f.data.offer(
                    h.getLevel(),
                    other,
                    new MagicCircuit.Cast(2, Mixture.pure(4 * Fixed.ONE), s, 2));
            f.tick(new Vec3(0, 0, 1));
            h.assertTrue(
                    f.data.size() == 2 && item.getDeltaMovement().length() <= .150001,
                    "多出口分别付费但不能叠加绕过物理预算");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeCollisionStopsTransportAndInversePulls(GameTestHelper h) {
        try (var f = fixture(h)) {
            var item = f.item(3);
            var wall = BlockPos.containing(f.origin.add(0, 0, 5));
            for (int x = -1; x <= 1; x++)
                for (int y = -1; y <= 1; y++)
                    h.getLevel()
                            .setBlockAndUpdate(
                                    wall.offset(x, y, 0), Blocks.STONE.defaultBlockState());
            var spell = spec(new ConnectionEffects(4 * Fixed.ONE, 0, false, false));
            f.touch(new Vec3(0, 0, 1));
            f.offer(spell, 16 * Fixed.ONE);
            double initial = item.getZ();
            for (int i = 0; i < 30; i++) {
                f.tick(new Vec3(0, 0, 1));
                item.move(net.minecraft.world.entity.MoverType.SELF, item.getDeltaMovement());
            }
            h.assertTrue(
                    item.getZ() > initial && item.getBoundingBox().maxZ <= wall.getZ() + .00001,
                    "真实碰撞允许推到墙前，但不能穿过方块");
            item.setDeltaMovement(Vec3.ZERO);
            f.data.release(f.key);
            f.touch(new Vec3(0, 0, 1));
            f.data.offer(
                    h.getLevel(),
                    f.key,
                    new MagicCircuit.Cast(
                            2,
                            Mixture.pure(4 * Fixed.ONE)
                                    .convert(Element.WIND, Fixed.ONE)
                                    .inverse(Fixed.ONE),
                            spell,
                            1));
            f.tick(new Vec3(0, 0, 1));
            h.assertTrue(item.getDeltaMovement().z < 0, "反相魔力沿相反方向牵引");
        }
        h.succeed();
    }
}
