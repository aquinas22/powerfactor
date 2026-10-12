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
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * The Config tab's side picker: a 3D model of the machine you can drag to turn. Each face is tinted
 * with its mode (blue in, orange out, green both, grey closed); the face under the mouse lights up,
 * and clicking it cycles the mode. The model is shown facing north, so faces map to the machine's
 * own front/back/left/right.
 */
public final class SideConfigView {
    private static final BlockDisplayContext CONTEXT = BlockDisplayContext.create();

    public record State(BlockModelRenderState block, int[] faceColors, int hover, float yaw, float pitch, int x0, int y0, int x1, int y1,
                        float scale, @Nullable ScreenRectangle scissorArea, @Nullable ScreenRectangle bounds) implements PictureInPictureRenderState {}

    public float yaw = 35, pitch = 25;

    /** Side config face for a model direction, with the model facing north. */
    public static SideConfig.Face face(Direction d) {
        return SideConfig.face(Direction.NORTH, d);
    }

    private static float size(int w, int h) {
        return Math.min(w, h) * 0.5F;
    }

    public void draw(GuiGraphicsExtractor g, BlockState machine, java.util.function.Function<SideConfig.Face, SideConfig.Mode> modes,
            int x0, int y0, int x1, int y1, int mouseX, int mouseY) {
        BlockState shown = machine.hasProperty(MachineBlock.FACING) ? machine.setValue(MachineBlock.FACING, Direction.NORTH) : machine;
        BlockModelRenderState block = new BlockModelRenderState();
        Minecraft.getInstance().getBlockModelResolver().update(block, shown, CONTEXT);
        int[] colors = new int[6];
        for (Direction d : Direction.values()) {
            colors[d.ordinal()] = modes.apply(face(d)).color;
        }
        Direction hover = pick(mouseX, mouseY, x0, y0, x1, y1);
        g.submitPictureInPictureRenderState(new State(block, colors, hover == null ? -1 : hover.ordinal(), yaw, pitch, x0, y0, x1, y1,
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
        protected void renderToTexture(State s, PoseStack pose, SubmitNodeCollector collector) {
            Minecraft.getInstance().gameRenderer.lighting().setupFor(Lighting.Entry.ENTITY_IN_UI);
            pose.mulPose(Axis.XP.rotationDegrees(180 + s.pitch()));
            pose.mulPose(Axis.YP.rotationDegrees(s.yaw()));
            pose.translate(-0.5F, -0.5F, -0.5F);
            s.block().submit(pose, collector, 0xF000F0, OverlayTexture.NO_OVERLAY, 0);
            collector.submitCustomGeometry(pose, RenderTypes.lightning(), (p, buf) -> {
                for (Direction d : Direction.values()) {
                    int c = s.faceColors()[d.ordinal()];
                    boolean hot = d.ordinal() == s.hover();
                    float a = hot ? 0.75F : 0.42F;
                    quad(buf, p, d, 0.004F, ((c >> 16) & 255) / 255F, ((c >> 8) & 255) / 255F, (c & 255) / 255F, a);
                    if (hot) {
                        quad(buf, p, d, 0.008F, 1, 1, 1, 0.25F);
                    }
                }
            });
        }

        private static void quad(VertexConsumer buf, PoseStack.Pose p, Direction d, float out, float r, float g, float b, float a) {
            Vector3f[] c = corners(d);
            float ox = d.getStepX() * out, oy = d.getStepY() * out, oz = d.getStepZ() * out;
            for (Vector3f v : c) {
                buf.addVertex(p, v.x + ox, v.y + oy, v.z + oz).setColor(r, g, b, a);
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
