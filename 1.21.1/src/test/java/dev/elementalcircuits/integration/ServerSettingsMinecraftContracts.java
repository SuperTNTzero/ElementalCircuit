package dev.elementalcircuits.integration;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.codec.JsonCodecs;
import dev.elementalcircuits.adapter.config.ServerSettings;
import dev.elementalcircuits.adapter.persistence.*;
import dev.elementalcircuits.adapter.resources.RuleResources;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.circuit.Blueprint;
import dev.elementalcircuits.domain.circuit.Route;
import dev.elementalcircuits.domain.circuit.Rules;
import dev.elementalcircuits.domain.circuit.Rune;
import dev.elementalcircuits.domain.circuit.TickCircuit;
import dev.elementalcircuits.domain.combat.Element;
import dev.elementalcircuits.server.*;
import dev.elementalcircuits.server.circuit.WorldCircuits;
import dev.elementalcircuits.server.equipment.SpellAttributes;
import dev.elementalcircuits.server.mana.PlayerMana;
import dev.elementalcircuits.server.mana.WorldManaField;

import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.*;

import java.util.*;

@GameTestHolder(ElementalCircuits.ID)
@PrefixGameTestTemplate(false)
public final class ServerSettingsMinecraftContracts {
    private static ServerPlayer player(GameTestHelper h) {
        return net.neoforged.neoforge.common.util.FakePlayerFactory.get(
                h.getLevel(),
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "settings-test"));
    }

    private static int command(GameTestHelper h, ServerPlayer p, String cmd) {
        try {
            return h.getLevel()
                    .getServer()
                    .getCommands()
                    .getDispatcher()
                    .execute(
                            cmd,
                            p.createCommandSourceStack().withPermission(4).withSuppressedOutput());
        } catch (Exception e) {
            throw new AssertionError(cmd, e);
        }
    }

    @GameTest(template = "empty")
    public static void managementCommandsAndPersistence(GameTestHelper h) {
        var p = player(h);
        command(h, p, "ec mana capacity @s 2048");
        command(h, p, "ec mana set @s 1000");
        command(h, p, "ec mana add @s -12.5");
        command(h, p, "ec mana regen @s 8");
        command(h, p, "ec mana delay @s 5");
        var pool = PlayerMana.get(p);
        h.assertTrue(
                pool.available() == 987.5 * Fixed.ONE
                        && pool.capacity() == 2048 * Fixed.ONE
                        && pool.regeneration()
                                == 8 * Fixed.ONE
                                        + pool.capacity()
                                                * ServerSettings.mana("player.percentRegeneration")
                                                / Fixed.ONE
                        && pool.recoveryDelay() == 5,
                "真实指令支持容量、负增量、小数和独立恢复策略");
        PlayerMana.forget(p);
        pool = PlayerMana.get(p);
        h.assertTrue(
                pool.available() == 987.5 * Fixed.ONE
                        && pool.capacity() == 2048 * Fixed.ONE
                        && pool.regeneration()
                                == 8 * Fixed.ONE
                                        + pool.capacity()
                                                * ServerSettings.mana("player.percentRegeneration")
                                                / Fixed.ONE
                        && pool.recoveryDelay() == 5,
                "管理员超果实容量及恢复覆盖断缓存后完整恢复");
        long spent = pool.snapshot().spent();
        command(h, p, "ec mana refill @s");
        h.assertTrue(
                pool.available() == pool.capacity() && pool.snapshot().spent() == spent,
                "补满为明确管理行为，不清空消费历史");
        command(h, p, "ec mana resetRecovery @s");
        h.assertTrue(
                pool.regeneration()
                                == ServerSettings.mana("player.regeneration")
                                        + pool.capacity()
                                                * ServerSettings.mana("player.percentRegeneration")
                                                / Fixed.ONE
                        && pool.recoveryDelay()
                                == ServerSettings.count("player.recoveryDelayTicks"),
                "清除覆盖后重新继承服务端默认值");
        var clone = player(h);
        PlayerMana.clone(p, clone);
        h.assertTrue(PlayerMana.get(clone).capacity() == 2048 * Fixed.ONE, "死亡复制保留管理员容量");
        boolean denied = false;
        try {
            h.getLevel()
                    .getServer()
                    .getCommands()
                    .getDispatcher()
                    .execute("ec mana refill @s", p.createCommandSourceStack().withPermission(0));
        } catch (Exception expected) {
            denied = true;
        }
        h.assertTrue(denied, "非管理员无法执行管理指令");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void configurationCreationAndFieldMigration(GameTestHelper h) {
        var p = player(h);
        double old = ServerSettings.get("circuit.woodRadius");
        try {
            var wand = new ItemStack(ElementalCircuits.WAND.get());
            var first = CarrierRepository.load(wand).circuit(1).definition();
            command(h, p, "ec config set circuit.woodRadius 32");
            var next =
                    CarrierRepository.load(new ItemStack(ElementalCircuits.WAND.get()))
                            .circuit(1)
                            .definition();
            h.assertTrue(
                    next.rules().boardRadius() == 32 && first.rules().boardRadius() == (int) old,
                    "新法阵采用配置尺寸，旧法阵保留冻结尺寸");
            h.assertTrue(
                    JsonCodecs.readBlueprint(JsonCodecs.blueprint(next)).equals(next),
                    "新规格经过严格编解码");
            var data = WorldManaField.get(h.getLevel());
            var cell =
                    data.discover(
                            h.getLevel(), h.absolutePos(new net.minecraft.core.BlockPos(1, 2, 1)));
            long held = data.field().amount(cell);
            command(h, p, "ec field applyConfig");
            h.assertTrue(
                    data.field().amount(cell) == held && data.field().snapshot().version() == 2,
                    "显式采用配置不重新补满场");
            var encoded = ManaFieldPersistence.write(data.field());
            h.assertTrue(
                    ManaFieldPersistence.read(encoded).snapshot().equals(data.field().snapshot()),
                    "真实NBT恢复保留v2场历史账本");
            boolean rejected = false;
            try {
                ServerSettings.set("field.ambient", 65536);
            } catch (RuntimeException expected) {
                rejected = true;
            }
            h.assertTrue(rejected, "跨配置项约束原子拒绝，坏配置不落盘");
        } finally {
            ServerSettings.set("circuit.woodRadius", old);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void existingWandAdoptsTransportPolicyWithoutRefill(GameTestHelper h) {
        var p = player(h);
        var wand = new ItemStack(ElementalCircuits.WAND.get());
        p.getInventory().selected = 0;
        p.getInventory().setItem(0, wand);
        var a = CarrierRepository.load(wand);
        var board =
                AssemblyContracts.line(
                        false,
                        Route.Payload.MANA,
                        Rune.Kind.INPUT,
                        Rune.Kind.CONVERT,
                        Rune.Kind.DIRECTED,
                        Rune.Kind.BEAM,
                        Rune.Kind.OUTPUT);
        a.replace(1, board);
        var old = a.circuit(1);
        old.start();
        old.tick(PlayerMana.get(p));
        long spent = PlayerMana.get(p).snapshot().spent();
        h.assertTrue(
                old.held() > 0
                        && old.snapshot().clock() == 1
                        && old.definition().rules().manaSteps() == 1,
                "真实载体中构造旧速度的在途状态");
        WorldCircuits.enqueue(wand, p);
        WorldCircuits.tick(h.getLevel(), id -> null);
        var next = a.circuit(1);
        h.assertTrue(
                next != old
                        && next.definition().rules().manaSteps()
                                == ServerSettings.count("circuit.manaSteps")
                        && next.definition().rules().version() == 4,
                "调度边界将既有法杖升级为当前传输策略");
        h.assertTrue(
                next.snapshot().clock() == 2
                        && next.snapshot().supplied() == old.snapshot().supplied()
                        && PlayerMana.get(p).snapshot().spent() == spent,
                "迁移不会复制输入、重置时钟或补充魔力");
        h.assertTrue(
                next.snapshot().supplied()
                        == next.held() + next.snapshot().loss() + next.snapshot().delivered(),
                "Minecraft服务器执行加速后保持账本守恒");
        String encoded = JsonCodecs.snapshot(next);
        CarrierRepository.commit(wand);
        h.assertTrue(
                JsonCodecs.snapshot(CarrierRepository.load(wand.copy()).circuit(1)).equals(encoded),
                "真实物品持久化恢复多轮传输及完整现场");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void largeNotebookRoundTrip(GameTestHelper h) {
        var rules = RuleResources.defaults().withBoards(new Rules.BoardSizes(64, 64, 64));
        var nodes = new ArrayList<Rune>();
        int id = 1025;
        for (int q = -40; q <= 40 && nodes.size() < 4000; q++)
            for (int r = -40; r <= 40 && nodes.size() < 4000; r++)
                if (new Hex(q, r).inside(40))
                    nodes.add(
                            new Rune(
                                    id++,
                                    Rune.Kind.COUNTER,
                                    new Hex(q, r),
                                    Element.PURE,
                                    1000000L * Fixed.ONE,
                                    true,
                                    false,
                                    List.of(1)));
        var b = new Blueprint(5, rules, nodes, List.of(), false);
        var book = new ItemStack(ElementalCircuits.NOTEBOOK.get());
        var volume = NotebookRepository.load(book).add("大型法阵", b);
        h.assertTrue(
                JsonCodecs.volume(volume).getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                        > 524288,
                "测试确实超过旧蓝图簿字节边界");
        NotebookRepository.save(book, volume);
        var tag =
                (net.minecraft.nbt.CompoundTag)
                        book.save(
                                h.getLevel().registryAccess(), new net.minecraft.nbt.CompoundTag());
        var restored = ItemStack.parseOptional(h.getLevel().registryAccess(), tag);
        h.assertTrue(NotebookRepository.load(restored).equals(volume), "四千符文蓝图经过真实物品NBT完整恢复");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void initialCapacityOnlyAffectsNewPlayers(GameTestHelper h) {
        var oldPlayer = player(h);
        var pool = PlayerMana.get(oldPlayer);
        pool.debit(Fixed.ONE);
        PlayerMana.save(oldPlayer);
        long held = pool.available();
        double old = ServerSettings.get("player.initialCapacity");
        try {
            ServerSettings.set("player.initialCapacity", 256);
            var fresh = player(h);
            long actual = PlayerMana.get(fresh).capacity();
            h.assertTrue(
                    actual == 256 * Fixed.ONE,
                    "initialCapacity actual="
                            + actual
                            + " configured="
                            + ServerSettings.get("player.initialCapacity")
                            + " bonus="
                            + fresh.getAttributeValue(SpellAttributes.MANA_CAPACITY)
                            + " loaded="
                            + ServerSettings.SPEC.isLoaded());
            PlayerMana.forget(oldPlayer);
            h.assertTrue(
                    PlayerMana.get(oldPlayer).available() == held
                            && PlayerMana.get(oldPlayer).capacity() == (long) (old * Fixed.ONE),
                    "已有玩家加载保留原容量和余量");
        } finally {
            ServerSettings.set("player.initialCapacity", old);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void performanceAdmissionDoesNotCorruptSavedDefinitions(GameTestHelper h) {
        var rules = ServerSettings.circuitRules();
        var large =
                new Blueprint(
                        5,
                        rules,
                        List.of(
                                new Rune(
                                        2048,
                                        Rune.Kind.COUNTER,
                                        new Hex(0, 0),
                                        Element.PURE,
                                        1000000L * Fixed.ONE,
                                        true,
                                        false,
                                        List.of())),
                        List.of(),
                        false);
        ServerSettings.admit(large);
        h.assertTrue(
                JsonCodecs.readSnapshot(JsonCodecs.snapshot(new TickCircuit(large)))
                        .definition()
                        .equals(large),
                "大计数参数和高编号实际物品运行格式可保存");
        double old = ServerSettings.get("spell.maxRange");
        try {
            ServerSettings.set("spell.maxRange", 8);
            var b =
                    new Blueprint(
                            5,
                            rules,
                            List.of(
                                    new Rune(
                                            1,
                                            Rune.Kind.RANGE,
                                            new Hex(0, 0),
                                            Element.PURE,
                                            64 * Fixed.ONE,
                                            true,
                                            false,
                                            List.of())),
                            List.of(),
                            false);
            boolean rejected = false;
            try {
                ServerSettings.admit(b);
            } catch (RuntimeException expected) {
                rejected = true;
            }
            h.assertTrue(
                    rejected && JsonCodecs.readBlueprint(JsonCodecs.blueprint(b)).equals(b),
                    "降低准入预算拒绝新编辑，同时允许既有定义严格恢复");
        } finally {
            ServerSettings.set("spell.maxRange", old);
        }
        h.succeed();
    }
}
