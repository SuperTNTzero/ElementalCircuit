package dev.elementalcircuits.testing;

import dev.elementalcircuits.adapter.codec.BlueprintTextCode;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.book.BlueprintVolume;
import dev.elementalcircuits.domain.circuit.Blueprint;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Explicit test fixtures: the same files can be pasted into the normal blueprint import UI. */
public final class TestBlueprints {
    public record Entry(String file, String title, Blueprint blueprint) {}

    private static String read(String file) {
        try (var input = TestBlueprints.class.getResourceAsStream("/blueprints/" + file)) {
            if (input == null) throw new IllegalStateException("Missing blueprint file: " + file);
            byte[] bytes = input.readNBytes(BlueprintTextCode.LIMIT + 1);
            if (bytes.length > BlueprintTextCode.LIMIT)
                throw new IllegalStateException("Blueprint fixture too large");
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static List<Entry> entries() {
        var entries = new ArrayList<Entry>();
        var files = new HashSet<String>();
        for (String row :
                read("index.tsv")
                        .lines()
                        .filter(l -> !l.isBlank() && !l.startsWith("#"))
                        .toList()) {
            String[] fields = row.split("\t", -1);
            if (fields.length != 2
                    || !fields[0].matches("[a-z0-9-]+\\.ecblueprint")
                    || !files.add(fields[0]))
                throw new IllegalStateException("Invalid blueprint index row: " + row);
            entries.add(
                    new Entry(
                            fields[0],
                            BlueprintVolume.validTitle(fields[1]),
                            BlueprintTextCode.decode(read(fields[0]))));
        }
        if (entries.isEmpty() || entries.size() > BlueprintVolume.CAPACITY)
            throw new IllegalStateException("Test blueprint collection exceeds notebook capacity");
        return List.copyOf(entries);
    }

    public static BlueprintVolume fill(BlueprintVolume empty) {
        if (!empty.pages().isEmpty())
            throw new IllegalArgumentException("Only inject into a new test notebook");
        var volume = empty;
        for (var entry : entries()) volume = volume.add(entry.title(), entry.blueprint());
        return volume.select(volume.pages().getFirst().id());
    }
}
