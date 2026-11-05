package dev.elementalcircuits.client.effect;

import dev.elementalcircuits.ElementalCircuits;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

@net.neoforged.fml.common.EventBusSubscriber(
        modid = ElementalCircuits.ID,
        bus = net.neoforged.fml.common.EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT)
public final class VisualReload {
    private VisualReload() {}

    @SubscribeEvent
    public static void register(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(
                (ResourceManagerReloadListener)
                        manager -> {
                            try (var reader =
                                    manager.openAsReader(
                                            ResourceLocation.fromNamespaceAndPath(
                                                    ElementalCircuits.ID, "art/effects.json"))) {
                                VisualStyles.load(reader);
                                SpellVisualClient.reload();
                            } catch (java.io.IOException | RuntimeException error) {
                                org.slf4j.LoggerFactory.getLogger("ElementalCircuits")
                                        .error(
                                                dev.elementalcircuits.client.text.UiText.string(
                                                        "text.elementalcircuits.client.effect._visual_reload.001"),
                                                error);
                            }
                        });
    }
}
