package com.powerfactor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.powerfactor.PowerFactor;
import com.powerfactor.movement.GrappleServer;
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

/** Worn reel and launcher follow the forearm, with a turning spool while reeling. */
public final class GrappleWristLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(PowerFactor.id("grapple_wrist"), "main");
    private static final net.minecraft.resources.Identifier TEXTURE = PowerFactor.id("textures/entity/grapple_wrist.png");
    private final ModelPart body, spool;
    public GrappleWristLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent, EntityModelSet models) {
        super(parent); var root = models.bakeLayer(LAYER); body = root.getChild("body"); spool = root.getChild("spool");
    }
    public static LayerDefinition createLayer() {
        var mesh = new MeshDefinition(); var root = mesh.getRoot();
        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-3.6F, 6.5F, -3.8F, 7.2F, 3.5F, 1.5F)
                .texOffs(0, 10).addBox(-2.5F, 7.1F, -5.1F, 5, 2.3F, 1.3F), PartPose.ZERO);
        root.addOrReplaceChild("spool", CubeListBuilder.create().texOffs(18, 0).addBox(-1, -1, -0.5F, 2, 2, 1), PartPose.ZERO);
        return LayerDefinition.create(mesh, 32, 32);
    }
    @Override public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yaw, float pitch) {
        var level = net.minecraft.client.Minecraft.getInstance().level; var entity = level == null ? null : level.getEntity(state.id);
        if (state.isInvisible || !(entity instanceof net.minecraft.world.entity.player.Player player)) return;
        var hook = GrappleServer.hook(player);
        if (GrappleServer.device(player).isEmpty() && hook == null) return;
        pose.pushPose(); getParentModel().rightArm.translateAndRotate(pose);
        collector.submitModelPart(body, pose, RenderTypes.entityCutout(TEXTURE), light, OverlayTexture.NO_OVERLAY, null, -1, null);
        pose.translate(0, 8.2F / 16, -5.7F / 16);
        if (hook != null && hook.anchored() && !player.isShiftKeyDown()) pose.mulPose(com.mojang.math.Axis.ZP.rotation(state.ageInTicks * 0.5F));
        collector.submitModelPart(spool, pose, RenderTypes.entityCutout(TEXTURE), light, OverlayTexture.NO_OVERLAY, null, -1, null);
        pose.popPose();
    }
}
