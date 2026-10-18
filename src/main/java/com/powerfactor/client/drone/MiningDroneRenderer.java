package com.powerfactor.client.drone;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.powerfactor.client.fx.Fx;
import com.powerfactor.client.wash.WashPlantRenderer;
import com.powerfactor.drone.MiningDroneEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Mining Drone: a quadcopter with spinning rotor blurs and nav lights. Mining, it fires a pulsing laser into
 * the ore with a hot spot where it bites; tunnelling through stone, a cyan bore field shimmers round it.
 */
public class MiningDroneRenderer extends EntityRenderer<MiningDroneEntity, MiningDroneRenderer.State> {
    public static class State extends EntityRenderState {
        float yaw, time, cut;
        int droneState, index;
        Vec3 target = Vec3.ZERO;
        boolean inside;
    }

    public MiningDroneRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(MiningDroneEntity e, State s, float partial) {
        super.extractRenderState(e, s, partial);
        s.yaw = e.getYRot();
        s.time = e.tickCount + partial;
        s.droneState = e.state();
        s.index = e.index();
        s.cut = e.cut() / 100F;
        s.target = Vec3.atCenterOf(e.target()).subtract(e.getPosition(partial));
        s.inside = !e.level().getBlockState(e.blockPosition()).isAir();
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector c, CameraRenderState camera) {
        int light = Fx.FULL_BRIGHT;
        float[] W = WashPlantRenderer.WHITE;
        float bob = Mth.sin(s.time * 0.25F) * 0.03F;
        pose.pushPose();
        pose.translate(0, 0.15F + bob, 0);
        pose.mulPose(Axis.YP.rotationDegrees(-s.yaw));
        // Tilt into the direction of travel when flying.
        if (s.droneState != MiningDroneEntity.STATE_MINING) pose.mulPose(Axis.XP.rotationDegrees(8));
        int accent = switch (s.index) {
            case 0 -> 0xFF6FE3FF;
            case 1 -> 0xFF7CFF8A;
            case 2 -> 0xFFFFD84A;
            default -> 0xFFB18CFF;
        };
        c.submitCustomGeometry(pose, RenderTypes.entityCutout(WashPlantRenderer.TEX), (p, b) -> {
            WashPlantRenderer.box(b, p, -0.13F, -0.05F, -0.16F, 0.13F, 0.07F, 0.16F, W, 0xFF2A3036, light);
            WashPlantRenderer.box(b, p, -0.1F, 0.07F, -0.12F, 0.1F, 0.1F, 0.12F, W, accent, light);
            WashPlantRenderer.box(b, p, -0.05F, -0.11F, 0.08F, 0.05F, -0.05F, 0.16F, W, 0xFF5A646C, light);   // laser head
            // H-frame: two crossbars carrying the motors, joined by a spine through the body.
            for (float az : new float[] {-0.28F, 0.28F}) WashPlantRenderer.box(b, p, -0.3F, 0.0F, az - 0.02F, 0.3F, 0.04F, az + 0.02F, W, 0xFFB8C4CC, light);
            WashPlantRenderer.box(b, p, -0.02F, 0.0F, -0.28F, 0.02F, 0.04F, 0.28F, W, 0xFFB8C4CC, light);
            for (int k = 0; k < 4; k++) {
                float ax = (k % 2 == 0 ? -1 : 1) * 0.28F, az = (k < 2 ? -1 : 1) * 0.28F;
                WashPlantRenderer.box(b, p, ax - 0.04F, 0.0F, az - 0.04F, ax + 0.04F, 0.08F, az + 0.04F, W, 0xFF39424A, light);
            }
        });
        // Rotor blurs and blades.
        for (int k = 0; k < 4; k++) {
            float ax = (k % 2 == 0 ? -1 : 1) * 0.28F, az = (k < 2 ? -1 : 1) * 0.28F;
            pose.pushPose();
            pose.translate(ax, 0.1F, az);
            Fx.ring(c, pose, 0.11F, 0.1F, 16, s.time * 0.8F * (k % 2 == 0 ? 1 : -1), Fx.alpha(0xFFDDE6EC, 0.35F));
            pose.mulPose(Axis.YP.rotationDegrees(s.time * 70 * (k % 2 == 0 ? 1 : -1)));
            c.submitCustomGeometry(pose, RenderTypes.entityCutout(WashPlantRenderer.TEX),
                    (p, b) -> WashPlantRenderer.box(b, p, -0.15F, 0, -0.012F, 0.15F, 0.01F, 0.012F, W, 0xFFE4ECF0, light));
            pose.popPose();
        }
        // Nav lights: green right, red left, blinking white strobe on top.
        for (int side = -1; side <= 1; side += 2) {
            pose.pushPose();
            pose.translate(side * 0.14F, 0.02F, 0.1F);
            Fx.glow(c, pose, camera.orientation, 0.08F, Fx.alpha(side > 0 ? 0xFF7CFF8A : 0xFFFF4A3A, 0.9F));
            pose.popPose();
        }
        if ((int) (s.time / 8) % 4 == 0) {
            pose.pushPose();
            pose.translate(0, 0.14F, 0);
            Fx.glow(c, pose, camera.orientation, 0.18F, Fx.alpha(0xFFFFFFFF, 0.9F));
            pose.popPose();
        }
        pose.popPose();
        // Laser into the ore.
        if (s.droneState == MiningDroneEntity.STATE_MINING) {
            float pulse = 0.7F + 0.3F * Mth.sin(s.time * 1.3F);
            Vec3 from = new Vec3(0, 0.08 + bob, 0);
            Fx.beam(c, pose, from, s.target, 0.035F + 0.03F * s.cut, s.time * 0.3F, Fx.alpha(0xFFFFF0D0, pulse), Fx.alpha(0xFFFF6A2A, 0.6F * pulse));
            pose.pushPose();
            pose.translate(s.target.x, s.target.y, s.target.z);
            Fx.glow(c, pose, camera.orientation, 0.4F + 0.5F * s.cut, Fx.alpha(0xFFFFB04A, 0.8F));
            pose.popPose();
        }
        if (s.inside) {
            pose.pushPose();
            pose.translate(0, 0.15F, 0);
            Fx.glow(c, pose, camera.orientation, 1.0F, Fx.alpha(0xFF6FE3FF, 0.4F + 0.2F * Mth.sin(s.time * 0.5F)));
            pose.popPose();
        }
        super.submit(s, pose, c, camera);
    }

    @Override
    protected AABB getBoundingBoxForCulling(MiningDroneEntity e) {
        return new AABB(e.position(), Vec3.atCenterOf(e.target())).inflate(1);
    }
}
