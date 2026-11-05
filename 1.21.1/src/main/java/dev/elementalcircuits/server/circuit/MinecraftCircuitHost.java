package dev.elementalcircuits.server.circuit;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.config.ServerSettings;
import dev.elementalcircuits.adapter.persistence.CarrierRepository;
import dev.elementalcircuits.adapter.persistence.ItemData;
import dev.elementalcircuits.adapter.resources.ShapeResources;
import dev.elementalcircuits.application.runtime.CircuitHost;
import dev.elementalcircuits.block.CircuitBlockEntity;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.casting.CastContext;
import dev.elementalcircuits.domain.casting.ReleasePermit;
import dev.elementalcircuits.domain.casting.ShapeRules;
import dev.elementalcircuits.domain.casting.SpellSpec;
import dev.elementalcircuits.domain.circuit.BoardSafety;
import dev.elementalcircuits.domain.circuit.CircuitAssembly;
import dev.elementalcircuits.domain.circuit.CircuitProtection;
import dev.elementalcircuits.domain.circuit.Rune;
import dev.elementalcircuits.domain.circuit.TickCircuit;
import dev.elementalcircuits.domain.mana.ManaOrigin;
import dev.elementalcircuits.domain.mana.ManaSource;
import dev.elementalcircuits.server.casting.CastingEquipment;
import dev.elementalcircuits.server.enchantment.MagicEnchantments;
import dev.elementalcircuits.server.equipment.EquipmentCasting;
import dev.elementalcircuits.server.equipment.SpellAttributes;
import dev.elementalcircuits.server.equipment.WandEquipment;
import dev.elementalcircuits.server.equipment.WizardEquipment;
import dev.elementalcircuits.server.mana.CrystalAmplification;
import dev.elementalcircuits.server.mana.PlayerMana;
import dev.elementalcircuits.server.mana.WorldManaField;
import dev.elementalcircuits.server.mana.WorldManaRefunds;
import dev.elementalcircuits.server.spell.BoardMeltdown;
import dev.elementalcircuits.server.spell.WorldConnections;
import dev.elementalcircuits.server.spell.WorldSpells;

import net.minecraft.server.level.*;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/** Maps Minecraft ownership, finite supply and effects onto the engine-free host contract. */
final class MinecraftCircuitHost implements CircuitHost {
    private final ServerLevel level;
    private final ItemStack stack;
    private final ServerPlayer player;
    private final CircuitBlockEntity block;
    private CircuitAssembly assembly;
    private ItemStack equipment;
    private long failures;
    private boolean worn = true;
    private BoardSafety safety;
    private static final java.util.Map<ServerPlayer, Long> FAILURE_NOTICE =
            new java.util.WeakHashMap<>();

    private MinecraftCircuitHost(
            ServerLevel level, ItemStack stack, ServerPlayer player, CircuitBlockEntity block) {
        this.level = level;
        this.stack = stack;
        this.player = player;
        this.block = block;
    }

    static MinecraftCircuitHost carried(ServerLevel level, ItemStack stack, ServerPlayer player) {
        return new MinecraftCircuitHost(level, stack, player, null);
    }

    static MinecraftCircuitHost fixed(ServerLevel level, CircuitBlockEntity block) {
        return new MinecraftCircuitHost(level, block.carrier(), null, block);
    }

    @Override
    public CircuitAssembly assembly() {
        if (assembly == null) assembly = CarrierRepository.load(stack);
        return assembly;
    }

