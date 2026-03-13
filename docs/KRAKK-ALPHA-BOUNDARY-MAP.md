# Krakk Alpha Boundary Map

## Purpose
Concrete `MOVE` / `WRAP` / `STAY` decisions for Phase A, based on the current Cannonical codebase.

## Classification Legend
1. `MOVE`: Class should be relocated into `:krakk` during migration.
2. `WRAP`: Class stays in Cannonical but should call Krakk API instead of Cannonical internals.
3. `STAY`: Class remains Cannonical-owned.

## MOVE (Krakk Ownership)
1. `common/src/main/java/org/shipwrights/cannonical/content/projectile/CannonBlockDamageSystem.java`
   - Reason: canonical damage simulation/state authority.
2. `common/src/main/java/org/shipwrights/cannonical/content/projectile/CannonBlockDamageChunkStorage.java`
   - Reason: chunk-local persistence format and memory model.
3. `common/src/main/java/org/shipwrights/cannonical/content/projectile/CannonBlockDamageChunkAccess.java`
   - Reason: chunk attachment API for damage storage.
4. `common/src/main/java/org/shipwrights/cannonical/content/projectile/FallingBlockDamageCarrier.java`
   - Reason: generic carrier interface for moved-block damage transfer.
5. `common/src/main/java/org/shipwrights/cannonical/content/explosive/CannonicalExplosions.java`
   - Reason: generic explosion simulation and raycast damage propagation.
6. `common/src/main/java/org/shipwrights/cannonical/client/CannonBlockDamageOverlayState.java`
   - Reason: generic client damage overlay state store and dirty-section tracking.
7. `common/src/main/java/org/shipwrights/cannonical/mixin/common/ChunkAccessMixin.java`
   - Reason: storage attachment injection for chunks.
8. `common/src/main/java/org/shipwrights/cannonical/mixin/common/ChunkSerializerMixin.java`
   - Reason: persistence load/save integration.
9. `common/src/main/java/org/shipwrights/cannonical/mixin/common/ChunkMapMixin.java`
   - Reason: chunk track sync/unload bridge.
10. `common/src/main/java/org/shipwrights/cannonical/mixin/common/LevelChunkMixin.java`
    - Reason: damage/placement transfer and proto->level chunk handoff.
11. `common/src/main/java/org/shipwrights/cannonical/mixin/common/LevelMixin.java`
    - Reason: clear/transfer behavior on block state changes.
12. `common/src/main/java/org/shipwrights/cannonical/mixin/common/PistonMovingBlockEntityMixin.java`
    - Reason: piston movement damage migration.
13. `common/src/main/java/org/shipwrights/cannonical/mixin/common/FallingBlockEntityMixin.java`
    - Reason: falling block damage migration/save/restore.
14. `common/src/main/java/org/shipwrights/cannonical/mixin/common/ServerPlayerGameModeMixin.java` (damage/mining baseline parts)
    - Reason: damage-aware mining progression baseline is generic to Krakk damage.
15. `common/src/main/java/org/shipwrights/cannonical/mixin/client/MultiPlayerGameModeMixin.java`
    - Reason: client mining baseline and destroy-delay behavior from damage.
16. `common/src/main/java/org/shipwrights/cannonical/mixin/client/MinecraftMixin.java` (overlay reset + instant-mine swing suppression parts)
    - Reason: client behavior parity tied to damage baseline system.
17. `common/src/main/java/org/shipwrights/cannonical/mixin/client/LevelRendererMixin.java`
    - Reason: damage overlay cached render pass.

## WRAP (Cannonical Uses Krakk API)
1. `common/src/main/java/org/shipwrights/cannonical/content/projectile/CannonballProjectileEntity.java`
   - Wrap targets:
   - block impact resolution should call `KrakkDamageApi`.
   - future explosion calls should use `KrakkExplosionApi` if needed.
2. `common/src/main/java/org/shipwrights/cannonical/content/explosive/GunpowderBarrelPrimedEntity.java`
   - Wrap target: call `KrakkExplosionApi.detonate(...)` instead of Cannonical-local explosion util.
3. `common/src/main/java/org/shipwrights/cannonical/content/explosive/GunpowderBarrelBlock.java`
   - Wrap target: keeps block behavior but delegates explosion mechanics to Krakk.
4. `common/src/main/java/org/shipwrights/cannonical/content/item/MalletItem.java`
   - Wrap target: delegates repair/damage queries to `KrakkDamageApi`.
5. `common/src/main/java/org/shipwrights/cannonical/command/CannonDamageCubeCommand.java`
   - Wrap target: debug command should call `KrakkDamageApi` debug setter.
6. `common/src/main/java/org/shipwrights/cannonical/command/GunpowderBarrelPowerCommand.java`
   - Wrap target: delegates power/radius control to `KrakkExplosionApi`.
7. `common/src/main/java/org/shipwrights/cannonical/network/CannonicalNetwork.java` (split)
   - Move block-damage packet lanes to Krakk.
   - Keep Cannonball precise sync in Cannonical.
8. `common/src/main/java/org/shipwrights/cannonical/Cannonical.java` (split wiring)
   - Keep Cannonical bootstrap.
   - Delegate damage/explosion event wiring and network init to Krakk entrypoints.

## STAY (Cannonical Ownership)
1. `common/src/main/java/org/shipwrights/cannonical/content/cannon/TwentyFourPounderCannonBlock.java`
2. `common/src/main/java/org/shipwrights/cannonical/content/cannon/TwentyFourPounderCannonBlockEntity.java`
3. `common/src/main/java/org/shipwrights/cannonical/content/projectile/CannonballItem.java`
4. `common/src/main/java/org/shipwrights/cannonical/content/enchantment/CarpenterEnchantment.java`
5. `common/src/main/java/org/shipwrights/cannonical/command/CannonballCommand.java`
6. `common/src/main/java/org/shipwrights/cannonical/client/render/entity/GunpowderBarrelPrimedRenderer.java`
7. Cannonical registries, data assets, textures, models, lang files, recipes, and tags remain Cannonical-owned.

## Split Notes
1. `ServerPlayerGameModeMixin` currently includes both mallet cancel-on-break behavior and damage-mining baseline behavior.
   - Krakk: mining baseline logic.
   - Cannonical: mallet repair interception.
2. `MinecraftMixin` currently includes overlay reset and instant-mine swing skip.
   - Krakk: both behaviors if tied to Krakk overlay state.
   - Cannonical: no custom logic needed after delegation.
3. `CannonicalNetwork` requires packet-lane split.
   - Krakk packet lane: block damage sync, section snapshot, chunk unload.
   - Cannonical packet lane: cannonball precise sync.

## Phase B Handoff Checklist
1. Add `:krakk` module and package namespace `org.shipwrights.krakk`.
2. Create API interfaces from `KRAKK-MIGRATION.md` Phase A contract.
3. Add Cannonical adapters that forward to Krakk while preserving existing call sites.
4. Move one vertical slice first: damage storage + sync + overlay state.
