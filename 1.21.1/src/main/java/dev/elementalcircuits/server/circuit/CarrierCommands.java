package dev.elementalcircuits.server.circuit;

import static dev.elementalcircuits.server.workshop.MaterialInventory.*;

import dev.elementalcircuits.*;
import dev.elementalcircuits.adapter.codec.JsonCodecs;
import dev.elementalcircuits.adapter.config.ServerSettings;
import dev.elementalcircuits.adapter.persistence.CarrierRepository;
import dev.elementalcircuits.application.observation.SpellSound;
import dev.elementalcircuits.block.CircuitBlockEntity;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.circuit.Blueprint;
import dev.elementalcircuits.domain.circuit.Rules;
import dev.elementalcircuits.domain.circuit.TickCircuit;
import dev.elementalcircuits.domain.combat.Element;
import dev.elementalcircuits.server.casting.CastingEquipment;
import dev.elementalcircuits.server.equipment.EquipmentCasting;
import dev.elementalcircuits.server.equipment.WizardEquipment;
import dev.elementalcircuits.server.spell.SpellFeedback;
import dev.elementalcircuits.server.spell.SpellSounds;
import dev.elementalcircuits.server.workshop.InscriptionAccess;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;

import java.util.*;

/** Validated player intents; the only editor command handler. */
public final class CarrierCommands {
    private static final Map<UUID, Long> LAST_EDIT = new HashMap<>();

    private CarrierCommands() {}

    public static void start(ServerPlayer player, ItemStack stack) {
        try {
            var a = CarrierRepository.load(stack);
            TickCircuit c = a.circuit(a.selected());
            if (c.powered()) {
                c.stop();
                player.displayClientMessage(
                        Component.literal(
                                dev.elementalcircuits.text.Texts.get(
                                        "text.elementalcircuits.server.circuit._carrier_commands.001")),
                        true);
            } else if (CastingEquipment.broken(CastingEquipment.wand(player, stack)))
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.server.circuit._carrier_commands.002"));
            else if (a.boardBroken(a.selected()))
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.server.circuit._carrier_commands.003"));
            else if (!c.start())
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.server.circuit._carrier_commands.004"));
            CarrierRepository.commit(stack);
            if (c.powered()) {
                SpellFeedback.started(
                        player.serverLevel(), player.getEyePosition(), player.getLookAngle());
                SpellSounds.observe(
                        player.serverLevel(),
                        SpellSound.Kind.START,
                        Element.PURE,
                        player.position());
            }
        } catch (RuntimeException e) {
            player.displayClientMessage(
                    Component.literal(
                            e.getMessage() == null
                                    ? dev.elementalcircuits.text.Texts.get(
                                            "text.elementalcircuits.server.circuit._carrier_commands.005")
                                    : e.getMessage()),
                    true);
        }
    }

    public static void request(ServerPlayer player, UUID id, int action, String payload) {
        request(player, id, 0, action, payload);
    }

    public static void request(ServerPlayer player, UUID id, int slot, int action, String payload) {
        request(player, id, slot, action, payload, new UUID(0, 0));
    }

    public static void request(
            ServerPlayer player, UUID id, int slot, int action, String payload, UUID token) {
        request(player, id, slot, action, payload, token, 1);
    }

