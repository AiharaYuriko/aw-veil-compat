# Phase 1: Investigation and Foundation - Research

**Researched:** 2026-04-30
**Domain:** Minecraft NeoForge 1.21.1 Mod Scaffold, Mixin Injection, GL State Probing, Runtime Detection
**Confidence:** HIGH

## Summary

Phase 1 establishes the diagnostic infrastructure and runtime skeleton for the AW-Veil compat mod. Three deliverables are required: (1) a NeoForge 1.21.1 mod scaffold with version lock, (2) dual-side Mixin GL state probes at AW's `RenderType.clearRenderState()` injection point and Veil's `RenderTypeStageRegistry`/`DirectShaderCompiler` entry points, and (3) secure runtime detection for AW (`armourers_workshop`) and Veil (`veil`) that prevents crash when either is absent.

The mod scaffold follows the AcceleratedRendering multi-mixin-config pattern: separate `awveilcompat.core.mixins.json`, `awveilcompat.aw.mixins.json`, and `awveilcompat.veil.mixins.json` files, each registered in `neoforge.mods.toml`. A Mixin plugin (`IMixinConfigPlugin`) gates AW/Veil-targeting mixins using `FMLLoader.getLoadingModList().getModFileById()` for early-load detection, combined with `@Pseudo` and `require = 0` for fail-safe injection.

