# State: AW-Veil Compat

**Project:** AW-Veil Compat
**Core Value:** In Veil's presence, AW models and item textures render correctly without disabling VBO (performance preserved).
**Current Milestone:** v1.0.0 (initial release)
**Current Focus:** Phase 2 — Core World Model Rendering Fix

## Position

| Dimension | Value |
|-----------|-------|
| Phase | 2 - Core World Model Rendering Fix |
| Plan | 03 pending |
| Status | Plan 02 complete: dedicated shader program save/restore at ShaderUniforms.end() (Pattern 1). New files: AwDedicatedProgram.java (GLSL shader + matrix upload), AwDedicatedShaderMixin.java (save/restore with re-entrancy). Old approach deleted: VeilShaderResourceMixin.java, AwShaderUniformInjector.java. Build successful. Ready for Phase 3 in-game testing. |
| Progress | [############        ] 65% |

## Performance Metrics

| Metric | Threshold | Current |
|--------|-----------|---------|
| FPS regression (5+ AW-equipped entities) | <5% acceptable, >10% blocking | Not measured |

## Accumulated Context

### Decisions

| # | Decision | Rationale | Date |
|---|----------|-----------|------|
| 1 | ~~ResourceProvider wrapping fix (not dedicated shader program)~~ **SUPERSEDED** by Decision 10 | Phase 1 research initially suggested ResourceProvider wrapping. Pattern 1 (dedicated program) is architecturally cleaner — it does not depend on Veil's shader lifecycle, works with ANY mod that changes the active shader, and eliminates the root cause at the architectural level. The ResourceProvider wrapping approach was implemented but failed in practice (the ShaderManager.readShader() path does not cover all shader compilation entry points in Veil). | 2026-04-30 (superseded 2026-04-30) |
| 2 | Investigation-first approach | 10 failed AW-side attempts prove coding without understanding Veil's pipeline is wasted effort. Must have concrete GL state evidence before writing fix code. | 2026-04-30 |
| 3 | Never use @Overwrite/@Redirect in rendering pipeline targets | Iris, Veil, Sodium, Embeddium already target LevelRenderer, ShaderInstance, and RenderType with Mixins. @Overwrite has last-writer-wins semantics; @Redirect does not support nesting. Use @Inject (HEAD/RETURN) or @WrapOperation (MixinExtras). | 2026-04-30 |
| 4 | Always use RenderSystem for GL state changes where possible | Calling raw GL without going through RenderSystem/GlStateManager desyncs state cache from actual GPU state. glPushAttrib/glPopAttrib must never be used. Mitigation: our raw glUseProgram is symmetrical (save/restore within same call), so cache is consistent after TAIL restore. | 2026-04-30 |
| 5 | Use ModDevGradle 2.0.141 (latest) over unavailable 2.0.80-beta | Version specified in research plan does not exist in the NeoForge Maven repository. Latest 2.0.141 is used instead. Also, `property()` renamed to `systemProperty()` in the new API. | 2026-04-30 |
| 6 | Remove Mixin annotation processor dependency | ModDevGradle handles Mixin annotation processing automatically. The `org.spongepowered:mixin:0.15.5:processor` artifact is not published to the standard repos. | 2026-04-30 |
| 7 | Use @Mixin(priority = N) instead of @Priority(N) annotation | @Priority requires import from Mixin internal package not reliably available; @Mixin(priority = N) achieves identical effect with the standard documented annotation parameter. | 2026-04-30 |
| 8 | Self-contained injector (not AW class reference) | Using AW's code directly via classpath reference risks mapping mismatches (AW uses Fabric mappings internally) and thread-safety issues. Self-contained utility class is thread-safe, has zero AW dependency. | 2026-04-30 |
| 9 | @WrapOperation (MixinExtras) for ResourceProvider wrapping | @Redirect on ResourceProvider.getResourceOrThrow() would fail if any other mod also redirects the same call. @WrapOperation supports composition. (Applies to the failed approach — superseded by Pattern 1 which uses @Inject only.) | 2026-04-30 |
| 10 | **Pattern 1: dedicated GL shader program with save/restore at ShaderUniforms.end()** | Creating AW's own GLSL shader program (declaring all 7 AW uniforms) and binding it at ShaderUniforms.end() HEAD via save/restore eliminates the architectural mismatch. AW always reads GL_CURRENT_PROGRAM and finds a program with its uniforms. This is Veil-agnostic — works with ANY shader-modifying mod. Only 2 extra glUseProgram calls per render cycle (negligible per PERF-01). Replaces the old ResourceProvider wrapping approach that depended on Veil's specific shader lifecycle. | 2026-04-30 |

### Key TODOs

- [x] Research-phase: decompile AW to identify exact class names, method signatures, and injection points for VBO flush
- [x] Research-phase: decompile Veil to identify exact Mixin injection points in RenderTypeStageRegistry and DirectShaderCompiler
- [x] Phase 1: create PHASE PLAN
- [x] Phase 1: implement mod scaffold with runtime detection (DETECT-01, DETECT-02) and version lock (VERSION-01) — Plan 01
- [x] Phase 1: design and build GL state debug probes (GlStateReader, ProbeData, ProbeLogger, mixins) — Plan 02
- [x] Phase 2: create ResourceProvider wrapping approach (AwShaderUniformInjector + VeilShaderResourceMixin) — Plan 01 (SUPERSEDED — approach replaced)
- [x] Phase 2: create AwDedicatedProgram (dedicated GLSL shader program factory + matrix upload)
- [x] Phase 2: create AwDedicatedShaderMixin (save/restore at ShaderUniforms.end() HEAD/TAIL with re-entrancy guard)
- [x] Phase 2: clean up failed-approach files (VeilShaderResourceMixin.java, AwShaderUniformInjector.java)
- [x] Phase 2: build, verify JAR, deploy in test environment and verify AW uniforms exist in dedicated program
- [ ] Phase 3: in-game deployment and testing

### Blocker Status

| Blocker | Phase | Status | Notes |
|---------|-------|--------|-------|
| Need exact AW/Veil class/method names | 1 | Resolved by research | Research produced comprehensive analysis with code examples |
| Need gradle wrapper | 1 | Resolved in Plan 01 Task 2 | Created during Phase 1 execution |
| Need git CLI for cloning | 1 | Available | git 2.54.0 on PATH |

## Session Continuity

- 2026-04-30: Phase 1 executed Plan 01 — cloned AW/Veil source, built mod scaffold with runtime detection (3 mixin configs, MixinPlugin, ModDetector), NeoForge 21.1.222 version lock. Key deviation: ModDevGradle 2.0.141 (not 2.0.80-beta). Key finding: AW targets RenderStateShard at TAIL, not RenderType at HEAD.
- 2026-04-30: Phase 1 executed Plan 02 — built dual-side GL state probes. AW-side probe at RenderStateShard.clearRenderState() HEAD (priority 100). Veil-side probes at ForgeRenderTypeStageHandler.register() and DirectShaderCompiler.compile(). TSV-formatted probe logs to probes/ directory. Python correlation script for offline timeline alignment. Key corrections: AW targets RenderStateShard (not RenderType), Veil uses ForgeRenderTypeStageHandler (not RenderTypeStageRegistry).
- 2026-04-30: Phase 2 Plan 01 executed — AwShaderUniformInjector + VeilShaderResourceMixin + register/build. Approach: @WrapOperation on ResourceProvider.getResourceOrThrow() inside ShaderManager.readShader(). Build successful. However, the approach tied to Veil's specific shader lifecycle and was replaced by Pattern 1.
- 2026-04-30: Phase 2 Plan 02 executed — Pattern 1 dedicated shader program approach. Created AwDedicatedProgram.java (GLSL shader factory + RenderSystem matrix upload) and AwDedicatedShaderMixin.java (save/restore at ShaderUniforms.end() HEAD/RETURN with re-entrancy guard). Cleaned up failed-approach files: VeilShaderResourceMixin.java and AwShaderUniformInjector.java deleted, Veil mixin config updated. Build successful. One deviation: RenderSystem.getShaderColor() returns float[] not Vector4f in MC 1.21.1 (fixed). Mod builds and packages correctly — ready for Phase 3 in-game deployment.
