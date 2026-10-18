package com.powerfactor.client.pocketvoid;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.powerfactor.client.fx.Fx;
import java.util.function.Consumer;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.NoDataSpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * The inside of the Pocket Void's frame cube: a box whose walls open onto the End's starfield (wound inward, so you
 * always look through the near side at the far walls), and floating in the middle a black hole: a lensing halo, a
 * spinning accretion disk, a photon ring hugging the horizon and motes of matter spiralling in. The Deep Pocket Void
 * burns gold and runs a second disk. The frame model's trim texture is animated, which keeps the GUI redrawing it.
 */
public class PocketVoidRenderer implements NoDataSpecialModelRenderer {
    private static final int VIOLET = 0xFFB18CFF, ORANGE = 0xFFFFA040, HOT = 0xFFFFF0D0, GOLD = 0xFFFFD36A;
    /** The walls sit just inside the bars, so the bars hide their edges. */
    private static final float WALL = 1.4F / 16;
    private final boolean deep;

    public PocketVoidRenderer(boolean deep) {
        this.deep = deep;
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector c, int light, int overlay, boolean foil, int outline) {
        float t = (Util.getMillis() % 3_600_000L) / 50F;
        walls(c, pose);
        pose.pushPose();
        pose.translate(0.5F, 0.5F + 0.012F * Mth.sin(t * 0.07F), 0.5F);
        // Which way the viewer is, in the item's own space (the camera looks down -Z), and a turn that faces it. Built
        // from the inverse of the pose rather than its rotation, as the GUI draws items through a mirroring matrix.
        Vector3f view = new org.joml.Matrix3f(pose.last().pose()).invert().transform(new Vector3f(0, 0, 1)).normalize();
        Vector3f right = new Vector3f(0, 1, 0).cross(view);
        if (right.lengthSquared() < 1e-6F) right.set(1, 0, 0);
        right.normalize();
        Vector3f up = new Vector3f(view).cross(right).normalize();
        Quaternionf face = new org.joml.Matrix3f(right, up, view).getNormalizedRotation(new Quaternionf());
        // The disk leans toward the viewer, so it always reads as a ring rather than a line.
        Vector3f tilt = new Vector3f(0, 1, 0).add(new Vector3f(view).mul(0.9F)).normalize();
        Quaternionf diskTurn = new Quaternionf().rotationTo(0, 1, 0, tilt.x, tilt.y, tilt.z);
        float r = deep ? 0.135F : 0.12F;
        int rim = deep ? GOLD : VIOLET;
        // Light bent round the hole.
        Fx.glow(c, pose, face, r * 5.6F, Fx.alpha(rim, 0.42F + 0.08F * Mth.sin(t * 0.11F)));
        Fx.glow(c, pose, face, r * 3.4F, Fx.alpha(ORANGE, 0.5F));
        // The accretion disk, tilted and wobbling a little, hottest at the inner edge.
        pose.pushPose();
        pose.mulPose(diskTurn);
        pose.mulPose(Axis.ZP.rotationDegrees(6 * Mth.sin(t * 0.04F)));
        int rings = deep ? 4 : 3;
        for (int k = 0; k < rings; k++) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(t * (9 - k * 1.6F)));
            Fx.ring(c, pose, r * (1.5F + k * (deep ? 0.42F : 0.5F)), r * (0.4F + 0.06F * k), 48, t * 0.06F, Fx.alpha(Fx.mix(HOT, deep ? GOLD : ORANGE, k / (rings - 1F)),
                    0.95F - k * 0.17F));
            pose.popPose();
        }
        if (deep) {
            // A second, steeper disk: the Deep one's hole is spinning faster than it can settle.
            pose.pushPose();
            pose.mulPose(Axis.XP.rotationDegrees(64));
            pose.mulPose(Axis.YP.rotationDegrees(-t * 13));
            Fx.ring(c, pose, r * 2.2F, r * 0.26F, 48, -t * 0.09F, Fx.alpha(VIOLET, 0.6F));
            pose.popPose();
        }
        pose.popPose();
        // Matter falling in: motes spiralling down to the horizon, brightening as they go.
        for (int i = 0; i < 6; i++) {
            float life = (t * 0.012F + i / 6F) % 1F;
            float a = i * 1.047F + life * 9;
            float d = r * (3.2F - 2.1F * life);
            pose.pushPose();
            pose.mulPose(diskTurn);
            pose.translate(Mth.cos(a) * d, 0, Mth.sin(a) * d);
            Fx.sprite(c, pose, face, Fx.SPARK, 0.05F + 0.04F * life, Fx.alpha(Fx.mix(rim, HOT, life), 0.25F + 0.7F * life * (1 - life) * 3));
            pose.popPose();
        }
        // The photon ring, always facing you, and the horizon itself: nothing at all.
        pose.pushPose();
        pose.mulPose(face);
        pose.mulPose(Axis.XP.rotationDegrees(90));
        Fx.ring(c, pose, r * 1.12F, r * 0.2F, 48, -t * 0.1F, Fx.alpha(HOT, 1F));
        pose.popPose();
        horizon(c, pose, r);
        pose.popPose();
    }

    /** The six inner walls, each wound to face the middle so the near ones cull away. */
    private static void walls(SubmitNodeCollector c, PoseStack pose) {
        float a = WALL, b = 1 - WALL;
        c.submitCustomGeometry(pose, RenderTypes.endPortal(), (p, buf) -> {
            // Down (floor, facing up), up (ceiling, facing down), then north, south, west, east.
            quad(buf, p, a, a, a, a, a, b, b, a, b, b, a, a);
            quad(buf, p, a, b, a, b, b, a, b, b, b, a, b, b);
            quad(buf, p, a, a, a, b, a, a, b, b, a, a, b, a);
            quad(buf, p, a, a, b, a, b, b, b, b, b, b, a, b);
            quad(buf, p, a, a, a, a, b, a, a, b, b, a, a, b);
            quad(buf, p, b, a, a, b, a, b, b, b, b, b, b, a);
        });
    }

    private static void quad(VertexConsumer buf, PoseStack.Pose p, float... v) {
        for (int i = 0; i < 12; i += 3) buf.addVertex(p, v[i], v[i + 1], v[i + 2]);
    }

    /** A solid black sphere. */
    private static void horizon(SubmitNodeCollector c, PoseStack pose, float r) {
        int lat = 8, lon = 14;
        c.submitCustomGeometry(pose, RenderTypes.entitySolid(Fx.GLOW), (p, b) -> {
            for (int i = 0; i < lat; i++) {
                double t0 = Math.PI * i / lat - Math.PI / 2, t1 = Math.PI * (i + 1) / lat - Math.PI / 2;
                for (int j = 0; j < lon; j++) {
                    double p0 = 2 * Math.PI * j / lon, p1 = 2 * Math.PI * (j + 1) / lon;
                    double[][] q = {{t0, p0}, {t1, p0}, {t1, p1}, {t0, p1}};
                    for (double[] v : q) {
                        float x = (float) (Math.cos(v[0]) * Math.cos(v[1])), y = (float) Math.sin(v[0]), z = (float) (Math.cos(v[0]) * Math.sin(v[1]));
                        b.addVertex(p, x * r, y * r, z * r).setColor(0xFF000000).setUv(0.5F, 0.5F).setOverlay(OverlayTexture.NO_OVERLAY)
                                .setLight(Fx.FULL_BRIGHT).setNormal(p, x, y, z);
                    }
                }
            }
        });
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        for (int i = 0; i < 8; i++) output.accept(new Vector3f(i & 1, (i >> 1) & 1, (i >> 2) & 1));
    }

    public record Unbaked(boolean deep) implements NoDataSpecialModelRenderer.Unbaked {
        public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.BOOL.optionalFieldOf("deep", false).forGetter(Unbaked::deep)).apply(i, Unbaked::new));

        @Override
        public SpecialModelRenderer<Void> bake(SpecialModelRenderer.BakingContext context) {
            return new PocketVoidRenderer(deep);
        }

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }
    }
}
