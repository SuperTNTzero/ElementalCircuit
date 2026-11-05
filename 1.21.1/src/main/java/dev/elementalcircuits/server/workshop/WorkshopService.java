package dev.elementalcircuits.server.workshop;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.codec.JsonCodecs;
import dev.elementalcircuits.adapter.resources.WandResources;
import dev.elementalcircuits.block.WandWorkbenchBlock;
import dev.elementalcircuits.block.WorkstationBlockEntity;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.book.WorkshopInspection;
import dev.elementalcircuits.network.BookNetwork;
import dev.elementalcircuits.server.casting.CastingEquipment;
import dev.elementalcircuits.server.circuit.CarrierService;
import dev.elementalcircuits.server.equipment.WandEquipment;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.*;

/** The server owns a specific wand/workbench session and revalidates it before each mutation. */
public final class WorkshopService {
    private record Session(
            UUID wand, ResourceKey<Level> dimension, BlockPos position, ItemStack held) {}

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<UUID, Long> LAST = new HashMap<>();

    private WorkshopService() {}

    private static ItemStack valid(ServerPlayer p, Session s) {
        var wand = p.getMainHandItem();
        if (s == null
                || p.serverLevel().dimension() != s.dimension
                || !p.serverLevel().hasChunkAt(s.position)
                || !p.serverLevel()
                        .getBlockState(s.position)
                        .is(ElementalCircuits.WAND_WORKBENCH.get())
                || p.position().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(s.position))
                        > 64
                || !WandWorkbenchBlock.complete(p.serverLevel(), s.position)
                || !p.isAlive()
                || !p.getAbilities().mayBuild
                || !p.serverLevel().mayInteract(p, s.position)
                || wand != s.held
                || !wand.is(ElementalCircuits.WAND.get())
                || !CarrierService.identity(wand).equals(s.wand))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.server.workshop._workshop_service.001"));
        return wand;
    }

    public static void open(ServerPlayer p, BlockPos pos) {
        try {
            if (!p.getMainHandItem().is(ElementalCircuits.WAND.get()))
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.server.workshop._workshop_service.002"));
            if (!WandWorkbenchBlock.ensureComplete(p, pos))
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.server.workshop._workshop_service.003"));
            var s =
                    new Session(
                            CarrierService.identity(p.getMainHandItem()),
                            p.serverLevel().dimension(),
                            pos.immutable(),
                            p.getMainHandItem());
            var wand = valid(p, s);
            SESSIONS.put(p.getUUID(), s);
            sync(
                    p,
                    wand,
                    s,
                    true,
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.server.workshop._workshop_service.004"));
        } catch (RuntimeException e) {
            p.displayClientMessage(Component.literal(e.getMessage()), true);
        }
    }

    public static void request(
            ServerPlayer p, UUID wandId, long position, int action, String value) {
        try {
            var s = SESSIONS.get(p.getUUID());
            var wand = valid(p, s);
            if (!s.wand.equals(wandId) || s.position.asLong() != position)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.server.workshop._workshop_service.005"));
            if (action == 3) {
                if (p.serverLevel().getBlockEntity(s.position) instanceof WorkstationBlockEntity b)
                    b.clear();
                SESSIONS.remove(p.getUUID());
                return;
            }
            if (action != 2) {
                long now = p.serverLevel().getGameTime();
                if (LAST.containsKey(p.getUUID()) && now - LAST.get(p.getUUID()) < 2)
                    throw new IllegalArgumentException(
                            dev.elementalcircuits.text.Texts.get(
                                    "text.elementalcircuits.server.workshop._workshop_service.006"));
                LAST.put(p.getUUID(), now);
                if (CarrierService.assembly(wand).powered()
                        || CastingEquipment.pairedBookPowered(p))
                    throw new IllegalArgumentException(
                            dev.elementalcircuits.text.Texts.get(
                                    "text.elementalcircuits.server.workshop._workshop_service.007"));
                if (action == 0) WandEquipment.install(p, wand, value);
                else if (action == 1) WandEquipment.repair(p, wand);
                else
                    throw new IllegalArgumentException(
                            dev.elementalcircuits.text.Texts.get(
                                    "text.elementalcircuits.server.workshop._workshop_service.008"));
            }
            sync(
                    p,
                    wand,
                    s,
                    false,
                    action == 2
                            ? dev.elementalcircuits.text.Texts.get(
                                    "text.elementalcircuits.server.workshop._workshop_service.009")
                            : dev.elementalcircuits.text.Texts.get(
                                    "text.elementalcircuits.server.workshop._workshop_service.010"));
        } catch (RuntimeException e) {
            var s = SESSIONS.get(p.getUUID());
            try {
                sync(
                        p,
                        valid(p, s),
                        s,
                        false,
                        dev.elementalcircuits.text.Texts.get(
                                        "text.elementalcircuits.server.workshop._workshop_service.011")
                                + e.getMessage());
            } catch (RuntimeException invalid) {
                p.displayClientMessage(
                        Component.literal(
                                dev.elementalcircuits.text.Texts.get(
                                                "text.elementalcircuits.server.workshop._workshop_service.012")
                                        + e.getMessage()),
                        true);
                SESSIONS.remove(p.getUUID());
            }
        }
    }

    private static void sync(
            ServerPlayer p, ItemStack wand, Session s, boolean open, String message) {
        if (p.serverLevel().getBlockEntity(s.position) instanceof WorkstationBlockEntity b)
            b.show(p, wand);
        var stock = new HashMap<String, Integer>();
        for (var part : WandResources.definitions().parts())
            stock.put(part.id(), count(p, ElementalCircuits.PARTS.get(part.id()).get()));
        var v =
                new WorkshopInspection(
                        s.wand,
                        s.position.asLong(),
                        WandEquipment.assembly(wand),
                        wand.getDamageValue(),
                        stock,
                        count(p, ElementalCircuits.WOOD_PLATE.get()),
                        message,
                        true);
        BookNetwork.workshop(p, JsonCodecs.workshop(v), open);
    }

    private static int count(ServerPlayer p, net.minecraft.world.item.Item item) {
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++)
            if (p.getInventory().getItem(i).is(item)) n += p.getInventory().getItem(i).getCount();
        return n;
    }

    public static void forget(UUID p) {
        SESSIONS.remove(p);
        LAST.remove(p);
    }
}
