package com.powerfactor.defense;

import com.mojang.serialization.MapCodec;
import com.powerfactor.machine.MachineBlock;
import com.powerfactor.machine.MachineBlockEntity;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/** Turret pedestal. Remembers who placed it so it never fires on them. */
public class TurretBlock extends MachineBlock {
    private static final VoxelShape SHAPE = Shapes.or(Block.box(1, 0, 1, 15, 8, 15), Block.box(3, 8, 3, 13, 16, 13));
    private final Supplier<? extends BlockEntityType<? extends MachineBlockEntity>> type;

    public TurretBlock(Supplier<? extends BlockEntityType<? extends MachineBlockEntity>> type, Properties properties) {
        super(properties);
        this.type = type;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        throw new UnsupportedOperationException("not data-driven");
    }

    @Override
    protected BlockEntityType<? extends MachineBlockEntity> blockEntityType() {
        return type.get();
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer != null && level.getBlockEntity(pos) instanceof TurretBlockEntity turret) turret.setOwner(placer.getUUID());
    }
}
