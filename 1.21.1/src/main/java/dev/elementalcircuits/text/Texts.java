package dev.elementalcircuits.text;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

/** Shared text lookup for diagnostics and string-based observations; no Minecraft dependency. */
public final class Texts {
    private static final Map<String, String> DEFAULTS = load();

    private Texts() {}

    private static Map<String, String> load() {
        String path = "/assets/elementalcircuits/lang/zh_cn.json";
        try (var stream = Objects.requireNonNull(Texts.class.getResourceAsStream(path), path);
                var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            Map<String, String> entries =
                    new Gson().fromJson(reader, new TypeToken<Map<String, String>>() {}.getType());
            return Map.copyOf(entries);
        } catch (IOException error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    public static String get(String key) {
        String value = DEFAULTS.get(key);
        if (value == null) throw new IllegalArgumentException(key);
        return value;
    }
}
