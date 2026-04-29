# Pitfalls Research

**Domain:** Minecraft NeoForge mod rendering compatibility (AW-Veil rendering pipeline conflict)
**Researched:** 2026-04-30
**Confidence:** HIGH (multi-sourced from issue trackers, official docs, modding community experience)

## Critical Pitfalls

### Pitfall 1: Assuming the "Current Shader Program" at Injection Time

**What goes wrong:**
AW's VBO rendering borrows the currently bound shader program and injects its own uniforms (aw_ModelViewMatrix, aw_MatrixFlags, aw_TextureMatrix). When Veil modifies shader compilation/binding, the program that is "current" at the time AW's flush executes is not the program AW expects. Uniforms get written to the wrong program -- or to a program that Veil has internally managed and will replace before the next draw call. Nine out of ten previous attempts failed because they treated this as a "when to inject" problem rather than a "which program owns the state" problem.

**Why it happens:**
The root cause is a fundamental architectural mismatch. AW assumes a vanilla-like rendering model where:
1. Minecraft binds a ShaderInstance
2. The mod can read/write uniforms on that instance
3. The draw call uses that instance

Veil (and Iris) operate differently: they maintain their own internal shader pipeline, replace ShaderInstance loading, and may compile/replace shader programs outside Minecraft's normal lifecycle. AW's "borrow current program" assumption collapses when the "current program" is a Veil-managed program that doesn't have AW's uniforms, or a temporary program that will be swapped out.

**How to avoid:**
Do not rely on GL_CURRENT_PROGRAM at the time of AW's flush. Instead, either:
- Create a dedicated AW shader program and force-bind it before AW's uniforms and draw calls, bypassing whatever program was active.
- Intercept the render level flush at a point where the target program is guaranteed stable and has AW's uniforms.
- Replace AW's VBO rendering path entirely with a standalone shader program that does not depend on borrowing the pipeline state.

**Warning signs:**
- Uniform writes succeed (no GL error) but rendering looks wrong -- the uniform is being written to a program that is never used for the actual draw.
- GL_INVALID_OPERATION on glUniform calls (program not linked or not current).
- The same fix works with one shader pack but not another.
- Disabling mod X (which changes shader compilation) makes the fix work.

**Phase to address:**
Phase 1 (Understanding) -- must fully map Veil's shader lifecycle before any injection strategy.

---

### Pitfall 2: Mixin @Overwrite and @Redirect Conflicts in the Rendering Pipeline

**What goes wrong:**
Multiple mods inject into the same rendering classes (LevelRenderer, ShaderInstance, RenderType). When two mods use @Overwrite on the same method, one silently gets skipped. When two mods use @Redirect on the same method call, one fails with InvalidInjectionException. This project targets classes that Iris, Veil, Sodium, and Embeddium all already modify. An @Overwrite-based approach will conflict.

**Why it happens:**
Iris, Veil, Sodium, and other rendering mods all use Mixin to transform the rendering pipeline. The LevelRenderer (WorldRenderer) class, ShaderInstance, and RenderType are common targets. Mixin's conflict resolution for @Overwrite is "last-writer-wins" with a warning log. For @Redirect, nesting is not supported -- only one mod's redirect takes effect. Since this project is a compatibility mod (not a core rendering mod), it has lower priority in the ecosystem and is the most likely to be silently dropped.

**How to avoid:**
- Never use @Overwrite. Always use @Inject at HEAD/RETURN or @WrapOperation (MixinExtras).
- Never use @Redirect or @ModifyConstant in rendering pipeline targets. Use MixinExtras' @WrapOperation instead, which supports composition.
- Use `require = 0` on all injections targeting classes that other rendering mods also patch, so that if Veil or Iris changes the target, the game does not crash.
- Consider injecting at a higher level (render event hooks provided by NeoForge) rather than directly into the rendering code path.

**Warning signs:**
- Mixin logs: "Method overwrite conflict for ... previously written by ... Skipping method."
- Mixin logs: "InvalidInjectionException: cannot inject into ... merged by ..."
- The fix works in isolation (only this mod installed) but fails with Veil present.

