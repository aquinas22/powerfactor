package com.powerfactor.client.tesseract;

import com.mojang.blaze3d.vertex.PoseStack;
import com.powerfactor.client.fx.Fx;
import com.powerfactor.tesseract.TesseractBlockEntity;
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
 * A hypercube turning through the fourth dimension inside the frame: sixteen vertices rotated in the
 * xw and yz planes and projected to 3D, the inner cube swelling and turning inside out as w rotates.
 * Edges glow violet when linked, and in each active channel's colour when sending or receiving.
 */
public class TesseractRenderer implements BlockEntityRenderer<TesseractBlockEntity, TesseractRenderer.State> {
    public static class State extends BlockEntityRenderState {
        float time;
        int modes;
        boolean linked;
    }

    public TesseractRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(TesseractBlockEntity be, State s, float partial, Vec3 camera, ModelFeatureRenderer.@Nullable CrumblingOverlay crumble) {
        BlockEntityRenderer.super.extractRenderState(be, s, partial, camera, crumble);
        s.time = be.getLevel() == null ? 0 : (be.getLevel().getGameTime() % 72000L) + partial;
        s.modes = be.clientModes();
        s.linked = be.clientLinked();
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector c, CameraRenderState camera) {
        double t = s.time / 20.0;
        Vec3[] v = new Vec3[16];
        double[] w = new double[16];
        for (int i = 0; i < 16; i++) {
            double x = (i & 1) == 0 ? -1 : 1, y = (i & 2) == 0 ? -1 : 1, z = (i & 4) == 0 ? -1 : 1, ww = (i & 8) == 0 ? -1 : 1;
            double a = t * 0.6, b = t * 0.4;
            double x2 = x * Math.cos(a) - ww * Math.sin(a), w2 = x * Math.sin(a) + ww * Math.cos(a);
            double y2 = y * Math.cos(b) - z * Math.sin(b), z2 = y * Math.sin(b) + z * Math.cos(b);
            double k = 0.3 * 2.4 / (3 - w2);   // outer cube ~0.72 across, filling the frame
            v[i] = new Vec3(0.5 + x2 * k, 0.5 + y2 * k, 0.5 + z2 * k);
            w[i] = w2;
        }
        int[] chan = {0xFFFFC94A, 0xFF4FA8FF, 0xFFFF5A4A};
        int active = -1;
        for (int ch = 0; ch < 3; ch++) if (((s.modes >> (ch * 2)) & 3) != 0) active = ch;
        int edge = s.linked ? 0xFF6FE3FF : 0xFF3F6A78, fourth = s.linked ? 0xFFB18CFF : 0xFF5A4A80;
        for (int i = 0; i < 16; i++) {
            for (int bit = 0; bit < 4; bit++) {
                int j = i ^ (1 << bit);
                if (j < i) continue;
                int col = bit == 3 ? fourth : (active >= 0 && s.linked && (i + j) % 5 == 0 ? chan[active] : edge);
                float depth = (float) (0.55 + 0.45 * (w[i] + w[j] + 2) / 4);
                Fx.beam(c, pose, v[i], v[j], 0.03F, (float) -t, Fx.alpha(col, depth), Fx.alpha(col, depth * 0.35F));
            }
        }
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        Fx.glow(c, pose, camera.orientation, 0.45F + 0.08F * Mth.sin(s.time * 0.1F), Fx.alpha(s.linked ? 0xFFB18CFF : 0xFF4A3A70, 0.7F));
        pose.popPose();
    }
}
