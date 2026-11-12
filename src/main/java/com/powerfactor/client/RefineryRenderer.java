package com.powerfactor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.powerfactor.PowerFactor;
import com.powerfactor.client.Mesh.Tile;
import com.powerfactor.machine.MachineBlock;
import com.powerfactor.multiblock.CasingBlock;
import com.powerfactor.oil.RefineryBlockEntity;
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
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The refinery plant around the distillation column, drawn once the structure forms: decks with
 * railings and a caged ladder, a fired heater whose burners flicker in their peep windows, the feed
 * pump, insulated product drums with gauge glasses that follow the tanks, the overhead vapour line
 * to a fin-fan cooler, and a flare at the top. Until then it projects the missing parts as a hologram.
 *
 * <p>Model pixels in the controller's frame: +Z out of its front, +Y up, origin at the middle of
 * its floor. The 3x3 base spans x -24..24, z -40..8 with its top at y 16; the column stands at z -16.
 */
public class RefineryRenderer implements BlockEntityRenderer<RefineryBlockEntity, RefineryRenderer.State> {
    private static final AABB FULL = new AABB(0.02, 0.02, 0.02, 0.98, 0.98, 0.98), COLUMN = new AABB(0.2, 0, 0.2, 0.8, 1, 0.8);
    private static final Identifier TEXTURE = PowerFactor.id("textures/entity/refinery.png");
    private static final RenderType TYPE = RenderTypes.entityCutout(TEXTURE);
    private static final RenderType GLOW = RenderTypes.entityTranslucentEmissive(TEXTURE);
    private static final int FULL_BRIGHT = 0xF000F0, TEX = 128;
    private static final float PX = 1 / 16F;

    // Texture sheet tiles; tools/oil_art.py paints the same rectangles.
    private static final Tile GRATE = new Tile(0, 0, 24, 24), RAIL = new Tile(0, 25, 32, 2), TOE = new Tile(0, 28, 32, 2),
            POST = new Tile(0, 31, 32, 2), LADDER = new Tile(0, 34, 48, 1), RUNG = new Tile(0, 36, 8, 1),
            PIPE_STEEL = new Tile(32, 25, 16, 4), PIPE_HOT = new Tile(32, 30, 16, 4), PIPE_DIESEL = new Tile(48, 25, 16, 4),
            PIPE_JET = new Tile(48, 30, 16, 4), PIPE_CRUDE = new Tile(64, 25, 16, 4), FLANGE = new Tile(64, 30, 16, 2),
            PIPE_END = new Tile(80, 25, 4, 4), DRUM_DIESEL = new Tile(24, 0, 32, 24), DRUM_JET = new Tile(56, 0, 32, 24),
            DRUM_CRUDE = new Tile(88, 0, 32, 24), HEAD = new Tile(0, 40, 12, 12), DOME = new Tile(12, 40, 12, 12),
            HEATER_FRONT = new Tile(24, 40, 12, 14), HEATER_SIDE = new Tile(36, 40, 12, 14), HEATER_TOP = new Tile(48, 40, 12, 12),
            STACK = new Tile(60, 40, 8, 4), GAUGE = new Tile(84, 40, 3, 16), FLUID = new Tile(88.5F, 40.5F, 1, 1),
            PUMP = new Tile(92, 40, 8, 6), MOTOR = new Tile(92, 47, 8, 5), MOTOR_END = new Tile(100, 40, 5, 5),
            FAN = new Tile(106, 40, 12, 12), BUNDLE = new Tile(0, 56, 16, 8), FRAME = new Tile(16, 56, 32, 2),
            PLENUM = new Tile(48, 56, 16, 8), VALVE = new Tile(38, 74, 6, 6), LAMP = new Tile(0, 74, 2, 2),
            LAMP_ON = new Tile(3, 74, 2, 2), SADDLE = new Tile(6, 74, 8, 6), SKID = new Tile(16, 74, 12, 6), PLATE = new Tile(30, 74, 6, 4);
    /** Flame strips: 8 texels wide, repeating every 16 texels down a 32-texel strip. */
    private static final float FLAME_U = 68, FLARE_U = 76, FLAME_V = 40, FLAME_PERIOD = 16;

