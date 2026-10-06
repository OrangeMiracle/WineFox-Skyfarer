# WineFox: Skyfarer 工程总索引

这是**项目总索引、当前项目事实总览和新开发会话的第一阅读入口**，面向维护者与后续 Codex。玩家安装与使用介绍保留在 [README](../README.md)。本文按 2026-10-05 的源码核对；构建版本为 **0.6.3**。

## 先读：长期维护规则

> **这些文档属于当前工程实现的一部分，而不是一次性说明文件。**
>
> 此后凡是对项目进行了功能、架构、状态机、飞控、异步系统、配置、命令、调试系统、验证体系、关键类职责或目录结构上的修改，只要相关事实发生变化，就必须同步更新对应的 `docs/` 文档。
>
> 不允许出现“源码已经改变，但文档仍然描述旧版本实现”的情况。
>
> 完成重要修改后，应主动检查 `PROJECT_INDEX.md`、`FLIGHT_CONTROL_OVERVIEW.md` 以及其他相关技术文档是否需要同步更新。

**文档描述当前实现事实。文档与源码冲突时，以源码为最终权威；发现冲突后应立即修正文档。** 历史需求、聊天记录、类名、注释、旧构建日志均不能替代对实际调用路径的检查。未接入的枚举、配置或方法必须注明，不能当成现有功能。

0.6.3 根据 [实机日志分析](FIELD_ANALYSIS_0.6.3.md) 修复窗口生命周期、下降捕获和标记起飞，并将 Funnel 扩为成功轨迹群的截面包络，支持显式俯冲/上升与转弯。算法、DTO、缓存及限制见 [ATTACK_FUNNELS](ATTACK_FUNNELS.md)。本轮仅编译、直接相关检查与打包，没有重跑全量验证。

新会话按以下顺序阅读：

1. 首先阅读本文件。
2. 涉及飞控、攻击或状态机，再读 [FLIGHT_CONTROL_OVERVIEW](FLIGHT_CONTROL_OVERVIEW.md)；物理公式、采集生命周期和完整路线认证见 [VANILLA_PHYSICS](VANILLA_PHYSICS.md)。
3. 涉及 Snapshot、Worker、Planner、缓存或性能，再读 [ASYNC_PLANNING](ASYNC_PLANNING.md)。
4. 涉及参数、命令、指挥镜、HUD 或兼容层，再读 [CONFIG_DEBUG_REFERENCE](CONFIG_DEBUG_REFERENCE.md)；验证与复现看 [VALIDATION](VALIDATION.md)。
5. 需要历史原因，再查 [buildHistory](../buildHistory.md)。历史章节描述当时版本，不是当前规范。
6. 真正修改前，仍必须检查对应源码及调用者；完成修改后同步检查并更新相关 `docs/`。

当前开发约定：**不在工作目录安装或启动 Minecraft**。编译与离线验证由开发会话执行，游戏内行为由用户手动测试。构建与更新报告写入 `buildHistory.md`；没有运行的验证不得写成“已通过”。不修改依赖 Mod 的 Jar 来实现本项目行为。

## 1. 项目定位与版本事实

| 项目 | 当前事实与出处 |
| --- | --- |
| 名称 / ID | WineFox: Skyfarer / `winefox_skyfarer`；[入口类](../src/main/java/dev/winefox/skyfarer/WineFoxSkyfarer.java) |
| 工作模式 ID | `winefox_skyfarer:skyfarer`；[SkyfarerTask](../src/main/java/dev/winefox/skyfarer/compat/tlm/SkyfarerTask.java) |
| 平台 | Minecraft 1.21.1，NeoForge；构建固定 NeoForge 21.1.250 |
| Java / 构建 | Java 21；Gradle Wrapper；ModDev 插件 2.0.148；[build.gradle](../build.gradle)、[gradle.properties](../gradle.properties) |
| 必需依赖 | `libs/touhoulittlemaid-1.5.3-neoforge+mc1.21.1.jar`、`libs/spear-backport-neoforge-1.8.0-1.21.1.jar` |
| 可选兼容 | YSM；`tools/ysm-reference.jar` 只用于编译参考；兼容检查针对其 2.6.5 接口，不是必需依赖 |
| 构建版本 | `build.gradle` 的 `version='0.6.3'`，Jar Manifest 的 `Implementation-Version` 来自它；[neoforge.mods.toml](../src/main/resources/META-INF/neoforge.mods.toml) 同步为 0.6.3。0.6.1 已修正此前 0.6.0/0.5.4 不一致；发版仍须核对实际 Jar |
| 运行依赖范围 | 元数据限定 MC `[1.21.1]`、NeoForge `[21.1.250,21.2)`、TLM `[1.5.3,1.6)`、Spears `[1.8.0,1.9)`，均为 BOTH；范围声明不等于每个版本均已实测 |
| 许可证 | [LGPL-3.0-only](../LICENSE) |

