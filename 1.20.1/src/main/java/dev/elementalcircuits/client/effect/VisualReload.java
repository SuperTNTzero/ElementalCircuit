package dev.elementalcircuits.client.effect;

import dev.elementalcircuits.ElementalCircuits;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=ElementalCircuits.ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class VisualReload {
    private VisualReload(){}
    @SubscribeEvent public static void register(RegisterClientReloadListenersEvent event){event.registerReloadListener((ResourceManagerReloadListener)manager->{
        try(var reader=manager.openAsReader(new ResourceLocation(ElementalCircuits.ID,"art/effects.json"))){VisualStyles.load(reader);SpellVisualClient.reload();}
        catch(java.io.IOException | RuntimeException error){org.slf4j.LoggerFactory.getLogger("ElementalCircuits").error("特效资源重载失败，保留上次完整样式",error);}
    });}
}
