package dev.elementalcircuits.client.input;

import com.mojang.blaze3d.platform.InputConstants;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.compat.CuriosEquipment;
import dev.elementalcircuits.adapter.persistence.ItemData;
import dev.elementalcircuits.client.screen.EquipmentWheelScreen;
import dev.elementalcircuits.network.EquipmentNetwork;
import dev.elementalcircuits.server.equipment.EquipmentCasting;
import dev.elementalcircuits.server.equipment.WizardEquipment;

import net.minecraft.client.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

import org.lwjgl.glfw.GLFW;

import java.util.*;

@net.neoforged.fml.common.EventBusSubscriber(modid = ElementalCircuits.ID, value = Dist.CLIENT)
public final class EquipmentClient {
    static final KeyMapping WHEEL = key("equipment_wheel", GLFW.GLFW_KEY_V);
    static final KeyMapping CAST = key("equipment_cast", GLFW.GLFW_KEY_J);
    static final KeyMapping RING = key("ring_cast", GLFW.GLFW_KEY_H);
    static final UUID ZERO = new UUID(0, 0);

    private static KeyMapping key(String name, int key) {
        return new KeyMapping(
                "key.elementalcircuits." + name,
                InputConstants.Type.KEYSYM,
                key,
                "key.categories.elementalcircuits");
    }

    @net.neoforged.fml.common.EventBusSubscriber(
            modid = ElementalCircuits.ID,
            value = Dist.CLIENT,
            bus = net.neoforged.fml.common.EventBusSubscriber.Bus.MOD)
    public static final class Bindings {
        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent e) {
            e.register(WHEEL);
            e.register(CAST);
            e.register(RING);
        }
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        var mc = Minecraft.getInstance();
        boolean allowed = mc.player != null && mc.screen == null;
        while (WHEEL.consumeClick())
            if (allowed) {
                mc.setScreen(new EquipmentWheelScreen());
                EquipmentNetwork.send(ZERO, 0, 0);
                allowed = false;
            }
        while (CAST.consumeClick()) if (allowed) EquipmentNetwork.send(ZERO, 0, 1);
        while (RING.consumeClick()) if (allowed) EquipmentNetwork.send(ZERO, 0, 4);
    }

    public static boolean wheelMatches(int key, int scan) {
        return WHEEL.matches(key, scan);
    }

    public static boolean castMatches(int key, int scan) {
        return CAST.matches(key, scan);
    }

    public static boolean wheelMouse(int button) {
        return WHEEL.matchesMouse(button);
    }

    public static void state(String json) {
        var values = new com.google.gson.Gson().fromJson(json, EquipmentCasting.Entry[].class);
        if (values == null || values.length > 128) return;
        var mc = Minecraft.getInstance();
        if (mc.screen instanceof EquipmentWheelScreen wheel) wheel.accept(List.of(values));
    }

    public static boolean carrier(UUID id) {
        var p = Minecraft.getInstance().player;
        if (p == null) return false;
        java.util.List<net.minecraft.world.item.ItemStack> items =
                new java.util.ArrayList<>(CuriosEquipment.rings(p));
        for (int i = 0; i < p.getInventory().getContainerSize(); i++)
            items.add(p.getInventory().getItem(i));
        for (var stack : items)
            if (WizardEquipment.carrier(stack)) {
                var tag = ItemData.read(stack).getCompound("ElementalCircuit");
                if (tag.hasUUID("Identity") && tag.getUUID("Identity").equals(id)) return true;
            }
        return false;
    }

    private EquipmentClient() {}
}