    static final float COLUMN_Z = -16, COLUMN_TOP = 64;
    static final float[] DECKS = {38, 60};
    /** Peep windows in the heater front: left edge x of each, and their span in y. */
    static final float[] WINDOWS = {11, 15, 19};
    static final float WINDOW_Y0 = 19, WINDOW_Y1 = 24, HEATER_FRONT_Z = 6;
    static final float FLARE_X = 3.5F, FLARE_Z = -24, FLARE_TIP = 80;
    static final float PUMP_FAN_X = 23.3F, PUMP_Y = 19.5F, PUMP_Z = -16, COOLER_X = -16, COOLER_Z = -16, COOLER_FAN_Y = 31.4F;
    /** Gauge glasses: x, z of the glass face, bottom and top of the visible fluid. */
    static final float[][] GAUGES = {{-16, 6.3F, 19.5F, 31}, {-12.5F, -25.3F, 18, 27}, {12.5F, -25.3F, 18, 27}};
    static final int[] FLUID_COLOURS = {0xFF2C2016, 0xFFCF8A2A, 0xFFE3DAA6};

    private static final Mesh PLANT = plant(), PUMP_FAN = pumpFan(), COOLER_FAN = coolerFan(), FLUID_BAR = fluidBar();

    public static class State extends BlockEntityRenderState {
        public final List<MultiblockGhost.Ghost> ghosts = new ArrayList<>();
        public float time, phase, heat;
        public boolean formed, running;
        public Direction facing = Direction.NORTH;
        public final float[] levels = new float[3];
    }

    public RefineryRenderer(BlockEntityRendererProvider.Context context) {}

    // ------------------------------------------------------------------ geometry

    private static Mesh plant() {
        var b = Mesh.builder(TEX, TEX);
        decks(b);
        ladder(b);
        heater(b);
        pump(b);
        drums(b);
        cooler(b);
        // Column top: insulated dome and the flare stack rising from the upper deck.
        b.push().translate(0, COLUMN_TOP, COLUMN_Z).rotateZ(90).wheel(4.6F, 0, 1.6F, 12, DOME, PIPE_HOT).pop();
        b.tube(FLARE_X, DECKS[1] + 1, FLARE_Z, FLARE_X, FLARE_TIP - 2, FLARE_Z, 0.9F, 8, PIPE_STEEL, PIPE_END);
        b.tube(FLARE_X, FLARE_TIP - 2, FLARE_Z, FLARE_X, FLARE_TIP, FLARE_Z, 1.4F, 8, STACK, PIPE_END);
        b.tile(PLATE).box(FLARE_X - 1.5F, DECKS[1] + 1, FLARE_Z - 1.5F, FLARE_X + 1.5F, DECKS[1] + 1.6F, FLARE_Z + 1.5F);
        // Process lines. Crude: drum -> valve -> feed pump; pump -> heater; heater -> column.
        pipe(b, PIPE_CRUDE, 1, new float[][] {{-16, 18.5F, -5}, {-16, 18.5F, -7.5F}, {16, 18.5F, -7.5F}, {16, 18.5F, -13.5F}});
        valve(b, -4, 18.5F, -7.5F);
        pipe(b, PIPE_CRUDE, 1, new float[][] {{16, 22, -16}, {16, 25, -16}, {16, 25, -6}});
        pipe(b, PIPE_HOT, 1.3F, new float[][] {{10, 27, -3}, {7, 27, -3}, {7, 27, -16}, {5, 27, -16}});
        // Side draws: diesel low, jet fuel high, each down into its drum.
        pipe(b, PIPE_DIESEL, 0.9F, new float[][] {{-5, 33, -16}, {-8, 33, -16}, {-8, 33, -32}, {-8, 28, -32}});
        pipe(b, PIPE_JET, 0.9F, new float[][] {{5, 50, -16}, {8, 50, -16}, {8, 50, -32}, {8, 28, -32}});
        // Overhead vapour line from the top of the column down to the cooler's header.
        pipe(b, PIPE_STEEL, 1.2F, new float[][] {{-2, COLUMN_TOP + 1.6F, COLUMN_Z}, {-2, 71, COLUMN_Z}, {-23, 71, COLUMN_Z},
                {-23, 27.5F, COLUMN_Z}, {-22, 27.5F, COLUMN_Z}});
        // Gauge glasses with their nozzles back to the vessel.
        for (float[] g : GAUGES) {
            b.decal(GAUGE).box(g[0] - 0.75F, g[2] - 0.5F, g[1] - 1, g[0] + 0.75F, g[3] + 0.5F, g[1]);
            b.tile(PIPE_END).box(g[0] - 0.4F, g[2] - 0.5F, g[1] - 1.8F, g[0] + 0.4F, g[2] + 0.3F, g[1] - 1)
                    .box(g[0] - 0.4F, g[3] - 0.3F, g[1] - 1.8F, g[0] + 0.4F, g[3] + 0.5F, g[1] - 1);
        }
        return b.build();
    }

