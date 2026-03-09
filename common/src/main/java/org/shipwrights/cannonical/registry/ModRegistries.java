package org.shipwrights.cannonical.registry;

public final class ModRegistries {
    private ModRegistries() {
    }

    public static void register() {
        ModBlocks.register();
        ModItems.register();
        ModEnchantments.register();
        ModBlockEntities.register();
        ModEntityTypes.register();
        ModCreativeTabs.register();
    }
}