**Phase to address:**
Phase 1 (Understanding) -- audit which classes Iris/Veil/Sodium already target with mixins to avoid conflicts.

---

### Pitfall 3: OpenGL State Desynchronization Between GlStateManager Cache and Actual GL State

**What goes wrong:**
Minecraft's GlStateManager (and its RenderSystem wrapper) caches OpenGL state to avoid redundant gl* calls. When a mod calls raw OpenGL (e.g., GL11.glDisable, GL20.glUseProgram) without going through RenderSystem, the cache becomes desynchronized from actual GPU state. Subsequent Minecraft rendering reads the cache, assumes state X, but the GPU has state Y. This causes visual artifacts, missing geometry, or crashes.

This is especially dangerous for AW's VBO code, which may interact with OpenGL directly rather than through Minecraft's abstractions.

**Why it happens:**
Both AW and Veil operate at a low OpenGL level. AW's VBO code uses LWJGL's GL15/GL20 calls directly for buffer management and uniform injection. Veil's Sable shader pipeline similarly operates close to the OpenGL metal. When these interact, GlStateManager's cache is not updated, causing downstream vanilla rendering (which trusts the cache) to malfunction.

**How to avoid:**
- Never call raw OpenGL (GL11, GL20, etc.) directly. Always go through RenderSystem or a wrapper that updates GlStateManager.
- If you must use raw OpenGL (e.g., for uniform operations that RenderSystem does not expose), explicitly update the relevant GlStateManager cache entries afterward.
- For uniform operations specifically, use ShaderInstance's getUniform() / set() methods instead of GL20.glUniform*, as these go through the managed pipeline.
- Save and restore any GL state you modify, using manual save/restore (not glPushAttrib/glPopAttrib, which bypass the cache).

**Warning signs:**
- Rendering works correctly on first frame but degrades over time.
- Vanilla elements (GUI, item frames, entity nameplates) render incorrectly after AW VBO flush.
- GL_INVALID_OPERATION errors that appear only after certain rendering sequences.
- Toggling a resource pack reload (F3+T) temporarily fixes rendering -- because the state cache is rebuilt.

**Phase to address:**
Phase 2 (Assessment) -- audit all OpenGL state interactions in both AW's VBO path and Veil's pipeline.

---

### Pitfall 4: Shader Uniform Auto-Detection Name Collisions with Veil's System

**What goes wrong:**
Veil's shader system automatically detects uniforms by name -- if a uniform matches a known name, Veil manages it automatically. AW injects custom uniforms (aw_ModelViewMatrix, aw_MatrixFlags, aw_TextureMatrix) that have specific prefixes. If Veil's auto-detection or shader modifier system intercepts or transforms these uniform names -- or if Veil uses a different compilation path where these uniforms do not exist -- AW's uniform writes silently target non-existent locations.

**Why it happens:**
Veil's wiki documents that "uniforms are auto-detected by name -- no extra work needed to add them." It uses a shader modifier system that can inject [UNIFORM] commands into shader source. When Veil compiles shaders through its DirectShaderCompiler path, the compiled program may not expose AW's expected uniform locations. The uniforms either do not exist in the transformed shader, or their locations differ from what AW expects.

**How to avoid:**
- Verify AW's actual uniform location retrieval (glGetUniformLocation) at runtime, not just at init time. Veil may recompile shaders dynamically.
- If Veil's shader modifier system transforms AW's target shaders, register a shader modifier that injects AW's uniforms explicitly rather than relying on auto-detection.
- Use Veil's own uniform infrastructure (VeilShaderBufferRegistry, VeilShaderBufferLayout) if available, rather than raw OpenGL calls.

**Warning signs:**
- glGetUniformLocation returns -1 for AW's custom uniforms when Veil is active, but returns valid locations without Veil.
- Shader compilation logs show AW's uniforms being stripped or transformed.
- Uniform writes that succeed but produce no visible effect (written to location -1, which is silently ignored by OpenGL).

**Phase to address:**
Phase 2 (Assessment) -- trace uniform locations across Veil's shader compilation lifecycle.

