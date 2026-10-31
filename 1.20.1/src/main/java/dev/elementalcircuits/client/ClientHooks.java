package dev.elementalcircuits.client;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.domain.Fixed;
import dev.elementalcircuits.network.CircuitNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=ElementalCircuits.ID,value=Dist.CLIENT)
public final class ClientHooks {
    private static long mana,capacity;
    private ClientHooks(){ }
    public static void editor(CircuitNetwork.Editor packet){
        Minecraft mc=Minecraft.getInstance();
        if(packet.open())mc.setScreen(new CircuitScreen(packet));
        else if(mc.screen instanceof CircuitScreen screen)screen.accept(packet);
        else if(mc.screen instanceof BlueprintScreen screen)screen.accept(packet);
        else if(mc.screen instanceof AssemblyScreen screen)screen.accept(packet);
        else if(mc.screen instanceof NotebookScreen screen)screen.acceptEditor(packet);
        else if(mc.screen instanceof NotebookImportScreen screen)screen.acceptEditor(packet);
    }
    public static void mana(CircuitNetwork.Mana packet){if(packet.capacity()>0 && packet.amount()>=0 && packet.amount()<=packet.capacity()){mana=packet.amount();capacity=packet.capacity();}}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){mana=0;capacity=0;}
    @SubscribeEvent public static void hud(RenderGuiOverlayEvent.Post event){
        Minecraft mc=Minecraft.getInstance();if(mc.player==null || capacity<=0 || mc.options.hideGui || !event.getOverlay().id().equals(VanillaGuiOverlay.HOTBAR.id()))return;
        GuiGraphics g=event.getGuiGraphics();int x=12,y=mc.getWindow().getGuiScaledHeight()-43,w=92;
        g.fill(x-3,y-13,x+w+3,y+10,0xba101a27);g.fill(x,y,x+w,y+5,0xff28364b);
        g.fill(x,y,x+(int)(w*mana/capacity),y+5,0xff83c8eb);
        g.drawString(mc.font,"魔力 "+Fixed.display(mana)+" / "+Fixed.display(capacity),x,y-10,0xffc5e7f6,false);
    }
}
