package dev.elementalcircuits.client.guide;

import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;

public final class GuideClient {
    private GuideClient() {}

    public static void open() {
        if (ModList.get().isLoaded("patchouli")) PatchouliGuideClient.open();
        else Minecraft.getInstance().setScreen(new GuideBookScreen());
    }
}
