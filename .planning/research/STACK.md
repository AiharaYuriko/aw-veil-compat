# Stack Research

**Domain:** Minecraft NeoForge 1.21.1 Compatibility Mod (AW vs Veil Rendering)
**Researched:** 2026-04-30
**Confidence:** HIGH (verified via official docs and modding community sources)

## Recommended Stack

### Core Technologies

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| **NeoForge MDK (ModDevGradle)** | 1.21.1 (21.1.x) | Mod development framework | The officially supported toolchain for NeoForge 1.21.1 modding. Uses the `net.neoforged.moddev` Gradle plugin, which auto-decompiles Minecraft and handles all classpath setup. Replaces the deprecated NeoGradle. | JDK 21 required. Verified via NeoForgeMDKs/MDK-1.21-ModDevGradle (updated April 2026). |
| **Java JDK** | 21 (64-bit) | Runtime and compilation | Mandatory for Minecraft 1.21+. The `net.neoforged.moddev` plugin enforces JavaLanguageVersion.of(21). Microsoft OpenJDK recommended for Windows. |
| **Mixin (SpongePowered)** | 0.15.x (bundled with NeoForge) | Bytecode injection framework | The standard mechanism for intercepting and modifying Minecraft and mod code at runtime without source access. NeoForge bundles Mixin and loads it via `neoforge.mods.toml` `[[mixins]]` block. No separate dependency needed. |
| **LWJGL** | 3.3.3 | OpenGL bindings | Minecraft 1.21.1 ships LWJGL 3.3.3. Confirmed via NeoForge 1.21.1 client logs (mclo.gs/d3AUa74). This version provides the `GL33C` and `GL30C` classes needed for VAO/VBO/shaders, plus `GL43C` for Direct State Access (DSA) if desired. |
| **OpenGL Profile** | 3.2 Core Profile | Rendering API | Minecraft 1.17+ uses OpenGL 3.2 Core. Fixed-function pipeline is unavailable. All rendering goes through VAOs, VBOs, and shaders. This is critical -- AW's VBO approach and Veil's shader pipeline both operate within this constraint. |
| **Gradle Wrapper** | 8.10+ | Build system | Included in MDK template. ModDevGradle requires Gradle 8.10+. The wrapper (`gradlew`) is the standard entry point. Do NOT use a system-installed Gradle; always use the wrapper. |

### Supporting Libraries and Frameworks

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| **SpongePowered Mixin (annotation processor)** | 0.15.x | Mixin refmap generation | Required at compile time. Configure in `build.gradle` as annotation processor. Generates the `.refmap.json` file that Mixin uses to resolve obfuscated names at runtime. |
| **Parchment Mappings** | 2024.12+ (for MC 1.21) | Human-readable parameter names | Optional but recommended. Provides meaningful names for Minecraft's obfuscated method parameters, making debugging easier. Configure via `neoForge.mappings()` block in `build.gradle`. |
| **ThinGL (thingl)** | 0.1.x | Modern OpenGL wrapper for Minecraft | Optional. A lightweight LWJGL 3 wrapper by RaphiMC (LGPL-3.0, updated April 2025). Provides VAO/VBO batch rendering with automatic GL state revert. **Use only if writing new custom rendering code**; not needed for compatibility fixes. |
| **NeoForge Event System** | (bundled) | Client rendering events | For surface-level hooks. `RenderLevelStageEvent` allows insertion at well-defined points in the world render pipeline (AFTER_BLOCK_ENTITIES, AFTER_ENTITIES, etc.). Used for hooking into vanilla rendering without Mixin. |
| **Access Transformers** | (bundled) | Widen class/field/method visibility | Use when a Mixin alone is insufficient (e.g., you need to access a private field in a vanilla class). Declare in `src/main/resources/META-INF/accesstransformer.cfg`. Works with ModDevGradle. |
| **Interface Injection** | (bundled) | Add interfaces at compile time | For type-safe casting. Declare in `META-INF/interfaceinjection.json`. Requires Mixin at runtime to actually implement the interface. Use when you need to call custom methods on vanilla objects. |
| **JUnit FML** | 7.0.x | Unit/integration testing | Optional. NeoForge provides `junit-fml` for running mod tests in a mocked environment. Not typically needed for rendering fixes. |

### Development Tools

