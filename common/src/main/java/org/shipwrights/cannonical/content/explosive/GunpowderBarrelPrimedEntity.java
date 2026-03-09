package org.shipwrights.cannonical.content.explosive;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.Level;
import org.shipwrights.cannonical.registry.ModEntityTypes;
import org.shipwrights.krakk.api.KrakkApi;

public class GunpowderBarrelPrimedEntity extends PrimedTnt {
    private LivingEntity barrelOwner;

    public GunpowderBarrelPrimedEntity(EntityType<? extends PrimedTnt> entityType, Level level) {
        super(entityType, level);
        this.barrelOwner = null;
    }

    public GunpowderBarrelPrimedEntity(Level level, double x, double y, double z, LivingEntity owner) {
        super(ModEntityTypes.GUNPOWDER_BARREL_PRIMED.get(), level);
        this.setPos(x, y, z);
        double randomAngle = level.random.nextDouble() * (Math.PI * 2.0D);
        this.setDeltaMovement(-Math.sin(randomAngle) * 0.02D, 0.2F, -Math.cos(randomAngle) * 0.02D);
        this.setFuse(80);
        this.xo = x;
        this.yo = y;
        this.zo = z;
        this.barrelOwner = owner;
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
                KrakkApi.explosions().detonate(
                        serverLevel,
                        this.getX(),
                        this.getY(),
                        this.getZ(),
                        this,
                        this.barrelOwner,
                        null
                );
            }
        } else {
            this.updateInWaterStateAndDoFluidPushing();
            if (this.level().isClientSide) {
                this.level().addParticle(ParticleTypes.SMOKE, this.getX(), this.getY() + 0.5D, this.getZ(), 0.0D, 0.0D, 0.0D);
            }
        }
    }

}
