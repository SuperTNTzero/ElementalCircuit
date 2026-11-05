package dev.elementalcircuits.client.input;

import com.mojang.blaze3d.platform.InputConstants;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.codec.JsonCodecs;
import dev.elementalcircuits.adapter.persistence.ItemAppearance;
import dev.elementalcircuits.client.book.BookAnimationClient;
import dev.elementalcircuits.client.screen.CircuitScreen;
import dev.elementalcircuits.client.screen.NotebookImportScreen;
import dev.elementalcircuits.client.screen.NotebookScreen;
import dev.elementalcircuits.client.screen.SpellWheelScreen;
import dev.elementalcircuits.client.screen.SpellbookScreen;
import dev.elementalcircuits.client.screen.WorkshopScreen;
import dev.elementalcircuits.network.BookNetwork;

import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.event.tick.*;

import org.lwjgl.glfw.GLFW;

import java.util.UUID;

@net.neoforged.fml.common.EventBusSubscriber(modid = ElementalCircuits.ID, value = Dist.CLIENT)
public final class BookClient {
    private static final UUID ZERO = new UUID(0, 0);
    private static final KeyMapping PREV =
            new KeyMapping(
                    "key.elementalcircuits.previous_spell",
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_Z,
                    "key.categories.elementalcircuits");
    private static final KeyMapping NEXT =
            new KeyMapping(
                    "key.elementalcircuits.next_spell",
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_X,
                    "key.categories.elementalcircuits");
    private static final KeyMapping OPEN =
            new KeyMapping(
                    "key.elementalcircuits.open_spellbook",
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_B,
                    "key.categories.elementalcircuits");
    private static final KeyMapping WHEEL =
            new KeyMapping(
                    "key.elementalcircuits.spell_wheel",
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_R,
                    "key.categories.elementalcircuits");
    private static BookNetwork.SpellHud state;

    private BookClient() {}