    public static void request(
            ServerPlayer player,
            UUID id,
            int slot,
            int action,
            String payload,
            UUID token,
            int layer) {
        ItemStack stack = player.getMainHandItem();
        CircuitBlockEntity block = null;
        try {
            if (action == 14) {
                InscriptionAccess.close(player, id);
                return;
            }
            var engravingDraft = InscriptionAccess.draft(player);
            boolean engraving =
                    !engravingDraft.isEmpty()
                            && CarrierRepository.identity(engravingDraft).equals(id);
            if (engraving) stack = engravingDraft;
            else {
                if (player.getOffhandItem().is(ElementalCircuits.SPELL_BOOK.get())
                        && CarrierRepository.identity(player.getOffhandItem()).equals(id))
                    stack = player.getOffhandItem();
                var gear = EquipmentCasting.owned(player, id);
                if (!gear.isEmpty()) stack = gear;
                if ((!WizardEquipment.carrier(stack)
                                && !stack.is(ElementalCircuits.WAND.get())
                                && !stack.is(ElementalCircuits.SPELL_BOOK.get())
                                && !stack.is(ElementalCircuits.CIRCUIT_ITEM.get()))
                        || !CarrierRepository.identity(stack).equals(id)) {
                    block = WorldCircuits.block(player.serverLevel(), id);
                    if (block == null || !block.allowed(player)) return;
                    stack = block.carrier();
                }
            }
            if (engraving && (slot != 0 && slot != 1 || action != 0 && action != 5))
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.server.circuit._carrier_commands.006"));
            if (action == 0
                    || action == 4
                    || action == 9
                    || action == 10
                    || action == 11
                    || action == 15
                    || action == 16
                    || action == 17
                    || action == 18
                    || action == 19) InscriptionAccess.require(player, stack, block);
            if (action == 20) InscriptionAccess.require(player, stack, block);
            if (WizardEquipment.carrier(stack) && (action == 1 || action == 2))
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.server.circuit._carrier_commands.007"));
            var a = CarrierRepository.load(stack);
            if (slot == 0) slot = a.selected();
            TickCircuit old = a.circuit(slot);
            var edited = a.layers(slot).definition(layer);
            String message =
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.server.circuit._carrier_commands.008");
            long now = player.serverLevel().getGameTime();
            Long last = LAST_EDIT.get(player.getUUID());
            if (action != 5) {
                if (last != null && now - last < 2)
                    throw new IllegalArgumentException(
                            dev.elementalcircuits.text.Texts.get(
                                    "text.elementalcircuits.server.circuit._carrier_commands.009"));
                LAST_EDIT.put(player.getUUID(), now);
            }
            if (layer != 1 && (action == 1 || action == 2 || action == 3 || action == 13))
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.server.circuit._carrier_commands.010"));
            switch (action) {
                case 20 -> {
                    if (a.powered())
                        throw new IllegalArgumentException(
                                dev.elementalcircuits.text.Texts.get(
                                        "text.elementalcircuits.server.circuit._carrier_commands.011"));
                    var condition = a.board(slot);
                    if (condition == null || condition.spent() == 0)
                        throw new IllegalArgumentException(
                                dev.elementalcircuits.text.Texts.get(
                                        "text.elementalcircuits.server.circuit._carrier_commands.012"));
                    int source = inventory(player, plate(old.definition().rules().material()));
                    a.renewBoard(slot, ServerSettings.boardSafety());
                    player.getInventory().getItem(source).shrink(1);
                    message =
                            dev.elementalcircuits.text.Texts.get(
                                    "text.elementalcircuits.server.circuit._carrier_commands.013");
                }
                case 0, 4 -> {
                    if (block != null
                            && WorldCircuitLinks.get(player.serverLevel()).references(id, slot))
                        throw new IllegalArgumentException(
                                dev.elementalcircuits.text.Texts.get(
                                        "text.elementalcircuits.server.circuit._carrier_commands.014"));
                    if (action == 4 && layer != 1)
                        throw new IllegalArgumentException(
                                dev.elementalcircuits.text.Texts.get(
                                        "text.elementalcircuits.server.circuit._carrier_commands.015"));
                    Blueprint draft =
                            action == 4 && !payload.startsWith("{")
                                    ? old.definition().withMaterial(Rules.Material.valueOf(payload))
                                    : JsonCodecs.readBlueprint(payload);
                    var previous = old.definition().rules().material();
                    boolean recoverPrevious = a.board(slot) == null || a.board(slot).spent() == 0;
                    var material = draft.rules().material();
                    if (action == 0 && material != previous)
                        throw new IllegalArgumentException(
                                dev.elementalcircuits.text.Texts.get(
                                        "text.elementalcircuits.server.circuit._carrier_commands.016"));
                    if (action == 4 && material == previous)
                        throw new IllegalArgumentException(
                                dev.elementalcircuits.text.Texts.get(
                                        "text.elementalcircuits.server.circuit._carrier_commands.017"));
                    Blueprint checked =
                            new Blueprint(
                                    draft.version(),
                                    action == 4
                                            ? old.definition().rules().withMaterial(material)
                                            : old.definition().rules(),
                                    draft.runes(),
                                    draft.routes(),
                                    draft.continuous());
                    int inventory = action == 4 ? inventory(player, plate(material)) : -1;
                    if (engraving && checked.runes().isEmpty())
                        throw new IllegalArgumentException(
                                dev.elementalcircuits.text.Texts.get(
                                        "text.elementalcircuits.server.circuit._carrier_commands.018"));
                    ServerSettings.admit(checked);
                    a.replaceLayer(slot, layer, checked);
                    if (action == 4) {
                        player.getInventory().getItem(inventory).shrink(1);
                        // A worn board cannot be laundered into a pristine material item.
                        if (recoverPrevious) give(player, new ItemStack(plate(previous)));
                        message =
                                dev.elementalcircuits.text.Texts.get(
                                        "text.elementalcircuits.server.circuit._carrier_commands.019");
                    }
                }
                case 1 -> {
                    if (block == null
                            && CastingEquipment.broken(CastingEquipment.wand(player, stack)))
                        throw new IllegalArgumentException(
                                dev.elementalcircuits.text.Texts.get(
                                        "text.elementalcircuits.server.circuit._carrier_commands.020"));
                    if (!old.start())
                        throw new IllegalArgumentException(
                                dev.elementalcircuits.text.Texts.get(
                                        "text.elementalcircuits.server.circuit._carrier_commands.021"));
                }
                case 2 -> {
                    old.stop();
                    message =
                            dev.elementalcircuits.text.Texts.get(
                                    "text.elementalcircuits.server.circuit._carrier_commands.001");
                }
                case 3 -> {
                    if (layer != 1)
                        throw new IllegalArgumentException(
                                dev.elementalcircuits.text.Texts.get(
                                        "text.elementalcircuits.server.circuit._carrier_commands.022"));
                    old.toggleManual(Integer.parseInt(payload));
                }
                case 5 ->
                        message =
                                layer == 1
                                        ? old.status()
                                        : dev.elementalcircuits.text.Texts.get(
                                                        "text.elementalcircuits.server.circuit._carrier_commands.023")
                                                + layer;
                case 6, 7 ->
                        throw new IllegalArgumentException(
                                dev.elementalcircuits.text.Texts.get(
                                        "text.elementalcircuits.server.circuit._carrier_commands.024"));
                case 8 -> {
                    a.select(Integer.parseInt(payload));
                    layer = 1;
                    message =
                            dev.elementalcircuits.text.Texts.get(
                                            "text.elementalcircuits.server.circuit._carrier_commands.025")
                                    + a.selected();
                }
                case 9 -> {
                    var material = Rules.Material.valueOf(payload);
                    int inventory = inventory(player, plate(material));
                    a.add(ServerSettings.circuitRules().withMaterial(material));
                    player.getInventory().getItem(inventory).shrink(1);
                    layer = 1;
                    message =
                            dev.elementalcircuits.text.Texts.get(
                                            "text.elementalcircuits.server.circuit._carrier_commands.026")
                                    + a.selected();
                }
                case 10 -> {
                    var r = JsonCodecs.readLinkRequest(payload);
                    if (r.from().carrier().equals(id) && r.to().carrier().equals(id)) {
                        if (block != null
                                && WorldCircuitLinks.get(player.serverLevel())
                                        .graph()
                                        .connections()
                                        .stream()
                                        .anyMatch(c -> c.from().equals(r.from())))
                            throw new IllegalArgumentException(
                                    dev.elementalcircuits.text.Texts.get(
                                            "text.elementalcircuits.server.circuit._carrier_commands.027"));
                        a.links()
                                .connect(
                                        r.from(),
                                        r.to(),
                                        r.payload(),
                                        k -> k.equals(id) ? a : null);
                    } else {
                        if (block == null)
                            throw new IllegalArgumentException(
                                    dev.elementalcircuits.text.Texts.get(
                                            "text.elementalcircuits.server.circuit._carrier_commands.028"));
                        WorldCircuitLinks.get(player.serverLevel())
                                .connect(player.serverLevel(), player, id, r);
                    }
                    message =
                            dev.elementalcircuits.text.Texts.get(
                                    "text.elementalcircuits.server.circuit._carrier_commands.029");
                }
                case 11 -> {
                    String[] parts = payload.split(":", -1);
                    if (parts.length != 2)
                        throw new IllegalArgumentException(
                                dev.elementalcircuits.text.Texts.get(
                                        "text.elementalcircuits.server.circuit._carrier_commands.030"));
                    int link = Integer.parseInt(parts[1]);
                    if (parts[0].equals("I"))
                        a.links().disconnect(link, k -> k.equals(id) ? a : null);
                    else if (parts[0].equals("W") && block != null)
                        WorldCircuitLinks.get(player.serverLevel())
                                .disconnect(player.serverLevel(), player, id, link);
                    else
                        throw new IllegalArgumentException(
                                dev.elementalcircuits.text.Texts.get(
                                        "text.elementalcircuits.server.circuit._carrier_commands.031"));
                    message =
                            dev.elementalcircuits.text.Texts.get(
                                    "text.elementalcircuits.server.circuit._carrier_commands.032");
                }
                case 16 -> {
                    int limit =
                            ServerSettings.layerRules().depth(old.definition().rules().material());
                    layer = a.addLayer(slot, limit);
                    message =
                            dev.elementalcircuits.text.Texts.get(
                                            "text.elementalcircuits.server.circuit._carrier_commands.033")
                                    + layer;
                }
                case 17 -> {
                    if (slot != a.selected())
                        throw new IllegalArgumentException(
                                dev.elementalcircuits.text.Texts.get(
                                        "text.elementalcircuits.server.circuit._carrier_commands.034"));
                    message =
                            layer == 1
                                    ? dev.elementalcircuits.text.Texts.get(
                                            "text.elementalcircuits.server.circuit._carrier_commands.035")
                                    : dev.elementalcircuits.text.Texts.get(
                                                    "text.elementalcircuits.server.circuit._carrier_commands.036")
                                            + layer;
                }
                case 18 -> {
                    a.removeLayer(slot, layer);
                    layer = 1;
                    message =
                            dev.elementalcircuits.text.Texts.get(
                                    "text.elementalcircuits.server.circuit._carrier_commands.037");
                }
                case 19 -> {
                    a.configureLayer(
                            slot,
                            layer,
                            payload.equals("off") ? null : JsonCodecs.readTriggerBinding(payload));
                    message =
                            dev.elementalcircuits.text.Texts.get(
                                    "text.elementalcircuits.server.circuit._carrier_commands.038");
                }
                case 15 ->
                        throw new IllegalArgumentException(
                                dev.elementalcircuits.text.Texts.get(
                                        "text.elementalcircuits.server.circuit._carrier_commands.039"));
                case 13 -> {
                    old.resetLogic(Integer.parseInt(payload));
                    message =
                            dev.elementalcircuits.text.Texts.get(
                                    "text.elementalcircuits.server.circuit._carrier_commands.040");
                }
                case 12 -> {
                    if (block == null)
                        throw new IllegalArgumentException(
                                dev.elementalcircuits.text.Texts.get(
                                        "text.elementalcircuits.server.circuit._carrier_commands.041"));
                    message =
                            dev.elementalcircuits.text.Texts.get(
                                            "text.elementalcircuits.server.circuit._carrier_commands.042")
                                    + Fixed.display(block.charge(player))
                                    + dev.elementalcircuits.text.Texts.get(
                                            "text.elementalcircuits.server.circuit._carrier_commands.043");
                }
                default ->
                        throw new IllegalArgumentException(
                                dev.elementalcircuits.text.Texts.get(
                                        "text.elementalcircuits.server.circuit._carrier_commands.044"));
            }
            CarrierRepository.commit(stack);
            if (stack.is(ElementalCircuits.CIRCUIT_ITEM.get()))
                FixedCircuitAppearance.update(stack);
            if (block != null) {
                block.setChanged();
                block.refreshMaterial();
            }
            if (engraving && action == 0) {
                InscriptionAccess.finish(player, stack);
                message =
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.server.circuit._carrier_commands.045");
            }
            if (action == 1) {
                SpellFeedback.started(
                        player.serverLevel(),
                        block == null ? player.getEyePosition() : block.origin(),
                        block == null ? player.getLookAngle() : block.direction());
            }
            if (action == 1)
                SpellSounds.observe(
                        player.serverLevel(),
                        SpellSound.Kind.START,
                        Element.PURE,
                        block == null
                                ? player.position()
                                : net.minecraft.world.phys.Vec3.atCenterOf(block.getBlockPos()));
            CarrierEditor.sync(player, stack, block, false, message, token, layer);
        } catch (RuntimeException e) {
            try {
                CarrierEditor.sync(
                        player,
                        stack,
                        block,
                        false,
                        dev.elementalcircuits.text.Texts.get(
                                        "text.elementalcircuits.server.circuit._carrier_commands.046")
                                + (e.getMessage() == null
                                        ? dev.elementalcircuits.text.Texts.get(
                                                "text.elementalcircuits.server.circuit._carrier_commands.047")
                                        : e.getMessage()),
                        token,
                        layer);
            } catch (RuntimeException broken) {
                CarrierFaults.report(stack, player);
            }
        }
    }

    public static void forget(UUID player) {
        LAST_EDIT.remove(player);
    }
}
