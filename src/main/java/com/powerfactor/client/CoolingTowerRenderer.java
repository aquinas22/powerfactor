package com.powerfactor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.powerfactor.steam.CoolingTowerBlockEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class CoolingTowerRenderer implements BlockEntityRenderer<CoolingTowerBlockEntity, CoolingTowerRenderer.State> {
    public static class State extends BlockEntityRenderState { float phase; }
    private final ModelPart fan;
    public CoolingTowerRenderer(BlockEntityRendererProvider.Context context) { fan = context.bakeLayer(SteamPlantRenderer.LAYER).getChild("fan"); }
    @Override public State createRenderState() { return new State(); }
    @Override public void extractRenderState(CoolingTowerBlockEntity entity, State state, float partial, Vec3 camera, ModelFeatureRenderer.@Nullable CrumblingOverlay breaking) {
        BlockEntityRenderer.super.extractRenderState(entity, state, partial, camera, breaking); state.phase = entity.fanPhase(partial);
        if (entity.getLevel() != null) state.lightCoords = net.minecraft.util.LightCoordsUtil.getLightCoords(
                net.minecraft.util.LightCoordsUtil.BrightnessGetter.DEFAULT, entity.getLevel(), entity.getBlockState(), entity.getBlockPos().above(2));
    }
    @Override public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        pose.pushPose(); pose.translate(0.5, 1.94, 0.5); pose.mulPose(Axis.XP.rotationDegrees(90)); pose.mulPose(Axis.ZP.rotation(state.phase));
        collector.submitModelPart(fan, pose, SteamPlantRenderer.TYPE, state.lightCoords, OverlayTexture.NO_OVERLAY, null, -1, state.breakProgress); pose.popPose();
    }
    @Override public AABB getRenderBoundingBox(CoolingTowerBlockEntity entity) { return new AABB(entity.getBlockPos()).expandTowards(0, 1.2, 0); }
}
