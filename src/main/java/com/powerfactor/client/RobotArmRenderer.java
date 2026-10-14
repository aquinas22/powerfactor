package com.powerfactor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.powerfactor.PowerFactor;
import com.powerfactor.logistics.RobotArmBlockEntity;
import com.powerfactor.machine.MachineBlock;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The robot arm in motion. The gripper follows a path between key points (home, down into the source,
 * up and over, down into the target) and a two-link inverse-kinematics solve sets the shoulder and
 * elbow so the arm always reaches exactly there. The carried stack rides in the gripper; the filter
 * item sits on the base like a label.
 */
public class RobotArmRenderer implements BlockEntityRenderer<RobotArmBlockEntity, RobotArmRenderer.State> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(PowerFactor.id("robot_arm"), "main");
    private static final RenderType TYPE = RenderTypes.entityCutout(PowerFactor.id("textures/entity/robot_arm.png"));
    /** Segment lengths and the shoulder height, in pixels. */
    private static final float L1 = 10, L2 = 9, SHOULDER = 8;
    private static final float[] HOME = {2, 15}, REACH = {13, 6};

    public static class State extends BlockEntityRenderState {
        Direction facing = Direction.NORTH;
        float yaw, a1, a2, grip;
        final ItemStackRenderState held = new ItemStackRenderState(), filter = new ItemStackRenderState();
    }

    private final ModelPart turret, lower, upper, gripper;
    private final ItemModelResolver items;

    public RobotArmRenderer(BlockEntityRendererProvider.Context context) {
        ModelPart root = context.bakeLayer(LAYER);
        turret = root.getChild("turret");
        lower = root.getChild("lower");
        upper = root.getChild("upper");
        gripper = root.getChild("gripper");
        items = context.itemModelResolver();
    }

    /** Parts are centred on their pivot and extend along -Y (the renderer flips Y up). */
    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        var r = mesh.getRoot();
        r.addOrReplaceChild("turret", CubeListBuilder.create().texOffs(0, 0).addBox(-4, -2, -4, 8, 2, 8)
                .texOffs(32, 0).addBox(-2.5F, -4, -2.5F, 5, 2, 5), PartPose.ZERO);
        r.addOrReplaceChild("lower", CubeListBuilder.create().texOffs(0, 10).addBox(-1.5F, -10, -1.5F, 3, 10, 3)
                .texOffs(12, 10).addBox(-2, -1, -2, 4, 2, 4), PartPose.ZERO);
        r.addOrReplaceChild("upper", CubeListBuilder.create().texOffs(28, 10).addBox(-1.25F, -9, -1.25F, 2.5F, 9, 2.5F)
                .texOffs(40, 10).addBox(-1.75F, -1, -1.75F, 3.5F, 2, 3.5F), PartPose.ZERO);
        r.addOrReplaceChild("gripper", CubeListBuilder.create().texOffs(0, 24).addBox(-2, -1, -1, 4, 1, 2)
                .texOffs(12, 24).addBox(-2, -4, -0.5F, 1, 3, 1).texOffs(16, 24).addBox(1, -4, -0.5F, 1, 3, 1), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 32);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    private static float smooth(float t) {
        return t * t * (3 - 2 * t);
    }

    @Override
    public void extractRenderState(RobotArmBlockEntity be, State s, float partial, Vec3 camera, ModelFeatureRenderer.@Nullable CrumblingOverlay crumble) {
        BlockEntityRenderer.super.extractRenderState(be, s, partial, camera, crumble);
        s.facing = be.getBlockState().getValueOrElse(MachineBlock.FACING, Direction.NORTH);
        float p = smooth(be.clientProgress(partial));
        float f, u, yaw;
        switch (be.clientStage()) {
            case 1 -> { // reach back into the source
                yaw = 180 * p;
                f = Mth.lerp(p, HOME[0], REACH[0]);
                u = Mth.lerp(p, HOME[1], REACH[1]);
            }
            case 2 -> { // lift, swing round, set down in the target
                yaw = 180 + 180 * p;
                float lift = Mth.sin(p * Mth.PI);
                f = REACH[0] - 6 * lift;
                u = REACH[1] + 9 * lift;
            }
            case 3 -> { // back home
                yaw = 0;
                f = Mth.lerp(p, REACH[0], HOME[0]);
                u = Mth.lerp(p, REACH[1], HOME[1]);
            }
            default -> {
                // Idle: a slow servo hunt so it never looks frozen.
                float t = be.getLevel() == null ? 0 : (be.getLevel().getGameTime() % 400 + partial) / 400F * Mth.TWO_PI;
                yaw = 8 * Mth.sin(t);
                f = HOME[0];
                u = HOME[1];
            }
        }
        // Two-link IK in the arm's vertical plane: angles from vertical, positive toward the reach.
        double d = Math.min(L1 + L2 - 0.01, Math.hypot(f, u));
        double a2 = Math.acos(Math.clamp((d * d - L1 * L1 - L2 * L2) / (2 * L1 * L2), -1, 1));
        double a1 = Math.atan2(f, u) - Math.atan2(L2 * Math.sin(a2), L1 + L2 * Math.cos(a2));
        s.yaw = yaw;
        s.a1 = (float) Math.toDegrees(a1);
        s.a2 = (float) Math.toDegrees(a2);
        s.grip = be.clientStage() == 2 || !be.clientHeld().isEmpty() ? 0.5F : 1.5F;
        items.updateForTopItem(s.held, be.clientHeld(), ItemDisplayContext.FIXED, be.getLevel(), null, 0);
        items.updateForTopItem(s.filter, be.clientFilter(), ItemDisplayContext.FIXED, be.getLevel(), null, 1);
        if (be.getLevel() != null) {
            s.lightCoords = LightCoordsUtil.getLightCoords(LightCoordsUtil.BrightnessGetter.DEFAULT, be.getLevel(), be.getBlockState(),
                    be.getBlockPos().above());
        }
    }

    private void part(SubmitNodeCollector c, PoseStack pose, ModelPart part, State s) {
        pose.pushPose();
        pose.scale(1, -1, -1);                 // a rotation, not a mirror: the parts are symmetric in Z
        c.submitModelPart(part, pose, TYPE, s.lightCoords, OverlayTexture.NO_OVERLAY, null, -1, s.breakProgress);
        pose.popPose();
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector c, CameraRenderState camera) {
        pose.pushPose();
        pose.translate(0.5, 6 / 16F, 0.5);
        // Local -Z is the delivery side.
        pose.mulPose(Axis.YP.rotationDegrees(180 - s.facing.toYRot()));
        if (!s.filter.isEmpty()) {
            pose.pushPose();
            pose.translate(0, 0.005, -5.2F / 16);
            pose.mulPose(Axis.XP.rotationDegrees(-90));
            pose.scale(0.22F, 0.22F, 0.22F);
            s.filter.submit(pose, c, s.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
        pose.mulPose(Axis.YP.rotationDegrees(s.yaw));
        part(c, pose, turret, s);
        pose.translate(0, (SHOULDER - 6) / 16F + 2 / 16F, 0);
        pose.mulPose(Axis.XP.rotationDegrees(-s.a1));
        part(c, pose, lower, s);
        pose.translate(0, L1 / 16F, 0);
        pose.mulPose(Axis.XP.rotationDegrees(-s.a2));
        part(c, pose, upper, s);
        pose.translate(0, L2 / 16F, 0);
        // Keep the gripper pointing straight down whatever the joints are doing.
        pose.mulPose(Axis.XP.rotationDegrees(s.a1 + s.a2 + 180));
        part(c, pose, gripper, s);
        if (!s.held.isEmpty()) {
            pose.pushPose();
            pose.translate(0, 3.5F / 16, 0);
            pose.scale(0.3F, 0.3F, 0.3F);
            s.held.submit(pose, c, s.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
        pose.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(RobotArmBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(1.5);
    }
}
