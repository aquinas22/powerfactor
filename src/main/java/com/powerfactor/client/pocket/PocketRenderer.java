package com.powerfactor.client.pocket;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.powerfactor.client.fx.Fx;
import com.powerfactor.pocket.PocketBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Inside the Dimensional Pocket's frame: rooms within rooms. Wireframe cubes nest inward, each turning a
 * little against the last and shading from cyan to violet as they recede, round a glowing core. Bigger
 * rooms nest deeper; a 13³ room adds orbiting motes, and a 21³ room a slow ring and twinkling stars.
 */
public class PocketRenderer implements BlockEntityRenderer<PocketBlockEntity, PocketRenderer.State> {
    private static final int CYAN = 0xFF6FE3FF, VIOLET = 0xFFB18CFF;

    public static class State extends BlockEntityRenderState {
        int tier = 1;
        boolean linked;
        float time;
    }

    public PocketRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(PocketBlockEntity be, State s, float partial, Vec3 camera, ModelFeatureRenderer.@Nullable CrumblingOverlay crumble) {
        BlockEntityRenderer.super.extractRenderState(be, s, partial, camera, crumble);
        s.tier = be.tier();
        s.linked = be.room() > 0;
        s.time = be.getLevel() == null ? 0 : (be.getLevel().getGameTime() % 72000L) + partial;
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector c, CameraRenderState camera) {
        float t = s.time;
        int depth = s.tier + 1;
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        for (int k = 0; k < depth; k++) {
            float f = (float) k / Math.max(1, depth - 1);
            float half = 0.36F * (float) Math.pow(0.68, k);
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(t * (0.6F + k * 0.5F) * (k % 2 == 0 ? 1 : -1)));
            pose.mulPose(Axis.XP.rotationDegrees(k * 9F + Mth.sin(t * 0.02F + k) * 6F));
            int col = Fx.mix(CYAN, VIOLET, f);
            wire(c, pose, half, t, Fx.alpha(col, s.linked ? 0.95F - 0.15F * f : 0.45F));
            pose.popPose();
        }
        float pulse = 0.5F + 0.5F * Mth.sin(t * 0.12F);
        Fx.glow(c, pose, camera.orientation, 0.22F + 0.06F * pulse + 0.04F * s.tier, Fx.alpha(0xFFE8DCFF, 0.75F + 0.2F * pulse));
        Fx.glow(c, pose, camera.orientation, 0.7F + 0.1F * s.tier, Fx.alpha(VIOLET, 0.18F + 0.08F * pulse));
        if (s.tier >= 2) {
            // Motes orbiting through the nested rooms.
            for (int k = 0; k < 4; k++) {
                float a = t * 0.05F + k * Mth.HALF_PI;
                pose.pushPose();
                pose.translate(Mth.cos(a) * 0.3F, Mth.sin(a * 2 + k) * 0.18F, Mth.sin(a) * 0.3F);
                Fx.glow(c, pose, camera.orientation, 0.09F, Fx.alpha(CYAN, 0.9F));
                pose.popPose();
            }
        }
        if (s.tier >= 3) {
            pose.pushPose();
            pose.mulPose(Axis.XP.rotationDegrees(20 * Mth.sin(t * 0.01F)));
            Fx.ring(c, pose, 0.33F, 0.04F, 32, t * 0.02F, Fx.alpha(VIOLET, 0.7F));
            pose.popPose();
            for (int k = 0; k < 8; k++) {
                float tw = Mth.sin(t * 0.2F + k * 2.1F);
                if (tw < 0.3F) continue;
                pose.pushPose();
                pose.translate(Mth.sin(k * 3.7F) * 0.3F, Mth.cos(k * 2.3F) * 0.3F, Mth.sin(k * 5.1F) * 0.3F);
                Fx.glow(c, pose, camera.orientation, 0.05F, Fx.alpha(0xFFFFFFFF, tw));
                pose.popPose();
            }
        }
        pose.popPose();
    }

    /** The twelve edges of a cube of half-size h. */
    private static void wire(SubmitNodeCollector c, PoseStack pose, float h, float t, int argb) {
        float w = 0.045F;
        for (int axis = 0; axis < 3; axis++) {
            for (int i = 0; i < 4; i++) {
                float u = (i & 1) == 0 ? -h : h, v = (i & 2) == 0 ? -h : h;
                Vec3 a, b;
                switch (axis) {
                    case 0 -> { a = new Vec3(-h, u, v); b = new Vec3(h, u, v); }
                    case 1 -> { a = new Vec3(u, -h, v); b = new Vec3(u, h, v); }
                    default -> { a = new Vec3(u, v, -h); b = new Vec3(u, v, h); }
                }
                Fx.beam(c, pose, a, b, w, t * 0.03F, argb, Fx.alpha(argb, 0.15F));
            }
        }
    }
}
