package com.powerfactor.client.farm;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.powerfactor.client.fx.Fx;
import com.powerfactor.client.wash.WashPlantRenderer;
import com.powerfactor.farm.FarmGantryBlockEntity;
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
 * The gantry crane over the field: four yellow posts and two rails, the bridge rolling along them to the
 * head's row, the carriage running across the bridge to its square, and the tool head dropping on a rod
 * to work the square, with a flash in the job's colour (soil brown, sprout green, harvest gold).
 */
public class FarmGantryRenderer implements BlockEntityRenderer<FarmGantryBlockEntity, FarmGantryRenderer.State> {
    static final float RAIL_Y = 2.4F, EDGE = FarmGantryBlockEntity.HALF + 0.75F, NEAR = 0.6F, FAR = FarmGantryBlockEntity.SIZE + 0.4F;

    public static class State extends BlockEntityRenderState {
        Direction facing = Direction.NORTH;
        float u, v, dip, time;
        int job;
        boolean on;
    }

    public FarmGantryRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(FarmGantryBlockEntity be, State s, float partial, Vec3 camera, ModelFeatureRenderer.@Nullable CrumblingOverlay crumble) {
        BlockEntityRenderer.super.extractRenderState(be, s, partial, camera, crumble);
        s.facing = be.getBlockState().getValueOrElse(MachineBlock.FACING, Direction.NORTH);
        float[] h = be.clientHead(partial);
        s.u = h[0];
        s.v = h[1];
        s.dip = h[2];
        s.job = be.clientJob();
        s.on = be.clientOn();
        s.time = be.getLevel() == null ? 0 : (be.getLevel().getGameTime() % 72000L) + partial;
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
        float[] Y = WashPlantRenderer.YELLOW, G = WashPlantRenderer.GALV, W = WashPlantRenderer.WHITE;
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(yRot(s.facing)));
        // Local frame: +Z is out over the field, X is mirrored "across" (x = -u).
        float x = -s.u, z = s.v;
        c.submitCustomGeometry(pose, RenderTypes.entityCutout(WashPlantRenderer.TEX), (p, b) -> {
            for (float px : new float[] {-EDGE, EDGE}) {
                for (float pz : new float[] {NEAR, FAR}) WashPlantRenderer.box(b, p, px - 0.09F, 0, pz - 0.09F, px + 0.09F, RAIL_Y, pz + 0.09F, Y, 0xFFFFFFFF, light);
                WashPlantRenderer.box(b, p, px - 0.11F, RAIL_Y, NEAR - 0.1F, px + 0.11F, RAIL_Y + 0.16F, FAR + 0.1F, G, 0xFFFFFFFF, light);
            }
            // Cross ties at the ends.
            for (float pz : new float[] {NEAR, FAR}) WashPlantRenderer.box(b, p, -EDGE, RAIL_Y + 0.02F, pz - 0.06F, EDGE, RAIL_Y + 0.12F, pz + 0.06F, Y, 0xFFFFFFFF, light);
            // Bridge: a box girder across the rails at the head's row, with end trucks.
            WashPlantRenderer.box(b, p, -EDGE, RAIL_Y + 0.16F, z - 0.14F, EDGE, RAIL_Y + 0.4F, z + 0.14F, Y, 0xFFFFFFFF, light);
            for (float px : new float[] {-EDGE, EDGE}) WashPlantRenderer.box(b, p, px - 0.16F, RAIL_Y + 0.12F, z - 0.3F, px + 0.16F, RAIL_Y + 0.42F, z + 0.3F, G, 0xFFC8D0D4, light);
            // Carriage on the bridge, and the tool rod down to the head.
            WashPlantRenderer.box(b, p, x - 0.22F, RAIL_Y + 0.05F, z - 0.22F, x + 0.22F, RAIL_Y + 0.5F, z + 0.22F, G, 0xFFFFFFFF, light);
            float headY = Mth.lerp(s.dip, RAIL_Y - 0.25F, 0.3F);
            WashPlantRenderer.box(b, p, x - 0.035F, headY + 0.15F, z - 0.035F, x + 0.035F, RAIL_Y + 0.05F, z + 0.035F, W, 0xFFB8C4CC, light);
            WashPlantRenderer.box(b, p, x - 0.16F, headY, z - 0.16F, x + 0.16F, headY + 0.18F, z + 0.16F, W, 0xFF5A646C, light);
            // Tines / nozzles under the head.
            for (int k = -1; k <= 1; k++) WashPlantRenderer.box(b, p, x + k * 0.1F - 0.015F, headY - 0.1F, z - 0.015F, x + k * 0.1F + 0.015F, headY, z + 0.015F, W, 0xFFDDE6EC, light);
        });
        if (s.on) {
            pose.pushPose();
            float headY = Mth.lerp(s.dip, RAIL_Y - 0.25F, 0.3F);
            pose.translate(x, headY - 0.05F, z);
            int col = switch (s.job) {
                case FarmGantryBlockEntity.JOB_TILL -> 0xFFB08050;
                case FarmGantryBlockEntity.JOB_PLANT -> 0xFF7CFF8A;
                case FarmGantryBlockEntity.JOB_HARVEST -> 0xFFFFD84A;
                default -> 0xFF6FE3FF;
            };
            Fx.glow(c, pose, camera.orientation, 0.25F + 0.4F * s.dip, Fx.alpha(col, 0.5F + 0.5F * s.dip));
            pose.popPose();
            // Status lamp on the carriage.
            pose.pushPose();
            pose.translate(x, RAIL_Y + 0.6F, z);
            Fx.glow(c, pose, camera.orientation, 0.18F, Fx.alpha(0xFF7CFF8A, 0.6F + 0.4F * Mth.sin(s.time * 0.3F)));
            pose.popPose();
        }
        pose.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(FarmGantryBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(12);
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }
}
