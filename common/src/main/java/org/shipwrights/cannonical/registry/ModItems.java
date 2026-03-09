package org.shipwrights.cannonical.registry;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Tiers;
import org.shipwrights.cannonical.Cannonical;
import org.shipwrights.cannonical.content.item.MalletItem;
import org.shipwrights.cannonical.content.projectile.CannonballItem;

public final class ModItems {
    private static final double WOODEN_MALLET_REPAIR_POWER = 1.0D;
    private static final double STONE_MALLET_REPAIR_POWER = 1.1D;
    private static final double COPPER_MALLET_REPAIR_POWER = 1.2D;
    private static final double IRON_MALLET_REPAIR_POWER = 1.5D;
    private static final double GOLD_MALLET_REPAIR_POWER = 1.0D;
    private static final double DIAMOND_MALLET_REPAIR_POWER = 2.0D;
    private static final double NETHERITE_MALLET_REPAIR_POWER = 2.2D;

    private static final double WOODEN_MALLET_SPEED = 1.0D;
    private static final double STONE_MALLET_SPEED = 1.4D;
    private static final double COPPER_MALLET_SPEED = 1.3D;
    private static final double IRON_MALLET_SPEED = 1.8D;
    private static final double GOLD_MALLET_SPEED = 2.2D;
    private static final double DIAMOND_MALLET_SPEED = 2.0D;
    private static final double NETHERITE_MALLET_SPEED = 2.0D;

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Cannonical.MOD_ID, Registries.ITEM);

    public static final RegistrySupplier<Item> CANNONBALL =
            ITEMS.register("cannonball", () -> new CannonballItem(new Item.Properties()));
    public static final RegistrySupplier<Item> MALLET =
            ITEMS.register("mallet", () -> new MalletItem(new Item.Properties().durability(256), WOODEN_MALLET_REPAIR_POWER, WOODEN_MALLET_SPEED, Tiers.WOOD));
    public static final RegistrySupplier<Item> STONE_MALLET =
            ITEMS.register("stone_mallet", () -> new MalletItem(new Item.Properties().durability(384), STONE_MALLET_REPAIR_POWER, STONE_MALLET_SPEED, Tiers.STONE));
    public static final RegistrySupplier<Item> COPPER_MALLET =
            ITEMS.register("copper_mallet", () -> new MalletItem(new Item.Properties().durability(448), COPPER_MALLET_REPAIR_POWER, COPPER_MALLET_SPEED, Tiers.STONE));
    public static final RegistrySupplier<Item> IRON_MALLET =
            ITEMS.register("iron_mallet", () -> new MalletItem(new Item.Properties().durability(768), IRON_MALLET_REPAIR_POWER, IRON_MALLET_SPEED, Tiers.IRON));
    public static final RegistrySupplier<Item> GOLD_MALLET =
            ITEMS.register("gold_mallet", () -> new MalletItem(new Item.Properties().durability(192), GOLD_MALLET_REPAIR_POWER, GOLD_MALLET_SPEED, Tiers.GOLD));
    public static final RegistrySupplier<Item> DIAMOND_MALLET =
            ITEMS.register("diamond_mallet", () -> new MalletItem(new Item.Properties().durability(2048), DIAMOND_MALLET_REPAIR_POWER, DIAMOND_MALLET_SPEED, Tiers.DIAMOND));
    public static final RegistrySupplier<Item> NETHERITE_MALLET =
            ITEMS.register("netherite_mallet", () -> new MalletItem(new Item.Properties().durability(3072).fireResistant(), NETHERITE_MALLET_REPAIR_POWER, NETHERITE_MALLET_SPEED, Tiers.NETHERITE));

    private ModItems() {
    }

    public static void register() {
        ITEMS.register();
    }
}
