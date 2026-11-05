package dev.elementalcircuits.server;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.block.CircuitBlockEntity;
import dev.elementalcircuits.network.CircuitNetwork;
import dev.elementalcircuits.server.casting.WandCasting;
import dev.elementalcircuits.server.circuit.CarrierService;
import dev.elementalcircuits.server.circuit.WorldCircuitLinks;
import dev.elementalcircuits.server.circuit.WorldCircuits;
import dev.elementalcircuits.server.equipment.EquipmentCasting;
import dev.elementalcircuits.server.mana.PlayerMana;
import dev.elementalcircuits.server.mana.WorldManaField;
import dev.elementalcircuits.server.probe.ProbeService;
import dev.elementalcircuits.server.spell.SpellFeedback;
import dev.elementalcircuits.server.spell.SpellSounds;
import dev.elementalcircuits.server.spell.SpellVisuals;
import dev.elementalcircuits.server.spell.WorldIllumination;
import dev.elementalcircuits.server.spell.WorldSpells;
import dev.elementalcircuits.server.workshop.BookService;
import dev.elementalcircuits.server.workshop.InscriptionAccess;
import dev.elementalcircuits.server.workshop.WorkshopService;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.*;

public final class ServerEvents {
    private static final java.util.Set<java.util.UUID> CORRUPT_MANA = new java.util.HashSet<>();

    private ServerEvents() {}

    @SubscribeEvent
    public static void equipment(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            EquipmentCasting.tick(p);
            if (p.tickCount % 10 == 0)
                dev.elementalcircuits.server.circuit.FixedRelationSight.sync(p);
        }
    }

    @SubscribeEvent
    public static void reactive(
            net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer p)
            EquipmentCasting.hurt(p, event.getSource(), event.getNewDamage());
    }

    @SubscribeEvent
    public static void commands(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
        AdminCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void world(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel level) {
            WorldManaField.get(level).tick(level);
            WorldCircuits.tick(level);
            dev.elementalcircuits.server.spell.WorldConnections.get(level).tick(level);
            ProbeService.tick(level);
            WorldSpells.get(level).tick(level);
            WorldIllumination.get(level).tick(level);
            SpellVisuals.flush(level);
            SpellSounds.flush(level);
            SpellFeedback.flush(level);
        }
    }

    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        WandCasting.tick(player);
        InscriptionAccess.tick(player);
        try {
            var pool = PlayerMana.get(player);
            pool.tick();
            PlayerMana.save(player);
            if (player.tickCount % 10 == 0) {
                CircuitNetwork.syncPolicy(player);
                CircuitNetwork.sendMana(player, pool.available(), pool.capacity());
                try {
                    BookService.hud(player);
                } catch (RuntimeException ignored) {
                }
            }
        } catch (RuntimeException e) {
            if (CORRUPT_MANA.add(player.getUUID())) {
                player.sendSystemMessage(
                        net.minecraft.network.chat.Component.literal(
                                dev.elementalcircuits.text.Texts.get(
                                        "text.elementalcircuits.server._server_events.001")));
                org.slf4j.LoggerFactory.getLogger("ElementalCircuits")
                        .error(
                                dev.elementalcircuits.text.Texts.get(
                                        "text.elementalcircuits.server._server_events.002"),
                                player.getUUID(),
                                e.getMessage());
            }
        }
    }

    @SubscribeEvent
    public static void unload(net.neoforged.neoforge.event.level.LevelEvent.Unload event) {
        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel level) {
            ProbeService.unload(level);
            WorldCircuits.unload(level);
            SpellVisuals.unload(level);
            SpellSounds.unload(level);
            SpellFeedback.unload(level);
        }
    }

    @SubscribeEvent
    public static void chunkLoad(net.neoforged.neoforge.event.level.ChunkEvent.Load event) {
        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel level) {
            var p = event.getChunk().getPos();
            WorldManaField.get(level).loaded(p.x, p.z);
        }
    }

    @SubscribeEvent
    public static void chunkUnload(net.neoforged.neoforge.event.level.ChunkEvent.Unload event) {
        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel level) {
            var p = event.getChunk().getPos();
            WorldManaField.get(level).unloaded(p.x, p.z);
        }
    }

    @SubscribeEvent
    public static void probeInteraction(
            net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if (event.getItemStack().is(ElementalCircuits.PROBE_WAND.get())
                || event.getItemStack().is(ElementalCircuits.CONTROL_WAND.get()))
            event.setUseBlock(net.neoforged.neoforge.common.util.TriState.FALSE);
    }

    @SubscribeEvent
    public static void breaking(net.neoforged.neoforge.event.level.BlockEvent.BreakEvent event) {
        if (event.getLevel().getBlockEntity(event.getPos()) instanceof CircuitBlockEntity block
                && event.getPlayer() instanceof ServerPlayer player) {
            try {
                if (!block.owns(player))
                    throw new IllegalArgumentException(
                            dev.elementalcircuits.text.Texts.get(
                                    "text.elementalcircuits.server._server_events.003"));
                if (WorldCircuitLinks.get(player.serverLevel())
                        .references(block.assembly().identity(), 0))
                    throw new IllegalArgumentException(
                            dev.elementalcircuits.text.Texts.get(
                                    "text.elementalcircuits.server._server_events.004"));
                CarrierService.persist(block.carrier());
            } catch (RuntimeException e) {
                event.setCanceled(true);
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal(
                                e.getMessage() == null
                                        ? dev.elementalcircuits.text.Texts.get(
                                                "text.elementalcircuits.server._server_events.005")
                                        : e.getMessage()),
                        true);
            }
        }
    }

    @SubscribeEvent
    public static void clone(PlayerEvent.Clone event) {
        if (event.getOriginal() instanceof ServerPlayer oldPlayer
                && event.getEntity() instanceof ServerPlayer newPlayer)
            PlayerMana.clone(oldPlayer, newPlayer);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            WandCasting.cancel(player);
            try {
                PlayerMana.save(player);
            } catch (RuntimeException ignored) {
            }
            ProbeService.forget(player);
            InscriptionAccess.close(player);
            InscriptionAccess.forget(player.getUUID());
            PlayerMana.forget(player);
            CarrierService.forget(player.getUUID());
            WorkshopService.forget(player.getUUID());
            BookService.forget(player.getUUID());
            CORRUPT_MANA.remove(player.getUUID());
        }
    }
}
