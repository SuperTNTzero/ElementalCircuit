package dev.elementalcircuits.client;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.config.ServerSettings;
import dev.elementalcircuits.adapter.resources.RuleResources;
import dev.elementalcircuits.adapter.resources.ShapeResources;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.casting.ReleasePermit;
import dev.elementalcircuits.domain.casting.SpellSpec;
import dev.elementalcircuits.domain.circuit.Blueprint;
import dev.elementalcircuits.domain.circuit.MagicCircuit;
import dev.elementalcircuits.domain.circuit.Route;
import dev.elementalcircuits.domain.circuit.Rune;
import dev.elementalcircuits.domain.circuit.TickCircuit;
import dev.elementalcircuits.domain.combat.DamageAffinity;
import dev.elementalcircuits.domain.combat.Element;
import dev.elementalcircuits.domain.mana.ManaOrigin;
import dev.elementalcircuits.domain.mana.ManaSource;
import dev.elementalcircuits.domain.mana.Mixture;
import dev.elementalcircuits.server.*;
import dev.elementalcircuits.server.equipment.SpellAttributes;
import dev.elementalcircuits.server.mana.PlayerMana;
import dev.elementalcircuits.server.mana.WorldManaRefunds;
import dev.elementalcircuits.server.spell.CombatEffects;
import dev.elementalcircuits.server.spell.SpellDamage;
import dev.elementalcircuits.server.spell.SpellEffects;

import net.minecraft.client.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.*;

/**
 * Native damage/health/effect transport and translated visuals in a disposable integrated world.
 */
@EventBusSubscriber(modid = ElementalCircuits.ID, value = Dist.CLIENT)
public final class CombatVisualSmoke {
    private record Sample(UUID target, String label, float health, float damage) {}

    private static int stage = -1, ticks, serverTicks;
    private static final List<Mob> targets = new ArrayList<>();
    private static volatile List<Sample> samples = List.of();
    private static volatile Component death;
    private static volatile long failedRefund;

    private static boolean enabled() {
        return Boolean.getBoolean("elementalcircuits.combatSmoke");
    }

    private static Mixture mana(Element e) {
        return Mixture.pure(Fixed.ONE).convert(e, Fixed.ONE);
    }

    private static void beam(
            net.minecraft.server.level.ServerLevel level,
            UUID owner,
            LivingEntity target,
            Mixture mixture) {
        Vec3 p = target.getBoundingBox().getCenter();
        var spec = new SpellSpec(SpellSpec.Method.DIRECTED, SpellSpec.Shape.BEAM, 0, 2, 0, 0);
        SpellEffects.castOwned(
                level,
                owner,
                p.add(0, 0, -1.2),
                new Vec3(0, 0, 1),
                UUID.randomUUID(),
                new MagicCircuit.Cast(0, mixture, spec),
                RuleResources.defaults(),
                Fixed.ONE);
    }

