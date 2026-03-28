package org.shipwrights.cannonical.content.explosive;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityInLevelCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import net.minecraft.world.level.gameevent.GameEvent;
import org.valkyrienskies.core.api.ships.PhysShip;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.core.api.ships.ShipPhysicsListener;
import org.valkyrienskies.core.api.ships.properties.ShipInertiaData;
import org.valkyrienskies.core.api.ships.properties.ShipTransform;
import org.valkyrienskies.core.api.world.PhysLevel;
import org.valkyrienskies.core.api.world.ServerShipWorld;
import org.valkyrienskies.core.internal.VsiCore;
import org.valkyrienskies.core.internal.physics.PhysicsEntityData;
import org.valkyrienskies.core.internal.physics.PhysicsEntityServer;
import org.valkyrienskies.core.internal.physics.RigidBodyDefaults;
import org.valkyrienskies.core.internal.physics.VSBoxCollisionShapeData;
import org.valkyrienskies.core.internal.world.VsiServerShipWorld;
import org.valkyrienskies.mod.api.ValkyrienSkies;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;

/**
 * Optional VS2 physics-entity integration for {@link GunpowderBarrelPrimedEntity}.
 *
 * <p>When VS2 is present, the barrel is registered as a Krunch rigid body so it rolls and
 * slides on ship decks and receives proper collision responses from ship geometry.
 * Position is driven by the physics simulation each server tick; the fuse countdown
 * continues as normal.
 *
 * <p>All VS2 class references are contained in this file; it is only loaded after
 * {@code KrakkVS2Support.isPresent()} confirms VS2 is on the runtime classpath.
 */
final class CannonicalVS2BarrelPhysics {

    private static final String TAG_PHYS_DATA = "vs2_phys_data";

    /**
     * Box side length = entity bounding box side (0.98).
     * The box center sits at entity feet + BARREL_HALF; when resting on flat ground
     * the physics engine places the center at world_y = BARREL_HALF, which maps back
     * to entity feet at world_y = 0 via the offset in {@link #tickPhysics}.
     */
    private static final double BARREL_SIDE = 0.99; // slightly under full block to avoid precision-induced starting velocity
    private static final double BARREL_HALF = BARREL_SIDE / 2.0; // 0.5

    /** Barrel mass in kg — tuned for a full wooden gunpowder barrel. */
    private static final double BARREL_MASS = 400.0;

    // Physical material properties matching a heavy wooden barrel.
    private static final double STATIC_FRICTION  = 0.6;
    private static final double DYNAMIC_FRICTION = 0.5;
    private static final double RESTITUTION      = 0.1; // low bounce

    // Flowing water push: F = m × (Δv/Δt) = m × flow × vanilla_scale × 20 ticks/s.
    // Vanilla applies Δv = flow × 0.014 per tick; converting to a continuous force:
    //   F = BARREL_MASS × 0.014 × 20 = BARREL_MASS × 0.28 N per unit flow vector.
    private static final double WATER_PUSH_FORCE_SCALE = BARREL_MASS * 0.07 * 20.0;

    private CannonicalVS2BarrelPhysics() {
    }

    /**
     * Applies flowing-water push each physics tick.
     * Buoyancy and fluid drag are handled natively by VS2 2.5.0+.
     *
     * <p>{@code flowX/Y/Z} is the summed {@link net.minecraft.world.level.material.FluidState#getFlow}
     * across all overlapping water blocks, written by the game thread in {@link #tickPhysics}
     * and read by the physics thread via {@code volatile} fields.
     */
    private static final class BarrelBuoyancyListener implements ShipPhysicsListener {
        /** Summed fluid flow vector from overlapping water blocks; written by the game thread. */
        volatile double flowX = 0.0, flowY = 0.0, flowZ = 0.0;

        @Override
        public void physTick(PhysShip physShip, PhysLevel physLevel) {
            double fx = flowX, fy = flowY, fz = flowZ;
            if (fx == 0.0 && fy == 0.0 && fz == 0.0) return;
            Vector3dc comPos = physShip.getKinematics().getPosition();
            physShip.applyWorldForce(
                    new Vector3d(fx, fy, fz).mul(WATER_PUSH_FORCE_SCALE),
                    comPos);
        }
    }

    /**
     * Allocates a VS2 ship ID and builds a {@link PhysicsEntityData} for a freshly-spawned barrel
     * with an identity initial rotation.
     *
     * <p>Only the physics data is created here; the actual rigid body is registered in
     * {@link #onSetLevelCallback} once the entity is added to the level.
     */
    static void initForSpawn(GunpowderBarrelPrimedEntity entity, ServerLevel level,
                             double x, double y, double z) {
        initForSpawn(entity, level, x, y, z, new Quaterniond());
    }