    /** Two grated decks round the column, each with toe boards, posts, knee and hand rails; open at the ladder. */
    private static void decks(Mesh.Builder b) {
        float[][] outline = {{2.5F, -6}, {10, -6}, {10, -26}, {-10, -26}, {-10, -6}, {-2.5F, -6}};
        for (float y : DECKS) {
            b.tile(GRATE);
            b.box(-10, y, -26, 10, y + 1, -21.5F, 0b110000).box(-10, y, -10.5F, -2.5F, y + 1, -6, 0b110000)
                    .box(2.5F, y, -10.5F, 10, y + 1, -6, 0b110000).box(-10, y, -21.5F, -5.5F, y + 1, -10.5F, 0b110000)
                    .box(5.5F, y, -21.5F, 10, y + 1, -10.5F, 0b110000);
            // Support brackets back to the column.
            b.tile(FRAME);
            for (float x : new float[] {-7.5F, 7.5F}) {
                b.bar(x, y - 3, COLUMN_Z, x > 0 ? 5 : -5, y - 0.2F, COLUMN_Z, 0.35F);
            }
            for (int i = 0; i + 1 < outline.length; i++) {
                float[] p = outline[i], q = outline[i + 1];
                b.tile(TOE).box(Math.min(p[0], q[0]) - 0.25F, y + 1, Math.min(p[1], q[1]) - 0.25F,
                        Math.max(p[0], q[0]) + 0.25F, y + 2, Math.max(p[1], q[1]) + 0.25F);
                b.tile(RAIL).bar(p[0], y + 4, p[1], q[0], y + 4, q[1], 0.3F).bar(p[0], y + 7, p[1], q[0], y + 7, q[1], 0.35F);
            }
            b.tile(POST);
            for (float[] p : outline) {
                b.bar(p[0], y + 1, p[1], p[0], y + 7.4F, p[1], 0.4F);
            }
            b.bar(0, y + 1, -26, 0, y + 7.4F, -26, 0.4F).bar(10, y + 1, -16, 10, y + 7.4F, -16, 0.4F).bar(-10, y + 1, -16, -10, y + 7.4F, -16, 0.4F);
        }
    }

