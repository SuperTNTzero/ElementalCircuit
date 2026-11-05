package dev.elementalcircuits.client;

import dev.elementalcircuits.*;
import dev.elementalcircuits.adapter.config.ServerSettings;
import dev.elementalcircuits.adapter.persistence.*;
import dev.elementalcircuits.block.ManaNodeBlockEntity;
import dev.elementalcircuits.client.screen.CircuitScreen;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.circuit.Rules;
import dev.elementalcircuits.domain.examples.StandardCircuit;
import dev.elementalcircuits.server.*;
import dev.elementalcircuits.server.equipment.SpellAttributes;
import dev.elementalcircuits.server.equipment.WizardEquipment;
import dev.elementalcircuits.server.workshop.InscriptionAccess;

import net.minecraft.client.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.*;

@net.neoforged.fml.common.EventBusSubscriber(modid = ElementalCircuits.ID, value = Dist.CLIENT)
public final class CrystalCastingVisualSmoke {
    private static int stage = -1, ticks;
    private static boolean injected, opened;
    private static volatile boolean requestEditor, saved, charged;

    private static boolean enabled() {
        return Boolean.getBoolean("elementalcircuits.crystalSmoke");
    }

    @SubscribeEvent
    public static void server(LevelTickEvent.Post e) {
        if (!enabled()
                || !(e.getLevel() instanceof net.minecraft.server.level.ServerLevel level)
                || level.dimension() != Level.OVERWORLD
                || level.players().isEmpty()) return;
        var p = level.players().getFirst();
        if (!injected) {
            injected = true;
            p.setGameMode(GameType.CREATIVE);
            p.connection.teleport(.5, -60, -6.5, 0, 12);
            level.setDayTime(6000);
            p.getInventory().selected = 0;
            var wand = new ItemStack(ElementalCircuits.WAND.get());
            var a = CarrierRepository.load(wand);
            var rules = ServerSettings.circuitRules().withMaterial(Rules.Material.CRYSTAL);
            a.replace(1, StandardCircuit.create(rules, false).withInputAmount(1, 16 * Fixed.ONE));
            for (int i = 0; i < 3; i++) a.add(rules);
            a.select(1);
            a.addLayer(1, 4);
            CarrierRepository.commit(wand);
            p.setItemSlot(EquipmentSlot.MAINHAND, wand);
            p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(WizardEquipment.HAT.get()));
            p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(WizardEquipment.ROBE.get()));
            p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(WizardEquipment.LEGGINGS.get()));
            p.setItemSlot(EquipmentSlot.FEET, new ItemStack(WizardEquipment.BOOTS.get()));
            level.setBlockAndUpdate(
                    new BlockPos(-3, -60, 0), ElementalCircuits.MANA_ORE.get().defaultBlockState());
            level.setBlockAndUpdate(
                    new BlockPos(-1, -60, 0),
                    ElementalCircuits.DEEPSLATE_MANA_ORE.get().defaultBlockState());
            for (int x : new int[] {1, 3})
                level.setBlockAndUpdate(
                        new BlockPos(x, -60, 0),
                        ElementalCircuits.MANA_CRYSTAL_BLOCK.get().defaultBlockState());
            level.setBlockAndUpdate(
                    new BlockPos(1, -59, 0),
                    ElementalCircuits.NATURAL_MANA_NODE.get().defaultBlockState());
            level.setBlockAndUpdate(
                    new BlockPos(3, -59, 0), ElementalCircuits.MANA_NODE.get().defaultBlockState());
            ((ManaNodeBlockEntity) level.getBlockEntity(new BlockPos(3, -59, 0))).insert();
            level.setBlockAndUpdate(
                    new BlockPos(5, -60, 0),
                    ElementalCircuits.MANA_CRYSTAL_BLOCK.get().defaultBlockState());
            level.setBlockAndUpdate(
                    new BlockPos(-2, -60, -4), ElementalCircuits.TABLE.get().defaultBlockState());
        }
        var a = CarrierRepository.load(p.getMainHandItem());
        if (a.circuit(1).charge() > Fixed.ONE) charged = true;
        if (requestEditor && !opened) {
            opened = true;
            a.stopAll();
            CarrierRepository.commit(p.getMainHandItem());
            InscriptionAccess.interact(p, new BlockPos(-2, -60, -4));
        }
        if (opened
                && a.circuit(1).definition().inputAmount(a.circuit(1).definition().rune(1))
                        == 20 * Fixed.ONE) saved = true;
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
            mc.options.renderDistance().set(4);
            org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 1440, 900);
            mc.resizeDisplay();
            stage = 0;
            ticks = 0;
            mc.createWorldOpenFlows()
                    .createFreshLevel(
                            "crystal-" + System.currentTimeMillis(),
                            new LevelSettings(
                                    "水晶与蓄力隔离验证",
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
        if (++ticks > 1800) throw new AssertionError("Crystal/charge GPU timeout");
        if (stage == 0 && ticks > 60 && mc.player != null && mc.screen == null) {
            if (Math.abs(mc.player.getAttributeValue(SpellAttributes.MANA_REDUCTION) - 22) > .001)
                throw new AssertionError("Robe reduction did not synchronize");
            capture(mc, "crystal-world");
            mc.options.keyUse.setDown(true);
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            stage = 1;
            ticks = 0;
        } else if (stage == 1 && ticks > 44) {
            if (!mc.player.isUsingItem())
                throw new AssertionError("Native held use ended before release");
            capture(mc, "charged-crosshair");
            mc.options.keyUse.setDown(false);
            mc.gameMode.releaseUsingItem(mc.player);
            stage = 2;
            ticks = 0;
        } else if (stage == 2 && ticks > 25 && charged) {
            requestEditor = true;
            stage = 3;
            ticks = 0;
        } else if (stage == 3 && mc.screen instanceof CircuitScreen editor && ticks > 18) {
            int count = 0;
            for (var c : editor.children())
                if (c instanceof AbstractWidget w) {
                    String t = w.getMessage().getString();
                    if (t.equals("分享") || t.equals("基板管理") || t.equals("叠阵关系")) {
                        count++;
                        if (w.getY() >= 24)
                            throw new AssertionError("Management action was not moved to header");
                    }
                    if (t.equals("保存") && w.getY() < editor.height - 70)
                        throw new AssertionError("Save left the lower-left corner");
                }
            if (count != 3) throw new AssertionError("Header actions missing");
            editor.acceptImported(editor.draftForExport().withInputAmount(1, 20 * Fixed.ONE));
            editor.setFocused(null);
            editor.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, 0, 0);
            stage = 4;
            ticks = 0;
        } else if (stage == 4 && saved && ticks > 20) {
            capture(mc, "editor-header");
            mc.setScreen(new BlockPreview());
            stage = 5;
            ticks = 0;
        } else if (stage == 5 && ticks > 18) {
            capture(mc, "crystal-block-items");
            org.slf4j.LoggerFactory.getLogger("CrystalSmoke")
                    .info(
                            "CRYSTAL_CASTING_GPU_PASS: real held right-click then release,"
                                    + " synchronized 22 percent reduction, native ore/node/crystal"
                                    + " block artwork, four-slot header actions, Enter saved draft"
                                    + " through real workstation authorization");
            stage = 6;
            ticks = 0;
        } else if (stage == 6 && ticks > 6) {
            stage = 7;
            mc.stop();
        }
    }

    private static final class BlockPreview extends Screen {
        BlockPreview() {
            super(Component.literal("魔力矿物与节点"));
        }

        @Override
        public void renderBackground(GuiGraphics g, int x, int y, float p) {}

        @Override
        public void render(GuiGraphics g, int x, int y, float p) {
            g.fill(0, 0, width, height, 0xff17242b);
            g.drawCenteredString(font, title, width / 2, 35, 0xffd3b875);
            var items =
                    List.of(
                            ElementalCircuits.MANA_ORE_ITEM,
                            ElementalCircuits.DEEPSLATE_MANA_ORE_ITEM,
                            ElementalCircuits.NATURAL_MANA_NODE_ITEM,
                            ElementalCircuits.MANA_NODE_ITEM,
                            ElementalCircuits.MANA_CRYSTAL_BLOCK_ITEM);
            String[] names = {"魔力水晶矿石", "深板岩矿石", "天然节点", "人工节点", "魔力水晶块"};
            for (int i = 0; i < items.size(); i++) {
                int cx = (2 * i + 1) * width / 10;
                g.pose().pushPose();
                g.pose().translate(cx - 40, height / 2 - 55, 0);
                g.pose().scale(5, 5, 1);
                g.renderItem(new ItemStack(items.get(i).get()), 0, 0);
                g.pose().popPose();
                g.drawCenteredString(font, names[i], cx, height / 2 + 42, 0xffbcd5db);
            }
            g.drawCenteredString(
                    font, "16×16 像素贴图 · 原版方块模型 · 深蓝水晶与黄铜", width / 2, height - 60, 0xff8cb5c4);
        }
    }
}
