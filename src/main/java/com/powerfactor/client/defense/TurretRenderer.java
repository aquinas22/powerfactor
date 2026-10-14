package com.powerfactor.client.defense;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.powerfactor.PowerFactor;
import com.powerfactor.client.fx.Fx;
import com.powerfactor.defense.TurretBlockEntity;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Turret head: a yoke that turns, a gun that turns and elevates. Plasma turrets carry twin barrels
 * whose muzzles flare green with each bolt; railgun turrets carry a long rail pair whose capacitor
 * coils glow brighter as a shot charges, and the whole gun kicks back on firing.
 */
public class TurretRenderer implements BlockEntityRenderer<TurretBlockEntity, TurretRenderer.State> {
    public static final ModelLayerLocation PLASMA = new ModelLayerLocation(PowerFactor.id("plasma_turret"), "main");
    public static final ModelLayerLocation RAIL = new ModelLayerLocation(PowerFactor.id("railgun_turret"), "main");
    private static final RenderType TYPE = RenderTypes.entityCutout(PowerFactor.id("textures/entity/turret.png"));
    private static final int GREEN = 0xFF7CFF9A, CYAN = 0xFF8FF4FF;

    public static class State extends BlockEntityRenderState {
        boolean rail, tracking;
        float yaw, pitch, charge, flash, time;
    }

    private final ModelPart plasmaYoke, plasmaGun, railYoke, railGun;

    public TurretRenderer(BlockEntityRendererProvider.Context context) {
        ModelPart p = context.bakeLayer(PLASMA), r = context.bakeLayer(RAIL);
        plasmaYoke = p.getChild("yoke");
        plasmaGun = p.getChild("gun");
        railYoke = r.getChild("yoke");
        railGun = r.getChild("gun");
    }

