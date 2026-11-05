package dev.elementalcircuits.client;

import dev.elementalcircuits.*;
import dev.elementalcircuits.adapter.resources.RuleResources;
import dev.elementalcircuits.adapter.resources.WandResources;
import dev.elementalcircuits.block.*;
import dev.elementalcircuits.block.InscriptionTable;
import dev.elementalcircuits.client.render.WandDeskPose;
import dev.elementalcircuits.client.render.WandPreview;
import dev.elementalcircuits.client.screen.CircuitScreen;
import dev.elementalcircuits.client.screen.WorkshopScreen;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.circuit.Blueprint;
import dev.elementalcircuits.domain.circuit.Route;
import dev.elementalcircuits.domain.circuit.Rules;
import dev.elementalcircuits.domain.circuit.Rune;
import dev.elementalcircuits.domain.equipment.WandParts;
import dev.elementalcircuits.server.*;
import dev.elementalcircuits.server.circuit.CarrierService;
import dev.elementalcircuits.server.circuit.FixedCircuitAppearance;
import dev.elementalcircuits.server.workshop.InscriptionAccess;
import dev.elementalcircuits.server.workshop.WorkshopService;

import net.minecraft.client.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Opt-in real GPU, model baking, client/server preview and workstation screen check. */
@EventBusSubscriber(modid = ElementalCircuits.ID, value = Dist.CLIENT)
public final class StationVisualSmoke {
    private static final BlockPos ALTAR = new BlockPos(-2, -60, 3), DESK = new BlockPos(1, -60, 3);
    private static volatile boolean initialized;
    private static boolean worldRequested;
    private static volatile int command, ack;
    private static int stage = -1, ticks, readyTicks;

    private static boolean enabled() {
        return Boolean.getBoolean("elementalcircuits.stationSmoke");
    }

    private static void validatePreviews(Minecraft mc) {
        var defs = WandResources.definitions();
        int count = 0;
        for (var h : defs.in(WandParts.Slot.HEAD))
            for (var b : defs.in(WandParts.Slot.BODY))
                for (var t : defs.in(WandParts.Slot.TAIL)) {
                    var a = new WandParts.Assembly(h.id(), b.id(), t.id());
                    var stack = WandPreview.stack(a);
                    var model = mc.getItemRenderer().getModel(stack, null, null, 0);
                    var fit = WandDeskPose.of(model);
                    var matrix = new com.mojang.blaze3d.vertex.PoseStack();
                    fit.apply(matrix);
                    double bottom = Double.POSITIVE_INFINITY,
                            contactMin = Double.POSITIVE_INFINITY,
                            contactMax = Double.NEGATIVE_INFINITY;
                    for (var vertex : WandDeskPose.vertices(model)) {
                        var v =
                                matrix.last()
                                        .pose()
                                        .transformPosition(
                                                new org.joml.Vector3f(
                                                        (float) vertex.x - .5f,
                                                        (float) vertex.y - .5f,
                                                        (float) vertex.z - .5f));
                        double plane =
                                WorkstationGeometry.desk().surfaceY()
                                        + (.5 - v.z) * Math.tan(Math.toRadians(12));
                        bottom = Math.min(bottom, v.y - plane);
                        if (v.y - plane < .004) {
                            contactMin = Math.min(contactMin, v.x);
                            contactMax = Math.max(contactMax, v.x);
                        }
                        if (v.y < plane + .002
                                || v.y > WorkstationGeometry.desk().ceiling() + .00001
                                || v.x < .05
                                || v.x > 1.95
                                || v.z < .05
                                || v.z > .95)
                            throw new AssertionError("法杖预览穿过桌面或越界：" + a + "/" + v);
                    }
                    if (bottom > .004 || contactMax - contactMin < .35)
                        throw new AssertionError(
                                "法杖没有稳定的两处接触："
                                        + a
                                        + "/"
                                        + bottom
                                        + "/"
                                        + (contactMax - contactMin));
                    count++;
                }
        if (count != 27) throw new AssertionError("没有覆盖全部法杖组合");
        org.slf4j.LoggerFactory.getLogger("StationVisualSmoke")
                .info(
                        "WAND_DESK_GEOMETRY_PASS: 27 actual baked models, two separated support"
                            + " contacts and every transformed vertex above the surface and inside"
                            + " one-block height");
    }

