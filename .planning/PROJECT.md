# AW-Veil Compat

## What This Is

一个 NeoForge 1.21.1 兼容模组，通过 Mixin 修补 Armourer's Workshop (AW) 与 Veil 渲染框架之间的不兼容问题，使 AW 的模型和物品在 Veil 环境下正确渲染。

## Core Value

在 Veil 共存时，AW 的模型和物品贴图正常渲染，且无需禁用 VBO（保持性能）。

## Requirements

### Validated

(None yet — ship to validate)

### Active

- [ ] AW 装备模型在 Veil 环境下不拉伸、不固定在玩家附近、随视角正确转动
- [ ] AW 物品贴图在 Veil 环境下正常可见
- [ ] 修复方案不影响 AW VBO 渲染路径的性能优势
- [ ] 与 Iris 光影兼容（不引入新的渲染问题）

### Out of Scope

- 修复 Veil 本身的渲染问题 — 仅处理与 AW 的冲突
- 支持 Minecraft 1.21.1 以外的版本
- 支持 Fabric/Forge 加载器 — 仅 NeoForge 21.1.222

## Context

**开发环境：**
- 源码路径：`D:\Software\CCode\AW`
- JDK：`D:\Software\CCode\zulu21.32.17-ca-jdk21.0.2-win_x64` (Zulu JDK 21)

**技术环境：**
- Minecraft 1.21.1，NeoForge 21.1.222 加载器
- Armourer's Workshop：装备定制模组，使用 VBO 渲染管线
- Veil (FoundryMC)：渲染框架，作为其他模组的前置依赖
- Veil 使用自己的 "pinwheel" shader 格式和 DirectShaderCompiler 进行 shader 编译
- 注意：Sable 是物理模组，不是 shader 管线（此前误解已修正）

**AW VBO 渲染架构（核心冲突点）：**
AW 的 VBO 方案采用"借用当前绑定的 shader 并注入 AW 专用 uniform（aw_ModelViewMatrix、aw_MatrixFlags、aw_TextureMatrix）"的方式。当 Veil/Iris 修改了当前 shader 管线状态和 program 后，AW 注入的 uniform 写入错误的 program 或携带错误的矩阵/纹理状态。

**已知现象：**
- 禁用 AW VBO → 所有渲染 bug 消失，但性能严重下降
- Iris 1.8.8 加载光影 → 模型拉伸/固定问题消失，但物品贴图仍不可见
- 此现象说明：Iris 在 Veil 之前劫持了部分渲染流程，绕过了世界渲染阶段的冲突，但物品渲染阶段仍未绕过

**已尝试并排除的方向：**

| # | 方向 | 结果 |
|---|------|------|
| 1 | 修改 AW shader 矩阵传入（使用接近 vanilla 的 ModelView 矩阵） | 模型放大、固定在玩家附近、不随视角转动 |
| 2 | drawElements 回调只执行一次 | 巨大错误渲染，占据屏幕并随视角转动 |
| 3 | drawElements 注入点从 RETURN 改到 HEAD | 与之前错误一致 |
| 4 | 沿用 AW "借用当前 shader + 注入 AW uniform" VBO 方案 | 在 Sable/Veil/Iris 环境下持续出错 |
| 5 | Patch Veil DirectShaderCompiler 注入 AW uniform | 部分 shader 被 patch，渲染异常仍在 |
| 6 | 包装 ShaderInstance ResourceProvider 注入 .vsh | vanilla/entity shader 被 patch，Veil 替换的关键 block shader 未解决 |
| 7 | 将 AW VBO flush 挂载到 AW 自己的 RenderType | 递归崩溃→修复后原 bug 仍存在 |
| 8 | AW VBO flush 加 re-entry guard | 只解决崩溃，不解决渲染 |
| 9 | 强制 AW uniform 写入当前 GL_CURRENT_PROGRAM | 渲染 bug 未消失 |
| 10 | 保留 VBO 仅替换触发 RenderType | 不修复核心异常 |

**关键推论（调研确认）：**
问题不只是 "在哪个时机注入"——调研确认根因是 **shader program 不匹配**。AW 在 `clearRenderState` hook 处读取 `GL_CURRENT_PROGRAM` 借用当前 shader，但 Veil 的 RenderTypeStageRegistry 替换了活跃的 shader program。AW 的 uniform 注入写入了错误的 program。推荐修复方案是给 AW 独立专用 shader program（Pattern 1），替换"借用"模式。

## Constraints

- **Minecraft 版本**: 1.21.1
- **加载器**: NeoForge 21.1.222
- **模组加载器 API**: NeoForge + Mixin
- **兼容目标**: Armourer's Workshop (NeoForge 1.21.1 版本) + Veil
- **语言**: Java (Minecraft 模组标准)
- **性能**: 修复方案不得显著降低帧率（保持 VBO 路径性能特征）

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| 采用 Mixin 修补方案 | AW 和 Veil 均为已发布模组，Mixin 是最小侵入性的兼容修复手段 | — Pending |
| 优先逆向 Veil 渲染管线而非直接在 AW 侧修补 | 10 种 AW 侧修补方向已全部失败，需先理解 Veil 到底改了什么状态 | — Pending |
| 采用专用 AW Shader Program 方案（Pattern 1） | 调研确认根因为 shader program 不匹配；给 AW 独立 shader 替换 borrow 模式，从根本上消除冲突 | — Pending |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd-transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd-complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-04-30 after research and roadmap creation*
