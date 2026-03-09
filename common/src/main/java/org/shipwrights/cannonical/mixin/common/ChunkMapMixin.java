package org.shipwrights.cannonical.mixin.common;

import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.apache.commons.lang3.mutable.MutableObject;
import org.shipwrights.cannonical.content.projectile.CannonBlockDamageSystem;
import org.shipwrights.cannonical.network.CannonicalNetwork;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin {
    @Shadow
    @Final
    private ServerLevel level;

    @Inject(method = "updateChunkTracking", at = @At("TAIL"))
    private void cannonical$syncDamageOnChunkTrackUpdate(ServerPlayer player, ChunkPos chunkPos,
                                                         MutableObject<ClientboundLevelChunkWithLightPacket> packetCache,
                                                         boolean wasLoaded, boolean load, CallbackInfo ci) {
        if (load && !wasLoaded) {
            CannonBlockDamageSystem.syncChunkColumnToPlayer(player, this.level, chunkPos.x, chunkPos.z);
            return;
        }

        if (!load && wasLoaded) {
            CannonicalNetwork.sendBlockDamageChunkUnload(player, this.level.dimension().location(), chunkPos.x, chunkPos.z);
        }
    }
}
