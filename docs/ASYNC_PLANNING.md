# 异步规划与线程边界

核对日期：2026-10-05。实现基线：Gradle 项目版本 0.6.0。本文描述当前源码；元数据版本不一致见 [验证文档](VALIDATION.md#7-当前构建证据与已知缺口)。

本文是工程实现的一部分。修改调度、快照、任务类型、缓存、线程边界或结果应用规则时，必须同步更新本文及 [项目索引](PROJECT_INDEX.md)。源码是最终权威，发现文档冲突须修正文档。飞行行为与状态转换见 [飞控总览](FLIGHT_CONTROL_OVERVIEW.md)，配置/调试入口见 [配置与调试参考](CONFIG_DEBUG_REFERENCE.md)。

## 1. 当前架构

异步化的是预测和搜索，实体控制仍由服务端主线程拥有。不是把整个 `AirCombatController.tick()` 搬到后台，也不是给每位女仆创建线程。

```text
服务端主线程
  EntityTickEvent.Pre / CommandManager
    → AirCombatController 或 TransitFlight
    → AsyncPlanning：采集当前状态、目标、配置，构造数值任务
    → PlannerSession.query：取兼容缓存 / 提交有界任务 / 返回 Pending
         ↑                            ↓
  ServerTickEvent.Pre                MaidTerrainSource
    → 分帧 pump                     → 数值碰撞盒、液体、加载标志
    → 收取结果 / 请求缺失单元          → 不可变 CollisionSnapshot
                                      ↓
共享 Worker 池：PlanningTask.compute(Space)
  数值地形、身体扫掠、候选、预测、命中/能量/离场评分
    → Ticket.outcome（volatile 发布的结果邮箱）
                                      ↓
服务端主线程：generation + 请求兼容性 + 年龄 + 当前运动状态校验
  → 必要时重新验证曲线接入
  → 姿态、火箭、物品、状态机、实际伤害
  → 当前真实位移的主线程碰撞防线
```

没有主线程 `Future.get()`，也没有 Worker 完成回调直接操作女仆。所谓“结果队列”在实现中是每个 `Ticket` 的 `volatile Outcome` 加主线程持有的 `pending` 表；主线程主动收取。

## 2. 代码与所有权

| 代码入口 | 职责、输入与输出 | 所属线程与生命周期 |
| --- | --- | --- |
| [AsyncPlanning](../src/main/java/dev/winefox/skyfarer/async/AsyncPlanning.java) | Controller 的适配层；从实体控制代码接收数值状态，构造 sealed `PlanningTask`，给出结果或安全等待用的局部控制 | 主线程；空战控制器持有一份，赶路控制器按需创建一份；释放时关闭 session |
| [MaidTerrainSource](../src/main/java/dev/winefox/skyfarer/async/MaidTerrainSource.java) | 唯一异步快照世界适配器；将真实碰撞形状/水体转换成数值盒并捕获身体尺寸、世界边界 | 仅创建它的主线程；持有 `EntityMaid`，绝不能交给 Worker |
| [CollisionSnapshot](../src/main/java/dev/winefox/skyfarer/async/CollisionSnapshot.java) | 不可变 cell map、数值身体尺寸、世界边界、水面逃逸标志；创建任务私有 `Space` 查询游标 | 快照可安全跨线程；`Space` 的缺失集合/探索标志只由单个计算使用 |
| [PlanningTask](../src/main/java/dev/winefox/skyfarer/async/PlanningTask.java) | 封闭的 DTO 任务集合；封装纯数学输入，调用 `flight` 规划器并返回数值结果 | 在主线程构造、Worker 执行；不允许携带任意调用方 callback |
| [PlannerExecutor](../src/main/java/dev/winefox/skyfarer/async/PlannerExecutor.java) | 全服共享线程池、背压、取消、结果发布；冻结预测倍率 | Worker 执行纯计算；提交/关闭由主线程生命周期驱动 |
| [PlannerSession](../src/main/java/dev/winefox/skyfarer/async/PlannerSession.java) | owner 检查、generation、任务合并、快照采集预算、缓存、结果验证 | 仅 owner 主线程；Worker 不持有 session/source/controller |
| [PredictionHorizon](../src/main/java/dev/winefox/skyfarer/flight/PredictionHorizon.java) | 在任务进入时用 ThreadLocal 固定倍率，结束时 remove | 主线程读取 live 配置；Worker 只读取任务冻结值 |

`AsyncPlanning.BoundedCorridor` 的 `live` 碰撞回调只在主线程存在。构造 Worker 的 `PlanningTask.Bounded` 时只复制数值 `Bounds(x,z,radius)`，并在 Worker 内绑定其自己的 snapshot space。旧调用签名中的 corridor/support/contact 参数不表示这些实时回调会进入任务。

## 3. 任务类型与实际分工

| DTO | 后台执行内容 | 结果及重要约束 |
| --- | --- | --- |
| `Glide` | `GlidePlanner.choose`、forecast、trace，沿轨迹额外评估狭窄净空 | `GlideResult` 包含姿态、预测、轨迹和净空成本；调试复用同一轨迹 |
| `Pulse` | 检查指定烟花 Flight 的完整随机寿命区间 | `PulseResult` 包含成本/原因和按实际寿命索引的已验证首步计划；点火可直接使用该姿态 |
| `Compact` | 紧凑爬升减速转向选择，或该转向下补能成本 | 转向方案或有限成本；执行时用当前状态重算便宜的首步指令 |
| `Launch` | 地面/水面起飞候选、剩余推进重评估、起飞补能验证 | `SurfaceLaunchPlanner.Plan` / 成本；`wet` 为数值模式标志 |
| `Slope` | `SlopeSampler.sample`，真实支撑面近/远坡度拟合 | `Estimate`；未知列不凭空产生坡面 |
| `Lane` | `AttackLanePlanner` 的直线、坡面、竖向曲线入口与出口几何 | 主线程只接收并设置 lane/entry/exit 等元数据 |
| `Exit` | `AttackExitPlanner` 离场候选与安全评分 | `Choice`；不在 Worker 切换状态 |
| `Chain` | 主攻击接触预测或次要目标二段追击 | 数值接触状态 / 追击计划；实体筛选和实际目标引用仍在主线程 |
| `Sweep` | 无 path 时生成连续转弯候选；有 path 时回放当前曲线跟踪 | `Selection` 或 `Rollout`；命中段射线查询数值碰撞盒 |
| `SweepPulse` | 将一枚火箭所有 12 种随机寿命在一个任务中评估 | 任一寿命不安全/不能命中则不可行，否则按平均成本及推进时长评分；避免每种寿命分别排队造成饥饿 |
| `Rejoin` | 用当前运动状态重新验证候选曲线接入；可选择增加出口净空和经验证的库存火箭 | `RejoinResult`；`duration=-1` 表示不新增火箭；此后仍由主线程实际点火 |
| `Bounded` | 给上述任务加数值水平导航范围 | 不含 live 世界 callback |

任务 slot 不是单纯“女仆 UUID”：包含类型、变体及选项。`Pulse` 的出口位置、`Chain` 的目标位置、`Launch/Compact` 的火箭种类、`Sweep` 的候选/跟踪区别参与区分，防止同轮多个候选互相取消。更改 slot 时要检查“同类多个合法请求并存”及重复提交是否受限。

## 4. 共享池、背压与取消

- 总线程数为 `max(2, min(4, availableProcessors - 2))`。其中 1 个专门负责几何候选，余下 1–3 个处理控制预测；线程名 `WFS-Planner-N`，daemon，空闲 30 秒允许退出。池是惰性建立，不是每 tick 建线程。
- 几何池接收 `Slope`、`Lane` 和没有 path 的 `Sweep`；其余任务进入控制池。两个池分别 `ArrayBlockingQueue(32)`，排队总上限 64；这不含正在执行的 2–4 个任务。
- 拒绝策略是 `AbortPolicy`。submit 捕获队列满异常并返回 null、累计 `rejected`；主线程走等待/回退，**不能改为 `CallerRunsPolicy` 或同步重算**。
- 单 session 最多 24 个 pending slot。兼容的在途任务在 6 ticks 内继续复用；不兼容或太旧时取消并替换。没有为每次查询创建 Future。
- `Ticket.cancel()` 设置 AtomicBoolean，移除仍排队的任务。运行中的计算在 `Space.get()` 查询碰撞 cell 时检查取消和中断并协作退出；不是任意 Java 指令处强制终止。
- Worker 将完成结果写入 volatile outcome。取消结果不应用；计算的 RuntimeException 作为 failure 返回，由主线程丢弃并限频记录 `[WFS] Async planner failed; result discarded: ...`。相同错误至多每 200 个采集 epoch 重复报告，错误内容改变可立即报告。
- 服务停止调用 `PlannerSession.stop()`，关闭所有 session 并 `shutdownNow()` 两个池、取消排队票据。下次提交可重新建池。

当前没有显式的任务超时中断 watchdog。过期替换采用协作取消；只做长时间纯数学、很久不访问 `Space` 的新算法应自行加入取消检查，不能假设任何任务都可立即停止。

## 5. 快照采集、真实形状与未知空间

### 数据格式

每个 cell 为 `4×4×4` 方块单元，坐标键按有符号 26/12/26 位保存 x/y/z，覆盖原版世界边界。`Cell` 只含：

- 固体数值 `FlightCorridor.Box` 列表；
- 水体数值盒列表；
- 64 位水体位置掩码；
- `loaded` 标志。

列表及 map 使用不可变拷贝。World、Entity、BlockState、VoxelShape、CollisionContext 都留在主线程。快照身体尺寸通常取女仆当前尺寸；起飞 snapshot 使用 0.6 高、0.4 眼高，宽度仍从实体取。真实执行前的安全扫掠再次使用实时尺寸。

`MaidTerrainSource.capture()` 在单元外增加一格 halo：最多读取 `6³=216` 个方块，防止围栏或动态碰撞形状跨出所属方块遗漏。使用 `getCollisionShape(...).toAabbs()` 原小数尺寸，不按方块名称硬编码雪层、半砖或台阶高度。水记录到 fluid，非水液体并入障碍；水面逃逸的 space 可以通过水体。支撑面由 `SupportSurface.nearest` 在数值盒中找真实可站立/可容纳身体的表面。

加载检查使用 `getChunkNow` / `hasChunkAt` 及实体 ticking 状态，不为碰撞查询主动加载区块。包含未加载或不可实体更新位置的 cell 是 `loaded=false`，始终不可作为安全走廊。全 air section 可以直接生成空 cell，跳过逐方块扫描；空区段和加载检查本身仍有主线程开销。

### 分帧预算与缓存

以下是代码常量，不是当前 `/wfs config` 参数：

| 范围 | 当前限额/规则 | 解读 |
| --- | --- | --- |
| 全服每 `ServerTickEvent.Pre` 重置 | 256 cells、21,600 次方块读取、累计采集耗时 2 ms | 三者任一到限停止继续采集；时间是软上限，单元不可拆分 |
| 活跃 session 公平份额 | 每 epoch 最多 `max(1,256/participants)` cells | 参与者为仍有 pending/requested 或最近 2 epochs 查询的 session；轮换处理起点 |
| 待采集 cell | 每 session 最多 512 | 选中走廊优先，其次邻格/其他候选/刷新；近当前位置者先 |
| 已缓存 cell | 每 session 最多 768 | 按 LinkedHashMap 插入顺序淘汰；不是完整 LRU |
| 刷新 | cell 年龄达 20 ticks，每次 pump 最多加入 8 个刷新请求 | 受全局预算继续限制，不保证第 20 tick 已刷新 |
| 过期 | enqueue 时移除年龄大于 80 ticks 的 cell | 已提交快照不可回写；当前真实位移仍需 live guard |
| Worker 缺失记录 | 每个 Space 最多 192 个不同 cell | 单次缺失集有限；严格计算与发现计算可以分别产生集合 |

预算只统计 `source.capture()` 耗时和其报告的 block reads，不包含排序、结果收取、目标扫描、metadata、实体操作、碰撞防线或全 Mod tick。非空 cell 最多 216 次显式 `getBlockState`，形状实现内部额外世界查询未被 `lastReads` 逐项计数。因此“2 ms”不是全 Mod MSPT 承诺，21,600 也不是所有引擎访问的总数。

快照 map 仅在 cell 数据改变时重新冻结；多个任务可共享同一不可变 map。选中候选/预测轨迹的缺失 cell 会优先采集，并预取六方向一格 halo，以降低“女仆已经飞到尚未采集的下一个单元”造成的反复等待。紧急请求与后台请求交错服务，避免几何候选永久饥饿。

### 严格计算与发现计算必须分开

1. 正常 `Space`：缺失 cell 不能判安全；记录请求。明确 `loaded=false` 同样不可通行。
2. 若严格计算遇到缺失，Worker 再用 `explore()` 做发现计算：只把“快照里还没有的 cell”暂时透明，用来发现所需路线及后续应采集的 cell；真实已知障碍、未加载标志、世界边界仍生效。
3. 发现计算产生的方案 **不作为执行结果发布**。outcome.value 始终来自第一轮严格计算；发现方案仅决定 `missing/preferred`。
4. 主线程补数据后重新提交。若只是其他候选缺数据，而已选结果自身已被严格认证，可以保留该结果，不必等所有候选空间齐全。

`usable()` 按结果类型判断是否可留入缓存（例如滑翔 plan 与 forecast 都安全、火箭成本有限、跟踪 rollout safe 且 hit）。这不是允许猜测未知空间。修改发现过程时必须用 `verifyAsyncPlanning` 的冷快照反例守住这条边界。

## 6. Generation、旧结果与缓存

`PlannerSession.begin(tick, phase, target, revision, horizon)` 比较逻辑阶段、目标和配置版本。不相同就增加 generation，取消 pending，清除结果缓存和几何 seed；维度改变还清空地形 cell/requested/frozen map。session/source 有创建线程断言。

空战的逻辑阶段有意合并：

- `APPROACH / REPOSITION → ENTRY`；
- `ATTACK_RUN / DIVE / SPEAR_CHARGE → ATTACK`；
- `TAKEOFF_PREP / TAKEOFF → LAUNCH`；
- 其余使用实际枚举名。

因此并非每个枚举转换都取消全部计算；同一逻辑阶段仍靠任务参数和运动状态检查保证兼容。目标由当前可攻击的 `runTarget`，否则 `preferred` 的 UUID 表示。控制器每 tick 开头更新 planning context；已经 managed 时，`transition()` 若改变逻辑阶段且新状态不是 GROUNDED，也立即同步上下文。赶路阶段键为 creative/water 模式加目标 `BlockPos`，它与空战 UUID 策略不同。

配置 revision 在设置、重置和 reload 时递增；倍率还在提交任务时独立冻结到 `PredictionHorizon` ThreadLocal。不能在 Worker 中读取 live `SkyfarerSettings`。

| 结果/工作 | 当前复用门槛 |
| --- | --- |
| 普通结果 | 基于原任务提交 tick，最多 4 ticks；请求兼容；当前位置差 ≤4 格、航向差 ≤24°、速度各分量绝对差之和 ≤1.2 |
| 坡面 `Slope` | 最多 20 ticks；目标采样中心误差 ≤0.5 格、采样半径/步长一致 |
| 候选几何 `Sweep(path=null)` | 最多 12 ticks；位置差 ≤16 格、航向差 ≤90°；作为几何候选，不能直接承诺攻击 |
| 几何 seed | 在兼容的候选 slot 内固定最多 12 ticks |
| `Rejoin` 接触认证 | 在一般门槛之上，年龄 ≤2 ticks，位置差 ≤2.5 格，yaw/pitch 差各 ≤8°，速度 L1 差 ≤0.35 |
| Pending 复用 | 6 ticks 内且兼容；否则取消并换新 |
| Debug trace | 仅读 generation 相同、年龄 ≤8 ticks 的完成缓存，不新提交预测 |

上述 age 从 **提交时刻** 算，不能从 Worker 完成时刻重新延长。cache 最多 96 条。普通 Glide 返回兼容缓存时会滚动提交下一次刷新，避免一个姿态僵持完整 4 ticks。

`PlanningTask.compatible` 还比较目标位置、目标速度、前摇、攻击范围、重力/转向率/碰撞余量等相关字段。例如 Aim 位置容差 1 格、速度每轴 0.2、前摇 4 ticks；不是只看 UUID。Compact 的当前恢复原点允许 2 格小幅变化，避免每 tick 移动让补能结果永远失配；远距离传送仍被拒绝。

曲线候选返回后必须走 `Rejoin`，用此时真实运动状态重新滚动预测并检验 safe/hit；不允许拿 12 ticks 前的几何选择直接点火切攻击。任务参数、曲线身份和火箭状态变化也参与兼容性检查。运行中真实方块可能改变，快照不会即时感知，仍依赖下述主线程执行防线。

## 7. Pending 时怎样继续飞

`Pending` 是单例、无堆栈异常，用于“尚无可用结果”，与“算完但无可行路线（null/∞）”不同。

- Lane、起飞、Exit 等必需结果会向上抛出 Pending，由控制器处理；等待起飞不耗尽地面准备计时。
- 一般 Glide 使用便宜的反馈俯仰作为暂时指令，safe=false；forecast 未到则不宣称安全，补能成本未到为 ∞。
- Compact 和已经接受曲线的跟踪会根据**当前**状态重算局部首步控制，而不是重复套旧首步命令或重新搜索整组候选。Compact 使用标量反馈；曲线 `command()` 仍通过 `Path.nearest()` 遍历有界采样段，因此不能称为严格 O(1)。曲线跟踪的暂时结果不是新的 safe-hit 承诺，不得据此接受一条新攻击路线。
- 连续转弯候选等待时，控制器最多 8 ticks 保持向目标的温和转向/当前高度，并继续管理能量；pending 每 tick 领取，完成搜索后再采用 12-tick 重评估节奏。
- 等待 ATTACK_EXIT 方案时先用反馈俯仰朝出口/较高目标控制，避免继续沿用上一阶段俯冲姿态。
- 补能通过完整寿命认证后可缓存每种实际寿命的首步姿态，主线程生成烟花后立即匹配使用，减少“新火箭配旧姿态”。

## 8. 必须保留的主线程工作与限制

目前仍在主线程：

1. 实体获取/存活/权限/目标可见性/装备/库存检查，目标扫描和优先级，状态转换和 TLM 移动接管。
2. 当前实体状态、目标运动数值及世界碰撞形状采集。
3. yaw/pitch、pose、使用物品、火箭实体生成/移除、实际伤害及命中回调。
4. `validateImmediateMotion()` 的真实下一步身体扫掠；试当前俯仰、-35/-65/-88°，必要时有限的 180°反转。LANDING 不使用这套飞行防线，避免阻止计划触地。
5. 当前地面净空、着陆射线/落点、安全传送以及实际 Spears 接触射线。
6. `TransitFlight` 中创造高速赶路的 `CreativeTransitPlanner.choose` 和当前实际移动长段扫掠。这条特殊赶路分支还会在主线程调整速度，普通战斗滑翔不这样做。100 倍速的长段世界查询仍可能昂贵。
7. 结果收取、排序、缓存冻结、网络 debug 打包等管理开销。

安全含义是“远期重计算移出主线程，并在执行点再次验证”，不是“主线程不再碰撞查询”或“得到快照方案就保证以后绝不碰撞”。快照采用时间刷新，尚无方块变更事件驱动的精确失效；动态地形/实体体积变化、极慢机器的结果延迟、实际高倍预测内存压力仍需实机观测。

## 9. 线程安全红线与扩展步骤

禁止：Worker 读取/写入 World、Level、Entity、BlockEntity、BlockState、VoxelShape；捕获 maid/world 的 Supplier 或 lambda 进入任务；Worker 修改库存、伤害、状态机；主线程等待结果完成；队列满同步重算；为了缺失快照调用 World；将 discovery 方案冒充已认证路线。

新增规划器应按以下顺序做：

1. 明确主线程输入和最小数值 DTO，放入 sealed `PlanningTask`；把物理参数和配置一起冻结。
2. 使用 Worker 私有 `Space`，缺失/未加载必须失败并请求采集；需要范围时复制数值 Bounds。
3. 定义 slot、兼容条件、generation 所属阶段、缓存年龄；有接触承诺的方案增加当前状态认证。
4. 定义 Pending、队列满、取消、目标消失/移动、维度/配置改变时的主线程行为。
5. 控制实际效果只从主线程应用，保留真实下一步/伤害检查；Debug 复用结果。
6. 更新本文和 [验证体系](VALIDATION.md)，运行边界检查及完整控制器回放，不能只证明纯算法输出正确。

## 10. 诊断指标与验证入口

HUD 的 `planner_status` 包含 waiting/ready、session generation、cells/requested/jobs/cache；还显示全局 queue/cache/stale/failed/captureMs/workerMs。其计数多数是进程累计值，不是每女仆本 tick：

- `stale` 包含 invalidation 时取消的 pending 数，并不等于“已完成但过期结果”数量；
- `captureMs` 是累计 capture 墙钟时间；
- `workerMs` 是所有 Ticket 的 `System.nanoTime()` 墙钟耗时相加，不是 CPU profiling；
- 全局计数在 stop 后没有统一清零，不应用它们直接推断新世界当前 tick 性能。

优先组合 `verifyAsyncPlanning`（安全边界）、`verifyAsyncSweep`（冷快照获得候选）、`verifyPlannerLoad`（有界调度）、`verifyControllerReplay` / `verifySweepingReplay` / `verifyDispatchReplay`（真实控制器衔接）。具体覆盖与限制见 [VALIDATION.md](VALIDATION.md)。尚无实机 MSPT/GC 的前后基准，不能把合成空世界报告当作提速百分比。
