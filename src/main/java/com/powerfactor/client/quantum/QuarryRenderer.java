package com.powerfactor.client.quantum;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.powerfactor.client.fx.Fx;
import com.powerfactor.quantum.QuarryBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The quarry's light show. Industrial: a glowing amber frame with a gantry that tracks the head and
 * a cutting laser down into the pit. Quantum: no frame at all; gyroscopic rings over the controller,
 * violet pylons at the corners, and a rift beam from a floating projector to whatever it is unmaking.
 */
public class QuarryRenderer implements BlockEntityRenderer<QuarryBlockEntity, QuarryRenderer.State> {
    private static final int AMBER = 0xFFFFB347, AMBER_HOT = 0xFFFFF0D0, VIOLET = 0xFFB18CFF, CYAN = 0xFF6FE3FF;

    public static class State extends BlockEntityRenderState {
        public boolean quantum, mining, done;
        public float time;
        public Vec3 corner = Vec3.ZERO, far = Vec3.ZERO, head = Vec3.ZERO;
        public boolean hasTarget;
        public double floor;
    }

    public QuarryRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(QuarryBlockEntity be, State s, float partial, Vec3 camera, ModelFeatureRenderer.@Nullable CrumblingOverlay crumble) {
        BlockEntityRenderer.super.extractRenderState(be, s, partial, camera, crumble);
        BlockPos origin = be.getBlockPos();
        s.quantum = be.tier() == QuarryBlockEntity.Tier.QUANTUM;
        s.mining = be.status() == QuarryBlockEntity.Status.MINING;
        s.done = be.status() == QuarryBlockEntity.Status.DONE;
        s.time = be.getLevel() == null ? 0 : (be.getLevel().getGameTime() % 72000L) + partial;
        BlockPos a = be.corner(), b = be.farCorner();
        s.corner = new Vec3(Math.min(a.getX(), b.getX()) - origin.getX(), 0, Math.min(a.getZ(), b.getZ()) - origin.getZ());
        s.far = new Vec3(Math.max(a.getX(), b.getX()) + 1 - origin.getX(), 0, Math.max(a.getZ(), b.getZ()) + 1 - origin.getZ());
        s.hasTarget = be.target() != null;
        s.head = be.head(partial).subtract(Vec3.atLowerCornerOf(origin));
        int cy = be.cursorY();
        s.floor = cy == Integer.MIN_VALUE ? 0 : cy - origin.getY();
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector c, CameraRenderState camera) {
        if (s.quantum) quantum(s, pose, c, camera);
        else industrial(s, pose, c, camera);
    }

    private void industrial(State s, PoseStack pose, SubmitNodeCollector c, CameraRenderState camera) {
        double y = 1.05;
        float t = s.time;
        int edge = Fx.alpha(AMBER, s.mining ? 0.9F : 0.45F);
        Vec3 a = new Vec3(s.corner.x, y, s.corner.z), b = new Vec3(s.far.x, y, s.corner.z), cc = new Vec3(s.far.x, y, s.far.z),
                d = new Vec3(s.corner.x, y, s.far.z);
        // The frame: four rails, posts down to the ground at each corner.
        for (Vec3[] e : new Vec3[][] {{a, b}, {b, cc}, {cc, d}, {d, a}}) Fx.beam(c, pose, e[0], e[1], 0.07F, t * 0.02F, edge, Fx.alpha(AMBER, 0.15F));
        for (Vec3 p : new Vec3[] {a, b, cc, d}) Fx.beam(c, pose, p, p.add(0, -1.05, 0), 0.07F, 0, edge, 0);
        if (!s.hasTarget || s.done) return;
        // Gantry: one bridge across, one along, crossing at the head.
        Vec3 head = new Vec3(Mth.clamp(s.head.x, s.corner.x, s.far.x), y, Mth.clamp(s.head.z, s.corner.z, s.far.z));
        int rail = Fx.alpha(0xFFE0E0E0, 0.6F);
        Fx.beam(c, pose, new Vec3(s.corner.x, y, head.z), new Vec3(s.far.x, y, head.z), 0.05F, 0, rail, Fx.alpha(AMBER, 0.1F));
        Fx.beam(c, pose, new Vec3(head.x, y, s.corner.z), new Vec3(head.x, y, s.far.z), 0.05F, 0, rail, Fx.alpha(AMBER, 0.1F));
        pose.pushPose();
        pose.translate(head.x, head.y, head.z);
        Fx.cube(c, pose, Fx.GLOW, 0.32F, Fx.alpha(AMBER, 0.55F));
        Fx.glow(c, pose, camera.orientation, 0.7F, Fx.alpha(AMBER, 0.6F));
        pose.popPose();
        if (!s.mining) return;
        // The cutting laser and its splash.
        Vec3 hit = s.head.add(0, 0.5, 0);
        float flicker = 0.75F + 0.25F * Mth.sin(t * 3.1F);
        Fx.beam(c, pose, head, hit, 0.06F * flicker, -t * 0.2F, Fx.alpha(AMBER_HOT, flicker), Fx.alpha(AMBER, 0.45F * flicker));
        pose.pushPose();
        pose.translate(hit.x, hit.y, hit.z);
        Fx.glow(c, pose, camera.orientation, 1.1F * flicker, Fx.alpha(AMBER, 0.8F));
        Fx.sprite(c, pose, camera.orientation, Fx.SPARK, 0.6F + 0.3F * Mth.sin(t * 5), Fx.alpha(AMBER_HOT, 0.9F));
        pose.popPose();
    }

