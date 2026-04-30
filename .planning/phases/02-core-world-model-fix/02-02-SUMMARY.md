---
phase: 02-core-world-model-fix
plan: 02
subsystem: shader-fix
tags: [dedicated-program, shader, save-restore, gl-state, pattern-1]
requires: [DETECT-01, DETECT-02]
provides: [RENDER-01, PERF-01]
affects: [Plan 03 (testing)]
tech-stack:
  added:
    - LWJGL GL20 shader compilation (glCreateShader, glCompileShader, glLinkProgram)
    - GLSL 150 core hard-coded shader sources (vertex + fragment)
    - MemoryStack zero-allocation matrix upload pattern
  patterns:
    - Dedicated GL program bind before ShaderUniforms.end() read
    - Save/restore with re-entrancy counter (reentrantDepth)
    - Lazy init with failure pinning (awProgram = -1 permanent skip)
    - @Pseudo + string target for zero compile-time mod dependency
key-files:
  created:
    - aw-veil-compat/src/main/java/com/example/awveilcompat/shader/AwDedicatedProgram.java
    - aw-veil-compat/src/main/java/com/example/awveilcompat/mixin/aw/AwDedicatedShaderMixin.java
  deleted:
    - aw-veil-compat/src/main/java/com/example/awveilcompat/mixin/veil/VeilShaderResourceMixin.java
    - aw-veil-compat/src/main/java/com/example/awveilcompat/shader/AwShaderUniformInjector.java
  modified:
    - aw-veil-compat/src/main/resources/awveilcompat.aw.mixins.json
    - aw-veil-compat/src/main/resources/awveilcompat.veil.mixins.json
decisions:
  - Use dedicated GL shader program (Pattern 1) to replace failed ResourceProvider wrapping approach (Pattern 2 - 02-01)
  - RenderSystem.getShaderColor() returns float[] in MC 1.21.1, not JOML Vector4f — array index access used instead
metrics:
  duration: 15 minutes
  completed_date: 2026-04-30
  tasks: 3/3
---

# Phase 2 Plan 2: Dedicated AW Shader Program (Pattern 1)

Replaced the failed Veil ResourceProvider wrapping approach with Pattern 1: a dedicated GLSL shader program for AW VBO rendering. The dedicated program declares all 7 AW uniforms and is bound at ShaderUniforms.end() HEAD via save/restore, ensuring AW always reads GL_CURRENT_PROGRAM and finds valid uniform locations.

## Files Created

### AwDedicatedProgram.java (224 lines)
- **Path:** `D:\Software\CCode\aw-veil-compat\src\main\java\com\example\awveilcompat\shader\AwDedicatedProgram.java`
- GLSL 150 core vertex shader with 6 entity attributes (layout 0-5), 3 vanilla uniforms (ModelViewMat, ProjMat, ColorModulator), and all 7 AW uniform declarations
- GLSL 150 core fragment shader with Sampler0 texture lookup, alpha discard at 0.1
- `compileShader(int type, String source)` helper with compile status check and RuntimeException on failure
- `create()` method: compile both shaders, link program, detach/delete shader objects, error-check throughout
- `uploadMatrices(int program)`: captures ProjMat and ModelViewMat from RenderSystem as Matrix4f, ColorModulator as float[], uploads via MemoryStack zero-allocation float buffers

### AwDedicatedShaderMixin.java (136 lines)
- **Path:** `D:\Software\CCode\aw-veil-compat\src\main\java\com\example\awveilcompat\mixin\aw\AwDedicatedShaderMixin.java`
- `@Pseudo @Mixin(targets = "moe.plushie.armourers_workshop.core.client.shader.ShaderUniforms")`
- `@Inject` at `end()` HEAD (onEnd): lazy-init program (RenderSystem.isOnRenderThread guard), save GL_CURRENT_PROGRAM, bind AW program, upload matrices
- `@Inject` at `end()` RETURN (onEndReturn): restore saved program, reset savedProgram and reentrantDepth
- Re-entrancy counter (reentrantDepth): nested end() calls increment depth and skip save/restore; outermost call handles restore
- Failure pinning: awProgram = -1 on create() failure, all future calls skip (graceful degradation)

## Files Deleted (Failed Approach Cleanup)

