package com.powerfactor.client.mob;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.powerfactor.client.fx.Fx;
import com.powerfactor.client.wash.WashPlantRenderer;
import com.powerfactor.machine.MachineBlock;
import com.powerfactor.mob.MobGrinderBlockEntity;
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
 * Mob Grinder: a toothed saw disc on the front face that idles round slowly and spins up to a blur while it
 * cuts, throwing red sparks; while cutting, red laser fences mark the corners of the kill zone.
 */
public class MobGrinderRenderer implements BlockEntityRenderer<MobGrinderBlockEntity, MobGrinderRenderer.State> {
    /** Spin is integrated per position on the client so it eases up and down instead of snapping. */
    private static final java.util.Map<Long, float[]> SPIN = new java.util.HashMap<>();

    public static class State extends BlockEntityRenderState {
        Direction facing = Direction.NORTH;
        float angle, speed, time;
    }

    public MobGrinderRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(MobGrinderBlockEntity be, State s, float partial, Vec3 camera, ModelFeatureRenderer.@Nullable CrumblingOverlay crumble) {
        BlockEntityRenderer.super.extractRenderState(be, s, partial, camera, crumble);
        s.facing = be.getBlockState().getValueOrElse(MachineBlock.FACING, Direction.NORTH);
        s.time = be.getLevel() == null ? 0 : (be.getLevel().getGameTime() % 72000L) + partial;
        // {angle, speed, last time}
        float[] st = SPIN.computeIfAbsent(be.getBlockPos().asLong(), k -> new float[] {0, 0, s.time});
        float dt = Mth.clamp(s.time - st[2], 0, 5);
        float target = be.clientCutting() ? 48 : 2;
        st[1] += (target - st[1]) * Math.min(1, dt * 0.08F);
        st[0] = (st[0] + st[1] * dt) % 360;
        st[2] = s.time;
        s.angle = st[0];
        s.speed = st[1];
    }

    static float yRot(Direction f) {
        return switch (f) {
            case NORTH -> 180;
            case EAST -> 90;
            case WEST -> -90;
            default -> 0;
        };
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector c, CameraRenderState camera) {
        int light = s.lightCoords;
        float[] W = WashPlantRenderer.WHITE;
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(yRot(s.facing)));
        // Local +Z is the front.
        pose.pushPose();
        pose.translate(0, 0, 0.53F);
        pose.mulPose(Axis.ZP.rotationDegrees(s.angle));
        float blur = Mth.clamp((s.speed - 10) / 38, 0, 1);
        c.submitCustomGeometry(pose, RenderTypes.entityCutout(WashPlantRenderer.TEX), (p, b) -> {
            // Disc, hub and 12 teeth.
            WashPlantRenderer.cylinder(b, p, 0.36F, -0.02F, 0.02F, 24, W, 0xFFB9C3CA, light, false);
            WashPlantRenderer.cylinder(b, p, 0.1F, 0.02F, 0.07F, 12, W, 0xFF39424A, light, false);
            for (int k = 0; k < 12; k++) {
                float a = k * Mth.TWO_PI / 12, ca = Mth.cos(a), sa = Mth.sin(a);
                for (int d = 0; d < 3; d++) {
                    float r = 0.36F + d * 0.035F, sz = 0.05F - d * 0.015F;
                    float x = ca * r - sa * d * 0.03F, y = sa * r + ca * d * 0.03F;
                    WashPlantRenderer.box(b, p, x - sz, y - sz, -0.015F, x + sz, y + sz, 0.015F, W, 0xFFE4ECF0, light);
                }
            }
            // Gullets: dark slots between the teeth.
            for (int k = 0; k < 12; k++) {
                float a = (k + 0.5F) * Mth.TWO_PI / 12;
                float x = Mth.cos(a) * 0.27F, y = Mth.sin(a) * 0.27F;
                WashPlantRenderer.box(b, p, x - 0.025F, y - 0.025F, 0.021F, x + 0.025F, y + 0.025F, 0.025F, W, 0xFF2A3036, light);
            }
        });
        if (blur > 0) Fx.ring(c, pose_xy(pose), 0.42F, 0.1F, 32, s.time * 0.4F, Fx.alpha(0xFFFF5A3A, 0.35F * blur));
        pose.popPose();
        if (blur > 0.2F) {
            // Sparks thrown off the rim.
            for (int k = 0; k < 5; k++) {
                float life = (s.time * 0.21F + k * 0.2F) % 1F, a = k * 2.3F + s.time * 0.4F;
                pose.pushPose();
                pose.translate(Mth.cos(a) * (0.42F + life * 0.6F), Mth.sin(a) * (0.42F + life * 0.6F) - life * life * 0.5F, 0.6F + life * 0.3F);
                Fx.glow(c, pose, camera.orientation, 0.08F, Fx.alpha(0xFFFFB04A, blur * (1 - life)));
                pose.popPose();
            }
            // Laser fence posts at the kill zone's four corners (5x3x5, centred three blocks out).
            float pulse = 0.4F + 0.3F * Mth.sin(s.time * 0.5F);
            for (float x : new float[] {-2.5F, 2.5F}) {
                for (float z : new float[] {0.5F, 5.5F}) {
                    Fx.beam(c, pose, new Vec3(x, -0.5, z), new Vec3(x, 2.5, z), 0.05F, s.time * 0.05F, Fx.alpha(0xFFFF3A3A, pulse * blur), Fx.alpha(0xFFFF3A3A, 0.15F * blur));
                }
            }
        }
        pose.popPose();
    }

    /** Fx.ring lies in XZ; tip it up into the disc's XY plane. */
    private static PoseStack pose_xy(PoseStack pose) {
        pose.mulPose(Axis.XP.rotationDegrees(90));
        return pose;
    }

    @Override
    public AABB getRenderBoundingBox(MobGrinderBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(6);
    }
}
