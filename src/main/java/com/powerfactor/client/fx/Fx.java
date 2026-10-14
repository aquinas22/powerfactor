package com.powerfactor.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.powerfactor.PowerFactor;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * Shared effect geometry for PowerFactor's high-tier machines and weapons: additive glow sprites,
 * textured energy rings, beams and lightning, all built from quads in the submit pass.
 *
 * <p>Additive work uses vanilla's energy-swirl pipeline (textured, additive, emissive, no culling),
 * so overlapping glows brighten rather than occlude. Colours are ARGB; alpha scales the glow.
 */
public final class Fx {
    public static final Identifier GLOW = PowerFactor.id("textures/entity/fx/glow.png");
    public static final Identifier RING = PowerFactor.id("textures/entity/fx/ring.png");
    public static final Identifier BEAM = PowerFactor.id("textures/entity/fx/beam.png");
    public static final Identifier SPARK = PowerFactor.id("textures/entity/fx/spark.png");
    public static final int FULL_BRIGHT = 0xF000F0;

    public static RenderType additive(Identifier texture) {
        return RenderTypes.energySwirl(texture, 0, 0);
    }

    public static RenderType scrolling(Identifier texture, float u, float v) {
        return RenderTypes.energySwirl(texture, u, v);
    }

    static void vertex(VertexConsumer b, PoseStack.Pose p, float x, float y, float z, float u, float v, int argb) {
        b.addVertex(p, x, y, z).setColor(argb).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(p, 0, 1, 0);
    }

    /** A camera-facing glow sprite of the given size at the pose origin. */
    public static void glow(SubmitNodeCollector c, PoseStack pose, Quaternionf camera, float size, int argb) {
        sprite(c, pose, camera, GLOW, size, argb);
    }

    public static void sprite(SubmitNodeCollector c, PoseStack pose, Quaternionf camera, Identifier tex, float size, int argb) {
        pose.pushPose();
        pose.mulPose(camera);
        float h = size / 2;
        c.submitCustomGeometry(pose, additive(tex), (p, b) -> {
            vertex(b, p, -h, -h, 0, 0, 1, argb);
            vertex(b, p, h, -h, 0, 1, 1, argb);
            vertex(b, p, h, h, 0, 1, 0, argb);
            vertex(b, p, -h, h, 0, 0, 0, argb);
        });
        pose.popPose();
    }

    /**
     * A flat textured ring in the XZ plane around the origin, the texture's U running around it. The
     * texture scrolls with {@code spin} so segmented rings appear to chase.
     */
    public static void ring(SubmitNodeCollector c, PoseStack pose, float radius, float width, int segments, float spin, int argb) {
        float r0 = radius - width / 2, r1 = radius + width / 2;
        c.submitCustomGeometry(pose, scrolling(RING, spin, 0), (p, b) -> {
            for (int i = 0; i < segments; i++) {
                double a0 = i * Math.PI * 2 / segments, a1 = (i + 1) * Math.PI * 2 / segments;
                float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
                float u0 = (float) i / segments * 4, u1 = (float) (i + 1) / segments * 4;
                vertex(b, p, c0 * r0, 0, s0 * r0, u0, 0, argb);
                vertex(b, p, c1 * r0, 0, s1 * r0, u1, 0, argb);
                vertex(b, p, c1 * r1, 0, s1 * r1, u1, 1, argb);
                vertex(b, p, c0 * r1, 0, s0 * r1, u0, 1, argb);
            }
        });
    }

    /** A vertical ribbon ring (a short glowing cylinder band) around the origin. */
    public static void band(SubmitNodeCollector c, PoseStack pose, float radius, float height, int segments, float spin, int argb) {
        c.submitCustomGeometry(pose, scrolling(RING, spin, 0), (p, b) -> {
            for (int i = 0; i < segments; i++) {
                double a0 = i * Math.PI * 2 / segments, a1 = (i + 1) * Math.PI * 2 / segments;
                float x0 = (float) Math.cos(a0) * radius, z0 = (float) Math.sin(a0) * radius;
                float x1 = (float) Math.cos(a1) * radius, z1 = (float) Math.sin(a1) * radius;
                float u0 = (float) i / segments * 4, u1 = (float) (i + 1) / segments * 4;
                vertex(b, p, x0, -height / 2, z0, u0, 1, argb);
                vertex(b, p, x1, -height / 2, z1, u1, 1, argb);
                vertex(b, p, x1, height / 2, z1, u1, 0, argb);
                vertex(b, p, x0, height / 2, z0, u0, 0, argb);
            }
        });
    }

