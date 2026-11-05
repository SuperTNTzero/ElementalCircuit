package dev.elementalcircuits.integration;

import com.google.gson.*;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.codec.JsonCodecs;
import dev.elementalcircuits.block.*;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.circuit.Blueprint;
import dev.elementalcircuits.domain.circuit.CircuitAddress;
import dev.elementalcircuits.domain.circuit.Route;
import dev.elementalcircuits.domain.circuit.Rune;
import dev.elementalcircuits.server.*;
import dev.elementalcircuits.server.circuit.CarrierService;
import dev.elementalcircuits.server.circuit.WorldCircuitLinks;
import dev.elementalcircuits.server.circuit.WorldCircuits;
import dev.elementalcircuits.server.mana.PlayerMana;
import dev.elementalcircuits.server.workshop.InscriptionAccess;

import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;

import java.util.*;

@GameTestHolder(ElementalCircuits.ID)
@PrefixGameTestTemplate(false)
public final class LogicMinecraftContracts {
    private static ServerPlayer player(GameTestHelper h) {
        return net.neoforged.neoforge.common.util.FakePlayerFactory.get(
                h.getLevel(), new com.mojang.authlib.GameProfile(UUID.randomUUID(), "logic-test"));
    }

    private static CircuitBlockEntity fixed(GameTestHelper h, BlockPos pos, ServerPlayer player) {
        h.setBlock(pos, ElementalCircuits.CIRCUIT_BLOCK.get());
        var b = (CircuitBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(pos));
        b.placed(new ItemStack(ElementalCircuits.CIRCUIT_ITEM.get()), player);
        WorldCircuits.register(b);
        return b;
    }

    private static Blueprint clockLine() {
        return AssemblyContracts.line(
                true,
                Route.Payload.SIGNAL,
                Rune.Kind.CLOCK,
                Rune.Kind.COUNTER,
                Rune.Kind.TOGGLE,
                Rune.Kind.SIGNAL_SEND);
    }

