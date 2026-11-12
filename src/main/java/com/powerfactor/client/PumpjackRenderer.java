package com.powerfactor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.powerfactor.PowerFactor;
import com.powerfactor.client.Mesh.Tile;
import com.powerfactor.machine.MachineBlock;
import com.powerfactor.oil.PumpjackBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The nodding donkey. An A-frame samson post carries the walking beam on its saddle bearing; the
 * motor's belt turns the gearbox sheave, the gearbox turns two cranks with their counterweights,
 * and the pitman arms rock the beam through the equalizer. At the front the horsehead's arc keeps
 * the bridle plumb, so the carrier bar draws the polished rod straight up out of the stuffing box.
 *
 * <p>Everything is laid out in model pixels in the machine's own frame: +Z toward the well, +Y up.
 * The block model supplies the skid, gearbox, motor, wellhead and control cabinet.
 */
public class PumpjackRenderer implements BlockEntityRenderer<PumpjackBlockEntity, PumpjackRenderer.State> {
    private static final RenderType TYPE = RenderTypes.entityCutout(PowerFactor.id("textures/entity/pumpjack.png"));
    private static final float PX = 1 / 16F;

    // Linkage, in pixels. The equalizer pin hangs below the beam, so it is described by its
    // distance from the saddle bearing and the angle it sits under the beam's axis.
    static final float PIVOT_Y = 34, HEAD_RADIUS = 19, TAIL_Z = -14, TAIL_DROP = 4.5F;
    static final float CRANK_Y = 14, CRANK_Z = -13, CRANK_RADIUS = 5, PITMAN = 15.5F;
    static final float TAIL_RADIUS = (float) Math.hypot(TAIL_Z, TAIL_DROP), TAIL_ANGLE = (float) (Math.PI + Math.atan2(TAIL_DROP, -TAIL_Z));
    /** Carrier bar height with the beam level, and the height the polished rod disappears at. */
    static final float CARRIER_Y = 21, STUFFING_BOX_Y = 8;
    static final float SHEAVE_Y = 14.5F, SHEAVE_Z = -18.6F, SHEAVE_RADIUS = 4.5F, MOTOR_Y = 5, MOTOR_Z = -21, PULLEY_RADIUS = 1.5F;
    static final float DRIVE_X = -6.3F;
    /** Turns of the gearbox sheave and of the motor for each turn of the cranks. */
    static final int SHEAVE_RATIO = 6, MOTOR_RATIO = 18;

    // Texture sheet (128 x 64); tools/gen_textures.py paints the same rectangles.
    private static final int TEX_W = 128, TEX_H = 64;
    private static final Tile BEAM_WEB = new Tile(0, 0, 32, 4), BEAM_FLANGE = new Tile(0, 5, 32, 5), LEG = new Tile(0, 11, 32, 2),
            BRACE = new Tile(0, 14, 32, 2), PITMAN_ARM = new Tile(0, 17, 16, 2), EQUALIZER = new Tile(0, 20, 24, 2),
            ROD = new Tile(0, 23, 24, 1), CABLE = new Tile(0, 25, 24, 1), BELT = new Tile(0, 27, 48, 2),
            HEAD_RIM = new Tile(0, 30, 64, 3), WEIGHT_RIM = new Tile(0, 34, 40, 3), WHEEL_RIM = new Tile(0, 38, 32, 2),
            HEAD_SIDE = new Tile(34, 0, 10, 20), WEIGHT_SIDE = new Tile(46, 0, 10, 6), CRANK_ARM = new Tile(58, 0, 4, 14),
            BEARING = new Tile(64, 0, 8, 6), SHEAVE = new Tile(74, 0, 10, 10), PULLEY = new Tile(86, 0, 4, 4),
            SHAFT = new Tile(92, 0, 4, 4), FAN = new Tile(98, 0, 6, 6), CARRIER = new Tile(64, 8, 8, 3),
            CLAMP = new Tile(64, 12, 4, 3), CHEEK = new Tile(86, 6, 5, 6), PLATFORM = new Tile(64, 16, 16, 8);
    /** The belt's pattern repeats every four texels; the sheet holds one spare repeat to slide into. */
    private static final float BELT_PERIOD = 4;

