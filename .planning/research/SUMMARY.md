# Project Research Summary

**Project:** AW-Veil Compat
**Domain:** Minecraft NeoForge 1.21.1 compatibility mod (AW VBO rendering vs Veil shader pipeline)
**Researched:** 2026-04-30
**Confidence:** HIGH (architecture and stack well-documented; feature scope validated against real user symptoms)

## Executive Summary

AW-Veil Compat fixes rendering incompatibilities between Armourer's Workshop (AW) VBO rendering and the Veil rendering framework. The root cause is a fundamental architectural mismatch: AW borrows the currently bound OpenGL shader program via a Mixin hook into `RenderType.clearRenderState()` and injects its own uniforms (`aw_ModelViewMatrix`, `aw_MatrixFlags`, `aw_TextureMatrix`). When Veil is present, it replaces or modifies the bound shader program through its `RenderTypeStageRegistry` and `DirectShaderCompiler`, so AW's hook reads the wrong program handle and writes uniforms to locations that do not exist or have different semantics. Ten previous AW-side attempts failed because they treated this as a "when to inject" problem rather than a "which program owns the state" problem.

The recommended fix is Pattern 1 from the architecture research: give AW its own dedicated shader program instead of borrowing the currently bound one. This eliminates the root cause (program confusion) entirely and is robust against any shader-modifying mod (Veil, Iris, OptiFine, etc.). The fix requires a dedicated `AwShaderProgram` class, a minimal pass-through vertex shader matching AW's VBO vertex format, and a targeted Mixin injection that replaces the "borrow current program" block with dedicated program binding, draw, and unbind.

Key risks are: (1) GL state cache desynchronization if raw OpenGL calls bypass `RenderSystem`, (2) Mixin conflicts with Iris, Veil, and other rendering mods that target the same classes, (3) performance regression from per-draw state wrapping that negates VBO performance benefits, and (4) the "works on my machine" trap from testing only on a single GPU/driver combination. All four risks have documented mitigations. The recommended approach is to start with systematic pipeline investigation (not code-in-first), then build the dedicated shader program fix, and harden against edge cases in later phases.

## Key Findings

### Recommended Stack

The development environment is locked to NeoForge 1.21.1 (21.1.x) using ModDevGradle on JDK 21. LWJGL 3.3.3 ships with Minecraft 1.21.1 and provides `GL33C`/`GL30C` classes for VAO/VBO/shader operations within OpenGL 3.2 Core Profile constraints. Mixin 0.15.x (bundled with NeoForge) is the bytecode injection framework; MixinExtras provides safer alternatives to `@Redirect`. RenderDoc 1.35+ is the essential GPU debugger for capturing and inspecting draw calls, shader program bindings, and uniform state at the conflict point.

**Important correction:** "Sable" is a physics mod, NOT a shader library. Earlier project context incorrectly referenced Sable as being Veil's shader pipeline. Veil uses its own "pinwheel" shader format and `DirectShaderCompiler` for shader compilation.

**Core technologies:**
- **NeoForge MDK (ModDevGradle) 21.1.x:** The officially supported toolchain. Uses `net.neoforged.moddev` Gradle plugin. JDK 21 required. Replaces deprecated NeoGradle.
- **Mixin 0.15.x + MixinExtras:** Bytecode injection. `@Inject` is preferred over `@Overwrite`/`@Redirect` to avoid conflicts. MixinExtras' `@WrapOperation` supports composition.
- **LWJGL 3.3.3 + OpenGL 3.2 Core Profile:** The only available rendering API. Fixed-function pipeline unavailable. All rendering goes through VAOs, VBOs, and shaders.
- **RenderDoc 1.35+:** GPU frame debugger for capturing draw calls, shader program state, and uniform values. Essential for root cause investigation.
- **Parchment Mappings 2024.12+:** Recommended for human-readable parameter names during debugging.

### Expected Features

**Must have (table stakes) — P1 for launch:**
- **AW World Model Rendering Fix:** Model stretching/fixation resolved. This is the mod's reason for existence.
- **AW Item Rendering Fix:** Item textures visible in inventory/hotbar. Separate code path from models (goes through `ItemRenderer`/`GameRenderer`, not `LevelRenderer`). Iris partially fixes models but items remain broken.
- **VBO Performance Preservation:** Fix must not fall back to non-VBO rendering. Verified by FPS comparison.
- **AW and Veil Runtime Detection:** Must not crash when either mod is absent. Use `@Pseudo` and separate Mixin configs per target.
- **Iris Coexistence:** Must not break when both Iris and Veil are loaded. Iris is extremely common in modpacks alongside Veil.
- **NeoForge 1.21.1 Lock:** Single version, single loader.

