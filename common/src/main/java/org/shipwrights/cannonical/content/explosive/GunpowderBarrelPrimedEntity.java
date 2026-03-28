package org.shipwrights.cannonical.content.explosive;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityInLevelCallback;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniondc;
import org.shipwrights.cannonical.registry.ModEntityTypes;
import org.shipwrights.krakk.api.KrakkApi;
import org.shipwrights.krakk.api.explosion.KrakkExplosionProfile;
import org.shipwrights.krakk.vs2.KrakkVS2Support;

public class GunpowderBarrelPrimedEntity extends PrimedTnt {
    private static final String TAG_GUNPOWDER_CHARGE = "GunpowderCharge";
    // TNT reference: vanilla TNT is roughly equivalent to Krakk power=25.
    private static final double TNT_EQUIVALENT_POWER = 25.0D;
    private static final double MIN_IMPACT_HEAT_CELSIUS = 300.0D;
    private static final double MAX_IMPACT_HEAT_CELSIUS = 800.0D;

    /**
     * Synced to the client so it knows VS2 physics is active and can suppress
     * vanilla movement. Empty string = no physics. Mirrors VSPhysicsEntity's approach
     * of using STRING because there is no built-in LONG serializer.
     */
    static final EntityDataAccessor<String> PHYSICS_SHIP_ID =
            SynchedEntityData.defineId(GunpowderBarrelPrimedEntity.class, EntityDataSerializers.STRING);

    /** Quaternion XYZ components synced for client-side rotation rendering. W is reconstructed. */
    static final EntityDataAccessor<Float> PHYSICS_ROT_X =
            SynchedEntityData.defineId(GunpowderBarrelPrimedEntity.class, EntityDataSerializers.FLOAT);
    static final EntityDataAccessor<Float> PHYSICS_ROT_Y =
            SynchedEntityData.defineId(GunpowderBarrelPrimedEntity.class, EntityDataSerializers.FLOAT);
    static final EntityDataAccessor<Float> PHYSICS_ROT_Z =
            SynchedEntityData.defineId(GunpowderBarrelPrimedEntity.class, EntityDataSerializers.FLOAT);

    // Client-side lerp state — mirrors VSPhysicsEntity.lerpPos / lerpSteps.
    private double vs2LerpX, vs2LerpY, vs2LerpZ;
    private int vs2LerpSteps = 0;
    private static final int VS2_CLIENT_LERP_STEPS = 3;

    private LivingEntity barrelOwner;
    private int gunpowderCharge;

    /**
     * Stores a {@code PhysicsEntityData} when VS2 is active.
     * Typed as {@code Object} to avoid loading VS2 classes when VS2 is absent.
     */
    Object vs2PhysicsData = null;

    /**
     * Stores a {@code PhysicsEntityServer} once the entity has been added to the level.
     * Null when VS2 is absent or before the entity enters the world.
     */
    Object vs2PhysicsServer = null;

    /**
     * Stores a {@code BarrelBuoyancyListener} once the physics body is registered.
     * The game thread writes water overlap into it each tick; the physics thread reads it.
     * Typed as {@code Object} to avoid loading VS2 classes when VS2 is absent.
     */
    Object vs2BuoyancyListener = null;

    public GunpowderBarrelPrimedEntity(EntityType<? extends PrimedTnt> entityType, Level level) {
        super(entityType, level);
        this.barrelOwner = null;
        this.gunpowderCharge = 0;
    }

    public GunpowderBarrelPrimedEntity(Level level, double x, double y, double z, LivingEntity owner) {
        this(level, x, y, z, owner, 0);
    }

    public GunpowderBarrelPrimedEntity(Level level, double x, double y, double z, LivingEntity owner, int gunpowderCharge) {
        super(ModEntityTypes.GUNPOWDER_BARREL_PRIMED.get(), level);
        this.setPos(x, y, z);
        double randomAngle = level.random.nextDouble() * (Math.PI * 2.0D);
        this.setDeltaMovement(-Math.sin(randomAngle) * 0.02D, 0.2F, -Math.cos(randomAngle) * 0.02D);
        this.setFuse(GunpowderBarrelBlock.DEFAULT_PRIME_FUSE);
        this.xo = x;
        this.yo = y;
        this.zo = z;
        this.barrelOwner = owner;
        this.gunpowderCharge = GunpowderBarrelBlock.sanitizeCharge(gunpowderCharge);

        if (!level.isClientSide && KrakkVS2Support.isPresent() && KrakkVS2Support.isKrunchClassicBackend()) {
            CannonicalVS2BarrelPhysics.initForSpawn(this, (ServerLevel) level, x, y, z);
        }
    }

