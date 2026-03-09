package org.shipwrights.cannonical.registry;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.enchantment.Enchantment;
import org.shipwrights.cannonical.Cannonical;
import org.shipwrights.cannonical.content.enchantment.CarpenterEnchantment;

public final class ModEnchantments {
    public static final DeferredRegister<Enchantment> ENCHANTMENTS =
            DeferredRegister.create(Cannonical.MOD_ID, Registries.ENCHANTMENT);

    public static final RegistrySupplier<Enchantment> CARPENTER =
            ENCHANTMENTS.register("carpenter", CarpenterEnchantment::new);

    private ModEnchantments() {
    }

    public static void register() {
        ENCHANTMENTS.register();
    }
}
