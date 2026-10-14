package com.powerfactor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.powerfactor.client.fx.Fx;
import com.powerfactor.logistics.GravityLiftBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** The lift column: a soft cyan shaft with rings drifting up its length and a glow on the emitter. */
public class GravityLiftRenderer implements BlockEntityRenderer<GravityLiftBlockEntity, GravityLiftRenderer.State> {
    private static final int CYAN = 0xFF6FE3FF, WHITE = 0xFFE6FCFF;

    public static class State extends BlockEntityRenderState {
        boolean on;
        int height;
        float time;
    }

    public GravityLiftRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(GravityLiftBlockEntity be, State s, float partial, Vec3 camera, ModelFeatureRenderer.@Nullable CrumblingOverlay crumble) {
        BlockEntityRenderer.super.extractRenderState(be, s, partial, camera, crumble);
        s.on = be.clientOn();
        s.height = be.clientHeight();
        s.time = be.getLevel() == null ? 0 : (be.getLevel().getGameTime() % 72000L) + partial;
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector c, CameraRenderState camera) {
        pose.pushPose();
        pose.translate(0.5, 0.26, 0.5);
        if (!s.on) {
            Fx.glow(c, pose, camera.orientation, 0.4F, Fx.alpha(CYAN, 0.2F));
            pose.popPose();
            return;
        }
        float top = s.height + 0.74F;
        Fx.beam(c, pose, Vec3.ZERO, new Vec3(0, top, 0), 0.38F, -s.time * 0.08F, Fx.alpha(WHITE, 0.12F), Fx.alpha(CYAN, 0.18F));
        Fx.glow(c, pose, camera.orientation, 0.9F, Fx.alpha(CYAN, 0.6F));
        // Rings rise up the shaft; more of them on taller columns.
        int rings = 2 + s.height / 3;
        for (int i = 0; i < rings; i++) {
            float f = (s.time * 0.012F + (float) i / rings) % 1F;
            pose.pushPose();
            pose.translate(0, f * top, 0);
            Fx.ring(c, pose, 0.42F, 0.04F, 28, s.time * 0.05F + i, Fx.alpha(CYAN, 0.7F * (1 - f) + 0.1F));
            pose.popPose();
        }
        pose.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(GravityLiftBlockEntity be) {
        return new AABB(be.getBlockPos()).expandTowards(0, GravityLiftBlockEntity.MAX_HEIGHT + 1, 0).inflate(0.5);
    }
}