    /**
     * Used by {@link CannonicalVS2BarrelPhysics#tryPrimeOnShip} when the source block is on a
     * VS2 ship. Position and rotation are already in world-space; the barrel is at rest
     * (no random kick) since VS2 physics drives movement from the moment of registration.
     */
    GunpowderBarrelPrimedEntity(Level level, double x, double y, double z,
                                 LivingEntity owner, int gunpowderCharge, Quaterniondc initialRot) {
        super(ModEntityTypes.GUNPOWDER_BARREL_PRIMED.get(), level);
        this.setPos(x, y, z);
        this.setDeltaMovement(0.0D, 0.0D, 0.0D); // at rest — VS2 physics drives movement
        this.setFuse(GunpowderBarrelBlock.DEFAULT_PRIME_FUSE);
        this.xo = x;
        this.yo = y;
        this.zo = z;
        this.barrelOwner = owner;
        this.gunpowderCharge = GunpowderBarrelBlock.sanitizeCharge(gunpowderCharge);

        if (!level.isClientSide && KrakkVS2Support.isPresent() && KrakkVS2Support.isKrunchClassicBackend()) {
            CannonicalVS2BarrelPhysics.initForSpawn(this, (ServerLevel) level, x, y, z, initialRot);
        }
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(PHYSICS_SHIP_ID, "");
        this.entityData.define(PHYSICS_ROT_X, 0.0f);
        this.entityData.define(PHYSICS_ROT_Y, 0.0f);
        this.entityData.define(PHYSICS_ROT_Z, 0.0f);
    }

    /** Called by {@link CannonicalVS2BarrelPhysics} to broadcast the ship ID to the client. */
    void setPhysicsShipId(long shipId) {
        this.entityData.set(PHYSICS_SHIP_ID, String.valueOf(shipId));
    }

    /** Called each server tick by {@link CannonicalVS2BarrelPhysics} to sync physics rotation. */
    void setPhysicsRotation(float qx, float qy, float qz) {
        this.entityData.set(PHYSICS_ROT_X, qx);
        this.entityData.set(PHYSICS_ROT_Y, qy);
        this.entityData.set(PHYSICS_ROT_Z, qz);
    }

    public boolean isPhysicsActive() { return !this.entityData.get(PHYSICS_SHIP_ID).isEmpty(); }
    public long getPhysicsShipId() {
        String s = this.entityData.get(PHYSICS_SHIP_ID);
        return s.isEmpty() ? -1L : Long.parseLong(s);
    }
    public float getPhysicsRotX() { return this.entityData.get(PHYSICS_ROT_X); }
    public float getPhysicsRotY() { return this.entityData.get(PHYSICS_ROT_Y); }
    public float getPhysicsRotZ() { return this.entityData.get(PHYSICS_ROT_Z); }

    /**
     * Redirects external pushes (e.g. Krakk explosion knockback) to the VS2 physics body
     * when physics is active, since deltaMovement is ignored in that mode.
     */
    @Override
    public void push(double x, double y, double z) {
        if (!this.level().isClientSide && isPhysicsActive() && KrakkVS2Support.isPresent()) {
            CannonicalVS2BarrelPhysics.applyExternalPush(this, x, y, z);
        } else {
            super.push(x, y, z);
        }
    }

    @Override
    public void setLevelCallback(EntityInLevelCallback callback) {
        super.setLevelCallback(callback);
        if (KrakkVS2Support.isPresent()) {
            CannonicalVS2BarrelPhysics.onSetLevelCallback(this, callback);
        }
    }

