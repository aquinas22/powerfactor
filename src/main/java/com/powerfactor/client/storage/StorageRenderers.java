package com.powerfactor.client.storage;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.powerfactor.PowerFactor;
import com.powerfactor.client.fx.Fx;
import com.powerfactor.storage.CellTier;
import com.powerfactor.storage.DriveBayBlockEntity;
import com.powerfactor.storage.NodeBlock;
import com.powerfactor.storage.NodeBlockEntity;
import com.powerfactor.storage.craft.AssemblerBlockEntity;
import com.powerfactor.storage.craft.CraftingProcessorBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Animated parts of the storage family. Everything glows harder on the quantum tier. */
public final class StorageRenderers {
    static final Identifier CORE = PowerFactor.id("textures/entity/storage/core.png");
    static final Identifier CELL = PowerFactor.id("textures/entity/storage/drive_cell.png");
    static final int CYAN = 0xFF6FE3FF, VIOLET = 0xFFB18CFF, RED = 0xFFFF4A3A, AMBER = 0xFFFFB347;

    public static class NodeState extends BlockEntityRenderState {
        public float time;
        public boolean active;
        public Direction facing = Direction.NORTH;
    }

    static void base(NodeBlockEntity be, NodeState s, float partial) {
        s.time = be.getLevel() == null ? 0 : (be.getLevel().getGameTime() % 24000L) + partial;
        s.active = be.active();
        s.facing = be.getBlockState().hasProperty(NodeBlock.FACING) ? be.getBlockState().getValue(NodeBlock.FACING) : Direction.NORTH;
    }

    /** Rotate so the model's north face points along {@code facing} (horizontal facings). */
    static void face(PoseStack pose, Direction facing) {
        pose.translate(0.5F, 0.5F, 0.5F);
        float y = switch (facing) {
            case SOUTH -> 180;
            case EAST -> -90;
            case WEST -> 90;
            default -> 0;
        };
        pose.mulPose(Axis.YP.rotationDegrees(y));
        pose.translate(-0.5F, -0.5F, -0.5F);
    }

    // ------------------------------------------------------------------ controller: a floating energy crystal

    public static class Controller<T extends NodeBlockEntity> implements BlockEntityRenderer<T, NodeState> {
        public Controller(BlockEntityRendererProvider.Context context) {}

        @Override
        public NodeState createRenderState() {
            return new NodeState();
        }

        @Override
        public void extractRenderState(T be, NodeState s, float partial, Vec3 camera, ModelFeatureRenderer.@Nullable CrumblingOverlay crumble) {
            BlockEntityRenderer.super.extractRenderState(be, s, partial, camera, crumble);
            base(be, s, partial);
        }

