package dev.elementalcircuits.client.registration;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.server.equipment.WizardEquipment;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.*;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.extensions.common.*;

import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;
import top.theillusivec4.curios.api.client.ICurioRenderer;

/**
 * One shared gold monocle model for native armor and Curios headwear, attached to the head bone.
 */
@net.neoforged.fml.common.EventBusSubscriber(
        modid = ElementalCircuits.ID,
        value = Dist.CLIENT,
        bus = net.neoforged.fml.common.EventBusSubscriber.Bus.MOD)
public final class MonocleRendering {
    private static final ModelLayerLocation LAYER =
            new ModelLayerLocation(
                    ResourceLocation.fromNamespaceAndPath(ElementalCircuits.ID, "circuit_monocle"),
                    "main");
    private static HumanoidModel<LivingEntity> model;

    @SubscribeEvent
    public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(LAYER, MonocleRendering::bake);
    }

    @SubscribeEvent
    public static void reload(EntityRenderersEvent.AddLayers event) {
        model = null;
    }

    @SubscribeEvent
    public static void items(RegisterClientExtensionsEvent event) {
        event.registerItem(
                new IClientItemExtensions() {
                    @Override
                    @SuppressWarnings({"rawtypes", "unchecked"})
                    public Model getGenericArmorModel(
                            LivingEntity entity,
                            ItemStack stack,
                            EquipmentSlot slot,
                            HumanoidModel<?> original) {
                        model();
                        ((HumanoidModel) original).copyPropertiesTo(model);
                        model.setAllVisible(true);
                        return model;
                    }
                },
                WizardEquipment.MONOCLE);
    }

    @SubscribeEvent
    public static void curios(FMLClientSetupEvent event) {
        event.enqueueWork(
                () ->
                        CuriosRendererRegistry.register(
                                WizardEquipment.MONOCLE.get(), MonocleCurioRenderer::new));
    }

    private static HumanoidModel<LivingEntity> model() {
        if (model == null)
            model = new HumanoidModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(LAYER));
        return model;
    }

    private static final class MonocleCurioRenderer implements ICurioRenderer {
        @Override
        public <T extends LivingEntity, M extends EntityModel<T>> void render(
                ItemStack stack,
                SlotContext context,
                PoseStack pose,
                RenderLayerParent<T, M> parent,
                MultiBufferSource buffers,
                int light,
                float swing,
                float amount,
                float partial,
                float age,
                float yaw,
                float pitch) {
            if (!context.identifier().equals("head")
                    || context.entity()
                            .getItemBySlot(EquipmentSlot.HEAD)
                            .is(WizardEquipment.MONOCLE.get())
                    || !(parent.getModel() instanceof HumanoidModel<?> original)) return;
            var lens = model();
            lens.head.copyFrom(original.head);
            lens.young = original.young;
            lens.setAllVisible(false);
            lens.head.visible = true;
            lens.renderToBuffer(
                    pose,
                    ItemRenderer.getArmorFoilBuffer(
                            buffers,
                            RenderType.armorCutoutNoCull(
                                    ResourceLocation.fromNamespaceAndPath(
                                            ElementalCircuits.ID,
                                            "textures/models/armor/circuit_monocle_layer_1.png")),
                            stack.hasFoil()),
                    light,
                    OverlayTexture.NO_OVERLAY,
                    -1);
        }
    }

    private static LayerDefinition bake() {
        var mesh = new MeshDefinition();
        var root = mesh.getRoot();
        for (String name :
                new String[] {
                    "head", "hat", "body", "right_arm", "left_arm", "right_leg", "left_leg"
                }) root.addOrReplaceChild(name, CubeListBuilder.create(), PartPose.ZERO);
        var head = root.getChild("head");
        // Octagonal rim on the right eye. All pieces stay clear of the skin surface (z=-4).
        box(head, "top", -2.9f, -5.4f, -4.55f, 1.8f, .4f, .4f, 0);
        box(head, "bottom", -2.9f, -2.6f, -4.55f, 1.8f, .4f, .4f, 0);
        box(head, "left", -3.5f, -4.8f, -4.55f, .4f, 1.6f, .4f, 0);
        box(head, "right", -.9f, -4.8f, -4.55f, .4f, 1.6f, .4f, 0);
        for (int i = 0; i < 2; i++)
            for (int j = 0; j < 2; j++)
                box(
                        head,
                        "corner" + i + j,
                        -3.2f + i * 2f,
                        -5.1f + j * 2.2f,
                        -4.55f,
                        .6f,
                        .6f,
                        .4f,
                        0);
        box(head, "crystal", -3.05f, -4.95f, -4.48f, 2.1f, 2.2f, .12f, 16);
        box(head, "hinge", -4.8f, -3.8f, -4.55f, 1.65f, .4f, .4f, 0);
        box(head, "temple", -4.8f, -3.8f, -4.25f, .35f, .4f, 3.0f, 0);
        box(head, "ear_hook", -4.8f, -3.8f, -1.4f, .35f, .85f, .35f, 0);
        return LayerDefinition.create(mesh, 32, 32);
    }

    private static void box(
            PartDefinition head,
            String name,
            float x,
            float y,
            float z,
            float w,
            float h,
            float d,
            int v) {
        head.addOrReplaceChild(
                name,
                CubeListBuilder.create().texOffs(0, v).addBox(x, y, z, w, h, d),
                PartPose.ZERO);
    }

    private MonocleRendering() {}
}
