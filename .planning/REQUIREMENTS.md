# Requirements: AW-Veil Compat

**Defined:** 2026-04-30
**Core Value:** 在 Veil 共存时，AW 的模型和物品贴图正常渲染，且无需禁用 VBO（保持性能）

## v1 Requirements

### Rendering Fix

- [ ] **RENDER-01**: AW 装备模型在 Veil 共存时正确渲染 — 不拉伸、不固定在玩家附近、随视角正确转动
- [ ] **RENDER-02**: AW 物品贴图在 Veil 共存时正确渲染 — 背包/快捷栏中可见

### Performance

- [ ] **PERF-01**: 修复方案保持 VBO 渲染路径，帧率退步 <5% 可接受，>10% 视为阻塞

### Runtime Detection

- [ ] **DETECT-01**: AW 模组不存在或未加载时，兼容模组不崩溃不报错
- [ ] **DETECT-02**: Veil 模组不存在或未加载时，兼容模组不崩溃不报错

### Compatibility

- [ ] **COMPAT-01**: Iris + Veil + AW 三者共存时，修复方案不引入新的渲染问题

### Diagnostics

- [ ] **DIAG-01**: GL State Debug Overlay — 实时 HUD 叠加层，显示当前绑定 shader program ID、AW 专用 uniform 值 (aw_ModelViewMatrix, aw_MatrixFlags, aw_TextureMatrix)、矩阵状态，通过快捷键切换
- [ ] **DIAG-02**: 诊断日志模式 — 通过配置文件开关控制，输出 AW VBO flush 路径的 shader program 绑定、uniform 注入、draw call 详情
- [ ] **DIAG-03**: Shader 状态捕获命令 — `/awv-compat capture` 客户端命令，dump 当前 GL program ID、所有活跃 uniform 名称与值、绑定纹理、vertex attribute 状态到日志文件

### Version Lock

- [ ] **VERSION-01**: 仅支持 NeoForge 1.21.1（版本 21.1.222），不支持多版本

## v2 Requirements

Deferred to future release. Tracked but not in current roadmap.

- **FALLBACK-01**: Graceful Fallback Modes — OFF/LIGHT/FULL 三种修复力度运行时切换，无需重启
- **MATRIX-01**: 兼容性测试矩阵 — JSON 配置文件记录已知版本组合的兼容状态 (tested_ok / tested_broken / unknown)
- **IRIS-01**: Auto-Iris Compat Mode — 检测到 Iris 时自动激活专用修复路径，利用 Iris 已有的模型修复只处理剩余的物品问题
- **TRACE-01**: Mixin Injection Trace — 崩溃报告中附加 AW-Veil-Compat 的注入状态、mod 检测结果、崩溃前最后的 GL 状态

## Out of Scope

| Feature | Reason |
|---------|--------|
| 修复 Veil 自身渲染 bug | 超出兼容模组定位，Veil bug 应向 Veil 上游报告 |
| 支持多个 Minecraft 版本 (1.20.1 / 1.18.2 / 1.21.3 等) | 各版本 AW/Veil 渲染代码不同，多版本支持指数级放大测试和维护成本 |
| 支持 Fabric / Forge 加载器 | 跨加载器需要 Architectury 或独立构建管线，复杂度远超当前目标 |
| 重写 AW 的 VBO 渲染器 | 风险高（破坏 AW 功能、产生 fork 依赖），保持最小化 Mixin 注入 |
| 完整 in-game shader 调试器 | 与 RenderDoc/apitrace 等专业工具竞争无意义，提供轻量级状态 overlay 即可 |
| 自动更新兼容矩阵 | 需要服务器、版本管理、启动时网络请求，过度设计 |
| 实时 FPS/性能仪表盘 | F3 已有 FPS 显示，Spark 是成熟 profiler，不应重复造轮子 |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| RENDER-01 | Phase 2 | Pending |
| RENDER-02 | Phase 3 | Pending |
| PERF-01 | Phase 2 | Pending |
| DETECT-01 | Phase 1 | Pending |
| DETECT-02 | Phase 1 | Pending |
| COMPAT-01 | Phase 3 | Pending |
| DIAG-01 | Phase 4 | Pending |
| DIAG-02 | Phase 4 | Pending |
| DIAG-03 | Phase 4 | Pending |
| VERSION-01 | Phase 1 | Pending |

**Coverage:**
- v1 requirements: 10 total
- Mapped to phases: 10
- Unmapped: 0 ✓

---
*Requirements defined: 2026-04-30*
*Last updated: 2026-04-30 after roadmap creation*
