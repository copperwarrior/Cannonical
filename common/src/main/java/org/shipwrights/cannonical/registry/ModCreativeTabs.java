package org.shipwrights.cannonical.registry;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import org.shipwrights.cannonical.Cannonical;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Cannonical.MOD_ID, Registries.CREATIVE_MODE_TAB);

    public static final RegistrySupplier<CreativeModeTab> MAIN =
            CREATIVE_MODE_TABS.register("main", () -> CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                    .title(Component.translatable("itemGroup.cannonical.main"))
                    .icon(() -> new ItemStack(ModBlocks.TWENTY_FOUR_POUNDER_CANNON.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModBlocks.TWENTY_FOUR_POUNDER_CANNON.get());
                        output.accept(ModBlocks.GUNPOWDER_BARREL.get());
                        output.accept(ModItems.CANNONBALL.get());
                        output.accept(ModItems.MALLET.get());
                        output.accept(ModItems.STONE_MALLET.get());
                        output.accept(ModItems.COPPER_MALLET.get());
                        output.accept(ModItems.IRON_MALLET.get());
                        output.accept(ModItems.GOLD_MALLET.get());
                        output.accept(ModItems.DIAMOND_MALLET.get());
                        output.accept(ModItems.NETHERITE_MALLET.get());
                    })
                    .build());

    private ModCreativeTabs() {
    }

    public static void register() {
        CREATIVE_MODE_TABS.register();
    }
}
