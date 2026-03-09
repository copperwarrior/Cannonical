package org.shipwrights.cannonical.content.projectile;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.shorts.Short2ByteMap;
import it.unimi.dsi.fastutil.shorts.Short2ByteOpenHashMap;
import it.unimi.dsi.fastutil.shorts.Short2LongOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

public final class CannonBlockDamageSavedData extends SavedData {
    private static final String SECTION_KEYS_TAG = "SectionKeys";
    private static final String LOCAL_INDICES_TAG = "LocalIndices";
    private static final String STATES_TAG = "States";
    private static final String LAST_UPDATE_TICKS_TAG = "LastUpdateTicks";
    private static final String LEGACY_POSITIONS_TAG = "DamagePositions";
    private static final String LEGACY_STATES_TAG = "DamageStates";
    private static final int MAX_DAMAGE_STATE = 15;
    private static final int NO_DAMAGE_STATE = -1;
    private static final long NO_LAST_UPDATE_TICK = -1L;

    private final Long2ObjectOpenHashMap<SectionDamageState> sections = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<LongOpenHashSet> sectionKeysByChunk = new Long2ObjectOpenHashMap<>();

    public static CannonBlockDamageSavedData load(CompoundTag tag) {
        CannonBlockDamageSavedData data = new CannonBlockDamageSavedData();

        long[] sectionKeys = tag.getLongArray(SECTION_KEYS_TAG);
        int[] localIndices = tag.getIntArray(LOCAL_INDICES_TAG);
        byte[] states = tag.getByteArray(STATES_TAG);
        long[] lastUpdateTicks = tag.getLongArray(LAST_UPDATE_TICKS_TAG);

        if (sectionKeys.length > 0 || localIndices.length > 0) {
            int len = Math.min(sectionKeys.length, Math.min(localIndices.length, states.length));
            for (int i = 0; i < len; i++) {
                int state = clamp(states[i], 0, MAX_DAMAGE_STATE);
                if (state <= 0) {
                    continue;
                }

                long sectionKey = sectionKeys[i];
                short localIndex = (short) (localIndices[i] & 0x0FFF);
                long updateTick = i < lastUpdateTicks.length ? lastUpdateTicks[i] : NO_LAST_UPDATE_TICK;
                data.putInternal(sectionKey, localIndex, (byte) state, updateTick);
            }
            return data;
        }

        long[] legacyPositions = tag.getLongArray(LEGACY_POSITIONS_TAG);
        byte[] legacyStates = tag.getByteArray(LEGACY_STATES_TAG);
        int len = Math.min(legacyPositions.length, legacyStates.length);
        for (int i = 0; i < len; i++) {
            int state = clamp(legacyStates[i], 0, MAX_DAMAGE_STATE);
            if (state <= 0) {
                continue;
            }
            long posLong = legacyPositions[i];
            long sectionKey = sectionKeyFromPosLong(posLong);
            short localIndex = localIndexFromPosLong(posLong);
            data.putInternal(sectionKey, localIndex, (byte) state, NO_LAST_UPDATE_TICK);
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        int totalEntries = 0;
        for (SectionDamageState section : this.sections.values()) {
            totalEntries += section.damageStates.size();
        }

        long[] sectionKeys = new long[totalEntries];
        int[] localIndices = new int[totalEntries];
        byte[] states = new byte[totalEntries];
        long[] lastUpdateTicks = new long[totalEntries];

        int i = 0;
        for (Long2ObjectMap.Entry<SectionDamageState> sectionEntry : this.sections.long2ObjectEntrySet()) {
            long sectionKey = sectionEntry.getLongKey();
            SectionDamageState section = sectionEntry.getValue();
            for (Short2ByteMap.Entry stateEntry : section.damageStates.short2ByteEntrySet()) {
                short localIndex = stateEntry.getShortKey();
                sectionKeys[i] = sectionKey;
                localIndices[i] = localIndex & 0x0FFF;
                states[i] = stateEntry.getByteValue();
                lastUpdateTicks[i] = section.lastUpdateTicks.get(localIndex);
                i++;
            }
        }

        tag.putLongArray(SECTION_KEYS_TAG, sectionKeys);
        tag.putIntArray(LOCAL_INDICES_TAG, localIndices);
        tag.putByteArray(STATES_TAG, states);
        tag.putLongArray(LAST_UPDATE_TICKS_TAG, lastUpdateTicks);
        return tag;
    }

    public int getDamageState(long posLong) {
        SectionDamageState section = this.sections.get(sectionKeyFromPosLong(posLong));
        if (section == null) {
            return 0;
        }

        int state = section.damageStates.get(localIndexFromPosLong(posLong));
        return state == NO_DAMAGE_STATE ? 0 : state;
    }

    public long getLastUpdateTick(long posLong) {
        SectionDamageState section = this.sections.get(sectionKeyFromPosLong(posLong));
        if (section == null) {
            return NO_LAST_UPDATE_TICK;
        }
        return section.lastUpdateTicks.get(localIndexFromPosLong(posLong));
    }

    public void setLastUpdateTick(long posLong, long lastUpdateTick) {
        long sectionKey = sectionKeyFromPosLong(posLong);
        SectionDamageState section = this.sections.get(sectionKey);
        if (section == null) {
            return;
        }

        short localIndex = localIndexFromPosLong(posLong);
        if (!section.damageStates.containsKey(localIndex)) {
            return;
        }

        long previous = section.lastUpdateTicks.put(localIndex, lastUpdateTick);
        if (previous != lastUpdateTick) {
            this.setDirty();
        }
    }

    public void setDamageState(long posLong, int state) {
        this.setDamageState(posLong, state, NO_LAST_UPDATE_TICK);
    }

    public void setDamageState(long posLong, int state, long lastUpdateTick) {
        int clamped = clamp(state, 0, MAX_DAMAGE_STATE);
        if (clamped <= 0) {
            this.removeDamageState(posLong);
            return;
        }

        long sectionKey = sectionKeyFromPosLong(posLong);
        short localIndex = localIndexFromPosLong(posLong);
        SectionDamageState section = this.sections.get(sectionKey);
        if (section == null) {
            section = new SectionDamageState();
            this.sections.put(sectionKey, section);
            indexSection(sectionKey);
        }

        int previousState = section.damageStates.put(localIndex, (byte) clamped);
        long previousTick = section.lastUpdateTicks.put(localIndex, lastUpdateTick);
        if (previousState != clamped || previousTick != lastUpdateTick) {
            this.setDirty();
        }
    }

    public int removeDamageState(long posLong) {
        long sectionKey = sectionKeyFromPosLong(posLong);
        SectionDamageState section = this.sections.get(sectionKey);
        if (section == null) {
            return NO_DAMAGE_STATE;
        }

        short localIndex = localIndexFromPosLong(posLong);
        int previous = section.damageStates.remove(localIndex);
        long previousTick = section.lastUpdateTicks.remove(localIndex);
        if (previous != NO_DAMAGE_STATE || previousTick != NO_LAST_UPDATE_TICK) {
            this.setDirty();
        }

        if (section.damageStates.isEmpty()) {
            this.sections.remove(sectionKey);
            unindexSection(sectionKey);
        }

        return previous;
    }

    public void forEachSectionInChunk(int chunkX, int chunkZ, SectionSnapshotConsumer consumer) {
        LongOpenHashSet sectionKeys = this.sectionKeysByChunk.get(chunkKey(chunkX, chunkZ));
        if (sectionKeys == null || sectionKeys.isEmpty()) {
            return;
        }

        long[] keys = sectionKeys.toLongArray();
        for (long sectionKey : keys) {
            SectionDamageState section = this.sections.get(sectionKey);
            if (section == null || section.damageStates.isEmpty()) {
                continue;
            }
            consumer.accept(sectionKey, section.snapshotDamageStates());
        }
    }

    private void putInternal(long sectionKey, short localIndex, byte damageState, long updateTick) {
        SectionDamageState section = this.sections.get(sectionKey);
        if (section == null) {
            section = new SectionDamageState();
            this.sections.put(sectionKey, section);
            indexSection(sectionKey);
        }
        section.damageStates.put(localIndex, damageState);
        section.lastUpdateTicks.put(localIndex, updateTick);
    }

    private void indexSection(long sectionKey) {
        long chunkKey = chunkKey(SectionPos.x(sectionKey), SectionPos.z(sectionKey));
        LongOpenHashSet keys = this.sectionKeysByChunk.get(chunkKey);
        if (keys == null) {
            keys = new LongOpenHashSet();
            this.sectionKeysByChunk.put(chunkKey, keys);
        }
        keys.add(sectionKey);
    }

    private void unindexSection(long sectionKey) {
        long chunkKey = chunkKey(SectionPos.x(sectionKey), SectionPos.z(sectionKey));
        LongOpenHashSet keys = this.sectionKeysByChunk.get(chunkKey);
        if (keys == null) {
            return;
        }
        keys.remove(sectionKey);
        if (keys.isEmpty()) {
            this.sectionKeysByChunk.remove(chunkKey);
        }
    }

    private static long sectionKeyFromPosLong(long posLong) {
        return SectionPos.asLong(
                BlockPos.getX(posLong) >> 4,
                BlockPos.getY(posLong) >> 4,
                BlockPos.getZ(posLong) >> 4
        );
    }

    private static short localIndexFromPosLong(long posLong) {
        int x = BlockPos.getX(posLong);
        int y = BlockPos.getY(posLong);
        int z = BlockPos.getZ(posLong);
        return (short) (((y & 15) << 8) | ((z & 15) << 4) | (x & 15));
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class SectionDamageState {
        private final Short2ByteOpenHashMap damageStates = new Short2ByteOpenHashMap();
        private final Short2LongOpenHashMap lastUpdateTicks = new Short2LongOpenHashMap();

        private SectionDamageState() {
            this.damageStates.defaultReturnValue((byte) NO_DAMAGE_STATE);
            this.lastUpdateTicks.defaultReturnValue(NO_LAST_UPDATE_TICK);
        }

        private Short2ByteOpenHashMap snapshotDamageStates() {
            return new Short2ByteOpenHashMap(this.damageStates);
        }
    }

    @FunctionalInterface
    public interface SectionSnapshotConsumer {
        void accept(long sectionKey, Short2ByteOpenHashMap states);
    }
}
