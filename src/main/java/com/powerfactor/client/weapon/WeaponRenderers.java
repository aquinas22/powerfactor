package com.powerfactor.client.weapon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.powerfactor.client.fx.Fx;
import com.powerfactor.weapon.PlasmaBoltEntity;
import com.powerfactor.weapon.SingularityEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Projectile renderers for the arsenal: glow sprites, rings and a window into the void. */
public final class WeaponRenderers {
    static final int PLASMA = 0xFF7CFF9A, VIOLET = 0xFFB18CFF, ORANGE = 0xFFFFA040, HOT = 0xFFFFF0D0;

    // ------------------------------------------------------------------ plasma bolt

    public static class PlasmaState extends EntityRenderState {
        Vec3 velocity = Vec3.ZERO;
    }

    public static class Plasma extends EntityRenderer<PlasmaBoltEntity, PlasmaState> {
        public Plasma(EntityRendererProvider.Context context) {
            super(context);
        }

        @Override
        public PlasmaState createRenderState() {
            return new PlasmaState();
        }

        @Override
        public void extractRenderState(PlasmaBoltEntity e, PlasmaState s, float partial) {
            super.extractRenderState(e, s, partial);
            s.velocity = e.getDeltaMovement();
        }

        @Override
        public void submit(PlasmaState s, PoseStack pose, SubmitNodeCollector c, CameraRenderState camera) {
            float t = s.ageInTicks;
            pose.pushPose();
            pose.translate(0, 0.15, 0);
            // A comet: a hot core, a green halo and a tapering trail behind it.
            Vec3 back = s.velocity.lengthSqr() < 1e-6 ? Vec3.ZERO : s.velocity.normalize().scale(-1.6);
            Fx.beam(c, pose, Vec3.ZERO, back, 0.12F, t * 0.3F, Fx.alpha(PLASMA, 0.8F), Fx.alpha(PLASMA, 0.25F));
            Fx.glow(c, pose, camera.orientation, 0.9F + 0.15F * Mth.sin(t * 2.7F), Fx.alpha(PLASMA, 0.85F));
            Fx.glow(c, pose, camera.orientation, 0.38F, Fx.alpha(HOT, 1F));
            Fx.sprite(c, pose, camera.orientation, Fx.SPARK, 0.6F, Fx.alpha(0xFFE8FFE8, 0.6F + 0.4F * Mth.sin(t * 5)));
            pose.popPose();
        }
    }

    // ------------------------------------------------------------------ singularity

    public static class HoleState extends EntityRenderState {
        boolean opened;
        float phase;
    }

    public static class Singularity extends EntityRenderer<SingularityEntity, HoleState> {
        public Singularity(EntityRendererProvider.Context context) {
            super(context);
        }

        @Override
        public HoleState createRenderState() {
            return new HoleState();
        }

        @Override
        public void extractRenderState(SingularityEntity e, HoleState s, float partial) {
            super.extractRenderState(e, s, partial);
            s.opened = e.opened();
            s.phase = e.phaseAge() + (e.opened() ? partial : 0);
        }

        @Override
        public boolean shouldRender(SingularityEntity entity, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
            return true;
        }

        @Override
        protected AABB getBoundingBoxForCulling(SingularityEntity entity) {
            return entity.getBoundingBox().inflate(6);
        }

