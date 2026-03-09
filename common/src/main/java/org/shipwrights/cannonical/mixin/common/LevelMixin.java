package org.shipwrights.cannonical.mixin.common;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.shipwrights.cannonical.content.projectile.CannonBlockDamageSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Level.class)
public abstract class LevelMixin {
    @Inject(method = "onBlockStateChange", at = @At("TAIL"))
    private void cannonical$clearDamageWhenBlockRemoved(BlockPos blockPos, BlockState oldState, BlockState newState, CallbackInfo ci) {
        Level level = (Level) (Object) this;
        if (level.isClientSide() || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (oldState.isAir()) {
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
