package com.powerfactor.client.fission;

import com.mojang.blaze3d.vertex.PoseStack;
import com.powerfactor.client.fx.Fx;
import com.powerfactor.client.wash.WashPlantRenderer;
import com.powerfactor.fission.ReactorControllerBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The fission reactor, drawn from its controller: above each fuel column a drive mechanism whose rod shaft
 * rises out of the roof as the rods are withdrawn (and drops home on a SCRAM, a red lamp pulsing); inside,
 * the absorber rods hanging into the fuel at their real depth; and the pool lit Cherenkov blue around the
 * fuel, brighter and wider with power, with steam haze rising from the roof when the core runs hot.
 * Cherenkov light only comes from fuel in water: a rippling sheath round each column, sparks in the pool, the
 * glass lit with caustics, and a blue flash when the power surges. Dry fuel glows with heat instead.
 */
public class ReactorRenderer implements BlockEntityRenderer<ReactorControllerBlockEntity, ReactorRenderer.State> {
    private static final int CHERENKOV = 0xFF3A8CFF, CHERENKOV_CORE = 0xFFB8E4FF, WATER = 0xFF3F76E4;


    public static class State extends BlockEntityRenderState {
        int[] bounds = new int[0], cols = new int[0];
        float[] rods = new float[0];
        float power, temp, time;
        boolean scrammed;
        int ox, oy, oz;
        java.util.List<Pane> glass = java.util.List.of();
        int[] wet = new int[0];
        int coolant;
        /** The blue flash of a power surge, 1 fading to 0. */
        float flash;
    }

    /** Per reactor: power last tick, game time of the last flash, last tick seen. */
    private static final java.util.Map<Long, double[]> SURGE = new java.util.HashMap<>();

    /** A Reactor Glass pane in a wall: where it is, which way it faces, how near the fuel it is (0..1), and its light. */
    record Pane(int x, int y, int z, net.minecraft.core.Direction face, float near, int light) {}

    /** Glass panes per reactor, rescanned every two seconds. */
    private static final java.util.Map<Long, java.util.List<Pane>> GLASS = new java.util.HashMap<>();
    private static final java.util.Map<Long, Long> SCANNED = new java.util.HashMap<>();

    private static java.util.List<Pane> glass(ReactorControllerBlockEntity be, int[] b, int[] cols, long now) {
        long key = be.getBlockPos().asLong();
        Long at = SCANNED.get(key);
        if (at != null && now - at < 40 && GLASS.containsKey(key)) return GLASS.get(key);
        var level = be.getLevel();
        java.util.List<Pane> out = new java.util.ArrayList<>();
        for (int x = b[0]; x <= b[3]; x++) {
            for (int y = b[1]; y <= b[4]; y++) {
                for (int z = b[2]; z <= b[5]; z++) {
                    int faces = (x == b[0] || x == b[3] ? 1 : 0) + (y == b[1] || y == b[4] ? 1 : 0) + (z == b[2] || z == b[5] ? 1 : 0);
                    if (faces != 1) continue;
                    var pos = new net.minecraft.core.BlockPos(x, y, z);
                    if (!level.getBlockState(pos).is(com.powerfactor.registry.ModFission.REACTOR_GLASS.get())) continue;
                    net.minecraft.core.Direction d = x == b[0] ? net.minecraft.core.Direction.WEST : x == b[3] ? net.minecraft.core.Direction.EAST
                            : y == b[1] ? net.minecraft.core.Direction.DOWN : y == b[4] ? net.minecraft.core.Direction.UP
                            : z == b[2] ? net.minecraft.core.Direction.NORTH : net.minecraft.core.Direction.SOUTH;
                    // Panes beside the fuel blaze; those across the pool only catch its light.
                    double near = 99;
                    for (int c = 0; c + 1 < cols.length; c += 2) near = Math.min(near, Math.hypot(cols[c] - x, cols[c + 1] - z));
                    var inside = pos.relative(d.getOpposite());
                    int light = net.minecraft.util.LightCoordsUtil.getLightCoords(net.minecraft.util.LightCoordsUtil.BrightnessGetter.DEFAULT, level,
                            level.getBlockState(inside), inside);
                    out.add(new Pane(x, y, z, d, (float) Mth.clamp(1.35 - near / 4.5, 0.3, 1), light));
                }
            }
        }
        GLASS.put(key, out);
        SCANNED.put(key, now);
        return out;
    }

