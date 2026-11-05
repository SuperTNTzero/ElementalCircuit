package dev.elementalcircuits.client;

import com.mojang.blaze3d.platform.InputConstants;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.config.FeedbackConfig;
import dev.elementalcircuits.adapter.persistence.*;
import dev.elementalcircuits.client.input.BookClient;
import dev.elementalcircuits.client.screen.AssemblyScreen;
import dev.elementalcircuits.client.screen.CircuitScreen;
import dev.elementalcircuits.client.screen.LayerScreen;
import dev.elementalcircuits.client.screen.SpellWheelScreen;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.casting.SpellSpec;
import dev.elementalcircuits.domain.casting.TriggerBinding;
import dev.elementalcircuits.domain.circuit.MagicCircuit;
import dev.elementalcircuits.domain.circuit.Rune;
import dev.elementalcircuits.domain.combat.Element;
import dev.elementalcircuits.domain.mana.Mixture;
import dev.elementalcircuits.integration.RecursionMinecraftContracts;
import dev.elementalcircuits.server.*;
import dev.elementalcircuits.server.circuit.CarrierEditor;
import dev.elementalcircuits.server.spell.WorldSpells;
import dev.elementalcircuits.server.workshop.BookService;
import dev.elementalcircuits.server.workshop.InscriptionAccess;

import net.minecraft.client.*;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import org.lwjgl.glfw.GLFW;

import java.util.*;

/**
 * Isolated real-client input, network acknowledgement, management layout and child-world-effect
 * proof.
 */
@EventBusSubscriber(modid = ElementalCircuits.ID, value = Dist.CLIENT)
public final class RecursionVisualSmoke {
    private static int stage = -1, ticks;
    private static volatile int command, ack, selected;
    private static volatile boolean ready, water;
    private static volatile TriggerBinding.When savedCondition;
    private static volatile UUID identity;
    private static CircuitScreen editor;

    private static boolean enabled() {
        return Boolean.getBoolean("elementalcircuits.recursionSmoke");
    }

