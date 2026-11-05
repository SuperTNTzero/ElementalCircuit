package dev.elementalcircuits.client;

public final class ConfigScreens {
    public static void register(net.neoforged.fml.ModContainer container) {
        container.registerExtensionPoint(
                net.neoforged.neoforge.client.gui.IConfigScreenFactory.class,
                net.neoforged.neoforge.client.gui.ConfigurationScreen::new);
    }

    private ConfigScreens() {}
}
