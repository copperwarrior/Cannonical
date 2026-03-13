# Krakk Phase H Validation

Date: 2026-03-09

## Scope
Phase H requires a regression matrix covering:
1. projectile block damage behavior
2. explosion propagation and block damage
3. chunk unload/reload persistence
4. client reconnect sync
5. piston/falling-block damage transfer
6. instant-mine and overlay behavior
7. overlay performance at high damaged-block counts

## What Was Run In This Environment
The current shell cannot run Gradle tasks (`gradlew` script not present and `gradle` binary not installed), so this pass executed static wiring and log validation only.

### Static wiring checks executed
1. Krakk runtime ownership and API routing:
   - Verified gameplay consumers route through `KrakkApi.damage()` / `KrakkApi.explosions()` / `KrakkApi.network()`.
2. Persistence and sync path:
   - Verified chunk read/write hooks in `KrakkChunkSerializerMixin`.
   - Verified chunk tracking sync/unload hooks in `KrakkChunkMapMixin`.
3. Transfer path:
   - Verified piston/falling transfer mixins and runtime methods are present and wired.
4. Client overlay path:
   - Verified section snapshot + dirty section consumption and section-cached renderer flow.
5. Log sanity check:
   - Reviewed `fabric/run/logs/latest.log` and `debug.log` for current startup; only auth/realms warnings observed, no active Krakk/Cannonical crash traces in latest run.

### Static check result
`PASS` for wiring-level validation.

## Repeatable Manual Runtime Script
Run these in-game (OP permissions required where noted):

1. Projectile damage baseline:
   - `/cannonball 5`
   - `/cannonball speed 30`
   - `/cannonball speed 80`
2. Damage cube setup:
   - `/cannondamagecube 10`
   - `/cannondamagecube 0`
3. Explosion power/radius checks:
   - `/gunpowderbarrelpower`
   - `/gunpowderbarrelpower 30`
   - `/setblock ~ ~ ~ cannonical:gunpowder_barrel`
   - Ignite the barrel and inspect break radius + damage propagation.
4. Persistence:
   - Apply damage with `/cannondamagecube 10`.
   - Save+quit to title.
   - Reload world and verify overlays + server behavior persist.
5. Reconnect sync:
   - Damage an area, disconnect, reconnect, verify overlays rehydrate.
6. Piston transfer:
   - Damage a block, push it with piston, verify damage follows moved block.
7. Falling block transfer:
   - Damage sand/gravel, let it fall, verify damage on landed block.
8. Mining/instant-mine behavior:
   - Compare high-damage vs low-damage stone with same tool.
   - Confirm instant-mine transition has no stuck desync.
9. Overlay stress:
   - Use `/cannondamagecube 10` in multiple nearby chunks.
   - Fly around and verify stable FPS and overlay updates.

## Signoff Matrix
1. Projectile block damage behavior: `STATIC PASS`, runtime pending.
2. Explosion propagation and block damage: `STATIC PASS`, runtime pending.
3. Chunk unload/reload persistence: `STATIC PASS`, runtime pending.
4. Client reconnect sync: `STATIC PASS`, runtime pending.
5. Piston/falling-block transfer: `STATIC PASS`, runtime pending.
6. Instant-mine and overlay behavior: `STATIC PASS`, runtime pending.
7. Overlay performance at high damaged-block counts: `STATIC PASS`, runtime pending.

Final runtime signoff must be completed in an environment that can launch Minecraft and execute Gradle tasks.
