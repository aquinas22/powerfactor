package com.powerfactor.client.fission;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.powerfactor.client.fx.Fx;
import com.powerfactor.client.wash.WashPlantRenderer;
import com.powerfactor.fission.IsotopeCentrifugeBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Isotope Centrifuge: inside the glass column a slender rotor spins up to a blur, the heavier isotope
 * banding green toward the wall while the lighter one glows at the axis.
 */
public class CentrifugeRenderer implements BlockEntityRenderer<IsotopeCentrifugeBlockEntity, CentrifugeRenderer.State> {
    private static final java.util.Map<Long, float[]> SPIN = new java.util.HashMap<>();

    public static class State extends BlockEntityRenderState {
        float angle, speed, time;
    }

    public CentrifugeRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(IsotopeCentrifugeBlockEntity be, State s, float partial, Vec3 camera, ModelFeatureRenderer.@Nullable CrumblingOverlay crumble) {
        BlockEntityRenderer.super.extractRenderState(be, s, partial, camera, crumble);
        s.time = be.getLevel() == null ? 0 : (be.getLevel().getGameTime() % 72000L) + partial;
        float[] st = SPIN.computeIfAbsent(be.getBlockPos().asLong(), k -> new float[] {0, 0, s.time});
        float dt = Mth.clamp(s.time - st[2], 0, 5);
        st[1] += ((be.getBlockState().getValueOrElse(com.powerfactor.machine.MachineBlock.RUNNING, false) ? 60 : 0) - st[1]) * Math.min(1, dt * 0.015F);
        st[0] = (st[0] + st[1] * dt) % 360;
        st[2] = s.time;
        s.angle = st[0];
        s.speed = st[1];
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector c, CameraRenderState camera) {
        int light = s.lightCoords;
        float[] W = WashPlantRenderer.WHITE;
        float f = s.speed / 60F;
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(s.angle));
        c.submitCustomGeometry(pose, RenderTypes.entityCutout(WashPlantRenderer.TEX), (p, b) -> {
            WashPlantRenderer.box(b, p, -0.06F, 0.18F, -0.06F, 0.06F, 0.88F, 0.06F, W, 0xFFC8D0D4, light);
            for (int k = 0; k < 4; k++) {
                float y = 0.26F + k * 0.17F;
                WashPlantRenderer.box(b, p, -0.17F, y, -0.025F, 0.17F, y + 0.03F, 0.025F, W, 0xFF8FA6B5, light);
            }
        });
        pose.popPose();
        if (f > 0.05F) {
            for (int k = 0; k < 3; k++) {
                pose.pushPose();
                pose.translate(0, 0.32F + k * 0.2F, 0);
                Fx.band(c, pose, 0.2F, 0.08F, 20, s.time * 0.2F * f, Fx.alpha(0xFF7CFF8A, 0.45F * f));
                pose.popPose();
            }
            pose.translate(0, 0.53F, 0);
            Fx.glow(c, pose, camera.orientation, 0.35F, Fx.alpha(0xFFB8FFC8, 0.5F * f));
        }
        pose.popPose();
    }
}