        @Override
        public void submit(HoleState s, PoseStack pose, SubmitNodeCollector c, CameraRenderState camera) {
            float t = s.ageInTicks;
            pose.pushPose();
            pose.translate(0, 0.25, 0);
            if (!s.opened) {
                // In flight: a dark mote with a violet corona.
                sphere(c, pose, 0.16F);
                Fx.glow(c, pose, camera.orientation, 1.1F, Fx.alpha(VIOLET, 0.8F));
                pose.mulPose(Axis.YP.rotationDegrees(t * 20));
                Fx.ring(c, pose, 0.32F, 0.05F, 24, t * 0.1F, Fx.alpha(ORANGE, 0.8F));
                pose.popPose();
                return;
            }
            float p = s.phase;
            float open = Mth.clamp(p / 12F, 0, 1);
            float collapse = p <= SingularityEntity.OPEN ? 0 : Mth.clamp((p - SingularityEntity.OPEN) / SingularityEntity.COLLAPSE, 0, 1);
            float size = (0.25F + 0.75F * easeOut(open)) * (1 - collapse);
            if (collapse > 0) {
                // The collapse: a blinding flash and an expanding violet shell.
                Fx.glow(c, pose, camera.orientation, 3 + 14 * collapse, Fx.alpha(HOT, 1 - collapse));
                Fx.glow(c, pose, camera.orientation, 2 + 20 * collapse, Fx.alpha(VIOLET, 0.9F * (1 - collapse)));
            }
            if (size > 0.01F) {
                // Lensing halo, then the disk behind and in front of the horizon.
                Fx.glow(c, pose, camera.orientation, 7F * size, Fx.alpha(VIOLET, 0.45F));
                Fx.glow(c, pose, camera.orientation, 3.4F * size, Fx.alpha(ORANGE, 0.5F));
                pose.pushPose();
                pose.mulPose(Axis.XP.rotationDegrees(18));
                pose.mulPose(Axis.ZP.rotationDegrees(8 * Mth.sin(t * 0.05F)));
                for (int k = 0; k < 4; k++) {
                    pose.pushPose();
                    pose.mulPose(Axis.YP.rotationDegrees(t * (14 - k * 2.5F)));
                    float r = (1.15F + k * 0.42F) * size;
                    int col = Fx.mix(HOT, ORANGE, k / 3F);
                    Fx.ring(c, pose, r, (0.22F + 0.06F * k) * size, 64, t * 0.08F, Fx.alpha(col, 0.95F - k * 0.15F));
                    pose.popPose();
                }
                pose.popPose();
                // Photon ring: a thin bright circle that always faces you, hugging the horizon.
                pose.pushPose();
                pose.mulPose(camera.orientation);
                pose.mulPose(Axis.XP.rotationDegrees(90));
                Fx.ring(c, pose, 0.98F * size, 0.07F * size, 64, -t * 0.12F, Fx.alpha(HOT, 1F));
                pose.popPose();
                // The event horizon: a sphere that shows the void itself.
                sphere(c, pose, 0.9F * size);
            }
            pose.popPose();
        }

        private static float easeOut(float x) {
            return 1 - (1 - x) * (1 - x) * (1 - x);
        }
    }

    public static void sphereFor(SubmitNodeCollector c, PoseStack pose, float r) {
        sphere(c, pose, r);
    }

    /** A latitude/longitude sphere drawn with the End portal shader: a hole full of distant stars. */
    static void sphere(SubmitNodeCollector c, PoseStack pose, float r) {
        int lat = 10, lon = 16;
        c.submitCustomGeometry(pose, RenderTypes.endPortal(), (p, b) -> {
            for (int i = 0; i < lat; i++) {
                double t0 = Math.PI * i / lat - Math.PI / 2, t1 = Math.PI * (i + 1) / lat - Math.PI / 2;
                for (int j = 0; j < lon; j++) {
                    double p0 = 2 * Math.PI * j / lon, p1 = 2 * Math.PI * (j + 1) / lon;
                    double[][] q = {{t0, p0}, {t0, p1}, {t1, p1}, {t1, p0}};
                    // Both windings, so the horizon is solid from any side regardless of culling.
                    for (double[] v : q) {
                        b.addVertex(p, (float) (Math.cos(v[0]) * Math.cos(v[1]) * r), (float) (Math.sin(v[0]) * r),
                                (float) (Math.cos(v[0]) * Math.sin(v[1]) * r));
                    }
                    q = new double[][] {{t1, p0}, {t1, p1}, {t0, p1}, {t0, p0}};
                    for (double[] v : q) {
                        b.addVertex(p, (float) (Math.cos(v[0]) * Math.cos(v[1]) * r), (float) (Math.sin(v[0]) * r),
                                (float) (Math.cos(v[0]) * Math.sin(v[1]) * r));
                    }
                }
            }
        });
    }

    private WeaponRenderers() {}
}
