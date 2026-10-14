package com.powerfactor.client.defense;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.powerfactor.client.fx.Fx;
import com.powerfactor.defense.ShieldGeneratorBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The dome: a lattice of latitude and longitude rings that shimmer and slowly turn, brightening in a
 * ripple whenever something is repulsed; a glowing emitter at its heart.
 */
public class ShieldRenderer implements BlockEntityRenderer<ShieldGeneratorBlockEntity, ShieldRenderer.State> {
    private static final int CYAN = 0xFF6FE3FF;

    public static class State extends BlockEntityRenderState {
        boolean on;
        int radius;
        float time, flash;
    }

    public ShieldRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(ShieldGeneratorBlockEntity be, State s, float partial, Vec3 camera, ModelFeatureRenderer.@Nullable CrumblingOverlay crumble) {
        BlockEntityRenderer.super.extractRenderState(be, s, partial, camera, crumble);
        s.on = be.clientOn();
        s.radius = be.clientRadius();
        s.time = be.getLevel() == null ? 0 : (be.getLevel().getGameTime() % 72000L) + partial;
        float since = be.getLevel() == null ? 99 : be.getLevel().getGameTime() + partial - be.clientHit();
        s.flash = Mth.clamp(1 - since / 12F, 0, 1);
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector c, CameraRenderState camera) {
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        if (!s.on) {
            Fx.glow(c, pose, camera.orientation, 0.4F, Fx.alpha(CYAN, 0.2F));
            pose.popPose();
            return;
        }
        float r = s.radius, base = 0.18F + 0.12F * Mth.sin(s.time * 0.08F) + 0.5F * s.flash;
        Fx.glow(c, pose, camera.orientation, 1.2F, Fx.alpha(CYAN, 0.8F));
        // Latitude rings over the upper hemisphere.
        for (int k = 0; k < 7; k++) {
            double lat = Math.PI / 2 * k / 7;
            float ry = (float) (Math.sin(lat) * r), rr = (float) (Math.cos(lat) * r);
            pose.pushPose();
            pose.translate(0, ry, 0);
            pose.mulPose(Axis.YP.rotationDegrees(s.time * 0.3F + k * 10));
            Fx.ring(c, pose, rr, 0.06F + 0.02F * r / 8, 64, s.time * 0.02F + k, Fx.alpha(CYAN, base));
            pose.popPose();
        }
        // Longitude arcs: full rings stood upright and turned; their lower halves sit below ground, out of sight.
        for (int k = 0; k < 6; k++) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(k * 30 + s.time * 0.3F));
            pose.mulPose(Axis.ZP.rotationDegrees(90));
            Fx.ring(c, pose, r, 0.06F + 0.02F * r / 8, 72, -s.time * 0.02F + k, Fx.alpha(CYAN, base * 0.8F));
            pose.popPose();
        }
        pose.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(ShieldGeneratorBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(ShieldGeneratorBlockEntity.MAX_R + 1);
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }
}