        @Override
        public void submit(NodeState s, PoseStack pose, SubmitNodeCollector c, CameraRenderState camera) {
            float t = s.time;
            int color = s.active ? CYAN : RED;
            float speed = s.active ? 1 : 0.15F;
            float pulse = s.active ? 0.8F + 0.2F * Mth.sin(t * 0.15F) : 0.35F + 0.1F * Mth.sin(t * 0.05F);
            pose.pushPose();
            pose.translate(0.5F, 0.5F + (s.active ? 0.03F * Mth.sin(t * 0.08F) : 0), 0.5F);
            // Inner crystal.
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(t * 2.4F * speed));
            Fx.crystal(c, pose, CORE, 0.16F, 0.24F, Fx.alpha(color, 0.9F));
            pose.popPose();
            // Counter-rotating cage of energy cubes.
            for (int i = 0; i < 4; i++) {
                pose.pushPose();
                pose.mulPose(Axis.YP.rotationDegrees(-t * 3.1F * speed + i * 90));
                pose.translate(0.27F, 0.12F * Mth.sin(t * 0.1F * speed + i), 0);
                pose.mulPose(Axis.XP.rotationDegrees(t * 6 * speed));
                Fx.cube(c, pose, Fx.GLOW, 0.05F, Fx.alpha(color, pulse));
                pose.popPose();
            }
            // Equatorial ring and the halo.
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotationDegrees(12 * Mth.sin(t * 0.03F)));
            Fx.ring(c, pose, 0.33F, 0.04F, 32, t * 0.02F * speed, Fx.alpha(color, 0.75F * pulse));
            pose.popPose();
            Fx.glow(c, pose, camera.orientation, 0.85F, Fx.alpha(color, 0.55F * pulse));
            Fx.glow(c, pose, camera.orientation, 0.35F, Fx.alpha(0xFFFFFFFF, 0.6F * pulse));
            pose.popPose();
        }
    }

    // ------------------------------------------------------------------ drive bay: cells in their bays

    public static class DriveState extends NodeState {
        public final int[] tiers = new int[DriveBayBlockEntity.SLOTS];
        public final float[] fills = new float[DriveBayBlockEntity.SLOTS];
    }

    public static class Drive implements BlockEntityRenderer<DriveBayBlockEntity, DriveState> {
        public Drive(BlockEntityRendererProvider.Context context) {}

        @Override
        public DriveState createRenderState() {
            return new DriveState();
        }

        @Override
        public void extractRenderState(DriveBayBlockEntity be, DriveState s, float partial, Vec3 camera,
                ModelFeatureRenderer.@Nullable CrumblingOverlay crumble) {
            BlockEntityRenderer.super.extractRenderState(be, s, partial, camera, crumble);
            base(be, s, partial);
            for (int i = 0; i < DriveBayBlockEntity.SLOTS; i++) {
                s.tiers[i] = be.ledTier(i);
                s.fills[i] = be.ledFill(i);
            }
        }

        @Override
        public void submit(DriveState s, PoseStack pose, SubmitNodeCollector c, CameraRenderState camera) {
            pose.pushPose();
            face(pose, s.facing);
            for (int i = 0; i < DriveBayBlockEntity.SLOTS; i++) {
                if (s.tiers[i] == 0) continue;
                int tierColor = DriveBayBlockEntity.ledColor(s.tiers[i]), tierGlow = DriveBayBlockEntity.ledGlow(s.tiers[i]);
                int col = i / 5, row = i % 5;
                float x0 = (col == 0 ? 2 : 9) / 16F, x1 = x0 + 6 / 16F;
                float yTop = 16 - (1 + row * 3), y1 = yTop / 16F, y0 = (yTop - 2) / 16F;
                float z = -0.004F;
                int light = s.lightCoords;
                // The cartridge face: lit by the world.
                c.submitCustomGeometry(pose, RenderTypes.entityCutout(CELL), (p, b) -> {
                    quad(b, p, x1, y0, x0, y1, z, 0, 0, 1, 1, 0xFFFFFFFF, light);
                });
                // Status LED and a fill gauge, both fullbright: blink while active, red when nearly full.
                float f = s.fills[i];
                int led = !s.active ? 0xFF303030 : f > 0.95F ? (((int) s.time / 5) % 2 == 0 ? RED : 0xFF501010)
                        : f > 0.75F ? AMBER : tierColor;
                float flicker = s.active ? 0.85F + 0.15F * Mth.sin(s.time * 0.7F + i * 1.7F) : 1;
                int ledColor = Fx.alpha(led, flicker);
                float lx0 = x1 - 1.4F / 16, gaugeW = (x1 - x0 - 2.2F / 16) * f;
                c.submitCustomGeometry(pose, Fx.additive(Fx.GLOW), (p, b) -> {
                    quad(b, p, x1 - 0.2F / 16, y0 + 0.4F / 16, lx0, y1 - 0.4F / 16, z - 0.002F, 0.3F, 0.3F, 0.7F, 0.7F, ledColor, Fx.FULL_BRIGHT);
                    if (gaugeW > 0) {
                        quad(b, p, x0 + 0.4F / 16 + gaugeW, y0 + 0.25F / 16, x0 + 0.4F / 16, y0 + 0.65F / 16, z - 0.002F, 0.45F, 0.45F, 0.55F, 0.55F,
                                Fx.alpha(tierColor, 0.7F), Fx.FULL_BRIGHT);
                    }
                });
                if (tierGlow >= 2 && s.active) {
                    // High tiers emit a faint bloom on the rack.
                    pose.pushPose();
                    pose.translate((x0 + x1) / 2, (y0 + y1) / 2, z - 0.01F);
                    Fx.glow(c, pose, camera.orientation, 0.18F + 0.06F * tierGlow, Fx.alpha(tierColor, 0.35F));
                    pose.popPose();
                }
            }
            pose.popPose();
        }
    }

    /** A quad on the plane z (facing -Z), from (xa, ya) to (xb, yb), with UVs. */
    static void quad(com.mojang.blaze3d.vertex.VertexConsumer b, PoseStack.Pose p, float xa, float ya, float xb, float yb, float z,
            float u0, float v0, float u1, float v1, int argb, int light) {
        b.addVertex(p, xa, ya, z).setColor(argb).setUv(u0, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0, 0, -1);
        b.addVertex(p, xb, ya, z).setColor(argb).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0, 0, -1);
        b.addVertex(p, xb, yb, z).setColor(argb).setUv(u1, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0, 0, -1);
        b.addVertex(p, xa, yb, z).setColor(argb).setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0, 0, -1);
    }

    // ------------------------------------------------------------------ crafting processors: holographic gyroscopes

    public static class ProcessorState extends NodeState {
        public int busy;
        public boolean quantum;
    }

    public static class Processor implements BlockEntityRenderer<CraftingProcessorBlockEntity, ProcessorState> {
        public Processor(BlockEntityRendererProvider.Context context) {}

        @Override
        public ProcessorState createRenderState() {
            return new ProcessorState();
        }

        @Override
        public void extractRenderState(CraftingProcessorBlockEntity be, ProcessorState s, float partial, Vec3 camera,
                ModelFeatureRenderer.@Nullable CrumblingOverlay crumble) {
            BlockEntityRenderer.super.extractRenderState(be, s, partial, camera, crumble);
            base(be, s, partial);
            s.busy = be.busy();
            s.quantum = be.quantum();
        }

        @Override
        public void submit(ProcessorState s, PoseStack pose, SubmitNodeCollector c, CameraRenderState camera) {
            if (!s.active) return;
            float t = s.time;
            boolean busy = s.busy > 0;
            int color = s.quantum ? VIOLET : CYAN;
            float speed = busy ? (s.quantum ? 2.4F : 1.6F) : 0.35F;
            float bright = busy ? 1 : 0.45F;
            pose.pushPose();
            pose.translate(0.5F, 13.8F / 16, 0.5F);
            // Inside the cupola: a spinning qubit with a tilted orbit ring.
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(t * 4 * speed));
            pose.mulPose(Axis.XP.rotationDegrees(35));
            Fx.ring(c, pose, 0.17F, 0.025F, 24, t * 0.05F * speed, Fx.alpha(color, 0.9F * bright));
            pose.popPose();
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(-t * 6 * speed));
            pose.mulPose(Axis.ZP.rotationDegrees(45));
            Fx.cube(c, pose, Fx.GLOW, 0.07F, Fx.alpha(0xFFFFFFFF, 0.8F * bright));
            pose.popPose();
            Fx.glow(c, pose, camera.orientation, 0.45F, Fx.alpha(color, 0.5F * bright));
            if (s.quantum) {
                // Quantum: three gyroscope rings in the open air above the core, one per axis, plus orbiting qubits.
                pose.translate(0, 0.42F, 0);
                for (int k = 0; k < 3; k++) {
                    pose.pushPose();
                    pose.mulPose(Axis.YP.rotationDegrees(t * (1.5F + k) * speed + k * 60));
                    pose.mulPose(Axis.XP.rotationDegrees(k * 60 + t * 0.7F * speed));
                    Fx.ring(c, pose, 0.32F - k * 0.05F, 0.03F, 40, t * 0.03F * (k + 1), Fx.alpha(Fx.mix(VIOLET, CYAN, k / 3F), 0.85F * bright));
                    pose.popPose();
                }
                for (int q = 0; q < Math.max(2, s.busy * 2); q++) {
                    pose.pushPose();
                    double a = t * 0.12 * speed + q * Math.PI * 2 / Math.max(2, s.busy * 2);
                    pose.translate((float) Math.cos(a) * 0.36F, 0.12F * Mth.sin(t * 0.2F + q), (float) Math.sin(a) * 0.36F);
                    Fx.glow(c, pose, camera.orientation, 0.16F, Fx.alpha(0xFFE8DCFF, bright));
                    pose.popPose();
                }
                Fx.glow(c, pose, camera.orientation, 0.9F, Fx.alpha(VIOLET, 0.3F * bright));
            }
            pose.popPose();
        }

        @Override
        public AABB getRenderBoundingBox(CraftingProcessorBlockEntity be) {
            return new AABB(be.getBlockPos()).expandTowards(0, 1, 0).inflate(0.3);
        }
    }

    // ------------------------------------------------------------------ assembler: item hologram under converging lasers

    public static class AssemblerState extends NodeState {
        public final ItemStackRenderState item = new ItemStackRenderState();
        public boolean working;
        public float progress;
    }

    public static class Assembler implements BlockEntityRenderer<AssemblerBlockEntity, AssemblerState> {
        private final ItemModelResolver items;

        public Assembler(BlockEntityRendererProvider.Context context) {
            this.items = context.itemModelResolver();
        }

        @Override
        public AssemblerState createRenderState() {
            return new AssemblerState();
        }

        @Override
        public void extractRenderState(AssemblerBlockEntity be, AssemblerState s, float partial, Vec3 camera,
                ModelFeatureRenderer.@Nullable CrumblingOverlay crumble) {
            BlockEntityRenderer.super.extractRenderState(be, s, partial, camera, crumble);
            base(be, s, partial);
            ItemStack showing = be.showing();
            s.working = !showing.isEmpty();
            s.progress = be.progress(partial);
            if (s.working) items.updateForTopItem(s.item, showing, ItemDisplayContext.FIXED, be.getLevel(), null, (int) be.getBlockPos().asLong());
            else s.item.clear();
        }

        @Override
        public void submit(AssemblerState s, PoseStack pose, SubmitNodeCollector c, CameraRenderState camera) {
            float t = s.time;
            Vec3 center = new Vec3(0.5, 0.5, 0.5);
            if (s.working) {
                // The product materialises: it grows in and spins faster as the craft completes.
                pose.pushPose();
                pose.translate(0.5F, 0.48F + 0.03F * Mth.sin(t * 0.3F), 0.5F);
                float scale = 0.25F + 0.25F * Mth.clamp(s.progress * 1.3F, 0, 1);
                pose.mulPose(Axis.YP.rotationDegrees(t * (6 + 20 * s.progress)));
                pose.scale(scale, scale, scale);
                s.item.submit(pose, c, Fx.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
                pose.popPose();
                // Four emitters fire at the work piece, flickering, and converge into a flash.
                float[][] heads = {{2.5F, 13, 2.5F}, {13.5F, 13, 2.5F}, {2.5F, 13, 13.5F}, {13.5F, 13, 13.5F}};
                for (int i = 0; i < 4; i++) {
                    float flicker = 0.6F + 0.4F * Mth.sin(t * 2.3F + i * 1.9F);
                    Vec3 from = new Vec3(heads[i][0] / 16, heads[i][1] / 16, heads[i][2] / 16);
                    Fx.beam(c, pose, from, center, 0.018F, -t * 0.15F, Fx.alpha(0xFFE0FBFF, flicker), Fx.alpha(CYAN, 0.35F * flicker));
                }
                pose.pushPose();
                pose.translate(0.5F, 0.5F, 0.5F);
                Fx.glow(c, pose, camera.orientation, 0.35F + 0.4F * s.progress, Fx.alpha(CYAN, 0.35F + 0.5F * s.progress));
                Fx.ring(c, pose, 0.3F - 0.2F * s.progress, 0.02F, 24, t * 0.1F, Fx.alpha(0xFFFFFFFF, 0.6F));
                pose.popPose();
            } else if (s.active) {
                // Idle: emitter heads breathe.
                for (float[] h : new float[][] {{2.5F, 13, 2.5F}, {13.5F, 13, 2.5F}, {2.5F, 13, 13.5F}, {13.5F, 13, 13.5F}}) {
                    pose.pushPose();
                    pose.translate(h[0] / 16, h[1] / 16 - 0.07F, h[2] / 16);
                    Fx.glow(c, pose, camera.orientation, 0.12F, Fx.alpha(CYAN, 0.35F + 0.2F * Mth.sin(t * 0.1F)));
                    pose.popPose();
                }
            }
        }
    }

    private StorageRenderers() {}
}