核心玩法是让东方女仆使用鞘翅、烟花和 Spears 长矛执行自主空战，并通过遥控器、指挥镜和调试工具管理她们。目标是能持续进攻、合理选择路线并安全离场；不是靠普通战斗直接写速度或瞬移来伪造命中。

装备准入以 [SkyfarerEquipment](../src/main/java/dev/winefox/skyfarer/compat/tlm/SkyfarerEquipment.java) 为准：主手是可蓄力的长矛、胸甲槽是可飞行的原版鞘翅、可用背包里存在烟花。TLM 的 `isEnable`、遥控改工作模式和起飞前检查都要尊重这些条件。

## 2. 当前已存在的功能

| 系统 | 当前实现 |
| --- | --- |
| 骑空士工作模式 | 通过 TLM `ILittleMaid` 扩展 API 注册；工作脑任务触发获取目标，实体 tick 驱动控制器。托管飞行期间抑制与飞控冲突的地面导航、跟随、随机行走等行为 |
| 飞行 / 地形 | 地面与水中起飞、原版鞘翅滑翔、有限烟花推进、坡面入口、平地俯冲、持续转弯曲线、侧向/爬升离场、紧凑重定位、低能量恢复和紧急姿态 |
| 长矛 | 使用 Spears 原生蓄力与伤害；女仆限定的上下虚拟命中扩展，真实方块截断、每次攻击流程 UUID 去重。没有修改目标实际尺寸，也没有改 Spears Jar |
| 持续作战 | 分层索敌、失败次数换目标、主目标后的可行二段追击、保留蓄力尾段；无机会后搜索、返航与落地 |
| 跟随 / Home | 战斗中心由指挥任务、Home 工作中心、主人位置依次决定；攻击周期中保存战斗中心，避免每 tick 追随移动主人重写已承诺航线 |
| 女仆遥控器 `maid_remote` | 右键所属女仆绑定，使用时打开同维度已加载女仆的工作模式面板；服务端 nonce、持有物、所有权、启用条件复核 |
| 指挥镜 `command_spyglass` / `creative_command_spyglass` | 原版望远镜右键缩放；左键切换目标标记，最多 5 个，超出按旧到新淘汰；逐女仆分配路线。创造镜可从注册表唤醒远方女仆，赶路区块票据、免费赶路推进与任务结束 Home 回收均已实现 |
| 标记限制 | 生存 Home 呼叫范围默认 128 格；创造镜默认呼叫半径 1000 格，设 0 才无限。创造镜不等于无限目标标记距离；默认标记仍是 256 格。显式标记可覆盖普通友好关系筛选，但玩家、主人、自己等保护仍存在 |
| 调试棒 `debug_stick` | 多女仆绑定，左右方向键切 HUD，卸载保留最后数据；远程列表、所有者筛选及权限控制的双向传送；当前女仆亮绿色透视框、多只女仆同时预测线 |
| 渲染 / 可选模型 | TLM 飞行与持矛姿态适配、YSM 可选接口接入和回退；模型是否提供相应动画/附件决定最终外观，不能把接口检查当成所有模型的视觉保证 |

四个物品在工具与实用物品创造分类注册；当前资源目录没有合成配方。命令、限制与可视化图例详见 [配置与调试参考](CONFIG_DEBUG_REFERENCE.md)。

## 3. 总体架构与调用入口

```text
TLM SkyfarerExtension → SkyfarerTask → SkyfarerBehavior → SkyfarerRuntime.workTick
                                                      （只请求 acquire）
NeoForge EntityTick.Pre → SkyfarerRuntime.tick
  ├─ CommandManager.beforeMaidTick → 回收 / TransitFlight 赶路（可独占本 tick）
  └─ AirCombatController.tick → 状态机 / 目标 / 当前实体状态
         ├─ AsyncPlanning → PlannerSession（主线程请求与邮箱）
         │     ├─ MaidTerrainSource → 不可变 CollisionSnapshot
         │     └─ PlanningTask + Snapshot + 冻结倍率
         │           → PlannerExecutor 共享 Worker → 纯数值规划
         │           → Ticket.outcome（volatile 发布）
         │           → PlannerSession 校验 generation / 参数 / 年龄 / 姿态
         └─ 主线程姿态指令、烟花实体与物品、实际长矛接触、安全检查、状态转移

ServerTick.Pre  → PlannerSession.serverTick：结果领取、预算重置、轮转增量采集
ServerTick.Post → CommandManager.tick / DebugWatchManager.tick：调度、票据、同步
客户端         ← Payload → HUD / 标记 / 预测线 / 管理面板
```

异步规划器不驱动 Entity，也不决定何时调用世界 API。主线程保有最终控制权；**规划成功不等于已经造成伤害**。网络处理通过 `context.enqueueWork` 回到相应游戏线程，客户端类只由客户端入口连接；不能把 common 类链接到 `Minecraft`、`Screen` 等客户端专属类型。

