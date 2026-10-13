package com.powerfactor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.powerfactor.PowerFactor;
import com.powerfactor.movement.GrappleHookEntity;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Barbed hook and segmented cable, including sag and a culling box covering both endpoints. */
public final class GrappleHookRenderer extends EntityRenderer<GrappleHookEntity, GrappleHookRenderer.State> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(PowerFactor.id("grapple_hook"), "main");
    private static final net.minecraft.resources.Identifier TEXTURE = PowerFactor.id("textures/entity/grapple_hook.png");
    private static final net.minecraft.resources.Identifier CABLE = PowerFactor.id("textures/entity/grapple_cable.png");
    public static class State extends EntityRenderState { Vec3 start = Vec3.ZERO; float sag, yaw, pitch; boolean cable; }
    private final ModelPart hook;
    public GrappleHookRenderer(EntityRendererProvider.Context context) { super(context); hook = context.bakeLayer(LAYER).getChild("hook"); }
    public static LayerDefinition createLayer() {
        var mesh = new MeshDefinition(); var root = mesh.getRoot();
        root.addOrReplaceChild("hook", CubeListBuilder.create().texOffs(0, 0).addBox(-1.5F, -1.5F, -3, 3, 3, 6)
                .texOffs(20, 0).addBox(-0.6F, -0.6F, 3, 1.2F, 1.2F, 3).addBox(-2.5F, -0.5F, 1, 5, 1, 1)
                .texOffs(0, 12).addBox(-2.5F, -0.5F, 2, 1, 1, 2).addBox(1.5F, -0.5F, 2, 1, 1, 2), PartPose.ZERO);
        return LayerDefinition.create(mesh, 32, 32);
    }
    @Override public State createRenderState() { return new State(); }
    @Override public void extractRenderState(GrappleHookEntity entity, State state, float partial) {
        super.extractRenderState(entity, state, partial); var owner = entity.getOwner();
        state.cable = owner != null;
        if (owner != null) {
            boolean flying = owner instanceof net.minecraft.world.entity.LivingEntity living && living.isFallFlying();
            double yaw = Math.toRadians(owner.getYRot()), width = flying ? 0.75 : 0.3, forward = flying ? 0.9 : 0.2;
            state.start = owner.getEyePosition(partial).add(-Math.cos(yaw) * width - Math.sin(yaw) * forward,
                    flying ? -0.2 : -0.75, -Math.sin(yaw) * width + Math.cos(yaw) * forward).subtract(entity.getPosition(partial));
            state.sag = (float) Math.clamp((entity.ropeLength() - state.start.length()) * 0.35, 0.03, 4);
        }
        state.yaw = entity.getYRot(); state.pitch = entity.getXRot();
    }
    @Override public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        pose.pushPose(); pose.mulPose(Axis.YP.rotationDegrees(state.yaw)); pose.mulPose(Axis.XP.rotationDegrees(-state.pitch));
        collector.submitModelPart(hook, pose, RenderTypes.entityCutout(TEXTURE), state.lightCoords, OverlayTexture.NO_OVERLAY, null, -1, null); pose.popPose();
        if (state.cable) {
            int segments = Math.clamp((int) (state.start.length() * 2), 8, 96);
            collector.submitCustomGeometry(pose, RenderTypes.entityCutout(CABLE), (p, out) -> {
                for (int i = 0; i < segments; i++) {
                    double t0 = i / (double) segments, t1 = (i + 1) / (double) segments;
                    Vec3 a = state.start.scale(t0).add(0, -state.sag * Math.sin(Math.PI * t0), 0);
                    Vec3 b = state.start.scale(t1).add(0, -state.sag * Math.sin(Math.PI * t1), 0);
                    Vec3 tangent = b.subtract(a).normalize(); Vec3 side = tangent.cross(new Vec3(0, 1, 0));
                    if (side.lengthSqr() < 1e-6) side = new Vec3(1, 0, 0); side = side.normalize().scale(0.014);
                    for (Vec3 edge : new Vec3[] {side, tangent.cross(side).normalize().scale(0.014)}) {
                        Vec3[] vertices = {a.add(edge), b.add(edge), b.subtract(edge), a.subtract(edge)};
                        for (int n = 0; n < 4; n++) out.addVertex(p, (float) vertices[n].x, (float) vertices[n].y, (float) vertices[n].z)
                                .setColor(0xFFD8DEE2).setUv(n < 2 ? 0 : 1, n == 0 || n == 3 ? 0 : 1)
                                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(state.lightCoords).setNormal(p, 0, 1, 0);
                    }
                }
            });
        }
        super.submit(state, pose, collector, camera);
    }
    @Override protected AABB getBoundingBoxForCulling(GrappleHookEntity entity) {
        var owner = entity.getOwner(); return owner == null ? super.getBoundingBoxForCulling(entity)
                : new AABB(entity.position(), owner.getEyePosition()).inflate(4);
    }
}
