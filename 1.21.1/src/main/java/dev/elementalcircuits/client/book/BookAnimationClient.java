package dev.elementalcircuits.client.book;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.persistence.ItemAppearance;
import dev.elementalcircuits.client.*;
import dev.elementalcircuits.client.screen.NotebookImportScreen;
import dev.elementalcircuits.client.screen.NotebookScreen;
import dev.elementalcircuits.client.screen.SpellbookScreen;
import dev.elementalcircuits.domain.equipment.WandParts;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

import java.util.*;

/** Two hand tracks and one current-page track; no live circuit references. */
@EventBusSubscriber(modid = ElementalCircuits.ID, value = Dist.CLIENT)
public final class BookAnimationClient {
    private record Key(Item item, UUID identity, WandParts.Assembly parts, int slot) {}

    private static final class Track {
        Key key;
        int age;
        final BookMotion motion = new BookMotion();

        void clear() {
            key = null;
            age = 0;
            motion.reset();
        }
    }

    private static final Track MAIN = new Track(), OFF = new Track();
    private static final BookMotion UI = new BookMotion();
    private static Screen screen;
    private static Object world;

    private BookAnimationClient() {}

    public static boolean book(ItemStack stack) {
        return stack.is(ElementalCircuits.SPELL_BOOK.get())
                || stack.is(ElementalCircuits.NOTEBOOK.get());
    }

    private static Key key(ItemStack s, int slot) {
        return new Key(
                s.getItem(),
                s.is(ElementalCircuits.NOTEBOOK.get())
                        ? ItemAppearance.notebookIdentity(s)
                        : ItemAppearance.identity(s),
                s.is(ElementalCircuits.WAND.get()) ? ItemAppearance.parts(s) : null,
                slot);
    }

    private static Track track(InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? MAIN : OFF;
    }

    private static void update(Track t, ItemStack s, int slot) {
        if (!book(s) && !s.is(ElementalCircuits.WAND.get())) {
            t.clear();
            return;
        }
        Key next = key(s, slot);
        if (!next.equals(t.key)) {
            t.clear();
            t.key = next;
        } else t.age = Math.min(12, t.age + 1);
        t.motion.tick(book(s));
    }

    public static boolean stableWand(ItemStack stack, HumanoidArm arm) {
        var p = Minecraft.getInstance().player;
        if (p == null) return false;
        InteractionHand hand =
                arm == p.getMainArm() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        var t = track(hand);
        return t.age >= 5
                && key(stack, hand == InteractionHand.MAIN_HAND ? p.getInventory().selected : -1)
                        .equals(t.key)
                && ItemAppearance.sameCarrier(stack, p.getItemInHand(hand));
    }

    public static BookMotion.Pose pose(ItemStack stack, ItemDisplayContext context, float partial) {
        if (context == ItemDisplayContext.GUI
                || context == ItemDisplayContext.GROUND
                || context == ItemDisplayContext.FIXED
                || context == ItemDisplayContext.HEAD) return new BookMotion.Pose(0, -1, 1);
        var p = Minecraft.getInstance().player;
        if (p != null)
            for (var hand : InteractionHand.values()) {
                var held = p.getItemInHand(hand);
                if (held == stack) {
                    var t = track(hand);
                    return t.motion.sample(partial);
                }
            }
        // Other entities have no local UI clock. Their held books use a stable open pose.
        return new BookMotion.Pose(1, -1, 1);
    }

    public static BookMotion.Pose ui(float partial) {
        return UI.sample(partial);
    }

    public static void page(UUID identity, int direction) {
        for (var t : List.of(MAIN, OFF))
            if (t.key != null && identity.equals(t.key.identity())) t.motion.flip(direction);
        UI.flip(direction);
        Minecraft.getInstance()
                .getSoundManager()
                .play(
                        net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, 1));
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (world != mc.level) {
            clear();
            world = mc.level;
        }
        if (mc.player == null || mc.isPaused()) return;
        update(MAIN, mc.player.getMainHandItem(), mc.player.getInventory().selected);
        update(OFF, mc.player.getOffhandItem(), -1);
        Screen next =
                mc.screen instanceof SpellbookScreen
                                || mc.screen instanceof NotebookScreen
                                || mc.screen instanceof NotebookImportScreen
                        ? mc.screen
                        : null;
        if (next != screen) {
            screen = next;
            UI.reset();
        }
        UI.tick(next != null);
    }

    private static void clear() {
        MAIN.clear();
        OFF.clear();
        UI.reset();
        screen = null;
        world = null;
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }
}
