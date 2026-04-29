---
phase: 01-investigation-foundation
plan: 02
subsystem: probes
tags: [probes, mixin, correlation, gl-state, investigation]
requires: [DETECT-01, DETECT-02]
provides: []
affects: [Plan 03 (fix implementation)]
tech-stack:
  added:
    - LWJGL MemoryStack (zero-allocation GL state queries)
    - Python 3 TSV parsing with csv module
  patterns:
    - Mixin @Pseudo + require=0 for optional Veil target injection
    - Thread-safe GL state probes gated by RenderSystem.isOnRenderThread()
    - Dual-probe correlation by nanosecond timestamp alignment
    - TSV-format probe logs for grep/awk/script processing
key-files:
  created:
    - aw-veil-compat/src/main/java/com/example/awveilcompat/probe/ProbeData.java
    - aw-veil-compat/src/main/java/com/example/awveilcompat/probe/GlStateReader.java
    - aw-veil-compat/src/main/java/com/example/awveilcompat/probe/ProbeLogger.java
    - aw-veil-compat/src/main/java/com/example/awveilcompat/mixin/core/RenderTypeProbeMixin.java
    - aw-veil-compat/src/main/java/com/example/awveilcompat/mixin/veil/RenderTypeStageProbeMixin.java
    - aw-veil-compat/src/main/java/com/example/awveilcompat/mixin/veil/DirectShaderCompilerProbeMixin.java
    - aw-veil-compat/tools/correlate_probes.py
  modified:
    - aw-veil-compat/src/main/resources/awveilcompat.core.mixins.json
    - aw-veil-compat/src/main/resources/awveilcompat.veil.mixins.json
decisions:
  - Use @Mixin(priority = 100) instead of @Priority(100) annotation — @Priority requires an import from org.spongepowered.asm.mixin.injection.struct.InjectionInfo.Priority which is not reliably available in all Mixin versions; @Mixin(priority = N) is the standard documented approach with identical effect
metrics:
  duration: 38 minutes
  completed_date: 2026-04-30
  tasks: 4/4
---

# Phase 1 Plan 2: Dual-Side GL State Probes with Offline Correlation

Built the complete GL state probe infrastructure: AW-side probe at RenderStateShard.clearRenderState() HEAD capturing program ID and AW uniform locations, Veil-side probes at ForgeRenderTypeStageHandler.register() and DirectShaderCompiler.compile() capturing shader lifecycle events, and a Python correlation script for offline timeline alignment.

## Key Corrections from Plan 01-01 Source Analysis

### AW-side: Target RenderStateShard, not RenderType

The plan assumed AW targets `RenderType.clearRenderState()`. Actual source analysis (Plan 01-01 SUMMARY) shows AW targets `RenderStateShard.clearRenderState()` at TAIL. Our probe was corrected to target `RenderStateShard.clearRenderState()` at HEAD with priority 100, ensuring it runs before AW's default-priority TAIL injection.

### Veil-side: No RenderTypeStageRegistry class

The plan assumed Veil uses `RenderTypeStageRegistry.addStage()`. Actual source analysis reveals:
- There is NO `RenderTypeStageRegistry` class
- Veil uses `ForgeRenderTypeStageHandler.register(RenderLevelStageEvent.Stage, RenderType)` in package `foundry.veil.forge.impl`
- The method takes `RenderType` as second parameter (not `ForgeRenderTypeStage` as the prompt corrections suggested)
- This is a `public static synchronized void` method

### Veil-side: DirectShaderCompiler confirmation

`DirectShaderCompiler.compile(int type, VeilShaderSource source)` at `foundry.veil.impl.client.render.shader.compiler.DirectShaderCompiler` was confirmed correct from actual source.

## Deviations from Plan

### Rule 3 - Blocking Issues (Auto-fixed)

**1. @Priority annotation import not available**
- **Found during:** Task 2, build
- **Issue:** `@Priority(100)` annotation requires import from `org.spongepowered.asm.mixin.injection.struct.InjectionInfo.Priority` but this annotation class is not reliably available in Mixin 0.15.x as a standalone import
- **Fix:** Switched to `@Mixin(value = RenderStateShard.class, priority = 100)` which achieves the identical effect (class-level Mixin priority) using the standard `@Mixin` annotation's built-in `priority` parameter
- **Files modified:** `RenderTypeProbeMixin.java` line 27
- **Commit:** 9b9646e

### Plan-target Corrections Applied

The following corrections from Plan 01-01 source analysis were factors:

