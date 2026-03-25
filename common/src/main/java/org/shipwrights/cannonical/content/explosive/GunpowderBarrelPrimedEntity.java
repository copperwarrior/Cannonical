package org.shipwrights.cannonical.content.explosive;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.shipwrights.cannonical.registry.ModEntityTypes;
import org.shipwrights.krakk.api.KrakkApi;
import org.shipwrights.krakk.api.explosion.KrakkExplosionProfile;

public class GunpowderBarrelPrimedEntity extends PrimedTnt {
    private static final String TAG_GUNPOWDER_CHARGE = "GunpowderCharge";
    // TNT reference: vanilla TNT is roughly equivalent to Krakk power=25.
    private static final double TNT_EQUIVALENT_POWER = 25.0D;
    private static final double MIN_IMPACT_HEAT_CELSIUS = 300.0D;
    private static final double MAX_IMPACT_HEAT_CELSIUS = 800.0D;

    private LivingEntity barrelOwner;
    private int gunpowderCharge;

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
    }

    @Override
    public void tick() {
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

        int nextFuse = this.getFuse() - 1;
        this.setFuse(nextFuse);
        if (nextFuse <= 0) {
            this.discard();
            if (this.level() instanceof ServerLevel serverLevel) {
                if (this.gunpowderCharge <= 0) {
                    return;
                }
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
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compoundTag) {
        super.readAdditionalSaveData(compoundTag);
        this.gunpowderCharge = GunpowderBarrelBlock.sanitizeCharge(compoundTag.getInt(TAG_GUNPOWDER_CHARGE));
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
