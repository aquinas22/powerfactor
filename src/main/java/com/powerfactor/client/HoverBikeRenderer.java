package com.powerfactor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.powerfactor.PowerFactor;
import com.powerfactor.client.fx.Fx;
import com.powerfactor.vehicle.HoverBikeEntity;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Hover-bike: a low, swept hull with a windscreen, handlebars and twin repulsor pods. It bobs on its
 * cushion, banks into turns and pitches with the terrain; the pods glow cyan under the hull, flare
 * hotter when boosting, and the exhaust streams a trail.
 */
public class HoverBikeRenderer extends EntityRenderer<HoverBikeEntity, HoverBikeRenderer.State> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(PowerFactor.id("hover_bike"), "main");
    private static final RenderType BODY = RenderTypes.entityCutout(PowerFactor.id("textures/entity/hover_bike.png"));
    private static final int CYAN = 0xFF6FE3FF, HOT = 0xFFFFB347;

    public static class State extends EntityRenderState {
        float yaw, pitch, bank, speed;
        boolean field, boost;
    }

    private final ModelPart body;

    public HoverBikeRenderer(EntityRendererProvider.Context context) {
        super(context);
        body = context.bakeLayer(LAYER).getChild("body");
        shadowRadius = 0.6F;
    }

    /** Model space: -Y up, -Z forward (flipped by the renderer). Units are pixels. */
    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-3.5F, -5, -10, 7, 4, 20)       // hull
                .texOffs(0, 24).addBox(-2.5F, -7, -2, 5, 2, 9)        // seat
                .texOffs(28, 24).addBox(-3, -8, -12, 6, 4, 3)         // nose cowl
                .texOffs(46, 24).addBox(-2.5F, -11, -10, 5, 3, 1)     // windscreen
                .texOffs(0, 36).addBox(-5, -8.5F, -7, 10, 1, 1)       // handlebars
                .texOffs(0, 40).addBox(-5.5F, -3, -8, 2, 3, 6)        // repulsor pods
                .texOffs(0, 40).addBox(3.5F, -3, -8, 2, 3, 6)
                .texOffs(16, 40).addBox(-5.5F, -3, 4, 2, 3, 6)
                .texOffs(16, 40).addBox(3.5F, -3, 4, 2, 3, 6)
                .texOffs(32, 36).addBox(-1.5F, -4, 10, 3, 3, 3),      // exhaust
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(HoverBikeEntity e, State s, float partial) {
        super.extractRenderState(e, s, partial);
        s.yaw = e.getYRot(partial);
        s.pitch = e.getXRot(partial);
        s.bank = e.bank(partial);
        s.speed = e.speed();
        s.field = e.field();
        s.boost = e.boosting();
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector c, CameraRenderState camera) {
        float t = s.ageInTicks;
        pose.pushPose();
        // A gentle bob on the cushion while the field is up.
        if (s.field) pose.translate(0, 0.04F * Mth.sin(t * 0.18F), 0);
        pose.mulPose(Axis.YP.rotationDegrees(180 - s.yaw));
        pose.mulPose(Axis.XP.rotationDegrees(-s.pitch));
        pose.mulPose(Axis.ZP.rotationDegrees(s.bank));
        pose.pushPose();
        pose.translate(0, 0.0625F, 0);
        pose.scale(1, -1, -1);
        c.submitModelPart(body, pose, BODY, s.lightCoords, OverlayTexture.NO_OVERLAY, null, -1, null);
        pose.popPose();
        if (s.field) {
            float heat = s.boost ? 1 : Math.min(1, s.speed * 1.2F);
            int col = s.boost ? HOT : CYAN;
            for (float x : new float[] {-4.5F / 16, 4.5F / 16}) {
                for (float z : new float[] {5F / 16, -7F / 16}) {
                    pose.pushPose();
                    pose.translate(x, 0.02F, z);
                    Fx.glow(c, pose, camera.orientation, 0.35F + 0.25F * heat, Fx.alpha(col, 0.6F + 0.3F * Mth.sin(t * 0.9F + x * 9)));
                    pose.mulPose(Axis.YP.rotationDegrees(t * 6));
                    Fx.ring(c, pose, 0.16F + 0.05F * Mth.sin(t * 0.5F), 0.03F, 16, t * 0.1F, Fx.alpha(col, 0.5F));
                    pose.popPose();
                }
            }
            // Exhaust: a short glow at idle, a long streak when boosting. Local +Z is behind the bike here.
            pose.pushPose();
            pose.translate(0, 0.15F, 12.5F / 16);
            Fx.glow(c, pose, camera.orientation, 0.18F + 0.3F * heat, Fx.alpha(col, 0.8F));
            if (heat > 0.2F) Fx.beam(c, pose, Vec3.ZERO, new Vec3(0, 0, 0.6 + 2.6 * heat), 0.07F + 0.06F * heat, -t * 0.4F,
                    Fx.alpha(0xFFFFFFFF, 0.6F * heat), Fx.alpha(col, 0.5F * heat));
            pose.popPose();
        }
        pose.popPose();
        super.submit(s, pose, c, camera);
    }
}
