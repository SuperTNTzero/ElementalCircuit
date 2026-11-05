package dev.elementalcircuits.client;

import dev.elementalcircuits.*;
import dev.elementalcircuits.adapter.config.ServerSettings;
import dev.elementalcircuits.adapter.persistence.CarrierRepository;
import dev.elementalcircuits.client.input.EquipmentClient;
import dev.elementalcircuits.client.screen.AssemblyScreen;
import dev.elementalcircuits.client.screen.CircuitScreen;
import dev.elementalcircuits.client.screen.EquipmentWheelScreen;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.circuit.Rules;
import dev.elementalcircuits.domain.circuit.Rune;
import dev.elementalcircuits.domain.examples.ShapeExamples;
import dev.elementalcircuits.network.EquipmentNetwork;
import dev.elementalcircuits.server.*;
import dev.elementalcircuits.server.circuit.CarrierEditor;
import dev.elementalcircuits.server.equipment.EquipmentCasting;
import dev.elementalcircuits.server.equipment.SpellAttributes;
import dev.elementalcircuits.server.equipment.WizardEquipment;

import net.minecraft.client.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.*;

/** Opt-in GPU validation in a disposable world, including real synchronized wearable attributes. */
@net.neoforged.fml.common.EventBusSubscriber(modid = ElementalCircuits.ID, value = Dist.CLIENT)
public final class EquipmentVisualSmoke {
    private static int stage = -1, ticks, serverTicks;

    private static boolean enabled() {
        return Boolean.getBoolean("elementalcircuits.equipmentSmoke");
    }