    private static final Mesh POST = post(), BEAM = beam(), CRANKS = cranks(), PITMANS = pitmans(), SHEAVE_WHEEL = sheave(),
            MOTOR_PULLEY = motorPulley(), BELT_LOOP = belt(), FAN_WHEEL = fan(), BRIDLE = bridle(), CARRIER_BAR = carrier(), POLISHED_ROD = rod();

    public static class State extends BlockEntityRenderState {
        public Direction facing = Direction.NORTH;
        public float phase;
        public float time;
        public final java.util.List<MultiblockGhost.Ghost> ghosts = new java.util.ArrayList<>();
    }

    public PumpjackRenderer(BlockEntityRendererProvider.Context context) {}

    // ------------------------------------------------------------------ geometry

    /** Samson post: four raked legs, side girts, and the saddle bearing's cheeks and axle. */
    private static Mesh post() {
        var b = Mesh.builder(TEX_W, TEX_H);
        for (int side = -1; side <= 1; side += 2) {
            for (int end = -1; end <= 1; end += 2) {
                b.tile(LEG);
                strut(b, side * 6, 2, end * 8, side * 2.5F, 31.5F, end * 1.5F, 1);
            }
            // Girts tie each pair of legs together a third of the way up.
            b.tile(BRACE);
            strut(b, side * 5, 10.5F, -6.3F, side * 5, 10.5F, 6.3F, 0.6F);
            strut(b, side * 3.9F, 20, -4.1F, side * 3.9F, 20, 4.1F, 0.6F);
            b.tile(CHEEK).box(side * 3.3F - 0.7F, 30.5F, -2.5F, side * 3.3F + 0.7F, 36.5F, 2.5F);
        }
        b.tile(BRACE);
        strut(b, -5.2F, 9.5F, 6.7F, 5.2F, 9.5F, 6.7F, 0.6F);
        strut(b, -5.2F, 9.5F, -6.7F, 5.2F, 9.5F, -6.7F, 0.6F);
        // Service platform under the bearing, and the cap plate the cheeks stand on.
        b.tile(PLATFORM).box(-4.5F, 29, -2.5F, 4.5F, 30, 2.5F);
        b.push().translate(0, PIVOT_Y, 0).wheel(1.3F, -4.6F, 4.6F, 8, SHAFT, WHEEL_RIM).pop();
        return b.build();
    }

    /** Walking beam, horsehead and tail bearing, about the saddle bearing with the beam level. */
    private static Mesh beam() {
        var b = Mesh.builder(TEX_W, TEX_H);
        b.tile(BEAM_WEB).box(-1, -2, -17, 1, 2, 11);
        b.tile(BEAM_FLANGE).box(-2.5F, 2, -17, 2.5F, 3, 11).box(-2.5F, -3, -17, 2.5F, -2, 11);
        // Stiffener plates over the saddle and the tail.
        b.tile(BEARING).box(-2, -2, -2, 2, 2, 2).box(-2, -2, -16, 2, 2, -12);
        // Tail bearing and the equalizer that spreads the load to both pitman arms.
        b.tile(BEARING).box(-2.5F, -TAIL_DROP - 1.2F, TAIL_Z - 2, 2.5F, -3, TAIL_Z + 2);
        b.tile(EQUALIZER).box(-11.2F, -TAIL_DROP - 1, TAIL_Z - 1, 11.2F, -TAIL_DROP + 1, TAIL_Z + 1);
        // Horsehead: an arc struck from the saddle bearing, so the bridle always leaves it plumb.
        int steps = 8;
        float from = 31, to = -27;
        float[][] outline = new float[steps + 3][];
        outline[0] = new float[] {9.5F, 9.5F};
        for (int i = 0; i <= steps; i++) {
            double a = Math.toRadians(from + (to - from) * i / steps);
            outline[i + 1] = new float[] {(float) Math.cos(a) * HEAD_RADIUS, (float) Math.sin(a) * HEAD_RADIUS};
        }
        outline[steps + 2] = new float[] {9.5F, -7.5F};
        b.plate(outline, -1.75F, 1.75F, HEAD_SIDE, HEAD_RIM);
        return b.build();
    }

