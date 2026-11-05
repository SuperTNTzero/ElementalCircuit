package dev.elementalcircuits.block;

import com.google.gson.*;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.*;

import java.io.*;
import java.nio.charset.StandardCharsets;

/** Immutable model geometry exported by Blender; shared by collision and the preview adapter. */
public final class WorkstationGeometry {
    private static final JsonObject ROOT = load();
    private static final DeskLayout LAYOUT = layout();
    private static final VoxelShape[][] DESK = desks();
    private static final VoxelShape[] ALTAR = altars();

    private WorkstationGeometry() {}

    private static JsonObject load() {
        try (var stream =
                WorkstationGeometry.class.getResourceAsStream(
                        "/assets/elementalcircuits/geometry/workstations.json")) {
            if (stream == null)
                throw new IllegalStateException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.block._workstation_geometry.001"));
            var json =
                    JsonParser.parseString(
                                    new String(stream.readAllBytes(), StandardCharsets.UTF_8))
                            .getAsJsonObject();
            if (json.get("version").getAsInt() != 1)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.block._workstation_geometry.002"));
            return json;
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.block._workstation_geometry.003"),
                    e);
        }
    }

    public record DeskLayout(
            double tiltDegrees,
            double surfaceY,
            double previewX,
            double previewZ,
            double preferredScale,
            double clearance,
            double ceiling) {}

    private static DeskLayout layout() {
        var j = ROOT.getAsJsonObject("desk");
        return new DeskLayout(
                j.get("tiltDegrees").getAsDouble(),
                j.get("surfaceY").getAsDouble(),
                j.get("previewX").getAsDouble(),
                j.get("previewZ").getAsDouble(),
                j.get("preferredScale").getAsDouble(),
                j.get("clearance").getAsDouble(),
                j.get("ceiling").getAsDouble());
    }

    public static DeskLayout desk() {
        return LAYOUT;
    }

    private static int turns(Direction facing) {
        return switch (facing) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
    }

    public static VoxelShape desk(Direction facing, boolean second) {
        return DESK[turns(facing)][second ? 1 : 0];
    }

    public static VoxelShape inscription(Direction facing) {
        return ALTAR[turns(facing)];
    }

    private static VoxelShape[][] desks() {
        var result = new VoxelShape[4][2];
        for (int rotation = 0; rotation < 4; rotation++)
            for (int part = 0; part < 2; part++)
                result[rotation][part] =
                        shape(
                                ROOT.getAsJsonObject("desk").getAsJsonArray("collision"),
                                rotation,
                                part);
        return result;
    }

    private static VoxelShape[] altars() {
        var result = new VoxelShape[4];
        for (int rotation = 0; rotation < 4; rotation++)
            result[rotation] =
                    shape(
                            ROOT.getAsJsonObject("inscription").getAsJsonArray("collision"),
                            rotation,
                            0);
        return result;
    }

    private static VoxelShape shape(JsonArray boxes, int rotation, int part) {
        var result = Shapes.empty();
        for (var entry : boxes) {
            var a = entry.getAsJsonArray();
            double x0 = Math.max(part, a.get(0).getAsDouble()) - part,
                    y0 = a.get(1).getAsDouble(),
                    z0 = a.get(2).getAsDouble();
            double x1 = Math.min(part + 1, a.get(3).getAsDouble()) - part,
                    y1 = a.get(4).getAsDouble(),
                    z1 = a.get(5).getAsDouble();
            if (x1 <= x0 || y1 <= y0 || z1 <= z0) continue;
            for (int i = 0; i < rotation; i++) {
                double next0 = 1 - z1, next1 = 1 - z0;
                z0 = x0;
                z1 = x1;
                x0 = next0;
                x1 = next1;
            }
            result =
                    Shapes.joinUnoptimized(
                            result, Shapes.box(x0, y0, z0, x1, y1, z1), BooleanOp.OR);
        }
        return result.optimize();
    }
}
