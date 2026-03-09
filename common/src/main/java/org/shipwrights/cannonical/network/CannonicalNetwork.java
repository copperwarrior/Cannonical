package org.shipwrights.cannonical.network;

import dev.architectury.networking.NetworkManager;
import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import it.unimi.dsi.fastutil.shorts.Short2ByteMap;
import it.unimi.dsi.fastutil.shorts.Short2ByteOpenHashMap;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.shipwrights.cannonical.Cannonical;
import org.shipwrights.cannonical.client.CannonBlockDamageOverlayState;
import org.shipwrights.cannonical.content.projectile.CannonballProjectileEntity;

public final class CannonicalNetwork {
    private static final ResourceLocation CANNONBALL_PRECISE_SYNC_PACKET =
            new ResourceLocation(Cannonical.MOD_ID, "cannonball_precise_sync");
    private static final ResourceLocation BLOCK_DAMAGE_SYNC_PACKET =
            new ResourceLocation(Cannonical.MOD_ID, "block_damage_sync");
    private static final ResourceLocation BLOCK_DAMAGE_SECTION_PACKET =
            new ResourceLocation(Cannonical.MOD_ID, "block_damage_section");
    private static final ResourceLocation BLOCK_DAMAGE_CHUNK_UNLOAD_PACKET =
            new ResourceLocation(Cannonical.MOD_ID, "block_damage_chunk_unload");
    private static final double CANNONBALL_SYNC_MAX_DISTANCE_SQR = 256.0D * 256.0D;

    private CannonicalNetwork() {
    }

    public static void init() {
        if (Platform.getEnvironment() != Env.CLIENT) {
            return;
        }

        NetworkManager.registerReceiver(NetworkManager.s2c(), CANNONBALL_PRECISE_SYNC_PACKET, (buf, context) -> {
            int entityId = buf.readVarInt();
            int sequence = buf.readVarInt();
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            double vx = buf.readDouble();
            double vy = buf.readDouble();
            double vz = buf.readDouble();
            float yRot = buf.readFloat();
            float xRot = buf.readFloat();
            boolean forcePosition = buf.readBoolean();

            context.queue(() -> {
                if (context.getPlayer() == null) {
                    return;
                }
                Level clientLevel = context.getPlayer().level();

                Entity entity = clientLevel.getEntity(entityId);
                if (entity instanceof CannonballProjectileEntity cannonball) {
                    cannonball.applyPreciseSyncFromServer(
                            sequence,
                            new Vec3(x, y, z),
                            new Vec3(vx, vy, vz),
                            yRot,
                            xRot,
                            forcePosition
                    );
                }
            });
        });

        NetworkManager.registerReceiver(NetworkManager.s2c(), BLOCK_DAMAGE_SYNC_PACKET, (buf, context) -> {
            ResourceLocation dimensionId = buf.readResourceLocation();
            long posLong = buf.readLong();
            int damageState = buf.readByte();

            context.queue(() -> {
                if (context.getPlayer() == null) {
                    return;
                }
                if (context.getPlayer().level() != null
                        && !context.getPlayer().level().dimension().location().equals(dimensionId)) {
                    return;
                }
                CannonBlockDamageOverlayState.apply(dimensionId, posLong, damageState);
            });
        });

        NetworkManager.registerReceiver(NetworkManager.s2c(), BLOCK_DAMAGE_SECTION_PACKET, (buf, context) -> {
            ResourceLocation dimensionId = buf.readResourceLocation();
            int sectionX = buf.readVarInt();
            int sectionY = buf.readVarInt();
            int sectionZ = buf.readVarInt();
            int size = buf.readVarInt();
            Short2ByteOpenHashMap sectionStates = new Short2ByteOpenHashMap(Math.max(0, size));

            for (int i = 0; i < size; i++) {
                short localIndex = buf.readShort();
                int damageState = buf.readByte();
                int clampedState = clampDamageState(damageState);
                if (clampedState > 0) {
                    sectionStates.put((short) (localIndex & 0x0FFF), (byte) clampedState);
                }
            }

            context.queue(() -> {
                if (context.getPlayer() == null) {
                    return;
                }
                if (context.getPlayer().level() != null
                        && !context.getPlayer().level().dimension().location().equals(dimensionId)) {
                    return;
                }
                CannonBlockDamageOverlayState.applySection(dimensionId, sectionX, sectionY, sectionZ, sectionStates);
            });
        });

        NetworkManager.registerReceiver(NetworkManager.s2c(), BLOCK_DAMAGE_CHUNK_UNLOAD_PACKET, (buf, context) -> {
            ResourceLocation dimensionId = buf.readResourceLocation();
            int chunkX = buf.readVarInt();
            int chunkZ = buf.readVarInt();

            context.queue(() -> {
                if (context.getPlayer() == null) {
                    return;
                }
                if (context.getPlayer().level() != null
                        && !context.getPlayer().level().dimension().location().equals(dimensionId)) {
                    return;
                }
                CannonBlockDamageOverlayState.clearChunk(dimensionId, chunkX, chunkZ);
            });
        });

    }

