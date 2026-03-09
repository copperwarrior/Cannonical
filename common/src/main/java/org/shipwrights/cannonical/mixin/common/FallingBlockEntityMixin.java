package org.shipwrights.cannonical.mixin.common;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.shipwrights.cannonical.content.projectile.CannonBlockDamageSystem;
import org.shipwrights.cannonical.content.projectile.FallingBlockDamageCarrier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FallingBlockEntity.class)
public abstract class FallingBlockEntityMixin implements FallingBlockDamageCarrier {
    @Unique
    private static final String CANNONICAL_CARRIED_DAMAGE_STATE_TAG = "CannonicalCarriedDamageState";
    @Unique
    private int cannonical$carriedDamageState;

    @Inject(method = "fall", at = @At("RETURN"))
    private static void cannonical$captureDamageOnFall(Level level, BlockPos blockPos, BlockState blockState,
                                                       CallbackInfoReturnable<FallingBlockEntity> cir) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        FallingBlockEntity entity = cir.getReturnValue();
        if (!(entity instanceof FallingBlockDamageCarrier carrier)) {
            return;
        }

        int carriedState = CannonBlockDamageSystem.takeDamageState(serverLevel, blockPos);
        if (carriedState > 0) {
            carrier.cannonical$setCarriedDamageState(carriedState);
        }
    }

    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"
            )
    )
    private boolean cannonical$applyCarriedDamageOnPlacement(Level level, BlockPos blockPos, BlockState blockState, int flags) {
        boolean placed = level.setBlock(blockPos, blockState, flags);
        if (placed && level instanceof ServerLevel serverLevel && this.cannonical$carriedDamageState > 0) {
            CannonBlockDamageSystem.applyTransferredDamageState(serverLevel, blockPos, blockState, this.cannonical$carriedDamageState);
            this.cannonical$carriedDamageState = 0;
        }
        return placed;
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void cannonical$saveCarriedDamageState(CompoundTag compoundTag, CallbackInfo ci) {
        if (this.cannonical$carriedDamageState > 0) {
            compoundTag.putByte(CANNONICAL_CARRIED_DAMAGE_STATE_TAG, (byte) this.cannonical$carriedDamageState);
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void cannonical$readCarriedDamageState(CompoundTag compoundTag, CallbackInfo ci) {
        if (compoundTag.contains(CANNONICAL_CARRIED_DAMAGE_STATE_TAG)) {
            this.cannonical$carriedDamageState = Math.max(0, Math.min(15, compoundTag.getByte(CANNONICAL_CARRIED_DAMAGE_STATE_TAG)));
        } else {
            this.cannonical$carriedDamageState = 0;
        }
    }

    @Override
    public int cannonical$getCarriedDamageState() {
        return this.cannonical$carriedDamageState;
    }

    @Override
    public void cannonical$setCarriedDamageState(int damageState) {
        this.cannonical$carriedDamageState = Math.max(0, Math.min(15, damageState));
    }
}