    @net.neoforged.fml.common.EventBusSubscriber(
            modid = ElementalCircuits.ID,
            value = Dist.CLIENT,
            bus = net.neoforged.fml.common.EventBusSubscriber.Bus.MOD)
    public static final class Bindings {
        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent e) {
            e.register(PREV);
            e.register(NEXT);
            e.register(OPEN);
            e.register(WHEEL);
        }
    }

    private static UUID identity(ItemStack s) {
        var value = ItemAppearance.identity(s);
        return value == null ? ZERO : value;
    }

    public static void openNotebook() {
        var mc = Minecraft.getInstance();
        if (mc.player != null)
            BookNetwork.send(new BookNetwork.NotebookRequest(ZERO, 6, 0, "", ""));
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        var mc = Minecraft.getInstance();
        boolean available = mc.player != null && mc.screen == null;
        while (PREV.consumeClick()) if (available) cycle(-1);
        while (NEXT.consumeClick()) if (available) cycle(1);
        while (WHEEL.consumeClick())
            if (available) {
                UUID id = carrierIdentity();
                if (!id.equals(ZERO)) {
                    mc.setScreen(new SpellWheelScreen(id));
                    BookNetwork.send(new BookNetwork.SpellRequest(id, 6, 0));
                    available = false;
                }
            }
        while (OPEN.consumeClick())
            if (available
                    && (mc.player.getOffhandItem().is(ElementalCircuits.SPELL_BOOK.get())
                            || mc.player.getMainHandItem().is(ElementalCircuits.SPELL_BOOK.get())))
                BookNetwork.send(new BookNetwork.SpellRequest(ZERO, 2, 0));
    }

    public static UUID carrierIdentity() {
        var p = Minecraft.getInstance().player;
        if (p == null || !p.getMainHandItem().is(ElementalCircuits.WAND.get())) return ZERO;
        return identity(p.getMainHandItem());
    }

    public static UUID bookIdentity() {
        var p = Minecraft.getInstance().player;
        return p != null && p.getOffhandItem().is(ElementalCircuits.SPELL_BOOK.get())
                ? identity(p.getOffhandItem())
                : ZERO;
    }

    public static boolean wheelMatches(int key, int scan) {
        return WHEEL.matches(key, scan);
    }

    public static boolean wheelMouse(int button) {
        return WHEEL.matchesMouse(button);
    }

    private static void cycle(int d) {
        UUID id = carrierIdentity();
        if (!id.equals(ZERO)) BookNetwork.send(new BookNetwork.SpellRequest(id, 8, d));
    }

    public static void selector(BookNetwork.SelectorState packet) {
        var view = JsonCodecs.readSelection(packet.json());
        if (!view.identity().equals(carrierIdentity())
                || view.slots().stream()
                        .anyMatch(
                                s ->
                                        !s.carrier()
                                                .equals(
                                                        s.book()
                                                                ? bookIdentity()
                                                                : carrierIdentity()))) return;
        var mc = Minecraft.getInstance();
        if (mc.screen instanceof SpellWheelScreen wheel) wheel.accept(view);
        hud(
                new BookNetwork.SpellHud(
                        view.selectedCarrier(),
                        view.selected(),
                        (int)
                                view.slots().stream()
                                        .filter(s -> s.carrier().equals(view.selectedCarrier()))
                                        .count(),
                        view.current().powered(),
                        view.book()));
    }

    public static void workshop(BookNetwork.WorkshopState p) {
        var mc = Minecraft.getInstance();
        var v = JsonCodecs.readWorkshop(p.json());
        if (p.open()) mc.setScreen(new WorkshopScreen(v));
        else if (mc.screen instanceof WorkshopScreen s) s.accept(v);
    }

    public static void notebook(BookNetwork.NotebookState p) {
        var mc = Minecraft.getInstance();
        var v = JsonCodecs.readNotebook(p.json());
        if (p.open())
            mc.setScreen(new NotebookScreen(mc.screen instanceof CircuitScreen c ? c : null, v));
        else if (mc.screen instanceof NotebookScreen s) s.accept(v);
        else if (mc.screen instanceof NotebookImportScreen s) s.accept(v);
    }

    public static void spellbook(BookNetwork.SpellState p) {
        var mc = Minecraft.getInstance();
        var v = JsonCodecs.readSpellbook(p.json());
        if (p.open()) mc.setScreen(new SpellbookScreen(v));
        else if (mc.screen instanceof SpellbookScreen s) s.accept(v);
    }

    public static void hud(BookNetwork.SpellHud p) {
        if (p.pages() >= 1 && p.pages() <= 4 && p.selected() >= 1 && p.selected() <= 65534) {
            if (state != null
                    && state.identity().equals(p.identity())
                    && state.selected() != p.selected())
                BookAnimationClient.page(
                        p.identity(), Integer.compare(p.selected(), state.selected()));
            state = p;
        }
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut e) {
        state = null;
    }

    @SubscribeEvent
    public static void render(RenderGuiEvent.Post e) {
        var mc = Minecraft.getInstance();
        if (mc.player == null
                || mc.options.hideGui
                || state == null
                || mc.screen instanceof SpellWheelScreen) return;
        UUID shown =
                mc.player.getMainHandItem().is(ElementalCircuits.WAND.get())
                        ? carrierIdentity()
                        : identity(mc.player.getOffhandItem());
        if (!shown.equals(state.identity())
                && !(mc.player.getMainHandItem().is(ElementalCircuits.WAND.get())
                        && bookIdentity().equals(state.identity()))) return;
        GuiGraphics g = e.getGuiGraphics();
        var board =
                dev.elementalcircuits.adapter.persistence.BoardAppearance.active(
                        mc.player.getMainHandItem(), mc.player);
        String status =
                board != null && board.remaining() == 0
                        ? board.melted()
                                ? dev.elementalcircuits.client.text.UiText.string(
                                        "text.elementalcircuits.client.input._book_client.001")
                                : dev.elementalcircuits.client.text.UiText.string(
                                        "text.elementalcircuits.client.input._book_client.002")
                        : state.powered()
                                ? dev.elementalcircuits.client.text.UiText.string(
                                        "text.elementalcircuits.client.input._book_client.003")
                                : dev.elementalcircuits.client.text.UiText.string(
                                        "text.elementalcircuits.client.input._book_client.004");
        String text =
                (state.book()
                                ? dev.elementalcircuits.client.text.UiText.string(
                                        "text.elementalcircuits.client.input._book_client.005")
                                : dev.elementalcircuits.client.text.UiText.string(
                                        "text.elementalcircuits.client.input._book_client.006"))
                        + dev.elementalcircuits.client.text.UiText.string(
                                "text.elementalcircuits.client.input._book_client.007")
                        + state.selected()
                        + " / "
                        + state.pages()
                        + " · "
                        + status
                        + "  ["
                        + WHEEL.getTranslatedKeyMessage().getString()
                        + "]";
        int x = (mc.getWindow().getGuiScaledWidth() - mc.font.width(text)) / 2,
                y = mc.getWindow().getGuiScaledHeight() - 84;
        g.fill(x - 4, y - 3, x + mc.font.width(text) + 4, y + 11, 0xcc181f21);
        g.drawString(mc.font, text, x, y, 0xffd9ba7a, false);
    }
}
