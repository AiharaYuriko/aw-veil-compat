# State: AW-Veil Compat

**Project:** AW-Veil Compat
**Core Value:** In Veil's presence, AW models and item textures render correctly without disabling VBO (performance preserved).
**Current Milestone:** v1.0.0 (initial release)
**Current Focus:** Phase 1 — Investigation & Foundation

## Position

| Dimension | Value |
|-----------|-------|
| Phase | 1 - Investigation & Foundation |
| Plan | Not started |
| Status | Not started |
| Progress | [                    ] 0% |

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

- [ ] Research-phase: decompile AW to identify exact class names, method signatures, and injection points for VBO flush
- [ ] Research-phase: decompile Veil to identify exact Mixin injection points in RenderTypeStageRegistry and DirectShaderCompiler
- [ ] Phase 1: implement mod scaffold with runtime detection (DETECT-01, DETECT-02) and version lock (VERSION-01)
- [ ] Phase 1: design and build GL state debug probes (GlStateTracker, GlStateDump, ProgramInspector)

### Blockers

None currently identified.

## Session Continuity

- ROADMAP.md created with 4 phases covering all 10 v1 requirements
- Coverage validated: 10/10 requirements mapped, 0 orphans
- Key architectural decision: dedicated shader program (Pattern 1) is the chosen fix approach
- Next action: execute research-phase for Phase 1 to identify exact AW/Veil injection targets via decompilation
- Phase ordering rationale: investigation before fix (Phase 1 before Phase 2), world models before items (Phase 2 before Phase 3), hardening after everything works (Phase 4 last)
