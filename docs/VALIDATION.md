# 验证、离线回放与构建

核对日期：2026-10-05。实现基线：Gradle 项目版本 0.6.3。本轮扩展静止窗口、面积/垂直转弯、下降补能、标记起飞与服务端 Maid 排除检查；保留既有物理差分和全路线包络验证定义。

本文属于工程实现的一部分。修改验证入口、夹具、场景或发布方式时必须更新本文及 [项目索引](PROJECT_INDEX.md)。飞控语义见 [FLIGHT_CONTROL_OVERVIEW.md](FLIGHT_CONTROL_OVERVIEW.md)，并发保证见 [ASYNC_PLANNING.md](ASYNC_PLANNING.md)。历史构建与问题修复原因仍记入 [buildHistory.md](../buildHistory.md)，不以本文代替 changelog。

## 1. 工作约束与入口

**不在工作目录安装或启动 Minecraft。** 开发者运行编译、普通 JVM 验证和离线回放；用户在自己的游戏实例进行手动实测。现有工具里的旧客户端/GameTest 日志不代表当前应恢复启动游戏。

当前依赖 Java 21、Gradle Wrapper 9.2.1、NeoForge 21.1.250、TLM 1.5.3、Spears 1.8.0。可选 YSM 编译/字节码检查使用 `tools/ysm-reference.jar`。实际构建定义只有 main mod 和独立验证/回放任务，没有配置供当前使用的 runClient/GameTest 流程。

在仓库根目录用 PowerShell：

0.6.3 按用户要求只运行六项直接相关检查及编译/打包，**没有运行下方完整 build 示例**。具体任务与日志见第 7 节。

```powershell
# 完整发布检查：build → check → 所有 26 项 verify 任务
& .\tools\build.ps1 -Stage maintenance-release -Tasks @('build', '--continue')

# 有针对性的并发、几何和生命周期检查
& .\tools\build.ps1 -Stage maintenance-async -Tasks @(
    'verifyAsyncPlanning', 'verifyAsyncSweep', 'verifyPlannerLoad',
    'verifyControllerReplay', 'verifySweepingReplay', 'verifyDispatchReplay', '--continue'
)

# 排查某一个控制器场景；此命令不是完整发布验证
& .\tools\build.ps1 -Stage maintenance-follow -Tasks @(
    'verifyControllerReplay', '-Dwfs.replayCase=follow-continuous'
)
```

[tools/build.ps1](../tools/build.ps1) 只有 `-Stage` 与 `-Tasks` 两个参数，没有 `-ExtraArgs`。它在已安装路径存在时设置 JDK 21.0.10；为本机 Windows 的 AF_UNIX 限制设置 `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=F:/wfs-nonexistent-socket-dir`，让 JDK 回退 TCP；调用根目录 wrapper，日志写入 `tools/<Stage>.log`，显示尾部 65 行并保留 Gradle 返回码。移植到其他机器需核对本地 JDK/该环境专用设置，不把此路径当作项目必装目录。

`build` 同时构建 Jar 和检查；仅 `assemble` 或 `jar` 成功不代表回归通过。`test` 目前是 `NO-SOURCE`，真正检查是 26 个 `JavaExec` 任务，它们的 `main()` 抛 `AssertionError` 判失败，不依赖 Java `-ea`。需要强制复跑时按 Gradle 方式传 `--rerun-tasks`；不要同时启动多个构建去争用相同的 `build/classes/controllerReplay` 和报告文件。

## 2. 三类证据必须区分