    /** Both cranks about the crankshaft, wrist pin straight up. */
    private static Mesh cranks() {
        var b = Mesh.builder(TEX_W, TEX_H);
        b.wheel(1, -8, 8, 8, SHAFT, WHEEL_RIM);
        float[][] weight = {{-3, 5.6F}, {-4.6F, 8.2F}, {-4.2F, 10.4F}, {-2.4F, 11.6F}, {2.4F, 11.6F}, {4.2F, 10.4F}, {4.6F, 8.2F}, {3, 5.6F}};
        for (int side = -1; side <= 1; side += 2) {
            float in = side * 7.4F, out = side * 9;
            b.tile(CRANK_ARM).box(Math.min(in, out), -2.5F, -2, Math.max(in, out), 10.5F, 2);
            b.plate(weight, side < 0 ? -10 : 7, side < 0 ? -7 : 10, WEIGHT_SIDE, WEIGHT_RIM);
            // Wrist pin through the pitman's lower eye.
            b.push().translate(0, CRANK_RADIUS, 0).wheel(0.8F, side < 0 ? -11.4F : 9, side < 0 ? -9 : 11.4F, 6, SHAFT, WHEEL_RIM).pop();
        }
        return b.build();
    }

    /** Both pitman arms, from the wrist pin (origin) up to the equalizer. */
    private static Mesh pitmans() {
        var b = Mesh.builder(TEX_W, TEX_H);
        for (int side = -1; side <= 1; side += 2) {
            float x = side * 10.6F;
            b.tile(PITMAN_ARM).box(x - 0.5F, -1, -1, x + 0.5F, PITMAN + 1, 1);
        }
        return b.build();
    }

    private static Mesh sheave() {
        return Mesh.builder(TEX_W, TEX_H).wheel(SHEAVE_RADIUS, DRIVE_X - 0.5F, DRIVE_X + 0.5F, 12, SHEAVE, WHEEL_RIM)
                .wheel(0.9F, DRIVE_X + 0.5F, DRIVE_X + 1.1F, 6, SHAFT, WHEEL_RIM).build();
    }

    private static Mesh motorPulley() {
        return Mesh.builder(TEX_W, TEX_H).wheel(PULLEY_RADIUS, DRIVE_X - 0.5F, DRIVE_X + 0.5F, 8, PULLEY, WHEEL_RIM)
                .wheel(0.6F, DRIVE_X + 0.5F, DRIVE_X + 1.1F, 6, SHAFT, WHEEL_RIM).build();
    }

    /** The V-belt as one loop around both wheels; its texture slides to make it run. */
    private static Mesh belt() {
        var b = Mesh.builder(TEX_W, TEX_H);
        double dz = SHEAVE_Z - MOTOR_Z, dy = SHEAVE_Y - MOTOR_Y, distance = Math.hypot(dz, dy);
        double axis = Math.atan2(dy, dz), spread = Math.acos((PULLEY_RADIUS - SHEAVE_RADIUS) / distance);
        java.util.List<float[]> path = new java.util.ArrayList<>();
        // Round the small pulley on the side away from the sheave, then round the sheave back again.
        arc(path, MOTOR_Z, MOTOR_Y, PULLEY_RADIUS + 0.25F, axis + spread, axis + Math.PI * 2 - spread, 6);
        arc(path, SHEAVE_Z, SHEAVE_Y, SHEAVE_RADIUS + 0.25F, axis - spread, axis + spread, 10);
        float run = 0, x0 = DRIVE_X - 0.35F, x1 = DRIVE_X + 0.35F;
        for (int i = 0; i < path.size(); i++) {
            float[] p = path.get(i), q = path.get((i + 1) % path.size());
            float length = (float) Math.hypot(q[0] - p[0], q[1] - p[1]);
            float u0 = BELT.u() + run, u1 = BELT.u() + run + length;
            run += length;
            float ny = -(q[0] - p[0]) / length, nz = (q[1] - p[1]) / length;
            b.quad(new float[] {
                    x0, p[1], p[0], u0, BELT.v(), 0, ny, nz, x1, p[1], p[0], u0, BELT.v() + BELT.h(), 0, ny, nz,
                    x1, q[1], q[0], u1, BELT.v() + BELT.h(), 0, ny, nz, x0, q[1], q[0], u1, BELT.v(), 0, ny, nz});
            // Edges of the belt, so it has thickness from the side.
            for (int edge = 0; edge < 2; edge++) {
                float x = edge == 0 ? x0 : x1, n = edge == 0 ? -1 : 1, in = 0.5F;
                b.quad(new float[] {
                        x, p[1], p[0], u0, BELT.v(), n, 0, 0, x, q[1], q[0], u1, BELT.v(), n, 0, 0,
                        x, q[1] - ny * in, q[0] - nz * in, u1, BELT.v() + 1, n, 0, 0, x, p[1] - ny * in, p[0] - nz * in, u0, BELT.v() + 1, n, 0, 0});
            }
        }
        return b.build();
    }

