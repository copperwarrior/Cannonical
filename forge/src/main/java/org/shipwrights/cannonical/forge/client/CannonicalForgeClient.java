package org.shipwrights.cannonical.forge.client;

import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.shipwrights.cannonical.Cannonical;
import org.shipwrights.cannonical.client.render.entity.GunpowderBarrelPrimedRenderer;
import org.shipwrights.cannonical.registry.ModEntityTypes;

@Mod.EventBusSubscriber(modid = Cannonical.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CannonicalForgeClient {
    private CannonicalForgeClient() {
    }

    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntityTypes.CANNONBALL_PROJECTILE.get(), ThrownItemRenderer::new);
        event.registerEntityRenderer(ModEntityTypes.GUNPOWDER_BARREL_PRIMED.get(), GunpowderBarrelPrimedRenderer::new);
    }
}
