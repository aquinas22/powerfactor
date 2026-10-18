package com.powerfactor.client.mob;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.powerfactor.client.fx.Fx;
import com.powerfactor.mob.SpawnerCageBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Spawner Cage: the snared mob turns slowly inside the bars, wrapped in a soul-fire swirl that spins up
 * and brightens as the next copy nears, wisps rising off the top; at the moment of spawning it flares.
 */
public class SpawnerCageRenderer implements BlockEntityRenderer<SpawnerCageBlockEntity, SpawnerCageRenderer.State> {
    private final EntityRenderDispatcher entities;
    /** One display mob per kind, made on demand (the vanilla spawner does the same). */
    private static final java.util.Map<String, Entity> DISPLAY = new java.util.HashMap<>();
    private static @Nullable Level displayLevel;

    public static class State extends BlockEntityRenderState {
        @Nullable EntityRenderState mob;
        float scale = 0.5F, progress, time;
    }

    public SpawnerCageRenderer(BlockEntityRendererProvider.Context ctx) {
        this.entities = ctx.entityRenderer();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    private static @Nullable Entity display(Level level, String id) {
        if (displayLevel != level) {
            DISPLAY.clear();
            displayLevel = level;
        }
        return DISPLAY.computeIfAbsent(id, k -> BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.parse(k))
                .map(t -> (Entity) t.create(level, EntitySpawnReason.LOAD)).map(e -> {
                    e.setId(-1);   // a display entity, as the vanilla spawner marks its own
                    return e;
                }).orElse(null));
    }

    @Override
    public void extractRenderState(SpawnerCageBlockEntity be, State s, float partial, Vec3 camera, ModelFeatureRenderer.@Nullable CrumblingOverlay crumble) {
        BlockEntityRenderer.super.extractRenderState(be, s, partial, camera, crumble);
        s.time = be.getLevel() == null ? 0 : (be.getLevel().getGameTime() % 72000L) + partial;
        s.progress = be.clientProgress();
        s.mob = null;
        if (be.getLevel() != null && !be.clientSoul().isEmpty()) {
            Entity e = display(be.getLevel(), be.clientSoul());
            if (e != null) {
                s.mob = entities.extractEntity(e, partial);
                s.mob.lightCoords = Fx.FULL_BRIGHT;
                s.scale = 0.5F / Math.max(1, Math.max(e.getBbWidth(), e.getBbHeight()));
            }
        }
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector c, CameraRenderState camera) {
        if (s.mob == null) return;
        float p = s.progress, t = s.time;
        pose.pushPose();
        pose.translate(0.5, 0.22, 0.5);
        // The mob, bobbing and turning; it shivers just before a copy is made.
        pose.pushPose();
        float shiver = p > 0.85F ? (p - 0.85F) * 0.25F * Mth.sin(t * 3.1F) : 0;
        pose.translate(shiver, 0.03F * Mth.sin(t * 0.1F), 0);
        pose.mulPose(Axis.YP.rotationDegrees(t * 2.5F));
        pose.scale(s.scale, s.scale, s.scale);
        entities.submit(s.mob, camera, 0, 0, 0, pose, c);
        pose.popPose();
        // Soul-fire swirl: three bands climbing at different speeds, brighter as the copy nears.
        int soul = 0xFF4FE3F0;
        for (int k = 0; k < 3; k++) {
            pose.pushPose();
            float rise = ((t * 0.012F + k / 3F) % 1F);
            pose.translate(0, 0.05F + rise * 0.55F, 0);
            Fx.band(c, pose, 0.36F - rise * 0.08F, 0.06F, 20, t * (0.03F + 0.02F * p) * (k % 2 == 0 ? 1 : -1),
                    Fx.alpha(soul, (0.25F + 0.6F * p) * Mth.sin(rise * Mth.PI)));
            pose.popPose();
        }
        pose.translate(0, 0.3, 0);
        Fx.glow(c, pose, camera.orientation, 0.7F + 0.5F * p, Fx.alpha(soul, 0.15F + 0.35F * p));
        // The flare as it spawns (progress resets to 0, so the flare lives at the first sliver).
        if (p < 0.06F) Fx.glow(c, pose, camera.orientation, 1.8F * (1 - p / 0.06F), Fx.alpha(0xFFB8FFFF, 0.9F * (1 - p / 0.06F)));
        // Wisps rising from the cap.
        for (int k = 0; k < 4; k++) {
            float w = (t * 0.02F + k * 0.25F) % 1F;
            pose.pushPose();
            pose.translate(0.18F * Mth.cos(k * 1.7F + t * 0.05F), 0.45F + w * 0.8F, 0.18F * Mth.sin(k * 1.7F + t * 0.05F));
            Fx.glow(c, pose, camera.orientation, 0.12F, Fx.alpha(soul, (0.3F + 0.5F * p) * (1 - w)));
            pose.popPose();
        }
        pose.popPose();
    }
}