    /**
     * Allocates a VS2 ship ID and builds a {@link PhysicsEntityData} for a freshly-spawned barrel
     * with the given initial rotation. Used when the barrel originates from a VS2 ship so it
     * inherits the ship's heading at the moment of ignition.
     */
    static void initForSpawn(GunpowderBarrelPrimedEntity entity, ServerLevel level,
                             double x, double y, double z, Quaterniondc initialRot) {
        ServerShipWorld sw = ValkyrienSkies.getShipWorld(level.getServer());
        if (sw == null) return;

        VsiServerShipWorld vsi = (VsiServerShipWorld) sw;
        String dimId = ValkyrienSkies.getDimensionId(level);
        long shipId = vsi.allocateShipId(dimId);

        VsiCore vsCore = ValkyrienSkiesMod.getVsCore();
        // Box center = entity feet + half-height so the box bottom aligns with entity feet.
        ShipTransform transform = vsCore.newShipTransform(
                new Vector3d(x, y + BARREL_HALF, z), // box center in world space
                new Vector3d(0.0, 0.0, 0.0),         // position in model (origin)
                new Quaterniond(initialRot),           // initial rotation (ship heading or identity)
                new Vector3d(1.0, 1.0, 1.0)          // uniform scale
        );

        // Inertia tensor for a uniform solid box: I_axis = m/12 * (a² + b²)
        // where a, b are the side lengths perpendicular to that axis.
        double sideSq = BARREL_SIDE * BARREL_SIDE;
        double inertia = BARREL_MASS / 12.0 * (sideSq + sideSq); // cube: all axes equal
        ShipInertiaData inertiaData = vsCore.newShipInertiaData(
                new Vector3d(0.0, 0.0, 0.0),          // center of mass at origin
                BARREL_MASS,
                new Matrix3d().m00(inertia).m11(inertia).m22(inertia)
        );

        // VS2 box shape lengths are half-extents; BARREL_HALF (0.5) gives a full 1×1×1 box.
        VSBoxCollisionShapeData boxShape = new VSBoxCollisionShapeData(
                BARREL_HALF, BARREL_HALF, BARREL_HALF);

        PhysicsEntityData data = new PhysicsEntityData(
                shipId,
                transform,
                inertiaData,
                new Vector3d(), // zero linear velocity
                new Vector3d(), // zero angular velocity
                boxShape,
                RigidBodyDefaults.DEFAULT_COLLISION_MASK,
                STATIC_FRICTION,
                DYNAMIC_FRICTION,
                RESTITUTION,
                false
        );

        entity.vs2PhysicsData = data;
        entity.setPhysicsShipId(shipId);
    }

    /**
     * Called from {@link GunpowderBarrelBlock#prime} when VS2 is present.
     *
     * <p>If {@code blockPos} in {@code level} is a block that belongs to a VS2 ship
     * (i.e. it is a position in the shipyard dimension), this method:
     * <ol>
     *   <li>Transforms the block position to world-space using the ship's current transform.</li>
     *   <li>Captures the ship's rotation as the barrel's initial physics-body orientation.</li>
     *   <li>Spawns the primed entity in the correct game-world {@link ServerLevel} at the
     *       world-space position — never in the shipyard.</li>
     * </ol>
     *
     * @return {@code true} if the entity was spawned here (caller must not spawn again),
     *         {@code false} if the block is not in the shipyard (caller should proceed normally).
     */
    static boolean tryPrimeOnShip(Level level, BlockPos blockPos,
                                   LivingEntity igniter, int fuseTicks, int gunpowderCharge) {
        if (!(level instanceof ServerLevel serverLevel)) return false;

        // Bail out early if this block is not in the VS2 shipyard dimension.
        if (!ValkyrienSkies.isBlockInShipyard(level, blockPos.getX(), blockPos.getY(), blockPos.getZ())) {
            return false;
        }

        // Look up the ship that owns this block via chunk-claim index (shipyard coords match).
        Ship ship = ValkyrienSkies.getShipManagingBlock(level, blockPos);
        if (ship == null) return false;

        // Transform the block's bottom-center from shipyard space to world space.
        // Entity spawn position is the block's bottom-center: (x+0.5, y+0.0, z+0.5).
        Vector3d worldSpawnPos = new Vector3d();
        ship.getShipToWorld().transformPosition(
                blockPos.getX() + 0.5, (double) blockPos.getY(), blockPos.getZ() + 0.5,
                worldSpawnPos);

        // Capture the ship's current rotation so the barrel inherits the ship's heading.
        Quaterniondc shipRot = ship.getTransform().getShipToWorldRotation();

        // Find the game-world ServerLevel the ship visually exists in.
        ServerLevel hostLevel = findShipHostLevel(serverLevel, ship);

        GunpowderBarrelPrimedEntity primed = new GunpowderBarrelPrimedEntity(
                hostLevel,
                worldSpawnPos.x, worldSpawnPos.y, worldSpawnPos.z,
                igniter,
                GunpowderBarrelBlock.sanitizeCharge(gunpowderCharge),
                shipRot);
        primed.setFuse(Math.max(1, fuseTicks));
        hostLevel.addFreshEntity(primed);
        hostLevel.gameEvent(igniter, GameEvent.PRIME_FUSE,
                BlockPos.containing(worldSpawnPos.x, worldSpawnPos.y, worldSpawnPos.z));
        return true;
    }

