package org.shipwrights.cannonical.forge;

import org.shipwrights.cannonical.Cannonical;
import dev.architectury.platform.forge.EventBuses;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(Cannonical.MOD_ID)
public final class CannonicalForge {
    public CannonicalForge() {
        // Submit our event bus to let Architectury API register our content on the right time.
        EventBuses.registerModEventBus(Cannonical.MOD_ID, FMLJavaModLoadingContext.get().getModEventBus());

        // Run our common setup.
        Cannonical.init();
    }
}
