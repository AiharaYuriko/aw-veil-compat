# Architecture Research

**Domain:** Minecraft Mod Rendering Compatibility (AW-Veil Conflict Resolution)
**Researched:** 2026-04-30
**Confidence:** HIGH (verified via multiple sources including Veil wiki, Iris issue tracker, and SDK documentation)

## Standard Architecture

### System Overview — Minecraft 1.21.1 Rendering Pipeline

The rendering pipeline flows through `LevelRenderer.renderLevel()` which is the central orchestrator for each frame. The following diagram shows the major stages and where Armourer's Workshop (AW) and Veil hook in.

```
LevelRenderer.renderLevel() (each frame)
│
├── 1. Setup: Camera, Frustum, PoseStack
│
├── 2. Sky & Fog rendering
│
├── 3. TERRAIN RENDERING (chunks)
│   ├── RenderType.SOLID chunks (opaque: stone, dirt)
│   ├── RenderType.CUTOUT chunks (no-blend transparency: glass panes)
│   ├── RenderType.CUTOUT_MIPPED chunks (leaves)
│   └── RenderType.TRANSLUCENT chunks (water, stained glass)
│       Each: setupRenderState() -> draw call(s) -> clearRenderState()
│       And: AW MIXIN HOOK into clearRenderState() ->
│           "Borrow current GL program -> inject AW uniforms -> draw VBO"
│
├── 4. BLOCK ENTITY RENDERING (TESRs: chests, signs, beacons)
│   └── Per-block-entity render() calls
│
├── 5. ENTITY RENDERING
│   ├── Opaque entities (most mobs, items)
│   └── Translucent entities (glowing, ghosts)
│       Each: setupRenderState() -> draw -> clearRenderState()
│       And: AW MIXIN HOOK (same mechanism, player entities)
│
├── 6. PARTICLES & WEATHER
│
└── 7. GUI / Hand / Hotbar (deferred to last)
```

### Component Responsibilities — Minecraft 1.21.1 Rendering

| Component | Responsibility | Implementation |
|-----------|----------------|----------------|
| `LevelRenderer.renderLevel()` | Frame orchestrator -- calls all stages in order | Vanilla Minecraft class |
| `RenderBuffers` | Manages buffer for each RenderType (SOLID, CUTOUT, etc.) | Vanilla -- holds `BufferBuilder` instances |
| `MultiBufferSource` | Routes vertex data to correct RenderType buffer | Vanilla -- interface wrapping `RenderBuffers` |
| `RenderType` | Defines a complete rendering configuration (shader, blend, depth, etc.) | Vanilla -- composes `RenderStateShard`s via lambda `andThen` chaining |
| `RenderStateShard` | Atomic OpenGL state unit -- has `setupState()` and `clearState()` runnables | Vanilla -- base class for all render state shards |
| `ShaderStateShard` (inner class) | Binds/unbinds GL shader program via `ShaderInstance.apply()`/`clear()` | Vanilla -- calls `glUseProgram(programId)` |
| `ShaderInstance` | Wraps GL shader program -- compilation, uniform management, binding | Vanilla -- loaded by `GameRenderer.loadShaders()` |
| `RenderSystem` | Central OpenGL state tracker -- matrix stacks, texture units, default uniforms | Vanilla -- thread-safe render state management |

### Component Responsibilities — Armourer's Workshop (AW)

| Component | Responsibility | Implementation |
|-----------|----------------|----------------|
| AW VBO Manager | Manages vertex buffer objects for armour skin geometry | AW internal class -- holds VBO handles + vertex format |
| AW Mixin (clearRenderState hook) | Triggers AW VBO rendering during RenderType cleanup | `@Inject` into `RenderType.clearRenderState()` |
| AW Uniform Injector | Reads current GL program, writes AW-specific uniforms | Calls `glGetIntegerv(GL_CURRENT_PROGRAM)` then `glUniform*()` for `aw_ModelViewMatrix`, `aw_MatrixFlags`, `aw_TextureMatrix` |
| AW Shader Assumptions | Expects vanilla shader program with known uniform locations | Hardcoded uniform names and matrix semantics (coupled to vanilla shaders) |
| AW VBO Draw | Issues `glDrawElements()` with borrowed program active | Standard indexed VBO draw call |

### Component Responsibilities — Veil (FoundryMC)