    private static void capture(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), m -> {});
    }

    private static void press(int key) {
        KeyMapping.click(InputConstants.getKey(key, 0));
    }

    private static void click(String prefix) {
        var mc = Minecraft.getInstance();
        for (var child : mc.screen.children())
            if (child instanceof AbstractWidget w
                    && w.getMessage().getString().startsWith(prefix)) {
                mc.screen.mouseClicked(w.getX() + w.getWidth() / 2.0, w.getY() + 9, 0);
                return;
            }
        throw new AssertionError("缺少控件 " + prefix);
    }

    private static void next() {
        stage++;
        ticks = 0;
    }

    @SubscribeEvent
    public static void world(LevelTickEvent.Post e) {
        if (!enabled()
                || !(e.getLevel() instanceof net.minecraft.server.level.ServerLevel level)
                || level.dimension() != Level.OVERWORLD
                || level.players().isEmpty()) return;
        var p = level.players().get(0);
        if (!ready) {
            var wand = new ItemStack(ElementalCircuits.WAND.get());
            var a = CarrierRepository.load(wand);
            a.replace(
                    1, dev.elementalcircuits.domain.RecursionContracts.child(Element.FIRE, false));
            for (Element el : new Element[] {Element.WATER, Element.LIGHT, Element.LIGHTNING}) {
                int slot = a.add(a.circuit(1).definition().rules());
                a.replace(slot, RecursionMinecraftContracts.area(el));
            }
            a.select(1);
            a.addLayer(1, 1);
            a.replaceLayer(1, 2, RecursionMinecraftContracts.area(Element.WATER));
            CarrierRepository.commit(wand);
            identity = a.identity();
            p.getInventory().selected = 0;
            p.getInventory().setItem(0, wand);
            p.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            for (int x = -4; x <= 4; x++)
                for (int z = 0; z <= 10; z++) {
                    level.setBlock(new BlockPos(x, -61, z), Blocks.STONE.defaultBlockState(), 3);
                    for (int y = -60; y <= -55; y++)
                        level.setBlock(
                                new BlockPos(x, y, z),
                                z == 7
                                        ? Blocks.STONE.defaultBlockState()
                                        : Blocks.AIR.defaultBlockState(),
                                3);
                }
            p.connection.teleport(.5, -60, .5, 0, 0);
            level.setDayTime(6000);
            ready = true;
        }
        var a = CarrierRepository.load(BookService.castCarrier(p, p.getMainHandItem()));
        selected = a.selected();
        if (command != ack) {
            switch (command) {
                case 1 -> {
                    a.select(1);
                    CarrierRepository.commit(p.getMainHandItem());
                    var station = new BlockPos(2, -60, 0);
                    level.setBlock(station, ElementalCircuits.TABLE.get().defaultBlockState(), 3);
                    InscriptionAccess.interact(p, station);
                }
                case 2 -> {
                    var book = new ItemStack(ElementalCircuits.SPELL_BOOK.get());
                    var b = CarrierRepository.load(book);
                    for (int i = 0; i < 3; i++) b.add(b.circuit(1).definition().rules());
                    b.select(1);
                    CarrierRepository.commit(book);
                    p.setItemSlot(EquipmentSlot.OFFHAND, book);
                }
                case 3 -> {
                    p.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
                    identity = CarrierRepository.load(p.getMainHandItem()).identity();
                }
                case 6 -> {
                    var own = CarrierRepository.load(p.getMainHandItem());
                    for (int i = 0; i < 10; i++) own.addLayer(4, 16);
                    own.select(4);
                    CarrierRepository.commit(p.getMainHandItem());
                    CarrierEditor.sync(
                            p, p.getMainHandItem(), null, true, "旧阵层降限保留", new UUID(0, 0), 11);
                }
                case 5 -> CarrierEditor.sync(p, p.getMainHandItem(), true, "");
                case 4 -> {
                    var own = CarrierRepository.load(p.getMainHandItem());
                    // Use a non-igniting parent: the modern one-mana water output would otherwise
                    // correctly spend its only block action extinguishing fire instead of creating
                    // the source this fixture observes.
                    var mana = Mixture.pure(16 * Fixed.ONE).convert(Element.WIND, Fixed.ONE);
                    WorldSpells.get(level)
                            .castAssembly(
                                    level,
                                    p.getUUID(),
                                    new net.minecraft.world.phys.Vec3(.5, -59.1, 2),
                                    new net.minecraft.world.phys.Vec3(0, 0, 1),
                                    own,
                                    1,
                                    new MagicCircuit.Cast(
                                            0,
                                            mana,
                                            new SpellSpec(
                                                    SpellSpec.Method.DIRECTED,
                                                    SpellSpec.Shape.SPHERE,
                                                    0,
                                                    12,
                                                    1,
                                                    0)),
                                    Fixed.ONE);
                }
                default -> throw new AssertionError("未知验证命令");
            }
            ack = command;
        }
        var saved = CarrierRepository.load(p.getMainHandItem()).layers(1).trigger(1);
        savedCondition = saved == null ? null : saved.when();
        if (command == 4)
            for (int x = -3; x <= 3; x++)
                for (int z = 0; z <= 9; z++)
                    for (int y = -60; y <= -57; y++)
                        if (level.getBlockState(new BlockPos(x, y, z)).is(Blocks.WATER))
                            water = true;
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        if (!enabled() || stage == 27) return;
        var mc = Minecraft.getInstance();
        mc.getToasts().clear();
        if (stage < 0) {
            if (mc.screen instanceof AccessibilityOnboardingScreen) {
                mc.options.onboardAccessibility = false;
                mc.setScreen(new TitleScreen());
            }
            if (++ticks > 800) throw new AssertionError("标题页超时");
            if (!(mc.screen instanceof TitleScreen) || mc.getOverlay() != null) return;
            mc.options.pauseOnLostFocus = false;
            mc.options.guiScale().set(2);
            mc.options.renderDistance().set(4);
            mc.options.simulationDistance().set(5);
            FeedbackConfig.CAMERA.set(false);
            GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 1280, 800);
            mc.resizeDisplay();
            mc.getLanguageManager().setSelected("zh_cn");
            mc.options.languageCode = "zh_cn";
            mc.reloadResourcePacks();
            stage = 0;
            ticks = 0;
            return;
        }
        if (mc.getOverlay() != null) return;
        if (++ticks > 700)
            throw new AssertionError(
                    "轮盘验证超时 stage="
                            + stage
                            + " selected="
                            + selected
                            + " command="
                            + command
                            + " ack="
                            + ack);
        switch (stage) {
            case 0 -> {
                if (ticks < 30) return;
                var settings =
                        new LevelSettings(
                                "轮盘与派生隔离验证",
                                GameType.CREATIVE,
                                false,
                                Difficulty.PEACEFUL,
                                true,
                                new GameRules(),
                                WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows()
                        .createFreshLevel(
                                "recursion-" + System.currentTimeMillis(),
                                settings,
                                new WorldOptions(20261101, false, false),
                                a ->
                                        a.registryOrThrow(Registries.WORLD_PRESET)
                                                .getHolderOrThrow(WorldPresets.FLAT)
                                                .value()
                                                .createWorldDimensions(),
                                new TitleScreen());
                next();
            }
            case 1 -> {
                if (!ready
                        || mc.player == null
                        || mc.screen != null
                        || !BookClient.carrierIdentity().equals(identity)
                        || mc.player.tickCount < 50) return;
                press(GLFW.GLFW_KEY_X);
                next();
            }
            case 2 -> {
                if (selected != 2 || ticks < 8) return;
                press(GLFW.GLFW_KEY_Z);
                next();
            }
            case 3 -> {
                if (selected != 1 || ticks < 8) return;
                press(GLFW.GLFW_KEY_R);
                next();
            }
            case 4 -> {
                if (!(mc.screen instanceof SpellWheelScreen w) || w.data() == null || ticks < 15)
                    return;
                if (w.data().slots().size() != 4) throw new AssertionError("轮盘缺少法杖槽位");
                w.mouseMoved(w.width / 2.0 + w.height * .2, w.height / 2.0);
                if (ticks < 20) return;
                capture(mc, "wheel-wand-four-slots");
                w.keyReleased(GLFW.GLFW_KEY_R, 0, 0);
                next();
            }
            case 5 -> {
                if (selected != 2 || ticks < 12) return;
                press(GLFW.GLFW_KEY_R);
                next();
            }
            case 6 -> {
                if (!(mc.screen instanceof SpellWheelScreen w) || w.data() == null || ticks < 10)
                    return;
                w.mouseMoved(w.width / 2.0, w.height / 2.0);
                w.keyReleased(GLFW.GLFW_KEY_R, 0, 0);
                next();
            }
            case 7 -> {
                if (ticks < 12) return;
                if (selected != 2) throw new AssertionError("中心取消改变槽位");
                command = 1;
                next();
            }
            case 8 -> {
                if (!(mc.screen instanceof CircuitScreen c) || ack != 1 || ticks < 12) return;
                editor = c;
                if (c.children().stream()
                        .anyMatch(
                                w ->
                                        w instanceof AbstractWidget b
                                                && (b.getMessage().getString().equals("起阵")
                                                        || b.getMessage()
                                                                .getString()
                                                                .equals("停供"))))
                    throw new AssertionError("刻印页残留运行按钮");
                capture(mc, "editor-slots-and-layers");
                c.manage(0);
                next();
            }
            case 9 -> {
                if (!(mc.screen instanceof AssemblyScreen) || ticks < 12) return;
                capture(mc, "management-four-slots");
                editor.manage(2);
                next();
            }
            case 10 -> {
                if (ticks < 12) return;
                capture(mc, "management-plate-collapsed");
                click("+ ");
                next();
            }
            case 11 -> {
                if (ticks < 12) return;
                capture(mc, "management-plate-expanded");
                editor.manage(3);
                next();
            }
            case 12 -> {
                if (ticks == 8) click("触发关系");
                if (ticks == 12) click("派生 ·");
                if (ticks == 18) {
                    boolean disabled =
                            mc.screen.children().stream()
                                    .anyMatch(
                                            c ->
                                                    c instanceof AbstractWidget w
                                                            && w.getMessage()
                                                                    .getString()
                                                                    .equals("保存阵层关系")
                                                            && !w.active);
                    if (!disabled || savedCondition != null) throw new AssertionError("未选择条件不能保存");
                    capture(mc, "management-trigger-unselected");
                    click("    定时");
                }
                if (ticks == 24) {
                    boolean delay =
                            mc.screen.children().stream()
                                    .anyMatch(
                                            c ->
                                                    c instanceof AbstractWidget w
                                                            && w.getMessage()
                                                                    .getString()
                                                                    .startsWith("延时 ·"));
                    if (!delay) throw new AssertionError("定时选择未显示独立延时");
                    capture(mc, "management-trigger-timer");
                    click("+ 条件 ·");
                    click("    命中/抵达");
                    click("保存阵层关系");
                }
                if (ticks < 32 || savedCondition != TriggerBinding.When.IMPACT) return;
                capture(mc, "management-projectile-binding");
                org.slf4j.LoggerFactory.getLogger("RecursionVisualSmoke")
                        .info(
                                "EXPLICIT_TRIGGER_GPU_PASS: overlapping plate; no start/stop;"
                                    + " direct slots and layers; empty choice blocks save; explicit"
                                    + " TIMER then IMPACT; native server save acknowledgement");
                GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 960, 720);
                mc.options.guiScale().set(3);
                mc.resizeDisplay();
                next();
            }
            case 13 -> {
                if (ticks < 12) return;
                capture(mc, "management-binding-small");
                mc.setScreen(null);
                command = 2;
                next();
            }
            case 14 -> {
                if (ack != 2 || !BookClient.carrierIdentity().equals(identity) || ticks < 15)
                    return;
                press(GLFW.GLFW_KEY_R);
                next();
            }
            case 15 -> {
                if (!(mc.screen instanceof SpellWheelScreen w) || w.data() == null || ticks < 12)
                    return;
                if (w.data().slots().size() != 8
                        || w.data().slots().stream().filter(s -> s.book()).count() != 4
                        || !w.data().selectedCarrier().equals(identity))
                    throw new AssertionError("配书后未保留杖阵选择和双方八个槽位");
                w.mouseMoved(w.width / 2.0 - 24, w.height / 2.0 - 24);
                if (ticks < 18) return;
                capture(mc, "wheel-wand-and-book-eight-slots");
                w.keyPressed(GLFW.GLFW_KEY_8, 0, 0);
                next();
            }
            case 16 -> {
                if (selected != 4 || ticks < 12) return;
                press(GLFW.GLFW_KEY_R);
                next();
            }
            case 17 -> {
                if (!(mc.screen instanceof SpellWheelScreen w) || w.data() == null || ticks < 12)
                    return;
                command = 3;
                next();
            }
            case 18 -> {
                if (ack != 3
                        || !BookClient.carrierIdentity().equals(identity)
                        || mc.screen != null
                        || selected != 1
                        || ticks < 15) return;
                GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 1280, 800);
                mc.options.guiScale().set(2);
                mc.resizeDisplay();
                command = 4;
                next();
            }
            case 19 -> {
                if (!water || ticks < 30) return;
                capture(mc, "recursive-water-world-result");
                org.slf4j.LoggerFactory.getLogger("RecursionVisualSmoke")
                        .info(
                                "RECURSION_GPU_PASS: actual Z/X; 4-slot wand wheel; dead zone"
                                    + " cancel; combined eight slots; explicit book source; stale"
                                    + " book closes wheel; wand fallback; folded plate; small GUI;"
                                    + " server projectile triggers real water");
                command = 5;
                next();
            }
            case 20 -> {
                if (!(mc.screen instanceof CircuitScreen c) || ack != 5 || ticks < 12) return;
                editor = c;
                click("槽位 2");
                next();
            }
            case 21 -> {
                if (!(mc.screen instanceof CircuitScreen c)
                        || c.slot() != 2
                        || selected != 2
                        || ticks < 12) return;
                capture(mc, "editor-direct-slot-two");
                click("槽位 1");
                next();
            }
            case 22 -> {
                if (!(mc.screen instanceof CircuitScreen c) || c.slot() != 1 || ticks < 12) return;
                click("叠阵 2");
                next();
            }
            case 23 -> {
                if (!(mc.screen instanceof CircuitScreen c) || c.layer() != 2 || ticks < 12) return;
                if (c.draftForExport().runes().stream()
                        .noneMatch(
                                r -> r.kind() == Rune.Kind.CONVERT && r.element() == Element.WATER))
                    throw new AssertionError("阵层切换载入了错误蓝图");
                capture(mc, "editor-layer-water");
                c.layers();
                next();
            }
            case 24 -> {
                if (!(mc.screen instanceof LayerScreen) || ticks < 12) return;
                capture(mc, "layer-relationship-overview");
                mc.screen.onClose();
                GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 960, 720);
                mc.options.guiScale().set(3);
                mc.resizeDisplay();
                next();
            }
            case 25 -> {
                if (!(mc.screen instanceof CircuitScreen c) || ticks < 12) return;
                for (var child : c.children())
                    if (child instanceof AbstractWidget w
                            && (w.getX() < 0
                                    || w.getY() < 0
                                    || w.getX() + w.getWidth() > c.width
                                    || w.getY() + w.getHeight() > c.height))
                        throw new AssertionError("小窗口控件越界");
                capture(mc, "editor-layer-small");
                org.slf4j.LoggerFactory.getLogger("RecursionVisualSmoke")
                        .info(
                                "PLATE_LAYERS_GPU_PASS: direct physical slot switch; local child"
                                    + " layer selection; independent relationship page; GUI2 and"
                                    + " GUI3 bounds; material depth shown");
                command = 6;
                next();
            }
            case 26 -> {
                if (!(mc.screen instanceof CircuitScreen c)
                        || c.slot() != 4
                        || c.layer() != 11
                        || ack != 6
                        || ticks < 15) return;
                if (c.children().stream()
                        .noneMatch(
                                w ->
                                        w instanceof AbstractWidget b
                                                && b.getMessage().getString().startsWith("叠阵 11")))
                    throw new AssertionError("分页未滚动到当前阵层");
                capture(mc, "editor-retained-layer-pagination");
                org.slf4j.LoggerFactory.getLogger("RecursionVisualSmoke")
                        .info(
                                "LAYER_PAGINATION_GPU_PASS: selected retained layer remains visible"
                                        + " after limits shrink");
                mc.stop();
                stage = 27;
            }
        }
    }
}
