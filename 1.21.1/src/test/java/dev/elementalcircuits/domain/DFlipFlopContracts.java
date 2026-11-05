package dev.elementalcircuits.domain;

import dev.elementalcircuits.adapter.codec.JsonCodecs;
import dev.elementalcircuits.adapter.resources.RuleResources;
import dev.elementalcircuits.domain.casting.SpellSpec;
import dev.elementalcircuits.domain.circuit.Blueprint;
import dev.elementalcircuits.domain.circuit.CircuitSnapshot;
import dev.elementalcircuits.domain.circuit.HexRouter;
import dev.elementalcircuits.domain.circuit.MagicCircuit;
import dev.elementalcircuits.domain.circuit.Route;
import dev.elementalcircuits.domain.circuit.Rules;
import dev.elementalcircuits.domain.circuit.Rune;
import dev.elementalcircuits.domain.circuit.TickCircuit;
import dev.elementalcircuits.domain.combat.Element;
import dev.elementalcircuits.domain.mana.ManaPool;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Gate-level experiment against the real VM; never shipped as a new rune or gameplay input. */
public final class DFlipFlopContracts {
    private static final int D = 1,
            CLK = 2,
            MASTER_Q = 8,
            Q = 14,
            QB = 16,
            CAST = 18,
            FIRE_GATE = 19,
            WATER_GATE = 20;
    private static int checks;

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    public static Blueprint blueprint() {
        List<Rune> nodes = new ArrayList<>();
        add(nodes, D, Rune.Kind.SIGNAL, -4, -2);
        add(nodes, CLK, Rune.Kind.SIGNAL, -2, -4);
        add(nodes, 3, Rune.Kind.NOT, -3, -2);
        add(nodes, 4, Rune.Kind.NOT, -1, -4);
        add(nodes, 5, Rune.Kind.AND, -4, 0);
        add(nodes, 6, Rune.Kind.AND, -2, -2);
        add(nodes, 7, Rune.Kind.OR, -3, 0);
        add(nodes, MASTER_Q, Rune.Kind.NOT, -2, 0);
        add(nodes, 9, Rune.Kind.OR, -4, 2);
        add(nodes, 10, Rune.Kind.NOT, -2, 2);
        add(nodes, 11, Rune.Kind.AND, 0, -2);
        add(nodes, 12, Rune.Kind.AND, 0, 0);
        add(nodes, 13, Rune.Kind.OR, 1, -2);
        add(nodes, Q, Rune.Kind.NOT, 2, -2);
        add(nodes, 15, Rune.Kind.OR, 1, 0);
        add(nodes, QB, Rune.Kind.NOT, 2, 0);
        add(nodes, 17, Rune.Kind.INPUT, -3, 4);
        add(nodes, CAST, Rune.Kind.SWITCH, -1, 4);
        add(nodes, FIRE_GATE, Rune.Kind.SWITCH, 1, 2);
        add(nodes, WATER_GATE, Rune.Kind.SWITCH, 3, 0);
        add(nodes, 21, Rune.Kind.CONVERT, 2, 2);
        add(nodes, 22, Rune.Kind.CONVERT, 4, 0);
        add(nodes, 23, Rune.Kind.DIRECTED, 3, 2);
        add(nodes, 24, Rune.Kind.DIRECTED, 5, 0);
        add(nodes, 25, Rune.Kind.BEAM, 4, 2);
        add(nodes, 26, Rune.Kind.BEAM, 6, -1);
        add(nodes, 27, Rune.Kind.OUTPUT, 5, 1);
        add(nodes, 28, Rune.Kind.OUTPUT, 6, -2);
        nodes.replaceAll(
                r ->
                        new Rune(
                                r.id(),
                                r.kind(),
                                r.cell(),
                                r.id() == 22 ? Element.WATER : Element.FIRE,
                                r.value(),
                                r.kind() != Rune.Kind.SIGNAL && r.kind() != Rune.Kind.SWITCH,
                                r.id() == FIRE_GATE || r.id() == WATER_GATE,
                                r.weights()));
        Blueprint positions =
                new Blueprint(
                        5,
                        RuleResources.defaults().withMaterial(Rules.Material.BRASS),
                        nodes,
                        List.of(),
                        true);
        List<Route> edges = new ArrayList<>();
        // Master: S = D & !CLK, R = !D & !CLK; cross-coupled NORs store M / !M.
        int[][] signal = {
            {D, 3},
            {CLK, 4},
            {D, 5},
            {4, 5},
            {3, 6},
            {4, 6},
            {6, 7},
            {10, 7},
            {7, MASTER_Q},
            {5, 9},
            {MASTER_Q, 9},
            {9, 10},
            // Slave: S = M & CLK, R = !M & CLK; cross-coupled NORs store Q / !Q.
            {MASTER_Q, 11},
            {CLK, 11},
            {10, 12},
            {CLK, 12},
            {12, 13},
            {QB, 13},
            {13, Q},
            {11, 15},
            {Q, 15},
            {15, QB},
            {QB, FIRE_GATE},
            {Q, WATER_GATE}
        };
        int[][] mana = {
            {17, CAST},
            {CAST, FIRE_GATE},
            {CAST, WATER_GATE},
            {FIRE_GATE, 21},
            {WATER_GATE, 22},
            {21, 23},
            {22, 24},
            {23, 25},
            {24, 26},
            {25, 27},
            {26, 28}
        };
        for (int[] link : signal) wire(positions, edges, link, Route.Payload.SIGNAL);
        for (int[] link : mana) wire(positions, edges, link, Route.Payload.MANA);
        return new Blueprint(5, positions.rules(), nodes, edges, true);
    }

