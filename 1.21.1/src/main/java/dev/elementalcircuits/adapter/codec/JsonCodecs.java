package dev.elementalcircuits.adapter.codec;

import com.google.gson.*;

import dev.elementalcircuits.application.observation.AssemblyInspection;
import dev.elementalcircuits.application.observation.CircuitSelection;
import dev.elementalcircuits.application.observation.SurveyReport;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.book.BlueprintVolume;
import dev.elementalcircuits.domain.book.NotebookInspection;
import dev.elementalcircuits.domain.book.SpellbookInspection;
import dev.elementalcircuits.domain.book.WorkshopInspection;
import dev.elementalcircuits.domain.casting.CastingLedger;
import dev.elementalcircuits.domain.casting.CastingRules;
import dev.elementalcircuits.domain.casting.ProjectileEffects;
import dev.elementalcircuits.domain.casting.ProjectileRules;
import dev.elementalcircuits.domain.casting.RecursionRules;
import dev.elementalcircuits.domain.casting.SecondaryRules;
import dev.elementalcircuits.domain.casting.SpellSpec;
import dev.elementalcircuits.domain.casting.TriggerBinding;
import dev.elementalcircuits.domain.casting.TriggeredCircuit;
import dev.elementalcircuits.domain.circuit.AssemblyRules;
import dev.elementalcircuits.domain.circuit.Blueprint;
import dev.elementalcircuits.domain.circuit.CircuitAddress;
import dev.elementalcircuits.domain.circuit.CircuitAssembly;
import dev.elementalcircuits.domain.circuit.CircuitBounds;
import dev.elementalcircuits.domain.circuit.CircuitLinks;
import dev.elementalcircuits.domain.circuit.CircuitSnapshot;
import dev.elementalcircuits.domain.circuit.InvocationProgram;
import dev.elementalcircuits.domain.circuit.PlateLayers;
import dev.elementalcircuits.domain.circuit.Route;
import dev.elementalcircuits.domain.circuit.Rules;
import dev.elementalcircuits.domain.circuit.Rune;
import dev.elementalcircuits.domain.circuit.StandardState;
import dev.elementalcircuits.domain.circuit.SupplyMode;
import dev.elementalcircuits.domain.circuit.TickCircuit;
import dev.elementalcircuits.domain.combat.Element;
import dev.elementalcircuits.domain.equipment.WandParts;
import dev.elementalcircuits.domain.logic.LogicDefaults;
import dev.elementalcircuits.domain.logic.LogicObservation;
import dev.elementalcircuits.domain.logic.RuneLogicState;
import dev.elementalcircuits.domain.mana.ManaOrigin;
import dev.elementalcircuits.domain.mana.ManaPool;
import dev.elementalcircuits.domain.mana.Mixture;
import dev.elementalcircuits.domain.mana.Packet;
import dev.elementalcircuits.domain.mana.RefundTicket;
import dev.elementalcircuits.domain.mana.StorageSettings;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** All untrusted JSON is decoded and validated before an active carrier can be changed. */
public final class JsonCodecs {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    public static final int BLUEPRINT_LIMIT = 2097152, SNAPSHOT_LIMIT = 16777216;

    private JsonCodecs() {}

