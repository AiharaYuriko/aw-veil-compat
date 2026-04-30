# Phase 2: Core World Model Rendering Fix — Research

**Researched:** 2026-04-30
**Domain:** AW Dedicated Shader Program for VBO Rendering (save/restore GL program around ShaderUniforms.end())
**Confidence:** HIGH (all code paths verified against source, existing probe mixin confirms ShaderUniforms.end() target)
**Approach:** Pattern 1 — Dedicated Shader Program

---

## Summary

The root cause of AW model rendering failure under Veil has been confirmed at every level: GL state (probes), source code (ShaderUniforms.end() reads GL_CURRENT_PROGRAM), and shader compilation (AW uniforms never declared in Veil-compiled programs). The previous fix approach (ResourceProvider wrapping in Veil's ShaderManager) was implemented and is viable, but this document researches an alternative that is architecturally cleaner: **giving AW its own dedicated GL shader program** and injecting a save/restore around AW's uniform application at `ShaderUniforms.end()`.

**The approach:**
1. At `ShaderUniforms.end()` HEAD: save current GL program, bind AW's dedicated shader (which declares all 7 AW uniforms)
2. AW reads GL_CURRENT_PROGRAM = our shader, links AW uniforms successfully (locations not -1), applies values, triggers VBO draw
3. At `ShaderUniforms.end()` TAIL: restore the previous GL program

This completely bypasses the "borrow current shader" problem. AW always sees a shader that has its uniforms, regardless of what Veil, Iris, or any other mod has bound.

**Primary recommendation:** Create a minimal AW-compatible GLSL shader program via LWJGL, inject at `ShaderUniforms.end()` (HEAD for save/bind, TAIL for restore). Lazy-initialize the program on the render thread. Capture `ProjMat` and `ModelViewMat` from Minecraft's `RenderSystem` before binding our shader.

### Key Findings

| Finding | Confidence | Source |
|---------|------------|--------|
| `ShaderUniforms` class exists at `moe.plushie.armourers_workshop.core.client.shader.ShaderUniforms` with `end()` and `apply(int)` methods | HIGH | Existing `ShaderUniformsProbeMixin.java` targets it, compiles and works |
| `ShaderUniforms.end()` reads `GL_CURRENT_PROGRAM` at call time (not cached) | HIGH | `ShaderUniformsProbeMixin` calls `GL20.glGetInteger(GL20.GL_CURRENT_PROGRAM)` in `onEnd()` to capture the program |
| `ShaderUniforms.apply(int program)` calls `glGetUniformLocation(program, name)` for each AW uniform | HIGH | AW's `ShaderUniform.link()` at line 55: `this.location = GL20.glGetUniformLocation(program, name)` |
| AW's `AbstractShaderObjectState.pop()` does NOT restore the GL program | HIGH | Source code: `pop()` restores VAO, VBO, IBO but `programId` is never used in `pop()` — only saved for uniform linking |
| AW's VBO vertex format matches `DefaultVertexFormat.NEW_ENTITY` | HIGH | AW's `AbstractVertexFormat.wrap()` wraps Minecraft's `VertexFormat`; AW renders through entity RenderTypes using standard entity format |
| AW always sets `aw_MatrixFlags` bit 0x01 (apply transforms) in `Shader.render()` | HIGH | Source: `context.setMatrixFlags(pose.properties() \| 0x01)` |
| GL context is available at injection time (render thread) | HIGH | Injection fires during AW's VBO rendering which runs on the render thread inside Minecraft's render pipeline |

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| AW dedicated shader program creation | Our compat mod | — | Create and manage the AW-specific GL program with all 7 AW uniforms |
| GL program save/restore around AW render | Our compat mod | — | Mixin @Inject at ShaderUniforms.end() HEAD/TAIL |
| Uniform value upload at render time | AW (existing code) | — | AW's `ShaderUniform.apply()` / `AbstractShaderUniformState.applyVariables()` handles this unchanged |
| VBO buffer management | AW (existing code) | — | AW's `AbstractShaderVertexBuffer` / `ShaderVertexObject` system unchanged |
| Matrix capture from Minecraft | Our compat mod | — | Capture `ProjMat` / `ModelViewMat` from `RenderSystem` before binding our program |
| Performance preservation | Our compat mod | — | Only 2 glUseProgram calls added per AW render cycle (negligible overhead) |

---

## User Constraints (from CONTEXT.md)

No CONTEXT.md exists for Phase 2. The following are inherited from REQUIREMENTS.md and STATE.md:

### Locked Decisions (from STATE.md)

| # | Decision | Rationale |
|---|----------|-----------|
| 1 | Investigation-first approach | 10 failed AW-side attempts prove coding without understanding is wasted effort |
| 2 | Never use @Overwrite/@Redirect in rendering pipeline targets | Iris, Veil, Sodium already target these classes; use @Inject or @WrapOperation |
| 3 | Always use RenderSystem for GL state changes where possible | Raw GL desyncs GlStateManager cache |
| 4 | Use ModDevGradle 2.0.141 | Earliest available is 2.0.118; 2.0.141 is latest |
| 5 | Remove Mixin annotation processor dependency | ModDevGradle handles it automatically |
| 6 | Use @Mixin(targets = "name") (string targets) for mod classes | Avoids compile-time dependency on mod classes; @Pseudo for optional targets |
| 7 | Use require = 0 on all mod-target mixins | Prevents cascading failures when mod versions change |
| 8 | Self-contained classes with zero mod dependency | Avoids mapping mismatches (AW uses Fabric mappings); ensures thread safety |

### Claude's Discretion
- Exact GLSL source for the dedicated shader program (vertex format, uniforms, pass-through behavior)
- Injection point precise annotation target within `ShaderUniforms.end()` (@At("HEAD") vs @At("INVOKE"))
- Whether to use explicit layout(location=N) in GLSL or post-link attribute binding
- Whether to capture matrices from RenderSystem or from the current GL program state
- Lazy init vs. deferred init on first client tick

### Deferred Ideas (OUT OF SCOPE)
- FALLBACK-01 through TRACE-01 requirements (v2 features)
- Iris auto-compat mode
- Multi-version support
- Full in-game shader debugger
- Rewriting AW's VBO renderer

---

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| LWJGL GL20/GL32 | 3.3.3 (bundled) | GL program compilation, uniform handling, draw calls | Minecraft bundles LWJGL 3.3.3. GL20 for shader program create/compile/link, uniform operations. No new dependencies needed. |
| Mixin (SpongePowered) | 0.15.x (bundled) | Injection at ShaderUniforms.end() HEAD/TAIL | Standard NeoForge modding tool; existing probes already target this class. |
| Minecraft RenderSystem | 1.21.1 (bundled) | Matrix capture (ProjMat, ModelViewMat) before binding our program | `RenderSystem.getProjectionMatrix()` and `RenderSystem.getModelViewMatrix()` provide correct current matrices. |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| AW's `ShaderUniforms` | from AW JAR (runtime) | Target class for Mixin injection; reads GL_CURRENT_PROGRAM | At injection time (runtime only, no compile-time dependency) |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Hard-coded GLSL strings (in Java) | Load `.vsh`/`.fsh` from resource packs | Resource loading requires async setup; hard-coded GLSL is deterministic and has zero loading complexity |
| Explicit `layout(location = N)` in GLSL | `glBindAttribLocation()` post-link | Both work equally; layout in shader source is cleaner and avoids post-link code |
| Lazy program init on first render | Init at mod constructor | Mod constructor may not have GL context; lazy init is safer since we're always on render thread at injection time |
| Capture matrices from RenderSystem | Capture from glGetFloatv | glGetFloatv causes pipeline stall; RenderSystem provides cached matrices with no GL roundtrip |
| Inject at `ShaderUniforms.end()` | Inject at `Shader.setupRenderState()` | `end()` directly reads GL_CURRENT_PROGRAM — perfect interception point. `setupRenderState` already has the cached program. |

---

## Architecture Patterns

### System Architecture Diagram

```
AW VBO RENDERING: HOW IT WORKS (No Veil)
============================================

1. Minecraft RenderType.setupRenderState()
   → binds correct program (e.g. rendertype_entity_solid)
   → sets ModelViewMat, ProjMat uniforms on that program

2. Minecraft draws geometry → RenderSystem.setDrawElementsCallback() fires
   → Channel.callout() → pipeline.render(shader, renderType)
   
3. Shader.render() calls:
   a. context.applyUniforms()
      → AbstractShaderUniformState.get(programId)
      → ShaderUniform.link(program) → glGetUniformLocation() ✓ found!
      → ShaderUniform.apply() → glUniform*() ✓ writes uniforms
   b. context.draw(object) → VBO draw call ✓ renders geometry

AW VBO RENDERING: WITH VEIL (Broken)
========================================

1. Veil overrides the shader pipeline via DirectShaderCompiler
   → Veil-compiled programs do NOT have AW's uniform declarations
   
2. Shader.render() calls:
   a. context.applyUniforms()
      → ShaderUniform.link(program) → glGetUniformLocation() → returns -1 ✗
      → it.isLinked() returns false → uniform removed from list → SILENT DROP
   b. context.draw(object) → VBO draw call → nothing renders (no uniforms applied)

FIX: DEDICATED SHADER PROGRAM (Pattern 1)
=============================================

[BEFORE end()]  Our @Inject at HEAD:
    savedProgram = glGetInteger(GL_CURRENT_PROGRAM)
    glUseProgram(awProgram)  ─── bind our shader ✓

[INSIDE end()]  AW's normal flow:
    currentProgram = glGetInteger(GL_CURRENT_PROGRAM) = awProgram ✓
    apply(awProgram):
        glGetUniformLocation(awProgram, "aw_ModelViewMatrix") → valid loc ✓
        glUniformMatrix4fv(...)  → writes to awProgram ✓
        ... all 7 uniforms succeed
    VBO draw call renders with awProgram ✓

[AFTER end()]   Our @Inject at TAIL:
    glUseProgram(savedProgram)  ─── restore original program ✓
```

### Mixin Injection Design

```
@Mixin(targets = "moe.plushie.armourers_workshop.core.client.shader.ShaderUniforms")
public class AwDedicatedShaderMixin {

    private static int savedProgram = 0;        // saved program from HEAD
    private static int awProgram = 0;           // our dedicated program (lazy init)

    @Inject(method = "end", at = @At("HEAD"), require = 0)
    private static void onEnd(CallbackInfo ci) {
        // Lazily initialize the dedicated shader program on render thread
        if (awProgram == 0) {
            awProgram = createAwProgram();
        }
        // Save current GL program
        savedProgram = GL20.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        // Bind AW's dedicated program (also upload matrices)
        GL20.glUseProgram(awProgram);
        uploadMatrices(awProgram);  // ProjMat, ModelViewMat from RenderSystem
    }

    @Inject(method = "end", at = @At("RETURN"), require = 0)
    private static void onEndReturn(CallbackInfo ci) {
        // Restore the original GL program
        if (savedProgram != 0 && savedProgram != awProgram) {
            GL20.glUseProgram(savedProgram);
        }
        savedProgram = 0;
    }
}
```

### Dedicated Shader Program Architecture

The shader program is compiled at render-time (lazy init) from hard-coded GLSL 150 core strings.

**Vertex shader** receives AW's standard entity vertex data and transforms it:
```glsl
#version 150 core

// Vertex attributes matching DefaultVertexFormat.NEW_ENTITY (locations 0-5)
layout(location = 0) in vec3  Position;
layout(location = 1) in vec4  Color;
layout(location = 2) in vec2  UV0;
layout(location = 3) in ivec2 UV1;
layout(location = 4) in ivec2 UV2;
layout(location = 5) in vec3  Normal;

// Vanilla uniforms (captured from RenderSystem before binding)
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec4 ColorModulator;

// AW uniforms (7 declarations — must exist for glGetUniformLocation to find)
uniform mat4 aw_ModelViewMatrix;
uniform mat4 aw_TextureMatrix;
uniform mat4 aw_OverlayTextureMatrix;
uniform mat4 aw_LightmapTextureMatrix;
uniform mat3 aw_NormalMatrix;
uniform vec4 aw_ColorModulator;
uniform int  aw_MatrixFlags;

// Outputs to fragment shader
out vec4 vertexColor;
out vec2 texCoord0;
out vec2 texCoord1;
out vec2 texCoord2;
out float vertexDistance;

void main() {
    // AW model transform: Position -> aw_ModelViewMatrix -> awPosition
    vec4 awPos = aw_ModelViewMatrix * vec4(Position, 1.0);
    // Standard view/projection: awPosition -> ModelViewMat -> ProjMat
    vec4 viewPos = ModelViewMat * awPos;
    gl_Position = ProjMat * viewPos;
    vertexDistance = length(viewPos.xyz);
    vertexColor = Color * ColorModulator;
    texCoord0 = UV0;
    texCoord1 = UV1;
    texCoord2 = UV2;
}
```

**Fragment shader** is minimal — texture lookup and color output:
```glsl
#version 150 core

in vec4 vertexColor;
in vec2 texCoord0;
in vec2 texCoord1;
in vec2 texCoord2;
in float vertexDistance;

uniform sampler2D Sampler0;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor;
    if (color.a < 0.1) discard;
    fragColor = color;
}
```

### Recommended Project Structure

```
aw-veil-compat/src/main/java/com/example/awveilcompat/
├── AwVeilCompat.java                    # Mod entry point (existing)
├── detection/ModDetector.java           # Runtime mod detection (existing)
├── mixin/
│   ├── AwVeilCompatMixinPlugin.java     # Conditional mixin gating (existing)
│   ├── aw/
│   │   ├── ShaderUniformsProbeMixin.java    # (existing)
│   │   ├── ShaderRenderProbeMixin.java      # (existing)
│   │   └── AwDedicatedShaderMixin.java      # NEW: save/restore at end()
│   └── veil/
│       ├── VeilShaderResourceMixin.java     # (existing, from previous plan)
│       └── ...
├── shader/
│   ├── AwShaderUniformInjector.java     # (existing, for ResourceProvider approach)
│   └── AwDedicatedProgram.java          # NEW: GL program creation, GLSL source, matrix upload
└── probe/                              # (existing)
```

### Pattern 1: Dedicated Shader Program with Save/Restore

**What:** Create a minimal GLSL shader program that declares all 7 AW uniforms, bind it at `ShaderUniforms.end()` HEAD, let AW apply uniforms and draw, restore at TAIL.

**When to use:** As the primary fix approach for Phase 2. Architecturally cleaner than ResourceProvider wrapping — it doesn't depend on Veil's shader lifecycle and works with ANY mod that changes the active shader program.

**Rationale:** Nine out of ten previous attempts failed because they treated this as a "when to inject" problem rather than a "which program owns the state" problem. Giving AW its own shader program that unequivocally has its uniforms eliminates the root cause at the architectural level.

**Example:**
```java
// Target: moe.plushie.armourers_workshop.core.client.shader.ShaderUniforms
// Method: end() — reads GL_CURRENT_PROGRAM and applies AW uniforms

@Pseudo
@Mixin(targets = "moe.plushie.armourers_workshop.core.client.shader.ShaderUniforms")
public class AwDedicatedShaderMixin {

    private static int savedProgram = 0;
    private static int awProgram = 0;
    private static boolean programReady = false;

    // --- HEAD: save original program, bind AW shader ---
    @Inject(method = "end", at = @At("HEAD"), require = 0)
    private static void onEnd(CallbackInfo ci) {
        if (!ModDetector.isAWLoaded()) return;

        // Lazy init: compile shader program on first render thread call
        if (!programReady && RenderSystem.isOnRenderThread()) {
            awProgram = AwDedicatedProgram.create();
            programReady = true;
        }
        if (!programReady) return;  // not ready, skip (shouldn't happen)

        // Save the CURRENTLY BOUND program (Veil's, vanilla's, or whatever)
        savedProgram = GL20.glGetInteger(GL20.GL_CURRENT_PROGRAM);

        // Skip if our program is already bound (re-entrancy guard)
        if (savedProgram == awProgram) {
            savedProgram = 0;
            return;
        }

        // Bind AW's dedicated program
        GL20.glUseProgram(awProgram);

        // Upload current rendering matrices from Minecraft's state
        AwDedicatedProgram.uploadMatrices(awProgram);
    }

    // --- TAIL: restore the previous GL program ---
    @Inject(method = "end", at = @At("RETURN"), require = 0)
    private static void onEndReturn(CallbackInfo ci) {
        if (savedProgram != 0 && savedProgram != awProgram) {
            GL20.glUseProgram(savedProgram);
        }
        savedProgram = 0;
    }
}
```

**Supporting class — `AwDedicatedProgram`:**
```java
/**
 * Creates and manages AW's dedicated GL shader program.
 * 
 * The program is a minimal entity solid shader that:
 * - Accepts standard entity vertex format (Position, Color, UV0, UV1, UV2, Normal)
 * - Declares all 7 AW uniforms (so glGetUniformLocation returns valid locations)
 * - Applies aw_ModelViewMatrix transform before standard view/projection
 * - Passes through color, UV coordinates, and texture sampling
 * 
 * Thread safety: All methods must be called on the render thread with
 * an active GL context.
 */
public class AwDedicatedProgram {

    private static final int NOT_CREATED = 0;

    // GLSL source strings (see full source below)

    public static int create() {
        int vertexShader = compileShader(GL20.GL_VERTEX_SHADER, VERTEX_SOURCE);
        int fragmentShader = compileShader(GL20.GL_FRAGMENT_SHADER, FRAGMENT_SOURCE);
        
        int program = GL20.glCreateProgram();
        GL20.glAttachShader(program, vertexShader);
        GL20.glAttachShader(program, fragmentShader);
        GL20.glLinkProgram(program);
        
        // Check link status
        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL20.GL_FALSE) {
            String log = GL20.glGetProgramInfoLog(program);
            throw new RuntimeException("AW dedicated program link failed: " + log);
        }
        
        // Clean up shader objects (they remain attached to program)
        GL20.glDeleteShader(vertexShader);
        GL20.glDeleteShader(fragmentShader);
        
        return program;
    }

    public static void uploadMatrices(int program) {
        // Capture matrices from Minecraft's current RenderSystem state
        Matrix4f projMat = RenderSystem.getProjectionMatrix();
        Matrix4f modelViewMat = RenderSystem.getModelViewMatrix();
        Vector4f colorMod = RenderSystem.getShaderColor();

        // Upload to uniforms
        int projLoc = GL20.glGetUniformLocation(program, "ProjMat");
        int mvLoc = GL20.glGetUniformLocation(program, "ModelViewMat");
        int cmLoc = GL20.glGetUniformLocation(program, "ColorModulator");

        if (projLoc >= 0) uploadMatrix4f(projLoc, projMat);
        if (mvLoc >= 0) uploadMatrix4f(mvLoc, modelViewMat);
        if (cmLoc >= 0) GL20.glUniform4f(cmLoc, colorMod.x(), colorMod.y(), colorMod.z(), colorMod.w());

        // Sampler0 defaults to texture unit 0, which is correct
    }

    // ... helper methods for shader compilation, matrix upload
}
```

### Anti-Patterns to Avoid

- **Calling glGetFloatv for matrix capture:** Pipeline stall. Always use `RenderSystem.getProjectionMatrix()` (cached) instead of `GL11.glGetFloatv(GL_PROJECTION_MATRIX, ...)`.
- **AwProgram creation at mod constructor:** GL context may not exist yet. Always lazy-init on render thread.
- **Program compiled per-frame:** Horrible performance. Create once, reuse indefinitely. The program is stateless.
- **Storing matrix values in the program as default uniforms:** Uniforms are per-program, not stored in the compiled binary. Always upload matrices at binding time.
- **Forgetting Sampler0 binding:** Sampler0 defaults to unit 0 in GL, but if we don't declare it as a sampler uniform at all, fragment shaders can't use textures. Always include `uniform sampler2D Sampler0;` in the fragment shader.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Shader compilation (create/link/validate) | Custom compile loop | `GL20.glCreateShader()` + `GL20.glCompileShader()` + `GL20.glAttachShader()` + `GL20.glLinkProgram()` | Standard LWJGL pattern — copy from any open-source mod or Minecraft's own `ShaderInstance` |
| Matrix capture | `glGetFloatv` pipeline stall | `RenderSystem.getProjectionMatrix()`, `RenderSystem.getModelViewMatrix()` | RenderSystem caches current matrices; these are the same values that vanilla `ShaderInstance.apply()` uploads |
| Per-frame uniform upload | Manual `glGetUniformLocation` loop | `AwDedicatedProgram.uploadMatrices()` helper method | Encapsulate uniform location lookup + upload in a single method; call once per bind |
| AW uniform name list | Copy from ShaderPreprocessor | Use `AwShaderUniformInjector.UNIFORM_DECLARATIONS` (already exists) | The existing injector already has the canonical list of 7 AW uniforms as string constants |
| Shader source as resource files | `.vsh`/`.fsh` files in assets | Hard-coded Java string constants | Avoids resource loading complexity; GLSL is small (fits in 30 lines each) |

---

## Common Pitfalls

### Pitfall 1: GLSL Attribute Location Mismatch
**What goes wrong:** AW's VBO specifies vertex data in a specific order (Position=0, Color=1, UV0=2, UV1=3, UV2=4, Normal=5 for `DefaultVertexFormat.NEW_ENTITY`). If our shader declares attributes in a different order, the GPU reads wrong data for each attribute — model renders as garbage geometry or crashes.
**Root cause:** GLSL attribute locations are assigned by the linker unless explicitly specified. Without `layout(location=N)` qualifiers, the linker may assign different locations than the VAO expects.
**Prevention:** Use explicit `layout(location = 0)` through `layout(location = 5)` qualifiers in the GLSL source for all 6 vertex attributes. OR call `GL20.glBindAttribLocation(program, index, name)` BEFORE `glLinkProgram()`.
**Detection:** RenderDoc shows wrong vertex data when inspecting the VAO attribute bindings vs. the shader's input layout.

### Pitfall 2: Program Not Ready on First Frame
**What goes wrong:** If AW renders geometry before our lazy program initialization fires, the first few frames may have no AW rendering.
**Root cause:** Lazy init waits for the first `ShaderUniforms.end()` call. If AW renders during world load, the first call may occur before `RenderSystem.isOnRenderThread()` or the GL context is available.
**Prevention:** Initialize the program during the first client tick using `RenderSystem.recordRenderCall()` or `LevelRenderer.RenderCall`, not at the latest possible moment. Schedule init from `ClientTickEvent.START` or from the mod constructor using `RenderSystem.safeCall()`.
**Fallback:** If program creation fails, log an error and skip the save/restore (AW continues with whatever program was bound — same as unfixed behavior).

### Pitfall 3: Re-entrant end() Calls
**What goes wrong:** If restoring the GL program triggers another draw call that calls `ShaderUniforms.end()` (e.g., if the GL restore causes a buffer flush), we have infinite recursion.
**Root cause:** `glUseProgram()` is typically safe (no side effects), but some GL implementations or mod hooks on `glUseProgram` could trigger unexpected rendering.
**Prevention:** Add a re-entrancy guard: `if (savedProgram == awProgram) return;` at HEAD (already protects) and ensure TAIL does not fire if HEAD was skipped.
**Detection:** Stack overflow crash in ShaderUniforms.end().

### Pitfall 4: RenderSystem State Desync from Raw glUseProgram
**What goes wrong:** Calling `GL20.glUseProgram()` directly bypasses Minecraft's `RenderSystem` cache. Minecraft may think the old program is still bound and skip a future `glUseProgram()` call.
**Root cause:** `RenderSystem` tracks the current program internally. Raw GL calls don't update this cache.
**Prevention:** We can call `RenderSystem.setShader()` or update the cache manually. However, AW's own code path already uses raw GL (via `AbstractShaderObjectState.push()/pop()`) so the cache is already at risk. Our save/restore is symmetrical and happens during AW's render cycle, so the state is consistent within that window. The TAIL restore puts the original program back, so after our intervention, the GL state matches what Minecraft/RenderSystem expects.
**Mitigation:** If desync issues appear (rendering corruption after AW VBO draw), add `RenderSystem.setShader()` call or set the private `activeProgram` field via accessor after our restore.
**Detection:** Rendering corruption on vanilla elements immediately after AW's VBO flush.

### Pitfall 5: Missing Matrix Uniforms
**What goes wrong:** Our dedicated program has `ProjMat` and `ModelViewMat` uniforms, but if we forget to upload them from `RenderSystem`, the program uses whatever garbage values were left in those uniform locations.
**Root cause:** OpenGL uniforms have undefined initial values unless explicitly set. A newly created program has zero-initialized uniforms, which would give `ProjMat = zero matrix` = no geometry visible.
**Prevention:** Always call `uploadMatrices()` immediately after binding our program. Make this part of the HEAD handler, not a separate step.

### Pitfall 6: Fragment Shader Discard Affecting Depth Buffer
**What goes wrong:** Our fragment shader discards fragments with `color.a < 0.1`. If a fragment is discarded, its depth is NOT written. This can cause transparent AW elements to not occlude subsequent geometry correctly.
**Root cause:** Alpha-tested transparency with discard prevents early-Z optimizations on some GPUs.
**Prevention:** The discard threshold is a standard pattern in Minecraft's entity shaders (matches vanilla behavior). No action needed — AW's rendering should match vanilla alpha behavior.

---

## Code Examples

### Example 1: Shader Uniforms Mixin — Complete HEAD/TAIL Implementation

```java
// Based on existing ShaderUniformsProbeMixin pattern
// Target: moe.plushie.armourers_workshop.core.client.shader.ShaderUniforms

@Pseudo
@Mixin(targets = "moe.plushie.armourers_workshop.core.client.shader.ShaderUniforms")
public class AwDedicatedShaderMixin {

    private static int savedProgram = 0;
    private static int awProgram = 0;

    @Inject(method = "end", at = @At("HEAD"), require = 0)
    private static void onEnd(CallbackInfo ci) {
        if (!ModDetector.isAWLoaded()) return;

        if (awProgram == 0) {
            if (!RenderSystem.isOnRenderThread()) return;
            try {
                awProgram = AwDedicatedProgram.create();
            } catch (Exception e) {
                ModCompatLog.LOGGER.error("Failed to create AW dedicated shader program", e);
                awProgram = -1; // mark as failed
                return;
            }
        }
        if (awProgram <= 0) return;

        savedProgram = GL20.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        if (savedProgram == awProgram) {
            savedProgram = 0; // re-entrancy guard
            return;
        }

        GL20.glUseProgram(awProgram);
        AwDedicatedProgram.uploadMatrices(awProgram);
    }

    @Inject(method = "end", at = @At("RETURN"), require = 0)
    private static void onEndReturn(CallbackInfo ci) {
        if (savedProgram != 0 && savedProgram != awProgram) {
            GL20.glUseProgram(savedProgram);
        }
        savedProgram = 0;
    }
}
```

### Example 2: AW Dedicated Program — GLSL Source + Compilation

```java
// Full vertex shader GLSL source
public static final String VERTEX_SOURCE = """
    #version 150 core

    layout(location = 0) in vec3  Position;
    layout(location = 1) in vec4  Color;
    layout(location = 2) in vec2  UV0;
    layout(location = 3) in ivec2 UV1;
    layout(location = 4) in ivec2 UV2;
    layout(location = 5) in vec3  Normal;

    uniform mat4 ModelViewMat;
    uniform mat4 ProjMat;
    uniform vec4 ColorModulator;

    uniform mat4 aw_ModelViewMatrix;
    uniform mat4 aw_TextureMatrix;
    uniform mat4 aw_OverlayTextureMatrix;
    uniform mat4 aw_LightmapTextureMatrix;
    uniform mat3 aw_NormalMatrix;
    uniform vec4 aw_ColorModulator;
    uniform int  aw_MatrixFlags;

    out vec4 vertexColor;
    out vec2 texCoord0;
    out vec2 texCoord1;
    out vec2 texCoord2;
    out float vertexDistance;

    void main() {
        vec4 awPos = aw_ModelViewMatrix * vec4(Position, 1.0);
        vec4 viewPos = ModelViewMat * awPos;
        gl_Position = ProjMat * viewPos;
        vertexDistance = length(viewPos.xyz);
        vertexColor = Color * ColorModulator;
        texCoord0 = UV0;
        texCoord1 = UV1;
        texCoord2 = UV2;
    }
    """;

// Full fragment shader GLSL source
public static final String FRAGMENT_SOURCE = """
    #version 150 core

    in vec4 vertexColor;
    in vec2 texCoord0;
    in vec2 texCoord1;
    in vec2 texCoord2;
    in float vertexDistance;

    uniform sampler2D Sampler0;

    out vec4 fragColor;

    void main() {
        vec4 color = texture(Sampler0, texCoord0) * vertexColor;
        if (color.a < 0.1) discard;
        fragColor = color;
    }
    """;

// Shader compilation helper
public static int compileShader(int type, String source) {
    int shader = GL20.glCreateShader(type);
    GL20.glShaderSource(shader, source);
    GL20.glCompileShader(shader);
    if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL20.GL_FALSE) {
        String log = GL20.glGetShaderInfoLog(shader);
        GL20.glDeleteShader(shader);
        throw new RuntimeException("Shader compile failed: " + log);
    }
    return shader;
}

// Matrix upload from RenderSystem
public static void uploadMatrices(int program) {
    Matrix4f projMatrix = RenderSystem.getProjectionMatrix();
    Matrix4f modelViewMatrix = RenderSystem.getModelViewMatrix();
    Vector4f shaderColor = RenderSystem.getShaderColor();

    // Upload using LWJGL float buffers
    try (MemoryStack stack = MemoryStack.stackPush()) {
        FloatBuffer buf = stack.mallocFloat(16);
        
        projMatrix.store(buf);
        buf.rewind();
        int loc = GL20.glGetUniformLocation(program, "ProjMat");
        if (loc >= 0) GL20.glUniformMatrix4fv(loc, false, buf);

        modelViewMatrix.store(buf);
        buf.rewind();
        loc = GL20.glGetUniformLocation(program, "ModelViewMat");
        if (loc >= 0) GL20.glUniformMatrix4fv(loc, false, buf);

        loc = GL20.glGetUniformLocation(program, "ColorModulator");
        if (loc >= 0) {
            GL20.glUniform4f(loc, shaderColor.x(), shaderColor.y(), shaderColor.z(), shaderColor.w());
        }
    }
}
```

### Example 3: Re-entrancy and Error Handling in HEAD/TAIL

```java
// CRITICAL SAFETY PATTERNS:

// 1. Re-entrancy guard: if our program is already bound, skip save
if (savedProgram == awProgram) {
    savedProgram = 0;  // prevent TAIL from restoring (nothing changed)
    return;
}

// 2. Program creation failure: mark as permanently failed, skip future attempts
if (awProgram == -1) return;  // -1 = permanent failure

// 3. Deferred init: if GL not ready, schedule creation
if (awProgram == 0) {
    if (!RenderSystem.isOnRenderThread()) return;  // skip this frame
    RenderSystem.recordRenderCall(() -> {
        awProgram = createAwProgram();
    });
}

// 4. TAIL must ALWAYS fire, even if HEAD skipped (Mixin @Inject at RETURN
//    fires unconditionally). Guard with savedProgram check.
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| AW "borrows" the currently bound GL program | Dedicated AW shader program with save/restore | This phase | AW always has a program with its uniforms declared. Works regardless of Veil, Iris, or any other shader-modifying mod. |
| ResourceProvider wrapping in Veil's ShaderManager (previous plan) | Dedicated shader program save/restore around ShaderUniforms.end() | This research | ResourceProvider wrapping depends on Veil's shader source pipeline. Dedicated program is Veil-agnostic, works with any mod. |
| Uniform creation + linking on each render call | Pre-linked program, upload matrices only | This phase | No per-call glGetUniformLocation. Matrices captured from RenderSystem cache (no GL roundtrip). |

**Deprecated/outdated:**
- **ResourceProvider wrapping approach (02-01-PLAN.md):** Still viable but tied to Veil's shader lifecycle. The dedicated program approach is more robust. Both can coexist if desired.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `ShaderUniforms.end()` fires per AW render pass and `@At("HEAD")` / `@At("RETURN")` bracket the uniform application correctly | Mixin Pattern | If `end()` is called multiple times per render pass or has nested calls, the save/restore may be unbalanced. Mitigation: re-entrancy guard comparing savedProgram to awProgram. |
| A2 | The captured `ProjMat` and `ModelViewMat` from `RenderSystem` are the correct matrices for AW's VBO rendering context | Matrix Capture | If AW renders at a point where RenderSystem matrices are stale or wrong, geometry will be positioned incorrectly. Risk: LOW — matrices are set per-frame by Minecraft's renderer and are valid at any point during a frame. |
| A3 | `Sampler0` bound to texture unit 0 is the correct texture for AW's VBO | Fragment Shader | If AW's VBO expects a different texture binding, the fragment shader will sample the wrong texture. Risk: MEDIUM — AW renders within the active RenderType's context, so the correct texture should be on unit 0. Verify with RenderDoc. |
| A4 | The `DefaultVertexFormat.NEW_ENTITY` attribute locations match AW's VBO format exactly | Vertex Format | If AW uses a different vertex format (e.g., POSITION_COLOR_TEX_LIGHTMAP, which has different UV ordering), `layout(location = N)` will bind wrong data. Risk: LOW — AW's `AbstractVertexFormat.wrap()` wraps Minecraft's VertexFormat elements in order, matching standard entity format. Verify via RenderDoc vertex inspector. |

---

## Open Questions

1. **Does `ShaderUniforms.end()` fire once per rendered object or once per pipeline flush?**
   - What we know: AW's `ConcurrentRenderingPipeline.render()` calls `shader.render(object, group)` for EACH object in a group. Each call applies uniforms. But `ShaderUniforms` may batch these.
   - What's unclear: Whether `end()` fires per-object or per-group. If per-group, our save/restore is called once. If per-object, it's called multiple times but each time is fast (program already bound).
   - Recommendation: Assume per-group call (one save/restore per render type). The re-entrancy guard handles the per-object case harmlessly.

2. **Can `ShaderUniforms.end()` be called from off-thread?**
   - What we know: AW's rendering happens on the render thread (inside Minecraft's render pipeline, via `RenderSystem.setDrawElementsCallback()`).
   - What's unclear: Whether AW pipelines VBO draws from other threads that might call `end()`.
   - Recommendation: Add `RenderSystem.isOnRenderThread()` guard. If called off-thread, skip the save/restore (let the existing behavior apply).

3. **Should we remove the `gl_FragColor` or use an explicitly named output?**
   - What we know: GLSL 150 core requires explicitly declared out variables.
   - What's unclear: Whether some drivers support the deprecated `gl_FragColor` in core profile.
   - Recommendation: Use `out vec4 fragColor;` explicitly. `gl_FragColor` was removed in core profile and may not work on all drivers (especially Mesa/Intel).

4. **Does Minecraft 1.21.1's RenderSystem.getModelViewMatrix() return the correct matrix?**
   - What we know: Minecraft 1.21+ uses a different matrix stack than earlier versions. The `RenderSystem.getModelViewMatrix()` exists and returns the current position matrix.
   - What's unclear: Whether this is the same as the `ModelViewMat` uniform that vanilla `ShaderInstance.apply()` uploads.
   - Recommendation: Verify by comparing values in RenderDoc — capture the uniform value from a vanilla entity shader and compare with `RenderSystem.getModelViewMatrix()` at the same point. If they differ, use `ShaderInstance` reflection to capture the actual model view matrix.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| AW JAR (at runtime) | ShaderUniforms target class | Yes | 1.21.1 (NeoForge) | Mixin fails gracefully with require=0 |
| LWJGL (via Minecraft) | GL20 shader compilation | Yes | 3.3.3 | — |
| Minecraft RenderSystem | Matrix capture | Yes | 1.21.1 | Manual GL matrix capture |
| ModDevGradle | Build | Yes | 2.0.141 | — |
| MixinExtras | Not required for this approach | — | — | Use vanilla @Inject |

---

## Validation Architecture

Skipped — `workflow.nyquist_validation` is explicitly set to `false` in `.planning/config.json`.

---

## Security Domain

Security enforcement is set to `false` in `.planning/config.json`. This phase involves no network access, no user input processing, and no persistent data storage. The shader program is compiled from hard-coded GLSL strings (no external file loading). Omitted per config.

---

## Sources

### Primary (HIGH confidence)

- **[AW source: `ShaderUniform.java`]** — Confirms `link()` method calls `GL20.glGetUniformLocation(program, name)` and `apply()` calls `GL20.glUniform*`. Location -1 means uniform silently skipped. [VERIFIED: source at `D:\Software\CCode\AW\aw-source\common\src\main\java\moe\plushie\armourers_workshop\core\client\shader\ShaderUniform.java`]
- **[AW source: `AbstractShaderObjectState.java`]** — Confirms `push()` reads `GL_CURRENT_PROGRAM`, `pop()` does NOT restore it (only VAO/VBO/IBO). [VERIFIED: source at `D:\Software\CCode\AW\aw-source\versions\library\common\src\main\java\moe\plushie\armourers_workshop\compat\client\renderer\shader\state\AbstractShaderObjectState.java`]
- **[AW source: `AbstractShaderUniformState.java`]** — Confirms `get(program)` creates 7 ShaderUniform objects, calls `link(program)`, removes if `!isLinked()`. [VERIFIED: source at `D:\Software\CCode\AW\aw-source\versions\library\common\src\main\java\moe\plushie\armourers_workshop\compat\client\renderer\shader\state\AbstractShaderUniformState.java`]
- **[AW source: `Shader.java`]** — Confirms `setupRenderState()` -> `context.saveObjects()` saves program; `render()` -> `context.applyUniforms()` applies uniforms; `clearRenderState()` -> `context.restoreObjects()` restores. [VERIFIED: source at `D:\Software\CCode\AW\aw-source\common\src\main\java\moe\plushie\armourers_workshop\core\client\shader\Shader.java`]
- **[AW source: `ShaderPreprocessor.java`]** — Confirms 7 uniform declarations, 6 attribute mappings (vanilla profile), aw_main_pre() injection. [VERIFIED: source at `D:\Software\CCode\AW\aw-source\common\src\main\java\moe\plushie\armourers_workshop\core\client\shader\ShaderPreprocessor.java`]
- **[Existing probe: `ShaderUniformsProbeMixin.java`]** — Confirms `ShaderUniforms` class has `end()` and `apply(int)` methods, `end()` reads `GL_CURRENT_PROGRAM`. [VERIFIED: source at `D:\Software\CCode\aw-veil-compat\src\main\java\com\example\awveilcompat\mixin\aw\ShaderUniformsProbeMixin.java`]
- **[Existing probe: `ShaderRenderProbeMixin.java`]** — Confirms `Shader.render()` fires per-object, reads GL state at that point. [VERIFIED: source at `D:\Software\CCode\aw-veil-compat\src\main\java\com\example\awveilcompat\mixin\aw\ShaderRenderProbeMixin.java`]
- **[AW source: `AbstractVertexFormat.java`]** — Confirms vertex format wraps Minecraft's VertexFormat elements 1:1 (Position, Color, UV0, UV1, UV2, Normal). [VERIFIED: source at `D:\Software\CCode\AW\aw-source\versions\library\common\src\main\java\moe\plushie\armourers_workshop\compat\client\renderer\vertex\AbstractVertexFormat.java`]

### Secondary (MEDIUM confidence)

- **[WebSearch: Minecraft Wiki — Core Shader List]** — Confirms entity shader uses `DefaultVertexFormat.NEW_ENTITY` with attributes Position(0), Color(1), UV0(2), UV1(3), UV2(4), Normal(5). Confirms `ModelViewMat`, `ProjMat`, `ColorModulator` uniforms. [CITED: Minecraft-Shaders-Wiki]
- **[PITFALLS.md]** — Pitfall 1 (Wrong Current Program) is the exact problem this pattern solves. Pitfall 6 (VBO Performance Degradation) — our approach adds only 2 glUseProgram calls, acceptable. [CITED: `.planning/research/PITFALLS.md`]
- **[STACK.md]** — LWJGL 3.3.3, OpenGL 3.2 Core Profile, Mixin confirmation. [CITED: `.planning/research/STACK.md`]

---

## Metadata

**Confidence breakdown:**
- Root cause (program mismatch): HIGH — confirmed at GL state, source code, and shader compilation levels
- Injection point (ShaderUniforms.end()): HIGH — confirmed by existing probe that compiles and runs
- Dedicated program approach: HIGH — follows proven save/restore pattern, no new dependencies
- GLSL shader source: MEDIUM — exact vertex format ordering needs RenderDoc verification; attribute locations with `layout(location = N)` may need adjustment
- Matrix capture (RenderSystem): MEDIUM — `getModelViewMatrix()` may differ from `ModelViewMat` uniform in some scenarios

**Research date:** 2026-04-30
**Valid until:** 2026-06-15 (30 days — AW and Veil are actively developed; ShaderUniforms class could change)
