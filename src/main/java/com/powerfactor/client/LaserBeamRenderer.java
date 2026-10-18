package com.powerfactor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.powerfactor.client.fx.Fx;
import com.powerfactor.laser.LaserNodeBlock;
import com.powerfactor.laser.LaserNodeBlockEntity;
import com.powerfactor.laser.LaserWrenchItem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Laser beams between linked nodes. Each link is drawn once (from the lower-positioned node). A thin
 * cyan carrier is always on; every channel moving something adds its own coloured strand with bright
 * packets racing along it (gold items, blue fluids, red energy). Active lenses spin and glow, and
 * while you hold a Laser Wrench with this node picked, a guide beam runs from it to your hand.
 */
public class LaserBeamRenderer implements BlockEntityRenderer<LaserNodeBlockEntity, LaserBeamRenderer.State> {
    private static final int CARRIER = 0xFF6FE3FF;

    public static class State extends BlockEntityRenderState {
        Direction facing = Direction.DOWN;
        final List<Vec3> partners = new ArrayList<>();
        final List<Integer> partnerActivity = new ArrayList<>();
        final List<Integer> partnerModes = new ArrayList<>();
        int activity, modes;
        float time;
        @Nullable Vec3 guide;
    }

    public LaserBeamRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    /** The lens tip, relative to the node's block origin: 7/16 out from the face it's attached to (the top of the dome). */
    static Vec3 lens(Direction facing) {
        return new Vec3(0.5, 0.5, 0.5).add(Vec3.atLowerCornerOf(facing.getUnitVec3i()).scale(0.5 - 7 / 16.0));
    }

