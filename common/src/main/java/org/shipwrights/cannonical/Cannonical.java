package org.shipwrights.cannonical;

import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import org.shipwrights.cannonical.command.CannonballCommand;
import org.shipwrights.cannonical.command.GunpowderBarrelPowerCommand;
import org.shipwrights.cannonical.content.item.MalletItem;
import org.shipwrights.cannonical.content.projectile.CannonBlockDamageSystem;
import org.shipwrights.cannonical.network.CannonicalNetwork;
import org.shipwrights.cannonical.registry.ModRegistries;

public final class Cannonical {
    public static final String MOD_ID = "cannonical";

    public static void init() {
        CannonicalNetwork.init();
        MalletItem.registerEvents();
        CannonballCommand.register();
        GunpowderBarrelPowerCommand.register();
        PlayerEvent.PLAYER_JOIN.register(CannonBlockDamageSystem::queueConnectSync);
        PlayerEvent.CHANGE_DIMENSION.register((player, oldLevel, newLevel) -> CannonBlockDamageSystem.queueConnectSync(player));
        PlayerEvent.PLAYER_RESPAWN.register((newPlayer, conqueredEnd) -> CannonBlockDamageSystem.queueConnectSync(newPlayer));
        PlayerEvent.PLAYER_QUIT.register(CannonBlockDamageSystem::clearQueuedSync);
        TickEvent.SERVER_POST.register(CannonBlockDamageSystem::tickQueuedSyncs);
        ModRegistries.register();
    }
}
