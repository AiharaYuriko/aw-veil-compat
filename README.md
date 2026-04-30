# AW-Veil Compat

NeoForge 1.21.1 compatibility mod that fixes rendering conflicts between [Armourer's Workshop](https://github.com/SAGESSE-CN/Armourers-Workshop) VBO rendering and the [Veil](https://github.com/FoundryMC/Veil) shader framework.

## The Problem

When Veil (or mods bundling it, like Sable) is loaded alongside Armourer's Workshop, AW's custom VBO rendering breaks: models stretch, fixate near the player, and item textures become invisible.

The root cause: AW's VBO renderer "borrows" the currently bound GL shader program and injects its custom uniforms (`aw_ModelViewMatrix`, `aw_MatrixFlags`, etc.). When Veil replaces vanilla shaders with its own compiled programs, those programs don't declare AW's uniforms — `glGetUniformLocation()` returns -1 and all AW uniforms are silently dropped.

The common workaround — disabling AW's VBO rendering (`ModDebugger.vbo = 2`) — fixes the visuals but causes significant GPU performance degradation.

## The Fix

This mod intercepts Veil's `VanillaShaderProcessor` — the pipeline Veil uses to compile vanilla Minecraft shaders — and injects AW's vertex attribute transformations at the GLSL source level, **before** compilation. Only the `Position` attribute is rewired through `aw_ModelViewMatrix`; color, texture, and normal attributes remain untouched.

This approach:
- Mirrors AW's official [Iris compatibility strategy](https://github.com/SAGESSE-CN/Armourers-Workshop/blob/develop/common/src/main/java/moe/plushie/armourers_workshop/compatibility/mixin/ShaderIrisMixin.java)
- Has **zero runtime overhead** — all work happens at shader compile time
- Is compatible with Iris, Sodium, and any other shader-modifying mod

## Installation

1. Download `awveilcompat-<version>.jar` from [Releases](../../releases)
2. Place it in your Minecraft `mods` folder
3. Requires NeoForge 21.1.x and Armourer's Workshop 3.2.7+

## Known Limitations

- **Thin/flat AW items may flicker** at certain camera angles. This is a pre-existing interaction between AW's CPU-side frustum culling and Veil's rendering pipeline. Iris shader packs exhibit the same behavior via a different mechanism.

## Compatibility

| Mod | Status |
|-----|--------|
| Armourer's Workshop 3.2.7-beta | ✓ |
| Veil 4.0.0 (bundled with Sable 1.2.2) | ✓ |
| Iris 1.8.8 | ✓ |
| Sodium 0.6.13 | ✓ |
| Create 6.0.10 | ✓ |

## Build from Source

```bash
git clone https://github.com/AiharaYuriko/aw-veil-compat.git
cd aw-veil-compat
set JAVA_HOME=<path-to-jdk21>
./gradlew build
# Output: build/libs/awveilcompat-<version>.jar
```

## Credits

DeepSeekV4pro & MisakaSteve

## License

MIT