| Component | Responsibility | Implementation |
|-----------|----------------|----------------|
| `RenderTypeStageRegistry` | Registers extra `RenderStateShard`s to existing `RenderType`s | Mixin-injects into RenderType lambda `setupState()`/`clearState()` chains |
| `VeilRegisterFixedBuffersEvent` | Registers custom fixed buffers that auto-flush at specific frame stages | Event system -- patches `LevelRenderer.renderLevel()` |
| `VeilRenderLevelStageEvent` | Fires events at specific points in the render loop | Mixin into `LevelRenderer.renderLevel()` |
| Shader Modification System | Injects GLSL code into vanilla shader sources at load time | Intercepts shader source before compilation; supports `[FUNCTION main(0) HEAD]`, `[UNIFORM]`, `[GET_ATTRIBUTE]` directives |
| `DirectShaderCompiler` | Compiles Veil shader programs | `foundry.veil.impl.client.render.shader.compiler` |
| `CachedShaderCompiler` | Adds caching layer over DirectShaderCompiler | Subclass of DirectShaderCompiler |
| `VeilRenderSystem` | Manages Veil-specific render state (light direction, render time) | Central bridge between Minecraft's `RenderSystem` and OpenGL |
| Post-Processing Pipeline | JSON-defined framebuffer post-processing stages | Custom pipeline executor -- replaces/extends vanilla `PostChain` |

## Recommended Project Structure

```
aw-veil-compat/
├── src/main/java/com/example/awveil/
│   ├── AwVeilCompat.java                # Mod entry point (NeoForge @Mod)
│   │
│   ├── mixin/                           # All Mixin patches
│   │   ├── aw/                          # Patches into Armourer's Workshop code
│   │   │   ├── AwVboRenderMixin.java    # Core fix: replace "borrow shader" with dedicated AW program
│   │   │   └── AwRenderTypeHookMixin.java # Adjust AW's clearRenderState hook timing/behavior
│   │   │
│   │   ├── veil/                        # Patches into Veil code
│   │   │   ├── VeilShaderCompilationMixin.java   # Ensure AW uniforms survive Veil shader compilation
│   │   │   ├── VeilRenderTypeStageMixin.java     # Prevent Veil stage registration from corrupting AW state
│   │   │   └── VeilProgramBindingMixin.java      # Track/guard program binding for AW compatibility
│   │   │
│   │   └── minecraft/                   # Patches into vanilla Minecraft (last resort)
│   │       └── LevelRenderMixin.java    # Wrapping/state save-restore around render stages
│   │
│   ├── shader/                          # AW's own shader program management
│   │   ├── AwShaderProgram.java         # Dedicated GL shader program for AW VBO rendering
│   │   ├── AwShaderUniforms.java        # AW uniform definitions and upload logic
│   │   └── AwShaderInstance.java        # Wrapper around ShaderInstance for AW-specific use
│   │
│   ├── state/                           # OpenGL state tracking and management
│   │   ├── GlStateTracker.java          # Tracks current GL program, VAO, texture units
│   │   ├── ProgramSnapshot.java         # Captures/restores shader program state around AW rendering
│   │   └── StateValidator.java          # Debug: validates GL state assumptions at AW render points
│   │
│   ├── diagnostics/                     # Debug and investigation tools
│   │   ├── GlStateDump.java             # Dumps complete GL state at render points for analysis
│   │   ├── ProgramInspector.java        # Reads uniforms/program info from bound GL program
│   │   └── RenderFrameDebugger.java     # Per-frame rendering trace for conflict detection
│   │
│   └── config/                          # Runtime configuration
│       ├── AwVeilConfig.java            # Toggle patches, enable/disable debug output
│       └── CompatibilityMode.java       # Detection of loaded mods to select patch strategy
│
├── src/main/resources/
│   ├── META-INF/
│   │   └── neoforge.mods.toml           # NeoForge mod metadata
│   │
│   └── awveil.mixins.json               # Mixin configuration (targets, refmap)
│
└── build.gradle                         # Gradle build (NeoForge + Mixin)
```

### Structure Rationale

