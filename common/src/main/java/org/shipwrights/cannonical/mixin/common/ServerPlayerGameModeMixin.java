package org.shipwrights.cannonical.mixin.common;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.shipwrights.cannonical.content.item.MalletItem;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerGameModeMixin {
    @Shadow
    protected ServerLevel level;

    @Shadow
    @Final
    protected ServerPlayer player;

    @Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true)
    private void cannonical$repairWithMalletOnBreak(BlockPos blockPos, CallbackInfoReturnable<Boolean> cir) {
        ItemStack heldStack = this.player.getMainHandItem();
        if (!(heldStack.getItem() instanceof MalletItem malletItem)) {
            return;
        }
        if (!(this.player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            cir.setReturnValue(false);
            return;
        }

        BlockState blockState = serverLevel.getBlockState(blockPos);
        malletItem.tryRepairOnBreak(serverLevel, blockPos, blockState, this.player, heldStack);
        // Mallets are repair tools; never allow the block to actually break.
        cir.setReturnValue(false);
        cir.cancel();
    }
}
