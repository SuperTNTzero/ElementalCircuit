package dev.elementalcircuits.client;

import dev.elementalcircuits.*;
import dev.elementalcircuits.adapter.config.ServerSettings;
import dev.elementalcircuits.adapter.persistence.*;
import dev.elementalcircuits.adapter.resources.WandResources;
import dev.elementalcircuits.client.render.WandPreview;
import dev.elementalcircuits.client.screen.*;
import dev.elementalcircuits.domain.Fixed;
import dev.elementalcircuits.domain.circuit.Rules;
import dev.elementalcircuits.domain.equipment.WandParts;
import dev.elementalcircuits.domain.examples.StandardCircuit;
import dev.elementalcircuits.server.mana.PlayerMana;
import dev.elementalcircuits.server.workshop.InscriptionAccess;

import net.minecraft.client.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.*;

/** Isolated native server trip, baked model loading, client status and network repair. */
@net.neoforged.fml.common.EventBusSubscriber(modid = ElementalCircuits.ID, value = Dist.CLIENT)
public final class BoardVisualSmoke {
    private static int stage = -1, ticks, observedBrokenTicks;
    private static boolean injected, opened, fired;
    private static volatile boolean editorRequested, broken, repaired;

    private static boolean enabled() {
        return Boolean.getBoolean("elementalcircuits.boardSmoke");
    }

    @SubscribeEvent
    public static void server(LevelTickEvent.Post event) {
        if (!enabled()
                || !(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level)
                || level.dimension() != Level.OVERWORLD
                || level.players().isEmpty()) return;
        var p = level.players().getFirst();
        if (!injected) {
            injected = true;
            p.setGameMode(GameType.SURVIVAL);
            p.connection.teleport(.5, -60, -2.5, 0, 5);
            level.setDayTime(6000);
            p.getInventory().selected = 0;
            var wand = new ItemStack(ElementalCircuits.WAND.get());
            var a = CarrierRepository.load(wand);
            a.replace(
                    1,
                    StandardCircuit.create(
                                    ServerSettings.circuitRules().withMaterial(Rules.Material.WOOD),
                                    false)
                            .withInputAmount(1, 512 * Fixed.ONE));
            a.addLayer(1, 1);
            CarrierRepository.commit(wand);
            p.getInventory().setItem(0, wand);
            p.getInventory().setItem(1, new ItemStack(ElementalCircuits.WOOD_PLATE.get(), 2));
            PlayerMana.get(p).setCapacity(2048 * Fixed.ONE);
            PlayerMana.get(p).setAmount(2048 * Fixed.ONE);
            level.setBlockAndUpdate(
                    new BlockPos(4, -60, -2), ElementalCircuits.TABLE.get().defaultBlockState());
        }
        var a = CarrierRepository.load(p.getMainHandItem());
        if (!fired && p.tickCount > 85) {
            fired = true;
            a.circuit(1).start();
            CarrierRepository.commit(p.getMainHandItem());
        }
        if (!broken && a.board(1).melted()) {
            if (p.getHealth() >= p.getMaxHealth())
                throw new AssertionError("Meltdown did not hurt its caster");
            broken = true;
        }
        if (editorRequested && !opened) {
            opened = true;
            InscriptionAccess.interact(p, new BlockPos(4, -60, -2));
        }
        if (broken && !a.boardBroken(1) && p.getInventory().getItem(1).getCount() == 1) {
            if (a.circuit(1).definition().inputAmount(a.circuit(1).definition().rune(1))
                            != 512 * Fixed.ONE
                    || a.layers(1).ids().size() != 2)
                throw new AssertionError("Repair lost circuit configuration");
            repaired = true;
        }
    }

