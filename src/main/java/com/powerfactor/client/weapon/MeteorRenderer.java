package com.powerfactor.client.weapon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.powerfactor.PowerFactor;
import com.powerfactor.client.fx.Fx;
import com.powerfactor.world.MeteorEntity;
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
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A meteor in flight: a tumbling lump of charred rock whose cracks glow white-hot, wrapped in a fiery
 * corona and dragging a long burning trail back along its path.
 */
public class MeteorRenderer extends EntityRenderer<MeteorEntity, MeteorRenderer.State> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(PowerFactor.id("meteor"), "main");
    private static final RenderType ROCK = RenderTypes.entityCutout(PowerFactor.id("textures/entity/meteor.png"));
    private static final RenderType CRACKS = RenderTypes.eyes(PowerFactor.id("textures/entity/meteor_glow.png"));
    private static final int FIRE = 0xFFFF7A2A, HOT = 0xFFFFE0A0;

    public static class State extends EntityRenderState {
        int size;
        Vec3 velocity = Vec3.ZERO;
    }

    private final ModelPart rock;

    public MeteorRenderer(EntityRendererProvider.Context context) {
        super(context);
        rock = context.bakeLayer(LAYER).getChild("rock");
    }

    /** A rough boulder: a core block with offset lumps so no face is flat. */
    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("rock", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-5, -5, -5, 10, 10, 10)
                .texOffs(0, 20).addBox(-6, -3, -3, 2, 6, 6)
                .texOffs(16, 20).addBox(4, -4, -2, 2, 5, 5)
                .texOffs(30, 20).addBox(-3, -6, -2, 6, 2, 5)
                .texOffs(30, 0).addBox(-2, 4, -3, 5, 2, 6)
                .texOffs(40, 10).addBox(-3, -2, -6, 6, 5, 2)
                .texOffs(40, 26).addBox(-2, -3, 4, 5, 6, 2), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 32);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(MeteorEntity e, State s, float partial) {
        super.extractRenderState(e, s, partial);
        s.size = e.size();
        s.velocity = e.getDeltaMovement();
    }

    @Override
    public boolean shouldRender(MeteorEntity entity, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    protected AABB getBoundingBoxForCulling(MeteorEntity entity) {
        return entity.getBoundingBox().inflate(30);
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector c, CameraRenderState camera) {
        float t = s.ageInTicks, scale = 0.6F + 0.45F * s.size;
        // Trail first, from the rock back along the path.
        Vec3 back = s.velocity.lengthSqr() < 1e-6 ? new Vec3(0, 1, 0) : s.velocity.normalize().scale(-1);
        // Seen from far off, the trail is what people notice: a white-hot core, an orange sheath, a long smoky tail.
        Fx.beam(c, pose, Vec3.ZERO, back.scale(18 + 8 * s.size), 0.9F * scale, -t * 0.4F, Fx.alpha(0xFFFFFFFF, 1F), Fx.alpha(HOT, 0.8F));
        Fx.beam(c, pose, Vec3.ZERO, back.scale(40 + 15 * s.size), 2.2F * scale, -t * 0.2F, Fx.alpha(FIRE, 0.7F), Fx.alpha(FIRE, 0.3F));
        Fx.beam(c, pose, Vec3.ZERO, back.scale(70 + 20 * s.size), 3.5F * scale, -t * 0.1F, Fx.alpha(0xFF8A4A2A, 0.25F), 0);
        pose.pushPose();
        pose.scale(scale, scale, scale);
        pose.mulPose(Axis.XP.rotationDegrees(t * 9));
        pose.mulPose(Axis.YP.rotationDegrees(t * 6));
        pose.pushPose();
        pose.scale(1, -1, -1);
        c.submitModelPart(rock, pose, ROCK, 0xF000F0, OverlayTexture.NO_OVERLAY, null, -1, null);
        c.submitModelPart(rock, pose, CRACKS, 0xF000F0, OverlayTexture.NO_OVERLAY, null, -1, null);
        pose.popPose();
        pose.popPose();
        // Corona and a heat-shock ring facing the direction of travel.
        Fx.glow(c, pose, camera.orientation, 7F * scale, Fx.alpha(FIRE, 0.7F));
        Fx.glow(c, pose, camera.orientation, 3F * scale, Fx.alpha(HOT, 1F));
    }
}
