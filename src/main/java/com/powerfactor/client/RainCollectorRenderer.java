package com.powerfactor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.powerfactor.device.RainCollectorBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** The catchment's visible water surface rises with its actual synchronized tank contents. */
public final class RainCollectorRenderer implements BlockEntityRenderer<RainCollectorBlockEntity, RainCollectorRenderer.State> {
    public static class State extends BlockEntityRenderState { float surface; @Nullable TextureAtlasSprite sprite; }
    public RainCollectorRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public State createRenderState() { return new State(); }
    @Override public void extractRenderState(RainCollectorBlockEntity entity, State state, float partial, Vec3 camera,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breaking) {
        BlockEntityRenderer.super.extractRenderState(entity, state, partial, camera, breaking);
        state.surface = entity.getTank().amount(0) == 0 ? 0 : 0.252F + 0.43F * entity.getTank().amount(0) / RainCollectorBlockEntity.TANK;
        state.sprite = Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(Fluids.WATER.defaultFluidState()).stillMaterial().sprite();
        if (entity.getLevel() != null) state.lightCoords = LightCoordsUtil.getLightCoords(LightCoordsUtil.BrightnessGetter.DEFAULT,
                entity.getLevel(), entity.getBlockState(), entity.getBlockPos().above());
    }
    @Override public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        var sprite = state.sprite; if (state.surface <= 0 || sprite == null) return;
        float y = state.surface; int light = state.lightCoords;
        collector.submitCustomGeometry(pose, RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS), (p, out) -> {
            float[][] vertices = {{0.126F, 0.126F, sprite.getU0(), sprite.getV0()}, {0.126F, 0.874F, sprite.getU0(), sprite.getV1()},
                    {0.874F, 0.874F, sprite.getU1(), sprite.getV1()}, {0.874F, 0.126F, sprite.getU1(), sprite.getV0()}};
            for (float[] v : vertices) out.addVertex(p, v[0], y, v[1]).setColor(0xDF3F76E4).setUv(v[2], v[3])
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0, 1, 0);
        });
    }
}