| 层级 | 当前做法 | 能证明什么 | 不能证明什么 |
| --- | --- | --- | --- |
| 真实依赖编译/字节码检查 | `compileJava` 对真实 NeoForge/TLM/Spears API；部分 verification 读 ASM/API | 签名、访问点、静态引用、Mixin 预期目标是否匹配当前依赖 | Mixin 真正在游戏初始化成功、真实网络/事件行为、视觉效果 |
| 纯数值/决策验证 | `src/verification/java` 调生产数学、几何、排序、去重、配置/NBT逻辑 | 指定边界和场景中的算法断言 | 完整实体生命周期与真实世界 tick |
| 生产控制器离线回放 | `compileControllerReplay` 重新编译生产 Controller/Planner，外部 API 用夹具 | 状态机与规划器是否能从输入衔接到命中/退出/落地；真实异步池是否正常衔接 | 原生 Spears 伤害、真实区块异步加载、TLM AI 调度细节、客户端动画、服务器实际性能 |

数值片段测试不能代替生命周期回放。过去有“单段算法测试通过，但连续状态机一直盘旋”的回归；新增/修改状态时应有真实控制器从地面或明确外部起始状态开始的场景，不直接篡改 controller 私有状态来凑通过。

## 3. 当前全部 26 项 Gradle 验证任务

注册和 `check` 依赖以 [build.gradle](../build.gradle) 为准。表内类名可在 [verification 源码](../src/verification/java/dev/winefox/skyfarer) 或 [replay 源码](../src/replay/java/dev/winefox/skyfarer) 下按包找到。

