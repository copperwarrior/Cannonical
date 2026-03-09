package org.shipwrights.cannonical.mixin.common;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.shipwrights.cannonical.content.projectile.CannonBlockDamageSystem;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
public abstract class LevelChunkMixin {
    @Shadow
    @Final
    private Level level;

    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void cannonical$syncDamageWithChunkStateWrites(BlockPos blockPos, BlockState newState, boolean moved,
                                                           CallbackInfoReturnable<BlockState> cir) {
        if (this.level.isClientSide() || !(this.level instanceof ServerLevel serverLevel)) {
            return;
        }

        BlockState oldState = cir.getReturnValue();
        if (oldState == null) {
            return;
        }
        if (oldState.getBlock() == newState.getBlock()) {
            return;
        }

        if (oldState.getBlock() instanceof MovingPistonBlock && !newState.isAir()) {
            CannonBlockDamageSystem.transferLikelyPistonCompletionDamage(serverLevel, blockPos, newState);
            return;
        }

        // Piston movement migrates damage explicitly via PistonMovingBlockEntityMixin.
        if (oldState.getBlock() instanceof MovingPistonBlock || newState.getBlock() instanceof MovingPistonBlock) {
            return;
        }
        if (newState.isAir() && CannonBlockDamageSystem.isLikelyPistonMoveSource(serverLevel, blockPos, oldState)) {
            return;
        }

        // Falling blocks migrate damage when they become entities.
        if (oldState.getBlock() instanceof FallingBlock && (newState.isAir() || !newState.getFluidState().isEmpty())) {
            return;
        }

        CannonBlockDamageSystem.clearDamage(serverLevel, blockPos);
    }
}