    @GameTest(template = "empty")
    public static void fixedSequentialStateAndCharge(GameTestHelper h) {
        var p = player(h);
        var b = fixed(h, new BlockPos(2, 1, 2), p);
        p.moveTo(Vec3.atCenterOf(b.getBlockPos()));
        b.charge(p);
        b.assembly().replace(1, clockLine());
        var c = b.assembly().circuit(1);
        c.start();
        long before = b.available();
        for (int i = 0; i < 43; i++) WorldCircuits.tick(h.getLevel(), id -> p);
        c = b.assembly().circuit(1);
        h.assertTrue(
                c.logic(1).phase() == 3 && c.logic(2).count() == 2 && !c.logic(3).latched(),
                "真实世界按固定步推进节拍、计数与锁存");
        h.assertTrue(
                before - b.available() == 43 * 4 * c.definition().rules().signalCost(),
                "时序控制从固定法阵真实扣费");
        var saved = b.saveWithFullMetadata(h.getLevel().registryAccess());
        var restored = new CircuitBlockEntity(b.getBlockPos(), b.getBlockState());
        restored.loadWithComponents(saved, h.getLevel().registryAccess());
        h.assertTrue(
                JsonCodecs.assembly(restored.assembly()).equals(JsonCodecs.assembly(b.assembly()))
                        && restored.available() == b.available(),
                "固定方块恢复全部时序态与收支");
        var observation = JsonCodecs.readLogicObservation(JsonCodecs.logicObservation(c));
        h.assertTrue(
                observation.find(2).count() == 2 && observation.find(1).phase() == 3,
                "客户端只读观察包含真实进度");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void authoritativeResetAndBlueprintValidation(GameTestHelper h) {
        var p = player(h);
        var wand = new ItemStack(ElementalCircuits.WAND.get());
        p.getInventory().setItem(0, wand);
        p.getInventory().selected = 0;
        var a = CarrierService.assembly(wand);
        a.replace(1, clockLine());
        var c = a.circuit(1);
        c.start();
        for (int i = 0; i < 43; i++) {
            WorldCircuits.enqueue(wand, p);
            WorldCircuits.tick(h.getLevel(), id -> null);
        }
        c = a.circuit(1);
        h.assertTrue(c.logic(2).count() == 2, "法杖也使用相同计数运行核心");
        long spent = PlayerMana.get(p).snapshot().spent();
        CarrierService.forget(p.getUUID());
        CarrierService.request(p, a.identity(), 1, 13, "2");
        h.assertTrue(
                c.logic(2).count() == 0
                        && c.powered()
                        && c.definition().rune(2).value() == 4 * Fixed.ONE,
                "运行复位清计数、不改变蓝图阈值或供给状态");
        h.assertTrue(PlayerMana.get(p).snapshot().spent() == spent, "复位不额外注入魔力或补玩家槽");
        c.stop();
        CarrierService.persist(wand);
        String before = JsonCodecs.assembly(a);
        var invalid =
                JsonParser.parseString(JsonCodecs.blueprint(c.definition())).getAsJsonObject();
        invalid.getAsJsonArray("runes")
                .get(1)
                .getAsJsonObject()
                .addProperty("value", Fixed.ONE + 1);
        var table = p.blockPosition().above(2);
        h.getLevel().setBlock(table, ElementalCircuits.TABLE.get().defaultBlockState(), 3);
        InscriptionAccess.interact(p, table);
        CarrierService.forget(p.getUUID());
        CarrierService.request(p, a.identity(), 1, 0, invalid.toString());
        h.assertTrue(JsonCodecs.assembly(a).equals(before), "服务器拒绝伪造小数阈值且不改活动现场");
        var loaded =
                ItemStack.parseOptional(
                        h.getLevel().registryAccess(),
                        (CompoundTag) wand.save(h.getLevel().registryAccess(), new CompoundTag()));
        h.assertTrue(
                JsonCodecs.assembly(CarrierService.assembly(loaded)).equals(before),
                "法杖NBT完整保存时序状态");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void clockFeedsCounterAcrossFixedBlocks(GameTestHelper h) {
        var p = player(h);
        var source = fixed(h, new BlockPos(1, 1, 1), p);
        var target = fixed(h, new BlockPos(4, 1, 1), p);
        p.moveTo(Vec3.atCenterOf(source.getBlockPos()));
        source.charge(p);
        target.charge(p);
        Blueprint out =
                AssemblyContracts.line(
                        true, Route.Payload.SIGNAL, Rune.Kind.CLOCK, Rune.Kind.SIGNAL_SEND);
        Rune clock = out.rune(1);
        out =
                new Blueprint(
                        5,
                        out.rules(),
                        List.of(
                                new Rune(
                                        1,
                                        clock.kind(),
                                        clock.cell(),
                                        clock.element(),
                                        2 * Fixed.ONE,
                                        true,
                                        false,
                                        clock.weights()),
                                out.rune(2)),
                        out.routes(),
                        true);
        source.assembly().replace(1, out);
        target.assembly()
                .replace(
                        1,
                        AssemblyContracts.line(
                                true,
                                Route.Payload.SIGNAL,
                                Rune.Kind.SIGNAL_RECEIVE,
                                Rune.Kind.COUNTER,
                                Rune.Kind.TOGGLE,
                                Rune.Kind.SIGNAL_SEND));
        var from = new CircuitAddress(source.assembly().identity(), 1, 2);
        var to = new CircuitAddress(target.assembly().identity(), 1, 1);
        WorldCircuitLinks.get(h.getLevel())
                .connect(
                        h.getLevel(),
                        p,
                        from.carrier(),
                        new JsonCodecs.LinkRequest(from, to, Route.Payload.SIGNAL));
        source.assembly().circuit(1).start();
        target.assembly().circuit(1).start();
        WorldCircuits.tick(h.getLevel(), id -> p);
        h.assertTrue(target.assembly().circuit(1).logic(2).count() == 0, "外部时钟不能同世界步触发计数");
        for (int i = 0; i < 11; i++) WorldCircuits.tick(h.getLevel(), id -> p);
        var c = target.assembly().circuit(1);
        h.assertTrue(c.logic(3).latched(), "节拍跨固定方块互联后每四拍翻转一次");
        int count = c.logic(2).count();
        source.assembly().circuit(1).stop();
        for (int i = 0; i < 6; i++) WorldCircuits.tick(h.getLevel(), id -> p);
        h.assertTrue(!c.validSignal(4) && c.logic(2).count() >= count, "源失效不会伪造新真信号，接收侧保留现场");
        h.succeed();
    }
}
