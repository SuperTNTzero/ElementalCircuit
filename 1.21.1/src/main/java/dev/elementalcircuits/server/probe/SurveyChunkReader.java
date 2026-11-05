package dev.elementalcircuits.server.probe;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.application.NodeSurvey;
import dev.elementalcircuits.domain.survey.SurveyArea;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Live reads on server thread, pure palette decoding on the native read completion thread. */
public final class SurveyChunkReader {
    private SurveyChunkReader() {}

    public static CompletableFuture<NodeSurvey.Sample> read(
            ServerLevel level, SurveyArea area, SurveyArea.Chunk pos) {
        return read(level, area, pos, new HashMap<>());
    }

    public static CompletableFuture<NodeSurvey.Sample> read(
            ServerLevel level, SurveyArea area, SurveyArea.Chunk pos, Map<Long, Boolean> regions) {
        var live = level.getChunkSource().getChunkNow(pos.x(), pos.z());
        if (live != null) {
            long count = 0;
            for (BlockPos p : live.getBlockEntitiesPos())
                if (area.contains(p.getX(), p.getY(), p.getZ())
                        && (live.getBlockState(p).is(ElementalCircuits.NATURAL_MANA_NODE.get())
                                || live.getBlockState(p)
                                        .is(ElementalCircuits.AERIAL_MANA_NODE.get()))) count++;
            return CompletableFuture.completedFuture(new NodeSurvey.Sample(true, count));
        }
        // Vanilla's read opens absent .mca files with CREATE. Skip absent regions before invoking
        // it.
        long region = ChunkPos.asLong(pos.x() >> 5, pos.z() >> 5);
        try {
            Boolean exists = regions.get(region);
            if (exists == null) {
                var root =
                        level.getServer()
                                .getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
                var folder =
                        net.minecraft.world.level.dimension.DimensionType.getStorageFolder(
                                        level.dimension(), root)
                                .resolve("region");
                var file = folder.resolve("r." + (pos.x() >> 5) + "." + (pos.z() >> 5) + ".mca");
                try {
                    exists = Files.readAttributes(file, BasicFileAttributes.class).isRegularFile();
                    if (!exists)
                        throw new IOException(
                                dev.elementalcircuits.text.Texts.get(
                                        "text.elementalcircuits.server.probe._survey_chunk_reader.001"));
                } catch (NoSuchFileException missing) {
                    exists = false;
                }
                regions.put(region, exists);
            }
            if (!exists) return CompletableFuture.completedFuture(new NodeSurvey.Sample(false, 0));
        } catch (IOException error) {
            return CompletableFuture.failedFuture(error);
        }
        return level.getChunkSource()
                .chunkMap
                .read(new ChunkPos(pos.x(), pos.z()))
                .thenApply(
                        tag ->
                                tag.map(t -> decode(t, area, pos))
                                        .orElseGet(() -> new NodeSurvey.Sample(false, 0)));
    }

    public static NodeSurvey.Sample decode(
            CompoundTag tag, SurveyArea area, SurveyArea.Chunk chunk) {
        if (!tag.contains("Status", Tag.TAG_STRING))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.server.probe._survey_chunk_reader.002"));
        if (!tag.getString("Status").equals("minecraft:full")
                && !tag.getString("Status").equals("full")) return new NodeSurvey.Sample(false, 0);
        if (!tag.contains("xPos", Tag.TAG_INT)
                || !tag.contains("zPos", Tag.TAG_INT)
                || tag.getInt("xPos") != chunk.x()
                || tag.getInt("zPos") != chunk.z()
                || !tag.contains("sections", Tag.TAG_LIST))
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.server.probe._survey_chunk_reader.003"));
        ListTag sections = (ListTag) tag.get("sections");
        if (sections.size() > 512
                || !sections.isEmpty() && sections.getElementType() != Tag.TAG_COMPOUND)
            throw new IllegalArgumentException(
                    dev.elementalcircuits.text.Texts.get(
                            "text.elementalcircuits.server.probe._survey_chunk_reader.004"));
        Set<Integer> seen = new HashSet<>();
        long count = 0;
        for (var value : sections) {
            var section = (CompoundTag) value;
            if (!section.contains("Y", Tag.TAG_BYTE) && !section.contains("Y", Tag.TAG_INT))
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.server.probe._survey_chunk_reader.005"));
            int y = section.getInt("Y");
            if (y < -256 || y > 255 || !seen.add(y))
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.server.probe._survey_chunk_reader.006"));
            if (!section.contains("block_states")) continue;
            if (!section.contains("block_states", Tag.TAG_COMPOUND))
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.server.probe._survey_chunk_reader.007"));
            var states = section.getCompound("block_states");
            if (!states.contains("palette", Tag.TAG_LIST))
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.server.probe._survey_chunk_reader.008"));
            var palette = states.getList("palette", Tag.TAG_COMPOUND);
            int size = palette.size();
            if (size < 1 || size > 4096)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.server.probe._survey_chunk_reader.009"));
            boolean[] wanted = new boolean[size];
            boolean any = false;
            for (int i = 0; i < size; i++) {
                if (!palette.getCompound(i).contains("Name", Tag.TAG_STRING))
                    throw new IllegalArgumentException(
                            dev.elementalcircuits.text.Texts.get(
                                    "text.elementalcircuits.server.probe._survey_chunk_reader.010"));
                wanted[i] =
                        java.util.Set.of(
                                        "elementalcircuits:natural_mana_node",
                                        "elementalcircuits:aerial_mana_node")
                                .contains(palette.getCompound(i).getString("Name"));
                any |= wanted[i];
            }
            int bits = Math.max(4, 32 - Integer.numberOfLeadingZeros(size - 1)), per = 64 / bits;
            long mask = (1L << bits) - 1;
            long[] data = size == 1 ? new long[0] : states.getLongArray("data");
            if (size > 1 && data.length != (4096 + per - 1) / per)
                throw new IllegalArgumentException(
                        dev.elementalcircuits.text.Texts.get(
                                "text.elementalcircuits.server.probe._survey_chunk_reader.011"));
            if (!any) continue;
            for (int i = 0; i < 4096; i++) {
                int id = size == 1 ? 0 : (int) (data[i / per] >>> ((i % per) * bits) & mask);
                if (id >= size)
                    throw new IllegalArgumentException(
                            dev.elementalcircuits.text.Texts.get(
                                    "text.elementalcircuits.server.probe._survey_chunk_reader.012"));
                if (wanted[id]
                        && area.contains(
                                chunk.x() * 16 + (i & 15),
                                y * 16 + (i >>> 8),
                                chunk.z() * 16 + (i >>> 4 & 15))) count++;
            }
        }
        return new NodeSurvey.Sample(true, count);
    }
}