## 4. 目录与 Package 索引

Java 包根为 `src/main/java/dev/winefox/skyfarer/`。目录名称不能直接当成线程约束，例如 `flight/` 同时包含纯预测器和必须在主线程操作实体的适配器。

| 目录 | 职责、调用关系与应修改它的情形 |
| --- | --- |
| [`ai/`](../src/main/java/dev/winefox/skyfarer/ai/) | 控制器、20 状态枚举、战斗中心、目标分层与每次攻击记录；由 runtime 驱动。改变决策顺序、阶段衔接、目标锁定或连续作战时进入这里 |
| [`async/`](../src/main/java/dev/winefox/skyfarer/async/) | 主线程 facade / 快照源 / 会话邮箱，以及纯 DTO、共享执行器、快照查询；性能、缓存、取消、旧结果与线程问题首先看这里 |
| [`flight/`](../src/main/java/dev/winefox/skyfarer/flight/) | 滑翔方程、身体扫掠、坡面/入口/曲线/离场/追击/起飞数学；同时有 `HeadingController`、`FireworkManager`、`ElytraFlightController` 等主线程执行器。改变实际航线或预测/执行一致性时看具体调用路径 |
| [`safety/`](../src/main/java/dev/winefox/skyfarer/safety/) | `CorridorProbe` 和 `AirCollisionChecker` 访问真实世界，负责即时身体、射线、支撑面和加载范围检查；不传给 Worker |
| [`command/`](../src/main/java/dev/winefox/skyfarer/command/) | `/wfs` 命令、标记队列、逐女仆调度、注册表、区块票据、赶路与创造高速数学。远方唤醒、Home 回收或指令抢目标时检查这里 |
| [`runtime/`](../src/main/java/dev/winefox/skyfarer/runtime/) | 每 UUID 控制器生命周期、遥控会话、多女仆调试会话和远程面板/传送；管理实体卸载、玩家退出及服务器停止清理 |
| [`compat/tlm/`](../src/main/java/dev/winefox/skyfarer/compat/tlm/) | TLM 官方任务/动画扩展、装备资格、地面记忆清理；更新依赖版本或工作模式准入时检查 |
| [`compat/spears/`](../src/main/java/dev/winefox/skyfarer/compat/spears/) | 原生蓄力、射程和速度接口，以及仅女仆使用的虚拟碰撞补充；伤害/双击/小目标问题看这里与相应 mixin |
| [`compat/ysm/`](../src/main/java/dev/winefox/skyfarer/compat/ysm/) | 可选 mixin 启用插件；必须在未安装 YSM 时也能安全加载 |
| [`mixin/`](../src/main/java/dev/winefox/skyfarer/mixin/) | 精确接入 MC/TLM/Spears 的移动、姿态、友伤、蓄力、Home 回收和数据访问；`ysm/` 子包接可选客户端接口。更改目标签名必须核对当前依赖字节码 |
| [`config/`](../src/main/java/dev/winefox/skyfarer/config/) | 参数目录、SERVER 配置、在线覆盖与 revision；参数增删时同步命令、相关 DTO、文档与验证 |
| [`item/`](../src/main/java/dev/winefox/skyfarer/item/) | 遥控器、调试棒、两种望远镜的物品入口；复杂服务端行为转交 runtime / command |
| [`network/`](../src/main/java/dev/winefox/skyfarer/network/) | 有类型的 Payload 与协议注册（当前 registrar `"3"`）；UI 提交必须再次服务端校验，不能相信客户端传来的 UUID/所有者/任务资格 |
| [`client/`](../src/main/java/dev/winefox/skyfarer/client/) | 客户端按键、指挥输入、动画；`gui/` 管理面板，`debug/` HUD 与世界线框；只消费服务端调试数据，不规划战斗 |
| [`src/main/resources/`](../src/main/resources/) | Mod 元数据、common/client/可选 YSM mixin 清单、语言和物品模型；资源与协议/版本变动要一起核对 |
| [`src/verification/`](../src/verification/) | 数学、策略、兼容字节码等独立离线断言；不进入交付 Jar |
| [`src/replay/`](../src/replay/) | 实际生产控制器配合外部 API 夹具及合成物理世界；还编译实际异步调度器。不是 Minecraft 世界运行环境 |
| [`tools/`](../tools/) | 构建包装脚本、离线日志设置、历史日志、反编译工具及被忽略的依赖参考；旧运行/失败日志不表示当前验证成功 |
| [`libs/`](../libs/) | 本机必需依赖 Jar，被 `.gitignore` 忽略；新机器要确认这些文件存在 |
| [`docs/`](./) | 当前工程知识；本索引分配各技术主题的维护位置 |
| `build/`、`.gradle/` | 生成物、回放报告和构建缓存；不是事实维护入口。`build/libs` 交付 Jar，`build/reports/controller-replay` 放逐 tick 记录 |
| [`README.md`](../README.md)、[`buildHistory.md`](../buildHistory.md) | 分别为玩家/普通开发者入口和按版本记录的证据；不可代替当前技术文档 |

