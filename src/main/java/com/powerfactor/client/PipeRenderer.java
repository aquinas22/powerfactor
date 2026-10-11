package com.powerfactor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.powerfactor.logistics.PipeBlock;
import com.powerfactor.logistics.PipeBlockEntity;
import com.powerfactor.logistics.PipeSide;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** What you see through the glass: items gliding along the pipe, and fluid filling it while it flows. */
public class PipeRenderer implements BlockEntityRenderer<PipeBlockEntity, PipeRenderer.State> {
    private static final float LO = 5.6F / 16, HI = 10.4F / 16;

    public static class State extends BlockEntityRenderState {
        public final List<ItemStackRenderState> items = new ArrayList<>();
        public final List<Vec3> offsets = new ArrayList<>();
        public float spin;
        public @Nullable TextureAtlasSprite fluid;
        public int fluidColor;
        public final boolean[] arms = new boolean[6];
    }

    private final ItemModelResolver resolver;

    public PipeRenderer(BlockEntityRendererProvider.Context context) {
        this.resolver = context.itemModelResolver();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(PipeBlockEntity be, State state, float partial, Vec3 camera, ModelFeatureRenderer.@Nullable CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(be, state, partial, camera, crumbling);
        state.items.clear();
        state.offsets.clear();
        state.fluid = null;
        if (be.getLevel() == null) {
            return;
        }
        float time = be.getLevel().getGameTime() + partial;
        state.spin = time * 6;
        Vec3 origin = Vec3.atLowerCornerOf(be.getBlockPos());
        int n = 0;
        for (var e : PipeTrafficClient.itemsIn(be.getBlockPos(), time)) {
            if (n++ >= 6) {
                break;
            }
            ItemStackRenderState item = new ItemStackRenderState();
            resolver.updateForTopItem(item, e.getKey(), ItemDisplayContext.GROUND, be.getLevel(), null, n);
            state.items.add(item);
            state.offsets.add(e.getValue().subtract(origin));
        }
        var flow = PipeTrafficClient.flowIn(be.getBlockPos());
        if (flow != null) {
            var model = Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(flow.fluid().defaultFluidState());
            state.fluid = model.stillMaterial().sprite();
            var tint = model.fluidTintSource();
            state.fluidColor = tint == null ? 0xFFFFFFFF : 0xFF000000 | tint.color(flow.fluid().defaultFluidState());
            for (Direction d : Direction.values()) {
                state.arms[d.ordinal()] = be.getBlockState().getValue(PipeBlock.SIDES.get(d)) != PipeSide.NONE;
            }
        }
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        for (int i = 0; i < state.items.size(); i++) {
            Vec3 o = state.offsets.get(i);
            pose.pushPose();
            pose.translate(o.x, o.y - 0.12, o.z);
            pose.mulPose(Axis.YP.rotationDegrees(state.spin + i * 47));
            pose.scale(0.55F, 0.55F, 0.55F);
            state.items.get(i).submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
        TextureAtlasSprite sprite = state.fluid;
        if (sprite != null) {
            int color = state.fluidColor, light = state.lightCoords;
            boolean[] arms = state.arms.clone();
            collector.submitCustomGeometry(pose, RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS), (p, buf) -> {
                box(buf, p, LO, LO, LO, HI, HI, HI, sprite, color, light);
                for (Direction d : Direction.values()) {
                    if (!arms[d.ordinal()]) {
                        continue;
                    }
                    float x0 = d.getStepX() < 0 ? 0 : d.getStepX() > 0 ? HI : LO, x1 = d.getStepX() < 0 ? LO : d.getStepX() > 0 ? 1 : HI;
                    float y0 = d.getStepY() < 0 ? 0 : d.getStepY() > 0 ? HI : LO, y1 = d.getStepY() < 0 ? LO : d.getStepY() > 0 ? 1 : HI;
                    float z0 = d.getStepZ() < 0 ? 0 : d.getStepZ() > 0 ? HI : LO, z1 = d.getStepZ() < 0 ? LO : d.getStepZ() > 0 ? 1 : HI;
                    box(buf, p, x0, y0, z0, x1, y1, z1, sprite, color, light);
                }
            });
        }
    }

    private static void box(VertexConsumer buf, PoseStack.Pose p, float x0, float y0, float z0, float x1, float y1, float z1,
            TextureAtlasSprite s, int color, int light) {
        float u0 = s.getU0(), u1 = s.getU1(), v0 = s.getV0(), v1 = s.getV1();
        float[][][] faces = {
                {{x0, y0, z0}, {x1, y0, z0}, {x1, y1, z0}, {x0, y1, z0}, {0, 0, -1}},
                {{x1, y0, z1}, {x0, y0, z1}, {x0, y1, z1}, {x1, y1, z1}, {0, 0, 1}},
                {{x0, y0, z1}, {x0, y0, z0}, {x0, y1, z0}, {x0, y1, z1}, {-1, 0, 0}},
                {{x1, y0, z0}, {x1, y0, z1}, {x1, y1, z1}, {x1, y1, z0}, {1, 0, 0}},
                {{x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1}, {0, 1, 0}},
                {{x0, y0, z1}, {x1, y0, z1}, {x1, y0, z0}, {x0, y0, z0}, {0, -1, 0}}};
        float[][] uv = {{u0, v1}, {u1, v1}, {u1, v0}, {u0, v0}};
        for (float[][] f : faces) {
            for (int k = 0; k < 4; k++) {
                buf.addVertex(p, f[k][0], f[k][1], f[k][2]).setColor(color).setUv(uv[k][0], uv[k][1]).setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(light).setNormal(p, f[4][0], f[4][1], f[4][2]);
            }
        }
    }
}
