package dev.elementalcircuits.client.registration;

import com.google.gson.*;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.server.equipment.WizardEquipment;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.*;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.extensions.common.*;

import java.io.*;
import java.util.*;

/**
 * Bakes Blender's bone-local cuboids once per resource reload; native armor handles poses and
 * glint.
 */
@net.neoforged.fml.common.EventBusSubscriber(
        modid = ElementalCircuits.ID,
        value = Dist.CLIENT,
        bus = net.neoforged.fml.common.EventBusSubscriber.Bus.MOD)
public final class WizardArmorRendering {
    private static final Map<EquipmentSlot, HumanoidModel<LivingEntity>> MODELS =
            new EnumMap<>(EquipmentSlot.class);
    private static final EquipmentSlot[] SLOTS = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private static ModelLayerLocation location(EquipmentSlot s) {
        return new ModelLayerLocation(
                ResourceLocation.fromNamespaceAndPath(
                        ElementalCircuits.ID, "wizard_" + s.getName()),
                "main");
    }

    @SubscribeEvent
    public static void layers(EntityRenderersEvent.RegisterLayerDefinitions e) {
        for (var slot : SLOTS) e.registerLayerDefinition(location(slot), () -> bake(slot));
    }

    @SubscribeEvent
    public static void reload(EntityRenderersEvent.AddLayers e) {
        MODELS.clear();
    }

    @SubscribeEvent
    public static void items(RegisterClientExtensionsEvent e) {
        e.registerItem(
                new IClientItemExtensions() {
                    @Override
                    @SuppressWarnings({"rawtypes", "unchecked"})
                    public Model getGenericArmorModel(
                            LivingEntity entity,
                            ItemStack stack,
                            EquipmentSlot slot,
                            HumanoidModel<?> original) {
                        var model =
                                MODELS.computeIfAbsent(
                                        slot,
                                        s ->
                                                new HumanoidModel<>(
                                                        Minecraft.getInstance()
                                                                .getEntityModels()
                                                                .bakeLayer(location(s))));
                        ((HumanoidModel) original).copyPropertiesTo(model);
                        model.setAllVisible(true);
                        return model;
                    }
                },
                WizardEquipment.HAT,
                WizardEquipment.ROBE,
                WizardEquipment.LEGGINGS,
                WizardEquipment.BOOTS);
    }

    private static LayerDefinition bake(EquipmentSlot slot) {
        MeshDefinition mesh = new MeshDefinition();
        var root = mesh.getRoot();
        root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-5, 2, 0));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(5, 2, 0));
        root.addOrReplaceChild(
                "right_leg", CubeListBuilder.create(), PartPose.offset(-1.9f, 12, 0));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(1.9f, 12, 0));
        var resource =
                ResourceLocation.fromNamespaceAndPath(
                        ElementalCircuits.ID, "models/armor/wizard.json");
        try (var reader = Minecraft.getInstance().getResourceManager().openAsReader(resource)) {
            var json = JsonParser.parseReader(reader).getAsJsonObject();
            if (json.get("version").getAsInt() != 1)
                throw new IOException(
                        dev.elementalcircuits.client.text.UiText.string(
                                "text.elementalcircuits.client.registration._wizard_armor_rendering.001"));
            var parts = json.getAsJsonArray("parts");
            if (parts.size() > 256)
                throw new IOException(
                        dev.elementalcircuits.client.text.UiText.string(
                                "text.elementalcircuits.client.registration._wizard_armor_rendering.002"));
            for (var value : parts) {
                var part = value.getAsJsonObject();
                if (!part.get("piece").getAsString().equals(slot.name())) continue;
                var b = part.getAsJsonArray("box");
                var uv = part.getAsJsonArray("uv");
                var r = part.getAsJsonArray("rotation");
                var p = part.getAsJsonArray("pivot");
                root.getChild(part.get("bone").getAsString())
                        .addOrReplaceChild(
                                part.get("name").getAsString(),
                                CubeListBuilder.create()
                                        .texOffs(uv.get(0).getAsInt(), uv.get(1).getAsInt())
                                        .addBox(
                                                b.get(0).getAsFloat(),
                                                b.get(1).getAsFloat(),
                                                b.get(2).getAsFloat(),
                                                b.get(3).getAsFloat(),
                                                b.get(4).getAsFloat(),
                                                b.get(5).getAsFloat()),
                                PartPose.offsetAndRotation(
                                        p.get(0).getAsFloat(),
                                        p.get(1).getAsFloat(),
                                        p.get(2).getAsFloat(),
                                        r.get(0).getAsFloat(),
                                        r.get(1).getAsFloat(),
                                        r.get(2).getAsFloat()));
            }
            return LayerDefinition.create(
                    mesh,
                    json.get("textureWidth").getAsInt(),
                    json.get("textureHeight").getAsInt());
        } catch (IOException | RuntimeException error) {
            throw new IllegalStateException(
                    dev.elementalcircuits.client.text.UiText.string(
                            "text.elementalcircuits.client.registration._wizard_armor_rendering.003"),
                    error);
        }
    }

    private WizardArmorRendering() {}
}
