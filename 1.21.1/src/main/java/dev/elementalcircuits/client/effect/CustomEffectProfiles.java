package dev.elementalcircuits.client.effect;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import javax.sound.sampled.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Client-local imported animation packs. Package data never affects spell authority. */
public final class CustomEffectProfiles {
    public record Animation(
            int[] frames,
            float fps,
            boolean loop,
            float opacity,
            float scale,
            float pulse,
            byte[] sound) {
        public Animation {
            frames = frames.clone();
            sound = sound == null ? null : sound.clone();
        }

        @Override
        public int[] frames() {
            return frames.clone();
        }

        @Override
        public byte[] sound() {
            return sound == null ? null : sound.clone();
        }

        public int frame(long age) {
            int step = (int) Math.floor(age * fps / 20.0);
            if (loop) step = Math.floorMod(step, frames.length);
            else step = Math.min(step, frames.length - 1);
            return frames[step];
        }
    }

    public record Profile(
            String id,
            ResourceLocation texture,
            int textureWidth,
            int textureHeight,
            int frameWidth,
            int frameHeight,
            int columns,
            Animation cast,
            Animation hit) {}

    private record PackageData(JsonObject manifest, byte[] sheet, byte[] castSound, byte[] hitSound) {}

    private static volatile Map<String, Profile> profiles = Map.of();
    private static final Path DIRECTORY = Path.of("config", "elementalcircuits", "effects");
    private static final int PACKAGE_LIMIT = 16 * 1024 * 1024;
    private static final int FILE_LIMIT = 8 * 1024 * 1024;
    private static final int PROFILE_LIMIT = 32;

    private CustomEffectProfiles() {}

    public static void reload() {
        Minecraft minecraft = Minecraft.getInstance();
        Path root = minecraft.gameDirectory.toPath().resolve(DIRECTORY).normalize();
        var previous = profiles;
        profiles = Map.of();
        previous.values().forEach(CustomEffectProfiles::close);
        Map<String, Profile> loaded = new TreeMap<>();
        try {
            Files.createDirectories(root);
            try (var files = Files.list(root)) {
                for (Path file : files.filter(p -> p.getFileName().toString().endsWith(".ecfx"))
                        .sorted().limit(PROFILE_LIMIT).toList()) {
                    if (Files.size(file) > PACKAGE_LIMIT) continue;
                    try {
                        Profile profile = load(file);
                        if (loaded.putIfAbsent(profile.id(), profile) != null) close(profile);
                    } catch (RuntimeException | IOException ignored) {
                        // Invalid packages are skipped atomically.
                    }
                }
            }
        } catch (IOException ignored) {
            loaded.clear();
        }
        profiles = Map.copyOf(loaded);
    }

    private static Profile load(Path file) throws IOException {
        String fileId = file.getFileName().toString();
        fileId = fileId.substring(0, fileId.length() - ".ecfx".length());
        if (!fileId.matches("[a-z0-9_.-]{1,48}")) throw new IllegalArgumentException();
        PackageData data = readPackage(file);
        JsonObject manifest = data.manifest();
        if (integer(manifest, "version", 1, 1) != 1
                || !string(manifest, "id").equals(fileId)) throw new IllegalArgumentException();
        int frameWidth = integer(manifest, "frameWidth", 8, 512);
        int frameHeight = integer(manifest, "frameHeight", 8, 512);
        int width, height;
        try (NativeImage image = NativeImage.read(data.sheet())) {
            int columns = image.getWidth() / frameWidth;
            int rows = image.getHeight() / frameHeight;
            if (image.getWidth() > 1024 || image.getHeight() > 1024
                    || image.getWidth() % frameWidth != 0 || image.getHeight() % frameHeight != 0
                    || columns < 1 || rows < 1 || columns * rows > 64)
                throw new IllegalArgumentException();
            width = image.getWidth();
            height = image.getHeight();
            Animation cast = animation(manifest.getAsJsonObject("cast"), data.castSound(), columns * rows);
            Animation hit = animation(manifest.getAsJsonObject("hit"), data.hitSound(), columns * rows);
            var texture = new DynamicTexture(NativeImage.read(data.sheet()));
            ResourceLocation location = Minecraft.getInstance().getTextureManager()
                    .register("elementalcircuits/custom_effect/" + fileId, texture);
            return new Profile(fileId, location, width, height, frameWidth, frameHeight, columns,
                    cast, hit);
        }
    }

