package com.powerfactor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.powerfactor.PowerFactor;
import com.powerfactor.machine.CoalTurbineBlockEntity;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** The turbine wheel spinning in the recess at the back of the coal turbine, at its real RPM. */
public class TurbineRotorRenderer implements BlockEntityRenderer<CoalTurbineBlockEntity, TurbineRotorRenderer.State> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(PowerFactor.id("turbine_rotor"), "main");
    private static final RenderType RENDER_TYPE = RenderTypes.entityCutout(PowerFactor.id("textures/entity/turbine_rotor.png"));
    private static final int BLADES = 8;

    public static class State extends BlockEntityRenderState {
        public Direction back = Direction.SOUTH;
        public float angle;
    }

    private final ModelPart rotor;

    public TurbineRotorRenderer(BlockEntityRendererProvider.Context context) {
        rotor = context.bakeLayer(LAYER).getChild("rotor");
    }

    /** Hub and pitched blades about local Z; fits the 12x12 recess. */
    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition rotor = root.addOrReplaceChild("rotor",
                CubeListBuilder.create().texOffs(0, 0).addBox(-2F, -2F, -1.5F, 4F, 4F, 3F), PartPose.ZERO);
        for (int i = 0; i < BLADES; i++) {
            rotor.addOrReplaceChild("blade" + i,
                    CubeListBuilder.create().texOffs(0, 8).addBox(-1F, 1.5F, -0.5F, 2F, 4F, 1F),
                    PartPose.rotation(0F, 0.45F, i * (float) (2 * Math.PI / BLADES)));
        }
        return LayerDefinition.create(mesh, 32, 32);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(CoalTurbineBlockEntity turbine, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(turbine, state, partialTicks, cameraPosition, breakProgress);
        state.back = turbine.getBlockState().getValueOrElse(MachineBlock.FACING, Direction.NORTH).getOpposite();
        state.angle = turbine.rotorAngle(partialTicks);
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        pose.pushPose();
        pose.translate(0.5F, 0.5F, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(-state.back.toYRot()));
        // Sit the wheel in the middle of the 3px-deep recess.
        pose.translate(0F, 0F, 6.5F / 16F);
        pose.mulPose(Axis.ZP.rotationDegrees(state.angle));
        collector.submitModelPart(rotor, pose, RENDER_TYPE, state.lightCoords, OverlayTexture.NO_OVERLAY, null, -1, state.breakProgress);
        pose.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(CoalTurbineBlockEntity turbine) {
        return new AABB(turbine.getBlockPos());
    }
}
