package dev.elementalcircuits.client;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.codec.JsonCodecs;
import dev.elementalcircuits.adapter.resources.WandResources;
import dev.elementalcircuits.client.editor.EditorLayout;
import dev.elementalcircuits.client.render.*;
import dev.elementalcircuits.client.screen.CircuitScreen;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.circuit.Route;
import dev.elementalcircuits.domain.circuit.Rune;
import dev.elementalcircuits.network.CircuitNetwork;
import dev.elementalcircuits.server.*;
import dev.elementalcircuits.server.mana.PlayerMana;

import net.minecraft.client.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.*;

/** Opt-in native widget, texture, eating and HUD check; isolated world, never shipped. */
@EventBusSubscriber(modid = ElementalCircuits.ID, value = Dist.CLIENT)
public final class ManaProgressionVisualSmoke {
    private static int stage = -1, ticks;
    private static volatile int command, ack;
    private static volatile boolean initialized;
    private static boolean eating;
    private static CircuitScreen editor;

    private static boolean enabled() {
        return Boolean.getBoolean("elementalcircuits.progressionSmoke");
    }

    private static void capture(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), m -> {});
    }

    private static final class RecipePreview extends Screen {
        RecipePreview() {
            super(Component.literal("魔力果实"));
        }

        @Override
        public void renderBackground(GuiGraphics g, int x, int y, float partial) {}

        private void item(GuiGraphics g, ItemStack stack, int x, int y, int size) {
            g.pose().pushPose();
            g.pose().translate(x, y, 0);
            g.pose().scale(size / 16f, size / 16f, 1);
            g.renderItem(stack, 0, 0);
            g.pose().popPose();
        }

        @Override
        public void render(GuiGraphics g, int x, int y, float partial) {
            WorkshopSkin.background(g, width, height);
            int left = width / 2 - 210;
            g.drawString(font, title, left, 38, WorkshopSkin.GOLD, false);
            item(g, new ItemStack(Items.APPLE), left, 90, 40);
            g.drawString(font, "+", left + 52, 106, WorkshopSkin.TEXT, false);
            for (int i = 0; i < 4; i++)
                item(
                        g,
                        new ItemStack(ElementalCircuits.MANA_CRYSTAL.get()),
                        left + 78 + i * 43,
                        90,
                        32);
            g.drawString(font, "→", left + 264, 106, WorkshopSkin.GOLD, false);
            item(g, new ItemStack(ElementalCircuits.MANA_FRUIT.get()), left + 302, 76, 64);
            g.drawString(font, "1个苹果 + 4块魔力水晶", left, 164, WorkshopSkin.TEXT, false);
            g.drawString(font, "永久增加16点魔力容量 · 上限1024", left, 188, WorkshopSkin.GOLD, false);
            g.drawString(font, "新增容量随时间恢复，满级后不再消耗果实", left, 212, WorkshopSkin.TEXT, false);
        }
    }

    private static long capacity() {
        try {
            var f = ClientHooks.class.getDeclaredField("capacity");
            f.setAccessible(true);
            return f.getLong(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void selectInput(CircuitScreen screen) {
        var layout = EditorLayout.of(screen.width, screen.height, 4);
        var cell = screen.draftForExport().rune(1).cell();
        screen.mouseClicked(layout.x(cell), layout.y(cell), 0);
        screen.mouseReleased(layout.x(cell), layout.y(cell), 0);
    }

    private static void amount(String value) {
        EditBox field =
                editor.children().stream()
                        .filter(EditBox.class::isInstance)
                        .map(EditBox.class::cast)
                        .findFirst()
                        .orElseThrow();
        field.setValue(value);
        Button button =
                editor.children().stream()
                        .filter(Button.class::isInstance)
                        .map(Button.class::cast)
                        .filter(b -> b.getMessage().getString().equals("设置每次注入量"))
                        .findFirst()
                        .orElseThrow();
        button.onPress();
    }

    @SubscribeEvent
    public static void world(LevelTickEvent.Post event) {
        if (!enabled()
                || !(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level)
                || level.dimension() != Level.OVERWORLD
                || level.players().isEmpty()) return;
        var p = level.players().get(0);
        if (!initialized) {
            p.getInventory().selected = 0;
            p.getInventory().setItem(0, new ItemStack(ElementalCircuits.MANA_FRUIT.get(), 2));
            p.getInventory().setItem(1, new ItemStack(ElementalCircuits.WAND.get()));
            p.connection.teleport(.5, -60, .5, 0, 0);
            level.setDayTime(6000);
            PlayerMana.get(p).debit(10 * Fixed.ONE);
            initialized = true;
        }
        if (command <= ack) return;
        if (command == 1) {
            var fruit = new ItemStack(ElementalCircuits.MANA_FRUIT.get(), 64);
            for (int i = 0; i < 59; i++) fruit.getItem().finishUsingItem(fruit, level, p);
            if (PlayerMana.get(p).capacity() != 1024 * Fixed.ONE || fruit.getCount() != 5)
                throw new AssertionError("食用60颗后的真实上限或消费数错误");
            p.getInventory().setItem(0, fruit);
            var state = PlayerMana.get(p).snapshot();
            PlayerMana.forget(p);
            if (!PlayerMana.get(p).snapshot().equals(state)) throw new AssertionError("永久容量加载不一致");
            fruit.getItem().finishUsingItem(fruit, level, p);
            if (fruit.getCount() != 5) throw new AssertionError("已满级仍消耗果实");
        }
        ack = command;
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!enabled()) return;
        var mc = Minecraft.getInstance();
        mc.getToasts().clear();
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
            org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 1280, 800);
            mc.resizeDisplay();
            mc.getLanguageManager().setSelected("zh_cn");
            mc.options.languageCode = "zh_cn";
            mc.reloadResourcePacks();
            mc.setScreen(new RecipePreview());
            stage = 0;
            ticks = 0;
            return;
        }
        if (mc.getOverlay() != null) return;
        if (++ticks > 1200)
            throw new AssertionError("魔力成长图形验证超时，stage=" + stage + ",cap=" + capacity());
        if (stage == 0 && ticks > 40) {
            var fruit = new ItemStack(ElementalCircuits.MANA_FRUIT.get());
            var model = mc.getItemRenderer().getModel(fruit, null, null, 0);
            if (model == mc.getModelManager().getMissingModel()
                    || model.getParticleIcon()
                            == mc.getModelManager().getMissingModel().getParticleIcon())
                throw new AssertionError("魔力果实纹理未烘焙");
            capture(mc, "mana-fruit-recipe");
            var b =
                    AssemblyContracts.line(
                            false,
                            Route.Payload.MANA,
                            Rune.Kind.INPUT,
                            Rune.Kind.CONVERT,
                            Rune.Kind.OUTPUT);
            editor =
                    new CircuitScreen(
                            new CircuitNetwork.Editor(
                                    UUID.randomUUID(),
                                    JsonCodecs.blueprint(b),
                                    false,
                                    0,
                                    false,
                                    "选中输入符文，设置每次注入量",
                                    WandResources.definitions().basic(),
                                    0));
            mc.setScreen(editor);
            selectInput(editor);
            stage = 1;
            ticks = 0;
            return;
        }
        if (stage == 1 && ticks > 15) {
            amount("8");
            if (editor.draftForExport().inputAmount(editor.draftForExport().rune(1))
                    != 8 * Fixed.ONE) throw new AssertionError("原生参数按钮未应用输入值");
            stage = 2;
            ticks = 0;
            return;
        }
        if (stage == 2 && ticks > 12) {
            capture(mc, "mana-input-quantity");
            amount("0");
            if (editor.draftForExport().inputAmount(editor.draftForExport().rune(1))
                    != 8 * Fixed.ONE) throw new AssertionError("非法输入修改了草稿");
            stage = 3;
            ticks = 0;
            var settings =
                    new LevelSettings(
                            "魔力果实隔离验证",
                            GameType.SURVIVAL,
                            false,
                            Difficulty.PEACEFUL,
                            true,
                            new GameRules(),
                            WorldDataConfiguration.DEFAULT);
            mc.createWorldOpenFlows()
                    .createFreshLevel(
                            "progression-" + System.currentTimeMillis(),
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
        if (stage == 3) {
            if (mc.level == null
                    || mc.player == null
                    || mc.screen != null
                    || !initialized
                    || mc.player.tickCount < 20
                    || !mc.player.getMainHandItem().is(ElementalCircuits.MANA_FRUIT.get())) return;
            if (!eating) {
                if (capacity() != 64 * Fixed.ONE) return;
                mc.options.keyUse.setDown(true);
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                eating = true;
            }
            if (mc.player.isUsingItem() && mc.player.getTicksUsingItem() == 16)
                capture(mc, "mana-fruit-eating");
            if (capacity() == 80 * Fixed.ONE && mc.player.getMainHandItem().getCount() == 1) {
                mc.options.keyUse.setDown(false);
                mc.gameMode.releaseUsingItem(mc.player);
                capture(mc, "mana-fruit-capacity-80");
                command = 1;
                stage = 4;
                ticks = 0;
            }
            return;
        }
        if (stage == 4 && ack == 1 && capacity() == 1024 * Fixed.ONE && ticks > 20) {
            capture(mc, "mana-fruit-capacity-1024");
            mc.level.disconnect();
            mc.disconnect(new TitleScreen());
            stage = 5;
            ticks = 0;
            org.slf4j.LoggerFactory.getLogger("ManaProgressionVisualSmoke")
                    .info(
                            "MANA_PROGRESSION_GPU_PASS: native input widget accepts 8 and rejects"
                                + " 0, fruit atlas, actual 32-tick eating and server-authoritative"
                                + " 64/80/1024 HUD, persistent cap and no consumption at max");
            return;
        }
        if (stage == 5 && ticks > 15) {
            stage = 6;
            mc.stop();
        }
    }
}
