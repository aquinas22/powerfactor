package com.powerfactor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.powerfactor.machine.MachineBlock;
import com.powerfactor.multiblock.CasingBlock;
import com.powerfactor.oil.RefineryBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Projects the refinery's missing parts as a hologram until the structure is complete. */
public class RefineryRenderer implements BlockEntityRenderer<RefineryBlockEntity, RefineryRenderer.State> {
    private static final AABB FULL = new AABB(0.02, 0.02, 0.02, 0.98, 0.98, 0.98), COLUMN = new AABB(0.2, 0, 0.2, 0.8, 1, 0.8);

    public static class State extends BlockEntityRenderState {
        public final List<MultiblockGhost.Ghost> ghosts = new ArrayList<>();
        public float time;
    }

    public RefineryRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(RefineryBlockEntity be, State state, float partial, Vec3 camera, ModelFeatureRenderer.@Nullable CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(be, state, partial, camera, crumbling);
        state.ghosts.clear();
        var level = be.getLevel();
        if (level == null || be.getBlockState().getValueOrElse(CasingBlock.FORMED, false)) {
            return;
        }
        state.time = level.getGameTime() + partial;
        BlockPos origin = be.getBlockPos();
        Direction facing = be.getBlockState().getValueOrElse(MachineBlock.FACING, Direction.NORTH);
        for (var part : RefineryBlockEntity.STRUCTURE.parts()) {
            BlockPos at = RefineryBlockEntity.STRUCTURE.worldPos(origin, facing, part);
            var bs = level.getBlockState(at);
            if (!part.matches(bs)) {
                boolean column = part.block().get() == com.powerfactor.registry.ModBlocks.DISTILLATION_COLUMN.get();
                state.ghosts.add(new MultiblockGhost.Ghost(at.subtract(origin), !bs.canBeReplaced(), column ? COLUMN : FULL));
            }
        }
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        MultiblockGhost.submit(pose, collector, state.ghosts, state.time);
    }

    @Override
    public AABB getRenderBoundingBox(RefineryBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(3, 0, 3).expandTowards(0, 4, 0);
    }
}
