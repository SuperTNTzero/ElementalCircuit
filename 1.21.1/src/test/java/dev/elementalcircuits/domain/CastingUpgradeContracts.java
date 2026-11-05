package dev.elementalcircuits.domain;

import dev.elementalcircuits.adapter.codec.JsonCodecs;
import dev.elementalcircuits.domain.casting.CastBudget;
import dev.elementalcircuits.domain.casting.CastContext;
import dev.elementalcircuits.domain.casting.CastGesture;
import dev.elementalcircuits.domain.casting.ShapeRules;
import dev.elementalcircuits.domain.casting.SpellSpec;
import dev.elementalcircuits.domain.circuit.Blueprint;
import dev.elementalcircuits.domain.circuit.MagicCircuit;
import dev.elementalcircuits.domain.circuit.SupplyMode;
import dev.elementalcircuits.domain.circuit.TickCircuit;
import dev.elementalcircuits.domain.mana.ManaPool;
import dev.elementalcircuits.testing.TestBlueprints;

import java.util.*;

public final class CastingUpgradeContracts {
    private static int checks;

    private static void check(boolean b, String text) {
        checks++;
        if (!b) throw new AssertionError(text);
    }

    public static int run() {
        checks = 0;
        var tap = new CastGesture(6, 40, 2 * Fixed.ONE);
        check(
                tap.advance(2) == CastGesture.Action.NONE && tap.multiplier() == Fixed.ONE,
                "Short click has no charge penalty");
        check(
                tap.release() == CastGesture.Action.RELEASE
                        && tap.release() == CastGesture.Action.NONE,
                "Release is handled exactly once");
        var hold = new CastGesture(6, 40, 2 * Fixed.ONE);
        check(
                hold.advance(6) == CastGesture.Action.CHARGE_STARTED,
                "Crossing threshold begins charge without output");
        check(hold.advance(7) == CastGesture.Action.NONE, "Charge feedback occurs once");
        long old = hold.multiplier();
        for (int i = 8; i <= 80; i++) {
            hold.advance(i);
            check(
                    hold.multiplier() >= old && hold.multiplier() <= 2 * Fixed.ONE,
                    "Charge grows monotonically and saturates");
            old = hold.multiplier();
        }
        check(
                hold.multiplier() == 2 * Fixed.ONE && hold.release() == CastGesture.Action.RELEASE,
                "Long hold releases at capped power");
        hold = new CastGesture(6, 40, 2 * Fixed.ONE);
        hold.advance(40);
        hold.cancel();
        check(hold.release() == CastGesture.Action.NONE, "Switch/cancel cannot accidentally cast");
        var board =
                TestBlueprints.entries().stream()
                        .filter(e -> e.file().equals("standard.ecblueprint"))
                        .findFirst()
                        .orElseThrow()
                        .blueprint();
        board =
                new Blueprint(board.version(), board.rules(), board.runes(), board.routes(), true)
                        .withInputAmount(1, 16 * Fixed.ONE);
        var c = new TickCircuit(board);
        check(
                c.start(SupplyMode.PULSE, 2 * Fixed.ONE),
                "Gesture overrides a continuous template without editing it");
        var pool = new ManaPool(1024 * Fixed.ONE, 0, 0);
        var casts = new ArrayList<MagicCircuit.Cast>();
        for (int i = 0; i < 30; i++) casts.addAll(c.tick(pool));
        check(
                pool.snapshot().spent() == 16 * Fixed.ONE,
                "Charged release funds exactly one fixed input");
        check(c.definition().continuous(), "Saved continuous preference remains intact");
        check(
                casts.size() == 1 && casts.getFirst().spell().damageMultiplier() == 2 * Fixed.ONE,
                "Charge multiplies released effect once");
        c = new TickCircuit(board);
        c.start(SupplyMode.PULSE, 2 * Fixed.ONE);
        c.tick(pool);
        var restored = JsonCodecs.readSnapshot(JsonCodecs.snapshot(c));
        check(
                restored.snapshot().version() == 14
                        && restored.supplyMode() == SupplyMode.PULSE
                        && restored.charge() == 2 * Fixed.ONE,
                "Invocation mode and charge survive reload");
        check(
                restored.withManaSteps(4).charge() == 2 * Fixed.ONE,
                "Timing changes preserve charge");
        check(
                restored.withCasting(board.rules().casting()).supplyMode() == SupplyMode.PULSE,
                "Policy adoption preserves pulse mode");
        var spec = new SpellSpec(SpellSpec.Method.AREA, SpellSpec.Shape.SPHERE, Fixed.ONE, 0, 1, 0);
        var standard =
                CastBudget.quote(spec, board.rules(), CastContext.NEUTRAL, ShapeRules.protocolV1());
        var discounted =
                CastBudget.quote(
                        spec,
                        board.rules(),
                        new CastContext(Fixed.ONE, Fixed.ONE / 2),
                        ShapeRules.protocolV1());
        var charged =
                CastBudget.quote(
                        spec,
                        board.rules(),
                        CastContext.NEUTRAL.charged(2 * Fixed.ONE),
                        ShapeRules.protocolV1());
        check(
                discounted.required() <= standard.required() / 2,
                "Sub-neutral mana cost is supported");
        check(
                charged.required() == 2 * standard.required()
                        || charged.required() == 2 * standard.required() + 1,
                "Charge also multiplies mana cost");
        check(
                new CastContext(16 * Fixed.ONE, Fixed.ONE).charged(2 * Fixed.ONE).manaMultiplier()
                        == Fixed.ONE,
                "At the damage cap charge cannot add a hidden cost");
        check(
                new CastContext(8 * Fixed.ONE, Fixed.MAX).charged(2 * Fixed.ONE).damageMultiplier()
                        == 8 * Fixed.ONE,
                "Cost cap cannot grant an unpaid charge boost");
        var forged = new TickCircuit(board);
        forged.start(SupplyMode.PULSE, 2 * Fixed.ONE);
        boolean rejected = false;
        try {
            JsonCodecs.readSnapshot(
                    JsonCodecs.snapshot(forged).replaceFirst("\"version\":14", "\"version\":13"));
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, "Legacy snapshots cannot silently carry charged invocation fields");
        var ancient =
                new TickCircuit(
                        new Blueprint(5, board.rules(), board.runes(), board.routes(), false));
        ancient.start();
        var before = ancient.snapshot();
        var legacy = JsonCodecs.readSnapshot(JsonCodecs.snapshot(ancient));
        check(
                before.version() == 14
                        && legacy.supplyMode() == SupplyMode.CONFIGURED
                        && legacy.charge() == Fixed.ONE,
                "当前现场保存完整供给状态");
        return checks;
    }
}
