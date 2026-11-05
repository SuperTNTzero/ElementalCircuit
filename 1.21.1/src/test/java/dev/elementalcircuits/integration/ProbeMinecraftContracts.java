package dev.elementalcircuits.integration;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.codec.JsonCodecs;
import dev.elementalcircuits.adapter.config.ServerSettings;
import dev.elementalcircuits.adapter.persistence.*;
import dev.elementalcircuits.block.*;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.combat.Element;
import dev.elementalcircuits.domain.survey.SurveyArea;
import dev.elementalcircuits.server.*;
import dev.elementalcircuits.server.mana.PlayerMana;
import dev.elementalcircuits.server.mana.WorldManaField;
import dev.elementalcircuits.server.probe.ProbeReports;
import dev.elementalcircuits.server.probe.ProbeService;
import dev.elementalcircuits.server.probe.SurveyChunkReader;
import dev.elementalcircuits.server.workshop.InscriptionAccess;

import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;

import java.util.*;

@GameTestHolder(ElementalCircuits.ID)
@PrefixGameTestTemplate(false)
public final class ProbeMinecraftContracts {
    private static ServerPlayer player(GameTestHelper h) {
        var p =
                net.neoforged.neoforge.common.util.FakePlayerFactory.get(
                        h.getLevel(),
                        new com.mojang.authlib.GameProfile(UUID.randomUUID(), "probe-test"));
        p.getInventory().selected = 0;
        return p;
    }

    private static ItemStack probe(ServerPlayer p) {
        var s = new ItemStack(ElementalCircuits.PROBE_WAND.get());
        p.setItemInHand(InteractionHand.MAIN_HAND, s);
        return s;
    }

