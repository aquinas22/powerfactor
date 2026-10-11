package com.powerfactor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.powerfactor.logistics.FluidTankBlockEntity;
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
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jspecify.annotations.Nullable;

/** Draws the tank's fluid, using the fluid's own still texture and tint, filled to its level. */
public class FluidTankRenderer implements BlockEntityRenderer<FluidTankBlockEntity, FluidTankRenderer.State> {
    private static final float INSET = 1.6F / 16, BOTTOM = 0.5F / 16, TOP = 15.5F / 16;

    public static class State extends BlockEntityRenderState {
        public @Nullable TextureAtlasSprite sprite;
        public int color = -1;
        public float level;
        public boolean glow;
        /** Gases fill the whole tank, thicker as the pressure rises. */
        public boolean gas;
    }

    public FluidTankRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(FluidTankBlockEntity tank, State state, float partialTicks, Vec3 camera,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(tank, state, partialTicks, camera, breakProgress);
        FluidStack fluid = tank.fluid();
        state.sprite = null;
        if (fluid.isEmpty()) {
            return;
        }
        var model = Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(fluid.getFluid().defaultFluidState());
        state.sprite = model.stillMaterial().sprite();
        var tint = model.fluidTintSource();
        state.color = tint == null ? -1
                : tank.getLevel() instanceof net.minecraft.client.renderer.block.BlockAndTintGetter world
                        ? 0xFF000000 | tint.colorInWorld(fluid.getFluid().defaultFluidState(), tank.getBlockState(), world, tank.getBlockPos())
                        : 0xFF000000 | tint.colorAsStack(fluid);
        state.level = (float) fluid.getAmount() / FluidTankBlockEntity.CAPACITY;
        state.glow = fluid.getFluid().getFluidType().getLightLevel(fluid) > 0;
        state.gas = fluid.getFluid().getFluidType().getDensity(fluid) < 0;
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        TextureAtlasSprite sprite = state.sprite;
        if (sprite == null || state.level <= 0) {
            return;
        }
        float top = state.gas ? TOP : BOTTOM + (TOP - BOTTOM) * Math.min(1, state.level);
        int light = state.glow ? 0xF000F0 : state.lightCoords;
        // Gas: a pale blue haze that thickens as the pressure rises.
        int color = state.gas ? (int) (0x70 + 0x8F * Math.min(1, state.level)) << 24 | 0x9CCFFF : state.color;
        collector.submitCustomGeometry(pose, RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS), (p, buf) -> {
            float lo = INSET, hi = 1 - INSET;
            float vTop = sprite.getV1() - (sprite.getV1() - sprite.getV0()) * (top - BOTTOM);
            // Four sides, then the surface.
            quad(buf, p, lo, BOTTOM, lo, hi, top, lo, sprite, vTop, color, light, 0, 0, -1);
            quad(buf, p, hi, BOTTOM, hi, lo, top, hi, sprite, vTop, color, light, 0, 0, 1);
            quad(buf, p, lo, BOTTOM, hi, lo, top, lo, sprite, vTop, color, light, -1, 0, 0);
            quad(buf, p, hi, BOTTOM, lo, hi, top, hi, sprite, vTop, color, light, 1, 0, 0);
            buf.addVertex(p, lo, top, lo).setColor(color).setUv(sprite.getU0(), sprite.getV0()).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0, 1, 0);
            buf.addVertex(p, lo, top, hi).setColor(color).setUv(sprite.getU0(), sprite.getV1()).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0, 1, 0);
            buf.addVertex(p, hi, top, hi).setColor(color).setUv(sprite.getU1(), sprite.getV1()).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0, 1, 0);
            buf.addVertex(p, hi, top, lo).setColor(color).setUv(sprite.getU1(), sprite.getV0()).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0, 1, 0);
        });
    }

    /** A vertical face from (x0, y0, z0) to (x1, y1, z1). */
    private static void quad(VertexConsumer buf, PoseStack.Pose p, float x0, float y0, float z0, float x1, float y1, float z1,
            TextureAtlasSprite s, float vTop, int color, int light, float nx, float ny, float nz) {
        buf.addVertex(p, x0, y0, z0).setColor(color).setUv(s.getU0(), s.getV1()).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
        buf.addVertex(p, x1, y0, z1).setColor(color).setUv(s.getU1(), s.getV1()).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
        buf.addVertex(p, x1, y1, z1).setColor(color).setUv(s.getU1(), vTop).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
        buf.addVertex(p, x0, y1, z0).setColor(color).setUv(s.getU0(), vTop).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
    }
}