    private static void arc(java.util.List<float[]> path, float z, float y, float radius, double from, double to, int steps) {
        for (int i = 0; i <= steps; i++) {
            double a = from + (to - from) * i / steps;
            path.add(new float[] {z + (float) Math.cos(a) * radius, y + (float) Math.sin(a) * radius});
        }
    }

    private static Mesh fan() {
        return Mesh.builder(TEX_W, TEX_H).wheel(2.2F, 0, 0.3F, 8, FAN, WHEEL_RIM).build();
    }

    /** Twin wire ropes from the horsehead down to the carrier bar; one pixel long, stretched to fit. */
    private static Mesh bridle() {
        var b = Mesh.builder(TEX_W, TEX_H).tile(CABLE);
        for (int side = -1; side <= 1; side += 2) {
            b.box(side * 1.1F - 0.3F, -1, -0.3F, side * 1.1F + 0.3F, 0, 0.3F);
        }
        return b.build();
    }

    private static Mesh carrier() {
        return Mesh.builder(TEX_W, TEX_H).tile(CARRIER).box(-2.5F, -0.75F, -1, 2.5F, 0.75F, 1)
                .tile(CLAMP).box(-1, 0.75F, -1, 1, 2.5F, 1).build();
    }

    /** One pixel of polished rod, stretched from the stuffing box up through the rod clamp. */
    private static Mesh rod() {
        return Mesh.builder(TEX_W, TEX_H).tile(ROD).box(-0.5F, 0, -0.5F, 0.5F, 1, 0.5F, 0b011111).build();
    }

    /** A square bar between two points. */
    private static void strut(Mesh.Builder b, float x0, float y0, float z0, float x1, float y1, float z1, float half) {
        float dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        float lean = (float) Math.toDegrees(Math.asin(-dx / length)), pitch = (float) Math.toDegrees(Math.atan2(dz, dy));
        b.push().translate(x0, y0, z0).rotateX(pitch).rotateZ(lean).box(-half, 0, -half, half, length, half).pop();
    }

    // ------------------------------------------------------------------ motion