    public ReactorRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(ReactorControllerBlockEntity be, State s, float partial, Vec3 camera, ModelFeatureRenderer.@Nullable CrumblingOverlay crumble) {
        BlockEntityRenderer.super.extractRenderState(be, s, partial, camera, crumble);
        s.bounds = be.clientBounds;
        s.cols = be.clientColumns;
        s.rods = be.clientRods;
        s.power = be.clientPower;
        s.temp = be.clientTemp;
        s.scrammed = be.clientScrammed;
        s.ox = be.getBlockPos().getX();
        s.oy = be.getBlockPos().getY();
        s.oz = be.getBlockPos().getZ();
        s.time = be.getLevel() == null ? 0 : (be.getLevel().getGameTime() % 72000L) + partial;
        s.glass = s.bounds.length < 6 || be.getLevel() == null ? java.util.List.of() : glass(be, s.bounds, s.cols, be.getLevel().getGameTime());
        s.wet = be.clientWet;
        s.coolant = be.clientCoolant;
        if (be.getLevel() != null && s.bounds.length >= 6) {
            long now = be.getLevel().getGameTime();
            double[] g = SURGE.computeIfAbsent(be.getBlockPos().asLong(), k -> new double[] {be.clientPower, -1000, now});
            if (g[2] != now) {
                // A fast rise in power, or running past rated: the blue flash.
                if (be.clientPower - g[0] > 0.12 || be.clientPower > 1.25 && (now - (long) g[1]) > 30) g[1] = now;
                g[0] = be.clientPower;
                g[2] = now;
                ReactorEffects.tick(be, now);
            }
            s.flash = Mth.clamp(1 - (s.time - (float) (g[1] % 72000L)) / 16F, 0, 1);
            if (s.time < g[1] % 72000L) s.flash = 0;
        }
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector c, CameraRenderState camera) {
        if (s.bounds.length < 6) return;
        int light = s.lightCoords;
        float[] W = WashPlantRenderer.WHITE, G = WashPlantRenderer.GALV;
        int minY = s.bounds[1], maxY = s.bounds[4];
        int inner = maxY - minY - 1;
        float glow = Mth.clamp(s.power, 0, 1.5F);
        // Cherenkov light reads strongly even at low power: brightness follows the square root.
        float sheen = Mth.sqrt(glow);
        pose.pushPose();
        pose.translate(-s.ox, -s.oy, -s.oz);   // world coordinates from here on
        int n = Math.min(s.cols.length / 2, s.rods.length);
        panes(c, pose, camera, s, n, glow, sheen);
        for (int i = 0; i < n; i++) {
            float x = s.cols[i * 2] + 0.5F, z = s.cols[i * 2 + 1] + 0.5F;
            float rod = s.rods[i];
            float top = maxY + 1;
            // Drive housing on the roof, and the rod shaft above it: withdrawn rods ride up out of the core.
            float shaft = (1 - rod) * inner * 0.6F;
            c.submitCustomGeometry(pose, RenderTypes.entityCutout(WashPlantRenderer.TEX), (p, b) -> {
                WashPlantRenderer.box(b, p, x - 0.22F, top, z - 0.22F, x + 0.22F, top + 0.5F, z + 0.22F, G, 0xFFB8C0C6, light);
                WashPlantRenderer.box(b, p, x - 0.16F, top + 0.5F, z - 0.16F, x + 0.16F, top + 0.62F, z + 0.16F, W, 0xFF39424A, light);
                WashPlantRenderer.box(b, p, x - 0.05F, top + 0.62F, z - 0.05F, x + 0.05F, top + 0.62F + shaft + 0.05F, z + 0.05F, W, 0xFFDDE6EC, light);
                // Inside: the absorber rod from the roof down to its insertion depth.
                float depth = rod * inner;
                if (depth > 0.02F) WashPlantRenderer.box(b, p, x - 0.07F, maxY - depth, z - 0.07F, x + 0.07F, maxY, z + 0.07F, W, 0xFF20262C, light);
            });
            // Status lamp: green at power, pulsing red when scrammed.
            pose.pushPose();
            pose.translate(x, top + 0.66F + shaft, z);
            int lamp = s.scrammed ? Fx.alpha(0xFFFF3A3A, 0.4F + 0.6F * Math.abs(Mth.sin(s.time * 0.25F))) : Fx.alpha(0xFF7CFF8A, 0.3F + 0.6F * glow);
            Fx.glow(c, pose, camera.orientation, 0.22F, lamp);
            pose.popPose();
            column(c, pose, camera, s, i, x, z, minY + 1, maxY, glow, sheen);
        }
        float cx = (s.bounds[0] + s.bounds[3] + 1) / 2F, cy = (minY + maxY + 1) / 2F, cz = (s.bounds[2] + s.bounds[5] + 1) / 2F;
        // Steam haze over a hot core.
        if (s.temp > 450) {
            float heat = Mth.clamp((s.temp - 450) / 600, 0, 1);
            for (int k = 0; k < 6; k++) {
                float life = (s.time * 0.02F + k / 6F) % 1F;
                pose.pushPose();
                pose.translate(cx + Mth.sin(k * 2.1F) * 1.2F, maxY + 1.5F + life * 3, cz + Mth.cos(k * 1.7F) * 1.2F);
                Fx.glow(c, pose, camera.orientation, 1.2F + life * 2, Fx.alpha(0xFFE8F0F4, 0.25F * heat * (1 - life)));
                pose.popPose();
            }
        }
        pose.popPose();
    }

