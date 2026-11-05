package dev.elementalcircuits.client.screen;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.codec.JsonCodecs;
import dev.elementalcircuits.application.observation.SurveyReport;
import dev.elementalcircuits.client.render.*;
import dev.elementalcircuits.network.ProbeNetwork;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

import java.util.UUID;

/** Observation-only report. Progress cannot reopen a dismissed report or overwrite a newer use. */
public final class ProbeScreen extends Screen {
    private final UUID token;
    private SurveyReport report;
    private int offset;
    private final String dimension;

    public ProbeScreen(UUID token, SurveyReport report) {
        super(
                dev.elementalcircuits.client.text.UiText.component(
                        "screen.elementalcircuits.probe_wand"));
        this.token = token;
        this.report = report;
        var mc = Minecraft.getInstance();
        dimension = mc.level == null ? "" : mc.level.dimension().location().toString();
    }

    public SurveyReport report() {
        return report;
    }

    public static void accept(ProbeNetwork.Report packet) {
        var mc = Minecraft.getInstance();
        if (mc.player == null
                || mc.level == null
                || !mc.level.dimension().location().equals(packet.dimension())) return;
        if (packet.open()
                && !mc.player.getMainHandItem().is(ElementalCircuits.PROBE_WAND.get())
                && !mc.player.getOffhandItem().is(ElementalCircuits.PROBE_WAND.get())) return;
        var data = JsonCodecs.readSurvey(packet.json());
        if (packet.open()) mc.setScreen(new ProbeScreen(packet.token(), data));
        else if (mc.screen instanceof ProbeScreen screen && screen.token.equals(packet.token()))
            screen.report = data;
    }

    @Override
    protected void init() {
        int span = Math.min(560, width - 16), left = (width - span) / 2;
        addRenderableWidget(
                new CircuitButton(
                        left + span - 64,
                        height - 30,
                        64,
                        22,
                        dev.elementalcircuits.client.text.UiText.string(
                                "text.elementalcircuits.client.screen._probe_screen.001"),
                        false,
                        null,
                        false,
                        this::onClose));
    }

    @Override
    public void tick() {
        if (minecraft.level == null
                || !minecraft.level.dimension().location().toString().equals(dimension)) onClose();
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double delta) {
        if (delta == 0) return false;
        int visible = ProbeRenderer.visible(height);
        offset =
                Math.max(
                        0,
                        Math.min(
                                Math.max(0, report.rows().size() - visible),
                                offset + (delta > 0 ? -1 : 1)));
        return true;
    }

    @Override
    public void renderBackground(GuiGraphics g, int x, int y, float partial) {}

    @Override
    public void render(GuiGraphics g, int x, int y, float partial) {
        g.drawManaged(() -> ProbeRenderer.paint(g, font, width, height, report, offset));
        super.render(g, x, y, partial);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