## 5. 关键类与生命周期

下表的“主线程”指服务端游戏线程；客户端另行标明。完整方法与细分 DTO 看各专题，修改前从这些类追踪调用者。

| 类 | 职责与输入 → 输出 | 线程、生命周期及关系 |
| --- | --- | --- |
| [WineFoxSkyfarer](../src/main/java/dev/winefox/skyfarer/WineFoxSkyfarer.java) | 注册物品、配置、事件、网络、票据控制器和命令 | Mod 初始化；实际事件各回到自己的游戏生命周期 |
| [SkyfarerRuntime](../src/main/java/dev/winefox/skyfarer/runtime/SkyfarerRuntime.java) | Maid UUID / 实体 tick → 唯一控制器与当前空中控制权 | 主线程静态 Map；实体离开 release，服务器停止清空；赶路/回收先于战斗控制器 |
| [AirCombatController](../src/main/java/dev/winefox/skyfarer/ai/AirCombatController.java) | 当前 Entity、工作/装备状态、目标与规划结果 → 姿态、物品使用、阶段迁移 | 每女仆一份；主线程。`managed` 是实际接管状态，不等同于枚举的 `controlsAir()` 推断；每轮准备保留是否从下方进场，攻击仍需完整物理认证；release 释放规划会话和推进/蓄力 |
| [TargetEvaluator](../src/main/java/dev/winefox/skyfarer/ai/TargetEvaluator.java) / [TargetPriority](../src/main/java/dev/winefox/skyfarer/ai/TargetPriority.java) | 已加载实体、锚点、标记/上一目标/失败集合 → 有效目标与分层排名 | 前者主线程读实体与射线，后者纯比较。获取时检查可见性，已锁定接近不能每 tick 强制直视目标 |
| [CombatAnchor](../src/main/java/dev/winefox/skyfarer/ai/CombatAnchor.java) | 指挥任务 / Home 限制中心 / 主人 → 工作中心 | 主线程；控制器在攻击周期间保存，不能因主人移动打断已承诺攻击 |
| [SpearPassHistory](../src/main/java/dev/winefox/skyfarer/ai/SpearPassHistory.java) | 每轮主目标、命中 UUID、结算 → 去重和失败换目标记录 | 控制器所有；主线程，避免原生命中与扩展判定双击 |
| [AsyncPlanning](../src/main/java/dev/winefox/skyfarer/async/AsyncPlanning.java) | 主线程请求 → 封闭 PlanningTask；已验证结果或等待/轻量控制 | 每控制器/赶路实例一份；不是可从后台访问的“世界代理” |
| [MaidTerrainSource](../src/main/java/dev/winefox/skyfarer/async/MaidTerrainSource.java) | 已加载实体可 tick 区域、真实形状、女仆尺寸、边界 → 数值 Cell / Snapshot | 主线程专用，持有 Maid；绝不能随任务传递 |
| [CollisionSnapshot](../src/main/java/dev/winefox/skyfarer/async/CollisionSnapshot.java) | 不可变盒子/水位掩码/已加载标记 → Worker 身体、射线、支撑面查询 | Snapshot 共享只读；`Space` 查询游标每任务独占，记录缺失单元与取消 |
| [PlanningTask](../src/main/java/dev/winefox/skyfarer/async/PlanningTask.java) | 封闭数值 DTO → 对应规划结果 | Worker；兼容性和 slot 是缓存/并行调度协议的一部分，不是随意可简化的 equals |
| [PlannerExecutor](../src/main/java/dev/winefox/skyfarer/async/PlannerExecutor.java) | Task + Snapshot + 冻结倍率 → Ticket / Outcome | 全服共享惰性线程池；控制与几何队列分离；取消标记原子化、结果 volatile 发布，停止时清队列并中断 |
| [PlannerSession](../src/main/java/dev/winefox/skyfarer/async/PlannerSession.java) | 主线程上下文/请求、完成票据 → 缓存、数据请求或经过验证的结果 | 每女仆/赶路会话；全服预算轮转；generation 与任务参数共同限制复用，close 清理 |
| [GlidePlanner](../src/main/java/dev/winefox/skyfarer/flight/GlidePlanner.java) / [FlightPrediction](../src/main/java/dev/winefox/skyfarer/flight/FlightPrediction.java) | 状态、目标/路线、有限推进 → 姿态候选、物理轨迹、接触/能量预测 | 完整搜索经 Worker；少量数值反馈/单步数学仍可在主线程作即时控制 |
| [VanillaElytra](../src/main/java/dev/winefox/skyfarer/flight/VanillaElytra.java) / [PhysicsContext](../src/main/java/dev/winefox/skyfarer/flight/PhysicsContext.java) / [PhysicsProvider](../src/main/java/dev/winefox/skyfarer/flight/PhysicsProvider.java) | 1.21.1 mapped 源码自由飞行公式；gravity/缓降冻结 DTO；实体/Level 身份绑定采集 | Provider 只在主线程；step 只读 DTO，不发明空气阻力属性 |
| [EnergyReserveEnvelope](../src/main/java/dev/winefox/skyfarer/flight/EnergyReserveEnvelope.java) | Approach→Strike→Exit 每步未来需求与最差 reserve | Worker 中的评分/补能指标；普通 AttackCertificate 和曲线 Rejoin 的动力学认证拥有最终否决权 |
| [SweepingAttackPlanner](../src/main/java/dev/winefox/skyfarer/flight/SweepingAttackPlanner.java) | 当前动量、接触方向、目标 → C1 连续入口/离场曲线与闭环回放 | 候选/评分/接入重验证在 Worker；当前曲线指令与进度判断是主线程轻量跟踪 |
| [AttackFunnelPlanner](../src/main/java/dev/winefox/skyfarer/flight/AttackFunnelPlanner.java) | 目标相对合成状态 → 最多四个正向认证窗口；当前状态 → coast/全寿命脉冲 Capture | 复用 C1/物理/能量/碰撞/接触/离场；控制器仍以最终 Rejoin 承诺攻击；独立目标地图 session 可跨起飞与接近阶段复用 |
| [HeadingController](../src/main/java/dev/winefox/skyfarer/flight/HeadingController.java) / [FireworkManager](../src/main/java/dev/winefox/skyfarer/flight/FireworkManager.java) | 规划姿态 / 已验证寿命代价和背包 → 朝向 / 实际火箭及消耗 | 主线程；前者每 tick 起始姿态避免多次指令累加转向，后者每控制器维护活动火箭与冷却 |
| [CommandManager](../src/main/java/dev/winefox/skyfarer/command/CommandManager.java) | 玩家标记、女仆记录、目标与区域 → 分配、赶路、战斗交接、回收 | 主线程服务；玩家退出/死亡/卸载/关服对应清理；不按所有女仆机械选列表第一项 |
| [TransitFlight](../src/main/java/dev/winefox/skyfarer/command/TransitFlight.java) | 已分配目标、女仆状态 → 普通免费推进或创造高速赶路 | 每次指挥赶路实例；主线程执行 + 独立异步会话；交接/结束 close，不与空战同时控制实体 |
| [SkyfarerMaidRegistry](../src/main/java/dev/winefox/skyfarer/command/SkyfarerMaidRegistry.java) / [CommandTickets](../src/main/java/dev/winefox/skyfarer/command/CommandTickets.java) | 所有者与最后已知 Maid 记录 / 预计赶路段 → 持久记录与有界区块票据 | 注册表属于 SavedData；票据为运行期资源，不能当作无限世界加载器；不要把最后记录当成实时 Entity |
| [RemoteSessions](../src/main/java/dev/winefox/skyfarer/runtime/RemoteSessions.java) / [DebugPanelManager](../src/main/java/dev/winefox/skyfarer/runtime/DebugPanelManager.java) | 玩家会话、操作 Payload → 服务端校验后的任务设置、列表或传送 | 主线程，nonce/所有者/权限/持有物等校验；面板传送有独立临时票据和超时 |
| [DebugWatchManager](../src/main/java/dev/winefox/skyfarer/runtime/DebugWatchManager.java) / [ClientDebugState](../src/main/java/dev/winefox/skyfarer/client/debug/ClientDebugState.java) | 控制器/赶路调试数据 → 按玩家多 UUID 快照 / 当前 HUD 选择 | 前者主线程按周期同步，后者客户端缓存；卸载显示最后值，退出清理，不继续模拟已卸载女仆 |

