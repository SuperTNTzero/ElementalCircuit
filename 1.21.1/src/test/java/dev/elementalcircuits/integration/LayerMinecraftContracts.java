package dev.elementalcircuits.integration;

import dev.elementalcircuits.*;
import dev.elementalcircuits.adapter.codec.JsonCodecs;
import dev.elementalcircuits.adapter.config.ServerSettings;
import dev.elementalcircuits.adapter.persistence.*;
import dev.elementalcircuits.adapter.resources.AssemblyResources;
import dev.elementalcircuits.adapter.resources.RuleResources;
import dev.elementalcircuits.adapter.resources.WandResources;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.casting.SpellSpec;
import dev.elementalcircuits.domain.casting.TriggerBinding;
import dev.elementalcircuits.domain.circuit.Blueprint;
import dev.elementalcircuits.domain.circuit.CircuitAssembly;
import dev.elementalcircuits.domain.circuit.MagicCircuit;
import dev.elementalcircuits.domain.circuit.Rules;
import dev.elementalcircuits.domain.circuit.Rune;
import dev.elementalcircuits.domain.circuit.TickCircuit;
import dev.elementalcircuits.domain.combat.Element;
import dev.elementalcircuits.domain.mana.Mixture;
import dev.elementalcircuits.network.CircuitNetwork;
import dev.elementalcircuits.server.*;
import dev.elementalcircuits.server.circuit.CarrierCommands;
import dev.elementalcircuits.server.circuit.CarrierService;
import dev.elementalcircuits.server.spell.WorldSpells;
import dev.elementalcircuits.server.workshop.InscriptionAccess;
import dev.elementalcircuits.server.workshop.MaterialInventory;

import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;

import java.util.*;

@GameTestHolder(ElementalCircuits.ID)
@PrefixGameTestTemplate(false)
public final class LayerMinecraftContracts {
    private static ServerPlayer player(GameTestHelper h) {
        return net.neoforged.neoforge.common.util.FakePlayerFactory.get(
                h.getLevel(), new com.mojang.authlib.GameProfile(UUID.randomUUID(), "plate-test"));
    }

    private static ItemStack wand(ServerPlayer p) {
        var w = new ItemStack(ElementalCircuits.WAND.get());
        p.getInventory().selected = 0;
        p.getInventory().setItem(0, w);
        return w;
    }

    private static void authorize(GameTestHelper h, ServerPlayer p) {
        var at = h.absolutePos(new BlockPos(3, 2, 3));
        h.getLevel().setBlock(at, ElementalCircuits.TABLE.get().defaultBlockState(), 3);
        p.moveTo(Vec3.atCenterOf(at).add(0, 0, -2));
        InscriptionAccess.interact(p, at);
    }

    private static void command(
            ServerPlayer p, CircuitAssembly a, int layer, int action, String payload) {
        CarrierService.forget(p.getUUID());
        CarrierCommands.request(
                p, a.identity(), a.selected(), action, payload, UUID.randomUUID(), layer);
    }

    private static MagicCircuit.Cast cast() {
        return new MagicCircuit.Cast(
                0,
                Mixture.pure(16 * Fixed.ONE).convert(Element.FIRE, Fixed.ONE),
                new SpellSpec(SpellSpec.Method.DIRECTED, SpellSpec.Shape.SPHERE, 0, 12, 1, 0));
    }

