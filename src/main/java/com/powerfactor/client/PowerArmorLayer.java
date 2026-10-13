package com.powerfactor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.powerfactor.PowerFactor;
import com.powerfactor.item.PowerArmorItem;
import com.powerfactor.item.PoweredItem;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;

/** Articulated shoulder plates, limb actuators and powered visor over the equipment skins. */
public final class PowerArmorLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(PowerFactor.id("power_armor"), "main");
    private static final net.minecraft.resources.Identifier TEXTURE = PowerFactor.id("textures/entity/power_armor_parts.png");
    private final ModelPart helmet, chest, shoulder, servo, piston, boot, visor, lamp;
    public PowerArmorLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent, EntityModelSet models) {
        super(parent); var root = models.bakeLayer(LAYER);
        helmet = root.getChild("helmet"); chest = root.getChild("chest"); shoulder = root.getChild("shoulder");
        servo = root.getChild("servo"); piston = root.getChild("piston"); boot = root.getChild("boot");
        visor = root.getChild("visor"); lamp = root.getChild("lamp");
    }
    public static LayerDefinition createLayer() {
        var mesh = new MeshDefinition(); var root = mesh.getRoot();
        root.addOrReplaceChild("helmet", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-5.7F, -8.5F, -4.2F, 1, 5, 8).addBox(4.7F, -8.5F, -4.2F, 1, 5, 8)
                .texOffs(24, 0).addBox(-2.5F, -3.2F, -5.8F, 5, 2.5F, 1.2F), PartPose.ZERO);
        root.addOrReplaceChild("visor", CubeListBuilder.create().texOffs(80, 0).addBox(-3.9F, -6.8F, -5.15F, 7.8F, 2.7F, 0.1F), PartPose.ZERO);
        root.addOrReplaceChild("chest", CubeListBuilder.create().texOffs(0, 20)
                .addBox(-4.4F, 0.5F, -3.2F, 8.8F, 7, 1).texOffs(24, 20).addBox(-3, 1, 2.8F, 6, 8, 1.8F)
                .texOffs(0, 42).addBox(-4.4F, 9, -2.9F, 8.8F, 2, 5.8F), PartPose.ZERO);
        root.addOrReplaceChild("shoulder", CubeListBuilder.create().texOffs(44, 0).addBox(-3.5F, -3.3F, -3.5F, 7, 5, 7), PartPose.ZERO);
        root.addOrReplaceChild("servo", CubeListBuilder.create().texOffs(44, 22)
                .addBox(-2.5F, 1, 2.6F, 5, 3, 1.8F).addBox(-2.5F, 7.5F, -2.8F, 5, 2.5F, 1)
                .texOffs(64, 22).addBox(-2.8F, 2, 0.8F, 1, 7, 1).addBox(1.8F, 2, 0.8F, 1, 7, 1), PartPose.ZERO);
        root.addOrReplaceChild("piston", CubeListBuilder.create().texOffs(70, 22).addBox(-0.5F, 3, 3.3F, 1, 4, 0.8F), PartPose.ZERO);
        root.addOrReplaceChild("boot", CubeListBuilder.create().texOffs(44, 42).addBox(-3.3F, 8, -3.7F, 6.6F, 5, 7)
                .texOffs(72, 42).addBox(-3.4F, 8.5F, -3.4F, 6.8F, 1, 6.8F), PartPose.ZERO);
        root.addOrReplaceChild("lamp", CubeListBuilder.create().texOffs(80, 0).addBox(-2, 4, -3.3F, 4, 0.6F, 0.15F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 128, 64);
    }
    @Override public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yaw, float pitch) {
        var level = net.minecraft.client.Minecraft.getInstance().level;
        var entity = level == null ? null : level.getEntity(state.id);
        if (state.isInvisible || !(entity instanceof net.minecraft.world.entity.player.Player player)) return;
        var model = getParentModel();
        for (var part : PowerArmorItem.Part.values()) {
            var stack = player.getItemBySlot(part.slot);
            if (!(stack.getItem() instanceof PowerArmorItem armor) || armor.part() != part) continue;
            int glow = PoweredItem.energy(stack) > 0 ? 0xFF53E6F2 : 0xFF455961;
            if (part == PowerArmorItem.Part.HELMET) {
                draw(helmet, model.head, pose, collector, light, -1, false);
                draw(visor, model.head, pose, collector, light, glow, PoweredItem.energy(stack) > 0);
            } else if (part == PowerArmorItem.Part.CHEST) {
                draw(chest, model.body, pose, collector, light, -1, false);
                draw(lamp, model.body, pose, collector, light, glow, PoweredItem.energy(stack) > 0);
                draw(shoulder, model.leftArm, pose, collector, light, -1, false);
                draw(shoulder, model.rightArm, pose, collector, light, -1, false);
            } else {
                for (var leg : new ModelPart[] {model.leftLeg, model.rightLeg}) {
                    draw(part == PowerArmorItem.Part.LEGS ? servo : boot, leg, pose, collector, light, -1, false);
                    if (part == PowerArmorItem.Part.LEGS) {
                        pose.pushPose(); leg.translateAndRotate(pose);
                        float extension = PowerArmorItem.assists(stack) && PoweredItem.energy(stack) > 0 ? 1 + Math.abs(leg.xRot) * 0.16F : 1;
                        pose.scale(1, extension, 1);
                        collector.submitModelPart(piston, pose, RenderTypes.entityCutout(TEXTURE), light, OverlayTexture.NO_OVERLAY, null, -1, null);
                        pose.popPose();
                    }
                }
            }
        }
    }
    private static void draw(ModelPart detail, ModelPart bone, PoseStack pose, SubmitNodeCollector collector, int light, int tint, boolean glow) {
        pose.pushPose(); bone.translateAndRotate(pose);
        collector.submitModelPart(detail, pose, glow ? RenderTypes.entityTranslucentEmissive(TEXTURE) : RenderTypes.entityCutout(TEXTURE),
                glow ? 0xF000F0 : light, OverlayTexture.NO_OVERLAY, null, tint, null);
        pose.popPose();
    }
}
