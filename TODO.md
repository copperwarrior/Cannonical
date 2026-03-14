# TODO

## Pending
- [ ] Cross dimensional explosions.
- [ ] Heat value for damage + detonations to determine various effects

## Explosion Perf Options
- [x] Precompute analytic air-arrival per active voxel and reuse in eikonal target scan. Parity risk: none. Status: tested, no meaningful gain/regressed core stages, reverted.
- [x] Replace `volumetricBaselineByPos` hash lookups with index-aligned baseline arrays in eikonal target scan. Parity risk: none. Status: tested, perf win, kept.
- [x] Store solid voxel field indices during field build to avoid per-target `BlockPos` decode and grid re-index in scan. Parity risk: none. Status: tested, perf regression, reverted.
- [x] Convert volumetric resistance sampling path from `Long2FloatOpenHashMap` lookups to dense grid indexing. Parity risk: none. Status: tested (`resFieldDense=v2`), perf regression, reverted.
- [ ] Make eikonal sweep worker count adaptive above 2 workers with size gating. Parity risk: none.
- [ ] Add changed-row/frontier tracking for fine refine sweeps while preserving same epsilon/stop semantics. Parity risk: very low.
- [ ] Narrow structural collapse candidate set to impacted-region bounds instead of all solids in radius volume. Parity risk: low-medium.
- [ ] Reintroduce parallel resistance sampling only through snapshot path with stricter task sizing/fallbacks. Parity risk: none if snapshot-only.
