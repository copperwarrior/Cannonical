# TODO

## Pending
- [ ] Cross dimensional explosions.
- [ ] Heat value for damage + detonations to determine various effects
- [ ] Profile parity candidate: evaluate frontier/Fast-Marching style Eikonal solver (potential perf gain, requires parity validation).
- [ ] Profile parity candidate: optimize volumetric direction blend lookup path (exact/near-exact acceleration for `sampleBlendedVolumetricPressure`).
- [ ] Profile parity candidate: active-frontier fast-sweeping Eikonal updates with the same convergence epsilon/stop criterion.
- [ ] Profile parity candidate: parallel Eikonal solve (red-black/Jacobi style) with strict parity validation.

## Parity-Risk Perf Candidates
- [x] Reduce volumetric direction samples (for example `3072 -> 1536 -> 1024`) to lower `pressureSolve`/`targetScan`; tradeoff: more faceting and directional artifacts. (tested at `1536`: parity acceptable, net perf regression)
- [x] Reduce volumetric radial shell steps (for example `192 -> 128 -> 96`) to lower pressure propagation cost; tradeoff: coarser falloff and shell attenuation fidelity. (tested at `128`: parity acceptable, net perf win)
- [x] Remove dual-solve shadow Eikonal path and approximate shadow attenuation analytically; tradeoff: weaker occlusion realism behind dense cover. (tested: major parity failure, reverted)
- [ ] Add multi-resolution Eikonal solve (coarse solve + upsample + optional local refine); tradeoff: local shape drift and edge-detail loss.
- [ ] Increase minimum retained target-energy threshold; tradeoff: trims low-energy fringe damage and shrinks soft outer edge.
- [ ] Cap or stochastically sample low-weight target blocks; tradeoff: reduced micro-detail consistency in weakly affected regions.
- [ ] Reduce or disable volumetric baseline smoothing pass; tradeoff: noisier/blockier local results.
- [ ] Simplify direction blend from neighbor blend to nearest-direction sampling; tradeoff: stronger angular/cardinal artifacting.
