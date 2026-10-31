package dev.elementalcircuits.client;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.persistence.CooldownComponent;
import dev.elementalcircuits.client.render.CastingHudRenderer;
import dev.elementalcircuits.domain.CastCooldown;
import dev.elementalcircuits.server.WandEquipment;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/** Read-only HUD. The persisted, server-synchronized component remains the only cooldown clock. */
@EventBusSubscriber(modid=ElementalCircuits.ID,value=Dist.CLIENT)
public final class CooldownHud {
    private CooldownHud() {}
    @SubscribeEvent public static void render(RenderGuiEvent.Post event) {
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null || mc.screen!=null || mc.options.hideGui || !mc.player.getMainHandItem().is(ElementalCircuits.WAND.get()))return;
        var wand=mc.player.getMainHandItem();
        CastCooldown state=wand.getOrDefault(CooldownComponent.TYPE.get(),CastCooldown.READY);
        boolean broken;
        try {broken=wand.getDamageValue()>=WandEquipment.stats(wand).durability();}
        catch(RuntimeException invalid) {return;}
        double partial=mc.isPaused()?0:mc.getTimer().getGameTimeDeltaPartialTick(false);
        double remaining=state.ready()?0:Math.max(0,state.remaining()-partial);
        double progress=broken?0:state.ready()?1:1-remaining/state.duration();
        var g=event.getGuiGraphics();
        g.drawManaged(()->CastingHudRenderer.cooldown(g,mc.getWindow().getGuiScaledWidth()/2,mc.getWindow().getGuiScaledHeight()/2,progress));
    }
}