The GL probe strategy is dual-sided: on the AW side, inject at `RenderType.clearRenderState()` HEAD with high priority (to capture the state AW sees before AW's own code runs); on the Veil side, inject at `RenderTypeStageRegistry.addStage()` and `DirectShaderCompiler.compile()` to capture shader lifecycle events. Both probes output to a dedicated probe log file with nanosecond timestamps using `System.nanoTime()` for offline correlation.

The environment is confirmed: JDK 21 (zulu21.32.17) at `D:\Software\CCode\zulu21.32.17-ca-jdk21.0.2-win_x64`, Windows 11. AW and Veil source repos must be cloned as the first task.

**Primary recommendation:** Build the mod scaffold first (with runtime detection and multi-mixin config), then add probes incrementally. Clone AW + Veil source repos as the very first step so exact class/method names can be verified against actual source.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Mod bootstrap (registration, metadata) | API/Backend (NeoForge) | -- | NeoForge's `@Mod` + `neoforge.mods.toml` handles mod lifecycle. No client/browser tier. |
| Mixin injection into RenderType | Bytecode (Mixin) | API/Backend (NeoForge) | Mixin transforms Minecraft classes at load time. NeoForge registers the mixin config. |
| Runtime mod detection | API/Backend (NeoForge ModList) | Bytecode (Mixin plugin) | `ModList.get().isLoaded()` for runtime checks; `FMLLoader.getLoadingModList()` in Mixin plugin (early load). |
| GL state reading | API/Backend (LWJGL) | -- | LWJGL's `GL20.glGetIntegerv()` etc. directly query OpenGL. No rendering engine wrapper used. |
| Probe log output | API/Backend (Log4j / file I/O) | -- | Java file I/O on the render thread. Output to dedicated probe log file. |
| AW shader program fix | Bytecode (Mixin) + API/Backend (shader) | -- | Not in Phase 1; planned for Phase 2. |
| Source analysis (AW/Veil decompilation) | Development tool (human analysis) | -- | Developer reads decompiled/cloned source to identify exact injection targets. |

## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** Source acquisition - Clone repos + decompile JAR combined. Prefer cloning GitHub repos for full source, fallback to decompiling release JAR for unmapped parts.
- **D-02:** Target repos - AW: `SAGESSE-CN/Armourers-Workshop` (NeoForge 1.21.1 maintenance branch), Veil: `FoundryMC/Veil` (NeoForge 1.21.1 corresponding version)
- **D-03:** Source storage path - Create `aw-source/` and `veil-source/` subdirectories under `D:\Software\CCode\AW\`
- **D-04:** Dual-side GL state probes - AW side at `clearRenderState` Mixin injection point, Veil side at `RenderTypeStage`/`DirectShaderCompiler` injection points
- **D-05:** Probe standard data set - AW side: `GL_CURRENT_PROGRAM` + `aw_ModelViewMatrix` + `aw_MatrixFlags` + `aw_TextureMatrix` + nanosecond timestamp; Veil side: shader bind/unbind events + compiled shader source hash + nanosecond timestamp
- **D-06:** Probe output to nanosecond-timestamped log files, offline diff/correlation via script, no real-time HUD in this phase

### Claude's Discretion

- GL state probe implementation details (probe class structure, GL state reading method, log format)
- Mod project skeleton - default to AcceleratedRendering's multi-mixin-config pattern (separate mixin config files per target mod + Mixin plugin runtime conditional loading + `@Pseudo` annotations)
- Probe log comparison script implementation approach
- Version detection and `mods.toml`/`build.gradle` specific configuration

### Deferred Ideas (OUT OF SCOPE)

None - discussion stayed within phase scope.

## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| DETECT-01 | AW mod not present/loaded: compat mod does not crash or error | Implemented via Mixin plugin (`shouldApplyMixin()` returning false) + `ModList.get().isLoaded("armourers_workshop")` runtime guard. See Architecture Patterns section. |
| DETECT-02 | Veil mod not present/loaded: compat mod does not crash or error | Implemented via Mixin plugin (`shouldApplyMixin()` returning false) + `ModList.get().isLoaded("veil")` runtime guard. See Architecture Patterns section. |
| VERSION-01 | Exclusively NeoForge 1.21.1 (version 21.1.222), no multi-version | Enforced in `gradle.properties` + `neoforge.mods.toml` dependency version range. See Standard Stack section. |

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| NeoForge ModDevGradle | 2.0.80-beta | Mod build system | Official NeoForge toolchain for 1.21.1. Replaces deprecated NeoGradle. [VERIFIED: neoforged/ModDevGradle GitHub] |
| JDK | 21 (64-bit) | Runtime + compilation | Mandatory for Minecraft 1.21+. ModDevGradle enforces `JavaLanguageVersion.of(21)`. [VERIFIED: STACK.md] |
| SpongePowered Mixin | 0.15.x (bundled) | Bytecode injection | Standard for Minecraft modding. NeoForge bundles it. [VERIFIED: STACK.md] |
| LWJGL | 3.3.3 (bundled) | OpenGL bindings | Minecraft 1.21.1 ships LWJGL 3.3.3. Provides `GL20`, `GL11` classes for state queries. [VERIFIED: NeoForge client log mclo.gs/d3AUa74] |
| OpenGL Profile | 3.2 Core Profile | Rendering API | Minecraft 1.17+ uses OpenGL 3.2 Core. Fixed-function pipeline unavailable. [VERIFIED: STACK.md] |
| Gradle Wrapper | 8.10+ | Build system | MDK template includes wrapper. Do not use system Gradle. [VERIFIED: STACK.md] |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Mixin annotation processor | 0.15.5:processor | Refmap generation | Required at compile time for mixin refmap. Configure in `build.gradle` dependencies. [VERIFIED: STACK.md] |
| Parchment Mappings | 2024.12+ (MC 1.21) | Human-readable parameter names | Optional but recommended for debugging. Configure via `neoForge.mappings()` block. [VERIFIED: STACK.md] |
| Access Transformers | (bundled) | Widen visibility | Use if Mixin alone is insufficient (private field access). Declare in `META-INF/accesstransformer.cfg`. [CITED: docs.neoforged.net] |

### Version Lock Configuration

```properties
# gradle.properties
neo_version=21.1.222
neo_version_range=[21.1,21.2)
minecraft_version=1.21.1
minecraft_version_range=[1.21,1.22)
loader_version_range=[1,)
```

The NeoForge version `21.1.222` is the locked target. Critical: `21.1.x` = 1.21.1, NOT `21.10.x` (which = 1.21.10). [VERIFIED: Stack research]

### Installation

```bash
# Build
./gradlew build

# Run client
./gradlew runClient

# Eclipse/IntelliJ: import as Gradle project, then run client task
```

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| ModDevGradle | NeoGradle (legacy) | NeoGradle deprecated for 1.21+. ModDevGradle is the maintained successor. |
| `IMixinConfigPlugin` | `@Pseudo` + `require = 0` only | Plugin is needed for per-mixin gating by mod presence. `@Pseudo` alone prevents crash but does not skip injection entirely -- injector still runs with empty/no-op body. |

## Architecture Patterns

### System Architecture Diagram

```
Source Repos (Phase 1, Step 1):
  D:\Software\CCode\AW\aw-source\     <-- SAGESSE-CN/Armourers-Workshop (NeoForge 1.21.1 branch)
  D:\Software\CCode\AW\veil-source\   <-- FoundryMC/Veil (1.21 branch)
       |
       | (developer reads source to identify exact class/method names)
       v

Mod Project (D:\Software\CCode\aw-veil-compat\):
  ┌─────────────────────────────────────────────┐
  │  neoforge.mods.toml                         │
  │  ├─ [[mods]] modId="awveilcompat"          │
  │  ├─ [[mixins]] config="awveilcompat.core.mixins.json"  │
  │  ├─ [[mixins]] config="awveilcompat.aw.mixins.json"    │
  │  └─ [[mixins]] config="awveilcompat.veil.mixins.json"  │
  └─────────────────────────────────────────────┘
       |
       v
  ┌─────────────────────────────────────────────┐
  │  MixinConfigPlugin                          │
  │  onLoad(): cache mod presence               │
  │  shouldApplyMixin():                        │
  │    aw/*.java -> needs "armourers_workshop"   │
  │    veil/*.java -> needs "veil"               │
  └─────────────────────────────────────────────┘
       |
       v (at game startup, Mixin transforms classes)
       
  ┌─────────────────────────────────────────────┐
  │  AW-side Probe                               │
  │  @Inject RenderType.clearRenderState() HEAD │
  │  High priority (runs before AW's own mixin) │
  │  Captures:                                   │
  │    GL_CURRENT_PROGRAM                        │
  │    aw_ModelViewMatrix location/value         │
  │    aw_MatrixFlags location/value             │
  │    aw_TextureMatrix location/value           │
  │    System.nanoTime()                         │
  └─────────────────────────────────────────────┘
       |
       v
  ┌─────────────────────────────────────────────┐
  │  Veil-side Probe                             │
  │  @Inject RenderTypeStageRegistry.addStage() │
  │  @Inject DirectShaderCompiler.compile()      │
  │  Captures:                                   │
  │    shader bind/unbind events                  │
  │    compiled shader source hash                │
  │    System.nanoTime()                         │
  └─────────────────────────────────────────────┘
       |
       v (both write to separate log file)
  ┌─────────────────────────────────────────────┐
  │  Probe Log Output                            │
  │  "run/probes/aw-probe-{timestamp}.log"       │
  │  "run/probes/veil-probe-{timestamp}.log"     │
  │  Format: grep/awk friendly TSV                │
  │  Columns: nanoTime | event | programId | ... │
  └─────────────────────────────────────────────┘
       |
       v (offline analysis, not in game)
  ┌─────────────────────────────────────────────┐
  │  diff/correlation script                     │
  │  Aligns AW + Veil timelines by nanoTime      │
  │  Detects program mismatches                   │
  └─────────────────────────────────────────────┘
```

### Recommended Project Structure

```
aw-veil-compat/
├── build.gradle                              # ModDevGradle build
├── gradle.properties                         # Version lock: neo=21.1.222
├── settings.gradle
├── gradlew / gradlew.bat
├── run/                                      # Dev runtime directory
│   ├── logs/latest.log                       # Minecraft game log
│   └── probes/                               # Probe log output (created at runtime)
│
└── src/main/
    ├── java/com/example/awveilcompat/
    │   ├── AwVeilCompat.java                 # @Mod entry point
    │   │
    │   ├── mixin/
    │   │   ├── AwVeilCompatMixinPlugin.java  # IMixinConfigPlugin - gates AW/Veil mixins
    │   │   │
    │   │   ├── core/                         # Mixins into vanilla Minecraft
    │   │   │   └── RenderTypeProbeMixin.java # Probe at clearRenderState()
    │   │   │
    │   │   ├── aw/                           # Mixins into Armourer's Workshop (gated)
    │   │   │   └── AwProbeMixin.java         # AW-specific injection points
    │   │   │
    │   │   └── veil/                         # Mixins into Veil (gated)
    │   │       ├── RenderTypeStageProbeMixin.java  # Probe at addStage()
    │   │       └── DirectShaderCompilerProbeMixin.java # Probe at compile()
    │   │
    │   ├── probe/                            # GL state probe infrastructure
    │   │   ├── GlStateReader.java            # LWJGL GL state queries
    │   │   ├── ProbeData.java                # Data record for one probe event
    │   │   └── ProbeLogger.java              # File output with nanoTime timestamps
    │   │
    │   └── detection/                        # Runtime mod detection
    │       └── ModDetector.java              # isAWLoaded() / isVeilLoaded() helpers
    │
    └── resources/
        ├── META-INF/
        │   └── neoforge.mods.toml            # Mod metadata + mixin config declarations
        │
        ├── awveilcompat.core.mixins.json     # Core mixins (always loaded)
        ├── awveilcompat.aw.mixins.json       # AW mixins (gated by plugin)
        └── awveilcompat.veil.mixins.json     # Veil mixins (gated by plugin)
```

### Pattern 1: Multi-Mixin Config with Plugin Gating (AcceleratedRendering Pattern)

**What:** One mixin config JSON per target domain (core/AW/Veil), each registered in `neoforge.mods.toml`. A single `IMixinConfigPlugin` gates which mixins actually get applied based on runtime mod detection.

**When to use:** This is the standard for this project. It provides:
- Independent failure domains (one config failing does not block others) [PITFALL-09 mitigation]
- Per-target-mod gating (AW mixins only when AW loaded, Veil mixins only when Veil loaded) [DETECT-01, DETECT-02]
- Clear separation of concerns per the project's architectural guideline [CITED: CONTEXT.md Claude's Discretion]

**neoforge.mods.toml:**
```toml
[[mixins]]
config = "awveilcompat.core.mixins.json"

[[mixins]]
config = "awveilcompat.aw.mixins.json"

[[mixins]]
config = "awveilcompat.veil.mixins.json"
```

**awveilcompat.core.mixins.json:**
```json
{
  "required": true,
  "package": "com.example.awveilcompat.mixin.core",
  "compatibilityLevel": "JAVA_21",
  "refmap": "awveilcompat.refmap.json",
  "client": ["RenderTypeProbeMixin"],
  "injectors": {
    "defaultRequire": 1
  }
}
```

**awveilcompat.aw.mixins.json:**
```json
{
  "required": true,
  "package": "com.example.awveilcompat.mixin.aw",
  "plugin": "com.example.awveilcompat.mixin.AwVeilCompatMixinPlugin",
  "compatibilityLevel": "JAVA_21",
  "refmap": "awveilcompat.refmap.json",
  "client": ["AwProbeMixin"],
  "injectors": {
    "defaultRequire": 0
  }
}
```

**AwVeilCompatMixinPlugin.java (core gating):**
```java
public class AwVeilCompatMixinPlugin implements IMixinConfigPlugin {
    private boolean awPresent;
    private boolean veilPresent;

    @Override
    public void onLoad(String mixinPackage) {
        // LoadingModList is available at Mixin plugin time; ModList.get() is NOT
        this.awPresent = FMLLoader.getLoadingModList()
                .getModFileById("armourers_workshop") != null;
        this.veilPresent = FMLLoader.getLoadingModList()
                .getModFileById("veil") != null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.contains(".aw.")) return this.awPresent;
        if (mixinClassName.contains(".veil.")) return this.veilPresent;
        return true; // core mixins always apply
    }

    // ... required stub methods (getRefMapperConfig, acceptTargets, preApply, postApply, getMixins)
}
```

### Pattern 2: Safe @Pseudo Mixin for Optional Target Classes

**What:** When a mixin targets a class that may not exist (AW or Veil internal class), use `@Pseudo` on the mixin class and `@Mixin(targets = "full.class.Name")` (string form, not class literal) combined with `require = 0` on injections.

**When to use:** All mixins in the `aw/` and `veil/` packages should use this pattern as a second safety layer beyond the plugin gating. If plugin gating somehow fails (e.g., updated mod ID), `@Pseudo` prevents a hard crash. [VERIFIED: SpongePowered Mixin docs]

**Example:**
```java
@Pseudo
@Mixin(targets = "some.other.mod.TargetClass")
public class OptionalModMixin {
    @Inject(method = "targetMethod", at = @At("HEAD"), require = 0)
    private void onTarget(CallbackInfo ci) {
        // Safe probe logic
    }
}
```

### Pattern 3: Dual-Side Probe with Nanosecond Correlation

**What:** Both AW-side and Veil-side probes emit events with a shared nanosecond timestamp (via `System.nanoTime()`). An offline script correlates the two timelines to determine the exact sequence of GL state changes.

**When to use:** Phase 1 probe implementation. The key investigative question is: what is `GL_CURRENT_PROGRAM` when AW's `clearRenderState` hook fires, and does Veil replace it before that point?

**Probe data flow:**
```
Veil compile event        [t=1000]  Veil compiles shader S1
Veil bind event           [t=1500]  Veil binds program P2
    ... time passes ...
AW-side HEAD injector     [t=2000]  AW reads GL_CURRENT_PROGRAM -> P2 (WRONG!)
                                     AW looks up "aw_ModelViewMatrix" on P2 -> -1 (not found)
                                     glGetError -> GL_INVALID_OPERATION (silent)
AW-side RETURN injector   [t=2100]  (if AW's VBO draw completed, or errored)
```

The correlation script aligns AW and Veil probe timelines to detect the mismatch.

### Anti-Patterns to Avoid

- **@Overwrite on any rendering class:** Multiple mods target `RenderType`, `LevelRenderer`, `ShaderInstance`. @Overwrite has last-writer-wins semantics. Always use `@Inject` (HEAD/RETURN) or `@WrapOperation` (MixinExtras). [CITED: STATE.md Decision 3, PITFALL-02]
- **Raw OpenGL calls bypassing RenderSystem:** Causes `GlStateManager` cache desync. If raw GL calls are necessary (e.g., uniform queries), explicitly update the cache afterward. [CITED: PITFALL-03]
- **Single large mixin config:** One failure blocks all patches. Use separate configs per concern. [CITED: PITFALL-09, PITFALL-11]
- **Trial-and-error injection point hunting:** 10 failed previous attempts prove this wastes effort. Phase 1 probes FIRST, then analyze data, then write fix code. [CITED: STATE.md Decision 2]

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Mixin conditional loading | Custom classloader checks per mixin | `IMixinConfigPlugin.shouldApplyMixin()` | Framework handles timing, caching, and error reporting. |
| GL state buffer allocation | Manual `ByteBuffer.allocateDirect()` | LWJGL `MemoryStack.stackPush().mallocInt(1)` | MemoryStack is faster (stack-allocated), auto-frees. [CITED: LWJGL memory management blog] |
| Mod presence detection | File-system JAR detection | `ModList.get().isLoaded(id)` + `FMLLoader.getLoadingModList()` | Framework handles classpath, versioning, mod discovery timing. |
| Shader program introspection | Manual OpenGL uniform enumeration loop per frame | Cache uniform locations at first query per program | `glGetUniformLocation` is cheap, but querying on every frame is wasteful. Cache on first read. |
| Log file rotation/management | Custom file naming, size limits | Log4j rolling file appender | Minecraft already uses Log4j; configure a probe-specific appender in `log4j2.xml`. |

**Key insight:** The modding ecosystem (Mixin, NeoForge, LWJGL) already provides robust infrastructure for every task in Phase 1. Do not bypass framework abstractions -- they handle edge cases (thread safety, classloading order, error recovery) that custom code would miss.

## Common Pitfalls

### Pitfall 1: Mixin Plugin runs before `ModList.get()` is available

**What goes wrong:** `IMixinConfigPlugin.onLoad()` fires during early startup. `ModList.get()` returns `null` at this point. Calling `ModList.get().isLoaded("veil")` throws a NullPointerException.

**Why it happens:** Mixin plugin initialization occurs before NeoForge's mod list construction is complete.

**How to avoid:** Use `FMLLoader.getLoadingModList().getModFileById("modid")` inside the plugin, not `ModList.get()`. Cache the result in `onLoad()` for use in `shouldApplyMixin()`. [CITED: Forge forums topic #122500]

**Warning signs:** NPE at startup in mixin plugin code, game fails to load.

### Pitfall 2: Both AW and the compat mod inject at `clearRenderState()` HEAD

**What goes wrong:** Both mixins target `clearRenderState()` at `@At("HEAD")`. Mixin applies all HEAD injectors in priority order before the original method body. The probe might run after AW's code (or vice versa), producing misleading probe data.

**Why it happens:** AW's own mixin and the compat probe both target the same injection point.

**How to avoid:** Set the probe's `@Priority` explicitly to a low value (high priority) to ensure it runs before AW's mixin. Example: `@Priority(100)` vs AW's default `@Priority(1000)`. Mixin applies lower priority values first. Document the chosen priority value. [CITED: Mixin @Priority javadoc]

**Warning signs:** Probe data shows uniform values already written (means AW ran first).

### Pitfall 3: `GL_CURRENT_PROGRAM` returns 0 at probe time

**What goes wrong:** If the probe fires when no shader program is bound (e.g., during setup, between clearRenderState and the actual draw), `GL_CURRENT_PROGRAM` is 0 and uniform queries fail.

**Why it happens:** `clearRenderState()` is a cleanup method -- the shader may have already been unbound by `ShaderStateShard.clearState()` depending on the order of composed lambda operations.

**How to avoid:** Check `programID != 0` before querying uniforms. If 0, log the fact (it is itself useful data: "no program bound at probe point"). The architecture research suggests AW's hook runs at HEAD, before the clear chain -- meaning the shader should still be bound. If it is not, that is a critical finding. [CITED: ARCHITECTURE.md Data Flow]

**Warning signs:** All probes log programID=0.

### Pitfall 4: LWJGL crash when querying GL from wrong thread

**What goes wrong:** OpenGL context is thread-specific. If the probe code runs outside the render thread, `glGetIntegerv` crashes the JVM (access-violation level, not catchable exception).

**Why it happens:** Minecraft NeoForge rendering happens on the render thread. Mixin injectors run on whatever thread the original method runs on -- which should be the render thread for `RenderType` methods. But if the method is called from a background thread (e.g., chunk rebuild thread), GL calls crash.

**How to avoid:** Always check `RenderSystem.isOnRenderThread()` or `RenderSystem.isOnGameThread()` before calling GL state queries. Log a warning and skip the probe if on the wrong thread. [CITED: PITFALL-03, LWJGL forum crash reports]

**Warning signs:** JVM crash (not Java exception) with `EXCEPTION_ACCESS_VIOLATION`, or probe data never appears because wrong-thread check silently skips.

### Pitfall 5: `@Pseudo` mixin silently does nothing if class name is wrong

**What goes wrong:** If the AW or Veil class name is mistyped (e.g., from obfuscated mappings), `@Pseudo` + `require = 0` means the mixin silently applies to nothing -- no errors, no probe output.

**Why it happens:** `@Pseudo` allows the target class to not exist. Combined with `require = 0`, Mixin will not emit any warning about the missing target.

**How to avoid:** Verify target class names by actually reading the decompiled AW/Veil source. Use the cloned repos (D-02/D-03) to check exact class names and method signatures. Test with each target mod loaded to confirm probes fire. [CITED: PITFALL-11]

**Warning signs:** Probe log file is empty or contains only probe startup messages.

## Code Examples

### Example 1: GL State Probe at RenderType.clearRenderState()

```java
// Source: LWJGL 3 API + Context7 docs for GL20
@Priority(100) // Run before AW's default priority (1000)
@Mixin(RenderType.class)
public abstract class RenderTypeProbeMixin {

    private static final Logger PROBE_LOG = LogManager.getLogger("awveil-probe");
    
    @Inject(method = "clearRenderState", at = @At("HEAD"))
    private void onClearRenderState(CallbackInfo ci) {
        // Only probe if both AW and Veil are loaded
        if (!ModDetector.isAWLoaded() || !ModDetector.isVeilLoaded()) return;
        
        // Verify we are on the render thread
        if (!RenderSystem.isOnRenderThread()) return;
        
        long nanoTime = System.nanoTime();
        
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // 1. Read current program
            IntBuffer buf = stack.mallocInt(1);
            GL20.glGetIntegerv(GL20.GL_CURRENT_PROGRAM, buf);
            int currentProgram = buf.get(0);
            
            if (currentProgram == 0) {
                PROBE_LOG.info("{}\tclearRenderState\tNO_PROGRAM_BOUND", nanoTime);
                return;
            }
            
            // 2. Query AW uniform locations
            int mvLoc = GL20.glGetUniformLocation(currentProgram, "aw_ModelViewMatrix");
            int flagsLoc = GL20.glGetUniformLocation(currentProgram, "aw_MatrixFlags");
            int texLoc = GL20.glGetUniformLocation(currentProgram, "aw_TextureMatrix");
            
            // 3. Log probe data (TSV format for grep/awk)
            PROBE_LOG.info("{}\tclearRenderState\tprogram={}\taw_ModelViewMatrix={}\taw_MatrixFlags={}\taw_TextureMatrix={}",
                    nanoTime, currentProgram, mvLoc, flagsLoc, texLoc);
        }
    }
}
```

### Example 2: Mixin Plugin Gating

```java
// Source: SpongePowered Mixin IMixinConfigPlugin javadoc + verified NeoForge pattern
package com.example.awveilcompat.mixin;

import cpw.mods.modlauncher.api.LambdaExceptionUtils;
import cpw.mods.loading.LoadingModList;
import net.neoforged.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import java.util.List;
import java.util.Set;

public class AwVeilCompatMixinPlugin implements IMixinConfigPlugin {

    private Boolean awLoaded;
    private Boolean veilLoaded;

    @Override
    public void onLoad(String mixinPackage) {
        // Use LoadingModList, NOT ModList.get() -- it is null at plugin init time
        this.awLoaded = FMLLoader.getLoadingModList()
                .getModFileById("armourers_workshop") != null;
        this.veilLoaded = FMLLoader.getLoadingModList()
                .getModFileById("veil") != null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.contains(".mixin.aw.")) {
            return awLoaded;
        }
        if (mixinClassName.contains(".mixin.veil.")) {
            return veilLoaded;
        }
        return true; // core mixins always apply
    }

    // ---- Required stubs ----
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
```

### Example 3: Runtime Mod Detection Service

```java
// Source: NeoForge ModList API
package com.example.awveilcompat.detection;

import net.neoforged.fml.ModList;

public final class ModDetector {
    private ModDetector() {}

    public static final String AW_MOD_ID = "armourers_workshop";
    public static final String VEIL_MOD_ID = "veil";

    private static Boolean awCached;
    private static Boolean veilCached;

    /** Called during mod constructor to cache once. */
    public static void init() {
        awCached = ModList.get().isLoaded(AW_MOD_ID);
        veilCached = ModList.get().isLoaded(VEIL_MOD_ID);
    }

    public static boolean isAWLoaded() {
        return awCached != null ? awCached : ModList.get().isLoaded(AW_MOD_ID);
    }

    public static boolean isVeilLoaded() {
        return veilCached != null ? veilCached : ModList.get().isLoaded(VEIL_MOD_ID);
    }
}
```

### Example 4: build.gradle (ModDevGradle)

```groovy
// Source: neoforged/ModDevGradle deepwiki + verified MDK template
plugins {
    id 'java'
    id 'net.neoforged.moddev' version '2.0.80-beta'
}