| Tool | Version | Purpose | Notes |
|------|---------|---------|-------|
| **IntelliJ IDEA** | 2024.3+ | Primary IDE | Industry standard for Minecraft modding. The `Minecraft Development` plugin (by mthvecchio / DarkKronicle) provides Mixin syntax highlighting, AT support, and refmap validation. |
| **RenderDoc** | 1.35+ | GPU frame debugger | **Essential for this project.** Captures every draw call, shader uniform state, VBO contents, and framebuffer. Launch Minecraft from RenderDoc with "Capture Child Processes" enabled. Use Pipeline State view to inspect AW vs Veil program bindings, uniform values, and GL state. |
| **NVIDIA Nsight Graphics** | 2025.x | GPU debugging/optimization | Alternative to RenderDoc. Provides detailed shader debugging (HLSL/GLSL source-level step-through). Use if RenderDoc doesn't capture enough detail on the specific GPU vendor. |
| **Nsight and RenderDoc Loader mod** | (mod) | Easy RenderDoc injection on Windows | Simplifies attaching RenderDoc to a NeoForge dev instance. Enable F12 capture without external launcher setup. |
| **Iris Shader Debug Mode** | Iris 1.8.8+ | Patched shader inspection | Enable via Ctrl+D + restart. Patched shaders are output to `.minecraft/patched_shaders/` with correct line numbers. Useful for comparing Veil's shader modifications against vanilla. |
| **NeoForge OpenGL Debug Mode** | (config) | OpenGL error tracing | Set `debugOpenGl=true` in FML config (FancyModLoader >=7.0.6). Enables OpenGL debug output with object labels. Helps catch GL errors like invalid program usage, uniform mismatch, or buffer binding errors. |

## Installation

### build.gradle (ModDevGradle)

```groovy
plugins {
    id 'java'
    id 'net.neoforged.moddev' version '2.0.80'
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

neoForge {
    version = "21.1.209" // Use 21.1.x for MC 1.21.1
    runs {
        client {
            client()
            gameDirectory = file("runs/client")
            property 'mixin.env.remapRefMap', 'true'
            property 'mixin.env.refMapRemappingFile', "${projectDir}/build/createSrgToMcp/output.srg"
            jvmArgument '-Dfml.earlyProgressWindow=false'
        }
        server {
            server()
        }
    }
    // Enable access transformers (auto-detected from META-INF)
    accessTransformers {
        file('src/main/resources/META-INF/accesstransformer.cfg')
    }
    interfaceInjectionData {
        file('src/main/resources/META-INF/interfaceinjection.json')
    }
}

dependencies {
    // Mixin annotation processor (for refmap generation)
    annotationProcessor 'org.spongepowered:mixin:0.15.5:processor'
}

// Ensure refmap is included in JAR
tasks.named('jar') {
    manifest {
        attributes(
            'MixinConfigs': "${mod_id}.mixins.json"
        )
    }
}
```

### gradle.properties

```properties
org.gradle.jvmargs=-Xmx4G
org.gradle.daemon=false
minecraft_version=1.21.1
minecraft_version_range=[1.21,1.22)
neo_version=21.1.209
neo_version_range=[21.1,21.2)
loader_version_range=[1,)
mod_id=awveilcompat
mod_name=AW-Veil Compat
mod_version=1.0.0
mod_group_id=com.example.awveilcompat
```

### neoforge.mods.toml (Mixin activation)

```toml
[[mixins]]
config="${mod_id}.mixins.json"
```

### Mixin Config (`src/main/resources/awveilcompat.mixins.json`)

```json
{
  "required": true,
  "package": "com.example.awveilcompat.mixin",
  "compatibilityLevel": "JAVA_21",
  "refmap": "${mod_id}.refmap.json",
  "client": [
    "render.AWModelVBOInjector",
    "render.VeilShaderProgramInterceptor",
    "render.BufferUploaderCaptureMixin"
  ],
  "injectors": {
    "defaultRequire": 1
  }
}
```

## Architecture: How These Technologies Fit Together

```
┌─────────────────────────────────────────────────────────────────┐
│                      Game Thread                                  │
│  Armourer's Workshop VBO code → builds geometry                 │
│  ↓ (Mixin intercepts uniform injection and shader binding)      │
├─────────────────────────────────────────────────────────────────┤
│                      Render Thread                                │
│  RenderSystem manages GL state cache → GlStateManager           │
│  ↓                                                               │
│  Vanilla: VertexBuffer.drawWithShader(pose, proj, shader)       │
│          → binds VAO, uploads uniforms, glDrawElements          │
│  ↓                                                               │
│  Veil: Overrides shader pipeline via DirectShaderCompiler       │
│       → replaces program bindings, manages own uniforms         │
│  ↓                                                               │
│  [CONFLICT ZONE] AW injects uniform into "current" shader       │
│  → Veil's shader may not have AW's uniform locations → MISMATCH │
└─────────────────────────────────────────────────────────────────┘
```