    @SubscribeEvent
    public static void world(LevelTickEvent.Post event) {
        if (!enabled()
                || !(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level)
                || level.dimension() != Level.OVERWORLD
                || level.players().isEmpty()) return;
        var p = level.players().getFirst();
        if (++serverTicks == 1) {
            p.setGameMode(GameType.SURVIVAL);
            p.connection.teleport(.5, -60, -3.5, 0, 5);
            level.setDayTime(6000);
            p.setItemInHand(
                    net.minecraft.world.InteractionHand.MAIN_HAND,
                    new ItemStack(ElementalCircuits.WAND.get()));
            p.addEffect(new MobEffectInstance(CombatEffects.DRENCHED, 120, 0, false, false, true));
            for (int i = 0; i < 4; i++) {
                Mob e = EntityType.PIG.create(level);
                e.setPos(-4 + i * 3, -60, 4.5);
                e.setNoAi(true);
                e.setNoGravity(true);
                e.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
                e.setHealth(100);
                e.setCustomName(
                        Component.literal(List.of("火 → 水", "水 → 雷", "雷 → 水", "火 → 寒霜 → 水").get(i)));
                e.setCustomNameVisible(true);
                level.addFreshEntity(e);
                targets.add(e);
            }
        }

        if (serverTicks == 60) {
            float[] before = new float[4];
            for (int i = 0; i < 4; i++) before[i] = targets.get(i).getHealth();
            var fire = mana(Element.FIRE);
            var water = mana(Element.WATER);
            var lightning = mana(Element.LIGHTNING);
            beam(level, p.getUUID(), targets.get(0), fire.plus(water));
            beam(level, p.getUUID(), targets.get(1), water.plus(lightning));
            beam(level, p.getUUID(), targets.get(2), lightning.plus(water));
            beam(
                    level,
                    p.getUUID(),
                    targets.get(3),
                    fire.plus(fire.inverse(Fixed.ONE)).plus(water));
            var result = new ArrayList<Sample>();
            float[] expected = {0, 3.5f, 2.5f, .5f};
            for (int i = 0; i < 4; i++) {
                var e = targets.get(i);
                float damage = before[i] - e.getHealth();
                if (damage != expected[i]) throw new AssertionError("真实光束反应伤害 " + i + "=" + damage);
                result.add(
                        new Sample(
                                e.getUUID(), e.getCustomName().getString(), e.getHealth(), damage));
            }
            death =
                    SpellDamage.source(level, p, DamageAffinity.FIRE)
                            .getLocalizedDeathMessage(targets.getFirst());
            p.getAttribute(SpellAttributes.POWER).setBaseValue(2);
            var context = SpellAttributes.context(p);
            var pool = PlayerMana.get(p);
            ManaSource source =
                    new ManaSource() {
                        public long available() {
                            return pool.available();
                        }

                        public boolean debit(long n) {
                            return pool.debit(n);
                        }

                        public ManaOrigin provenance() {
                            return new ManaOrigin("player/" + p.getUUID());
                        }
                    };
            var kinds =
                    new Rune.Kind[] {
                        Rune.Kind.INPUT,
                        Rune.Kind.CONVERT,
                        Rune.Kind.DIRECTED,
                        Rune.Kind.BEAM,
                        Rune.Kind.AIM,
                        Rune.Kind.OUTPUT
                    };
            var nodes = new ArrayList<Rune>();
            var routes = new ArrayList<Route>();
            for (int i = 0; i < kinds.length; i++) {
                var rune = Rune.create(i + 1, kinds[i], new Hex(i - 2, 0));
                if (kinds[i] == Rune.Kind.AIM)
                    rune =
                            new Rune(
                                    rune.id(),
                                    rune.kind(),
                                    rune.cell(),
                                    rune.element(),
                                    0,
                                    true,
                                    false,
                                    rune.weights());
                nodes.add(rune);
                if (i > 0)
                    routes.add(
                            new Route(
                                    i,
                                    i + 1,
                                    Route.Payload.MANA,
                                    List.of(nodes.get(i - 1).cell(), rune.cell())));
            }
            var definition = new Blueprint(5, ServerSettings.circuitRules(), nodes, routes, false);
            var small = new TickCircuit(definition.withInputAmount(1, Fixed.ONE));
            small.start();
            long beforeMana = pool.available();
            if (!small.tick(
                                    source,
                                    ReleasePermit.UNLIMITED,
                                    ManaSource.NONE,
                                    context,
                                    ShapeResources.rules())
                            .isEmpty()
                    || small.failures() != 1) throw new AssertionError("固定输入不足应失败");
            for (var refund : small.refunds()) {
                if (!WorldManaRefunds.get(level)
                        .accept(UUID.randomUUID() + "/1/" + refund.sequence(), refund.mana()))
                    throw new AssertionError("无法接受返还");
                failedRefund += refund.mana().total();
                small.acknowledgeRefund(refund.sequence());
            }
            WorldManaRefunds.get(level).tick(level);
            if (pool.available() != beforeMana - Fixed.ONE + failedRefund || failedRefund <= 0)
                throw new AssertionError("在线原来源返还错误");
            var paid = new TickCircuit(definition.withInputAmount(1, 4 * Fixed.ONE));
            paid.start();
            var casts =
                    paid.tick(
                            source,
                            ReleasePermit.UNLIMITED,
                            ManaSource.NONE,
                            context,
                            ShapeResources.rules());
            if (casts.size() != 1 || casts.getFirst().spell().damageMultiplier() != 2 * Fixed.ONE)
                throw new AssertionError("实际VM输出应冻结法强二");
            Mob powered = EntityType.PIG.create(level);
            powered.setPos(8, -60, 4.5);
            powered.setNoAi(true);
            powered.setNoGravity(true);
            powered.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
            powered.setHealth(100);
            powered.setCustomName(Component.literal("法强 2 × 实际法阵"));
            powered.setCustomNameVisible(true);
            level.addFreshEntity(powered);
            Vec3 at = powered.getBoundingBox().getCenter();
            SpellEffects.castOwned(
                    level,
                    p.getUUID(),
                    at.add(0, 0, -1.2),
                    new Vec3(0, 0, 1),
                    UUID.randomUUID(),
                    casts.getFirst(),
                    definition.rules(),
                    Fixed.ONE);
            if (powered.getHealth() != 96) throw new AssertionError("法强二实际火束应造成四点伤害");
            result.add(
                    new Sample(
                            powered.getUUID(),
                            powered.getCustomName().getString(),
                            powered.getHealth(),
                            4));
            samples = List.copyOf(result);
        }
    }