    private static void capture(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), m -> {});
    }

    @SubscribeEvent
    public static void world(LevelTickEvent.Post event) {
        if (!enabled()
                || !(event.getLevel() instanceof net.minecraft.server.level.ServerLevel l)
                || l.dimension() != Level.OVERWORLD
                || l.players().isEmpty()) return;
        var p = l.players().get(0);
        if (!initialized) {
            l.setDayTime(6000);
            l.setWeatherParameters(100000, 0, false, false);
            p.getInventory().selected = 0;
            p.getInventory().setItem(0, new ItemStack(ElementalCircuits.WAND.get()));
            p.getInventory().setItem(1, new ItemStack(ElementalCircuits.BRASS_PLATE.get()));
            p.getInventory().setItem(2, new ItemStack(ElementalCircuits.CRYSTAL_PLATE.get()));
            l.setBlock(ALTAR, ElementalCircuits.TABLE.get().defaultBlockState(), 3);
            l.setBlock(DESK, ElementalCircuits.WAND_WORKBENCH.get().defaultBlockState(), 3);
            ElementalCircuits.WAND_WORKBENCH
                    .get()
                    .setPlacedBy(
                            l,
                            DESK,
                            l.getBlockState(DESK),
                            p,
                            new ItemStack(ElementalCircuits.WORKBENCH_ITEM.get()));
            p.connection.teleport(.6, -60, 7.2f, 180, 12);
            initialized = true;
        }
        if (command == ack) return;
        switch (command) {
            case 1 ->
                    ((WorkstationBlockEntity) l.getBlockEntity(DESK)).show(p, p.getMainHandItem());
            case 2 -> WorkshopService.open(p, DESK);
            case 3 -> {
                WorkshopService.request(
                        p, CarrierService.identity(p.getMainHandItem()), DESK.asLong(), 3, "");
                InscriptionAccess.interact(p, ALTAR);
            }
            case 4, 5 -> {
                CarrierService.forget(p.getUUID());
                CarrierService.request(
                        p,
                        CarrierService.identity(p.getMainHandItem()),
                        1,
                        4,
                        command == 4 ? "BRASS" : "CRYSTAL");
            }
            case 6 -> {
                InscriptionAccess.close(p);
                p.getInventory().setItem(0, ItemStack.EMPTY);
            }
            case 7 -> {
                var w = new ItemStack(ElementalCircuits.WAND.get());
                p.getInventory().setItem(0, w);
                CarrierService.assembly(w)
                        .replace(
                                1,
                                Blueprint.empty(
                                        RuleResources.defaults()
                                                .withMaterial(Rules.Material.CRYSTAL)));
                CarrierService.persist(w);
                ((WorkstationBlockEntity) l.getBlockEntity(DESK)).show(p, w);
                ((WorkstationBlockEntity) l.getBlockEntity(ALTAR)).show(p, w);
                p.connection.teleport(2, -60, 5.9f, 180, 22);
            }
            case 8 -> p.connection.teleport(-1.5, -60, 5.6f, 180, 24);
            case 9 -> {
                InscriptionAccess.close(p);
                ((WorkstationBlockEntity) l.getBlockEntity(DESK)).clear();
                int i = 0;
                for (var material : Rules.Material.values()) {
                    var pos = new BlockPos(-2 + 2 * i++, -60, 0);
                    var stack = new ItemStack(ElementalCircuits.CIRCUIT_ITEM.get());
                    CarrierService.assembly(stack)
                            .replace(
                                    1,
                                    dev.elementalcircuits.domain.AssemblyContracts.line(
                                                    false,
                                                    Route.Payload.MANA,
                                                    Rune.Kind.INPUT,
                                                    Rune.Kind.OUTPUT)
                                            .withMaterial(material));
                    CarrierService.persist(stack);
                    FixedCircuitAppearance.update(stack);
                    l.setBlock(
                            pos,
                            ElementalCircuits.CIRCUIT_BLOCK
                                    .get()
                                    .defaultBlockState()
                                    .setValue(CircuitBlock.MATERIAL, BoardMaterial.of(material)),
                            3);
                    ((CircuitBlockEntity) l.getBlockEntity(pos)).placed(stack, p);
                }
                p.getInventory().setItem(0, new ItemStack(ElementalCircuits.WOOD_PLATE.get(), 2));
                p.connection.teleport(.5, -58.7, 4.1f, 180, 40);
            }
            case 10 -> p.connection.teleport(-1.5, -60, 5.6f, 180, 30);
            case 11 -> p.connection.teleport(1.5, -60, 5.6f, 180, 30);
            case 12 -> InscriptionAccess.interact(p, ALTAR);
            case 13 -> {
                if (p.getMainHandItem().getCount() != 1
                        || !p.getMainHandItem().is(ElementalCircuits.WOOD_PLATE.get())
                        || p.getInventory().countItem(ElementalCircuits.CIRCUIT_ITEM.get()) != 1
                        || !InscriptionAccess.draft(p).isEmpty())
                    throw new AssertionError("实际完成按钮未按一次刻印消耗并交付");
            }

            case 14, 15, 16 ->
                    p.connection.teleport(-1.5 + 2 * (command - 14), -60, 1.85f, 180, 47);
            case 17 -> p.connection.teleport(.5, -59, 8.8f, 180, 22);
            default -> throw new AssertionError("Unexpected smoke command");
        }
        ack = command;
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!enabled()) return;
        var mc = Minecraft.getInstance();
        if (stage < 0) {
            if (mc.screen instanceof AccessibilityOnboardingScreen) {
                mc.options.onboardAccessibility = false;
                mc.setScreen(new TitleScreen());
            }
            if (!(mc.screen instanceof TitleScreen) || mc.getOverlay() != null) return;
            mc.options.pauseOnLostFocus = false;
            mc.options.guiScale().set(2);
            mc.options.renderDistance().set(4);
            mc.options.simulationDistance().set(5);
            org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 1440, 900);
            mc.resizeDisplay();
            mc.getLanguageManager().setSelected("zh_cn");
            mc.options.languageCode = "zh_cn";
            mc.reloadResourcePacks();
            stage = 0;
            ticks = 0;
            return;
        }
        if (mc.getOverlay() != null) return;
        if (++ticks > 1500) throw new AssertionError("工作台 GPU 验证超时，阶段 " + stage);
        if (stage == 0 && ticks > 40 && !worldRequested) {
            worldRequested = true;
            var settings =
                    new LevelSettings(
                            "工作台隔离验证",
                            GameType.CREATIVE,
                            false,
                            Difficulty.PEACEFUL,
                            true,
                            new GameRules(),
                            WorldDataConfiguration.DEFAULT);
            mc.createWorldOpenFlows()
                    .createFreshLevel(
                            "stations-" + System.currentTimeMillis(),
                            settings,
                            new WorldOptions(20261031, false, false),
                            a ->
                                    a.registryOrThrow(Registries.WORLD_PRESET)
                                            .getHolderOrThrow(WorldPresets.FLAT)
                                            .value()
                                            .createWorldDimensions(),
                            new TitleScreen());
            return;
        }
        if (stage == 21 && ticks > 10) {
            mc.stop();
            return;
        }
        if (!initialized || mc.level == null || mc.player == null || ticks < 45 || command != ack)
            return;
        if (stage == 0 && mc.screen == null) {
            if (mc.player.tickCount < 80 || ++readyTicks < 30) return;
            validatePreviews(mc);
            for (var facing : Direction.Plane.HORIZONTAL)
                for (var part : net.minecraft.world.level.block.state.properties.BedPart.values()) {
                    var state =
                            ElementalCircuits.WAND_WORKBENCH
                                    .get()
                                    .defaultBlockState()
                                    .setValue(WandWorkbenchBlock.FACING, facing)
                                    .setValue(WandWorkbenchBlock.PART, part);
                    var actual = mc.getBlockRenderer().getBlockModel(state);
                    var vertices = WandDeskPose.vertices(actual);
                    if (vertices.size() < 40)
                        throw new AssertionError("工作台半边缺少实体模型：" + facing + "/" + part);
                    for (var v : vertices)
                        if (v.x < -.0001
                                || v.x > 1.0001
                                || v.y < -.0001
                                || v.y > 1.0001
                                || v.z < -.0001
                                || v.z > 1.0001)
                            throw new AssertionError(
                                    "工作台静态几何跨越其区块占位：" + facing + "/" + part + "/" + v);
                }
            var model = mc.getBlockRenderer().getBlockModel(mc.level.getBlockState(ALTAR));
            int quads = 0;
            var random = net.minecraft.util.RandomSource.create(1);
            for (Direction d : Direction.values()) quads += model.getQuads(null, d, random).size();
            quads += model.getQuads(null, null, random).size();
            if (quads < 100) throw new AssertionError("刻阵台没有烘焙 Blender OBJ：" + quads);
            capture(mc, "station-01-empty");
            command = 1;
            stage = 1;
            ticks = 0;
            return;
        }
        if (stage == 1 && mc.screen == null) {
            if (!(mc.level.getBlockEntity(DESK) instanceof WorkstationBlockEntity b)
                    || b.parts() == null) throw new AssertionError("桌面部件预览未同步到客户端");
            capture(mc, "station-02-wand-preview");
            command = 2;
            stage = 2;
            ticks = 0;
            return;
        }
        if (stage == 2 && mc.screen instanceof WorkshopScreen) {
            capture(mc, "station-03-workshop-screen");
            mc.setScreen(null);
            command = 3;
            stage = 3;
            ticks = 0;
            return;
        }
        if (stage == 3 && mc.screen instanceof CircuitScreen) {
            capture(mc, "station-04-inscription-screen");
            mc.setScreen(null);
            stage = 4;
            ticks = 0;
            return;
        }
        if (stage >= 4 && stage <= 6 && mc.screen == null) {
            var expected =
                    stage == 4
                            ? BoardMaterial.WOOD
                            : stage == 5 ? BoardMaterial.BRASS : BoardMaterial.CRYSTAL;
            if (mc.level.getBlockState(ALTAR).getValue(InscriptionTable.BOARD) != expected)
                throw new AssertionError("基板材质预览未同步：" + expected);
            capture(mc, "station-0" + (stage + 1) + "-" + expected.getSerializedName());
            command = stage == 4 ? 4 : stage == 5 ? 5 : 6;
            stage++;
            ticks = 0;
            return;
        }
        if (stage == 7 && mc.screen == null) {
            if (((WorkstationBlockEntity) mc.level.getBlockEntity(DESK)).parts() != null
                    || mc.level.getBlockState(ALTAR).getValue(InscriptionTable.BOARD)
                            != BoardMaterial.EMPTY) throw new AssertionError("结束会话后工作台预览未清空");
            capture(mc, "station-08-cleared");
            mc.options.hideGui = true;
            command = 7;
            stage = 8;
            ticks = 0;
            return;
        }
        if (stage == 8 && mc.screen == null) {
            capture(mc, "station-09-desk-detail");
            command = 8;
            stage = 9;
            ticks = 0;
            return;
        }
        if (stage == 9 && mc.screen == null) {
            capture(mc, "station-10-altar-detail");
            command = 9;
            stage = 10;
            ticks = 0;
            return;
        }
        if (stage == 10 && mc.screen == null) {
            for (var material :
                    new BoardMaterial[] {
                        BoardMaterial.WOOD, BoardMaterial.BRASS, BoardMaterial.CRYSTAL
                    })
                for (var facing : Direction.Plane.HORIZONTAL) {
                    var state =
                            ElementalCircuits.CIRCUIT_BLOCK
                                    .get()
                                    .defaultBlockState()
                                    .setValue(CircuitBlock.MATERIAL, material)
                                    .setValue(CircuitBlock.FACING, facing);
                    var actual = mc.getBlockRenderer().getBlockModel(state);
                    var vs = WandDeskPose.vertices(actual);
                    if (vs.size() < 200) throw new AssertionError("固定法阵没有烘焙新六角阵盘：" + material);
                    for (var v : vs)
                        if (v.x < 0 || v.x > 1 || v.y < 0 || v.y > .221 || v.z < 0 || v.z > 1)
                            throw new AssertionError("阵盘几何越界：" + v);
                }
            capture(mc, "fixed-01-three-materials");
            mc.options.hideGui = false;
            command = 10;
            stage = 11;
            ticks = 0;
            return;
        }
        if (stage == 11 && mc.screen == null) {
            if (!(mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit)
                    || !hit.getBlockPos().equals(ALTAR)) throw new AssertionError("选框截图未瞄准刻阵台");
            capture(mc, "fixed-02-altar-outline");
            command = 11;
            stage = 12;
            ticks = 0;
            return;
        }
        if (stage == 12 && mc.screen == null) {
            if (!(mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit)
                    || !hit.getBlockPos().equals(DESK)) throw new AssertionError("选框截图未瞄准法杖台");
            capture(mc, "fixed-03-desk-outline");
            command = 12;
            stage = 13;
            ticks = 0;
            return;
        }
        if (stage == 13 && mc.screen instanceof CircuitScreen screen) {
            if (!mc.player.getMainHandItem().is(ElementalCircuits.WOOD_PLATE.get())
                    || mc.player.getMainHandItem().getCount() != 2)
                throw new AssertionError("进入草稿时提前扣除了基板");
            capture(mc, "fixed-04-blank-draft");
            screen.acceptImported(
                    dev.elementalcircuits.domain.AssemblyContracts.line(
                            false, Route.Payload.MANA, Rune.Kind.INPUT, Rune.Kind.OUTPUT));
            stage = 14;
            ticks = 0;
            return;
        }
        if (stage == 14 && mc.screen instanceof CircuitScreen screen) {
            capture(mc, "fixed-05-ready-to-engrave");
            boolean clicked = false;
            for (var widget : screen.children())
                if (widget instanceof net.minecraft.client.gui.components.Button b
                        && b.getMessage().getString().equals("完成刻印")) {
                    b.onPress();
                    clicked = true;
                    break;
                }
            if (!clicked) throw new AssertionError("草稿界面缺少完成刻印按钮");
            stage = 15;
            ticks = 0;
            return;
        }
        if (stage == 15 && ticks > 60 && mc.screen instanceof CircuitScreen failed) {
            capture(mc, "fixed-debug-reply");
            throw new AssertionError("完成刻印未关闭：" + failed.message());
        }
        if (stage == 15 && mc.screen == null) {
            command = 13;
            stage = 16;
            ticks = 0;
            return;
        }
        if (stage == 16 && mc.screen == null) {
            capture(mc, "fixed-06-delivered");
            mc.options.hideGui = true;
            command = 14;
            stage = 17;
            ticks = 0;
            return;
        }
        if (stage >= 17 && stage <= 19 && mc.screen == null) {
            capture(mc, "array-detail-" + new String[] {"wood", "brass", "crystal"}[stage - 17]);
            command = stage == 17 ? 15 : stage == 18 ? 16 : 17;
            stage++;
            ticks = 0;
            return;
        }
        if (stage == 20 && mc.screen == null) {
            capture(mc, "array-detail-distance");
            stage = 21;
            ticks = 0;
            org.slf4j.LoggerFactory.getLogger("StationVisualSmoke")
                    .info(
                            "WORKSTATION_GPU_PASS: all 12 fixed-array models, three material"
                                    + " closeups and distance, simple outlines, actual completion"
                                    + " button and 27 wand poses");
            mc.level.disconnect();
            mc.disconnect(new TitleScreen());
        }
    }
}
