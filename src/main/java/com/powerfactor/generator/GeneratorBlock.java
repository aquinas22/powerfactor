package com.powerfactor.generator;

import com.mojang.serialization.MapCodec;
import com.powerfactor.machine.MachineBlock;
import com.powerfactor.machine.MachineBlockEntity;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** One block class for the generators and grid devices; each differs only in its block entity (and shape). */
public class GeneratorBlock extends MachineBlock {
    private final Supplier<BlockEntityType<? extends MachineBlockEntity>> type;
    private final VoxelShape shape;
    private final MapCodec<GeneratorBlock> codec;

    public GeneratorBlock(Supplier<BlockEntityType<? extends MachineBlockEntity>> type, VoxelShape shape, Properties properties) {
        super(properties);
        this.type = type;
        this.shape = shape;
        this.codec = simpleCodec(p -> new GeneratorBlock(type, shape, p));
    }

    public static GeneratorBlock full(Supplier<BlockEntityType<? extends MachineBlockEntity>> type, Properties p) {
        return new GeneratorBlock(type, Shapes.block(), p);
    }

    public static GeneratorBlock slab(Supplier<BlockEntityType<? extends MachineBlockEntity>> type, Properties p) {
        return new GeneratorBlock(type, Block.box(0, 0, 0, 16, 6, 16), p);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return codec;
    }

    @Override
    protected BlockEntityType<? extends MachineBlockEntity> blockEntityType() {
        return type.get();
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape;
    }
}
