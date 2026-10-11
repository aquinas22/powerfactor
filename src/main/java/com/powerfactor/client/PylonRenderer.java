package com.powerfactor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.powerfactor.grid.PylonBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Three sagging conductors (one per phase) from this pylon's crossarm to each linked pylon. */
public class PylonRenderer implements BlockEntityRenderer<PylonBlockEntity, PylonRenderer.State> {
    private static final float ARM_Y = 1.85F, ARM = 0.75F, THICK = 0.045F;
    private static final int SEGMENTS = 20;
    private static final int COLOR = 0xFF2E3236;

    public static class State extends BlockEntityRenderState {
        public final List<Vec3> spans = new ArrayList<>();
    }

    public PylonRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(PylonBlockEntity be, State state, float partialTicks, Vec3 camera,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, camera, breakProgress);
        state.spans.clear();
        BlockPos here = be.getBlockPos();
        for (BlockPos other : be.links()) {
            // Each span is drawn once, by its lower-ordered end.
            if (here.asLong() < other.asLong()) {
                state.spans.add(Vec3.atLowerCornerOf(other.subtract(here)));
            }
        }
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.spans.isEmpty()) {
            return;
        }
        int light = state.lightCoords;
        List<Vec3> spans = List.copyOf(state.spans);
        collector.submitCustomGeometry(pose, RenderTypes.textBackground(), (p, buf) -> {
            for (Vec3 d : spans) {
                double len = Math.sqrt(d.x * d.x + d.z * d.z);
                if (len < 0.01) {
                    continue;
                }
                // Crossarm runs perpendicular to the span.
                double px = -d.z / len, pz = d.x / len;
                double sag = 0.04 * Math.sqrt(d.lengthSqr()) + 0.2;
                for (int phase = -1; phase <= 1; phase++) {
                    double ox = 0.5 + px * ARM * phase, oz = 0.5 + pz * ARM * phase;
                    Vec3 prev = null;
                    for (int i = 0; i <= SEGMENTS; i++) {
                        double t = (double) i / SEGMENTS;
                        Vec3 pt = new Vec3(ox + d.x * t, ARM_Y + d.y * t - sag * 4 * t * (1 - t), oz + d.z * t);
                        if (prev != null) {
                            ribbon(buf, p, prev, pt, light);
                        }
                        prev = pt;
                    }
                }
            }
        });
    }

    /** A thin cross of two quads, so the line reads from any angle; both windings. */
    private static void ribbon(com.mojang.blaze3d.vertex.VertexConsumer buf, PoseStack.Pose p, Vec3 a, Vec3 b, int light) {
        float h = THICK;
        float[][] offs = {{0, h, 0}, {h, 0, 0}};
        for (float[] o : offs) {
            float ax = (float) a.x, ay = (float) a.y, az = (float) a.z, bx = (float) b.x, by = (float) b.y, bz = (float) b.z;
            buf.addVertex(p, ax - o[0], ay - o[1], az - o[2]).setColor(COLOR).setLight(light);
            buf.addVertex(p, bx - o[0], by - o[1], bz - o[2]).setColor(COLOR).setLight(light);
            buf.addVertex(p, bx + o[0], by + o[1], bz + o[2]).setColor(COLOR).setLight(light);
            buf.addVertex(p, ax + o[0], ay + o[1], az + o[2]).setColor(COLOR).setLight(light);
            buf.addVertex(p, ax + o[0], ay + o[1], az + o[2]).setColor(COLOR).setLight(light);
            buf.addVertex(p, bx + o[0], by + o[1], bz + o[2]).setColor(COLOR).setLight(light);
            buf.addVertex(p, bx - o[0], by - o[1], bz - o[2]).setColor(COLOR).setLight(light);
            buf.addVertex(p, ax - o[0], ay - o[1], az - o[2]).setColor(COLOR).setLight(light);
        }
    }

    @Override
    public AABB getRenderBoundingBox(PylonBlockEntity be) {
        return be.renderBox();
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }
}
