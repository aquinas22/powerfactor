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
    private static final float INSET = 0.65F / 16, BOTTOM = 0.85F / 16, TOP = 15.3F / 16;

    public static class State extends BlockEntityRenderState {
        public @Nullable TextureAtlasSprite sprite;
        public int color = -1;
        public float level;
        public boolean glow;
        /** Gases fill the whole tank, thicker as the pressure rises. */
        public boolean gas;
        public final boolean[] joined = new boolean[6], filledNeighbor = new boolean[6];
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
        for (var direction : net.minecraft.core.Direction.values()) {
            state.joined[direction.ordinal()] = tank.getBlockState().getValue( com.powerfactor.logistics.FluidTankBlock.JOINED.get(direction));
            var level = tank.getLevel(); var neighbor = tank.getBlockPos().relative(direction);
            state.filledNeighbor[direction.ordinal()] = state.joined[direction.ordinal()] && level != null && level.hasChunkAt(neighbor)
                    && level.getBlockEntity(neighbor) instanceof FluidTankBlockEntity other && !other.fluid().isEmpty()
                    && FluidStack.isSameFluidSameComponents(fluid, other.fluid());
        }
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
        boolean[] joined = state.joined.clone(), neighbors = state.filledNeighbor.clone();
        float bottom = joined[net.minecraft.core.Direction.DOWN.ordinal()] ? 0 : BOTTOM;
        float ceiling = joined[net.minecraft.core.Direction.UP.ordinal()] ? 1 : TOP;
        float top = state.gas ? ceiling : bottom + (ceiling - bottom) * Math.min(1, state.level);
        int light = state.glow ? 0xF000F0 : state.lightCoords;
        // Gas: a pale blue haze that thickens as the pressure rises.
        int color = state.gas ? (int) (0x70 + 0x8F * Math.min(1, state.level)) << 24 | 0x9CCFFF : state.color;
        collector.submitCustomGeometry(pose, RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS), (p, buf) -> {
            float x0 = joined[net.minecraft.core.Direction.WEST.ordinal()] ? 0 : INSET;
            float x1 = joined[net.minecraft.core.Direction.EAST.ordinal()] ? 1 : 1-INSET;
            float z0 = joined[net.minecraft.core.Direction.NORTH.ordinal()] ? 0 : INSET;
            float z1 = joined[net.minecraft.core.Direction.SOUTH.ordinal()] ? 1 : 1-INSET;
            float vTop = sprite.getV1() - (sprite.getV1() - sprite.getV0()) * (top - bottom);
            if (!neighbors[net.minecraft.core.Direction.NORTH.ordinal()]) quad(buf, p, x0, bottom, z0, x1, top, z0, sprite, vTop, color, light, 0, 0, -1);
            if (!neighbors[net.minecraft.core.Direction.SOUTH.ordinal()]) quad(buf, p, x1, bottom, z1, x0, top, z1, sprite, vTop, color, light, 0, 0, 1);
            if (!neighbors[net.minecraft.core.Direction.WEST.ordinal()]) quad(buf, p, x0, bottom, z1, x0, top, z0, sprite, vTop, color, light, -1, 0, 0);
            if (!neighbors[net.minecraft.core.Direction.EAST.ordinal()]) quad(buf, p, x1, bottom, z0, x1, top, z1, sprite, vTop, color, light, 1, 0, 0);
            if ((state.gas || state.level >= 1) && neighbors[net.minecraft.core.Direction.UP.ordinal()]) return;
            buf.addVertex(p, x0, top, z0).setColor(color).setUv(sprite.getU0(), sprite.getV0()).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0, 1, 0);
            buf.addVertex(p, x0, top, z1).setColor(color).setUv(sprite.getU0(), sprite.getV1()).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0, 1, 0);
            buf.addVertex(p, x1, top, z1).setColor(color).setUv(sprite.getU1(), sprite.getV1()).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0, 1, 0);
            buf.addVertex(p, x1, top, z0).setColor(color).setUv(sprite.getU1(), sprite.getV0()).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0, 1, 0);
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
