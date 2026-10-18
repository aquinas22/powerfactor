package com.powerfactor.client.wash;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.powerfactor.PowerFactor;
import com.powerfactor.chem.OreMetal;
import com.powerfactor.client.MultiblockGhost;
import com.powerfactor.client.fx.Fx;
import com.powerfactor.machine.MachineBlock;
import com.powerfactor.multiblock.CasingBlock;
import com.powerfactor.wash.WashPlantBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The working wash line, drawn by the shaker-table controller in its own frame (facing north: the line runs
 * back along +Z). A perforated trommel drum turns on its trunnions with ore tumbling inside and spray bars
 * along the top; water and slurry sheet down the sluice over the riffles, where concentrate glitters in the
 * metal's colour; the shaker table's deck chatters and streaks concentrate off its edge. Unformed, it shows
 * the hologram of the missing parts.
 */
public class WashPlantRenderer implements BlockEntityRenderer<WashPlantBlockEntity, WashPlantRenderer.State> {
    public static final Identifier TEX = PowerFactor.id("textures/entity/wash_plant.png");
    private static final RenderType CUTOUT = RenderTypes.entityCutout(TEX);
    private static final RenderType WATER = RenderTypes.entityTranslucent(TEX);
    /** Texture regions (128x128): drum mesh, water sheet (stored twice, stacked, so it scrolls seamlessly), white patch, shaker deck. */
    public static final float[] MESH = {0, 0, 64, 32}, SHEET = {64, 0, 32, 32}, WHITE = {98, 2, 4, 4}, DECK = {0, 32, 64, 32}, RIFFLE = {96, 32, 16, 16},
            GALV = {0, 64, 16, 16}, YELLOW = {32, 64, 16, 16};
    /** Drum: axis along Z at x 0.5, y 1.5, from z 3.9 to 7.1, sloping down toward the front. */
    private static final float DRUM_R = 0.58F, DRUM_Z0 = 3.95F, DRUM_Z1 = 7.05F, DRUM_Y = 2.05F, SLUICE_Y = 0.42F;
    /**
     * The sluice is one continuous incline (drawn here, not by the block models): its floor drops from
     * {@link #FLOOR_BACK} under the feed end to {@link #FLOOR_FRONT} at the lip over the table, a 1:10
     * grade like a real sluice; walls stand 3 px above it and water runs a pixel deep.
     */
    private static final float SLUICE_Z0 = 1.0F, SLUICE_Z1 = 7.0F, FLOOR_FRONT = 0.62F, FLOOR_BACK = 1.22F, WALL = 3.5F / 16;

    static float floorAt(float z) {
        return Mth.lerp((z - SLUICE_Z0) / (SLUICE_Z1 - SLUICE_Z0), FLOOR_FRONT, FLOOR_BACK);
    }

    public static class State extends BlockEntityRenderState {
        Direction facing = Direction.NORTH;
        boolean formed, running;
        int color = 0xFFC9A55A;
        long washed;
        float time;
        final List<MultiblockGhost.Ghost> ghosts = new ArrayList<>();
    }

    public WashPlantRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(WashPlantBlockEntity be, State s, float partial, Vec3 camera, ModelFeatureRenderer.@Nullable CrumblingOverlay crumble) {
        BlockEntityRenderer.super.extractRenderState(be, s, partial, camera, crumble);
        Level level = be.getLevel();
        s.facing = be.getBlockState().getValueOrElse(MachineBlock.FACING, Direction.NORTH);
        s.formed = be.getBlockState().getValueOrElse(CasingBlock.FORMED, false);
        s.running = be.clientRunning();
        OreMetal m = be.clientMetal();
        s.color = m == null ? 0xFFE8C860 : m.color;
        s.washed = be.clientWashed();
        s.time = level == null ? 0 : (level.getGameTime() % 72000L) + partial;
        s.ghosts.clear();
        if (level == null) return;
        if (!s.formed) {
            BlockPos origin = be.getBlockPos();
            for (var part : WashPlantBlockEntity.STRUCTURE.parts()) {
                BlockPos at = WashPlantBlockEntity.STRUCTURE.worldPos(origin, s.facing, part);
                var bs = level.getBlockState(at);
                if (!part.matches(bs)) s.ghosts.add(new MultiblockGhost.Ghost(at.subtract(origin), !bs.canBeReplaced(), new AABB(0, 0, 0, 1, 1, 1)));
            }
        } else if (s.running && level.getGameTime() % 3 == 0) {
            spray(level, be.getBlockPos(), s.facing);
        }
    }

