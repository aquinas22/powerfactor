package com.powerfactor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.powerfactor.PowerFactor;
import com.powerfactor.client.fx.Fx;
import com.powerfactor.vehicle.MechEntity;
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
 * The mech: an exo-frame with an open cockpit, so the pilot is seen at the controls. Legs swing from
 * the hips and bend at the knees through a walk cycle driven by distance travelled; arms swing
 * against the legs; the torso bobs with each footfall; jump-jets on the back flare and stream exhaust.
 */
public class MechRenderer extends EntityRenderer<MechEntity, MechRenderer.State> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(PowerFactor.id("mech"), "main");
    private static final RenderType TYPE = RenderTypes.entityCutout(PowerFactor.id("textures/entity/mech.png"));
    private static final int FLAME = 0xFFFFB347, CYAN = 0xFF6FE3FF;

    public static class State extends EntityRenderState {
        float yaw, stride, amp;
        boolean jets;
    }

    private final ModelPart pelvis, torso, thigh, shin, foot, upperArm, forearm, jetpack;

    public MechRenderer(EntityRendererProvider.Context context) {
        super(context);
        ModelPart r = context.bakeLayer(LAYER);
        pelvis = r.getChild("pelvis");
        torso = r.getChild("torso");
        thigh = r.getChild("thigh");
        shin = r.getChild("shin");
        foot = r.getChild("foot");
        upperArm = r.getChild("upper_arm");
        forearm = r.getChild("forearm");
        jetpack = r.getChild("jetpack");
        shadowRadius = 1.0F;
    }

    /** Pixel units. Limbs hang down from their pivot (+Y in model space = down after the flip); the torso rises. */
    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        var r = mesh.getRoot();
        r.addOrReplaceChild("pelvis", CubeListBuilder.create().texOffs(0, 0).addBox(-8, -2, -4, 16, 4, 8), PartPose.ZERO);
        // Cockpit frame: a back plate, two side ribs and a roll bar, open at the front.
        r.addOrReplaceChild("torso", CubeListBuilder.create()
                .texOffs(0, 12).addBox(-8, -20, 4, 16, 20, 2)
                .texOffs(36, 12).addBox(-9, -18, -5, 2, 18, 10)
                .texOffs(36, 12).addBox(7, -18, -5, 2, 18, 10)
                .texOffs(0, 34).addBox(-9, -21, -5, 18, 3, 11)
                .texOffs(0, 48).addBox(-6, -7, -6, 12, 2, 2), PartPose.ZERO);
        r.addOrReplaceChild("thigh", CubeListBuilder.create().texOffs(40, 40).addBox(-2.5F, 0, -2.5F, 5, 11, 5), PartPose.ZERO);
        r.addOrReplaceChild("shin", CubeListBuilder.create().texOffs(0, 52).addBox(-2, 0, -2, 4, 11, 4)
                .texOffs(16, 52).addBox(-2.5F, -1, -3, 5, 3, 1), PartPose.ZERO);
        r.addOrReplaceChild("foot", CubeListBuilder.create().texOffs(24, 56).addBox(-3, 0, -5, 6, 2, 8), PartPose.ZERO);
        r.addOrReplaceChild("upper_arm", CubeListBuilder.create().texOffs(52, 0).addBox(-2, -1, -2, 4, 9, 4), PartPose.ZERO);
        r.addOrReplaceChild("forearm", CubeListBuilder.create().texOffs(52, 14).addBox(-2, 0, -2, 4, 9, 4)
                .texOffs(56, 28).addBox(-1, 9, -3, 2, 2, 2), PartPose.ZERO);
        r.addOrReplaceChild("jetpack", CubeListBuilder.create().texOffs(36, 0).addBox(-6, -12, 6, 4, 10, 4)
                .texOffs(36, 0).addBox(2, -12, 6, 4, 10, 4), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(MechEntity e, State s, float partial) {
        super.extractRenderState(e, s, partial);
        s.yaw = e.getYRot(partial);
        s.stride = e.stride(partial);
        s.amp = Math.min(1, e.speed() * 6);
        s.jets = e.jets();
    }

    private void part(SubmitNodeCollector c, PoseStack pose, ModelPart p, State s) {
        pose.pushPose();
        pose.scale(1, -1, -1);   // rotation, not mirror: model +Y down becomes world down, model -Z becomes forward
        c.submitModelPart(p, pose, TYPE, s.lightCoords, OverlayTexture.NO_OVERLAY, null, -1, null);
        pose.popPose();
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector c, CameraRenderState camera) {
        float swing = Mth.sin(s.stride) * 32 * s.amp;
        float bob = Math.abs(Mth.sin(s.stride)) * 0.06F * s.amp;
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180 - s.yaw));
        float hip = 22 / 16F;
        // Legs.
        for (int side = -1; side <= 1; side += 2) {
            float phase = side < 0 ? swing : -swing;
            float knee = Math.max(0, side < 0 ? -Mth.cos(s.stride) : Mth.cos(s.stride)) * 40 * s.amp;
            pose.pushPose();
            pose.translate(side * 5F / 16, hip + bob, 0);
            pose.mulPose(Axis.XP.rotationDegrees(-phase));
            part(c, pose, thigh, s);
            pose.translate(0, -11F / 16, 0);
            pose.mulPose(Axis.XP.rotationDegrees(knee));
            part(c, pose, shin, s);
            pose.translate(0, -11F / 16, 0);
            pose.mulPose(Axis.XP.rotationDegrees(phase - knee));
            part(c, pose, foot, s);
            pose.popPose();
        }
        // Pelvis, torso, jetpack.
        pose.pushPose();
        pose.translate(0, hip + bob, 0);
        part(c, pose, pelvis, s);
        pose.translate(0, 2F / 16, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(Mth.sin(s.stride) * 2 * s.amp));
        part(c, pose, torso, s);
        part(c, pose, jetpack, s);
        // Arms swing against the legs.
        for (int side = -1; side <= 1; side += 2) {
            pose.pushPose();
            pose.translate(side * 11F / 16, 17F / 16, 0);
            pose.mulPose(Axis.XP.rotationDegrees((side < 0 ? -swing : swing) * 0.6F - 10));
            part(c, pose, upperArm, s);
            pose.translate(0, -8F / 16, 0);
            pose.mulPose(Axis.XP.rotationDegrees(-25 - 10 * s.amp));
            part(c, pose, forearm, s);
            pose.popPose();
        }
        // Status lights on the roll bar.
        for (int side = -1; side <= 1; side += 2) {
            pose.pushPose();
            pose.translate(side * 6F / 16, 19.5F / 16, -5.2F / 16);
            Fx.glow(c, pose, camera.orientation, 0.12F, Fx.alpha(CYAN, 0.9F));
            pose.popPose();
        }
        if (s.jets) {
            for (int side = -1; side <= 1; side += 2) {
                pose.pushPose();
                pose.translate(side * 4F / 16, 2F / 16, 8F / 16);
                Fx.glow(c, pose, camera.orientation, 0.5F + 0.15F * Mth.sin(s.ageInTicks * 1.7F), Fx.alpha(FLAME, 0.95F));
                Fx.beam(c, pose, Vec3.ZERO, new Vec3(0, -1.6, 0), 0.12F, -s.ageInTicks * 0.5F, Fx.alpha(0xFFFFFFFF, 0.8F), Fx.alpha(FLAME, 0.6F));
                pose.popPose();
            }
        }
        pose.popPose();
        pose.popPose();
        super.submit(s, pose, c, camera);
    }
}
