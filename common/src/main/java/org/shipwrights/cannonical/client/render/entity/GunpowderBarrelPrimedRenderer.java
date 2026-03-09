package org.shipwrights.cannonical.client.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.TntMinecartRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.shipwrights.cannonical.content.explosive.GunpowderBarrelPrimedEntity;
import org.shipwrights.cannonical.registry.ModBlocks;

public class GunpowderBarrelPrimedRenderer extends EntityRenderer<GunpowderBarrelPrimedEntity> {
    private final BlockRenderDispatcher blockRenderer;

    public GunpowderBarrelPrimedRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.5F;
        this.blockRenderer = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(GunpowderBarrelPrimedEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        poseStack.translate(0.0F, 0.5F, 0.0F);
        int fuse = entity.getFuse();
        if ((float) fuse - partialTick + 1.0F < 10.0F) {
            float fuseProgress = 1.0F - ((float) fuse - partialTick + 1.0F) / 10.0F;
            fuseProgress = Mth.clamp(fuseProgress, 0.0F, 1.0F);
            fuseProgress *= fuseProgress;
            fuseProgress *= fuseProgress;
            float scale = 1.0F + fuseProgress * 0.3F;
            poseStack.scale(scale, scale, scale);
        }

        poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F));
        poseStack.translate(-0.5F, -0.5F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
        TntMinecartRenderer.renderWhiteSolidBlock(
                this.blockRenderer,
                ModBlocks.GUNPOWDER_BARREL.get().defaultBlockState(),
                poseStack,
                buffer,
                packedLight,
                fuse / 5 % 2 == 0
        );
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(GunpowderBarrelPrimedEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
