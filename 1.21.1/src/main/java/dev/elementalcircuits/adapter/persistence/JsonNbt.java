package dev.elementalcircuits.adapter.persistence;

import net.minecraft.nbt.*;

import java.nio.*;
import java.nio.charset.*;

/** Versioned UTF-8 bytes bypass NBT's 65535-byte modified-UTF string limit. */
public final class JsonNbt {
    private JsonNbt() {}

    public static CompoundTag encode(String text, int limit) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > limit)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.adapter.persistence._json_nbt.001"));
        CompoundTag result = new CompoundTag();
        result.putInt("Format", 1);
        result.putByteArray("Utf8", bytes);
        return result;
    }

    /** Small ASCII-era payloads stay readable; long queues bypass modified UTF limits. */
    public static Tag compact(String text, int limit) {
        var encoded = encode(text, limit);
        return encoded.getByteArray("Utf8").length <= 16000 ? StringTag.valueOf(text) : encoded;
    }

    /** Old string is accepted only by the caller's normal strict domain decoder. */
    public static String decode(Tag tag, int limit) {
        if (tag instanceof StringTag old) {
            String text = old.getAsString();
            if (text.getBytes(StandardCharsets.UTF_8).length > limit)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.adapter.persistence._json_nbt.002"));
            return text;
        }
        if (!(tag instanceof CompoundTag data)
                || !data.contains("Format", Tag.TAG_INT)
                || data.getInt("Format") != 1
                || !data.contains("Utf8", Tag.TAG_BYTE_ARRAY))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.adapter.persistence._json_nbt.003"));
        byte[] bytes = data.getByteArray("Utf8");
        if (bytes.length > limit)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.adapter.persistence._json_nbt.004"));
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.adapter.persistence._json_nbt.005"),
                    e);
        }
    }
}
