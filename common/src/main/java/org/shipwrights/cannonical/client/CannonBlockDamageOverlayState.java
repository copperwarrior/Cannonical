package org.shipwrights.cannonical.client;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.shorts.Short2ByteMap;
import it.unimi.dsi.fastutil.shorts.Short2ByteOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;

public final class CannonBlockDamageOverlayState {
    private static final Object LOCK = new Object();
    private static final Long2ByteOpenHashMap DAMAGE_STATES = new Long2ByteOpenHashMap();
    private static final Long2ObjectOpenHashMap<LongOpenHashSet> DAMAGE_POSITIONS_BY_CHUNK = new Long2ObjectOpenHashMap<>();
    private static final Long2ObjectOpenHashMap<LongOpenHashSet> DAMAGE_POSITIONS_BY_SECTION = new Long2ObjectOpenHashMap<>();

    private static ResourceLocation activeDimensionId = null;

    private CannonBlockDamageOverlayState() {
    }

    public static void apply(ResourceLocation dimensionId, long posLong, int damageState) {
        synchronized (LOCK) {
            if (!dimensionId.equals(activeDimensionId)) {
                clearAll();
                activeDimensionId = dimensionId;
            }

            int clampedState = Math.max(0, Math.min(15, damageState));
            if (clampedState <= 0) {
                removeDamageState(posLong);
                return;
            }

            putDamageState(posLong, (byte) clampedState);
        }
    }

    public static Long2ByteOpenHashMap snapshot(ResourceLocation dimensionId) {
        synchronized (LOCK) {
            if (activeDimensionId == null || !activeDimensionId.equals(dimensionId)) {
                return new Long2ByteOpenHashMap();
            }
            return new Long2ByteOpenHashMap(DAMAGE_STATES);
        }
    }

    public static void forEachInChunkRange(ResourceLocation dimensionId, int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ,
                                           DamageStateConsumer consumer) {
        synchronized (LOCK) {
            if (activeDimensionId == null || !activeDimensionId.equals(dimensionId)) {
                return;
            }

            int fromChunkX = Math.min(minChunkX, maxChunkX);
            int toChunkX = Math.max(minChunkX, maxChunkX);
            int fromChunkZ = Math.min(minChunkZ, maxChunkZ);
            int toChunkZ = Math.max(minChunkZ, maxChunkZ);

            for (int chunkX = fromChunkX; chunkX <= toChunkX; chunkX++) {
                for (int chunkZ = fromChunkZ; chunkZ <= toChunkZ; chunkZ++) {
                    LongOpenHashSet positions = DAMAGE_POSITIONS_BY_CHUNK.get(toChunkKey(chunkX, chunkZ));
                    if (positions == null || positions.isEmpty()) {
                        continue;
                    }

                    LongIterator iterator = positions.iterator();
                    while (iterator.hasNext()) {
                        long posLong = iterator.nextLong();
                        int damageState = DAMAGE_STATES.get(posLong);
                        if (damageState > 0) {
                            consumer.accept(posLong, (byte) damageState);
                        }
                    }
                }
            }
        }
    }

    public static void applySection(ResourceLocation dimensionId, int sectionX, int sectionY, int sectionZ, Short2ByteOpenHashMap sectionStates) {
        synchronized (LOCK) {
            if (!dimensionId.equals(activeDimensionId)) {
                clearAll();
                activeDimensionId = dimensionId;
            }

            long sectionKey = SectionPos.asLong(sectionX, sectionY, sectionZ);
            clearSection(sectionKey);

            for (Short2ByteMap.Entry entry : sectionStates.short2ByteEntrySet()) {
                int clampedState = Math.max(0, Math.min(15, entry.getByteValue()));
                if (clampedState <= 0) {
                    continue;
                }

                short localIndex = entry.getShortKey();
                int localX = localIndex & 15;
                int localZ = (localIndex >> 4) & 15;
                int localY = (localIndex >> 8) & 15;

                int blockX = (sectionX << 4) | localX;
                int blockY = (sectionY << 4) | localY;
                int blockZ = (sectionZ << 4) | localZ;
                long posLong = BlockPos.asLong(blockX, blockY, blockZ);
                putDamageState(posLong, (byte) clampedState);
            }
        }
    }

