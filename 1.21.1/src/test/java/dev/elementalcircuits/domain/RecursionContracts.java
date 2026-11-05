package dev.elementalcircuits.domain;

import dev.elementalcircuits.adapter.codec.JsonCodecs;
import dev.elementalcircuits.adapter.resources.AssemblyResources;
import dev.elementalcircuits.adapter.resources.RuleResources;
import dev.elementalcircuits.application.observation.CircuitSelection;
import dev.elementalcircuits.client.editor.SpellWheelModel;
import dev.elementalcircuits.client.editor.TriggerDraft;
import dev.elementalcircuits.domain.book.BookSelection;
import dev.elementalcircuits.domain.casting.RecursionRules;
import dev.elementalcircuits.domain.casting.SpellSpec;
import dev.elementalcircuits.domain.casting.TriggerBinding;
import dev.elementalcircuits.domain.casting.TriggerCondition;
import dev.elementalcircuits.domain.casting.TriggeredCircuit;
import dev.elementalcircuits.domain.circuit.Blueprint;
import dev.elementalcircuits.domain.circuit.CircuitAssembly;
import dev.elementalcircuits.domain.circuit.InvocationProgram;
import dev.elementalcircuits.domain.circuit.LayerRules;
import dev.elementalcircuits.domain.circuit.Route;
import dev.elementalcircuits.domain.circuit.Rules;
import dev.elementalcircuits.domain.circuit.Rune;
import dev.elementalcircuits.domain.circuit.TickCircuit;
import dev.elementalcircuits.domain.combat.Element;
import dev.elementalcircuits.domain.mana.ManaPool;

import java.util.*;

/** Prepaid invocation isolation, conservation, state recovery and selection geometry. */
public final class RecursionContracts {
    private static int checks;

    private static void check(boolean ok, String why) {
        checks++;
        if (!ok) throw new AssertionError(why);
    }

    private static void rejects(Runnable task) {
        boolean bad = false;
        try {
            task.run();
        } catch (RuntimeException e) {
            bad = true;
        }
        check(bad, "非法调用状态在发布前拒绝");
    }

    public static Blueprint child(Element element, boolean continuous) {
        var b =
                AssemblyContracts.line(
                        continuous,
                        Route.Payload.MANA,
                        Rune.Kind.INPUT,
                        Rune.Kind.CONVERT,
                        Rune.Kind.DIRECTED,
                        Rune.Kind.SPHERE,
                        Rune.Kind.OUTPUT);
        var nodes =
                b.runes().stream()
                        .map(
                                r ->
                                        r.kind() == Rune.Kind.CONVERT
                                                ? new Rune(
                                                        r.id(),
                                                        r.kind(),
                                                        r.cell(),
                                                        element,
                                                        r.value(),
                                                        r.on(),
                                                        r.signalControl(),
                                                        r.weights())
                                                : r)
                        .toList();
        return new Blueprint(5, b.rules().withManaSteps(8), nodes, b.routes(), continuous);
    }

    private static void ledger(TriggeredCircuit c) {
        var s = c.state();
        var v = TickCircuit.restore(s.circuit());
        check(
                s.budget()
                        == s.supply().amount()
                                + v.held()
                                + s.circuit().loss()
                                + s.circuit().delivered(),
                "预算包含未支取、阵内、损耗和输出");
        check(s.supply().spent() == s.circuit().supplied(), "预付池支出等于子实例供给");
    }