    /** Where the linkage stands for a crank angle: everything the renderer needs to pose the parts. */
    record Pose(float headAngle, float pinY, float pinZ, float tailY, float tailZ) {
        static Pose at(float phase) {
            float pinY = CRANK_Y + (float) Math.cos(phase) * CRANK_RADIUS, pinZ = CRANK_Z - (float) Math.sin(phase) * CRANK_RADIUS;
            float dy = pinY - PIVOT_Y;
            double reach = Math.hypot(dy, pinZ);
            // Four-bar linkage: the equalizer pin is where its circle about the saddle meets the pitman's about the wrist pin.
            double tail = Math.atan2(dy, pinZ) + Math.PI * 2 - Math.acos(Math.clamp(
                    (TAIL_RADIUS * TAIL_RADIUS + reach * reach - PITMAN * PITMAN) / (2 * TAIL_RADIUS * reach), -1, 1));
            return new Pose((float) (tail - TAIL_ANGLE), pinY, pinZ,
                    PIVOT_Y + TAIL_RADIUS * (float) Math.sin(tail), TAIL_RADIUS * (float) Math.cos(tail));
        }
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(PumpjackBlockEntity be, State state, float partial, Vec3 camera, ModelFeatureRenderer.@Nullable CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(be, state, partial, camera, crumbling);
        state.facing = be.getBlockState().getValueOrElse(MachineBlock.FACING, Direction.NORTH);
        state.phase = be.crankPhase(partial);
        state.ghosts.clear();
        if (be.getLevel() != null) {
            state.time = be.getLevel().getGameTime() + partial;
            if (!be.getBlockState().getValueOrElse(com.powerfactor.multiblock.CasingBlock.FORMED, false)) {
                var structure = be.buildStructure();
                for (var part : structure.parts()) {
                    var at = structure.worldPos(be.getBlockPos(), state.facing, part);
                    var block = be.getLevel().getBlockState(at);
                    if (!part.matches(block)) {
                        state.ghosts.add(new MultiblockGhost.Ghost(at.subtract(be.getBlockPos()), !block.canBeReplaced(),
                                new AABB(0.02, 0.02, 0.02, 0.98, 0.98, 0.98)));
                    }
                }
            }
            state.lightCoords = LightCoordsUtil.getLightCoords(LightCoordsUtil.BrightnessGetter.DEFAULT, be.getLevel(), be.getBlockState(),
                    be.getBlockPos().above());
        }
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector c, CameraRenderState camera) {
        MultiblockGhost.submit(pose, c, s.ghosts, s.time);
        int light = s.lightCoords;
        Pose link = Pose.at(s.phase);
        pose.pushPose();
        pose.translate(0.5F, 0, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(-s.facing.toYRot()));
        draw(c, pose, POST, light);

        pose.pushPose();
        pose.translate(0, PIVOT_Y * PX, 0);
        pose.mulPose(Axis.XP.rotation(-link.headAngle()));
        draw(c, pose, BEAM, light);
        pose.popPose();

        pose.pushPose();
        pose.translate(0, CRANK_Y * PX, CRANK_Z * PX);
        pose.mulPose(Axis.XP.rotation(-s.phase));
        draw(c, pose, CRANKS, light);
        pose.popPose();

        pose.pushPose();
        pose.translate(0, link.pinY() * PX, link.pinZ() * PX);
        pose.mulPose(Axis.XP.rotation((float) Math.atan2(link.tailZ() - link.pinZ(), link.tailY() - link.pinY())));
        draw(c, pose, PITMANS, light);
        pose.popPose();

        // Belt drive: the motor spins three times for each turn of the big sheave.
        spin(c, pose, SHEAVE_WHEEL, SHEAVE_Y, SHEAVE_Z, 0, s.phase * SHEAVE_RATIO, light);
        spin(c, pose, MOTOR_PULLEY, MOTOR_Y, MOTOR_Z, 0, s.phase * MOTOR_RATIO, light);
        spin(c, pose, FAN_WHEEL, MOTOR_Y, MOTOR_Z, 4.2F, s.phase * MOTOR_RATIO, light);
        float travelled = s.phase * SHEAVE_RATIO * SHEAVE_RADIUS;
        float slide = (travelled % BELT_PERIOD + BELT_PERIOD) % BELT_PERIOD / TEX_W;
        c.submitCustomGeometry(pose, TYPE, (p, out) -> BELT_LOOP.emit(out, p, -1, light, slide, 0));

        // The bridle unwinds from the horsehead as it nods, so the carrier bar rises by the arc it lets out.
        float carrierY = CARRIER_Y + HEAD_RADIUS * link.headAngle();
        pose.pushPose();
        pose.translate(0, PIVOT_Y * PX, (HEAD_RADIUS + 0.3F) * PX);
        pose.scale(1, PIVOT_Y - carrierY, 1);
        draw(c, pose, BRIDLE, light);
        pose.popPose();
        pose.pushPose();
        pose.translate(0, carrierY * PX, (HEAD_RADIUS + 0.3F) * PX);
        draw(c, pose, CARRIER_BAR, light);
        pose.popPose();
        pose.pushPose();
        pose.translate(0, STUFFING_BOX_Y * PX, (HEAD_RADIUS + 0.3F) * PX);
        pose.scale(1, carrierY + 4 - STUFFING_BOX_Y, 1);
        draw(c, pose, POLISHED_ROD, light);
        pose.popPose();
        pose.popPose();
    }

    private static void draw(SubmitNodeCollector c, PoseStack pose, Mesh mesh, int light) {
        c.submitCustomGeometry(pose, TYPE, (p, out) -> mesh.emit(out, p, -1, light));
    }

    private static void spin(SubmitNodeCollector c, PoseStack pose, Mesh mesh, float y, float z, float x, float angle, int light) {
        pose.pushPose();
        pose.translate(x * PX, y * PX, z * PX);
        pose.mulPose(Axis.XP.rotation(-angle));
        draw(c, pose, mesh, light);
        pose.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(PumpjackBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(1.6, 0, 1.6).expandTowards(0, 2.2, 0).expandTowards(0, -1, 0);
    }
}
