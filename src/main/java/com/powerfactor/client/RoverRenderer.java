package com.powerfactor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.powerfactor.PowerFactor;
import com.powerfactor.vehicle.RoverEntity;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/** Open utility chassis, articulated steering wheels, exposed axles, roll cage and powered lamps. */
public final class RoverRenderer extends EntityRenderer<RoverEntity, RoverRenderer.State> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(PowerFactor.id("electric_rover"), "main");
    private static final Identifier BODY = PowerFactor.id("textures/entity/rover_body.png"), TIRE = PowerFactor.id("textures/entity/rover_tire.png"), LIGHT = PowerFactor.id("textures/entity/rover_lights.png");
    private final ModelPart chassis, lamps, tail, steering;
    private final ModelPart[] wheels = new ModelPart[4], shocks = new ModelPart[4];
    public static class State extends EntityRenderState { float yaw, pitch, wheel, steer, bounce; boolean charged, brake; }
    public RoverRenderer(EntityRendererProvider.Context context) {
        super(context); shadowRadius = 1.15F; var root = context.bakeLayer(LAYER);
        chassis = root.getChild("chassis"); lamps = root.getChild("lamps"); tail = root.getChild("tail"); steering = chassis.getChild("steering");
        for (int i = 0; i < 4; i++) { wheels[i] = root.getChild("wheel" + i); shocks[i] = root.getChild("shock" + i); }
    }
    public static LayerDefinition createLayer() {
        var mesh = new MeshDefinition(); var root = mesh.getRoot();
        var chassis = root.addOrReplaceChild("chassis", CubeListBuilder.create().texOffs(0, 0).addBox(-11, -9, -18, 22, 3, 36)
                .texOffs(0, 42).addBox(-9.5F, -15, -17, 19, 6, 11).addBox(-9, -13, 8, 18, 4, 9)
                .texOffs(64, 0).addBox(-10.5F, -10, -4, 2, 2, 20).addBox(8.5F, -10, -4, 2, 2, 20)
                .texOffs(64, 28).addBox(-12, -8, -19, 24, 2, 2).addBox(-12, -8, 17, 24, 2, 2)
                .texOffs(0, 64).addBox(-4.5F, -12, -1, 9, 3, 9).addBox(-4.5F, -21, 6, 9, 12, 2)
                .texOffs(64, 44).addBox(-10, -26, 7.5F, 1.5F, 18, 1.5F).addBox(8.5F, -26, 7.5F, 1.5F, 18, 1.5F)
                .addBox(-10, -26, -5, 1.5F, 13, 1.5F).addBox(8.5F, -26, -5, 1.5F, 13, 1.5F)
                .addBox(-10, -27, -5, 20, 1.5F, 1.5F).addBox(-10, -27, 7.5F, 20, 1.5F, 1.5F)
                .texOffs(0, 85).addBox(-10, -27, -5, 1.5F, 1.5F, 14).addBox(8.5F, -27, -5, 1.5F, 1.5F, 14)
                .texOffs(40, 64).addBox(-8, -16, -5.5F, 16, 2, 3).addBox(-1, -17, -3.5F, 2, 3, 2)
                .texOffs(0, 105).addBox(-7, -14, 10, 14, 1.5F, 5), PartPose.ZERO);
        chassis.addOrReplaceChild("steering", CubeListBuilder.create().texOffs(70, 75).addBox(-3.5F, -0.5F, -0.5F, 7, 1, 1)
                .addBox(-3.5F, -0.5F, -0.5F, 1, 4, 1).addBox(2.5F, -0.5F, -0.5F, 1, 4, 1).addBox(-3.5F, 2.5F, -0.5F, 7, 1, 1),
                PartPose.offsetAndRotation(0, -17, -2, -0.35F, 0, 0));
        root.addOrReplaceChild("lamps", CubeListBuilder.create().texOffs(0, 0).addBox(-8.5F, -13, -17.1F, 4, 2.5F, 0.2F)
                .addBox(4.5F, -13, -17.1F, 4, 2.5F, 0.2F).addBox(-6, -26.5F, -5.1F, 12, 0.8F, 0.2F), PartPose.ZERO);
        root.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(0, 8).addBox(-8.5F, -11, 17.1F, 3, 1.5F, 0.2F)
                .addBox(5.5F, -11, 17.1F, 3, 1.5F, 0.2F), PartPose.ZERO);
        for (int i = 0; i < 4; i++) {
            float x = (i % 2 == 0 ? -1 : 1) * 12.3F, z = i < 2 ? -11.5F : 11.5F;
            root.addOrReplaceChild("wheel" + i, CubeListBuilder.create().texOffs(0, 0).addBox(-2.5F, -5.5F, -4, 5, 11, 8)
                    .texOffs(0, 24).addBox(-2.6F, -4, -5.5F, 5.2F, 8, 11)
                    .texOffs(32, 0).addBox(-2.8F, -2.5F, -2.5F, 5.6F, 5, 5), PartPose.offset(x, -5.5F, z));
            root.addOrReplaceChild("shock" + i, CubeListBuilder.create().texOffs(64, 90).addBox(-1, -3, -1, 2, 6, 2)
                    .texOffs(76, 90).addBox(-0.4F, 1, -0.4F, 0.8F, 5, 0.8F), PartPose.offset(x * 0.78F, -9, z));
        }
        return LayerDefinition.create(mesh, 128, 128);
    }
    @Override public State createRenderState() { return new State(); }
    @Override public void extractRenderState(RoverEntity rover, State state, float partial) {
        super.extractRenderState(rover, state, partial); state.yaw = Mth.rotLerp(partial, rover.yRotO, rover.getYRot());
        state.pitch = Mth.lerp(partial, rover.xRotO, rover.getXRot()); state.wheel = rover.wheel(partial);
        state.steer = rover.steering(); state.bounce = rover.suspension(partial); state.charged = rover.charge() > 0; state.brake = rover.braking();
    }
    @Override public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        pose.pushPose(); pose.mulPose(Axis.YP.rotationDegrees(180 - state.yaw)); pose.scale(-1, -1, 1);
        pose.pushPose(); pose.translate(0, state.bounce, 0); pose.mulPose(Axis.XP.rotationDegrees(state.pitch));
        steering.zRot = -state.steer * 0.55F;
        collector.submitModelPart(chassis, pose, RenderTypes.entityCutout(BODY), state.lightCoords, OverlayTexture.NO_OVERLAY, null, -1, null);
        collector.submitModelPart(lamps, pose, RenderTypes.entityCutout(LIGHT), state.charged ? 0xF000F0 : state.lightCoords, OverlayTexture.NO_OVERLAY, null, state.charged ? -1 : 0xFF343B3D, null);
        collector.submitModelPart(tail, pose, RenderTypes.entityCutout(LIGHT), state.brake ? 0xF000F0 : state.lightCoords, OverlayTexture.NO_OVERLAY, null, state.brake ? -1 : 0xFF774444, null);
        pose.popPose();
        for (int i = 0; i < 4; i++) {
            wheels[i].xRot = state.wheel; wheels[i].yRot = i < 2 ? -state.steer * 0.48F : 0;
            collector.submitModelPart(wheels[i], pose, RenderTypes.entityCutout(TIRE), state.lightCoords, OverlayTexture.NO_OVERLAY, null, -1, null);
            shocks[i].yScale = 1 + state.bounce * 2;
            collector.submitModelPart(shocks[i], pose, RenderTypes.entityCutout(BODY), state.lightCoords, OverlayTexture.NO_OVERLAY, null, -1, null);
        }
        pose.popPose(); super.submit(state, pose, collector, camera);
    }
}