    /** Model space: origin at the pivot, -Y up, -Z forward (the renderer flips Y and Z). */
    private static MeshDefinition yoke() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("yoke", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-5, 4, -5, 10, 2, 10)        // turntable
                .texOffs(0, 12).addBox(-5.5F, -3, -2, 1.5F, 7, 4)   // left arm
                .texOffs(0, 12).addBox(4, -3, -2, 1.5F, 7, 4),      // right arm
                PartPose.ZERO);
        return mesh;
    }

    public static LayerDefinition plasmaLayer() {
        MeshDefinition mesh = yoke();
        mesh.getRoot().addOrReplaceChild("gun", CubeListBuilder.create()
                .texOffs(0, 24).addBox(-4, -3, -4, 8, 6, 9)          // housing
                .texOffs(34, 24).addBox(-3, -4, -1, 6, 1, 5)         // dorsal heat sink
                .texOffs(32, 0).addBox(-3, -1, -12, 2, 2, 8)         // barrels
                .texOffs(32, 0).addBox(1, -1, -12, 2, 2, 8)
                .texOffs(32, 12).addBox(-3.5F, -1.5F, -13, 3, 3, 1.5F) // muzzle collars
                .texOffs(32, 12).addBox(0.5F, -1.5F, -13, 3, 3, 1.5F),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    public static LayerDefinition railLayer() {
        MeshDefinition mesh = yoke();
        mesh.getRoot().addOrReplaceChild("gun", CubeListBuilder.create()
                .texOffs(0, 24).addBox(-4, -3, -3, 8, 6, 9)          // breech
                .texOffs(0, 40).addBox(-2.5F, -1.5F, -17, 1, 3, 14)  // rails
                .texOffs(0, 40).addBox(1.5F, -1.5F, -17, 1, 3, 14)
                .texOffs(32, 12).addBox(-3, -2.5F, -7, 6, 5, 1)      // capacitor coils
                .texOffs(32, 12).addBox(-3, -2.5F, -11, 6, 5, 1)
                .texOffs(32, 12).addBox(-3, -2.5F, -15, 6, 5, 1)
                .texOffs(34, 24).addBox(-2, 3, -2, 4, 2, 6),         // capacitor bank
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(TurretBlockEntity be, State s, float partial, Vec3 camera, ModelFeatureRenderer.@Nullable CrumblingOverlay crumble) {
        BlockEntityRenderer.super.extractRenderState(be, s, partial, camera, crumble);
        be.smooth(0.35F);
        s.rail = be.tier() == TurretBlockEntity.Tier.RAIL;
        s.yaw = be.drawYaw();
        s.pitch = be.drawPitch();
        s.charge = be.charge();
        s.tracking = be.tracking();
        s.time = be.getLevel() == null ? 0 : (be.getLevel().getGameTime() % 72000L) + partial;
        float since = be.getLevel() == null ? 99 : be.getLevel().getGameTime() + partial - be.lastShot();
        s.flash = Mth.clamp(1 - since / (s.rail ? 8F : 3F), 0, 1);
        if (be.getLevel() != null) {
            s.lightCoords = LightCoordsUtil.getLightCoords(LightCoordsUtil.BrightnessGetter.DEFAULT, be.getLevel(), be.getBlockState(),
                    be.getBlockPos().above());
        }
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector c, CameraRenderState camera) {
        pose.pushPose();
        pose.translate(0.5, TurretBlockEntity.PIVOT, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-s.yaw));
        pose.pushPose();
        pose.scale(1, -1, -1);
        c.submitModelPart(s.rail ? railYoke : plasmaYoke, pose, TYPE, s.lightCoords, OverlayTexture.NO_OVERLAY, null, -1, s.breakProgress);
        pose.popPose();
        pose.mulPose(Axis.XP.rotationDegrees(s.pitch));
        // Recoil: the gun slides back along its axis and springs forward.
        float kick = s.flash * s.flash * (s.rail ? 0.18F : 0.05F);
        pose.translate(0, 0, -kick);
        pose.pushPose();
        pose.scale(1, -1, -1);
        c.submitModelPart(s.rail ? railGun : plasmaGun, pose, TYPE, s.lightCoords, OverlayTexture.NO_OVERLAY, null, -1, s.breakProgress);
        pose.popPose();
        if (s.rail) {
            // Coils light in sequence as the charge builds; a bright core forms at the muzzle.
            for (int k = 0; k < 3; k++) {
                float lit = Mth.clamp(s.charge * 3 - k, 0, 1);
                float z = (7 + 4 * k) / 16F;
                pose.pushPose();
                pose.translate(0, 0, z - 0.03F);
                pose.mulPose(Axis.XP.rotationDegrees(90));        // rings lie flat by default; stand them around the rails
                Fx.ring(c, pose, 0.19F, 0.03F + 0.03F * lit, 20, s.time * 0.2F, Fx.alpha(CYAN, 0.15F + 0.85F * Math.max(lit, s.flash)));
                pose.popPose();
            }
            pose.pushPose();
            pose.translate(0, 0, 17.5F / 16);
            Fx.glow(c, pose, camera.orientation, 0.15F + 0.5F * s.charge + 1.2F * s.flash, Fx.alpha(CYAN, Math.max(s.charge, s.flash)));
            pose.popPose();
        } else {
            for (float x : new float[] {-2 / 16F, 2 / 16F}) {
                pose.pushPose();
                pose.translate(x, 0, 13.5F / 16);
                float idle = s.tracking ? 0.35F + 0.15F * Mth.sin(s.time * 0.8F) : 0.12F;
                Fx.glow(c, pose, camera.orientation, 0.12F + 0.55F * s.flash, Fx.alpha(GREEN, Math.max(idle, s.flash)));
                pose.popPose();
            }
        }
        // Sensor eye on the housing: green/cyan when tracking, dim amber when scanning.
        pose.pushPose();
        pose.translate(0, 1.5F / 16, (s.rail ? 3.2F : 4.2F) / 16);
        Fx.glow(c, pose, camera.orientation, 0.08F, s.tracking ? (s.rail ? CYAN : GREEN) : Fx.alpha(0xFFFFB347, 0.6F));
        pose.popPose();
        pose.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(TurretBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(1.2);
    }
}
