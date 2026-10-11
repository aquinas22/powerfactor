package com.powerfactor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.powerfactor.PowerFactor;
import com.powerfactor.generator.HydroTurbineBlockEntity;
import com.powerfactor.generator.SynchronousCondenserBlockEntity;
import com.powerfactor.generator.WindTurbineBlockEntity;
import com.powerfactor.machine.MachineBlock;
import java.util.function.BiFunction;
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
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Rotors on the front face of a machine: the wind turbine's three big blades, the hydro turbine's
 * runner and the synchronous condenser's flywheel. One renderer, configured per machine.
 */
public class SpinningRenderers<T extends BlockEntity> implements BlockEntityRenderer<T, SpinningRenderers.State> {
    public static final ModelLayerLocation WIND = new ModelLayerLocation(PowerFactor.id("wind_rotor"), "main");
    public static final ModelLayerLocation HYDRO = new ModelLayerLocation(PowerFactor.id("hydro_runner"), "main");
    public static final ModelLayerLocation CONDENSER = new ModelLayerLocation(PowerFactor.id("condenser_rotor"), "main");

    public static class State extends BlockEntityRenderState {
        public Direction facing = Direction.NORTH;
        public float angle;
    }

    private final ModelPart rotor;
    private final Identifier texture;
    private final BiFunction<T, Float, Float> angle;
    private final float offset, scale;
    private final float inflate;

    private SpinningRenderers(ModelPart rotor, Identifier texture, BiFunction<T, Float, Float> angle, float offset, float scale, float inflate) {
        this.rotor = rotor;
        this.texture = texture;
        this.angle = angle;
        this.offset = offset;
        this.scale = scale;
        this.inflate = inflate;
    }

    public static SpinningRenderers<WindTurbineBlockEntity> wind(BlockEntityRendererProvider.Context c) {
        return new SpinningRenderers<>(c.bakeLayer(WIND).getChild("rotor"), PowerFactor.id("textures/entity/wind_turbine_blades.png"),
                WindTurbineBlockEntity::angle, 0.62F, 1.25F, 3);
    }

    public static SpinningRenderers<HydroTurbineBlockEntity> hydro(BlockEntityRendererProvider.Context c) {
        return new SpinningRenderers<>(c.bakeLayer(HYDRO).getChild("rotor"), PowerFactor.id("textures/entity/hydro_runner.png"),
                HydroTurbineBlockEntity::angle, 0.4F, 1F, 0.5F);
    }

    public static SpinningRenderers<SynchronousCondenserBlockEntity> condenser(BlockEntityRendererProvider.Context c) {
        return new SpinningRenderers<>(c.bakeLayer(CONDENSER).getChild("rotor"), PowerFactor.id("textures/entity/condenser_rotor.png"),
                SynchronousCondenserBlockEntity::angle, 0.53F, 1F, 0.5F);
    }

    /** Hub plus three blades pointing out along +Y, tip at max Y. */
    public static LayerDefinition windLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition rotor = mesh.getRoot().addOrReplaceChild("rotor",
                CubeListBuilder.create().texOffs(0, 0).addBox(-3, -3, -2, 6, 6, 4), PartPose.ZERO);
        for (int i = 0; i < 3; i++) {
            rotor.addOrReplaceChild("blade" + i, CubeListBuilder.create().texOffs(0, 16).addBox(-2, 2, -0.5F, 4, 30, 1),
                    PartPose.rotation(0, 0.25F, i * (float) (Math.PI * 2 / 3)));
        }
        return LayerDefinition.create(mesh, 64, 64);
    }

    public static LayerDefinition hydroLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition rotor = mesh.getRoot().addOrReplaceChild("rotor",
                CubeListBuilder.create().texOffs(0, 0).addBox(-2, -2, -2, 4, 4, 4), PartPose.ZERO);
        for (int i = 0; i < 6; i++) {
            rotor.addOrReplaceChild("vane" + i, CubeListBuilder.create().texOffs(0, 8).addBox(-0.5F, 1.5F, -1.5F, 1, 5, 3),
                    PartPose.rotation(0, 0.5F, i * (float) (Math.PI / 3)));
        }
        return LayerDefinition.create(mesh, 32, 32);
    }

    public static LayerDefinition condenserLayer() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("rotor", CubeListBuilder.create().texOffs(0, 0).addBox(-5, -5, -1, 10, 10, 2), PartPose.ZERO);
        return LayerDefinition.create(mesh, 32, 32);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(T be, State state, float partialTicks, Vec3 camera, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, camera, breakProgress);
        state.facing = be.getBlockState().getValueOrElse(MachineBlock.FACING, Direction.NORTH);
        state.angle = angle.apply(be, partialTicks);
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        pose.pushPose();
        pose.translate(0.5F, 0.5F, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(-state.facing.toYRot()));
        pose.translate(0, 0, offset);
        pose.mulPose(Axis.ZP.rotationDegrees(state.angle));
        // Entity-model convention: flip Y and Z so the nose faces out and the faces light correctly.
        pose.scale(scale, -scale, -scale);
        collector.submitModelPart(rotor, pose, RenderTypes.entityCutout(texture), state.lightCoords, OverlayTexture.NO_OVERLAY, null, -1,
                state.breakProgress);
        pose.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(T be) {
        return new AABB(be.getBlockPos()).inflate(inflate);
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return inflate > 1;
    }
}
