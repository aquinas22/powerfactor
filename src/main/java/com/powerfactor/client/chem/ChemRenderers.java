package com.powerfactor.client.chem;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.powerfactor.chem.ChemicalReactorBlockEntity;
import com.powerfactor.chem.OreDissolverBlockEntity;
import com.powerfactor.chem.OreMetal;
import com.powerfactor.client.fx.Fx;
import com.powerfactor.client.wash.WashPlantRenderer;
import com.powerfactor.machine.MachineBlock;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** The chemistry machines' moving parts: acid sloshing in glass, a spinning impeller, crystals growing in a vat. */
public final class ChemRenderers {
    static final int ACID = 0xD8E4E25A;

    static float facingDeg(Direction d) {
        return switch (d) {
            case EAST -> -90;
            case SOUTH -> 180;
            case WEST -> 90;
            default -> 0;
        };
    }

    /** Chemical Reactor: an acid level rising in the glass, the impeller turning, bubbles and a burner glow while reacting. */
    public static class Reactor implements BlockEntityRenderer<ChemicalReactorBlockEntity, Reactor.State> {
        public static class State extends BlockEntityRenderState {
            float acid, time;
            boolean running;
        }

        public Reactor(BlockEntityRendererProvider.Context ctx) {}

        @Override
        public State createRenderState() {
            return new State();
        }

        @Override
        public void extractRenderState(ChemicalReactorBlockEntity be, State s, float partial, Vec3 camera, ModelFeatureRenderer.@Nullable CrumblingOverlay crumble) {
            BlockEntityRenderer.super.extractRenderState(be, s, partial, camera, crumble);
            s.acid = be.clientAcid();
            s.running = be.getBlockState().getValueOrElse(MachineBlock.RUNNING, false);
            s.time = be.getLevel() == null ? 0 : (be.getLevel().getGameTime() % 72000L) + partial;
        }

        @Override
        public void submit(State s, PoseStack pose, SubmitNodeCollector c, CameraRenderState camera) {
            int light = s.lightCoords;
            float level = 6.3F / 16 + (8.4F / 16) * Math.max(0.12F, s.acid);
            float slosh = s.running ? 0.01F * Mth.sin(s.time * 0.6F) : 0;
            c.submitCustomGeometry(pose, RenderTypes.entityTranslucent(WashPlantRenderer.TEX), (p, b) -> {
                WashPlantRenderer.box(b, p, 2.3F / 16, 6.1F / 16, 2.3F / 16, 13.7F / 16, level + slosh, 13.7F / 16, WashPlantRenderer.WHITE, ACID, light);
            });
            // Impeller on its shaft, turning while it reacts.
            pose.pushPose();
            pose.translate(0.5, 0, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(s.running ? s.time * 18 : 0));
            c.submitCustomGeometry(pose, RenderTypes.entityCutout(WashPlantRenderer.TEX), (p, b) -> {
                WashPlantRenderer.box(b, p, -0.025F, 7F / 16, -0.025F, 0.025F, 15F / 16, 0.025F, WashPlantRenderer.WHITE, 0xFFB8C4CC, light);
                WashPlantRenderer.box(b, p, -0.3F, 7.4F / 16, -0.04F, 0.3F, 7.9F / 16, 0.04F, WashPlantRenderer.WHITE, 0xFFDDE6EC, light);
                WashPlantRenderer.box(b, p, -0.04F, 7.4F / 16, -0.3F, 0.04F, 7.9F / 16, 0.3F, WashPlantRenderer.WHITE, 0xFFDDE6EC, light);
            });
            pose.popPose();
            if (s.running) {
                for (int k = 0; k < 7; k++) {
                    float f = (s.time * 0.03F + k / 7F) % 1F;
                    pose.pushPose();
                    pose.translate(0.25 + (k * 0.37 % 0.5), 6.5F / 16 + f * (level - 6.5F / 16), 0.25 + (k * 0.61 % 0.5));
                    Fx.glow(c, pose, camera.orientation, 0.07F, Fx.alpha(0xFFFFF6A0, 1 - f));
                    pose.popPose();
                }
                pose.pushPose();
                pose.translate(0.5, 6.4F / 16, 0.5);
                Fx.glow(c, pose, camera.orientation, 0.55F, Fx.alpha(0xFF4FA8FF, 0.6F + 0.2F * Mth.sin(s.time * 0.7F)));
                pose.popPose();
            }
        }
    }