| 任务 | 入口类 | 当前主要覆盖 |
| --- | --- | --- |
| `verifyScanCadence` | `ai.ScanCadenceChecks` | Brain 奇数 tick 回调、Entity/Brain 同 tick 去重、间隔动态缩短、时钟倒退 |
| `verifyFlightMath` | `flight.FlightMathChecks` | 掠过平面及长矛蓄力时机的基本数值边界 |
| `verifyVanillaPhysics` | `flight.VanillaPhysicsChecks` | 18,000 步真实 Mth/Vec3 源码 oracle 差分；float、epsilon、俯冲/拉升、重力/缓降/火箭时序及 Provider 身份/fallback |
| `verifyAttackEnvelope` | `flight.AttackEnvelopeChecks` | 上下攻击、悬崖绝对高度平移、长 Approach、不同活动火箭余寿命/未点燃库存/完整随机寿命、最差 reserve、出口阻挡与时域否决 |
| `verifyFlightPrediction` | `flight.FlightPredictionChecks` | 162 组有/无推进拦截、不同速度/高度/剩余火箭寿命、火箭选择和固定出口转向 |
| `verifyAttackPolicy` | `ai.AttackPolicyChecks` | 攻击周期保护、返航/失控范围、Charge 尾段范围预算、入口距离与对齐、重复恢复高度不累加 |
| `verifySpearIntercept` | `flight.SpearInterceptChecks` | 144 组原生尺寸长矛拦截，前摇、速度、姿态和接触预测 |
| `verifyMaidCombat` | `ai.MaidCombatChecks` | 小目标竖向容差、水平/射程边界、同轮去重、失败换目标、尾段与主目标不受损的多穿 |
| `verifyGlideCorridor` | `flight.GlideCorridorChecks` | 144 组攻击/蓄力与 48 组闭环走廊回放、真实身体扫掠、能量/推进/障碍拒绝 |
| `verifyTerrainCombat` | `flight.TerrainCombatChecks` | 坡面及陡坡/慢速攻击、有限火箭、计划离场、低屋顶起飞、目标层级、空中续战；慢速墙体风险不立即急刹 |
| `verifySurfaceLaunch` | `flight.SurfaceLaunchChecks` | 60 组冰/雪/水起飞及火箭寿命、低顶侧向脱离、封闭水体拒绝 |
| `verifyYsmCompatibility` | `flight.YsmCompatibilityChecks` | YSM 2.6.5 参考 Jar 的 5 个可选注入入口及其女仆缺少原生 elytra 状态；不是动画实机测试 |
| `verifyCombatRecovery` | `flight.CombatRecoveryChecks` | 离场长度为旧计算的 2/3、Charge 不延迟重新评估、同轮迟到命中/失败结算、16/32 格密集坡面采样、分数高度支撑 |
| `verifySorties` | `flight.SortieChecks` | 紧凑动能换高度转向、竖向曲线俯冲、左右二段追击和被挡拒绝；这是分段物理检查 |
| `verifyCommandDispatch` | `command.CommandChecks` | 五项 FIFO/切换、归属/范围/战略排序、指挥交接、有限票据、创造驻留条件、预测倍率和真实望远镜/Home API 字节码 |
| `verifyDebugTools` | `runtime.DebugToolsChecks` | 多绑定/循环切换、卸载快照保留/重载/解绑/退出、TLM 名册和跨维度传送签名、公共服务端代码不引用客户端 GUI |
| `verifySweepingAttacks` | `flight.SweepingAttackChecks` | 五类三次曲线路径、C1 连续、当前三维动量切线、山肩遮挡、最终长矛射线与身体拒绝、曲线进度掠过判定 |
| `verifyControllerReplay` | `replay.ControllerReplayChecks` | 21 个完整生产空战控制器生命周期场景及洞穴/可见上身/offset 索敌边界，详见下节 |
| `verifyDispatchReplay` | `command.DispatchReplayChecks` | 7 组生产赶路→生产空战→命中→落地，含友好标记、900 格及创造 100 倍速 |
| `verifyCombatEntries` | `replay.CombatEntryReplayChecks` | 山峰低能量接近不长期停滞；由下向上进场可以命中后落地 |
| `verifySweepingReplay` | `replay.SweepingAttackReplayChecks` | 两个左右镜像遮挡山腰场景，实际 Controller 接受曲线、命中、继续曲线离场并落地 |
| `verifyAsyncPlanning` | `async.AsyncPlannerChecks` | 不可变快照、世界边界坐标键、未知拒绝、导航范围、实际 Worker、配置冻结、主线程所有权、generation/目标/阶段/维度/配置失效、取消、有界队列、重启、接触结果年龄/姿态容差 |
| `verifyAsyncSweep` | `async.AsyncSweepChecks` | 完整 snapshot 可生成山腰曲线；移动女仆冷快照可在有限 tick 内取得候选。**不独自证明候选可安全执行** |
| `verifyPlannerLoad` | `async.PlannerLoadChecks` | 24 个 session、100 ticks 的合成异步调度；方块读取/队列/每女仆任务和 cell 上限、所有会话获得采集、有效结果、停止清理 |
| `verifyAttackFunnels` | `flight.AttackFunnelChecks` | 面积、俯冲/上升/各自持续转弯、山腰、反向物理否决、下游截面捕获、低速门槛、前摇过冲、最小安全火箭全部 12 寿命、coast 优先 |
| `verifyFunnelAsync` | `async.FunnelAsyncChecks` | 冷快照不可执行、100 tick 连续合法窗口、同 Input 补空槽/无变化身份、证明覆盖与上下文失效、地面预热、coast/pulse/下降控制器 Capture→Rejoin→命中、近距指令立即选择及异步安全起飞 |

`verifyDebugTools` 显式使用 [offline-log4j2.xml](../tools/offline-log4j2.xml)，避免依赖 Jar 的游戏日志配置。API/字节码类使用编译依赖作为验证 classpath；其他纯算法任务不会引导一个游戏实例。

## 4. 生产回放如何工作

[compileControllerReplay 定义](../build.gradle) 从 main 源码直接编译：

- `ai/*.java`、`flight/*.java`、`async/*.java`；
- `SkyfarerConfig`、`SkyfarerSettings`、`DebugSnapshot`；
- `TransitFlight`、`DispatchPolicy`、`CreativeTransitPlanner`。

其中生产 `ElytraFlightController` 和 `MaidTerrainSource` 排除，由 `src/replay/java` 中的同名外部世界适配替换。编译输出独立放在 `build/classes/controllerReplay`，classpath 不引用真实游戏启动代码。`src/replay` 不属于 main source set，不进入交付 Jar。

