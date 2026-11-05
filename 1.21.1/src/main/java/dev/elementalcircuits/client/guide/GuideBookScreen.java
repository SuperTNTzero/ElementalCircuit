package dev.elementalcircuits.client.guide;

import com.google.gson.*;

import dev.elementalcircuits.ElementalCircuits;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.network.chat.*;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/** Native book navigation and paper; pagination uses the active font, including resource packs. */
public final class GuideBookScreen extends BookViewScreen {
    public GuideBookScreen() {
        super(new BookAccess(pages()));
    }

    @Override
    protected void createMenuControls() {
        super.createMenuControls();
        addRenderableWidget(
                Button.builder(
                                Component.translatable("guide.elementalcircuits.contents"),
                                b -> setPage(0))
                        .bounds(width / 2 - 40, 220, 80, 20)
                        .build());
    }

    public static List<Component> pages() {
        var mc = Minecraft.getInstance();
        String lang = mc.options.languageCode;
        var resources = mc.getResourceManager();
        var location =
                ResourceLocation.fromNamespaceAndPath(
                        ElementalCircuits.ID, "guidebook/" + lang + ".json");
        var resource =
                resources
                        .getResource(location)
                        .orElseGet(
                                () ->
                                        resources
                                                .getResource(
                                                        ResourceLocation.fromNamespaceAndPath(
                                                                ElementalCircuits.ID,
                                                                "guidebook/en_us.json"))
                                                .orElseThrow());
        try (var reader = resource.openAsReader()) {
            var entries = JsonParser.parseReader(reader).getAsJsonArray();
            int contents = (entries.size() + 3) / 4;
            List<Component> result =
                    new ArrayList<>(Collections.nCopies(contents, Component.empty()));
            List<Component> links = new ArrayList<>();
            for (var value : entries) {
                var entry = value.getAsJsonObject();
                String title = entry.get("title").getAsString();
                int first = result.size() + 1;
                links.add(
                        Component.literal(title)
                                .withStyle(
                                        s ->
                                                s.withColor(0x24526b)
                                                        .withUnderlined(true)
                                                        .withClickEvent(
                                                                new ClickEvent(
                                                                        ClickEvent.Action
                                                                                .CHANGE_PAGE,
                                                                        Integer.toString(first)))));
                for (var text : entry.getAsJsonArray("pages")) {
                    var lines =
                            mc.font.getSplitter().splitLines(text.getAsString(), 114, Style.EMPTY);
                    for (int start = 0; start < lines.size(); start += 10) {
                        var page =
                                Component.literal(title + "\n\n")
                                        .withStyle(s -> s.withColor(0x243b50));
                        for (int i = start; i < Math.min(start + 10, lines.size()); i++)
                            page.append(
                                    Component.literal(lines.get(i).getString() + "\n")
                                            .withStyle(s -> s.withColor(0x332d28)));
                        result.add(page);
                    }
                }
            }
            for (int i = 0; i < contents; i++) {
                var toc = Component.translatable("guide.elementalcircuits.contents").append("\n\n");
                for (int j = i * 4; j < Math.min(i * 4 + 4, links.size()); j++)
                    toc.append(links.get(j)).append("\n\n");
                result.set(i, toc);
            }
            return List.copyOf(result);
        } catch (java.io.IOException bad) {
            throw new IllegalStateException("Cannot read circuit field guide", bad);
        }
    }
}