    /** Spray-bar droplets over the drum and splash where the sluice tips onto the table, in world space. */
    private static void spray(Level level, BlockPos origin, Direction facing) {
        var r = level.getRandom();
        for (int i = 0; i < 2; i++) {
            Vec3 local = new Vec3(0.5 + (r.nextDouble() - 0.5) * 0.5, DRUM_Y + DRUM_R + 0.12, DRUM_Z0 + r.nextDouble() * (DRUM_Z1 - DRUM_Z0));
            Vec3 w = toWorld(origin, facing, local);
            level.addParticle(ParticleTypes.FALLING_WATER, w.x, w.y, w.z, 0, 0, 0);
        }
        Vec3 lip = toWorld(origin, facing, new Vec3(0.5 + (r.nextDouble() - 0.5) * 0.4, FLOOR_FRONT - 0.02, SLUICE_Z0 - 0.2));
        level.addParticle(ParticleTypes.SPLASH, lip.x, lip.y, lip.z, 0, 0.05, 0);
    }

    static Vec3 toWorld(BlockPos origin, Direction facing, Vec3 local) {
        Vec3 c = local.subtract(0.5, 0, 0.5);
        double a = Math.toRadians(-yRot(facing));
        double x = c.x * Math.cos(a) + c.z * Math.sin(a), z = -c.x * Math.sin(a) + c.z * Math.cos(a);
        return new Vec3(origin.getX() + 0.5 + x, origin.getY() + c.y, origin.getZ() + 0.5 + z);
    }

