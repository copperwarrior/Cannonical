package org.shipwrights.cannonical.network;

import dev.architectury.networking.NetworkManager;
import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.shipwrights.cannonical.Cannonical;
import org.shipwrights.cannonical.content.projectile.CannonballProjectileEntity;
import org.shipwrights.krakk.api.KrakkApi;

public final class CannonicalNetwork {
    private static final ResourceLocation CANNONBALL_PRECISE_SYNC_PACKET =
            new ResourceLocation(Cannonical.MOD_ID, "cannonball_precise_sync");
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
                if (context.getPlayer().level() == null) {
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

        KrakkApi.network().initClientReceivers();
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

}
