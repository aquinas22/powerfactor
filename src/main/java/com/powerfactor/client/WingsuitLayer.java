package com.powerfactor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.powerfactor.PowerFactor;
import com.powerfactor.item.WingsuitItem;
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
import net.minecraft.world.entity.EquipmentSlot;

/** Shoulder-to-hip membranes and leg fabric unfold with the avatar's custom spread-arm pose. */
public final class WingsuitLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(PowerFactor.id("wingsuit"), "main");
    private static final net.minecraft.resources.Identifier TEXTURE = PowerFactor.id("textures/entity/wingsuit.png");
    private final ModelPart harness;
    public WingsuitLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent, EntityModelSet models) {
        super(parent); harness = models.bakeLayer(LAYER).getChild("harness");
    }
    public static LayerDefinition createLayer() {
        var mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("harness", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-3.5F, 2, 2.7F, 7, 8, 1.3F).texOffs(20, 0)
                .addBox(-4.2F, 1, -2.65F, 1, 8, 0.6F).addBox(3.2F, 1, -2.65F, 1, 8, 0.6F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 32);
    }
    @Override public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yaw, float pitch) {
        var level = net.minecraft.client.Minecraft.getInstance().level; var entity = level == null ? null : level.getEntity(state.id);
        if (state.isInvisible || !(entity instanceof net.minecraft.world.entity.player.Player player) || !WingsuitItem.worn(player.getItemBySlot(EquipmentSlot.CHEST))) return;
        float open = MovementClient.wingOpen(state.id, state.partialTick);
        pose.pushPose(); getParentModel().body.translateAndRotate(pose);
        collector.submitModelPart(harness, pose, RenderTypes.entityCutout(TEXTURE), light, OverlayTexture.NO_OVERLAY, null, -1, null);
        if (open > 0.01F) {
            var type = RenderTypes.entityCutout(TEXTURE);
            float span = 4.5F + 9F * open;
            collector.submitCustomGeometry(pose, type, (p, vertices) -> {
                for (int side : new int[] {-1, 1}) {
                    float[][] points = {{side * 4, 1.5F, 2.65F, 0.5F, 0.5F}, {side * span, 6.5F, 2.4F, 1, 0.5F},
                            {side * 3.5F, 11.5F, 2.7F, 0.5F, 1}, {side * 3.5F, 11.5F, 2.7F, 0.5F, 1}};
                    for (float[] v : points) vertices.addVertex(p, v[0] / 16, v[1] / 16, v[2] / 16).setColor(0xFFFFFFFF)
                            .setUv(v[3], v[4]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0, 0, 1);
                }
                float[][] points = {{-3.8F, 10.5F, 2.6F, 0.5F, 0.5F}, {3.8F, 10.5F, 2.6F, 1, 0.5F},
                        {1.5F * open, 22, 2.5F, 1, 1}, {-1.5F * open, 22, 2.5F, 0.5F, 1}};
                for (float[] v : points) vertices.addVertex(p, v[0] / 16, v[1] / 16, v[2] / 16).setColor(0xFFFFFFFF)
                        .setUv(v[3], v[4]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0, 0, 1);
            });
        }
        pose.popPose();
    }
}