**Should have (competitive differentiators) — P2 post-MVP:**
- **Diagnostic Logging Mode:** Toggleable verbose logging of AW VBO flush path. Controlled by config toggle (off by default). Enable when users submit bug reports.
- **GL State Debug Overlay:** Real-time HUD showing bound GL program, AW uniform values, matrix state. Toggle via keybind (e.g., F3+A). Most useful during development but valuable for user bug reports.
- **Graceful Fallback Modes:** Multiple fix aggressiveness levels (OFF, LIGHT, FULL) switchable at runtime without restart. LIGHT patches model path only; FULL also patches items.
- **Shader Program Capture Command:** `/awv-compat capture` dumps current GL program ID, uniforms, bound textures, and vertex attribute state to log file.

**Defer (v2+):**
- Compatibility Test Matrix (auto-detected version combinations), Auto-Iris Compat Mode, Mixin Injection Trace, Integration Debug Guide.

### Architecture Approach

The system is centered on Minecraft's `LevelRenderer.renderLevel()` frame orchestrator. AW hooks into `RenderType.clearRenderState()` via Mixin, where it borrows the current GL shader program, injects its uniforms, and draws its VBO geometry. Veil's `RenderTypeStageRegistry` injects additional `RenderStateShard` instances into the setup/clear lambda chains, changing which shader program is bound when AW's hook fires. The result is AW writing uniforms to a Veil-managed program that either lacks AW's uniform locations or uses them with different semantics.

The investigation architecture recommends starting with a mixin-based debug probe (GlStateTracker + GlStateDump + ProgramInspector) that captures the exact GL state at AW's render point with and without Veil. Then build a dedicated `AwShaderProgram` that completely replaces the "borrow current program" pattern. This is supported by a state tracking layer (GlStateTracker, ProgramSnapshot, StateValidator) and a diagnostics layer (GlStateDump, ProgramInspector, RenderFrameDebugger) that are compile-time-optional for production builds.

**Major components:**
1. **AW-side Mixins (`mixin/aw/`):** Core fix — replace "borrow shader" with dedicated AW program. AwVboRenderMixin, AwRenderTypeHookMixin.
2. **Veil-side Mixins (`mixin/veil/`):** Defensive patches to prevent Veil from corrupting AW state. VeilShaderCompilationMixin, VeilRenderTypeStageMixin, VeilProgramBindingMixin.
3. **Dedicated Shader System (`shader/`):** AwShaderProgram (GL program management), AwShaderUniforms (uniform definitions), AwShaderInstance (wrapper).
4. **State Tracking (`state/`):** GlStateTracker (program/VAO/texture tracking), ProgramSnapshot (save/restore), StateValidator (debug assertions).
5. **Diagnostics (`diagnostics/`):** GlStateDump, ProgramInspector, RenderFrameDebugger — all behind config flags.

### Critical Pitfalls

1. **Assuming the "Current Shader Program" at Injection Time (Critical):** AW borrows `GL_CURRENT_PROGRAM`, but Veil changes which program is bound. Uniforms get written to the wrong program or to a program that Veil will replace. **Prevention:** Create a dedicated AW shader program and force-bind it before AW's uniforms and draw calls. Do not rely on `GL_CURRENT_PROGRAM`.

2. **Mixin `@Overwrite`/`@Redirect` Conflicts in Rendering Pipeline (Critical):** Iris, Veil, Sodium, and Embeddium already target `LevelRenderer`, `ShaderInstance`, and `RenderType` with Mixins. `@Overwrite` has last-writer-wins semantics; `@Redirect` does not support nesting. **Prevention:** Never use `@Overwrite` or `@Redirect` in rendering pipeline targets. Use `@Inject` (HEAD/RETURN) or `@WrapOperation` (MixinExtras). Use `require = 0` on injections into classes that other rendering mods also patch.

3. **OpenGL State Desynchronization (Critical):** Both AW and Veil operate at a low OpenGL level. Calling raw GL without going through `RenderSystem`/`GlStateManager` desyncs the state cache from actual GPU state. **Prevention:** Always go through `RenderSystem` for GL state changes. Never use `glPushAttrib`/`glPopAttrib`. If raw GL is unavoidable, explicitly update the relevant cache entries afterward.