base {
    archivesName = "awveilcompat"
}

java.toolchain.languageVersion = JavaLanguageVersion.of(21)

neoForge {
    version = "21.1.222"

    runs {
        client {
            client()
            gameDirectory = file("runs/client")
            property 'mixin.env.remapRefMap', 'true'
            property 'mixin.env.refMapRemappingFile', "${projectDir}/build/createSrgToMcp/output.srg"
        }
        server {
            server()
        }
    }

    mods {
        awveilcompat {
            sourceSet(sourceSets.main)
        }
    }
}

dependencies {
    annotationProcessor 'org.spongepowered:mixin:0.15.5:processor'
}
```

### Example 5: Probe Log Format (TSV for grep/awk)

```
# Fields: nanoTime | eventType | key=value [key=value...]
# AW-side probes:
1623456789012  clearRenderState  program=1234  aw_ModelViewMatrix=42  aw_MatrixFlags=7  aw_TextureMatrix=18
1623456789123  clearRenderState  program=0     aw_ModelViewMatrix=-1 aw_MatrixFlags=-1 aw_TextureMatrix=-1

# Veil-side probes:
1623456788000  shaderCompile     sourceHash=a1b2c3d4  program=1234
1623456788500  shaderBind        program=1234  type=COMPUTE
1623456789000  shaderBind        program=5678  type=RENDER
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| NeoGradle (legacy Forge Gradle) | ModDevGradle plugin | NeoForge 1.20.5+ | New DSL (`neoForge {}` block), auto-decompilation, no manual `setupDecompWorkspace`. |
| SRG/MCP mappings | Official Mojang mappings (via NeoForm) | Minecraft 1.17+ / NeoForge 1.20+ | Mixin targets use Mojang names. Access transformers use `net.minecraft.` not `net/minecraft/`. |
| Single mixin config | Multi-config per target domain | Community best practice (AcceleratedRendering, 2024) | Isolated failure domains, modular gating. |

