package dev.elementalcircuits.server.workshop;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.codec.BlueprintTextCode;
import dev.elementalcircuits.adapter.codec.JsonCodecs;
import dev.elementalcircuits.adapter.persistence.CarrierRepository;
import dev.elementalcircuits.adapter.persistence.NotebookRepository;
import dev.elementalcircuits.application.observation.CircuitSelection;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.book.BlueprintVolume;
import dev.elementalcircuits.domain.book.BookSelection;
import dev.elementalcircuits.domain.book.NotebookInspection;
import dev.elementalcircuits.domain.book.SpellbookInspection;
import dev.elementalcircuits.network.BookNetwork;
import dev.elementalcircuits.server.casting.CastingEquipment;
import dev.elementalcircuits.server.circuit.CarrierService;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/** Physical notebook owns immutable blueprints; physical spellbook owns a normal full assembly. */
public final class BookService {
    public static final UUID ZERO = new UUID(0, 0);
    private static final Map<UUID, Long> LAST = new HashMap<>();

    private BookService() {}

    public static BlueprintVolume volume(ItemStack stack) {
        return NotebookRepository.load(stack);
    }

    public static void write(ItemStack stack, BlueprintVolume v) {
        NotebookRepository.save(stack, v);
    }

    public static ItemStack findNotebook(ServerPlayer p, UUID id) {
        ItemStack found = ItemStack.EMPTY;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            var s = p.getInventory().getItem(i);
            if (s.is(ElementalCircuits.NOTEBOOK.get())
                    && (id.equals(ZERO) || volume(s).identity().equals(id))) {
                if (id.equals(ZERO)) return s;
                if (!found.isEmpty())
                    throw new IllegalArgumentException(
                            dev.elementalcircuits.text.Texts.get(
                                    "text.elementalcircuits.server.workshop._book_service.001"));
                found = s;
            }
        }
        if (found.isEmpty())
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.server.workshop._book_service.002"));
        return found;
    }

    public static void openNotebook(ServerPlayer p, ItemStack stack) {
        try {
            BookNetwork.notebook(
                    p,
                    JsonCodecs.notebook(
                            NotebookInspection.inspect(
                                    volume(stack),
                                    dev.elementalcircuits.text.Texts.get(
                                            "text.elementalcircuits.server.workshop._book_service.003"))),
                    true);
        } catch (RuntimeException e) {
            message(p, e);
        }
    }

    private static void rate(ServerPlayer p) {
        long now = p.serverLevel().getGameTime();
        if (LAST.containsKey(p.getUUID()) && now - LAST.get(p.getUUID()) < 2)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.server.workshop._book_service.004"));
        LAST.put(p.getUUID(), now);
    }

    public static void notebookRequest(
            ServerPlayer p, UUID id, int action, int page, String title, String payload) {
        ItemStack book = ItemStack.EMPTY;
        try {
            book = findNotebook(p, id);
            var v = volume(book);
            if (action == 5 || action == 6) {
                openNotebook(p, book);
                return;
            }
            if (!v.identity().equals(id))
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.server.workshop._book_service.005"));
            rate(p);
            var next =
                    switch (action) {
                        case 0 -> v.select(page);
                        case 1 -> v.add(title, BlueprintTextCode.decode(payload));
                        case 2 -> v.rename(page, title);
                        case 3 -> v.remove(page);
                        case 4 -> v;
                        default ->
                                throw new IllegalArgumentException(
                                        dev.elementalcircuits.text.Texts.get(
                                                "text.elementalcircuits.server.workshop._book_service.006"));
                    };
            write(book, next);
            BookNetwork.notebook(
                    p,
                    JsonCodecs.notebook(
                            NotebookInspection.inspect(
                                    next,
                                    dev.elementalcircuits.text.Texts.get(
                                            "text.elementalcircuits.server.workshop._book_service.007"))),
                    false);
        } catch (RuntimeException e) {
            try {
                if (!book.isEmpty())
                    BookNetwork.notebook(
                            p,
                            JsonCodecs.notebook(
                                    NotebookInspection.inspect(
                                            volume(book),
                                            dev.elementalcircuits.text.Texts.get(
                                                            "text.elementalcircuits.server.workshop._book_service.008")
                                                    + e.getMessage())),
                            false);
                else message(p, e);
            } catch (RuntimeException invalid) {
                message(p, invalid);
            }
        }
    }

    public static ItemStack heldSpellbook(ServerPlayer p) {
        var off = p.getOffhandItem();
        if (off.is(ElementalCircuits.SPELL_BOOK.get())) return off;
        var main = p.getMainHandItem();
        if (main.is(ElementalCircuits.SPELL_BOOK.get())) return main;
        throw new IllegalArgumentException(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.server.workshop._book_service.009"));
    }

    public static ItemStack castCarrier(ServerPlayer p, ItemStack wand) {
        return CastingEquipment.carrier(p, wand);
    }

    public static ItemStack equipment(ServerPlayer p, ItemStack carrier) {
        return CastingEquipment.wand(p, carrier);
    }

    public static boolean pairedBookPowered(ServerPlayer p) {
        return CastingEquipment.pairedBookPowered(p);
    }

    public static void openSpellbook(ServerPlayer p, ItemStack book) {
        try {
            if (!book.is(ElementalCircuits.SPELL_BOOK.get()))
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.server.workshop._book_service.010"));
            var a = CarrierRepository.load(book);
            BookNetwork.spellbook(
                    p,
                    JsonCodecs.spellbook(
                            SpellbookInspection.inspect(
                                    a,
                                    dev.elementalcircuits.text.Texts.get(
                                            "text.elementalcircuits.server.workshop._book_service.011"))),
                    true);
            hud(p);
        } catch (RuntimeException e) {
            message(p, e);
        }
    }

    public static void spellRequest(ServerPlayer p, UUID id, int action, int value) {
        spellRequest(p, id, id, action, value);
    }

    public static void spellRequest(ServerPlayer p, UUID id, UUID source, int action, int value) {
        try {
            if (action >= 6 && action <= 8) {
                selectorRequest(p, id, source, action, value);
                return;
            }
            var book = heldSpellbook(p);
            var a = CarrierRepository.load(book);
            if (!a.identity().equals(id) && !(id.equals(ZERO) && action == 2))
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.server.workshop._book_service.012"));
            if (action != 2 && action != 5) rate(p);
            switch (action) {
                case 0 -> {
                    CastingEquipment.wand(p, book);
                    BookSelection.cycle(a, value);
                    activateBook(p, book);
                    CarrierRepository.commit(book);
                    hud(p);
                    return;
                }
                case 1 -> {
                    a.select(value);
                    activateBook(p, book);
                }
                case 2 -> {
                    openSpellbook(p, book);
                    return;
                }
                case 3 -> {
                    CarrierService.open(p);
                    return;
                }
                case 5 -> {}
                case 4 -> {
                    a.circuit(value);
                    if (value != a.selected())
                        throw new IllegalArgumentException(
                                dev.elementalcircuits.text.Texts.get(
                                        "text.elementalcircuits.server.workshop._book_service.013"));
                    CastingEquipment.wand(p, book);
                    activateBook(p, book);
                    CarrierService.start(p, book);
                }
                default ->
                        throw new IllegalArgumentException(
                                dev.elementalcircuits.text.Texts.get(
                                        "text.elementalcircuits.server.workshop._book_service.014"));
            }
            CarrierRepository.commit(book);
            BookNetwork.spellbook(
                    p,
                    JsonCodecs.spellbook(
                            SpellbookInspection.inspect(
                                    a,
                                    dev.elementalcircuits.text.Texts.get(
                                                    "text.elementalcircuits.server.workshop._book_service.015")
                                            + a.selected()
                                            + dev.elementalcircuits.text.Texts.get(
                                                    "text.elementalcircuits.server.workshop._book_service.016"))),
                    false);
            hud(p);
        } catch (RuntimeException e) {
            message(p, e);
        }
    }

    private static void activateBook(ServerPlayer p, ItemStack book) {
        if (p.getOffhandItem() == book && p.getMainHandItem().is(ElementalCircuits.WAND.get())) {
            var a = CarrierRepository.load(book);
            CastingEquipment.select(p, a.identity(), a.selected());
        }
    }

    private static ItemStack selectionWand(ServerPlayer p) {
        var wand = p.getMainHandItem();
        if (!wand.is(ElementalCircuits.WAND.get()))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.server.workshop._book_service.017"));
        return wand;
    }

    public static CircuitSelection selection(ServerPlayer p) {
        var wand = selectionWand(p);
        var book = p.getOffhandItem();
        return CircuitSelection.inspect(
                CarrierRepository.load(wand),
                book.is(ElementalCircuits.SPELL_BOOK.get()) ? CarrierRepository.load(book) : null,
                CarrierRepository.identity(CastingEquipment.carrier(p, wand)));
    }

    private static void selectorRequest(
            ServerPlayer p, UUID id, UUID source, int action, int value) {
        var wand = selectionWand(p);
        if (!CarrierRepository.identity(wand).equals(id))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.server.workshop._book_service.018"));
        var view = selection(p);
        if (action != 6) {
            rate(p);
            BookSelection.Choice choice;
            if (action == 7) {
                choice = new BookSelection.Choice(source, value);
                if (view.slots().stream().noneMatch(s -> s.choice().equals(choice)))
                    throw new IllegalArgumentException(
                            dev.elementalcircuits.text.Texts.get(
                                    "text.elementalcircuits.server.workshop._book_service.019"));
            } else {
                choice =
                        BookSelection.cycle(
                                view.slots().stream().map(CircuitSelection.Slot::choice).toList(),
                                view.current().choice(),
                                value);
            }
            CastingEquipment.select(p, choice.carrier(), choice.slot());
            view = selection(p);
        }
        BookNetwork.selector(p, JsonCodecs.selection(view));
        hud(p);
    }

    public static void hud(ServerPlayer p) {
        ItemStack stack =
                p.getMainHandItem().is(ElementalCircuits.WAND.get())
                        ? CastingEquipment.carrier(p, p.getMainHandItem())
                        : p.getOffhandItem().is(ElementalCircuits.SPELL_BOOK.get())
                                ? p.getOffhandItem()
                                : ItemStack.EMPTY;
        if (!stack.isEmpty()) {
            var a = CarrierRepository.load(stack);
            BookNetwork.hud(
                    p,
                    a.identity(),
                    a.selected(),
                    a.slots().size(),
                    a.circuit(a.selected()).powered(),
                    stack.is(ElementalCircuits.SPELL_BOOK.get()));
        }
    }

    private static void message(ServerPlayer p, RuntimeException e) {
        p.displayClientMessage(
                Component.literal(
                        e.getMessage() == null
                                ? dev.elementalcircuits.text.Texts.get(
                                        "text.elementalcircuits.server.workshop._book_service.020")
                                : e.getMessage()),
                true);
    }

    public static void forget(UUID id) {
        LAST.remove(id);
    }
}
