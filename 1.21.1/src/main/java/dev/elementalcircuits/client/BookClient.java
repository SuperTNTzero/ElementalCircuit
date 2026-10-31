package dev.elementalcircuits.client;
import com.mojang.blaze3d.platform.InputConstants;
import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.JsonCodecs;
import dev.elementalcircuits.network.BookNetwork;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.*;

import net.neoforged.neoforge.event.tick.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import java.util.UUID;

@net.neoforged.fml.common.EventBusSubscriber(modid=ElementalCircuits.ID,value=Dist.CLIENT)
public final class BookClient {
    private static final UUID ZERO=new UUID(0,0);
    private static final KeyMapping PREV=new KeyMapping("key.elementalcircuits.previous_spell",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_Z,"key.categories.elementalcircuits");
    private static final KeyMapping NEXT=new KeyMapping("key.elementalcircuits.next_spell",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_X,"key.categories.elementalcircuits");
    private static final KeyMapping OPEN=new KeyMapping("key.elementalcircuits.open_spellbook",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_B,"key.categories.elementalcircuits");
    private static BookNetwork.SpellHud state;
    private BookClient(){ }
    @net.neoforged.fml.common.EventBusSubscriber(modid=ElementalCircuits.ID,value=Dist.CLIENT,bus=net.neoforged.fml.common.EventBusSubscriber.Bus.MOD)
    public static final class Bindings {@SubscribeEvent public static void keys(RegisterKeyMappingsEvent e){e.register(PREV);e.register(NEXT);e.register(OPEN);}}
    private static UUID identity(ItemStack s){var tag=dev.elementalcircuits.adapter.persistence.ItemData.read(s);if(tag==null || !tag.contains("ElementalCircuit"))return ZERO;var c=tag.getCompound("ElementalCircuit");return c.hasUUID("Identity")?c.getUUID("Identity"):ZERO;}
    public static void openNotebook(){var mc=Minecraft.getInstance();if(mc.player!=null)BookNetwork.send(new BookNetwork.NotebookRequest(ZERO,6,0,"",""));}
    @SubscribeEvent public static void tick(ClientTickEvent.Post e){var mc=Minecraft.getInstance();boolean available=mc.player!=null && mc.screen==null;while(PREV.consumeClick())if(available)cycle(-1);while(NEXT.consumeClick())if(available)cycle(1);while(OPEN.consumeClick())if(available && (mc.player.getOffhandItem().is(ElementalCircuits.SPELL_BOOK.get()) || mc.player.getMainHandItem().is(ElementalCircuits.SPELL_BOOK.get())))BookNetwork.send(new BookNetwork.SpellRequest(ZERO,2,0));}
    private static void cycle(int d){var p=Minecraft.getInstance().player;if(p.getMainHandItem().is(ElementalCircuits.WAND.get()) && p.getOffhandItem().is(ElementalCircuits.SPELL_BOOK.get())){UUID id=identity(p.getOffhandItem());if(!id.equals(ZERO))BookNetwork.send(new BookNetwork.SpellRequest(id,0,d));}}
    public static void workshop(BookNetwork.WorkshopState p){var mc=Minecraft.getInstance();var v=JsonCodecs.readWorkshop(p.json());if(p.open())mc.setScreen(new WorkshopScreen(v));else if(mc.screen instanceof WorkshopScreen s)s.accept(v);}
    public static void notebook(BookNetwork.NotebookState p){var mc=Minecraft.getInstance();var v=JsonCodecs.readNotebook(p.json());if(p.open())mc.setScreen(new NotebookScreen(mc.screen instanceof CircuitScreen c?c:null,v));else if(mc.screen instanceof NotebookScreen s)s.accept(v);else if(mc.screen instanceof NotebookImportScreen s)s.accept(v);}
    public static void spellbook(BookNetwork.SpellState p){var mc=Minecraft.getInstance();var v=JsonCodecs.readSpellbook(p.json());if(p.open())mc.setScreen(new SpellbookScreen(v));else if(mc.screen instanceof SpellbookScreen s)s.accept(v);hud(new BookNetwork.SpellHud(v.identity(),v.selected(),v.slots().size(),v.current().powered()));}
    public static void hud(BookNetwork.SpellHud p){if(p.pages()>=1 && p.pages()<=4 && p.selected()>=1 && p.selected()<=4){if(state!=null && state.identity().equals(p.identity()) && state.selected()!=p.selected())dev.elementalcircuits.client.book.BookAnimationClient.page(p.identity(),Integer.compare(p.selected(),state.selected()));state=p;}}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){state=null;}
    @SubscribeEvent public static void render(RenderGuiEvent.Post e){var mc=Minecraft.getInstance();if(mc.player==null || mc.options.hideGui || state==null || !mc.player.getOffhandItem().is(ElementalCircuits.SPELL_BOOK.get()) || !identity(mc.player.getOffhandItem()).equals(state.identity()))return;GuiGraphics g=e.getGuiGraphics();String text="法术书 · 阵 "+state.selected()+" / "+state.pages()+" · "+(state.powered()?"运行":"停供")+"  ["+PREV.getTranslatedKeyMessage().getString()+" / "+NEXT.getTranslatedKeyMessage().getString()+"]";int x=(mc.getWindow().getGuiScaledWidth()-mc.font.width(text))/2,y=mc.getWindow().getGuiScaledHeight()-67;g.fill(x-4,y-3,x+mc.font.width(text)+4,y+11,0xcc181f21);g.drawString(mc.font,text,x,y,0xffd9ba7a,false);}
}
