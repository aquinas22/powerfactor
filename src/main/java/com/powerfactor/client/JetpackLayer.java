package com.powerfactor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.powerfactor.PowerFactor;
import com.powerfactor.item.JetpackItem;
import com.powerfactor.item.JetpackServer;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.resources.Identifier;

/**
 * The jetpack on a player's back: a battery pack between two ducted fans, shoulder straps, fans that
 * spin up with thrust, and a cyan glow from the nozzles.
 */
public class JetpackLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(PowerFactor.id("jetpack"), "main");
    private static final Identifier TEXTURE = PowerFactor.id("textures/entity/jetpack.png");
    private static final RenderType SOLID = RenderTypes.entityCutout(TEXTURE);
    private static final RenderType GLOW = RenderTypes.entityTranslucentEmissive(TEXTURE);

    private final ModelPart pack;
    private final ModelPart fan;
    private final ModelPart glow;

    public JetpackLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent, EntityModelSet models) {
        super(parent);
        ModelPart root = models.bakeLayer(LAYER);
        this.pack = root.getChild("pack");
        this.fan = root.getChild("fan");
        this.glow = root.getChild("glow");
    }

    /** Entity-model pixels, relative to the body part (origin at the neck; +Y down, +Z toward the back). */
    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("pack", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-3.5F, 1, 2.1F, 7, 9, 1.5F)               // back plate
                .texOffs(18, 0).addBox(-2, 2, 3.6F, 4, 7, 2.5F)                 // battery
                .texOffs(0, 12).addBox(-7.2F, 0.5F, 2.6F, 3.4F, 9, 3.4F)         // left fan duct
                .texOffs(0, 12).addBox(3.8F, 0.5F, 2.6F, 3.4F, 9, 3.4F)          // right fan duct
                .texOffs(14, 12).addBox(-7.5F, 9.5F, 2.3F, 4, 1.2F, 4)           // left nozzle
                .texOffs(14, 12).addBox(3.5F, 9.5F, 2.3F, 4, 1.2F, 4)            // right nozzle
                .texOffs(32, 0).addBox(-3.8F, 4, 3.2F, 0.8F, 1.5F, 1)            // duct struts
                .texOffs(32, 0).addBox(3.0F, 4, 3.2F, 0.8F, 1.5F, 1)
                .texOffs(36, 0).addBox(-3.4F, -0.2F, -2.3F, 1.2F, 8, 0.4F)       // shoulder straps, front
                .texOffs(36, 0).addBox(2.2F, -0.2F, -2.3F, 1.2F, 8, 0.4F)
                .texOffs(40, 0).addBox(-3.4F, -0.3F, -2.3F, 1.2F, 0.4F, 4.6F)    // over the shoulders
                .texOffs(40, 0).addBox(2.2F, -0.3F, -2.3F, 1.2F, 0.4F, 4.6F)
                .texOffs(36, 10).addBox(-3.4F, 5.5F, -2.4F, 6.8F, 0.8F, 0.5F),   // chest strap
                PartPose.ZERO);
        // Fan rotor: four blades round a hub, spun about Y inside each duct.
        PartDefinition fan = root.addOrReplaceChild("fan", CubeListBuilder.create().texOffs(30, 16).addBox(-0.5F, -0.3F, -0.5F, 1, 0.6F, 1),
                PartPose.ZERO);
        for (int i = 0; i < 4; i++) {
            fan.addOrReplaceChild("blade" + i, CubeListBuilder.create().texOffs(30, 20).addBox(0.3F, -0.15F, -0.35F, 1.2F, 0.3F, 0.7F),
                    PartPose.rotation(0.35F, i * (float) Math.PI / 2, 0));
        }
        root.addOrReplaceChild("glow", CubeListBuilder.create().texOffs(48, 16).addBox(-1.5F, 0, -1.5F, 3, 2.5F, 3), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 32);
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
        // The render state only carries chest gear that has an armour model, so ask the player itself.
        var level = net.minecraft.client.Minecraft.getInstance().level;
        var entity = level == null ? null : level.getEntity(state.id);
        if (state.isInvisible || !(entity instanceof net.minecraft.world.entity.player.Player player)
                || !(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).getItem() instanceof JetpackItem)) {
            return;
        }
        pose.pushPose();
        getParentModel().body.translateAndRotate(pose);
        collector.submitModelPart(pack, pose, SOLID, light, OverlayTexture.NO_OVERLAY, null, -1, null);
        float angle = JetpackClient.fanAngle(state.id, state.partialTick);
        int mode = JetpackClient.mode(state.id);
        for (float x : new float[] {-5.5F, 5.5F}) {
            pose.pushPose();
            pose.translate(x / 16F, 1.4F / 16F, 4.3F / 16F);
            pose.mulPose(Axis.YP.rotationDegrees(x < 0 ? angle : -angle));
            collector.submitModelPart(fan, pose, SOLID, light, OverlayTexture.NO_OVERLAY, null, -1, null);
            pose.popPose();
            if (mode != JetpackServer.OFF) {
                pose.pushPose();
                pose.translate(x / 16F, 10.7F / 16F, 4.3F / 16F);
                float s = mode == JetpackServer.THRUST ? 1.0F : 0.6F;
                pose.scale(s, s * (0.8F + 0.2F * (float) Math.sin(state.ageInTicks * 2.7F)), s);
                collector.submitModelPart(glow, pose, GLOW, 0xF000F0, OverlayTexture.NO_OVERLAY, null, -1, null);
                pose.popPose();
            }
        }
        pose.popPose();
    }
}
