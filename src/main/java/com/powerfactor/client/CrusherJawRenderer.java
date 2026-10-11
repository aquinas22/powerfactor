package com.powerfactor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.powerfactor.PowerFactor;
import com.powerfactor.machine.CrusherBlockEntity;
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
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The crusher's jaws, seen through the feed hopper on top: a fixed jaw and a swing jaw that rocks
 * against it while the motor runs, with the ore being crushed jiggling between them.
 */
public class CrusherJawRenderer implements BlockEntityRenderer<CrusherBlockEntity, CrusherJawRenderer.State> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(PowerFactor.id("crusher_jaws"), "main");
    private static final RenderType RENDER_TYPE = RenderTypes.entityCutout(PowerFactor.id("textures/entity/crusher_jaws.png"));

    public static class State extends BlockEntityRenderState {
        public Direction facing = Direction.NORTH;
        public float time;
        public float swing;
        public final ItemStackRenderState item = new ItemStackRenderState();
    }

    private final ModelPart jaw;
    private final ItemModelResolver items;

    public CrusherJawRenderer(BlockEntityRendererProvider.Context context) {
        this.jaw = context.bakeLayer(LAYER).getChild("jaw");
        this.items = context.itemModelResolver();
    }

    /** A 9x4x2 plate with four teeth on its +Z face; origin at the plate's top-centre back edge. */
    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        CubeListBuilder jaw = CubeListBuilder.create().texOffs(0, 0).addBox(-4.5F, 0, 0, 9, 4, 2);
        for (int i = 0; i < 4; i++) {
            jaw.texOffs(0, 8).addBox(-3.9F + i * 2.6F, 0, 2, 1, 4, 1);
        }
        mesh.getRoot().addOrReplaceChild("jaw", jaw, PartPose.ZERO);
        return LayerDefinition.create(mesh, 32, 32);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(CrusherBlockEntity be, State state, float partialTicks, Vec3 camera,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, camera, breakProgress);
        state.facing = be.getBlockState().getValueOrElse(MachineBlock.FACING, Direction.NORTH);
        boolean running = be.getBlockState().getValueOrElse(MachineBlock.RUNNING, false);
        state.time = be.getLevel() == null ? 0 : (be.getLevel().getGameTime() % 24000) + partialTicks;
        // The swing jaw eases in and out as the motor starts and stops.
        state.swing = be.jawSwing(partialTicks, running);
        if (be.getLevel() != null) {
            // The block itself is solid; light the hopper from the air above it.
            state.lightCoords = LightCoordsUtil.getLightCoords(LightCoordsUtil.BrightnessGetter.DEFAULT, be.getLevel(), be.getBlockState(),
                    be.getBlockPos().above());
        }
        items.updateForTopItem(state.item, be.shownInput(), ItemDisplayContext.FIXED, be.getLevel(), null, (int) be.getBlockPos().asLong());
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        float phase = (float) Math.sin(state.time * 0.9F);
        pose.pushPose();
        pose.translate(0.5F, 0, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(-state.facing.toYRot()));

        // Fixed jaw against the front wall of the hopper, teeth facing back.
        pose.pushPose();
        pose.translate(0, 11 / 16F, -5 / 16F);
        collector.submitModelPart(jaw, pose, RENDER_TYPE, state.lightCoords, OverlayTexture.NO_OVERLAY, null, -1, state.breakProgress);
        pose.popPose();

        // Swing jaw hangs from a pivot at the top of the back wall and rocks toward the fixed one.
        pose.pushPose();
        pose.translate(0, 15 / 16F, 5 / 16F - state.swing * (0.5F + 0.5F * phase) * 1.2F / 16F);
        pose.mulPose(Axis.XP.rotationDegrees(state.swing * phase * 7F));
        pose.translate(0, -4 / 16F, 0);
        pose.mulPose(Axis.YP.rotationDegrees(180));
        collector.submitModelPart(jaw, pose, RENDER_TYPE, state.lightCoords, OverlayTexture.NO_OVERLAY, null, -1, state.breakProgress);
        pose.popPose();

        // The ore in the nip, rattling while it's crushed.
        if (!state.item.isEmpty()) {
            pose.pushPose();
            float rattle = state.swing * (float) Math.sin(state.time * 2.3F) * 0.02F;
            pose.translate(0, 12.8F / 16F + Math.abs(rattle), rattle * 0.5F);
            pose.mulPose(Axis.YP.rotationDegrees(state.swing * (float) Math.sin(state.time * 1.7F) * 12F));
            pose.scale(0.4F, 0.4F, 0.4F);
            state.item.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
        pose.popPose();
    }
}
