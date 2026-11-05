package dev.elementalcircuits.adapter.persistence;

import com.google.gson.stream.JsonReader;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.elementalcircuits.adapter.resources.WandResources;
import dev.elementalcircuits.domain.equipment.WandParts;

import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/** Small read-only projections. Never copy/decode the full assembly or create an identity. */
public final class ItemAppearance {
    private static final MapCodec<UUID> CARRIER =
            UUIDUtil.CODEC.fieldOf("Identity").codec().fieldOf("ElementalCircuit");
    private static final MapCodec<UUID> SPELL_SOURCE =
            UUIDUtil.CODEC.fieldOf("SelectedSpellSource");
    private static final MapCodec<ByteBuffer> NOTEBOOK =
            Codec.BYTE_BUFFER.fieldOf("Utf8").codec().fieldOf("BlueprintVolume");
    private static final MapCodec<WandParts.Assembly> PARTS =
            RecordCodecBuilder.<WandParts.Assembly>mapCodec(
                            i ->
                                    i.group(
                                                    Codec.STRING
                                                            .fieldOf("Head")
                                                            .forGetter(WandParts.Assembly::head),
                                                    Codec.STRING
                                                            .fieldOf("Body")
                                                            .forGetter(WandParts.Assembly::body),
                                                    Codec.STRING
                                                            .fieldOf("Tail")
                                                            .forGetter(WandParts.Assembly::tail))
                                            .apply(i, WandParts.Assembly::new))
                    .codec()
                    .fieldOf("WandAssembly");

    private ItemAppearance() {}

    public static UUID identity(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .read(CARRIER)
                .result()
                .orElse(null);
    }

    public static UUID spellSource(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .read(SPELL_SOURCE)
                .result()
                .orElse(null);
    }

    public static UUID notebookIdentity(ItemStack stack) {
        var data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        var bytes = data.read(NOTEBOOK).result();
        String prefix;
        if (bytes.isPresent()) {
            var buffer = bytes.get().asReadOnlyBuffer();
            byte[] header = new byte[Math.min(512, buffer.remaining())];
            buffer.get(header);
            prefix = new String(header, StandardCharsets.UTF_8);
        } else return null;
        // Current volume encoders write identity in the header. Never parse/copy the pages.
        try (var reader = new JsonReader(new StringReader(prefix))) {
            reader.beginObject();
            while (reader.hasNext()) {
                if (reader.nextName().equals("identity"))
                    return UUID.fromString(reader.nextString());
                reader.skipValue();
            }
        } catch (IOException | RuntimeException invalid) {
            return null;
        }
        return null;
    }

    public static WandParts.Assembly parts(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .read(PARTS)
                .result()
                .orElseGet(() -> WandResources.definitions().basic());
    }

    public static boolean sameCarrier(ItemStack from, ItemStack to) {
        return !from.isEmpty()
                && !to.isEmpty()
                && from.is(to.getItem())
                && Objects.equals(identity(from), identity(to));
    }

    public static boolean reequipWand(ItemStack from, ItemStack to, boolean slotChanged) {
        return slotChanged || !sameCarrier(from, to) || !parts(from).equals(parts(to));
    }

    public static boolean reequipBook(
            ItemStack from, ItemStack to, boolean slotChanged, boolean notebook) {
        return slotChanged
                || from.isEmpty()
                || to.isEmpty()
                || !from.is(to.getItem())
                || !Objects.equals(
                        notebook ? notebookIdentity(from) : identity(from),
                        notebook ? notebookIdentity(to) : identity(to));
    }
}
