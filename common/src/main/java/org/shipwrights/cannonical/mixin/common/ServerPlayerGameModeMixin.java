package org.shipwrights.cannonical.mixin.common;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.shipwrights.cannonical.content.item.MalletItem;
import org.shipwrights.cannonical.content.projectile.CannonBlockDamageSystem;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerGameModeMixin {
    private static final float MIN_HARDNESS_MULTIPLIER = 0.08F;
    private static final float DAMAGE_CURVE_EXPONENT = 0.60F;
    private static final int DEBUG_LOG_INTERVAL_TICKS = 5;
    private static final Logger CANNONICAL_MINING_LOGGER = LogUtils.getLogger();

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

    @Redirect(
            method = "incrementDestroyProgress",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;getDestroyProgress(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F"
            )
    )
    private float cannonical$scaleDestroyProgressInTick(BlockState state, Player player, BlockGetter getter, BlockPos blockPos) {
        return this.cannonical$scaledDestroyProgress(state, player, getter, blockPos, "tick");
    }

    private float cannonical$scaledDestroyProgress(BlockState state, Player player, BlockGetter getter, BlockPos blockPos, String source) {
        float vanillaProgress = state.getDestroyProgress(player, getter, blockPos);
        if (!(getter instanceof ServerLevel serverLevel)) {
            return vanillaProgress;
        }

        int rawDamageState = CannonBlockDamageSystem.getRawDamageState(serverLevel, blockPos);
        int effectiveDamageState = CannonBlockDamageSystem.getEffectiveDamageState(serverLevel, blockPos);
        float damageFraction = Math.min(1.0F, Math.max(0.0F, effectiveDamageState / 15.0F));
        if (damageFraction <= 0.0F) {
            return vanillaProgress;
        }

        float curvedDamageFraction = (float) Math.pow(damageFraction, DAMAGE_CURVE_EXPONENT);
        float hardnessMultiplier = Math.max(
                MIN_HARDNESS_MULTIPLIER,
                1.0F - ((1.0F - MIN_HARDNESS_MULTIPLIER) * curvedDamageFraction)
        );
        float scaledProgress = vanillaProgress / hardnessMultiplier;

        if ((this.level.getGameTime() % DEBUG_LOG_INTERVAL_TICKS) == 0L) {
            CANNONICAL_MINING_LOGGER.info(
                    "[Cannonical Debug][MiningScale] player={} pos={} block={} source={} rawDamage={} effectiveDamage={} damageFraction={} curvedDamageFraction={} hardnessMul={} vanillaPerTick={} scaledPerTick={}",
                    this.player.getScoreboardName(),
                    blockPos.toShortString(),
                    state.getBlock().toString(),
                    source,
                    rawDamageState,
                    effectiveDamageState,
                    damageFraction,
                    curvedDamageFraction,
                    hardnessMultiplier,
                    vanillaProgress,
                    scaledProgress
            );
        }

        return scaledProgress;
    }
}