    public static void sendCannonballPreciseSync(ServerLevel level, CannonballProjectileEntity cannonball, int sequence, boolean forcePosition) {
        Vec3 position = cannonball.position();
        Vec3 velocity = cannonball.getDeltaMovement();
        float yRot = cannonball.getYRot();
        float xRot = cannonball.getXRot();

        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(cannonball) > CANNONBALL_SYNC_MAX_DISTANCE_SQR) {
                continue;
            }

            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            buf.writeVarInt(cannonball.getId());
            buf.writeVarInt(sequence);
            buf.writeDouble(position.x);
            buf.writeDouble(position.y);
            buf.writeDouble(position.z);
            buf.writeDouble(velocity.x);
            buf.writeDouble(velocity.y);
            buf.writeDouble(velocity.z);
            buf.writeFloat(yRot);
            buf.writeFloat(xRot);
            buf.writeBoolean(forcePosition);
            NetworkManager.sendToPlayer(player, CANNONBALL_PRECISE_SYNC_PACKET, buf);
        }
    }

    public static void sendBlockDamageSync(ServerLevel level, BlockPos blockPos, int damageState) {
        int clampedState = clampDamageState(damageState);
        ResourceLocation dimensionId = level.dimension().location();
        ChunkPos targetChunk = new ChunkPos(blockPos);
        int viewDistance = level.getServer().getPlayerList().getViewDistance() + 1;

        for (ServerPlayer player : level.players()) {
            if (player.level() != level) {
                continue;
            }
            ChunkPos playerChunk = player.chunkPosition();
            if (Math.abs(playerChunk.x - targetChunk.x) > viewDistance || Math.abs(playerChunk.z - targetChunk.z) > viewDistance) {
                continue;
            }

            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            buf.writeResourceLocation(dimensionId);
            buf.writeLong(blockPos.asLong());
            buf.writeByte(clampedState);
            NetworkManager.sendToPlayer(player, BLOCK_DAMAGE_SYNC_PACKET, buf);
        }
    }

    public static void sendBlockDamageSectionSnapshot(ServerPlayer player, ResourceLocation dimensionId,
                                                      int sectionX, int sectionY, int sectionZ,
                                                      Short2ByteOpenHashMap states) {
        if (states.isEmpty()) {
            return;
        }

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeResourceLocation(dimensionId);
        buf.writeVarInt(sectionX);
        buf.writeVarInt(sectionY);
        buf.writeVarInt(sectionZ);
        buf.writeVarInt(states.size());

        for (Short2ByteMap.Entry entry : states.short2ByteEntrySet()) {
            int clampedState = clampDamageState(entry.getByteValue());
            buf.writeShort(entry.getShortKey() & 0x0FFF);
            buf.writeByte(clampedState);
        }

        NetworkManager.sendToPlayer(player, BLOCK_DAMAGE_SECTION_PACKET, buf);
    }

    public static void sendBlockDamageChunkUnload(ServerPlayer player, ResourceLocation dimensionId, int chunkX, int chunkZ) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeResourceLocation(dimensionId);
        buf.writeVarInt(chunkX);
        buf.writeVarInt(chunkZ);
        NetworkManager.sendToPlayer(player, BLOCK_DAMAGE_CHUNK_UNLOAD_PACKET, buf);
    }

    private static int clampDamageState(int damageState) {
        return Math.max(0, Math.min(15, damageState));
    }
}
