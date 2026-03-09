package org.shipwrights.cannonical.mixin.client;

import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.shipwrights.cannonical.client.CannonBlockDamageOverlayState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Unique
    private static final long CANNONICAL_SWEEP_INTERVAL_TICKS = 2L;
    @Unique
    private static final int CANNONICAL_CHUNK_PADDING = 2;
    @Unique
    private static final double CANNONICAL_MAX_RENDER_DISTANCE_SQR = 1024.0D;

    @Shadow
    @Final
    private RenderBuffers renderBuffers;

    @Unique
    private final Long2ByteOpenHashMap cannonical$visibleStages = new Long2ByteOpenHashMap();
    @Unique
    private ResourceLocation cannonical$activeDimensionId = null;
    @Unique
    private long cannonical$lastSweepTick = Long.MIN_VALUE;

    @Unique
    private static int cannonical$toDestroyStage(int damageState) {
        int clamped = Math.max(0, Math.min(15, damageState));
        if (clamped <= 0) {
            return 0;
        }
        // Bias toward visibility so low custom damage states are still readable.
        return Math.max(1, Math.min(9, Mth.ceil((clamped * 9.0F) / 15.0F)));
    }

    @Unique
    private static int cannonical$getDecalQuarterTurns(long posLong) {
        long mixed = posLong * 0x9E3779B97F4A7C15L;
        mixed ^= (mixed >>> 33);
        mixed *= 0xC2B2AE3D27D4EB4FL;
        mixed ^= (mixed >>> 29);
        return (int) (mixed & 3L);
    }

    @Unique
    private void cannonical$clearAllRenderState() {
        this.cannonical$visibleStages.clear();
    }

    @Inject(
            method = "renderLevel(Lcom/mojang/blaze3d/vertex/PoseStack;FJZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;)V",
            at = @At("HEAD")
    )
    private void cannonical$updateVisibleCustomDamage(PoseStack poseStack, float partialTick, long finishNanoTime, boolean renderBlockOutline,
                                                      Camera camera, GameRenderer gameRenderer, LightTexture lightTexture,
                                                      Matrix4f projectionMatrix, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            this.cannonical$activeDimensionId = null;
            this.cannonical$lastSweepTick = Long.MIN_VALUE;
            this.cannonical$clearAllRenderState();
            return;
        }

        ResourceLocation dimensionId = level.dimension().location();
        if (!dimensionId.equals(this.cannonical$activeDimensionId)) {
            this.cannonical$activeDimensionId = dimensionId;
            this.cannonical$lastSweepTick = Long.MIN_VALUE;
            this.cannonical$clearAllRenderState();
        }

        long gameTick = level.getGameTime();
        if (this.cannonical$lastSweepTick != Long.MIN_VALUE
                && (gameTick - this.cannonical$lastSweepTick) < CANNONICAL_SWEEP_INTERVAL_TICKS) {
            return;
        }
        this.cannonical$lastSweepTick = gameTick;

        Vec3 cameraPos = camera.getPosition();
        int cameraChunkX = Mth.floor(cameraPos.x) >> 4;
        int cameraChunkZ = Mth.floor(cameraPos.z) >> 4;
        int renderDistanceChunks = minecraft.options.getEffectiveRenderDistance() + CANNONICAL_CHUNK_PADDING;

        int minChunkX = cameraChunkX - renderDistanceChunks;
        int maxChunkX = cameraChunkX + renderDistanceChunks;
        int minChunkZ = cameraChunkZ - renderDistanceChunks;
        int maxChunkZ = cameraChunkZ + renderDistanceChunks;

        this.cannonical$visibleStages.clear();
        CannonBlockDamageOverlayState.forEachInChunkRange(
                dimensionId,
                minChunkX,
                maxChunkX,
                minChunkZ,
                maxChunkZ,
                (posLong, damageState) -> {
                    int stage = cannonical$toDestroyStage(damageState);
                    if (stage > 0) {
                        this.cannonical$visibleStages.put(posLong, (byte) stage);
                    }
                }
        );
    }

    @Inject(
            method = "renderLevel(Lcom/mojang/blaze3d/vertex/PoseStack;FJZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;)V",
            at = @At(value = "INVOKE_STRING", target = "Lnet/minecraft/util/profiling/ProfilerFiller;popPush(Ljava/lang/String;)V", args = "ldc=destroyProgress", shift = At.Shift.AFTER)
    )
    private void cannonical$renderCustomDamageOverlay(PoseStack poseStack, float partialTick, long finishNanoTime, boolean renderBlockOutline,
                                                      Camera camera, GameRenderer gameRenderer, LightTexture lightTexture,
                                                      Matrix4f projectionMatrix, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }

        Vec3 cameraPos = camera.getPosition();
        double cameraX = cameraPos.x;
        double cameraY = cameraPos.y;
        double cameraZ = cameraPos.z;
        if (!this.cannonical$visibleStages.isEmpty()) {
            BlockRenderDispatcher blockRenderer = minecraft.getBlockRenderer();
            MultiBufferSource.BufferSource crumblingBuffer = this.renderBuffers.crumblingBufferSource();

            for (Long2ByteMap.Entry entry : this.cannonical$visibleStages.long2ByteEntrySet()) {
                int stage = Math.max(0, Math.min(9, entry.getByteValue()));
                if (stage <= 0) {
                    continue;
                }

                long posLong = entry.getLongKey();
                BlockPos blockPos = BlockPos.of(posLong);
                double dx = blockPos.getX() - cameraX;
                double dy = blockPos.getY() - cameraY;
                double dz = blockPos.getZ() - cameraZ;
                double distanceSqr = dx * dx + dy * dy + dz * dz;
                if (distanceSqr > CANNONICAL_MAX_RENDER_DISTANCE_SQR) {
                    continue;
                }

                BlockState blockState = level.getBlockState(blockPos);
                if (blockState.isAir()) {
                    continue;
                }

                poseStack.pushPose();
                poseStack.translate(dx, dy, dz);
                PoseStack.Pose pose = poseStack.last();
                int quarterTurns = cannonical$getDecalQuarterTurns(posLong);
                Matrix4f decalPose = pose.pose();
                Matrix3f decalNormal = pose.normal();
                if (quarterTurns != 0) {
                    float angle = quarterTurns * ((float) (Math.PI / 2.0D));
                    decalPose = new Matrix4f(decalPose).rotateZ(angle);
                    decalNormal = new Matrix3f(decalNormal).rotateZ(angle);
                }
                VertexConsumer buffer = new SheetedDecalTextureGenerator(
                        crumblingBuffer.getBuffer(ModelBakery.DESTROY_TYPES.get(stage)),
                        decalPose,
                        decalNormal,
                        1.0F
                );
                blockRenderer.renderBreakingTexture(blockState, blockPos, level, poseStack, buffer);
                poseStack.popPose();
            }
        }
    }
}
