package com.powerfactor.client.quantum;

import com.mojang.blaze3d.vertex.PoseStack;
import com.powerfactor.client.fx.Fx;
import com.powerfactor.quantum.SpaceElevatorBlockEntity;
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
 * The tether: a taut line climbing out of the anchor as segments are raised. Complete, it reaches
 * past the clouds; a climber pod rides it and pulses of beamed power run down to the ground.
 */
public class SpaceElevatorRenderer implements BlockEntityRenderer<SpaceElevatorBlockEntity, SpaceElevatorRenderer.State> {
    private static final int CYAN = 0xFF6FE3FF, VIOLET = 0xFFB18CFF;
    public static final float FULL = 300;

    public static class State extends BlockEntityRenderState {
        int segments;
        float time, top;
    }

    public SpaceElevatorRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(SpaceElevatorBlockEntity be, State s, float partial, Vec3 camera, ModelFeatureRenderer.@Nullable CrumblingOverlay crumble) {
        BlockEntityRenderer.super.extractRenderState(be, s, partial, camera, crumble);
        s.segments = be.clientSegments();
        s.time = be.getLevel() == null ? 0 : (be.getLevel().getGameTime() % 72000L) + partial;
        s.top = FULL * s.segments / SpaceElevatorBlockEntity.SEGMENTS;
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector c, CameraRenderState camera) {
        if (s.segments <= 0) return;
        pose.pushPose();
        pose.translate(0.5, 1.0, 0.5);
        boolean complete = s.segments >= SpaceElevatorBlockEntity.SEGMENTS;
        // A dark ribbon with a bright cyan sheath, so the line reads against the sky from far below.
        Fx.beam(c, pose, Vec3.ZERO, new Vec3(0, s.top, 0), 0.1F, 0, Fx.alpha(0xFFE6F4FF, 0.95F), Fx.alpha(CYAN, complete ? 0.45F : 0.2F));
        if (complete) Fx.beam(c, pose, Vec3.ZERO, new Vec3(0, s.top, 0), 0.3F, -s.time * 0.05F, Fx.alpha(CYAN, 0.18F), 0);
        if (complete) {
            // Power pulses running down the tether.
            for (int k = 0; k < 4; k++) {
                float f = 1 - ((s.time * 0.01F + k / 4F) % 1F);
                pose.pushPose();
                pose.translate(0, f * s.top, 0);
                Fx.glow(c, pose, camera.orientation, 0.5F, Fx.alpha(VIOLET, 0.8F));
                pose.popPose();
            }
            // The climber: up and down on a long cycle.
            float t = (s.time % 1200) / 1200F, f = t < 0.5F ? t * 2 : 2 - t * 2;
            pose.pushPose();
            pose.translate(0, 1.5 + f * (s.top - 3), 0);
            Fx.glow(c, pose, camera.orientation, 1.2F, Fx.alpha(CYAN, 0.9F));
            Fx.ring(c, pose, 0.45F, 0.08F, 24, s.time * 0.1F, Fx.alpha(0xFFFFFFFF, 0.8F));
            pose.popPose();
        } else {
            // The spool head at the tip, glowing as it climbs.
            pose.pushPose();
            pose.translate(0, s.top, 0);
            Fx.glow(c, pose, camera.orientation, 0.8F + 0.2F * Mth.sin(s.time * 0.3F), Fx.alpha(0xFFFFB347, 0.9F));
            pose.popPose();
        }
        // Anchor collar.
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(s.time * 2));
        Fx.ring(c, pose, 0.35F, 0.05F, 24, s.time * 0.05F, Fx.alpha(CYAN, 0.7F));
        pose.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(SpaceElevatorBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(2).expandTowards(0, FULL + 2, 0);
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 512;
    }
}