    /** Ore Dissolver: an acid bath, the ore lump shrinking and crystals rising in the metal's colour. */
    public static class Dissolver implements BlockEntityRenderer<OreDissolverBlockEntity, Dissolver.State> {
        public static class State extends BlockEntityRenderState {
            float acid, progress, time;
            int color = -1;
            boolean catalyst, running;
            Direction facing = Direction.NORTH;
        }

        public Dissolver(BlockEntityRendererProvider.Context ctx) {}

        @Override
        public State createRenderState() {
            return new State();
        }

        @Override
        public void extractRenderState(OreDissolverBlockEntity be, State s, float partial, Vec3 camera, ModelFeatureRenderer.@Nullable CrumblingOverlay crumble) {
            BlockEntityRenderer.super.extractRenderState(be, s, partial, camera, crumble);
            s.acid = be.clientAcid();
            s.progress = be.clientProgress();
            OreMetal m = be.clientMetal();
            s.color = m == null ? -1 : m.color;
            s.catalyst = be.clientCatalyst();
            s.running = be.getBlockState().getValueOrElse(MachineBlock.RUNNING, false);
            s.facing = be.getBlockState().getValueOrElse(MachineBlock.FACING, Direction.NORTH);
            s.time = be.getLevel() == null ? 0 : (be.getLevel().getGameTime() % 72000L) + partial;
        }

        @Override
        public void submit(State s, PoseStack pose, SubmitNodeCollector c, CameraRenderState camera) {
            int light = s.lightCoords;
            float surface = 7.1F / 16 + (5.4F / 16) * Math.max(0.1F, s.acid) + (s.running ? 0.006F * Mth.sin(s.time * 0.8F) : 0);
            c.submitCustomGeometry(pose, RenderTypes.entityTranslucent(WashPlantRenderer.TEX), (p, b) ->
                    WashPlantRenderer.box(b, p, 2F / 16, 7F / 16, 2F / 16, 14F / 16, surface, 14F / 16, WashPlantRenderer.WHITE, ACID, light));
            if (s.color == -1) return;
            int color = s.color;
            pose.pushPose();
            pose.translate(0.5, 0, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(facingDeg(s.facing)));
            pose.translate(-0.5, 0, -0.5);
            float p = s.progress;
            int crystals = s.catalyst ? 5 : 4;
            c.submitCustomGeometry(pose, RenderTypes.entityCutout(WashPlantRenderer.TEX), (pp, b) -> {
                // The ore lump, front left, dissolving.
                float h = 0.11F * (1F - 0.75F * p);
                WashPlantRenderer.box(b, pp, 0.3F - h, 7F / 16, 0.32F - h, 0.3F + h, 7F / 16 + h * 1.6F, 0.32F + h, WashPlantRenderer.WHITE,
                        Fx.mix(color, 0xFF4A3A2E, 0.5F), light);
                // Crystals at the back, growing.
                for (int k = 0; k < crystals; k++) {
                    float x = 0.25F + k * 0.12F, z = 0.68F + (k % 2) * 0.08F;
                    float ht = (0.06F + 0.03F * (k % 3)) * Math.min(1F, p * 1.3F - k * 0.04F);
                    if (ht <= 0) continue;
                    WashPlantRenderer.box(b, pp, x - 0.025F, 7F / 16, z - 0.025F, x + 0.025F, 7F / 16 + ht * 2.2F, z + 0.025F, WashPlantRenderer.WHITE,
                            Fx.mix(color, 0xFFFFFFFF, 0.25F), light);
                }
            });
            if (s.running) {
                for (int k = 0; k < 5; k++) {
                    float f = (s.time * 0.04F + k / 5F) % 1F;
                    pose.pushPose();
                    pose.translate(0.3 + (k % 3) * 0.06, 7.2F / 16 + f * (surface - 7.2F / 16), 0.32);
                    Fx.glow(c, pose, camera.orientation, 0.06F, Fx.alpha(0xFFFFF6A0, 1 - f));
                    pose.popPose();
                }
                pose.pushPose();
                pose.translate(0.5, surface + 0.02, 0.7);
                Fx.glow(c, pose, camera.orientation, 0.3F, Fx.alpha(Fx.mix(color, 0xFFFFFFFF, 0.4F), 0.5F + 0.3F * Mth.sin(s.time * 0.3F)));
                pose.popPose();
            }
            pose.popPose();
        }
    }

    private ChemRenderers() {}
}
