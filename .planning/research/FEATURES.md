# Feature Landscape

**Domain:** Minecraft NeoForge mod rendering compatibility (Armourer's Workshop VBO + Veil shader pipeline)
**Researched:** 2026-04-30
**Confidence:** MEDIUM (features grounded in domain analysis; implementation complexity estimates need validation)

## Feature Landscape

### Table Stakes (Users Expect These)

Features that must work for the mod to be useful at all. Missing any of these makes the product non-functional.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| AW World Model Rendering Fix | Primary symptom: AW armor/equipment models stretch or fixate near player when Veil is present. Without this fix, the mod has no reason to exist. | HIGH | Requires understanding Veil's shader compilation/binding pipeline to re-establish correct matrix state before AW VBO flush. 10+ approaches already failed in AW-only patches; root cause is in Veil's state management. |
| AW Item Rendering Fix | Secondary symptom: AW item textures become invisible in inventory/hotbar when Veil is present. Iris 1.8.8 partially fixes models but items remain broken, suggesting a separate code path. | HIGH | Item rendering goes through different render layers and shader programs than world/entity rendering. May need a separate fix strategy. |
| VBO Performance Preservation | Core value proposition: users must not have to disable AW VBO to get correct rendering. Fix must keep the VBO path's performance advantage over non-VBO fallback. | MEDIUM | VBO path uses GL buffer objects to batch geometry. The fix must intercept at the right point without adding per-vertex overhead. |
| AW and Veil Runtime Detection | Mod must not crash when AW and/or Veil are absent. Both are optional mods that may or may not be in a pack. | LOW | Standard NeoForge pattern: use `@Pseudo` annotation on mixins targeting optional mods, and Mixin plugins for conditional loading. |
| Iris Coexistence | The mod must not break or conflict with Iris shaders when both Iris and Veil are present. Iris partially fixes AW model rendering but leaves items broken, so the interaction is non-trivial. | MEDIUM | May need an Iris-specific compatibility mixin config (separate file, loaded only when Iris is present). Model after AcceleratedRendering's per-mod mixin config pattern. |
| Crash-Free on Modpack Startup | Loading the mod in any combination (AW+Veil, AW only, Veil only, neither) must not cause startup crashes, classloading errors, or mixin injection failures. | LOW | Use separate mixin configs per target mod, `@Pseudo` annotations, and proper refMap configuration. |
| NeoForge 1.21.1 Only | Version lock prevents class/method signature drift. Only target `neoforge-21.1.222`. | N/A | Hard requirement from PROJECT.md constraints. No multi-version support. |

### Differentiators (Competitive Advantage)

Features that add unique value beyond just fixing rendering. These are what make the mod better than a workaround (like disabling VBO or using Iris).

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| GL State Debug Overlay | Real-time HUD overlay showing the currently bound GL shader program, active AW uniforms (`aw_ModelViewMatrix`, `aw_MatrixFlags`, `aw_TextureMatrix`), and current matrix state at the moment of VBO flush. Since the root problem is state mismanagement, this turns a black-box rendering bug into a debuggable problem. | MEDIUM | Toggle via keybind (e.g., F3+A). Overlay renders on top of vanilla debug HUD. Reads GL state via `GL11.glGetInteger(GL_CURRENT_PROGRAM)` and uniform introspection. Most useful during development but valuable for users reporting bugs. |
| Diagnostic Logging Mode | Toggleable verbose logging of the AW VBO flush path: which shader program was bound before/after fix, which AW uniforms were injected, their values, and the matrix stack state at each draw call. | LOW | Controlled by a config toggle (off by default). Logs at `DEBUG` level through Minecraft's logger. Enables users to provide structured bug reports. |
| Shader Program Capture Command | A client-side command (e.g., `/awv-compat capture`) that dumps the current GL program ID, all active uniform names and values, bound textures, and vertex attribute state to the log file and optionally to a timestamped file. | MEDIUM | Essentially a snapshot of the GL state machine relevant to AW rendering. Mirrors what apitrace does but is always available in-game without external tools. |
| Graceful Fallback Modes | Multiple fix aggressiveness levels (OFF, LIGHT, FULL) that the user can switch at runtime. LIGHT only patches the model rendering path; FULL also patches item rendering. If FULL causes issues, user can drop to LIGHT without restarting. | MEDIUM | Config-driven. Mixin-based patches can use feature-flag checks (like AcceleratedRendering's context-feature toggle pattern) to decide at runtime whether to apply each fix. |
| Compatibility Test Matrix | A JSON config file (in `config/awv-compat-matrix.json`) mapping mod version combinations to known states: `"tested_ok"`, `"tested_broken"`, `"unknown"`. On startup, the mod checks current AW+Veil versions against the matrix and warns if the combination is known-broken or untested. | LOW | Community-contributed data. Mod ships with a built-in matrix covering tested versions. Auto-detects AW and Veil versions from their mod metadata. |
| Auto-Iris Compat Mode | When Iris shaders are detected alongside Veil, automatically activate a specialized fix path that accounts for Iris's partial fix (models OK, items broken). Avoids duplicating Iris's model fix and only handles the remaining item issue. | MEDIUM | Requires dedicated mixin config for Iris classes (loaded only when Iris is present via Mixin plugin). Follows AcceleratedRendering's compat layer pattern. |
| Mixin Injection Trace | Augments crash reports (like MixinTrace Reforged) with AW-Veil-Compat-specific context: which compatibility patches were active, which mod detection results were found, and the last GL state before any crash in the AW rendering path. | LOW | Lightweight. Overrides a crash report section or adds to existing mixin trace. Drastically reduces time to diagnose user bug reports. |
| Integration Debug Guide | An included README/debug document (accessible via `/awv-compat guide`) explaining how to use apitrace, RenderDoc, and the mod's own diagnostic features to capture useful bug reports. | LOW | Documentation only, but significantly reduces support burden. Links to external tools and explains what to capture. |

### Anti-Features (Commonly Requested, Often Problematic)

Features that seem good but would create problems for this project.

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| Fix Veil's Own Rendering Bugs | "Since you're patching Veil anyway, can you fix this other Veil issue?" | Scope creep. The mod's purpose is AW+Veil compatibility, not fixing Veil. Veil bugs will change with Veil updates, creating a maintenance burden. | File issues on the Veil repository. Stay focused on the intersection. |
| Support Multiple Minecraft Versions | "Can you make this work for 1.20.1 / 1.18.2 / 1.21.3?" | AW and Veil have different rendering code per MC version. Multi-version multiplies mixin targets exponentially. Each version requires separate testing matrix. | Lock to 1.21.1 NeoForge. If demand emerges later, branch the project per version. |
| Support Fabric/Forge Loaders | "I use Fabric with Iris." | Cross-loader support requires Architectury or separate build pipelines. Mirage (NeoForge) uses different classloaders, different mixin systems. Adds enormous testing complexity. | NeoForge only, as specified in constraints. |
| Rewrite AW's VBO Renderer | "The VBO code is the problem, just replace it." | Risk of breaking AW features, creates a fork dependency. If AW updates VBO code, the rewrite becomes stale. Maintenance nightmare. | Keep Mixin-based patching. Minimal, targeted injections. |
| Full In-Game Shader Debugger | "I want to see all shader uniforms and edit them live." | Massive complexity. Competes with mature tools like RenderDoc and apitrace. Would take more effort than the core fix. | Provide the GL State Debug Overlay (limited scope) and the Integration Debug Guide pointing to RenderDoc/apitrace. |
| Auto-Updater for Compat Matrix | "Can the mod auto-download the latest compatibility matrix?" | Requires a server, versioning scheme, update infrastructure. Increases mod complexity and introduces network calls at startup, which can be blocked or slow. | Ship matrix with the mod. Update it with mod releases. Users on old versions use old matrices. |
| Real-Time FPS/Metrics Dashboard | "Show FPS impact of the fix vs non-VBO fallback." | F3 debug screen already shows FPS. Spark is a dedicated profiler. Building another profiler adds code path that could itself affect performance. | Document how to use F3 + Spark for performance comparison. Keep the mod focused on correctness. |

## Feature Dependencies

```
AW World Model Rendering Fix
    └──requires──> Veil Runtime Detection (must know Veil is present to activate patches)
    └──requires──> AW Runtime Detection (must know AW class structure)
    └──requires──> GL State Interception (must intercept at correct point in VBO flush)
                       └──requires──> Mixin Injection Points (must identify correct callback sites)

AW Item Rendering Fix
    └──requires──> AW World Model Rendering Fix (may share some state interception)
    └──requires──> Iris Coexistence (if Iris is present, item fix must not conflict with Iris's model fix)
                   
Iris Coexistence
    └──requires──> Iris Runtime Detection (Mixin plugin for Iris classes)
    
GL State Debug Overlay
    └──enhances──> AW World Model Rendering Fix (helps verify fix is working)
    └──enhances──> AW Item Rendering Fix (helps verify fix is working)
    └──requires──> GL State Interception (shares the state capture mechanism)

Diagnostic Logging Mode
    └──enhances──> GL State Debug Overlay (provides persistent record of overlay data)
    
Graceful Fallback Modes
    └──conflicts──> Auto-Iris Compat Mode (fallback modes choose which patches apply; auto-Iris also chooses)
                         Resolved by: Auto-Iris is a subset of FULL mode logic operating conditionally

Compatibility Test Matrix
    └──requires──> AW Runtime Detection (reads AW version)
    └──requires──> Veil Runtime Detection (reads Veil version)

Mixin Injection Trace
    └──enhances──> all other features (traces active state in crash reports)
    
Shader Program Capture Command
    └──requires──> GL State Interception (reuses the state capture infrastructure)
```

### Dependency Notes

- **All fixes depend on runtime detection first.** You cannot patch what you have not detected. The detection layer (Mixin plugins + `@Pseudo`) is the foundational dependency.
- **Item fix depends on model fix** because they share the same core mechanism (state interception during VBO flush) but the item path goes through different render layers (`ItemRenderer` / `GameRenderer` vs `EntityRenderer` / `LevelRenderer`).
- **Iris coexistence is a constraint on the item fix.** If Iris is present, its partial model fix changes the assumptions. The item fix must account for what Iris already does.
- **Debug overlay and logging are non-blocking enhancements.** They add value but are not required for the core fix to work. They can be built after the fix is functional.
- **Fallback modes conflict with auto-Iris mode** only in the sense that both control which patches are active. The resolution is a simple priority: if auto-Iris is active, fallback mode toggles only the remaining patches not handled by auto-Iris.

## MVP Definition

### Launch With (v1)

The minimum that proves the mod works:

- [ ] **AW World Model Rendering Fix** — model stretching/fixation resolved. This is the mod's reason for existence. Without it, there is no v1.
- [ ] **AW Item Rendering Fix** — item textures visible again. If models work but items dont, users still cannot use AW properly.
- [ ] **VBO Performance Preservation** — fix must not fall back to non-VBO rendering. Must be verified by FPS comparison.
- [ ] **AW and Veil Runtime Detection** — must not crash when either mod is absent. Must use Mixin plugin or `@Pseudo`.
- [ ] **Iris Coexistence** — must not break when both Iris and Veil are loaded. Iris is extremely common alongside Veil in modpacks.
- [ ] **NeoForge 1.21.1 Lock** — single version, single loader.

### Add After Validation (v1.x)

Features to add once the core fix is stable:

- [ ] **Diagnostic Logging Mode** — triggered by first bug report that requires debugging. The lack of diagnostics makes bug reports useless.
- [ ] **GL State Debug Overlay** — triggered by need to visualize fix behavior. Can be added incrementally.
- [ ] **Graceful Fallback Modes** — triggered by user reports of the fix causing issues in specific modpack configurations.
- [ ] **Shader Program Capture Command** — triggered by persistent debugging challenges.

### Future Consideration (v2+)

Features to defer until product-market fit is established:

- [ ] **Compatibility Test Matrix** — requires a user community to contribute test results. Premature if nobody uses the mod yet.
- [ ] **Auto-Iris Compat Mode** — only needed if users commonly run Iris+Veil+AW and the basic fix doesn't handle it well.
- [ ] **Mixin Injection Trace** — nice to have but the mod is small enough that crash reports are usually clear.
- [ ] **Integration Debug Guide** — write when there are users to read it.

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| AW World Model Rendering Fix | HIGH | VERY HIGH (unknown unknowns in Veil's pipeline) | P1 |
| AW Item Rendering Fix | HIGH | HIGH (separate code path from models) | P1 |
| VBO Performance Preservation | HIGH | MEDIUM (constraint, not independent feature) | P1 |
| AW + Veil Runtime Detection | HIGH | LOW | P1 |
| Iris Coexistence | HIGH | MEDIUM | P1 |
| NeoForge 1.21.1 Lock | HIGH | N/A (decision, not code) | P1 |
| Diagnostic Logging Mode | MEDIUM | LOW | P2 |
| GL State Debug Overlay | MEDIUM | MEDIUM | P2 |
| Graceful Fallback Modes | MEDIUM | MEDIUM | P2 |
| Shader Program Capture Command | LOW | MEDIUM | P3 |
| Compatibility Test Matrix | LOW | LOW | P3 |
| Auto-Iris Compat Mode | MEDIUM | MEDIUM | P2 (if needed) |
| Mixin Injection Trace | LOW | LOW | P3 |
| Integration Debug Guide | MEDIUM | VERY LOW | P2 |

**Priority key:**
- P1: Must have for launch
- P2: Should have, add when possible (post-MVP)
- P3: Nice to have, future consideration

## Implementation Notes

### Architecture Pattern to Follow

Reference: AcceleratedRendering's mixin architecture (Argon4W), which uses:
1. **Separate mixin config files per target mod** — `compat.veil.mixins.json`, `compat.iris.mixins.json`, `core.mixins.json`
2. **Mixin plugins** for runtime detection — one plugin per optional mod (Veil, Iris) that returns `false` from `shouldApply()` when the mod is absent
3. **`@Pseudo`** annotation on all mixin targets from optional mods — prevents classloading errors
4. **Feature-flag checks** at injection points — runtime booleans that control whether the fix logic executes
5. **`compileOnly`** Gradle dependencies for optional mods — keeps class references valid at build but absent at runtime

### Known Failed Approaches (Do Not Repeat)

From PROJECT.md's "tried and excluded" list (10 approaches):

| Failed Approach | Lesson |
|----------------|--------|
| Patching AW shader matrix injection | The matrix state itself is not the root cause |
| `drawElements` callback manipulation | Timing of the callback relative to Veil state is the issue, not the callback itself |
| Injecting AW uniforms into DirectShaderCompiler | Veil's shader compilation pipeline is more complex than uniform injection |
| Wrapping ShaderInstance ResourceProvider | Vanilla shader patching misses Veil's block shaders |
| Redirecting AW VBO flush to AW's own RenderType | Recursion; Veil overrides RenderType behavior |
| Re-entry guards on VBO flush | Prevents crashes but does not fix state |
| Forcing uniform writes to `GL_CURRENT_PROGRAM` | The problem is not which program the uniforms go to, but the state assumptions the shader makes |

**Key lesson from failures:** The fix must understand what Veil changes about the GL state machine and re-establish AW's expected state at the right point. This likely means patching Veil's render-level stage setup, not AW's VBO flush.

## Estimated Mixin Injection Points

Based on domain analysis, the following injection points are likely needed:

| Target | Injection Point | Purpose |
|--------|----------------|---------|
| Veil `VeilRenderLevelStageEvent` handler | Capture shader program/state changes during render stages | Understand when and how Veil modifies the GL state for world rendering |
| AW VBO flush method | `@HEAD` and `@TAIL` injection | Save state before AW draws, verify/restore after |
| Minecraft `ItemRenderer` | `renderItem` or related | Capture state during item rendering (separate path from world) |
| `RenderSystem` state management | `setupShader`, `setupOverlayColor`, etc. | Veil may be overriding vanilla state managers |
| Iris `ShaderPipeline` (if present) | Compilation or binding | Account for Iris's intermediate shader layer between vanilla and Veil |

## Competitor Feature Analysis

| Feature | Our Mod | Disable AW VBO (workaround) | Iris-only fix | Generic compat mod |
|---------|---------|------------------------------|---------------|-------------------|
| AW model fix | YES | YES (no VBO) | PARTIAL (models OK, items broken) | Would not target specifically |
| AW item fix | YES | YES (no VBO) | NO | Would not target specifically |
| VBO performance | PRESERVED | LOST | PRESERVED (but items broken) | N/A |
| Debug overlay | YES (planned) | NO | NO | NO |
| Diagnostics logging | YES (planned) | NO | NO | Varies |
| Compat matrix | YES (planned) | N/A | N/A | Varies |
| Iris-aware | YES (planned) | N/A | Built-in | Varies |

## Sources

- DeepWiki - Argon4W/AcceleratedRendering: mixin architecture patterns for rendering compatibility layers (separate configs per mod, Mixin plugins, `@Pseudo`, feature-flag checks)
- DeepWiki - nikitacartes-archive/minecraftdeobfuscated-yarn: debug/development tools patterns in Minecraft
- GitHub Gist (Sturmlilie) - "Debugging OpenGL issues in Minecraft mods using apitrace": external debugging tool patterns
- ModernFix Wiki (embeddedt) - 1.21.1 Summary of Patches: catalog of bugfix and debug mixin features
- MixinTrace Reforged - Crash report augmentation pattern
- Veil GitHub Wiki - Shader, PostProcessing, ShaderModification pages: Veil rendering architecture
- Veil API Javadocs (foundrymc.github.io) - DirectShaderCompiler, VeilRenderSystem, VeilRenderLevelStageEvent
- Project context (PROJECT.md): validated requirements, failed approaches, constraints, key decisions
- Armourer's Workshop GitHub (SAGESSE-CN fork): rendering refactor commits, known issues

---
*Feature research for: AW-Veil Compat (Minecraft NeoForge mod)*
*Researched: 2026-04-30*