    private static void add(List<Rune> nodes, int id, Rune.Kind kind, int q, int r) {
        nodes.add(Rune.create(id, kind, new Hex(q, r)));
    }

    private static void wire(Blueprint b, List<Route> edges, int[] link, Route.Payload payload) {
        edges.add(
                new Route(
                        link[0],
                        link[1],
                        payload,
                        HexRouter.segment(
                                b, b.rune(link[0]).cell(), b.rune(link[1]).cell(), List.of())));
    }

    /**
     * External bench stimulus only. Change two SIGNAL constants, preserving every runtime field.
     * Production deliberately has no such live-definition API; this is NOT a game control.
     */
    private static TickCircuit drive(TickCircuit circuit, boolean d, boolean clock) {
        CircuitSnapshot s = circuit.snapshot();
        Blueprint old = s.blueprint();
        List<Rune> nodes =
                old.runes().stream()
                        .map(
                                r ->
                                        r.id() != D && r.id() != CLK
                                                ? r
                                                : new Rune(
                                                        r.id(),
                                                        r.kind(),
                                                        r.cell(),
                                                        r.element(),
                                                        r.value(),
                                                        r.id() == D ? d : clock,
                                                        r.signalControl(),
                                                        r.weights()))
                        .toList();
        Blueprint next = new Blueprint(5, old.rules(), nodes, old.routes(), old.continuous());
        return TickCircuit.restore(
                new CircuitSnapshot(
                        s.version(),
                        next,
                        s.clock(),
                        s.sequence(),
                        s.powered(),
                        s.remaining(),
                        s.injectionCountdown(),
                        s.supplied(),
                        s.loss(),
                        s.delivered(),
                        s.reserve(),
                        s.local(),
                        s.inbox(),
                        s.transit(),
                        s.pending(),
                        s.signals(),
                        s.gates()));
    }

    private static boolean bit(TickCircuit c, int id) {
        boolean[] signals = c.snapshot().signals();
        List<Route> edges = c.definition().routes();
        for (int i = 0; i < edges.size(); i++)
            if (edges.get(i).source() == id && edges.get(i).payload() == Route.Payload.SIGNAL)
                return signals[i];
        throw new IllegalArgumentException("没有观察线 " + id);
    }

    private static boolean gate(TickCircuit c, int id) {
        List<Rune> nodes =
                c.definition().runes().stream().sorted(Comparator.comparingInt(Rune::id)).toList();
        for (int i = 0; i < nodes.size(); i++)
            if (nodes.get(i).id() == id) return c.snapshot().gates()[i];
        throw new IllegalArgumentException("没有门 " + id);
    }

    private static void ledger(TickCircuit c) {
        var s = c.snapshot();
        check(s.supplied() == s.loss() + s.delivered() + c.held(), "D触发器实验资源严格守恒");
    }