    /**
     * Returns the game-world {@link ServerLevel} that visually hosts the given ship
     * (not the shipyard dimension).
     *
     * <p>Iterates all loaded server levels and uses a spatial query to find which level
     * "sees" the ship at its current world-space position. Falls back to the overworld
     * if no match is found — ships in Cannonical are always in the overworld.
     */
    private static ServerLevel findShipHostLevel(ServerLevel shipyardLevel, Ship ship) {
        var server = shipyardLevel.getServer();
        String shipyardDimId = ValkyrienSkies.getDimensionId(shipyardLevel);
        Vector3dc com = ship.getTransform().getPosition();

        for (ServerLevel candidate : server.getAllLevels()) {
            if (ValkyrienSkies.getDimensionId(candidate).equals(shipyardDimId)) continue;
            for (Ship s : ValkyrienSkies.getShipsIntersecting(candidate, com.x(), com.y(), com.z())) {
                if (s.getId() == ship.getId()) return candidate;
            }
        }
        return server.overworld(); // fallback — ships in Cannonical are always in the overworld
    }

    /**
     * Registers or deregisters the VS2 rigid body when the entity is added to or removed
     * from the level.
     *
     * <p>Called from {@link GunpowderBarrelPrimedEntity#setLevelCallback}.
     */
    static void onSetLevelCallback(GunpowderBarrelPrimedEntity entity,
                                   EntityInLevelCallback callback) {
        if (entity.level().isClientSide) return;
        PhysicsEntityData data = (PhysicsEntityData) entity.vs2PhysicsData;
        if (data == null) return;

        ServerLevel level = (ServerLevel) entity.level();
        ServerShipWorld sw = ValkyrienSkies.getShipWorld(level.getServer());
        if (sw == null) return;
        VsiServerShipWorld vsi = (VsiServerShipWorld) sw;

        if (callback == EntityInLevelCallback.NULL) {
            // Entity is being removed — destroy the physics body.
            if (entity.vs2PhysicsServer != null) {
                try {
                    vsi.deletePhysicsEntity(data.getShipId());
                } catch (Exception ignored) {
                    // Tolerate double-removal (e.g. mid-detonation discard).
                }
                entity.vs2PhysicsServer = null;
                entity.vs2BuoyancyListener = null;
            }
        } else {
            // Entity is being added — register the physics body.
            if (entity.vs2PhysicsServer == null) {
                String dimId = ValkyrienSkies.getDimensionId(level);
                try {
                    PhysicsEntityServer server = vsi.createPhysicsEntity(data, dimId);
                    BarrelBuoyancyListener buoyancyListener = new BarrelBuoyancyListener();
                    server.getPhysicsListeners().add(buoyancyListener);
                    entity.vs2BuoyancyListener = buoyancyListener;
                    entity.vs2PhysicsServer = server;
                } catch (Exception ignored) {
                    // Gracefully degrade — barrel falls back to vanilla movement.
                }
            }
        }
    }