**Key interface points for Mixin injection:**

| Hook Point | Target | Purpose |
|------------|--------|---------|
| AW VBO flush | `AW VBO rendering class` | Intercept the draw call path to capture/redirect uniform injection |
| Veil program switch | `Veil ShaderProgram` class | Detect when Veil changes the active shader program |
| `ShaderInstance.apply()` | `net.minecraft.client.renderer.ShaderInstance` | Capture when vanilla shader uniforms are uploaded |
| `RenderSystem.applyShader()` | `com.mojang.blaze3d.systems.RenderSystem` | Intercept uniform upload at the RenderSystem level |
| `BufferUploader.drawWithShader()` | `com.mojang.blaze3d.vertex.BufferUploader` | Capture the actual draw submission for debugging |

## Alternatives Considered

| Category | Recommended | Alternative | Why Not |
|----------|-------------|-------------|---------|
| **Build plugin** | ModDevGradle | NeoGradle (legacy) | NeoGradle is deprecated for 1.21+. ModDevGradle is the officially maintained successor with better MCP mappings support and simplified DSL. |
| **Deobfuscation** | Official NeoForge mappings | Yarn (Fabric) / MCP | NeoForge bundles its own mappings via NeoForm. Mixing Fabric's Yarn with NeoForge causes class/method resolution issues. |
| **Shader debugging** | RenderDoc | GPU vendor tools (NSight, Radeon GPU Profiler) | RenderDoc is vendor-agnostic, open source, and captures the full OpenGL state. NSight is useful for NV-specific issues but vendor-locked. |
| **Bytecode manipulation** | Mixin (SpongePowered) | CoreMod / Plugins / ASM direct | Mixin is the community standard. CoreMods are fragile and harder to debug. Mixin provides annotations, refmap, and compatibility layer across obfuscated names. |
| **Custom rendering library** | Direct LWJGL + RenderSystem | ThinGL | ThinGL is useful for new rendering code but adds a dependency. For compatibility fixes, we are intercepting existing rendering, not creating new pipelines. |

## What NOT to Use

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| **`GlStateManager.pushAttrib()` / `popAttrib()`** | These desync GlStateManager's internal state cache from actual OpenGL state. `popAttrib()` restores GL state directly via `glPopAttrib` but does NOT update `GlStateManager`'s `currentState` booleans. The manager then silently skips future GL calls because its cache says "already in the desired state" when it is not. Documented in Forge Issue #1637 and ModularMachinery Issue #115. | Manually save and restore only the specific GL states you change, using `RenderSystem` methods. |
| **Direct `GL11.gl*` calls bypassing `RenderSystem`** | Skips RenderSystem's thread-safety checks and state cache. Causes the same desync problem as pushAttrib/popAttrib. RenderSystem is the sole entry point for GL state on 1.21+. | Always use `RenderSystem.*` methods. If you must make raw GL calls, also call the corresponding `RenderSystem` setter to update the cache. |
| **`Tesselator.end()` pattern** | Removed in 1.21. Uses the old immediate-mode tessellation path. | `BufferBuilder.begin(...)` + vertices + `BufferUploader.drawWithShader(builder.end())` |
| **Reflection on private `GlStateManager` / `RenderSystem` fields** | The field names and obfuscation change across Minecraft versions. Extremely fragile. This is especially dangerous for `shaderLightDirections` (private static Vec3f[]) and internal cache booleans. | Use Mixin `@Inject`/`@Accessor` or NeoForge Access Transformers to expose fields in a version-stable way. |
| **Multiple Mixin configs from different mods targeting the same method** | Two mods injecting at the same method with `@Inject` at `HEAD`/`RETURN` can conflict. Priority ordering (`@Inject(require=...)`) matters. | Test your mixins against the full modset (AW + Veil + Iris + any other rendering mods). Use `@Group` and `@Prefix` for coordination. |
| **Legacy Forge-style `@Mod.EventBusSubscriber` on render thread** | In 1.21+, rendering events fire on the render thread. `@SubscribeEvent` handlers must not mutate game state. | Ensure all rendering event handlers are thread-safe and only modify GL state. |
| **System-installed Gradle instead of wrapper** | Version mismatches cause cryptic build failures. ModDevGradle requires Gradle 8.10+. | Always use `./gradlew` (the Gradle wrapper included in the MDK template). |

## Stack Patterns by Variant

**If targeting NeoForge 21.1.222 specifically:**
- Use `neo_version = "21.1.222"` in `gradle.properties`. The `21.1.x` line is the 1.21.1 branch. Double-check that the mod generator doesn't set `21.10.x` (which is 1.21.10). Issue #33 on neoforged/mod-generator documents this trap.

