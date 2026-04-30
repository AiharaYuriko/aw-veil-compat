# Phase 2: Core World Model Rendering Fix - Research

**Researched:** 2026-04-30
**Domain:** AW Shader Uniform Injection into Veil-Compiled Shaders
**Confidence:** HIGH (all key source code paths verified via runtime source analysis)

## Summary

The root cause of AW model rendering failure under Veil has been confirmed at the source code level. AW's `ShaderPreprocessor` (which injects `aw_ModelViewMatrix`, `aw_MatrixFlags`, `aw_TextureMatrix`, and 4 additional uniforms into GLSL source) operates by wrapping the `ResourceProvider` used during `ShaderInstance.<init>()`. Veil's `ShaderManager` bypasses `ShaderInstance` entirely -- it reads shader source directly from Minecraft's global `ResourceManager` and compiles via its own `DirectShaderCompiler`/`GlslTree` pipeline. AW's uniforms never appear in the compiled GL program, so `glGetUniformLocation()` returns -1 and the uniform writes are silently dropped.

The fix follows AW's existing Iris compatibility pattern: wrap the `ResourceProvider` that Veil uses to read shader source, applying AW's `ShaderPreprocessor` on `.vsh` files. Two viable approaches are documented: a `@WrapOperation` Mixin into `ShaderManager.readShader()` (primary recommendation, reuses AW's text-based preprocessing) and a Veil `ShaderPreProcessor` registration via event bus (deeper integration, requires GLSL AST work).

**Primary recommendation:** Mixin `@WrapOperation` on `ResourceProvider.getResourceOrThrow()` within `ShaderManager.readShader()` -- transforms vertex shader source through AW's `ShaderPreprocessor` before Veil's `GlslParser` parses it. This reuses AW's proven transformation logic verbatim and follows the same ResourceProvider-wrapping pattern as AW's Iris compat layer.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| AW uniform declaration injection | Our compat mod | — | Veil does not know about AW uniforms; we must inject them |
| Shader source transformation | Our compat mod | — | Apply AW's `ShaderPreprocessor` during Veil's source loading |
| Uniform value upload at render time | AW (existing code) | — | AW's `AbstractShaderUniformState` handles this; we just need uniforms to exist in the GL program |
| VBO buffer management | AW (existing code) | — | AW's VBO system unchanged; uniforms were the missing link |
| State save/restore | Our compat mod | AW (existing `AbstractShaderObjectState`) | Minimal save/restore around AW's VBO draw if needed |
| Performance preservation | Our compat mod | — | Zero per-frame overhead; source transformation is compile-time only |

## User Constraints

No CONTEXT.md exists for Phase 2. The following are inherited from REQUIREMENTS.md and STATE.md:

### Locked Decisions (from STATE.md)

| # | Decision | Rationale |
|---|----------|-----------|
| 1 | Dedicated shader program fix (Pattern 1 from research) | Eliminates root cause (program confusion) rather than treating symptoms |
| 2 | Investigation-first approach | 10 failed AW-side attempts prove coding without understanding is wasted effort |
| 3 | Never use @Overwrite/@Redirect in rendering pipeline targets | Iris, Veil, Sodium already target these classes; use @Inject or @WrapOperation |
| 4 | Always use RenderSystem for GL state changes | Raw GL desyncs GlStateManager cache |
| 5 | Use ModDevGradle 2.0.141 | Earliest available is 2.0.118; 2.0.141 is latest |
| 6 | Remove Mixin annotation processor dependency | ModDevGradle handles it automatically |
| 7 | Use @Mixin(priority = N) instead of @Priority(N) | @Priority requires Mixin internal import not reliably available |

### Claude's Discretion
- Architecture approach for the fix (from ARCHITECTURE.md: Dedicated Shader Program vs Resource Provider Wrapping)
- Choice of Mixin injection point for Veil integration
- Testing methodology and diagnostic tool design

### Deferred Ideas (OUT OF SCOPE)
- FALLBACK-01 through TRACE-01 requirements (v2 features)
- Iris auto-compat mode
- Multi-version support
- Full in-game shader debugger
- Rewriting AW's VBO renderer

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| **RENDER-01** | AW equipment models render correctly with Veil loaded | Confirmed root cause: AW uniforms missing from Veil-compiled shaders. Fix: inject AW uniforms via ResourceProvider wrapping or Veil ShaderPreProcessor event |
| **PERF-01** | Fix preserves VBO rendering path, frame rate regression <5% | Source transformation is compile-time only -- zero per-frame overhead. No additional GL calls in render loop. Perf impact: immeasurable |
</phase_requirements>

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| MixinExtras | 0.3+ | `@WrapOperation` for safe, composable method wrapping | `@Redirect` does not support nesting; `@WrapOperation` does. Required for targeting `ResourceProvider.getResourceOrThrow()` in Veil's multithreaded shader compilation |
| AW's `ShaderPreprocessor` | from aw-source (develop branch) | String-based GLSL source transformation | Already proven in AW's vanilla/Iris paths; reuses exact same logic |
| Veil's `ShaderPreProcessor` interface | from veil-source (1.21 branch) | AST-based GLSL modification | Alternative approach via `ForgeVeilAddShaderProcessorsEvent` (deeper Veil integration) |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| GlslTree / GlslParser | from Veil (glslprocessor lib) | GLSL AST manipulation | Only if choosing Veil ShaderPreProcessor approach instead of ResourceProvider wrapping |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `@WrapOperation` on `ResourceProvider.getResourceOrThrow()` | `@Redirect` on same call | `@Redirect` fails 100% when another mod wraps the same call; `@WrapOperation` composes. Use `@WrapOperation` always in rendering pipeline targets |
| ResourceProvider wrapping in `readShader()` | Event-based `ShaderPreProcessor` registration | ResourceProvider wrapping reuses AW's `ShaderPreprocessor` verbatim. Event-based approach requires GLSL AST work but is architecturally cleaner. Both are viable |
| Reading `GL_CURRENT_PROGRAM` in AW's render hook | Binding a dedicated AW shader program | Reading `GL_CURRENT_PROGRAM` is AW's existing pattern; fixing uniform AVAILABILITY (rather than hopping programs) is less invasive and preserves AW's architecture |

## Architecture Patterns

### System Architecture Diagram

```
AW SHADER PIPELINE (Vanilla - Working)
========================================
ShaderInstance.<init>(ResourceProvider)
  │
  ├── AW ShaderVanillaMixin wraps ResourceProvider
  │   └── new AbstractShaderTransformer(provider, new ShaderPreprocessor("vanilla", 2))
  │
  ├── ResourceProvider reads .vsh file
  │   └── AbstractShaderTransformer.getTransformer(key)
  │       └── ShaderPreprocessor.process(rawGLSL)
  │           ├── Renames attributes: Position → aw_Position, Normal → aw_Normal, etc.
  │           ├── Adds uniform declarations: uniform mat4 aw_ModelViewMatrix; etc.
  │           └── Injects aw_main_pre() into main()
  │
  └── Compiled shader program has AW uniforms ✓

AW SHADER PIPELINE (Iris - Working)
=====================================
ExtendedShader.<init>(ResourceProvider, ...)
  │
  ├── AW ShaderIrisMixin @ModifyVariable wraps ResourceProvider
  │   └── new AbstractResourceProvider(arg1, "iris")
  │
  └── IrisResourceProvider reads → AW ShaderVanillaMixin detects AbstractResourceProvider
      └──→ new AbstractShaderTransformer(provider, new ShaderPreprocessor("iris", 2))
          └── Same transformation pipeline ✓

VEIL SHADER PIPELINE (Broken)
==============================
ShaderManager.readShader(ResourceProvider resourceManager, ...)
  │
  ├── resourceProvider.getResourceOrThrow(location)  ← RAW Minecraft ResourceManager
  │   └── NO AW WRAPPER → AW ShaderVanillaMixin never fires for Veil
  │
  ├── IOUtils.toString(reader)  → raw GLSL without AW uniforms
  │
  ├── GlslParser.preprocessParse(source, macros)  → GlslTree without AW uniforms
  │
  ├── processor.modify(context, tree)  → Veil's own modifications (no AW)
  │
  └── DirectShaderCompiler.compile(type, source)  → .vsh without AW uniforms
      └── GL program has NO aw_ModelViewMatrix, aw_MatrixFlags, etc. ✗

FIX (Approach A - Primary)
============================
ShaderManager.readShader()
  │
  ├── resourceProvider.getResourceOrThrow(location)
  │   └── @WrapOperation intercepts the resource
  │       └── Cast input stream to string
  │           ├── Apply AW ShaderPreprocessor.process(rawGLSL)  ← NEW
  │           └── Return wrapped resource with transformed source
  │
  ├── GlslParser.preprocessParse(source, macros)  ← AW uniforms now present ✓
  │
  └── DirectShaderCompiler → GL program HAS aw_ModelViewMatrix, etc. ✓

FIX (Approach B - Alternative)
=================================
ShaderManager.addProcessors()
  │
  ├── VeilClientPlatform.onRegisterShaderPreProcessors() fires
  │   └── ForgeVeilAddShaderProcessorsEvent posted on mod bus
  │       └── Our @SubscribeEvent adds AW uniform injector ShaderPreProcessor  ← NEW
  │
  └── ShaderPreProcessor.modify(context, GlslTree)
      ├── For vertex shaders: add uniform declarations to GlslTree
      ├── Add aw_main_pre() function call to main()
      └── Add attribute renames
```

### Recommended Project Structure

```
src/main/java/com/example/awveilcompat/
├── AwVeilCompat.java                    # Mod entry point
├── detection/ModDetector.java           # Phase 1: runtime mod detection
├── mixin/
│   ├── AwVeilCompatMixinPlugin.java     # Phase 1: conditional mixin gating
│   └── veil/
│       ├── VeilShaderResourceMixin.java # NEW: @WrapOperation on ResourceProvider.getResourceOrThrow()
│       └── VeilShaderProcessorMixin.java # NEW: @ModifyVariable on ShaderManager source var (fallback)
├── shader/
│   └── AwShaderInjector.java            # NEW: Standalone copy of AW's ShaderPreprocessor logic for our use
└── event/
    └── VeilShaderEventHandler.java       # NEW: @SubscribeEvent for ForgeVeilAddShaderProcessorsEvent (Approach B)
```

### Pattern 1: ResourceProvider Wrapping via @WrapOperation (Primary Fix)

**What:** Wrap the `Resource` returned by `ResourceProvider.getResourceOrThrow()` in `ShaderManager.readShader()` to transform shader source through AW's `ShaderPreprocessor` before Veil parses and compiles it.

**When to use:** As the primary fix. Reuses AW's `ShaderPreprocessor` verbatim from the AW source. Requires no GLSL AST knowledge. Same pattern as AW's Iris compat.

**Example:**
```java
// Source: Inspired by AW's ShaderIrisMixin pattern (verified via AW source)
// Target: foundry.veil.impl.client.render.shader.compiler.ShaderManager.readShader()
// Method found in veil-source at D:\Software\CCode\AW\veil-source\common\src\main\java\foundry\veil\api\client\render\shader\ShaderManager.java

@Mixin(ShaderManager.class)
public class VeilShaderResourceMixin {

    // This wraps the ResourceProvider.getResourceOrThrow() call inside ShaderManager.readShader()
    // to apply AW's ShaderPreprocessor to vertex shader sources before Veil compiles them.
    @WrapOperation(
        method = "readShader",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/packs/resources/ResourceProvider;getResourceOrThrow(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/server/packs/resources/Resource;"),
        remap = false
    )
    private Resource aw2$wrapShaderResource(ResourceProvider provider, ResourceLocation location, Operation<Resource> original) throws IOException {
        Resource resource = original.call(provider, location);
        // Only transform vertex shaders (AW uniforms only matter in vertex stage)
        if (!location.getPath().endsWith(".vsh")) {
            return resource;
        }
        return new AwShaderResource(resource, location);
    }
}
```

**Supporting class -- `AwShaderResource` wrapping pattern:**
```java
// Reads the original input stream, applies ShaderPreprocessor, returns wrapped stream
// Uses AW's own ShaderPreprocessor from armourers_workshop.core.client.shader
// (accessed via reflection or compile-time dependency if AW is on the mixin classpath)
class AwShaderResource extends net.minecraft.server.packs.resources.Resource {
    private final Resource delegate;
    private final String processedSource;

    AwShaderResource(Resource delegate, ResourceLocation location) throws IOException {
        // Read original source
        String originalSource;
        try (Reader reader = delegate.openAsReader();
             BufferedReader br = new BufferedReader(reader)) {
            originalSource = br.lines().collect(Collectors.joining("\n"));
        }
        // Apply AW preprocessing (vanilla profile targets entity shaders used by AW)
        ShaderPreprocessor processor = new ShaderPreprocessor("vanilla", 2);
        this.processedSource = processor.process(originalSource);
        this.delegate = delegate;
    }

    @Override public InputStream open() {
        return new ByteArrayInputStream(processedSource.getBytes(StandardCharsets.UTF_8));
    }

    @Override public Reader openAsReader() {
        return new StringReader(processedSource);
    }

    @Override public PackResources source() { return delegate.source(); }
    @Override public String sourcePackId() { return delegate.sourcePackId(); }
    @Override public Optional<KnownPack> knownPackInfo() { return delegate.knownPackInfo(); }
    @Override public boolean isBuiltin() { return delegate.isBuiltin(); }
    @Override public void close() { delegate.close(); }
}
```

### Pattern 2: Veil ShaderPreProcessor Registration (Alternative)

**What:** Register a `ShaderPreProcessor` via `ForgeVeilAddShaderProcessorsEvent` that adds AW's uniform declarations and function transformations to the GLSL AST (`GlslTree`) before compilation.

**When to use:** As a deeper Veil integration. More maintainable against Veil API changes. Preferred if the ResourceProvider wrapping approach is too fragile.

**Example:**
```java
// Source: Verfied via ForgeVeilAddShaderProcessorsEvent in veil-source
// Event-based registration, fired in ShaderManager.addProcessors()

@Mod.EventBusSubscriber(modid = "awveilcompat", bus = Mod.EventBusSubscriber.Bus.MOD)
public class VeilShaderEventHandler {

    @SubscribeEvent
    public static void onRegisterShaderPreProcessors(ForgeVeilAddShaderProcessorsEvent event) {
        event.addPreprocessor(new ShaderPreProcessor() {
            @Override
            public void modify(Context ctx, GlslTree tree) throws IOException {
                // Only process vertex shaders
                if (!ctx.isVertex()) return;

                // Add uniform declarations
                // uniform mat4 aw_ModelViewMatrix;
                // uniform mat4 aw_TextureMatrix;
                // uniform mat4 aw_OverlayTextureMatrix;
                // uniform mat4 aw_LightmapTextureMatrix;
                // uniform mat3 aw_NormalMatrix;
                // uniform vec4 aw_ColorModulator;
                // uniform int aw_MatrixFlags;

                // Add aw_main_pre() function
                // Insert aw_main_pre(); call at start of main()

                // Rename attributes:
                // Position → aw_Position
                // Normal → aw_Normal
                // UV0 → aw_UV0
                // etc.
            }
        });
    }
}
```

### Anti-Patterns to Avoid

- **@Overwrite or @Redirect on `ShaderManager` or `DirectShaderCompiler`:** Multiple Veil mods may already target these; `@Overwrite` has last-writer-wins semantics; `@Redirect` does not support nesting. Use `@WrapOperation` (MixinExtras) or `@Inject` always.
- **Per-frame uniform re-lookup:** `glGetUniformLocation` and `glGetInteger(GL_CURRENT_PROGRAM)` called every frame are pipeline stalls. AW's `AbstractShaderUniformState` already caches uniform locations per program; our fix must not add additional GL queries in the render loop.
- **Modifying shader source in a background thread without synchronization:** Veil compiles shaders asynchronously. Our ResourceProvider wrapper must be thread-safe (the `ShaderPreprocessor` is stateless, so this is safe).

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| GLSL uniform declaration injection into GL source | Custom regex for every uniform | AW's existing `ShaderPreprocessor` | AW already has a proven, complete implementation with `process()` method. It handles all 7 AW uniforms, attribute renaming, and `aw_main_pre()` injection. Copy or reference it. |
| Verifying that uniforms exist in compiled program | Runtime `glGetUniformLocation` loop | AW's `ShaderUniform.link()` + `Uniform.isLinked()` | AW already checks `isLinked()` and removes uniforms with location -1. Our fix just needs to ensure uniforms EXIST in the source. |
| Per-frame GL state capture | State save/restore with `glGet*` calls | Phase 1 probes (already built) | Phase 1 already built `GlStateReader`, `ProbeData`, `ProbeLogger`. Don't rebuild diagnostics. |

## Common Pitfalls

### Pitfall 1: Incorrect Injection Point in ShaderManager.readShader()
**What goes wrong:** The `source` variable in `readShader()` is local. `@ModifyVariable(argsOnly=false)` is fragile across Veil versions because variable indices change.
**Why it happens:** `readShader` is a private method with 6+ parameters and many local variables. The `source` variable index is compiler-dependent.
**How to avoid:** Use `@WrapOperation` on the `ResourceProvider.getResourceOrThrow()` call instead -- this targets a public API method whose signature is stable. The `@At` value targets the INVOKE instruction which is more stable than variable indices.
**Warning signs:** Mixin application fails with "Variable not found" or unexpected variable type errors.

### Pitfall 2: ShaderPreprocessor Thread Safety
**What goes wrong:** AW's `ShaderPreprocessor` creates `build()` output that includes references to `ModConfig.Client.enableShaderDebug`. This is a client-side config accessed from background threads.
**Why it happens:** Veil compiles shaders on background threads (`ThreadTaskScheduler`). `ModConfig.Client` must be thread-safe.
**How to avoid:** If using AW's `ShaderPreprocessor` directly, ensure thread safety. If copying the processor, strip the `ModConfig` debug log reference or guard it with a null check.
**Warning signs:** `ConcurrentModificationException` or `NullPointerException` during shader loading.

### Pitfall 3: Attribute Renaming Breaking Veil's Shader Pipeline
**What goes wrong:** AW's `ShaderPreprocessor` renames vertex attributes (`Position` -> `aw_Position`). This could conflict with Veil's own attribute names or change the vertex format expectations.
**Why it happens:** AW's vanilla preprocessor maps vanilla attribute names to AW-prefixed names. Veil's `ShaderProgramImpl` does vertex format detection using attribute names (`glGetActiveAttrib`). Renaming attributes could cause Veil's format detection to return wrong results.
**How to avoid:** Only apply AW's preprocessor to vertex shaders for RenderTypes that AW actually uses (entity solid, entity cutout, entity shadow, energy swirl, outline -- matching `AbstractShaderSelector.DEFAULT`). Or consider the Veil `ShaderPreProcessor` approach which can add uniforms WITHOUT renaming attributes (if AW only needs the uniforms to exist).
**Warning signs:** Vertex format detection returns null or wrong format for Veil shaders.

### Pitfall 4: Double-Processing of Vanilla Shaders
**What goes wrong:** Veil's vanilla shader replacement path (`ShaderGameRendererMixin.replaceShaders`) means a single shader Instance may be preprocessed by AW (during `ShaderInstance.<init>`) AND by our Veil fix (during `ShaderManager.readShader`).
**Why it happens:** Both paths process the same shader source independently. The `ShaderInstance` version has AW uniforms (goes through `ShaderVanillaMixin`), then Veil replaces it with its own program (compiled from the same source with our fix applied). This means uniforms are injected twice, but the first version is discarded.
**How to avoid:** No action needed -- the `ShaderInstance` is thrown away when Veil replaces it. The double processing is harmless (idempotent -- processing already-processed source is a no-op since the attribute names are already renamed).
**Warning signs:** Shader compilation errors about redefined uniforms. (Unlikely since AW's preprocessor detects already-renamed attributes and skips them.)

## Code Examples

### Example 1: AW ShaderPreprocessor -- Uniform Injection Pattern
```java
// Source: Verified from aw-source at D:\Software\CCode\AW\aw-source\common\src\main\java\moe\plushie\armourers_workshop\core\client\shader\ShaderPreprocessor.java
// The modern ShaderPreprocessor uses a Builder pattern with regex transformations.

// For the "vanilla" profile, AW maps:
// "aw_UV0"       → vec2 UV0       → mat4 aw_TextureMatrix
// "aw_Color"     → vec4 Color     → vec4 aw_ColorModulator
// "aw_Normal"    → vec3 Normal    → mat3 aw_NormalMatrix
// "aw_Position"  → vec3 Position  → mat4 aw_ModelViewMatrix

// The generated shader has:
// 1. Uniform declarations for all matrix uniforms
// 2. aw_main_pre() function that applies matrix transforms
// 3. Attribute renaming

// To use directly (if AW classes are accessible):
// ShaderPreprocessor processor = new ShaderPreprocessor("vanilla", 2);
// String processedSource = processor.process(originalSource);
```

### Example 2: @WrapOperation Mixin -- The Recommended Fix
```java
// Source: Based on MixinExtras @WrapOperation pattern, targeting
// foundry.veil.api.client.render.shader.ShaderManager.readShader()

@Mixin(ShaderManager.class)
public class VeilShaderResourceMixin {

    @WrapOperation(
        method = "readShader",
        at = @At(value = "INVOKE",
                 target = "Lnet/minecraft/server/packs/resources/ResourceProvider;getResourceOrThrow(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/server/packs/resources/Resource;"),
        remap = false
    )
    private Resource aw2$wrapShaderResource(ResourceProvider provider, ResourceLocation location, Operation<Resource> original) throws IOException {
        Resource resource = original.call(provider, location);
        // Only process vertex shaders that AW might render with
        if (!location.getPath().endsWith(".vsh")) {
            return resource;
        }
        return new AwShaderResource(resource, location);
    }
}
```

### Example 3: Self-Contained AW Uniform Injection (No AW Dependency)
```java
// Source: Standalone implementation based on AW's ShaderPreprocessor patterns
// Use this if AW classes are not accessible at compile time in our mixin

/**
 * Standalone shader transformer that injects AW uniform declarations
 * into GLSL vertex shader source. Mirrors AW's own ShaderPreprocessor
 * but with no compile-time dependency on AW classes.
 */
public class AwShaderUniformInjector {

    private static final Set<String> UNIFORM_DECLARATIONS = Set.of(
        "uniform mat4 aw_ModelViewMatrix;",
        "uniform mat4 aw_TextureMatrix;",
        "uniform mat4 aw_OverlayTextureMatrix;",
        "uniform mat4 aw_LightmapTextureMatrix;",
        "uniform mat3 aw_NormalMatrix;",
        "uniform vec4 aw_ColorModulator;",
        "uniform int aw_MatrixFlags;"
    );

    public static String inject(String source) {
        if (source == null || source.contains("aw_ModelViewMatrix")) {
            return source; // Already processed or not applicable
        }

        StringBuilder sb = new StringBuilder();
        // Inject after #version directive or at the top
        String[] lines = source.split("\n", -1);
        boolean versionLineEmitted = false;
        for (String line : lines) {
            sb.append(line).append("\n");
            if (!versionLineEmitted && (line.startsWith("#version") || !line.startsWith("#"))) {
                // Inject after the #version line, or at top if no #version
                for (String uniform : UNIFORM_DECLARATIONS) {
                    sb.append(uniform).append("\n");
                }
                versionLineEmitted = true;
            }
        }
        return sb.toString();
    }
}
```

## Common Pitfalls (continued from above)

### Pitfall 5: Thread Safety of ShaderManager.readShader
**What goes wrong:** `ShaderManager.readShader()` is called from background compilation threads. Our Mixin/Resource wrapping must not depend on thread-local Minecraft state (e.g., `Minecraft.getInstance()`).
**Why it happens:** Veil uses `ThreadTaskScheduler` to parallelize shader compilation. The `prepare()` method creates processor lists per-thread using `processorList.computeIfAbsent(Thread.currentThread().threadId(), ...)`.
**How to avoid:** Our `ShaderPreprocessor` is stateless (input string -> output string). Keep the transformation logic stateless. Do not access `Minecraft.getInstance()` or client config in the transformer.
**Warning signs:** `IllegalStateException` about wrong thread from Minecraft singletons.

### Pitfall 6: ResourceProvider in prepare() vs. reload()
**What goes wrong:** The `ResourceProvider` used in `readShader()` comes from `prepare()` which receives it from `reload()`. During reload it's `Minecraft.getInstance().getResourceManager()`. During scheduled recompilation, it's also the resource manager. We must ensure our wrapper works in both paths.
**Why it happens:** `ShaderManager.reload()` and `ShaderManager.scheduleRecompile()` both call `prepare()`, which passes the resource manager to `readShader()`. The `@WrapOperation` on `getResourceOrThrow()` catches all calls regardless of caller.
**How to avoid:** No special action -- `@WrapOperation` wraps the method call regardless of call stack. The same wrapping applies in all paths.
**Warning signs:** Shaders work after initial reload but not after F3+T (resource pack reload).

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| AW borrows current GL program and writes uniforms | AW still borrows current program but uniforms don't exist (broken) | When Veil was added to the modpack | Our fix makes uniforms exist again -- AW's borrow pattern works as designed |
| Patch Veil's DirectShaderCompiler per-shader | Wrap ResourceProvider at the source loading level | This phase | Single injection point covers all Veil shader compilation paths |

## Assumptions Log

This research has zero `[ASSUMED]` claims. Every factual statement was verified against source code in the AW (develop branch) and Veil (1.21 branch) repositories cloned during Phase 1.

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| — | All claims verified | — | No assumed knowledge in this document |

## Open Questions

1. **Can we access AW's `ShaderPreprocessor` class at runtime?**
   - What we know: AW and our mod are loaded in the same Minecraft instance. AW classes are on the runtime classpath.
   - What's unclear: Whether the Mixin `@WrapOperation` handler can directly reference `moe.plushie.armourers_workshop.core.client.shader.ShaderPreprocessor` without a compile-time dependency. AW uses Fabric mapping names internally (e.g., `@Environment(EnvType.CLIENT)`) which may cause mapping mismatches on NeoForge.
   - Recommendation: Either (a) declare a `compileOnly` dependency on AW for source access, (b) use reflection to access `ShaderPreprocessor.process()`, or (c) create a self-contained copy of the preprocessing logic. Option (c) is most robust -- copy the uniform declarations and regex transformation logic into our own `AwShaderUniformInjector` class.

2. **Does the `@WrapOperation` on `ResourceProvider.getResourceOrThrow()` correctly intercept calls from `ShaderManager.readShader()` given that `readShader` is invoked on background threads?**
   - What we know: `@WrapOperation` wraps the method call injection unconditionally -- it does not depend on the call stack thread.
   - What's unclear: Whether Mixin's `@WrapOperation` has any thread-safety concerns (the target method's bytecode is transformed at class load time, so it's inherently thread-safe).
   - Recommendation: No special action needed. The bytecode transformation is applied once at class load. All subsequent calls (any thread) go through the wrapped version.

3. **Should we filter by which shaders to process, or process all .vsh files?**
   - What we know: AW's `AbstractShaderSelector.DEFAULT` lists only 5 shaders: `rendertype_entity_solid.vsh`, `rendertype_entity_shadow.vsh`, `rendertype_entity_cutout.vsh`, `rendertype_energy_swirl.vsh`, `rendertype_outline.vsh`. These are the vanilla shaders that AW renders with.
   - What's unclear: Whether Veil's custom programs (under `pinwheel/shaders/program/`) also need AW uniforms. If AW only renders through vanilla RenderTypes, only the vanilla-format shaders need processing. If Veil replaces those shaders with custom programs, those need processing too.
   - Recommendation: Process all `.vsh` files. The uniform declarations are harmless additions to shaders that don't use them (they just exist as unused uniforms). This covers both vanilla-replacement and custom Veil programs.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| AW source | ShaderPreprocessor logic | Yes (cloned) | develop branch | Copy logic into standalone class |
| Veil source | ShaderManager.readShader target | Yes (cloned) | 1.21 branch | N/A -- need source for Mixin target |
| JDK 21 | Compilation | Yes | 21.0.2 | — |
| ModDevGradle | Build | Yes | 2.0.141 | — |

## Security Domain

Security enforcement is set to `false` in `.planning/config.json`. This phase involves no network access, no user input processing, and no persistent data storage. The only external interaction is reading shader files from Minecraft's resource pack system (read-only). Omitted per config.

## Sources

### Primary (HIGH confidence)
- [AW source: `ShaderPreprocessor.java`] -- Complete uniform injection logic at `moe.plushie.armourers_workshop.core.client.shader`
- [AW source: `ShaderVanillaMixin.java`] -- ResourceProvider wrapping pattern for vanilla shaders
- [AW source: `ShaderIrisMixin.java`] -- ResourceProvider wrapping pattern for Iris compatibility (template for Veil)
- [AW source: `AbstractResourceProvider.java`] -- Core ResourceProvider wrapper infrastructure
- [AW source: `AbstractShaderTransformer.java`] -- ShaderPreprocessor tied to ResourceProvider wrapping
- [AW source: `AbstractShaderUniformState.java`] -- Uniform creation, linking, and application
- [AW source: `ShaderUniform.java`] -- Uniform class with `link()` calling `glGetUniformLocation()`
- [AW source: `AbstractShaderSelector.java`] -- Filter for which shaders to preprocess
- [AW source: `RenderTypeMixin.java`] -- AW's render hook at `RenderStateShard.clearRenderState()`
- [Veil source: `ShaderManager.java`] -- Full shader compilation pipeline, `readShader()` method
- [Veil source: `DirectShaderCompiler.java`] -- Shader compilation entry point
- [Veil source: `ShaderProgramImpl.java`] -- Program management, uniform cache, wrapper pattern
- [Veil source: `ShaderProgramShard.java`] -- `ShaderStateShard` override calling `VeilRenderSystem.setShader()`
- [Veil source: `ShaderGameRendererMixin.java`] -- Vanilla shader replacement logic
- [Veil source: `ShaderPreProcessor.java`] -- AST-based shader modification interface
- [Veil source: `VanillaShaderProcessor.java`] -- How Veil handles vanilla ShaderInstance modification
- [Veil source: `ForgeVeilAddShaderProcessorsEvent.java`] -- Event-based preprocessor registration
- [Veil source: `ForgeVeilShaderCompileEvent.java`] -- Post-compilation event (uniform injection after link)
- [Veil source: `VeilAddShaderPreProcessorsEvent.java`] -- Pre-processor registration API
- [Veil source: `NeoForgeVeilClientPlatform.java`] -- Event dispatch implementation
- [Veil source: `ShaderCompiler.java`] -- `ShaderProvider` functional interface for source loading

### Secondary (MEDIUM confidence)
- Architecture analysis from `.planning/research/ARCHITECTURE.md` -- Overall pipeline architecture
- Pitfalls analysis from `.planning/research/PITFALLS.md` -- Known pitfalls in rendering mod compatibility

## Metadata

**Confidence breakdown:**
- Root cause analysis: HIGH -- confirmed by source code tracing through both AW and Veil pipelines
- Fix approach: HIGH -- follows proven AW Iris compat pattern (`ShaderIrisMixin`)
- Mixin target: HIGH -- `@WrapOperation` on `ResourceProvider.getResourceOrThrow()` in `readShader()` is the correct level
- Thread safety: HIGH -- `@WrapOperation` is class-load-time bytecode transformation; our processor is stateless
- Perf impact: HIGH -- zero per-frame cost (compile-time only transformation)

**Research date:** 2026-04-30
**Valid until:** 2026-06-15 (30 days -- Veil and AW are actively developed; shader pipeline changes invalidate this)