    @Override
    public void extractRenderState(LaserNodeBlockEntity be, State s, float partial, Vec3 camera, ModelFeatureRenderer.@Nullable CrumblingOverlay crumble) {
        BlockEntityRenderer.super.extractRenderState(be, s, partial, camera, crumble);
        s.facing = be.getBlockState().getValue(LaserNodeBlock.FACING);
        s.activity = be.clientActivity();
        int[] m = be.clientModes();
        s.modes = (m[0] != 0 ? 1 : 0) | (m[1] != 0 ? 2 : 0) | (m[2] != 0 ? 4 : 0);
        s.time = be.getLevel() == null ? 0 : (be.getLevel().getGameTime() % 72000L) + partial;
        s.partners.clear();
        s.partnerActivity.clear();
        s.partnerModes.clear();
        BlockPos me = be.getBlockPos();
        Vec3 origin = Vec3.atLowerCornerOf(me);
        for (BlockPos p : be.clientLinks()) {
            if (p.asLong() < me.asLong()) continue;          // the other end draws this one
            if (!(be.getLevel().getBlockEntity(p) instanceof LaserNodeBlockEntity other)) continue;
            Vec3 end = Vec3.atLowerCornerOf(p).add(lens(other.getBlockState().getValue(LaserNodeBlock.FACING))).subtract(origin);
            s.partners.add(end);
            s.partnerActivity.add(other.clientActivity());
            int[] om = other.clientModes();
            s.partnerModes.add((om[0] != 0 ? 1 : 0) | (om[1] != 0 ? 2 : 0) | (om[2] != 0 ? 4 : 0));
        }
        s.guide = null;
        var player = Minecraft.getInstance().player;
        if (player != null) {
            for (var stack : new net.minecraft.world.item.ItemStack[] {player.getMainHandItem(), player.getOffhandItem()}) {
                if (stack.getItem() instanceof LaserWrenchItem && me.equals(LaserWrenchItem.selected(stack))) {
                    // Aim at the wrench in the hand: a little ahead, right and below the eye.
                    Vec3 view = player.getViewVector(partial);
                    Vec3 right = view.cross(new Vec3(0, 1, 0)).normalize();
                    boolean main = stack == player.getMainHandItem();
                    boolean rightHanded = (player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT) == main;
                    Vec3 hand = player.getEyePosition(partial).add(view.scale(0.7)).add(right.scale(rightHanded ? 0.32 : -0.32)).add(0, -0.3, 0);
                    s.guide = hand.subtract(origin);
                }
            }
        }
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector c, CameraRenderState camera) {
        Vec3 from = lens(s.facing);
        int act = s.activity;
        for (int i = 0; i < s.partners.size(); i++) {
            Vec3 to = s.partners.get(i);
            int flow = act | s.partnerActivity.get(i);
            // Carrier: a solid white-cyan rod wrapped in a soft scrolling halo.
            Fx.tube(c, pose, from, to, 0.03F, flow != 0 ? 0xFFF2FDFF : 0xD8C8F4FF);
            Fx.beam(c, pose, from, to, 0.09F, -s.time * 0.2F, Fx.alpha(0xFFE6FCFF, flow != 0 ? 0.8F : 0.6F), Fx.alpha(CARRIER, flow != 0 ? 0.4F : 0.28F));
            Vec3 d = to.subtract(from);
            double len = d.length();
            Vec3 n = d.scale(1 / Math.max(1e-4, len));
            Vec3 side = Math.abs(n.y) < 0.9 ? n.cross(new Vec3(0, 1, 0)).normalize() : n.cross(new Vec3(1, 0, 0)).normalize();
            // A channel gets a strand when both ends have it switched on; it brightens and carries packets while flowing.
            int lit = s.modes & s.partnerModes.get(i);
            for (int ch = 0; ch < 3; ch++) {
                if ((lit & (1 << ch)) == 0) continue;
                boolean moving = (flow & (1 << ch)) != 0;
                int col = LaserNodeScreen.COLORS[ch];
                // Each channel's strand twists around the carrier at its own phase.
                double phase = ch * Math.PI * 2 / 3 + s.time * 0.08;
                Vec3 off = side.scale(0.09 * Math.cos(phase)).add(n.cross(side).scale(0.09 * Math.sin(phase)));
                Fx.tube(c, pose, from.add(off), to.add(off), moving ? 0.024F : 0.014F, moving ? col : Fx.mix(col, 0xFF203040, 0.45F));
                Fx.beam(c, pose, from.add(off), to.add(off), 0.045F, -s.time * 0.5F, Fx.alpha(col, moving ? 0.95F : 0.3F), Fx.alpha(col, moving ? 0.35F : 0.1F));
                if (!moving) continue;
                // Packets racing along the strand.
                int packets = Math.max(2, (int) (len / 2));
                for (int k = 0; k < packets; k++) {
                    double f = ((s.time * (0.05 + 0.02 * ch) / Math.max(1, len) * 6 + (double) k / packets) % 1.0);
                    Vec3 at = from.add(d.scale(f)).add(off);
                    pose.pushPose();
                    pose.translate(at.x, at.y, at.z);
                    Fx.glow(c, pose, camera.orientation, 0.22F, Fx.alpha(col, 0.95F));
                    pose.popPose();
                }
            }
        }
        // Lens: glows when configured; spins and flares while moving things.
        pose.pushPose();
        pose.translate(from.x, from.y, from.z);
        int col = (act & 4) != 0 ? LaserNodeScreen.COLORS[2] : (act & 2) != 0 ? LaserNodeScreen.COLORS[1] : (act & 1) != 0 ? LaserNodeScreen.COLORS[0] : CARRIER;
        Fx.glow(c, pose, camera.orientation, s.modes != 0 ? 0.35F + (act != 0 ? 0.12F * Mth.sin(s.time * 0.6F) : 0) : 0.15F,
                Fx.alpha(col, s.modes != 0 ? 0.85F : 0.3F));
        if (act != 0) {
            pose.mulPose(Axis.YP.rotationDegrees(s.time * 8));
            Fx.ring(c, pose, 0.18F, 0.025F, 16, s.time * 0.2F, Fx.alpha(col, 0.9F));
        }
        pose.popPose();
        if (s.guide != null) {
            Fx.beam(c, pose, from, s.guide, 0.02F, -s.time * 0.6F, Fx.alpha(0xFFFFFFFF, 0.5F), Fx.alpha(CARRIER, 0.35F));
        }
    }

    @Override
    public AABB getRenderBoundingBox(LaserNodeBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(LaserNodeBlockEntity.RANGE + 1);
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }
}