    private static final class Bench {
        TickCircuit circuit = new TickCircuit(blueprint());
        final ManaPool supply =
                new ManaPool(
                        4096 * Fixed.ONE, 0, 0); // Finite lab supply, not a player-capacity change.
        final StringBuilder csv =
                new StringBuilder(
                        "tick,phase,D,CLK,M,Q,Qbar,fire_gate,water_gate,fire_casts,water_casts,held_raw,supplied_raw,loss_raw,delivered_raw\n");
        boolean d, clock;

        Bench() {
            check(circuit.start(), "实验手动起阵");
        }

        void input(boolean data, boolean clk) {
            d = data;
            clock = clk;
            circuit = drive(circuit, data, clk);
        }

        List<MagicCircuit.Cast> step(String phase) {
            List<MagicCircuit.Cast> casts = circuit.tick(supply);
            ledger(circuit);
            int fire = 0, water = 0;
            for (var cast : casts) {
                check(
                        cast.spell().method() == SpellSpec.Method.DIRECTED
                                && cast.spell().shape() == SpellSpec.Shape.BEAM,
                        "两支路交付指向光束");
                boolean f = cast.mana().values()[Element.FIRE.component(false)] > 0,
                        w = cast.mana().values()[Element.WATER.component(false)] > 0;
                check(f != w && cast.mana().values()[0] == 0, "单次交付只含火或水，不把两种元素混成一包");
                if (f) fire++;
                if (w) water++;
            }
            var s = circuit.snapshot();
            csv.append(
                    String.format(
                            Locale.ROOT,
                            "%d,%s,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d%n",
                            s.clock(),
                            phase,
                            d ? 1 : 0,
                            clock ? 1 : 0,
                            bit(circuit, MASTER_Q) ? 1 : 0,
                            bit(circuit, Q) ? 1 : 0,
                            bit(circuit, QB) ? 1 : 0,
                            gate(circuit, FIRE_GATE) ? 1 : 0,
                            gate(circuit, WATER_GATE) ? 1 : 0,
                            fire,
                            water,
                            circuit.held(),
                            s.supplied(),
                            s.loss(),
                            s.delivered()));
            return casts;
        }

        void settle(String phase, int ticks) {
            for (int i = 0; i < ticks; i++) step(phase);
        }

        void state(boolean expected, String message) {
            check(bit(circuit, Q) == expected && bit(circuit, QB) != expected, message);
        }

        int casts(String phase, Element expected, int ticks) {
            int count = 0;
            for (int i = 0; i < ticks; i++)
                for (var cast : step(phase)) {
                    check(cast.mana().dominant() == expected, "稳定期只能交付所选元素：" + phase);
                    count++;
                }
            check(count > 0, "稳定期确有交付：" + phase);
            return count;
        }
    }

    private record Result(
            String csv,
            int fireCasts,
            int waterCasts,
            int fireTailTicks,
            int simultaneousCastTicks) {}

