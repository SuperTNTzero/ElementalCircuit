package dev.elementalcircuits.client.screen;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.server.workshop.*;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

public final class RuneEngravingScreen extends AbstractContainerScreen<RuneEngravingMenu> {
    private int page;
    private static final int PAGE_SIZE = 8;

    public RuneEngravingScreen(RuneEngravingMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 232;
        inventoryLabelY = 139;
    }

    @Override
    protected void init() {
        super.init();
        buttons();
    }

    private void buttons() {
        clearWidgets();
        for (int row = 0; row < PAGE_SIZE; row++) {
            int id = page * PAGE_SIZE + row;
            if (id >= RuneEngraving.KEYS.size()) break;
            var item = RuneEngraving.RUNES.get(RuneEngraving.KEYS.get(id)).get();
            addRenderableWidget(
                    Button.builder(
                                    item.getDescription(),
                                    button -> {
                                        minecraft.gameMode.handleInventoryButtonClick(
                                                menu.containerId, id);
                                    })
                            .bounds(leftPos + 8, topPos + 32 + row * 12, 160, 12)
                            .build());
        }
        addRenderableWidget(
                Button.builder(
                                Component.literal("<"),
                                b -> {
                                    if (page > 0) {
                                        page--;
                                        buttons();
                                    }
                                })
                        .bounds(leftPos + 8, topPos + 17, 20, 12)
                        .build());
        addRenderableWidget(
                Button.builder(
                                Component.literal(">"),
                                b -> {
                                    if ((page + 1) * PAGE_SIZE < RuneEngraving.KEYS.size()) {
                                        page++;
                                        buttons();
                                    }
                                })
                        .bounds(leftPos + 148, topPos + 17, 20, 12)
                        .build());
    }

    @Override
    protected void renderBg(GuiGraphics g, float partial, int x, int y) {
        g.fill(
                leftPos - 2,
                topPos - 2,
                leftPos + imageWidth + 2,
                topPos + imageHeight + 2,
                0xffab8b4a);
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xff172334);
        for (var slot : menu.slots)
            g.fill(
                    leftPos + slot.x - 1,
                    topPos + slot.y - 1,
                    leftPos + slot.x + 17,
                    topPos + slot.y + 17,
                    0xff435366);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int x, int y) {
        g.drawString(font, title, 8, 5, 0xffdcc58a, false);
        g.drawString(
                font,
                Component.translatable("screen.elementalcircuits.rune_cost"),
                8,
                130,
                0xffa9cbdf,
                false);
        g.drawCenteredString(
                font,
                (page + 1) + " / " + ((RuneEngraving.KEYS.size() + PAGE_SIZE - 1) / PAGE_SIZE),
                88,
                18,
                0xffdcc58a);
    }

    @Override
    public void render(GuiGraphics g, int x, int y, float partial) {
        super.render(g, x, y, partial);
        renderTooltip(g, x, y);
    }

    @net.neoforged.fml.common.EventBusSubscriber(
            modid = ElementalCircuits.ID,
            value = Dist.CLIENT,
            bus = net.neoforged.fml.common.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent
        public static void screens(RegisterMenuScreensEvent event) {
            event.register(RuneEngraving.MENU.get(), RuneEngravingScreen::new);
        }
    }
}
