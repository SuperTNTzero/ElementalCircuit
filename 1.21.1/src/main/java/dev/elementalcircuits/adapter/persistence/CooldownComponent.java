package dev.elementalcircuits.adapter.persistence;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.elementalcircuits.domain.casting.CastCooldown;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.registries.*;

/** A small independent component avoids rewriting the entire circuit NBT on each cooldown tick. */
public final class CooldownComponent {
    public static final DeferredRegister<DataComponentType<?>> TYPES =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, "elementalcircuits");

    private record Fields(int version, int remaining, int duration) {}

    private static final Codec<CastCooldown> CODEC =
            RecordCodecBuilder.<Fields>create(
                            i ->
                                    i.group(
                                                    Codec.intRange(1, 1)
                                                            .fieldOf("version")
                                                            .forGetter(Fields::version),
                                                    Codec.intRange(0, 1200)
                                                            .fieldOf("remaining")
                                                            .forGetter(Fields::remaining),
                                                    Codec.intRange(0, 1200)
                                                            .fieldOf("duration")
                                                            .forGetter(Fields::duration))
                                            .apply(i, Fields::new))
                    .comapFlatMap(
                            f -> {
                                try {
                                    return DataResult.success(
                                            new CastCooldown(f.remaining(), f.duration()));
                                } catch (IllegalArgumentException invalid) {
                                    return DataResult.error(invalid::getMessage);
                                }
                            },
                            c -> new Fields(1, c.remaining(), c.duration()));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CastCooldown>> TYPE =
            TYPES.register(
                    "wand_cooldown",
                    () ->
                            DataComponentType.<CastCooldown>builder()
                                    .persistent(CODEC)
                                    .networkSynchronized(
                                            StreamCodec.of(
                                                    (b, c) -> {
                                                        b.writeVarInt(c.remaining());
                                                        b.writeVarInt(c.duration());
                                                    },
                                                    b ->
                                                            new CastCooldown(
                                                                    b.readVarInt(),
                                                                    b.readVarInt())))
                                    .build());

    private CooldownComponent() {}
}
