package org.shipwrights.cannonical;

import org.shipwrights.cannonical.command.CannonballCommand;
import org.shipwrights.cannonical.content.item.MalletItem;
import org.shipwrights.cannonical.integration.krakk.CannonicalKrakkBridge;
import org.shipwrights.cannonical.network.CannonicalNetwork;
import org.shipwrights.cannonical.registry.ModRegistries;

public final class Cannonical {
    public static final String MOD_ID = "cannonical";

    public static void init() {
        CannonicalKrakkBridge.init();
        CannonicalNetwork.init();
        MalletItem.registerEvents();
        CannonballCommand.register();
        ModRegistries.register();
    }
}