---

### Pitfall 5: NeoForge 1.21.x Version-Specific Rendering Pipeline Breaks

**What goes wrong:**
NeoForge's rendering pipeline for 1.21.x underwent substantial refactoring across minor versions (21.1.62, 21.1.145, 21.1.222, 21.1.x+). The codebase targets NeoForge 21.1.222, but if users run a slightly different version, rendering changes may break compatibility. Known issues: shared connected vertex mode pipeline bugs (Issue #2919), shader compilation changes, and scene graph rendering changes in 1.21.10+.

**Why it happens:**
NeoForge is actively developed with frequent refactoring of the rendering layer. Minor version bumps can change how VertexFormat, RenderType, or ShaderInstance work internally. The Iris project documented that NeoForge 21.1.62 broke shader compatibility entirely (black screen on main menu). The project is pinned to 21.1.222 but must account for version variance in the modpack ecosystem.

**How to avoid:**
- Pin the exact NeoForge version in mod metadata (mods.toml version range).
- Do not depend on internals of the rendering pipeline that changed between 1.21 versions.
- Test against at least three NeoForge 1.21.1 minor versions: the target (21.1.222), one older (21.1.62), and the latest available.
- Keep mixin targets at the Mojang/Minecraft class level, not NeoForge-added methods, which are more volatile.

**Warning signs:**
- The crash report mentions a NeoForge rendering class that does not exist or has different method signatures.
- Issue trackers for other mods show "works on X.Y.Z, breaks on X.Y.W."
- The fix depends on a specific behavior of VertexFormat or RenderType that is known to have changed.

**Phase to address:**
Phase 1 (Understanding) -- document exact version requirements. Phase 4 (Testing) covers multi-version validation.

---

### Pitfall 6: VBO Performance Degradation from Compatibility Wrapping

**What goes wrong:**
The entire reason to use AW's VBO path is performance. Adding compatibility layers (wrapping each draw call, intercepting each flush, inserting state preservation/restoration around every VBO operation) can increase per-draw overhead enough to negate the VBO benefit. The fix could leave users with VBO rendering that performs as poorly as the non-VBO fallback they were trying to avoid.

**Why it happens:**
AW's VBO rendering processes geometry through buffer objects with batched draw calls. Each compatibility interjection adds:
- Additional CPU-side state checks
- Extra gl* calls for state preservation/restoration
- Potential pipeline stalls from glGet* calls
- Extra uniform lookups and uploads

If the mixin injects into the inner render loop (per-entity or per-part), these costs multiply. The project context documents that "fix must not significantly reduce frame rate (maintain VBO path performance characteristics)."

**How to avoid:**
- Inject at the coarsest granularity possible -- per-chunk or per-RenderType flush, not per-draw-call or per-vertex.
- Batch state changes: group all AW uniforms into one upload, not one glUniform per variable.
- Avoid glGet* calls in hot paths (they stall the pipeline). Cache uniform locations at init time.
- Use persistent performance monitoring: log frame time with and without the fix. A >5% regression is significant.
- Reference: Efficient Entities mod reduced CPU draw overhead by moving interception from per-part to per-entity level.

**Warning signs:**
- Frame rate drops when entities with AW equipment are on screen vs. when they are not.
- Profiling shows `glUniform*` or `glGetUniformLocation` in hot paths.
- Profiling shows increased draw call count compared to vanilla AW operation.
- The non-VBO path (which disables VBO) performs similarly to the "fixed" VBO path.

**Phase to address:**
Phase 3 (Implementation) -- performance budget must be defined before coding begins. Phase 4 (Testing) includes profiling.

---

### Pitfall 7: "Works on My Machine" Testing Trap (GPU/Driver/Mod Combination False Positives)

**What goes wrong:**
A fix that works perfectly on the developer's NVIDIA + Windows setup fails on AMD + Linux, or with Iris + a particular shader pack, or with a different mod combination. The root cause is that shader compilation, OpenGL extension support, and driver behavior vary dramatically across GPU vendors, driver versions, and operating systems. Without systematic cross-platform testing, the fix ships with hidden incompatibilities.

