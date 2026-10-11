package com.powerfactor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.powerfactor.device.TeslaCoilBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Lightning from the coil's toroid to whatever it struck (and on to the chained target), re-drawn in
 * a fresh jagged path every tick; and, while charged, little purple streamers crackling off the
 * toroid into the air.
 */
public class TeslaCoilRenderer implements BlockEntityRenderer<TeslaCoilBlockEntity, TeslaCoilRenderer.State> {
    private record Arc(Vec3 from, Vec3 to, float strength, long seed) {}

    public static class State extends BlockEntityRenderState {
        public final List<Arc> arcs = new ArrayList<>();
    }

    public TeslaCoilRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(TeslaCoilBlockEntity be, State state, float partialTicks, Vec3 camera,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, camera, breakProgress);
        state.arcs.clear();
        if (be.getLevel() == null) {
            return;
        }
        long tick = be.getLevel().getGameTime();
        Vec3 origin = Vec3.atLowerCornerOf(be.getBlockPos());
        Vec3 top = new Vec3(0.5, TeslaCoilBlockEntity.TOP, 0.5);
        for (int[] bolt : be.bolts()) {
            Entity to = be.getLevel().getEntity(bolt[1]);
            if (to == null) {
                continue;
            }
            Vec3 start = top;
            if (bolt[0] >= 0) {
                Entity from = be.getLevel().getEntity(bolt[0]);
                if (from == null) {
                    continue;
                }
                start = from.getPosition(partialTicks).add(0, from.getBbHeight() * 0.6, 0).subtract(origin);
            }
            Vec3 end = to.getPosition(partialTicks).add(0, to.getBbHeight() * 0.6, 0).subtract(origin);
            state.arcs.add(new Arc(start, end, 1F - bolt[2] / 7F, tick * 31 + bolt[1]));
        }
        if (be.charged()) {
            RandomSource r = RandomSource.create(tick / 2 * 977 + be.getBlockPos().asLong());
            int n = 1 + r.nextInt(3);
            for (int i = 0; i < n; i++) {
                double a = r.nextDouble() * Math.PI * 2, len = 0.45 + r.nextDouble() * 0.8;
                Vec3 from = top.add(Math.cos(a) * 0.4, -0.05, Math.sin(a) * 0.4);
                Vec3 to = from.add(Math.cos(a) * len, (r.nextDouble() - 0.3) * len, Math.sin(a) * len);
                state.arcs.add(new Arc(from, to, 0.45F, r.nextLong()));
            }
        }
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.arcs.isEmpty()) {
            return;
        }
        List<Arc> arcs = List.copyOf(state.arcs);
        collector.submitCustomGeometry(pose, RenderTypes.lightning(), (p, buf) -> {
            for (Arc arc : arcs) {
                List<Vec3> path = path(arc);
                // A wide violet glow, then a thin white-hot core.
                strip(buf, p, path, 0.16F * arc.strength(), 0.55F, 0.35F, 1.0F, 0.3F * arc.strength());
                strip(buf, p, path, 0.035F + 0.025F * arc.strength(), 0.95F, 0.9F, 1.0F, 0.9F * arc.strength());
            }
        });
    }

    /** Midpoint displacement: a jagged path from one end to the other, with a couple of forks of its own. */
    private static List<Vec3> path(Arc arc) {
        RandomSource r = RandomSource.create(arc.seed());
        List<Vec3> pts = new ArrayList<>();
        pts.add(arc.from());
        pts.add(arc.to());
        double spread = arc.from().distanceTo(arc.to()) * 0.18;
        for (int pass = 0; pass < 4; pass++) {
            List<Vec3> next = new ArrayList<>();
            for (int i = 0; i + 1 < pts.size(); i++) {
                Vec3 a = pts.get(i), b = pts.get(i + 1);
                next.add(a);
                next.add(a.add(b).scale(0.5).add((r.nextDouble() - 0.5) * spread, (r.nextDouble() - 0.5) * spread, (r.nextDouble() - 0.5) * spread));
            }
            next.add(pts.getLast());
            pts = next;
            spread *= 0.55;
        }
        return pts;
    }

    /** Each segment as two crossed quads, so the bolt has width from every angle. */
    private static void strip(VertexConsumer buf, PoseStack.Pose p, List<Vec3> pts, float w, float r, float g, float b, float a) {
        for (int i = 0; i + 1 < pts.size(); i++) {
            Vec3 s = pts.get(i), e = pts.get(i + 1);
            Vec3 d = e.subtract(s).normalize();
            Vec3 u = Math.abs(d.y) < 0.9 ? d.cross(new Vec3(0, 1, 0)).normalize() : d.cross(new Vec3(1, 0, 0)).normalize();
            Vec3 v = d.cross(u).normalize();
            for (Vec3 off : new Vec3[] {u.scale(w), v.scale(w)}) {
                vertex(buf, p, s.subtract(off), r, g, b, a);
                vertex(buf, p, e.subtract(off), r, g, b, a);
                vertex(buf, p, e.add(off), r, g, b, a);
                vertex(buf, p, s.add(off), r, g, b, a);
            }
        }
    }

    private static void vertex(VertexConsumer buf, PoseStack.Pose p, Vec3 v, float r, float g, float b, float a) {
        buf.addVertex(p, (float) v.x, (float) v.y, (float) v.z).setColor(r, g, b, a);
    }

    @Override
    public AABB getRenderBoundingBox(TeslaCoilBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(TeslaCoilBlockEntity.RANGE + 2);
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }
}