    private static Result exercise() {
        Bench b = new Bench();
        // All signal wires start false. Both latch halves require a deliberate initialization
        // cycle.
        b.input(false, false);
        b.settle("initialize_master_fire", 32);
        b.input(false, true);
        b.settle("initialize_slave_fire", 32);
        b.state(false, "初始化上升沿后保存火状态");
        b.circuit.toggleManual(CAST);
        b.settle("fire_pipeline", 12);
        int fire = b.casts("fire_steady", Element.FIRE, 24);
        // D changing while CLK stays high must not change the held output.
        b.input(true, true);
        for (int i = 0; i < 24; i++) {
            b.step("D_changes_while_clock_high");
            b.state(false, "高电平期间改变D不改Q");
        }
        b.input(true, false);
        for (int i = 0; i < 32; i++) {
            b.step("falling_edge_and_master_water");
            b.state(false, "下降沿及低电平只更新主锁存器");
        }
        // Actual rising edge: retain the complete transient, rather than assuming zero-delay
        // switching.
        b.input(true, true);
        int lastFire = 0, both = 0;
        for (int i = 1; i <= 32; i++) {
            var casts = b.step("rising_edge_select_water");
            boolean f = false, w = false;
            for (var cast : casts) {
                f |= cast.mana().dominant() == Element.FIRE;
                w |= cast.mana().dominant() == Element.WATER;
            }
            if (f) lastFire = i;
            if (f && w) both++;
            if (i <= 12) {
                TickCircuit a = TickCircuit.restore(b.circuit.snapshot()),
                        copy = JsonCodecs.readSnapshot(JsonCodecs.snapshot(b.circuit));
                ManaPool pa = JsonCodecs.readPool(JsonCodecs.pool(b.supply), 0, 0),
                        pb = JsonCodecs.readPool(JsonCodecs.pool(b.supply), 0, 0);
                for (int ahead = 0; ahead < 8; ahead++) {
                    check(a.tick(pa).equals(copy.tick(pb)), "切换暂态任意边界恢复后的交付一致");
                    check(
                            JsonCodecs.snapshot(a).equals(JsonCodecs.snapshot(copy)),
                            "切换暂态恢复不重置反馈或缓存");
                }
            }
        }
        b.state(true, "上升沿采样D=1，保存水状态");
        int water = b.casts("water_steady", Element.WATER, 24);
        b.input(false, true);
        for (int i = 0; i < 24; i++) {
            b.step("D_returns_zero_without_edge");
            b.state(true, "D回零但无上升沿，仍保存水");
        }

        // Full serialized runtime must continue identically, including the memory feedback and
        // queued mana.
        TickCircuit restored = JsonCodecs.readSnapshot(JsonCodecs.snapshot(b.circuit));
        ManaPool copy = JsonCodecs.readPool(JsonCodecs.pool(b.supply), 0, 0);
        for (int i = 0; i < 16; i++) {
            List<MagicCircuit.Cast> expected = restored.tick(copy);
            check(b.step("snapshot_roundtrip").equals(expected), "恢复后的法术轨迹相同");
            check(
                    JsonCodecs.snapshot(b.circuit).equals(JsonCodecs.snapshot(restored)),
                    "恢复后全部反馈、缓存、账本一致");
        }
        b.input(false, false);
        b.settle("prepare_fire_again", 32);
        b.state(true, "低电平保持已保存水");
        b.input(false, true);
        b.settle("rising_edge_select_fire", 32);
        b.state(false, "第二次上升沿采样D=0切回火");
        fire += b.casts("fire_again_steady", Element.FIRE, 24);

        boolean[] before = b.circuit.snapshot().signals();
        b.circuit.stop();
        for (int i = 0; i < 12; i++) {
            b.step("explicit_stop");
            check(Arrays.equals(before, b.circuit.snapshot().signals()), "停供保留反馈信号");
        }
        b.state(false, "停供后仍保留火状态");
        check(b.circuit.start(), "手动重新起阵");
        b.settle("restart_same_memory", 24);
        b.state(false, "重启保留所存元素");
        // Document real boundary: current fan-out gives the closed branch a cached share.
        check(b.circuit.held() > 0, "关闭支路的魔力份额真实保留，不能宣称无缓存选择器");
        check(lastFire > 0, "上升沿之后确有旧火交付尾流，不是瞬时互斥切换");
        return new Result(b.csv.toString(), fire, water, lastFire, both);
    }

    private static void finitePower() {
        Bench b = new Bench();
        b.input(true, false);
        b.settle("finite_initialize_master", 32);
        b.input(true, true);
        b.settle("finite_initialize_slave", 32);
        b.state(true, "断供测试先保存水");
        b.circuit.tick(new ManaPool(1, 0, 0));
        check(!b.circuit.powered(), "控制费不足原子停供");
        boolean[] saved = b.circuit.snapshot().signals();
        for (int i = 0; i < 8; i++) {
            b.circuit.tick(b.supply);
            check(Arrays.equals(saved, b.circuit.snapshot().signals()), "补能不自动重启或清除记忆");
        }
        b.state(true, "断供保持水状态");
    }

