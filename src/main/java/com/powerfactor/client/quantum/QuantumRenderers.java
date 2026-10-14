package com.powerfactor.client.quantum;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.powerfactor.client.fx.Fx;
import com.powerfactor.quantum.ColliderBlockEntity;
import com.powerfactor.quantum.ZeroPointBlockEntity;
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

/** The two flagship quantum machines' light shows. */
public final class QuantumRenderers {
    static final int VIOLET = 0xFFB18CFF, CYAN = 0xFF6FE3FF, ORANGE = 0xFFFFB347, HOT = 0xFFFFF0D0, WHITE = 0xFFFFFFFF;

    // ------------------------------------------------------------------ particle collider

    public static class ColliderState extends BlockEntityRenderState {
        float time, phase, progress;
        int flash;
        boolean running;
    }

    public static class Collider implements BlockEntityRenderer<ColliderBlockEntity, ColliderState> {
        public Collider(BlockEntityRendererProvider.Context context) {}

        @Override
        public ColliderState createRenderState() {
            return new ColliderState();
        }

        @Override
        public void extractRenderState(ColliderBlockEntity be, ColliderState s, float partial, Vec3 camera,
                ModelFeatureRenderer.@Nullable CrumblingOverlay crumble) {
            BlockEntityRenderer.super.extractRenderState(be, s, partial, camera, crumble);
            s.time = be.getLevel() == null ? 0 : (be.getLevel().getGameTime() % 72000L) + partial;
            s.phase = be.industrialPhase(partial);
            s.progress = be.progress();
            s.flash = be.flash();
            s.running = be.getBlockState().getValue(com.powerfactor.machine.MachineBlock.RUNNING);
        }

        @Override
        public void submit(ColliderState s, PoseStack pose, SubmitNodeCollector c, CameraRenderState camera) {
            float t = s.time;
            float power = s.running ? 0.55F + 0.45F * s.progress : 0.3F;
            pose.pushPose();
            pose.translate(0.5, 1.25, 0.5);
            // The accelerator ring floats over the machine: a steel-blue track and a hot inner beam line.
            Fx.ring(c, pose, 1.25F, 0.16F, 72, t * 0.01F, Fx.alpha(0xFF5D7A8A, 0.7F));
            Fx.ring(c, pose, 1.25F, 0.04F, 72, -t * (0.05F + 0.4F * s.progress), Fx.alpha(CYAN, power));
            // Dipole magnets around the track glow orange as the energy banks.
            for (int k = 0; k < 8; k++) {
                pose.pushPose();
                double a = k * Math.PI / 4;
                pose.translate(Math.cos(a) * 1.25, 0, Math.sin(a) * 1.25);
                Fx.cube(c, pose, Fx.GLOW, 0.14F, Fx.alpha(k < s.progress * 8 ? ORANGE : 0xFF403020, 0.9F));
                pose.popPose();
            }
            if (s.running) {
                // Two counter-rotating bunches race around, faster and brighter as they approach collision energy.
                float speed = 0.15F + 1.6F * s.progress * s.progress;
                for (int b = 0; b < 2; b++) {
                    for (int tail = 0; tail < 6; tail++) {
                        double a = (b == 0 ? 1 : -1) * (t * speed - tail * 0.09) + b * Math.PI;
                        pose.pushPose();
                        pose.translate(Math.cos(a) * 1.25, 0, Math.sin(a) * 1.25);
                        Fx.glow(c, pose, camera.orientation, (0.42F - tail * 0.05F) * (0.7F + 0.6F * s.progress),
                                Fx.alpha(b == 0 ? CYAN : VIOLET, 1 - tail * 0.15F));
                        pose.popPose();
                    }
                }
            }
            if (s.flash > 0) {
                float f = s.flash / 20F;
                Fx.glow(c, pose, camera.orientation, 4.5F * (1.2F - f), Fx.alpha(WHITE, f));
                Fx.ring(c, pose, 0.4F + 2.5F * (1 - f), 0.25F * f, 48, 0, Fx.alpha(VIOLET, f));
            }
            Fx.glow(c, pose, camera.orientation, 0.45F, Fx.alpha(VIOLET, 0.4F * power));
            pose.popPose();
        }

        @Override
        public AABB getRenderBoundingBox(ColliderBlockEntity be) {
            return new AABB(be.getBlockPos()).inflate(1.6).expandTowards(0, 1.2, 0);
        }
    }

    // ------------------------------------------------------------------ zero-point generator

    public static class ZeroState extends BlockEntityRenderState {
        float time, stability, phase;
        long seed;
    }

    public static class ZeroPoint implements BlockEntityRenderer<ZeroPointBlockEntity, ZeroState> {
        public ZeroPoint(BlockEntityRendererProvider.Context context) {}

        @Override
        public ZeroState createRenderState() {
            return new ZeroState();
        }

        @Override
        public void extractRenderState(ZeroPointBlockEntity be, ZeroState s, float partial, Vec3 camera,
                ModelFeatureRenderer.@Nullable CrumblingOverlay crumble) {
            BlockEntityRenderer.super.extractRenderState(be, s, partial, camera, crumble);
            s.time = be.getLevel() == null ? 0 : (be.getLevel().getGameTime() % 72000L) + partial;
            s.stability = be.stability();
            s.phase = be.industrialPhase(partial);
            s.seed = be.getBlockPos().asLong();
        }