- **mixin/aw/:** Patches that modify AW's own rendering behavior. The primary target is replacing the "borrow current shader" pattern with a dedicated AW shader program. This is the highest-risk, highest-impact component.
- **mixin/veil/:** Patches that modify Veil's shader pipeline to not corrupt the state that AW depends on. These are defensive patches -- they prevent Veil from breaking AW rather than fixing AW itself.
- **mixin/minecraft/:** Vanilla rendering patches that provide a stable execution environment. Used as a fallback when AW-side or Veil-side patches are insufficient.
- **shader/:** A dedicated AW shader program that replaces the "borrow" pattern. This is the most robust fix -- if AW uses its own program, program confusion cannot occur.
- **state/:** OpenGL state tracking infrastructure. Essential for debugging and for implementing save/restore patterns around AW's rendering hooks.
- **diagnostics/:** Debugging tools for investigating the exact state at conflict points. Critical for the initial investigation phase.

## Architectural Patterns

### Pattern 1: Dedicated Shader Program (Core Fix)

**What:** Replace AW's "borrow current GL program and inject uniforms" pattern with a dedicated AW-managed shader program. AW creates its own ShaderProgram, applies it before VBO drawing, and clears it after. This eliminates all program-confusion issues.

**When to use:** As the primary fix. This is the most robust approach because it eliminates the root cause (program confusion) rather than working around symptoms.

**Trade-offs:**
- Requires AW to have a compatible vertex shader that matches its VBO vertex format
- If AW's VBO format uses a non-standard vertex layout, the shader must accept that layout
- AW's existing shader (if it has one embedded) can be adapted; otherwise a minimal pass-through shader is needed
- May not fix issues where Veil modifies the VAO/VBO binding state itself (buffer binding leakage)

**Example (pseudocode):**

```
// INSTEAD OF:
int currentProgram = glGetIntegerv(GL_CURRENT_PROGRAM);
glUniformMatrix4fv(glGetUniformLocation(currentProgram, "aw_ModelViewMatrix"), ...);

// DO:
awProgram.bind();
awProgram.setUniform("aw_ModelViewMatrix", ...);
glBindVertexArray(awVao);
glDrawElements(...);
ShaderProgram.unbind();
```

### Pattern 2: State Save/Restore Wrapper

**What:** Wrap AW's rendering block in a complete OpenGL state save/restore. Before AW renders, snapshot the current GL program, VAO, texture units, blend state, depth state, and matrix mode. After AW renders, restore everything.

**When to use:** When the dedicated shader program fix is not sufficient alone (because Veil modifies more than just the program -- e.g., texture bindings, matrix state, or VAO state).

**Trade-offs:**
- Performance overhead from state queries/restores (glGet* calls are GPU synchronization points)
- Fragile -- only fixes states you explicitly save/restore
- May mask deeper issues that cause rendering artifacts beyond state corruption
- Useful as a diagnostic tool to identify which state modifications actually matter

**Example (pseudocode):**

```
// Capture
int savedProgram = glGetIntegerv(GL_CURRENT_PROGRAM);
int savedVao = glGetIntegerv(GL_VERTEX_ARRAY_BINDING);
int savedTexture = glGetIntegerv(GL_TEXTURE_BINDING_2D);

// Set up AW state
awProgram.bind();
awProgram.setUniforms(poseMatrix, textureMatrix, flags);
glBindVertexArray(awVao);
glDrawElements(...);

// Restore
glUseProgram(savedProgram);
glBindVertexArray(savedVao);
glBindTexture(GL_TEXTURE_2D, savedTexture);
```

### Pattern 3: RenderType Stage Insertion (Long-Term Fix)

**What:** Instead of hooking into `clearRenderState()` (which is called during RenderType cleanup and may have unpredictable state), register AW's VBO rendering as its own RenderType stage in Veil's system. This puts AW rendering on the same footing as other render stages.

**When to use:** When the goal is to make AW's VBO rendering a first-class citizen in the modified pipeline rather than a hack attached to cleanup. This is the architecturally correct long-term approach.

**Trade-offs:**
- Requires understanding of Veil's `RenderTypeStageRegistry` and `VeilRegisterFixedBuffersEvent` APIs
- AW rendering may need to happen at a specific point in the frame for correct depth/alpha ordering
- More invasive change to AW's architecture -- requires AW to use a proper RenderType instead of its current approach
- This is the long-term correct fix but requires more AW-side refactoring

### Pattern 4: Debug Probe Mixin (Investigation Pattern)

**What:** A Mixin that injects at the exact point AW renders and dumps all relevant OpenGL state: current program ID, program uniform locations for AW's uniform names, VAO binding, texture bindings, matrix stack contents.

