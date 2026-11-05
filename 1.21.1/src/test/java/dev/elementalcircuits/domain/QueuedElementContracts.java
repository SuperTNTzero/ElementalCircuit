package dev.elementalcircuits.domain;

import com.google.gson.*;

import dev.elementalcircuits.adapter.codec.JsonCodecs;
import dev.elementalcircuits.adapter.resources.ElementalCombatResources;
import dev.elementalcircuits.domain.casting.SpellSpec;
import dev.elementalcircuits.domain.circuit.CircuitBounds;
import dev.elementalcircuits.domain.circuit.MagicCircuit;
import dev.elementalcircuits.domain.circuit.Route;
import dev.elementalcircuits.domain.circuit.Rune;
import dev.elementalcircuits.domain.circuit.TickCircuit;
import dev.elementalcircuits.domain.combat.DamageAffinity;
import dev.elementalcircuits.domain.combat.Element;
import dev.elementalcircuits.domain.combat.ElementReactionQueue;
import dev.elementalcircuits.domain.combat.ElementalCombat;
import dev.elementalcircuits.domain.combat.ElementalCombatRules;
import dev.elementalcircuits.domain.mana.ManaPool;
import dev.elementalcircuits.domain.mana.Mixture;
import dev.elementalcircuits.domain.mana.Packet;

import java.util.*;

/** Actual sequence semantics, resource conservation, transport and historical migration. */
public final class QueuedElementContracts {
    private static int checks;
    private static final ElementalCombatRules R = ElementalCombatResources.rules();
    private static final ElementalCombat.State DRY =
            new ElementalCombat.State(false, false, 0, false, false, false);

    private static void check(boolean ok, String why) {
        checks++;
        if (!ok) throw new AssertionError(why);
    }

    private static void rejects(Runnable r) {
        boolean bad = false;
        try {
            r.run();
        } catch (RuntimeException e) {
            bad = true;
        }
        check(bad, "畸形顺序必须拒绝");
    }

    private static Mixture m(Element e, long n) {
        return Mixture.ordered(List.of(new Mixture.Portion(e.component(false), n, 0)));
    }

    private static ElementalCombat.Plan hit(Mixture m) {
        return ElementalCombat.evaluate(m, Fixed.ONE, DRY, R);
    }

    private static void legacy(JsonElement e) {
        if (e.isJsonObject()) {
            var o = e.getAsJsonObject();
            o.remove("arrivalSequence");
            o.remove("orderVersion");
            o.remove("order");
            for (var v : o.entrySet()) legacy(v.getValue());
        } else if (e.isJsonArray()) for (var v : e.getAsJsonArray()) legacy(v);
    }

    private static void inverseArcane() {
        var rules = R.reactions();
        int antiComponent = Element.ARCANE.component(true);
        var anti = Mixture.ordered(List.of(new Mixture.Portion(antiComponent, Fixed.ONE, 0)));
        check(rules.version() == 2, "新反应表明确使用v2");
        for (Element element : Element.values()) {
            if (element == Element.PURE) continue;
            for (boolean inverse : new boolean[] {false, true}) {
                int component = element.component(inverse);
                if (component == antiComponent) continue;
                var other = Mixture.ordered(List.of(new Mixture.Portion(component, Fixed.ONE, 0)));
                check(
                        ElementReactionQueue.reduce(other.plus(anti), rules).remaining().empty(),
                        "反相奥术抵消先到的正反元素：" + component);
                check(
                        ElementReactionQueue.reduce(anti.plus(other), rules).remaining().empty(),
                        "反相奥术抵消后到的正反元素：" + component);
                check(
                        ElementReactionQueue.reduce(other.plus(other).plus(anti), rules)
                                .remaining()
                                .sameAmounts(other),
                        "严格按1:1抵消，余量保留");
            }
        }
        check(
                ElementReactionQueue.reduce(anti.plus(anti), rules).remaining().total()
                        == 2 * Fixed.ONE,
                "反相奥术彼此不抵消");
        var pure = Mixture.pure(Fixed.ONE);
        check(
                ElementReactionQueue.reduce(pure.plus(anti), rules)
                        .remaining()
                        .sameAmounts(pure.plus(anti)),
                "纯净魔力不属于元素，保持不变");
        var fire = m(Element.FIRE, Fixed.ONE);
        var lightning = m(Element.LIGHTNING, Fixed.ONE);
        check(
                ElementReactionQueue.reduce(fire.plus(lightning).plus(anti), rules)
                        .remaining()
                        .equals(lightning),
                "反相奥术先消耗最早的相克片段");
        check(
                ElementReactionQueue.reduce(anti.plus(lightning).plus(fire), rules)
                        .remaining()
                        .equals(fire),
                "反相奥术先到时仍按后续实际到达顺序处理");
        check(
                hit(fire.plus(anti)).totalDamage() == 0 && hit(fire.plus(anti)).steps().isEmpty(),
                "已抵消的火不会造成伤害或点燃");
        check(hit(anti).totalDamage() == 0 && hit(anti).healing() == 0, "反相奥术余量不是换皮奥术伤害");
        var mixed = new Packet(m(Element.ARCANE, Fixed.ONE).plus(anti), SpellSpec.EMPTY);
        check(
                dev.elementalcircuits.domain.casting.SpellPayloads.split(mixed)
                        .equals(List.of(mixed)),
                "正奥术不能通过穿墙分包逃避反相抵消");
        var lit = new Packet(m(Element.LIGHT, 16 * Fixed.ONE).plus(anti), SpellSpec.EMPTY);
        var prepared =
                dev.elementalcircuits.domain.casting.SpellPayloads.illumination(
                        lit,
                        Fixed.ONE,
                        dev.elementalcircuits.adapter.resources.RadianceResources.rules());
        check(prepared.ticks() == 0 && prepared.impact().equals(lit), "含抵消剂的光保留到接触时统一反应，不提前挪作照明");
        check(
                !dev.elementalcircuits.domain.combat.ElementReactionRules.standard()
                        .opposed(antiComponent, Element.FIRE.component(false)),
                "历史v1冻结默认值未改变");
    }

