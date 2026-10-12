package com.powerfactor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/**
 * Hologram of an unfinished multiblock: a pulsing cyan box where a part still needs placing, red
 * where something else is in the way. Positions are relative to the block being rendered.
 */
public final class MultiblockGhost {
    public record Ghost(BlockPos offset, boolean blocked, AABB shape) {}

    public static void submit(PoseStack pose, SubmitNodeCollector collector, List<Ghost> ghosts, float time) {
        if (ghosts.isEmpty()) {
            return;
        }
        float pulse = 0.18F + 0.1F * (float) Math.sin(time * 0.15F);
        List<Ghost> copy = List.copyOf(ghosts);
        collector.submitCustomGeometry(pose, RenderTypes.lightning(), (p, buf) -> {
            for (Ghost g : copy) {
                float r = g.blocked() ? 1F : 0.25F, gr = g.blocked() ? 0.25F : 0.88F, b = g.blocked() ? 0.2F : 1F;
                AABB s = g.shape().move(g.offset());
                box(buf, p, s, r, gr, b, pulse);
                box(buf, p, s.inflate(-0.06), r, gr, b, pulse * 0.6F);
            }
        });
    }

    private static void box(VertexConsumer buf, PoseStack.Pose p, AABB a, float r, float g, float b, float al) {
        float x0 = (float) a.minX, y0 = (float) a.minY, z0 = (float) a.minZ, x1 = (float) a.maxX, y1 = (float) a.maxY, z1 = (float) a.maxZ;
        float[][][] faces = {
                {{x0, y0, z0}, {x0, y1, z0}, {x1, y1, z0}, {x1, y0, z0}}, {{x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}},
                {{x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}, {x0, y1, z0}}, {{x1, y0, z0}, {x1, y1, z0}, {x1, y1, z1}, {x1, y0, z1}},
                {{x0, y1, z0}, {x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}}, {{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}}};
        for (float[][] f : faces) {
            for (float[] v : f) {
                buf.addVertex(p, v[0], v[1], v[2]).setColor(r, g, b, al);
            }
        }
    }

    private MultiblockGhost() {}
}
