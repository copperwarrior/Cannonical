package org.shipwrights.cannonical.content.projectile;

import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;
import org.shipwrights.cannonical.network.CannonicalNetwork;
import org.shipwrights.cannonical.registry.ModEntityTypes;
import org.shipwrights.cannonical.registry.ModItems;
import org.shipwrights.krakk.api.KrakkApi;
import org.shipwrights.krakk.api.damage.KrakkImpactResult;

public class CannonballProjectileEntity extends ThrowableItemProjectile {
    private static final EntityDataAccessor<Float> DATA_SYNC_VX =
            SynchedEntityData.defineId(CannonballProjectileEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_SYNC_VY =
            SynchedEntityData.defineId(CannonballProjectileEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_SYNC_VZ =
            SynchedEntityData.defineId(CannonballProjectileEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_SYNC_SEQ =
            SynchedEntityData.defineId(CannonballProjectileEntity.class, EntityDataSerializers.INT);
    private static final double MOTION_SYNC_EPSILON_SQR = 1.0E-5D;
    private static final double PRECISE_SYNC_POSITION_EPSILON_SQR = 1.0E-4D;
    private static final double STEP_COLLISION_SPEED_THRESHOLD = 20.0D;
    private static final double STEP_COLLISION_SEGMENT_LENGTH = 0.9D;
    private static final int STEP_COLLISION_MAX_IMPACTS_PER_TICK = 12;
    private static final double PRECISE_SYNC_SPEED_THRESHOLD = 8.0D;

    private static final double DAMAGE_PER_SPEED = 6.5D;
    private static final double MIN_DAMAGE = 1.0D;
    private static final double MAX_DAMAGE = 40.0D;
    private static final double MIN_LIFE_SPEED_RATIO = 0.10D;
    private static final double FLUID_SPEED_MULTIPLIER = 0.35D;
    private static final double IN_FLUID_TICK_MULTIPLIER = 0.70D;
    private static final String LAUNCH_SPEED_TAG = "LaunchSpeed";
    private static final String MARKED_FOR_DEATH_TAG = "MarkedForDeath";

    private double launchSpeed = -1.0D;
    private boolean markedForDeath = false;
    private int lastAppliedMotionSyncSeq = -1;
    private int lastAppliedPreciseSyncSeq = -1;
    private int lastAppliedPreciseSyncTick = Integer.MIN_VALUE;
    private Vec3 lastSentMotion = Vec3.ZERO;
    private int lastSentMotionTick = Integer.MIN_VALUE;
    private Vec3 lastPreciseSyncPos = Vec3.ZERO;
    private Vec3 lastPreciseSyncMotion = Vec3.ZERO;
    private int lastPreciseSyncTick = Integer.MIN_VALUE;
    private int preciseSyncSeq = 0;
    private boolean blockHitThisTick = false;
    private Vec3 tickStartPos = Vec3.ZERO;
    private double tickStartSpeed = 0.0D;

    public CannonballProjectileEntity(EntityType<? extends CannonballProjectileEntity> entityType, Level level) {
        super(entityType, level);
    }

    public CannonballProjectileEntity(Level level, LivingEntity owner) {
        super(ModEntityTypes.CANNONBALL_PROJECTILE.get(), owner, level);
    }

    public CannonballProjectileEntity(Level level, double x, double y, double z) {
        super(ModEntityTypes.CANNONBALL_PROJECTILE.get(), x, y, z, level);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.CANNONBALL.get();
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_SYNC_VX, 0.0F);
        this.entityData.define(DATA_SYNC_VY, 0.0F);
        this.entityData.define(DATA_SYNC_VZ, 0.0F);
        this.entityData.define(DATA_SYNC_SEQ, 0);
    }

    @Override
    public void tick() {
        if (!this.level().isClientSide) {
            this.tickStartPos = this.position();
            this.tickStartSpeed = this.getDeltaMovement().length();
            this.blockHitThisTick = false;
        }

        super.tick();

        if (this.level().isClientSide) {
            this.applyServerMotionSyncIfNeeded();
            return;
        }
        if (!this.isAlive()) {
            return;
        }

        double speedForSweep = Math.max(this.tickStartSpeed, this.getDeltaMovement().length());
        if (speedForSweep >= STEP_COLLISION_SPEED_THRESHOLD) {
            this.performSteppedCollisionSweep(this.tickStartPos, this.position());
            if (!this.isAlive()) {
                return;
            }
        }

        if (this.isInWaterOrBubble() || this.isInLava()) {
            this.setDeltaMovement(this.getDeltaMovement().scale(IN_FLUID_TICK_MULTIPLIER));
            this.hasImpulse = true;
        }

        this.captureLaunchSpeed();
        this.updateDeathMarkFromSpeed();
        this.syncMotionFromServer(false);
        this.syncPreciseStateFromServer(false);
    }

    @Override
    protected void onHitEntity(EntityHitResult hitResult) {
        super.onHitEntity(hitResult);
        if (this.level().isClientSide) {
            return;
        }

        Entity hitEntity = hitResult.getEntity();
        Entity owner = this.getOwner();

        double speed = this.getDeltaMovement().length();
        float damage = (float) Math.max(MIN_DAMAGE, Math.min(MAX_DAMAGE, speed * DAMAGE_PER_SPEED));
        hitEntity.hurt(this.damageSources().thrown(this, owner == null ? this : owner), damage);

        double speedLossRatio = this.computeEntitySpeedLoss(hitEntity);
        this.applySpeedLoss(speedLossRatio, false, null);
        this.updateDeathMarkFromSpeed();
        this.syncMotionFromServer(true);
        this.syncPreciseStateFromServer(true);
    }

    @Override
    protected void onHit(HitResult hitResult) {
        if (!this.level().isClientSide) {
            if (this.resolveMarkedDeathOnInteraction()) {
                return;
            }
        }

        super.onHit(hitResult);

        if (!this.level().isClientSide) {
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.GENERIC_EXPLODE, SoundSource.NEUTRAL, 0.45F,
                    1.1F + (this.random.nextFloat() * 0.2F));
            this.level().broadcastEntityEvent(this, (byte) 3);
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == 3) {
            ItemStack itemStack = this.getItem();
            ParticleOptions particleOptions = itemStack.isEmpty()
                    ? ParticleTypes.ITEM_SNOWBALL
                    : new ItemParticleOption(ParticleTypes.ITEM, itemStack);

            for (int i = 0; i < 8; i++) {
                this.level().addParticle(particleOptions, this.getX(), this.getY(), this.getZ(),
                        0.0D, 0.0D, 0.0D);
            }
            return;
        }
        super.handleEntityEvent(id);
    }

    @Override
    protected float getGravity() {
        return 0.06F;
    }

    @Override
    protected void onHitBlock(BlockHitResult hitResult) {
        if (!this.level().isClientSide) {
            this.blockHitThisTick = true;
        }
        super.onHitBlock(hitResult);

        BlockState state = this.level().getBlockState(hitResult.getBlockPos());
        if (state.isAir()) {
            if (!this.level().isClientSide) {
                KrakkApi.damage().clearDamage((ServerLevel) this.level(), hitResult.getBlockPos());
            }
            return;
        }

        if (!this.level().isClientSide && !this.level().getFluidState(hitResult.getBlockPos()).isEmpty()) {
            KrakkApi.damage().clearDamage((ServerLevel) this.level(), hitResult.getBlockPos());
            this.applySpeedLoss(1.0D - FLUID_SPEED_MULTIPLIER, false, null);
            this.updateDeathMarkFromSpeed();
            this.syncMotionFromServer(true);
            this.syncPreciseStateFromServer(true);
            return;
        }

        if (!this.level().isClientSide) {
            ServerLevel serverLevel = (ServerLevel) this.level();
            double speed = this.getDeltaMovement().length();
            float resistance = state.getBlock().getExplosionResistance();
            float hardness = Math.max(0.0F, state.getDestroySpeed(this.level(), hitResult.getBlockPos()));
            boolean unbreakable = state.getDestroySpeed(this.level(), hitResult.getBlockPos()) < 0.0F;

            double impactPower = speed * speed * 14.0D;
            double speedLoss = this.computeBlockSpeedLoss(resistance, hardness);

            if (!unbreakable) {
                KrakkImpactResult impactResult = KrakkApi.damage().applyImpact(
                        serverLevel, hitResult.getBlockPos(), state, this, impactPower, true);
                if (impactResult.broken()) {
                    this.applyBlockPenetrationResponse(speedLoss, hitResult);
                } else {
                    this.applyBlockCollisionResponse(speedLoss, hitResult, 0.08D);
                }
            } else {
                KrakkApi.damage().clearDamage(serverLevel, hitResult.getBlockPos());
                this.applyBlockCollisionResponse(speedLoss, hitResult, 0.08D);
            }

            this.updateDeathMarkFromSpeed();
            this.syncMotionFromServer(true);
            this.syncPreciseStateFromServer(true);
        }
    }

    private double computeEntitySpeedLoss(Entity entity) {
        if (!(entity instanceof LivingEntity livingEntity)) {
            return 0.15D;
        }

        double maxHealth = livingEntity.getMaxHealth();
        double armor = livingEntity.getArmorValue();
        double toughness = livingEntity.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        double targetMass = (maxHealth * 0.35D) + (armor * 2.0D) + (toughness * 1.5D);
        double loss = targetMass / (targetMass + 25.0D);
        return clamp(loss, 0.05D, 0.85D);
    }

    private double computeBlockSpeedLoss(float explosionResistance, float hardness) {
        double strength = Math.max(0.0D, explosionResistance) + (Math.max(0.0D, hardness) * 2.2D);
        double normalized = strength / (strength + 6.0D);
        double loss = 0.04D + (0.78D * Math.pow(normalized, 1.2D));

        return clamp(loss, 0.04D, 0.95D);
    }

    private void applySpeedLoss(double lossRatio, boolean bounce, BlockHitResult hitResult) {
        Vec3 velocity = this.getDeltaMovement();
        double speed = velocity.length();
        if (speed <= 1.0E-6D) {
            return;
        }

        double retained = clamp(1.0D - lossRatio, 0.0D, 1.0D);
        Vec3 nextVelocity;

        if (bounce && hitResult != null) {
            Vec3 normal = Vec3.atLowerCornerOf(hitResult.getDirection().getNormal()).normalize();
            Vec3 reflected = velocity.subtract(normal.scale(2.0D * velocity.dot(normal)));
            nextVelocity = reflected.scale(retained);

            Vec3 offset = normal.scale(0.03D);
            this.setPos(hitResult.getLocation().x + offset.x, hitResult.getLocation().y + offset.y, hitResult.getLocation().z + offset.z);
        } else {
            nextVelocity = velocity.scale(retained);
        }

        this.setDeltaMovement(nextVelocity);
        this.hasImpulse = true;
    }

    private void applyBlockCollisionResponse(double lossRatio, BlockHitResult hitResult, double redirectStrength) {
        Vec3 velocity = this.getDeltaMovement();
        double speed = velocity.length();
        if (speed <= 1.0E-6D) {
            return;
        }

        Vec3 normal = Vec3.atLowerCornerOf(hitResult.getDirection().getNormal()).normalize();
        double normalDot = velocity.dot(normal);
        Vec3 normalComponent = normal.scale(normalDot);
        Vec3 tangentialComponent = velocity.subtract(normalComponent);

        double retained = clamp(1.0D - lossRatio, 0.0D, 1.0D);
        Vec3 nextTangential = tangentialComponent.scale(retained);

        // Strongly bias toward surface sliding; only a small outward rebound remains.
        double inSurfaceSpeed = Math.max(0.0D, -normalDot);
        double speedReboundScale = Math.pow(clamp(10.0D / (speed + 10.0D), 0.0D, 1.0D), 2.0D);
        double highSpeedGate = clamp((35.0D - speed) / 20.0D, 0.0D, 1.0D);
        double rebound = inSurfaceSpeed * clamp(redirectStrength, 0.0D, 1.0D) * speedReboundScale * highSpeedGate;
        rebound = Math.min(rebound, Math.min(speed * 0.03D, 0.45D));
        Vec3 nextVelocity = nextTangential.add(normal.scale(rebound));

        Vec3 offset = normal.scale(0.03D);
        this.setPos(hitResult.getLocation().x + offset.x, hitResult.getLocation().y + offset.y, hitResult.getLocation().z + offset.z);
        this.setDeltaMovement(nextVelocity);
        this.hasImpulse = true;
    }

    private void applyBlockPenetrationResponse(double lossRatio, BlockHitResult hitResult) {
        Vec3 velocity = this.getDeltaMovement();
        double speed = velocity.length();
        if (speed <= 1.0E-6D) {
            return;
        }

        // When a block breaks, preserve most forward momentum so high-speed shots can punch through lines of blocks.
        double effectiveLoss = clamp(lossRatio * 0.35D, 0.02D, 0.30D);
        double retained = clamp(1.0D - effectiveLoss, 0.0D, 1.0D);
        Vec3 direction = velocity.normalize();
        Vec3 nextVelocity = direction.scale(speed * retained);

        // Nudge through the broken face to reduce immediate re-collision against the same boundary.
        Vec3 through = direction.scale(0.20D);
        this.setPos(hitResult.getLocation().x + through.x, hitResult.getLocation().y + through.y, hitResult.getLocation().z + through.z);
        this.setDeltaMovement(nextVelocity);
        this.hasImpulse = true;
    }

    private void captureLaunchSpeed() {
        if (this.launchSpeed > 0.0D) {
            return;
        }

        double speed = this.getDeltaMovement().length();
        if (speed > 1.0E-4D) {
            this.launchSpeed = speed;
        }
    }

    public void captureLaunchSpeedNow() {
        this.captureLaunchSpeed();
        this.syncMotionFromServer(true);
    }

    private void updateDeathMarkFromSpeed() {
        if (this.launchSpeed <= 0.0D) {
            return;
        }

        if (this.isAboveDeathThreshold()) {
            this.markedForDeath = false;
            return;
        }

        this.markedForDeath = true;
    }

    private boolean resolveMarkedDeathOnInteraction() {
        if (!this.markedForDeath) {
            return false;
        }

        if (this.isAboveDeathThreshold()) {
            this.markedForDeath = false;
            return false;
        }

        this.discard();
        return true;
    }

    private boolean isAboveDeathThreshold() {
        if (this.launchSpeed <= 0.0D) {
            return true;
        }
        return this.getDeltaMovement().length() > (this.launchSpeed * MIN_LIFE_SPEED_RATIO);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compoundTag) {
        super.addAdditionalSaveData(compoundTag);
        compoundTag.putDouble(LAUNCH_SPEED_TAG, this.launchSpeed);
        compoundTag.putBoolean(MARKED_FOR_DEATH_TAG, this.markedForDeath);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compoundTag) {
        super.readAdditionalSaveData(compoundTag);
        if (compoundTag.contains(LAUNCH_SPEED_TAG)) {
            this.launchSpeed = compoundTag.getDouble(LAUNCH_SPEED_TAG);
        }
        if (compoundTag.contains(MARKED_FOR_DEATH_TAG)) {
            this.markedForDeath = compoundTag.getBoolean(MARKED_FOR_DEATH_TAG);
        }
    }

    private void syncMotionFromServer(boolean force) {
        if (this.level().isClientSide) {
            return;
        }

        Vec3 velocity = this.getDeltaMovement();
        int elapsedTicks = this.tickCount - this.lastSentMotionTick;
        int interval = this.getAdaptiveMotionSyncInterval(velocity.length());

        if (!force && elapsedTicks < interval) {
            return;
        }

        if (!force && velocity.distanceToSqr(this.lastSentMotion) < MOTION_SYNC_EPSILON_SQR && elapsedTicks < Math.max(interval * 2, 8)) {
            return;
        }

        this.entityData.set(DATA_SYNC_VX, (float) velocity.x);
        this.entityData.set(DATA_SYNC_VY, (float) velocity.y);
        this.entityData.set(DATA_SYNC_VZ, (float) velocity.z);
        this.entityData.set(DATA_SYNC_SEQ, this.entityData.get(DATA_SYNC_SEQ) + 1);
        this.lastSentMotion = velocity;
        this.lastSentMotionTick = this.tickCount;
    }

    private void applyServerMotionSyncIfNeeded() {
        if (this.tickCount == this.lastAppliedPreciseSyncTick) {
            return;
        }

        int syncSeq = this.entityData.get(DATA_SYNC_SEQ);
        if (syncSeq <= 0) {
            return;
        }
        if (syncSeq == this.lastAppliedMotionSyncSeq) {
            return;
        }

        Vec3 synced = new Vec3(
                this.entityData.get(DATA_SYNC_VX),
                this.entityData.get(DATA_SYNC_VY),
                this.entityData.get(DATA_SYNC_VZ)
        );

        if (this.getDeltaMovement().distanceToSqr(synced) > 1.0E-4D) {
            this.setDeltaMovement(synced);
            this.updateRotationFromMotion(synced);
        }
        this.lastAppliedMotionSyncSeq = syncSeq;
    }

    public void applyPreciseSyncFromServer(int sequence, Vec3 position, Vec3 velocity, float yRot, float xRot, boolean forcePosition) {
        if (!this.level().isClientSide) {
            return;
        }
        if (sequence <= this.lastAppliedPreciseSyncSeq) {
            return;
        }

        if (forcePosition || this.position().distanceToSqr(position) > PRECISE_SYNC_POSITION_EPSILON_SQR) {
            this.setPos(position.x, position.y, position.z);
        }
        if (this.getDeltaMovement().distanceToSqr(velocity) > MOTION_SYNC_EPSILON_SQR) {
            this.setDeltaMovement(velocity);
            this.hasImpulse = true;
        }

        this.setYRot(yRot);
        this.setXRot(xRot);
        this.yRotO = yRot;
        this.xRotO = xRot;
        this.lastAppliedMotionSyncSeq = this.entityData.get(DATA_SYNC_SEQ);
        this.lastAppliedPreciseSyncSeq = sequence;
        this.lastAppliedPreciseSyncTick = this.tickCount;
    }

    private void updateRotationFromMotion(Vec3 velocity) {
        double horizontal = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        if (horizontal < 1.0E-6D && Math.abs(velocity.y) < 1.0E-6D) {
            return;
        }

        float targetYaw = (float) (Mth.atan2(velocity.x, velocity.z) * (180.0D / Math.PI));
        float targetPitch = (float) (Mth.atan2(velocity.y, horizontal) * (180.0D / Math.PI));
        this.setYRot(targetYaw);
        this.setXRot(targetPitch);
        this.yRotO = targetYaw;
        this.xRotO = targetPitch;
    }

    private void performSteppedCollisionSweep(Vec3 start, Vec3 end) {
        Vec3 initialTravel = end.subtract(start);
        double remainingDistance = initialTravel.length();
        if (remainingDistance <= STEP_COLLISION_SEGMENT_LENGTH) {
            return;
        }

        Vec3 segmentStart = start;
        Vec3 direction = initialTravel.normalize();
        int impacts = 0;
        long lastImpactedPosLong = Long.MIN_VALUE;

        while (remainingDistance > 1.0E-6D && impacts < STEP_COLLISION_MAX_IMPACTS_PER_TICK) {
            if (!this.isAlive()) {
                return;
            }
            if (direction.lengthSqr() < 1.0E-10D) {
                return;
            }

            double segmentLength = Math.min(STEP_COLLISION_SEGMENT_LENGTH, remainingDistance);
            Vec3 segmentEnd = segmentStart.add(direction.scale(segmentLength));
            BlockHitResult blockHitResult = this.level().clip(new ClipContext(
                    segmentStart,
                    segmentEnd,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    this
            ));

            if (blockHitResult.getType() == HitResult.Type.BLOCK) {
                BlockState state = this.level().getBlockState(blockHitResult.getBlockPos());
                if (!state.isAir()) {
                    long impactedPosLong = blockHitResult.getBlockPos().asLong();
                    if (impactedPosLong == lastImpactedPosLong) {
                        remainingDistance -= segmentLength;
                        segmentStart = segmentEnd;
                        continue;
                    }

                    Vec3 hitLocation = blockHitResult.getLocation();
                    double traversed = Math.max(0.0D, segmentStart.distanceTo(hitLocation));

                    this.setPos(blockHitResult.getLocation().x, blockHitResult.getLocation().y, blockHitResult.getLocation().z);
                    this.onHitBlock(blockHitResult);
                    impacts++;
                    lastImpactedPosLong = impactedPosLong;

                    remainingDistance -= Math.max(0.05D, traversed);
                    segmentStart = this.position();
                    if (!this.isAlive()) {
                        return;
                    }

                    Vec3 newVelocity = this.getDeltaMovement();
                    if (newVelocity.lengthSqr() < 1.0E-10D) {
                        return;
                    }
                    direction = newVelocity.normalize();
                    continue;
                }
            }

            remainingDistance -= segmentLength;
            segmentStart = segmentEnd;
        }
    }

    private int getAdaptiveMotionSyncInterval(double speed) {
        if (speed >= 40.0D) {
            return 1;
        }
        if (speed >= 15.0D) {
            return 2;
        }
        if (speed >= 5.0D) {
            return 3;
        }
        if (speed >= 1.0D) {
            return 5;
        }
        return 8;
    }

    private void syncPreciseStateFromServer(boolean forcePosition) {
        if (this.level().isClientSide || !(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        Vec3 position = this.position();
        Vec3 velocity = this.getDeltaMovement();
        double speed = velocity.length();
        int interval = this.getAdaptivePreciseSyncInterval(speed);
        int elapsedTicks = this.tickCount - this.lastPreciseSyncTick;

        if (!forcePosition && speed < PRECISE_SYNC_SPEED_THRESHOLD) {
            return;
        }
        if (!forcePosition && elapsedTicks < interval) {
            return;
        }
        if (!forcePosition
                && position.distanceToSqr(this.lastPreciseSyncPos) < PRECISE_SYNC_POSITION_EPSILON_SQR
                && velocity.distanceToSqr(this.lastPreciseSyncMotion) < MOTION_SYNC_EPSILON_SQR
                && elapsedTicks < Math.max(interval * 2, 6)) {
            return;
        }

        this.preciseSyncSeq++;
        CannonicalNetwork.sendCannonballPreciseSync(serverLevel, this, this.preciseSyncSeq, forcePosition);
        this.lastPreciseSyncPos = position;
        this.lastPreciseSyncMotion = velocity;
        this.lastPreciseSyncTick = this.tickCount;
    }

    private int getAdaptivePreciseSyncInterval(double speed) {
        if (speed >= 80.0D) {
            return 1;
        }
        if (speed >= 35.0D) {
            return 2;
        }
        if (speed >= PRECISE_SYNC_SPEED_THRESHOLD) {
            return 3;
        }
        return 6;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