**When to use:** During the investigation phase to understand exactly what state differs between "working" (no Veil) and "broken" (with Veil).

**Trade-offs:**
- Only useful for debugging, not for production
- Essential for data-driven decision making about which fix to apply
- Use `GL_INVALID_OPERATION` error capture to detect uniform upload failures in real-time

## Data Flow

### Normal (Working) Flow -- AW VBO Without Veil

AW's Mixin injects into `RenderType.clearRenderState()` at `@At("HEAD")`, meaning AW's code runs BEFORE the composed clear lambda chain. At that point, the shader program set up during `setupRenderState()` is still bound.

```
RenderType.setupRenderState()
  -> [multiple RenderStateShard.setupState() calls]
  -> ShaderStateShard.setupState(): glUseProgram(vanillaShader.programId)
  -> [more shard setups]
  -> Lambda composition complete

BufferBuilder.draw() / glDrawElements()
  -> Vanilla geometry drawn with vanillaShader

RenderType.clearRenderState()
  -> AW MIXIN @Inject(at = @At("HEAD")) -- AW runs FIRST
    -> glGetIntegerv(GL_CURRENT_PROGRAM) -> vanillaShader.programId  [OK]
    -> glGetUniformLocation(vanillaShader.programId, "aw_ModelViewMatrix") -> finds it
    -> glUniformMatrix4fv(...) -> writes AW uniforms
    -> glBindVertexArray(awVao)
    -> glDrawElements(...) -> draws AW geometry with vanillaShader  [OK]
  -> [then the composed clear chain runs in reverse]
  -> ShaderStateShard.clearState(): glUseProgram(0)
```

**Key insight**: Without Veil, the active shader at the AW hook point is always the vanilla shader for the current RenderType. AW's uniform names exist on that shader. This works because AW's code executes before the shader is unbound.

### Broken Flow -- AW VBO With Veil

Veil's `RenderTypeStageRegistry` injects additional `RenderStateShard` instances into the setup/clear lambda chains. This means:

1. The shader program may be replaced with a Veil-managed program (via `ShaderStateShard` substitution)
2. Extra shards add state changes that AW doesn't account for

```
RenderType.setupRenderState()
  -> [vanilla shard setups]
  -> VEIL INJECTED SHARD: extra ShaderStateShard or state modification
  -> ShaderStateShard.setupState(): glUseProgram(VEIL_MODIFIED_SHADER)
  -> [more shard setups]

BufferBuilder.draw() / glDrawElements()
  -> Veil-altered geometry drawn with VEIL_MODIFIED_SHADER

RenderType.clearRenderState()
  -> AW MIXIN @Inject(at = @At("HEAD")) -- AW runs FIRST
    -> glGetIntegerv(GL_CURRENT_PROGRAM) -> VEIL_MODIFIED_SHADER  [PROBLEM]
    -> glGetUniformLocation(veilShader, "aw_ModelViewMatrix") -> -1 (NOT FOUND)  [PROBLEM]
    -> glUniformMatrix4fv(-1, ...) -> GL_INVALID_OPERATION (silent failure)  [PROBLEM]
    -> OR: uniform IS found but has different semantics -> wrong rendering  [PROBLEM]
  -> [then the composed clear chain runs]
  -> VEIL INJECTED SHARD.clearState(): extra cleanup
  -> ShaderStateShard.clearState(): glUseProgram(0)
```

### Root Cause Summary