## 6. 状态机总览

[AirCombatState](../src/main/java/dev/winefox/skyfarer/ai/AirCombatState.java) 共 20 个值，真实行为在控制器方法中。下面是主要可达路径，箭头不表示无条件转移：

```text
GROUNDED → TARGET_ACQUIRE → TARGET_SELECTED → TAKEOFF_PREP → TAKEOFF → CLIMB
CLIMB → APPROACH ↔ REPOSITION → ATTACK_RUN → DIVE → SPEAR_CHARGE
                                         │              │
                                         └──────────────┤
                                                        ├→ CHAIN_ATTACK → ATTACK_EXIT
                                                        └→ ATTACK_EXIT
ATTACK_EXIT → APPROACH / LOITER_SEARCH / RETURN_TO_OWNER
LOITER_SEARCH → APPROACH 或 RETURN_TO_OWNER
RETURN_TO_OWNER → 可恢复时重新攻击；否则 LANDING → GROUNDED
水中且具备脱离资格 → WATER_ESCAPE → APPROACH / 原返航流程 / GROUNDED
```

`BREAKAWAY`、`ABORT`、`EMERGENCY` 保留枚举与处理分支，但当前普通路径没有主动 `transition` 进入它们。`beginBreakaway()` 通常进入 `ATTACK_EXIT`。`ASSESS_NEXT_ATTACK_ENTRY` 是调试原因字符串，不是另一个枚举状态。

