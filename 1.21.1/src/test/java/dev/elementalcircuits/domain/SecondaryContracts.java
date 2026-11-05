package dev.elementalcircuits.domain;

import com.google.gson.*;

import dev.elementalcircuits.adapter.codec.BlueprintTextCode;
import dev.elementalcircuits.adapter.codec.JsonCodecs;
import dev.elementalcircuits.adapter.resources.AssemblyResources;
import dev.elementalcircuits.adapter.resources.LogicResources;
import dev.elementalcircuits.adapter.resources.ProjectileResources;
import dev.elementalcircuits.adapter.resources.RuleResources;
import dev.elementalcircuits.adapter.resources.SecondaryResources;
import dev.elementalcircuits.client.editor.*;
import dev.elementalcircuits.client.editor.EditorDraft;
import dev.elementalcircuits.domain.casting.DerivationState;
import dev.elementalcircuits.domain.casting.ProjectileEffects;
import dev.elementalcircuits.domain.casting.SecondaryBudget;
import dev.elementalcircuits.domain.casting.SecondaryRules;
import dev.elementalcircuits.domain.casting.SpellSpec;
import dev.elementalcircuits.domain.circuit.Blueprint;
import dev.elementalcircuits.domain.circuit.CircuitAddress;
import dev.elementalcircuits.domain.circuit.CircuitAssembly;
import dev.elementalcircuits.domain.circuit.CircuitBounds;
import dev.elementalcircuits.domain.circuit.Route;
import dev.elementalcircuits.domain.circuit.Rune;
import dev.elementalcircuits.domain.circuit.TickCircuit;
import dev.elementalcircuits.domain.combat.Element;
import dev.elementalcircuits.domain.examples.ProjectileExamples;
import dev.elementalcircuits.domain.mana.ManaPool;
import dev.elementalcircuits.domain.mana.Mixture;
import dev.elementalcircuits.domain.mana.Packet;

import java.nio.file.*;
import java.util.*;

public final class SecondaryContracts {
    private static int checks;

    private static void check(boolean ok, String why) {
        checks++;
        if (!ok) throw new AssertionError(why);
    }

    private static void rejects(Runnable r, String why) {
        boolean no = false;
        try {
            r.run();
        } catch (RuntimeException e) {
            no = true;
        }
        check(no, why);
    }

    private static final List<Rune.Kind> KINDS =
            List.of(
                    Rune.Kind.FRAGMENT,
                    Rune.Kind.CHAIN,
                    Rune.Kind.IGNITE,
                    Rune.Kind.SLOW,
                    Rune.Kind.KNOCKBACK);