    /** Everything in and over the pool draws in this one sorted translucent type over the block atlas, so it all layers
     *  back to front. (Draws of different render types come out in no fixed order, which made layered glows flicker.) */
    private static final net.minecraft.client.renderer.rendertype.RenderType POOL =
            RenderTypes.entityTranslucent(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS);

    private static net.minecraft.client.renderer.texture.TextureAtlasSprite sprite(String name) {
        return net.minecraft.client.Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(net.minecraft.data.AtlasIds.BLOCKS)
                .getSprite(com.powerfactor.PowerFactor.id("block/fx/" + name));
    }

    /** The light columns and sparks are additive (light adds up the way light does), each through one cached render
     *  type: draws of different types come out in an order fixed only by which types are present, so a fresh type
     *  every frame would let the order, and the look, flicker. The ripple scrolls in the vertex UVs instead. */
    private static final net.minecraft.client.renderer.rendertype.RenderType SHEATH = Fx.additive(com.powerfactor.PowerFactor.id("textures/entity/fx/cherenkov.png")),
            SPARKS = Fx.additive(Fx.SPARK);

    /**
     * One fuel column's light. In water: Cherenkov radiation, a sheath of blue light hugging the rods that ripples
     * upward as the water moves, a white-blue heart, and flecks of light flickering through the pool round it. Dry
     * (graphite-moderated) fuel makes no Cherenkov light; it glows with heat instead once it runs hot.
     */
    private static void column(SubmitNodeCollector c, PoseStack pose, CameraRenderState camera, State s, int i, float x, float z, int y0, int y1,
            float glow, float sheen) {
        float wet = i < s.wet.length ? s.wet[i] / 100F : 1;
        float k = sheen * wet + s.flash * 0.9F;
        float flicker = 0.88F + 0.12F * Mth.sin(s.time * 0.37F + i * 2.3F) * Mth.sin(s.time * 0.11F + i);
        float scroll = s.time * 0.012F + i * 0.17F;
        if (k > 0.01F) {
            pillar(c, pose, camera, x, y0, y1, z, 1.4F + 1.2F * glow, scroll, Fx.alpha(CHERENKOV, Math.min(0.9F, 0.7F * k * flicker)));
            pillar(c, pose, camera, x, y0, y1, z, 0.55F + 0.3F * glow, scroll * 1.6F, Fx.alpha(CHERENKOV_CORE, Math.min(0.95F, 0.8F * k * flicker)));
            // Flecks: brief sparks in the water, more of them the harder the core runs.
            int flecks = (int) ((y1 - y0) * 1.6F * Math.min(1.4F, k));
            long bucket = (long) (s.time / 3);
            float age = (s.time / 3) % 1F;
            for (int f = 0; f < flecks; f++) {
                long h = mix(i * 7919L + f * 104729L + bucket * 31L);
                float fx = x + ((h & 0xFF) / 255F - 0.5F) * 1.4F, fz = z + (((h >> 8) & 0xFF) / 255F - 0.5F) * 1.4F;
                float fy = y0 + ((h >> 16) & 0xFFFF) / 65535F * (y1 - y0) + age * 0.25F;
                float size = (0.14F + 0.12F * ((h >> 32) & 0xF) / 15F) / 2;
                int col = Fx.alpha(CHERENKOV_CORE, Math.min(1, k) * (1 - age));
                pose.pushPose();
                pose.translate(fx, fy, fz);
                pose.mulPose(camera.orientation);
                c.submitCustomGeometry(pose, SPARKS, (p, b) -> {
                    vertex(b, p, -size, -size, 0, 0, 1, col);
                    vertex(b, p, size, -size, 0, 1, 1, col);
                    vertex(b, p, size, size, 0, 1, 0, col);
                    vertex(b, p, -size, size, 0, 0, 0, col);
                });
                pose.popPose();
            }
        }
        // Dry fuel above 650 K glows dull red, through orange towards yellow.
        float hot = Mth.clamp((s.temp - 650) / 650, 0, 1) * (1 - wet);
        if (hot > 0.01F) {
            pillar(c, pose, camera, x, y0, y1, z, 0.9F, s.time * 0.004F, Fx.alpha(Fx.mix(0xFFB02810, 0xFFFFC060, hot), 0.55F * hot * flicker));
        }
    }

