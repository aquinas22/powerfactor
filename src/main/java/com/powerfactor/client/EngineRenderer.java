package com.powerfactor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.powerfactor.PowerFactor;
import com.powerfactor.engine.EngineBlockEntity;
import com.powerfactor.engine.EngineKind;
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
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Draws an engine's moving parts: the piston plate pumping up and down, the leather bellows
 * stretching behind it, and the heat-colored trunk. The fixed base plate is the block model.
 */
public class EngineRenderer implements BlockEntityRenderer<EngineBlockEntity, EngineRenderer.State> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(PowerFactor.id("engine"), "main");
    private static final RenderType[] PLATE = {
            RenderTypes.entityCutout(PowerFactor.id("textures/entity/engine_redstone.png")),
            RenderTypes.entityCutout(PowerFactor.id("textures/entity/engine_stirling.png"))};
    private static final RenderType TRUNK = RenderTypes.entityCutout(PowerFactor.id("textures/entity/engine_trunk.png"));
    /** BuildCraft's heat colors: blue, green, yellow, red. */
    private static final int[] HEAT_TINT = {0xFF4A7BFF, 0xFF3CC94A, 0xFFF2D030, 0xFFE0302A};
    /** How far the plate travels, in blocks. */
    private static final float STROKE = 0.5F;

    public static class State extends BlockEntityRenderState {
        public Direction facing = Direction.UP;
        public float plate;
        public int stage;
        public int kind;
    }

    private final ModelPart plate;
    private final ModelPart bellows;
    private final ModelPart trunk;

    public EngineRenderer(BlockEntityRendererProvider.Context context) {
        ModelPart root = context.bakeLayer(LAYER);
        plate = root.getChild("plate");
        bellows = root.getChild("bellows");
        trunk = root.getChild("trunk");
    }

    /** Pixels about the block center, engine axis +Y. The base (block model) is y -8..-4. */
    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("plate", CubeListBuilder.create().texOffs(0, 0).addBox(-8F, -4F, -8F, 16F, 4F, 16F), PartPose.ZERO);
        // One pixel tall; stretched to fill the gap under the plate.
        root.addOrReplaceChild("bellows", CubeListBuilder.create().texOffs(0, 20).addBox(-7F, 0F, -7F, 14F, 1F, 14F), PartPose.ZERO);
        root.addOrReplaceChild("trunk", CubeListBuilder.create().texOffs(0, 40).addBox(-4F, -4F, -4F, 8F, 12F, 8F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(EngineBlockEntity engine, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(engine, state, partialTicks, cameraPosition, breakProgress);
        state.facing = engine.facing();
        state.plate = engine.plateOffset(partialTicks);
        state.stage = Mth.clamp(engine.heatStage(), 0, 3);
        state.kind = engine.kind() == EngineKind.REDSTONE ? 0 : 1;
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        pose.pushPose();
        pose.translate(0.5F, 0.5F, 0.5F);
        pose.mulPose(state.facing.getRotation());

        collector.submitModelPart(trunk, pose, TRUNK, state.lightCoords, OverlayTexture.NO_OVERLAY, null, HEAT_TINT[state.stage], state.breakProgress);

        float lift = state.plate * STROKE;
        if (lift > 0.002F) {
            pose.pushPose();
            pose.translate(0F, -4F / 16F, 0F);
            pose.scale(1F, lift * 16F, 1F);
            collector.submitModelPart(bellows, pose, PLATE[state.kind], state.lightCoords, OverlayTexture.NO_OVERLAY, null, -1, state.breakProgress);
            pose.popPose();
        }

        pose.pushPose();
        pose.translate(0F, lift, 0F);
        collector.submitModelPart(plate, pose, PLATE[state.kind], state.lightCoords, OverlayTexture.NO_OVERLAY, null, -1, state.breakProgress);
        pose.popPose();

        pose.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(EngineBlockEntity engine) {
        return new AABB(engine.getBlockPos()).inflate(1.0);
    }
}
