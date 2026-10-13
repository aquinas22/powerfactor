package com.powerfactor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.powerfactor.PowerFactor;
import com.powerfactor.machine.MachineBlock;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import org.jspecify.annotations.Nullable;

/** Preserve each machine's renderer and add a physical socket only on connected energy faces. */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class PowerConnections<T extends BlockEntity> implements BlockEntityRenderer<T, PowerConnections.State> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(PowerFactor.id("power_socket"), "main");
    private final @Nullable BlockEntityRenderer delegate;
    private final ModelPart socket, lead;
    public static class State extends BlockEntityRenderState { @Nullable BlockEntityRenderState inner; int ports; float anchor = 0.5F; }
    private PowerConnections(@Nullable BlockEntityRenderer delegate, BlockEntityRendererProvider.Context context) {
        this.delegate = delegate; var root = context.bakeLayer(LAYER); socket = root.getChild("socket"); lead = root.getChild("lead");
    }
    public static <T extends BlockEntity, S extends BlockEntityRenderState> BlockEntityRendererProvider<T, State> wrap(BlockEntityRendererProvider<T, S> provider) {
        return context -> new PowerConnections<>(provider.create(context), context);
    }
    public static <T extends BlockEntity> BlockEntityRendererProvider<T, State> only() { return context -> new PowerConnections<>(null, context); }
    public static LayerDefinition createLayer() {
        var mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("socket", CubeListBuilder.create().texOffs(0,0).addBox(-3,-3,-1.5F,6,6,1.5F)
                .texOffs(0,20).addBox(-2,-2,0,4,4,1.2F), PartPose.ZERO);
        mesh.getRoot().addOrReplaceChild("lead", CubeListBuilder.create().texOffs(32,0).addBox(-1,0,-1,2,16,2), PartPose.ZERO);
        return LayerDefinition.create(mesh,64,64);
    }
    @Override public State createRenderState() { var s = new State(); if (delegate != null) s.inner = delegate.createRenderState(); return s; }
    @Override public void extractRenderState(T be, State state, float partial, Vec3 camera, ModelFeatureRenderer.@Nullable CrumblingOverlay breaking) {
        BlockEntityRenderer.super.extractRenderState(be, state, partial, camera, breaking);
        if (delegate != null) { delegate.extractRenderState(be, state.inner, partial, camera, breaking); state.lightCoords = state.inner.lightCoords; }
        state.anchor = switch (net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).getPath()) {
            case "charging_pad" -> 0.1F;
            case "solar_panel" -> 0.2F;
            case "grow_lamp" -> 0.88F;
            case "radio_tower" -> 0.2F;
            default -> 0.5F;
        };
        state.ports = 0; var level = be.getLevel(); if (level == null) return;
        for (Direction d : Direction.values()) {
            boolean energy = level.getCapability(Capabilities.Energy.BLOCK, be.getBlockPos(), d) != null || be instanceof com.powerfactor.api.ReactiveLoad;
            if (!energy) continue;
            var pos = be.getBlockPos().relative(d); var other = level.getBlockState(pos);
            boolean cable = other.getBlock() instanceof com.powerfactor.grid.WireBlock && other.getValue(com.powerfactor.grid.ConnectorBlock.CONNECTIONS.get(d.getOpposite()));
            boolean linked = level.getCapability(Capabilities.Energy.BLOCK, pos, d.getOpposite()) != null;
            if (cable || linked) state.ports |= 1 << d.ordinal();
        }
    }
    @Override public void submit(State s, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (delegate != null) { s.inner.lightCoords = s.lightCoords; delegate.submit(s.inner, pose, collector, camera); }
        for (Direction d : Direction.values()) if ((s.ports & 1 << d.ordinal()) != 0) {
            if (d.getAxis() == Direction.Axis.Y && s.anchor != 0.5F) {
                float end = d == Direction.UP ? 1 : 0;
                pose.pushPose(); pose.translate(0.5, Math.min(s.anchor, end), 0.5);
                pose.scale(1, Math.abs(s.anchor - end), 1);
                collector.submitModelPart(lead,pose,RenderTypes.entityCutout(PowerFactor.id("textures/entity/power_socket.png")),s.lightCoords,OverlayTexture.NO_OVERLAY,null,-1,s.breakProgress);
                pose.popPose();
            }
            pose.pushPose(); pose.translate(0.5,0.5,0.5);
            if (d == Direction.UP || d == Direction.DOWN) pose.mulPose(Axis.XP.rotationDegrees(d == Direction.UP ? -90 : 90));
            else pose.mulPose(Axis.YP.rotationDegrees(-d.toYRot()));
            pose.translate(0,0,0.5);
            collector.submitModelPart(socket,pose,RenderTypes.entityCutout(PowerFactor.id("textures/entity/power_socket.png")),s.lightCoords,OverlayTexture.NO_OVERLAY,null,-1,s.breakProgress);
            if (d.getAxis().isHorizontal() && s.anchor != 0.5F) {
                pose.translate(0, Math.min(s.anchor, 0.5F) - 0.5F, -0.055);
                pose.scale(1, Math.abs(s.anchor - 0.5F), 1);
                collector.submitModelPart(lead,pose,RenderTypes.entityCutout(PowerFactor.id("textures/entity/power_socket.png")),s.lightCoords,OverlayTexture.NO_OVERLAY,null,-1,s.breakProgress);
            }
            pose.popPose();
        }
    }
    @Override public AABB getRenderBoundingBox(T be) { return delegate == null ? new AABB(be.getBlockPos()).inflate(0.1) : delegate.getRenderBoundingBox(be).inflate(0.1); }
    @Override public boolean shouldRenderOffScreen() { return delegate != null && delegate.shouldRenderOffScreen(); }
    @Override public int getViewDistance() { return delegate == null ? 64 : delegate.getViewDistance(); }
}