夹具提供 EntityMaid、LivingEntity、库存、Vec3/AABB、方块盒、加载边界、世界时间、烟花实体、TLM/Spears 适配等。生产 `FlightCorridor` 判断身体扫掠；生产 `FlightPrediction` 方程推进每 tick 位移，实际 `FireworkManager` 选择推进；起跳夹具提供初速度和重力。命中夹具仍要求前摇、使用长矛、射线接触与无遮挡，然后调用生产命中回调，但不运行原生 Spears 的全部伤害系统。0.6.1 的四类生命周期火箭夹具记录 createdAt，下一实体 tick 才首次推进/递增 life，对齐 mapped EntityTickList；完整原版公式另外由独立差分 oracle 检查。

回放使用**真实共享异步线程池**，没有用同步 executor 冒充后台计算。`ReplayWorld.advance()` 推进时钟、调用 `PlannerSession.serverTick()`，默认等待 50 ms 给 Worker 运行；结果到达 tick 会随机器负载改变。`wfs.replayTickMillis` 是回放 JVM 的调试属性，当前 Gradle 任务未像 `wfs.replayCase` 一样转发它，不能假设给 Gradle 加同名 `-D` 会自动作用到 JavaExec。

`verifyControllerReplay` 的 `wfs.replayCase` 按场景名精确筛选；空值运行全部。发布验证必须不带筛选，否则“任务成功”仅代表选中场景通过。

### Controller 场景清单

`flat-near`、`flat-normal`、`flat-behind-long-pulse`、`home-continuous`、`flat-short-pulse`、`flat-longest-pulse`、`flat-far`、`follow-continuous`、`slope-cycle`、`small-target-cycle`、`target-despawn`、`task-cancel`、`snow-layer-cycle`、`mixed-partial-cycle`、`slab-cycle`、`ice-inertia-cycle`、`radius-32-cycle`、`surface-and-cave`、`summit-cycle`、`uphill-cycle`、`upward-pillar`。

主体从地面开始，由生产控制器获取目标、起飞、选入口、攻击、离场、续战/返航并落地。目标消失/工作取消只改外部输入。记录同 tick 中间状态；对非计划触地、身体碰撞、长期无命中、过高/过远、紧急传送代替完成流程、落地后继续补能等判失败。额外索敌检查覆盖只有地下敌人不应起飞、隐藏历史目标不应优先、上半身可见允许选择及实时瞄准偏移。

`CombatEntryReplayChecks` 和 `SweepingAttackReplayChecks` 为专门空中初始条件场景，不宣称独自覆盖地面起飞；完整地面链由 Controller/Dispatch 回放覆盖。

### 远程调度场景

7 个配置包括 180 格正向、900 格反向雪层、256 格侧向、180 格友好标记、900 格创造 2 倍、900 格创造 100 倍、180 格友好创造 2 倍。断言免费赶路不扣库存、交接清除免费推进、攻击/落地仍由生产控制器决策，检查交接范围及无非计划触地。

世界预先提供 fixture 加载地形；不模拟 NeoForge 实际加载票据、磁盘读盘、实体唤醒延迟或 TLM 存档恢复。普通赶路和创造高速赶路的交接距离不同，不能把旧 [src/replay/README.md](../src/replay/README.md) 的“48 格交接”概括为所有现有场景。精确行为以 `DispatchPolicy` 和 [飞控总览](FLIGHT_CONTROL_OVERVIEW.md) 为准。

## 5. 异步专测的真实证明范围

`verifyAsyncPlanning` 用生产 `PlannerExecutor`/`PlannerSession`，检查 sealed task 边界、主线程 Source 调用、错误线程访问拒绝。测试将 live 预测倍率 supplier 改成抛异常，确认 Worker 使用提交时冻结值。它也在冷快照下确认 discovery 没有逃逸成安全执行结果，覆盖队列满拒绝和停服/重启。

