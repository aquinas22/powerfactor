package com.powerfactor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.powerfactor.PowerFactor;
import com.powerfactor.generator.WindTurbineBlockEntity;
import com.powerfactor.machine.MachineBlock;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A utility-scale wind turbine: the nacelle (with cooler, wind vane, cup anemometer and aviation
 * lights) yawing gently on its tower, and a three-bladed rotor. The spinner and blades are generated
 * geometry: each blade is lofted from aerofoil sections that blend from a round root into a twisted,
 * tapering aerofoil, with cone angle and pre-bend, red tip bands, and live blade pitch.
 */
public class WindTurbineRenderer implements BlockEntityRenderer<WindTurbineBlockEntity, WindTurbineRenderer.State> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(PowerFactor.id("wind_turbine"), "main");
    private static final Identifier TEXTURE = PowerFactor.id("textures/entity/wind_turbine.png");
    private static final RenderType SOLID = RenderTypes.entityCutout(TEXTURE);
    private static final RenderType GLOW = RenderTypes.entityTranslucentEmissive(TEXTURE);
    /** A pure white patch of the texture, tinted by vertex colour for the generated geometry. */
    private static final float WHITE_U = 124 / 128F, WHITE_V = 124 / 128F;
    private static final int FULL_BRIGHT = 0xF000F0;

    private static final int BLADE_WHITE = 0xFFF3F5F5, BLADE_RED = 0xFFD2352B, SPINNER = 0xFFEEF1F1;
    private static final float TILT = 5F, CONE = 2.5F;
    /** Blade sections: distance from the hub centre, in blocks. */
    private static final float[] STATIONS = {0.25F, 0.5F, 0.8F, 1.05F, 1.35F, 1.7F, 2.1F, 2.6F, 3.1F, 3.6F, 4.1F, 4.6F, 5.0F, 5.35F,
            5.65F, 5.95F, 6.2F, 6.4F, 6.5F};
    /** Chordwise sample points (0 = leading edge, 1 = trailing edge). */
    private static final float[] XS = {0F, 0.02F, 0.08F, 0.18F, 0.32F, 0.5F, 0.7F, 0.88F, 1F};
    private static final int LOOP = XS.length * 2 - 2;

    public static class State extends BlockEntityRenderState {
        public Direction facing = Direction.NORTH;
        public float yaw, rotor, pitch, anemometer;
        public boolean lightOn;
        /** 0 by day, 1 at night: how much glow the aviation lights throw. */
        public float darkness;
    }

    private final ModelPart nacelle;
    private final ModelPart anemometer;

    public WindTurbineRenderer(BlockEntityRendererProvider.Context context) {
        ModelPart root = context.bakeLayer(LAYER);
        this.nacelle = root.getChild("nacelle");
        this.anemometer = root.getChild("anemometer");
    }

    /**
     * Entity-model coordinates (pixels; -Y up, -Z toward the rotor), origin at the centre of the
     * nacelle block's top face.
     */
    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("nacelle", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-10, -18, -14, 20, 18, 44)          // nacelle body
                .texOffs(0, 62).addBox(-7, -16, -16, 14, 14, 2)           // main bearing housing
                .texOffs(32, 62).addBox(-7, -22, 16, 14, 4, 10)           // roof cooler
                .texOffs(80, 62).addBox(-4.5F, -27, 22, 1, 9, 1)          // anemometer mast
                .texOffs(80, 62).addBox(3.5F, -27, 22, 1, 9, 1)           // wind vane mast
                .texOffs(84, 62).addBox(3.75F, -29.5F, 22.5F, 0.5F, 3, 6), // wind vane fin
                PartPose.ZERO);
        PartDefinition cups = root.addOrReplaceChild("anemometer", CubeListBuilder.create()
                .texOffs(100, 62).addBox(-1, -1, -1, 2, 2, 2), PartPose.ZERO);
        for (int i = 0; i < 3; i++) {
            cups.addOrReplaceChild("arm" + i, CubeListBuilder.create()
                    .texOffs(100, 66).addBox(0, -0.3F, -0.3F, 4, 0.6F, 0.6F)
                    .texOffs(110, 62).addBox(3.4F, -0.8F, -0.8F, 1.6F, 1.6F, 1.6F),
                    PartPose.rotation(0, i * (float) (Math.PI * 2 / 3), 0));
        }
        return LayerDefinition.create(mesh, 128, 128);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(WindTurbineBlockEntity be, State state, float partialTicks, Vec3 camera,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, camera, breakProgress);
        state.facing = be.getBlockState().getValueOrElse(MachineBlock.FACING, Direction.NORTH);
        state.yaw = be.yawOffset(partialTicks);
        state.rotor = be.rotorAngle(partialTicks);
        state.pitch = be.bladePitch(partialTicks);
        state.anemometer = be.anemometerAngle(partialTicks);
        if (be.getLevel() != null) {
            // Wind farms flash their aviation lights in unison: 30 flashes a minute.
            state.lightOn = (be.getLevel().getGameTime() % 40) < 16;
            state.darkness = Math.clamp(be.getLevel().getSkyDarken() / 8F, 0F, 1F);
            state.lightCoords = LightCoordsUtil.getLightCoords(LightCoordsUtil.BrightnessGetter.DEFAULT, be.getLevel(), be.getBlockState(),
                    be.getBlockPos().above());
        }
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        int light = state.lightCoords;
        pose.pushPose();
        pose.translate(0.5F, 1.0F, 0.5F);
        // Local frame from here: +Z toward the rotor, +Y up, units of blocks.
        pose.mulPose(Axis.YP.rotationDegrees(-state.facing.toYRot() + state.yaw));

        pose.pushPose();
        pose.scale(1, -1, -1);
        collector.submitModelPart(nacelle, pose, SOLID, light, OverlayTexture.NO_OVERLAY, null, -1, state.breakProgress);
        pose.translate(-4 / 16F, -27 / 16F, 22.5F / 16F);
        pose.mulPose(Axis.YP.rotationDegrees(state.anemometer));
        collector.submitModelPart(anemometer, pose, SOLID, light, OverlayTexture.NO_OVERLAY, null, -1, state.breakProgress);
        pose.popPose();

        // Aviation lights on the roof, either side of the cooler. By night they throw a glow.
        boolean on = state.lightOn;
        int halo = (int) (0x60 * state.darkness) << 24 | 0xFF2010;
        collector.submitCustomGeometry(pose, GLOW, (p, buf) -> {
            for (float x : new float[] {-0.52F, 0.52F}) {
                cube(buf, p, x, 1.18F, -0.62F, 0.05F, on ? 0xFFFF3020 : 0xFF501510, FULL_BRIGHT);
                if (on && state.darkness > 0.05F) {
                    cube(buf, p, x, 1.18F, -0.62F, 0.14F, halo, FULL_BRIGHT);
                }
            }
        });

        // Rotor: the shaft is tilted up a few degrees; the rotor turns clockwise seen from upwind.
        pose.pushPose();
        pose.translate(0, (float) (WindTurbineBlockEntity.HUB_HEIGHT - 1.0), (float) WindTurbineBlockEntity.HUB_FORWARD);
        pose.mulPose(Axis.XP.rotationDegrees(-TILT));
        pose.mulPose(Axis.ZP.rotationDegrees(-state.rotor));
        collector.submitCustomGeometry(pose, SOLID, (p, buf) -> spinner(buf, p, light));
        float[][][] blade = bladeMesh(state.pitch);
        for (int k = 0; k < 3; k++) {
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotationDegrees(k * 120F));
            collector.submitCustomGeometry(pose, SOLID, (p, buf) -> emitBlade(buf, p, blade, light));
            pose.popPose();
        }
        pose.popPose();
        pose.popPose();
    }

    // ------------------------------------------------------------------ blade geometry

    /** [station][loop point][x, y, z, nx, ny, nz] in the blade frame: span +Y, upwind +Z, leading edge +X at zero pitch. */
    static float[][][] bladeMesh(float pitch) {
        float[][][] m = new float[STATIONS.length][LOOP][6];
        for (int j = 0; j < STATIONS.length; j++) {
            float r = STATIONS[j];
            float s = smoothstep(0.5F, 1.35F, r);
            float chord = r <= 1.35F ? 0.26F + (0.6F - 0.26F) * s : 0.14F + 0.46F * (float) Math.pow((6.5F - r) / 5.15F, 1.1);
            if (j == STATIONS.length - 1) {
                chord *= 0.5F;  // rounded tip
            }
            float thick = r <= 1.35F ? 0.36F : 0.36F - 0.2F * (r - 1.35F) / 5.15F;
            float twist = r <= 1.35F ? 14F : 14F * (float) Math.pow(1 - (r - 1.35F) / 5.15F, 1.3);
            double th = Math.toRadians(twist + pitch);
            float cx = (float) Math.cos(th), cz = (float) Math.sin(th);      // chord direction, trailing -> leading
            float ux = (float) Math.sin(th), uz = (float) -Math.cos(th);     // suction side (downwind at zero pitch)
            float lift = (float) (r * Math.sin(Math.toRadians(CONE)) + 0.012 * r * r);  // cone + pre-bend, upwind
            for (int k = 0; k < LOOP; k++) {
                boolean upper = k < XS.length;
                float x = upper ? XS[k] : XS[LOOP - k];
                float yt = 5 * thick * (float) (0.2969 * Math.sqrt(x) - 0.126 * x - 0.3516 * x * x + 0.2843 * x * x * x - 0.1036 * x * x * x * x);
                float camber = 0.03F * (float) Math.sin(Math.PI * x);
                float a = (0.3F - x) * chord, b = (upper ? camber + yt : camber - 0.75F * yt) * chord;
                double phi = (upper ? 1 : -1) * Math.PI * x;
                float ca = 0.13F * (float) Math.cos(phi), cb = 0.13F * (float) Math.sin(phi);
                a = ca + (a - ca) * s;
                b = cb + (b - cb) * s;
                m[j][k][0] = a * cx + b * ux;
                m[j][k][1] = r;
                m[j][k][2] = a * cz + b * uz + lift;
            }
        }
        // Smooth normals: across the section and along the span, pointed away from the section's centre.
        for (int j = 0; j < STATIONS.length; j++) {
            float mx = 0, mz = 0;
            for (int k = 0; k < LOOP; k++) {
                mx += m[j][k][0] / LOOP;
                mz += m[j][k][2] / LOOP;
            }
            int jp = Math.min(j + 1, STATIONS.length - 1), jm = Math.max(j - 1, 0);
            for (int k = 0; k < LOOP; k++) {
                float[] a = m[j][(k + 1) % LOOP], b = m[j][(k + LOOP - 1) % LOOP], c = m[jp][k], d = m[jm][k];
                float t1x = a[0] - b[0], t1y = a[1] - b[1], t1z = a[2] - b[2];
                float t2x = c[0] - d[0], t2y = c[1] - d[1], t2z = c[2] - d[2];
                float nx = t1y * t2z - t1z * t2y, ny = t1z * t2x - t1x * t2z, nz = t1x * t2y - t1y * t2x;
                float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (len < 1e-6F) {
                    nx = m[j][k][0] - mx;
                    ny = 0;
                    nz = m[j][k][2] - mz;
                    len = (float) Math.sqrt(nx * nx + nz * nz) + 1e-6F;
                }
                if (nx * (m[j][k][0] - mx) + nz * (m[j][k][2] - mz) < 0) {
                    len = -len;
                }
                m[j][k][3] = nx / len;
                m[j][k][4] = ny / len;
                m[j][k][5] = nz / len;
            }
        }
        return m;
    }

    private static void emitBlade(VertexConsumer buf, PoseStack.Pose p, float[][][] m, int light) {
        for (int j = 0; j + 1 < m.length; j++) {
            float mid = (STATIONS[j] + STATIONS[j + 1]) / 2;
            int color = (mid > 5.35F && mid < 5.65F) || mid > 5.95F ? BLADE_RED : BLADE_WHITE;
            for (int k = 0; k < LOOP; k++) {
                int k1 = (k + 1) % LOOP;
                vertex(buf, p, m[j][k], color, light);
                vertex(buf, p, m[j][k1], color, light);
                vertex(buf, p, m[j + 1][k1], color, light);
                vertex(buf, p, m[j + 1][k], color, light);
            }
        }
        // Tip cap.
        float[][] tip = m[m.length - 1];
        float cx = 0, cy = 0, cz = 0;
        for (float[] v : tip) {
            cx += v[0] / LOOP;
            cy += v[1] / LOOP;
            cz += v[2] / LOOP;
        }
        float[] centre = {cx, cy + 0.04F, cz, 0, 1, 0};
        for (int k = 0; k < LOOP; k++) {
            vertex(buf, p, tip[k], BLADE_RED, light);
            vertex(buf, p, tip[(k + 1) % LOOP], BLADE_RED, light);
            vertex(buf, p, centre, BLADE_RED, light);
            vertex(buf, p, centre, BLADE_RED, light);
        }
    }

    private static void vertex(VertexConsumer buf, PoseStack.Pose p, float[] v, int color, int light) {
        buf.addVertex(p, v[0], v[1], v[2]).setColor(color).setUv(WHITE_U, WHITE_V).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(p, v[3], v[4], v[5]);
    }

    // ------------------------------------------------------------------ spinner (nose cone)

    /** Profile of the spinner as (z along the shaft, radius): a short drum, then a paraboloid nose. */
    private static final float[][] SPINNER_PROFILE;

    static {
        float[] radii = {0.43F, 0.42F, 0.40F, 0.37F, 0.33F, 0.28F, 0.22F, 0.15F, 0.08F, 0F};
        SPINNER_PROFILE = new float[radii.length + 1][];
        SPINNER_PROFILE[0] = new float[] {-0.3F, 0.41F};
        for (int i = 0; i < radii.length; i++) {
            float q = radii[i] / 0.43F;
            SPINNER_PROFILE[i + 1] = new float[] {0.05F + 0.8F * (1 - q * q), radii[i]};
        }
    }

    private static void spinner(VertexConsumer buf, PoseStack.Pose p, int light) {
        int seg = 24;
        float[][] prof = SPINNER_PROFILE;
        for (int i = 0; i + 1 < prof.length; i++) {
            for (int a = 0; a < seg; a++) {
                double t0 = Math.PI * 2 * a / seg, t1 = Math.PI * 2 * (a + 1) / seg;
                spinnerVertex(buf, p, prof, i, t0, light);
                spinnerVertex(buf, p, prof, i, t1, light);
                spinnerVertex(buf, p, prof, i + 1, t1, light);
                spinnerVertex(buf, p, prof, i + 1, t0, light);
            }
        }
    }

    private static void spinnerVertex(VertexConsumer buf, PoseStack.Pose p, float[][] prof, int i, double theta, int light) {
        float z = prof[i][0], rad = prof[i][1];
        float[] a = prof[Math.max(0, i - 1)], b = prof[Math.min(prof.length - 1, i + 1)];
        float dz = b[0] - a[0], dr = b[1] - a[1];
        float len = (float) Math.sqrt(dz * dz + dr * dr);
        float nr = dz / len, nz = -dr / len;
        float c = (float) Math.cos(theta), s = (float) Math.sin(theta);
        buf.addVertex(p, rad * c, rad * s, z).setColor(SPINNER).setUv(WHITE_U, WHITE_V).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(p, nr * c, nr * s, nz);
    }

    // ------------------------------------------------------------------ helpers

    private static void cube(VertexConsumer buf, PoseStack.Pose p, float x, float y, float z, float h, int color, int light) {
        float[][] faces = {
                {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (float[] n : faces) {
            // Two axes spanning the face.
            float[] u = n[0] != 0 ? new float[] {0, 1, 0} : new float[] {1, 0, 0};
            float[] v = {n[1] * u[2] - n[2] * u[1], n[2] * u[0] - n[0] * u[2], n[0] * u[1] - n[1] * u[0]};
            float[][] corners = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
            for (float[] cr : corners) {
                buf.addVertex(p, x + h * (n[0] + cr[0] * u[0] + cr[1] * v[0]), y + h * (n[1] + cr[0] * u[1] + cr[1] * v[1]),
                        z + h * (n[2] + cr[0] * u[2] + cr[1] * v[2]))
                        .setColor(color).setUv(WHITE_U, WHITE_V).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, n[0], n[1], n[2]);
            }
        }
    }

    private static float smoothstep(float a, float b, float x) {
        float t = Math.clamp((x - a) / (b - a), 0F, 1F);
        return t * t * (3 - 2 * t);
    }

    @Override
    public AABB getRenderBoundingBox(WindTurbineBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(8);
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }
}
