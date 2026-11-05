package dev.elementalcircuits.integration;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.persistence.*;
import dev.elementalcircuits.adapter.resources.ManaFieldResources;
import dev.elementalcircuits.adapter.resources.RuleResources;
import dev.elementalcircuits.block.*;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.circuit.Route;
import dev.elementalcircuits.domain.circuit.Rune;
import dev.elementalcircuits.domain.examples.ManaFieldExamples;
import dev.elementalcircuits.domain.mana.ManaField;
import dev.elementalcircuits.server.*;
import dev.elementalcircuits.server.circuit.CarrierService;
import dev.elementalcircuits.server.circuit.WorldCircuits;
import dev.elementalcircuits.server.mana.PlayerMana;
import dev.elementalcircuits.server.mana.WorldManaField;

import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;

import java.util.*;

@GameTestHolder(ElementalCircuits.ID)
@PrefixGameTestTemplate(false)
public final class ManaFieldMinecraftContracts {
    private static ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath(ElementalCircuits.ID, name);
    }

    private static ServerPlayer player(GameTestHelper h) {
        return net.neoforged.neoforge.common.util.FakePlayerFactory.get(
                h.getLevel(), new com.mojang.authlib.GameProfile(UUID.randomUUID(), "field-test"));
    }

    private static ManaNodeBlockEntity node(GameTestHelper h, BlockPos relative, boolean natural) {
        h.setBlock(
                relative,
                (natural
                                ? ElementalCircuits.NATURAL_MANA_NODE.get()
                                : ElementalCircuits.MANA_NODE.get())
                        .defaultBlockState());
        var b = (ManaNodeBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(relative));
        WorldManaField.get(h.getLevel()).register(b);
        return b;
    }

    private static void use(GameTestHelper h, ManaNodeBlockEntity node, ServerPlayer p) {
        var pos = node.getBlockPos();
        p.moveTo(Vec3.atCenterOf(pos).add(0, 0, 1));
        node.getBlockState()
                .useItemOn(
                        p.getMainHandItem(),
                        h.getLevel(),
                        p,
                        InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }

    @GameTest(template = "empty")
    public static void conciseWandTooltipKeepsRealAttributes(GameTestHelper h) {
        var wand = new ItemStack(ElementalCircuits.WAND.get());
        var tooltip =
                wand.getTooltipLines(
                        Item.TooltipContext.of(h.getLevel()), player(h), TooltipFlag.NORMAL);
        h.assertTrue(tooltip.size() == 4, "常规说明只保留名称和三行内容，不重复显示原版近战属性");
        h.assertTrue(!wand.getAttributeModifiers().modifiers().isEmpty(), "隐藏重复说明后仍保留真实近战属性");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void coldPhaseCanActivateNewlyLoadedNodes(GameTestHelper h) {
        var initial = new ManaField(ManaFieldResources.defaults());
        var oldCell = new ManaField.Cell(123456, 0, 123456);
        initial.discover(oldCell);
        for (int i = 0; i < 3; i++) initial.tick(List.of(oldCell));
        var recovered = WorldManaField.load(ManaFieldPersistence.write(initial));
        var n = node(h, new BlockPos(2, 1, 2), false);
        n.insert();
        recovered.register(n);
        recovered.tick(h.getLevel());
        var pos = n.getBlockPos();
        h.assertTrue(
                recovered.field().phase() == 4
                        && recovered
                                .field()
                                .cells()
                                .contains(ManaField.Cell.at(pos.getX(), pos.getY(), pos.getZ())),
                "非零相位恢复且旧区域未加载时，新节点仍能激活场，不会永久等待相位归零");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void survivalFuelAndWholeCrystalGuard(GameTestHelper h) {
        var p = player(h);
        var n = node(h, new BlockPos(2, 1, 2), false);
        p.setItemInHand(
                InteractionHand.MAIN_HAND, new ItemStack(ElementalCircuits.MANA_CRYSTAL.get(), 9));
        h.assertTrue(n.remaining() == 0, "新人工节点不赠送魔力");
        use(h, n, p);
        h.assertTrue(
                n.remaining() == 64 * Fixed.ONE && p.getMainHandItem().getCount() == 8,
                "真实右键把一枚水晶转入节点");
        for (int i = 0; i < 7; i++) use(h, n, p);
        long full = n.remaining();
        use(h, n, p);
        h.assertTrue(
                full == 512 * Fixed.ONE
                        && n.remaining() == full
                        && p.getMainHandItem().getCount() == 1,
                "满载右键不吞水晶");
        var natural = node(h, new BlockPos(4, 1, 2), true);
        long before = natural.remaining();
        use(h, natural, p);
        h.assertTrue(
                natural.remaining() == before && p.getMainHandItem().getCount() == 1, "天然节点不接受燃料");
        h.assertTrue(
                h.getLevel().getRecipeManager().byKey(id("mana_node")).isPresent(), "人工节点具有生存配方");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void nodeBreakPlaceAndCorruptState(GameTestHelper h) {
        var n = node(h, new BlockPos(2, 1, 2), false);
        n.insert();
        n.emitted(n.emission());
        long remaining = n.remaining();
        var saved = n.saveWithFullMetadata(h.getLevel().registryAccess());
        var clone = new ManaNodeBlockEntity(n.getBlockPos(), n.getBlockState());
        clone.loadWithComponents(saved, h.getLevel().registryAccess());
        h.assertTrue(
                clone.remaining() == remaining
                        && clone.saveWithFullMetadata(h.getLevel().registryAccess()).equals(saved),
                "节点NBT往返不回填");
        var drops =
                Block.getDrops(
                        n.getBlockState(), h.getLevel(), n.getBlockPos(), n, null, ItemStack.EMPTY);
        h.assertTrue(
                drops.size() == 1
                        && drops.get(0).is(ElementalCircuits.MANA_NODE_ITEM.get())
                        && drops.get(0).getMaxStackSize() == 1,
                "人工节点拆下保留独立状态");
        h.getLevel().removeBlock(n.getBlockPos(), false);
        var replacement = node(h, new BlockPos(3, 1, 2), false);
        replacement.placed(drops.get(0));
        h.assertTrue(replacement.remaining() == remaining, "移动节点保持已消耗储量");
        var bad = saved.copy();
        bad.getCompound("Node").putLong("Remaining", 999 * Fixed.ONE);
        clone.loadWithComponents(bad, h.getLevel().registryAccess());
        h.assertTrue(
                !clone.usable()
                        && clone.saveWithFullMetadata(h.getLevel().registryAccess()).equals(bad),
                "坏账停用并完整保留");
        var broken = node(h, new BlockPos(4, 1, 2), false);
        broken.placed(clone.drop());
        h.assertTrue(!broken.usable(), "拆装不能洗掉损坏状态");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void exhaustedNaturalDoesNotMintCrystals(GameTestHelper h) {
        var n = node(h, new BlockPos(2, 1, 2), true);
        h.assertTrue(n.drop().getCount() == 2, "富集地脉最多掉两枚水晶");
        while (n.remaining() > 0) n.emitted(n.emission());
        h.assertTrue(
                n.drop().isEmpty() && !n.getBlockState().getValue(ManaNodeBlock.LIT),
                "地脉耗尽后熄灭且不再产出燃料");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void fieldNbtAndQuotaAreRealAndFinite(GameTestHelper h) {
        var level = h.getLevel();
        var data = WorldManaField.get(level);
        var pos = h.absolutePos(new BlockPos(2, 1, 2));
        var c = data.discover(level, pos);
        var tap = data.tap(level, pos);
        long before = data.field().amount(c);
        long quota = data.field().rules().intakePerTick();
        h.assertTrue(
                tap.debit(quota) && !tap.debit(1) && before - data.field().amount(c) == quota,
                "同一供给能力总额度不可重复领取");
        var snapshot = data.save(new CompoundTag(), level.registryAccess());
        var restored = WorldManaField.load(snapshot);
        h.assertTrue(
                restored.valid()
                        && restored.save(new CompoundTag(), level.registryAccess())
                                .equals(snapshot),
                "维度魔力场相位与收支完整NBT恢复");
        var bad = snapshot.copy();
        bad.putLong("Extracted", bad.getLong("Extracted") + 1);
        var disabled = WorldManaField.load(bad);
        h.assertTrue(
                !disabled.valid()
                        && disabled.save(new CompoundTag(), level.registryAccess()).equals(bad),
                "场数据损坏停用，保存不覆盖原始数据");
        data.discover(level, pos);
        h.assertTrue(data.field().amount(c) == before - quota, "反复激活和读取不重生初始魔力");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void actualFixedAbsorptionCastsWithoutLocalCharge(GameTestHelper h) {
        var p = player(h);
        var relative = new BlockPos(3, 1, 2);
        h.setBlock(
                relative,
                ElementalCircuits.CIRCUIT_BLOCK
                        .get()
                        .defaultBlockState()
                        .setValue(CircuitBlock.FACING, Direction.SOUTH));
        var b = (CircuitBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(relative));
        b.placed(new ItemStack(ElementalCircuits.CIRCUIT_ITEM.get()), p);
        WorldCircuits.register(b);
        b.assembly().replace(1, ManaFieldExamples.beam(RuleResources.defaults()));
        b.assembly().circuit(1).start();
        var zombie = h.spawn(EntityType.ZOMBIE, new BlockPos(3, 1, 5));
        zombie.setNoAi(true);
        float health = zombie.getHealth();
        long spent = PlayerMana.get(p).snapshot().spent();
        for (int i = 0; i < 20; i++) WorldCircuits.tick(h.getLevel(), id -> p);
        h.assertTrue(
                b.available() == 0
                        && PlayerMana.get(p).snapshot().spent() == spent
                        && b.assembly().circuit(1).snapshot().supplied() > 0,
                "环境法阵没有本地储量，也未消费玩家槽");
        h.assertTrue(zombie.getHealth() < health, "真实调度的汲取光束造成世界命中");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void multipleCircuitsShareOneHostIntake(GameTestHelper h) {
        var p = player(h);
        var stack = new ItemStack(ElementalCircuits.WAND.get());
        p.getInventory().selected = 0;
        p.getInventory().setItem(0, stack);
        p.moveTo(Vec3.atCenterOf(h.absolutePos(new BlockPos(2, 1, 2))));
        var a = CarrierService.assembly(stack);
        a.replace(1, AssemblyContracts.line(true, Route.Payload.MANA, Rune.Kind.ABSORB));
        a.add(RuleResources.defaults());
        a.replace(2, AssemblyContracts.line(true, Route.Payload.MANA, Rune.Kind.ABSORB));
        a.slots().forEach(s -> a.circuit(s).start());
        WorldCircuits.enqueue(stack, p);
        WorldCircuits.tick(h.getLevel(), id -> p);
        long supplied = a.slots().stream().mapToLong(s -> a.circuit(s).snapshot().supplied()).sum();
        h.assertTrue(
                supplied == 2 * ManaFieldResources.defaults().intakePerTick(),
                "两口增加两倍真实吞吐，并共用同一有限场源");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void oreLootUsesNativeFortuneAndSilk(GameTestHelper h) {
        var level = h.getLevel();
        var pos = h.absolutePos(new BlockPos(2, 1, 2));
        var tool = new ItemStack(Items.IRON_PICKAXE);
        for (var block :
                List.of(
                        ElementalCircuits.MANA_ORE.get(),
                        ElementalCircuits.DEEPSLATE_MANA_ORE.get())) {
            var drops = Block.getDrops(block.defaultBlockState(), level, pos, null, null, tool);
            h.assertTrue(
                    drops.size() == 1 && drops.get(0).is(ElementalCircuits.MANA_CRYSTAL.get()),
                    "真实矿石掉落魔力水晶");
            var silk = tool.copy();
            silk.enchant(
                    level.registryAccess()
                            .lookupOrThrow(Registries.ENCHANTMENT)
                            .getOrThrow(Enchantments.SILK_TOUCH),
                    1);
            var untouched = Block.getDrops(block.defaultBlockState(), level, pos, null, null, silk);
            h.assertTrue(
                    untouched.size() == 1 && untouched.get(0).is(block.asItem()), "精准采集保留对应矿石");
            var fortune = tool.copy();
            fortune.enchant(
                    level.registryAccess()
                            .lookupOrThrow(Registries.ENCHANTMENT)
                            .getOrThrow(Enchantments.FORTUNE),
                    3);
            int crystals = 0;
            for (int i = 0; i < 40; i++)
                crystals +=
                        Block.getDrops(block.defaultBlockState(), level, pos, null, null, fortune)
                                .stream()
                                .mapToInt(ItemStack::getCount)
                                .sum();
            h.assertTrue(crystals > 40 && crystals <= 160, "时运使用原版矿物加成");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeWorldgenPlacesNodeAndBiomeFeatures(GameTestHelper h) {
        var level = h.getLevel();
        var configured = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE);
        var placed = level.registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
        var origin = h.absolutePos(new BlockPos(2, 1, 2));
        level.setBlock(origin, Blocks.DEEPSLATE.defaultBlockState(), 3);
        configured
                .get(id("natural_mana_node"))
                .place(
                        level,
                        level.getChunkSource().getGenerator(),
                        RandomSource.create(42),
                        origin);
        h.assertTrue(
                level.getBlockState(origin).is(ElementalCircuits.NATURAL_MANA_NODE.get())
                        && level.getBlockEntity(origin) instanceof ManaNodeBlockEntity,
                "原生世界生成创建天然节点与方块实体");
        var n = (ManaNodeBlockEntity) level.getBlockEntity(origin);
        h.assertTrue(n.remaining() == 4096 * Fixed.ONE, "新生成的地脉有有限初始储量");
        var feature =
                placed.getHolderOrThrow(
                        net.minecraft.resources.ResourceKey.create(
                                Registries.PLACED_FEATURE, id("mana_crystal_ore")));
        var natural =
                placed.getHolderOrThrow(
                        net.minecraft.resources.ResourceKey.create(
                                Registries.PLACED_FEATURE, id("natural_mana_node")));
        var plains =
                level.registryAccess()
                        .registryOrThrow(Registries.BIOME)
                        .get(net.minecraft.world.level.biome.Biomes.PLAINS);
        h.assertTrue(
                plains.getGenerationSettings().features().stream()
                                .anyMatch(list -> list.contains(feature))
                        && plains.getGenerationSettings().features().stream()
                                .anyMatch(list -> list.contains(natural)),
                "主世界生物群系已注入矿石及节点生成阶段");
        h.succeed();
    }
}