    /** Caged ladder up the front of the column, through both decks. */
    private static void ladder(Mesh.Builder b) {
        float z = -9.5F, top = DECKS[1] + 7;
        b.tile(LADDER).bar(-2.2F, 16, z, -2.2F, top, z, 0.3F).bar(2.2F, 16, z, 2.2F, top, z, 0.3F);
        b.tile(RUNG);
        for (float y = 18; y < top - 1; y += 3) {
            b.bar(-2.2F, y, z, 2.2F, y, z, 0.25F);
        }
        b.tile(RAIL);
        for (float y : new float[] {24, 30, 46, 52}) {
            b.bar(-2.8F, y, z, -2.8F, y, -5.5F, 0.25F).bar(-2.8F, y, -5.5F, 2.8F, y, -5.5F, 0.25F).bar(2.8F, y, -5.5F, 2.8F, y, z, 0.25F);
        }
        for (float x : new float[] {-2.8F, 0, 2.8F}) {
            float zz = x == 0 ? -5.5F : -7.5F;
            b.bar(x, 24, zz, x, 37.5F, zz, 0.2F).bar(x, 46, zz, x, 59.5F, zz, 0.2F);
        }
    }

    /** Fired heater on the front-left casing, its stack, and the burner windows the flames show in. */
    private static void heater(Mesh.Builder b) {
        float x0 = 10, x1 = 22, y0 = 16, y1 = 30, z0 = -6, z1 = HEATER_FRONT_Z;
        b.decal(HEATER_FRONT).box(x0, y0, z0, x1, y1, z1, 0b000010);
        b.decal(HEATER_SIDE).box(x0, y0, z0, x1, y1, z1, 0b001101);
        b.decal(HEATER_TOP).box(x0, y0, z0, x1, y1, z1, 0b110000);
        b.tube(16, y1, 0, 16, 42, 0, 1.6F, 8, STACK, PIPE_END);
        b.tube(16, 41, 0, 16, 42.5F, 0, 2.1F, 8, FLANGE, PIPE_END);
        // Burner plenum and fuel manifold under the windows.
        b.tile(PIPE_END).box(x0 + 0.5F, 16.5F, z1, x1 - 0.5F, 18, z1 + 1.2F);
        b.tube(x0 + 0.5F, 17.2F, z1 + 1.6F, x1 - 0.5F, 17.2F, z1 + 1.6F, 0.5F, 6, PIPE_DIESEL, PIPE_END);
    }

    /** Feed pump on its skid with the motor's cooling fan outboard (the fan itself turns). */
    private static void pump(Mesh.Builder b) {
        b.tile(SKID).box(12.5F, 16, -19.5F, 23.5F, 17, -12.5F);
        b.tile(PUMP).box(14, 17, -18.5F, 18, 22, -13.5F);
        b.tile(MOTOR).box(18.5F, 17.5F, -18, 23, 21.5F, -14);
        b.decal(MOTOR_END).box(18.5F, 17.5F, -18, 23, 21.5F, -14, 0b001000);
        b.tile(PIPE_END).box(18, 19, -16.5F, 18.5F, 20, -15.5F);
    }

    /** Diesel and jet-fuel drums on saddles at the back, crude surge drum standing at the front right. */
    private static void drums(Mesh.Builder b) {
        for (int side = -1; side <= 1; side += 2) {
            float a = side < 0 ? -22 : 3, c = side < 0 ? -3 : 22;
            b.push().translate(0, 22.5F, -32).wheel(5.5F, a, c, 16, HEAD, side < 0 ? DRUM_DIESEL : DRUM_JET).pop();
            for (float x : new float[] {a + 3, c - 3}) {
                b.tile(SADDLE).box(x - 1.5F, 16, -36, x + 1.5F, 19, -28);
            }
            // Relief valve on top of each drum.
            b.tube(side * 17, 28, -32, side * 17, 30.5F, -32, 0.6F, 6, PIPE_STEEL, PIPE_END);
            b.tile(PIPE_END).box(side * 17 - 0.8F, 30.5F, -32.8F, side * 17 + 0.8F, 31.6F, -31.2F);
        }
        b.push().translate(-16, 16, 0).rotateZ(90).wheel(5, 0, 18, 16, HEAD, DRUM_CRUDE).pop();
        b.tube(-16, 34, 0, -16, 35.5F, 0, 1.4F, 8, FLANGE, PIPE_END);
        b.tile(PIPE_END).box(-12.2F, 16, -4, -11, 18, 4).box(-21, 16, -4, -19.8F, 18, 4);
    }