    /**
     * Intercepts server position updates when physics is active so they feed our
     * own lerp instead of vanilla's — matching the VSPhysicsEntity pattern exactly.
     */
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps, boolean onGround) {
        if (this.isPhysicsActive()) {
            this.vs2LerpX = x;
            this.vs2LerpY = y;
            this.vs2LerpZ = z;
            this.vs2LerpSteps = VS2_CLIENT_LERP_STEPS;
        } else {
            super.lerpTo(x, y, z, yRot, xRot, steps, onGround);
        }
    }

    @Override
    public void tick() {
        boolean physicsActive = this.isPhysicsActive();

        if (physicsActive) {
            if (this.level().isClientSide) {
                // Smooth interpolation toward the last server-sent position; no vanilla movement.
                if (vs2LerpSteps > 0) {
                    double x = this.getX() + (vs2LerpX - this.getX()) / vs2LerpSteps;
                    double y = this.getY() + (vs2LerpY - this.getY()) / vs2LerpSteps;
                    double z = this.getZ() + (vs2LerpZ - this.getZ()) / vs2LerpSteps;
                    --vs2LerpSteps;
                    this.setPos(x, y, z);
                }
            } else {
                // Server: VS2 physics drives position; no vanilla movement.
                CannonicalVS2BarrelPhysics.tickPhysics(this);
            }
        } else {
            // Vanilla movement — no VS2 physics present or not yet registered.
            this.updateInWaterStateAndDoFluidPushing();
            boolean inWater = this.isInWater();

            if (!this.isNoGravity() && !inWater) {
                this.setDeltaMovement(this.getDeltaMovement().add(0.0D, -0.04D, 0.0D));
            }
            if (inWater) {
                Vec3 movement = this.getDeltaMovement();
                double buoyantY = Math.min(0.02D, movement.y + 0.012D);
                this.setDeltaMovement(movement.x * 0.9D, buoyantY, movement.z * 0.9D);
            }

            this.move(MoverType.SELF, this.getDeltaMovement());
            this.setDeltaMovement(this.getDeltaMovement().scale(inWater ? 0.9D : 0.98D));
            if (this.onGround() && !inWater) {
                this.setDeltaMovement(this.getDeltaMovement().multiply(0.7D, -0.5D, 0.7D));
            }
        }

        int nextFuse = this.getFuse() - 1;
        this.setFuse(nextFuse);
        if (nextFuse <= 0) {
            if (this.level() instanceof ServerLevel serverLevel && this.gunpowderCharge > 0) {
                Vec3 detonationCenter = this.getBoundingBox().getCenter();
                emitDetonationParticles(serverLevel, detonationCenter.x, detonationCenter.y, detonationCenter.z, this.gunpowderCharge);
                double blastPower = TNT_EQUIVALENT_POWER * this.gunpowderCharge;
                double impactHeatCelsius = computeImpactHeatCelsius(this.gunpowderCharge);
                KrakkApi.explosions().detonate(
                        serverLevel,
                        detonationCenter.x,
                        detonationCenter.y,
                        detonationCenter.z,
                        this,
                        this.barrelOwner,
                        KrakkExplosionProfile.fromPower(blastPower, impactHeatCelsius)
                );
            }
            this.discard();
        } else {
            if (this.level().isClientSide) {
                this.level().addParticle(ParticleTypes.SMOKE, this.getX(), this.getY() + 0.5D, this.getZ(), 0.0D, 0.0D, 0.0D);
            }
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compoundTag) {
        super.addAdditionalSaveData(compoundTag);
        compoundTag.putInt(TAG_GUNPOWDER_CHARGE, this.gunpowderCharge);
        if (KrakkVS2Support.isPresent()) {
            CannonicalVS2BarrelPhysics.savePhysicsData(this, compoundTag);
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compoundTag) {
        super.readAdditionalSaveData(compoundTag);
        this.gunpowderCharge = GunpowderBarrelBlock.sanitizeCharge(compoundTag.getInt(TAG_GUNPOWDER_CHARGE));
        if (KrakkVS2Support.isPresent()) {
            CannonicalVS2BarrelPhysics.loadPhysicsData(this, compoundTag);
        }
    }

    private static double computeImpactHeatCelsius(int charge) {
        int clampedCharge = Math.max(1, GunpowderBarrelBlock.sanitizeCharge(charge));
        double progress = (clampedCharge - 1) / (double) (GunpowderBarrelBlock.MAX_GUNPOWDER_CHARGE - 1);
        return MIN_IMPACT_HEAT_CELSIUS + (progress * (MAX_IMPACT_HEAT_CELSIUS - MIN_IMPACT_HEAT_CELSIUS));
    }

    private static void emitDetonationParticles(ServerLevel level, double x, double y, double z, int charge) {
        int clampedCharge = GunpowderBarrelBlock.sanitizeCharge(charge);

        int explosionCount = 2 + (clampedCharge * 2);
        int explosionEmitterCount = 1 + (clampedCharge / 3);
        double explosionSpread = 0.25D + (clampedCharge * 0.06D);
        double explosionSpeed = 0.02D + (clampedCharge * 0.01D);

        int lavaCount = 12 + (clampedCharge * 10);
        double lavaSpread = 0.3D + (clampedCharge * 0.08D);
        double lavaSpeed = 0.12D + (clampedCharge * 0.03D);

        level.sendParticles(ParticleTypes.EXPLOSION, x, y + 0.2D, z,
                explosionCount, explosionSpread, explosionSpread * 0.6D, explosionSpread, explosionSpeed);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, x, y + 0.2D, z,
                explosionEmitterCount, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.LAVA, x, y + 0.25D, z,
                lavaCount, lavaSpread, lavaSpread * 0.7D, lavaSpread, lavaSpeed);
    }
}