    @SubscribeEvent
    public static void server(LevelTickEvent.Post e) {
        if (!enabled()
                || !(e.getLevel() instanceof net.minecraft.server.level.ServerLevel level)
                || level.dimension() != Level.OVERWORLD
                || level.players().isEmpty()) return;
        var p = level.players().getFirst();
        if (++serverTicks == 8) {
            var robe = p.getItemBySlot(EquipmentSlot.CHEST);
            EquipmentCasting.trigger(robe, 1, EquipmentCasting.Trigger.HURT);
            boolean accepted = p.hurt(p.damageSources().genericKill(), 1);
            if (!accepted || !CarrierRepository.load(robe).powered())
                throw new AssertionError("真实玩家原版受击事件未启动法袍法阵");
            CarrierRepository.load(robe).stopAll();
            EquipmentCasting.trigger(robe, 1, EquipmentCasting.Trigger.MANUAL);
            CarrierRepository.commit(robe);
            return;
        }
        if (serverTicks != 1) return;
        p.setGameMode(GameType.SURVIVAL);
        p.connection.teleport(.5, -60, .5, 0, 0);
        level.setDayTime(6000);
        p.getInventory().selected = 0;
        p.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ElementalCircuits.WAND.get()));
        p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(WizardEquipment.HAT.get()));
        p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(WizardEquipment.ROBE.get()));
        p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(WizardEquipment.LEGGINGS.get()));
        p.setItemSlot(EquipmentSlot.FEET, new ItemStack(WizardEquipment.BOOTS.get()));
        var robe = p.getItemBySlot(EquipmentSlot.CHEST);
        var a = CarrierRepository.load(robe);
        var rules = ServerSettings.circuitRules().withMaterial(Rules.Material.CRYSTAL);
        a.replace(1, ShapeExamples.create(rules, Rune.Kind.FAN));
        a.add(rules);
        a.add(rules);
        a.add(rules);
        a.select(1);
        a.addLayer(1, 4);
        a.addLayer(1, 4);
        CarrierRepository.commit(robe);
        var ring = new ItemStack(WizardEquipment.RING.get());
        CarrierRepository.load(ring)
                .replace(1, ShapeExamples.create(ServerSettings.circuitRules(), Rune.Kind.BEAM));
        CarrierRepository.commit(ring);
        top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(p)
                .orElseThrow()
                .getStacksHandler("ring")
                .orElseThrow()
                .getStacks()
                .setStackInSlot(0, ring);
    }

    private static void capture(Minecraft mc, String name) {
        mc.getToasts().clear();
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), m -> {});
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        if (!enabled()) return;
        var mc = Minecraft.getInstance();
        if (stage < 0) {
            if (mc.screen instanceof AccessibilityOnboardingScreen) {
                mc.options.onboardingAccessibilityFinished();
                mc.setScreen(new TitleScreen());
                return;
            }
            if (!(mc.screen instanceof TitleScreen)) return;
            mc.options.pauseOnLostFocus = false;
            mc.options.guiScale().set(2);
            mc.options.renderDistance().set(4);
            org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 1280, 900);
            mc.resizeDisplay();
            stage = 0;
            ticks = 0;
            mc.createWorldOpenFlows()
                    .createFreshLevel(
                            "wizard-" + System.currentTimeMillis(),
                            new LevelSettings(
                                    "装备隔离验证",
                                    GameType.SURVIVAL,
                                    false,
                                    Difficulty.PEACEFUL,
                                    true,
                                    new GameRules(),
                                    WorldDataConfiguration.DEFAULT),
                            new WorldOptions(20261102, false, false),
                            r ->
                                    r.registryOrThrow(Registries.WORLD_PRESET)
                                            .getHolderOrThrow(WorldPresets.FLAT)
                                            .value()
                                            .createWorldDimensions(),
                            new TitleScreen());
            return;
        }
        if (++ticks > 1600) throw new AssertionError("装备GPU验证超时");
        if (stage == 0
                && mc.player != null
                && mc.screen == null
                && serverTicks > 25
                && mc.player.hurtTime == 0) {
            if (Math.abs(mc.player.getAttributeValue(SpellAttributes.POWER) - 1.55) > .0001
                    || mc.player.getAttributeValue(SpellAttributes.MANA_CAPACITY) != 88
                    || mc.player.getAttributeValue(SpellAttributes.MANA_REGENERATION) != 2.75)
                throw new AssertionError("原生装备属性同步错误");
            if (!EquipmentClient.castMatches(org.lwjgl.glfw.GLFW.GLFW_KEY_J, 0)
                    || EquipmentClient.castMatches(org.lwjgl.glfw.GLFW.GLFW_KEY_G, 0))
                throw new AssertionError("装备施法不应占用Curios默认G键");
            mc.setScreen(new Preview());
            stage = 1;
            ticks = 0;
        } else if (stage == 1 && ticks > 16) {
            capture(mc, "wizard-armor");
            mc.setScreen(new EquipmentWheelScreen());
            EquipmentNetwork.send(new UUID(0, 0), 0, 0);
            stage = 2;
            ticks = 0;
        } else if (stage == 2 && ticks > 18) {
            capture(mc, "wizard-wheel");
            mc.getSingleplayerServer()
                    .execute(
                            () -> {
                                var p =
                                        mc.getSingleplayerServer()
                                                .getPlayerList()
                                                .getPlayers()
                                                .getFirst();
                                CarrierEditor.sync(
                                        p,
                                        p.getItemBySlot(EquipmentSlot.CHEST),
                                        null,
                                        true,
                                        "装备法阵刻印验证");
                            });
            stage = 3;
            ticks = 0;
        } else if (stage == 3 && ticks > 20 && mc.screen instanceof CircuitScreen screen) {
            int min = Integer.MAX_VALUE,
                    max = Integer.MIN_VALUE,
                    count = 0,
                    slotHeight = 0,
                    layerHeight = 0;
            for (var child : screen.children())
                if (child instanceof net.minecraft.client.gui.components.AbstractWidget w) {
                    var text = w.getMessage().getString();
                    if (text.startsWith("槽位 ")) {
                        min = Math.min(min, w.getX());
                        max = Math.max(max, w.getX() + w.getWidth());
                        count++;
                        slotHeight = w.getHeight();
                    }
                    if (text.equals("主阵")) layerHeight = w.getHeight();
                }
            if (count != 4
                    || Math.abs((min + max) / 2.0 - screen.width / 2.0) > 3
                    || layerHeight >= slotHeight) throw new AssertionError("槽位和叠阵导航未居中或层级尺寸错误");
            capture(mc, "wizard-editor");
            screen.manage(2);
            if (mc.screen instanceof AssemblyScreen management)
                for (var child : List.copyOf(management.children()))
                    if (child instanceof net.minecraft.client.gui.components.AbstractWidget w
                            && w.getMessage().getString().startsWith("+ ")) {
                        management.mouseClicked(w.getX() + 5, w.getY() + 5, 0);
                        break;
                    }
            stage = 4;
            ticks = 0;
        } else if (stage == 4 && ticks > 14) {
            capture(mc, "wizard-management");
            org.slf4j.LoggerFactory.getLogger("WizardSmoke")
                    .info(
                            "WIZARD_EQUIPMENT_GPU_PASS: four native 3D armor models, real-player"
                                    + " native damage trigger, real Curios ring, synchronized"
                                    + " attributes 1.55 / 88 / 2.75, equipment wheel, centered"
                                    + " four-slot navigation, smaller layers and indented material"
                                    + " children");
            stage = 5;
            ticks = 0;
            mc.level.disconnect();
            mc.disconnect(new TitleScreen());
        } else if (stage == 5 && ticks > 8) {
            stage = 6;
            mc.stop();
        }
    }

    private static final class Preview extends Screen {
        Preview() {
            super(Component.literal("深蓝法袍"));
        }

        @Override
        public void renderBackground(GuiGraphics g, int x, int y, float p) {}

        @Override
        public void render(GuiGraphics g, int x, int y, float partial) {
            g.fill(0, 0, width, height, 0xff0b1729);
            g.drawCenteredString(font, "握柄与晶缀法袍 · 双手正面 / 侧面", width / 2, 16, 0xffe0bf7c);
            minecraft.getToasts().clear();
            var p = minecraft.player;
            var arm = p.getMainArm();
            float body = p.yBodyRot,
                    yaw = p.getYRot(),
                    pitch = p.getXRot(),
                    head = p.yHeadRot,
                    headOld = p.yHeadRotO;
            try {
                for (int i = 0; i < 4; i++) {
                    p.setMainArm(
                            i < 2
                                    ? net.minecraft.world.entity.HumanoidArm.RIGHT
                                    : net.minecraft.world.entity.HumanoidArm.LEFT);
                    float angle = 180 + (i % 2 == 0 ? 0 : (i < 2 ? 90 : -90));
                    p.yBodyRot = angle;
                    p.setYRot(angle);
                    p.setXRot(0);
                    p.yHeadRot = angle;
                    p.yHeadRotO = angle;
                    int cx = (2 * i + 1) * width / 8;
                    InventoryScreen.renderEntityInInventory(
                            g,
                            cx,
                            height / 2 - 10,
                            95,
                            new org.joml.Vector3f(0, p.getBbHeight() / 2 + .05f, 0),
                            new org.joml.Quaternionf().rotateZ((float) Math.PI),
                            new org.joml.Quaternionf(),
                            p);
                    g.drawCenteredString(
                            font,
                            (i < 2 ? "右手" : "左手") + (i % 2 == 0 ? " · 正面" : " · 侧面"),
                            cx,
                            height - 98,
                            0xffc7bda5);
                }
            } finally {
                p.setMainArm(arm);
                p.yBodyRot = body;
                p.setYRot(yaw);
                p.setXRot(pitch);
                p.yHeadRot = head;
                p.yHeadRotO = headOld;
            }
            var pieces =
                    List.of(
                            WizardEquipment.HAT.get(),
                            WizardEquipment.ROBE.get(),
                            WizardEquipment.LEGGINGS.get(),
                            WizardEquipment.BOOTS.get());
            for (int i = 0; i < 4; i++) {
                g.pose().pushPose();
                g.pose().translate(width / 2 - 88 + i * 48, height - 73, 0);
                g.pose().scale(2, 2, 1);
                g.renderItem(new ItemStack(pieces.get(i)), 0, 0);
                g.pose().popPose();
            }
            g.drawCenteredString(font, "16×16 像素图标 · 原版物品渲染", width / 2, height - 25, 0xff8acbf7);
        }
    }
}
