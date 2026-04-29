# State: AW-Veil Compat

**Project:** AW-Veil Compat
**Core Value:** In Veil's presence, AW models and item textures render correctly without disabling VBO (performance preserved).
**Current Milestone:** v1.0.0 (initial release)
**Current Focus:** Phase 1 — Investigation & Foundation

## Position

| Dimension | Value |
|-----------|-------|
| Phase | 1 - Investigation & Foundation |
| Plan | 01 completed |
| Status | Plan 01 executed (scaffold + detection) |
| Progress | [####                ] 20% |

## Performance Metrics

| Metric | Threshold | Current |
|--------|-----------|---------|
| FPS regression (5+ AW-equipped entities) | <5% acceptable, >10% blocking | Not measured |

## Accumulated Context

### Decisions

| # | Decision | Rationale | Date |
|---|----------|-----------|------|
| 1 | Dedicated shader program fix (Pattern 1 from research) | Eliminates root cause (program confusion) rather than treating symptoms. Robust against any shader-modifying mod (Veil, Iris, OptiFine, etc.). | 2026-04-30 |
| 2 | Investigation-first approach | 10 failed AW-side attempts prove coding without understanding Veil's pipeline is wasted effort. Must have concrete GL state evidence before writing fix code. | 2026-04-30 |
| 3 | Never use @Overwrite/@Redirect in rendering pipeline targets | Iris, Veil, Sodium, Embeddium already target LevelRenderer, ShaderInstance, and RenderType with Mixins. @Overwrite has last-writer-wins semantics; @Redirect does not support nesting. Use @Inject (HEAD/RETURN) or @WrapOperation (MixinExtras). | 2026-04-30 |
| 4 | Always use RenderSystem for GL state changes | Calling raw GL without going through RenderSystem/GlStateManager desyncs state cache from actual GPU state. glPushAttrib/glPopAttrib must never be used. | 2026-04-30 |
| 5 | Use ModDevGradle 2.0.141 (latest) over unavavailable 2.0.80-beta | Version specified in research plan does not exist in the NeoForge Maven repository. Latest 2.0.141 is used instead. Also, `property()` renamed to `systemProperty()` in the new API. | 2026-04-30 |
| 6 | Remove Mixin annotation processor dependency | ModDevGradle handles Mixin annotation processing automatically. The `org.spongepowered:mixin:0.15.5:processor` artifact is not published to the standard repos. | 2026-04-30 |

### Key TODOs

- [x] Research-phase: decompile AW to identify exact class names, method signatures, and injection points for VBO flush
- [x] Research-phase: decompile Veil to identify exact Mixin injection points in RenderTypeStageRegistry and DirectShaderCompiler
- [x] Phase 1: create PHASE PLAN
- [x] Phase 1: implement mod scaffold with runtime detection (DETECT-01, DETECT-02) and version lock (VERSION-01) — Plan 01
- [ ] Phase 1: design and build GL state debug probes (GlStateTracker, GlStateDump, ProgramInspector) — Plan 02

### Blocker Status

| Blocker | Phase | Status | Notes |
|---------|-------|--------|-------|
| Need exact AW/Veil class/method names | 1 | Resolved by research | Research produced comprehensive analysis with code examples |
| Need gradle wrapper | 1 | To be generated in execution | Plan 01 Task 2 creates the wrapper |
| Need git CLI for cloning | 1 | Available | git 2.54.0 on PATH |

## Session Continuity

- 2026-04-30: Phase 1 executed Plan 01 — cloned AW/Veil source, built mod scaffold with runtime detection (3 mixin configs, MixinPlugin, ModDetector), NeoForge 21.1.222 version lock. Key deviation: ModDevGradle 2.0.141 (not 2.0.80-beta). Key finding: AW targets RenderStateShard at TAIL, not RenderType at HEAD.
- Next: Execute Plan 02 (GL state debug probes)