    /** Fin-fan cooler on the middle-right casing: four legs, the tube bundle, plenum and fan shroud. */
    private static void cooler(Mesh.Builder b) {
        float x0 = COOLER_X - 6, x1 = COOLER_X + 6, z0 = COOLER_Z - 6, z1 = COOLER_Z + 6;
        b.tile(FRAME);
        for (float x : new float[] {x0 + 0.5F, x1 - 0.5F}) {
            for (float z : new float[] {z0 + 0.5F, z1 - 0.5F}) {
                b.bar(x, 16, z, x, 26, z, 0.6F);
            }
        }
        b.tile(BUNDLE).box(x0, 26, z0, x1, 29, z1);
        b.tile(PLENUM).box(x0 + 0.5F, 29, z0 + 0.5F, x1 - 0.5F, 31, z1 - 0.5F);
        b.tile(FRAME).box(x0 + 0.5F, 31, z0 + 0.5F, x1 - 0.5F, 32.2F, z0 + 1.2F).box(x0 + 0.5F, 31, z1 - 1.2F, x1 - 0.5F, 32.2F, z1 - 0.5F)
                .box(x0 + 0.5F, 31, z0 + 1.2F, x0 + 1.2F, 32.2F, z1 - 1.2F).box(x1 - 1.2F, 31, z0 + 1.2F, x1 - 0.5F, 32.2F, z1 - 1.2F);
        // Fan motor hanging under the bundle, driving the fan shaft up through it.
        b.tile(MOTOR).box(COOLER_X - 1.5F, 21, COOLER_Z - 1.5F, COOLER_X + 1.5F, 25.5F, COOLER_Z + 1.5F);
        b.tube(COOLER_X, 25.5F, COOLER_Z, COOLER_X, COOLER_FAN_Y, COOLER_Z, 0.4F, 6, PIPE_STEEL, PIPE_END);
    }

    private static void valve(Mesh.Builder b, float x, float y, float z) {
        b.tile(PIPE_END).box(x - 1, y - 1.5F, z - 1.5F, x + 1, y + 1.5F, z + 1.5F);
        b.tile(PIPE_STEEL).bar(x, y + 1.5F, z, x, y + 4, z, 0.3F);
        b.push().translate(x, y + 4, z).rotateZ(90).wheel(1.9F, 0, 0.5F, 10, VALVE, FLANGE).pop();
    }

    /** A pipe through the points, with elbows at the bends and flanges where it meets equipment. */
    private static void pipe(Mesh.Builder b, Tile tile, float radius, float[][] points) {
        for (int i = 0; i + 1 < points.length; i++) {
            float[] p = points[i], q = points[i + 1];
            b.tube(p[0], p[1], p[2], q[0], q[1], q[2], radius, 8, tile, PIPE_END);
            if (i > 0) {
                b.tile(tile).box(p[0] - radius * 1.1F, p[1] - radius * 1.1F, p[2] - radius * 1.1F,
                        p[0] + radius * 1.1F, p[1] + radius * 1.1F, p[2] + radius * 1.1F);
            }
        }
        for (int end = 0; end < 2; end++) {
            float[] p = points[end == 0 ? 0 : points.length - 1], q = points[end == 0 ? 1 : points.length - 2];
            float dx = q[0] - p[0], dy = q[1] - p[1], dz = q[2] - p[2], len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            float k = 0.6F / len;
            b.tube(p[0], p[1], p[2], p[0] + dx * k, p[1] + dy * k, p[2] + dz * k, radius + 0.7F, 8, FLANGE, PIPE_END);
        }
    }

    private static Mesh pumpFan() {
        return Mesh.builder(TEX, TEX).wheel(1.8F, 0, 0.3F, 8, FAN, FRAME).build();
    }

