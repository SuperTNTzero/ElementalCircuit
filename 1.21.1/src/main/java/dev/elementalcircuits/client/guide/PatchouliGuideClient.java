package dev.elementalcircuits.client.guide;

import dev.elementalcircuits.ElementalCircuits;

import net.minecraft.resources.ResourceLocation;

import vazkii.patchouli.api.PatchouliAPI;

/** Loaded only after the optional mod is present; no Patchouli types cross this boundary. */
final class PatchouliGuideClient {
    private PatchouliGuideClient() {}

    static void open() {
        PatchouliAPI.get()
                .openBookGUI(
                        ResourceLocation.fromNamespaceAndPath(ElementalCircuits.ID, "field_guide"));
    }
}