    private static JsonElement parse(String text, int max) {
        if (text == null || text.getBytes(StandardCharsets.UTF_8).length > max)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.adapter.codec._json_codecs.001"));
        int depth = 0;
        boolean quoted = false, escaped = false;
        for (char c : text.toCharArray()) {
            if (quoted) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') quoted = false;
            } else if (c == '"') quoted = true;
            else if (c == '{' || c == '[') {
                if (++depth > 32)
                    throw new IllegalArgumentException(
                            dev.elementalcircuits.text.Texts.get(
                                    "text.elementalcircuits.adapter.codec._json_codecs.002"));
            } else if (c == '}' || c == ']') depth--;
        }
        JsonElement value = JsonParser.parseString(text);
        if (!value.isJsonObject())
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.adapter.codec._json_codecs.003"));
        checkIntegers(value);
        return value;
    }

    private static void checkIntegers(JsonElement e) {
        if (e.isJsonArray()) for (JsonElement child : e.getAsJsonArray()) checkIntegers(child);
        else if (e.isJsonObject())
            for (var entry : e.getAsJsonObject().entrySet()) checkIntegers(entry.getValue());
        else if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
            String number = e.getAsString();
            if (!number.matches("-?(0|[1-9][0-9]{0,18})"))
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.adapter.codec._json_codecs.004"));
            try {
                Long.parseLong(number);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.adapter.codec._json_codecs.005"));
            }
        }
    }

    private static long number(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                                    "text.elementalcircuits.adapter.codec._json_codecs.006")
                            + key);
        return e.getAsLong();
    }

    private static int integer(JsonObject o, String key) {
        return Math.toIntExact(number(o, key));
    }

    private static boolean bool(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isBoolean())
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                                    "text.elementalcircuits.adapter.codec._json_codecs.007")
                            + key);
        return e.getAsBoolean();
    }

    private static String string(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                                    "text.elementalcircuits.adapter.codec._json_codecs.008")
                            + key);
        return e.getAsString();
    }

    private static JsonArray array(JsonObject o, String key, int limit) {
        JsonArray a = o.getAsJsonArray(key);
        if (a == null || a.size() > limit)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                                    "text.elementalcircuits.adapter.codec._json_codecs.009")
                            + key);
        return a;
    }

    private static <T extends Enum<T>> T enumeration(Class<T> cls, JsonObject o, String key) {
        return Enum.valueOf(cls, string(o, key));
    }

    private static Hex hex(JsonElement e) {
        JsonObject o = e.getAsJsonObject();
        return new Hex(integer(o, "q"), integer(o, "r"));
    }

    private static Rules rules(JsonObject r) {
        if (integer(r, "version") >= 2 && !r.has("boards"))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.adapter.codec._json_codecs.010"));
        if (integer(r, "version") >= 3 && !r.has("manaSteps"))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.adapter.codec._json_codecs.011"));
        return new Rules(
                integer(r, "version"),
                enumeration(Rules.Material.class, r, "material"),
                number(r, "conversion"),
                number(r, "inversion"),
                number(r, "coupling"),
                number(r, "nodeCapacity"),
                number(r, "injection"),
                integer(r, "interval"),
                integer(r, "window"),
                number(r, "signalCost"),
                number(r, "playerCapacity"),
                number(r, "playerRegen"),
                integer(r, "playerDelay"),
                number(r, "range"),
                number(r, "radius"),
                number(r, "routingRetention"),
                number(r, "oppositionRetention"),
                r.has("boards")
                        ? new Rules.BoardSizes(
                                integer(r.getAsJsonObject("boards"), "wood"),
                                integer(r.getAsJsonObject("boards"), "brass"),
                                integer(r.getAsJsonObject("boards"), "crystal"))
                        : Rules.BoardSizes.legacy(),
                r.has("manaSteps") ? integer(r, "manaSteps") : 1,
                integer(r, "version") >= 4
                        ? castingRules(r.getAsJsonObject("casting"))
                        : CastingRules.legacy());
    }

    private static CastingRules castingRules(JsonObject o) {
        var rates = new ArrayList<Long>();
        for (var e : array(o, "refundRates", Mixture.COMPONENTS)) {
            if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber())
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.adapter.codec._json_codecs.012"));
            rates.add(e.getAsLong());
        }
        return new CastingRules(
                integer(o, "version"),
                number(o, "parameterLossScale"),
                number(o, "shapeCostPerBlock"),
                number(o, "failureLossScale"),
                rates,
                number(o, "movingLoss"),
                number(o, "cacheLoss"),
                number(o, "defaultEffect"),
                integer(o, "version") >= 2
                        ? standard(o.getAsJsonObject("standard"))
                        : StandardState.LEGACY);
    }

    public static SecondaryRules readSecondaryRules(String text) {
        var o = parse(text, 2048).getAsJsonObject();
        return new SecondaryRules(
                integer(o, "version"),
                integer(o, "fragments"),
                integer(o, "chains"),
                integer(o, "ignite"),
                integer(o, "slow"),
                integer(o, "knockback"),
                integer(o, "childRange"),
                integer(o, "chainRange"),
                integer(o, "fanDegrees"),
                number(o, "retention"),
                number(o, "statusCost"));
    }

    public static String secondaryRules(SecondaryRules rules) {
        return GSON.toJson(rules);
    }

    public static ProjectileRules readProjectileRules(String text) {
        var o = parse(text, 2048).getAsJsonObject();
        return new ProjectileRules(
                integer(o, "version"),
                integer(o, "burst"),
                integer(o, "bounces"),
                integer(o, "homing"),
                number(o, "bounceRetention"),
                integer(o, "homingDivisor"),
                integer(o, "turnDegrees"));
    }

    public static String projectileRules(ProjectileRules rules) {
        return GSON.toJson(rules);
    }

    private static ProjectileEffects projectileEffects(JsonObject s) {
        if (!s.has("effects")) return ProjectileEffects.NONE;
        var e = s.getAsJsonObject("effects");
        if (!e.has("fragments")
                && !e.has("chains")
                && !e.has("ignite")
                && !e.has("slow")
                && !e.has("knockback"))
            return new ProjectileEffects(
                    integer(e, "burst"), integer(e, "bounces"), integer(e, "homing"));
        return new ProjectileEffects(
                integer(e, "burst"),
                integer(e, "bounces"),
                integer(e, "homing"),
                integer(e, "fragments"),
                integer(e, "chains"),
                integer(e, "ignite"),
                integer(e, "slow"),
                integer(e, "knockback"));
    }

    private static StandardState standard(JsonObject o) {
        return new StandardState(
                integer(o, "version"),
                number(o, "transmissionLossScale"),
                number(o, "storageLoss"),
                integer(o, "delayTicks"),
                integer(o, "maximumDelay"),
                number(o, "nodeWeight"),
                number(o, "lengthWeight"),
                number(o, "depthWeight"),
                number(o, "feedbackWeight"),
                number(o, "referenceComplexity"),
                integer(o, "range"),
                integer(o, "radius"),
                integer(o, "aim"));
    }

    private static StorageSettings storage(JsonObject r) {
        if (!r.has("storage")) {
            if (enumeration(Rune.Kind.class, r, "kind") == Rune.Kind.STORAGE)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.adapter.codec._json_codecs.013"));
            return StorageSettings.NONE;
        }
        var o = r.getAsJsonObject("storage");
        return new StorageSettings(
                number(o, "capacity"), number(o, "threshold"), number(o, "release"));
    }

    private static long[] timing(JsonObject o, int size) {
        var a = array(o, "wakeTicks", CircuitBounds.RUNES);
        if (a.size() != size)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.adapter.codec._json_codecs.014"));
        long[] values = new long[size];
        for (int i = 0; i < size; i++) values[i] = a.get(i).getAsLong();
        return values;
    }

    public static LogicDefaults readLogicDefaults(String text) {
        var o = parse(text, 2048).getAsJsonObject();
        return new LogicDefaults(
                integer(o, "version"), integer(o, "counterThreshold"), integer(o, "clockPeriod"));
    }

    public static Rules readRules(String text) {
        return rules(parse(text, 8192).getAsJsonObject());
    }

    public static String blueprint(Blueprint b) {
        return GSON.toJson(b);
    }

    public static Blueprint readBlueprint(String text) {
        return blueprint(parse(text, BLUEPRINT_LIMIT).getAsJsonObject());
    }

    private static Blueprint blueprint(JsonObject root) {
        List<Rune> runes = new ArrayList<>();
        List<Route> routes = new ArrayList<>();
        for (JsonElement e : array(root, "runes", CircuitBounds.RUNES)) {
            JsonObject r = e.getAsJsonObject();
            List<Integer> w = new ArrayList<>();
            for (JsonElement v : array(r, "weights", CircuitBounds.ROUTES)) {
                if (!v.isJsonPrimitive() || !v.getAsJsonPrimitive().isNumber())
                    throw new IllegalArgumentException(
                            dev.elementalcircuits.text.Texts.get(
                                    "text.elementalcircuits.adapter.codec._json_codecs.015"));
                w.add(Math.toIntExact(v.getAsLong()));
            }
            runes.add(
                    new Rune(
                            integer(r, "id"),
                            enumeration(Rune.Kind.class, r, "kind"),
                            hex(r.get("cell")),
                            enumeration(Element.class, r, "element"),
                            number(r, "value"),
                            bool(r, "on"),
                            bool(r, "signalControl"),
                            w,
                            storage(r),
                            string(r, "filter")));
        }
        for (JsonElement e : array(root, "routes", CircuitBounds.ROUTES)) {
            JsonObject r = e.getAsJsonObject();
            List<Hex> path = new ArrayList<>();
            for (JsonElement h : array(r, "path", CircuitBounds.PATH)) path.add(hex(h));
            routes.add(
                    new Route(
                            integer(r, "source"),
                            integer(r, "target"),
                            enumeration(Route.Payload.class, r, "payload"),
                            path,
                            r.has("sourcePort") ? integer(r, "sourcePort") : 0));
        }
        return new Blueprint(
                integer(root, "version"),
                rules(root.getAsJsonObject("rules")),
                runes,
                routes,
                bool(root, "continuous"));
    }

    private static Mixture mixture(JsonObject root) {
        long[] values = new long[Mixture.COMPONENTS];
        JsonArray a = array(root, "values", Mixture.COMPONENTS);
        if (a.size() != values.length)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.adapter.codec._json_codecs.016"));
        for (int i = 0; i < values.length; i++) {
            if (!a.get(i).isJsonPrimitive() || !a.get(i).getAsJsonPrimitive().isNumber())
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.adapter.codec._json_codecs.017"));
            values[i] = a.get(i).getAsLong();
        }
        requireOrder(root);
        var parts = new ArrayList<Mixture.Portion>();
        for (var item : array(root, "order", CircuitBounds.MANA_SEGMENTS)) {
            var part = item.getAsJsonObject();
            parts.add(
                    new Mixture.Portion(
                            integer(part, "component"),
                            number(part, "amount"),
                            number(part, "arrival"),
                            integer(root, "orderVersion") >= 2
                                    ? new ManaOrigin(
                                            part.getAsJsonObject("origin").get("key").getAsString())
                                    : ManaOrigin.UNKNOWN));
        }
        var result = Mixture.ordered(parts);
        if (!result.sameAmounts(new Mixture(values)))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.adapter.codec._json_codecs.018"));
        return result;
    }

    private static void requireOrder(JsonObject root) {
        if (!root.has("orderVersion")
                || (integer(root, "orderVersion") < 1 || integer(root, "orderVersion") > 2)
                || !root.has("order"))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.adapter.codec._json_codecs.019"));
    }

    private static void requireOrderedTree(JsonElement value) {
        if (value.isJsonObject()) {
            var o = value.getAsJsonObject();
            if (o.has("values")) requireOrder(o);
            for (var e : o.entrySet()) requireOrderedTree(e.getValue());
        } else if (value.isJsonArray()) for (var e : value.getAsJsonArray()) requireOrderedTree(e);
    }

    private static void requireSourcedTree(JsonElement value) {
        if (value.isJsonObject()) {
            var o = value.getAsJsonObject();
            if (o.has("values") && integer(o, "orderVersion") != 2)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.adapter.codec._json_codecs.020"));
            if (o.has("spell")) number(o.getAsJsonObject("spell"), "damageMultiplier");
            for (var e : o.entrySet()) requireSourcedTree(e.getValue());
        } else if (value.isJsonArray()) for (var e : value.getAsJsonArray()) requireSourcedTree(e);
    }

    private static Packet packet(JsonElement e) {
        if (e.isJsonNull()) return null;
        JsonObject p = e.getAsJsonObject(), s = p.getAsJsonObject("spell");
        return new Packet(
                mixture(p.getAsJsonObject("mana")),
                new SpellSpec(
                        enumeration(SpellSpec.Method.class, s, "method"),
                        enumeration(SpellSpec.Shape.class, s, "shape"),
                        number(s, "amount"),
                        integer(s, "range"),
                        integer(s, "radius"),
                        integer(s, "aim"),
                        projectileEffects(s),
                        s.has("damageMultiplier") ? number(s, "damageMultiplier") : Fixed.ONE,
                        s.has("reactive") && bool(s, "reactive"),
                        s.has("effectScale")
                                ? new dev.elementalcircuits.domain.casting.EffectScale(
                                        number(s.getAsJsonObject("effectScale"), "numerator"),
                                        number(s.getAsJsonObject("effectScale"), "denominator"))
                                : dev.elementalcircuits.domain.casting.EffectScale.IDENTITY,
                        materialPorts(s),
                        connectionEffects(s),
                        spatialParameters(s)));
    }

    private static dev.elementalcircuits.domain.casting.SpatialParameters spatialParameters(
            JsonObject s) {
        if (!s.has("spatial")) return dev.elementalcircuits.domain.casting.SpatialParameters.NONE;
        var p = s.getAsJsonObject("spatial");
        return new dev.elementalcircuits.domain.casting.SpatialParameters(
                number(p, "angle"), number(p, "yaw"), number(p, "pitch"), number(p, "distance"));
    }

    private static dev.elementalcircuits.domain.casting.ConnectionEffects connectionEffects(
            JsonObject s) {
        if (!s.has("connection"))
            return dev.elementalcircuits.domain.casting.ConnectionEffects.NONE;
        var c = s.getAsJsonObject("connection");
        return new dev.elementalcircuits.domain.casting.ConnectionEffects(
                number(c, "force"),
                number(c, "constraint"),
                bool(c, "interact"),
                bool(c, "high"),
                c.has("anchored") && bool(c, "anchored"),
                c.has("target") ? integer(c, "target") : 0,
                c.has("control") ? integer(c, "control") : 0);
    }

    private static dev.elementalcircuits.domain.casting.MaterialPorts materialPorts(
            JsonObject spell) {
        if (!spell.has("materials")) return dev.elementalcircuits.domain.casting.MaterialPorts.NONE;
        var m = spell.getAsJsonObject("materials");
        return new dev.elementalcircuits.domain.casting.MaterialPorts(
                integer(m, "input"),
                integer(m, "output"),
                !m.has("anchor") || m.get("anchor").isJsonNull()
                        ? null
                        : java.util.UUID.fromString(m.get("anchor").getAsString()),
                string(m, "filter"));
    }

    private static Packet[] packets(JsonObject o, String key, int size) {
        JsonArray a = array(o, key, CircuitBounds.ROUTES);
        if (a.size() != size)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.adapter.codec._json_codecs.021"));
        Packet[] p = new Packet[size];
        for (int i = 0; i < size; i++) p[i] = packet(a.get(i));
        return p;
    }

    private static boolean[] booleans(JsonObject o, String key, int size) {
        JsonArray a = array(o, key, CircuitBounds.ROUTES);
        if (a.size() != size)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.adapter.codec._json_codecs.022"));
        boolean[] v = new boolean[size];
        for (int i = 0; i < size; i++) {
            if (!a.get(i).isJsonPrimitive() || !a.get(i).getAsJsonPrimitive().isBoolean())
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.adapter.codec._json_codecs.023"));
            v[i] = a.get(i).getAsBoolean();
        }
        return v;
    }

    public static String snapshot(TickCircuit circuit) {
        return GSON.toJson(circuit.snapshot());
    }

    private static List<RuneLogicState> logicStates(JsonObject o) {
        List<RuneLogicState> states = new ArrayList<>();
        for (var value : array(o, "logic", CircuitBounds.RUNES)) {
            var s = value.getAsJsonObject();
            var queue = new ArrayList<RuneLogicState.Transition>();
            for (var item : array(s, "queue", CircuitBounds.DELAY_TICKS + 1)) {
                var event = item.getAsJsonObject();
                queue.add(
                        new RuneLogicState.Transition(
                                number(event, "due"), bool(event, "value"), bool(event, "valid")));
            }
            states.add(
                    new RuneLogicState(
                            integer(s, "count"),
                            integer(s, "phase"),
                            bool(s, "latched"),
                            bool(s, "last"),
                            bool(s, "armed"),
                            number(s, "elapsed"),
                            queue));
        }
        return states;
    }

    public static TickCircuit readSnapshot(String text) {
        JsonObject o = parse(text, SNAPSHOT_LIMIT).getAsJsonObject();
        Blueprint b = blueprint(o.getAsJsonObject("blueprint"));
        int n = b.runes().size(), e = b.routes().size();
        int v = integer(o, "version");
        if (v != 14)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.adapter.codec._json_codecs.024"));
        requireSourcedTree(o);
        requireOrderedTree(o);
        return TickCircuit.restore(
                new CircuitSnapshot(
                        v,
                        b,
                        number(o, "clock"),
                        number(o, "sequence"),
                        bool(o, "powered"),
                        integer(o, "remaining"),
                        integer(o, "injectionCountdown"),
                        number(o, "supplied"),
                        number(o, "loss"),
                        number(o, "delivered"),
                        mixture(o.getAsJsonObject("reserve")),
                        packets(o, "local", n),
                        packets(o, "inbox", e),
                        packets(o, "transit", e),
                        packets(o, "pending", e),
                        booleans(o, "signals", e),
                        booleans(o, "gates", n),
                        packets(o, "ports", n),
                        booleans(o, "drives", n),
                        booleans(o, "emitted", n),
                        booleans(o, "routeValidity", e),
                        booleans(o, "driveValidity", n),
                        booleans(o, "emittedValidity", n),
                        logicStates(o),
                        number(o, "arrivalSequence"),
                        castingLedger(o.getAsJsonObject("castingLedger")),
                        timing(o, n),
                        enumeration(SupplyMode.class, o, "supplyMode"),
                        number(o, "charge")));
    }

    private static CastingLedger castingLedger(JsonObject o) {
        var pending = new ArrayList<RefundTicket>();
        for (var value : array(o, "pending", 1024)) {
            var t = value.getAsJsonObject();
            pending.add(
                    new RefundTicket(number(t, "sequence"), mixture(t.getAsJsonObject("mana"))));
        }
        return new CastingLedger(number(o, "refunded"), number(o, "failures"), pending);
    }

    public static String pool(ManaPool pool) {
        return GSON.toJson(pool.snapshot());
    }

    public static String packetText(Packet packet) {
        var o = GSON.toJsonTree(packet).getAsJsonObject();
        o.addProperty("packetVersion", 3);
        return GSON.toJson(o);
    }

    public static Packet readPacket(String text) {
        var o = parse(text, 2097152).getAsJsonObject();
        if (!o.has("packetVersion") || integer(o, "packetVersion") != 3)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.adapter.codec._json_codecs.025"));
        requireOrder(o.getAsJsonObject("mana"));
        if (integer(o.getAsJsonObject("mana"), "orderVersion") != 2)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.adapter.codec._json_codecs.026"));
        number(o.getAsJsonObject("spell"), "damageMultiplier");
        return packet(o);
    }

    public static Packet readOrderedPacket(String text) {
        var o = parse(text, 2097152).getAsJsonObject();
        if (!o.has("packetVersion") || integer(o, "packetVersion") != 3)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.adapter.codec._json_codecs.027"));
        return readPacket(text);
    }

    public static ManaPool readPool(String text, long regen, int delay) {
        JsonObject o = parse(text, 2048).getAsJsonObject();
        return ManaPool.restore(
                new ManaPool.State(
                        integer(o, "version"),
                        number(o, "capacity"),
                        number(o, "amount"),
                        integer(o, "waitTicks"),
                        integer(o, "remainder"),
                        number(o, "spent"),
                        integer(o, "version") == 1 ? 0 : number(o, "equipmentBonus")),
                regen,
                delay);
    }

    private static AssemblyRules assemblyRules(JsonObject o) {
        return new AssemblyRules(
                integer(o, "version"),
                integer(o, "maxSlots"),
                integer(o, "maxLinks"),
                number(o, "retention"),
                integer(o, "externalSlots"),
                integer(o, "range"),
                number(o, "blockCapacity"),
                number(o, "chargeStep"));
    }

    public static AssemblyRules readAssemblyRules(String text) {
        return assemblyRules(parse(text, 4096).getAsJsonObject());
    }

    private static CircuitAddress address(JsonObject o) {
        return new CircuitAddress(
                UUID.fromString(string(o, "carrier")), integer(o, "slot"), integer(o, "rune"));
    }

    private static CircuitLinks.Snapshot links(JsonObject o) {
        List<CircuitLinks.Connection> links = new ArrayList<>();
        for (JsonElement e : array(o, "connections", 32)) {
            JsonObject c = e.getAsJsonObject();
            links.add(
                    new CircuitLinks.Connection(
                            integer(c, "id"),
                            address(c.getAsJsonObject("from")),
                            address(c.getAsJsonObject("to")),
                            enumeration(Route.Payload.class, c, "payload"),
                            c.has("transit") ? packet(c.get("transit")) : null,
                            bool(c, "signal"),
                            bool(c, "valid")));
        }
        return new CircuitLinks.Snapshot(
                integer(o, "version"),
                integer(o, "nextId"),
                assemblyRules(o.getAsJsonObject("rules")),
                number(o, "accepted"),
                number(o, "delivered"),
                number(o, "loss"),
                links);
    }

    public static String links(CircuitLinks links) {
        return GSON.toJson(links.snapshot());
    }

    public static CircuitLinks readLinks(String text) {
        return CircuitLinks.restore(links(parse(text, 262144).getAsJsonObject()));
    }

    public static String assembly(CircuitAssembly assembly) {
        return GSON.toJson(assembly.snapshot());
    }

    public static CircuitAssembly readAssembly(String text) {
        JsonObject o = parse(text, 67108864).getAsJsonObject();
        List<CircuitAssembly.Slot> slots = new ArrayList<>();
        for (JsonElement e : array(o, "slots", 4)) {
            JsonObject s = e.getAsJsonObject();
            var b = integer(o, "version") >= 4 ? s.getAsJsonObject("board") : null;
            var board =
                    b != null
                            ? new dev.elementalcircuits.domain.circuit.BoardCondition(
                                    integer(b, "version"),
                                    number(b, "maximum"),
                                    number(b, "spent"),
                                    bool(b, "melted"),
                                    number(b, "supplyCursor"))
                            : null;
            slots.add(
                    new CircuitAssembly.Slot(
                            integer(s, "id"),
                            readSnapshot(GSON.toJson(s.get("circuit"))).snapshot(),
                            board));
        }
        return CircuitAssembly.restore(
                new CircuitAssembly.Snapshot(
                        integer(o, "version"),
                        UUID.fromString(string(o, "identity")),
                        assemblyRules(o.getAsJsonObject("rules")),
                        integer(o, "selected"),
                        integer(o, "nextSlot"),
                        slots,
                        links(o.getAsJsonObject("links")),
                        triggerBindings(o),
                        overlayStates(o)));
    }

    private static List<CircuitAssembly.Overlay> overlayStates(JsonObject o) {
        if (integer(o, "version") < 3) return List.of();
        List<CircuitAssembly.Overlay> result = new ArrayList<>();
        for (var e : array(o, "overlays", 4)) {
            var entry = e.getAsJsonObject();
            var s = entry.getAsJsonObject("state");
            List<PlateLayers.Layer> layers = new ArrayList<>();
            List<TriggerBinding> bindings = new ArrayList<>();
            for (var l : array(s, "layers", 16)) {
                var n = l.getAsJsonObject();
                layers.add(
                        new PlateLayers.Layer(
                                integer(n, "id"), blueprint(n.getAsJsonObject("blueprint"))));
            }
            for (var t : array(s, "bindings", 17))
                bindings.add(triggerBinding(t.getAsJsonObject()));
            result.add(
                    new CircuitAssembly.Overlay(
                            integer(entry, "slot"),
                            new PlateLayers.State(
                                    integer(s, "version"),
                                    integer(s, "nextId"),
                                    layers,
                                    bindings)));
        }
        return result;
    }

    private static List<TriggerBinding> triggerBindings(JsonObject o) {
        if (!o.has("triggers")) return List.of();
        List<TriggerBinding> result = new ArrayList<>();
        for (var e : array(o, "triggers", 4)) result.add(triggerBinding(e.getAsJsonObject()));
        return result;
    }

    private static TriggerBinding triggerBinding(JsonObject o) {
        return new TriggerBinding(
                integer(o, "source"),
                integer(o, "target"),
                enumeration(TriggerBinding.When.class, o, "when"),
                integer(o, "delay"),
                integer(o, "percent"));
    }

    public static String triggerBinding(TriggerBinding b) {
        return GSON.toJson(b);
    }

    public static TriggerBinding readTriggerBinding(String text) {
        return triggerBinding(parse(text, 1024).getAsJsonObject());
    }

    public static String program(InvocationProgram p) {
        return GSON.toJson(p);
    }

    public static InvocationProgram readProgram(String text) {
        var o = parse(text, 16777216).getAsJsonObject();
        List<InvocationProgram.Definition> definitions = new ArrayList<>();
        for (var e : array(o, "definitions", 17)) {
            var d = e.getAsJsonObject();
            definitions.add(
                    new InvocationProgram.Definition(
                            integer(d, "slot"), blueprint(d.getAsJsonObject("blueprint"))));
        }
        List<TriggerBinding> bindings = new ArrayList<>();
        for (var e : array(o, "bindings", 17)) bindings.add(triggerBinding(e.getAsJsonObject()));
        var r = o.getAsJsonObject("rules");
        var rules =
                new RecursionRules(
                        integer(r, "version"),
                        integer(r, "maxDepth"),
                        integer(r, "activations"),
                        integer(r, "jobTicks"),
                        number(r, "retention"));
        return new InvocationProgram(integer(o, "version"), definitions, bindings, rules);
    }

    public static String triggered(TriggeredCircuit c) {
        var o = GSON.toJsonTree(c.state()).getAsJsonObject();
        o.getAsJsonObject("circuit").remove("blueprint");
        return GSON.toJson(o);
    }

    public static TriggeredCircuit readTriggered(
            String text, Blueprint definition, RecursionRules rules) {
        var o = parse(text, 16777216).getAsJsonObject();
        if (o.getAsJsonObject("circuit").has("blueprint"))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.adapter.codec._json_codecs.028"));
        o.getAsJsonObject("circuit")
                .add(
                        "blueprint",
                        GSON.toJsonTree(
                                TriggeredCircuit.plan(
                                        definition, number(o, "budget"), integer(o, "limit"))));
        return TriggeredCircuit.restore(
                new TriggeredCircuit.State(
                        integer(o, "version"),
                        integer(o, "age"),
                        integer(o, "limit"),
                        number(o, "budget"),
                        readPool(GSON.toJson(o.get("supply")), 0, 0).snapshot(),
                        readSnapshot(GSON.toJson(o.get("circuit"))).snapshot(),
                        mixture(o.getAsJsonObject("funding"))),
                definition,
                rules);
    }

    public static String selection(CircuitSelection view) {
        return GSON.toJson(view);
    }

    public static CircuitSelection readSelection(String text) {
        var o = parse(text, 4096).getAsJsonObject();
        List<CircuitSelection.Slot> slots = new ArrayList<>();
        for (var e : array(o, "slots", 8)) {
            var q = e.getAsJsonObject();
            slots.add(
                    new CircuitSelection.Slot(
                            UUID.fromString(string(q, "carrier")),
                            bool(q, "book"),
                            integer(q, "id"),
                            enumeration(Rules.Material.class, q, "material"),
                            bool(q, "powered"),
                            integer(q, "runes"),
                            enumeration(Element.class, q, "element")));
        }
        return new CircuitSelection(
                UUID.fromString(string(o, "identity")),
                UUID.fromString(string(o, "selectedCarrier")),
                bool(o, "book"),
                integer(o, "selected"),
                slots);
    }

    public record LinkRequest(CircuitAddress from, CircuitAddress to, Route.Payload payload) {}

    public static String linkRequest(LinkRequest request) {
        return GSON.toJson(request);
    }

    public static LinkRequest readLinkRequest(String text) {
        JsonObject o = parse(text, 2048).getAsJsonObject();
        return new LinkRequest(
                address(o.getAsJsonObject("from")),
                address(o.getAsJsonObject("to")),
                enumeration(Route.Payload.class, o, "payload"));
    }

    public static String logicObservation(TickCircuit circuit) {
        return GSON.toJson(LogicObservation.inspect(circuit));
    }

    public static LogicObservation readLogicObservation(String text) {
        var o = parse(text, 1048576).getAsJsonObject();
        List<LogicObservation.Node> nodes = new ArrayList<>();
        for (var e : array(o, "nodes", CircuitBounds.RUNES)) {
            var n = e.getAsJsonObject();
            nodes.add(
                    new LogicObservation.Node(
                            integer(n, "id"),
                            enumeration(Rune.Kind.class, n, "kind"),
                            integer(n, "count"),
                            integer(n, "phase"),
                            bool(n, "latched"),
                            integer(n, "parameter")));
        }
        return new LogicObservation(integer(o, "version"), nodes);
    }

    public static final int VOLUME_LIMIT = 33554432;

    public static String volume(BlueprintVolume volume) {
        for (var p : volume.pages())
            if (blueprint(p.blueprint()).getBytes(StandardCharsets.UTF_8).length > BLUEPRINT_LIMIT)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.adapter.codec._json_codecs.029"));
        String s = GSON.toJson(volume);
        if (s.getBytes(StandardCharsets.UTF_8).length > VOLUME_LIMIT)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.adapter.codec._json_codecs.030"));
        return s;
    }

    public static BlueprintVolume readVolume(String text) {
        var o = parse(text, VOLUME_LIMIT).getAsJsonObject();
        List<BlueprintVolume.Page> pages = new ArrayList<>();
        for (var e : array(o, "pages", 16)) {
            var p = e.getAsJsonObject();
            pages.add(
                    new BlueprintVolume.Page(
                            integer(p, "id"),
                            string(p, "title"),
                            blueprint(p.getAsJsonObject("blueprint"))));
        }
        var v =
                new BlueprintVolume(
                        integer(o, "version"),
                        UUID.fromString(string(o, "identity")),
                        integer(o, "nextId"),
                        integer(o, "selected"),
                        pages);
        volume(v);
        return v;
    }

    public static String notebook(NotebookInspection view) {
        return GSON.toJson(view);
    }

    public static NotebookInspection readNotebook(String text) {
        var o = parse(text, 131072).getAsJsonObject();
        List<NotebookInspection.Page> pages = new ArrayList<>();
        for (var e : array(o, "pages", 16)) {
            var p = e.getAsJsonObject();
            pages.add(new NotebookInspection.Page(integer(p, "id"), string(p, "title")));
        }
        return new NotebookInspection(
                UUID.fromString(string(o, "identity")),
                integer(o, "selected"),
                pages,
                !o.has("blueprint") || o.get("blueprint").isJsonNull()
                        ? null
                        : blueprint(o.getAsJsonObject("blueprint")),
                string(o, "message"));
    }

    public static String spellbook(SpellbookInspection view) {
        return GSON.toJson(view);
    }

    public static SpellbookInspection readSpellbook(String text) {
        var o = parse(text, 16777216).getAsJsonObject();
        List<SpellbookInspection.Slot> slots = new ArrayList<>();
        for (var e : array(o, "slots", 4)) {
            var p = e.getAsJsonObject();
            slots.add(
                    new SpellbookInspection.Slot(
                            integer(p, "id"),
                            blueprint(p.getAsJsonObject("blueprint")),
                            bool(p, "powered"),
                            number(p, "held")));
        }
        return new SpellbookInspection(
                UUID.fromString(string(o, "identity")),
                integer(o, "selected"),
                slots,
                string(o, "message"));
    }

    public static String workshop(WorkshopInspection view) {
        return GSON.toJson(view);
    }

    public static WorkshopInspection readWorkshop(String text) {
        var o = parse(text, 8192).getAsJsonObject();
        var p = o.getAsJsonObject("assembly");
        var stock = new HashMap<String, Integer>();
        for (var e : o.getAsJsonObject("stock").entrySet())
            stock.put(e.getKey(), integer(o.getAsJsonObject("stock"), e.getKey()));
        return new WorkshopInspection(
                UUID.fromString(string(o, "identity")),
                number(o, "position"),
                new WandParts.Assembly(string(p, "head"), string(p, "body"), string(p, "tail")),
                integer(o, "wear"),
                stock,
                integer(o, "plates"),
                string(o, "message"),
                bool(o, "valid"));
    }

    public static String inspection(AssemblyInspection view) {
        return GSON.toJson(view);
    }

    public static AssemblyInspection readInspection(String text) {
        parse(text, 131072);
        return GSON.fromJson(text, AssemblyInspection.class);
    }

    public static String survey(SurveyReport report) {
        String text = GSON.toJson(report);
        if (text.getBytes(StandardCharsets.UTF_8).length > 16384)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.adapter.codec._json_codecs.031"));
        return text;
    }

    public static SurveyReport readSurvey(String text) {
        var o = parse(text, 16384).getAsJsonObject();
        var rows = new ArrayList<SurveyReport.Row>();
        for (var e : array(o, "rows", 32)) {
            var row = e.getAsJsonObject();
            rows.add(new SurveyReport.Row(string(row, "name"), string(row, "value")));
        }
        return new SurveyReport(
                integer(o, "version"),
                string(o, "title"),
                string(o, "location"),
                rows,
                string(o, "note"),
                integer(o, "checked"),
                integer(o, "total"),
                bool(o, "done"));
    }
}
