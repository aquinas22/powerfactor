package com.powerfactor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.powerfactor.client.fx.Fx;
import com.powerfactor.machine.MachineBlock;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Moving parts for machines whose models are otherwise still: each kind shows what it is doing, and only
 * while it is doing it (the block's RUNNING state). Idle machines keep a faint standby sign of life.
 *
 * <ul>
 *   <li>Solar panel: a sun glint sweeping the cells while it generates.</li>
 *   <li>Charging pad: rings of light rising off the pad while it charges something.</li>
 *   <li>Grow lamp: the light pool below it breathing, with photon motes drifting down.</li>
 *   <li>Radio tower: a red aviation light blinking at the tip, and transmission rings expanding while on air.</li>
 *   <li>Geothermal generator: a glow from the bore and steam puffing from the vent.</li>
 *   <li>Thermoelectric generator: heat glowing up the fins and shimmering off the top.</li>
 *   <li>Transformer: corona glow on the bushings, humming with load, and the odd crackle.</li>
 *   <li>Capacitor banks: charge shimmering up the glass cans while switched in.</li>
 *   <li>Logic controller: a red glow pulsing on top while its output is on.</li>
 * </ul>
 */
public class AmbientMachineRenderer<T extends BlockEntity> implements BlockEntityRenderer<T, AmbientMachineRenderer.State> {
    public enum Kind { SOLAR, CHARGING_PAD, GROW_LAMP, RADIO, GEOTHERMAL, THERMOELECTRIC, TRANSFORMER, CAPACITOR, LOGIC }

    public static class State extends BlockEntityRenderState {
        boolean running;
        float time;
        long seed;
    }

    private final Kind kind;
    /** Last game tick each machine spawned particles, so a frame rate change doesn't change the puff rate. */
    private static final Map<BlockPos, Long> LAST_PUFF = new WeakHashMap<>();

    public AmbientMachineRenderer(BlockEntityRendererProvider.Context context, Kind kind) {
        this.kind = kind;
    }

    public static <T extends BlockEntity> BlockEntityRendererProvider<T, ?> of(Kind kind) {
        return PowerConnections.wrap(ctx -> new AmbientMachineRenderer<T>(ctx, kind));
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(T be, State s, float partial, Vec3 camera, ModelFeatureRenderer.@Nullable CrumblingOverlay crumble) {
        BlockEntityRenderer.super.extractRenderState(be, s, partial, camera, crumble);
        var state = be.getBlockState();
        s.running = running(state);
        Level level = be.getLevel();
        s.time = level == null ? 0 : (level.getGameTime() % 72000L) + partial;
        s.seed = be.getBlockPos().asLong();
        if (level != null && s.running) puff(level, be.getBlockPos());
    }

    /** The block's "running" flag, whichever block class declares it (not every device is a MachineBlock). */
    private static boolean running(net.minecraft.world.level.block.state.BlockState state) {
        if (state.hasProperty(MachineBlock.RUNNING)) return state.getValue(MachineBlock.RUNNING);
        for (var p : state.getProperties()) {
            if (p.getName().equals("running") && p instanceof net.minecraft.world.level.block.state.properties.BooleanProperty b) return state.getValue(b);
        }
        return false;
    }

    /** Particle effects, at most once per game tick per machine. */
    private void puff(Level level, BlockPos pos) {
        long tick = level.getGameTime();
        Long last = LAST_PUFF.get(pos);
        if (last != null && last == tick) return;
        LAST_PUFF.put(pos.immutable(), tick);
        var r = level.getRandom();
        double x = pos.getX() + 0.5, y = pos.getY(), z = pos.getZ() + 0.5;
        switch (kind) {
            case GEOTHERMAL -> {
                if (tick % 6 == 0) level.addParticle(ParticleTypes.CLOUD, x + (r.nextDouble() - 0.5) * 0.3, y + 0.9, z + (r.nextDouble() - 0.5) * 0.3,
                        0, 0.06 + r.nextDouble() * 0.03, 0);
            }
            case THERMOELECTRIC -> {
                if (tick % 9 == 0) level.addParticle(ParticleTypes.SMOKE, x + (r.nextDouble() - 0.5) * 0.6, y + 0.9, z + (r.nextDouble() - 0.5) * 0.6,
                        0, 0.02, 0);
            }
            case TRANSFORMER -> {
                if (r.nextInt(90) == 0) level.addParticle(ParticleTypes.ELECTRIC_SPARK, x + (r.nextDouble() - 0.5) * 0.5, y + 1.0, z + (r.nextDouble() - 0.5) * 0.5,
                        0, 0.05, 0);
            }
            default -> {}
        }
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector c, CameraRenderState camera) {
        float t = s.time;
        float phase = Math.floorMod(s.seed, 97) * 0.37F;   // machines side by side don't pulse in lockstep
        switch (kind) {
            case SOLAR -> {
                if (!s.running) return;
                // A glint sliding across the cells, slow and diagonal.
                float f = ((t + phase * 20) % 160) / 160F;
                pose.pushPose();
                pose.translate(-0.1 + 1.2 * f, 4.3 / 16, 0.5);
                pose.mulPose(Axis.XP.rotationDegrees(90));
                Fx.glow(c, pose, camera.orientation, 0.5F, Fx.alpha(0xFFFFF4C8, 0.95F * Mth.sin(f * Mth.PI)));
                pose.popPose();
            }
            case CHARGING_PAD -> {
                pose.pushPose();
                pose.translate(0.5, 2.5 / 16, 0.5);
                if (s.running) {
                    for (int k = 0; k < 3; k++) {
                        float f = ((t * 0.03F + k / 3F) % 1F);
                        pose.pushPose();
                        pose.translate(0, f * 1.4, 0);
                        pose.mulPose(Axis.XP.rotationDegrees(90));
                        Fx.ring(c, pose, 0.42F - f * 0.12F, 0.05F, 24, t * 0.05F, Fx.alpha(0xFF6FE3FF, 1F - f * 0.7F));
                        pose.popPose();
                    }
                } else {
                    pose.mulPose(Axis.XP.rotationDegrees(90));
                    Fx.ring(c, pose, 0.42F, 0.03F, 24, 0, Fx.alpha(0xFF6FE3FF, 0.4F + 0.2F * Mth.sin(t * 0.08F + phase)));
                }
                pose.popPose();
            }
            case GROW_LAMP -> {
                if (!s.running) return;
                // A cone of grow light falling from the diffuser, breathing slowly, with a bright face on the lamp.
                float breathe = 0.85F + 0.15F * Mth.sin(t * 0.1F + phase);
                Fx.beam(c, pose, new Vec3(0.5, 11.8 / 16, 0.5), new Vec3(0.5, -1.4, 0.5), 0.45F, -t * 0.01F,
                        Fx.alpha(0xFFFF8AE6, 0.75F * breathe), Fx.alpha(0xFFB060FF, 0.5F * breathe));
                pose.pushPose();
                pose.translate(0.5, 11.7 / 16, 0.5);
                Fx.glow(c, pose, camera.orientation, 0.9F, Fx.alpha(0xFFFF8AE6, breathe));
                pose.popPose();
                // Photon motes falling toward the crops.
                for (int k = 0; k < 6; k++) {
                    float f = ((t * 0.012F + k / 6F + phase) % 1F);
                    float a = k * 2.4F + phase;
                    pose.pushPose();
                    pose.translate(0.5 + 0.32 * Mth.cos(a), 11.5 / 16 - f * 1.4, 0.5 + 0.32 * Mth.sin(a));
                    Fx.glow(c, pose, camera.orientation, 0.12F, Fx.alpha(k % 2 == 0 ? 0xFFFF9AE8 : 0xFFB8C8FF, 0.95F * (1 - f)));
                    pose.popPose();
                }
            }
            case RADIO -> {
                // Aviation light: always blinking (1 Hz), it's a tower.
                boolean lit = ((t + phase * 20) % 20) < 7;
                pose.pushPose();
                pose.translate(0.5, 16.9 / 16, 0.5);
                Fx.glow(c, pose, camera.orientation, lit ? 0.35F : 0.12F, Fx.alpha(0xFFFF3A2A, lit ? 1F : 0.35F));
                pose.popPose();
                if (s.running) {
                    for (int k = 0; k < 2; k++) {
                        float f = ((t * 0.025F + k / 2F) % 1F);
                        pose.pushPose();
                        pose.translate(0.5, 15.5 / 16, 0.5);
                        pose.mulPose(Axis.XP.rotationDegrees(90));
                        Fx.ring(c, pose, 0.15F + f * 1.6F, 0.05F, 32, 0, Fx.alpha(0xFF8FD8FF, 1F - f));
                        pose.popPose();
                    }
                }
            }
            case GEOTHERMAL -> {
                if (!s.running) return;
                pose.pushPose();
                pose.translate(0.5, 14.3 / 16, 0.5);
                Fx.glow(c, pose, camera.orientation, 0.75F, Fx.alpha(0xFFFF7A2A, 0.8F + 0.2F * Mth.sin(t * 0.13F + phase)));
                pose.popPose();
            }
            case THERMOELECTRIC -> {
                if (!s.running) return;
                // Heat rising: three soft orange wisps climbing off the fins.
                for (int k = 0; k < 3; k++) {
                    float f = ((t * 0.02F + k / 3F + phase) % 1F);
                    pose.pushPose();
                    pose.translate(0.25 + 0.25 * k, 14 / 16F + f * 0.6, 0.5);
                    Fx.glow(c, pose, camera.orientation, 0.42F * (1 - f * 0.4F), Fx.alpha(0xFFFF9A4A, 1F - f));
                    pose.popPose();
                }
            }
            case TRANSFORMER -> {
                float hum = s.running ? 0.75F + 0.25F * Mth.sin(t * 1.9F + phase) : 0.2F;   // 120 Hz hum, aliased to something visible
                for (int k = 0; k < 3; k++) {
                    pose.pushPose();
                    pose.translate(0.3 + 0.2 * k, 16.3 / 16, 0.5);
                    Fx.glow(c, pose, camera.orientation, 0.24F, Fx.alpha(0xFF9AD8FF, hum));
                    pose.popPose();
                }
            }
            case LOGIC -> {
                if (!s.running) return;
                pose.pushPose();
                pose.translate(0.5, 0.75, 0.5);
                Fx.glow(c, pose, camera.orientation, 0.55F, Fx.alpha(0xFFFF3A2A, 0.75F + 0.25F * Mth.sin(t * 0.4F)));
                pose.popPose();
            }
            case CAPACITOR -> {
                if (!s.running) return;
                // Charge shimmer climbing each glass can.
                for (int k = 0; k < 4; k++) {
                    float f = ((t * 0.03F + k * 0.27F + phase) % 1F);
                    pose.pushPose();
                    pose.translate(k < 2 ? 0.3 : 0.7, 2 / 16F + f * 12 / 16F, k % 2 == 0 ? 0.3 : 0.7);
                    Fx.glow(c, pose, camera.orientation, 0.3F, Fx.alpha(0xFF6FE3FF, Mth.sin(f * Mth.PI)));
                    pose.popPose();
                }
            }
        }
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return kind == Kind.RADIO || kind == Kind.CHARGING_PAD;
    }
}
