# 目标攻击窗口与主动捕获（0.6.3）

本文件描述当前源码；入口见 [PROJECT_INDEX](PROJECT_INDEX.md)，物理见 [VANILLA_PHYSICS](VANILLA_PHYSICS.md)，线程规则见 [ASYNC_PLANNING](ASYNC_PLANNING.md)。

本轮先核对实机日志，根因与时间线见 [FIELD_ANALYSIS_0.6.3](FIELD_ANALYSIS_0.6.3.md)。

## 为什么增加这一层

旧 `SweepingAttackPlanner.candidates()` 使用女仆当前坐标和速度切线构造入口；`trySweepingEntry()` 只在距离 6–64 格且已经滑翔时提交。Compact 重定位没有已知攻击窗口作为目的地，必须等待某次当前姿态搜索成功。

现在的顺序是：**选定目标 → 异步发现目标周围的窗口 → 从当前状态比较 Capture → 主动接入 → 当前状态 Rejoin → ATTACK_RUN**。旧 Sweep 与 Lane 仍可补充有限采样遗漏的机会。已经在途的局部 Sweep 不因目标地图先返回而被饿死；无可捕获窗口时仍可领取它的当前状态证书。

战略目标选择不变。本轮只对已选目标建图，没有实现跨目标 Funnel 评分，因此不会越过 marked / previous / protected / ordinary 层级。

## 数值模型与复用

新增纯数值 [AttackFunnelPlanner](../src/main/java/dev/winefox/skyfarer/flight/AttackFunnelPlanner.java)：

- `Input` 冻结目标位置、Aim、PhysicsContext、转向率、最低攻击速度与出口长度。
- `templates()` 最多 256 条定向几何：原有 160 条多方向、直线/左右弧、肩部绕行及几何反向，加 96 条八方位 × Strike 坡度 ±.4/±.8 × 直线/左右弧。竖向 witness 按入口切线生成 pitch/vy；不再固定水平。合成入口只取目标相对坐标，不取当前女仆姿态。
- 每条几何采样 .85 / 1.3 水平速度尺度以及无动力 / 剩余 8 ticks 活动火箭。最多 1024 次初筛正向模拟，几何拒绝和成功 coast 会减少次数。按物理成本排序，保留最优路线、可行的俯冲与上升家族，再以方向/pitch 差异补齐最多四个窗口；简单路线只享有轻微 simplicity 优势。
- 每个选中 `Window` 再试最多 27 个邻近入口：横向 0/±3、高度 0/±2、朝向 0/±10°，同时扰动 pitch、速度、vy。只将完整命中且安全离场的模拟纳入包络，保存成功 seeds 供快照覆盖重证。
- `Section` 按成功轨迹的进场/离场相对进度对齐，17 个截面记录 center、width/height、yaw/pitch 容差、vy 范围和水平速度范围。前六个截面允许尝试 Capture；后段用于表达 Strike 收敛及离场，不能在已过目标的位置重新开始攻击。宽高是带有限搜索余量的经验范围，上限半宽 6 / 半高 4；不是矩形内部每个状态都获证明。
- `Atlas` 是最多四个 Window 的不可变列表。
- `Capture` 保存选定窗口和截面、duration、相对点火时刻、预计捕获时间、成本、首步指令与完整捕获/攻击/离场轨迹。

反向只交换几何控制点；**每个方向必须从该方向自己的合成状态重新向前模拟**。共享 VanillaElytra、Sweeping 的受限转向/速度滞后、逐步 swept body、真实接触射线、前摇/速度门槛、EnergyReserveEnvelope 与完整离场认证。没有第二套鞘翅方程，也不要求整段持续直视目标。

窗口是 **有 witness 证明的有限采样搜索区域**。容差中的每个连续状态并未被穷举证明；紫色体积也不是可穿墙或可忽略动力学的安全授权。动态门槛只筛选 Rejoin 请求，最终仍验证实际姿态与运动状态。

## 早期生成与执行

`refreshFunnels()` 在控制器 tick 中于姿态决策前提交目标地图，目标选定后的地面准备、起飞、爬升都可开始。合成身体使用既有起飞快照的 .6 高 / .4 眼高，避免站立尺寸否决空中窗口。

指挥镜赶路仍由 TransitFlight 独占控制；已分配目标实体已加载时，CommandManager 调用 `prewarmCommand()`，只更新战斗控制器的数值输入和窗口邮箱，不转向、不启动滑翔、不消耗库存。目标区域没有加载/可 tick 时只能等待快照，不能为了 Funnel 强加载世界。地图范围固定在目标水平半径 64 格，避免高速赶路位置变化每 tick 使几何失配；真实 Capture/Rejoin 仍受作战导航边界约束。

APPROACH / REPOSITION 优先检查有效窗口：

1. 当前状态同时满足空间、真实水平速度、速度方向、look yaw、pitch、vy、火箭状态与启动延迟门槛，才请求最终 `Rejoin`；此请求不自行增加火箭。
2. 尚未接入时在 Worker 比较现有窗口的 Capture。根据 position、velocity yaw/speed、look yaw、pitch、上升高度成本，每窗口最多取三个低成本截面，先验证不新增火箭的完整方案；当前活动火箭始终使用实际剩余寿命。主线程只作有界的廉价截面选择和反馈，没有 rollout/search。
3. 没有无新增火箭方案，且主线程库存/CD 允许时，按 duration 从小到大比较现在 / 8 / 16 ticks 后点火。每个方案必须通过全部 12 个随机寿命。
4. Capture 模拟最多 64 ticks 接入段。预测接入后重新从完整原生前摇开始模拟 Strike 和 Exit，不能把未充能的重定位时间抵扣前摇；后半段复用 Sweeping 的最多 160 ticks 认证。
5. 延迟点火只安排重评估时刻；到时重新从当前状态请求立即点火的完整证书。旧计划的倒计时不授权点火。实际库存提取、生成实体和推进延迟仍由 FireworkManager 控制。
6. 有完整结果时执行其已验证首步指令；等待或无解时继续廉价截面捕获反馈及实时下一步身体 guard，不退回朝目标转向的 FUNNEL_ALIGN。捕获失败只表示未找到完整攻击方案，不能当作即时身体碰撞强制切到 ATTACK_EXIT。局部 Sweep 仍能补充已认证机会。
7. 真正提交复用 `commitSweep()`，启动新 Spears use session，然后走原有曲线跟踪、原生伤害、二段机会和 ATTACK_EXIT。