索敌按**标记层级 → 上一目标/保护范围层级 → 同层幻翼 → 路线分数与切换滞后**比较；失败换目标另外使用避免集合，只有被避免目标可选时允许重试。不能将“幻翼优先”扩成全局无条件追击。详细状态目的、进入/退出、数据、Planner、回退及边界见 [飞控总览](FLIGHT_CONTROL_OVERVIEW.md)。

## 7. 多线程原则与禁止事项

流水线是 **Main Thread Snapshot → Async Pure Planning → Result Mailbox → Main Thread Validate & Apply**。

- 全服共用 2–4 个 Worker，控制队列和几何队列各 32；每会话最多 24 个 pending。满队列退让，不创建无限任务。任务是有界池中的 Ticket，不是每 tick 新开线程/Future。
- 主线程按预算采集真实形状；每会话缓存最多 768 个单元。缺失数据不当空气执行，探索结果仅用于请求更多快照。
- 目标、逻辑阶段、配置 revision 或维度变化取消旧任务；参数、年龄、位移和姿态继续验证。阶段分组是 LAUNCH / ENTRY / ATTACK 等逻辑组，不是每个枚举变化都取消所有工作。
- 普通预测短暂复用，曲线几何与当前接入验证分离。缓存让控制继续运行，不代表旧轨迹可以无限期执行。
- 目标地图另有固定阶段 session，共用 Source、Worker 和全服采集预算。每地图最多一个在途任务、一个正地图、四个窗口、768 cells；与控制 session 合计最多 25 pending、1536 cells。合法正地图保持，未满额每 40 ticks 后台补空槽；空结果 8 ticks。证明 Cell 优先保留/刷新，变化、缺失或超过 80 ticks 仍拒绝复用。Capture/Rejoin 使用严格当前状态门槛。
- 即时世界碰撞、最终伤害、落点、目标扫描、物品和实体操作仍在主线程。创造高速当前 tick 的长位移扫掠也是主线程工作。

**线程安全红线：**

1. Worker 不得直接读取 Minecraft World / Level / BlockEntity，或持有、读写 Entity、BlockState、VoxelShape 等活对象。
2. 不得把捕获 Maid/Level 的 lambda、Supplier、CorridorProbe 或其他可访问世界的回调塞进 DTO；只能把需要的数据复制为数值。
3. Worker 不得修改实体、背包、蓄力、实际伤害、网络同步或状态机。
4. 主线程不得 `Future.get()` / `join()` 等待重规划，也不得在队列拒绝或 Pending 时同步重跑完整 Planner。
5. 不得绕过 `MaidTerrainSource` / `CollisionSnapshot` 边界查询世界，不得为了规划强行加载未知区块；实际票据授权属于指挥/调试服务。
6. 不得执行探索模式的推测结果，不能把缺失/不可 tick 的单元认证为空气；实时安全防线不能为提高命中率而移除。
7. 新增配置读取必须在主线程冻结；新增任务要定义 slot、兼容性、取消响应、结果有效期和验证。队列/缓存必须有界，实体卸载和关服必须释放资源。
8. 不得仅凭 `flight/` 包名断言它能在 Worker 使用，也不得从 common/network 引用客户端渲染类。

完整预算、缓存淘汰、结果验收条件、等待控制与故障排查见 [异步架构](ASYNC_PLANNING.md)。

## 8. 重要配置速查

[SkyfarerConfig](../src/main/java/dev/winefox/skyfarer/config/SkyfarerConfig.java) 定义 SERVER 配置；[SkyfarerSettings](../src/main/java/dev/winefox/skyfarer/config/SkyfarerSettings.java) 统一取值、保存/覆盖与 revision。默认 20 ticks = 1 秒是游戏正常速率换算，不是卡顿时的墙钟保证。完整范围、单位和消费者见 [配置参考](CONFIG_DEBUG_REFERENCE.md)。