    private static void isolated() {
        var b = child(Element.WATER, true);
        var live = new TickCircuit(b);
        live.start();
        live.tick(new ManaPool(64 * Fixed.ONE, 0, 0));
        String before = JsonCodecs.snapshot(live);
        var c = new TriggeredCircuit(b, 3 * Fixed.ONE, RecursionRules.defaults());
        var outputs = c.step();
        check(
                outputs.size() == 1 && outputs.getFirst().mana().dominant() == Element.WATER,
                "原请求4点但预算3点，事件入口按预算运行水元素子阵");
        check(outputs.getFirst().mana().total() < 3 * Fixed.ONE && c.done(), "连续原图在事件实例中只注入一次");
        ledger(c);
        check(JsonCodecs.snapshot(live).equals(before) && b.continuous(), "事件实例不改原图或原实例现场");
        check(c.step().isEmpty(), "完成后不会因重复调度再次释放");
        for (int raw : new int[] {1, 2, 16, 65536, 262144, 1000000}) {
            var r = new TriggeredCircuit(b, raw, RecursionRules.defaults());
            for (int i = 0; i < 200 && !r.done(); i++) {
                r.step();
                ledger(r);
            }
            check(r.done(), "小额/大额预算都有界结束");
        }
        rejects(() -> new TriggeredCircuit(b, 0, RecursionRules.defaults()));
        rejects(() -> new RecursionRules(1, 17, 64, 120, Fixed.ONE));
        rejects(() -> new TriggerBinding(1, 2, TriggerBinding.When.TIMER, 0, 75));
    }

    private static Blueprint controlled() {
        var a = Rune.create(1, Rune.Kind.INPUT, new Hex(-1, 0));
        var b = Rune.create(2, Rune.Kind.CONVERT, new Hex(0, 0));
        var c =
                new Rune(
                        3,
                        Rune.Kind.SWITCH,
                        new Hex(1, 0),
                        Element.PURE,
                        0,
                        false,
                        true,
                        List.of(1));
        var d = Rune.create(4, Rune.Kind.OUTPUT, new Hex(2, 0));
        var clock =
                new Rune(
                        5,
                        Rune.Kind.CLOCK,
                        new Hex(1, -1),
                        Element.PURE,
                        2 * Fixed.ONE,
                        true,
                        false,
                        List.of());
        return new Blueprint(
                5,
                RuleResources.defaults().withManaSteps(8),
                List.of(a, b, c, d, clock),
                List.of(
                        new Route(1, 2, Route.Payload.MANA, List.of(a.cell(), b.cell())),
                        new Route(2, 3, Route.Payload.MANA, List.of(b.cell(), c.cell())),
                        new Route(3, 4, Route.Payload.MANA, List.of(c.cell(), d.cell())),
                        new Route(5, 3, Route.Payload.SIGNAL, List.of(clock.cell(), c.cell()))),
                true);
    }

    private static void delayedAndSaved() {
        var b = controlled();
        var c = new TriggeredCircuit(b, 2 * Fixed.ONE, RecursionRules.defaults());
        check(c.step().isEmpty(), "信号启动暂态不能被魔力微步预演");
        String encoded = JsonCodecs.triggered(c);
        check(!encoded.contains("\"blueprint\""), "任务保存引用冻结程序，不复制完整蓝图");
        var copy = JsonCodecs.readTriggered(encoded, b, RecursionRules.defaults());
        int count = 0, first = 0;
        for (int i = 2; i < 50 && !c.done(); i++) {
            var a = c.step();
            check(a.equals(copy.step()), "恢复后的控制与效果序列一致");
            if (!a.isEmpty()) {
                count += a.size();
                first = i;
            }
            check(JsonCodecs.triggered(c).equals(JsonCodecs.triggered(copy)), "恢复后的全部预付现场一致");
            ledger(c);
        }
        check(count == 1 && first == 3 && c.unspent() > 0, "预留控制成本，第三步阀门开启后才输出");
        rejects(
                () ->
                        JsonCodecs.readTriggered(
                                encoded.replace("\"budget\":131072", "\"budget\":131073"),
                                b,
                                RecursionRules.defaults()));
        rejects(
                () ->
                        JsonCodecs.readTriggered(
                                encoded, child(Element.FIRE, true), RecursionRules.defaults()));
    }