4. **VBO Performance Degradation (Moderate):** Adding state save/restore around every VBO draw call adds per-draw overhead that can negate VBO performance benefits. `glGet*` calls are GPU synchronization points. **Prevention:** Inject at the coarsest granularity (per-RenderType flush, not per-draw-call). Batch uniform uploads into a single operation. Cache uniform locations at program creation time. Define a performance budget (>5% regression is significant) before coding.

5. **"Works on My Machine" Testing Trap (Moderate):** Shader compilation, OpenGL extension support, and driver behavior vary dramatically across GPU vendors, driver versions, and OS platforms. **Prevention:** Test on at least NVIDIA, AMD, and Intel GPUs. Test with and without Iris shader packs (at least Complementary, BSL, Sildurs). Test on Windows and Linux (Mesa drivers are stricter about GLSL compliance). Document the exact test matrix.

## Implications for Roadmap

Based on research, the suggested phase structure follows the investigation-first, fix-second philosophy. The 10 failed previous attempts prove that coding without understanding Veil's pipeline is wasted effort. Each phase explicitly avoids the "vacuum cleaner" pitfall (Pitfall 11) by requiring a deliverable that increases understanding before the next phase begins.

### Phase 1: OpenGL State Investigation (Foundation)
**Rationale:** Must understand exactly what GL state differs between working (no Veil) and broken (with Veil) at AW's render point. The 10 previous failed attempts all skipped this step and guessed at injection points. RenderDoc capture plus Mixin debug probes are the only way to get the data needed for a targeted fix.
**Delivers:**
- `GlStateTracker` + `GlStateDump` + `ProgramInspector` debug mixins
- GL state diff report showing which programs, uniforms, and matrices differ with/without Veil
- Pipeline dependency graph (from ARCHITECTURE.md) mapping the exact render path
- Conflict matrix of Mixin targets already used by Iris/Veil/Sodium (from PITFALLS.md audit)
- Confirmed root cause hypothesis with concrete evidence
**Addresses features:** Core investigation capability (prerequisite for all fixes)
**Avoids pitfalls:** 1 (wrong program), 10 (matrix vs pipeline), 11 (vacuum cleaner approach)
**Research flag:** Needs `/gsd-research-phase` to decompile/read AW and Veil source code to identify exact class names, method signatures, and injection points for the debug probes.

### Phase 2: Dedicated Shader Program Fix (Core Fix)
**Rationale:** After Phase 1 confirms the program mismatch hypothesis (which is highly likely given the documented symptoms), the fix is straightforward: give AW its own shader program. This eliminates the root cause instead of working around symptoms. Pattern 1 from ARCHITECTURE.md is the most robust approach.
**Delivers:**
- `AwShaderProgram` — dedicated GL shader program for AW VBO rendering
- `AwShaderUniforms` — AW uniform definitions and upload logic
- Minimal pass-through vertex shader matching AW's VBO vertex format
- Mixin into AW VBO rendering: replace "borrow current program" with dedicated program bind/draw/unbind
- State save/restore wrapper around the draw call (targeted, not broad)
- Verifiable fix for world model rendering (the primary symptom)
**Addresses features:** AW World Model Rendering Fix (P1), VBO Performance Preservation (P1), AW + Veil Runtime Detection (P1)
**Uses stack:** Mixin for injection, LWJGL for shader program creation, MixinExtras `@WrapOperation`
**Implements architecture components:** `mixin/aw/AwVboRenderMixin`, `shader/AwShaderProgram`, `shader/AwShaderUniforms`, `state/ProgramSnapshot`
**Avoids pitfalls:** 1 (wrong program assumption — eliminated by dedicated program), 3 (GL state desync — targeted save/restore), 6 (VBO performance — coarse injection, cached locations)
**Research flag:** Standard patterns. The dedicated program approach is well-documented in LWJGL tutorials and NeoForge rendering discussions. No deeper research needed.