    private void quantum(State s, PoseStack pose, SubmitNodeCollector c, CameraRenderState camera) {
        float t = s.time;
        float power = s.mining ? 1F : s.done ? 0.35F : 0.55F;
        // Gyroscope over the controller.
        pose.pushPose();
        pose.translate(0.5, 1.55 + 0.06 * Mth.sin(t * 0.07F), 0.5);
        for (int k = 0; k < 3; k++) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(t * (2 + k) * (s.mining ? 2 : 0.5F) + k * 40));
            pose.mulPose(Axis.XP.rotationDegrees(55 * k + t * 0.6F));
            Fx.ring(c, pose, 0.55F - k * 0.09F, 0.05F, 48, t * 0.04F * (k + 1), Fx.alpha(Fx.mix(VIOLET, CYAN, k / 2F), 0.85F * power));
            pose.popPose();
        }
        Fx.glow(c, pose, camera.orientation, 0.9F, Fx.alpha(VIOLET, 0.55F * power));
        Fx.glow(c, pose, camera.orientation, 0.35F, Fx.alpha(0xFFFFFFFF, 0.8F * power));
        pose.popPose();
        // Border: a shimmering rectangle at ground level and light pylons at the corners down to the cut face.
        double y = 1.02;
        Vec3 a = new Vec3(s.corner.x, y, s.corner.z), b = new Vec3(s.far.x, y, s.corner.z), cc = new Vec3(s.far.x, y, s.far.z),
                d = new Vec3(s.corner.x, y, s.far.z);
        int border = Fx.alpha(VIOLET, 0.75F * power);
        for (Vec3[] e : new Vec3[][] {{a, b}, {b, cc}, {cc, d}, {d, a}}) {
            Fx.beam(c, pose, e[0], e[1], 0.05F, t * 0.05F, border, Fx.alpha(CYAN, 0.12F * power));
        }
        double depth = Math.min(0, s.floor);
        for (Vec3 p : new Vec3[] {a, b, cc, d}) {
            Fx.beam(c, pose, p.add(0, 2.5, 0), new Vec3(p.x, depth, p.z), 0.09F, -t * 0.03F, border, Fx.alpha(VIOLET, 0.18F * power));
            pose.pushPose();
            pose.translate(p.x, p.y + 2.5, p.z);
            Fx.glow(c, pose, camera.orientation, 0.6F, Fx.alpha(VIOLET, 0.7F * power));
            pose.popPose();
        }
        if (!s.hasTarget || s.done) return;
        // The projector hangs over the middle of the area; the rift beam bends space from it to the target.
        Vec3 projector = new Vec3((s.corner.x + s.far.x) / 2, 5.5 + 0.2 * Mth.sin(t * 0.05F), (s.corner.z + s.far.z) / 2);
        Fx.beam(c, pose, new Vec3(0.5, 1.55, 0.5), projector, 0.07F, -t * 0.08F, Fx.alpha(0xFFE8DCFF, 0.7F * power), Fx.alpha(VIOLET, 0.3F * power));
        pose.pushPose();
        pose.translate(projector.x, projector.y, projector.z);
        pose.mulPose(Axis.YP.rotationDegrees(t * 4));
        Fx.ring(c, pose, 0.7F, 0.08F, 48, t * 0.06F, Fx.alpha(CYAN, 0.9F * power));
        pose.mulPose(Axis.XP.rotationDegrees(90));
        Fx.ring(c, pose, 0.5F, 0.05F, 40, -t * 0.08F, Fx.alpha(VIOLET, 0.9F * power));
        pose.popPose();
        pose.pushPose();
        pose.translate(projector.x, projector.y, projector.z);
        Fx.glow(c, pose, camera.orientation, 1.6F, Fx.alpha(VIOLET, 0.65F * power));
        pose.popPose();
        if (!s.mining) return;
        Vec3 hit = s.head;
        float flicker = 0.8F + 0.2F * Mth.sin(t * 4.3F);
        Fx.beam(c, pose, projector, hit, 0.12F * flicker, -t * 0.3F, Fx.alpha(0xFFF4ECFF, flicker), Fx.alpha(VIOLET, 0.5F * flicker));
        Fx.beam(c, pose, projector, hit, 0.03F, -t * 0.6F, Fx.alpha(CYAN, 0.9F), 0);
        pose.pushPose();
        pose.translate(hit.x, hit.y, hit.z);
        Fx.glow(c, pose, camera.orientation, 1.8F * flicker, Fx.alpha(VIOLET, 0.8F));
        Fx.glow(c, pose, camera.orientation, 0.7F, Fx.alpha(0xFFFFFFFF, 0.9F));
        for (int k = 0; k < 2; k++) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(t * 9 + k * 90));
            pose.mulPose(Axis.XP.rotationDegrees(70 + k * 40));
            Fx.ring(c, pose, 0.75F - k * 0.2F, 0.04F, 32, t * 0.2F, Fx.alpha(k == 0 ? CYAN : VIOLET, 0.9F));
            pose.popPose();
        }
        pose.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(QuarryBlockEntity be) {
        BlockPos a = be.corner(), b = be.farCorner();
        int bottom = be.getLevel() == null ? -64 : be.getLevel().getMinY();
        return new AABB(Math.min(a.getX(), b.getX()) - 1, bottom, Math.min(a.getZ(), b.getZ()) - 1,
                Math.max(a.getX(), b.getX()) + 2, be.getBlockPos().getY() + 8, Math.max(a.getZ(), b.getZ()) + 2);
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 160;
    }
}