    private static PackageData readPackage(Path file) throws IOException {
        byte[] manifest = null, sheet = null, castSound = null, hitSound = null;
        int count = 0, total = 0;
        try (var zip = new ZipInputStream(Files.newInputStream(file), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++count > 4 || entry.isDirectory()) throw new IllegalArgumentException();
                String name = entry.getName();
                if (!Set.of("manifest.json", "spritesheet.png", "cast.wav", "hit.wav").contains(name))
                    throw new IllegalArgumentException();
                byte[] bytes = readBounded(zip, FILE_LIMIT);
                total = Math.addExact(total, bytes.length);
                if (total > PACKAGE_LIMIT) throw new IllegalArgumentException();
                switch (name) {
                    case "manifest.json" -> manifest = bytes;
                    case "spritesheet.png" -> sheet = bytes;
                    case "cast.wav" -> castSound = bytes;
                    case "hit.wav" -> hitSound = bytes;
                }
            }
        }
        if (manifest == null || sheet == null) throw new IllegalArgumentException();
        JsonObject root = JsonParser.parseString(new String(manifest, StandardCharsets.UTF_8)).getAsJsonObject();
        if (root.has("cast") && root.getAsJsonObject("cast").has("sound")
                && !"cast.wav".equals(string(root.getAsJsonObject("cast"), "sound")))
            throw new IllegalArgumentException();
        if (root.has("hit") && root.getAsJsonObject("hit").has("sound")
                && !"hit.wav".equals(string(root.getAsJsonObject("hit"), "sound")))
            throw new IllegalArgumentException();
        if (root.getAsJsonObject("cast") == null || root.getAsJsonObject("hit") == null
                || root.getAsJsonObject("cast").has("sound") && castSound == null
                || root.getAsJsonObject("hit").has("sound") && hitSound == null)
            throw new IllegalArgumentException();
        validateWav(castSound);
        validateWav(hitSound);
        return new PackageData(root, sheet, castSound, hitSound);
    }

    private static byte[] readBounded(InputStream input, int max) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            if (output.size() + read > max) throw new IllegalArgumentException();
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static void validateWav(byte[] data) throws IOException {
        if (data == null) return;
        try (AudioInputStream audio = AudioSystem.getAudioInputStream(new ByteArrayInputStream(data))) {
            AudioFormat format = audio.getFormat();
            if (format.getChannels() < 1 || format.getChannels() > 2
                    || format.getSampleRate() < 8000 || format.getSampleRate() > 96000
                    || format.getSampleSizeInBits() != 8 && format.getSampleSizeInBits() != 16
                    || AudioSystem.NOT_SPECIFIED != audio.getFrameLength()
                            && audio.getFrameLength() > format.getFrameRate() * 8)
                throw new IllegalArgumentException();
        } catch (UnsupportedAudioFileException error) {
            throw new IllegalArgumentException(error);
        }
    }

    private static Animation animation(JsonObject json, byte[] sound, int frameLimit) {
        JsonArray values = json.getAsJsonArray("frames");
        if (values == null || values.isEmpty() || values.size() > 64) throw new IllegalArgumentException();
        int[] frames = new int[values.size()];
        for (int i = 0; i < frames.length; i++) {
            frames[i] = values.get(i).getAsBigDecimal().intValueExact();
            if (frames[i] < 0 || frames[i] >= frameLimit) throw new IllegalArgumentException();
        }
        return new Animation(frames,
                decimal(json, "fps", 1, 60),
                json.has("loop") && json.get("loop").getAsBoolean(),
                decimal(json, "opacity", 0, 1),
                decimal(json, "scale", .1f, 4),
                decimal(json, "pulse", 0, 1),
                sound);
    }

    private static int integer(JsonObject json, String key, int min, int max) {
        int value = json.get(key).getAsBigDecimal().intValueExact();
        if (value < min || value > max) throw new IllegalArgumentException();
        return value;
    }

    private static float decimal(JsonObject json, String key, float min, float max) {
        float value = json.get(key).getAsFloat();
        if (!Float.isFinite(value) || value < min || value > max) throw new IllegalArgumentException();
        return value;
    }

    private static String string(JsonObject json, String key) {
        return json.get(key).getAsString();
    }

    private static void close(Profile profile) {
        Minecraft.getInstance().getTextureManager().release(profile.texture());
    }

    public static Profile get(String id) {
        return id == null || id.isEmpty() ? null : profiles.get(id);
    }

    public static Animation get(String id, boolean hit) {
        Profile profile = get(id);
        return profile == null ? null : hit ? profile.hit() : profile.cast();
    }

    public static Collection<Profile> all() {
        return profiles.values();
    }
}
