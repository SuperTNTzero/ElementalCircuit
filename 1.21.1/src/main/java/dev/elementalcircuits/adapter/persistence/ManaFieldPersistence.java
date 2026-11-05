package dev.elementalcircuits.adapter.persistence;

import com.google.gson.*;

import dev.elementalcircuits.adapter.resources.ManaFieldResources;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.mana.ManaField;
import dev.elementalcircuits.domain.mana.ManaFieldRules;
import dev.elementalcircuits.domain.mana.ManaNode;

import net.minecraft.nbt.*;

import java.util.*;

/** Strict field/node persistence. Callers publish only a completely validated replacement. */
public final class ManaFieldPersistence {
    private static final Gson JSON = new Gson();

    private ManaFieldPersistence() {}

    private static int integer(CompoundTag t, String key) {
        if (!t.contains(key, Tag.TAG_INT))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                                    "text.elementalcircuits.adapter.persistence._mana_field_persistence.001")
                            + key);
        return t.getInt(key);
    }

    private static long number(CompoundTag t, String key) {
        if (!t.contains(key, Tag.TAG_LONG))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                                    "text.elementalcircuits.adapter.persistence._mana_field_persistence.002")
                            + key);
        return t.getLong(key);
    }

    public static void rules(CompoundTag t, ManaFieldRules rules) {
        t.putString("Rules", JSON.toJson(rules));
    }

    public static ManaFieldRules rules(CompoundTag t) {
        if (!t.contains("Rules", Tag.TAG_STRING) || t.getString("Rules").length() > 2048)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.adapter.persistence._mana_field_persistence.003"));
        return ManaFieldResources.parse(
                JsonParser.parseString(t.getString("Rules")).getAsJsonObject());
    }

    public static CompoundTag write(ManaField field) {
        var s = field.snapshot();
        CompoundTag t = new CompoundTag();
        t.putInt("Version", s.version());
        rules(t, s.rules());
        t.putInt("Phase", s.phase());
        t.putLong("Ambient", s.ambientCreated());
        t.putLong("Injected", s.injected());
        t.putLong("Extracted", s.extracted());
        t.putLong("Dissipated", s.dissipated());
        ListTag list = new ListTag();
        for (var sample : s.cells()) {
            CompoundTag c = new CompoundTag();
            c.putInt("X", sample.cell().x());
            c.putInt("Y", sample.cell().y());
            c.putInt("Z", sample.cell().z());
            c.putLong("Amount", sample.amount());
            list.add(c);
        }
        t.put("Cells", list);
        return t;
    }

    public static ManaField read(CompoundTag t) {
        if (!t.contains("Cells", Tag.TAG_LIST)
                || t.getList("Cells", Tag.TAG_COMPOUND).size() != ((ListTag) t.get("Cells")).size())
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.adapter.persistence._mana_field_persistence.004"));
        ListTag list = t.getList("Cells", Tag.TAG_COMPOUND);
        if (list.size() > ManaField.MAX_CELLS)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.adapter.persistence._mana_field_persistence.005"));
        List<ManaField.Sample> cells = new ArrayList<>();
        for (Tag tag : list) {
            CompoundTag c = (CompoundTag) tag;
            cells.add(
                    new ManaField.Sample(
                            new ManaField.Cell(integer(c, "X"), integer(c, "Y"), integer(c, "Z")),
                            number(c, "Amount")));
        }
        return ManaField.restore(
                new ManaField.Snapshot(
                        integer(t, "Version"),
                        rules(t),
                        integer(t, "Phase"),
                        number(t, "Ambient"),
                        number(t, "Injected"),
                        number(t, "Extracted"),
                        number(t, "Dissipated"),
                        cells));
    }

    public static CompoundTag node(ManaNode node, ManaFieldRules rules) {
        var s = node.snapshot();
        CompoundTag t = new CompoundTag();
        rules(t, rules);
        t.putInt("Version", s.version());
        t.putString("Kind", s.kind().name());
        t.putLong("Initial", s.initial());
        t.putLong("Received", s.received());
        t.putLong("Emitted", s.emitted());
        t.putLong("Remaining", s.remaining());
        t.putLong("Regenerated", s.regenerated());
        t.putInt("RegenerationRemainder", s.regenerationRemainder());
        return t;
    }

    public static ManaNode node(CompoundTag t) {
        if (!t.contains("Kind", Tag.TAG_STRING))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.adapter.persistence._mana_field_persistence.006"));
        return ManaNode.restore(
                rules(t),
                new ManaNode.State(
                        integer(t, "Version"),
                        ManaNode.Kind.valueOf(t.getString("Kind")),
                        number(t, "Initial"),
                        number(t, "Received"),
                        number(t, "Emitted"),
                        number(t, "Remaining"),
                        integer(t, "Version") >= 2 ? number(t, "Regenerated") : 0,
                        integer(t, "Version") >= 2 ? integer(t, "RegenerationRemainder") : 0));
    }
}
