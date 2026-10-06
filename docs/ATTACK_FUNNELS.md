# 目标攻击窗口与主动捕获（0.6.2）

本文件描述当前源码；入口见 [PROJECT_INDEX](PROJECT_INDEX.md)，物理见 [VANILLA_PHYSICS](VANILLA_PHYSICS.md)，线程规则见 [ASYNC_PLANNING](ASYNC_PLANNING.md)。

## 为什么增加这一层

旧 `SweepingAttackPlanner.candidates()` 使用女仆当前坐标和速度切线构造入口；`trySweepingEntry()` 只在距离 6–64 格且已经滑翔时提交。Compact 重定位没有已知攻击窗口作为目的地，必须等待某次当前姿态搜索成功。

现在的顺序是：**选定目标 → 异步发现目标周围的窗口 → 从当前状态比较 Capture → 主动接入 → 当前状态 Rejoin → ATTACK_RUN**。旧 Sweep 与 Lane 仍可补充有限采样遗漏的机会。已经在途的局部 Sweep 不因目标地图先返回而被饿死；无可捕获窗口时仍可领取它的当前状态证书。

战略目标选择不变。本轮只对已选目标建图，没有实现跨目标 Funnel 评分，因此不会越过 marked / previous / protected / ordinary 层级。

## 数值模型与复用

新增纯数值 [AttackFunnelPlanner](../src/main/java/dev/winefox/skyfarer/flight/AttackFunnelPlanner.java)：

- `Input` 冻结目标位置、Aim、PhysicsContext、转向率、最低攻击速度与出口长度。
- `templates()` 最多 160 条定向几何：多方位、直线/左右弧、两档入口距离与高度、两端交换；另用 `SweepingAttackPlanner.candidate()` 的同一 C1 原语构造肩部绕行及侧偏接触。合成入口只取目标相对坐标，不取当前女仆姿态。
- 每条几何采样 .85 / 1.3 水平速度尺度以及无动力 / 剩余 8 ticks 活动火箭。最多 640 次正向模拟，几何拒绝、已成功的无动力样本会减少实际次数。候选不存完整逐步轨迹，最多四个不同入场方向的窗口才记录完整证明与低密度中心线。
- `Window` 保存 Path、已认证 witness、火箭状态、位置/朝向/pitch/速度/vy 容差、完整 Rollout/能量包络和最多 18 个调试中心点。入口位置门槛是 entry 起点至 `entry.at(.3)` 的有限近似管段，半径 4 格；不是整个 Strike/Exit 都可重新开始攻击。
- `Atlas` 是最多四个 Window 的不可变列表。
- `Capture` 保存选定窗口、duration、相对点火时刻、预计捕获时间、成本、首步指令与完整捕获/攻击/离场轨迹。

反向只交换几何控制点；**每个方向必须从该方向自己的合成状态重新向前模拟**。共享 VanillaElytra、Sweeping 的受限转向/速度滞后、逐步 swept body、真实接触射线、前摇/速度门槛、EnergyReserveEnvelope 与完整离场认证。没有第二套鞘翅方程，也不要求整段持续直视目标。

窗口是 **有 witness 证明的有限采样搜索区域**。容差中的每个连续状态并未被穷举证明；紫色体积也不是可穿墙或可忽略动力学的安全授权。动态门槛只筛选 Rejoin 请求，最终仍验证实际姿态与运动状态。

## 早期生成与执行

`refreshFunnels()` 在控制器 tick 中于姿态决策前提交目标地图，目标选定后的地面准备、起飞、爬升都可开始。合成身体使用既有起飞快照的 .6 高 / .4 眼高，避免站立尺寸否决空中窗口。

指挥镜赶路仍由 TransitFlight 独占控制；已分配目标实体已加载时，CommandManager 调用 `prewarmCommand()`，只更新战斗控制器的数值输入和窗口邮箱，不转向、不启动滑翔、不消耗库存。目标区域没有加载/可 tick 时只能等待快照，不能为了 Funnel 强加载世界。地图范围固定在目标水平半径 64 格，避免高速赶路位置变化每 tick 使几何失配；真实 Capture/Rejoin 仍受作战导航边界约束。

APPROACH / REPOSITION 优先检查有效窗口：

