package dev.elementalcircuits.domain.casting;

import java.util.UUID;

/**
 * Direction indices follow down/up/north/south/west/east. Binding is granted only by a fixed host.
 */
public record MaterialPorts(int input, int output, UUID anchor, String filter) {
    public static final MaterialPorts NONE = new MaterialPorts(-1, -1, null, "");

    public MaterialPorts(int input, int output, UUID anchor) {
        this(input, output, anchor, "");
    }

    public MaterialPorts {
        if (input < -1 || input > 5 || output < -1 || output > 5)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.casting._material_ports.001"));
        if (input == -1 && output == -1 && anchor != null)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.casting._material_ports.002"));
        if (filter == null)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.casting._material_ports.003"));
        if (!filter.isEmpty()) {
            String key = filter.startsWith("#") ? filter.substring(1) : filter;
            if (!key.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.domain.casting._material_ports.004"));
        }
    }

    public MaterialPorts bind(UUID id) {
        return input < 0 && output < 0 ? this : new MaterialPorts(input, output, id, filter);
    }

    public MaterialPorts merge(MaterialPorts other) {
        if (anchor != null && other.anchor != null && !anchor.equals(other.anchor))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.domain.casting._material_ports.005"));
        return new MaterialPorts(
                mergeSide(input, other.input),
                mergeSide(output, other.output),
                anchor != null ? anchor : other.anchor,
                mergeFilter(filter, other.filter));
    }

    private static String mergeFilter(String a, String b) {
        if (a.isEmpty()) return b;
        if (b.isEmpty() || a.equals(b)) return a;
        throw new IllegalArgumentException(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.casting._material_ports.006"));
    }

    private static int mergeSide(int a, int b) {
        if (a < 0) return b;
        if (b < 0 || a == b) return a;
        throw new IllegalArgumentException(
                dev.elementalcircuits.text.Texts.get(
                        "text.elementalcircuits.domain.casting._material_ports.007"));
    }
}
