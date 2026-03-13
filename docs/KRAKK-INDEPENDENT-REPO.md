# Krakk Independent Repo Setup

This repo is configured so Cannonical can consume Krakk in two standard ways:
1. source-included submodule projects for local development (default)
2. published Maven artifacts for external builds

## Consumption modes
1. Source-included submodule (preferred for local development)
   - Keep a Krakk checkout available.
   - Cannonical `settings.gradle` maps these projects from `krakk/`:
     - `:krakkCommon` -> `krakk/common`
     - `:krakkFabric` -> `krakk/fabric`
     - `:krakkForge` -> `krakk/forge`
   - Override source path with:
     - `-PkrakkCheckoutPath=/absolute/path/to/Krakk`
2. Maven artifact
   - Publish Krakk to `mavenLocal()` or remote Maven from the Krakk repo.
   - Cannonical resolves by coordinates from `gradle.properties`:
     - `org.shipwrights.krakk:krakk-common:${krakk_version}`
     - `org.shipwrights.krakk:krakk-fabric:${krakk_version}`
     - `org.shipwrights.krakk:krakk-forge:${krakk_version}`

## Moving Krakk to its own git repo
1. Create/push a standalone Krakk repository from the `krakk/` directory.
2. In Cannonical, point to that checkout:
   - as a submodule at `krakk/`, or
   - as any local path with `-PkrakkCheckoutPath=/path/to/Krakk`.
3. Optional remote artifact flow:
   - set `krakk_maven_url` in Cannonical `gradle.properties`.

## Cannonical knobs
In `/gradle.properties`:
1. `krakk_group`
2. `krakk_artifact`
3. `krakk_version`
4. `krakk_maven_url` (optional)

## Notes
1. Cannonical does not depend on Krakk's root build script for local source consumption.
2. Krakk keeps its own `settings.gradle`, `gradle.properties`, and `build.gradle` for independent versioning and publishing.
