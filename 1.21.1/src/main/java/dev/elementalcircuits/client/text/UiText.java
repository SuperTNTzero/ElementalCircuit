package dev.elementalcircuits.client.text;

import dev.elementalcircuits.domain.combat.Element;

import net.minecraft.network.chat.Component;

import java.util.Locale;

/** Client-facing text must resolve through the Minecraft language resources. */
public final class UiText {
    private UiText() {}

    public static Component component(String key, Object... arguments) {
        return Component.translatable(key, arguments);
    }

    public static String string(String key, Object... arguments) {
        String resolved = component(key, arguments).getString();
        if (!resolved.equals(key)) return resolved;
        String fallback = dev.elementalcircuits.text.Texts.get(key);
        return arguments.length == 0
                ? fallback
                : String.format(Locale.ROOT, fallback.replace("%s", "%s"), arguments);
    }

    public static String element(Element element) {
        return string("element.elementalcircuits." + element.name().toLowerCase(Locale.ROOT));
    }
}
