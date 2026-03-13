package org.shipwrights.cannonical.content.explosive;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;

public class GunpowderBarrelBlock extends TntBlock {
    public static final int MAX_GUNPOWDER_CHARGE = 10;
    public static final IntegerProperty GUNPOWDER_CHARGE = IntegerProperty.create("charge", 0, MAX_GUNPOWDER_CHARGE);

    public GunpowderBarrelBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(UNSTABLE, false).setValue(GUNPOWDER_CHARGE, 0));
    }

    public static void prime(Level level, BlockPos blockPos, LivingEntity igniter) {
        prime(level, blockPos, igniter, 80);
    }

    public static void prime(Level level, BlockPos blockPos, LivingEntity igniter, int fuseTicks) {
        prime(level, blockPos, igniter, fuseTicks, getStoredCharge(level.getBlockState(blockPos)));
    }

    public static void prime(Level level, BlockPos blockPos, LivingEntity igniter, int fuseTicks, int gunpowderCharge) {
        if (level.isClientSide) {
            return;
        }

        GunpowderBarrelPrimedEntity primed = new GunpowderBarrelPrimedEntity(
                level,
                blockPos.getX() + 0.5D,
                blockPos.getY(),
                blockPos.getZ() + 0.5D,
                igniter,
                sanitizeCharge(gunpowderCharge)
        );
        primed.setFuse(Math.max(1, fuseTicks));
        level.addFreshEntity(primed);
        level.gameEvent(igniter, GameEvent.PRIME_FUSE, blockPos);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos blockPos, BlockState oldState, boolean movedByPiston) {
        if (!oldState.is(state.getBlock()) && level.hasNeighborSignal(blockPos)) {
            prime(level, blockPos, null, 80, getStoredCharge(state));
            level.removeBlock(blockPos, false);
        }
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos blockPos, Block block, BlockPos fromPos, boolean isMoving) {
        if (level.hasNeighborSignal(blockPos)) {
            prime(level, blockPos, null, 80, getStoredCharge(state));
            level.removeBlock(blockPos, false);
        }
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos blockPos, BlockState state, Player player) {
        if (!level.isClientSide && !player.isCreative() && state.getValue(UNSTABLE)) {
            prime(level, blockPos, player, 80, getStoredCharge(state));
        }

        super.playerWillDestroy(level, blockPos, state, player);
    }

    @Override
    public void wasExploded(Level level, BlockPos blockPos, Explosion explosion) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        LivingEntity owner = null;
        Entity indirectSource = explosion.getIndirectSourceEntity();
        if (indirectSource instanceof LivingEntity livingEntity) {
            owner = livingEntity;
        }

        int shortenedFuse = serverLevel.random.nextInt(15) + 10;
        prime(serverLevel, blockPos, owner, shortenedFuse, getStoredCharge(level.getBlockState(blockPos)));
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos blockPos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        ItemStack heldStack = player.getItemInHand(hand);
        if (heldStack.is(Items.GUNPOWDER)) {
            int currentCharge = getStoredCharge(state);
            if (currentCharge >= MAX_GUNPOWDER_CHARGE) {
                return InteractionResult.sidedSuccess(level.isClientSide);
            }

            if (!level.isClientSide) {
                level.setBlock(blockPos, state.setValue(GUNPOWDER_CHARGE, currentCharge + 1), 3);
                if (!player.getAbilities().instabuild) {
                    heldStack.shrink(1);
                }
                player.awardStat(Stats.ITEM_USED.get(Items.GUNPOWDER));
                level.gameEvent(player, GameEvent.BLOCK_CHANGE, blockPos);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (!heldStack.is(Items.FLINT_AND_STEEL) && !heldStack.is(Items.FIRE_CHARGE)) {
            return super.use(state, level, blockPos, player, hand, hitResult);
        }

        prime(level, blockPos, player, 80, getStoredCharge(state));
        level.setBlock(blockPos, Blocks.AIR.defaultBlockState(), 11);

        Item usedItem = heldStack.getItem();
        if (!player.getAbilities().instabuild) {
            if (heldStack.is(Items.FLINT_AND_STEEL)) {
                heldStack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
            } else {
                heldStack.shrink(1);
            }
        }

        player.awardStat(Stats.ITEM_USED.get(usedItem));
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onProjectileHit(Level level, BlockState state, BlockHitResult hitResult, Projectile projectile) {
        if (level.isClientSide) {
            return;
        }

        BlockPos blockPos = hitResult.getBlockPos();
        if (!projectile.isOnFire() || !projectile.mayInteract(level, blockPos)) {
            return;
        }

        Entity owner = projectile.getOwner();
        LivingEntity igniter = owner instanceof LivingEntity livingEntity ? livingEntity : null;
        prime(level, blockPos, igniter, 80, getStoredCharge(state));
        level.removeBlock(blockPos, false);
    }

    @Override
    public boolean dropFromExplosion(Explosion explosion) {
        return false;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(GUNPOWDER_CHARGE);
    }

    private static int getStoredCharge(BlockState state) {
        return state.hasProperty(GUNPOWDER_CHARGE) ? state.getValue(GUNPOWDER_CHARGE) : 0;
    }

    private static int sanitizeCharge(int gunpowderCharge) {
        return Math.max(0, Math.min(MAX_GUNPOWDER_CHARGE, gunpowderCharge));
    }
}
