package org.shipwrights.cannonical.content.explosive;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import org.shipwrights.cannonical.registry.ModBlocks;

public class GunpowderBarrelBlock extends TntBlock implements SimpleWaterloggedBlock {
    private static final String TAG_GUNPOWDER_CHARGE = "GunpowderCharge";
    private static final String TAG_CLOSED = "Closed";
    private static final String TAG_WATERLOGGED = "Waterlogged";
    private static final int MIN_BURN_CONSUME_TICKS = 80;
    private static final int MAX_BURN_CONSUME_TICKS = 120;
    public static final int MAX_GUNPOWDER_CHARGE = 7;
    public static final int DEFAULT_PRIME_FUSE = 80;
    public static final IntegerProperty GUNPOWDER_CHARGE = IntegerProperty.create("charge", 0, MAX_GUNPOWDER_CHARGE);
    public static final BooleanProperty CLOSED = BooleanProperty.create("closed");
    public static final BooleanProperty BURNING = BooleanProperty.create("burning");
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final IntegerProperty BURN_TICKS = IntegerProperty.create("burn_ticks", 0, MAX_BURN_CONSUME_TICKS);

    public GunpowderBarrelBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(UNSTABLE, false)
                .setValue(GUNPOWDER_CHARGE, 0)
                .setValue(CLOSED, false)
                .setValue(BURNING, false)
                .setValue(WATERLOGGED, false)
                .setValue(BURN_TICKS, 0));
    }

    public static void prime(Level level, BlockPos blockPos, LivingEntity igniter) {
        prime(level, blockPos, igniter, DEFAULT_PRIME_FUSE);
    }

    public static void prime(Level level, BlockPos blockPos, LivingEntity igniter, int fuseTicks) {
        prime(level, blockPos, igniter, fuseTicks, getStoredCharge(level.getBlockState(blockPos)));
    }

    public static void prime(Level level, BlockPos blockPos, LivingEntity igniter, int fuseTicks, int gunpowderCharge) {
        if (level.isClientSide) {
            return;
        }
        BlockState state = level.getBlockState(blockPos);
        if (!isClosed(state) || isWaterlogged(state)) {
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

    public boolean ignite(Level level, BlockPos blockPos, LivingEntity igniter) {
        if (level.isClientSide) {
            return false;
        }
        BlockState state = level.getBlockState(blockPos);
        if (!isClosed(state) || isWaterlogged(state)) {
            return false;
        }

        int chainFuse = level.random.nextInt(15) + 10;
        prime(level, blockPos, igniter, chainFuse, getStoredCharge(state));
        level.removeBlock(blockPos, false);
        return true;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos blockPos, BlockState oldState, boolean movedByPiston) {
        if (oldState.is(state.getBlock())) {
            return;
        }
        if (tryPrimeFromRedstoneSignal(level, blockPos, state)) {
            return;
        }
        tryIgniteFromEnvironment(level, blockPos, state);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        ItemStack stack = context.getItemInHand();
        int storedCharge = readStoredCharge(stack);
        boolean storedClosed = readStoredClosed(stack);
        boolean storedWaterlogged = readStoredWaterlogged(stack);
        FluidState fluidState = context.getLevel().getFluidState(context.getClickedPos());
        return this.defaultBlockState()
                .setValue(GUNPOWDER_CHARGE, storedCharge)
                .setValue(CLOSED, storedClosed)
                .setValue(BURNING, false)
                .setValue(WATERLOGGED, storedWaterlogged || (!storedClosed && fluidState.is(FluidTags.WATER)))
                .setValue(BURN_TICKS, 0);
    }

    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos blockPos, BlockState state) {
        return createBarrelStack(state);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos blockPos, Block block, BlockPos fromPos, boolean isMoving) {
        if (tryPrimeFromRedstoneSignal(level, blockPos, state)) {
            return;
        }
        tryIgniteFromEnvironment(level, blockPos, state);
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos blockPos, BlockState state, Player player) {
        if (!level.isClientSide && !player.isCreative()) {
            if (isClosed(state) && state.getValue(UNSTABLE) && !isWaterlogged(state)) {
                prime(level, blockPos, player, DEFAULT_PRIME_FUSE, getStoredCharge(state));
            }
        }

        super.playerWillDestroy(level, blockPos, state, player);
    }

    @Override
    public void playerDestroy(Level level, Player player, BlockPos blockPos, BlockState state, BlockEntity blockEntity, ItemStack toolStack) {
        player.awardStat(Stats.BLOCK_MINED.get(this));
        player.causeFoodExhaustion(0.005F);
        if (!level.isClientSide && !player.isCreative()) {
            if (isClosed(state) && state.getValue(UNSTABLE) && !isWaterlogged(state)) {
                return;
            }

            int charge = getStoredCharge(state);
            if (isClosed(state)) {
                popResource(level, blockPos, createBarrelStack(state));
            } else {
                if (charge > 0) {
                    popResource(level, blockPos, new ItemStack(Items.GUNPOWDER, charge));
                }
                popResource(level, blockPos, createBarrelStack(0, false, false));
            }
        }
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos blockPos, RandomSource random) {
        if (isWaterlogged(state)) {
            if (isBurning(state) || state.getValue(BURN_TICKS) > 0) {
                level.setBlock(blockPos, state.setValue(BURNING, false).setValue(BURN_TICKS, 0), 3);
            }
            return;
        }
        if (isClosed(state) || !isBurning(state)) {
            return;
        }

        int currentCharge = getStoredCharge(state);
        if (currentCharge <= 0) {
            level.setBlock(blockPos, state.setValue(BURNING, false).setValue(BURN_TICKS, 0), 3);
            return;
        }

        AABB flameAabb = new AABB(
                blockPos.getX() + 0.125D,
                blockPos.getY() + 0.95D,
                blockPos.getZ() + 0.125D,
                blockPos.getX() + 0.875D,
                blockPos.getY() + 1.25D,
                blockPos.getZ() + 0.875D
        );
        for (Entity entity : level.getEntities((Entity) null, flameAabb, e -> e.isAlive() && !e.fireImmune())) {
            applyBarrelFireDamage(level, state, entity);
        }

        double smokeX = blockPos.getX() + 0.5D + (random.nextDouble() - 0.5D) * 0.3D;
        double smokeY = blockPos.getY() + 0.95D;
        double smokeZ = blockPos.getZ() + 0.5D + (random.nextDouble() - 0.5D) * 0.3D;
        level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, smokeX, smokeY, smokeZ, 0, 0.0D, 0.16D, 0.0D, 1.0D);

        int burnTicks = state.getValue(BURN_TICKS);
        if (burnTicks > 1) {
            level.setBlock(blockPos, state.setValue(BURN_TICKS, burnTicks - 1), 3);
            level.scheduleTick(blockPos, this, 1);
            return;
        }

        int nextCharge = currentCharge - 1;
        BlockState nextState = state.setValue(GUNPOWDER_CHARGE, nextCharge);
        if (nextCharge <= 0) {
            nextState = nextState.setValue(BURNING, false).setValue(BURN_TICKS, 0);
            level.setBlock(blockPos, nextState, 3);
            return;
        }

        nextState = nextState.setValue(BURN_TICKS, nextBurnConsumeDelay(random));
        level.setBlock(blockPos, nextState, 3);
        level.scheduleTick(blockPos, this, 1);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos blockPos, RandomSource random) {
        if (isClosed(state) || isWaterlogged(state) || !isBurning(state) || getStoredCharge(state) <= 0) {
            return;
        }

        double baseX = blockPos.getX() + 0.5D;
        double baseY = blockPos.getY() + 0.95D;
        double baseZ = blockPos.getZ() + 0.5D;
        for (int i = 0; i < 2; i++) {
            double offsetX = (random.nextDouble() - 0.5D) * 0.3D;
            double offsetZ = (random.nextDouble() - 0.5D) * 0.3D;
            level.addParticle(ParticleTypes.LAVA, baseX + offsetX, baseY, baseZ + offsetZ, 0.0D, 0.05D, 0.0D);
        }
    }

    @Override
    public void wasExploded(Level level, BlockPos blockPos, Explosion explosion) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        BlockState state = level.getBlockState(blockPos);
        if (!isClosed(state) || isWaterlogged(state)) {
            return;
        }

        LivingEntity owner = null;
        Entity indirectSource = explosion.getIndirectSourceEntity();
        if (indirectSource instanceof LivingEntity livingEntity) {
            owner = livingEntity;
        }

        int shortenedFuse = serverLevel.random.nextInt(15) + 10;
        prime(serverLevel, blockPos, owner, shortenedFuse, getStoredCharge(state));
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos blockPos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        ItemStack heldStack = player.getItemInHand(hand);
        if (heldStack.isEmpty()) {
            if (!level.isClientSide) {
                level.setBlock(blockPos, state.cycle(CLOSED).setValue(BURNING, false).setValue(BURN_TICKS, 0), 3);
                level.gameEvent(player, GameEvent.BLOCK_CHANGE, blockPos);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (heldStack.is(Items.GUNPOWDER)) {
            if (isClosed(state) || isBurning(state)) {
                return InteractionResult.PASS;
            }

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
        if (isWaterlogged(state)) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!isClosed(state)) {
            if (!startOpenBurning(level, blockPos, state, player)) {
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        } else {
            prime(level, blockPos, player, DEFAULT_PRIME_FUSE, getStoredCharge(state));
            level.setBlock(blockPos, Blocks.AIR.defaultBlockState(), 11);
        }

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
        if (isWaterlogged(state)) {
            return;
        }

        Entity owner = projectile.getOwner();
        LivingEntity igniter = owner instanceof LivingEntity livingEntity ? livingEntity : null;
        if (!isClosed(state)) {
            startOpenBurning(level, blockPos, state, igniter);
        } else {
            prime(level, blockPos, igniter, DEFAULT_PRIME_FUSE, getStoredCharge(state));
            level.removeBlock(blockPos, false);
        }
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) && !isClosed(state) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos blockPos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED) && !isClosed(state)) {
            level.scheduleTick(blockPos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, direction, neighborState, level, blockPos, neighborPos);
    }

    @Override
    public boolean canPlaceLiquid(BlockGetter level, BlockPos blockPos, BlockState state, Fluid fluid) {
        return !isClosed(state) && !state.getValue(WATERLOGGED) && fluid == Fluids.WATER;
    }

    @Override
    public boolean placeLiquid(LevelAccessor level, BlockPos blockPos, BlockState state, FluidState fluidState) {
        if (isClosed(state) || state.getValue(WATERLOGGED) || fluidState.getType() != Fluids.WATER) {
            return false;
        }

        BlockState nextState = state.setValue(WATERLOGGED, true).setValue(BURNING, false).setValue(BURN_TICKS, 0);
        level.setBlock(blockPos, nextState, 3);
        level.scheduleTick(blockPos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        return true;
    }

    @Override
    public boolean dropFromExplosion(Explosion explosion) {
        return false;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(GUNPOWDER_CHARGE, CLOSED, BURNING, WATERLOGGED, BURN_TICKS);
    }

    private static int getStoredCharge(BlockState state) {
        return state.getValue(GUNPOWDER_CHARGE);
    }

    public static int sanitizeCharge(int gunpowderCharge) {
        return Math.max(0, Math.min(MAX_GUNPOWDER_CHARGE, gunpowderCharge));
    }

    private static boolean isClosed(BlockState state) {
        return state.getValue(CLOSED);
    }

    private static boolean isBurning(BlockState state) {
        return state.getValue(BURNING);
    }

    private static boolean isWaterlogged(BlockState state) {
        return state.getValue(WATERLOGGED);
    }

    private static int readStoredCharge(ItemStack stack) {
        if (!stack.hasTag()) {
            return 0;
        }
        return sanitizeCharge(stack.getTag().getInt(TAG_GUNPOWDER_CHARGE));
    }

    private static boolean readStoredClosed(ItemStack stack) {
        return stack.hasTag() && stack.getTag().getBoolean(TAG_CLOSED);
    }

    private static boolean readStoredWaterlogged(ItemStack stack) {
        return stack.hasTag() && stack.getTag().getBoolean(TAG_WATERLOGGED);
    }

    public static int getBurningLightLevel(BlockState state) {
        if (!isBurning(state) || isWaterlogged(state)) {
            return 0;
        }

        int clampedCharge = Math.max(1, sanitizeCharge(getStoredCharge(state)));
        double progress = (clampedCharge - 1) / (double) (MAX_GUNPOWDER_CHARGE - 1);
        return 13 + (int) Math.round(progress * 2.0D);
    }

    private static ItemStack createBarrelStack(BlockState state) {
        return createBarrelStack(getStoredCharge(state), isClosed(state), isWaterlogged(state));
    }

    private static ItemStack createBarrelStack(int charge, boolean closed, boolean waterlogged) {
        ItemStack stack = new ItemStack(ModBlocks.GUNPOWDER_BARREL.get());
        stack.getOrCreateTag().putInt(TAG_GUNPOWDER_CHARGE, sanitizeCharge(charge));
        stack.getTag().putBoolean(TAG_CLOSED, closed);
        stack.getTag().putBoolean(TAG_WATERLOGGED, waterlogged);
        return stack;
    }

    private boolean startOpenBurning(Level level, BlockPos blockPos, BlockState state, LivingEntity igniter) {
        if (isClosed(state) || isWaterlogged(state) || getStoredCharge(state) <= 0) {
            return false;
        }
        if (isBurning(state)) {
            return false;
        }

        if (!level.isClientSide) {
            level.setBlock(blockPos, state.setValue(BURNING, true).setValue(BURN_TICKS, nextBurnConsumeDelay(level.random)), 3);
            level.scheduleTick(blockPos, this, 1);
            level.gameEvent(igniter, GameEvent.BLOCK_CHANGE, blockPos);
        }
        return true;
    }

    private static int nextBurnConsumeDelay(RandomSource random) {
        return MIN_BURN_CONSUME_TICKS + random.nextInt(MAX_BURN_CONSUME_TICKS - MIN_BURN_CONSUME_TICKS + 1);
    }

    private static void applyBarrelFireDamage(Level level, BlockState state, Entity entity) {
        if (isClosed(state) || isWaterlogged(state) || !isBurning(state) || getStoredCharge(state) <= 0) {
            return;
        }

        entity.setRemainingFireTicks(entity.getRemainingFireTicks() + 1);
        if (entity.getRemainingFireTicks() == 0) {
            entity.setSecondsOnFire(8);
        }
        entity.hurt(level.damageSources().inFire(), 1.0F);
    }

    private static boolean tryPrimeFromRedstoneSignal(Level level, BlockPos blockPos, BlockState state) {
        if (!level.hasNeighborSignal(blockPos) || !isClosed(state) || isWaterlogged(state)) {
            return false;
        }
        prime(level, blockPos, null, DEFAULT_PRIME_FUSE, getStoredCharge(state));
        level.removeBlock(blockPos, false);
        return true;
    }

    private void tryIgniteFromEnvironment(Level level, BlockPos blockPos, BlockState state) {
        if (level.isClientSide || isWaterlogged(state) || isBurning(state) || !hasIgnitingNeighbor(level, blockPos)) {
            return;
        }

        if (isClosed(state)) {
            prime(level, blockPos, null, DEFAULT_PRIME_FUSE, getStoredCharge(state));
            level.removeBlock(blockPos, false);
            return;
        }

        startOpenBurning(level, blockPos, state, null);
    }

    private static boolean hasIgnitingNeighbor(Level level, BlockPos blockPos) {
        for (Direction direction : Direction.values()) {
            BlockPos adjacentPos = blockPos.relative(direction);
            BlockState adjacentState = level.getBlockState(adjacentPos);
            if (adjacentState.getBlock() instanceof BaseFireBlock) {
                return true;
            }
            if (adjacentState.getBlock() instanceof CampfireBlock
                    && adjacentState.hasProperty(CampfireBlock.LIT)
                    && adjacentState.getValue(CampfireBlock.LIT)) {
                return true;
            }
            if (level.getFluidState(adjacentPos).is(FluidTags.LAVA)) {
                return true;
            }
        }
        return false;
    }
}
