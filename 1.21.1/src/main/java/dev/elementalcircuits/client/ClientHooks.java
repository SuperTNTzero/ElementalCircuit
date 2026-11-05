package dev.elementalcircuits.client;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.config.ClientServerPolicy;
import dev.elementalcircuits.client.render.CastingHudRenderer;
import dev.elementalcircuits.client.screen.AssemblyScreen;
import dev.elementalcircuits.client.screen.BlueprintScreen;
import dev.elementalcircuits.client.screen.CircuitScreen;
import dev.elementalcircuits.client.screen.LayerScreen;
import dev.elementalcircuits.client.screen.NotebookImportScreen;
import dev.elementalcircuits.client.screen.NotebookScreen;
import dev.elementalcircuits.network.CircuitNetwork;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.*;

@net.neoforged.fml.common.EventBusSubscriber(modid = ElementalCircuits.ID, value = Dist.CLIENT)
public final class ClientHooks {
    private static long mana, capacity;

    private ClientHooks() {}

    public static void editor(CircuitNetwork.Editor packet) {
        Minecraft mc = Minecraft.getInstance();
        if (packet.open()) mc.setScreen(new CircuitScreen(packet));
        else if (mc.screen instanceof CircuitScreen screen) screen.accept(packet);
        else if (mc.screen instanceof BlueprintScreen screen) screen.accept(packet);
        else if (mc.screen instanceof AssemblyScreen screen) screen.accept(packet);
        else if (mc.screen instanceof LayerScreen screen) screen.accept(packet);
        else if (mc.screen instanceof NotebookScreen screen) screen.acceptEditor(packet);
        else if (mc.screen instanceof NotebookImportScreen screen) screen.acceptEditor(packet);
    }

    public static void mana(CircuitNetwork.Mana packet) {
        if (packet.capacity() > 0 && packet.amount() >= 0 && packet.amount() <= packet.capacity()) {
            mana = packet.amount();
            capacity = packet.capacity();
        }
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        mana = 0;
        capacity = 0;
        ClientServerPolicy.clear();
    }

    @SubscribeEvent
    public static void hud(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || capacity <= 0 || mc.options.hideGui) return;
        GuiGraphics g = event.getGuiGraphics();
        g.drawManaged(
                () ->
                        CastingHudRenderer.mana(
                                g, mc.font, mc.getWindow().getGuiScaledHeight(), mana, capacity));
    }
}
