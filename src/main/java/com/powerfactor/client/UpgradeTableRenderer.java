package com.powerfactor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.powerfactor.upgrade.UpgradeTableBlockEntity;
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
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** The tool on the bench, turning slowly in a cone of cyan light from the projector in the table top. */
public class UpgradeTableRenderer implements BlockEntityRenderer<UpgradeTableBlockEntity, UpgradeTableRenderer.State> {
    public static class State extends BlockEntityRenderState {
        public final ItemStackRenderState item = new ItemStackRenderState();
        public float time;
    }

    private final ItemModelResolver items;

    public UpgradeTableRenderer(BlockEntityRendererProvider.Context context) {
        this.items = context.itemModelResolver();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(UpgradeTableBlockEntity be, State state, float partialTicks, Vec3 camera,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, camera, breakProgress);
        state.time = be.getLevel() == null ? 0 : (be.getLevel().getGameTime() % 24000) + partialTicks;
        items.updateForTopItem(state.item, be.tool().getItem(0), ItemDisplayContext.FIXED, be.getLevel(), null, 0);
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.item.isEmpty()) {
            return;
        }
        float t = state.time;
        // Light cone from the projector to the tool.
        collector.submitCustomGeometry(pose, RenderTypes.lightning(), (p, buf) -> cone(buf, p, t));
        pose.pushPose();
        pose.translate(0.5F, 1.35F + 0.04F * (float) Math.sin(t * 0.08F), 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(t * 2.2F));
        pose.scale(0.6F, 0.6F, 0.6F);
        state.item.submit(pose, collector, 0xF000F0, OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
    }

    private static void cone(VertexConsumer buf, PoseStack.Pose p, float t) {
        int seg = 16;
        float y0 = 0.89F, y1 = 1.6F, r0 = 0.12F, r1 = 0.42F;
        float pulse = 0.18F + 0.06F * (float) Math.sin(t * 0.2F);
        for (int i = 0; i < seg; i++) {
            double a0 = Math.PI * 2 * i / seg, a1 = Math.PI * 2 * (i + 1) / seg;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            buf.addVertex(p, 0.5F + c0 * r0, y0, 0.5F + s0 * r0).setColor(0.25F, 0.88F, 1F, pulse);
            buf.addVertex(p, 0.5F + c1 * r0, y0, 0.5F + s1 * r0).setColor(0.25F, 0.88F, 1F, pulse);
            buf.addVertex(p, 0.5F + c1 * r1, y1, 0.5F + s1 * r1).setColor(0.25F, 0.88F, 1F, 0F);
            buf.addVertex(p, 0.5F + c0 * r1, y1, 0.5F + s0 * r1).setColor(0.25F, 0.88F, 1F, 0F);
        }
    }
}
