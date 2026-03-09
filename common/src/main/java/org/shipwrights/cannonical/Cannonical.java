package org.shipwrights.cannonical;

import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import org.shipwrights.cannonical.command.CannonballCommand;
import org.shipwrights.cannonical.content.item.MalletItem;
import org.shipwrights.cannonical.integration.krakk.CannonicalKrakkBridge;
import org.shipwrights.cannonical.network.CannonicalNetwork;
import org.shipwrights.cannonical.registry.ModRegistries;
import org.shipwrights.krakk.Krakk;
import org.shipwrights.krakk.api.KrakkApi;

public final class Cannonical {
    public static final String MOD_ID = "cannonical";

    public static void init() {
        Krakk.init();
        CannonicalKrakkBridge.init();
        CannonicalNetwork.init();
        MalletItem.registerEvents();
        CannonballCommand.register();
        PlayerEvent.PLAYER_JOIN.register(player -> KrakkApi.damage().queuePlayerSync(player));
        PlayerEvent.CHANGE_DIMENSION.register((player, oldLevel, newLevel) -> KrakkApi.damage().queuePlayerSync(player));
        PlayerEvent.PLAYER_RESPAWN.register((newPlayer, conqueredEnd) -> KrakkApi.damage().queuePlayerSync(newPlayer));
        PlayerEvent.PLAYER_QUIT.register(player -> KrakkApi.damage().clearQueuedPlayerSync(player));
        TickEvent.SERVER_POST.register(server -> KrakkApi.damage().tickQueuedSyncs(server));
        ModRegistries.register();
    }
}
