package dev.elementalcircuits.client;
import com.mojang.blaze3d.platform.InputConstants;
import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.JsonCodecs;
import dev.elementalcircuits.network.BookNetwork;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import java.util.UUID;

@Mod.EventBusSubscriber(modid=ElementalCircuits.ID,value=Dist.CLIENT)
public final class BookClient {
    private static final UUID ZERO=new UUID(0,0);
    private static final KeyMapping PREV=new KeyMapping("key.elementalcircuits.previous_spell",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_Z,"key.categories.elementalcircuits");
    private static final KeyMapping NEXT=new KeyMapping("key.elementalcircuits.next_spell",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_X,"key.categories.elementalcircuits");
    private static final KeyMapping OPEN=new KeyMapping("key.elementalcircuits.open_spellbook",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_B,"key.categories.elementalcircuits");
    private static BookNetwork.SpellHud state;
    private BookClient(){ }
    @Mod.EventBusSubscriber(modid=ElementalCircuits.ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Bindings {@SubscribeEvent public static void keys(RegisterKeyMappingsEvent e){e.register(PREV);e.register(NEXT);e.register(OPEN);}}
    private static UUID identity(ItemStack s){var tag=s.getTag();if(tag==null || !tag.contains("ElementalCircuit"))return ZERO;var c=tag.getCompound("ElementalCircuit");return c.hasUUID("Identity")?c.getUUID("Identity"):ZERO;}
    public static void openNotebook(){var mc=Minecraft.getInstance();if(mc.player!=null)BookNetwork.send(new BookNetwork.NotebookRequest(ZERO,6,0,"",""));}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){if(e.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();boolean available=mc.player!=null && mc.screen==null;while(PREV.consumeClick())if(available)cycle(-1);while(NEXT.consumeClick())if(available)cycle(1);while(OPEN.consumeClick())if(available && (mc.player.getOffhandItem().is(ElementalCircuits.SPELL_BOOK.get()) || mc.player.getMainHandItem().is(ElementalCircuits.SPELL_BOOK.get())))BookNetwork.send(new BookNetwork.SpellRequest(ZERO,2,0));}
    private static void cycle(int d){var p=Minecraft.getInstance().player;if(p.getMainHandItem().is(ElementalCircuits.WAND.get()) && p.getOffhandItem().is(ElementalCircuits.SPELL_BOOK.get())){UUID id=identity(p.getOffhandItem());if(!id.equals(ZERO))BookNetwork.send(new BookNetwork.SpellRequest(id,0,d));}}
    public static void workshop(BookNetwork.WorkshopState p){var mc=Minecraft.getInstance();var v=JsonCodecs.readWorkshop(p.json());if(p.open())mc.setScreen(new WorkshopScreen(v));else if(mc.screen instanceof WorkshopScreen s)s.accept(v);}
    public static void notebook(BookNetwork.NotebookState p){var mc=Minecraft.getInstance();var v=JsonCodecs.readNotebook(p.json());if(p.open())mc.setScreen(new NotebookScreen(mc.screen instanceof CircuitScreen c?c:null,v));else if(mc.screen instanceof NotebookScreen s)s.accept(v);else if(mc.screen instanceof NotebookImportScreen s)s.accept(v);}
    public static void spellbook(BookNetwork.SpellState p){var mc=Minecraft.getInstance();var v=JsonCodecs.readSpellbook(p.json());if(p.open())mc.setScreen(new SpellbookScreen(v));else if(mc.screen instanceof SpellbookScreen s)s.accept(v);hud(new BookNetwork.SpellHud(v.identity(),v.selected(),v.slots().size(),v.current().powered()));}
    public static void hud(BookNetwork.SpellHud p){if(p.pages()>=1 && p.pages()<=4 && p.selected()>=1 && p.selected()<=4)state=p;}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){state=null;}
    @SubscribeEvent public static void render(RenderGuiOverlayEvent.Post e){var mc=Minecraft.getInstance();if(mc.player==null || mc.options.hideGui || state==null || !e.getOverlay().id().equals(VanillaGuiOverlay.HOTBAR.id()) || !mc.player.getOffhandItem().is(ElementalCircuits.SPELL_BOOK.get()) || !identity(mc.player.getOffhandItem()).equals(state.identity()))return;GuiGraphics g=e.getGuiGraphics();String text="法术书 · 阵 "+state.selected()+" / "+state.pages()+" · "+(state.powered()?"运行":"停供")+"  ["+PREV.getTranslatedKeyMessage().getString()+" / "+NEXT.getTranslatedKeyMessage().getString()+"]";int x=(mc.getWindow().getGuiScaledWidth()-mc.font.width(text))/2,y=mc.getWindow().getGuiScaledHeight()-67;g.fill(x-4,y-3,x+mc.font.width(text)+4,y+11,0xc0101b28);g.drawString(mc.font,text,x,y,0xffe9c98a,false);}
}