    public static int run() {
        inverseArcane();
        var f = m(Element.FIRE, Fixed.ONE);
        var w = m(Element.WATER, Fixed.ONE);
        var l = m(Element.LIGHTNING, Fixed.ONE);
        var c = f.inverse(Fixed.ONE);
        check(
                hit(f.plus(w)).totalDamage() == 0 && hit(f.plus(w)).steps().isEmpty(),
                "同次命中先相克抵消，无火伤水伤或状态");
        check(hit(f.plus(f).plus(w)).totalDamage() == 2 * Fixed.ONE, "剩余一份火才造成伤害");
        check(
                ElementReactionQueue.reduce(w.plus(f).plus(c), R.reactions()).remaining().equals(c),
                "水先抵消火，寒霜保留");
        check(
                ElementReactionQueue.reduce(f.plus(c).plus(w), R.reactions()).remaining().equals(w),
                "火先抵消寒霜，水保留");
        check(hit(w.plus(l)).damage().get(DamageAffinity.LIGHTNING) == 3 * Fixed.ONE, "水先到，后续雷导电");
        check(hit(l.plus(w)).damage().get(DamageAffinity.LIGHTNING) == 2 * Fixed.ONE, "先雷后水不追溯增伤");
        var earth = m(Element.EARTH, Fixed.ONE);
        var ice =
                f.inverse(Fixed.ONE)
                        .plus(f.inverse(Fixed.ONE))
                        .plus(f.inverse(Fixed.ONE))
                        .plus(f.inverse(Fixed.ONE));
        check(
                hit(ice.plus(earth)).reactions().contains(ElementalCombat.Reaction.SHATTER)
                        && !hit(earth.plus(ice))
                                .reactions()
                                .contains(ElementalCombat.Reaction.SHATTER),
                "冻结与碎冰按剩余队列顺序");
        check(
                hit(f.plus(m(Element.WIND, Fixed.ONE)))
                                .reactions()
                                .contains(ElementalCombat.Reaction.FAN)
                        && !hit(m(Element.WIND, Fixed.ONE).plus(f))
                                .reactions()
                                .contains(ElementalCombat.Reaction.FAN),
                "先火后风才助燃");
        for (Element e : Element.values())
            if (e != Element.PURE)
                check(
                        ElementReactionQueue.reduce(
                                        m(e, 99).plus(m(e, 99).inverse(Fixed.ONE)), R.reactions())
                                .remaining()
                                .empty(),
                        "每种元素正反相消");
        var a = f.arriving(8).plus(w.arriving(10)).plus(f.arriving(12));
        check(
                !a.equals(new Mixture(a.values())) && a.sameAmounts(new Mixture(a.values())),
                "语义相等包括顺序，账本相等只比数量");
        check(
                a.order().size() == 3 && a.inverse(Fixed.ONE).inverse(Fixed.ONE).equals(a),
                "非相邻同元素不折叠，反转保留顺序");
        check(
                new Packet(w.arriving(10), SpellSpec.EMPTY)
                                .merge(new Packet(f.arriving(8), SpellSpec.EMPTY))
                                .mana()
                                .order()
                                .getFirst()
                                .component()
                        == Element.FIRE.component(false),
                "缓存合流按接收序号而非枚举或迭代顺序");
        var packet = new Packet(a, SpellSpec.EMPTY);
        check(
                JsonCodecs.readOrderedPacket(JsonCodecs.packetText(packet)).equals(packet),
                "包v2保存重复元素和到达序号");
        var bad = JsonParser.parseString(JsonCodecs.packetText(packet)).getAsJsonObject();
        bad.getAsJsonObject("mana")
                .getAsJsonArray("order")
                .get(0)
                .getAsJsonObject()
                .addProperty("amount", 1);
        rejects(() -> JsonCodecs.readPacket(bad.toString()));
        var old = JsonParser.parseString(JsonCodecs.packetText(packet)).getAsJsonObject();
        old.remove("packetVersion");
        legacy(old);
        rejects(() -> JsonCodecs.readPacket(old.toString()));
        var vm =
                new TickCircuit(
                        AssemblyContracts.line(
                                false,
                                Route.Payload.MANA,
                                Rune.Kind.INPUT,
                                Rune.Kind.CONVERT,
                                Rune.Kind.OUTPUT));
        vm.start();
        vm.tick(new ManaPool(64 * Fixed.ONE, 0, 0));
        var text = JsonCodecs.snapshot(vm);
        check(JsonCodecs.snapshot(JsonCodecs.readSnapshot(text)).equals(text), "VMv9含队列和到达计数完整往返");
        var prior = JsonParser.parseString(text).getAsJsonObject();
        prior.addProperty("version", 8);
        rejects(() -> JsonCodecs.readSnapshot(prior.toString()));
        var invalid = JsonParser.parseString(text).getAsJsonObject();
        invalid.addProperty("arrivalSequence", 0);
        rejects(() -> JsonCodecs.readSnapshot(invalid.toString()));
        var receiver =
                new TickCircuit(
                        AssemblyContracts.line(
                                false,
                                Route.Payload.MANA,
                                Rune.Kind.MANA_RECEIVE,
                                Rune.Kind.OUTPUT));
        receiver.start();
        check(
                receiver.receive(1, new Packet(w, SpellSpec.EMPTY))
                        && receiver.receive(1, new Packet(l, SpellSpec.EMPTY)),
                "接收成功后才分配到达序号");
        var restored = JsonCodecs.readSnapshot(JsonCodecs.snapshot(receiver));
        var bank = new ManaPool(64 * Fixed.ONE, 0, 0);
        var twin = new ManaPool(64 * Fixed.ONE, 0, 0);
        List<MagicCircuit.Cast> output = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            var first = receiver.tick(bank);
            check(first.equals(restored.tick(twin)), "接收后保存并继续，输出语义顺序一致");
            output.addAll(first);
        }
        check(
                output.size() == 1
                        && output.getFirst().mana().order().getFirst().component()
                                == Element.WATER.component(false)
                        && hit(output.getFirst().mana())
                                .reactions()
                                .contains(ElementalCombat.Reaction.CONDUCTION),
                "实际VM接收水雷顺序传到命中反应");
        var segmented = Mixture.EMPTY;
        for (int i = 0; i < 16; i++)
            segmented = segmented.plus(m(Element.WATER, Fixed.ONE / 16).arriving(i));
        check(
                hit(segmented).damage().equals(hit(w).damage())
                        && hit(segmented).steps().equals(hit(w).steps()),
                "连续同元素到达片段不因分包规避或重复触发状态阈值");
        var crowded =
                new TickCircuit(
                        AssemblyContracts.line(
                                false,
                                Route.Payload.MANA,
                                Rune.Kind.MANA_RECEIVE,
                                Rune.Kind.OUTPUT));
        crowded.start();
        var full = new ArrayList<Mixture.Portion>();
        for (int i = 0; i < CircuitBounds.MANA_SEGMENTS; i++)
            full.add(
                    new Mixture.Portion(
                            (i % 2 == 0 ? Element.WATER : Element.LIGHTNING).component(false),
                            1,
                            i));
        check(
                crowded.receive(1, new Packet(Mixture.ordered(full), SpellSpec.EMPTY)),
                "容量边界内的真实接收成功");
        var before = crowded.snapshot();
        check(
                !crowded.receive(1, new Packet(w, SpellSpec.EMPTY))
                        && crowded.snapshot().arrivalSequence() == before.arrivalSequence()
                        && crowded.snapshot().supplied() == before.supplied()
                        && crowded.held() == before.supplied(),
                "队列满时原子拒收，不记到达或虚增供给");
        var exhausted = JsonParser.parseString(JsonCodecs.snapshot(receiver)).getAsJsonObject();
        exhausted.addProperty("arrivalSequence", Long.MAX_VALUE - 1);
        var guarded = JsonCodecs.readSnapshot(exhausted.toString());
        check(!guarded.receive(1, new Packet(w, SpellSpec.EMPTY)), "接收计数耗尽拒绝，不回绕");
        var rng = new Random(31027);
        for (int n = 0; n < 500; n++) {
            var parts = new ArrayList<Mixture.Portion>();
            for (int i = 0; i < 24; i++)
                parts.add(new Mixture.Portion(1 + rng.nextInt(16), 1 + rng.nextInt(10000), i));
            var input = Mixture.ordered(parts);
            var result = ElementReactionQueue.reduce(input, R.reactions());
            check(input.sameAmounts(result.remaining().plus(result.consumed())), "每个片段只被消费一次，资源守恒");
            check(
                    ElementReactionQueue.reduce(result.remaining(), R.reactions())
                            .remaining()
                            .equals(result.remaining()),
                    "完整抵消后再次求值幂等");
            var shares = input.split(new int[] {1, 2, 3});
            var sum = Mixture.EMPTY;
            for (var share : shares) {
                sum = sum.plus(share);
                long last = -1;
                for (var p : share.order()) {
                    check(p.arrival() >= last, "每条分流保留源片段子序列");
                    last = p.arrival();
                }
            }
            check(sum.sameAmounts(input), "分流不创造或丢失成分");
        }
        return checks;
    }
}
