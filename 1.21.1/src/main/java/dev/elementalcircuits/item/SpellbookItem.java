package dev.elementalcircuits.item;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.persistence.ItemAppearance;
import dev.elementalcircuits.server.*;
import dev.elementalcircuits.server.circuit.CarrierService;
import dev.elementalcircuits.server.workshop.BookService;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

import java.util.List;

public final class SpellbookItem extends Item {
    public SpellbookItem(Properties p) {
        super(p);
    }

    @Override
    public boolean isEnchantable(net.minecraft.world.item.ItemStack stack) {
        return true;
    }

    @Override
    public int getEnchantmentValue() {
        return 15;
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack a, ItemStack b, boolean changed) {
        return ItemAppearance.reequipBook(a, b, changed, false);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level l, Player p, InteractionHand hand) {
        if (!l.isClientSide && p instanceof ServerPlayer server)
            BookService.openSpellbook(server, p.getItemInHand(hand));
        return InteractionResultHolder.sidedSuccess(p.getItemInHand(hand), l.isClientSide);
    }

    @Override
    public void inventoryTick(ItemStack s, Level l, Entity e, int slot, boolean selected) {
        if (!l.isClientSide && e instanceof ServerPlayer p) {
            try {
                CarrierService.identity(s);
                if (p.getOffhandItem() == s && p.getMainHandItem().is(ElementalCircuits.WAND.get()))
                    CarrierService.tick(s, p);
                else {
                    var a = CarrierService.assembly(s);
                    if (a.powered()) {
                        a.stopAll();
                        CarrierService.persist(s);
                    }
                }
            } catch (RuntimeException bad) {
                CarrierService.corrupt(s, p);
            }
        }
    }

    @Override
    public boolean onEntityItemUpdate(ItemStack s, ItemEntity e) {
        if (!e.level().isClientSide) CarrierService.tickDropped(s, e);
        return false;
    }

    @Override
    public void appendHoverText(
            ItemStack s, Item.TooltipContext context, List<Component> t, TooltipFlag f) {
        t.add(Component.translatable("tooltip.elementalcircuits.spellbook_capacity"));
        t.add(Component.translatable("tooltip.elementalcircuits.spellbook_wheel"));
        t.add(Component.translatable("tooltip.elementalcircuits.spellbook_inscribe"));
    }
}