**Deprecated/outdated:**
- **NeoGradle:** Use ModDevGradle instead. The `minecraft {}` block from ForgeGradle/NeoGradle does not work with ModDevGradle.
- **`mods.toml` without mixin section:** Older mods put mixin config only in JAR manifest. NeoForge 1.21 uses `[[mixins]]` in `neoforge.mods.toml`.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | AW mod ID is `armourers_workshop` [ASSUMED] | Architecture Patterns | If wrong ID, mixin plugin gates incorrectly -- AW mixins apply when AW is absent, or do not apply when AW is present. Verify via `ls D:\Software\CCode\AW\aw-source\src\main\resources\META-INF\neoforge.mods.toml` after cloning. |
| A2 | Veil mod ID is `veil` [ASSUMED] | Architecture Patterns | Same risk as A1. Verify by reading Veil's `neoforge.mods.toml` after cloning. |
| A3 | AW's Mixin targets `RenderType.clearRenderState()` at HEAD with default priority [ASSUMED] | Architecture Patterns | If AW uses a different priority, the probe may run after AW instead of before. Verify by reading AW source (search for `clearRenderState` and `@Priority`). |
| A4 | AW's uniform names are `aw_ModelViewMatrix`, `aw_MatrixFlags`, `aw_TextureMatrix` [ASSUMED] | Code Examples | If names differ, probe queries return -1 uniformly, producing unusable data. Verify by searching AW source for `glGetUniformLocation` or uniform name strings. |
| A5 | Clear `debugOpenGl=true` in FML config works for NeoForge 21.1.222 [ASSUMED] | Stack | If disabled, GL errors are silent. Stack research mentions it works for FancyModLoader >=7.0.6. Add it as a run config property. |
| A6 | `System.nanoTime()` provides sufficient cross-probe timeline correlation on the same machine [ASSUMED] | Architecture | nanoTime is monotonic per JVM but may drift between probe write calls. For nanosecond-level correlation this may introduce skew. If correlation is noisy, switch to `Instant.now().toEpochMilli()` with microsecond counters. |

