package com.powerfactor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.powerfactor.PowerFactor;
import com.powerfactor.machine.MachineBlock;
import com.powerfactor.machine.RecipeMachineBlockEntity;
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
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The laser engraver's bed: the wafer lies under a gantry, and while a job runs the laser head
 * rasters back and forth over it, its beam striking sparks off the silicon.
 */
public class LaserEngraverRenderer implements BlockEntityRenderer<RecipeMachineBlockEntity, LaserEngraverRenderer.State> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(PowerFactor.id("laser_head"), "main");
    private static final RenderType HEAD = RenderTypes.entityCutout(PowerFactor.id("textures/entity/laser_head.png"));
    private static final float BED = 11F / 16, HEAD_Y = 14F / 16, BRIDGE_Y = 16F / 16;

    public static class State extends BlockEntityRenderState {
        public Direction facing = Direction.NORTH;
        public boolean running;
        public float time;
        public final ItemStackRenderState item = new ItemStackRenderState();
    }

    private final ModelPart head;
    private final ModelPart bridge;
    private final ItemModelResolver items;

    public LaserEngraverRenderer(BlockEntityRendererProvider.Context context) {
        ModelPart root = context.bakeLayer(LAYER);
        this.head = root.getChild("head");
        this.bridge = root.getChild("bridge");
        this.items = context.itemModelResolver();
    }

    /** Head carriage (4x2x4) with the lens nozzle underneath; origin at the nozzle tip. */
    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-2, -3.5F, -2, 4, 2, 4)
                .texOffs(0, 8).addBox(-1, -1.5F, -1, 2, 1.5F, 2), PartPose.ZERO);
        // The bridge the head rides on, spanning the two side rails.
        mesh.getRoot().addOrReplaceChild("bridge", CubeListBuilder.create().texOffs(0, 0).addBox(-7, -1, -1, 14, 1, 2), PartPose.ZERO);
        return LayerDefinition.create(mesh, 32, 16);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(RecipeMachineBlockEntity be, State state, float partial, Vec3 camera,
            ModelFeatureRenderer.@Nullable CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(be, state, partial, camera, crumbling);
        state.facing = be.getBlockState().getValueOrElse(MachineBlock.FACING, Direction.NORTH);
        state.running = be.getBlockState().getValueOrElse(MachineBlock.RUNNING, false);
        state.time = be.getLevel() == null ? 0 : (be.getLevel().getGameTime() % 100000) + partial;
        if (be.getLevel() != null) {
            state.lightCoords = LightCoordsUtil.getLightCoords(LightCoordsUtil.BrightnessGetter.DEFAULT, be.getLevel(), be.getBlockState(),
                    be.getBlockPos().above());
            // Sparks where the beam meets the wafer.
            if (state.running && be.getLevel().getRandom().nextInt(3) == 0) {
                float[] spot = spot(state.time);
                var p = be.getBlockPos();
                double c = Math.cos(Math.toRadians(-state.facing.toYRot())), s = Math.sin(Math.toRadians(-state.facing.toYRot()));
                double x = spot[0] * c + spot[1] * s, z = -spot[0] * s + spot[1] * c;
                be.getLevel().addParticle(ParticleTypes.ELECTRIC_SPARK, p.getX() + 0.5 + x, p.getY() + BED + 0.04, p.getZ() + 0.5 + z,
                        (be.getLevel().getRandom().nextDouble() - 0.5) * 0.1, 0.05, (be.getLevel().getRandom().nextDouble() - 0.5) * 0.1);
            }
        }
        items.updateForTopItem(state.item, be.shownInput(), ItemDisplayContext.FIXED, be.getLevel(), null, 0);
    }

    /** The beam's spot on the bed (block-centred coordinates): a fast sweep across, a slow step along. */
    private static float[] spot(float time) {
        float sweep = (float) Math.sin(time * 0.9F);
        float step = ((time * 0.02F) % 2F);
        float along = step < 1 ? step : 2 - step;
        return new float[] {sweep * 0.17F, (along - 0.5F) * 0.34F};
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        pose.pushPose();
        pose.translate(0.5F, 0, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(-state.facing.toYRot()));
        if (!state.item.isEmpty()) {
            pose.pushPose();
            pose.translate(0, BED + 0.01F, 0);
            pose.mulPose(Axis.XP.rotationDegrees(90));
            pose.scale(0.42F, 0.42F, 0.42F);
            state.item.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
        float[] s = state.running ? spot(state.time) : new float[] {0, 0};
        pose.pushPose();
        pose.translate(0, BRIDGE_Y, s[1]);
        pose.scale(1, -1, -1);
        collector.submitModelPart(bridge, pose, HEAD, state.lightCoords, OverlayTexture.NO_OVERLAY, null, -1, state.breakProgress);
        pose.popPose();
        pose.pushPose();
        pose.translate(s[0], HEAD_Y, s[1]);
        pose.scale(1, -1, -1);
        collector.submitModelPart(head, pose, HEAD, state.lightCoords, OverlayTexture.NO_OVERLAY, null, -1, state.breakProgress);
        pose.popPose();
        if (state.running) {
            float top = HEAD_Y - 1.5F / 16, bottom = BED + 0.02F;
            collector.submitCustomGeometry(pose, RenderTypes.lightning(), (p, buf) -> {
                beam(buf, p, s[0], s[1], top, bottom, 0.012F, 1F, 0.25F, 0.15F, 0.95F);
                beam(buf, p, s[0], s[1], top, bottom, 0.035F, 1F, 0.1F, 0.05F, 0.35F);
                // A glowing spot where it lands.
                float r = 0.05F;
                buf.addVertex(p, s[0] - r, bottom, s[1] - r).setColor(1F, 0.6F, 0.4F, 0.9F);
                buf.addVertex(p, s[0] - r, bottom, s[1] + r).setColor(1F, 0.6F, 0.4F, 0.9F);
                buf.addVertex(p, s[0] + r, bottom, s[1] + r).setColor(1F, 0.6F, 0.4F, 0.9F);
                buf.addVertex(p, s[0] + r, bottom, s[1] - r).setColor(1F, 0.6F, 0.4F, 0.9F);
            });
        }
        pose.popPose();
    }

    /** A vertical beam as two crossed quads. */
    private static void beam(VertexConsumer buf, PoseStack.Pose p, float x, float z, float top, float bottom, float w,
            float r, float g, float b, float a) {
        for (float[] o : new float[][] {{w, 0}, {0, w}}) {
            buf.addVertex(p, x - o[0], top, z - o[1]).setColor(r, g, b, a);
            buf.addVertex(p, x - o[0], bottom, z - o[1]).setColor(r, g, b, a);
            buf.addVertex(p, x + o[0], bottom, z + o[1]).setColor(r, g, b, a);
            buf.addVertex(p, x + o[0], top, z + o[1]).setColor(r, g, b, a);
        }
    }
}