    /**
     * Reads position and rotation from the VS2 physics simulation and pushes them to the entity.
     * Position is converted from box-center to entity-feet by subtracting {@link #BARREL_HALF}.
     * Rotation is stored as quaternion XYZ components via synced entity data for client rendering.
     *
     * @return {@code true} if the physics body is active and the entity was updated
     */
    static boolean tickPhysics(GunpowderBarrelPrimedEntity entity) {
        PhysicsEntityServer server = (PhysicsEntityServer) entity.vs2PhysicsServer;
        if (server == null) return false;
        ShipTransform transform = server.getShipTransform();
        if (transform == null) return false;

        // Position: physics reports box center; entity position is box center - half-height.
        Vector3dc pos = transform.getPosition();
        entity.setPos(pos.x(), pos.y() - BARREL_HALF, pos.z());

        // Rotation: sync quaternion XYZ to the client via SynchedEntityData.
        Quaterniondc q = transform.getRotation();
        entity.setPhysicsRotation((float) q.x(), (float) q.y(), (float) q.z());

        // Compute flowing-water push force and pass it to the physics listener.
        BarrelBuoyancyListener bl = (BarrelBuoyancyListener) entity.vs2BuoyancyListener;
        if (bl != null) {
            AABB aabb = entity.getBoundingBox();
            double flowSumX = 0.0, flowSumY = 0.0, flowSumZ = 0.0;
            BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
            for (int bx = Mth.floor(aabb.minX); bx <= Mth.floor(aabb.maxX); bx++) {
                for (int by = Mth.floor(aabb.minY); by <= Mth.floor(aabb.maxY); by++) {
                    for (int bz = Mth.floor(aabb.minZ); bz <= Mth.floor(aabb.maxZ); bz++) {
                        mpos.set(bx, by, bz);
                        var fluidState = entity.level().getFluidState(mpos);
                        if (fluidState.is(FluidTags.WATER)) {
                            var flow = fluidState.getFlow(entity.level(), mpos);
                            flowSumX += flow.x;
                            flowSumY += flow.y;
                            flowSumZ += flow.z;
                        }
                    }
                }
            }
            bl.flowX = flowSumX;
            bl.flowY = flowSumY;
            bl.flowZ = flowSumZ;
        }

        return true;
    }

    /**
     * Converts a vanilla-style push (blocks/tick Δv) into a VS2 force for one physics tick
     * and submits it through the GTPA. Called when an external source (e.g. Krakk explosion
     * knockback via {@code entity.push()}) acts on the barrel while VS2 physics is active.
     *
     * <p>Vanilla push is in blocks/tick; converting to m/s: × 20 ticks/s.
     * To produce that Δv in one physics tick (60 Hz): F = m × Δv × 60.
     */
    static void applyExternalPush(GunpowderBarrelPrimedEntity entity, double dx, double dy, double dz) {
        PhysicsEntityData data = (PhysicsEntityData) entity.vs2PhysicsData;
        if (data == null) return;
        ServerLevel level = (ServerLevel) entity.level();
        ServerShipWorld sw = ValkyrienSkies.getShipWorld(level.getServer());
        if (sw == null) return;

        // push values are in blocks/tick = m/tick; × 20 → m/s; × 60 → force for one physics tick.
        double scale = BARREL_MASS * 20.0 * 60.0;
        Vector3d force = new Vector3d(dx * scale, dy * scale, dz * scale);
        if (!force.isFinite()) return;

        String dimId = ValkyrienSkies.getDimensionId(level);
        ValkyrienSkiesMod.getOrCreateGTPA(dimId).applyWorldForce(data.getShipId(), force, null);
    }

    /**
     * Serializes the {@link PhysicsEntityData} into {@code tag} so the physics body
     * configuration survives chunk unload and server restart.
     */
    static void savePhysicsData(GunpowderBarrelPrimedEntity entity, CompoundTag tag) {
        PhysicsEntityData data = (PhysicsEntityData) entity.vs2PhysicsData;
        if (data == null) return;
        try {
            ObjectMapper mapper = ValkyrienSkiesMod.getVsCore().getDefaultMapper();
            byte[] bytes = mapper.writeValueAsBytes(data);
            tag.putByteArray(TAG_PHYS_DATA, bytes);
        } catch (Exception ignored) {
        }
    }

    /**
     * Deserializes {@link PhysicsEntityData} from {@code tag}, restoring the physics body
     * across chunk reloads.
     */
    static void loadPhysicsData(GunpowderBarrelPrimedEntity entity, CompoundTag tag) {
        if (!tag.contains(TAG_PHYS_DATA)) return;
        try {
            ObjectMapper mapper = ValkyrienSkiesMod.getVsCore().getDefaultMapper();
            byte[] bytes = tag.getByteArray(TAG_PHYS_DATA);
            PhysicsEntityData data = mapper.readValue(bytes, PhysicsEntityData.class);
            entity.vs2PhysicsData = data;
            entity.setPhysicsShipId(data.getShipId());
        } catch (Exception ignored) {
        }
    }
}
