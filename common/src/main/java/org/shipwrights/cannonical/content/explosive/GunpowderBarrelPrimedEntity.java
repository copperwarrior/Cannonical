package org.shipwrights.cannonical.content.explosive;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.Level;
import org.shipwrights.cannonical.registry.ModEntityTypes;
import org.shipwrights.krakk.api.KrakkApi;
import org.shipwrights.krakk.api.explosion.KrakkExplosionProfile;

public class GunpowderBarrelPrimedEntity extends PrimedTnt {
    private static final String TAG_GUNPOWDER_CHARGE = "GunpowderCharge";
    private static final double BASE_VOLUMETRIC_RADIUS = 2.7D;
    private static final double RADIUS_PER_GUNPOWDER = 0.47D;
    private static final double BASE_VOLUMETRIC_ENERGY = 4.0D;
    private static final double ENERGY_PER_GUNPOWDER = 8.0D;

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
        this.setFuse(80);
        this.xo = x;
        this.yo = y;
        this.zo = z;
        this.barrelOwner = owner;
        this.gunpowderCharge = sanitizeCharge(gunpowderCharge);
    }

    @Override
    public void tick() {
        if (!this.isNoGravity()) {
            this.setDeltaMovement(this.getDeltaMovement().add(0.0D, -0.04D, 0.0D));
        }

        this.move(MoverType.SELF, this.getDeltaMovement());
        this.setDeltaMovement(this.getDeltaMovement().scale(0.98D));
        if (this.onGround()) {
            this.setDeltaMovement(this.getDeltaMovement().multiply(0.7D, -0.5D, 0.7D));
        }

        int nextFuse = this.getFuse() - 1;
        this.setFuse(nextFuse);
        if (nextFuse <= 0) {
            this.discard();
            if (this.level() instanceof ServerLevel serverLevel) {
                double blastRadius = BASE_VOLUMETRIC_RADIUS + (this.gunpowderCharge * RADIUS_PER_GUNPOWDER);
                double blastEnergy = BASE_VOLUMETRIC_ENERGY + (this.gunpowderCharge * ENERGY_PER_GUNPOWDER);
                KrakkApi.explosions().detonate(
                        serverLevel,
                        this.getX(),
                        this.getY(),
                        this.getZ(),
                        this,
                        this.barrelOwner,
                        KrakkExplosionProfile.volumetric(blastRadius, blastEnergy)
                );
            }
        } else {
            this.updateInWaterStateAndDoFluidPushing();
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
        this.gunpowderCharge = sanitizeCharge(compoundTag.getInt(TAG_GUNPOWDER_CHARGE));
    }

    private static int sanitizeCharge(int value) {
        return Math.max(0, Math.min(GunpowderBarrelBlock.MAX_GUNPOWDER_CHARGE, value));
    }
}