    private static void multiInput() {
        var a = Rune.create(1, Rune.Kind.INPUT, new Hex(-1, 0));
        var b = Rune.create(2, Rune.Kind.INPUT, new Hex(0, -1));
        var gate = Rune.create(3, Rune.Kind.AND, new Hex(0, 0));
        var out = Rune.create(4, Rune.Kind.OUTPUT, new Hex(1, 0));
        var graph =
                new Blueprint(
                        5,
                        RuleResources.defaults().withManaSteps(8),
                        List.of(a, b, gate, out),
                        List.of(
                                new Route(1, 3, Route.Payload.MANA, List.of(a.cell(), gate.cell())),
                                new Route(2, 3, Route.Payload.MANA, List.of(b.cell(), gate.cell())),
                                new Route(
                                        3,
                                        4,
                                        Route.Payload.MANA,
                                        List.of(gate.cell(), out.cell()))),
                        false);
        var c = new TriggeredCircuit(graph, Fixed.ONE, RecursionRules.defaults());
        var result = c.step();
        check(
                result.size() == 1 && result.getFirst().mana().total() < Fixed.ONE,
                "多个入口分享单份预算，含与门控制成本");
        ledger(c);
        var planned = c.state().circuit().blueprint();
        check(
                planned.rune(1).value() + planned.rune(2).value() < Fixed.ONE
                        && Math.abs(planned.rune(1).value() - planned.rune(2).value()) <= 1,
                "入口额度比例分配，余数稳定");
        rejects(() -> new TriggeredCircuit(graph, 1, RecursionRules.defaults()));
    }

    private static void bindings() {
        var a =
                new CircuitAssembly(
                        UUID.randomUUID(),
                        AssemblyResources.rules(),
                        new TickCircuit(child(Element.FIRE, false)));
        a.add(a.circuit(1).definition().rules());
        a.replace(2, child(Element.WATER, false));
        a.select(1);
        a.configureTrigger(1, TriggerBinding.hit(1, 2));
        a.configureTrigger(2, new TriggerBinding(2, 1, TriggerBinding.When.TIMER, 5, 50));
        var program = InvocationProgram.freeze(a, RecursionRules.defaults());
        check(
                program.bindings().size() == 2 && program.binding(2).target() == 1,
                "A→B→A可表示而不展开Java递归栈");
        String save = JsonCodecs.assembly(a);
        check(
                JsonCodecs.assembly(JsonCodecs.readAssembly(save)).equals(save)
                        && a.snapshot().version() == 2,
                "派生关系现场完整保存循环配置");
        check(JsonCodecs.readProgram(JsonCodecs.program(program)).equals(program), "冻结程序严格往返");
        a.configureTrigger(1, null);
        check(program.binding(1) != null, "发射后改配置不改变已冻结程序");
        var stamp = a.changeStamp();
        a.configureTrigger(2, null);
        check(!a.changeStamp().equals(stamp), "派生设置进入仓储变更检测");
        a.circuit(1).start();
        rejects(() -> a.configureTrigger(1, TriggerBinding.hit(1, 2)));
        a.stopAll();
        rejects(() -> a.configureTrigger(1, TriggerBinding.hit(1, 99)));
        check(
                JsonCodecs.readAssembly(JsonCodecs.assembly(a)).triggers().isEmpty()
                        && a.snapshot().version() == 1,
                "无派生关系现场使用紧凑快照");
        String raw = save.replace("\"target\":2", "\"target\":99");
        rejects(() -> JsonCodecs.readAssembly(raw));
        rejects(() -> new TriggerBinding(1, 2, TriggerBinding.When.IMPACT, 20, 100));
        check(
                InvocationProgram.projectile(
                                new SpellSpec(
                                        SpellSpec.Method.DIRECTED,
                                        SpellSpec.Shape.SPHERE,
                                        0,
                                        0,
                                        0,
                                        0))
                        && !InvocationProgram.projectile(
                                new SpellSpec(
                                        SpellSpec.Method.DIRECTED,
                                        SpellSpec.Shape.BEAM,
                                        0,
                                        0,
                                        0,
                                        0)),
                "只对实际飞行投射物附加胶囊");
        var view = CircuitSelection.inspect(a, false);
        String json = JsonCodecs.selection(view);
        check(json.length() < 2048 && JsonCodecs.readSelection(json).equals(view), "轮盘只传轻量槽位观察");
        var book =
                new CircuitAssembly(
                        new UUID(99, 7),
                        AssemblyResources.rules(),
                        new TickCircuit(child(Element.WATER, false)));
        for (int i = 0; i < 3; i++) book.add(book.circuit(1).definition().rules());
        var combined = CircuitSelection.inspect(a, book, book.identity());
        check(
                combined.slots().size() == a.slots().size() + 4
                        && combined.current().carrier().equals(book.identity())
                        && combined.book(),
                "书中槽位追加到法杖轮盘，重复局部槽号保留来源");
        check(combined.slots().stream().filter(combined::selected).count() == 1, "两个来源的同号槽位只有一个高亮");
        check(
                JsonCodecs.readSelection(JsonCodecs.selection(combined)).equals(combined)
                        && JsonCodecs.selection(combined).length() < 4096,
                "合并轮盘有界编解码");
        var choices = combined.slots().stream().map(CircuitSelection.Slot::choice).toList();
        var first = choices.getFirst();
        check(
                BookSelection.cycle(choices, choices.getLast(), 1).equals(first)
                        && BookSelection.cycle(choices, first, -1).equals(choices.getLast()),
                "跨来源循环可正向反向绕回");
        rejects(() -> BookSelection.cycle(List.of(first, first), first, 1));
        rejects(() -> BookSelection.cycle(choices, first, 0));
        rejects(() -> CircuitSelection.inspect(a, a, a.identity()));

        String before = JsonCodecs.snapshot(a.circuit(1));
        BookSelection.cycle(a, 1);
        check(JsonCodecs.snapshot(a.circuit(1)).equals(before), "快捷选用不改法阵现场");
    }

