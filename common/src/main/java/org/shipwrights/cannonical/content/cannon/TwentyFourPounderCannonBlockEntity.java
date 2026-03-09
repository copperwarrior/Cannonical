package org.shipwrights.cannonical.content.cannon;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.shipwrights.cannonical.registry.ModBlockEntities;

public class TwentyFourPounderCannonBlockEntity extends BlockEntity {
    public TwentyFourPounderCannonBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(ModBlockEntities.TWENTY_FOUR_POUNDER_CANNON.get(), blockPos, blockState);
    }
}