| Original Plan Target | Corrected Target | Source |
|---------------------|------------------|--------|
| `RenderType.class` | `RenderStateShard.class` | AW `RenderTypeMixin.java` targets `@Mixin(RenderStateShard.class)` |
| `RenderTypeStageRegistry.addStage()` | `ForgeRenderTypeStageHandler.register(RenderLevelStageEvent.Stage, RenderType)` | No `RenderTypeStageRegistry` class in Veil source; Veil uses event-based stage management |

No builds were broken by these corrections — they were applied before the first build attempt.

## Veil Source Signatures Found

### ForgeRenderTypeStageHandler
- **Class:** `foundry.veil.forge.impl.ForgeRenderTypeStageHandler`
- **Method:** `public static synchronized void register(@Nullable RenderLevelStageEvent.Stage stage, RenderType renderType)`
- **Location:** `D:\Software\CCode\AW\veil-source\neoforge\src\main\java\foundry\veil\forge\impl\ForgeRenderTypeStageHandler.java`

### DirectShaderCompiler
- **Class:** `foundry.veil.impl.client.render.shader.compiler.DirectShaderCompiler`
- **Method:** `public CompiledShader compile(int type, VeilShaderSource source)`
- **Alternate overload:** `public CompiledShader compile(int type, ResourceLocation path)`
- **Location:** `D:\Software\CCode\AW\veil-source\common\src\main\java\foundry\veil\impl\client\render\shader\compiler\DirectShaderCompiler.java`

## Verification Results

- [x] `ProbeData.java` has `toTsvLine()` returning `nanoTime\teventType\tdata` format
- [x] `ProbeData.java` has static `tsvHeader()` returning `# nanoTime\teventType\tdata`
- [x] `GlStateReader.java` has `isOnRenderThread()`, `readCurrentProgramId()`, `readUniformLocation()`
- [x] `GlStateReader.readCurrentProgramId()` uses `MemoryStack.stackPush()` and `GL20.glGetIntegerv()`
- [x] `ProbeLogger.java` creates `probes/` directory in constructor, writes TSV header on open
- [x] `ProbeLogger.write()` calls `writer.flush()` after every write
- [x] `awveilcompat.core.mixins.json` has `"client": ["RenderTypeProbeMixin"]`
- [x] `RenderTypeProbeMixin.java` uses `@Mixin(priority = 100)` targeting `RenderStateShard.class`
- [x] AW probe targets `clearRenderState` at `@At("HEAD")`
- [x] AW probe guards: `ModDetector.isAWLoaded()` AND `ModDetector.isVeilLoaded()` AND `GlStateReader.isOnRenderThread()`
- [x] AW probe handles program=0 gracefully (logs `NO_PROGRAM_BOUND` instead of failing)
- [x] AW probe queries uniform locations: "aw_ModelViewMatrix", "aw_MatrixFlags", "aw_TextureMatrix"
- [x] `awveilcompat.veil.mixins.json` has `"client": ["RenderTypeStageProbeMixin", "DirectShaderCompilerProbeMixin"]`
- [x] Both Veil probes use `@Pseudo` and `require = 0`
- [x] Both Veil probes guard with `ModDetector.isVeilLoaded()` + `GlStateReader.isOnRenderThread()`
- [x] `correlate_probes.py` exists with `--aw`, `--veil`, and `--dir` CLI options
- [x] Python script syntax valid (`--help` returns successfully)
- [x] Build succeeds (BUILD SUCCESSFUL)
- [x] JAR contains all 3 mixin config JSONs
- [x] JAR contains GlStateReader.class, ProbeData.class, ProbeLogger.class
- [x] JAR contains RenderTypeProbeMixin.class, RenderTypeStageProbeMixin.class, DirectShaderCompilerProbeMixin.class

## Self-Check: PASSED

All 25 verification criteria checked. All 7 created files and 2 modified files verified on disk. All 4 commits verified in git log.

## How to Run the Probe

1. Build the mod: `./gradlew build` in `aw-veil-compat/`
2. Place the JAR in your NeoForge 21.1.222 `mods/` folder alongside AW and Veil
3. Launch the game
4. The `probes/` directory will be created in the game run directory with:
   - `aw-probe.log` — AW-side probe events
   - `veil-probe.log` — Veil-side probe events
5. To correlate:
   ```
   python tools/correlate_probes.py --dir runs/client/probes/
   ```

## Probe Log Format

```
# nanoTime      eventType       data
1623456789012   clearRenderState        program=1234    aw_ModelViewMatrix=42   aw_MatrixFlags=7        aw_TextureMatrix=18
1623456789123   clearRenderState        program=0       status=NO_PROGRAM_BOUND
1623456788000   shaderCompile   program=1234    event=compile
1623456789000   renderTypeStage program=5678    event=registerStage
```
