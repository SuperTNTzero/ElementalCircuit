package dev.elementalcircuits.client;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.WandResources;
import dev.elementalcircuits.domain.WandParts;
import dev.elementalcircuits.server.WandEquipment;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid=ElementalCircuits.ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class WandModelRegistration {
    private WandModelRegistration(){ }
    @SubscribeEvent public static void setup(FMLClientSetupEvent event){event.enqueueWork(()->ItemProperties.register(ElementalCircuits.WAND.get(),new ResourceLocation(ElementalCircuits.ID,"assembly"),(stack,level,entity,seed)->{
        try{
            WandParts.Assembly a=WandEquipment.assembly(stack);WandParts d=WandResources.definitions();
            return d.in(WandParts.Slot.HEAD).indexOf(d.get(a.head()))*9+d.in(WandParts.Slot.BODY).indexOf(d.get(a.body()))*3+d.in(WandParts.Slot.TAIL).indexOf(d.get(a.tail()));
        }catch(RuntimeException e){return 0;}
    }));}
}