    /**
     * A vertical band of light turned to face the camera (a cylinder's billboard), stood just in front of the fuel
     * bundle so the light wraps the rods rather than hiding behind them; u across it, v along it in world height.
     */
    private static void pillar(SubmitNodeCollector c, PoseStack pose, CameraRenderState camera, float cx, float y0, float y1, float cz, float width,
            float scroll, int argb) {
        double dx = camera.pos.x - cx, dz = camera.pos.z - cz;
        double len = Math.sqrt(dx * dx + dz * dz);
        float rx = len < 1e-4 ? 1 : (float) (-dz / len), rz = len < 1e-4 ? 0 : (float) (dx / len);
        float x = cx + (len < 1e-4 ? 0 : (float) (dx / len) * 0.5F), z = cz + (len < 1e-4 ? 0 : (float) (dz / len) * 0.5F);
        float h = width / 2;
        float v0 = -y0 / 4F - scroll, v1 = -y1 / 4F - scroll;
        c.submitCustomGeometry(pose, SHEATH, (p, b) -> {
            vertex(b, p, x - rx * h, y0, z - rz * h, 0, v0, argb);
            vertex(b, p, x + rx * h, y0, z + rz * h, 1, v0, argb);
            vertex(b, p, x + rx * h, y1, z + rz * h, 1, v1, argb);
            vertex(b, p, x - rx * h, y1, z - rz * h, 0, v1, argb);
        });
    }

    private static void vertex(com.mojang.blaze3d.vertex.VertexConsumer b, PoseStack.Pose p, float x, float y, float z, float u, float v, int argb) {
        b.addVertex(p, x, y, z).setColor(argb).setUv(u, v).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .setLight(Fx.FULL_BRIGHT).setNormal(p, 0, 1, 0);
    }