| 配置键（命令用短名） | 默认 / 单位 | 作用与主要模块 |
| --- | --- | --- |
| `turn_rate` / `attack_turn_rate` | 5 / 2.5 °/tick | 普通/攻击转向；Controller、Heading、各数值请求；曲线采用两者最大值，不能假设全部攻击只用 2.5 |
| `firework_cooldown_ticks` | 30 ticks | FireworkManager 发射间隔，活动火箭期间不会重复点火 |
| `minimum_boost_speed` / `attack_minimum_boost_speed` | 0.8 / 0.4 block/tick | 普通/攻击补能门槛；不是强行设置速度 |
| `energy_reserve` / `attack_energy_reserve` | 0.08 / 0.02 模型能量单位 | 预测储备与补能判断，不是恒定高度控制 |
| `search_range` / `combat_range` / `hard_combat_range` | 32 / 40 / 56 blocks | 目标查询、战斗与追击范围；Home 保护范围可以增大后两者的实际值 |
| `scan_interval_ticks` | 10 ticks | ScanCadence 节流索敌 |
| `attack_timeout_ticks` | 100 ticks | 攻击/准备相关阶段的有界预算；具体倍数由调用处决定 |
| `breakaway_distance` | 12 blocks | 离场距离基础值；`SkyfarerSettings.attackExitDistance()` 使用其 2/3 |
| `attack_aim_offset` | 0.5 block | 在目标身体中心之上瞄准 |
| `spear_vertical_expansion` | 上下各 3 blocks | 女仆长矛虚拟判定，不改实体碰撞箱 |
| `max_failed_passes` | 2 次 | SpearPassHistory 的换目标门槛 |
| `post_attack_charge_ticks` | 60 ticks | 完成攻击/普通掠过后的蓄力尾段，不等于强制离场 60 ticks |
| `secondary_target_radius` | 6 blocks | 主目标附近次要机会的发现范围，不绕过后续路线可行性 |
| `slope_sample_radius` / `slope_sample_step` | 16 / 0.5 blocks | 坡面范围/步长，半径最大 32；SlopeSampler 与入口路线 |
| `corridor_side_margin` / `corridor_vertical_margin` | 各 0.35 block | 普通预测通道余量；部分专用路径使用自己的固定/紧急余量 |
| `prediction_length_multiplier` | 1，最大 10 | PredictionHorizon 中使用倍率的预测；不能假设所有 Planner 的固定上限都同比放大 |
| `command_mark_range` / `command_home_range` | 256 / 128 blocks | 指挥标记 / 生存 Home 呼叫 |
| `creative_command_range` | 1000 blocks；0 无限 | 创造呼叫的三维距离上限，跟随和 Home 都受限 |
| `creative_transit_speed_multiplier` / `creative_transit_height` | 2 倍 / 世界 Y=200 | 创造赶路，最大 100 倍；高度还受维度与路线限制 |
| `sync_interval_ticks` / `log_interval_ticks` | 5 / 100 ticks | 调试同步 / 周期诊断，不是所有事件日志的统一频率 |
| `debuglog` | false | 诊断日志开关；与调试棒绑定/HUD 显示不是同一个开关 |

`previous_target_bonus`、`forward_collision_check_distance`、`climb_height` 是当前保留但不驱动有效运行路径的参数；不要在排障时依赖它们调参。特别是 `arcPoint()` 确实由 LOITER 调用，但调用前已排除 `preferred != null`，因此其中读取 `climbHeight()` 的有目标分支不会执行。详细依据见配置参考。

## 9. 调试与验证入口

- `/wfs`：配置查看/设置/重置与 debuglog 开关，配置和日志子命令要求权限等级 2；没有调试绑定子命令。绑定通过调试棒或其远程面板完成。精确参数树见 [配置与调试参考](CONFIG_DEBUG_REFERENCE.md)。
- HUD：状态、原因、目标、蓄力、姿态与异步指标。`captureMs` 是累计快照采集墙钟时间，`workerMs` 是各任务累计墙钟耗时，**不是 CPU profiler 结果**；`stale` 包含被失效取消的 pending，不是每 tick 过期比例。
- 世界调试：红色目标线、蓝色引导、黄色身体预测通道、绿色入口方块与方向、青绿色拟合曲线；当前 HUD 女仆亮绿色粗透视框。不同线表示不同数据，不能用蓝色单一 waypoint 代替完整预测轨迹。
- 紫色稀疏管道显示已完成的目标攻击窗口；每只女仆最多四条、各十八个截面。它是采样可接入区域的图示，不能把进入紫色空间当成已满足动力学或攻击资格。
- 指挥目标标记：普通亮红橙、创造亮紫，角框 + `▼` + 序号；穿墙简化，只有标记者可见。它和调试棒的女仆选择框是独立系统。
- 入口命令：`./gradlew.bat build` 触发 `check` 及 26 项 verify；只编译可用 `classes`。当前环境包装脚本为 `tools/build.ps1`，参数和限制见 [验证文档](VALIDATION.md)。
- 完整验证包括 21 个控制器生命周期、2 个特殊攻击入口、2 个镜像山腰、7 个赶路生命周期，以及物理差分与包络验证；本轮仅执行六项相关检查，实际结果与日志见 [buildHistory](../buildHistory.md)。离线通过不是对任意世界/模组组合的全覆盖证明。