    @SubscribeEvent
    public static void hud(RenderGuiEvent.Post event) {
        if (!enabled() || samples.isEmpty()) return;
        var mc = Minecraft.getInstance();
        if (mc.screen != null) return;
        var g = event.getGuiGraphics();
        g.fill(8, 8, 390, 140, 0xd914242d);
        g.drawString(mc.font, "施法预算与元素反应", 15, 15, 0xffd8b16a, false);
        for (int i = 0; i < samples.size(); i++) {
            var s = samples.get(i);
            g.drawString(
                    mc.font,
                    s.label() + "  →  " + s.damage() + " 伤害",
                    15,
                    32 + i * 14,
                    0xffb9dbe6,
                    false);
        }
        g.drawString(
                mc.font,
                "失败返还原来源：" + Fixed.display(failedRefund) + " 魔力",
                15,
                104,
                0xffd8b16a,
                false);
        if (death != null) g.drawString(mc.font, death, 15, 118, 0xffaaa8b2, false);
    }

    private static void capture(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), m -> {});
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!enabled()) return;
        var mc = Minecraft.getInstance();
        if (stage < 0) {
            if (!(mc.screen instanceof TitleScreen)) return;
            mc.options.pauseOnLostFocus = false;
            mc.options.guiScale().set(2);
            mc.options.renderDistance().set(4);
            mc.options.simulationDistance().set(5);
            org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 1280, 800);
            mc.resizeDisplay();
            var settings =
                    new LevelSettings(
                            "元素伤害隔离验证",
                            GameType.SURVIVAL,
                            false,
                            Difficulty.NORMAL,
                            true,
                            new GameRules(),
                            WorldDataConfiguration.DEFAULT);
            stage = 0;
            ticks = 0;
            mc.createWorldOpenFlows()
                    .createFreshLevel(
                            "combat-" + System.currentTimeMillis(),
                            settings,
                            new WorldOptions(20261101, false, false),
                            a ->
                                    a.registryOrThrow(Registries.WORLD_PRESET)
                                            .getHolderOrThrow(WorldPresets.FLAT)
                                            .value()
                                            .createWorldDimensions(),
                            new TitleScreen());
            return;
        }
        if (++ticks > 1200) throw new AssertionError("元素反应GPU验证超时");
        if (stage == 0
                && mc.level != null
                && mc.player != null
                && mc.screen == null
                && serverTicks >= 65
                && !samples.isEmpty()) {
            for (var s : samples) {
                var e = mc.level.entitiesForRendering();
                boolean found = false;
                for (Entity entity : e)
                    if (entity.getUUID().equals(s.target())) {
                        if (!(entity instanceof LivingEntity living)
                                || living.getHealth() != s.health())
                            throw new AssertionError("客户端真实血量未同步");
                        found = true;
                        break;
                    }
                if (!found) return;
            }
            if (mc.player.getAttributeValue(SpellAttributes.POWER) != 2) return;
            if (!mc.player.hasEffect(CombatEffects.DRENCHED))
                throw new AssertionError("潮湿未使用原版状态同步");
            if (!death.getString().contains("火元素") || death.getString().contains("death.attack"))
                throw new AssertionError("中文死亡提示未本地化：" + death.getString());
            mc.getToasts().clear();
            capture(mc, "economy-reactions");
            stage = 1;
            ticks = 0;
            return;
        }
        if (stage == 1 && ticks > 6) {
            mc.getToasts().clear();
            org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), 1, 1);
            mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
            stage = 2;
            ticks = 0;
        }
        if (stage == 2 && ticks > 12) {
            capture(mc, "economy-status");
            org.slf4j.LoggerFactory.getLogger("CombatVisualSmoke")
                    .info(
                            "ELEMENTAL_COMBAT_GPU_PASS: four FIFO beam reactions (0 / 3.5 / 2.5 /"
                                + " 0.5), authoritative health transported to client, Chinese"
                                + " ec_damage death text, drenched status transport and icon;"
                                + " ECONOMY_GPU_PASS: native spell power 2, actual paid VM fire"
                                + " beam damage 4, insufficient fixed input rejected, online origin"
                                + " refund settled exactly once");
            stage = 3;
            ticks = 0;
            mc.level.disconnect();
            mc.disconnect(new TitleScreen());
        }
        if (stage == 3 && ticks > 8) {
            stage = 4;
            mc.stop();
        }
    }
}
