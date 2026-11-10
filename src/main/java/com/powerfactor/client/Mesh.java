package com.powerfactor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Textured geometry for the large machines, built once and drawn through
 * {@code submitCustomGeometry}. A {@link Builder} works in model pixels (sixteen to the block) with
 * the same axes as the world (+Y up) and maps every face to a tile of the machine's texture sheet
 * at one texel per pixel, so beams, wheels and curved plates keep the density of a block texture.
 */
public final class Mesh {
    /** A rectangle of the texture sheet, in texels. */
    public record Tile(float u, float v, float w, float h) {}

    /** Four vertices of eight floats each: position, texture coordinate, normal. */
    private static final int STRIDE = 32;
    private final float[] data;

    private Mesh(float[] data) {
        this.data = data;
    }

    public void emit(VertexConsumer out, PoseStack.Pose pose, int color, int light) {
        emit(out, pose, color, light, 0, 0);
    }

    /** Draws with the texture shifted by ({@code du}, {@code dv}) in UV units, for running belts and flames. */
    public void emit(VertexConsumer out, PoseStack.Pose pose, int color, int light, float du, float dv) {
        for (int i = 0; i < data.length; i += 8) {
            out.addVertex(pose, data[i], data[i + 1], data[i + 2]).setColor(color).setUv(data[i + 3] + du, data[i + 4] + dv)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, data[i + 5], data[i + 6], data[i + 7]);
        }
    }

    public static Builder builder(int textureWidth, int textureHeight) {
        return new Builder(textureWidth, textureHeight);
    }

    public static final class Builder {
        private static final float PIXEL = 1 / 16F;
        private final float texW, texH;
        private final List<float[]> quads = new ArrayList<>();
        private final ArrayDeque<Matrix4f> stack = new ArrayDeque<>();
        private Matrix4f transform = new Matrix4f();
        private Tile tile = new Tile(0, 0, 16, 16);
        private boolean stretch;

        private Builder(int textureWidth, int textureHeight) {
            texW = textureWidth;
            texH = textureHeight;
        }

        /** Faces take as much of this tile as their own size, anchored at its top-left corner. */
        public Builder tile(Tile tile) {
            this.tile = tile;
            stretch = false;
            return this;
        }

        /** Faces are covered by the whole tile, whatever their size: for dials, plates and decals. */
        public Builder decal(Tile tile) {
            this.tile = tile;
            stretch = true;
            return this;
        }

        public Builder push() {
            stack.push(new Matrix4f(transform));
            return this;
        }

        public Builder pop() {
            transform = stack.pop();
            return this;
        }

        public Builder translate(float x, float y, float z) {
            transform.translate(x, y, z);
            return this;
        }

        public Builder rotateX(float degrees) {
            transform.rotateX((float) Math.toRadians(degrees));
            return this;
        }

        public Builder rotateY(float degrees) {
            transform.rotateY((float) Math.toRadians(degrees));
            return this;
        }

        public Builder rotateZ(float degrees) {
            transform.rotateZ((float) Math.toRadians(degrees));
            return this;
        }

        /** An axis-aligned cuboid, all six faces. */
        public Builder box(float x0, float y0, float z0, float x1, float y1, float z1) {
            return box(x0, y0, z0, x1, y1, z1, 0b111111);
        }

        /** A cuboid with only the faces of {@code mask}: bit 0 north (-Z), 1 south, 2 west (-X), 3 east, 4 up, 5 down. */
        public Builder box(float x0, float y0, float z0, float x1, float y1, float z1, int mask) {
            float w = x1 - x0, h = y1 - y0, d = z1 - z0;
            if ((mask & 1) != 0) face(x1, y1, z0, x1, y0, z0, x0, y0, z0, x0, y1, z0, w, h);
            if ((mask & 2) != 0) face(x0, y1, z1, x0, y0, z1, x1, y0, z1, x1, y1, z1, w, h);
            if ((mask & 4) != 0) face(x0, y1, z0, x0, y0, z0, x0, y0, z1, x0, y1, z1, d, h);
            if ((mask & 8) != 0) face(x1, y1, z1, x1, y0, z1, x1, y0, z0, x1, y1, z0, d, h);
            if ((mask & 16) != 0) face(x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, w, d);
            if ((mask & 32) != 0) face(x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1, w, d);
            return this;
        }

        /**
         * One rectangle given as top-left, bottom-left, bottom-right, top-right seen from outside,
         * {@code fw} by {@code fh} pixels. A long face turns the tile so that its long side follows it.
         */
        private void face(float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz,
                float dx, float dy, float dz, float fw, float fh) {
            boolean turn = (fh > fw && tile.w() > tile.h()) || (fw > fh && tile.h() > tile.w());
            float along = turn ? fh : fw, across = turn ? fw : fh;
            float u1 = tile.u() + (stretch ? tile.w() : Math.min(along, tile.w()));
            float v1 = tile.v() + (stretch ? tile.h() : Math.min(across, tile.h()));
            float u0 = tile.u(), v0 = tile.v();
            if (turn) {
                // Quarter turn: the tile's length runs down the face.
                quad(ax, ay, az, u0, v1, bx, by, bz, u1, v1, cx, cy, cz, u1, v0, dx, dy, dz, u0, v0);
            } else {
                quad(ax, ay, az, u0, v0, bx, by, bz, u0, v1, cx, cy, cz, u1, v1, dx, dy, dz, u1, v0);
            }
        }

        /** A free quad, counter-clockwise from outside, with texel coordinates and a flat normal. */
        public Builder quad(float ax, float ay, float az, float au, float av, float bx, float by, float bz, float bu, float bv,
                float cx, float cy, float cz, float cu, float cv, float dx, float dy, float dz, float du, float dv) {
            float e1x = bx - ax, e1y = by - ay, e1z = bz - az, e2x = dx - ax, e2y = dy - ay, e2z = dz - az;
            float nx = e1y * e2z - e1z * e2y, ny = e1z * e2x - e1x * e2z, nz = e1x * e2y - e1y * e2x;
            if (nx * nx + ny * ny + nz * nz < 1e-9F) {
                // A triangle folded into a quad: take the normal from its other corner.
                e1x = cx - bx; e1y = cy - by; e1z = cz - bz; e2x = dx - bx; e2y = dy - by; e2z = dz - bz;
                nx = e1y * e2z - e1z * e2y; ny = e1z * e2x - e1x * e2z; nz = e1x * e2y - e1y * e2x;
            }
            return quad(new float[] {ax, ay, az, au, av, nx, ny, nz, bx, by, bz, bu, bv, nx, ny, nz,
                    cx, cy, cz, cu, cv, nx, ny, nz, dx, dy, dz, du, dv, nx, ny, nz});
        }

        /** A quad of four vertices (x, y, z, u, v, nx, ny, nz) with their own normals, for rounded surfaces. */
        public Builder quad(float[] q) {
            float[] out = new float[STRIDE];
            Vector3f p = new Vector3f(), n = new Vector3f();
            for (int i = 0; i < STRIDE; i += 8) {
                transform.transformPosition(q[i], q[i + 1], q[i + 2], p);
                transform.transformDirection(q[i + 5], q[i + 6], q[i + 7], n);
                if (n.lengthSquared() > 0) n.normalize();
                out[i] = p.x * PIXEL; out[i + 1] = p.y * PIXEL; out[i + 2] = p.z * PIXEL;
                out[i + 3] = q[i + 3] / texW; out[i + 4] = q[i + 4] / texH;
                out[i + 5] = n.x; out[i + 6] = n.y; out[i + 7] = n.z;
            }
            quads.add(out);
            return this;
        }

        /**
         * A wheel on the X axis centred on the origin of the current transform: {@code face} is
         * painted flat on both sides (so spokes turn with it), {@code rim} wraps the tread.
         */
        public Builder wheel(float radius, float x0, float x1, int segments, Tile face, Tile rim) {
            float[][] ring = new float[segments + 1][2];
            for (int i = 0; i <= segments; i++) {
                double a = i * Math.PI * 2 / segments;
                ring[i][0] = (float) Math.cos(a) * radius;   // z
                ring[i][1] = (float) Math.sin(a) * radius;   // y
            }
            for (int i = 0; i < segments; i++) {
                float z0 = ring[i][0], y0 = ring[i][1], z1 = ring[i + 1][0], y1 = ring[i + 1][1];
                float ru0 = rim.u() + rim.w() * i / segments, ru1 = rim.u() + rim.w() * (i + 1) / segments;
                quad(new float[] {
                        x0, y0, z0, ru0, rim.v(), 0, y0, z0,
                        x1, y0, z0, ru0, rim.v() + rim.h(), 0, y0, z0,
                        x1, y1, z1, ru1, rim.v() + rim.h(), 0, y1, z1,
                        x0, y1, z1, ru1, rim.v(), 0, y1, z1});
                // Side discs as fans of triangles folded into quads.
                float fu0 = face.u() + face.w() * (0.5F + z0 / (2 * radius)), fv0 = face.v() + face.h() * (0.5F - y0 / (2 * radius));
                float fu1 = face.u() + face.w() * (0.5F + z1 / (2 * radius)), fv1 = face.v() + face.h() * (0.5F - y1 / (2 * radius));
                float cu = face.u() + face.w() / 2, cv = face.v() + face.h() / 2;
                quad(new float[] {
                        x1, 0, 0, cu, cv, 1, 0, 0, x1, y1, z1, fu1, fv1, 1, 0, 0,
                        x1, y0, z0, fu0, fv0, 1, 0, 0, x1, 0, 0, cu, cv, 1, 0, 0});
                quad(new float[] {
                        x0, 0, 0, cu, cv, -1, 0, 0, x0, y0, z0, fu0, fv0, -1, 0, 0,
                        x0, y1, z1, fu1, fv1, -1, 0, 0, x0, 0, 0, cu, cv, -1, 0, 0});
            }
            return this;
        }

        /**
         * A convex outline in the Z-Y plane, extruded along X. Both sides show {@code face}, fitted
         * to the outline's bounds; {@code rim} follows the edge at one texel per pixel.
         */
        public Builder plate(float[][] outline, float x0, float x1, Tile face, Tile rim) {
            float minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE, minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE, mz = 0, my = 0;
            for (float[] p : outline) {
                minZ = Math.min(minZ, p[0]); maxZ = Math.max(maxZ, p[0]); minY = Math.min(minY, p[1]); maxY = Math.max(maxY, p[1]);
                mz += p[0] / outline.length; my += p[1] / outline.length;
            }
            float cu = face.u() + face.w() * (mz - minZ) / (maxZ - minZ), cv = face.v() + face.h() * (maxY - my) / (maxY - minY);
            float run = 0;
            for (int i = 0; i < outline.length; i++) {
                float[] a = outline[i], b = outline[(i + 1) % outline.length];
                float au = face.u() + face.w() * (a[0] - minZ) / (maxZ - minZ), av = face.v() + face.h() * (maxY - a[1]) / (maxY - minY);
                float bu = face.u() + face.w() * (b[0] - minZ) / (maxZ - minZ), bv = face.v() + face.h() * (maxY - b[1]) / (maxY - minY);
                // The outline runs counter-clockwise seen from +X.
                quad(new float[] {
                        x1, my, mz, cu, cv, 1, 0, 0, x1, a[1], a[0], au, av, 1, 0, 0,
                        x1, b[1], b[0], bu, bv, 1, 0, 0, x1, my, mz, cu, cv, 1, 0, 0});
                quad(new float[] {
                        x0, my, mz, cu, cv, -1, 0, 0, x0, b[1], b[0], bu, bv, -1, 0, 0,
                        x0, a[1], a[0], au, av, -1, 0, 0, x0, my, mz, cu, cv, -1, 0, 0});
                float length = (float) Math.hypot(b[0] - a[0], b[1] - a[1]);
                float ru0 = rim.u() + Math.min(run, rim.w()), ru1 = rim.u() + Math.min(run + length, rim.w());
                run += length;
                quad(x0, a[1], a[0], ru0, rim.v(), x0, b[1], b[0], ru1, rim.v(),
                        x1, b[1], b[0], ru1, rim.v() + rim.h(), x1, a[1], a[0], ru0, rim.v() + rim.h());
            }
            return this;
        }

        public Mesh build() {
            float[] data = new float[quads.size() * STRIDE];
            for (int i = 0; i < quads.size(); i++) {
                System.arraycopy(quads.get(i), 0, data, i * STRIDE, STRIDE);
            }
            return new Mesh(data);
        }
    }
}