### Phase 3: Item Rendering and Iris Coexistence (Edge Cases)
**Rationale:** Item rendering uses a separate code path (`ItemRenderer`, `GameRenderer`) from world/entity rendering (`LevelRenderer`). The item fix may share some of the Phase 2 state interception mechanism but targets different render layers and shader programs. Separating this from Phase 2 keeps each phase's scope narrow and testable.
**Delivers:**
- Item rendering fix (inventory/hotbar AW item textures visible)
- Iris coexistence verification: fix must not break when Iris shader packs are loaded
- Mixed-mode testing: AW models + AW items + Iris shaders + Veil modifications all working
- Fallback mode detection: if Iris partially fixes models, skip duplicate model fix and only handle items
**Addresses features:** AW Item Rendering Fix (P1), Iris Coexistence (P1)
**Avoids pitfalls:** 2 (Mixin conflicts — Iris already targets some same classes, so `require=0` is critical), 5 (NeoForge version breaks — test on multiple 21.1.x minors)
**Research flag:** Needs `/gsd-research-phase` to understand Iris's partial fix for AW models and identify which Mixin injection points Iris already uses. Key research question: "Does Iris also fix the shader program issue or does it fix something else?"

### Phase 4: Hardening and Multi-Config Testing
**Rationale:** The fix works so far but only on the developer's machine. This phase validates across GPU vendors, driver versions, Iris shader packs, NeoForge minor versions, and mod combinations. Performance profiling is done here with clear pass/fail criteria.
**Delivers:**
- Cross-GPU test results (NVIDIA, AMD, Intel) — documented in test matrix
- Cross-shader-pack test results (Complementary, BSL, Sildurs, vanilla shaders)
- Multiple NeoForge minor version validation (21.1.62, 21.1.222, latest 21.1.x)
- Performance profiling: FPS with 1/5/20 entities wearing AW equipment, compared to non-VBO fallback
- Diagnostic Logging Mode (P2 feature)
- Fix works after resource pack reload (F3+T) and shader reload (F3+R in Iris)
**Addresses features:** Diagnostic Logging Mode (P2), Graceful Fallback Modes (P2)
**Avoids pitfalls:** 7 ("works on my machine"), 5 (NeoForge version-specific breaks), 6 (performance regression detection)
**Research flag:** Standard testing patterns. No deeper research needed — this is execution against a defined test matrix.

### Phase 5: Diagnostics and Production Cleanup
**Rationale:** Debug tooling from Phase 1 is useful but must not ship enabled. This phase productionizes the mod: gates debug code behind config flags, creates user-facing diagnostic features, and polishes for release.
**Delivers:**
- GL State Debug Overlay (P2) — keybind-toggle, shows bound program + AW uniform values
- Shader Program Capture Command (P3) — `/awv-compat capture` dumps GL state
- Graceful Fallback Modes (P2) — OFF/LIGHT/FULL, runtime switchable
- All debug/diagnostic code behind config flags (disabled by default)
- Clean Mixin configurations, single responsibility per config
- Release-ready mod JAR
**Addresses features:** GL State Debug Overlay (P2), Shader Program Capture Command (P3), Graceful Fallback Modes (P2)
**Avoids pitfalls:** 3 (GL state desync — all raw GL calls wrapped), 9 (cascading Mixin failures — separate configs, `require=0`)
**Research flag:** Needs `/gsd-research-phase` to design the debug overlay rendering integration (how to render the HUD text without conflicting with vanilla debug screen or other overlay mods).

### Phase Ordering Rationale

- **Phase 1 must come first** because every previous attempt that skipped systematic investigation failed. The 10 failed approaches are the evidence. Without understanding Veil's exact effect on the GL state, any fix is a guess.
- **Phase 2 (world model) before Phase 3 (items)** because items share some rendering infrastructure with models but have a separate code path. Fixing the model path first validates the dedicated program approach; the item fix then adapts that approach to a different render layer.
- **Phase 4 (testing) after Phase 2/3 but before release** because the fix must be validated across hardware/mod/version combinations. A fix that only works on one GPU is not a fix.
- **Phase 5 (production cleanup) last** because diagnostics are most useful during earlier phases. They only become a liability if shipped enabled by default.

### Research Flags

Phases likely needing deeper research during planning:
- **Phase 1:** Needs `/gsd-research-phase` to decompile/read AW and Veil source code for exact class names, method signatures, and injection points. Without precise targets, the debug probes cannot be written.
- **Phase 3:** Needs `/gsd-research-phase` to understand Iris's partial fix mechanism for AW models, to avoid duplicating or conflicting with it.
- **Phase 5:** Needs `/gsd-research-phase` for the debug overlay rendering approach — how to render overlay text without conflicting with vanilla debug screen.