    private static void targetContract() {
        var a =
                new CircuitAssembly(
                        new UUID(88, 3),
                        AssemblyResources.rules(),
                        new TickCircuit(child(Element.FIRE, false)));
        int target = a.add(a.circuit(1).definition().rules());
        rejects(() -> a.configureTrigger(1, TriggerBinding.hit(1, target)));
        a.replace(target, child(Element.WATER, false));
        a.configureTrigger(1, TriggerBinding.hit(1, target));
        String before = JsonCodecs.assembly(a);
        rejects(() -> a.replace(target, Blueprint.empty(a.circuit(target).definition().rules())));
        check(JsonCodecs.assembly(a).equals(before), "被引用子阵不能静默失去入口，拒绝时现场不变");
        a.configureTrigger(1, null);
        a.replace(target, Blueprint.empty(a.circuit(target).definition().rules()));
        check(a.circuit(target).definition().runes().isEmpty(), "解除引用后可清空子阵");
    }

    private static void wheel() {
        check(
                SpellWheelModel.hover(0, 0, 4) == -1 && SpellWheelModel.hover(19.9, 0, 4) == -1,
                "中心死区取消");
        for (int n = 1; n <= 8; n++)
            for (int i = 0; i < n; i++) {
                double angle = -Math.PI / 2 + i * Math.PI * 2 / n;
                check(
                        SpellWheelModel.hover(Math.cos(angle) * 70, Math.sin(angle) * 70, n) == i,
                        "各扇区中心选择正确稳定槽位");
            }
        check(
                SpellWheelModel.hover(-100, 0, 4) == 3 && SpellWheelModel.hover(0, 100, 4) == 2,
                "左侧和下侧无角度绕回错误");
        rejects(() -> SpellWheelModel.hover(Double.NaN, 0, 4));
        rejects(() -> SpellWheelModel.hover(0, 0, 0));
        for (int[] size : new int[][] {{320, 240}, {640, 400}, {1280, 720}, {2560, 1080}})
            check(
                    SpellWheelModel.radius(size[0], size[1]) < Math.min(size[0], size[1]) / 2 - 20,
                    "轮盘适应窗口并留出提示边距");
    }