**If Mixin annotation processing fails:**
- Add `annotationProcessor 'org.spongepowered:mixin:0.15.5:processor'` to dependencies explicitly. Some MDK templates omit this, causing "No refMap loaded" errors at runtime.

**If the Veil DirectShaderCompiler is compiled fresh each frame (no caching):**
- The `DirectShaderCompiler` does NOT cache by default (documented in Veil API). If performance issues arise, use the `CachedShaderCompiler` factory method instead.

**If debugging OpenGL errors at runtime:**
- Enable `debugOpenGl=true` in FML config. This enables OpenGL `GL_DEBUG_OUTPUT` with source/type/severity filtering. Combine with RenderDoc for full call-stack traces.

## Version Compatibility

| Package | Compatible With | Notes |
|---------|-----------------|-------|
| `net.neoforged.moddev:2.0.80+` | Gradle 8.10+ | Lower Gradle versions will fail. Use the wrapper. |
| Minecraft 1.21.1 | NeoForge 21.1.x (not 21.10.x) | CRITICAL: 21.10.x = Minecraft 1.21.10, NOT 1.21.1. Check the version carefully. |
| LWJGL 3.3.3 | OpenGL 3.2 Core Profile | Minecraft creates a 3.2 core context. DSA (GL 4.5) features work only if the driver supports them at runtime; do not assume DSA availability. |
| Iris 1.8.8 | NeoForge 1.21.1 | Iris does NOT require Fabric. A NeoForge-compatible build of Iris exists for 1.21.1. Iris should be included in the test mod set. |
| Sable (ryanhcode) | NeoForge 1.21.1 | Sable is a physics mod, NOT a shader library. Earlier references to "Sable" as Veil's shader pipeline appear to be a confusion. Veil uses its own "pinwheel" shader format. |
| Veil (FoundryMC) | NeoForge 1.21.1 (branch: 1.21) | Veil's shader modification system uses a custom DSL with `[UNIFORM]`, `[FUNCTION]`, `#priority` directives. Shader modifications go in `assets/modid/pinwheel/shader_modifiers/`. |

## OpenGL State Management: Critical Details for This Project

Minecraft's rendering state management is a hierarchy that this project MUST account for:

```
RenderSystem (thread-safe public API)
  └─ GlStateManager (internal state cache, not thread-safe)
       └─ OpenGL context (actual GPU state)
```

**The cache desync problem:**
- When a mod calls `GlStateManager.enableBlend()`, the manager sets `blendState.blend.currentState = true` AND calls `GL11.glEnable(GL_BLEND)`.
- When a mod calls `GL11.glEnable(GL_BLEND)` directly (bypassing the manager), only the GPU state changes -- the cache stays `false`.
- When a mod calls `glPushAttrib`/`glPopAttrib`, the GPU state is restored but the cache is NOT updated.
- Subsequent `GlStateManager.enableBlend()` sees `currentState == true`, thinks "already enabled," and does nothing -- but GL is actually disabled.