## Open Questions

1. **What is AW's @Priority value on its clearRenderState mixin?**
   - What we know: Both the probe and AW's mixin inject at `clearRenderState()` HEAD. Default Mixin priority is 1000. Lower = runs first.
   - What's unclear: AW's actual priority, which determines whether `@Priority(100)` is sufficient for the probe to run first.
   - Recommendation: Clone AW source and grep for `@Priority` and `clearRenderState`. Set probe priority to 100 (well below default) as a reasonable default. Verify by checking probe output order.

2. **What is the exact LWJGL class path for matrix state reading on Minecraft 1.21.1?**
   - What we know: Minecraft uses LWJGL 3.3.3. Matrices are uploaded via `glUniformMatrix4fv`. Matrix state can be read via `GL20.glGetUniformfv(program, location, buf)` for uniform values.
   - What's unclear: Whether reading uniform values back (glGetUniformfv) introduces a pipeline stall significant enough to affect the probe measurement.
   - Recommendation: Accept the pipeline stall -- probes are for investigation, not production. Document the stall as a caveat.

3. **What is the exact callback signature of `RenderTypeStageRegistry.addStage()` and `DirectShaderCompiler.compile()`?**
   - What we know: API docs from Veil GitHub pages show the signatures (see Sources).
   - What's unclear: The exact parameter types for the Mixin injection descriptors.
   - Recommendation: Decompile or clone Veil source to get exact method signatures before writing Mixin injection code.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JDK 21 | NeoForge compilation + runtime | Yes | zulu21.32.17-ca-jdk21.0.2 | -- [CITED: CLAUDE.md] |