    private static void explicitConditions() {
        var draft = TriggerDraft.empty(1, 2);
        check(!draft.ready() && draft.when() == null, "新派生配置没有隐式触发条件");
        rejects(draft::binding);
        var changed = draft.target(3).delay(7).percent(60);
        check(!changed.ready(), "修改其他参数不会选中触发条件");
        for (var when : TriggerBinding.When.values()) {
            var chosen = changed.condition(when);
            var binding = chosen.binding();
            check(
                    binding.when() == when
                            && binding.target() == 3
                            && binding.delay() == 7
                            && binding.percent() == 60,
                    "只有显式选择才形成有效配置");
            check(TriggerDraft.from(binding).binding().equals(binding), "已有玩家选择原样恢复");
            check(
                    JsonCodecs.readTriggerBinding(JsonCodecs.triggerBinding(binding))
                            .equals(binding),
                    "触发类型按稳定名称编解码");
            rejects(() -> when.evaluate(null, 1));
            rejects(() -> when.evaluate(TriggerCondition.Event.FLIGHT_TICK, 0));
            check(
                    when.evaluate(TriggerCondition.Event.TERMINAL, 10)
                            == TriggerCondition.Decision.FIRE,
                    "现有终止触发兼容");
        }
        check(!draft.ready(), "选择新草稿不修改旧草稿");
        TriggerCondition policy = TriggerBinding.When.IMPACT;
        check(
                policy.evaluate(TriggerCondition.Event.FLIGHT_TICK, 1)
                        == TriggerCondition.Decision.WAIT,
                "命中策略不随时间自动触发");
        policy = TriggerBinding.When.TIMER;
        check(
                policy.evaluate(TriggerCondition.Event.FLIGHT_TICK, 2)
                                == TriggerCondition.Decision.COUNTDOWN
                        && policy.evaluate(TriggerCondition.Event.FLIGHT_TICK, 1)
                                == TriggerCondition.Decision.FIRE,
                "定时策略在边界触发一次");
        rejects(
                () ->
                        JsonCodecs.readTriggerBinding(
                                "{\"source\":1,\"target\":2,\"delay\":20,\"percent\":75}"));
        rejects(
                () ->
                        JsonCodecs.readTriggerBinding(
                                "{\"source\":1,\"target\":2,\"when\":\"UNKNOWN\",\"delay\":20,\"percent\":75}"));
        rejects(() -> new TriggerBinding(1, 2, null, 20, 75));
    }

