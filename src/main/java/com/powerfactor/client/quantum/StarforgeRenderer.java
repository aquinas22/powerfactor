package com.powerfactor.client.quantum;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.powerfactor.client.fx.Fx;
import com.powerfactor.machine.MachineBlock;
import com.powerfactor.quantum.StarforgeBlockEntity;
import com.powerfactor.registry.ModQuantum;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Starforge at work: the qubit lens focuses a plasma column down into the crucible, the four
 * pylons arc into it, heat rings rise, and a finished paxel leaves a white-hot flash.
 */
public class StarforgeRenderer implements BlockEntityRenderer<StarforgeBlockEntity, StarforgeRenderer.State> {
    private static final int HOT = 0xFFFF9A3C, CORE = 0xFFFFF0D8, VIOLET = 0xFFB18CFF, CYAN = 0xFF6FE3FF;

    public static class State extends BlockEntityRenderState {
        boolean formed, running;
        float time, progress;
        int flash;
        Vec3 crucible = Vec3.ZERO, lens = Vec3.ZERO;
        final List<Vec3> pylons = new ArrayList<>();
        long seed;
    }

    public StarforgeRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(StarforgeBlockEntity be, State s, float partial, Vec3 camera, ModelFeatureRenderer.@Nullable CrumblingOverlay crumble) {
        BlockEntityRenderer.super.extractRenderState(be, s, partial, camera, crumble);
        s.formed = be.formed();
        s.running = be.getBlockState().getValue(MachineBlock.RUNNING);
        s.time = be.getLevel() == null ? 0 : (be.getLevel().getGameTime() % 72000L) + partial;
        s.progress = be.clientProgress();
        s.flash = be.flash();
        s.seed = be.getBlockPos().asLong();
        s.pylons.clear();
        BlockPos origin = be.getBlockPos();
        var facing = be.getBlockState().getValue(MachineBlock.FACING);
        for (var part : StarforgeBlockEntity.STRUCTURE.parts()) {
            Vec3 at = Vec3.atCenterOf(StarforgeBlockEntity.STRUCTURE.worldPos(origin, facing, part)).subtract(Vec3.atLowerCornerOf(origin));
            var block = part.block().get();
            if (block == ModQuantum.STARFORGE_CRUCIBLE.get()) s.crucible = at;
            else if (block == ModQuantum.STARFORGE_PYLON.get()) s.pylons.add(at.add(0, 0.45, 0));
            else if (block == ModQuantum.STORAGE_BLOCKS.get("qubit_crystal").get()) s.lens = at.add(0, -0.5, 0);
        }
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector c, CameraRenderState camera) {
        if (!s.formed) return;
        float t = s.time;
        float heat = s.running ? 0.45F + 0.55F * s.progress : 0.15F;
        Vec3 bowl = s.crucible.add(0, 0.25, 0);
        // Molten crucible glow, always faintly alive once formed.
        pose.pushPose();
        pose.translate(bowl.x, bowl.y, bowl.z);
        Fx.glow(c, pose, camera.orientation, 1.0F + 0.8F * heat, Fx.alpha(HOT, 0.4F + 0.5F * heat));
        Fx.glow(c, pose, camera.orientation, 0.5F, Fx.alpha(CORE, heat));
        pose.popPose();
        // The lens: a violet glow and slow ring.
        pose.pushPose();
        pose.translate(s.lens.x, s.lens.y, s.lens.z);
        Fx.glow(c, pose, camera.orientation, 0.9F, Fx.alpha(VIOLET, 0.35F + 0.5F * heat));
        pose.mulPose(Axis.YP.rotationDegrees(t * 3));
        Fx.ring(c, pose, 0.42F, 0.05F, 32, t * 0.04F, Fx.alpha(CYAN, 0.3F + 0.6F * heat));
        pose.popPose();
        for (Vec3 p : s.pylons) {
            pose.pushPose();
            pose.translate(p.x, p.y, p.z);
            Fx.glow(c, pose, camera.orientation, 0.45F, Fx.alpha(CYAN, 0.4F + 0.5F * heat));
            pose.popPose();
        }
        if (s.running) {
            // Plasma column from lens to crucible.
            float flicker = 0.85F + 0.15F * Mth.sin(t * 3.3F);
            Fx.beam(c, pose, s.lens, bowl, 0.14F * flicker * (0.6F + s.progress), -t * 0.35F, Fx.alpha(CORE, flicker), Fx.alpha(HOT, 0.55F));
            // Rising heat rings.
            for (int k = 0; k < 3; k++) {
                float f = ((t * 0.03F) + k / 3F) % 1F;
                pose.pushPose();
                pose.translate(bowl.x, bowl.y + f * (s.lens.y - bowl.y), bowl.z);
                Fx.ring(c, pose, 0.45F - 0.25F * f, 0.04F, 32, 0, Fx.alpha(HOT, (1 - f) * 0.8F));
                pose.popPose();
            }
            // Pylon arcs.
            long tick = (long) t;
            RandomSource r = RandomSource.create(tick / 2 * 41 + s.seed);
            List<Vec3[]> bolts = new ArrayList<>();
            for (Vec3 p : s.pylons) if (r.nextFloat() < 0.4F + 0.5F * s.progress) bolts.add(new Vec3[] {p, bowl});
            long seed = r.nextLong();
            c.submitCustomGeometry(pose, RenderTypes.lightning(), (pp, buf) -> {
                RandomSource j = RandomSource.create(seed);
                for (Vec3[] b : bolts) {
                    Vec3 prev = b[0];
                    for (int i = 1; i <= 7; i++) {
                        Vec3 next = b[0].lerp(b[1], i / 7.0);
                        if (i < 7) next = next.add((j.nextDouble() - .5) * .25, (j.nextDouble() - .5) * .2, (j.nextDouble() - .5) * .25);
                        seg(buf, pp, prev, next, 0.022F);
                        prev = next;
                    }
                }
            });
        }
        if (s.flash > 0) {
            float f = s.flash / 30F;
            pose.pushPose();
            pose.translate(bowl.x, bowl.y + 0.3, bowl.z);
            Fx.glow(c, pose, camera.orientation, 5 * (1.2F - f), Fx.alpha(0xFFFFFFFF, f));
            Fx.ring(c, pose, 0.3F + 3F * (1 - f), 0.2F * f, 48, 0, Fx.alpha(HOT, f));
            pose.popPose();
        }
    }

    private static void seg(com.mojang.blaze3d.vertex.VertexConsumer buf, PoseStack.Pose p, Vec3 a, Vec3 b, float w) {
        Vec3 d = b.subtract(a).normalize();
        Vec3 u = Math.abs(d.y) < 0.9 ? d.cross(new Vec3(0, 1, 0)).normalize() : d.cross(new Vec3(1, 0, 0)).normalize();
        Vec3 v = d.cross(u).normalize();
        for (Vec3 off : new Vec3[] {u.scale(w), v.scale(w)}) {
            for (Vec3 q : new Vec3[] {a.subtract(off), b.subtract(off), b.add(off), a.add(off)}) {
                buf.addVertex(p, (float) q.x, (float) q.y, (float) q.z).setColor(0.75F, 0.9F, 1F, 0.85F);
            }
        }
    }

    @Override
    public AABB getRenderBoundingBox(StarforgeBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(3).expandTowards(0, 3, 0);
    }
}