| Gradle Wrapper | Build | To be generated by MDK template | -- | -- |
| Git | Clone AW/Veil repos | To be checked | -- | Manual ZIP download if no git CLI |
| IntelliJ IDEA | Development | To be checked | -- | Any text editor for source analysis |
| RenderDoc | GPU debugging | To be checked | -- | Apitrace (cross-platform) |
| AW source repo | Identifying injection points | Not yet cloned | -- | Decompile JAR (D-01 fallback) |
| Veil source repo | Identifying injection points | Not yet cloned | -- | Decompile JAR (D-01 fallback) |

**Missing dependencies with no fallback:**
- AW and Veil source repos (must be cloned as Phase 1 first task -- no meaningful probe design without them)

**Missing dependencies with fallback:**
- RenderDoc: Apitrace for cross-platform GL trace, or skip GPU debugging for Phase 1 (probes provide sufficient data)

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | No | -- (Phase 1 is investigation only, no auth) |
| V3 Session Management | No | -- |
| V4 Access Control | No | -- |
| V5 Input Validation | No | -- (probes read GL state, do not process untrusted input) |
| V6 Cryptography | No | -- |

**Phase 1 threat analysis:** No user input, no network, no data storage, no authentication. The sole risk is:

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| GL state read from wrong thread causing JVM crash | Denial of Service | Check `RenderSystem.isOnRenderThread()` before GL calls |
| Mixin injection crashing game | Denial of Service | `require = 0` + plugin gating + `@Pseudo` -- three layers of fail-safe |

