package com.powerfactor.client.dilation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.powerfactor.client.fx.Fx;
import com.powerfactor.dilation.TimeFieldBlockEntity;
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
 * The field: a gold clockwork core spinning over the block, three clock rings orbiting it faster the
 * harder time is pushed, and the edge of the bubble drawn as a faint shimmering cage that pulses at the
 * dilated rate.
 */
public class TimeFieldRenderer implements BlockEntityRenderer<TimeFieldBlockEntity, TimeFieldRenderer.State> {
    public static class State extends BlockEntityRenderState {
        boolean on;
        int speed = 1, radius = 2;
        float time;
    }

    public TimeFieldRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(TimeFieldBlockEntity be, State s, float partial, Vec3 camera, ModelFeatureRenderer.@Nullable CrumblingOverlay crumble) {
        BlockEntityRenderer.super.extractRenderState(be, s, partial, camera, crumble);
        s.on = be.clientOn();
        s.speed = Math.max(1, be.clientSpeed());
        s.radius = be.clientRadius();
        s.time = be.getLevel() == null ? 0 : (be.getLevel().getGameTime() % 72000L) + partial;
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector c, CameraRenderState camera) {
        float t = s.time, rate = s.on ? s.speed : 0.25F;
        pose.pushPose();
        pose.translate(0.5, 1.15, 0.5);
        Fx.glow(c, pose, camera.orientation, 0.6F + (s.on ? 0.1F * Mth.sin(t * 0.3F * rate) : 0), Fx.alpha(0xFFFFD84A, s.on ? 1F : 0.4F));
        for (int k = 0; k < 3; k++) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(t * 2 * rate * (k + 1) * (k % 2 == 0 ? 1 : -1)));
            pose.mulPose(Axis.XP.rotationDegrees(60 * k + 20));
            Fx.ring(c, pose, 0.35F + k * 0.12F, 0.03F, 32, t * 0.05F * rate, Fx.alpha(k == 1 ? 0xFF6FE3FF : 0xFFFFD84A, s.on ? 0.95F : 0.35F));
            pose.popPose();
        }
        pose.popPose();
        if (!s.on) return;
        // The bubble's edge: the cube it acts on, its edges glowing and pulsing at the dilated rate.
        float r = s.radius + 0.5F, pulse = 0.35F + 0.25F * (0.5F + 0.5F * Mth.sin(t * 0.25F * s.speed));
        int col = Fx.alpha(0xFFFFD84A, pulse);
        float lo = 0.5F - r, hi = 0.5F + r, ylo = 0.5F - r, yhi = 0.5F + r;
        float[][] corners = {{lo, lo}, {hi, lo}, {hi, hi}, {lo, hi}};
        for (int i = 0; i < 4; i++) {
            float[] a = corners[i], b = corners[(i + 1) % 4];
            Fx.beam(c, pose, new Vec3(a[0], ylo, a[1]), new Vec3(b[0], ylo, b[1]), 0.04F, t * 0.02F, col, Fx.alpha(col, 0.4F));
            Fx.beam(c, pose, new Vec3(a[0], yhi, a[1]), new Vec3(b[0], yhi, b[1]), 0.04F, t * 0.02F, col, Fx.alpha(col, 0.4F));
            Fx.beam(c, pose, new Vec3(a[0], ylo, a[1]), new Vec3(a[0], yhi, a[1]), 0.04F, t * 0.02F, col, Fx.alpha(col, 0.4F));
        }
        // Motes drifting through the bubble, hurried along by the field.
        for (int k = 0; k < 10; k++) {
            float f = (t * 0.004F * s.speed + k * 0.1F) % 1F;
            double a = k * 2.399;
            pose.pushPose();
            pose.translate(0.5 + Math.cos(a) * r * 0.7, ylo + f * (yhi - ylo), 0.5 + Math.sin(a) * r * 0.7);
            Fx.glow(c, pose, camera.orientation, 0.12F, Fx.alpha(0xFFFFE8A0, 1F - Math.abs(f - 0.5F) * 2));
            pose.popPose();
        }
    }

    @Override
    public AABB getRenderBoundingBox(TimeFieldBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(10);
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }
}
