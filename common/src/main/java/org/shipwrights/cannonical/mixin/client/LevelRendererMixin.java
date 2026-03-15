package org.shipwrights.cannonical.mixin.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.shipwrights.cannonical.client.CannonBlockDamageOverlayState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Unique
    private static final int CANNONICAL_MAX_SECTION_REBUILDS_PER_FRAME = 8;
    @Unique
    private static final long CANNONICAL_CACHE_SWEEP_INTERVAL_TICKS = 40L;
    @Unique
    private static final int CANNONICAL_BUFFER_BUILDER_CAPACITY = 256;

    @Unique
    private final Long2ObjectOpenHashMap<SectionRenderCache> cannonical$sectionCaches = new Long2ObjectOpenHashMap<>();
    @Unique
    private final LongOpenHashSet cannonical$pendingDirtySections = new LongOpenHashSet();
    @Unique
    private ResourceLocation cannonical$activeDimensionId = null;
    @Unique
    private ClientLevel cannonical$activeLevel = null;
    @Unique
    private long cannonical$lastCacheSweepTick = Long.MIN_VALUE;

    @Unique
    private static int cannonical$toDestroyStage(int damageState) {
        int clamped = Math.max(0, Math.min(15, damageState));
        if (clamped <= 0) {
            return 0;
        }
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
    private void cannonical$clearRenderCache() {
        for (SectionRenderCache cache : this.cannonical$sectionCaches.values()) {
            cache.close();
        }
        this.cannonical$sectionCaches.clear();
        this.cannonical$pendingDirtySections.clear();
    }

    @Inject(
            method = "renderLevel(Lcom/mojang/blaze3d/vertex/PoseStack;FJZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;)V",
            at = @At("HEAD")
    )
    private void cannonical$collectDirtySections(PoseStack poseStack, float partialTick, long finishNanoTime, boolean renderBlockOutline,
                                                 Camera camera, GameRenderer gameRenderer, LightTexture lightTexture,
                                                 Matrix4f projectionMatrix, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            this.cannonical$activeLevel = null;
            this.cannonical$activeDimensionId = null;
            this.cannonical$lastCacheSweepTick = Long.MIN_VALUE;
            this.cannonical$clearRenderCache();
            return;
        }

        if (level != this.cannonical$activeLevel) {
            this.cannonical$activeLevel = level;
            this.cannonical$activeDimensionId = level.dimension().location();
            this.cannonical$lastCacheSweepTick = Long.MIN_VALUE;
            this.cannonical$clearRenderCache();
        }

        ResourceLocation dimensionId = level.dimension().location();
        if (!dimensionId.equals(this.cannonical$activeDimensionId)) {
            this.cannonical$activeDimensionId = dimensionId;
            this.cannonical$lastCacheSweepTick = Long.MIN_VALUE;
            this.cannonical$clearRenderCache();
        }

        long[] dirtySections = CannonBlockDamageOverlayState.consumeDirtySections(dimensionId);
        for (long sectionKey : dirtySections) {
            this.cannonical$pendingDirtySections.add(sectionKey);
        }
    }

    @Inject(
            method = "renderLevel(Lcom/mojang/blaze3d/vertex/PoseStack;FJZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;)V",
            at = @At(value = "INVOKE_STRING", target = "Lnet/minecraft/util/profiling/ProfilerFiller;popPush(Ljava/lang/String;)V", args = "ldc=destroyProgress", shift = At.Shift.AFTER)
    )
    private void cannonical$renderCachedDamageOverlay(PoseStack poseStack, float partialTick, long finishNanoTime, boolean renderBlockOutline,
                                                      Camera camera, GameRenderer gameRenderer, LightTexture lightTexture,
                                                      Matrix4f projectionMatrix, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || this.cannonical$activeDimensionId == null) {
            return;
        }

        BlockRenderDispatcher blockRenderer = minecraft.getBlockRenderer();
        this.cannonical$rebuildDirtySections(level, blockRenderer);

        Vec3 cameraPos = camera.getPosition();
        int cameraChunkX = Mth.floor(cameraPos.x) >> 4;
        int cameraChunkZ = Mth.floor(cameraPos.z) >> 4;
        int renderDistanceChunks = minecraft.options.getEffectiveRenderDistance();
        int minChunkX = cameraChunkX - renderDistanceChunks;
        int maxChunkX = cameraChunkX + renderDistanceChunks;
        int minChunkZ = cameraChunkZ - renderDistanceChunks;
        int maxChunkZ = cameraChunkZ + renderDistanceChunks;
        this.cannonical$sweepFarCacheEntries(level, minChunkX, maxChunkX, minChunkZ, maxChunkZ);

        for (int stage = 1; stage <= 9; stage++) {
            RenderType renderType = ModelBakery.DESTROY_TYPES.get(stage);
            boolean stageRendered = false;

            for (SectionRenderCache cache : this.cannonical$sectionCaches.values()) {
                if (!cache.inChunkRange(minChunkX, maxChunkX, minChunkZ, maxChunkZ)) {
                    continue;
                }

                VertexBuffer buffer = cache.getStageBuffer(stage);
                if (buffer == null) {
                    continue;
                }

                if (!stageRendered) {
                    renderType.setupRenderState();
                    stageRendered = true;
                }

                Matrix4f modelViewMatrix = new Matrix4f(poseStack.last().pose())
                        .translate(
                                (float) (cache.originX() - cameraPos.x),
                                (float) (cache.originY() - cameraPos.y),
                                (float) (cache.originZ() - cameraPos.z)
                        );
                buffer.bind();
                buffer.drawWithShader(modelViewMatrix, projectionMatrix, GameRenderer.getRendertypeCrumblingShader());
            }

            if (stageRendered) {
                VertexBuffer.unbind();
                renderType.clearRenderState();
            }
        }
    }

    @Unique
    private void cannonical$rebuildDirtySections(ClientLevel level, BlockRenderDispatcher blockRenderer) {
        if (this.cannonical$pendingDirtySections.isEmpty()) {
            return;
        }

        int rebuilds = 0;
        LongIterator iterator = this.cannonical$pendingDirtySections.iterator();
        while (iterator.hasNext() && rebuilds < CANNONICAL_MAX_SECTION_REBUILDS_PER_FRAME) {
            long sectionKey = iterator.nextLong();
            iterator.remove();

            this.cannonical$rebuildSectionCache(level, blockRenderer, sectionKey);
            rebuilds++;
        }
    }

    @Unique
    private void cannonical$rebuildSectionCache(ClientLevel level, BlockRenderDispatcher blockRenderer, long sectionKey) {
        SectionRenderCache previous = this.cannonical$sectionCaches.remove(sectionKey);
        if (previous != null) {
            previous.close();
        }

        Long2ByteOpenHashMap sectionSnapshot = CannonBlockDamageOverlayState.snapshotSection(this.cannonical$activeDimensionId, sectionKey);
        if (sectionSnapshot.isEmpty()) {
            return;
        }

        SectionRenderCache cache = new SectionRenderCache(sectionKey);
        LongOpenHashSet[] positionsByStage = new LongOpenHashSet[10];
        for (Long2ByteMap.Entry entry : sectionSnapshot.long2ByteEntrySet()) {
            int stage = cannonical$toDestroyStage(entry.getByteValue());
            if (stage <= 0) {
                continue;
            }
            LongOpenHashSet positions = positionsByStage[stage];
            if (positions == null) {
                positions = new LongOpenHashSet();
                positionsByStage[stage] = positions;
            }
            positions.add(entry.getLongKey());
        }

        for (int stage = 1; stage <= 9; stage++) {
            LongOpenHashSet stagePositions = positionsByStage[stage];
            if (stagePositions == null || stagePositions.isEmpty()) {
                continue;
            }

            VertexBuffer stageBuffer = cannonical$buildStageBuffer(
                    level,
                    blockRenderer,
                    stagePositions,
                    cache.originBlockX(),
                    cache.originBlockY(),
                    cache.originBlockZ()
            );
            if (stageBuffer != null) {
                cache.setStageBuffer(stage, stageBuffer);
            }
        }
        VertexBuffer.unbind();

        if (cache.isEmpty()) {
            cache.close();
            return;
        }

        this.cannonical$sectionCaches.put(sectionKey, cache);
    }

    @Unique
    private VertexBuffer cannonical$buildStageBuffer(ClientLevel level, BlockRenderDispatcher blockRenderer, LongOpenHashSet stagePositions,
                                                     int sectionOriginX, int sectionOriginY, int sectionOriginZ) {
        BufferBuilder bufferBuilder = new BufferBuilder(CANNONICAL_BUFFER_BUILDER_CAPACITY);
        bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);

        LongIterator iterator = stagePositions.iterator();
        while (iterator.hasNext()) {
            long posLong = iterator.nextLong();
            BlockPos blockPos = BlockPos.of(posLong);
            BlockState blockState = level.getBlockState(blockPos);
            if (blockState.isAir()) {
                continue;
            }

            PoseStack blockPose = new PoseStack();
            blockPose.translate(
                    blockPos.getX() - sectionOriginX,
                    blockPos.getY() - sectionOriginY,
                    blockPos.getZ() - sectionOriginZ
            );

            PoseStack.Pose pose = blockPose.last();
            Matrix4f decalPose = pose.pose();
            Matrix3f decalNormal = pose.normal();
            int quarterTurns = cannonical$getDecalQuarterTurns(posLong);
            if (quarterTurns != 0) {
                float angle = quarterTurns * ((float) (Math.PI / 2.0D));
                decalPose = new Matrix4f(decalPose).rotateZ(angle);
                decalNormal = new Matrix3f(decalNormal).rotateZ(angle);
            }

            VertexConsumer consumer = new SheetedDecalTextureGenerator(bufferBuilder, decalPose, decalNormal, 1.0F);
            blockRenderer.renderBreakingTexture(blockState, blockPos, level, blockPose, consumer);
        }

        BufferBuilder.RenderedBuffer renderedBuffer = bufferBuilder.endOrDiscardIfEmpty();
        if (renderedBuffer == null) {
            return null;
        }

        VertexBuffer vertexBuffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        vertexBuffer.bind();
        vertexBuffer.upload(renderedBuffer);
        return vertexBuffer;
    }

    @Unique
    private void cannonical$sweepFarCacheEntries(ClientLevel level,
                                                 int minChunkX,
                                                 int maxChunkX,
                                                 int minChunkZ,
                                                 int maxChunkZ) {
        long gameTick = level.getGameTime();
        if (this.cannonical$lastCacheSweepTick != Long.MIN_VALUE
                && (gameTick - this.cannonical$lastCacheSweepTick) < CANNONICAL_CACHE_SWEEP_INTERVAL_TICKS) {
            return;
        }
        this.cannonical$lastCacheSweepTick = gameTick;

        LongIterator iterator = this.cannonical$sectionCaches.keySet().iterator();
        while (iterator.hasNext()) {
            long sectionKey = iterator.nextLong();
            SectionRenderCache cache = this.cannonical$sectionCaches.get(sectionKey);
            if (cache == null) {
                iterator.remove();
                continue;
            }

            if (cache.inChunkRange(minChunkX, maxChunkX, minChunkZ, maxChunkZ)) {
                continue;
            }

            cache.close();
            iterator.remove();
        }
    }

    @Unique
    private static final class SectionRenderCache {
        private final long sectionKey;
        private final int sectionX;
        private final int sectionY;
        private final int sectionZ;
        private final int originBlockX;
        private final int originBlockY;
        private final int originBlockZ;
        private final VertexBuffer[] stageBuffers = new VertexBuffer[10];

        private SectionRenderCache(long sectionKey) {
            this.sectionKey = sectionKey;
            this.sectionX = SectionPos.x(sectionKey);
            this.sectionY = SectionPos.y(sectionKey);
            this.sectionZ = SectionPos.z(sectionKey);
            this.originBlockX = this.sectionX << 4;
            this.originBlockY = this.sectionY << 4;
            this.originBlockZ = this.sectionZ << 4;
        }

        private int originBlockX() {
            return this.originBlockX;
        }

        private int originBlockY() {
            return this.originBlockY;
        }

        private int originBlockZ() {
            return this.originBlockZ;
        }

        private double originX() {
            return this.originBlockX;
        }

        private double originY() {
            return this.originBlockY;
        }

        private double originZ() {
            return this.originBlockZ;
        }

        private void setStageBuffer(int stage, VertexBuffer buffer) {
            this.stageBuffers[stage] = buffer;
        }

        private VertexBuffer getStageBuffer(int stage) {
            return this.stageBuffers[stage];
        }

        private boolean isEmpty() {
            for (int stage = 1; stage <= 9; stage++) {
                if (this.stageBuffers[stage] != null) {
                    return false;
                }
            }
            return true;
        }

        private boolean inChunkRange(int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ) {
            return this.sectionX >= minChunkX && this.sectionX <= maxChunkX
                    && this.sectionZ >= minChunkZ && this.sectionZ <= maxChunkZ;
        }

        private void close() {
            for (int stage = 1; stage <= 9; stage++) {
                VertexBuffer buffer = this.stageBuffers[stage];
                if (buffer != null) {
                    buffer.close();
                    this.stageBuffers[stage] = null;
                }
            }
        }
    }
}
