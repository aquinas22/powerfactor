package com.powerfactor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.powerfactor.PowerFactor;
import com.powerfactor.machine.MachineBlock;
import com.powerfactor.machine.RecipeMachine;
import com.powerfactor.machine.RecipeMachineBlockEntity;
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

/** Moving tooling in the open frames of the press, mixer, centrifuge and sawmill. */
public final class WorkshopRenderer implements BlockEntityRenderer<RecipeMachineBlockEntity, WorkshopRenderer.State> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(PowerFactor.id("workshop"), "main");
    private static final RenderType TYPE = RenderTypes.entityCutout(PowerFactor.id("textures/entity/workshop.png"));
    private final ModelPart press, paddle, rotor, saw;

    public static class State extends BlockEntityRenderState {
        Direction facing = Direction.NORTH;
        RecipeMachine machine = RecipeMachine.MECHANICAL_PRESS;
        float phase;
    }

    public WorkshopRenderer(BlockEntityRendererProvider.Context context) {
        var root = context.bakeLayer(LAYER);
        press = root.getChild("press");
        paddle = root.getChild("paddle");
        rotor = root.getChild("rotor");
        saw = root.getChild("saw");
    }

    public static LayerDefinition createLayer() {
        var mesh = new MeshDefinition();
        var root = mesh.getRoot();
        root.addOrReplaceChild("press", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4, -1, -4, 8, 2, 8)
                .texOffs(0, 12).addBox(-1.5F, 1, -1.5F, 3, 6, 3), PartPose.ZERO);
        root.addOrReplaceChild("paddle", CubeListBuilder.create()
                .texOffs(16, 12).addBox(-0.5F, 0, -0.5F, 1, 9, 1)
                .texOffs(0, 24).addBox(-5, 1, -0.5F, 10, 2, 1)
                .texOffs(0, 24).addBox(-5, 4, -0.5F, 10, 2, 1), PartPose.ZERO);
        var centrifuge = root.addOrReplaceChild("rotor", CubeListBuilder.create()
                .texOffs(24, 12).addBox(-2, -1, -2, 4, 2, 4), PartPose.ZERO);
        for (int i = 0; i < 4; i++) {
            centrifuge.addOrReplaceChild("bucket" + i, CubeListBuilder.create()
                    .texOffs(0, 24).addBox(0, -0.5F, -0.5F, 5, 1, 1)
                    .texOffs(24, 24).addBox(4, -2, -1.5F, 2, 4, 3),
                    PartPose.rotation(0, (float) (i * Math.PI / 2), 0));
        }
        var blade = root.addOrReplaceChild("saw", CubeListBuilder.create()
                .texOffs(0, 32).addBox(-0.5F, -3.5F, -3.5F, 1, 7, 7), PartPose.ZERO);
        for (int i = 0; i < 12; i++) {
            blade.addOrReplaceChild("tooth" + i, CubeListBuilder.create().texOffs(20, 32).addBox(-0.65F, 3, -0.5F, 1.3F, 1.5F, 1),
                    PartPose.rotation((float) (i * Math.PI / 6), 0, 0));
        }
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(RecipeMachineBlockEntity be, State state, float partial, Vec3 camera,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breaking) {
        BlockEntityRenderer.super.extractRenderState(be, state, partial, camera, breaking);
        state.facing = be.getBlockState().getValueOrElse(MachineBlock.FACING, Direction.NORTH);
        state.machine = be.machine();
        state.phase = be.workshopPhase(partial);
        if (be.getLevel() != null) {
            state.lightCoords = LightCoordsUtil.getLightCoords(LightCoordsUtil.BrightnessGetter.DEFAULT, be.getLevel(), be.getBlockState(),
                    be.getBlockPos().above());
        }
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-s.facing.toYRot()));
        ModelPart moving;
        switch (s.machine) {
            case MECHANICAL_PRESS -> {
                pose.translate(0, 0.56 + Math.cos(s.phase) * 0.15, 0);
                moving = press;
            }
            case MIXER -> {
                pose.translate(0, 0.34, 0);
                pose.mulPose(Axis.YP.rotation(s.phase));
                moving = paddle;
            }
            case CENTRIFUGE -> {
                pose.translate(0, 0.65, 0);
                pose.mulPose(Axis.YP.rotation(s.phase));
                moving = rotor;
            }
            case SAWMILL -> {
                pose.translate(0, 0.65, 0);
                pose.mulPose(Axis.XP.rotation(s.phase));
                moving = saw;
            }
            default -> { pose.popPose(); return; }
        }
        collector.submitModelPart(moving, pose, TYPE, s.lightCoords, OverlayTexture.NO_OVERLAY, null, -1, s.breakProgress);
        pose.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(RecipeMachineBlockEntity be) {
        return new AABB(be.getBlockPos()).expandTowards(0, 0.25, 0);
    }
}
