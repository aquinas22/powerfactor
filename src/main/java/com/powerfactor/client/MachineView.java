package com.powerfactor.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A live 3D view of a machine inside its GUI: the block model plus its block-entity renderer
 * (spinning rotors, crusher jaws, lasers), turning slowly on a turntable.
 */
public final class MachineView {
    private static final BlockDisplayContext CONTEXT = BlockDisplayContext.create();

    public record State(BlockModelRenderState block, @Nullable BlockEntityRenderer<?, ?> renderer, @Nullable BlockEntityRenderState beState,
                        float yaw, float pitch, int x0, int y0, int x1, int y1, float scale, @Nullable ScreenRectangle scissorArea,
                        @Nullable ScreenRectangle bounds) implements PictureInPictureRenderState {}

    /** Draw {@code be}'s machine filling the box, turned {@code yaw} degrees. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void draw(GuiGraphicsExtractor g, BlockEntity be, int x0, int y0, int x1, int y1, float yaw, float partial) {
        Minecraft mc = Minecraft.getInstance();
        BlockState state = be.getBlockState();
        BlockModelRenderState block = new BlockModelRenderState();
        mc.getBlockModelResolver().update(block, state, CONTEXT);
        BlockEntityRenderer renderer = mc.getBlockEntityRenderDispatcher().getRenderer(be);
        BlockEntityRenderState beState = null;
        if (renderer != null && be.hasLevel()) {
            beState = renderer.createRenderState();
            renderer.extractRenderState(be, beState, partial, Vec3.atCenterOf(be.getBlockPos()), null);
            beState.lightCoords = 0xF000F0;
        }
        // Tall machines (turbines with towers, coils) need room: fit about two blocks high.
        float size = Math.min(x1 - x0, y1 - y0) * 0.6F;
        g.submitPictureInPictureRenderState(new State(block, renderer, beState, yaw, 26, x0, y0, x1, y1, size, null,
                PictureInPictureRenderState.getBounds(x0, y0, x1, y1, null)));
    }

    public static class Renderer extends PictureInPictureRenderer<State> {
        @Override
        public Class<State> getRenderStateClass() {
            return State.class;
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        protected void renderToTexture(State s, PoseStack pose, SubmitNodeCollector collector) {
            Minecraft.getInstance().gameRenderer.lighting().setupFor(Lighting.Entry.ENTITY_IN_UI);
            pose.mulPose(Axis.XP.rotationDegrees(180 + s.pitch()));
            pose.mulPose(Axis.YP.rotationDegrees(s.yaw()));
            pose.translate(-0.5F, -0.5F, -0.5F);
            s.block().submit(pose, collector, 0xF000F0, OverlayTexture.NO_OVERLAY, 0);
            if (s.renderer() != null && s.beState() != null) {
                ((BlockEntityRenderer) s.renderer()).submit(s.beState(), pose, collector, new CameraRenderState());
            }
        }

        @Override
        protected float getTranslateY(int height, int guiScale) {
            return height / 2F;
        }

        @Override
        protected String getTextureLabel() {
            return "powerfactor machine view";
        }
    }

    private MachineView() {}
}