烟花可能因过早经过 Strike、转不过弯、接触遮挡或出口未完成而被拒绝。能量包络用于排序，不能推翻物理否决。有限模板、有限速度样本和有限 Capture 控制策略不是全局可达性求解器；返回无解不等于证明目标永远不可达。

Capture reserve 使用 `水平速度² − 截面最低速度² + 2g × (当前高度 − 截面高度)`，不使用谷底高度。它用于调度诊断，不授权点火；coast 无完整方案便在同一后台任务中评估所有可用 duration/寿命，持续下降无需等到 clearance<4。原有非 Funnel Compact / 返回 / 出口恢复策略仍存在，不应宣称全部旧能量分支已被替换。

## 缓存、线程与失效

新增 `PlanningTask.Funnel` / `FunnelResult` / `Capture`，全部是数值 DTO。Funnel 进入现有几何池，Capture/Rejoin 进入控制池；没有新线程池或同步 fallback。

AsyncPlanning 的控制 session 继续管理当前阶段；另一个目标地图 session 固定阶段键 `TARGET_FUNNELS`，共用同一个主线程 Source 和同一全服预算。这样起飞/入口阶段变化不会删除地图，同时真实目标、配置、Level/dimension、物理上下文及显式 invalidate/close 仍会取消它的 generation。缓降正常倒计时按年龄推进，异常刷新/移除会失效。

地图 session 只有一个 Funnel slot、一个已完成地图，最多四个窗口；碰撞缓存上限仍为 768 cells，地图所引用的证明覆盖最多 640 cells。控制 session 的 24 pending / 96 结果上限保持；因此启用地图的女仆最多额外一份 pending 地图与 768 cells，不应再称为“每女仆总共 768 cells”。全服队列仍为 32+32、2–4 Worker，采集仍共享 256 cells / 21,600 reads / 约 2 ms 软预算。

正地图没有固定年龄淘汰：通过当前输入、物理及覆盖检查时继续复用。未满四个窗口时每 40 ticks 后台补充候选，满额合法地图不重复搜索；刷新期间仍返回合法旧地图。对于完全相同 Input，已有合法 Window 保持身份，只补空槽；没有新增窗口时连 Atlas 身份也保留，避免冷快照不同子集互相替换。合并后的证明覆盖仍须 ≤640 cells。空结果最多缓存 8 ticks；在途 Funnel 仍最多复用 40 ticks。目标相对原输入超过 .75 格、Aim/武器/速度参数不兼容、配置/物理/世界变化不能复用。地图 State 是目标锚点，不使用女仆位移来废弃地图。

`Space.proofSpace()` 用独立游标重放所有成功 seeds，记录完整证明读取的数值 Cell。覆盖 cells 优先刷新并避免被无关搜索驱逐；主线程仍要求它们存在、年龄不超过 80 ticks、内容和 loaded 状态相同。缺失、过期、变化均拒绝复用，不为消除闪烁保留非法地图。世界变化依赖分批刷新才可观测，不宣称即时失效。Discovery 只请求数据，永不发布可执行窗口。

Capture 与 Rejoin 都使用既有严格门槛：年龄 ≤2 ticks、位移 ≤2.5 格、yaw/pitch 差 ≤8°、速度 L1 差 ≤.35，并检查目标/实际火箭/物理/启动延迟与任务参数。最终真实下一步扫掠和实际 Spears 射线保留主线程。

## 调试与验证

`attack_funnels` 是 DebugSnapshot 的附加 NBT 字段；每女仆最多 4 条管道、每条最多 18 个横截面（当前 17）。客户端按相邻中心点三维切线和各自 width/height 绘制四条紫色纵边与截面，不调用 Planner。青绿攻击曲线、黄色身体、蓝/红/绿保持。新增 funnelGen/cancelled/submitted 和无 flightRequest 时的 energy 日志；Capture 区分 pending、coast、全寿命 pulse、无库存、cooldown、无完整路线，不再将失败写为 capture_coast。

`verifyAttackFunnels` 覆盖面积、俯冲/上升及持续转弯、山腰、正反向物理差异、下游截面、低速门槛、过冲、coast 优先和 Flight 0 全 12 寿命。`verifyFunnelAsync` 覆盖静止 100 ticks 跨刷新保持已有合法 Window 身份，无新增时也保持 Atlas 身份，以及覆盖/上下文失效、地面预热、实际控制器 coast/pulse/下降 Capture→Rejoin→命中及命令绕过旧 retry 后安全起飞。`verifyCommandDispatch` 核查服务端 mark 射线的 EntityMaid 排除字节码；Debug 与左右山腰回放保持专门检查。

本轮仅编译和直接相关验证，不运行全量 build/check。具体执行任务、结果见 [VALIDATION](VALIDATION.md) 与 [buildHistory](../buildHistory.md)。实机需复测窗口连续性、复杂山地首次攻击耗时、多女仆预算和紫色体积视觉。
