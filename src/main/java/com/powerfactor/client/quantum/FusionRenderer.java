package com.powerfactor.client.quantum;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.powerfactor.client.fx.Fx;
import com.powerfactor.machine.MachineBlock;
import com.powerfactor.multiblock.CasingBlock;
import com.powerfactor.quantum.FusionReactorBlockEntity;
import com.powerfactor.registry.ModQuantum;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The tokamak at work, seen through the viewport: a plasma torus of twisted filament rings that
 * spins faster and shifts from red to white-blue as it heats, field magnets glowing around it, and
 * arcs leaping from the magnets into the plasma during ignition.
 */
public class FusionRenderer implements BlockEntityRenderer<FusionReactorBlockEntity, FusionRenderer.State> {
    public static class State extends BlockEntityRenderState {
        boolean formed;
        int plasmaState;
        float temp, time;
        Vec3 centre = Vec3.ZERO;
        final List<Vec3> magnets = new ArrayList<>();
        long seed;
    }

    public FusionRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(FusionReactorBlockEntity be, State s, float partial, Vec3 camera, ModelFeatureRenderer.@Nullable CrumblingOverlay crumble) {
        BlockEntityRenderer.super.extractRenderState(be, s, partial, camera, crumble);
        s.formed = be.getBlockState().getValue(CasingBlock.FORMED);
        s.temp = be.clientTemp();
        s.plasmaState = be.clientState();
        s.time = be.getLevel() == null ? 0 : (be.getLevel().getGameTime() % 72000L) + partial;
        s.centre = be.chamberOffset();
        s.seed = be.getBlockPos().asLong();
        s.magnets.clear();
        var facing = be.getBlockState().getValue(MachineBlock.FACING);
        for (var part : FusionReactorBlockEntity.STRUCTURE.parts()) {
            if (part.block().get() != ModQuantum.FUSION_MAGNET.get()) continue;
            var at = FusionReactorBlockEntity.STRUCTURE.worldPos(net.minecraft.core.BlockPos.ZERO, facing, part);
            s.magnets.add(new Vec3(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5));
        }
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector c, CameraRenderState camera) {
        if (!s.formed || s.temp <= 0.01F) return;
        float t = s.temp;
        int col = FusionScreen.plasma(t);
        boolean burning = s.plasmaState == FusionReactorBlockEntity.STATE_BURNING;
        pose.pushPose();
        pose.translate(s.centre.x, s.centre.y, s.centre.z);
        // Torus: filament rings tilted around the major circle, spinning with heat. Each filament is a
        // wide coloured halo with a thin white-hot core, so the plasma stays vivid even at full heat.
        int halo = FusionScreen.halo(t);
        float spin = s.time * (1.5F + 10 * t);
        for (int k = 0; k < 5; k++) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(spin + k * 72));
            pose.mulPose(Axis.XP.rotationDegrees(Mth.sin(s.time * 0.05F + k) * 7));
            float r = 1.0F + 0.05F * Mth.sin(s.time * 0.2F + k * 1.3F);
            Fx.ring(c, pose, r, 0.12F + 0.3F * t, 48, s.time * (0.05F + 0.1F * t) + k, Fx.alpha(halo, 0.45F + 0.5F * t));
            Fx.ring(c, pose, r, 0.03F + 0.04F * t, 48, -s.time * 0.1F, Fx.alpha(col, 0.6F + 0.4F * t));
            pose.popPose();
        }
        Fx.ring(c, pose, 1.0F, 0.06F, 48, -s.time * 0.2F, Fx.alpha(0xFFFFFFFF, burning ? 0.9F * t : 0.3F * t));
        Fx.glow(c, pose, camera.orientation, 1.8F + 1.6F * t, Fx.alpha(halo, 0.35F + 0.4F * t));
        pose.popPose();
        // Magnets glow in sequence, like a field sweeping around the ring.
        for (int i = 0; i < s.magnets.size(); i++) {
            Vec3 m = s.magnets.get(i);
            float wave = 0.5F + 0.5F * Mth.sin(s.time * (0.1F + 0.3F * t) - i * 0.6F);
            pose.pushPose();
            pose.translate(m.x, m.y, m.z);
            Fx.glow(c, pose, camera.orientation, 0.35F + 0.3F * t, Fx.alpha(0xFF48C4F0, (0.2F + 0.6F * t) * (0.5F + 0.5F * wave)));
            pose.popPose();
        }
        if (s.plasmaState == FusionReactorBlockEntity.STATE_IGNITING) {
            // Ignition arcs from random magnets into the plasma.
            RandomSource r = RandomSource.create((long) s.time / 2 * 31 + s.seed);
            List<Vec3[]> bolts = new ArrayList<>();
            for (Vec3 m : s.magnets) if (r.nextFloat() < 0.25F) bolts.add(new Vec3[] {m, s.centre.add(m.subtract(s.centre).normalize().scale(1.0))});
            long seed = r.nextLong();
            c.submitCustomGeometry(pose, RenderTypes.lightning(), (pp, buf) -> {
                RandomSource j = RandomSource.create(seed);
                for (Vec3[] b : bolts) {
                    Vec3 prev = b[0];
                    for (int i = 1; i <= 5; i++) {
                        Vec3 next = b[0].lerp(b[1], i / 5.0);
                        if (i < 5) next = next.add((j.nextDouble() - .5) * .2, (j.nextDouble() - .5) * .2, (j.nextDouble() - .5) * .2);
                        seg(buf, pp, prev, next, 0.02F);
                        prev = next;
                    }
                }
            });
        }
    }

    private static void seg(com.mojang.blaze3d.vertex.VertexConsumer buf, PoseStack.Pose p, Vec3 a, Vec3 b, float w) {
        Vec3 d = b.subtract(a).normalize();
        Vec3 u = Math.abs(d.y) < 0.9 ? d.cross(new Vec3(0, 1, 0)).normalize() : d.cross(new Vec3(1, 0, 0)).normalize();
        Vec3 v = d.cross(u).normalize();
        for (Vec3 off : new Vec3[] {u.scale(w), v.scale(w)}) {
            for (Vec3 q : new Vec3[] {a.subtract(off), b.subtract(off), b.add(off), a.add(off)}) {
                buf.addVertex(p, (float) q.x, (float) q.y, (float) q.z).setColor(0.75F, 0.9F, 1F, 0.85F);
            }
        }
    }

    @Override
    public AABB getRenderBoundingBox(FusionReactorBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(5);
    }
}
