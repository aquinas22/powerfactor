package com.powerfactor.client.blackhole;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.powerfactor.blackhole.BlackHoleBlockEntity;
import com.powerfactor.client.fx.Fx;
import com.powerfactor.client.wash.WashPlantRenderer;
import com.powerfactor.machine.MachineBlock;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The contained black hole: a perfectly black horizon sized by its mass, a thin photon ring that always
 * faces you, a tilted accretion disk glowing white-hot at its inner edge and red at the rim and spinning
 * faster inward, blue-white jets from the poles that brighten with output, and matter streaking in from
 * the injectors as it feeds. While igniting, violet energy converges on a growing point.
 */
public class BlackHoleRenderer implements BlockEntityRenderer<BlackHoleBlockEntity, BlackHoleRenderer.State> {
    public static class State extends BlockEntityRenderState {
        Vec3 centre = Vec3.ZERO;
        float mass, hawking, feed, ignition, time;
        int state;
        Direction facing = Direction.NORTH;
    }

    public BlackHoleRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(BlackHoleBlockEntity be, State s, float partial, Vec3 camera, ModelFeatureRenderer.@Nullable CrumblingOverlay crumble) {
        BlockEntityRenderer.super.extractRenderState(be, s, partial, camera, crumble);
        s.facing = be.getBlockState().getValueOrElse(MachineBlock.FACING, Direction.NORTH);
        s.centre = be.centre().subtract(Vec3.atLowerCornerOf(be.getBlockPos()));
        s.mass = be.clientMass();
        s.hawking = be.clientHawking();
        s.feed = be.clientFeed();
        s.state = be.clientState();
        s.ignition = (float) Math.clamp(be.ignition() / BlackHoleBlockEntity.IGNITION, 0, 1);
        s.time = be.getLevel() == null ? 0 : (be.getLevel().getGameTime() % 72000L) + partial;
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector c, CameraRenderState camera) {
        if (s.state == BlackHoleBlockEntity.STATE_DORMANT) return;
        float t = s.time;
        pose.pushPose();
        pose.translate(s.centre.x, s.centre.y, s.centre.z);
        if (s.state == BlackHoleBlockEntity.STATE_IGNITING) {
            float f = s.ignition;
            for (int k = 0; k < 10; k++) {
                float life = (t * 0.04F + k / 10F) % 1F, a = k * 2.4F + t * 0.1F;
                float r = 1.4F * (1 - life);
                pose.pushPose();
                pose.translate(Mth.cos(a) * r, Mth.sin(k * 1.3F) * r * 0.6F, Mth.sin(a) * r);
                Fx.glow(c, pose, camera.orientation, 0.15F, Fx.alpha(0xFFB18CFF, life));
                pose.popPose();
            }
            Fx.glow(c, pose, camera.orientation, 0.2F + 1.2F * f, Fx.alpha(0xFFE8DCFF, 0.4F + 0.5F * f));
            pose.popPose();
            return;
        }
        float r = 0.18F + 0.4F * (float) Math.cbrt(s.mass / 1000F);
        float bright = Mth.clamp((float) Math.log10(Math.max(10, s.hawking)) / 7F, 0.2F, 1.4F);
        // The horizon: a black sphere that takes no light.
        c.submitCustomGeometry(pose, RenderTypes.entitySolid(WashPlantRenderer.TEX), (p, b) -> sphere(b, p, r, 0xFF000000));
        // The accretion disk, tilted, in rings that spin faster nearer the hole.
        pose.pushPose();
        pose.mulPose(Axis.XP.rotationDegrees(14));
        pose.mulPose(Axis.ZP.rotationDegrees(6));
        int rings = 7;
        for (int k = 0; k < rings; k++) {
            float f = k / (float) (rings - 1);
            float rr = r * (1.5F + 1.4F * f);
            int col = f < 0.3F ? Fx.mix(0xFFFFFFFF, 0xFFFFE07A, f / 0.3F) : Fx.mix(0xFFFFB347, 0xFFC0301A, (f - 0.3F) / 0.7F);
            float speed = 0.12F / (0.4F + f);
            Fx.ring(c, pose, rr, r * 0.32F, 40, t * speed, Fx.alpha(col, (0.85F - 0.5F * f) * Math.min(1, bright)));
        }
        pose.popPose();
        // Photon ring: a thin bright halo hugging the shadow, always seen face-on.
        pose.pushPose();
        pose.mulPose(camera.orientation);
        pose.mulPose(Axis.XP.rotationDegrees(90));
        Fx.ring(c, pose, r * 1.28F, r * 0.08F, 48, t * 0.05F, Fx.alpha(0xFFFFF4D6, 0.9F));
        Fx.ring(c, pose, r * 1.45F, r * 0.22F, 48, -t * 0.03F, Fx.alpha(0xFFFFB347, 0.35F * bright));
        pose.popPose();
        // Polar jets.
        float jet = Mth.clamp(bright - 0.3F, 0, 1);
        if (jet > 0.02F) {
            for (int dir = -1; dir <= 1; dir += 2) {
                Vec3 from = new Vec3(0, dir * r * 1.1, 0), to = new Vec3(0, dir * (r * 1.1 + 0.9 + 0.4 * jet), 0);
                Fx.beam(c, pose, from, to, 0.08F + 0.06F * jet, t * 0.2F * dir, Fx.alpha(0xFFDDF4FF, 0.8F * jet), Fx.alpha(0xFF6FA8FF, 0.4F * jet));
            }
        }
        // Infalling matter: streaks spiralling in from the four injectors while it feeds.
        if (s.feed > 0.001F) {
            for (int k = 0; k < 12; k++) {
                float life = (t * 0.05F + k / 12F) % 1F;
                float a = (k % 4) * Mth.HALF_PI + life * 4F;
                float rr = 1.6F * (1 - life) + r;
                pose.pushPose();
                pose.translate(Mth.cos(a) * rr, Mth.sin(a * 0.5F) * 0.2F * (1 - life), Mth.sin(a) * rr);
                Fx.glow(c, pose, camera.orientation, 0.1F + 0.1F * life, Fx.alpha(0xFFFFD08A, 0.4F + 0.6F * life));
                pose.popPose();
            }
        }
        // A wide dim glow so the sphere lights its glass.
        Fx.glow(c, pose, camera.orientation, r * 6F, Fx.alpha(0xFFFF8A3A, 0.12F * bright));
        pose.popPose();
    }

