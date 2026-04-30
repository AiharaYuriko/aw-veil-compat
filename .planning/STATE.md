# State: AW-Veil Compat

**Project:** AW-Veil Compat
**Core Value:** In Veil's presence, AW models and item textures render correctly without disabling VBO (performance preserved).
**Current Milestone:** v1.0.0 (initial release)
**Current Focus:** Phase 2 — Core World Model Rendering Fix

## Position

| Dimension | Value |
|-----------|-------|
| Phase | 2 - Core World Model Rendering Fix |
| Plan | 01 planned |
| Status | Plan 01 created: shader uniform injection via @WrapOperation |
| Progress | [##########          ] 45% |

## Performance Metrics

| Metric | Threshold | Current |
|--------|-----------|---------|
| FPS regression (5+ AW-equipped entities) | <5% acceptable, >10% blocking | Not measured |

## Accumulated Context

### Decisions

| # | Decision | Rationale | Date |
|---|----------|-----------|------|
| 1 | ResourceProvider wrapping fix (not dedicated shader program) | Phase 1 research revealed AW uniforms just need to EXIST in Veil-compiled programs. Wrapping ResourceProvider during ShaderManager.readShader() reuses AW's existing pattern (ShaderIrisMixin), is compile-time only (zero per-frame cost), and is simpler than a dedicated program approach. | 2026-04-30 |
| 2 | Investigation-first approach | 10 failed AW-side attempts prove coding without understanding Veil's pipeline is wasted effort. Must have concrete GL state evidence before writing fix code. | 2026-04-30 |
| 3 | Never use @Overwrite/@Redirect in rendering pipeline targets | Iris, Veil, Sodium, Embeddium already target LevelRenderer, ShaderInstance, and RenderType with Mixins. @Overwrite has last-writer-wins semantics; @Redirect does not support nesting. Use @Inject (HEAD/RETURN) or @WrapOperation (MixinExtras). | 2026-04-30 |
| 4 | Always use RenderSystem for GL state changes | Calling raw GL without going through RenderSystem/GlStateManager desyncs state cache from actual GPU state. glPushAttrib/glPopAttrib must never be used. | 2026-04-30 |
| 5 | Use ModDevGradle 2.0.141 (latest) over unavavailable 2.0.80-beta | Version specified in research plan does not exist in the NeoForge Maven repository. Latest 2.0.141 is used instead. Also, `property()` renamed to `systemProperty()` in the new API. | 2026-04-30 |
| 6 | Remove Mixin annotation processor dependency | ModDevGradle handles Mixin annotation processing automatically. The `org.spongepowered:mixin:0.15.5:processor` artifact is not published to the standard repos. | 2026-04-30 |
| 7 | Use @Mixin(priority = N) instead of @Priority(N) annotation | @Priority requires import from Mixin internal package not reliably available; @Mixin(priority = N) achieves identical effect with the standard documented annotation parameter. | 2026-04-30 |
| 8 | Self-contained copy of AW ShaderPreprocessor (not runtime reference) | Using AW's ShaderPreprocessor directly via classpath reference risks mapping mismatches (AW uses Fabric mappings internally) and thread-safety issues (ShaderPreprocessor.SourceBuilder references ModConfig.Client.enableShaderDebug). Self-contained AwShaderUniformInjector is thread-safe, has zero AW dependency, and is more robust. | 2026-04-30 |
| 9 | @WrapOperation (MixinExtras) for ResourceProvider wrapping | @Redirect on ResourceProvider.getResourceOrThrow() would fail if any other mod also redirects the same call. @WrapOperation supports composition (multiple wrappers chain correctly). This is essential for a compatibility mod that must coexist with other Veil-integration mods. | 2026-04-30 |

### Key TODOs

- [x] Research-phase: decompile AW to identify exact class names, method signatures, and injection points for VBO flush
- [x] Research-phase: decompile Veil to identify exact Mixin injection points in RenderTypeStageRegistry and DirectShaderCompiler
- [x] Phase 1: create PHASE PLAN
- [x] Phase 1: implement mod scaffold with runtime detection (DETECT-01, DETECT-02) and version lock (VERSION-01) — Plan 01
- [x] Phase 1: design and build GL state debug probes (GlStateReader, ProbeData, ProbeLogger, mixins) — Plan 02
- [ ] Phase 2: create AwShaderUniformInjector (self-contained GLSL transformer)
- [ ] Phase 2: create VeilShaderResourceMixin (@WrapOperation on ResourceProvider.getResourceOrThrow())
- [ ] Phase 2: register mixin, build, verify JAR
- [ ] Phase 2: deploy in test environment and verify AW uniforms exist in Veil-compiled programs

### Blocker Status

| Blocker | Phase | Status | Notes |
|---------|-------|--------|-------|
| Need exact AW/Veil class/method names | 1 | Resolved by research | Research produced comprehensive analysis with code examples |
| Need gradle wrapper | 1 | Resolved in Plan 01 Task 2 | Created during Phase 1 execution |
| Need git CLI for cloning | 1 | Available | git 2.54.0 on PATH |

## Session Continuity

- 2026-04-30: Phase 1 executed Plan 01 — cloned AW/Veil source, built mod scaffold with runtime detection (3 mixin configs, MixinPlugin, ModDetector), NeoForge 21.1.222 version lock. Key deviation: ModDevGradle 2.0.141 (not 2.0.80-beta). Key finding: AW targets RenderStateShard at TAIL, not RenderType at HEAD.
- 2026-04-30: Phase 1 executed Plan 02 — built dual-side GL state probes. AW-side probe at RenderStateShard.clearRenderState() HEAD (priority 100). Veil-side probes at ForgeRenderTypeStageHandler.register() and DirectShaderCompiler.compile(). TSV-formatted probe logs to probes/ directory. Python correlation script for offline timeline alignment. Key corrections: AW targets RenderStateShard (not RenderType), Veil uses ForgeRenderTypeStageHandler (not RenderTypeStageRegistry).
- 2026-04-30: Phase 2 planned — single plan 02-01: AwShaderUniformInjector + VeilShaderResourceMixin + register/build. Approach: @WrapOperation on ResourceProvider.getResourceOrThrow() inside ShaderManager.readShader() to inject AW uniforms into .vsh files at shader-source level. Self-contained injector (zero AW dependency). Thread-safe, compile-time only.
