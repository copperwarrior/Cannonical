package org.shipwrights.cannonical.registry;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.shipwrights.cannonical.Cannonical;
import org.shipwrights.cannonical.content.cannon.TwentyFourPounderCannonBlock;
import org.shipwrights.cannonical.content.explosive.GunpowderBarrelBlock;

import java.util.function.Supplier;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Cannonical.MOD_ID, Registries.BLOCK);

    public static final RegistrySupplier<TwentyFourPounderCannonBlock> TWENTY_FOUR_POUNDER_CANNON =
            registerWithItem("twenty_four_pounder_cannon",
                    () -> new TwentyFourPounderCannonBlock(BlockBehaviour.Properties.of()
                            .strength(4.0F, 8.0F)
                            .sound(SoundType.METAL)
                            .noOcclusion()));

    public static final RegistrySupplier<GunpowderBarrelBlock> GUNPOWDER_BARREL =
            registerWithItem("gunpowder_barrel",
                    () -> new GunpowderBarrelBlock(BlockBehaviour.Properties.copy(Blocks.TNT)));

    private ModBlocks() {
    }

    public static void register() {
        BLOCKS.register();
    }

    private static <T extends Block> RegistrySupplier<T> registerWithItem(String name, Supplier<T> blockSupplier) {
        RegistrySupplier<T> block = BLOCKS.register(name, blockSupplier);
        ModItems.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
        return block;
    }
}
