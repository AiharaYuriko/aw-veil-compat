# Phase 1: Investigation & Foundation - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-30
**Phase:** 01-investigation-foundation
**Areas discussed:** 源码获取方式, Mixin 注入点选择

---

## 源码获取方式

| Option | Description | Selected |
|--------|-------------|----------|
| Clone 仓库 + 自行编译 | 从 GitHub clone AW (SAGESSE-CN) 和 Veil (FoundryMC)，本地编译 | |
| 直接反编译发布 JAR | 从 CurseForge/Modrinth 下载发布 JAR，反编译提取源码 | |
| 两者结合 | 优先 clone 仓库编译，fallback 反编译 JAR 处理无源码映射部分 | ✓ |

**User's choice:** 两者结合
**Notes:** Wants full source context from repos with decompilation as safety net

### 源码存放路径

| Option | Description | Selected |
|--------|-------------|----------|
| AW/ 子文件夹 | `D:\Software\CCode\AW\` 下创建 `aw-source\`、`veil-source\` | ✓ |
| 独立位置 | 放在项目外的独立路径 | |
| .planning/ 下 | 作为调研产物管理 | |

**User's choice:** AW/ 子文件夹
**Notes:** 源码和 mod 代码放在同一个父目录便于管理

### 目标仓库

| Option | Description | Selected |
|--------|-------------|----------|
| AW SAGESSE-CN fork | NeoForge 1.21.1 主要维护分支 | |
| AW 上游 + SAGESSE fork | 两个 AW 仓库做对比 | |
| AW fork + Veil 上游 | SAGESSE-CN/Armourers-Workshop + FoundryMC/Veil | ✓ |

**User's choice:** AW fork + Veil 上游
**Notes:** AW: SAGESSE-CN fork (NeoForge 1.21.1), Veil: FoundryMC upstream (对应版本)

---

## Mixin 注入点选择

### 探针策略

| Option | Description | Selected |
|--------|-------------|----------|
| AW 侧优先 | clearRenderState 注入点挂探针，建立有/无 Veil 对比基线 | |
| Veil 侧优先 | RenderTypeStage/DirectShaderCompiler 注入探针，追踪 program 替换时序 | |
| 两侧同时注入 | 交叉对比精确定位 program 替换时刻和 AW flush 的时序关系 | ✓ |

**User's choice:** 两侧同时注入
**Notes:** 最彻底的方案 — 能同时看到 Veil 什么时候改 program 和 AW 什么时候读 program

### 探测数据范围

| Option | Description | Selected |
|--------|-------------|----------|
| 最小集 | program ID + 时间戳 | |
| 标准集 | program + AW uniforms + 矩阵 + Veil shader source hash | ✓ |
| 完整集 | 全 GL 状态快照 | |

**User's choice:** 标准集
**Notes:** 在信息量和侵入性之间取平衡 — 足够验证 uniform 匹配性和 program 混淆假设

### 探针输出方式

| Option | Description | Selected |
|--------|-------------|----------|
| 日志文件对比 | 纳秒时间戳日志 + 离线 diff/correlation 脚本 | ✓ |
| HUD 实时显示 | 游戏中 overlay 显示 program ID 对比 | |
| 两者都做 | 日志 + HUD | |

**User's choice:** 日志文件对比
**Notes:** 简单可复用，适合精确时序分析。HUD 留到 Phase 4 (DIAG-01)

---

## Claude's Discretion

- GL State 探测方法具体实现（探针类结构、GL 状态读取方式、日志格式）
- Mod 项目骨架结构（默认遵循 AcceleratedRendering 多 mixin config 模式）
- 探针日志对比脚本实现
- 版本检测和 `mods.toml` / `build.gradle` 配置

## Deferred Ideas

None — discussion stayed within phase scope
