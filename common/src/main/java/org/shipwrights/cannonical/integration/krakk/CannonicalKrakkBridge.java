package org.shipwrights.cannonical.integration.krakk;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.shorts.Short2ByteOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.shipwrights.cannonical.Cannonical;
import org.shipwrights.cannonical.client.CannonBlockDamageOverlayState;
import org.shipwrights.cannonical.content.explosive.GunpowderBarrelBlock;
import org.shipwrights.cannonical.registry.ModBlocks;
import org.shipwrights.krakk.api.KrakkApi;
import org.shipwrights.krakk.api.client.KrakkClientOverlayApi;
import org.shipwrights.krakk.network.KrakkBlockDamageNetwork;
import org.shipwrights.krakk.runtime.explosion.KrakkExplosionRuntime;

public final class CannonicalKrakkBridge {
    private CannonicalKrakkBridge() {
    }

    public static void init() {
        KrakkApi.setClientOverlayApi(new CannonicalClientOverlayApi());
        KrakkApi.setNetworkApi(new KrakkBlockDamageNetwork(Cannonical.MOD_ID));
        KrakkExplosionRuntime.setSpecialBlockHandler(CannonicalKrakkBridge::handleSpecialExplosionBlock);
    }

    private static boolean handleSpecialExplosionBlock(ServerLevel level, BlockPos blockPos, BlockState blockState,
                                                       Entity source, LivingEntity owner) {
        if (!blockState.is(ModBlocks.GUNPOWDER_BARREL.get())) {
            return false;
        }

        int chainFuse = level.random.nextInt(15) + 10;
        GunpowderBarrelBlock.prime(level, blockPos, owner, chainFuse);
        level.removeBlock(blockPos, false);
        return true;
    }

    private static final class CannonicalClientOverlayApi implements KrakkClientOverlayApi {
        @Override
        public void resetClientState() {
            CannonBlockDamageOverlayState.resetClientState();
        }

        @Override
        public void applyDamage(ResourceLocation dimensionId, long posLong, int damageState) {
            CannonBlockDamageOverlayState.apply(dimensionId, posLong, damageState);
        }

        @Override
        public void applySection(ResourceLocation dimensionId, int sectionX, int sectionY, int sectionZ,
                                 Short2ByteOpenHashMap sectionStates) {
            CannonBlockDamageOverlayState.applySection(dimensionId, sectionX, sectionY, sectionZ, sectionStates);
        }

        @Override
        public void clearChunk(ResourceLocation dimensionId, int chunkX, int chunkZ) {
            CannonBlockDamageOverlayState.clearChunk(dimensionId, chunkX, chunkZ);
        }

        @Override
        public float getMiningBaseline(ResourceLocation dimensionId, long posLong) {
            return CannonBlockDamageOverlayState.getMiningProgressFraction(dimensionId, posLong);
        }

        @Override
        public long[] consumeDirtySections(ResourceLocation dimensionId) {
            return CannonBlockDamageOverlayState.consumeDirtySections(dimensionId);
        }

        @Override
        public Long2ByteOpenHashMap snapshotSection(ResourceLocation dimensionId, long sectionKey) {
            return CannonBlockDamageOverlayState.snapshotSection(dimensionId, sectionKey);
        }
    }
}
