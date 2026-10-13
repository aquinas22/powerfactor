package com.powerfactor.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.powerfactor.machine.MachineBlock;
import com.powerfactor.machine.SideConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * A draggable live model beside the fixed face controls. Colored outlines indicate modes without
 * hiding the machinery. The model faces north, matching the machine's front/back/left/right.
 */
public final class SideConfigView {
    private static final BlockDisplayContext CONTEXT = BlockDisplayContext.create();

    public record State(BlockModelRenderState block, @Nullable BlockEntityRenderer<?, ?> renderer, @Nullable BlockEntityRenderState beState,
                        float beYaw, int[] faceColors, int hover, float yaw, float pitch, int x0, int y0, int x1, int y1,
                        float scale, @Nullable ScreenRectangle scissorArea, @Nullable ScreenRectangle bounds) implements PictureInPictureRenderState {}

    public float yaw = 35, pitch = 25;

    /** Side config face for a model direction, with the model facing north. */
    public static SideConfig.Face face(Direction d) {
        return SideConfig.face(Direction.NORTH, d);
    }

    private static float size(int w, int h) {
        return Math.min(w, h) * 0.5F;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public void draw(GuiGraphicsExtractor g, BlockState machine, @Nullable BlockEntity be, java.util.function.Function<SideConfig.Face, SideConfig.Mode> modes,
            int x0, int y0, int x1, int y1, int mouseX, int mouseY, float partial) {
        BlockState shown = machine.hasProperty(MachineBlock.FACING) ? machine.setValue(MachineBlock.FACING, Direction.NORTH) : machine;
        BlockModelRenderState block = new BlockModelRenderState();
        Minecraft.getInstance().getBlockModelResolver().update(block, shown, CONTEXT);
        BlockEntityRenderer renderer = be == null ? null : Minecraft.getInstance().getBlockEntityRenderDispatcher().getRenderer(be);
        BlockEntityRenderState beState = null;
        if (renderer != null && be.hasLevel()) {
            beState = renderer.createRenderState();
            renderer.extractRenderState(be, beState, partial, Vec3.atCenterOf(be.getBlockPos()), null);
            beState.lightCoords = 0xF000F0;
        }
        float beYaw = machine.hasProperty(MachineBlock.FACING) ? machine.getValue(MachineBlock.FACING).toYRot() - Direction.NORTH.toYRot() : 0;
        int[] colors = new int[6];
        for (Direction d : Direction.values()) {
            colors[d.ordinal()] = modes.apply(face(d)).color;
        }
        Direction hover = pick(mouseX, mouseY, x0, y0, x1, y1);
        g.submitPictureInPictureRenderState(new State(block, renderer, beState, beYaw, colors, hover == null ? -1 : hover.ordinal(), yaw, pitch, x0, y0, x1, y1,
                size(x1 - x0, y1 - y0), null, PictureInPictureRenderState.getBounds(x0, y0, x1, y1, null)));
    }

    /** The same model transform the renderer uses, in GUI units with y down. */
    private Matrix4f transform(float size) {
        return new Matrix4f().scale(size, -size, size)
                .rotateX((float) Math.toRadians(pitch)).rotateY((float) Math.toRadians(yaw)).translate(-0.5F, -0.5F, -0.5F);
    }

    /** Which face of the cube is under the mouse, or null. */
    public @Nullable Direction pick(double mx, double my, int x0, int y0, int x1, int y1) {
        if (mx < x0 || mx >= x1 || my < y0 || my >= y1) {
            return null;
        }
        Matrix4f m = transform(size(x1 - x0, y1 - y0));
        float px = (float) (mx - (x0 + x1) / 2.0), py = (float) (my - (y0 + y1) / 2.0);
        Direction best = null;
        float bestDepth = Float.NEGATIVE_INFINITY;
        for (Direction d : Direction.values()) {
            Vector3f[] c = corners(d);
            for (Vector3f v : c) {
                m.transformPosition(v);
            }
            // Facing the viewer (-Z toward the eye in GUI space) and containing the point.
            Vector3f n = new Vector3f(c[1]).sub(c[0]).cross(new Vector3f(c[3]).sub(c[0]));
            if (n.z >= 0 || !inside(px, py, c)) {
                continue;
            }
            float depth = -(c[0].z + c[1].z + c[2].z + c[3].z) / 4;
            if (depth > bestDepth) {
                bestDepth = depth;
                best = d;
            }
        }
        return best;
    }

    private static boolean inside(float x, float y, Vector3f[] q) {
        boolean pos = false, neg = false;
        for (int i = 0; i < 4; i++) {
            Vector3f a = q[i], b = q[(i + 1) % 4];
            float cross = (b.x - a.x) * (y - a.y) - (b.y - a.y) * (x - a.x);
            pos |= cross > 0;
            neg |= cross < 0;
        }
        return !(pos && neg);
    }

    /** A face's corners, counter-clockwise seen from outside. */
    static Vector3f[] corners(Direction d) {
        return switch (d) {
            case DOWN -> new Vector3f[] {v(0, 0, 0), v(1, 0, 0), v(1, 0, 1), v(0, 0, 1)};
            case UP -> new Vector3f[] {v(0, 1, 0), v(0, 1, 1), v(1, 1, 1), v(1, 1, 0)};
            case NORTH -> new Vector3f[] {v(0, 0, 0), v(0, 1, 0), v(1, 1, 0), v(1, 0, 0)};
            case SOUTH -> new Vector3f[] {v(0, 0, 1), v(1, 0, 1), v(1, 1, 1), v(0, 1, 1)};
            case WEST -> new Vector3f[] {v(0, 0, 0), v(0, 0, 1), v(0, 1, 1), v(0, 1, 0)};
            case EAST -> new Vector3f[] {v(1, 0, 0), v(1, 1, 0), v(1, 1, 1), v(1, 0, 1)};
        };
    }

    private static Vector3f v(float x, float y, float z) {
        return new Vector3f(x, y, z);
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
                pose.pushPose(); pose.translate(0.5, 0, 0.5); pose.mulPose(Axis.YP.rotationDegrees(s.beYaw())); pose.translate(-0.5, 0, -0.5);
                ((BlockEntityRenderer) s.renderer()).submit(s.beState(), pose, collector, new CameraRenderState()); pose.popPose();
            }
            collector.submitCustomGeometry(pose, RenderTypes.lightning(), (p, buf) -> {
                for (Direction d : Direction.values()) {
                    int c = s.faceColors()[d.ordinal()];
                    boolean hot = d.ordinal() == s.hover();
                    frame(buf, p, d, 0.004F, hot ? 1 : ((c >> 16) & 255) / 255F,
                            hot ? 1 : ((c >> 8) & 255) / 255F, hot ? 1 : (c & 255) / 255F);
                }
            });
        }

        private static void frame(VertexConsumer buf, PoseStack.Pose p, Direction d, float out, float r, float g, float b) {
            Vector3f[] c = corners(d);
            float ox = d.getStepX() * out, oy = d.getStepY() * out, oz = d.getStepZ() * out;
            Vector3f center = new Vector3f(); for (Vector3f v : c) center.add(v); center.mul(0.25F);
            for (int i = 0; i < 4; i++) {
                Vector3f a = c[i], next = c[(i + 1) % 4];
                for (Vector3f v : new Vector3f[]{a, next, new Vector3f(next).lerp(center, 0.055F), new Vector3f(a).lerp(center, 0.055F)})
                    buf.addVertex(p, v.x + ox, v.y + oy, v.z + oz).setColor(r, g, b, 0.65F);
            }
        }

        @Override
        protected float getTranslateY(int height, int guiScale) {
            return height / 2F;
        }

        @Override
        protected String getTextureLabel() {
            return "powerfactor side config";
        }
    }
}