    private static void use(ServerPlayer p, ItemStack s, BlockPos pos) {
        p.moveTo(Vec3.atCenterOf(pos).add(0, 0, -2));
        p.gameMode.useItemOn(
                p,
                p.serverLevel(),
                s,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false));
    }

    private static CompoundTag disk(SurveyArea.Chunk pos, int paletteSize, int index) {
        var root = new CompoundTag();
        root.putInt("xPos", pos.x());
        root.putInt("zPos", pos.z());
        root.putString("Status", "minecraft:full");
        var sections = new ListTag();
        var section = new CompoundTag();
        section.putByte("Y", (byte) 0);
        var states = new CompoundTag();
        var palette = new ListTag();
        for (int i = 0; i < paletteSize; i++) {
            var b = new CompoundTag();
            b.putString(
                    "Name",
                    i == paletteSize - 1
                            ? "elementalcircuits:natural_mana_node"
                            : i == 0 ? "minecraft:air" : "minecraft:stone");
            palette.add(b);
        }
        states.put("palette", palette);
        if (paletteSize > 1) {
            int bits = Math.max(4, 32 - Integer.numberOfLeadingZeros(paletteSize - 1)),
                    per = 64 / bits;
            long[] data = new long[(4096 + per - 1) / per];
            data[index / per] = (long) (paletteSize - 1) << ((index % per) * bits);
            states.putLongArray("data", data);
        }
        section.put("block_states", states);
        sections.add(section);
        root.put("sections", sections);
        return root;
    }

    @GameTest(template = "empty")
    public static void paletteCountUsesActualBlocksAndPackedWordBoundaries(GameTestHelper h) {
        var chunk = new SurveyArea.Chunk(-2, -3);
        int index = 12;
        var area = new SurveyArea(-32 + (index & 15), index >>> 8, -48 + (index >>> 4 & 15), 1);
        for (int size : new int[] {2, 17, 33, 257})
            h.assertTrue(
                    SurveyChunkReader.decode(disk(chunk, size, index), area, chunk).nodes() == 1,
                    "调色板位宽与填充边界准确 size=" + size);
        var all = disk(chunk, 1, 0);
        var sphere = new SurveyArea(-24, 8, -40, 2);
        h.assertTrue(
                SurveyChunkReader.decode(all, sphere, chunk).nodes() == 33,
                "单一调色板仍按真实三维球体过滤，不整段盲计");
        var unfinished = disk(chunk, 2, 12);
        unfinished.putString("Status", "minecraft:features");
        h.assertTrue(
                !SurveyChunkReader.decode(unfinished, area, chunk).generated(), "尚未完成生成不能当成完整空区块");
        var wrong = disk(chunk, 17, 12);
        wrong.getList("sections", 10)
                .getCompound(0)
                .getCompound("block_states")
                .putLongArray("data", new long[1]);
        boolean rejected = false;
        try {
            SurveyChunkReader.decode(wrong, area, chunk);
        } catch (IllegalArgumentException e) {
            rejected = true;
        }
        h.assertTrue(rejected, "错误位存储拒绝，不能返回虚假零节点");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void inspectIsReadonlyAndCrystalInspectionIsRetired(GameTestHelper h) {
        var level = h.getLevel();
        var p = player(h);
        var pos = h.absolutePos(new BlockPos(2, 1, 2));
        h.setBlock(new BlockPos(2, 1, 2), ElementalCircuits.MANA_NODE.get());
        var node = (ManaNodeBlockEntity) level.getBlockEntity(pos);
        node.insert();
        var data = WorldManaField.get(level);
        var field = ManaFieldPersistence.write(data.field());
        long before = node.remaining();
        var report = ProbeReports.block(level, pos);
        h.assertTrue(
                report.title().contains("人工")
                        && report.rows().stream().anyMatch(r -> r.name().equals("储量"))
                        && node.remaining() == before
                        && ManaFieldPersistence.write(data.field()).equals(field),
                "读取储量不消耗、装填或发现环境魔力");
        p.setItemInHand(
                InteractionHand.MAIN_HAND, new ItemStack(ElementalCircuits.MANA_CRYSTAL.get()));
        h.assertTrue(
                p.getMainHandItem().getItem().use(level, p, InteractionHand.MAIN_HAND).getResult()
                        == InteractionResult.PASS,
                "水晶对空气已不再探测");
        h.assertTrue(
                level.getBlockState(pos)
                                .useWithoutItem(
                                        level,
                                        p,
                                        new BlockHitResult(
                                                Vec3.atCenterOf(pos), Direction.UP, pos, false))
                        == InteractionResult.PASS,
                "空手右键节点不再查看信息");
        h.assertTrue(ManaFieldPersistence.write(data.field()).equals(field), "旧入口不会激活魔力场");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeProbeInteractionDoesNotStartOrSelectCircuit(GameTestHelper h) {
        var p = player(h);
        var s = probe(p);
        var rel = new BlockPos(3, 1, 3);
        h.setBlock(rel, ElementalCircuits.CIRCUIT_BLOCK.get());
        var b = (CircuitBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(rel));
        b.placed(new ItemStack(ElementalCircuits.CIRCUIT_ITEM.get()), p);
        var a = b.assembly();
        a.replace(1, RecursionContracts.child(Element.FIRE, false));
        String before = JsonCodecs.assembly(a);
        long mana = PlayerMana.get(p).available();
        use(p, s, b.getBlockPos());
        h.assertTrue(
                JsonCodecs.assembly(a).equals(before)
                        && b.available() == 0
                        && PlayerMana.get(p).available() == mana,
                "原生右键管线使用探测杖不切换供给、不充能");
        h.assertTrue(!InscriptionAccess.allowed(p, b.carrier(), b), "探测不能获得刻印权限");
        var view = ProbeReports.block(h.getLevel(), b.getBlockPos());
        h.assertTrue(
                view.rows().stream().anyMatch(r -> r.name().startsWith("槽位 01")),
                "法阵报告包含明确槽位和运行状态");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void probeDoesNotOpenWorkbenchOrConsumeCrystal(GameTestHelper h) {
        var p = player(h);
        var s = probe(p);
        var pos = h.absolutePos(new BlockPos(3, 1, 3));
        h.setBlock(new BlockPos(3, 1, 3), ElementalCircuits.TABLE.get());
        use(p, s, pos);
        h.assertTrue(!InscriptionAccess.allowed(p, s, null), "探测工作台只读，不创建刻印会话");
        h.setBlock(new BlockPos(3, 1, 3), ElementalCircuits.MANA_NODE.get());
        var n = (ManaNodeBlockEntity) h.getLevel().getBlockEntity(pos);
        p.getCooldowns().removeCooldown(ElementalCircuits.PROBE_WAND.get());
        use(p, s, pos);
        h.assertTrue(n.remaining() == 0 && s.getCount() == 1, "读取空节点不补充燃料或消费探测杖");
        h.setBlock(
                new BlockPos(3, 1, 3),
                ElementalCircuits.WAND_WORKBENCH
                        .get()
                        .defaultBlockState()
                        .setValue(
                                WandWorkbenchBlock.PART,
                                net.minecraft.world.level.block.state.properties.BedPart.HEAD));
        p.getCooldowns().removeCooldown(ElementalCircuits.PROBE_WAND.get());
        use(p, s, pos);
        h.assertTrue(ProbeService.progress(p) == null, "双格法杖台无方块实体的另一半也显示信息，不误启动范围勘测");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void liveScanIncludesDepletedNaturalButNotArtificial(GameTestHelper h) {
        var level = h.getLevel();
        var rel = new BlockPos(3, 1, 3);
        h.setBlock(rel, ElementalCircuits.NATURAL_MANA_NODE.get());
        var natural = (ManaNodeBlockEntity) level.getBlockEntity(h.absolutePos(rel));
        while (natural.remaining() > 0) natural.emitted(natural.emission());
        h.setBlock(rel.east(), ElementalCircuits.MANA_NODE.get());
        var p = natural.getBlockPos();
        var area = new SurveyArea(p.getX(), p.getY(), p.getZ(), 1);
        var chunk = new SurveyArea.Chunk(Math.floorDiv(p.getX(), 16), Math.floorDiv(p.getZ(), 16));
        var result = SurveyChunkReader.read(level, area, chunk);
        h.assertTrue(result.isDone() && result.getNow(null).nodes() == 1, "已加载真实方块计数包含耗尽地脉，排除人工节点");
        var nativeChunk = level.getChunkSource().getChunkNow(chunk.x(), chunk.z());
        var nativeNbt =
                net.minecraft.world.level.chunk.storage.ChunkSerializer.write(level, nativeChunk);
        h.assertTrue(
                SurveyChunkReader.decode(nativeNbt, area, chunk).equals(result.getNow(null)),
                "原生ChunkSerializer保存的真实调色板与活体计数一致");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 2000)
    public static void unloadedDiskSurveyReadsWithoutLoadingOrGenerating(GameTestHelper h) {
        var level = h.getLevel();
        var chunk = new SurveyArea.Chunk(1500000, 1500000);
        var area = new SurveyArea(24000012, 0, 24000000, 1);
        h.assertTrue(level.getChunkSource().getChunkNow(chunk.x(), chunk.z()) == null, "测试区块未加载");
        // GameTestServer runs ticks without wall-clock pacing. Await only test IO fixtures with a
        // real deadline.
        try {
            level.getChunkSource()
                    .chunkMap
                    .write(new ChunkPos(chunk.x(), chunk.z()), disk(chunk, 17, 12))
                    .get(10, java.util.concurrent.TimeUnit.SECONDS);
            var read = SurveyChunkReader.read(level, area, chunk);
            var result = read.get(10, java.util.concurrent.TimeUnit.SECONDS);
            h.assertTrue(
                    result.nodes() == 1
                            && level.getChunkSource().getChunkNow(chunk.x(), chunk.z()) == null,
                    "真实异步存档被计入，仍未加载区块");
            h.succeed();
        } catch (Exception error) {
            throw new IllegalStateException("隔离测试存档I/O失败或超过10秒", error);
        }
    }

    @GameTest(template = "empty")
    public static void radiusFreezesAndLogoutDiscardsPendingObservation(GameTestHelper h) {
        var p = player(h);
        probe(p);
        p.moveTo(h.absoluteVec(new Vec3(4, 2, 4)));
        double radius = ServerSettings.get("probe.radius"),
                budget = ServerSettings.get("probe.chunksPerTick"),
                concurrent = ServerSettings.get("probe.maxConcurrent");
        try {
            ServerSettings.set("probe.maxConcurrent", 8);
            ServerSettings.set("probe.radius", 1);
            ProbeService.use(p, InteractionHand.MAIN_HAND, null);
            var start = ProbeService.progress(p);
            h.assertTrue(start != null && start.total() <= 4, "配置半径用于新的真实探测请求");
            ServerSettings.set("probe.radius", 500);
            ServerSettings.set("probe.chunksPerTick", 1);
            ProbeService.tick(h.getLevel());
            var later = ProbeService.progress(p);
            h.assertTrue(later == null || later.total() == start.total(), "运行中的半径不被配置变化重写");
            ProbeService.forget(p);
            h.assertTrue(ProbeService.progress(p) == null, "退出只丢弃观察任务，不保留世界运行能力");
        } finally {
            ServerSettings.set("probe.radius", radius);
            ServerSettings.set("probe.chunksPerTick", budget);
            ServerSettings.set("probe.maxConcurrent", concurrent);
            ProbeService.forget(p);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void probeRecipeAndItemAreRealRegisteredContent(GameTestHelper h) {
        var recipe =
                h.getLevel()
                        .getRecipeManager()
                        .byKey(
                                ResourceLocation.fromNamespaceAndPath(
                                        ElementalCircuits.ID, "probe_wand"));
        h.assertTrue(
                recipe.isPresent()
                        && recipe.get()
                                .value()
                                .getResultItem(h.getLevel().registryAccess())
                                .is(ElementalCircuits.PROBE_WAND.get()),
                "工作台真实合成探测杖");
        var p = player(h);
        var stack = probe(p);
        var tooltip =
                stack.getTooltipLines(Item.TooltipContext.of(h.getLevel()), p, TooltipFlag.NORMAL);
        h.assertTrue(
                stack.getMaxStackSize() == 1 && !stack.isDamageableItem() && tooltip.size() >= 4,
                "独立不堆叠探测工具及用途提示已注册");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void missingRegionDoesNotCreateFilesOrChunks(GameTestHelper h) {
        var level = h.getLevel();
        var c = new SurveyArea.Chunk(-1500000, 1500000);
        var root =
                level.getServer()
                        .getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
        var file =
                net.minecraft.world.level.dimension.DimensionType.getStorageFolder(
                                level.dimension(), root)
                        .resolve("region")
                        .resolve("r." + (c.x() >> 5) + "." + (c.z() >> 5) + ".mca");
        h.assertTrue(!java.nio.file.Files.exists(file), "隔离世界的远处区域文件不存在");
        var result = SurveyChunkReader.read(level, new SurveyArea(-24000000, 0, 24000000, 1), c);
        h.assertTrue(
                result.isDone()
                        && !result.getNow(null).generated()
                        && !java.nio.file.Files.exists(file)
                        && level.getChunkSource().getChunkNow(c.x(), c.z()) == null,
                "探测缺失区域不创建空文件，不生成或加载区块");
        h.succeed();
    }
}