    private static Mesh coolerFan() {
        return Mesh.builder(TEX, TEX).rotateZ(90).wheel(5.2F, 0, 0.4F, 16, FAN, FRAME).build();
    }

    /** One pixel tall, scaled to the fluid level in its gauge glass. */
    private static Mesh fluidBar() {
        return Mesh.builder(TEX, TEX).decal(FLUID).box(-0.35F, 0, 0, 0.35F, 1, 0.15F, 0b111110).build();
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(RefineryBlockEntity be, State state, float partial, Vec3 camera, ModelFeatureRenderer.@Nullable CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(be, state, partial, camera, crumbling);
        state.ghosts.clear();
        state.facing = be.getBlockState().getValueOrElse(MachineBlock.FACING, Direction.NORTH);
        state.formed = be.getBlockState().getValueOrElse(CasingBlock.FORMED, false);
        state.running = be.getBlockState().getValueOrElse(MachineBlock.RUNNING, false);
        state.phase = be.industrialPhase(partial);
        state.heat = be.heat(partial);
        for (int i = 0; i < 3; i++) {
            state.levels[i] = be.level(i, partial);
        }
        var level = be.getLevel();
        if (level == null) {
            return;
        }
        state.time = level.getGameTime() + partial;
        state.lightCoords = LightCoordsUtil.getLightCoords(LightCoordsUtil.BrightnessGetter.DEFAULT, level, be.getBlockState(), be.getBlockPos().above());
        if (state.formed) {
            return;
        }
        BlockPos origin = be.getBlockPos();
        for (var part : RefineryBlockEntity.STRUCTURE.parts()) {
            BlockPos at = RefineryBlockEntity.STRUCTURE.worldPos(origin, state.facing, part);
            var bs = level.getBlockState(at);
            if (!part.matches(bs)) {
                boolean column = part.block().get() == com.powerfactor.registry.ModBlocks.DISTILLATION_COLUMN.get();
                state.ghosts.add(new MultiblockGhost.Ghost(at.subtract(origin), !bs.canBeReplaced(), column ? COLUMN : FULL));
            }
        }
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector c, CameraRenderState camera) {
        MultiblockGhost.submit(pose, c, s.ghosts, s.time);
        if (!s.formed) {
            return;
        }
        int light = s.lightCoords;
        pose.pushPose();
        pose.translate(0.5F, 0, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(-s.facing.toYRot()));
        draw(c, pose, PLANT, light);

        pose.pushPose();
        pose.translate(PUMP_FAN_X * PX, PUMP_Y * PX, PUMP_Z * PX);
        pose.mulPose(Axis.XP.rotation(-s.phase * 3));
        draw(c, pose, PUMP_FAN, light);
        pose.popPose();
        pose.pushPose();
        pose.translate(COOLER_X * PX, COOLER_FAN_Y * PX, COOLER_Z * PX);
        pose.mulPose(Axis.YP.rotation(s.phase * 1.4F));
        draw(c, pose, COOLER_FAN, light);
        pose.popPose();

        for (int i = 0; i < 3; i++) {
            float[] g = GAUGES[i];
            float height = (g[3] - g[2]) * Math.clamp(s.levels[i], 0, 1);
            if (height < 0.05F) {
                continue;
            }
            int colour = FLUID_COLOURS[i];
            pose.pushPose();
            pose.translate(g[0] * PX, g[2] * PX, g[1] * PX);
            pose.scale(1, height, 1);
            c.submitCustomGeometry(pose, TYPE, (p, out) -> FLUID_BAR.emit(out, p, colour, light));
            pose.popPose();
        }

        // Burners, the flare and the beacon glow regardless of daylight.
        float heat = s.heat;
        boolean beacon = s.running && (int) (s.time / 20) % 2 == 0;
        float flameScroll = (s.time * 0.55F) % FLAME_PERIOD, flareScroll = (s.time * 0.8F) % FLAME_PERIOD;
        float flicker = 0.82F + 0.12F * (float) Math.sin(s.time * 0.9F) + 0.06F * (float) Math.sin(s.time * 2.3F + 1);
        c.submitCustomGeometry(pose, GLOW, (p, out) -> {
            lamp(out, p, beacon);
            if (heat < 0.02F) {
                return;
            }
            int alpha = (int) (heat * 255) << 24;
            for (int w = 0; w < WINDOWS.length; w++) {
                float x0 = WINDOWS[w], x1 = x0 + 2, z = HEATER_FRONT_Z + 0.04F;
                quad(out, p, x0, WINDOW_Y0, z, x1, WINDOW_Y1, z, 88.5F, 40.5F, 89.5F, 41.5F, (int) (heat * 150) << 24 | 0xFF6A1C);
                float u = FLAME_U + 1 + w * 2, v = FLAME_V + flameScroll;
                quad(out, p, x0, WINDOW_Y0, z + 0.02F, x1, WINDOW_Y1, z + 0.02F, u, v + 5, u + 2, v, alpha | 0xFFFFFF);
            }
            float height = 7 * flicker * heat, v = FLAME_V + flareScroll;
            for (int plane = 0; plane < 2; plane++) {
                float dx = plane == 0 ? 1.8F : 0, dz = plane == 0 ? 0 : 1.8F;
                quad(out, p, FLARE_X - dx, FLARE_TIP, FLARE_Z - dz, FLARE_X + dx, FLARE_TIP + height, FLARE_Z + dz,
                        FLARE_U, v + 16, FLARE_U + 8, v, alpha | 0xFFFFFF);
            }
        });
        pose.popPose();
    }

    /** The red beacon on the top deck's hand rail, blinking while the unit runs: two crossed glowing quads. */
    private static void lamp(com.mojang.blaze3d.vertex.VertexConsumer out, PoseStack.Pose p, boolean on) {
        float x = -10, y = DECKS[1] + 7.4F, z = -26, h = 0.7F;
        Tile t = on ? LAMP_ON : LAMP;
        float u0 = t.u() + 0.5F, v0 = t.v() + 0.5F;
        quad(out, p, x - h, y, z, x + h, y + 2 * h, z, u0, v0 + 1, u0 + 1, v0, 0xFFFFFFFF);
        quad(out, p, x, y, z + h, x, y + 2 * h, z - h, u0, v0 + 1, u0 + 1, v0, 0xFFFFFFFF);
    }

    /** A vertical quad from (x0, y0, z0) to (x1, y1, z1), two-sided, texel UVs (v0 at the bottom). */
    private static void quad(com.mojang.blaze3d.vertex.VertexConsumer out, PoseStack.Pose p, float x0, float y0, float z0, float x1, float y1, float z1,
            float u0, float v0, float u1, float v1, int colour) {
        float[][] corners = {{x0, y0, z0, u0, v0}, {x1, y0, z1, u1, v0}, {x1, y1, z1, u1, v1}, {x0, y1, z0, u0, v1}};
        float nx = -(z1 - z0), nz = x1 - x0, n = (float) Math.sqrt(nx * nx + nz * nz) + 1e-6F;
        for (int side = 0; side < 2; side++) {
            for (int i = 0; i < 4; i++) {
                float[] v = corners[side == 0 ? i : 3 - i];
                out.addVertex(p, v[0] * PX, v[1] * PX, v[2] * PX).setColor(colour).setUv(v[3] / TEX, v[4] / TEX).setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(FULL_BRIGHT).setNormal(p, (side == 0 ? 1 : -1) * nx / n, 0, (side == 0 ? 1 : -1) * nz / n);
            }
        }
    }

    private static void draw(SubmitNodeCollector c, PoseStack pose, Mesh mesh, int light) {
        c.submitCustomGeometry(pose, TYPE, (p, out) -> mesh.emit(out, p, -1, light));
    }

    @Override
    public AABB getRenderBoundingBox(RefineryBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(3, 0, 3).expandTowards(0, 5.5, 0);
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }
}