    @Override
    public boolean prepare() {
        failures = assembly.slots().stream().mapToLong(s -> assembly.circuit(s).failures()).sum();
        assembly.reformCasting(ServerSettings.castingRules());
        assembly.retimeMana(ServerSettings.count("circuit.manaSteps"));
        safety = ServerSettings.boardSafety();
        assembly.prepareBoards(safety);
        if (ItemData.read(stack).getBoolean("ElementalCircuitFault")
                || ItemData.read(stack).getCompound("ElementalCircuit").getBoolean("Corrupt")) {
            assembly.stopAll();
            return false;
        }
        if (block != null) {
            if (!WorldCircuitLinks.get(level).anchored(assembly.identity(), block.getBlockPos())) {
                assembly.stopAll();
                return false;
            }
            return true;
        }
        boolean owned = false;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++)
            if (player.getInventory().getItem(slot) == stack) {
                owned = true;
                break;
            }
        if (WizardEquipment.carrier(stack)) {
            worn = EquipmentCasting.worn(player, stack);
            owned = owned || worn;
            if (!worn) assembly.stopAll();
        }
        if (!owned || player.serverLevel() != level || !player.isAlive() || player.isRemoved()) {
            assembly.stopAll();
            return false;
        }
        if (stack.is(ElementalCircuits.SPELL_BOOK.get())
                && (player.getOffhandItem() != stack
                        || !player.getMainHandItem().is(ElementalCircuits.WAND.get()))) {
            assembly.stopAll();
            return false;
        }
        equipment = CastingEquipment.wand(player, stack);
        if (CastingEquipment.broken(equipment)) {
            assembly.stopAll();
            return false;
        }
        return true;
    }

    @Override
    public ManaSource supply() {
        if (!worn) return ManaSource.NONE;
        ManaSource source = block == null ? PlayerMana.get(player) : block;
        ManaOrigin origin =
                block == null
                        ? new ManaOrigin("player/" + player.getUUID())
                        : new ManaOrigin(
                                "fixed/"
                                        + level.dimension().location()
                                        + "/"
                                        + assembly.identity());
        return new ManaSource() {
            public long available() {
                return source.available();
            }

            public boolean debit(long n) {
                return source.debit(n);
            }

            public ManaOrigin provenance() {
                return origin;
            }
        };
    }

    private ManaSource ambient;

    @Override
    public ManaSource ambient() {
        if (!worn) return ManaSource.NONE;
        if (ambient == null) {
            int mouths =
                    assembly.slots().stream()
                            .map(assembly::circuit)
                            .filter(TickCircuit::powered)
                            .mapToInt(
                                    c ->
                                            (int)
                                                    c.definition().runes().stream()
                                                            .filter(
                                                                    r ->
                                                                            r.kind()
                                                                                    == Rune.Kind
                                                                                            .ABSORB)
                                                            .count())
                            .sum();
            ambient =
                    mouths > 0
                            ? WorldManaField.get(level)
                                    .tap(
                                            level,
                                            block == null
                                                    ? player.blockPosition()
                                                    : block.getBlockPos(),
                                            mouths)
                            : ManaSource.NONE;
        }
        return ambient;
    }

    @Override
    public CastContext castContext() {
        return player == null
                ? CrystalAmplification.context(level, block.getBlockPos())
                : SpellAttributes.context(player)
                        .withSpeed(
                                MagicEnchantments.speed(
                                        level,
                                        equipment,
                                        WizardEquipment.carrier(equipment)
                                                ? Fixed.ONE
                                                : WandEquipment.stats(equipment).speed()))
                        .withReactive(
                                WizardEquipment.carrier(stack)
                                        && ItemData.read(stack).getBoolean("ReactiveCast"));
    }

    @Override
    public ShapeRules shapeRules() {
        return ShapeResources.rules();
    }

    @Override
    public int steps() {
        return 1;
    }

    @Override
    public ReleasePermit releasePermit() {
        return !worn
                ? () -> false
                : block == null
                        ? () -> CastingEquipment.release(equipment, player)
                        : ReleasePermit.UNLIMITED;
    }

    @Override
    public CircuitProtection protection(int slot) {
        return circuit -> {
            long load = circuit.manaLoad();
            var material = circuit.definition().rules().material();
            boolean creative = player != null && player.getAbilities().instabuild;
            // Derive the costly elemental signature only when a real meltdown is imminent.
            var blast =
                    !creative
                                    && !assembly.boardBroken(slot)
                                    && load
                                            >= safety.profiles().get(material).capacity()
                                                    * safety.fuseMultiplier()
                            ? safety.blast(
                                    material,
                                    load,
                                    BoardSafety.signature(
                                            circuit.definition(), circuit.loadSignature()))
                            : null;
            var result =
                    assembly.assessBoard(
                            slot, safety, creative, MagicEnchantments.boardWear(level, stack));
            if (result.tripped()) {
                // Commit the one-shot trip before invoking native explosion callbacks or player
                // death.
                CarrierRepository.commit(stack);
                if (block != null) block.setChanged();
                if (player != null)
                    player.displayClientMessage(
                            net.minecraft.network.chat.Component.literal(
                                    result.meltdown()
                                            ? dev.elementalcircuits.text.Texts.get(
                                                    "text.elementalcircuits.server.circuit._minecraft_circuit_host.001")
                                            : dev.elementalcircuits.text.Texts.get(
                                                    "text.elementalcircuits.server.circuit._minecraft_circuit_host.002")),
                            true);
                if (blast != null)
                    BoardMeltdown.explode(
                            level,
                            block == null ? player.getUUID() : block.owner(),
                            block == null ? player.position().add(0, .7, 0) : block.origin(),
                            blast);
            }
            return !result.tripped();
        };
    }

    @Override
    public void sampleExternalSignals() {
        int power = block == null ? 0 : level.getBestNeighborSignal(block.getBlockPos());
        Boolean[] aimed = new Boolean[2];
        for (int slot : assembly.slots()) {
            var circuit = assembly.circuit(slot);
            Boolean active = null;
            for (var rune : circuit.definition().runes()) {
                if (rune.kind() == Rune.Kind.REDSTONE)
                    circuit.drive(rune.id(), power * Fixed.ONE >= rune.value());
                else if (rune.kind() == Rune.Kind.PROBE && circuit.powered()) {
                    int mode = (int) (rune.value() / Fixed.ONE);
                    boolean value;
                    if (mode < 2) {
                        if (aimed[mode] == null)
                            aimed[mode] =
                                    dev.elementalcircuits.server.spell.WorldProbe.sample(
                                            level,
                                            assembly.identity(),
                                            slot,
                                            block == null
                                                    ? player.getEyePosition()
                                                    : block.origin(),
                                            direction(),
                                            mode);
                        value = aimed[mode];
                    } else {
                        if (active == null)
                            active = WorldConnections.get(level).active(assembly.identity(), slot);
                        value = mode == 2 ? active : !active;
                    }
                    circuit.drive(rune.id(), value);
                }
            }
        }
    }

    @Override
    public void emit(CircuitAssembly.Output output) {
        if (output.cast().spell().method() == SpellSpec.Method.CONTACT) {
            int control = output.cast().spell().connection().control();
            if (control != 0 && assembly.circuit(output.slot()).contactReleased(control)) return;
            var key =
                    new WorldConnections.Key(
                            assembly.identity(), output.slot(), output.cast().output());
            var connections = WorldConnections.get(level);
            connections.touch(
                    level,
                    key,
                    block == null ? player.getUUID() : block.owner(),
                    block == null ? player.getEyePosition() : block.origin(),
                    direction());
            connections.offer(
                    level,
                    key,
                    output.cast(),
                    block == null ? CastingEquipment.strength(equipment) : Fixed.ONE);
            if (block == null)
                WandEquipment.castingWear(
                        equipment,
                        player,
                        output.cast().spell().effectScale().apply(output.cast().mana()).total());
            return;
        }
        if (output.cast().mana().total() == output.cast().mana().values()[0]) return;
        UUID owner = block == null ? player.getUUID() : block.owner();
        var cast = output.cast();
        var ports = cast.spell().materials().bind(block == null ? null : assembly.identity());
        cast =
                new dev.elementalcircuits.domain.circuit.MagicCircuit.Cast(
                        cast.sequence(),
                        cast.mana(),
                        cast.spell().withMaterials(ports),
                        cast.output());
        WorldSpells.get(level)
                .castAssembly(
                        level,
                        owner,
                        block == null ? player.getEyePosition() : block.origin(),
                        block == null
                                ? (WizardEquipment.carrier(stack)
                                        ? EquipmentCasting.direction(player, stack)
                                        : player.getLookAngle())
                                : block.direction(),
                        assembly,
                        output.slot(),
                        cast,
                        block == null ? CastingEquipment.strength(equipment) : Fixed.ONE,
                        assembly.circuit(output.slot()).invocationContext(castContext()));
        if (block == null) {
            if (WizardEquipment.carrier(stack)) assembly.circuit(output.slot()).stop();
            else
                WandEquipment.castingWear(
                        equipment,
                        player,
                        output.cast().spell().effectScale().apply(output.cast().mana()).total());
        }
    }

    @Override
    public void afterAdvance() {
        for (int slot : assembly.slots()) {
            var circuit = assembly.circuit(slot);
            for (var rune : circuit.definition().runes())
                if (rune.kind() == Rune.Kind.CONTACT && circuit.contactReleased(rune.id()))
                    WorldConnections.get(level)
                            .releaseControl(assembly.identity(), slot, rune.id());
        }
        if (player != null
                && assembly.slots().stream().mapToLong(s -> assembly.circuit(s).failures()).sum()
                        > failures) {
            long now = level.getGameTime(), last = FAILURE_NOTICE.getOrDefault(player, -100L);
            if (now - last >= 20) {
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal(
                                dev.elementalcircuits.text.Texts.get(
                                        "text.elementalcircuits.server.circuit._minecraft_circuit_host.003")),
                        true);
                FAILURE_NOTICE.put(player, now);
            }
        }
        if (block == null && CastingEquipment.broken(equipment)) assembly.stopAll();
    }

    @Override
    public void commit() {
        if (assembly != null) {
            var connections = WorldConnections.get(level);
            for (int slot : assembly.slots())
                for (var rune : assembly.circuit(slot).definition().runes())
                    if (rune.kind() == Rune.Kind.OUTPUT) {
                        var key = new WorldConnections.Key(assembly.identity(), slot, rune.id());
                        if (assembly.circuit(slot).powered() && !assembly.boardBroken(slot)) {
                            if (connections.contains(key))
                                connections.touch(
                                        level,
                                        key,
                                        block == null ? player.getUUID() : block.owner(),
                                        block == null ? player.getEyePosition() : block.origin(),
                                        direction());
                        } else connections.release(key);
                    }
        }
        if (assembly != null)
            for (int slot : assembly.slots())
                for (var refund : assembly.circuit(slot).refunds())
                    if (WorldManaRefunds.get(level)
                            .accept(
                                    assembly.identity() + "/" + slot + "/" + refund.sequence(),
                                    refund.mana()))
                        assembly.circuit(slot).acknowledgeRefund(refund.sequence());
        boolean changed = CarrierRepository.commit(stack);
        if (block != null) {
            if (changed) block.setChanged();
        } else PlayerMana.save(player);
    }

    @Override
    public void failed(RuntimeException error) {
        if (assembly != null) WorldConnections.get(level).releaseCarrier(assembly.identity());
        CarrierFaults.report(stack, player);
    }

    private net.minecraft.world.phys.Vec3 direction() {
        return block != null
                ? block.direction()
                : WizardEquipment.carrier(stack)
                        ? EquipmentCasting.direction(player, stack)
                        : player.getLookAngle();
    }
}
