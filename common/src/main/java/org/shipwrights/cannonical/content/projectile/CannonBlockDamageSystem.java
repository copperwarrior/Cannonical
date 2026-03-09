package org.shipwrights.cannonical.content.projectile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.shipwrights.cannonical.network.CannonicalNetwork;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class CannonBlockDamageSystem {
    private static final int MAX_DAMAGE_STATE = 15;
    private static final int NO_DAMAGE_STATE = -1;
    private static final double MIN_IMPACT_FOR_ONE_DAMAGE_STATE = 350.0D; // speed 5 -> 5^2 * 14
    private static final long DAMAGE_DECAY_INTERVAL_TICKS = 24_000L;
    private static final String SAVE_DATA_ID = "cannonical_block_damage";
    private static final int CONNECT_SYNC_DELAY_TICKS = 12;
    private static final int CONNECT_SYNC_ATTEMPTS = 3;
    private static final Map<UUID, PendingSync> PENDING_PLAYER_SYNCS = new HashMap<>();

    private CannonBlockDamageSystem() {
    }

    public static ImpactResult applyImpact(ServerLevel level, BlockPos blockPos, BlockState blockState, Entity source, double impactPower) {
        return applyImpact(level, blockPos, blockState, source, impactPower, true);
    }

    public static ImpactResult applyImpact(ServerLevel level, BlockPos blockPos, BlockState blockState, Entity source,
                                           double impactPower, boolean dropOnBreak) {
        float hardness = blockState.getDestroySpeed(level, blockPos);
        if (hardness < 0.0F || blockState.isAir()) {
            clearDamage(level, blockPos);
            return new ImpactResult(false, NO_DAMAGE_STATE);
        }

        float resistance = blockState.getBlock().getExplosionResistance();
        int addedState = computeDamageStateDelta(blockState, impactPower, resistance, hardness);
        if (addedState <= 0) {
            return new ImpactResult(false, getDamageState(level, blockPos));
        }

        int previousState = getDamageState(level, blockPos);
        int nextState = clamp(previousState + addedState, 0, MAX_DAMAGE_STATE);

        if (nextState >= MAX_DAMAGE_STATE) {
            clearDamage(level, blockPos);
            boolean broken = level.destroyBlock(blockPos, dropOnBreak, source);
            if (!broken) {
                int fallbackState = MAX_DAMAGE_STATE - 1;
                setDamageState(level, blockPos, fallbackState);
                return new ImpactResult(false, fallbackState);
            }
            return new ImpactResult(true, NO_DAMAGE_STATE);
        }

        setDamageState(level, blockPos, nextState);
        return new ImpactResult(false, nextState);
    }

    public static void clearDamage(ServerLevel level, BlockPos blockPos) {
        CannonBlockDamageSavedData data = getData(level);
        long key = blockPos.asLong();
        if (data.removeDamageState(key) != NO_DAMAGE_STATE) {
            syncDamageState(level, blockPos, 0);
        }
    }

    public static float getMiningProgressFraction(ServerLevel level, BlockPos blockPos) {
        int damageState = getDamageState(level, blockPos);
        if (damageState <= 0) {
            return 0.0F;
        }
        return Math.min(1.0F, damageState / (float) MAX_DAMAGE_STATE);
    }

    public static int getRawDamageState(ServerLevel level, BlockPos blockPos) {
        return getData(level).getDamageState(blockPos.asLong());
    }

    public static int getEffectiveDamageState(ServerLevel level, BlockPos blockPos) {
        return getDamageState(level, blockPos);
    }

    public static void queueConnectSync(ServerPlayer player) {
        PENDING_PLAYER_SYNCS.put(player.getUUID(), new PendingSync(CONNECT_SYNC_DELAY_TICKS, CONNECT_SYNC_ATTEMPTS));
    }

    public static void clearQueuedSync(ServerPlayer player) {
        PENDING_PLAYER_SYNCS.remove(player.getUUID());
    }

    public static void tickQueuedSyncs(MinecraftServer server) {
        if (PENDING_PLAYER_SYNCS.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<UUID, PendingSync>> iterator = PENDING_PLAYER_SYNCS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PendingSync> entry = iterator.next();
            PendingSync pending = entry.getValue();
            if (pending.ticksUntilNextAttempt > 0) {
                pending.ticksUntilNextAttempt--;
                continue;
            }

            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || player.connection == null) {
                iterator.remove();
                continue;
            }

            syncAllDamageStatesToPlayer(player);

            pending.attemptsRemaining--;
            if (pending.attemptsRemaining <= 0) {
                iterator.remove();
            } else {
                pending.ticksUntilNextAttempt = CONNECT_SYNC_DELAY_TICKS;
            }
        }
    }

    public static void syncAllDamageStatesToPlayer(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        ChunkPos centerChunk = player.chunkPosition();
        int viewDistance = level.getServer().getPlayerList().getViewDistance() + 1;
        for (int chunkX = centerChunk.x - viewDistance; chunkX <= centerChunk.x + viewDistance; chunkX++) {
            for (int chunkZ = centerChunk.z - viewDistance; chunkZ <= centerChunk.z + viewDistance; chunkZ++) {
                syncChunkColumnToPlayer(player, level, chunkX, chunkZ);
            }
        }
    }

    public static void syncChunkColumnToPlayer(ServerPlayer player, ServerLevel level, int chunkX, int chunkZ) {
        CannonBlockDamageSavedData data = getData(level);
        data.forEachSectionInChunk(chunkX, chunkZ, (sectionKey, states) -> {
            if (states.isEmpty()) {
                return;
            }
            CannonicalNetwork.sendBlockDamageSectionSnapshot(
                    player,
                    level.dimension().location(),
                    SectionPos.x(sectionKey),
                    SectionPos.y(sectionKey),
                    SectionPos.z(sectionKey),
                    states
            );
        });
    }

    public static int repairDamage(ServerLevel level, BlockPos blockPos, int repairAmount) {
        if (repairAmount <= 0) {
            return 0;
        }

        int currentState = getDamageState(level, blockPos);
        if (currentState <= 0) {
            return 0;
        }

        int nextState = clamp(currentState - repairAmount, 0, MAX_DAMAGE_STATE);
        if (nextState <= 0) {
            clearDamage(level, blockPos);
            return currentState;
        }

        setDamageState(level, blockPos, nextState);
        return currentState - nextState;
    }

    public static int takeDamageState(ServerLevel level, BlockPos blockPos) {
        int currentState = getDamageState(level, blockPos);
        if (currentState <= 0) {
            return 0;
        }

        clearDamage(level, blockPos);
        return currentState;
    }

    public static int takeStoredDamageState(ServerLevel level, BlockPos blockPos) {
        CannonBlockDamageSavedData data = getData(level);
        long key = blockPos.asLong();
        int removedState = data.removeDamageState(key);
        int clampedState = clamp(removedState, 0, MAX_DAMAGE_STATE);
        if (removedState != NO_DAMAGE_STATE) {
            syncDamageState(level, blockPos, 0);
        }
        return clampedState;
    }

    public static boolean isLikelyPistonMoveSource(ServerLevel level, BlockPos sourcePos, BlockState sourceState) {
        if (sourceState.isAir()) {
            return false;
        }

        for (Direction direction : Direction.values()) {
            BlockPos possibleDestination = sourcePos.relative(direction);
            if (!level.getBlockState(possibleDestination).is(Blocks.MOVING_PISTON)) {
                continue;
            }

            BlockEntity blockEntity = level.getBlockEntity(possibleDestination);
            if (!(blockEntity instanceof PistonMovingBlockEntity movingBlockEntity)) {
                continue;
            }
            if (movingBlockEntity.isSourcePiston()) {
                continue;
            }
            if (movingBlockEntity.getMovementDirection() != direction) {
                continue;
            }

            BlockState movedState = movingBlockEntity.getMovedState();
            if (movedState.isAir()) {
                continue;
            }
            if (movedState.getBlock() != sourceState.getBlock()) {
                continue;
            }
            return true;
        }

        return false;
    }

    public static boolean transferLikelyPistonCompletionDamage(ServerLevel level, BlockPos destinationPos, BlockState destinationState) {
        if (destinationState.isAir() || destinationState.getDestroySpeed(level, destinationPos) < 0.0F) {
            return false;
        }

        CannonBlockDamageSavedData data = getData(level);
        BlockPos bestSourcePos = null;
        int bestDamageState = 0;
        for (Direction direction : Direction.values()) {
            BlockPos sourceCandidate = destinationPos.relative(direction);
            int candidateState = data.getDamageState(sourceCandidate.asLong());
            if (candidateState <= 0) {
                continue;
            }

            BlockState candidateLiveState = level.getBlockState(sourceCandidate);
            if (!candidateLiveState.isAir() && !(candidateLiveState.getBlock() instanceof MovingPistonBlock)) {
                continue;
            }
            if (candidateState <= bestDamageState) {
                continue;
            }
            bestDamageState = candidateState;
            bestSourcePos = sourceCandidate.immutable();
        }

        if (bestSourcePos == null) {
            return false;
        }

        int carriedState = takeStoredDamageState(level, bestSourcePos);
        if (carriedState <= 0) {
            return false;
        }
        applyTransferredDamageState(level, destinationPos, destinationState, carriedState);
        return true;
    }

    public static void applyTransferredDamageState(ServerLevel level, BlockPos blockPos, BlockState expectedState, int transferredState) {
        int clampedState = clamp(transferredState, 0, MAX_DAMAGE_STATE);
        if (clampedState <= 0) {
            return;
        }

        BlockState currentState = level.getBlockState(blockPos);
        if (currentState.isAir() || currentState.getDestroySpeed(level, blockPos) < 0.0F) {
            return;
        }
        if (expectedState != null && currentState.getBlock() != expectedState.getBlock()) {
            return;
        }

        int existingState = getDamageState(level, blockPos);
        int mergedState = Math.max(existingState, clampedState);
        if (mergedState > existingState) {
            setDamageState(level, blockPos, mergedState);
        }
    }

    public static ImpactResult accumulateTransferredDamageState(ServerLevel level, BlockPos blockPos, BlockState expectedState,
                                                                int addedState, boolean dropOnBreak) {
        int clampedAdded = clamp(addedState, 0, MAX_DAMAGE_STATE);
        if (clampedAdded <= 0) {
            return new ImpactResult(false, getDamageState(level, blockPos));
        }

        BlockState currentState = level.getBlockState(blockPos);
        if (currentState.isAir() || currentState.getDestroySpeed(level, blockPos) < 0.0F) {
            return new ImpactResult(false, NO_DAMAGE_STATE);
        }
        if (expectedState != null && currentState.getBlock() != expectedState.getBlock()) {
            return new ImpactResult(false, NO_DAMAGE_STATE);
        }

        int existingState = getDamageState(level, blockPos);
        int nextState = clamp(existingState + clampedAdded, 0, MAX_DAMAGE_STATE);
        if (nextState >= MAX_DAMAGE_STATE) {
            clearDamage(level, blockPos);
            boolean broken = level.destroyBlock(blockPos, dropOnBreak, null);
            if (!broken) {
                int fallbackState = MAX_DAMAGE_STATE - 1;
                setDamageState(level, blockPos, fallbackState);
                return new ImpactResult(false, fallbackState);
            }
            return new ImpactResult(true, NO_DAMAGE_STATE);
        }

        setDamageState(level, blockPos, nextState);
        return new ImpactResult(false, nextState);
    }

    public static void moveDamageState(ServerLevel level, BlockPos fromPos, BlockPos toPos, BlockState expectedDestinationState) {
        if (fromPos.equals(toPos)) {
            return;
        }

        int carriedState = takeStoredDamageState(level, fromPos);
        if (carriedState <= 0) {
            return;
        }

        applyTransferredDamageState(level, toPos, expectedDestinationState, carriedState);
    }

    private static int computeDamageStateDelta(BlockState blockState, double impactPower, float resistance, float hardness) {
        double safeResistance = Math.max(0.0D, resistance);
        double safeHardness = Math.max(0.0D, hardness);

        // Tuned curve targets:
        // - sand-like blocks: instant break at speed 1
        // - dirt-like blocks: ~75% damage at speed 1
        // - stone-like blocks: start taking damage at speed 1, instant at speed 30
        // - obsidian-like blocks: damage starts at speed 5, instant at speed 80
        double baseDurability = 0.45D
                + (0.75D * Math.pow(safeHardness, 1.35D))
                + (0.18D * Math.pow(safeResistance, 0.60D))
                + (0.002D * safeHardness * safeResistance);

        // Falling blocks (sand/gravel/concrete powder-like) should stay more fragile.
        double materialFactor = blockState.getBlock() instanceof FallingBlock ? 1.0D : 1.47D;
        double durability = baseDurability * materialFactor;

        double normalizedImpact = impactPower / durability;
        int delta = clamp((int) Math.floor(normalizedImpact), 0, 15);
        if (delta <= 0 && impactPower >= MIN_IMPACT_FOR_ONE_DAMAGE_STATE) {
            return 1;
        }
        return delta;
    }

    private static int getDamageState(ServerLevel level, BlockPos blockPos) {
        BlockState liveState = level.getBlockState(blockPos);
        if (liveState.isAir() || liveState.getDestroySpeed(level, blockPos) < 0.0F) {
            clearDamage(level, blockPos);
            return 0;
        }

        CannonBlockDamageSavedData data = getData(level);
        long posLong = blockPos.asLong();
        int state = data.getDamageState(posLong);
        if (state <= 0) {
            return 0;
        }

        long now = level.getGameTime();
        long lastUpdateTick = data.getLastUpdateTick(posLong);
        if (lastUpdateTick < 0L) {
            data.setLastUpdateTick(posLong, now);
            return state;
        }

        long elapsedTicks = now - lastUpdateTick;
        if (elapsedTicks < DAMAGE_DECAY_INTERVAL_TICKS) {
            return state;
        }

        int decayAmount = (int) (elapsedTicks / DAMAGE_DECAY_INTERVAL_TICKS);
        int decayedState = clamp(state - decayAmount, 0, MAX_DAMAGE_STATE);
        if (decayedState <= 0) {
            clearDamage(level, blockPos);
            return 0;
        }

        long consumedTicks = (long) decayAmount * DAMAGE_DECAY_INTERVAL_TICKS;
        data.setDamageState(posLong, decayedState, lastUpdateTick + consumedTicks);
        syncDamageState(level, blockPos, decayedState);
        return decayedState;
    }

    private static void setDamageState(ServerLevel level, BlockPos blockPos, int damageState) {
        CannonBlockDamageSavedData data = getData(level);
        long posLong = blockPos.asLong();
        int clampedState = clamp(damageState, 0, MAX_DAMAGE_STATE);
        int previousState = data.getDamageState(posLong);
        data.setDamageState(posLong, clampedState, level.getGameTime());
        if (previousState != clampedState) {
            syncDamageState(level, blockPos, clampedState);
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static CannonBlockDamageSavedData getData(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                CannonBlockDamageSavedData::load,
                CannonBlockDamageSavedData::new,
                SAVE_DATA_ID
        );
    }

    private static void syncDamageState(ServerLevel level, BlockPos blockPos, int damageState) {
        CannonicalNetwork.sendBlockDamageSync(level, blockPos, damageState);
    }

    private static final class PendingSync {
        private int ticksUntilNextAttempt;
        private int attemptsRemaining;

        private PendingSync(int ticksUntilNextAttempt, int attemptsRemaining) {
            this.ticksUntilNextAttempt = ticksUntilNextAttempt;
            this.attemptsRemaining = attemptsRemaining;
        }
    }

    public record ImpactResult(boolean broken, int damageState) {
    }
}