**Why it happens:**
- NVIDIA, AMD, and Intel GPUs all compile GLSL differently. A shader that compiles cleanly on NVIDIA may silently fail on Mesa/Intel.
- Driver versions can introduce regressions (e.g., Intel Arc driver 8132 broke specific mod combinations).
- Iris shader packs add another dimension of variation -- different packs use different GLSL features, and the fix may interact differently with each.
- The LWJGL OpenGL context creation parameters differ across platforms.

**How to avoid:**
- Test on at least three hardware configurations: NVIDIA, AMD, and Intel (including integrated GPU).
- Test with and without Iris shader packs (at least 3 popular packs: Complementary, BSL, Sildurs).
- Test on Windows, and if possible Linux (which uses Mesa drivers that are stricter about GLSL compliance).
- Use Apitrace to capture OpenGL call sequences and replay them for debugging on different GPUs.
- Use GREMEDY_string_marker to label sections in the trace for easier debugging.
- Document the exact test matrix in the project's testing plan.

**Warning signs:**
- The fix was only tested on one GPU/driver combination.
- Shader logs show warnings about GLSL extensions or version fallbacks.
- Rendering artifacts appear on one GPU but not another.
- GL_INVALID_OPERATION errors that vary by GPU vendor.

**Phase to address:**
Phase 4 (Testing) -- hardware diversity testing must be in the test plan from day one.

---

### Pitfall 8: Mod Load Order and Mixin Application Timing Dependencies

