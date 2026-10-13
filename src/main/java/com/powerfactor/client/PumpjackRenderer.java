package com.powerfactor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.powerfactor.PowerFactor;
import com.powerfactor.machine.MachineBlock;
import com.powerfactor.oil.PumpjackBlockEntity;
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

/**
 * The nodding donkey: an A-frame samson post carries the red walking beam, which rocks about its
 * pivot; the horse head at the front lifts the polished rod out of the well while the crank's
 * counterweights swing round at the back.
 */
public class PumpjackRenderer implements BlockEntityRenderer<PumpjackBlockEntity, PumpjackRenderer.State> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(PowerFactor.id("pumpjack"), "main");
    private static final RenderType TYPE = RenderTypes.entityCutout(PowerFactor.id("textures/entity/pumpjack.png"));
    /** Pivot height (blocks above the base) and beam swing (degrees). */
    private static final float PIVOT_Y = 1.55F, BEAM_ARM = 0.72F, CRANK_RADIUS = 0.22F, PITMAN_LENGTH = 0.97F;

    public static class State extends BlockEntityRenderState {
        public Direction facing = Direction.NORTH;
        public float phase;
        public float time;
        public final java.util.List<MultiblockGhost.Ghost> ghosts = new java.util.ArrayList<>();
    }

    private final ModelPart beam, head, post, weight, rod, bearing, link, axle;

    public PumpjackRenderer(BlockEntityRendererProvider.Context context) {
        ModelPart root = context.bakeLayer(LAYER);
        beam = root.getChild("beam");
        head = root.getChild("head");
        post = root.getChild("post");
        weight = root.getChild("weight");
        rod = root.getChild("rod");
        bearing = root.getChild("bearing");
        link = root.getChild("link");
        axle = root.getChild("axle");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        var r = mesh.getRoot();
        r.addOrReplaceChild("beam", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-12, -1, -1, 24, 2, 2)
                .texOffs(0, 36).addBox(-12, -2, -2, 24, 1, 4)
                .texOffs(0, 36).addBox(-12, 1, -2, 24, 1, 4), PartPose.ZERO);
        r.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 6).addBox(-2, -4, -3, 4, 8, 6), PartPose.ZERO);
        r.addOrReplaceChild("post", CubeListBuilder.create().texOffs(24, 6).addBox(-1, 0, -1, 2, 12, 2), PartPose.ZERO);
        r.addOrReplaceChild("weight", CubeListBuilder.create().texOffs(0, 22).addBox(-3, -3, -1, 6, 6, 2), PartPose.ZERO);
        r.addOrReplaceChild("rod", CubeListBuilder.create().texOffs(20, 22).addBox(-0.5F, 0, -0.5F, 1, 10, 1), PartPose.ZERO);
        r.addOrReplaceChild("bearing", CubeListBuilder.create().texOffs(32, 22).addBox(-1, -1, -1, 2, 2, 2), PartPose.ZERO);
        r.addOrReplaceChild("link", CubeListBuilder.create().texOffs(40, 22).addBox(-0.5F, 0, -0.5F, 1, 16, 1), PartPose.ZERO);
        r.addOrReplaceChild("axle", CubeListBuilder.create().texOffs(0, 44).addBox(-6, -1, -1, 12, 2, 2), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
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
        float crankY = 0.58F + (float) Math.cos(s.phase) * CRANK_RADIUS;
        float crankZ = -0.72F - (float) Math.sin(s.phase) * CRANK_RADIUS;
        float dy = crankY - PIVOT_Y;
        float distance = (float) Math.hypot(dy, crankZ);
        // Four-bar linkage: the walking beam and pitman arm keep their physical lengths.
        double alpha = Math.atan2(dy, -crankZ) + Math.acos(Math.clamp(
                (BEAM_ARM * BEAM_ARM + distance * distance - PITMAN_LENGTH * PITMAN_LENGTH) / (2 * BEAM_ARM * distance), -1F, 1F));
        float tilt = (float) Math.toDegrees(alpha);
        float rearY = PIVOT_Y + BEAM_ARM * (float) Math.sin(alpha), rearZ = -BEAM_ARM * (float) Math.cos(alpha);
        pose.pushPose();
        pose.translate(0.5F, 0, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(-s.facing.toYRot()));
        // Samson post: two legs leaning in to the pivot.
        for (float side : new float[] {-0.28F, 0.28F}) {
            for (float foot : new float[] {-0.38F, 0.38F}) {
                segment(post, pose, c, light, side, 0.15F, foot, PIVOT_Y, 0, 12F / 16F);
            }
        }
        pose.pushPose();
        pose.translate(0, PIVOT_Y, 0);
        c.submitModelPart(axle, pose, TYPE, light, OverlayTexture.NO_OVERLAY, null, -1, s.breakProgress);
        pose.popPose();
        // Walking beam: +Z faces out after the facing transform; the crank sits behind it at -Z.
        pose.pushPose();
        pose.translate(0, PIVOT_Y, 0);
        c.submitModelPart(bearing, pose, TYPE, light, OverlayTexture.NO_OVERLAY, null, -1, null);
        pose.mulPose(Axis.XP.rotationDegrees(tilt));
        pose.mulPose(Axis.YP.rotationDegrees(90));
        c.submitModelPart(beam, pose, TYPE, light, OverlayTexture.NO_OVERLAY, null, -1, null);
        pose.translate(-0.72F, -0.05F, 0);
        c.submitModelPart(head, pose, TYPE, light, OverlayTexture.NO_OVERLAY, null, -1, null);
        pose.popPose();
        // Polished rod hanging from the horse head into the wellhead.
        float headY = PIVOT_Y - 0.25F - BEAM_ARM * (float) Math.sin(alpha);
        float headZ = BEAM_ARM * (float) Math.cos(alpha);
        pose.pushPose();
        pose.translate(0, 0.25F, headZ);
        pose.scale(1, Math.max(0.05F, (headY - 0.25F) / (10F / 16F)), 1);
        c.submitModelPart(rod, pose, TYPE, light, OverlayTexture.NO_OVERLAY, null, -1, null);
        pose.popPose();
        // Crosshead bearing spans both pitman arms at the rear end of the walking beam.
        pose.pushPose();
        pose.translate(0, rearY, rearZ);
        c.submitModelPart(axle, pose, TYPE, light, OverlayTexture.NO_OVERLAY, null, -1, s.breakProgress);
        pose.popPose();
        // Crank counterweights turning at the back.
        for (float side : new float[] {-0.32F, 0.32F}) {
            segment(link, pose, c, light, side, crankY, crankZ, rearY, rearZ, 1);
            pose.pushPose();
            pose.translate(side, 0.58F, -0.72F);
            pose.mulPose(Axis.XP.rotationDegrees((float) -Math.toDegrees(s.phase)));
            pose.mulPose(Axis.YP.rotationDegrees(90));
            pose.translate(0, -CRANK_RADIUS, 0);
            c.submitModelPart(weight, pose, TYPE, light, OverlayTexture.NO_OVERLAY, null, -1, null);
            pose.popPose();
        }
        pose.popPose();
    }

    private static void segment(ModelPart part, PoseStack pose, SubmitNodeCollector c, int light,
            float x, float y, float z, float endY, float endZ, float modelLength) {
        float dy = endY - y, dz = endZ - z;
        pose.pushPose();
        pose.translate(x, y, z);
        pose.mulPose(Axis.XP.rotationDegrees((float) Math.toDegrees(Math.atan2(dz, dy))));
        pose.scale(1, (float) Math.hypot(dy, dz) / modelLength, 1);
        c.submitModelPart(part, pose, TYPE, light, OverlayTexture.NO_OVERLAY, null, -1, null);
        pose.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(PumpjackBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(1.5, 0, 1.5).expandTowards(0, 2, 0).expandTowards(0, -1, 0);
    }
}
