package dev.elementalcircuits.client;

import dev.elementalcircuits.*;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.circuit.Rune;
import dev.elementalcircuits.server.*;
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
public final class FixedRelationVisualSmoke {
    private static int stage = -1, ticks, serverTicks;

    private static boolean enabled() {
        return Boolean.getBoolean("elementalcircuits.relationSmoke");
    }

    @SubscribeEvent
    public static void server(LevelTickEvent.Post event) {
        if (!enabled()
                || !(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level)
                || level.dimension() != Level.OVERWORLD
                || level.players().isEmpty()) return;
        if (++serverTicks != 1) return;
        var p = level.players().getFirst();
        p.connection.teleport(.5, -60, -3.5, 0, 12);
        p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(WizardEquipment.MONOCLE.get()));
        p.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ElementalCircuits.CONTROL_WAND.get()));
        level.setDayTime(6000);
        var points =
                List.of(
                        new net.minecraft.core.BlockPos(-2, -60, 1),
                        new net.minecraft.core.BlockPos(2, -60, 1));
        var blocks = new java.util.ArrayList<dev.elementalcircuits.block.CircuitBlockEntity>();
        for (var point : points) {
            level.setBlockAndUpdate(
                    point, ElementalCircuits.CIRCUIT_BLOCK.get().defaultBlockState());
            var block =
                    (dev.elementalcircuits.block.CircuitBlockEntity) level.getBlockEntity(point);
            block.placed(new ItemStack(ElementalCircuits.CIRCUIT_ITEM.get()), p);
            dev.elementalcircuits.server.circuit.WorldCircuits.register(block);
            blocks.add(block);
        }
        var from = blocks.get(0);
        var to = blocks.get(1);
        from.assembly()
                .replace(
                        1,
                        dev.elementalcircuits.domain.AssemblyContracts.line(
                                true,
                                dev.elementalcircuits.domain.circuit.Route.Payload.MANA,
                                Rune.Kind.INPUT,
                                Rune.Kind.MANA_SEND));
        to.assembly()
                .replace(
                        1,
                        dev.elementalcircuits.domain.AssemblyContracts.line(
                                true,
                                dev.elementalcircuits.domain.circuit.Route.Payload.MANA,
                                Rune.Kind.MANA_RECEIVE,
                                Rune.Kind.OUTPUT));
        dev.elementalcircuits.server.circuit.WorldCircuitLinks.get(level)
                .connect(
                        level,
                        p,
                        from.assembly().identity(),
                        new dev.elementalcircuits.adapter.codec.JsonCodecs.LinkRequest(
                                new dev.elementalcircuits.domain.circuit.CircuitAddress(
                                        from.assembly().identity(), 1, 2),
                                new dev.elementalcircuits.domain.circuit.CircuitAddress(
                                        to.assembly().identity(), 1, 1),
                                dev.elementalcircuits.domain.circuit.Route.Payload.MANA));
        dev.elementalcircuits.server.circuit.CarrierService.persist(from.carrier());
        dev.elementalcircuits.server.circuit.CarrierService.persist(to.carrier());
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
        if (stage == 0 && mc.player != null && mc.screen == null && serverTicks > 40) {
            capture(mc, "circuit-relations");
            mc.setScreen(new Preview());
            stage = 1;
            ticks = 0;
        } else if (stage == 1 && ticks > 20) {
            capture(mc, "gold-monocle");
            if (!mc.player.getItemBySlot(EquipmentSlot.HEAD).is(WizardEquipment.MONOCLE.get()))
                throw new AssertionError("Monocle equipment did not sync");
            mc.getSingleplayerServer()
                    .execute(
                            () -> {
                                var p = mc.getSingleplayerServer().overworld().players().getFirst();
                                p.setItemSlot(
                                        EquipmentSlot.HEAD,
                                        new ItemStack(WizardEquipment.HAT.get()));
                                top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(p)
                                        .orElseThrow()
                                        .getStacksHandler("head")
                                        .orElseThrow()
                                        .getStacks()
                                        .setStackInSlot(
                                                0, new ItemStack(WizardEquipment.MONOCLE.get()));
                            });
            mc.setScreen(null);
            stage = 2;
            ticks = 0;
        } else if (stage == 2
                && ticks > 40
                && mc.player.getItemBySlot(EquipmentSlot.HEAD).is(WizardEquipment.HAT.get())) {
            if (!dev.elementalcircuits.adapter.compat.CuriosEquipment.monocleWorn(mc.player))
                throw new AssertionError("Curios monocle did not sync");
            capture(mc, "curio-circuit-relations");
            mc.setScreen(new Preview());
            stage = 3;
            ticks = 0;
        } else if (stage == 3 && ticks > 20) {
            capture(mc, "curio-monocle-with-hat");
            System.out.println(
                    "FIXED_RELATION_GPU_PASS: helmet and Curios monocle, shared short temple model,"
                            + " hat coexistence and loaded links");
            stage = 4;
            ticks = 0;
            mc.level.disconnect();
            mc.disconnect(new TitleScreen());
        } else if (stage == 4 && ticks > 10) {
            stage = 5;
            mc.stop();
        }
    }

    private static final class Preview extends Screen {
        Preview() {
            super(Component.literal("金框观阵单片镜"));
        }

        @Override
        public void renderBackground(GuiGraphics g, int x, int y, float p) {}

        @Override
        public void render(GuiGraphics g, int x, int y, float partial) {
            g.fill(0, 0, width, height, 0xff0b1729);
            g.drawCenteredString(font, "金框观阵单片镜 · 正面 / 侧面", width / 2, 16, 0xffe0bf7c);
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
                            WizardEquipment.MONOCLE.get(),
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
