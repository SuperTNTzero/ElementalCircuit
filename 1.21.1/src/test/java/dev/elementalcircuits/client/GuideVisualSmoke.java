package dev.elementalcircuits.client;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.client.guide.*;

import net.minecraft.client.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

@net.neoforged.fml.common.EventBusSubscriber(modid = ElementalCircuits.ID, value = Dist.CLIENT)
public final class GuideVisualSmoke {
    private static int stage = -1, ticks, entry;
    private static boolean injected;
    private static volatile boolean ready;
    private static java.util.List<ResourceLocation> entries;

    private static boolean enabled() {
        return Boolean.getBoolean("elementalcircuits.guideSmoke");
    }

    @SubscribeEvent
    public static void server(LevelTickEvent.Post e) {
        if (!enabled()
                || injected
                || !(e.getLevel() instanceof net.minecraft.server.level.ServerLevel level)
                || level.dimension() != Level.OVERWORLD
                || level.players().isEmpty()) return;
        injected = true;
        var p = level.players().getFirst();
        p.setGameMode(GameType.CREATIVE);
        p.getInventory().selected = 0;
        p.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ElementalCircuits.GUIDE.get()));
        ready = true;
    }

    private static void capture(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), m -> {});
    }

    @SubscribeEvent
    public static void client(ClientTickEvent.Post e) {
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
            mc.options.renderDistance().set(3);
            org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 1440, 900);
            mc.resizeDisplay();
            stage = 0;
            ticks = 0;
            mc.createWorldOpenFlows()
                    .createFreshLevel(
                            "guide-" + System.currentTimeMillis(),
                            new LevelSettings(
                                    "指南隔离验证",
                                    GameType.CREATIVE,
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
        if (++ticks > 1800) throw new AssertionError("Guide GPU timeout stage=" + stage);
        boolean patchouli = ModList.get().isLoaded("patchouli");
        if (stage == 0
                && ready
                && mc.player != null
                && mc.player.getMainHandItem().is(ElementalCircuits.GUIDE.get())
                && mc.screen == null
                && ticks > 50) {
            var model =
                    mc.getItemRenderer()
                            .getModel(mc.player.getMainHandItem(), mc.level, mc.player, 0);
            if (model == mc.getModelManager().getMissingModel())
                throw new AssertionError("Missing guide model");
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            stage = 10;
            ticks = 0;
        } else if (stage == 10 && ticks > 60) {
            capture(mc, "guide-held-first-person");
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            stage = 11;
            ticks = 0;
        } else if (stage == 11 && ticks > 20) {
            capture(mc, "guide-held-third-person");
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            // Exercise the actual native item action, including the server packet.
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            stage = 1;
            ticks = 0;
        } else if (stage == 1 && ticks > 20) {
            if (patchouli) {
                var book =
                        vazkii.patchouli.common.book.BookRegistry.INSTANCE.books.get(
                                id("field_guide"));
                var content = book.getContents();
                if (content.isErrored())
                    throw new AssertionError("Patchouli content error", content.getException());
                if (content.categories.size() != 6
                        || content.entries.size() != 24
                        || !book.getBookItem().is(ElementalCircuits.GUIDE.get()))
                    throw new AssertionError("Wrong guide registration");
                if (!mc.screen.getClass().getName().contains("GuiBook"))
                    throw new AssertionError("Native item did not open Patchouli");
                entries =
                        content.entries.keySet().stream()
                                .sorted(java.util.Comparator.comparing(ResourceLocation::toString))
                                .toList();
            } else if (!(mc.screen instanceof GuideBookScreen))
                throw new AssertionError("Missing native fallback");
            capture(mc, patchouli ? "patchouli-contents" : "native-contents");
            stage = 2;
            ticks = 0;
        } else if (stage == 2 && ticks > 4) {
            if (patchouli && entry < entries.size()) {
                var book =
                        vazkii.patchouli.common.book.BookRegistry.INSTANCE.books.get(
                                id("field_guide"));
                var target = book.getContents().entries.get(entries.get(entry));
                for (int page = 0; page < target.getPages().size(); page += 2)
                    vazkii.patchouli.api.PatchouliAPI.get()
                            .openBookEntry(id("field_guide"), entries.get(entry), page);
                entry++;
                ticks = 0;
                return;
            }
            if (patchouli)
                vazkii.patchouli.api.PatchouliAPI.get()
                        .openBookEntry(id("field_guide"), id("beginning/first_steps"), 0);
            else {
                var pages = GuideBookScreen.pages();
                if (pages.size() < 50) throw new AssertionError("Incomplete native manual");
                for (var p : pages)
                    if (mc.font.split(p, 114).size() > 14)
                        throw new AssertionError("Native page overflow: " + p.getString());
                ((GuideBookScreen) mc.screen).setPage(6);
            }
            stage = 3;
            ticks = 0;
        } else if (stage == 3 && ticks > 20) {
            capture(mc, patchouli ? "patchouli-entry" : "native-entry");
            if (patchouli)
                vazkii.patchouli.api.PatchouliAPI.get()
                        .openBookEntry(id("field_guide"), id("field/amplification"), 2);
            else ((GuideBookScreen) mc.screen).setPage(0);
            stage = 4;
            ticks = 0;
        } else if (stage == 4 && ticks > 20) {
            capture(mc, patchouli ? "patchouli-recipe" : "native-return-to-contents");
            mc.setScreen(new Cover());
            stage = 5;
            ticks = 0;
        } else if (stage == 5 && ticks > 20) {
            capture(mc, "guide-cover");
            org.slf4j.LoggerFactory.getLogger("GuideSmoke")
                    .info(
                            "GUIDE_GPU_PASS: mode={}, native item action, model, 24 entries / 6"
                                    + " categories, recipe pages or font pagination",
                            patchouli ? "patchouli" : "native");
            mc.stop();
            stage = 6;
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ElementalCircuits.ID, path);
    }

    private static final class Cover extends Screen {
        Cover() {
            super(Component.literal("法阵手札"));
        }

        @Override
        public void render(GuiGraphics g, int x, int y, float p) {
            g.fill(0, 0, width, height, 0xff111e2a);
            g.pose().pushPose();
            g.pose().translate(width / 2 - 64, height / 2 - 90, 0);
            g.pose().scale(8, 8, 1);
            g.renderItem(new ItemStack(ElementalCircuits.GUIDE.get()), 0, 0);
            g.pose().popPose();
            g.drawCenteredString(font, "法阵手札", width / 2, height / 2 + 60, 0xffd2ac64);
            g.drawCenteredString(
                    font, "深蓝书皮 · 黄铜包角 · 水晶封印", width / 2, height / 2 + 80, 0xff9fc2cf);
        }
    }
}
