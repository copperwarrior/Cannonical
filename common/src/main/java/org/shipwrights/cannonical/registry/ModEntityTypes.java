package org.shipwrights.cannonical.registry;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import org.shipwrights.cannonical.content.explosive.GunpowderBarrelPrimedEntity;
import org.shipwrights.cannonical.Cannonical;
import org.shipwrights.cannonical.content.projectile.CannonballProjectileEntity;

public final class ModEntityTypes {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Cannonical.MOD_ID, Registries.ENTITY_TYPE);

    public static final RegistrySupplier<EntityType<CannonballProjectileEntity>> CANNONBALL_PROJECTILE =
            ENTITY_TYPES.register("cannonball_projectile", () -> EntityType.Builder
                    .<CannonballProjectileEntity>of(CannonballProjectileEntity::new, MobCategory.MISC)
                    .sized(0.3125F, 0.3125F)
                    .clientTrackingRange(64)
                    .updateInterval(1)
                    .build(new ResourceLocation(Cannonical.MOD_ID, "cannonball_projectile").toString()));

    public static final RegistrySupplier<EntityType<GunpowderBarrelPrimedEntity>> GUNPOWDER_BARREL_PRIMED =
            ENTITY_TYPES.register("gunpowder_barrel_primed", () -> EntityType.Builder
                    .<GunpowderBarrelPrimedEntity>of(GunpowderBarrelPrimedEntity::new, MobCategory.MISC)
                    .sized(0.98F, 0.98F)
                    .clientTrackingRange(10)
                    .updateInterval(10)
                    .build(new ResourceLocation(Cannonical.MOD_ID, "gunpowder_barrel_primed").toString()));

    private ModEntityTypes() {
    }

    public static void register() {
        ENTITY_TYPES.register();
    }
}