Phases with standard patterns (skip research-phase):
- **Phase 2:** The dedicated shader program approach is well-documented. LWJGL shader creation, Mixin injection patterns, and state save/restore are standard techniques.
- **Phase 4:** Cross-platform testing. Test matrix definition is standard QA practice.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Verified via NeoForge MDK template, LWJGL logs from running client, and Mixin official docs. Version lock to 1.21.1 eliminates uncertainty. |
| Features | MEDIUM | Core features (model fix, item fix, VBO perf) are validated against real user symptoms from PROJECT.md. Diagnostic features are well-reasoned but implementation complexity estimates need validation. |
| Architecture | HIGH | Root cause analysis is verified by Veil wiki documentation, Iris issue #2786 (AW developer report), and the documented 10 failed attempts. The shader program mismatch is the only explanation consistent with all symptoms. |
| Pitfalls | HIGH | Multi-sourced from issue trackers (NeoForge #2919, #2636, #264; Modular Machinery #115; Iris #2651), official docs (Veil wiki, CleanroomMC Render Book), and real-world modding community reports. |

**Overall confidence:** HIGH

The highest-confidence finding is the root cause: AW's program borrowing assumption breaks when Veil changes the active shader program. The 10 failed attempts, Iris issue #2786, and the Veil architecture documentation all converge on the same explanation. The recommended fix (dedicated AW shader program) is the standard solution for this class of compatibility problem and is not speculative.

### Gaps to Address

- **Exact AW source structure:** The research files reference AW classes like "AW VBO Manager" and "AW Uniform Injector" by function, but the actual class names in AW's codebase are unknown without decompilation. Phase 1 investigation must identify the exact targets.
- **Exact Veil injection points:** Veil's `RenderTypeStageRegistry` is described at the API level, but the exact Mixin injection points and class names in Veil's implementation need source reading. Phase 1 research-phase will resolve this.
- **Iris partial fix mechanism:** The research describes Iris partially fixing AW models but leaving items broken, but the mechanism is not fully understood. Phase 3 research-phase needed.
- **Performance budget thresholds:** The research recommends defining a performance budget "before coding" but does not specify exact thresholds. This should be decided during Phase 2 planning (suggested: <5% FPS regression at 20 entities with AW equipment is acceptable; >10% is a blocking issue).
- **Shader format compatibility:** AW's VBO vertex format needs to be verified against the proposed dedicated pass-through shader. If AW uses a non-standard vertex layout (e.g., interleaved attributes, custom attribute locations), the shader must be adapted. This is low risk for a pass-through shader but needs verification in Phase 2.

## Sources

### Primary (HIGH confidence)
- NeoForge MDK template (NeoForgeMDKs/MDK-1.21.1-ModDevGradle) — build configuration, version requirements
- Veil GitHub Wiki (FoundryMC/Veil) — ShaderModification, RenderTypeStage, ShaderModification pages
- Veil API Javadocs (foundrymc.github.io) — DirectShaderCompiler, VeilRenderSystem, VeilRenderLevelStageEvent
- Mixin (SpongePowered) documentation — injection patterns, conflict resolution
- Iris issue #2786 (IrisShaders/Iris) — AW developer report of clearRenderState hook breakage
- NeoForge issue #2919 — shared vertex mode pipeline rendering bug
- PROJECT.md — validated requirements, 10 failed attempts, key decisions
- Minecraft 1.21.1 client logs (mclo.gs/d3AUa74) — confirmed LWJGL 3.3.3 and OpenGL 3.2 Core Profile

### Secondary (MEDIUM confidence)
- CleanroomMC Render Book (State Concerns) — OpenGL state management in Minecraft
- GitHub issues (Modular Machinery #115, Forge #1637) — GlStateManager popAttrib desync documentation
- IrisShaders/Iris compatibility documentation — Core Shaders compatibility
- FabricMC rendering concepts documentation — pipeline overview (concepts apply to NeoForge)
- Argon4W/AcceleratedRendering DeepWiki — Mixin architecture patterns for rendering compatibility layers
- ShaderLABS Wiki — RenderDoc attachment guide for Minecraft
- conditional-mixin library (Fallen-Breath) — conditional mixin loading
- MixinTrace Reforged — crash report augmentation pattern

### Tertiary (LOW confidence)
- Sable on Modrinth — confirmed Sable is a physics mod, not a shader library
- Intel Arc driver regression thread — driver-specific rendering failure example
- Create Flywheel rendering compatibility analysis — cross-mod compatibility pattern reference

---
*Research completed: 2026-04-30*
*Ready for roadmap: yes*
