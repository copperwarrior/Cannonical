# Alpha Phase Plan: 24-Pounder + Basic Cannonball

## Scope (Alpha Only)
- Deliver one playable vertical slice:
- `24-pounder cannon` with aiming and reload behavior.
- `cannonball` ammo item and projectile.
- Server-authoritative fire flow that works in singleplayer and dedicated multiplayer.
- Use existing assets:
- Cannon model: `common/src/main/resources/assets/models/block/cannons/24-pound-cannon.json`
- Cannonball texture: `common/src/main/resources/assets/textures/items/cannonball.png`

Out of scope for Alpha:
- Other cannon types (4-pounder, 32-pounder)
- Other ammo types (grape, chain, flame, specials)
- Deep balancing pass and full VFX polish

## Architectural Direction
- Keep gameplay logic in `common` (Architectury shared code).
- Keep platform entrypoint code in `fabric`/`forge` minimal.
- Use registries + data-driven resources first, then behavior.
- Make server the source of truth for:
- Cannon aim clamp validation
- Reload state
- Projectile spawn and launch parameters

## Phase Breakdown

### Alpha-A: Core Registrations and Data
Goal: make all core content register and load cleanly.

Tasks:
- Create registry classes in `common` for:
- Items (`cannonball`)
- Blocks (`24-pounder cannon`)
- Block entities (`24-pounder cannon` state container)
- Entity types (`cannonball projectile`)
- Add missing resource JSONs:
- Item model for cannonball item
- Blockstate + item model for 24-pounder cannon
- Basic lang entries
- Wire registration calls from `Cannonical.init()`.

Definition of done:
- Game boots on Fabric and Forge with no registry errors.
- Item appears in creative tabs (or test command gives item).
- Cannon block places with correct model and pick block behavior.

### Alpha-B: Cannonball Projectile
Goal: get projectile physics and impact behavior working.

Tasks:
- Implement `CannonballEntity` with:
- Initial velocity based on cannon orientation
- Gravity and drag tuned for a standard artillery arc
- Collision handling with entity/block hit results
- On impact:
- Damage entities in direct hit
- Spawn basic particle/sound event
- Despawn safely
- Add client entity renderer (simple item/projectile render is enough).

Definition of done:
- Firing spawns visible projectile.
- Projectile travels, collides, damages, and despawns consistently.
- No crash/desync when many projectiles are fired.

### Alpha-C: 24-Pounder Interaction Loop
Goal: complete player interaction: aim -> load -> fire -> reload.

Tasks:
- Implement cannon block + block entity behavior:
- Store yaw/pitch and reload cooldown
- Validate aim limits (`45° yaw`, `90° pitch`) on server
- Player interactions:
- Right-click with cannonball to load ammo
- Fire action (interaction or dedicated trigger item/keybinding fallback)
- Start and enforce reload cooldown after fire
- Prevent fire when unloaded or cooling down
- Add basic sync packets/state updates for aim, load state, reload timer.

Definition of done:
- Full loop works: load -> fire -> cooldown -> ready.
- Client visuals/state match server truth.
- Basic anti-duplication protections in place (consume ammo once).

### Alpha-D: Validation and Hardening
Goal: reduce obvious multiplayer and lifecycle bugs before Beta.

Tasks:
- Add focused tests/checklist runs:
- Singleplayer smoke test
- Dedicated server with 2 players
- Rapid-fire + chunk boundary tests
- Verify edge cases:
- Cannon broken during reload
- Player disconnect mid-load
- Projectile owner null/invalid handling
- Stabilize logs (no repeating warnings/errors during normal use).

Definition of done:
- No known crashers in normal cannon usage.
- No known ammo dupe in expected use paths.
- Alpha demo scenario is repeatable.

## Implementation Order (Execution Queue)
1. Registries and content scaffolding (Alpha-A)
2. Projectile entity and renderer (Alpha-B)
3. Cannon block entity + interaction logic (Alpha-C)
4. Sync, constraints, and reload authority (Alpha-C)
5. Multiplayer smoke and bug hardening (Alpha-D)

## Proposed File Targets
- `common/src/main/java/org/shipwrights/cannonical/Cannonical.java`
- `common/src/main/java/org/shipwrights/cannonical/registry/*`
- `common/src/main/java/org/shipwrights/cannonical/content/cannon/*`
- `common/src/main/java/org/shipwrights/cannonical/content/projectile/*`
- `common/src/main/resources/assets/cannonical/blockstates/*`
- `common/src/main/resources/assets/cannonical/models/block/*`
- `common/src/main/resources/assets/cannonical/models/item/*`
- `common/src/main/resources/assets/cannonical/lang/en_us.json`
- `fabric/src/main/java/org/shipwrights/cannonical/fabric/client/*` (renderer hookup)
- `forge/src/main/java/org/shipwrights/cannonical/forge/*` (if platform hooks needed)

## Alpha Acceptance Checklist
- [ ] Place 24-pounder cannon in world.
- [ ] Aim respects `45° yaw` and `90° pitch` constraints.
- [ ] Load cannonball using inventory ammo.
- [ ] Fire launches visible cannonball with stable trajectory.
- [ ] Cannonball impact deals damage and cleans up entity.
- [ ] Reload cooldown prevents immediate refire.
- [ ] Behavior matches in singleplayer and dedicated server.
- [ ] No obvious dupes/crashes in 10-minute stress test.

## Risks and Mitigations
- Risk: Cross-platform registration differences.
- Mitigation: Keep registry + behavior in `common`; only renderer/platform glue outside.
- Risk: Client/server state mismatch for cooldown and loaded state.
- Mitigation: Server-authoritative state with explicit sync packets.
- Risk: Cannon model orientation mismatch.
- Mitigation: Normalize canonical block facing and convert to yaw/pitch in one utility.
