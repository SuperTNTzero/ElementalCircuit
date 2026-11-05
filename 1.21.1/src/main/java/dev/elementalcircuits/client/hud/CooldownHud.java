package dev.elementalcircuits.client.hud;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.config.ClientServerPolicy;
import dev.elementalcircuits.adapter.persistence.CooldownComponent;
import dev.elementalcircuits.client.render.CastingHudRenderer;
import dev.elementalcircuits.domain.Fixed;
import dev.elementalcircuits.domain.casting.CastCooldown;
import dev.elementalcircuits.server.equipment.WandEquipment;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/** Read-only HUD. The persisted, server-synchronized component remains the only cooldown clock. */
@EventBusSubscriber(modid = ElementalCircuits.ID, value = Dist.CLIENT)
public final class CooldownHud {
    private CooldownHud() {}

    @SubscribeEvent
    public static void render(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null
                || mc.screen != null
                || mc.options.hideGui
                || !mc.player.getMainHandItem().is(ElementalCircuits.WAND.get())) return;
        var wand = mc.player.getMainHandItem();
        CastCooldown state = wand.getOrDefault(CooldownComponent.TYPE.get(), CastCooldown.READY);
        boolean broken;
        try {
            broken = wand.getDamageValue() >= WandEquipment.stats(wand).durability();
        } catch (RuntimeException invalid) {
            return;
        }
        double partial = mc.isPaused() ? 0 : mc.getTimer().getGameTimeDeltaPartialTick(false);
        double remaining = state.ready() ? 0 : Math.max(0, state.remaining() - partial);
        double progress = broken ? 0 : state.ready() ? 1 : 1 - remaining / state.duration();
        var g = event.getGuiGraphics();
        int cx = mc.getWindow().getGuiScaledWidth() / 2,
                cy = mc.getWindow().getGuiScaledHeight() / 2;
        g.drawManaged(
                () -> {
                    CastingHudRenderer.cooldown(g, cx, cy, progress);
                    if (mc.player.isUsingItem()
                            && mc.player.getUsedItemHand()
                                    == net.minecraft.world.InteractionHand.MAIN_HAND
                            && mc.player.getUseItem().is(ElementalCircuits.WAND.get())) {
                        var start = ClientServerPolicy.chargeStart();
                        double elapsed = mc.player.getTicksUsingItem() + partial;
                        if (elapsed >= start)
                            CastingHudRenderer.charge(
                                    g,
                                    mc.font,
                                    cx,
                                    cy,
                                    Math.clamp(
                                            (elapsed - start)
                                                    / (ClientServerPolicy.chargeFull() - start),
                                            0,
                                            1),
                                    ClientServerPolicy.chargeMaximum() / (double) Fixed.ONE);
                    }
                });
    }
}
