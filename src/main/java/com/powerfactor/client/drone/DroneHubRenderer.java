package com.powerfactor.client.drone;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.powerfactor.client.fx.Fx;
import com.powerfactor.drone.DroneHubBlockEntity;
import com.powerfactor.machine.MachineBlock;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Drone Hub: the landing pad's ring lights chase while it works, and a radar sweep spins over it. */
public class DroneHubRenderer implements BlockEntityRenderer<DroneHubBlockEntity, DroneHubRenderer.State> {
    public static class State extends BlockEntityRenderState {
        boolean on;
        float time;
    }

    public DroneHubRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(DroneHubBlockEntity be, State s, float partial, Vec3 camera, ModelFeatureRenderer.@Nullable CrumblingOverlay crumble) {
        BlockEntityRenderer.super.extractRenderState(be, s, partial, camera, crumble);
        s.on = be.getBlockState().getValueOrElse(MachineBlock.RUNNING, false);
        s.time = be.getLevel() == null ? 0 : (be.getLevel().getGameTime() % 72000L) + partial;
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector c, CameraRenderState camera) {
        pose.pushPose();
        pose.translate(0.5, 1.02, 0.5);
        Fx.ring(c, pose, 0.42F, 0.06F, 24, s.on ? s.time * 0.08F : 0, Fx.alpha(0xFF6FE3FF, s.on ? 0.8F : 0.25F));
        if (s.on) {
            // Radar sweep: a bright arm turning over the pad, and a pale dome of the survey.
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(s.time * 6));
            Fx.beam(c, pose, Vec3.ZERO, new Vec3(0.4, 0, 0), 0.04F, 0, Fx.alpha(0xFF7CFF8A, 0.9F), Fx.alpha(0xFF7CFF8A, 0.3F));
            pose.popPose();
            pose.translate(0, 0.25F + 0.05F * Mth.sin(s.time * 0.1F), 0);
            Fx.glow(c, pose, camera.orientation, 0.3F, Fx.alpha(0xFF7CFF8A, 0.5F));
        }
        pose.popPose();
    }
}