    private static void sphere(com.mojang.blaze3d.vertex.VertexConsumer b, PoseStack.Pose p, float r, int color) {
        int lat = 12, lon = 20;
        float[] uv = WashPlantRenderer.WHITE;
        for (int i = 0; i < lat; i++) {
            float t0 = Mth.PI * i / lat, t1 = Mth.PI * (i + 1) / lat;
            for (int j = 0; j < lon; j++) {
                float p0 = Mth.TWO_PI * j / lon, p1 = Mth.TWO_PI * (j + 1) / lon;
                float[][] v = {
                        {Mth.sin(t0) * Mth.cos(p0), Mth.cos(t0), Mth.sin(t0) * Mth.sin(p0)},
                        {Mth.sin(t0) * Mth.cos(p1), Mth.cos(t0), Mth.sin(t0) * Mth.sin(p1)},
                        {Mth.sin(t1) * Mth.cos(p1), Mth.cos(t1), Mth.sin(t1) * Mth.sin(p1)},
                        {Mth.sin(t1) * Mth.cos(p0), Mth.cos(t1), Mth.sin(t1) * Mth.sin(p0)}};
                for (float[] q : v) {
                    b.addVertex(p, q[0] * r, q[1] * r, q[2] * r).setColor(color).setUv((uv[0] + 1) / 128F, (uv[1] + 1) / 128F)
                            .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(Fx.FULL_BRIGHT).setNormal(p, q[0], q[1], q[2]);
                }
            }
        }
    }

    @Override
    public AABB getRenderBoundingBox(BlackHoleBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(6);
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }
}