    private static void definitions() {
        var rules = SecondaryResources.rules();
        check(rules.equals(SecondaryRules.protocolV1()), "二次派生规则来自版本化资源");
        check(
                JsonCodecs.readSecondaryRules(JsonCodecs.secondaryRules(rules)).equals(rules),
                "冻结规则完整往返");
        var board =
                ProjectileExamples.secondary(
                        RuleResources.defaults(),
                        LogicResources.defaults(),
                        ProjectileResources.rules(),
                        rules);
        check(board.runes().size() == 10 && board.routes().size() == 9, "木质基板普通十符文示例");
        for (var k : KINDS) {
            Rune r =
                    Rune.create(
                            1,
                            k,
                            new Hex(0, 0),
                            LogicResources.defaults(),
                            ProjectileResources.rules(),
                            rules);
            check(
                    k.manaIn() && k.manaOut() && !k.signalIn() && !k.signalOut(),
                    "新符文随魔力包配置，信号不隐式转化");
            check(
                    RuneCatalog.Group.EFFECT.children.contains(k) && !RuneShapes.of(k).isEmpty(),
                    "有独立目录与符形");
            var d = new EditorDraft(Blueprint.empty(RuleResources.defaults()));
            d.place(k, new Hex(0, 0));
            check(d.get().rune(1).value() == r.value(), "落笔参数使用注入资源");
            rejects(
                    () ->
                            new Rune(
                                    1,
                                    k,
                                    new Hex(0, 0),
                                    Element.FIRE,
                                    r.value() + 1,
                                    true,
                                    false,
                                    List.of(1)),
                    "所有效果拒绝小数");
            rejects(
                    () -> new Rune(1, k, new Hex(0, 0), Element.FIRE, 0, true, false, List.of(1)),
                    "所有效果拒绝零");
            rejects(
                    () ->
                            new Rune(
                                    1,
                                    k,
                                    new Hex(0, 0),
                                    Element.FIRE,
                                    (CircuitBounds.DURATION + 1) * Fixed.ONE,
                                    true,
                                    false,
                                    List.of(1)),
                    "效果参数上界冻结");
        }
        var injected = new SecondaryRules(1, 4, 3, 5, 4, 2, 5, 4, 45, Fixed.ONE / 2, Fixed.ONE / 2);
        check(
                Rune.create(
                                        1,
                                        Rune.Kind.FRAGMENT,
                                        new Hex(0, 0),
                                        LogicResources.defaults(),
                                        ProjectileResources.rules(),
                                        injected)
                                .value()
                        == 4 * Fixed.ONE,
                "自定义默认值冻结到创建配置");
        var spec = new SpellSpec(SpellSpec.Method.DIRECTED, SpellSpec.Shape.BEAM, 0, 0, 0, 0);
        for (var k : KINDS) spec = spec.modify(Rune.create(1, k, new Hex(0, 0)));
        check(
                spec.compatibleOutput()
                        && spec.effects().fragments() == 3
                        && spec.effects().chains() == 2
                        && spec.effects().hit(),
                "光束可组合命中派生和三种附加效果");
        var full = spec;
        check(full.modify(Rune.create(1, Rune.Kind.CHAIN, new Hex(0, 0))).equals(full), "同值连锁幂等");
        rejects(
                () ->
                        full.merge(
                                new SpellSpec(
                                        SpellSpec.Method.UNSET,
                                        SpellSpec.Shape.UNSET,
                                        0,
                                        0,
                                        0,
                                        -1,
                                        new ProjectileEffects(0, 0, 0, 0, 3, 0, 0, 0))),
                "不同连锁配置不能覆盖");
        var packet = new Packet(Mixture.pure(Fixed.ONE), full);
        check(JsonCodecs.readPacket(JsonCodecs.packetText(packet)).equals(packet), "新完整请求往返");
        var legacy = JsonParser.parseString(JsonCodecs.packetText(packet)).getAsJsonObject();
        var e = legacy.getAsJsonObject("spell").getAsJsonObject("effects");
        for (var k : List.of("fragments", "chains", "ignite", "slow", "knockback")) e.remove(k);
        check(
                !JsonCodecs.readPacket(legacy.toString()).spell().effects().secondary(),
                "旧三字段效果明确无二次派生");
        e.addProperty("chains", 2);
        rejects(() -> JsonCodecs.readPacket(legacy.toString()), "部分新字段丢失必须拒绝");
        var state = DerivationState.initial(full.effects());
        var id = UUID.randomUUID();
        var hit = state.afterHit(id);
        check(
                state.splitReady()
                        && state.chainLeft() == 2
                        && state.visited().isEmpty()
                        && hit.equals(List.of(id)),
                "运行请求与命中名单不可变隔离");
        rejects(() -> hit.add(UUID.randomUUID()), "观察名单不可修改");
        rejects(() -> new DerivationState(false, 2, List.of(id, id)), "拒绝重复命中名单");
        rejects(
                () -> new DerivationState(true, 0, List.of()).validate(ProjectileEffects.NONE),
                "不能伪造分裂权限");
        var chain = new DerivationState(false, 1, hit);
        check(chain.afterHit(id).equals(hit), "重复记录不增长");
    }