    private static void overlappingPlates() {
        var formula = LayerRules.defaults();
        check(
                formula.depth(Rules.Material.WOOD) == 1
                        && formula.depth(Rules.Material.BRASS) == 2
                        && formula.depth(Rules.Material.CRYSTAL) == 4,
                "材料深度通过1×2^等级计算");
        var fractional = new LayerRules(3, Fixed.ONE * 3 / 2);
        check(
                fractional.depth(Rules.Material.WOOD) == 3
                        && fractional.depth(Rules.Material.BRASS) == 4
                        && fractional.depth(Rules.Material.CRYSTAL) == 6,
                "公式最终向下取整，保留中间定点精度");
        check(new LayerRules(16, 4 * Fixed.ONE).depth(Rules.Material.CRYSTAL) == 16, "公式有独立保存格式边界");
        var root = child(Element.FIRE, false);
        var a =
                new CircuitAssembly(
                        UUID.randomUUID(), AssemblyResources.rules(), new TickCircuit(root));
        int two = a.addLayer(1, 2);
        a.replaceLayer(1, two, child(Element.WATER, false));
        int three = a.addLayer(1, 2);
        a.replaceLayer(1, three, child(Element.LIGHT, false));
        rejects(() -> a.addLayer(1, 2));
        check(
                a.slots().equals(List.of(1)) && a.layers(1).ids().equals(List.of(1, 2, 3)),
                "叠阵占同一槽位，不产生新的玩家施法槽");
        a.configureLayer(1, 1, new TriggerBinding(1, 2, TriggerBinding.When.IMPACT, 20, 75));
        a.configureLayer(1, 2, new TriggerBinding(2, 1, TriggerBinding.When.TIMER, 1, 75));
        String before = JsonCodecs.assembly(a);
        var copy = JsonCodecs.readAssembly(before);
        check(
                before.equals(JsonCodecs.assembly(copy)) && copy.snapshot().version() == 3,
                "叠阵现场保存完整阵层定义和关系");
        var p = InvocationProgram.freeze(a, 1, RecursionRules.defaults(), 2);
        check(
                p.version() == 2
                        && p.rules().maxDepth() == 2
                        && p.definition(2).equals(child(Element.WATER, false)),
                "冻结程序只包含当前基板，深度来自材料");
        check(JsonCodecs.readProgram(JsonCodecs.program(p)).equals(p), "v2叠阵程序严格往返");
        rejects(() -> a.replaceLayer(1, 2, Blueprint.empty(root.rules())));
        rejects(() -> a.removeLayer(1, 2));
        rejects(() -> a.configureLayer(1, 1, TriggerBinding.hit(1, 99)));
        check(JsonCodecs.assembly(a).equals(before), "失效关系拒绝且不改变现场");
        rejects(
                () ->
                        a.replaceLayer(
                                1,
                                3,
                                child(Element.LIGHT, false).withMaterial(Rules.Material.CRYSTAL)));
        a.configureLayer(1, 1, null);
        a.configureLayer(1, 2, null);
        a.removeLayer(1, 2);
        check(a.addLayer(1, 2) == 4, "删除阵层不复用旧编号，迟到操作不会重定向");
        a.replace(1, root.withMaterial(Rules.Material.CRYSTAL));
        check(
                a.layers(1).ids().stream()
                        .allMatch(
                                id ->
                                        a.layers(1)
                                                .definition(id)
                                                .rules()
                                                .equals(a.circuit(1).definition().rules())),
                "更换基板统一调整所有阵层规则");
        var stamp = a.changeStamp();
        a.retimeMana(8);
        var stable = a.changeStamp();
        a.retimeMana(8);
        check(stable.equals(a.changeStamp()), "固定步调速不持续重建叠阵状态");
        a.circuit(1).start();
        rejects(() -> a.addLayer(1, 4));
        a.stopAll();
        int other = a.add(root.rules());
        a.replace(other, child(Element.WATER, false));
        a.select(1);
        check(a.layers(other).ids().equals(List.of(1)), "新基板不共享其他槽位叠阵");
        rejects(
                () ->
                        new InvocationProgram(
                                2,
                                List.of(new InvocationProgram.Definition(2, root)),
                                List.of(),
                                RecursionRules.defaults()));
        rejects(
                () ->
                        new InvocationProgram(
                                2,
                                List.of(
                                        new InvocationProgram.Definition(1, root),
                                        new InvocationProgram.Definition(
                                                2, root.withMaterial(Rules.Material.CRYSTAL))),
                                List.of(),
                                RecursionRules.defaults()));
        rejects(() -> JsonCodecs.readAssembly(before.replace("\"target\":2", "\"target\":99")));
        check(stamp != null, "阵层进入仓储变更检测");
    }

    public static int run() {
        overlappingPlates();
        explicitConditions();
        isolated();
        delayedAndSaved();
        multiInput();
        bindings();
        targetContract();
        wheel();
        return checks;
    }
}
