package dev.elementalcircuits.server.workshop;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;

/**
 * Vanilla container-button packets provide server ownership and distance checks. No client
 * inventory authority.
 */
public final class RuneEngravingMenu extends AbstractContainerMenu {
    private final ContainerLevelAccess access;

    public RuneEngravingMenu(int id, Inventory inventory) {
        this(id, inventory, ContainerLevelAccess.NULL);
    }

    public RuneEngravingMenu(int id, Inventory inventory, ContainerLevelAccess access) {
        super(RuneEngraving.MENU.get(), id);
        this.access = access;
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 150 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, 208));
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, RuneEngraving.TABLE.get());
    }

    @Override
    public boolean clickMenuButton(Player player, int button) {
        if (!(player instanceof ServerPlayer server)
                || !stillValid(player)
                || !player.getAbilities().mayBuild
                || button < 0
                || button >= RuneEngraving.KEYS.size()) return false;
        if (!player.isCreative()
                && (MaterialInventory.count(server, Items.PAPER) < 1
                        || MaterialInventory.count(server, RuneEngraving.DUST.get()) < 1)) {
            player.displayClientMessage(
                    Component.translatable("message.elementalcircuits.engraving_materials"), true);
            return false;
        }
        if (!player.isCreative()) {
            player.getInventory()
                    .getItem(MaterialInventory.inventory(server, Items.PAPER))
                    .shrink(1);
            player.getInventory()
                    .getItem(MaterialInventory.inventory(server, RuneEngraving.DUST.get()))
                    .shrink(1);
        }
        MaterialInventory.give(
                server,
                new ItemStack(RuneEngraving.RUNES.get(RuneEngraving.KEYS.get(button)).get()));
        player.getInventory().setChanged();
        broadcastChanges();
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
