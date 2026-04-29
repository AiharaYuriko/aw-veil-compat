# Phase 1: Investigation & Foundation - Context

**Gathered:** 2026-04-30
**Status:** Ready for planning

## Phase Boundary

建立诊断基础设施和 mod 运行骨架：通过 AW/Veil 双侧 Mixin GL State 探针精确捕获冲突点的 shader program 状态差异，同时实现安全的运行时检测（AW/Veil 缺失时不崩溃）和 NeoForge 1.21.1 版本锁定。本 phase 不包含任何修复代码。

## Implementation Decisions

### 源码获取方式
- **D-01:** Clone 仓库 + 反编译 JAR 结合 — 优先 clone GitHub 仓库获取完整源码和注释，对无源码映射的部分 fallback 反编译发布 JAR
- **D-02:** 目标仓库 — AW: `SAGESSE-CN/Armourers-Workshop`（NeoForge 1.21.1 维护分支），Veil: `FoundryMC/Veil`（NeoForge 1.21.1 对应版本）
- **D-03:** 源码存放路径 — `D:\Software\CCode\AW\` 下创建 `aw-source\` 和 `veil-source\` 子目录

### Mixin 注入点与探针策略
- **D-04:** AW 侧和 Veil 侧同时注入 GL State 探针做交叉对比 — AW 侧在 clearRenderState Mixin 注入点，Veil 侧在 RenderTypeStage/DirectShaderCompiler 注入点
- **D-05:** 探针捕获标准数据集 — AW 侧：`GL_CURRENT_PROGRAM` + `aw_ModelViewMatrix` + `aw_MatrixFlags` + `aw_TextureMatrix` + 纳秒时间戳；Veil 侧：shader bind/unbind 事件 + 编译后 shader source hash + 纳秒时间戳
- **D-06:** 探针输出到带纳秒时间戳的日志文件，离线用脚本做 diff/correlation 分析，不在此 phase 做实时 HUD 显示

### Claude's Discretion
- GL State 探测方法的具体实现细节（探针类的结构、GL 状态读取方式、日志格式）
- Mod 项目骨架结构 — 默认遵循 AcceleratedRendering 的多 mixin config 模式（每个目标 mod 独立 mixin config 文件 + Mixin plugin 实现运行时条件加载 + `@Pseudo` 注解）
- 探针日志对比脚本的实现方式
- 版本检测和 `mods.toml` / `build.gradle` 的具体配置

## Specific Ideas

- 探针输出格式应便于 grep/awk 分析和脚本化处理
- 交叉对比的关键问题是：Veil 替换 shader program 的时刻 vs AW flush 的时刻，两者之间的纳秒级时序关系

## Canonical References

### Phase scope and requirements
- `.planning/ROADMAP.md` §Phase 1 — 目标、成功标准、需求映射 (DETECT-01, DETECT-02, VERSION-01)
- `.planning/REQUIREMENTS.md` — DETECT-01（AW 不存在不崩溃）、DETECT-02（Veil 不存在不崩溃）、VERSION-01（NeoForge 1.21.1 版本锁定）

### Project context
- `.planning/PROJECT.md` — 整体项目定义、约束、关键决策、排除方向列表

### Research findings
- `.planning/research/ARCHITECTURE.md` — AW/Veil 架构分析、冲突机制、渲染管线组件边界、5 phase 构建顺序
- `.planning/research/STACK.md` — NeoForge MDK 工具链、Mixin 配置、LWJGL/OpenGL 栈、RenderDoc 使用指南
- `.planning/research/PITFALLS.md` — 11 个领域陷阱（Mixin 冲突、GL 状态 desync、单 GPU 测试陷阱、性能回归风险）

## Existing Code Insights

### Reusable Assets
- 无 — 绿场项目，此为第一个 phase

### Established Patterns
- AcceleratedRendering (Argon4W) 的多 mixin config 架构模式：每个目标 mod 独立 mixin config 文件 + Mixin plugin 运行时检测 + `@Pseudo` 注解 + `compileOnly` Gradle 依赖
- NeoForge ModDevGradle 插件 + JDK 21 工具链

### Integration Points
- AW 渲染链路：`RenderType.clearRenderState()` → AW Mixin `@Inject` → VBO flush
- Veil 渲染链路：`RenderTypeStageRegistry` shader 绑定 → `DirectShaderCompiler` shader 编译 → pinwheel shader 格式

## Deferred Ideas

None — discussion stayed within phase scope

---

*Phase: 01-investigation-foundation*
*Context gathered: 2026-04-30*
