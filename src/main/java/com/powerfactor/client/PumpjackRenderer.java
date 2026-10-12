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
    private static final float PIVOT_Y = 1.55F, SWING = 14F;

    public static class State extends BlockEntityRenderState {
        public Direction facing = Direction.NORTH;
        public float phase;
    }

    private final ModelPart beam, head, post, weight, rod, bearing;

    public PumpjackRenderer(BlockEntityRendererProvider.Context context) {
        ModelPart root = context.bakeLayer(LAYER);
        beam = root.getChild("beam");
        head = root.getChild("head");
        post = root.getChild("post");
        weight = root.getChild("weight");
        rod = root.getChild("rod");
        bearing = root.getChild("bearing");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        var r = mesh.getRoot();
        r.addOrReplaceChild("beam", CubeListBuilder.create().texOffs(0, 0).addBox(-12, -1, -1, 24, 2, 2), PartPose.ZERO);
        r.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 6).addBox(-2, -4, -3, 4, 8, 6), PartPose.ZERO);
        r.addOrReplaceChild("post", CubeListBuilder.create().texOffs(24, 6).addBox(-1, 0, -1, 2, 12, 2), PartPose.ZERO);
        r.addOrReplaceChild("weight", CubeListBuilder.create().texOffs(0, 22).addBox(-3, -3, -1, 6, 6, 2), PartPose.ZERO);
        r.addOrReplaceChild("rod", CubeListBuilder.create().texOffs(20, 22).addBox(-0.5F, 0, -0.5F, 1, 10, 1), PartPose.ZERO);
        r.addOrReplaceChild("bearing", CubeListBuilder.create().texOffs(32, 22).addBox(-1, -1, -1, 2, 2, 2), PartPose.ZERO);
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
        boolean running = be.getBlockState().getValueOrElse(MachineBlock.RUNNING, false);
        long t = be.getLevel() == null ? 0 : be.getLevel().getGameTime();
        // A stopped pumpjack rests mid-stroke rather than snapping.
        state.phase = running ? (t + partial) * 0.09F : 0.6F;
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector c, CameraRenderState camera) {
        int light = s.lightCoords;
        float tilt = (float) Math.sin(s.phase) * SWING;
        pose.pushPose();
        pose.translate(0.5F, 0, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(-s.facing.toYRot()));
        // Samson post: two legs leaning in to the pivot.
        for (float side : new float[] {-0.28F, 0.28F}) {
            for (float lean : new float[] {-12F, 12F}) {
                pose.pushPose();
                pose.translate(side, 0.15F, lean > 0 ? -0.3F : 0.3F);
                pose.mulPose(Axis.XP.rotationDegrees(lean * -1));
                pose.scale(1, 1.18F, 1);
                c.submitModelPart(post, pose, TYPE, light, OverlayTexture.NO_OVERLAY, null, -1, null);
                pose.popPose();
            }
        }
        // Walking beam, rocking about the bearing; head at -Z (front), counterweight crank at +Z.
        pose.pushPose();
        pose.translate(0, PIVOT_Y, 0);
        c.submitModelPart(bearing, pose, TYPE, light, OverlayTexture.NO_OVERLAY, null, -1, null);
        pose.mulPose(Axis.XP.rotationDegrees(tilt));
        pose.mulPose(Axis.YP.rotationDegrees(90));
        c.submitModelPart(beam, pose, TYPE, light, OverlayTexture.NO_OVERLAY, null, -1, null);
        pose.translate(-0.8F, -0.05F, 0);
        c.submitModelPart(head, pose, TYPE, light, OverlayTexture.NO_OVERLAY, null, -1, null);
        pose.popPose();
        // Polished rod hanging from the horse head into the wellhead.
        float headY = PIVOT_Y - 0.25F + (float) Math.sin(Math.toRadians(-tilt)) * 0.8F;
        pose.pushPose();
        pose.translate(0, 0.25F, -0.8F);
        pose.scale(1, Math.max(0.05F, (headY - 0.25F) / (10F / 16F)), 1);
        c.submitModelPart(rod, pose, TYPE, light, OverlayTexture.NO_OVERLAY, null, -1, null);
        pose.popPose();
        // Crank counterweights turning at the back.
        for (float side : new float[] {-0.32F, 0.32F}) {
            pose.pushPose();
            pose.translate(side, 0.55F, 0.7F);
            pose.mulPose(Axis.XP.rotationDegrees((float) Math.toDegrees(s.phase)));
            pose.mulPose(Axis.YP.rotationDegrees(90));
            pose.translate(0, -0.18F, 0);
            c.submitModelPart(weight, pose, TYPE, light, OverlayTexture.NO_OVERLAY, null, -1, null);
            pose.popPose();
        }
        pose.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(PumpjackBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(1.5, 0, 1.5).expandTowards(0, 2, 0);
    }
}