1. 当前状态同时满足空间、真实水平速度、速度方向、look yaw、pitch、vy、火箭状态与启动延迟门槛，才请求最终 `Rejoin`；此请求不自行增加火箭。
2. 尚未接入时在 Worker 比较现有窗口的 Capture。先逐个验证不新增火箭的完整方案；当前活动火箭始终使用实际剩余寿命。
3. 没有无新增火箭方案，且主线程库存/CD 允许时，按 duration 从小到大比较现在 / 8 / 16 ticks 后点火。每个方案必须通过全部 12 个随机寿命。
4. Capture 模拟最多 64 ticks 接入段。预测接入后重新从完整原生前摇开始模拟 Strike 和 Exit，不能把未充能的重定位时间抵扣前摇；后半段复用 Sweeping 的最多 160 ticks 认证。
5. 延迟点火只安排重评估时刻；到时重新从当前状态请求立即点火的完整证书。旧计划的倒计时不授权点火。实际库存提取、生成实体和推进延迟仍由 FireworkManager 控制。
6. 执行反馈每 tick 从当前状态计算。等待时不充能/不承诺攻击/不强行补火箭，继续相同的廉价捕获反馈及 live guard；完整 Capture 被拒绝时使用既有局部 Glide 朝已知入口调整。局部 Sweep 仍能提供其他已认证攻击机会。
7. 真正提交复用 `commitSweep()`，启动新 Spears use session，然后走原有曲线跟踪、原生伤害、二段机会和 ATTACK_EXIT。

烟花可能因过早经过 Strike、转不过弯、接触遮挡或出口未完成而被拒绝。能量包络用于排序，不能推翻物理否决。有限模板、有限速度样本和有限 Capture 控制策略不是全局可达性求解器；返回无解不等于证明目标永远不可达。

## 缓存、线程与失效

新增 `PlanningTask.Funnel` / `FunnelResult` / `Capture`，全部是数值 DTO。Funnel 进入现有几何池，Capture/Rejoin 进入控制池；没有新线程池或同步 fallback。

AsyncPlanning 的控制 session 继续管理当前阶段；另一个目标地图 session 固定阶段键 `TARGET_FUNNELS`，共用同一个主线程 Source 和同一全服预算。这样起飞/入口阶段变化不会删除地图，同时真实目标、配置、Level/dimension、物理上下文及显式 invalidate/close 仍会取消它的 generation。缓降正常倒计时按年龄推进，异常刷新/移除会失效。

地图 session 只有一个 Funnel slot、一个已完成地图，最多四个窗口；碰撞缓存上限仍为 768 cells，地图所引用的证明覆盖最多 640 cells。控制 session 的 24 pending / 96 结果上限保持；因此启用地图的女仆最多额外一份 pending 地图与 768 cells，不应再称为“每女仆总共 768 cells”。全服队列仍为 32+32、2–4 Worker，采集仍共享 256 cells / 21,600 reads / 约 2 ms 软预算。

正结果从提交 tick 起最多 40 ticks，已完整搜索的空结果最多 8 ticks；在途 Funnel 最多复用 40 ticks，超过即取消重提。目标位置相对原输入超过 .75 格、Aim/武器/速度参数不兼容、配置/物理/世界改变时不能返回旧窗口。目标地图的 State 是目标锚点，不使用女仆位移来废弃地图。

`Space.proofSpace()` 用独立查询游标记录选中 witness 完整攻击证明实际读取的数值 Cell。主线程再次检查这些 Cell 仍在缓存、年龄不超过 80 ticks、内容及 loaded 状态相同；缺失、驱逐或已观测到的形状变化拒绝复用。动态方块变化仍需等待既有分批刷新才可观测，不宣称即时失效。Discovery 只请求数据，永不发布可执行窗口。

Capture 与 Rejoin 都使用既有严格门槛：年龄 ≤2 ticks、位移 ≤2.5 格、yaw/pitch 差 ≤8°、速度 L1 差 ≤.35，并检查目标/实际火箭/物理/启动延迟与任务参数。最终真实下一步扫掠和实际 Spears 射线保留主线程。

## 调试与验证

`attack_funnels` 是 DebugSnapshot 的附加 NBT 字段；每女仆最多 4 条管道、每条最多 18 个横截面。客户端按相邻中心点的三维切线绘制四条紫色纵边和稀疏截面，不调用 Planner，不绘连续曲面。现有青绿 `attack_curve`、黄色物理身体、蓝色引导、红色目标和绿色入口含义不变。紫色图示不等于连续体积已认证。

`verifyAttackFunnels` 覆盖山腰弯曲窗口、正反向动力学不对称、低速空间捕获拒绝、前摇过冲、无动力优先和 Flight 0 的全部 12 个寿命。`verifyFunnelAsync` 覆盖冷快照与缓存、目标/世界/物理/配置/年龄失效、stale/cancelled mailbox、地面预热以及实际控制器 Capture→Rejoin→接触。`verifyDebugTools` 检查有界网络数据、清空和 renderer 无 Planner 调用；`verifySweepingAttacks` / `verifySweepingReplay` 检查共享 C1 原语和左右山腰生命周期。

本轮只执行这些直接相关检查及 `verifyAsyncPlanning`，不运行全量 build/check。具体日志与结果见 [VALIDATION](VALIDATION.md) 和 [buildHistory](../buildHistory.md)。实机还需观察窗口刷新、活动烟花接入、目标移动、复杂山地捕获成功率、多女仆预算与紫色管道视觉。