    /**
     * The pool seen through the glass. Behind formed Reactor Glass the water's own faces aren't drawn
     * (ConnectedGlassBlock), so each pane facing the camera carries the pool instead: the water texture, lit like the
     * water, and over it while the core runs a Cherenkov wash, brightest beside the fuel, with caustics drifting
     * across it. A pane facing away would only be seen through the pool, so it isn't drawn.
     */
    private static void panes(SubmitNodeCollector c, PoseStack pose, CameraRenderState camera, State s, int n, float glow, float sheen) {
        var water = net.minecraft.client.Minecraft.getInstance().getModelManager().getFluidStateModelSet()
                .get(net.minecraft.world.level.material.Fluids.WATER.defaultFluidState()).stillMaterial().sprite();
        var wash = sprite("reactor_glow");
        var caustics = sprite("reactor_caustics");
        float wetAll = 0;
        for (int k = 0; k < Math.min(n, s.wet.length); k++) wetAll += s.wet[k] / 100F;
        wetAll = n == 0 ? 1 : wetAll / n;
        boolean lit = glow > 0.01F || s.flash > 0;
        for (Pane g : s.glass) {
            var out = g.face().getUnitVec3i();
            double toCam = (camera.pos.x - (g.x() + 0.5 + out.getX() * 0.5)) * out.getX() + (camera.pos.y - (g.y() + 0.5 + out.getY() * 0.5)) * out.getY()
                    + (camera.pos.z - (g.z() + 0.5 + out.getZ() * 0.5)) * out.getZ();
            if (toCam <= 0) continue;
            float flicker = 0.92F + 0.08F * Mth.sin(s.time * 0.3F + g.x() * 1.3F + g.y() * 0.7F + g.z());
            float k = lit ? Math.min(1, sheen * flicker * (0.35F + 0.65F * wetAll) + s.flash * 0.8F) : 0;
            // The more the pool glows, the less of the water's own colour shows (and the less the order the glow and the
            // water happen to be drawn in matters).
            int waterCol = tint(WATER, 0.66F * (1 - 0.8F * k));
            float u = (g.x() * 0.37F + g.z() * 0.61F + g.y() * 0.23F) % 1F;
            boolean flip = ((g.x() + g.y() + g.z()) & 1) == 0;
            c.submitCustomGeometry(pose, POOL, (p, b) -> {
                quad(b, p, paneQuad(g, 0.002F), water, 0, 1, 1, 0, waterCol, g.light());
                if (k < 0.01F) return;
                quad(b, p, paneQuad(g, 0.004F), wash, 0, 1, 1, 0, tint(CHERENKOV, Math.min(0.65F, 0.15F + (0.25F + 0.4F * g.near()) * k)), Fx.FULL_BRIGHT);
                // Caustics, each pane a different window into the sheet (sprites can't wrap) so neighbours don't repeat.
                float a0 = flip ? u * 0.5F : 0.5F + u * 0.5F;
                quad(b, p, paneQuad(g, 0.006F), caustics, a0, a0 + 0.5F, 1, 0, tint(CHERENKOV_CORE, Math.min(0.7F, 0.2F + 0.5F * k)), Fx.FULL_BRIGHT);
            });
        }
    }

    /** A colour at the given opacity, for the alpha-blended pool (unlike Fx.alpha, the colour itself isn't dimmed). */
    private static int tint(int rgb, float a) {
        return Math.round(Mth.clamp(a, 0, 1) * 255) << 24 | rgb & 0xFFFFFF;
    }

    private static float[][] paneQuad(Pane g, float o) {
        return new float[][] {at(g, 0, 0, o), at(g, 1, 0, o), at(g, 1, 1, o), at(g, 0, 1, o)};
    }

    /** A quad of the sprite's (u0..u1, v0..v1) fraction, normal up so per-face shading leaves it at full brightness. */
    private static void quad(com.mojang.blaze3d.vertex.VertexConsumer b, PoseStack.Pose p, float[][] q, net.minecraft.client.renderer.texture.TextureAtlasSprite sp,
            float u0, float u1, float v0, float v1, int argb, int light) {
        float su0 = sp.getU(u0), su1 = sp.getU(u1), sv0 = sp.getV(v0), sv1 = sp.getV(v1);
        float[][] uv = {{su0, sv0}, {su1, sv0}, {su1, sv1}, {su0, sv1}};
        for (int k = 0; k < 4; k++) {
            b.addVertex(p, q[k][0], q[k][1], q[k][2]).setColor(argb).setUv(uv[k][0], uv[k][1])
                    .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0, 1, 0);
        }
    }

    /** A point on the outside of a pane from its in-plane coordinates (along, up; for roof and floor x, z), {@code o} out from the glass. */
    private static float[] at(Pane g, float a, float b, float o) {
        return switch (g.face()) {
            case NORTH -> new float[] {g.x() + a, g.y() + b, g.z() - o};
            case SOUTH -> new float[] {g.x() + a, g.y() + b, g.z() + 1 + o};
            case WEST -> new float[] {g.x() - o, g.y() + b, g.z() + a};
            case EAST -> new float[] {g.x() + 1 + o, g.y() + b, g.z() + a};
            case UP -> new float[] {g.x() + a, g.y() + 1 + o, g.z() + b};
            default -> new float[] {g.x() + a, g.y() - o, g.z() + b};
        };
    }

    private static long mix(long h) {
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        h *= 0xc4ceb9fe1a85ec53L;
        return h ^ h >>> 33;
    }

    @Override
    public AABB getRenderBoundingBox(ReactorControllerBlockEntity be) {
        int[] b = be.clientBounds;
        if (b.length < 6) return new AABB(be.getBlockPos()).inflate(2);
        return new AABB(b[0] - 1, b[1] - 1, b[2] - 1, b[3] + 2, b[4] + 8, b[5] + 2);
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
