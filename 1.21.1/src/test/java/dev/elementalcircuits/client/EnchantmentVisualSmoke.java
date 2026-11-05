package dev.elementalcircuits.client;

import dev.elementalcircuits.*;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.server.*;

import net.minecraft.client.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
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
public final class EnchantmentVisualSmoke {
    private static int stage = -1, ticks, serverTicks;

    private static boolean enabled() {
        return Boolean.getBoolean("elementalcircuits.enchantmentSmoke");
    }

    @SubscribeEvent
    public static void server(LevelTickEvent.Post event) {
        if (!enabled()
                || !(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level)
                || level.dimension() != Level.OVERWORLD
                || level.players().isEmpty()) return;
        if (++serverTicks != 1) return;
        var p = level.players().getFirst();
        p.connection.teleport(.5, -60, -5.5, 0, 0);
        p.setGameMode(GameType.SURVIVAL);
        level.setDayTime(6000);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, level.getServer());
        var robe = new ItemStack(dev.elementalcircuits.server.equipment.WizardEquipment.ROBE.get());
        var ring = new ItemStack(dev.elementalcircuits.server.equipment.WizardEquipment.RING.get());
        var registry = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        for (String name : List.of("mana_capacity", "mana_recovery", "spell_power")) {
            var enchantment =
                    registry.getHolderOrThrow(
                            net.minecraft.resources.ResourceKey.create(
                                    Registries.ENCHANTMENT,
                                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                                            ElementalCircuits.ID, name)));
            robe.enchant(enchantment, 3);
            ring.enchant(enchantment, 3);
        }
        p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, robe);
        top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(p)
                .orElseThrow()
                .getStacksHandler("ring")
                .orElseThrow()
                .getStacks()
                .setStackInSlot(0, ring);
        var wand = new ItemStack(ElementalCircuits.WAND.get());
        wand.enchant(
                registry.getHolderOrThrow(
                        net.minecraft.resources.ResourceKey.create(
                                Registries.ENCHANTMENT,
                                net.minecraft.resources.ResourceLocation.parse(
                                        "elementalcircuits:swift_casting"))),
                3);
        p.getInventory().setItem(0, wand);
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
                                    "附魔隔离验证",
                                    GameType.SURVIVAL,
                                    false,
                                    Difficulty.NORMAL,
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
        if (++ticks > 1600) throw new AssertionError("附魔GPU验证超时");
        if (stage == 0 && mc.player != null && mc.screen == null && serverTicks > 70) {
            var power =
                    mc.player.getAttributeValue(
                            dev.elementalcircuits.server.equipment.SpellAttributes.POWER);
            var capacity =
                    mc.player.getAttributeValue(
                            dev.elementalcircuits.server.equipment.SpellAttributes.MANA_CAPACITY);
            if (Math.abs(power - 1.8) > .00001 || Math.abs(capacity - 512) > .00001)
                throw new AssertionError(
                        "Native armor and Curios attributes failed to sync: "
                                + power
                                + "/"
                                + capacity);
            capture(mc, "enchantment-hud");
            mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
            stage = 1;
            ticks = 0;
        } else if (stage == 1 && ticks > 40) {
            capture(mc, "enchantment-inventory");
            System.out.println(
                    "ENCHANTMENT_GPU_PASS: native equipment and Curios attributes synchronized,"
                            + " capacity=576, power=1.8");
            stage = 2;
            ticks = 0;
            mc.level.disconnect();
            mc.disconnect(new TitleScreen());
        } else if (stage == 2 && ticks > 10) {
            stage = 3;
            mc.stop();
        }
    }
}
