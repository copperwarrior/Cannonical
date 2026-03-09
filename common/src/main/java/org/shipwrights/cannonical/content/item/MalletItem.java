package org.shipwrights.cannonical.content.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.shipwrights.cannonical.Cannonical;
import org.shipwrights.cannonical.content.projectile.CannonBlockDamageSystem;
import org.shipwrights.cannonical.registry.ModEnchantments;

public class MalletItem extends DiggerItem {
    private static final float INSTAMINE_DESTROY_SPEED = 1024.0F;
    private static final int BASE_REPAIR_COOLDOWN_TICKS = 12;
    private static final int CARPENTER_RADIUS = 1;
    private static final TagKey<Block> MALLET_EFFECTIVE_BLOCKS =
            TagKey.create(Registries.BLOCK, new ResourceLocation(Cannonical.MOD_ID, "mallet_effective_blocks"));
    private final double baseRepairPower;
    private final double repairSpeedMultiplier;

    public MalletItem(Properties properties, double baseRepairPower, double repairSpeedMultiplier, Tier tier) {
        super(1.0F, -2.8F, tier, MALLET_EFFECTIVE_BLOCKS, properties);
        this.baseRepairPower = Math.max(0.05D, baseRepairPower);
        this.repairSpeedMultiplier = Math.max(0.1D, repairSpeedMultiplier);
    }

    @Override
    public int getEnchantmentValue() {
        return 14;
    }

    @Override
    public boolean canAttackBlock(BlockState blockState, Level level, BlockPos blockPos, Player player) {
        // Mallets should never mine/harvest blocks; left-click is reserved for repair logic.
        return false;
    }

    @Override
    public float getDestroySpeed(ItemStack itemStack, BlockState blockState) {
        return INSTAMINE_DESTROY_SPEED;
    }

    public static void registerEvents() {
        // Break-triggered repair is handled via ServerPlayerGameMode mixin.
    }

    public boolean tryRepairOnBreak(ServerLevel level, BlockPos blockPos, BlockState blockState, Player player, ItemStack malletStack) {
        return this.tryRepairNow(level, blockPos, blockState, player, malletStack);
    }

    private boolean tryRepairNow(ServerLevel level, BlockPos blockPos, BlockState blockState, Player player, ItemStack malletStack) {
        if (blockState.isAir() || blockState.getDestroySpeed(level, blockPos) < 0.0F) {
            return false;
        }
        if (player.getCooldowns().isOnCooldown(this)) {
            return false;
        }

        int efficiencyLevel = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.BLOCK_EFFICIENCY, malletStack);
        double effectiveRepairPower = this.baseRepairPower + Math.max(0, efficiencyLevel);
        int centerRepairPower = this.rollRepairAmount(level, effectiveRepairPower);

        int repairedBlocks = 0;
        int centerRepaired = centerRepairPower > 0 ? CannonBlockDamageSystem.repairDamage(level, blockPos, centerRepairPower) : 0;
        if (centerRepaired > 0) {
            repairedBlocks++;
            boolean fullyRepaired = CannonBlockDamageSystem.getMiningProgressFraction(level, blockPos) <= 0.0F;
            this.emitRepairFeedback(level, blockPos, blockState, centerRepaired, fullyRepaired);
        }

        int carpenterLevel = EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.CARPENTER.get(), malletStack);
        if (carpenterLevel > 0) {
            repairedBlocks += this.repairNearbyBlocks(level, blockPos, effectiveRepairPower, carpenterLevel);
        }

        if (repairedBlocks <= 0) {
            return false;
        }

        if (!player.getAbilities().instabuild) {
            malletStack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(InteractionHand.MAIN_HAND));
        }
        player.getCooldowns().addCooldown(this, this.computeRepairCooldownTicks(efficiencyLevel));
        return true;
    }

    private int repairNearbyBlocks(ServerLevel level, BlockPos center, double basePower, int carpenterLevel) {
        int repairedBlocks = 0;
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        for (int x = -CARPENTER_RADIUS; x <= CARPENTER_RADIUS; x++) {
            for (int y = -CARPENTER_RADIUS; y <= CARPENTER_RADIUS; y++) {
                for (int z = -CARPENTER_RADIUS; z <= CARPENTER_RADIUS; z++) {
                    if (x == 0 && y == 0 && z == 0) {
                        continue;
                    }

                    mutablePos.setWithOffset(center, x, y, z);
                    BlockState nearbyState = level.getBlockState(mutablePos);
                    if (nearbyState.isAir() || nearbyState.getDestroySpeed(level, mutablePos) < 0.0F) {
                        continue;
                    }

                    int repairAmount = this.rollRepairAmount(level, basePower * this.getCarpenterExtraFraction(carpenterLevel));
                    if (repairAmount <= 0) {
                        continue;
                    }

                    int repairedAmount = CannonBlockDamageSystem.repairDamage(level, mutablePos, repairAmount);
                    if (repairedAmount > 0) {
                        repairedBlocks++;
                        boolean fullyRepaired = CannonBlockDamageSystem.getMiningProgressFraction(level, mutablePos) <= 0.0F;
                        this.emitRepairFeedback(level, mutablePos, nearbyState, repairedAmount, fullyRepaired);
                    }
                }
            }
        }

        return repairedBlocks;
    }

    private int rollRepairAmount(ServerLevel level, double repairPower) {
        if (repairPower <= 0.0D) {
            return 0;
        }

        int guaranteed = (int) Math.floor(repairPower);
        double fractionalChance = repairPower - guaranteed;
        if (level.random.nextDouble() < fractionalChance) {
            guaranteed++;
        }
        return guaranteed;
    }

    private int computeRepairCooldownTicks(int efficiencyLevel) {
        double efficiencySpeedMultiplier = 1.0D + (Math.max(0, efficiencyLevel) * 0.12D);
        double totalSpeedMultiplier = this.repairSpeedMultiplier * efficiencySpeedMultiplier;
        return Math.max(1, (int) Math.ceil(BASE_REPAIR_COOLDOWN_TICKS / totalSpeedMultiplier));
    }

    private void emitRepairFeedback(ServerLevel level, BlockPos blockPos, BlockState blockState, int repairedAmount, boolean fullyRepaired) {
        int particleCount = Math.max(2, Math.min(6, repairedAmount + 2));
        double x = blockPos.getX() + 0.5D;
        double y = blockPos.getY() + 0.5D;
        double z = blockPos.getZ() + 0.5D;
        level.sendParticles(
                new BlockParticleOption(ParticleTypes.BLOCK, blockState),
                x, y, z,
                particleCount,
                0.15D, 0.15D, 0.15D,
                0.02D
        );
        level.playSound(
                null,
                blockPos,
                blockState.getSoundType().getHitSound(),
                SoundSource.BLOCKS,
                0.35F,
                0.95F + (level.random.nextFloat() * 0.1F)
        );

        if (!fullyRepaired) {
            return;
        }

        level.sendParticles(
                ParticleTypes.HAPPY_VILLAGER,
                x, y + 0.2D, z,
                6,
                0.2D, 0.15D, 0.2D,
                0.01D
        );
        level.playSound(
                null,
                blockPos,
                blockState.getSoundType().getPlaceSound(),
                SoundSource.BLOCKS,
                0.45F,
                0.95F + (level.random.nextFloat() * 0.1F)
        );
    }

    private double getCarpenterExtraFraction(int carpenterLevel) {
        int clampedLevel = Math.max(1, Math.min(3, carpenterLevel));
        return switch (clampedLevel) {
            case 1 -> 0.33D;
            case 2 -> 0.66D;
            default -> 1.0D;
        };
    }
}