    private static void budgets() {
        var rng = new Random(29109);
        var rules = SecondaryResources.rules();
        var effects = new ProjectileEffects(0, 0, 0, 3, 4, 5, 5, 4);
        for (int n = 0; n < 300; n++) {
            long[] a = new long[17];
            for (int i = 0; i < 17; i++) a[i] = rng.nextInt(50000);
            var mana = new Mixture(a);
            for (boolean split : List.of(false, true))
                for (boolean chain : List.of(false, true)) {
                    var budget = SecondaryBudget.contact(mana, split, chain);
                    check(
                            budget.impact()
                                    .plus(budget.fragments())
                                    .plus(budget.chain())
                                    .sameAmounts(mana),
                            "直接/分裂/连锁逐成分唯一分配");
                    check(split || budget.fragments().empty(), "无分裂不能获得份额");
                    check(chain || budget.chain().empty(), "无连锁不能获得份额");
                    var hit = SecondaryBudget.hit(budget.impact(), effects);
                    var sum = hit.elemental();
                    for (var part : hit.modifiers()) sum = sum.plus(part);
                    check(sum.sameAmounts(budget.impact()), "附加效果和元素不会重复使用同一份额");
                    for (var part : hit.modifiers()) {
                        check(
                                SecondaryBudget.duration(part.total(), 2 * Fixed.ONE, 5, rules)
                                        <= 100,
                                "时长受请求上限限制");
                        check(
                                SecondaryBudget.impulse(part.total(), 2 * Fixed.ONE, 4, rules)
                                        <= Fixed.ONE,
                                "冲量有界");
                    }
                    check(
                            budget.fragments().retain(rules.retention()).total()
                                    <= budget.fragments().total(),
                            "子代变换计耗散");
                }
        }
        check(
                SecondaryBudget.duration(0, Fixed.ONE, 5, rules) == 0
                        && SecondaryBudget.impulse(0, Fixed.ONE, 4, rules) == 0,
                "无支付不产生效果");
        check(SecondaryBudget.duration(Fixed.ONE / 8, Fixed.ONE, 5, rules) == 10, "小预算按比例缩短持续时间");
        check(
                SecondaryBudget.hit(Mixture.pure(1), effects).modifiers().stream()
                                .mapToLong(Mixture::total)
                                .sum()
                        == 0,
                "舍入不凭空制造附加预算");
    }

    private static void runtime() {
        var board =
                ProjectileExamples.secondary(
                        RuleResources.defaults(),
                        LogicResources.defaults(),
                        ProjectileResources.rules(),
                        SecondaryResources.rules());
        var c = new TickCircuit(board);
        c.start();
        var pool = new ManaPool(64 * Fixed.ONE, 0, 0);
        int outputs = 0;
        for (int i = 0; i < 42; i++) {
            var copy = JsonCodecs.readSnapshot(JsonCodecs.snapshot(c));
            var twin = ManaPool.restore(pool.snapshot(), 0, 0);
            var result = c.tick(pool);
            check(
                    copy.tick(twin).equals(result)
                            && JsonCodecs.snapshot(c).equals(JsonCodecs.snapshot(copy)),
                    "任意VM边界保存包含新修饰并同轨迹继续");
            for (var cast : result) {
                outputs++;
                check(
                        cast.spell().effects().fragments() == 3
                                && cast.spell().effects().chains() == 2
                                && cast.spell().effects().hit(),
                        "真实输出携带全部五种新效果");
            }
            var s = c.snapshot();
            check(s.supplied() == s.loss() + s.delivered() + c.held(), "新增修饰继续遵守法阵资源账本");
        }
        check(outputs == 1, "命中派生不在VM内免费重复输出");
        check(BlueprintTextCode.decode(BlueprintTextCode.encode(board)).equals(board), "蓝图仅保存创建配置");
        var source =
                new CircuitAssembly(
                        UUID.randomUUID(),
                        AssemblyResources.rules(),
                        new TickCircuit(
                                AssemblyContracts.line(
                                        false,
                                        Route.Payload.MANA,
                                        Rune.Kind.INPUT,
                                        Rune.Kind.FRAGMENT,
                                        Rune.Kind.CHAIN,
                                        Rune.Kind.IGNITE,
                                        Rune.Kind.MANA_SEND)));
        source.add(RuleResources.defaults());
        source.replace(
                2,
                AssemblyContracts.line(
                        true,
                        Route.Payload.MANA,
                        Rune.Kind.MANA_RECEIVE,
                        Rune.Kind.DIRECTED,
                        Rune.Kind.SPHERE,
                        Rune.Kind.OUTPUT));
        source.links()
                .connect(
                        new CircuitAddress(source.identity(), 1, 5),
                        new CircuitAddress(source.identity(), 2, 1),
                        Route.Payload.MANA,
                        id -> source);
        source.circuit(1).start();
        source.circuit(2).start();
        var bank = new ManaPool(64 * Fixed.ONE, 0, 0);
        int linked = 0;
        for (int i = 0; i < 30; i++)
            for (var cast : source.tick(bank, 1)) {
                linked++;
                check(
                        cast.cast().spell().effects().fragments() == 3
                                && cast.cast().spell().effects().ignite() == 2,
                        "分裂连锁附加请求跨阵保留");
            }
        check(linked == 1, "跨阵不复制输出");
        try {
            var dir = Path.of("build/secondary-examples");
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("命中派生.json"), JsonCodecs.blueprint(board));
            Files.writeString(dir.resolve("命中派生.ec1.txt"), BlueprintTextCode.encode(board));
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static int run() {
        definitions();
        budgets();
        runtime();
        return checks;
    }
}