    /** Degrees about Y that map the north-facing layout onto {@code facing} (matches Structure.rotation). */
    static float yRot(Direction facing) {
        return switch (facing) {
            case EAST -> -90;
            case SOUTH -> -180;
            case WEST -> 90;
            default -> 0;
        };
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector c, CameraRenderState camera) {
        if (!s.formed) {
            MultiblockGhost.submit(pose, c, s.ghosts, s.time);
            return;
        }
        int light = s.lightCoords;
        float t = s.time;
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(yRot(s.facing)));
        pose.translate(-0.5, 0, -0.5);
        drum(s, pose, c, light, t);
        sluice(s, pose, c, light, t, camera);
        table(s, pose, c, light, t);
        pose.popPose();
    }

    // ------------------------------------------------------------------ trommel

    private void drum(State s, PoseStack pose, SubmitNodeCollector c, int light, float t) {
        float spin = s.running ? t * 4.5F : 0;   // ~ 14 RPM
        pose.pushPose();
        pose.translate(0.5, DRUM_Y, 0);
        pose.mulPose(Axis.XP.rotationDegrees(-5.7F));   // tipped toward the discharge end, like the sluice under it
        // Perforated screen, turning.
        pose.pushPose();
        pose.mulPose(Axis.ZP.rotationDegrees(spin));
        c.submitCustomGeometry(pose, CUTOUT, (p, b) -> cylinder(b, p, DRUM_R, DRUM_Z0, DRUM_Z1, 24, MESH, 0xFFFFFFFF, light, true));
        // Riding rings at each end, a shade darker, and lifter bars inside.
        c.submitCustomGeometry(pose, CUTOUT, (p, b) -> {
            for (float z : new float[] {DRUM_Z0 + 0.12F, DRUM_Z1 - 0.12F}) cylinder(b, p, DRUM_R + 0.05F, z - 0.08F, z + 0.08F, 24, WHITE, 0xFF5D676D, light, false);
            for (int k = 0; k < 4; k++) {
                float a = k * Mth.HALF_PI;
                float x = Mth.cos(a) * (DRUM_R - 0.04F), y = Mth.sin(a) * (DRUM_R - 0.04F);
                box(b, p, x - 0.03F, y - 0.03F, DRUM_Z0, x + 0.03F, y + 0.03F, DRUM_Z1, WHITE, 0xFF8C9AA2, light);
            }
        });
        pose.popPose();
        // Ore tumbling inside: lumps carried up by the lifters and falling back, drifting toward the discharge.
        if (s.running) {
            c.submitCustomGeometry(pose, CUTOUT, (p, b) -> {
                for (int k = 0; k < 14; k++) {
                    float ph = (t * 0.05F + k * 0.137F) % 1F;
                    float z = DRUM_Z1 - 0.2F - ph * (DRUM_Z1 - DRUM_Z0 - 0.4F);
                    float a = Mth.DEG_TO_RAD * (spin * 0.8F + k * 51) % Mth.TWO_PI;
                    float lift = Mth.sin(a);
                    float rr = DRUM_R - 0.14F;
                    float x = -Mth.cos(a) * rr * 0.6F, y = -rr + Math.max(0, lift) * rr * 0.9F;
                    float h = 0.045F + (k % 3) * 0.012F;
                    box(b, p, x - h, y - h, z - h, x + h, y + h, z + h, WHITE, Fx.mix(s.color, 0xFF5A4A3A, 0.35F + (k % 4) * 0.1F), light);
                }
            });
            // Spray bar: a pipe along the top with fans of water.
            c.submitCustomGeometry(pose, WATER, (p, b) -> {
                for (int k = 0; k < 6; k++) {
                    float z = DRUM_Z0 + 0.3F + k * 0.5F, y = DRUM_R + 0.12F;
                    float wob = 0.03F * Mth.sin(t * 0.9F + k);
                    quad(b, p, new float[][] {{-0.12F + wob, y, z - 0.08F}, {-0.2F, DRUM_R - 0.02F, z + 0.08F}, {0.2F, DRUM_R - 0.02F, z + 0.08F}, {0.12F + wob, y, z - 0.08F}},
                            SHEET, (t * 0.04F) % 1F, 0xB0B8E4FF, light, 0, 1, 0);
                }
            });
        }
        c.submitCustomGeometry(pose, CUTOUT, (p, b) -> box(b, p, -0.03F, DRUM_R + 0.1F, DRUM_Z0 + 0.1F, 0.03F, DRUM_R + 0.16F, DRUM_Z1 - 0.1F, WHITE, 0xFFB8C4CC, light));
        pose.popPose();
        // Feed chute: from under the hopper down into the drum's mouth.
        c.submitCustomGeometry(pose, CUTOUT, (p, b) -> {
            float y0 = 2.3F, z0 = 7.3F, y1 = DRUM_Y + 0.15F, z1 = DRUM_Z1 - 0.35F;
            for (int k = 0; k < 6; k++) {
                float f0 = k / 6F, f1 = (k + 1) / 6F;
                box(b, p, 0.32F, Mth.lerp(f1, y0, y1), Mth.lerp(f1, z0, z1) - 0.06F, 0.68F, Mth.lerp(f0, y0, y1) + 0.04F, Mth.lerp(f0, z0, z1) + 0.06F,
                        WHITE, 0xFF9AA4AA, light);
            }
        });
    }

    // ------------------------------------------------------------------ sluice

    private void sluice(State s, PoseStack pose, SubmitNodeCollector c, int light, float t, CameraRenderState camera) {
        float x0 = 0.12F, x1 = 0.88F;
        // The trough: riffled floor, galvanized side walls and a tail board, on yellow legs that grow toward the back.
        c.submitCustomGeometry(pose, CUTOUT, (p, b) -> {
            for (int seg = 0; seg < 6; seg++) {
                float za = SLUICE_Z0 + seg, zb = za + 1;
                float ya = floorAt(za), yb = floorAt(zb);
                quad(b, p, new float[][] {{x0, ya, za}, {x1, ya, za}, {x1, yb, zb}, {x0, yb, zb}}, RIFFLE, 0, 0xFFFFFFFF, light, 0, 1, 0);
                quad(b, p, new float[][] {{x1, ya - 0.06F, za}, {x0, ya - 0.06F, za}, {x0, yb - 0.06F, zb}, {x1, yb - 0.06F, zb}}, GALV, 0, 0xFFB0B8BC, light, 0, -1, 0);
                for (float x : new float[] {x0 - 0.05F, x1}) {
                    float xa = x, xb = x + 0.05F;
                    quad(b, p, new float[][] {{xa, ya - 0.06F, za}, {xa, yb - 0.06F, zb}, {xa, yb + WALL, zb}, {xa, ya + WALL, za}}, GALV, 0, 0xFFFFFFFF, light, -1, 0, 0);
                    quad(b, p, new float[][] {{xb, ya - 0.06F, za}, {xb, ya + WALL, za}, {xb, yb + WALL, zb}, {xb, yb - 0.06F, zb}}, GALV, 0, 0xFFFFFFFF, light, 1, 0, 0);
                    quad(b, p, new float[][] {{xa, ya + WALL, za}, {xa, yb + WALL, zb}, {xb, yb + WALL, zb}, {xb, ya + WALL, za}}, GALV, 0, 0xFFE0E6EA, light, 0, 1, 0);
                }
                // Riffle bars across the flow, two per block.
                for (float rz : new float[] {za + 0.25F, za + 0.75F}) {
                    float ry = floorAt(rz);
                    box(b, p, x0, ry, rz - 0.02F, x1, ry + 0.045F, rz + 0.02F, WHITE, 0xFFC8D0D4, light);
                }
                // A pair of legs under the middle of each section.
                float lz = za + 0.5F, ly = floorAt(lz) - 0.06F;
                box(b, p, x0 + 0.02F, 0, lz - 0.05F, x0 + 0.12F, ly, lz + 0.05F, YELLOW, 0xFFFFFFFF, light);
                box(b, p, x1 - 0.12F, 0, lz - 0.05F, x1 - 0.02F, ly, lz + 0.05F, YELLOW, 0xFFFFFFFF, light);
            }
            // Tail board at the head, and the lip spout over the table.
            float yb = floorAt(SLUICE_Z1);
            box(b, p, x0, yb - 0.06F, SLUICE_Z1 - 0.04F, x1, yb + WALL, SLUICE_Z1, GALV, 0xFFFFFFFF, light);
            float yf = floorAt(SLUICE_Z0);
            quad(b, p, new float[][] {{x0 + 0.1F, yf, SLUICE_Z0}, {x1 - 0.1F, yf, SLUICE_Z0}, {x1 - 0.1F, yf - 0.05F, SLUICE_Z0 - 0.25F},
                    {x0 + 0.1F, yf - 0.05F, SLUICE_Z0 - 0.25F}}, GALV, 0, 0xFFE0E6EA, light, 0, 1, 0);
        });
        // Water sheeting down the incline, the texture streaming toward the lip, with a little standing wave at each riffle.
        if (s.running) {
            c.submitCustomGeometry(pose, WATER, (p, b) -> {
                for (int seg = 0; seg < 24; seg++) {
                    float za = SLUICE_Z0 - 0.25F + (SLUICE_Z1 - SLUICE_Z0 + 0.2F) * seg / 24F, zb = SLUICE_Z0 - 0.25F + (SLUICE_Z1 - SLUICE_Z0 + 0.2F) * (seg + 1) / 24F;
                    float ripple = 0.01F * Mth.sin(t * 0.5F + seg * 1.7F);
                    float ya = floorAt(Math.max(SLUICE_Z0, za)) + 0.065F + ripple - (za < SLUICE_Z0 ? 0.05F : 0);
                    float yb = floorAt(Math.max(SLUICE_Z0, zb)) + 0.065F;
                    int col = 0xC890B8D8;   // clear, a little silty
                    // Counter-clockwise seen from above: the translucent pass culls back faces.
                    quad(b, p, new float[][] {{x0, ya, za}, {x0, yb, zb}, {x1, yb, zb}, {x1, ya, za}}, SHEET, (t * 0.06F + seg * 0.25F) % 1F,
                            col, light, 0, 1, 0);
                }
            });
        }
        // Concentrate caught behind each riffle: specks that build up as loads wash through, glinting.
        int caught = (int) Math.min(40, (s.washed % 64) + (s.running ? 8 : 0));
        c.submitCustomGeometry(pose, CUTOUT, (p, b) -> {
            for (int k = 0; k < caught; k++) {
                int riffle = k % 12;
                float z = SLUICE_Z0 + 0.3F + riffle * 0.5F;
                float x = x0 + 0.05F + ((k * 37) % 13) / 13F * (x1 - x0 - 0.1F);
                float y = floorAt(z);
                box(b, p, x - 0.016F, y, z - 0.018F, x + 0.016F, y + 0.02F, z + 0.018F, WHITE, s.color, light);
            }
        });
        if (s.running) {
            for (int k = 0; k < 6; k++) {
                int idx = (int) ((t / 5 + k * 7) % Math.max(1, caught));
                float z = SLUICE_Z0 + 0.3F + (idx % 12) * 0.5F, x = x0 + 0.05F + ((idx * 37) % 13) / 13F * (x1 - x0 - 0.1F);
                pose.pushPose();
                pose.translate(x, floorAt(z) + 0.03F, z);
                Fx.glow(c, pose, camera.orientation, 0.09F, Fx.alpha(Fx.mix(s.color, 0xFFFFFFFF, 0.5F), 0.9F));
                pose.popPose();
            }
        }
    }

    // ------------------------------------------------------------------ shaker table

    private void table(State s, PoseStack pose, SubmitNodeCollector c, int light, float t) {
        float shake = s.running ? 0.012F * Mth.sin(t * 5.3F) : 0;
        pose.pushPose();
        pose.translate(shake, 0, 0);
        c.submitCustomGeometry(pose, CUTOUT, (p, b) -> {
            // Deck: a rubber-lined plate with diagonal riffles, tilted toward the catch side.
            float y0 = 0.62F, y1 = 0.56F;
            quad(b, p, new float[][] {{0.06F, y0, 0.06F}, {0.94F, y1, 0.06F}, {0.94F, y1, 0.94F}, {0.06F, y0, 0.94F}}, DECK, 0, 0xFFFFFFFF, light, 0, 1, 0);
        });
        if (s.running) {
            // Streaks: concentrate fanning across the deck toward the near edge, black sand behind it.
            c.submitCustomGeometry(pose, CUTOUT, (p, b) -> {
                for (int k = 0; k < 9; k++) {
                    float f = ((t * 0.02F + k / 9F) % 1F);
                    float z = 0.9F - f * 0.8F, x = 0.15F + k * 0.08F + f * 0.15F;
                    int col = k < 3 ? 0xFF2A2A30 : s.color;
                    box(b, p, x - 0.02F, 0.6F - x * 0.06F, z - 0.05F, x + 0.02F, 0.62F - x * 0.06F, z + 0.05F, WHITE, col, light);
                }
            });
        }
        pose.popPose();
    }

    // ------------------------------------------------------------------ geometry

    /** An open cylinder along +Z, u around and v along it; {@code inside} also draws the inner face. */
    public static void cylinder(VertexConsumer b, PoseStack.Pose p, float r, float z0, float z1, int seg, float[] uv, int color, int light, boolean inside) {
        for (int i = 0; i < seg; i++) {
            float a0 = Mth.TWO_PI * i / seg, a1 = Mth.TWO_PI * (i + 1) / seg;
            float x0 = Mth.cos(a0) * r, y0 = Mth.sin(a0) * r, x1 = Mth.cos(a1) * r, y1 = Mth.sin(a1) * r;
            float u0 = uv[0] + uv[2] * ((i * 4F / seg) % 1F), u1 = u0 + uv[2] * 4F / seg;
            float v0 = uv[1], v1 = uv[1] + uv[3];
            float nx = Mth.cos((a0 + a1) / 2), ny = Mth.sin((a0 + a1) / 2);
            v(b, p, x0, y0, z0, u0, v0, color, light, nx, ny, 0);
            v(b, p, x0, y0, z1, u0, v1, color, light, nx, ny, 0);
            v(b, p, x1, y1, z1, u1, v1, color, light, nx, ny, 0);
            v(b, p, x1, y1, z0, u1, v0, color, light, nx, ny, 0);
            if (inside) {
                v(b, p, x1, y1, z0, u1, v0, color, light, -nx, -ny, 0);
                v(b, p, x1, y1, z1, u1, v1, color, light, -nx, -ny, 0);
                v(b, p, x0, y0, z1, u0, v1, color, light, -nx, -ny, 0);
                v(b, p, x0, y0, z0, u0, v0, color, light, -nx, -ny, 0);
            }
        }
    }

    public static void box(VertexConsumer b, PoseStack.Pose p, float x0, float y0, float z0, float x1, float y1, float z1, float[] uv, int color, int light) {
        float[][][] f = {
                {{x0, y1, z0}, {x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}, {0, 1, 0}}, {{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}, {0, -1, 0}},
                {{x0, y0, z0}, {x0, y1, z0}, {x1, y1, z0}, {x1, y0, z0}, {0, 0, -1}}, {{x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}, {0, 0, 1}},
                {{x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}, {x0, y1, z0}, {-1, 0, 0}}, {{x1, y0, z0}, {x1, y1, z0}, {x1, y1, z1}, {x1, y0, z1}, {1, 0, 0}}};
        for (float[][] q : f) {
            quad(b, p, new float[][] {q[0], q[1], q[2], q[3]}, uv, 0, color, light, q[4][0], q[4][1], q[4][2]);
        }
    }

    /** A quad with the texture region {@code uv}, its V scrolled by {@code scroll} (0..1 of the region). */
    public static void quad(VertexConsumer b, PoseStack.Pose p, float[][] v, float[] uv, float scroll, int color, int light, float nx, float ny, float nz) {
        float u0 = uv[0], u1 = uv[0] + uv[2], v0 = uv[1] + uv[3] * scroll, v1 = v0 + uv[3];
        v(b, p, v[0][0], v[0][1], v[0][2], u0, v0, color, light, nx, ny, nz);
        v(b, p, v[1][0], v[1][1], v[1][2], u1, v0, color, light, nx, ny, nz);
        v(b, p, v[2][0], v[2][1], v[2][2], u1, v1, color, light, nx, ny, nz);
        v(b, p, v[3][0], v[3][1], v[3][2], u0, v1, color, light, nx, ny, nz);
    }

    static void v(VertexConsumer b, PoseStack.Pose p, float x, float y, float z, float u, float vv, int color, int light, float nx, float ny, float nz) {
        b.addVertex(p, x, y, z).setColor(color).setUv(u / 128F, vv / 128F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
    }

    @Override
    public AABB getRenderBoundingBox(WashPlantBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(9);
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }
}
