package org.shipwrights.cannonical.registry;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.shipwrights.cannonical.Cannonical;
import org.shipwrights.cannonical.content.cannon.TwentyFourPounderCannonBlockEntity;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Cannonical.MOD_ID, Registries.BLOCK_ENTITY_TYPE);

    public static final RegistrySupplier<BlockEntityType<TwentyFourPounderCannonBlockEntity>> TWENTY_FOUR_POUNDER_CANNON =
            BLOCK_ENTITY_TYPES.register("twenty_four_pounder_cannon",
                    () -> BlockEntityType.Builder.of(TwentyFourPounderCannonBlockEntity::new,
                            ModBlocks.TWENTY_FOUR_POUNDER_CANNON.get()).build(null));

    private ModBlockEntities() {
    }

    public static void register() {
        BLOCK_ENTITY_TYPES.register();
    }
}
