package org.shipwrights.cannonical.fabric.client;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import org.shipwrights.cannonical.client.render.entity.GunpowderBarrelPrimedRenderer;
import org.shipwrights.cannonical.registry.ModEntityTypes;

public final class CannonicalFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModEntityTypes.CANNONBALL_PROJECTILE.get(), ThrownItemRenderer::new);
        EntityRendererRegistry.register(ModEntityTypes.GUNPOWDER_BARREL_PRIMED.get(), GunpowderBarrelPrimedRenderer::new);
    }
}
