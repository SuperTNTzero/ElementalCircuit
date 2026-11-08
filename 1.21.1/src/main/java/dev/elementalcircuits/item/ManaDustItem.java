package dev.elementalcircuits.item;

import dev.elementalcircuits.block.CircuitBlockEntity;
import dev.elementalcircuits.domain.circuit.CircuitAddress;
import dev.elementalcircuits.domain.circuit.CircuitAssembly;
import dev.elementalcircuits.domain.circuit.Rune;
import dev.elementalcircuits.domain.circuit.Route;
import dev.elementalcircuits.server.circuit.WorldCircuitLinks;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.ChatFormatting;

import java.util.*;
import java.util.List;

/** Powder binds one idle mana output on a fixed circuit to an idle input on another. */
public final class ManaDustItem extends Item {
    private record Pending(BlockPos position, long tick) {}

    private static final Map<ServerLevel, Map<UUID, Pending>> PENDING = new WeakHashMap<>();
    private static final long EXPIRE_TICKS = 600;

    public ManaDustItem() {
        super(new Properties());
    }

    @Override
    public void appendHoverText(
            ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.elementalcircuits.mana_dust")
                .withStyle(ChatFormatting.GRAY));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)
                || !(context.getPlayer() instanceof ServerPlayer player))
            return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(context.getClickedPos()) instanceof CircuitBlockEntity clicked))
            return InteractionResult.PASS;
        try {
            if (!clicked.owns(player))
                throw new IllegalArgumentException("message.elementalcircuits.mana_dust.owner");
            var pending = PENDING.computeIfAbsent(level, ignored -> new HashMap<>());
            pending.entrySet().removeIf(e -> level.getGameTime() - e.getValue().tick() > EXPIRE_TICKS);
            Pending first = pending.remove(player.getUUID());
            if (first == null) {
                if (outputs(clicked.assembly()).isEmpty())
                    throw new IllegalArgumentException("message.elementalcircuits.mana_dust.no_output");
                pending.put(player.getUUID(), new Pending(context.getClickedPos().immutable(), level.getGameTime()));
                player.displayClientMessage(Component.translatable("message.elementalcircuits.mana_dust.source"), true);
                return InteractionResult.SUCCESS;
            }
            if (first.position().equals(context.getClickedPos())) {
                pending.put(player.getUUID(), new Pending(first.position(), level.getGameTime()));
                player.displayClientMessage(Component.translatable("message.elementalcircuits.mana_dust.source"), true);
                return InteractionResult.SUCCESS;
            }
            if (!(level.getBlockEntity(first.position()) instanceof CircuitBlockEntity source)
                    || !source.owns(player))
                throw new IllegalArgumentException("message.elementalcircuits.mana_dust.source_lost");
            connect(level, player, source, clicked);
            ItemStack stack = context.getItemInHand();
            if (!player.getAbilities().instabuild) stack.shrink(1);
            player.displayClientMessage(Component.translatable("message.elementalcircuits.mana_dust.linked"), true);
            return InteractionResult.SUCCESS;
        } catch (RuntimeException error) {
            String message = error.getMessage();
            player.displayClientMessage(
                    Component.translatable(message != null && message.startsWith("message.elementalcircuits.")
                            ? message : "message.elementalcircuits.mana_dust.invalid"), true);
            var active = PENDING.get(level);
            if (active != null) active.remove(player.getUUID());
            return InteractionResult.FAIL;
        }
    }

    private static void connect(
            ServerLevel level, ServerPlayer player,
            CircuitBlockEntity from, CircuitBlockEntity to) {
        if (from == to) throw new IllegalArgumentException();
        CircuitAssembly a = from.assembly(), b = to.assembly();
        int range = Math.min(a.rules().range(), b.rules().range());
        if (from.getBlockPos().distSqr(to.getBlockPos()) > (long) range * range)
            throw new IllegalArgumentException("message.elementalcircuits.mana_dust.too_far");
        var saved = WorldCircuitLinks.get(level);
        if (!saved.anchored(a.identity(), from.getBlockPos())
                || !saved.anchored(b.identity(), to.getBlockPos()))
            throw new IllegalArgumentException("message.elementalcircuits.mana_dust.anchor_moved");

        CircuitAddress output = outputs(a).stream()
                .filter(address -> !a.links().connections().stream().anyMatch(c -> c.from().equals(address)))
                .filter(address -> !saved.graph().connections().stream().anyMatch(c -> c.from().equals(address)))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("message.elementalcircuits.mana_dust.no_output"));
        CircuitAddress input = inputs(b).stream()
                .filter(address -> !b.links().connections().stream().anyMatch(c -> c.to().equals(address)))
                .filter(address -> !saved.graph().connections().stream().anyMatch(c -> c.to().equals(address)))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("message.elementalcircuits.mana_dust.no_input"));
        saved.connect(level, player, a.identity(),
                new dev.elementalcircuits.adapter.codec.JsonCodecs.LinkRequest(
                        output, input, Route.Payload.MANA));
    }

    private static List<CircuitAddress> outputs(CircuitAssembly assembly) {
        return ports(assembly, Rune.Kind.MANA_SEND);
    }

    private static List<CircuitAddress> inputs(CircuitAssembly assembly) {
        return ports(assembly, Rune.Kind.MANA_RECEIVE);
    }

    private static List<CircuitAddress> ports(CircuitAssembly assembly, Rune.Kind kind) {
        List<CircuitAddress> result = new ArrayList<>();
        for (int slot : assembly.slots())
            for (Rune rune : assembly.circuit(slot).definition().runes())
                if (rune.kind() == kind)
                    result.add(new CircuitAddress(assembly.identity(), slot, rune.id()));
        return result;
    }
}