    private static void capture(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), m -> {});
    }

    private static void click(Screen screen, String prefix) {
        for (var child : screen.children())
            if (child instanceof AbstractWidget w
                    && w.getMessage().getString().startsWith(prefix)) {
                screen.mouseClicked(w.getX() + 5, w.getY() + 6, 0);
                return;
            }
        throw new AssertionError("Missing button " + prefix);
    }

    @SubscribeEvent
    public static void client(ClientTickEvent.Post event) {
        if (!enabled()) return;
        var mc = Minecraft.getInstance();
        if (mc.getOverlay() != null) {
            ticks = 0;
            return;
        }
        mc.getToasts().clear();
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
            org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 1440, 900);
            mc.resizeDisplay();
            stage = 0;
            ticks = 0;
            mc.createWorldOpenFlows()
                    .createFreshLevel(
                            "board-" + System.currentTimeMillis(),
                            new LevelSettings(
                                    "基板熔断隔离验证",
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
        if (++ticks > 1500) throw new AssertionError("Board GPU timeout stage=" + stage);
        if (stage == 0 && mc.player != null && mc.screen == null && broken && ticks > 80) {
            var status = BoardAppearance.read(mc.player.getMainHandItem());
            if (status == null || !status.melted() || status.remaining() != 0) return;
            // Native screenshots read the last rendered frame, not the current tick's new NBT.
            if (++observedBrokenTicks < 12) return;
            var inactive = mc.player.getMainHandItem();
            var healthy = inactive.copy();
            ItemData.remove(healthy, "BoardStatus");
            if (mc.getItemRenderer().getModel(inactive, mc.level, mc.player, 0)
                    == mc.getItemRenderer().getModel(healthy, mc.level, mc.player, 0))
                throw new AssertionError("Actual held model did not reflect broken board");
            capture(mc, "broken-wand-held");
            editorRequested = true;
            stage = 1;
            ticks = 0;
        } else if (stage == 1 && mc.screen instanceof CircuitScreen editor && ticks > 15) {
            mc.setScreen(new AssemblyScreen(editor, 0));
            stage = 2;
            ticks = 0;
        } else if (stage == 2 && mc.screen instanceof AssemblyScreen screen && ticks > 15) {
            capture(mc, "broken-slot-status");
            click(screen, "基板");
            stage = 3;
            ticks = 0;
        } else if (stage == 3 && mc.screen instanceof AssemblyScreen screen && ticks > 15) {
            capture(mc, "board-repair-action");
            click(screen, "修复当前基板");
            stage = 4;
            ticks = 0;
        } else if (stage == 4 && repaired && ticks > 20) {
            capture(mc, "board-repaired");
            mc.setScreen(new Gallery());
            stage = 5;
            ticks = 0;
        } else if (stage == 5 && ticks > 18) {
            capture(mc, "wand-active-and-inactive-models");
            org.slf4j.LoggerFactory.getLogger("BoardSmoke")
                    .info(
                            "BOARD_FUSE_GPU_PASS: native survival meltdown, held broken model, slot"
                                    + " status, physical plate network repair preserves layers, 54"
                                    + " baked model variants");
            stage = 6;
            ticks = 0;
        } else if (stage == 6 && ticks > 8) {
            stage = 7;
            mc.stop();
        }
    }

    private static final class Gallery extends Screen {
        private final List<ItemStack> items = new ArrayList<>();

        Gallery() {
            super(Component.literal("法杖基板 · 正常与熔断"));
            var defs = WandResources.definitions();
            var mc = Minecraft.getInstance();
            for (var h : defs.in(WandParts.Slot.HEAD))
                for (var b : defs.in(WandParts.Slot.BODY))
                    for (var t : defs.in(WandParts.Slot.TAIL)) {
                        var healthy =
                                WandPreview.stack(new WandParts.Assembly(h.id(), b.id(), t.id()));
                        var inactive = healthy.copy();
                        var status = new net.minecraft.nbt.CompoundTag();
                        status.putInt("Slot", 1);
                        status.putLong("Remaining", 0);
                        status.putLong("Maximum", 256 * Fixed.ONE);
                        status.putLong("Capacity", 128 * Fixed.ONE);
                        status.putBoolean("Melted", true);
                        ItemData.put(inactive, "BoardStatus", status);
                        var a = mc.getItemRenderer().getModel(healthy, null, null, 0);
                        var c = mc.getItemRenderer().getModel(inactive, null, null, 0);
                        if (a == c || c == mc.getModelManager().getMissingModel())
                            throw new AssertionError("Missing inactive model");
                        items.add(healthy);
                        items.add(inactive);
                    }
        }

        @Override
        public void renderBackground(GuiGraphics g, int x, int y, float p) {}

        @Override
        public void render(GuiGraphics g, int x, int y, float p) {
            g.fill(0, 0, width, height, 0xff17242b);
            g.drawCenteredString(font, title, width / 2, 15, 0xffd3b875);
            for (int i = 0; i < items.size(); i++) {
                int pair = i / 2, cx = 36 + pair % 9 * 76 + i % 2 * 32, cy = 70 + pair / 9 * 115;
                g.pose().pushPose();
                g.pose().translate(cx - 20, cy, 0);
                g.pose().scale(3, 3, 1);
                g.renderItem(items.get(i), 0, 0);
                g.pose().popPose();
            }
        }
    }
}