`verifyAsyncSweep` 中完整 snapshot 首先直接检查纯数值曲线，然后用 session 和真实 Worker 从冷数据开始获取候选。后面打印 `review=... hit=...` 是诊断，当前代码只断言“取得候选”，没有断言打印的 review 必须安全。既有 release 日志中出现 `review=false hit=true` 并不代表“已安全接入”；旧几何重新接入认证由生产 `Rejoin` 和完整 Controller/Sweeping 回放覆盖。不要用此任务单独宣称实机全程通过。

`verifyPlannerLoad` 的 Source 返回合成空碰撞 cell，以默认每 cell 216 reads 计费，循环每 tick 等待 10 ms。它检查上限和没有会话完全饿死，**没有证明每位女仆在任何机器/地图上都能在固定 deadline 内完成规划**。该报告不是 Minecraft TPS、GC、真实 block-shape 采集或优化前后对比。输出里的 `worker CPU` 实际来自 `System.nanoTime()` 累加任务墙钟时间，不能当作 CPU profiler 统计。

## 6. 报告、工具与故障定位

| 位置 | 用途 |
| --- | --- |
| [buildHistory.md](../buildHistory.md) | 每次版本构建/修复记录、实测依据、交付 Jar/哈希和验证边界 |
| `tools/<Stage>.log` | 原始 Gradle 与检查 stdout/stderr；Stage 名不保证成功，必须查看实际结尾 |
| `build/reports/controller-replay/` | 回放逐 tick TSV 与 `.events.log`，同 tick 状态切换、命中、火箭、轨迹等；后续运行会覆盖相应场景 |
| [async-planner-load.txt](../build/reports/async-planner-load.txt) | 最近一次合成调度报告；构建产物，清理 build 后可不存在 |
| `build/reports/problems/` | Gradle problems 报告，不是游戏飞控报告 |
| `tools/minecraft`、`tools/tlm`、`tools/spears`、`tools/ysm` | 当前依赖参考/反编译资料，帮助核对实际 API；不能代替正在编译的依赖 Jar |
| `tools/cfr.jar`、`tools/ysm-reference.jar` | 反编译工具与只读 YSM 接口参考，不打入 Mod |

