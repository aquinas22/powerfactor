package com.powerfactor.client.charging;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.powerfactor.charging.WirelessChargerBlockEntity;
import com.powerfactor.client.fx.Fx;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Wireless charger: a glowing crown of coil rings, and while it charges, a flickering arc of power from the
 * crown to each player it's feeding. The dimensional charger adds a pillar of light into the sky while it
 * sends power across worlds.
 */
public class ChargerRenderer implements BlockEntityRenderer<WirelessChargerBlockEntity, ChargerRenderer.State> {
    public static class State extends BlockEntityRenderState {
        boolean active, dimensional;
        float time;
        final List<Vec3> targets = new ArrayList<>();
    }

    public ChargerRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(WirelessChargerBlockEntity be, State s, float partial, Vec3 camera, ModelFeatureRenderer.@Nullable CrumblingOverlay crumble) {
        BlockEntityRenderer.super.extractRenderState(be, s, partial, camera, crumble);
        s.active = be.clientActive();
        s.dimensional = be.dimensional();
        s.time = be.getLevel() == null ? 0 : (be.getLevel().getGameTime() % 72000L) + partial;
        s.targets.clear();
        if (be.getLevel() == null) return;
        Vec3 origin = Vec3.atLowerCornerOf(be.getBlockPos());
        for (int id : be.clientCharged()) {
            Entity e = be.getLevel().getEntity(id);
            if (e != null && e.distanceToSqr(origin) < 64 * 64) s.targets.add(e.getPosition(partial).add(0, e.getBbHeight() * 0.55, 0).subtract(origin));
        }
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector c, CameraRenderState camera) {
        float t = s.time;
        int col = s.dimensional ? 0xFFB18CFF : 0xFF6FE3FF;
        Vec3 crown = new Vec3(0.5, s.dimensional ? 1.25 : 1.1, 0.5);
        pose.pushPose();
        pose.translate(crown.x, crown.y, crown.z);
        for (int k = 0; k < 3; k++) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(t * (s.active ? 6 : 1.5F) * (k % 2 == 0 ? 1 : -1)));
            pose.translate(0, -0.12 * k, 0);
            Fx.ring(c, pose, 0.28F - k * 0.05F, 0.03F, 24, t * 0.05F, Fx.alpha(col, s.active ? 0.95F : 0.6F));
            pose.popPose();
        }
        Fx.glow(c, pose, camera.orientation, s.active ? 0.6F + 0.1F * Mth.sin(t * 0.7F) : 0.35F, Fx.alpha(col, s.active ? 1F : 0.6F));
        pose.popPose();
        if (!s.active) return;
        // Arcs: a jagged bolt to each player, re-rolled several times a second.
        long seed = (long) (t / 2);
        for (Vec3 target : s.targets) {
            java.util.Random r = new java.util.Random(seed * 31 + (long) (target.x * 7));
            Vec3 prev = crown;
            int segs = 8;
            for (int i = 1; i <= segs; i++) {
                double f = (double) i / segs;
                Vec3 p = crown.lerp(target, f);
                if (i < segs) p = p.add((r.nextDouble() - 0.5) * 0.5, (r.nextDouble() - 0.5) * 0.5, (r.nextDouble() - 0.5) * 0.5);
                Fx.beam(c, pose, prev, p, 0.035F, t * 0.3F, Fx.alpha(0xFFFFFFFF, 0.9F), Fx.alpha(col, 0.6F));
                prev = p;
            }
        }
        if (s.dimensional) {
            Fx.beam(c, pose, crown, crown.add(0, 96, 0), 0.12F, -t * 0.4F, Fx.alpha(0xFFE6DCFF, 0.85F), Fx.alpha(col, 0.5F));
        }
    }

    @Override
    public AABB getRenderBoundingBox(WirelessChargerBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(48).expandTowards(0, 96, 0);
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }
}