    /**
     * A straight beam between two points (pose-local): two crossed textured quads plus a wider soft
     * halo. {@code scroll} animates energy flowing along it.
     */
    public static void beam(SubmitNodeCollector c, PoseStack pose, Vec3 from, Vec3 to, float width, float scroll, int core, int halo) {
        Vec3 d = to.subtract(from);
        double len = d.length();
        if (len < 1e-4) return;
        Vec3 n = d.scale(1 / len);
        Vec3 u = Math.abs(n.y) < 0.95 ? n.cross(new Vec3(0, 1, 0)).normalize() : n.cross(new Vec3(1, 0, 0)).normalize();
        Vec3 v = n.cross(u).normalize();
        float vLen = (float) len;
        for (int pass = 0; pass < 2; pass++) {
            float w = pass == 0 ? width * 3.2F : width;
            int color = pass == 0 ? halo : core;
            if ((color >>> 24) == 0) continue;
            c.submitCustomGeometry(pose, scrolling(BEAM, 0, scroll), (p, b) -> {
                for (Vec3 off : new Vec3[] {u.scale(w / 2), v.scale(w / 2)}) {
                    Vec3 a0 = from.subtract(off), a1 = from.add(off), b0 = to.subtract(off), b1 = to.add(off);
                    vertex(b, p, (float) a0.x, (float) a0.y, (float) a0.z, 0, 0, color);
                    vertex(b, p, (float) a1.x, (float) a1.y, (float) a1.z, 1, 0, color);
                    vertex(b, p, (float) b1.x, (float) b1.y, (float) b1.z, 1, vLen, color);
                    vertex(b, p, (float) b0.x, (float) b0.y, (float) b0.z, 0, vLen, color);
                }
            });
        }
    }

    /** A glowing textured cube centred on the origin (inner energy cores, qubits). */
    public static void cube(SubmitNodeCollector c, PoseStack pose, Identifier tex, float size, int argb) {
        float h = size / 2;
        c.submitCustomGeometry(pose, additive(tex), (p, b) -> {
            float[][] faces = {
                    {-h, -h, -h, h, -h, -h, h, h, -h, -h, h, -h}, {-h, -h, h, -h, h, h, h, h, h, h, -h, h},
                    {-h, -h, -h, -h, h, -h, -h, h, h, -h, -h, h}, {h, -h, -h, h, -h, h, h, h, h, h, h, -h},
                    {-h, h, -h, h, h, -h, h, h, h, -h, h, h}, {-h, -h, -h, -h, -h, h, h, -h, h, h, -h, -h}};
            for (float[] f : faces) {
                vertex(b, p, f[0], f[1], f[2], 0, 0, argb);
                vertex(b, p, f[3], f[4], f[5], 1, 0, argb);
                vertex(b, p, f[6], f[7], f[8], 1, 1, argb);
                vertex(b, p, f[9], f[10], f[11], 0, 1, argb);
            }
        });
    }

    /** An octahedral crystal (two pyramids), textured with an emissive translucent energy texture. */
    public static void crystal(SubmitNodeCollector c, PoseStack pose, Identifier tex, float radius, float height, int argb) {
        c.submitCustomGeometry(pose, RenderTypes.entityTranslucentEmissive(tex), (p, b) -> {
            float[][] ring = {{radius, 0}, {0, radius}, {-radius, 0}, {0, -radius}};
            for (int i = 0; i < 4; i++) {
                float[] a = ring[i], z = ring[(i + 1) % 4];
                for (float tip : new float[] {height, -height}) {
                    // Degenerate quad (triangle) per face; both windings so it shows from inside the glass too.
                    vertexLit(b, p, 0, tip, 0, 0.5F, tip > 0 ? 0 : 1, argb);
                    vertexLit(b, p, a[0], 0, a[1], 0, 0.5F, argb);
                    vertexLit(b, p, z[0], 0, z[1], 1, 0.5F, argb);
                    vertexLit(b, p, z[0], 0, z[1], 1, 0.5F, argb);
                }
            }
        });
    }

    private static void vertexLit(VertexConsumer b, PoseStack.Pose p, float x, float y, float z, float u, float v, int argb) {
        b.addVertex(p, x, y, z).setColor(argb).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(p, 0, 1, 0);
    }

    /**
     * Scale a colour's intensity. Additive blending ignores alpha, so fades scale RGB too; alpha
     * still matters for the pipeline's cut-out threshold.
     */
    public static int alpha(int argb, float a) {
        float k = Math.max(0, Math.min(1, a));
        int al = Math.round(((argb >>> 24) & 0xFF) * k);
        int r = Math.round(((argb >> 16) & 0xFF) * k), g = Math.round(((argb >> 8) & 0xFF) * k), b = Math.round((argb & 0xFF) * k);
        return al << 24 | r << 16 | g << 8 | b;
    }

    /** Blend two RGB colours (alpha from the first). */
    public static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = (int) (((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return (a & 0xFF000000) | r << 16 | g << 8 | bl;
    }

    private Fx() {}
}
