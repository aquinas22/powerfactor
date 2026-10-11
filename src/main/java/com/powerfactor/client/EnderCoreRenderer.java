package com.powerfactor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.powerfactor.PowerFactor;
import com.powerfactor.ender.EnderLinkBlockEntity;
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
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** A glowing Ender core floating above the link, tumbling faster while it carries energy. */
public class EnderCoreRenderer implements BlockEntityRenderer<EnderLinkBlockEntity, EnderCoreRenderer.State> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(PowerFactor.id("ender_core"), "main");
    private static final net.minecraft.resources.Identifier TEXTURE = PowerFactor.id("textures/entity/ender_core.png");

    public static class State extends BlockEntityRenderState {
        public float spin, bob;
        public boolean transmitter;
    }

    private final ModelPart core, shell;

    public EnderCoreRenderer(BlockEntityRendererProvider.Context context) {
        ModelPart root = context.bakeLayer(LAYER);
        core = root.getChild("core");
        shell = root.getChild("shell");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("core", CubeListBuilder.create().texOffs(0, 0).addBox(-2, -2, -2, 4, 4, 4), PartPose.ZERO);
        mesh.getRoot().addOrReplaceChild("shell", CubeListBuilder.create().texOffs(0, 8).addBox(-3, -3, -3, 6, 6, 6), PartPose.ZERO);
        return LayerDefinition.create(mesh, 32, 32);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(EnderLinkBlockEntity be, State state, float partialTicks, Vec3 camera,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, camera, breakProgress);
        state.spin = be.spin(partialTicks);
        state.bob = Mth.sin(state.spin * 0.03F) * 0.06F;
        state.transmitter = be.transmitter();
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        pose.pushPose();
        pose.translate(0.5F, 1.3F + state.bob, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(state.spin));
        pose.mulPose(Axis.XP.rotationDegrees(state.spin * 0.6F));
        collector.submitModelPart(core, pose, RenderTypes.entityTranslucentEmissive(TEXTURE), 0xF000F0, OverlayTexture.NO_OVERLAY, null, -1,
                state.breakProgress);
        pose.mulPose(Axis.ZP.rotationDegrees(-state.spin * 1.3F));
        collector.submitModelPart(shell, pose, RenderTypes.entityTranslucentEmissive(TEXTURE), 0xF000F0, OverlayTexture.NO_OVERLAY, null,
                state.transmitter ? 0xC0B070FF : 0xC040E0C0, state.breakProgress);
        pose.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(EnderLinkBlockEntity be) {
        return new AABB(be.getBlockPos()).expandTowards(0, 1, 0);
    }
}
