package com.powerfactor.client.quantum;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.powerfactor.PowerFactor;
import com.powerfactor.client.fx.Fx;
import com.powerfactor.quantum.OrbitalCollectorBlockEntity;
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
 * The collector dish: a parabolic dish on a yoke that slowly tracks across the sky. While collecting,
 * its feed horn glows and a shimmering beam of falling motes reaches up into the night.
 */
public class OrbitalCollectorRenderer implements BlockEntityRenderer<OrbitalCollectorBlockEntity, OrbitalCollectorRenderer.State> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(PowerFactor.id("orbital_collector"), "main");
    private static final RenderType TYPE = RenderTypes.entityCutout(PowerFactor.id("textures/entity/turret.png"));
    private static final int CYAN = 0xFF6FE3FF, VIOLET = 0xFFB18CFF;

    public static class State extends BlockEntityRenderState {
        boolean working;
        float time, progress;
    }

    private final ModelPart yoke, dish;

    public OrbitalCollectorRenderer(BlockEntityRendererProvider.Context context) {
        ModelPart root = context.bakeLayer(LAYER);
        yoke = root.getChild("yoke");
        dish = root.getChild("dish");
    }

    /** Boxes centred on their pivots, extending along -Y (flipped up by the renderer). */
    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("yoke", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-3, -1, -3, 6, 1, 6)
                .texOffs(0, 12).addBox(-4, -6, -1, 1, 6, 2)
                .texOffs(0, 12).addBox(3, -6, -1, 1, 6, 2), PartPose.ZERO);
        // A stepped parabola: three rings of plates, wider as they rise, and a feed horn on struts.
        mesh.getRoot().addOrReplaceChild("dish", CubeListBuilder.create()
                .texOffs(0, 40).addBox(-3, -1, -3, 6, 1, 6)
                .texOffs(0, 40).addBox(-5, -2, -5, 10, 1, 10)
                .texOffs(0, 40).addBox(-7, -3, -7, 14, 1, 14)
                .texOffs(32, 0).addBox(-0.5F, -9, -0.5F, 1, 8, 1)
                .texOffs(32, 12).addBox(-1.5F, -10, -1.5F, 3, 2, 3), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(OrbitalCollectorBlockEntity be, State s, float partial, Vec3 camera, ModelFeatureRenderer.@Nullable CrumblingOverlay crumble) {
        BlockEntityRenderer.super.extractRenderState(be, s, partial, camera, crumble);
        s.working = be.clientState() == OrbitalCollectorBlockEntity.WORKING;
        s.progress = be.clientProgress();
        s.time = be.getLevel() == null ? 0 : (be.getLevel().getGameTime() % 72000L) + partial;
        if (be.getLevel() != null) {
            s.lightCoords = LightCoordsUtil.getLightCoords(LightCoordsUtil.BrightnessGetter.DEFAULT, be.getLevel(), be.getBlockState(),
                    be.getBlockPos().above());
        }
    }

    private void part(SubmitNodeCollector c, PoseStack pose, ModelPart p, State s) {
        pose.pushPose();
        pose.scale(1, -1, -1);
        c.submitModelPart(p, pose, TYPE, s.lightCoords, OverlayTexture.NO_OVERLAY, null, -1, s.breakProgress);
        pose.popPose();
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector c, CameraRenderState camera) {
        pose.pushPose();
        pose.translate(0.5, 0.75, 0.5);
        // Tracking: a slow sweep in azimuth with a gentle nod in elevation; faster while collecting.
        float speed = s.working ? 0.6F : 0.12F;
        pose.mulPose(Axis.YP.rotationDegrees(s.time * speed));
        part(c, pose, yoke, s);
        pose.translate(0, 5 / 16F, 0);
        pose.mulPose(Axis.XP.rotationDegrees(25 + 12 * Mth.sin(s.time * 0.01F)));
        part(c, pose, dish, s);
        if (s.working) {
            pose.pushPose();
            pose.translate(0, 9.5F / 16, 0);
            Fx.glow(c, pose, camera.orientation, 0.35F + 0.15F * Mth.sin(s.time * 0.3F), Fx.alpha(CYAN, 0.9F));
            Fx.beam(c, pose, Vec3.ZERO, new Vec3(0, 24, 0), 0.08F + 0.06F * s.progress, -s.time * 0.25F, Fx.alpha(0xFFFFFFFF, 0.55F),
                    Fx.alpha(VIOLET, 0.35F));
            pose.popPose();
            pose.mulPose(Axis.YP.rotationDegrees(s.time * 2));
            Fx.ring(c, pose, 0.5F, 0.03F, 32, s.time * 0.05F, Fx.alpha(CYAN, 0.5F));
        }
        pose.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(OrbitalCollectorBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(1).expandTowards(0, 24, 0);
    }
}