    public static void clearChunk(ResourceLocation dimensionId, int chunkX, int chunkZ) {
        synchronized (LOCK) {
            if (activeDimensionId == null || !activeDimensionId.equals(dimensionId)) {
                return;
            }

            long chunkKey = toChunkKey(chunkX, chunkZ);
            LongOpenHashSet positions = DAMAGE_POSITIONS_BY_CHUNK.get(chunkKey);
            if (positions == null || positions.isEmpty()) {
                return;
            }

            long[] posLongs = positions.toLongArray();
            for (long posLong : posLongs) {
                removeDamageState(posLong);
            }
        }
    }

    private static void clearAll() {
        DAMAGE_STATES.clear();
        DAMAGE_POSITIONS_BY_CHUNK.clear();
        DAMAGE_POSITIONS_BY_SECTION.clear();
    }

    private static void putDamageState(long posLong, byte damageState) {
        byte previous = DAMAGE_STATES.put(posLong, damageState);
        if (previous > 0) {
            return;
        }

        long chunkKey = toChunkKeyFromPos(posLong);
        LongOpenHashSet chunkPositions = DAMAGE_POSITIONS_BY_CHUNK.get(chunkKey);
        if (chunkPositions == null) {
            chunkPositions = new LongOpenHashSet();
            DAMAGE_POSITIONS_BY_CHUNK.put(chunkKey, chunkPositions);
        }
        chunkPositions.add(posLong);

        long sectionKey = toSectionKeyFromPos(posLong);
        LongOpenHashSet sectionPositions = DAMAGE_POSITIONS_BY_SECTION.get(sectionKey);
        if (sectionPositions == null) {
            sectionPositions = new LongOpenHashSet();
            DAMAGE_POSITIONS_BY_SECTION.put(sectionKey, sectionPositions);
        }
        sectionPositions.add(posLong);
    }

    private static void removeDamageState(long posLong) {
        byte previous = DAMAGE_STATES.remove(posLong);
        if (previous <= 0) {
            return;
        }

        long chunkKey = toChunkKeyFromPos(posLong);
        LongOpenHashSet chunkPositions = DAMAGE_POSITIONS_BY_CHUNK.get(chunkKey);
        if (chunkPositions == null) {
            return;
        }

        chunkPositions.remove(posLong);
        if (chunkPositions.isEmpty()) {
            DAMAGE_POSITIONS_BY_CHUNK.remove(chunkKey);
        }

        long sectionKey = toSectionKeyFromPos(posLong);
        LongOpenHashSet sectionPositions = DAMAGE_POSITIONS_BY_SECTION.get(sectionKey);
        if (sectionPositions == null) {
            return;
        }
        sectionPositions.remove(posLong);
        if (sectionPositions.isEmpty()) {
            DAMAGE_POSITIONS_BY_SECTION.remove(sectionKey);
        }
    }

    private static void clearSection(long sectionKey) {
        LongOpenHashSet sectionPositions = DAMAGE_POSITIONS_BY_SECTION.get(sectionKey);
        if (sectionPositions == null || sectionPositions.isEmpty()) {
            return;
        }

        long[] posLongs = sectionPositions.toLongArray();
        for (long posLong : posLongs) {
            removeDamageState(posLong);
        }
    }

    private static long toChunkKeyFromPos(long posLong) {
        return toChunkKey(BlockPos.getX(posLong) >> 4, BlockPos.getZ(posLong) >> 4);
    }

    private static long toSectionKeyFromPos(long posLong) {
        return SectionPos.asLong(BlockPos.getX(posLong) >> 4, BlockPos.getY(posLong) >> 4, BlockPos.getZ(posLong) >> 4);
    }

    private static long toChunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL);
    }

    @FunctionalInterface
    public interface DamageStateConsumer {
        void accept(long posLong, byte damageState);
    }
}
