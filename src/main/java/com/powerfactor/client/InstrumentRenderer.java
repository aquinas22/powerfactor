package com.powerfactor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.powerfactor.PowerFactor;
import com.powerfactor.generator.BatteryBankBlockEntity;
import com.powerfactor.generator.PowerMeterBlockEntity;
import com.powerfactor.machine.MachineBlock;
import com.powerfactor.machine.MachineBlockEntity;
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
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Live instrument needles and battery segments in the existing physical control panels. */
public final class InstrumentRenderer<T extends MachineBlockEntity> implements BlockEntityRenderer<T, InstrumentRenderer.State> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(PowerFactor.id("instruments"), "main");
    private static final Identifier TEXTURE = PowerFactor.id("textures/entity/instruments.png");
    private final ModelPart needle, segment, lamp;
    public static class State extends BlockEntityRenderState {
        Direction facing = Direction.NORTH;
        boolean meter, powered;
        float voltage, load, charge, pulse;
        int flow;
    }

    public InstrumentRenderer(BlockEntityRendererProvider.Context context) {
        var root = context.bakeLayer(LAYER);
        needle = root.getChild("needle"); segment = root.getChild("segment"); lamp = root.getChild("lamp");
    }

    public static LayerDefinition createLayer() {
        var mesh = new MeshDefinition(); var root = mesh.getRoot();
        root.addOrReplaceChild("needle", CubeListBuilder.create().texOffs(0, 0).addBox(-0.12F, -1.65F, 0, 0.24F, 1.9F, 0.12F)
                .texOffs(0, 8).addBox(-0.24F, -0.24F, -0.05F, 0.48F, 0.48F, 0.2F), PartPose.ZERO);
        root.addOrReplaceChild("segment", CubeListBuilder.create().texOffs(16, 0).addBox(-0.3F, -1.25F, 0, 0.6F, 1.25F, 0.12F), PartPose.ZERO);
        root.addOrReplaceChild("lamp", CubeListBuilder.create().texOffs(16, 8).addBox(-0.24F, -0.24F, 0, 0.48F, 0.48F, 0.12F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 32, 32);
    }

    @Override public State createRenderState() { return new State(); }

    @Override public void extractRenderState(T be, State s, float partial, Vec3 camera,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breaking) {
        BlockEntityRenderer.super.extractRenderState(be, s, partial, camera, breaking);
        s.facing = be.getBlockState().getValueOrElse(MachineBlock.FACING, Direction.NORTH);
        s.meter = be instanceof PowerMeterBlockEntity;
        s.pulse = be.getLevel() == null ? 1 : 0.82F + 0.18F * (float) Math.sin((be.getLevel().getGameTime() + partial) * 0.18);
        if (be instanceof PowerMeterBlockEntity meter) {
            s.voltage = meter.shownVoltage(partial); s.load = meter.shownLoad(partial); s.powered = meter.visualPowered();
        } else if (be instanceof BatteryBankBlockEntity battery) {
            s.charge = battery.shownCharge(partial); s.flow = battery.visualFlow();
        }
    }

    private void part(ModelPart model, State s, PoseStack pose, SubmitNodeCollector collector,
            float x, float y, float z, float angle, float height, int color, boolean glow) {
        pose.pushPose(); pose.translate(x / 16, y / 16, z / 16);
        pose.mulPose(Axis.ZP.rotationDegrees(angle)); pose.scale(1, -height, -1);
        collector.submitModelPart(model, pose, RenderTypes.entityCutout(TEXTURE), glow ? 0xF000F0 : s.lightCoords,
                OverlayTexture.NO_OVERLAY, null, color, s.breakProgress);
        pose.popPose();
    }

    @Override public void submit(State s, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        pose.pushPose(); pose.translate(0.5, 0, 0.5); pose.mulPose(Axis.YP.rotationDegrees(-s.facing.toYRot()));
        if (s.meter) {
            part(needle, s, pose, collector, -2.625F, 10.219F, 6.45F, 70 - Math.clamp(s.voltage / 1.25F, 0, 1) * 140, 1, 0xFF202C35, false);
            part(needle, s, pose, collector, 2.625F, 10.219F, 6.45F, 70 - Math.clamp(s.load / 1.25F, 0, 1) * 140, 1, 0xFFB55333, false);
            boolean warning = s.powered && (s.voltage < 0.9F || s.voltage > 1.1F || s.load > 1);
            part(lamp, s, pose, collector, 0, 7.15F, 6.45F, 0, 1,
                    !s.powered ? 0xFF34444B : warning ? 0xFFFFAA40 : 0xFF66E4B1, s.powered);
        } else {
            for (int i = 0; i < 5; i++) {
                float fill = Math.clamp(s.charge * 5 - i, 0, 1);
                if (fill > 0.001F) part(segment, s, pose, collector, -2.8125F + i * 1.25F, 6.8125F, 7.4F, 0, fill,
                        s.charge < 0.1F ? 0xFFFFB44D : 0xFF67E9B0, true);
            }
            int color = s.flow > 0 ? 0xFF66BDFF : s.flow < 0 ? 0xFFFFAE55 : 0xFF34444B;
            float height = s.flow == 0 ? 1 : s.pulse;
            part(lamp, s, pose, collector, 2.6F, 5.2F, 7.4F, 0, height, color, s.flow != 0);
        }
        pose.popPose();
    }
}
