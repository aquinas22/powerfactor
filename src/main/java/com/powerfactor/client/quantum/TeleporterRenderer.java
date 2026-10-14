package com.powerfactor.client.quantum;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.powerfactor.client.fx.Fx;
import com.powerfactor.quantum.TeleporterBlockEntity;
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
 * The pad's field: an idle halo when linked, rings climbing a column of light while someone charges,
 * and a white-violet burst when a jump goes through.
 */
public class TeleporterRenderer implements BlockEntityRenderer<TeleporterBlockEntity, TeleporterRenderer.State> {
    private static final int VIOLET = 0xFFB18CFF, CYAN = 0xFF6FE3FF, AMBER = 0xFFFFB347;

    public static class State extends BlockEntityRenderState {
        boolean linked;
        float charge, time, flash;
    }

    public TeleporterRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(TeleporterBlockEntity be, State s, float partial, Vec3 camera, ModelFeatureRenderer.@Nullable CrumblingOverlay crumble) {
        BlockEntityRenderer.super.extractRenderState(be, s, partial, camera, crumble);
        s.linked = be.clientLinked();
        s.charge = be.clientCharge();
        s.time = be.getLevel() == null ? 0 : (be.getLevel().getGameTime() % 72000L) + partial;
        float since = be.getLevel() == null ? 99 : be.getLevel().getGameTime() + partial - be.clientFlash();
        s.flash = Mth.clamp(1 - since / 16F, 0, 1);
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector c, CameraRenderState camera) {
        pose.pushPose();
        pose.translate(0.5, 0.27, 0.5);
        if (!s.linked) {
            Fx.glow(c, pose, camera.orientation, 0.5F, Fx.alpha(AMBER, 0.25F + 0.1F * Mth.sin(s.time * 0.1F)));
            pose.popPose();
            return;
        }
        float k = s.charge;
        // Idle halo, spinning faster as the charge builds.
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(s.time * (2 + 18 * k)));
        Fx.ring(c, pose, 0.42F, 0.05F + 0.04F * k, 32, s.time * 0.05F, Fx.alpha(VIOLET, 0.45F + 0.55F * k));
        Fx.ring(c, pose, 0.3F, 0.03F, 24, -s.time * 0.08F, Fx.alpha(CYAN, 0.3F + 0.6F * k));
        pose.popPose();
        Fx.glow(c, pose, camera.orientation, 0.6F + 0.8F * k, Fx.alpha(VIOLET, 0.3F + 0.5F * k));
        if (k > 0) {
            // Rings climb the column, more of them and faster as the jump nears.
            int rings = 2 + Math.round(4 * k);
            for (int i = 0; i < rings; i++) {
                float f = (s.time * (0.02F + 0.06F * k) + (float) i / rings) % 1F;
                pose.pushPose();
                pose.translate(0, f * 2.1F, 0);
                Fx.ring(c, pose, 0.45F - 0.1F * f, 0.04F, 28, 0, Fx.alpha(f > 0.5F ? CYAN : VIOLET, k * (1 - f)));
                pose.popPose();
            }
            Fx.beam(c, pose, Vec3.ZERO, new Vec3(0, 2.2, 0), 0.05F + 0.35F * k * k, -s.time * 0.3F, Fx.alpha(0xFFFFFFFF, k * 0.7F),
                    Fx.alpha(VIOLET, k * 0.5F));
        }
        if (s.flash > 0) {
            pose.pushPose();
            pose.translate(0, 0.9, 0);
            Fx.glow(c, pose, camera.orientation, 3.5F * (1.1F - s.flash), Fx.alpha(0xFFFFFFFF, s.flash));
            pose.popPose();
            Fx.ring(c, pose, 0.4F + 2.2F * (1 - s.flash), 0.18F * s.flash, 48, 0, Fx.alpha(CYAN, s.flash));
        }
        pose.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(TeleporterBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(2.5).expandTowards(0, 2, 0);
    }
}