**This is EXACTLY the scenario that likely causes the AW-Veil conflict.** AW's VBO code injects uniforms by "borrowing the currently bound shader." If Veil (or Iris) has changed the current shader program through a path that bypasses `RenderSystem`'s cache (e.g., via Veil's own `DirectShaderCompiler`), then AW's uniform injection may target a stale program handle or a program that doesn't have AW's uniforms.

**Mitigation strategy:**
1. Use RenderDoc to capture the exact OpenGL state at the point AW calls `glGetIntegerv(GL_CURRENT_PROGRAM)` and immediately after.
2. Compare which program is bound vs. which program Veil expects to be bound.
3. If a mismatch exists, the fix is either: (a) force AW to use Veil's actual program handle, (b) create a shared program state that both can write to, or (c) redirect AW's uniform writes to the correct program before the draw call.

## Debugging Toolchain Flow

```
Initial diagnosis:
  RenderDoc capture → inspect Pipeline State → compare AW frame vs vanilla frame

Targeted investigation:
  Mixin @Inject at ShaderInstance.apply() → log program ID and uniforms
  RenderDoc Pipeline State → check GL_CURRENT_PROGRAM, uniform values

Root cause confirmation:
  Verify: AW writes uniform U to program P1, but GL_CURRENT_PROGRAM = P2 (Veil's program)
  → Confirms shader state mismatch hypothesis

Fix verification:
  After patch → RenderDoc capture → verify uniform U is written to correct program
  → verify correct visual output
```

## Sources

- [NeoForgeMDKs/MDK-1.21.1-ModDevGradle](https://github.com/NeoForgeMDKs/MDK-1.21.1-ModDevGradle) -- Official MDK template, verified April 2026. HIGH confidence.
- [NeoForgeMDKs/MDK-1.21.1-NeoGradle](https://github.com/NeoForgeMDKs/MDK-1.21.1-NeoGradle) -- NeoGradle variant, last synced ~1 year ago. Use ModDevGradle instead. MEDIUM confidence.
- [Mixin (SpongePowered) GitHub](https://github.com/SpongePowered/Mixin) -- Official Mixin framework. HIGH confidence.
- [FoundryMC/Veil Wiki - ShaderModification](https://github-wiki-see.page/m/FoundryMC/Veil/wiki/ShaderModification) -- Veil shader injection DSL documentation. MEDIUM confidence (wiki may lag behind code).
- [FoundryMC/Veil API - DirectShaderCompiler](https://foundrymc.github.io/Veil/foundry/veil/impl/client/render/shader/compiler/DirectShaderCompiler.html) -- Veil's uncompiled shader compiler. HIGH confidence for API surface.
- [Veil on Modrinth](https://modrinth.com/mod/veil) -- Veil mod page. HIGH confidence.
- [Forge Issue #1637 - GlStateManager.popAttrib() does not work properly](https://github.com/MinecraftForge/MinecraftForge/issues/1637) -- Documented state cache desync bug. HIGH confidence.
- [Modular Machinery Issue #115 - JEI scene renderer should not use GLSM push/popAttrib()](https://github.com/NovaEngineering-Source/ModularMachinery-Community-Edition/issues/115) -- Concrete example of the desync symptom. HIGH confidence.
- [MinecraftForge Issue #10165 - 1.21.3 Rendering State Extraction Hook](https://github.com/MinecraftForge/MinecraftForge/issues/10165) -- 1.21 rendering changes. MEDIUM confidence.
- [NeoForge Docs - Access Transformers](https://docs.neoforged.net/docs/1.21.1/advanced/accesstransformers/) -- Official AT documentation for 1.21.1. HIGH confidence.
- [ShaderLABS Wiki - Attaching Graphics Debuggers to Minecraft](https://shaderlabs.org/wiki/Attaching_Graphics_Debuggers_to_Minecraft) -- RenderDoc setup guide. HIGH confidence.
- [MCBBS Chinese Tutorial - RenderDoc debugging MC rendering](https://mcbbs.10961096.xyz/post/1463527) -- Step-by-step RenderDoc setup. MEDIUM confidence.
- [shaders.properties - Debugging Shaders](https://shaders.properties/current/reference/miscellaneous/debugging_shaders/) -- Iris debug mode and RenderDoc loader mod. MEDIUM confidence.
- [NeoForge 1.21.1 Client Log](https://mclo.gs/d3AUa74) -- Confirms LWJGL 3.3.3, OpenGL 3.2 Core Profile. HIGH confidence.
- [ThinGL on GitHub](https://github.com/raphimc/thingl) -- Modern LWJGL wrapper, LGPL-3.0, created Jan 2025. MEDIUM confidence.
- [NeoForge Client Events - RenderLevelStageEvent](https://nekoyue.github.io/ForgeJavaDocs-NG/javadoc/1.21.1-21.1.x/net/neoforged/neoforge/client/event/RenderLevelStageEvent.html) -- JavaDoc for rendering stage event. HIGH confidence.
- [AcceleratedRendering Dev Guide - Mixin Patterns](https://deepwiki.com/Argon4W/AcceleratedRendering/8-development-guide) -- Reference implementation of 13 modular Mixin configs for rendering interception. MEDIUM confidence (3rd party code).
- [FabricMC Basic Rendering Concepts](https://docs.fabricmc.net/zh_cn/1.21.11/develop/rendering/basic-concepts) -- Rendering pipeline overview (Fabric-side but concepts apply to NeoForge). MEDIUM confidence.
- [OpenComputers RenderState.scala](https://git.oneechan.xyz/AstralTransRocketries/OpenComputers/blame/commit/a91b7b39391103fb241fb4eb6a3584a59f0754fd/src/main/scala/li/cil/oc/util/RenderState.scala) -- Real-world workaround for GlStateManager cache desync. HIGH confidence.
- [Sable on Modrinth](https://modrinth.com/mod/sable/version/mXmIPopR) -- Sable is a physics mod, NOT a shader library. MEDIUM confidence (project description).

---
*Stack research for: AW-Veil Compat (Minecraft NeoForge 1.21.1)*
*Researched: 2026-04-30*
