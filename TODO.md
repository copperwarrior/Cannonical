# TODO

## Pending
- [ ] Cross dimensional explosions.
- [ ] Heat value for damage + detonations to determine various effects

## Full Pipeline Perf Candidates (impactApply -> visible result, sorted by potential gain)
- [ ] Add break-path profiling split in damage runtime (`impactCalc`, `breakAttempt`, `breakFallbackSet`, `sync`) to isolate true break cost under live `applyImpact` profiling. Parity risk: none.
- [ ] Damage-runtime fast break short-circuit: if computed added-state reaches break threshold, skip damage-state read and break directly. Parity risk: very low.
- [ ] Remove duplicate chunk-storage lookups in prevalidated impact path by threading resolved storage through apply path. Parity risk: none.
- [ ] Avoid redundant live-state reread in `setDamageState` / `applyImpact` when prevalidated state is already known. Parity risk: low.
- [ ] Apply impacts grouped by chunk/section for storage locality and sync coherence while preserving deterministic per-block outcomes. Parity risk: low-medium (ordering-sensitive interactions).
- [ ] Multithreaded pre-processing only: build chunk/section impact buckets off-thread, then keep all world writes on server thread. Parity risk: low.
- [ ] Multithreaded sync prep only: parallelize section payload materialization for large batched flushes, main-thread send remains ordered. Parity risk: low-medium.
- [ ] Revisit structural collapse optimization with parity-safe data-structure/runtime tuning only (no candidate-scope reduction). Parity risk: low-medium.
- [ ] Calibrate full-pipeline profiler knobs (`sampleStep`, `maxImpact`, `dropOnBreak`) against real explosion impact mix so profiler workload tracks production path. Parity risk: profiler-only.
- [x] Add impactApply substage profiling split (`directApply`, `collapseSeed`, `collapseBfs`, `collapseApply`) to isolate hotspots. Parity risk: none. Status: implemented, kept.
- [x] Skip damage-state conversion handler invocation entirely when handler is `NOOP` (default) to remove pure overhead in debug/apply loops. Parity risk: none. Status: implemented in `setDamageStateWithResolvedStorage`.

## Damage Sync / Network Fixes
- [x] Add explicit bulk-damage sync mode in `KrakkDamageRuntime` (begin/end) that buffers dirty positions instead of immediate per-block sync. Parity risk: low. Status: implemented (`beginBulkSync/endBulkSync/runInBulkSync`), wired into `profdamage`.
- [x] Flush buffered dirty state by section snapshot (or chunk cache dirty-notify) when dirty count crosses threshold; keep per-block sync for low-volume edits. Parity risk: low. Status: implemented as section-delta fallback when chunk-cache access is unavailable.
- [x] Add configurable fanout thresholds (`perBlockLimit`, `sectionSnapshotLimit`) and log which sync path is selected for profiling visibility. Parity risk: none. Status: implemented in `KrakkDamageRuntime` bulk flush routing (`System` properties + runtime sync route counters).
- [x] Ensure integrated server path uses chunk-cache dirty-notify if available before packet fallback (`markDamageStateChanged` currently packet-dominant). Parity risk: none. Status: implemented with interface + direct method probe path before packet fallback.
- [x] Coalesce duplicate updates to same block within a bulk pass (apply+clear) before emitting sync payloads. Parity risk: low. Status: implemented by deduplicating same-state dirty writes in `SyncBatchContext` and reporting `syncCoalesced`.
- [x] Add section-resolved bulk clear API for mass debug/profiler clears and wire `profdamage` pre/post clear to use it. Parity risk: low. Status: implemented (`clearDamageStatesBulk`) and integrated in `clearDamageProfileTargets`.
- [ ] Add one-shot `profdamage` bulk-safe mode that exercises real damage API writes but suppresses network emission until flush stage for diagnosis. Parity risk: diagnostic-only.

## Legacy Bulk-Debug Sync Candidates (Archived)
- [x] P1 (High): Add a true bulk apply API in damage runtime (section-oriented state writes + one sync flush per section group) to cut per-block method/lookup overhead. Parity risk: low if final states are identical. Status: implemented (`setDamageStatesForDebugBulk`), wired into `profdamage` apply path.
- [x] P2 (High): Parallelize section-state snapshot materialization for large bulk flushes (`sectionSnapshot` route) with bounded worker pool; main thread only performs final sends. Parity risk: low-medium; keep world reads on safe path. Status: implemented with bounded `ForkJoinPool` snapshot-copy workers and main-thread send stage.
- [x] P3 (Medium-High): Store bulk sync dirty state by section incrementally during writes (avoid rebuilding section maps at flush). Parity risk: low. Status: implemented in `SyncBatchContext` (`dirtySections`) and consumed directly by section-batch flush; hot-path tracking now adaptively switches from per-block to section mode after threshold.
- [x] P4 (Medium-High): Parallelize section serialization/payload construction in bulk flush (main-thread send only after payloads built). Parity risk: low if send order is preserved. Status: implemented (`serializeSectionSnapshotPayload`/`serializeSectionDeltaPayload` + runtime prepared-batch parallel payload path with in-order sends).
- [ ] P5 (Medium): Tune `sectionSnapshotLimit` for workload fit (more delta vs more snapshot) and lock in best threshold for debug bulk-write paths. Parity risk: none. Status: paused while full-pipeline `profdamage` is active.
- [x] P6 (Medium): Worker pre-grouping: build `bySection` buckets off-thread from an immutable copied dirty map before send stage. Parity risk: low-medium; validate allocation overhead vs gain. Status: tested, regression, reverted.
- [ ] P7 (Medium-Low): Investigate why cache-route stays `0` during `profdamage` and enable chunk-cache notify route where available. Parity risk: low. Status: implemented adaptive cache-route gating + chunk-holder notify fallback path; latest `profdamage` still reports `cache=0` (route not exercised in this workload).
- [x] P8 (Low): Parallel recipient filtering per section (view-distance cull precompute) with bounded workers and deterministic merge. Parity risk: low. Status: tested in single-player harness, not practically verifiable there, reverted and dropped.
- [ ] P9 (Diagnostic): Add profiling mode switch to skip `preClear` when not needed for strict reproducibility; use for lower-overhead timing passes. Parity risk: diagnostic-only.
