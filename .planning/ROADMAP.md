# Roadmap: AW-Veil Compat

**Core Value:** In Veil's presence, AW models and item textures render correctly without disabling VBO (performance preserved).

**Granularity:** Coarse
**Mode:** YOLO

## Phases

- [ ] **Phase 1: Investigation & Foundation** - Set up mod scaffold with runtime detection, investigate exact GL state difference at AW's render point with/without Veil, map injection targets
- [ ] **Phase 2: Core World Model Rendering Fix** - Replace "borrow current program" pattern with dedicated AW shader program; fix entity model rendering with VBO performance preserved
- [ ] **Phase 3: Item Rendering & Iris Coexistence** - Fix item rendering code path for inventory/hotbar and validate coexistence with Iris shader packs
- [ ] **Phase 4: Hardening & Diagnostics** - Cross-platform testing, diagnostic tooling (overlay, logging, capture command), production cleanup, and release

## Phase Details

### Phase 1: Investigation & Foundation
**Goal**: Understand exact GL state difference at AW's render point with/without Veil, and set up mod scaffold with safe runtime detection.
**Depends on**: Nothing (first phase)
**Requirements**: DETECT-01, DETECT-02, VERSION-01
**Success Criteria** (what must be TRUE):
  1. Mod loads without crash when AW and/or Veil are absent — graceful no-op with no Mixin errors or log spam
  2. With Veil loaded, GL state probe confirms the active shader program ID at AW's render point differs from the no-Veil baseline, validating the root cause hypothesis with concrete evidence
  3. Exact AW class names, method signatures, and injection points for VBO flush are identified from source decompilation
  4. Exact Veil injection points (RenderTypeStageRegistry, DirectShaderCompiler) are mapped
  5. Conflict matrix of Mixin targets already used by Iris/Veil/Sodium on the same classes is documented
  6. Mod JAR targets NeoForge 1.21.1 only (21.1.222) — version locked in mods.toml and build.gradle
**Plans**: TBD
**Research flag**: Phase 1 needs `/gsd-research-phase` to decompile AW and Veil source code for precise injection targets.

### Phase 2: Core World Model Rendering Fix
**Goal**: AW equipment models on entities render correctly with Veil loaded by replacing the "borrow current program" pattern with a dedicated AW shader program, while preserving VBO rendering performance.
**Depends on**: Phase 1 (must know exact injection targets and GL state behavior)
**Requirements**: RENDER-01, PERF-01
**Success Criteria** (what must be TRUE):
  1. AW equipment models on entities render correctly with Veil loaded — not stretched, not fixed near player, rotate properly with camera
  2. FPS with 5+ AW-equipped entities stays within 5% of the non-VBO fallback baseline (VBO rendering path preserved, no fallback to non-VBO)
  3. Dedicated AW shader program binding/unbinding does not corrupt subsequent vanilla/Veil rendering — no flicker, no missing chunks, no GL errors on subsequent draw calls
  4. Mod survives resource pack reload (F3+T) without rendering artifacts
  5. Dedicated program's uniform locations (aw_ModelViewMatrix, aw_MatrixFlags, aw_TextureMatrix) are correctly cached at program creation and uploaded with correct values matching AW's expected semantics
**Plans**: TBD

### Phase 3: Item Rendering & Iris Coexistence
**Goal**: AW item textures render correctly in inventory/hotbar with Veil loaded, and the fix works alongside Iris shader packs without conflict or duplication.
**Depends on**: Phase 2 (core fix must work before item code path is adapted)
**Requirements**: RENDER-02, COMPAT-01
**Success Criteria** (what must be TRUE):
  1. AW item textures visible in inventory and hotbar with Veil loaded — no invisible items, no texture corruption, no missing icons
  2. Iris shader packs (Complementary, BSL, Sildurs) + Veil + AW all loaded simultaneously: both models and items render correctly with no regression from Phase 2
  3. When Iris partially fixes AW models via its own mechanism, the mod does not duplicate or conflict with Iris's fix — only handles the remaining rendering gap
  4. Item rendering survives shader pack reload (F3+R in Iris) and resource pack reload (F3+T) without artifacts
**Plans**: TBD
**Research flag**: Phase 3 may need `/gsd-research-phase` to understand Iris's partial fix mechanism for AW models (Iris issue #2786).

### Phase 4: Hardening & Diagnostics
**Goal**: Production validation across GPU vendors and mod configurations, user-facing diagnostics for bug reporting, and release-ready polish.
**Depends on**: Phase 3 (fix must be stable before hardening)
**Requirements**: DIAG-01, DIAG-02, DIAG-03
**Success Criteria** (what must be TRUE):
  1. Toggleable diagnostic logging (config-controlled, off by default) outputs AW VBO flush path details — shader program binding, uniform injection values, draw call sequence — to a timestamped log file
  2. `/awv-compat capture` client command dumps current GL program ID, all active uniform names and values, bound textures, and vertex attribute state to a timestamped log file
  3. GL State Debug Overlay renders real-time HUD (toggle via configurable keybind) showing: current bound GL program ID, AW uniform values (aw_ModelViewMatrix, aw_MatrixFlags, aw_TextureMatrix), and matrix state
  4. All diagnostic features are disabled by default with zero performance impact when not in use
  5. Fix validated on at least 2 GPU vendors/drivers with documented test results in test matrix
  6. Fix works after resource pack reload (F3+T) and shader reload (F3+R) without artifacts or crashes
**Plans**: TBD
**UI hint**: yes

## Progress

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1 - Investigation & Foundation | 0/0 | Not started | - |
| 2 - Core World Model Rendering Fix | 0/0 | Not started | - |
| 3 - Item Rendering & Iris Coexistence | 0/0 | Not started | - |
| 4 - Hardening & Diagnostics | 0/0 | Not started | - |

---

*Roadmap created: 2026-04-30*
