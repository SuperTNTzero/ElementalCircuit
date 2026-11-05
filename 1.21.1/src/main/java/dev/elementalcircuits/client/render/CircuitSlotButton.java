package dev.elementalcircuits.client.render;

import dev.elementalcircuits.application.observation.AssemblyInspection;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Accessible click adapter; slot card painting is a separate read-only renderer. */
public final class CircuitSlotButton extends Button {
    private final AssemblyInspection.Slot slot;
    private final boolean selected;

    public CircuitSlotButton(
            int x,
            int y,
            int width,
            AssemblyInspection.Slot slot,
            boolean selected,
            Runnable action) {
        super(
                x,
                y,
                width,
                38,
                Component.translatable("ui.elementalcircuits.slot", slot.id()),
                b -> action.run(),
                DEFAULT_NARRATION);
        this.slot = slot;
        this.selected = selected;
    }

    @Override
    public void renderWidget(GuiGraphics g, int x, int y, float partial) {
        g.drawManaged(
                () ->
                        AssemblyRenderer.slot(
                                g,
                                Minecraft.getInstance().font,
                                getX(),
                                getY(),
                                getWidth(),
                                slot,
                                selected,
                                isHoveredOrFocused()));
    }
}
