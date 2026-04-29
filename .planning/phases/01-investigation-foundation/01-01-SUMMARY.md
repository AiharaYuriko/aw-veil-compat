---
phase: 01-investigation-foundation
plan: 01
subsystem: mod-scaffold
tags: [scaffold, detection, mixin, build, source-analysis]
requires: []
provides: [DETECT-01, DETECT-02, VERSION-01]
affects: [Plan 01-02 (probes), Plan 03 (fix implementation)]
tech-stack:
  added:
    - NeoForge ModDevGradle 2.0.141 (build system)
    - Gradle 8.10 (wrapper)
    - JDK 21 toolchain
    - Mixin IMixinConfigPlugin (conditional gating)
  patterns:
    - Multi-mixin-config per target domain (core/AW/Veil)
    - FMLLoader.getLoadingModList() for early-load mod detection
    - ModDetector service with cached presence check
key-files:
  created:
    - .gitignore
    - source-verification.md
    - aw-veil-compat/build.gradle
    - aw-veil-compat/gradle.properties
    - aw-veil-compat/settings.gradle
    - aw-veil-compat/gradlew / gradlew.bat
    - aw-veil-compat/src/main/resources/META-INF/neoforge.mods.toml
    - aw-veil-compat/src/main/java/com/example/awveilcompat/AwVeilCompat.java
    - aw-veil-compat/src/main/java/com/example/awveilcompat/detection/ModDetector.java
    - aw-veil-compat/src/main/java/com/example/awveilcompat/mixin/AwVeilCompatMixinPlugin.java
    - aw-veil-compat/src/main/resources/awveilcompat.core.mixins.json
    - aw-veil-compat/src/main/resources/awveilcompat.aw.mixins.json
    - aw-veil-compat/src/main/resources/awveilcompat.veil.mixins.json
  modified: []
decisions:
  - Use ModDevGradle 2.0.141 (latest) instead of unavavailable 2.0.80-beta
  - Remove Mixin annotation processor dependency (handled by ModDevGradle automatically)
metrics:
  duration: 38 minutes
  completed_date: 2026-04-30
  tasks: 3/3
---

# Phase 1 Plan 1: Mod Scaffold with Source Analysis and Runtime Detection

Cloned AW and Veil source repos (develop and 1.21 branches), created a buildable NeoForge 21.1.222 mod project with ModDevGradle, implemented triple-layer fail-safe with MixinPlugin + ModDetector + 3 mixin config JSONs.

## Key Findings from Source Analysis

### AW (Armourer's Workshop)

