package dev.elementalcircuits.server;

import dev.elementalcircuits.*;
import dev.elementalcircuits.adapter.codec.BlueprintTextCode;
import dev.elementalcircuits.adapter.persistence.*;
import dev.elementalcircuits.domain.circuit.Rune;
import dev.elementalcircuits.testing.TestBlueprints;

import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(ElementalCircuits.ID)
@PrefixGameTestTemplate(false)
public final class BlueprintFileMinecraftContracts {
    @GameTest(template = "empty")
    public static void fileBlueprintsPersistInPhysicalNotebook(GameTestHelper h) {
        var stack = new ItemStack(ElementalCircuits.NOTEBOOK.get());
        var volume = TestBlueprints.fill(NotebookRepository.load(stack));
        NotebookRepository.save(stack, volume);
        var copy = stack.copy();
        h.assertTrue(
                NotebookRepository.load(copy).equals(volume),
                "All indexed file-based pages persist in native stack data");
        var encoded = copy.save(h.getLevel().registryAccess());
        var decoded = ItemStack.parse(h.getLevel().registryAccess(), encoded).orElseThrow();
        h.assertTrue(
                NotebookRepository.load(decoded).equals(volume),
                "Native item serialization preserves titles, page order and frozen settings");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void storageFileLoadsThroughNotebookImport(GameTestHelper h) {
        var file =
                TestBlueprints.entries().stream()
                        .filter(e -> e.file().equals("storage.ecblueprint"))
                        .findFirst()
                        .orElseThrow();
        var stack = new ItemStack(ElementalCircuits.NOTEBOOK.get());
        var volume =
                NotebookRepository.load(stack)
                        .add(
                                file.title(),
                                BlueprintTextCode.decode(
                                        BlueprintTextCode.encode(file.blueprint())));
        NotebookRepository.save(stack, volume);
        var loaded = NotebookRepository.load(stack).page(volume.selected()).blueprint();
        h.assertTrue(
                loaded.equals(file.blueprint()) && loaded.rune(2).kind() == Rune.Kind.STORAGE,
                "Normal blueprint text import includes storage settings");
        h.succeed();
    }
}