    private static int[] sweep() {
        int latestOld = 0, earliestNew = Integer.MAX_VALUE, latestNew = 0;
        // Sweep every phase of the real ten-tick injection schedule, in both directions.
        for (boolean target : new boolean[] {false, true})
            for (int offset = 0; offset < 10; offset++) {
                Bench b = new Bench();
                b.input(!target, false);
                b.settle("sweep_init_master", 32);
                b.input(!target, true);
                b.settle("sweep_init_slave", 32);
                b.state(!target, "扫描初态已初始化");
                b.circuit.toggleManual(CAST);
                b.settle("sweep_fill_old_branch", 24);
                b.input(target, false);
                b.settle("sweep_prepare_data", 32 + offset);
                b.state(!target, "全部供给相位的下降沿保持Q");
                b.input(target, true);
                int lastOld = 0, firstNew = 0;
                for (int i = 1; i <= 32; i++) {
                    for (var cast : b.step("sweep_rising")) {
                        boolean selected =
                                cast.mana().dominant() == (target ? Element.WATER : Element.FIRE);
                        if (selected && firstNew == 0) firstNew = i;
                        if (!selected) lastOld = i;
                    }
                }
                b.state(target, "全部供给相位的上升沿都采样成功");
                b.casts("sweep_steady", target ? Element.WATER : Element.FIRE, 24);
                check(firstNew > 0, "各供给相位均有新元素交付");
                latestOld = Math.max(latestOld, lastOld);
                earliestNew = Math.min(earliestNew, firstNew);
                latestNew = Math.max(latestNew, firstNew);
                // Mutate D during the opaque phase, then restore the *entire* runtime at a
                // boundary.
                b.input(!target, true);
                for (int i = 0; i < 12; i++) {
                    b.step("sweep_hold_high");
                    b.state(target, "高电平不跟随D，双方向/全相位保持");
                }
            }
        return new int[] {latestOld, earliestNew, latestNew};
    }

    private static void coldStart() {
        Bench b = new Bench();
        boolean invalid = false;
        for (int i = 0; i < 32; i++) {
            b.step("cold_without_clock");
            if (i >= 16 && bit(b.circuit, Q) == bit(b.circuit, QB)) invalid = true;
        }
        check(invalid, "全假冷启动不是合法存储态，静态导入不能代替初始化时钟");
        check(
                !Rune.Kind.SWITCH.signalOut() && !Rune.Kind.SIGNAL.signalIn(),
                "现行手动阀门不输出信号，常量信号也无运行输入端口");
    }

    public static int run() {
        checks = 0;
        Blueprint blueprint = blueprint();
        check(
                blueprint.runes().size() == 28 && blueprint.routes().size() == 35,
                "实验规模在既有64符文/64线路内");
        check(
                JsonCodecs.readBlueprint(JsonCodecs.blueprint(blueprint)).equals(blueprint),
                "实验可导入蓝图往返");
        exercise();
        finitePower();
        sweep();
        coldStart();
        return checks;
    }

    public static void main(String[] args) throws Exception {
        checks = 0;
        Result result = exercise();
        finitePower();
        int[] sweep = sweep();
        coldStart();
        Path root = Path.of(args.length == 0 ? "." : args[0]).toAbsolutePath().normalize();
        Path examples = root.resolve("examples"),
                report = root.resolve("docs/experiments/d-flip-flop");
        Files.createDirectories(examples);
        Files.createDirectories(report);
        Files.writeString(
                examples.resolve("d-flip-flop-fire-water.json"),
                JsonCodecs.blueprint(blueprint()) + "\n",
                StandardCharsets.UTF_8);
        Files.writeString(report.resolve("timing.csv"), result.csv, StandardCharsets.UTF_8);
        String summary =
                String.format(
                        Locale.ROOT,
                        "{\n"
                                + "  \"checks\": %d,\n"
                                + "  \"runes\": 28,\n"
                                + "  \"routes\": 35,\n"
                                + "  \"steady_fire_casts\": %d,\n"
                                + "  \"steady_water_casts\": %d,\n"
                                + "  \"last_old_fire_tick_after_stimulus\": %d,\n"
                                + "  \"simultaneous_cast_ticks_in_switch_window\": %d,\n"
                                + "  \"swept_transitions\": 20,\n"
                                + "  \"sweep_latest_old_element_tick\": %d,\n"
                                + "  \"sweep_first_new_element_tick_min\": %d,\n"
                                + "  \"sweep_first_new_element_tick_max\": %d,\n"
                                + "  \"runtime_D_CLK_input_available\": false\n"
                                + "}\n",
                        checks,
                        result.fireCasts,
                        result.waterCasts,
                        result.fireTailTicks,
                        result.simultaneousCastTicks,
                        sweep[0],
                        sweep[1],
                        sweep[2]);
        Files.writeString(report.resolve("result.json"), summary, StandardCharsets.UTF_8);
        System.out.println("D FLIP-FLOP EXPERIMENT PASSED: " + checks);
        System.out.println(summary);
    }
}