Veil modifies the shader program that is bound when AW's hook fires. AW reads the wrong program, tries to write uniforms that don't exist, and the render silently fails (GL_INVALID_OPERATION for `glUniform*` on a program that doesn't have that uniform is silently swallowed by most drivers).

### Key State Flows That Get Corrupted

1. **Program Binding Flow:**
   Vanilla: setupState -> bind(vanillaShader) -> draw -> AW hook -> read(vanillaShader) -> OK
   Veil:    setupState -> bind(veilShader) -> draw -> AW hook -> read(veilShader) -> uniform -1

2. **Uniform Upload Flow:**
   AW reads current program ID
     -> glGetUniformLocation(programId, "aw_ModelViewMatrix")
     -> if Veil replaced the program: location = -1 (not found)
     -> if Veil modified the program but kept uniform: same location [OK case, unlikely]
     -> if Veil added the uniform with different semantics: wrong rendering

3. **RenderType Lambda Composition Flow:**
   Vanilla setup chain:  [shader, texture, depth, blend, ...]
   Veil setup chain:     [shader, texture, depth, VEIL_EXTRA, blend, ...]
   Vanilla clear chain:  [..., blend, depth, texture, shader]
   Veil clear chain:     [..., VEIL_CLEAR, blend, depth, texture, shader]
   AW hooks at HEAD of clear, but Veil's extra shards change what state is active.

## Conflict Point Analysis

### Conflict Point 1: Shader Program Mismatch (Critical)

**What happens:** Veil replaces vanilla shaders with its own compiled programs via `RenderTypeStageRegistry.addStage()`. When AW reads `GL_CURRENT_PROGRAM`, it gets a Veil-managed program that doesn't have AW's uniform names.

**Detection:** Compare `glGetIntegerv(GL_CURRENT_PROGRAM)` at AW's hook point with and without Veil loaded. Compare the program's uniform list using `glGetProgramiv()`/`glGetActiveUniform()`.
- Without Veil: program ID = X (vanilla shader), has uniform "aw_ModelViewMatrix" at location Y
- With Veil: program ID = Z (Veil shader), has uniform "aw_ModelViewMatrix" = -1 or location W

**Likelihood:** VERY HIGH. This is the most probable root cause given the symptom (model rendering breaks, disabling AW VBO fixes it). The 10 failed AW-side attempts all confirm that the program environment is wrong.

### Conflict Point 2: Veil Shader Compilation Drops AW Uniforms (Critical)

**What happens:** Even when Veil uses vanilla shader sources, its shader modification system (`[FUNCTION main(0) HEAD]` injections) may strip or rename uniforms that AW expects. Veil's `DirectShaderCompiler` may produce a program where AW's uniform names don't resolve.

**Detection:** Decompile Veil's modified shader source (after modification injection) and check if AW uniforms survive. Compare the shader source with/without Veil modifications.

**Likelihood:** HIGH. The project context reports that patching Veil's `DirectShaderCompiler` to inject AW uniforms only worked partially -- some shaders got patched but others didn't.

### Conflict Point 3: RenderType Lambda Composition Order (Moderate)

**What happens:** Veil's `RenderTypeStageRegistry` Mixin injects additional `RenderStateShard` instances into the lambda chain. This changes the order of state setup/clear relative to AW's hook point. AW may be executing at a point where the shader has already been unbound or a different shader is active.

**Detection:** Trace the `setupState.run()` / `clearState.run()` lambda chains with and without Veil. Use a stack trace dump at AW's hook point to understand the call order.

**Likelihood:** MODERATE. Depends on whether Veil injects shader-affecting shards or just non-shader shards (render target, blend state).

### Conflict Point 4: VAO/VBO Binding Conflicts (Moderate)

**What happens:** Veil's post-processing pipeline or RenderType stage modifications may change the active VAO or VBO bindings. When AW tries to bind its VBO and draw, the wrong VAO is active, or the element buffer is from a different mesh.

**Detection:** At AW's draw call point, dump `GL_VERTEX_ARRAY_BINDING`, `GL_ELEMENT_ARRAY_BUFFER`, and the contents of AW's expected VBO handles. Compare with/without Veil.

**Likelihood:** MODERATE. Less likely than program confusion but possible if Veil modifies buffer state.

### Conflict Point 5: Matrix Stack Corruption (Moderate)

**What happens:** Veil's ShaderStage events or VeilRenderSystem may push/pop matrix state differently than vanilla. AW's shader expects specific matrix contents (model-view matrix, texture matrix) that no longer match reality after Veil's modifications.

**Detection:** Dump model-view and projection matrix contents at AW's render point with/without Veil. Compare for unexpected differences.

**Likelihood:** MODERATE. The project context reports that using "接近 vanilla 的 ModelView 矩阵" resulted in "模型放大、固定在玩家附近" -- indicating matrix assumptions are part of the problem.

### Conflict Point 6: ImmediateState.mergeRendering (Iris Pattern)

**What happens:** Iris issue #2786 showed that Iris's `ImmediateState.mergeRendering` flag causes the `clearRenderState` method to be skipped entirely. AW's hook into `clearRenderState` stops firing.

**Detection:** Check if Veil has any "merge rendering" or "deferred flush" mechanism that short-circuits RenderType lifecycle. Monitor whether AW's mixin callback is actually being invoked.

**Likelihood:** LOW for Veil directly, but the pattern is documented via Iris and worth checking.

## Recommended Investigation Build Order

### Phase 1: OpenGL State Probe (Foundation)

**Goal:** Establish a reliable debugging setup to capture GL state at conflict points.

**Build:**
1. `GlStateTracker.java` -- Reads and caches GL state (current program, VAO, texture bindings, matrix state)
2. `GlStateDump.java` -- Formats and logs state to the debug console
3. `ProgramInspector.java` -- Given a program ID, lists all active uniforms (names, types, locations)
4. Mixin to inject at AW's VBO draw point and dump state

**Success criteria:** Can reproduce the render bug and capture a GL state diff between "working" (no Veil) and "broken" (with Veil).

**De-risks:** Shows exactly which programs are bound, which uniforms are available, and which matrix values are active at the conflict point. Without this data, all fixes are guesses.

### Phase 2: Shader Pipeline Analysis

**Goal:** Understand exactly what Veil does to shader compilation and program binding.

**Build:**
1. Mixin to intercept Veil's `DirectShaderCompiler.compile()` and log all compiled shaders
2. Mixin to intercept `RenderTypeStageRegistry.addStage()` and log all registered stages
3. Capture the final GLSL source after Veil's modification injection
4. Compare uniform availability between vanilla and Veil-modified shaders

**Success criteria:** Know which specific Veil operation destroys AW's uniform expectations.

**De-risks:** If Veil modifies shader source, the fix is to inject AW uniforms during Veil's modification process. If Veil replaces programs entirely, the fix is to use a dedicated AW program.

### Phase 3: Minimal Fix Prototype

**Goal:** Implement the simplest possible fix that produces correct rendering.

**Build:**
1. `AwShaderProgram.java` -- Create a minimal pass-through shader that accepts AW's VBO vertex format
2. Mixin into AW's VBO rendering to use the dedicated AW program instead of borrowing the current one
3. Wrap with state save/restore to prevent side effects on Veil's state

**Success criteria:** AW models render correctly with Veil loaded. Performance is comparable to the non-Veil VBO path.

**De-risks:** The dedicated program approach eliminates the root cause (program confusion). If it works, it works everywhere. If it does not, the remaining issue is VAO/VBO or matrix state (not program).

### Phase 4: Edge Case Hardening

**Goal:** Fix remaining issues and ensure compatibility with Iris.

**Build:**
1. Test with Iris shader packs loaded alongside Veil
2. Test with different RenderType configurations (entity, item, block entity)
3. Add compatibility detection and fallback paths
4. Performance benchmarking

**Success criteria:** All AW models and items render correctly in all tested configurations. No regression on the non-Veil path.

### Phase 5: Production Cleanup

**Goal:** Remove debug tooling, finalize patches, release.

**Build:**
1. Move all debug/diagnostic code behind a config flag (disabled by default)
2. Clean up Mixin configurations
3. Test against full modpack scenarios
4. Release

## Anti-Patterns

### Anti-Pattern 1: Patching Random RenderType Lifecycle Points

**What people do:** Try every possible injection point in the RenderType lifecycle (HEAD/RETURN of setupState, clearState, draw, etc.) until something "works."

**Why it's wrong:** This is the essence of the 10 failed directions described in the project context. Without understanding the actual state at the injection point, you are guessing. Even if a point happens to work in one test configuration, it will break in another because the state dependencies are not understood.

**Do this instead:** First instrument the rendering path to capture actual GL state at the AW render point (Phase 1). Use data to choose the fix strategy, not trial and error.

### Anti-Pattern 2: Fixing at the Wrong Level of Abstraction

**What people do:** Patch the symptom (wrong uniforms -> add more uniforms to more programs). This is attempting to fix Veil's behavior rather than fixing AW's broken assumption.

**Why it's wrong:** The fundamental broken assumption is "the current GL program is always the vanilla block/entity shader, and I can write my uniforms to it." This assumption is violated by ANY shader-modifying mod (Veil, Iris, OptiFine, Oculus, Canvas). Patching each individual violating mod is not sustainable.

**Do this instead:** Fix the assumption at AW's level. Stop borrowing shaders. Use a dedicated AW program. This works regardless of which shader mod is loaded.

### Anti-Pattern 3: Overly Broad State Save/Restore

**What people do:** Save every possible GL state, do AW rendering, restore everything.

**Why it's wrong:** `glGet*` calls are GPU synchronization points. Reading 15+ state values per frame kills performance. State save/restore also masks the underlying issue and makes debugging harder. If the dedicated program fix works, save/restore is unnecessary.

**Do this instead:** Use targeted save/restore only for the state that is actually conflicting (identified via Phase 1 probing). Better yet, eliminate the need for save/restore by using a dedicated AW program.

### Anti-Pattern 4: All-in-One Mixin God Class

**What people do:** Write a single Mixin class that patches into AW, Veil, and vanilla simultaneously with complex conditional logic.

**Why it's wrong:** Mixin conflicts are hard to debug. A single class patching into multiple mods creates implicit coupling. If AW or Veil updates, the god class needs to be untangled.

**Do this instead:** Separate mixins by target (mixin/aw/, mixin/veil/, mixin/minecraft/). Each mixin has a single responsibility. Use config flags to enable/disable individual mixins.

## Integration Points

### Internal Boundaries

| Boundary | Communication | Notes |
|----------|---------------|-------|
| AW-side mixins <-> AW internal state | Direct method access + @Shadow | Must match AW's internal class structure (use AW source or decompilation) |
| Veil-side mixins <-> Veil internal state | Direct method access + @Shadow | Must match Veil's internal class structure (use Veil source or decompilation) |
| Patches <-> Diagnostics | Event/callback: diagnostics can observe but not modify | Diagnostics should be compile-time optional (build config flag) |
| AW shader program <-> VBO draw | Standard GL program binding + draw | The dedicated AW program replaces "borrow" pattern entirely |

### Mod Integration Boundaries

| Mod | How Compat Mod Interacts | Risk |
|-----|--------------------------|------|
| Armourer's Workshop | Mixin into AW VBO rendering classes (clearRenderState hook) | HIGH -- AW internals may change between versions |
| Veil (FoundryMC) | Mixin into Veil's RenderTypeStageRegistry, DirectShaderCompiler, ShaderProgram binding | HIGH -- Veil internals may change; LGPL licensed (check terms) |
| Vanilla Minecraft (1.21.1) | Mixin into RenderType, LevelRenderer (minimal, last resort) | LOW -- vanilla 1.21.1 is frozen |
| Iris (optional compatibility) | No direct mixin -- compatibility achieved via robust AW-side fix | MEDIUM -- Iris behavior is unpredictable, tested via integration testing |
| NeoForge | Standard @Mod, events system, mixin registration | LOW -- standard NeoForge patterns |

## Scaling Considerations

| Concern | Small Setup (2 mods) | Medium Setup (5-10 mods) | Full Modpack (50+ mods) |
|---------|----------------------|--------------------------|--------------------------|
| Rendering compatibility | AW + Veil only tested | Add Iris, other shader mods | Unknown conflicts with any rendering mod |
| Performance | Dedicated program minimal overhead | More programs = more state switches | Shader compilation at load time scales poorly if many programs |
| Debugging | Simple logs sufficient | Need configurable debug levels | Need full diagnostics framework, crash-proof |
| State tracking | Track only program ID | Track program + VAO + textures | Track full GL state (expensive, for debug only) |

### Recommended Approach for All Scales

The dedicated AW shader program (Pattern 1) is the right fix for all scales. It eliminates the root cause regardless of how many other mods are loaded. The main scaling concern is ensuring the diagnostics framework does not leak into production -- gate all debug tooling behind a compile-time flag or runtime config.

## Sources

- FoundryMC/Veil GitHub Wiki -- RenderTypeStage, Shader, ShaderModification, PostProcessing pages
- IrisShaders/Iris GitHub Issue #2786 -- "Broken the setup/clear of RenderType in Iris 1.8.12" (AW developer report)
- IrisShaders/Iris -- Core Shaders Compatibility documentation
- FabricMC 1.21 Rendering Concepts documentation
- DeepWiki jacobo-mc/mc_1.18.1_src -- Rendering System and Level Renderer analysis
- Minecraft Forge/Official documentation on RenderStateShard, ShaderInstance architecture
- Project context -- 10 failed attempts documented in PROJECT.md

---
*Architecture research for: AW-Veil Compatibility Mod (Minecraft 1.21.1 NeoForge)*
*Researched: 2026-04-30*