        @Override
        public void submit(ZeroState s, PoseStack pose, SubmitNodeCollector c, CameraRenderState camera) {
            float t = s.time, st = s.stability;
            pose.pushPose();
            pose.translate(0.5, 1.7 + 0.05 * Mth.sin(t * 0.04F), 0.5);
            float core = 0.12F + 0.3F * st;
            // Lensing halo and inner glow.
            if (st > 0.02F) {
                Fx.glow(c, pose, camera.orientation, 3.4F * core / 0.42F * st + 0.4F, Fx.alpha(VIOLET, 0.5F * st));
                Fx.glow(c, pose, camera.orientation, 1.6F * st + 0.2F, Fx.alpha(ORANGE, 0.45F * st));
            }
            // Gyroscope: three rings locked to different axes, spinning up with stability.
            for (int k = 0; k < 3; k++) {
                pose.pushPose();
                pose.mulPose(Axis.YP.rotationDegrees(s.phase * 40 * (k + 1) + k * 60));
                pose.mulPose(Axis.XP.rotationDegrees(60 * k + s.phase * 13));
                Fx.ring(c, pose, 0.75F - k * 0.13F, 0.05F, 56, t * 0.05F * (k + 1), Fx.alpha(Fx.mix(CYAN, VIOLET, k / 2F), 0.35F + 0.6F * st));
                pose.popPose();
            }
            // Accretion disk.
            pose.pushPose();
            pose.mulPose(Axis.XP.rotationDegrees(14));
            pose.mulPose(Axis.YP.rotationDegrees(t * 6 * (0.2F + st)));
            for (int k = 0; k < 3; k++) {
                Fx.ring(c, pose, core * (1.4F + k * 0.45F), core * (0.28F + 0.08F * k), 56, t * 0.06F, Fx.alpha(Fx.mix(HOT, ORANGE, k / 2F), 0.9F * st));
            }
            pose.popPose();
            // Photon ring and the void itself.
            pose.pushPose();
            pose.mulPose(camera.orientation);
            pose.mulPose(Axis.XP.rotationDegrees(90));
            Fx.ring(c, pose, core * 1.08F, core * 0.12F, 48, -t * 0.1F, Fx.alpha(HOT, 0.4F + 0.6F * st));
            pose.popPose();
            com.powerfactor.client.weapon.WeaponRenderers.sphereFor(c, pose, core);
            pose.popPose();
            // Lightning grounds itself into the four corner electrodes.
            if (st > 0.3F) {
                long tick = (long) t;
                RandomSource r = RandomSource.create(tick / 2 * 31 + s.seed);
                Vec3 from = new Vec3(0.5, 1.7, 0.5);
                List<Vec3[]> bolts = new ArrayList<>();
                for (int k = 0; k < 4; k++) {
                    if (r.nextFloat() > 0.35F + 0.5F * st) continue;
                    Vec3 to = new Vec3(k % 2 == 0 ? 0.08 : 0.92, 1.0, k < 2 ? 0.08 : 0.92);
                    bolts.add(new Vec3[] {from, to});
                }
                long seed = r.nextLong();
                c.submitCustomGeometry(pose, RenderTypes.lightning(), (p, buf) -> {
                    RandomSource jitter = RandomSource.create(seed);
                    for (Vec3[] b : bolts) {
                        Vec3 prev = b[0];
                        for (int i = 1; i <= 8; i++) {
                            Vec3 next = b[0].lerp(b[1], i / 8.0);
                            if (i < 8) next = next.add((jitter.nextDouble() - .5) * 0.18, (jitter.nextDouble() - .5) * 0.12, (jitter.nextDouble() - .5) * 0.18);
                            quad(buf, p, prev, next, 0.025F, 0.75F, 0.6F, 1F, 0.8F);
                            prev = next;
                        }
                    }
                });
            }
        }

        private static void quad(com.mojang.blaze3d.vertex.VertexConsumer buf, PoseStack.Pose p, Vec3 a, Vec3 b, float w, float r, float g, float bl, float al) {
            Vec3 d = b.subtract(a).normalize();
            Vec3 u = Math.abs(d.y) < 0.9 ? d.cross(new Vec3(0, 1, 0)).normalize() : d.cross(new Vec3(1, 0, 0)).normalize();
            Vec3 v = d.cross(u).normalize();
            for (Vec3 off : new Vec3[] {u.scale(w), v.scale(w)}) {
                for (Vec3 q : new Vec3[] {a.subtract(off), b.subtract(off), b.add(off), a.add(off)}) {
                    buf.addVertex(p, (float) q.x, (float) q.y, (float) q.z).setColor(r, g, bl, al);
                }
            }
        }

        @Override
        public AABB getRenderBoundingBox(ZeroPointBlockEntity be) {
            return new AABB(be.getBlockPos()).inflate(2).expandTowards(0, 2, 0);
        }
    }

    private QuantumRenderers() {}
}
