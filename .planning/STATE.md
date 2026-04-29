# State: AW-Veil Compat

**Project:** AW-Veil Compat
**Core Value:** In Veil's presence, AW models and item textures render correctly without disabling VBO (performance preserved).
**Current Milestone:** v1.0.0 (initial release)
**Current Focus:** Phase 1 — Investigation & Foundation

## Position

| Dimension | Value |
|-----------|-------|
| Phase | 1 - Investigation & Foundation |
| Plan | Plans created |
| Status | Plans ready for execution |
| Progress | [##                  ] 10% |

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

### Key TODOs

- [x] Research-phase: decompile AW to identify exact class names, method signatures, and injection points for VBO flush
- [x] Research-phase: decompile Veil to identify exact Mixin injection points in RenderTypeStageRegistry and DirectShaderCompiler
- [x] Phase 1: create PHASE PLAN
- [ ] Phase 1: implement mod scaffold with runtime detection (DETECT-01, DETECT-02) and version lock (VERSION-01) — Plan 01
- [ ] Phase 1: design and build GL state debug probes (GlStateTracker, GlStateDump, ProgramInspector) — Plan 02

### Blocker Status

| Blocker | Phase | Status | Notes |
|---------|-------|--------|-------|
| Need exact AW/Veil class/method names | 1 | Resolved by research | Research produced comprehensive analysis with code examples |
| Need gradle wrapper | 1 | To be generated in execution | Plan 01 Task 2 creates the wrapper |
| Need git CLI for cloning | 1 | Available | git 2.54.0 on PATH |

## Session Continuity

- 2026-04-30: Phase 1 context gathered — 01-CONTEXT.md created with 6 implementation decisions
- 2026-04-30: Research complete — 01-RESEARCH.md with standard stack, architecture patterns, pitfalls
- 2026-04-30: Phase 1 planned — 2 plans created (01-01: scaffold + detection, 01-02: probes + correlation)
- Source strategy: Clone SAGESSE-CN/AW + FoundryMC/Veil, fallback to JAR decompilation
- Probe strategy: Dual-side GL state probes (AW clearRenderState + Veil RenderTypeStage/DirectShaderCompiler) with nanosecond-timestamped log comparison
- Next action: run `/gsd-execute-phase 1` to execute Phase 1