No further security controls needed for Phase 1. Security-relevant controls (input validation for `/awv-compat capture` command) will be needed in Phase 4.

## Sources

### Primary (HIGH confidence)

- **NeoForge MDK-1.21.1-ModDevGradle** -- Official MDK template for NeoForge 1.21.1, ModDevGradle setup. [VERIFIED: github.com/NeoForgeMDKs/MDK-1.21.1-ModDevGradle]
- **SpongePowered Mixin GitHub** -- Mixin framework, IMixinConfigPlugin, @Pseudo, @Priority docs. [VERIFIED: github.com/SpongePowered/Mixin]
- **LWJGL 3.3.3** -- GL20 API for glGetIntegerv, glGetUniformLocation, glGetActiveUniform. [VERIFIED: lwjgl.org]
- **Veil API docs (foundrymc.github.io/Veil/)** -- DirectShaderCompiler, RenderTypeStageRegistry class docs. [VERIFIED: foundrymc.github.io/Veil/]
- **NeoForge Docs (docs.neoforged.net)** -- ModDevGradle toolchain, mods.toml format, Access Transformers. [VERIFIED: docs.neoforged.net]
- **FancyModLoader DeepWiki** -- Mixin integration, LoadingModList, deferred mixin registration. [VERIFIED: deepwiki.com/neoforged/FancyModLoader]

