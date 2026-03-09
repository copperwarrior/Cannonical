package org.shipwrights.cannonical.mixin.common;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.shipwrights.cannonical.content.projectile.CannonBlockDamageSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PistonMovingBlockEntity.class)
public abstract class PistonMovingBlockEntityMixin {
    @Unique
    private boolean cannonical$damageMigrated;

    @Inject(method = "finalTick", at = @At("TAIL"))
    private void cannonical$migrateDamageOnFinalTick(CallbackInfo ci) {
        cannonical$migrateDamageIfReady((PistonMovingBlockEntity) (Object) this);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private static void cannonical$migrateDamageOnTickFinalize(Level level, BlockPos blockPos, BlockState blockState,
                                                               PistonMovingBlockEntity movingBlockEntity, CallbackInfo ci) {
        cannonical$migrateDamageIfReady(movingBlockEntity);
    }

    @Unique
    private static void cannonical$migrateDamageIfReady(PistonMovingBlockEntity movingBlockEntity) {
        PistonMovingBlockEntityMixin mixin = (PistonMovingBlockEntityMixin) (Object) movingBlockEntity;
        if (mixin.cannonical$damageMigrated) {
            return;
        }

        Level level = movingBlockEntity.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (movingBlockEntity.isSourcePiston()) {
            return;
        }

        BlockPos sourcePos = movingBlockEntity.getBlockPos();
        Direction movementDirection = movingBlockEntity.getMovementDirection();

        BlockState movedState = movingBlockEntity.getMovedState();
        if (movedState.isAir()) {
            return;
        }

        BlockPos resolvedDestination = cannonical$resolveDestinationPos(serverLevel, sourcePos, movementDirection, movedState);
        if (resolvedDestination == null) {
            return;
        }

        BlockPos[] sourceCandidates = new BlockPos[]{
                sourcePos,
                sourcePos.relative(movementDirection.getOpposite())
        };
        for (BlockPos sourceCandidate : sourceCandidates) {
            if (sourceCandidate.equals(resolvedDestination)) {
                continue;
            }
            int carriedState = CannonBlockDamageSystem.takeStoredDamageState(serverLevel, sourceCandidate);
            if (carriedState <= 0) {
                continue;
            }
            CannonBlockDamageSystem.applyTransferredDamageState(serverLevel, resolvedDestination, movedState, carriedState);
            mixin.cannonical$damageMigrated = true;
            return;
        }
    }

    @Unique
    private static BlockPos cannonical$resolveDestinationPos(ServerLevel level, BlockPos basePos,
                                                             Direction movementDirection, BlockState movedState) {
        BlockPos[] destinationCandidates = new BlockPos[]{
                basePos.relative(movementDirection),
                basePos
        };
        for (BlockPos candidate : destinationCandidates) {
            BlockState liveState = level.getBlockState(candidate);
            if (liveState.is(Blocks.MOVING_PISTON)) {
                continue;
            }
            if (liveState.isAir()) {
                continue;
            }
            if (liveState.getBlock() != movedState.getBlock()) {
                continue;
            }
            return candidate;
        }
        return null;
    }
}