    @GameTest(template = "empty")
    public static void layerAuthoringRequiresStationAndPreservesPhysicalSlot(GameTestHelper h) {
        var p = player(h);
        var w = wand(p);
        var a = CarrierRepository.load(w);
        a.replace(1, RecursionContracts.child(Element.FIRE, false));
        CarrierRepository.commit(w);
        command(p, a, 1, 16, "");
        h.assertTrue(a.layers(1).ids().size() == 1, "无授权不能凭请求添加叠阵");
        authorize(h, p);
        command(p, a, 1, 16, "");
        h.assertTrue(
                a.slots().equals(List.of(1)) && a.layers(1).ids().equals(List.of(1, 2)),
                "新增叠阵不消费另一块基板或创建新槽位");
        String root = JsonCodecs.snapshot(a.circuit(1));
        var originalWater = RecursionMinecraftContracts.area(Element.WATER);
        var water =
                new Blueprint(
                        originalWater.version(),
                        a.circuit(1).definition().rules(),
                        originalWater.runes(),
                        originalWater.routes(),
                        originalWater.continuous());
        command(p, a, 2, 0, JsonCodecs.blueprint(water));
        h.assertTrue(
                a.layers(1).definition(2).equals(water)
                        && JsonCodecs.snapshot(a.circuit(1)).equals(root),
                "真实刻印命令准确保存目标阵层，主阵现场不变");
        command(p, a, 1, 19, JsonCodecs.triggerBinding(TriggerBinding.hit(1, 2)));
        var copy = CarrierRepository.load(w.copy());
        h.assertTrue(
                copy.layers(1).trigger(1).target() == 2
                        && copy.layers(1).definition(2).equals(water),
                "阵层定义和关系通过真实物品NBT持久化");
        String before = JsonCodecs.assembly(a);
        command(p, a, 1, 16, "");
        h.assertTrue(before.equals(JsonCodecs.assembly(a)), "木质公式上限拒绝第二个叠阵且不改现场");
        command(p, a, 99, 0, JsonCodecs.blueprint(water));
        h.assertTrue(before.equals(JsonCodecs.assembly(a)), "不存在的阵层编号不重定向主阵");
        InscriptionAccess.forget(p.getUUID());
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void formulaChangesFutureFlightsWithoutDeletingLayers(GameTestHelper h) {
        var a = RecursionMinecraftContracts.program(TriggerBinding.When.TIMER, 1);
        double initial = ServerSettings.get("layers.initialDepth"),
                growth = ServerSettings.get("layers.depthGrowth");
        try {
            ServerSettings.set("layers.initialDepth", 2);
            ServerSettings.set("layers.depthGrowth", 1.5);
            h.assertTrue(
                    ServerSettings.layerRules().depth(Rules.Material.CRYSTAL) == 4,
                    "配置的2×1.5²向下取整为4；实际初值="
                            + ServerSettings.get("layers.initialDepth")
                            + "，倍率="
                            + ServerSettings.get("layers.depthGrowth")
                            + "，深度="
                            + ServerSettings.layerRules().depth(Rules.Material.CRYSTAL));
            ServerSettings.set("layers.initialDepth", 1);
            ServerSettings.set("layers.depthGrowth", 1);
            var world = new WorldSpells();
            world.castAssembly(
                    h.getLevel(),
                    null,
                    h.absoluteVec(new Vec3(4, 3, 2)),
                    new Vec3(0, 0, 1),
                    a,
                    1,
                    cast(),
                    Fixed.ONE);
            var data =
                    world.save(new CompoundTag(), h.getLevel().registryAccess())
                            .getCompound("Recursion")
                            .getList("Programs", 10)
                            .getCompound(0);
            var frozen = JsonCodecs.readProgram(JsonNbt.decode(data.get("Data"), 16777216));
            h.assertTrue(
                    frozen.version() == 2
                            && frozen.rules().maxDepth() == 1
                            && a.layers(1).ids().size() == 2,
                    "新发射冻结公式上限，降低配置不删除既有阵层");
        } finally {
            ServerSettings.set("layers.initialDepth", initial);
            ServerSettings.set("layers.depthGrowth", growth);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void independentPlateSlotUsesLocalRootNumber(GameTestHelper h) {
        var a =
                new CircuitAssembly(
                        UUID.randomUUID(),
                        AssemblyResources.rules(),
                        new TickCircuit(RecursionContracts.child(Element.FIRE, false)));
        int slot = a.add(a.circuit(1).definition().rules());
        a.replace(slot, RecursionContracts.child(Element.LIGHT, false));
        a.addLayer(slot, 1);
        a.replaceLayer(slot, 2, RecursionMinecraftContracts.area(Element.WATER));
        a.configureLayer(slot, 1, TriggerBinding.hit(1, 2));
        var world = new WorldSpells();
        world.castAssembly(
                h.getLevel(),
                null,
                h.absoluteVec(new Vec3(4, 3, 2)),
                new Vec3(0, 0, 1),
                a,
                slot,
                cast(),
                Fixed.ONE);
        var saved = world.save(new CompoundTag(), h.getLevel().registryAccess());
        h.assertTrue(
                world.armedProjectiles() == 1
                        && saved.getCompound("Recursion")
                                        .getList("Arms", 10)
                                        .getCompound(0)
                                        .getInt("Slot")
                                == 1,
                "物理槽2发射时使用本基板主阵1，不误引用阵层2");
        h.assertTrue(a.layers(1).ids().equals(List.of(1)), "另一块基板的叠阵保持独立");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void legacySlotBindingsRemainStoredButCannotArmNewFlight(GameTestHelper h) {
        var a =
                new CircuitAssembly(
                        UUID.randomUUID(),
                        AssemblyResources.rules(),
                        new TickCircuit(RecursionContracts.child(Element.FIRE, false)));
        a.add(a.circuit(1).definition().rules());
        a.replace(2, RecursionMinecraftContracts.area(Element.WATER));
        a.configureTrigger(1, TriggerBinding.hit(1, 2));
        String old = JsonCodecs.assembly(a);
        var restored = JsonCodecs.readAssembly(old);
        var world = new WorldSpells();
        world.castAssembly(
                h.getLevel(),
                null,
                h.absoluteVec(new Vec3(4, 3, 2)),
                new Vec3(0, 0, 1),
                restored,
                1,
                cast(),
                Fixed.ONE);
        h.assertTrue(
                restored.trigger(1) != null
                        && world.armedProjectiles() == 0
                        && old.equals(JsonCodecs.assembly(restored)),
                "旧跨槽配置保留无损，新的投射物不使用它");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void materialInstallValidatesAllLayersBeforeTakingPlate(GameTestHelper h) {
        var p = player(h);
        var w = wand(p);
        var a = CarrierRepository.load(w);
        a.replace(
                1,
                RecursionContracts.child(Element.FIRE, false).withMaterial(Rules.Material.CRYSTAL));
        var rules = a.circuit(1).definition().rules();
        a.addLayer(1, 4);
        var node = Rune.create(1, Rune.Kind.INPUT, new Hex(7, 0));
        a.replaceLayer(1, 2, new Blueprint(5, rules, List.of(node), List.of(), false));
        CarrierRepository.commit(w);
        p.getInventory().setItem(1, new ItemStack(MaterialInventory.plate(Rules.Material.WOOD), 1));
        authorize(h, p);
        String before = JsonCodecs.assembly(a);
        command(p, a, 1, 4, "WOOD");
        h.assertTrue(
                before.equals(JsonCodecs.assembly(a))
                        && p.getInventory().getItem(1).getCount() == 1,
                "叠阵放不进低级基板时，整体拒绝且不消费库存");
        InscriptionAccess.forget(p.getUUID());
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void legacyFrozenProgramContinuesAfterUpgrade(GameTestHelper h) {
        for (int x = 0; x <= 8; x++)
            for (int z = 0; z <= 8; z++) {
                h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                for (int y = 1; y <= 6; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
            }
        var a = RecursionMinecraftContracts.program(TriggerBinding.When.TIMER, 1);
        var world = new WorldSpells();
        world.castAssembly(
                h.getLevel(),
                null,
                h.absoluteVec(new Vec3(4, 3, 2)),
                new Vec3(0, 0, 1),
                a,
                1,
                cast(),
                Fixed.ONE);
        var raw = world.save(new CompoundTag(), h.getLevel().registryAccess());
        var program = raw.getCompound("Recursion").getList("Programs", 10).getCompound(0);
        String current = JsonNbt.decode(program.get("Data"), 16777216);
        String legacy = current.replaceFirst("\"version\":2", "\"version\":1");
        h.assertTrue(!legacy.equals(current), "构造历史冻结程序v1");
        program.put("Data", JsonNbt.encode(legacy, 16777216));
        var restored = WorldSpells.load(raw);
        for (int i = 0; i < 6; i++) restored.tick(h.getLevel());
        boolean water = false;
        for (int x = 0; x <= 8; x++)
            for (int z = 0; z <= 8; z++)
                for (int y = 1; y <= 4; y++)
                    water |= h.getBlockState(new BlockPos(x, y, z)).is(Blocks.WATER);
        h.assertTrue(water, "升级前已支付的v1冻结程序继续产生真实世界效果");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void layerAddressSurvivesNativeTransport(GameTestHelper h) {
        var id = UUID.randomUUID();
        var token = UUID.randomUUID();
        var request = new CircuitNetwork.Request(id, 2, 19, "off", token, 3);
        var reply =
                new CircuitNetwork.Editor(
                        id,
                        JsonCodecs.blueprint(Blueprint.empty(RuleResources.defaults())),
                        false,
                        0,
                        false,
                        "叠阵",
                        WandResources.definitions().basic(),
                        0,
                        2,
                        false,
                        "",
                        "",
                        false,
                        token,
                        false,
                        3);
        var b = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            CircuitNetwork.writeRequest(request, b);
            CircuitNetwork.writeEditor(reply, b);
            h.assertTrue(
                    CircuitNetwork.readRequest(b).equals(request)
                            && CircuitNetwork.readEditor(b).equals(reply)
                            && b.readableBytes() == 0,
                    "请求和回执均包含物理槽、阵层与令牌");
        } finally {
            b.release();
        }
        h.succeed();
    }
}
