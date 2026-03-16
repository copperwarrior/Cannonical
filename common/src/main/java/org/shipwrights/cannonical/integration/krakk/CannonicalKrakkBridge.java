package org.shipwrights.cannonical.integration.krakk;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.shipwrights.cannonical.content.explosive.GunpowderBarrelBlock;
import org.shipwrights.cannonical.registry.ModBlocks;
import org.shipwrights.krakk.runtime.damage.KrakkDamageRuntime;
import org.shipwrights.krakk.runtime.explosion.KrakkExplosionRuntime;

public final class CannonicalKrakkBridge {
    private CannonicalKrakkBridge() {
    }

    public static void init() {
        CannonicalDamageBlockConversions.init();
        KrakkDamageRuntime.setDamageStateConversionHandler(CannonicalDamageBlockConversions::applyConversionForDamageState);
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
}