### Secondary (MEDIUM confidence)

- **Iris Issue #2786** -- AW developer reporting clearRenderState breakage with Iris 1.8.12. Confirms AW's Mixin target. [CITED: github.com/IrisShaders/Iris/issues/2786]
- **Veil Wiki - RenderTypeStage** -- RenderTypeStageRegistry.addStage() usage patterns. [CITED: github-wiki-see.page/m/FoundryMC/Veil/wiki/RenderTypeStage]
- **AcceleratedRendering DeepWiki** -- Multi-mixin-config reference implementation (13 configs). [CITED: deepwiki.com/Argon4W/AcceleratedRendering/8.2-mixin-architecture]
- **Fallen-Breath/conditional-mixin** -- RestrictiveMixinConfigPlugin pattern, @Restriction/@Condition annotation approach. [CITED: github.com/Fallen-Breath/conditional-mixin]
- **Forge Forums #122500** -- LoadingModList.getModFileById() as ModList alternative during early init. [CITED: forums.minecraftforge.net]
- **NeoForge Issue #2636** -- Cascading mixin failure masking root cause. [CITED: github.com/neoforged/NeoForge/issues/2636]

### Tertiary (LOW confidence)

- Various forum posts about AW mod internals -- unverified source structures.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - NeoForge MDK, LWJGL, and Mixin versions verified via registry and official docs.
- Architecture: MEDIUM - AW/Veil injection point details depend on exact source structure. Assumptions A3 and A4 need source verification.
- Pitfalls: HIGH - Multi-sourced from issue trackers, official docs, and modding community experience.

**Research date:** 2026-04-30
**Valid until:** 2026-06-01 (30 days for NeoForge/Minecraft toolchain versions)