| Finding | Detail |
|---------|--------|
| **Mod ID** | `armourers_workshop` (confirmed from forge mods.toml) |
| **Branch** | `develop` (contains all MC version targets including 21.1) |
| **clearRenderState target** | `RenderStateShard.class` (NOT `RenderType.class`) |
| **Injection point** | `@At("TAIL")` (runs after the shard's clear state logic) |
| **setupRenderState target** | `@At("HEAD")` (runs before the shard's setup logic) |
| **@Priority** | Not used (default Mixin priority = 1000) |
| **Uniform names** | `aw_ModelViewMatrix` (mat4), `aw_MatrixFlags` (int), `aw_TextureMatrix` (mat4) |
| **Uniform operations** | `ShaderUniform` class via `GL20.glGetUniformLocation()` + `GL20.glUniformMatrix4fv()` |
| **Loader** | Standard Forge (targets `mods.toml`, not `neoforge.mods.toml`) |

**CRITICAL DEVIATION from research assumptions:** AW targets `RenderStateShard` at TAIL, NOT `RenderType` at HEAD. This means:
- Our core probe must also target `RenderStateShard.clearRenderState()` with high priority to run BEFORE AW's TAIL injection
- AW's injection fires per-shard within the RenderType pipeline, not at the RenderType level

### Veil (FoundryMC)

| Finding | Detail |
|---------|--------|
| **Mod ID** | `veil` (confirmed from gradle.properties + `Veil.MODID` constant) |
| **Branch** | `1.21` (default) |
| **Stage registry** | `ForgeRenderTypeStageHandler.register()` (NOT `RenderTypeStageRegistry.addStage()`) |
| **Shader compiler** | `DirectShaderCompiler.compile(int type, VeilShaderSource source)` and `DirectShaderCompiler.compile(int type, ResourceLocation path)` |
| **Shader program** | `ShaderProgramImpl` with `compile()`, `bind()`, `recompile()`, `setActiveBuffers()` |
| **Program shard** | `ShaderProgramShard` extends `ShaderStateShard` - replaces active shader via `VeilRenderSystem.setShader()` |
| **Caching** | `CachedShaderCompiler` extends `DirectShaderCompiler` with `Int2ObjectMap<CompiledShader>` cache |
| **Mixin configs** | 19 separate mixin configs (command, debug, dynamicbuffer, fix, framebuffer, pipeline, shader, etc.) |

**DEVIATION from research assumptions:** No `RenderTypeStageRegistry` class exists. Veil uses `ForgeRenderTypeStageHandler` with `RenderLevelStageEvent.Stage` for stage management via NeoForge events.

## Deviations from Plan

### Rule 3 - Blocking Issues (Auto-fixed)

**1. ModDevGradle plugin version unavailable**
- **Found during:** Task 2, initial build
- **Issue:** Version `2.0.80-beta` specified in plan does not exist in the NeoForge Maven repository. Earliest 2.0.x version is `2.0.118`.
- **Fix:** Updated to `2.0.141` (latest available version)
- **Files modified:** `aw-veil-compat/build.gradle` line 3
- **Commit:** 1f6b25b

**2. ModDevGradle API change: property() renamed to systemProperty()**
- **Found during:** Task 2 build after fixing version
- **Issue:** The `property()` method does not exist on `RunModel` in ModDevGradle 2.0.x. The API uses `systemProperty()` instead.
- **Fix:** Changed `property` to `systemProperty` for mixin refmap JVM arguments
- **Files modified:** `aw-veil-compat/build.gradle` lines 21-22
- **Commit:** 1f6b25b

**3. Mixin annotation processor not resolvable**
- **Found during:** Task 3 build
- **Issue:** `org.spongepowered:mixin:0.15.5:processor` not found in NeoForge maven or Maven Central. The official ModDevGradle MDK template does not include this dependency — ModDevGradle handles Mixin annotation processing automatically.
- **Fix:** Removed the `annotationProcessor` dependency and the unnecessary `mavenCentral()` repository
- **Files modified:** `aw-veil-compat/build.gradle`
- **Commit:** 9731861

## Task Summary

| Task | Name | Commit | Key Files |
|------|------|--------|-----------|
| 1 | Clone AW and Veil source repos, verify mod IDs and injection targets | 45d8057 | `.gitignore`, `source-verification.md` |
| 2 | Create ModDevGradle project skeleton with version lock | 1f6b25b | `build.gradle`, `gradle.properties`, `settings.gradle`, `neoforge.mods.toml`, gradle wrapper |
| 3 | Create mod entry point, runtime detection, and MixinPlugin with 3 mixin configs | 9731861 | `AwVeilCompat.java`, `ModDetector.java`, `AwVeilCompatMixinPlugin.java`, 3 mixin JSONs |

## Verification Results

- [x] AW source cloned at D:\Software\CCode\AW\aw-source\ (develop branch)
- [x] Veil source cloned at D:\Software\CCode\AW\veil-source\ (1.21 branch)
- [x] AW mod ID verified: `armourers_workshop`
- [x] Veil mod ID verified: `veil`
- [x] Build succeeds at D:\Software\CCode\aw-veil-compat\ (BUILD SUCCESSFUL)
- [x] ModDetector caches mod presence at init()
- [x] MixinPlugin gates AW/Veil mixins by path (.mixin.aw., .mixin.veil.)
- [x] Three mixin config JSONs exist with correct structure (empty client lists)
- [x] neoforge.mods.toml registers all 3 mixin configs
- [x] gradle.properties locks NeoForge to 21.1.222 with [21.1,21.2) range
- [x] Compiled JAR contains all 3 mixin configs, compiled classes, and mod metadata

## Requirements Met

- **DETECT-01**: AW mod not present/loaded: compat mod does not crash or error — MixinPlugin returns false for .mixin.aw. paths when AW absent; ModDetector caches presence
- **DETECT-02**: Veil mod not present/loaded: compat mod does not crash or error — MixinPlugin returns false for .mixin.veil. paths when Veil absent; ModDetector caches presence
- **VERSION-01**: Exclusively NeoForge 21.1.222 — gradle.properties `neo_version=21.1.222`, `neo_version_range=[21.1,21.2)`

## Self-Check: PASSED

All 13 key files verified present. All 3 commits verified.