| File | Reason |
|------|--------|
| VeilShaderResourceMixin.java | Core of failed ResourceProvider wrapping approach. Tied to Veil's specific shader lifecycle. Replaced by Pattern 1 which is Veil-agnostic. |
| AwShaderUniformInjector.java | GLSL transformer used exclusively by VeilShaderResourceMixin. Dead code after VeilShaderResourceMixin removal. |

## Files Modified

| File | Change |
|------|--------|
| awveilcompat.aw.mixins.json | Added "AwDedicatedShaderMixin" to client list (after existing probes) |
| awveilcompat.veil.mixins.json | Removed "VeilShaderResourceMixin" from client list |

## Deviations from Plan

### Rule 1 - Auto-fixed Bugs

**1. RenderSystem.getShaderColor() returns float[], not Vector4f**
- **Found during:** Task 3, build (compileJava failed)
- **Issue:** The plan specified `Vector4f shaderColor = RenderSystem.getShaderColor()`, but Minecraft 1.21.1's RenderSystem.getShaderColor() returns `float[4]`. The JOML `Vector4f` import was incorrect.
- **Fix:** Removed `org.joml.Vector4f` import, changed local variable to `float[] shaderColor`, changed access from `.x()/.y()/.z()/.w()` to `[0]/[1]/[2]/[3]`.
- **Files modified:** `AwDedicatedProgram.java`
- **Commit:** 5006b43

## Build Verification

- [x] `./gradlew build` completes with BUILD SUCCESSFUL
- [x] JAR contains `AwDedicatedProgram.class` and `AwDedicatedShaderMixin.class`
- [x] JAR does NOT contain `VeilShaderResourceMixin.class` or `AwShaderUniformInjector.class`
- [x] AW mixin config in JAR (`awveilcompat.aw.mixins.json`) contains `"AwDedicatedShaderMixin"`
- [x] Veil mixin config in JAR (`awveilcompat.veil.mixins.json`) does NOT contain `"VeilShaderResourceMixin"`
- [x] All existing probe classes preserved in JAR (ShaderUniformsProbeMixin, ShaderRenderProbeMixin, RenderTypeProbeMixin, RenderTypeStageProbeMixin, DirectShaderCompilerProbeMixin, GlStateReader, ProbeData, ProbeLogger)

## Known Stubs

None detected. All files are fully implemented with no placeholder values.

## Threat Flags

None detected. The new files introduce GL program save/restore at ShaderUniforms.end(), which was already in the plan's threat model (T-02-05 through T-02-08, all mitigated or accepted).

## Verification Against Success Criteria

1. [x] AwDedicatedProgram.java exists, compiles, and is packaged in mod JAR
2. [x] AwDedicatedShaderMixin.java exists with correct HEAD/TAIL save/restore, compiles, is registered in AW mixin config
3. [x] VeilShaderResourceMixin.java deleted (failed approach removed)
4. [x] AwShaderUniformInjector.java deleted (dead code from failed approach)
5. [x] Veil mixin config no longer references VeilShaderResourceMixin
6. [x] Build succeeds with no errors
7. [x] All Phase 1 probe infrastructure preserved and unaffected
8. [ ] Mod is ready for in-game deployment to verify AW uniform locations and rendering (next phase)

## Next Steps

Deploy the built JAR to a NeoForge 21.1.222 test environment alongside AW and Veil to verify:
- glGetUniformLocation returns valid locations (not -1) for all 7 AW uniforms when program is bound
- AW equipment models render correctly on entities
- No performance regression vs non-VBO baseline (within 5% per PERF-01)
- Program restore in TAIL does not corrupt subsequent vanilla/Veil rendering

## Commits

| Hash | Message |
|------|---------|
| 0843983 | feat(02-02): create AwDedicatedProgram — GL shader program factory and matrix upload |
| 48a5a80 | feat(02-02): create AwDedicatedShaderMixin — save/restore GL program at ShaderUniforms.end() |
| d558c05 | feat(02-02): remove failed approach files — VeilShaderResourceMixin and AwShaderUniformInjector |
| 5006b43 | fix(02-02): fix RenderSystem.getShaderColor() return type for Minecraft 1.21.1 |

## Self-Check: PASSED

All 5 key files verified (2 created, 2 deleted, 2 modified). All 4 commits verified in git log.