排查顺序：先看失败任务/断言和最后有效状态，再读对应 TSV/events，核对参数及源代码。并发修改至少看 `waiting/gen/requested/jobs/queue/stale/failed`；区别“未取得快照”“结果不兼容”“搜索无路线”“立即碰撞防线拒绝”，不能一概增加超时。具体指标语义见 [异步架构](ASYNC_PLANNING.md#10-诊断指标与验证入口)。

改变数值算法先跑对应小型任务，状态或 Planner 接口变化再跑相关 Controller/Dispatch/Sweeping；验证范围遵循当前任务要求，不为了保险自动扩大为完整 `build`。一旦发现新类型失败，应添加有意义的反例并覆盖恢复/收尾，不只放宽断言使当前日志通过。

## 7. 当前构建证据与已知缺口

0.6.3 本轮六项为 `verifyAttackFunnels`、`verifyFunnelAsync`、`verifyAsyncPlanning`、`verifyCommandDispatch`、`verifyDebugTools`、`verifySweepingReplay`。首轮 [v063-targeted.log](../tools/v063-targeted.log) 成功（42s）；垂直持续转弯、下降回放与后台地图补充在后续迭代完善。`v063-regression.log` / `v063-final.log` 含中间失败，不能当作全部成功的交付证据。`v063-final.log` 中数值 Funnel、指挥镜、Debug、异步边界和双侧山腰已通过，但静止地图刷新断言发现冷快照子集替换，随后修复为保留合法窗口、仅补空槽。修复后的 [v063-cache-final.log](../tools/v063-cache-final.log) 成功（49s），重跑编译、FunnelAsync、AsyncPlanning 并打包。

释放失效地图的旧 footprint 后，[v063-delivery.log](../tools/v063-delivery.log) 成功（1m 13s）：编译、FunnelAsync、AsyncPlanning、双侧山腰与 jar；左右山腰均第 53 tick 命中、第 143 tick 落地。最后将 Capture 提前到旧 ascent staging 查询之前，[v063-capture-priority.log](../tools/v063-capture-priority.log) 成功（41s），编译、实际 Capture/下降/起飞回归及最终打包通过。

0.6.2 的历史针对性结果保留于 [v062-targeted.log](../tools/v062-targeted.log) 和 [v062-capture-final.log](../tools/v062-capture-final.log)，不作为本轮验证。

0.6.1 的历史完整构建保留于 [tools/v061-acceptance.log](../tools/v061-acceptance.log)，当时 24 项全部通过（含 21 个 Controller、7 个 Dispatch、2 个特殊进场、2 个镜像曲线、18,000 步物理差分）；这不是本轮重新执行的证据。

最终 Jar 大小、SHA-256 与逐项证据记录在 [buildHistory](../buildHistory.md)。`tools/funnel-*.log` 属于 0.6.2 迭代记录；本轮 `v063-first/regression/final.log` 也含中间失败，应结合最后修复后的成功日志查看。

本轮 Gradle / Mod 元数据版本为 0.6.3。Jar 内容、实际版本及哈希核对结果见 buildHistory；没有运行 build/check 或其他 20 项 verify，未启动 Minecraft。

下次交付至少检查：

1. `build.gradle`、Jar `META-INF/MANIFEST.MF` 和 `META-INF/neoforge.mods.toml` 的实际版本一致。
2. Jar 中有预期资源、语言、模型、Mixin JSON，且不含 `src/replay`/verification 的类、模拟 `net.minecraft`/TLM API 或依赖 Jar 内容。
3. 对**最终** Jar 记录大小与 SHA-256，构建日志确实成功且未筛选掉场景；信息写入 `buildHistory.md`。
4. README 与 docs 的行为、限制和验证入口与最终源码一致。

## 8. 必须实机确认的事项

- Funnel 在起飞/已加载目标的赶路阶段是否及时出现，活动目标与动态地形下是否合理刷新，窗口捕获是否减少绕圈；紫色截面与已有黄/青绿线的区分、多女仆地图缓存与 Worker 等待开销。

- gravity/noGravity、缓降到期、魂符收起后跨维度放出、同名 Level 重建以及其他 Mod 修改 travel 或实体 tick 顺序时的真实行为。
- 真正 NeoForge/TLM/Mixin 生命周期、服务端关服/重新开世界、实体卸载重载/跨维度、玩家退出时序。
- 创造指挥镜的未加载女仆唤醒、滚动票据、目标区块卸载、Home 回收、真实传送的安全落点和资源释放。
- 客户端网络、望远镜左键/缩放、标记动效音效、Debug 多女仆切换、穿墙框、HUD、预测走廊/曲线渲染。
- TLM 原生模型和可选 YSM 模型的实际鞘翅动画/装备显示；接口匹配不能保证每个模型提供动画。
- 原生 Spears 伤害、友好保护、附魔/第三方模组交互、扩展命中是否符合用户体验。
- 真实复杂世界与模组碰撞形状、动态方块、液体、活动目标、外部击退、网络和 chunk 延迟。
- 不同 CPU、女仆数量、预测倍率和创造高速赶路下的 MSPT、队列等待、GC/分配、内存与加载开销。应使用真正 profiling 做前后对比，不能由合成负载推导提升百分比。

离线通过是可重复的工程门槛，不是上述实机行为已经确认的替代证据。收到用户实测问题后，应保留日志、参数、场景和 Mod/依赖版本，再决定补充哪一个可复现的离线反例。
