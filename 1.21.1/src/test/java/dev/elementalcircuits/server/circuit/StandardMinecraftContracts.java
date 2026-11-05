package dev.elementalcircuits.server.circuit;

import dev.elementalcircuits.*;
import dev.elementalcircuits.adapter.config.ServerSettings;
import dev.elementalcircuits.adapter.persistence.*;
import dev.elementalcircuits.adapter.resources.RuleResources;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.casting.ReleasePermit;
import dev.elementalcircuits.domain.casting.TriggeredCircuit;
import dev.elementalcircuits.domain.circuit.TickCircuit;
import dev.elementalcircuits.domain.examples.StandardCircuit;
import dev.elementalcircuits.domain.mana.ManaPool;
import dev.elementalcircuits.domain.mana.ManaSource;
import dev.elementalcircuits.domain.mana.StorageSettings;
import dev.elementalcircuits.server.mana.PlayerMana;

import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.*;

import java.util.*;

@GameTestHolder(ElementalCircuits.ID)
@PrefixGameTestTemplate(false)
public final class StandardMinecraftContracts {
    private static ServerPlayer player(GameTestHelper h) {
        return new net.neoforged.neoforge.common.util.FakePlayer(
                h.getLevel(),
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "standard-mana"));
    }

    @GameTest(template = "empty")
    public static void fullInputThroughNativeSupply(GameTestHelper h) {
        var p = player(h);
        var wand = new ItemStack(ElementalCircuits.WAND.get());
        p.getInventory().selected = 0;
        p.getInventory().setItem(0, wand);
        var a = CarrierRepository.load(wand);
        var old64 =
                RuleResources.defaults()
                        .withManaSteps(8)
                        .withCasting(ServerSettings.castingRules());
        a.replace(1, StandardCircuit.create(old64, false).withInputAmount(1, 10000 * Fixed.ONE));
        var pool = PlayerMana.get(p);
        pool.setCapacity(20000 * Fixed.ONE);
        pool.setAmount(20000 * Fixed.ONE);
        var host = MinecraftCircuitHost.carried(h.getLevel(), wand, p);
        host.assembly();
        h.assertTrue(host.prepare(), "真实物品供给宿主准备成功");
        var c = a.circuit(1);
        c.start();
        c.tick(
                host.supply(),
                ReleasePermit.UNLIMITED,
                ManaSource.NONE,
                host.castContext(),
                host.shapeRules());
        h.assertTrue(
                pool.snapshot().spent() == 10000 * Fixed.ONE
                        && c.snapshot().supplied() == 10000 * Fixed.ONE,
                "旧64容量的法杖经真实玩家供给完整注入10000，不能截成56/64/1024");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void lowManaDoesNotScaleDownRequest(GameTestHelper h) {
        var p = player(h);
        var pool = PlayerMana.get(p);
        pool.setCapacity(64 * Fixed.ONE);
        pool.setAmount(56 * Fixed.ONE);
        var c =
                new TickCircuit(
                        StandardCircuit.create(ServerSettings.circuitRules(), false)
                                .withInputAmount(1, 10000 * Fixed.ONE));
        c.start();
        c.tick(pool);
        h.assertTrue(
                pool.available() == 56 * Fixed.ONE
                        && pool.snapshot().spent() == 0
                        && !c.powered()
                        && c.failures() == 1,
                "真实玩家余额56时请求10000必须整次失败，不能偷偷降低输入");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void storageAndTimingSurviveNativeItemNbt(GameTestHelper h) {
        var wand = new ItemStack(ElementalCircuits.WAND.get());
        var a = CarrierRepository.load(wand);
        a.replace(1, StandardCircuit.create(ServerSettings.circuitRules(), true));
        var c = a.circuit(1);
        var pool = new ManaPool(64 * Fixed.ONE, 0, 0);
        c.start();
        for (int i = 0; i < 3; i++) c.tick(pool);
        c.stop();
        CarrierRepository.commit(wand);
        var encoded = wand.save(h.getLevel().registryAccess(), new net.minecraft.nbt.CompoundTag());
        var copy =
                ItemStack.parseOptional(
                        h.getLevel().registryAccess(), (net.minecraft.nbt.CompoundTag) encoded);
        var resumed = CarrierRepository.load(copy).circuit(1);
        h.assertTrue(
                resumed.snapshot().version() == 14
                        && resumed.held() == c.held()
                        && resumed.snapshot().local()[1] != null,
                "存储状态通过真实物品组件与NBT恢复");
        h.assertTrue(
                resumed.definition().rune(2).storage().equals(StorageSettings.DEFAULT),
                "存储参数独立完整保存");
        var triggered = TriggeredCircuit.plan(resumed.definition(), 8 * Fixed.ONE, 40);
        h.assertTrue(
                triggered.version() == 5
                        && triggered.rune(2).storage().equals(StorageSettings.DEFAULT),
                "派生法阵也可包含存储符文");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void configuredDefaultInputDoesNotInheritLegacyBufferCap(GameTestHelper h) {
        double injection = ServerSettings.get("circuit.defaultInjection"),
                capacity = ServerSettings.get("circuit.nodeCapacity");
        try {
            ServerSettings.set("circuit.nodeCapacity", 64);
            ServerSettings.set("circuit.defaultInjection", 10000);
            var r = ServerSettings.circuitRules();
            h.assertTrue(
                    r.injection() == 10000 * Fixed.ONE
                            && r.nodeCapacity() == 64 * Fixed.ONE
                            && r.casting().explicitStorage(),
                    "全局默认输入也不能被历史64缓冲上限截断或退回旧协议");
        } finally {
            ServerSettings.set("circuit.defaultInjection", injection);
            ServerSettings.set("circuit.nodeCapacity", capacity);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void standardConfigurationActuallyChangesBudgetAndTiming(GameTestHelper h) {
        var c = new TickCircuit(StandardCircuit.create(ServerSettings.circuitRules(), false));
        h.assertTrue(
                c.complexity().nodes() == 5
                        && c.castingDelay(Fixed.ONE) == ServerSettings.count("standard.delayTicks"),
                "标准参考阵准备时间与服务器配置一致");
        h.assertTrue(c.definition().rules().casting().retention(62259) > 62259, "原生默认新旧基板采用低损失策略");
        h.succeed();
    }
}
