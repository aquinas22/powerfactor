package com.powerfactor.client.powernet;

import com.mojang.blaze3d.vertex.PoseStack;
import com.powerfactor.client.fx.Fx;
import com.powerfactor.powernet.PowerNodeBlock;
import com.powerfactor.powernet.PowerNodeBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Wireless power devices glow in their network's colour. Plugs and Points pulse in their crystal core with the
 * power through them, sparks running down each arm (inward for a Plug, outward for a Point); Cells show their
 * charge as a rising band of light; the Controller turns a hologram ring over itself.
 */
public class PowerNodeRenderer implements BlockEntityRenderer<PowerNodeBlockEntity, PowerNodeRenderer.State> {
    public static class State extends BlockEntityRenderState {
        PowerNodeBlockEntity.Kind kind = PowerNodeBlockEntity.Kind.PLUG;
        int color;
        boolean active;
        float time, load, fill;
        boolean[] arms = new boolean[6];
    }

    public PowerNodeRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(PowerNodeBlockEntity be, State s, float partial, Vec3 camera, ModelFeatureRenderer.@Nullable CrumblingOverlay crumble) {
        BlockEntityRenderer.super.extractRenderState(be, s, partial, camera, crumble);
        s.kind = be.kind();
        s.color = be.clientColor();
        s.active = be.clientActive();
        s.time = be.getLevel() == null ? 0 : (be.getLevel().getGameTime() % 72000L) + partial;
        s.load = Mth.clamp((float) Math.log10(1 + Math.abs(be.flow())) / 6F, 0, 1);
        s.fill = be.kind().cell() ? (float) be.energy() / be.kind().capacity : 0;
        var st = be.getBlockState();
        for (Direction d : Direction.values()) {
            s.arms[d.ordinal()] = st.hasProperty(PowerNodeBlock.SIDES.get(d)) && st.getValue(PowerNodeBlock.SIDES.get(d));
        }
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector c, CameraRenderState camera) {
        float pulse = 0.55F + 0.45F * Mth.sin(s.time * (0.08F + 0.25F * s.load));
        int col = 0xFF000000 | s.color;
        float bright = s.active ? 0.35F + 0.65F * Math.max(s.load, 0.25F) * pulse : 0.12F;
        switch (s.kind) {
            case PLUG, POINT -> {
                pose.pushPose();
                pose.translate(0.5, 0.5, 0.5);
                Fx.glow(c, pose, camera.orientation, 0.42F + 0.08F * pulse, Fx.alpha(col, bright));
                pose.popPose();
                if (!s.active) return;
                boolean in = s.kind == PowerNodeBlockEntity.Kind.PLUG;
                for (Direction d : Direction.values()) {
                    if (!s.arms[d.ordinal()]) continue;
                    // A spark running along the arm: toward the core for a Plug, away from it for a Point.
                    float t = (s.time * (0.05F + 0.1F * s.load) + d.ordinal() * 0.17F) % 1F;
                    float along = in ? 0.5F * (1 - t) : 0.5F * t;
                    pose.pushPose();
                    pose.translate(0.5 + d.getStepX() * along, 0.5 + d.getStepY() * along, 0.5 + d.getStepZ() * along);
                    Fx.glow(c, pose, camera.orientation, 0.14F, Fx.alpha(col, 0.5F + 0.5F * s.load));
                    pose.popPose();
                }
            }
            case CELL, DENSE_CELL, QUANTUM_CELL -> {
                // Charge level glows up the window on each side, topped by a bright line.
                float y0 = 3 / 16F, y1 = y0 + 10 / 16F * Math.max(0.02F, s.fill);
                float x0 = 5 / 16F, x1 = 11 / 16F, o = 0.502F;
                int fillCol = Fx.alpha(col, s.active ? 0.45F + 0.2F * pulse : 0.2F);
                for (int k = 0; k < 4; k++) {
                    pose.pushPose();
                    pose.translate(0.5, 0, 0.5);
                    pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(90 * k));
                    float a0 = x0 - 0.5F, a1 = x1 - 0.5F;
                    Fx.quad(c, pose, Fx.GLOW, new float[] {a0, y0, o}, new float[] {a1, y0, o}, new float[] {a1, y1, o}, new float[] {a0, y1, o}, fillCol);
                    Fx.quad(c, pose, Fx.BEAM, new float[] {a0, y1 - 0.02F, o + 0.001F}, new float[] {a1, y1 - 0.02F, o + 0.001F},
                            new float[] {a1, y1 + 0.02F, o + 0.001F}, new float[] {a0, y1 + 0.02F, o + 0.001F}, Fx.alpha(col, 0.9F));
                    pose.popPose();
                }
            }
            case CONTROLLER -> {
                pose.pushPose();
                pose.translate(0.5, 1.25 + 0.04 * Mth.sin(s.time * 0.06F), 0.5);
                Fx.ring(c, pose, 0.42F, 0.05F, 32, s.time * 0.04F, Fx.alpha(col, s.active ? 0.85F : 0.25F));
                Fx.ring(c, pose, 0.3F, 0.03F, 32, -s.time * 0.07F, Fx.alpha(col, s.active ? 0.6F : 0.2F));
                Fx.glow(c, pose, camera.orientation, 0.35F, Fx.alpha(col, bright));
                pose.popPose();
            }
        }
    }
}
