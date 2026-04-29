# Source Repo Verification - Plan 01-01 Task 1

## AW (Armourer's Workshop)

| Property | Value |
|----------|-------|
| **Repo** | SAGESSE-CN/Armourers-Workshop (develop branch) |
| **Path** | D:\Software\CCode\AW\aw-source\ |
| **Mod ID** | `armourers_workshop` (from forge/src/main/resources/META-INF/mods.toml) |
| **Target MC** | 1.21.1 (via versions/21.1/build.gradle -> setup.gradle) |
| **Loader** | Standard Forge (not NeoForge - uses mods.toml not neoforge.mods.toml) |

### clearRenderState Mixin

AW's compatibility mixin targets `RenderStateShard.class` (NOT `RenderType.class`):

- **File**: `versions/library/common/src/main/java/moe/plushie/armourers_workshop/compat/mixin/RenderTypeMixin.java`
- **Injection point**: `@Inject(method = "clearRenderState", at = @At("TAIL"))`
- **Injection point (setup)**: `@Inject(method = "setupRenderState", at = @At("HEAD"))`
- **Priority**: Not specified (uses default Mixin priority = 1000)
- **Version gate**: `@Available("[16, 26)")` (MC 1.16 through 1.25.x)

### Uniform Names (verified from source)

| Uniform | Type | Source File |
|---------|------|-------------|
| `aw_ModelViewMatrix` | mat4 | `ShaderPreprocessor.java`, `AbstractShaderUniformState.java` |
| `aw_MatrixFlags` | int | `ShaderPreprocessor.java`, `AbstractShaderUniformState.java` |
| `aw_TextureMatrix` | mat4 | `ShaderPreprocessor.java`, `AbstractShaderUniformState.java` |

### Uniform Operations

AW uses `ShaderUniform` class which calls `GL20.glGetUniformLocation(program, name)` and `GL20.glUniformMatrix4fv()` for matrix uniforms.

---

## Veil (FoundryMC)

| Property | Value |
|----------|-------|
| **Repo** | FoundryMC/Veil (1.21 branch) |
| **Path** | D:\Software\CCode\AW\veil-source\ |
| **Mod ID** | `veil` (from gradle.properties + `Veil.MODID`) |
| **Target MC** | 1.21 (branch name) |

### Key Classes (deviations from research assumptions)

The research assumed a class `RenderTypeStageRegistry` with `addStage()` method. The actual Veil architecture uses different naming:

| Research Assumption | Actual Class | Method |
|--------------------|-------------|--------|
| `RenderTypeStageRegistry.addStage()` | `ForgeRenderTypeStageHandler.register()` | `register(@Nullable RenderLevelStageEvent.Stage stage, RenderType renderType)` |
| `DirectShaderCompiler.compile()` | `DirectShaderCompiler` | `compile(int type, VeilShaderSource source)` and `compile(int type, ResourceLocation path)` |
| -- | `ShaderProgramShard` | Extends `ShaderStateShard`; overrides `setupRenderState()` to call `VeilRenderSystem.setShader()` |
| -- | `ShaderProgramImpl` | Main program with `compile()`, `bind()`, `recompile()`, `setActiveBuffers()` |

### DirectShaderCompiler Signature

```java
public class DirectShaderCompiler implements ShaderCompiler {
    // Compile from resource path
    public CompiledShader compile(int type, ResourceLocation path) throws IOException, ShaderException;
    // Compile from source
    public CompiledShader compile(int type, VeilShaderSource source) throws ShaderException;
}
```

Where `type` is a GL shader type constant (e.g., `GL20C.GL_FRAGMENT_SHADER`, `GL20C.GL_VERTEX_SHADER`).

### CachedShaderCompiler

Extends `DirectShaderCompiler` with caching (`Int2ObjectMap<CompiledShader>`).

### ForgeRenderTypeStageHandler.register()

```java
public static synchronized void register(@Nullable RenderLevelStageEvent.Stage stage, RenderType renderType)
```

Registers custom render types to be rendered at specific stage events.

### ShaderProgramShard

```java
public class ShaderProgramShard extends RenderStateShard.ShaderStateShard {
    @Override
    public void setupRenderState() {
        VeilRenderSystem.setShader(this.shader);  // Replaces the active shader
    }
}
```

This is the mechanism by which Veil replaces the active shader program. It extends `ShaderStateShard` and is used as part of Veil's custom `RenderType` pipeline, replacing the shader at `setupRenderState()` time.