**What goes wrong:**
The fix assumes that certain mixins (AW's mixins, Veil's mixins, Iris's mixins) have already been applied when its own mixin runs. If mod load order changes, if NeoForge's deferred mixin registration affects ordering, or if the Mixin config priority is incorrect, the fix may apply before or after the transformations it depends on -- causing bytecode that targets classes or methods that don't yet exist in the expected form.

**Why it happens:**
NeoForge uses deferred mixin registration. Mixin configs are collected during mod discovery and registered later. The order depends on mod loading order, which can change based on file system (alphabetical, hash-based) or user modpack configuration. Mixin also applies configs within a mod in the order they are declared. If this project's mixin config is declared after Veil's, the transforms might apply in an unexpected order.

**How to avoid:**
- Use Mixin plugin's `shouldApplyMixin()` to check for the presence of expected transformations before applying.
- Use `@Priority` explicitly (lower values applied earlier) to control ordering relative to known conflicting mods.
- Do not depend on AW or Veil mixins having been applied. Instead, inject into vanilla classes that both AW and Veil transform, and handle the state differences at runtime.
- Use `conditional-mixin` library's `@Restriction(require = @Condition("veil"))` to ensure the mixin only applies when Veil is loaded.
- Add a runtime check in the mixin handler: verify the expected class structure before proceeding.

**Warning signs:**
- Mixin errors about target class not matching expected shape.
- The fix works in a dev environment (where load order is deterministic) but fails in production.
- Crash or rendering issues that appear/disappear when renaming the mod JAR file.
- Mixin logs showing the fix's transformations applying before Veil's.

**Phase to address:**
Phase 1 (Understanding) -- document mixin ordering requirements. Phase 3 (Implementation) -- use conditional loading.

---

### Pitfall 9: Cascading Mixin Failures Hiding the Real Problem

**What goes wrong:**
A mixin failure in a rendering class prevents the entire class from loading. This triggers a cascade of NoClassDefFoundError for every mod that depends on that class. The crash report shows dozens of errors in unrelated mods, but the root cause is one mixin injection failure in this project. The developer wastes hours debugging the wrong thing.

**Why it happens:**
Minecraft's rendering classes are deeply interconnected. If GameRendererMixin fails to apply, GameRenderer fails to load. Any code referencing GameRenderer then fails. NeoForge has acknowledged this issue (Issue #2636): "Cascading errors often hide the real problem causing mod in crash reports."

**How to avoid:**
- Always use `require = 0` on injections into rendering pipeline classes (they are the most likely to be modified by other mods).
- Use separate Mixin config files for different concerns, so one failure does not block all mixins.
- Log mixin application success/failure explicitly with the mod's logger.
- When debugging a crash with many errors, look at the FIRST error in the log, not the last.
- Test mixins in isolation first (only this mod + vanilla) before adding Veil and AW.

**Warning signs:**
- Crash report has 10+ errors from different mods but the FIRST one is from this project's mixin.
- The error mentions "NoClassDefFoundError" for a class that has a mixin in this project.
- The error mentions "Mixin apply failed" for this project's mixin config.

**Phase to address:**
Phase 3 (Implementation) -- build mixins incrementally, testing each in isolation first.

---

### Pitfall 10: Treating Matrix Transformations as a Uniform Problem Instead of a Pipeline Problem

**What goes wrong:**
AW's rendering issues manifest as incorrect model transforms (stretched models, fixed position relative to player, not following viewpoint). It is tempting to treat this as "AW is writing the wrong matrix values" and try to fix the matrix uniform upload. But the project's previous attempts show that modifying AW's matrix uniforms (attempts #1, #4, #9) produces different wrong behavior rather than correct rendering. The real issue may be that Veil changes the projection/modelview matrix pipeline in ways that make AW's matrix assumptions invalid at the pipeline level, not just the uniform level.

**Why it happens:**
Veil may modify the projection stack, the modelview matrix, or the way these are passed to shaders (e.g., through uniform blocks rather than individual uniforms, or through a different coordinate space). AW assumes vanilla matrix semantics, but under Veil the same matrix names may have different meanings or be in a different coordinate space. Writing different values to the same uniform slots does not fix the fundamental mismatch.

**How to avoid:**
- Profile the actual GL matrix state (ModelViewMatrix, ProjectionMatrix, NormalMatrix) at the point of AW's VBO flush, both with and without Veil.
- Compare the matrix values: are they identical? Different by a known transform? Completely different coordinate spaces?
- Use Apitrace to capture the full matrix state at the draw call point.
- Do not assume the matrix uniform name implies a specific coordinate space. Verify what coordinate space Veil's pipeline is operating in.

**Warning signs:**
- Changing matrix uniform values changes the rendering but never makes it correct -- always wrong in a different way.
- Models appear at the correct world position but do not respond to view rotation (wrong ModelView).
- Models appear at the correct screen position but are the wrong size (wrong Projection or viewport).
- Matrix values retrieved via glGet* differ significantly from the values AW computed.

**Phase to address:**
Phase 2 (Assessment) -- trace matrix state through Veil's pipeline before attempting any matrix fix.

---

### Pitfall 11: Vacuum Cleaner Approach to Mixin Targets (Too Many, Too Broad)

**What goes wrong:**
The previous 10 failed attempts covered a wide range of targets: drawElements, ShaderInstance, RenderType, DirectShaderCompiler, ResourceProvider, uniform binding. Each attempt was a new mixin in a new location. This scatter-shot approach does not converge on a solution because each injection point tests a different hypothesis, and failures do not accumulate knowledge about the root cause.

**Why it happens:**
When none of the obvious injection points work, the natural impulse is to try "something else" -- inject into a different class, try a different approach. But without understanding Veil's rendering pipeline structure, each new injection point is essentially random. The 10 attempts covered 5 different architectural layers (shader compilation, shader loading, uniform binding, draw call interception, RenderType management) without systematically ruling any out.

**How to avoid:**
- Before any coding, trace the full rendering path for AW's VBO flush from end to end: which classes are called, what OpenGL state is modified, what shader program is current at each step.
- Create a dependency graph of the rendering pipeline: what calls what, what state modifies what.
- For each potential injection point, predict what behavior change it should produce. If multiple injection points could explain the same symptom, design a minimal test to distinguish them.
- Do not start coding a fix until you can articulate a causal hypothesis for why the fix should work.

**Warning signs:**
- The project has tried N approaches without understanding why any of them failed.
- Each new attempt uses a different class/approach than the last.
- Attempts are not documented with concrete evidence of what was observed (only "it didn't work").

**Phase to address:**
Phase 1 (Understanding) -- systematic pipeline tracing before any coding.

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Hardcoded uniform injection points (specific shader program IDs) | Fast to implement | Breaks when Veil updates its compilation strategy; fragile across versions | Never -- use uniform names, not program IDs |
| Disabling Veil's shader replacement entirely | Eliminates the conflict | Destroys Veil's functionality -- the project should not break another mod | Never -- defeats the purpose of compatibility |
| Copy-pasting AW's VBO render code with modifications | Quick starting point | Hard to maintain; diverges from AW upstream; licensing concerns | Only as a prototype for understanding; never ship |
| Single large Mixin config file for all patches | Simple project setup | One failure blocks all patches; hard to disable individual fixes | Split into separate configs per concern from the start |
| Adding glFinish() calls to force synchronization | Fixes timing-dependent bugs | Devastating performance impact (stalls entire GPU pipeline) | Never -- use fences or barriers instead |
| Assuming uniform locations are stable across shader recompiles | Avoids location lookup code | Breaks when Veil recompiles shaders; wrong uniform target | Never -- always look up locations dynamically |

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| Veil's DirectShaderCompiler | Patching DCS to inject AW uniforms misses shaders compiled through other paths | Intercept at ShaderInstance level, not compiler level |
| Iris shader packs | Testing only with one shader pack (e.g., Complementary) | Test with at least 3 packs covering different GLSL styles |
| GlStateManager cache | Calling glUseProgram directly without updating GlStateManager.activeProgram | Use ShaderInstance::setUniform or RenderSystem wrapper |
| Texture binding after deletion | Reusing a texture ID that GlStateManager thinks is still valid | Explicitly unbind textures before deletion |
| Multiple RenderType flushes | AW's VBO flush inside one RenderType interferes with another | Save/restore full shader state around AW's flush |
| MixinExtras version | Bundling a different MixinExtras version than other mods | Use jar-in-jar relocation or match the ecosystem version |
| Fabric-layer mods via Sinytra Connector | Mixin targets change when Connector translates between Forge/Fabric | Test with Connector if it's in the target modpack |
| OptiFine | OptiFine's custom renderer bypasses standard injection points | Flag as out of scope if it requires special handling |

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| Per-entity state save/restore | FPS drops proportional to entity count with AW equipment | Batch state changes; save/restore once per flush, not per entity | >10 entities visible |
| glGet* calls for uniform locations every frame | High CPU time in GL driver, frame time spikes | Cache uniform locations at program creation time, invalidate on relink | First frame after loading |
| Adding glFinish() for synchronization | Severe frame rate drop, GPU underutilization | Use glFenceSync + glClientWaitSync instead | Immediately -- unacceptable for any entity count |
| Injecting into inner render loop | Gradual frame rate degradation, increased draw call count | Inject at flush/end level, not per-primitive | >50 draw calls per frame |
| Not caching ShaderInstance lookups | Repeated ShaderInstance.getShader() calls during rendering | Cache the instance reference at init or when program changes | Per-frame with many entities |
| Double uniform upload (AW + compat mod both setting same uniforms) | Wasted GPU bandwidth, potential state thrash | Detect whether AW already uploaded; skip or coalesce | Every frame with AW-rendered entities |

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| Unsafe reflection into AW's private fields | Class loading errors if AW's internals change; potential crash | Use Mixin accessors instead of reflection |
| Unvalidated uniform location writes | Writing to location -1 (silently ignored) produces no error but no rendering | Always check glGetUniformLocation result before writing |
| Not guarding against re-entrant VBO flushes | Stack overflow crash if compat code triggers AW's render path that triggers compat code | Use re-entry guard flag (attempted in attempt #8) |
| Using raw LWJGL pointers without bounds checking | JVM crash from invalid native memory access | Never use direct buffer addresses without validation |

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| Silent failure (rendering broken, no error) | User does not know the fix is not working | Add visual indicator or /aw-veil-status command |
| Crashing on load when Veil is not installed | Users who do not use Veil get crashes | Conditional mixin loading -- only apply when Veil detected |
| Performance regression without user notification | User sees lower FPS, blames AW or Veil | Log performance warning if frame time exceeds threshold |
| Incompatibility with specific Iris shader pack | "Fix works except with BSL shaders" -- confusing for users | Document known-incompatible packs; add runtime detection |
| Debug logging that users cannot disable | Console spam, confusion on crash report forums | Use Mojang's Log4j logger with appropriate levels; off by default |

## "Looks Done But Isn't" Checklist

- [ ] **VBO rendering works for armor models:** Test every AW skin type (normal, slim, cube, humanoid) not just one
- [ ] **VBO rendering works for held items:** Item rendering uses a different rendering path than armor -- test separately
- [ ] **Works in first-person:** First-person rendering uses different camera transforms -- test separately
- [ ] **Works in third-person:** Verify rotation follows camera correctly
- [ ] **Works on non-player entities:** Test AW rendering on armor stands, other players in multiplayer
- [ ] **Works with at least one Iris shader pack:** Test Complementary, BSL, Sildurs, and vanilla (no shaders)
- [ ] **No performance regression measured:** Profile FPS with 1, 5, 20 entities wearing AW equipment
- [ ] **No GL errors logged:** Check glGetError() or OpenGL debug output after each frame
- [ ] **Works after resource pack reload (F3+T):** Verify state is properly rebuilt
- [ ] **Works after shader pack reload (F3+R in Iris):** Verify uniforms are re-applied after shader recompile
- [ ] **Works without AW VBO fallback enabled:** The fix must be at least as good as the non-VBO path
- [ ] **Works with MixinExtras properly relocated:** No classpath conflicts with other mods' copy of MixinExtras
- [ ] **Conditional mixin loading tested:** Verify mixins do NOT load when Veil is not installed
- [ ] **Cross-GPU tested:** At minimum, test on NVIDIA and Intel GPU (or get AMD report from community)

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| Mixin @Overwrite conflict with another mod | MEDIUM | Switch to @Inject/@WrapOperation; coordinate with conflicting mod author |
| GlStateManager desync causing vanilla rendering issues | MEDIUM | Wrap all GL calls through RenderSystem; force cache resync via F3+T combination |
| Performance regression from per-draw wrapping | HIGH | Profile to find hot injection points; batch state changes; reduce injection granularity |
| Load order dependency crash | LOW | Use Mixin plugin's shouldApplyMixin() to check prerequisites; add conditional dependency |
| Wrong program for uniform injection | HIGH | Switch strategy: create dedicated AW program instead of borrowing the current one |
| Uniform location -1 after Veil shader recompile | MEDIUM | Re-lookup uniforms on the newly compiled program; hook into shader compilation event |

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| 1. Wrong current program assumption | Phase 2 (Assessment): map Veil's shader lifecycle | Phase 2 deliverable: lifecycle diagram showing when shader programs are created, linked, and bound |
| 2. Mixin @Overwrite/@Redirect conflicts | Phase 1 (Understanding): audit other mods' mixin targets | Phase 1 deliverable: conflict matrix of all mixin targets |
| 3. OpenGL state desync | Phase 2 (Assessment): audit GL state interactions | Phase 2 deliverable: state interaction map and save/restore specification |
| 4. Veil uniform auto-detection collisions | Phase 2 (Assessment): trace uniform lifecycle | Phase 2 deliverable: uniform lifecycle for each shader program path |
| 5. NeoForge version-specific breaks | Phase 1 (Understanding): pin version, test multiple minor versions | Phase 4 (Testing): multi-version test pass |
| 6. VBO performance degradation | Phase 3 (Implementation): define performance budget, coarse injection | Phase 4 (Testing): profiled FPS comparison with/without fix |
| 7. "Works on my machine" testing trap | Phase 4 (Testing): define cross-platform test matrix | Phase 4 deliverable: test results on 3+ GPU/driver combos |
| 8. Load order/mixin timing dependencies | Phase 3 (Implementation): conditional mixin loading | Phase 4 (Testing): test with different mod load orders |
| 9. Cascading mixin failures | Phase 3 (Implementation): require=0, separate configs | Phase 4 (Testing): intentional failure injection test |
| 10. Matrix as uniform vs. pipeline problem | Phase 2 (Assessment): trace matrix state through Veil | Phase 2 deliverable: matrix state comparison (with/without Veil) |
| 11. Vacuum cleaner approach | Phase 1 (Understanding): systematic pipeline tracing first | Phase 1 deliverable: pipeline dependency graph before any code |

## Sources

- [Mixin @Inject documentation (Fabric Wiki)](https://wiki.fabricmc.net/tutorial:mixin_injects) -- Injection point best practices and pitfalls
- [Mixin Overwrite conflicts with Sodium/Iris](https://blog.gitcode.com/5a38ba5eb5424e3d5d71ec3229e7b8a6.html) -- CloudRenderer mixin conflict analysis
- [Iris Shaders + Carpet Mod compatibility analysis](https://blog.gitcode.com/c7c9e0f8afeced89e23d8643c138f954.html) -- Real-world mixin priority conflict resolution
- [CleanroomMC Render Book: State Concerns](https://cleanroommc.com/renderbook/state/concerns) -- OpenGL state management pitfalls
- [GlStateManager desync with pushAttrib/popAttrib](https://github.com/NovaEngineering-Source/ModularMachinery-Community-Edition/issues/115) -- Concrete state desync issue analysis
- [NeoForge Issue #2919: Shared vertex mode pipeline](https://github.com/neoforged/NeoForge/issues/2919) -- Connected vertex mode rendering bug
- [NeoForge Issue #2636: Cascading errors](https://github.com/neoforged/NeoForge/issues/2636) -- Cascading error masking root cause
- [NeoForge Issue #264: Mixin config double parsing](https://github.com/neoforged/NeoForge/issues/264) -- Manifest.MF vs mods.toml mixin config issue
- [Intel Arc driver regression with modded Minecraft](https://community.intel.com/t5/Intel-Arc-Discrete-Graphics/Severe-frame-presentation-issues-with-modded-Minecraft/m-p/1719587) -- Driver-specific rendering failure
- [Sodium project: Overwolf overlay GL state corruption](https://blog.gitcode.com/728877d43373259ad094b290e8ef39bb.html) -- External GL state corruption example
- [Create Flywheel rendering compatibility analysis](https://blog.gitcode.com/02c36e2f554c84a35b6c8e736f8ee2ac.html) -- "Works on my machine" false positive example
- [Debugging OpenGL issues with apitrace (comp500's gist)](https://gist.github.com/Sturmlilie/69d6c4d2dce9d648cd706093c95ba195) -- Apitrace usage for Minecraft mod rendering debugging
- [Veil Wiki: Shader](https://github-wiki-see.page/m/FoundryMC/Veil/wiki/Shader) -- Veil's uniform auto-detection by name
- [Veil Wiki: ShaderModification](https://github-wiki-see.page/m/FoundryMC/Veil/wiki/ShaderModification) -- Veil's shader modifier system and uniform injection
- [Rubidium + custom ShaderInstance compatibility (Issue #577)](https://github.com/Asek3/Rubidium/issues/577) -- Uniform cache null from constructor mismatch
- [Iris Issue #2651: GL_INVALID_OPERATION with shader packs](https://github.com/IrisShaders/Iris/issues/2651) -- Shader uniform state errors
- [conditional-mixin library](https://github.com/Fallen-Breath/conditional-mixin) -- Conditional mixin loading via annotations
- [Efficient Entities mod analysis](https://modrinth.com/mod/efficient-entities) -- Performance optimization patterns for entity rendering
- [Mixin architecture for rendering compatibility (AcceleratedRendering DeepWiki)](https://deepwiki.com/Argon4W/AcceleratedRendering/8.2-mixin-architecture) -- Mixin architecture patterns for rendering mod compatibility

---
*Pitfalls research for: AW-Veil rendering compatibility mod*
*Researched: 2026-04-30*
