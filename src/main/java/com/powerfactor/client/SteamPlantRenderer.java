package com.powerfactor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.powerfactor.PowerFactor;
import com.powerfactor.machine.MachineBlock;
import com.powerfactor.machine.MachineBlockEntity;
import com.powerfactor.multiblock.Buildable;
import com.powerfactor.multiblock.CasingBlock;
import com.powerfactor.steam.SteamBoilerBlockEntity;
import com.powerfactor.steam.SteamTurbineBlockEntity;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
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
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Large pressure vessels, an exposed rotor and alternator fitted to the multiblock footprint. */
public final class SteamPlantRenderer<T extends MachineBlockEntity & Buildable> implements BlockEntityRenderer<T, SteamPlantRenderer.State> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(PowerFactor.id("steam_plant"), "main");
    static final RenderType TYPE = RenderTypes.entityCutout(PowerFactor.id("textures/entity/steam_plant.png"));
    private static final float[][] BOILER_PROFILE = {{0.12F, 0}, {0.05F, 0.5F}, {-0.03F, 0.9F}, {-0.2F, 1.1F}, {-1.8F, 1.1F}, {-1.97F, 0.9F}, {-2.05F, 0.5F}, {-2.12F, 0}};
    private static final float[][] TURBINE_PROFILE = {{0.1F, 0.35F}, {-0.15F, 0.85F}, {-2.1F, 0.85F}, {-2.5F, 0.55F}};
    private static final int[][] CORNERS = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
    public static class State extends BlockEntityRenderState {
        Direction facing = Direction.NORTH;
        boolean boiler, formed;
        float phase, time;
        int heat;
        final java.util.List<MultiblockGhost.Ghost> ghosts = new java.util.ArrayList<>();
    }
    private final boolean boiler;
    private final ModelPart boilerFrame, turbineFrame, rotor, alternator;
    private SteamPlantRenderer(BlockEntityRendererProvider.Context context, boolean boiler) {
        this.boiler = boiler;
        var root = context.bakeLayer(LAYER);
        boilerFrame = root.getChild("boiler_frame"); turbineFrame = root.getChild("turbine_frame");
        rotor = root.getChild("rotor"); alternator = root.getChild("alternator");
    }
    public static SteamPlantRenderer<SteamBoilerBlockEntity> boiler(BlockEntityRendererProvider.Context context) { return new SteamPlantRenderer<>(context, true); }
    public static SteamPlantRenderer<SteamTurbineBlockEntity> turbine(BlockEntityRendererProvider.Context context) { return new SteamPlantRenderer<>(context, false); }
    public static LayerDefinition createLayer() {
        var mesh = new MeshDefinition(); var root = mesh.getRoot();
        root.addOrReplaceChild("boiler_frame", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-24, 0, -40, 48, 2, 48)
                .texOffs(0, 60).addBox(-18, 2, -28, 3, 12, 24).texOffs(0, 60).addBox(15, 2, -28, 3, 12, 24)
                .texOffs(62, 60).addBox(17, 10, -24, 2, 30, 2).texOffs(70, 60).addBox(-3, 39, -35, 6, 18, 6)
                .texOffs(96, 60).addBox(14, 36, -22, 8, 4, 4), PartPose.ZERO);
        root.addOrReplaceChild("turbine_frame", CubeListBuilder.create().texOffs(0, 0).addBox(-24, 0, -72, 48, 2, 80)
                .texOffs(0, 60).addBox(-18, 2, -37, 3, 8, 32).texOffs(0, 60).addBox(15, 2, -37, 3, 8, 32), PartPose.ZERO);
        var generator = CubeListBuilder.create().texOffs(0, 96).addBox(-13, 3, -67, 26, 25, 26);
        for (int z = -65; z <= -43; z += 4) {
            generator.texOffs(62, 60).addBox(-14, 5, z, 1, 21, 1).addBox(13, 5, z, 1, 21, 1);
        }
        root.addOrReplaceChild("alternator", generator, PartPose.ZERO);
        var rotor = root.addOrReplaceChild("rotor", CubeListBuilder.create().texOffs(88, 0).addBox(-3, -3, -1.5F, 6, 6, 3), PartPose.ZERO);
        for (int i = 0; i < 8; i++) rotor.addOrReplaceChild("vane" + i, CubeListBuilder.create().texOffs(88, 14).addBox(-0.7F, 2, -1, 1.4F, 9, 2),
                PartPose.rotation(0, 0.3F, (float) (i * Math.PI / 4)));
        var fan = root.addOrReplaceChild("fan", CubeListBuilder.create().texOffs(88, 0).addBox(-1.5F, -1.5F, -1, 3, 3, 2), PartPose.ZERO);
        for (int i = 0; i < 4; i++) fan.addOrReplaceChild("blade" + i, CubeListBuilder.create().texOffs(88, 14).addBox(-1, 1, -0.5F, 2, 4, 1),
                PartPose.rotation(0, 0.3F, (float) (i * Math.PI / 2)));
        return LayerDefinition.create(mesh, 256, 256);
    }
    @Override public State createRenderState() { return new State(); }
    @Override public void extractRenderState(T entity, State state, float partial, Vec3 camera, ModelFeatureRenderer.@Nullable CrumblingOverlay breaking) {
        BlockEntityRenderer.super.extractRenderState(entity, state, partial, camera, breaking);
        state.boiler = boiler; state.facing = entity.getBlockState().getValue(MachineBlock.FACING);
        state.formed = entity.getBlockState().getValue(CasingBlock.FORMED);
        state.phase = entity instanceof SteamTurbineBlockEntity turbine ? turbine.rotorPhase(partial) : 0;
        state.heat = entity instanceof SteamBoilerBlockEntity vessel ? vessel.model().heat() : 0;
        state.ghosts.clear(); var level = entity.getLevel(); if (level == null) return;
        state.time = level.getGameTime() + partial;
        state.lightCoords = LightCoordsUtil.getLightCoords(LightCoordsUtil.BrightnessGetter.DEFAULT, level, entity.getBlockState(), entity.getBlockPos().above(2));
        if (!state.formed) {
            var structure = entity.buildStructure();
            for (var part : structure.parts()) {
                var at = structure.worldPos(entity.getBlockPos(), state.facing, part); var block = level.getBlockState(at);
                if (!part.matches(block)) state.ghosts.add(new MultiblockGhost.Ghost(at.subtract(entity.getBlockPos()), !block.canBeReplaced(),
                        new AABB(0.02, 0.02, 0.02, 0.98, 0.98, 0.98)));
            }
        }
    }
    @Override public void submit(State s, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        MultiblockGhost.submit(pose, collector, s.ghosts, s.time); if (!s.formed) return;
        pose.pushPose(); pose.translate(0.5, 0, 0.5); pose.mulPose(Axis.YP.rotationDegrees(-s.facing.toYRot()));
        collector.submitModelPart(s.boiler ? boilerFrame : turbineFrame, pose, TYPE, s.lightCoords, OverlayTexture.NO_OVERLAY, null, -1, s.breakProgress);
        if (!s.boiler) collector.submitModelPart(alternator, pose, TYPE, s.lightCoords, OverlayTexture.NO_OVERLAY, null, 0xFF78A7B8, s.breakProgress);
        pose.translate(0, s.boiler ? 1.6F : 1.15F, 0);
        int color = s.boiler ? s.heat >= 750 ? 0xFFCEB5A0 : 0xFFC3CBD0 : 0xFF93ABB4;
        collector.submitCustomGeometry(pose, TYPE, (p, vertices) -> {
            vessel(vertices, p, s.boiler ? BOILER_PROFILE : TURBINE_PROFILE, color, s.lightCoords);
            float radius = s.boiler ? 1.115F : 0.865F;
            for (float z : new float[] {-0.3F, -1.7F}) {
                vessel(vertices, p, new float[][] {{z, radius}, {z - 0.075F, radius}}, 0xFF657D88, s.lightCoords);
            }
        });
        if (!s.boiler) {
            pose.translate(0, 0, 0.25); pose.mulPose(Axis.ZP.rotation(s.phase));
            collector.submitModelPart(rotor, pose, TYPE, s.lightCoords, OverlayTexture.NO_OVERLAY, null, 0xFFE8C88E, s.breakProgress);
        }
        pose.popPose();
    }
    private static void vessel(VertexConsumer out, PoseStack.Pose pose, float[][] profile, int color, int light) {
        for (int ring = 0; ring + 1 < profile.length; ring++) {
            float z0 = profile[ring][0], r0 = profile[ring][1], z1 = profile[ring + 1][0], r1 = profile[ring + 1][1];
            float len = (float) Math.hypot(z1 - z0, r1 - r0);
            for (int segment = 0; segment < 32; segment++) for (int[] corner : CORNERS) {
                float radius = corner[0] == 0 ? r0 : r1, z = corner[0] == 0 ? z0 : z1;
                double theta = (segment + corner[1]) * Math.PI / 16;
                float cos = (float) Math.cos(theta), sin = (float) Math.sin(theta);
                out.addVertex(pose, cos * radius, sin * radius, z).setColor(color).setUv((segment + corner[1]) / 32F, (ring + corner[0]) / (float) (profile.length - 1))
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, cos * (z0 - z1) / len, sin * (z0 - z1) / len, (r1 - r0) / len);
            }
        }
    }
    @Override public AABB getRenderBoundingBox(T entity) { return new AABB(entity.getBlockPos()).inflate(5, 0, 5).expandTowards(0, 4, 0); }
    @Override public boolean shouldRenderOffScreen() { return true; }
    @Override public int getViewDistance() { return 192; }
}