## 10. 当前开发状态与限制

| 状态 | 当前事实 |
| --- | --- |
| 已实现 | 骑空士空战、直线/曲线与坡面攻击、持续作战、指挥调度/区块票据、调试体系、共享异步流水线，以及 mapped 物理预测/冻结上下文/全程能量包络、0.6.2 目标窗口与主动 Capture |
| 正在长期维护 | 预测与实际执行一致性、取消/旧结果边界、兼容 Mixin、配置消费者、离线回放，以及这套 `docs/`；不是承诺后台另有自动维护任务 |
| 当前边界 | 预测不覆盖第三方 travel Mixin、外部火箭或外力；完整攻击认证时域最多 160 ticks。原生效果/实体 tick 时序与复杂模组组合仍需实机确认 |
| 已知遗留 | 三个不驱动当前行为的配置，以及保留但当前没有普通入口的状态分支；不要为“文档看起来齐全”编造作用或自动删除兼容字段 |
| 性能边界 | 快照约 2 ms 是全服单 tick 累计采集软预算，不包含所有 Mod 工作；即时世界查询、实体扫描和创造极高速实际位移仍在主线程。缓存可短暂陈旧，依赖验收和实时安全检查 |
| 验证边界 | 离线世界与外部 API 是夹具，不能完全复现真实区块时序、服务端调度、碰撞形状模组或网络同步；合成负载不是 MC MSPT/GC 基准 |
| 需要实机验证 | 异步延迟对复杂地形命中与连续作战的影响、多女仆 MSPT/内存/GC、区块加载/卸载/回家竞态、高速赶路、YSM 各模型视觉与客户端同步 |
| 暂时不处理 | 不新增配方、不安装或启动游戏，不在本轮构造第三方物理 Mod 的专用集成；没有据此宣布所有历史行为问题已被实机验证解决 |

不要把用户对上一版本的良好体验或单个回放的命中数写成全局成功率。也不要把历史日志中的失败当作当前仍稳定存在的故障；先定位代码版本、配置、场景和真正完成的验证任务。

## 11. 按问题查文档与源码

| 问题 / 修改 | 先读 | 重点源码与验证 |
| --- | --- | --- |
| 不起飞、水中卡住、起飞立即急转 | [飞控总览](FLIGHT_CONTROL_OVERVIEW.md) | prepare / launchFlight / waterEscape、SurfaceLaunchPlanner；verifySurfaceLaunch、verifyControllerReplay |
| 盘旋、不攻击、错误返航/跟主人 | 飞控总览、配置参考 | approach / compactTurn / CombatAnchor / TargetEvaluator；完整生命周期回放 |
| 山腰、斜坡、窗口捕获、曲线提前离场 | 飞控总览、[攻击窗口](ATTACK_FUNNELS.md) | AttackFunnelPlanner、AttackLanePlanner、SlopeSampler、SweepingAttackPlanner / Rejoin；verifyAttackFunnels、verifyFunnelAsync、verifySweepingAttacks、verifySweepingReplay |
| 火箭补能迟滞/过冲/连续作战变差 | 飞控总览、[异步架构](ASYNC_PLANNING.md) | FireworkManager、manageEnergy、Pulse/Compact/SweepPulse slot 与兼容性；verifyCombatEntries、Controller replay |
| 主线程卡顿、任务一直 waiting、GC | 异步架构、[验证体系](VALIDATION.md) | capture / pump / enqueue、MaidTerrainSource、PlannerExecutor；verifyAsyncPlanning、verifyPlannerLoad，再做实机测量 |
| 远处唤醒、标记分配、落地回家 | 配置参考、飞控总览 | CommandManager、Registry、Tickets、TransitFlight、CommandHomeRecallMixin；verifyCommandDispatch、verifyDispatchReplay |
| 双击、友好目标、小碰撞箱 | 飞控总览、配置参考 | SpearPassHistory、MaidSpearCollisions、Spears mixins；verifyMaidCombat、verifySpearIntercept |
| HUD/卸载/多绑定/远程传送 | 配置参考 | DebugWatchManager、DebugPanelManager、Payload、ClientDebugState、Renderer；verifyDebugTools |
| 更新 TLM/Spears/YSM、模型姿态 | 配置参考、验证体系 | compat、mixin 清单与依赖字节码；verifyYsmCompatibility 等，不猜 API 名 |
| 发版、新机器构建、报告解释 | 验证体系、buildHistory | build.gradle、mods.toml、libs、Jar 内容/双版本、成功日志与实际任务数 |

每次重要修改收尾时，依次核对：本索引事实与导航 → 对应专题算法/线程/参数 → 验证入口与限制 → README 用户行为 → buildHistory 实际执行记录。若新增技术主题，先在本索引登记它的职责与阅读时机，避免出现无人可发现的孤立文档。
