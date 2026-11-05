package dev.elementalcircuits.integration;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.codec.JsonCodecs;
import dev.elementalcircuits.adapter.config.ServerSettings;
import dev.elementalcircuits.adapter.persistence.*;
import dev.elementalcircuits.adapter.resources.AssemblyResources;
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
import dev.elementalcircuits.domain.mana.Packet;
import dev.elementalcircuits.server.*;
import dev.elementalcircuits.server.circuit.CarrierService;
import dev.elementalcircuits.server.spell.WorldSpells;
import dev.elementalcircuits.server.workshop.BookService;
import dev.elementalcircuits.server.workshop.InscriptionAccess;

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
public final class RecursionMinecraftContracts {
    private static void prepare(GameTestHelper h) {
        for (int x = 0; x <= 8; x++)
            for (int z = 0; z <= 8; z++) {
                h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                for (int y = 1; y <= 6; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
            }
    }

    public static Blueprint area(Element element) {
        var b = RecursionContracts.child(element, false);
        var nodes =
                b.runes().stream()
                        .map(
                                r ->
                                        r.kind() == Rune.Kind.DIRECTED
                                                ? new Rune(
                                                        r.id(),
                                                        Rune.Kind.AREA,
                                                        r.cell(),
                                                        r.element(),
                                                        r.value(),
                                                        r.on(),
                                                        false,
                                                        r.weights())
                                                : r)
                        .toList();
        return new Blueprint(5, b.rules(), nodes, b.routes(), false);
    }

    public static CircuitAssembly program(TriggerBinding.When when, int delay) {
        var a =
                new CircuitAssembly(
                        UUID.randomUUID(),
                        AssemblyResources.rules(),
                        new TickCircuit(
                                RecursionContracts.child(Element.FIRE, false)
                                        .withMaterial(Rules.Material.CRYSTAL)));
        a.addLayer(1, 4);
        a.replaceLayer(1, 2, area(Element.WATER).withMaterial(Rules.Material.CRYSTAL));
        a.configureLayer(1, 1, new TriggerBinding(1, 2, when, delay, 75));
        return a;
    }

    private static MagicCircuit.Cast root(long n) {
        return new MagicCircuit.Cast(
                0,
                Mixture.pure(n).convert(Element.FIRE, Fixed.ONE),
                new SpellSpec(SpellSpec.Method.DIRECTED, SpellSpec.Shape.SPHERE, 0, 12, 1, 0));
    }

    private static CompoundTag save(WorldSpells s, GameTestHelper h) {
        return s.save(new CompoundTag(), h.getLevel().registryAccess());
    }

    private static void fire(
            WorldSpells state, GameTestHelper h, CircuitAssembly a, UUID owner, Vec3 position) {
        state.castAssembly(
                h.getLevel(),
                owner,
                position,
                new Vec3(0, 0, 1),
                a,
                1,
                root(16 * Fixed.ONE),
                Fixed.ONE);
    }

    private static boolean water(GameTestHelper h) {
        for (int x = 0; x <= 8; x++)
            for (int z = 0; z <= 8; z++)
                for (int y = 1; y <= 4; y++)
                    if (h.getBlockState(new BlockPos(x, y, z)).is(Blocks.WATER)) return true;
        return false;
    }

    @GameTest(template = "empty")
    public static void impactRunsFrozenWaterCircuitAtContact(GameTestHelper h) {
        prepare(h);
        for (int x = 2; x <= 6; x++)
            for (int y = 1; y <= 4; y++) h.setBlock(new BlockPos(x, y, 5), Blocks.STONE);
        var a = program(TriggerBinding.When.IMPACT, 20);
        var state = new WorldSpells();
        String original = JsonCodecs.blueprint(a.layers(1).definition(2));
        fire(state, h, a, null, h.absoluteVec(new Vec3(4.5, 1.7, 1.5)));
        var launched = save(state, h);
        h.assertTrue(
                state.armedProjectiles() == 1
                        && launched.getCompound("Recursion").getList("Programs", 10).size() == 1,
                "根投射物携带一份预付预算和冻结程序");
        long primary =
                JsonCodecs.readPacket(
                                launched.getList("Flights", 10).getCompound(0).getString("Packet"))
                        .mana()
                        .total();
        long reserved =
                JsonCodecs.readPacket(
                                launched.getCompound("Recursion")
                                        .getList("Arms", 10)
                                        .getCompound(0)
                                        .getString("Mana"))
                        .mana()
                        .total();
        h.assertTrue(
                primary + reserved == 16 * Fixed.ONE && reserved == 12 * Fixed.ONE,
                "母伤害和子阵预算精确拆分，不复制原魔力");
        a.configureLayer(1, 1, null);
        a.replaceLayer(1, 2, area(Element.LIGHT).withMaterial(Rules.Material.CRYSTAL));
        for (int i = 0; i < 12 && !water(h); i++) state.tick(h.getLevel());
        h.assertTrue(water(h), "命中后实际执行冻结的水元素子阵并创建水源，后来改光元素无效");
        h.assertTrue(
                a.circuit(1).snapshot().clock() == 0 && !a.circuit(1).powered(), "子实例不推进基板的主阵实例");
        h.assertTrue(
                !original.isEmpty() && state.invocationJobs() == 0 && state.armedProjectiles() == 0,
                "事件消费一次并回收任务");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void timerBudgetAndNextBoundary(GameTestHelper h) {
        prepare(h);
        var a = program(TriggerBinding.When.TIMER, 2);
        var state = new WorldSpells();
        fire(state, h, a, null, h.absoluteVec(new Vec3(4.5, 3, 1.5)));
        state.tick(h.getLevel());
        h.assertTrue(state.invocationJobs() == 0 && state.armedProjectiles() == 1, "第一步只减少定时剩余");
        state.tick(h.getLevel());
        var tagged = save(state, h);
        h.assertTrue(
                state.invocationJobs() == 1 && state.armedProjectiles() == 0, "第二步创建子阵，但不当场展开调用");
        var job = tagged.getCompound("Recursion").getList("Jobs", 10).getCompound(0);
        String encoded = JsonNbt.decode(job.get("State"), JsonCodecs.SNAPSHOT_LIMIT);
        h.assertTrue(
                encoded.contains("\"age\":0") && !encoded.contains("\"blueprint\""),
                "新任务年龄0，保存只引用程序而不复制蓝图");
        for (int i = 0; i < 4; i++) state.tick(h.getLevel());
        h.assertTrue(
                state.invocationJobs() == 0
                        && state.armedProjectiles() == 0
                        && !save(state, h).getList("Flights", 10).isEmpty(),
                "定时只触发一次，母投射物仍继续飞行");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void recursiveSelfCallIsFiniteAndRestoresExactly(GameTestHelper h) {
        prepare(h);
        var a = program(TriggerBinding.When.TIMER, 1);
        a.replaceLayer(
                1,
                2,
                RecursionContracts.child(Element.WATER, false)
                        .withMaterial(Rules.Material.CRYSTAL));
        a.configureLayer(1, 2, new TriggerBinding(2, 1, TriggerBinding.When.TIMER, 1, 75));
        var state = new WorldSpells();
        fire(state, h, a, null, h.absoluteVec(new Vec3(4.5, 4, 1.5)));
        state.tick(h.getLevel());
        var raw = save(state, h);
        var copy = WorldSpells.load(raw);
        for (int i = 0; i < 12; i++) {
            state.tick(h.getLevel());
            copy.tick(h.getLevel());
            h.assertTrue(save(state, h).equals(save(copy, h)), "循环派生的完整现场、胶囊编号和轨迹恢复后一致");
        }
        for (int i = 0; i < 50; i++) state.tick(h.getLevel());
        var done = save(state, h).getCompound("Recursion");
        h.assertTrue(
                done.getList("Jobs", 10).isEmpty()
                        && done.getList("Arms", 10).isEmpty()
                        && done.getList("Chains", 10).isEmpty()
                        && done.getList("Programs", 10).isEmpty(),
                "A→B→A到深度/有限预算后全部结束并回收共享程序");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void workloadRotatesAndProgramIsShared(GameTestHelper h) {
        prepare(h);
        var a = program(TriggerBinding.When.TIMER, 1);
        var state = new WorldSpells();
        double old = ServerSettings.get("recursion.workPerTick");
        try {
            ServerSettings.set("recursion.workPerTick", 1);
            for (int i = 0; i < 3; i++)
                fire(state, h, a, null, h.absoluteVec(new Vec3(2.5 + i * 2, 4, 1.5)));
            state.tick(h.getLevel());
            h.assertTrue(
                    state.invocationJobs() == 3
                            && save(state, h)
                                            .getCompound("Recursion")
                                            .getList("Programs", 10)
                                            .size()
                                    == 1,
                    "三个根施法链共享同一份冻结程序");
            for (int left = 2; left >= 0; left--) {
                state.tick(h.getLevel());
                h.assertTrue(state.invocationJobs() == left, "每世界步只推进配置数量的任务，其余等待");
            }
        } finally {
            ServerSettings.set("recursion.workPerTick", old);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void unloadedFlightAndJobFreezeWithoutLoading(GameTestHelper h) {
        prepare(h);
        var a = program(TriggerBinding.When.TIMER, 1);
        var state = new WorldSpells();
        Vec3 far = h.absoluteVec(new Vec3(1000000.5, 3, 1000000.5));
        fire(state, h, a, null, far);
        var before = save(state, h);
        for (int i = 0; i < 4; i++) state.tick(h.getLevel());
        h.assertTrue(
                save(state, h).equals(before) && !h.getLevel().hasChunkAt(BlockPos.containing(far)),
                "未加载投射物与定时冻结，不强加载");
        state = new WorldSpells();
        fire(state, h, a, null, h.absoluteVec(new Vec3(4.5, 3, 1.5)));
        state.tick(h.getLevel());
        var raw = save(state, h);
        raw.put("Flights", new ListTag());
        var job = raw.getCompound("Recursion").getList("Jobs", 10).getCompound(0);
        job.putDouble("PX", far.x);
        job.putDouble("PY", far.y);
        job.putDouble("PZ", far.z);
        state = WorldSpells.load(raw);
        before = save(state, h);
        state.tick(h.getLevel());
        h.assertTrue(save(state, h).equals(before), "已付款子任务在未加载区块保持完整VM与供给现场");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void shieldsAbsorbTheReservedContinuation(GameTestHelper h) {
        prepare(h);
        var a = program(TriggerBinding.When.IMPACT, 20);
        var state = new WorldSpells();
        Vec3 origin = h.absoluteVec(new Vec3(4.5, 2.5, 1.5));
        fire(state, h, a, new UUID(1, 2), origin);
        state.shield(new UUID(3, 4), origin.add(0, 0, 1.5), new Vec3(0, 0, -1), 2, 32 * Fixed.ONE);
        for (int i = 0; i < 5; i++) state.tick(h.getLevel());
        h.assertTrue(
                state.invocationJobs() == 0
                        && state.armedProjectiles() == 0
                        && save(state, h).getList("Flights", 10).isEmpty(),
                "完整屏障吸收母攻击和预留预算，不透过屏障免费触发子阵");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void corruptRecursionPreservesOriginalAndLegacyMigrates(GameTestHelper h) {
        prepare(h);
        var a = program(TriggerBinding.When.TIMER, 1);
        var state = new WorldSpells();
        fire(state, h, a, null, h.absoluteVec(new Vec3(4.5, 3, 1.5)));
        var raw = save(state, h);
        var orphan = raw.copy();
        orphan.getCompound("Recursion").put("Programs", new ListTag());
        var stopped = WorldSpells.load(orphan);
        stopped.tick(h.getLevel());
        h.assertTrue(save(stopped, h).equals(orphan), "悬空程序记录停用并保留原始NBT");
        var duplicate = raw.copy();
        duplicate
                .getList("Flights", 10)
                .add(duplicate.getList("Flights", 10).getCompound(0).copy());
        h.assertTrue(save(WorldSpells.load(duplicate), h).equals(duplicate), "同一胶囊不能被两个投射物消费");
        var old = new WorldSpells();
        old.launch(
                null,
                h.absoluteVec(new Vec3(4, 3, 1)),
                new Vec3(0, 0, 1),
                10,
                1,
                new Packet(root(Fixed.ONE).mana(), root(Fixed.ONE).spell()));
        var legacy = save(old, h);
        legacy.putInt("Version", 8);
        legacy.remove("Recursion");
        var migrated = save(WorldSpells.load(legacy), h);
        h.assertTrue(
                migrated.getInt("Version") == 12
                        && migrated.getCompound("Recursion").getList("Jobs", 10).isEmpty()
                        && migrated.getList("Flights", 10).equals(legacy.getList("Flights", 10)),
                "v8显式初始化空递归表，不改旧飞行体");
        h.succeed();
    }

    private static ServerPlayer player(GameTestHelper h) {
        return net.neoforged.neoforge.common.util.FakePlayerFactory.get(
                h.getLevel(),
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "selector-test"));
    }

    @GameTest(template = "empty")
    public static void wandAndBookSelectionRespectPhysicalCarrier(GameTestHelper h) {
        var p = player(h);
        var wand = new ItemStack(ElementalCircuits.WAND.get());
        p.getInventory().selected = 0;
        p.getInventory().setItem(0, wand);
        var a = CarrierRepository.load(wand);
        a.add(a.circuit(1).definition().rules());
        a.select(1);
        String before = JsonCodecs.snapshot(a.circuit(1));
        BookService.forget(p.getUUID());
        BookService.spellRequest(p, a.identity(), 8, 1);
        h.assertTrue(
                a.selected() == 2 && JsonCodecs.snapshot(a.circuit(1)).equals(before),
                "不带书时快捷键选择法杖自身槽位，不重启VM");
        BookService.forget(p.getUUID());
        BookService.spellRequest(p, a.identity(), 7, 1);
        h.assertTrue(a.selected() == 1, "轮盘确认按明确槽位选择");
        var book = new ItemStack(ElementalCircuits.SPELL_BOOK.get());
        p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, book);
        var b = CarrierRepository.load(book);
        b.add(b.circuit(1).definition().rules());
        b.select(1);
        h.assertTrue(
                BookService.selection(p).slots().size() == 4
                        && BookService.castCarrier(p, wand) == wand,
                "配书追加两个槽位，默认仍使用法杖");
        BookService.forget(p.getUUID());
        BookService.spellRequest(p, a.identity(), 7, 2);
        h.assertTrue(a.selected() == 2 && b.selected() == 1, "配书后法杖自身槽位仍可选择");
        BookService.forget(p.getUUID());
        BookService.spellRequest(p, a.identity(), b.identity(), 7, 2);
        h.assertTrue(
                b.selected() == 2 && BookService.castCarrier(p, wand) == book, "来源身份和局部槽号共同确定书中法阵");
        BookService.forget(p.getUUID());
        BookService.spellRequest(p, a.identity(), 8, 1);
        h.assertTrue(
                a.selected() == 1 && BookService.castCarrier(p, wand) == wand, "跨来源快捷键从书末页循环回法杖");
        BookService.forget(p.getUUID());
        BookService.spellRequest(p, a.identity(), b.identity(), 7, 2);
        p.getInventory().setItem(0, ItemStack.EMPTY);
        BookService.forget(p.getUUID());
        BookService.spellRequest(p, a.identity(), b.identity(), 7, 1);
        h.assertTrue(b.selected() == 2, "松开轮盘时已换手则拒绝改变载体");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void bindingEditsRequireStationAndStoppedCircuits(GameTestHelper h) {
        var p = player(h);
        var w = new ItemStack(ElementalCircuits.WAND.get());
        var a = CarrierRepository.load(w);
        a.replace(1, RecursionContracts.child(Element.FIRE, false));
        a.addLayer(1, 2);
        a.replaceLayer(1, 2, area(Element.WATER));
        a.add(a.circuit(1).definition().rules());
        a.replace(2, area(Element.LIGHT));
        a.select(1);
        CarrierRepository.commit(w);
        p.getInventory().setItem(0, w);
        var pos = h.absolutePos(new BlockPos(3, 2, 3));
        h.getLevel().setBlock(pos, ElementalCircuits.TABLE.get().defaultBlockState(), 3);
        p.moveTo(Vec3.atCenterOf(pos).add(0, 0, -2));
        String config = JsonCodecs.triggerBinding(TriggerBinding.hit(1, 2));
        CarrierService.forget(p.getUUID());
        CarrierService.request(p, a.identity(), 1, 19, config);
        h.assertTrue(a.layers(1).trigger(1) == null, "仅靠近工作台不能远程修改派生");
        InscriptionAccess.interact(p, pos);
        CarrierService.forget(p.getUUID());
        CarrierService.request(p, a.identity(), 1, 19, config.replace("\"when\":\"IMPACT\",", ""));
        h.assertTrue(a.layers(1).trigger(1) == null, "缺少明确条件的网络请求不能保存默认命中");
        CarrierService.forget(p.getUUID());
        CarrierService.request(p, a.identity(), 1, 19, config);
        h.assertTrue(
                a.layers(1).trigger(1) != null
                        && JsonCodecs.readAssembly(JsonCodecs.assembly(a))
                                        .layers(1)
                                        .trigger(1)
                                        .target()
                                == 2,
                "工作台授权后配置实际保存到载体");
        a.circuit(2).start();
        CarrierService.forget(p.getUUID());
        CarrierService.request(p, a.identity(), 1, 19, "off");
        h.assertTrue(a.layers(1).trigger(1) != null, "任一槽供给中拒绝改派生，不影响已有现场");
        a.circuit(2).stop();
        p.moveTo(Vec3.atCenterOf(pos).add(9, 0, 0));
        CarrierService.forget(p.getUUID());
        CarrierService.request(p, a.identity(), 1, 19, "off");
        h.assertTrue(a.layers(1).trigger(1) != null, "远离工作台撤销修改权");
        InscriptionAccess.forget(p.getUUID());
        CarrierService.forget(p.getUUID());
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void partialShieldAndAdmissionNeverDuplicateBudget(GameTestHelper h) {
        prepare(h);
        var a = program(TriggerBinding.When.TIMER, 2);
        var state = new WorldSpells();
        Vec3 p = h.absoluteVec(new Vec3(4.5, 3, 1.5));
        UUID owner = UUID.randomUUID();
        fire(state, h, a, owner, p);
        state.shield(
                UUID.randomUUID(),
                p.add(0, 0, .4),
                new Vec3(0, 0, -1),
                3,
                Fixed.ONE / 2,
                Element.ARCANE);
        state.tick(h.getLevel());
        var saved = save(state, h);
        long base =
                JsonCodecs.readPacket(
                                saved.getList("Flights", 10).getCompound(0).getString("Packet"))
                        .mana()
                        .total();
        long held =
                JsonCodecs.readPacket(
                                saved.getCompound("Recursion")
                                        .getList("Arms", 10)
                                        .getCompound(0)
                                        .getString("Mana"))
                        .mana()
                        .total();
        h.assertTrue(
                base + held < 16 * Fixed.ONE && held < 12 * Fixed.ONE, "部分屏障同时消耗母投射物与子阵预留，两者总量下降");
        h.assertTrue(save(WorldSpells.load(saved), h).equals(saved), "部分吸收后的单份预留可完整恢复");
        double old = ServerSettings.get("recursion.activeUnits");
        try {
            ServerSettings.set("recursion.activeUnits", 1);
            state.tick(h.getLevel());
            h.assertTrue(
                    state.armedProjectiles() == 0 && state.invocationJobs() == 0,
                    "工作容量不足则消耗一次事件，不无限排队、不透支");
        } finally {
            ServerSettings.set("recursion.activeUnits", old);
        }
        h.succeed();
    }
}
