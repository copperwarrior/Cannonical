# TODO

## Pending
- [ ] Cross dimensional explosions.
- [ ] Heat value for damage + detonations to determine various effects
- [ ] Implement ton, kiloton, megaton units
- [ ] Entity damage
- [ ] Entity knockback with wave pressure sim
- [ ] Entities affecting resistance field
- [ ] Blast Resistance enchantment affecting resistance field
- [ ] VS2 Ship knockback with wave pressure sim
- [ ] Damage type split: overpressure, debris, thermal
- [ ] Directional blasts and shaped-charge behavior
- [ ] Falling block conversion, data driven
- [ ] Shielding affecting blast attenuation/resistance
- [ ] Chain-reaction logic for volatile blocks/entities with cooldown safeguards
- [ ] Perf: Replace volumetric pressure blending O(directionCount) scan with lookup+neighbor blend in `sampleBlendedVolumetricPressure`
- [ ] Perf: Add fast radial early-out in `scanVolumetricTargetScanChunk` before expensive edge/noise math
- [ ] Perf: Reduce transcendental (`pow`) cost in volumetric scan hot loop via LUT/approximation
- [ ] Perf: Reduce Krakk sweep task churn (raise parallel threshold / coarser sweep task sizing)
- [ ] Perf: Optimize block impact break path allocations (`applySingleBlockImpact` / `breakDamagedBlockExplosionStyle`)
