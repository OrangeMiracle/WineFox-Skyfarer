# 飞控系统总设计与当前实现

0.7.4 只调整实际推进之后的客户端光效与圣物掉落行为，物理/认证/动力仲裁未改动，见 [彗星光效与掉落保护](COMET_RADIANCE_074.md)。

0.7.3 统一动力源权威仲裁，永燃独占推进、持续闭环速度保持和客户端双赤尾焰/气流环，见 [动力仲裁与持续控制](PROPULSION_073.md)。饰品与隐藏觉醒机制见 [彗星专题](COMET_HEARTS.md)。

当前版本 **0.7.4**。航迹承诺、控制仲裁与延迟安全见 [航迹连续性](../src/main/java/dev/winefox/skyfarer/flight/TrajectoryCommitment.java)，既有推进边界见 [架构审计](ARCHITECTURE_070.md)，定向结果见 [验证](VALIDATION.md)。源码为最终权威。

## 导航

- [1. 控制目标与物理边界](#1-控制目标与物理边界)
- [2. 每-tick-控制流程](#2-每-tick-控制流程)
- [3. 状态机与共同规则](#3-状态机与共同规则)
- [4. 各状态的实现契约](#4-各状态的实现契约)
- [5. 目标、战斗中心与锁定](#5-目标战斗中心与锁定)
- [6. 候选几何、曲线和坡面](#6-候选几何曲线和坡面)
- [7. 碰撞通道与紧急机动](#7-碰撞通道与紧急机动)
- [8. 能量、烟花和补能时机](#8-能量烟花和补能时机)
- [9. 长矛实际命中与多目标连续攻击](#9-长矛实际命中与多目标连续攻击)
- [10. 指挥镜赶路及高速特例](#10-指挥镜赶路及高速特例)
- [11. 异步 Planner 与实际飞控之间的关系](#11-异步-planner-与实际飞控之间的关系)
- [12. 调试、回归与当前限制](#12-调试回归与当前限制)

## 1. 控制目标与物理边界

核心目标是从当前动量出发，在真实身体通道可通过、长矛可接触、攻击后有恢复路线的前提下完成攻击，随后尽早评估下一次进场。地形附近的方块数量或单根射线不是攻击可行性的替代品；近处有山体、地板或屋顶不等于航线被堵塞。

正常战斗使用原版鞘翅滑翔、附着烟花、WineFox 有限时长激流或永燃之心的有限虚拟推力；激流可服务已认证的 Strike/Exit，仍须证明推力及完整后续路线。高度、速度、阻力和有限推进共同影响未来能量；普通控制主要修改观察方向，激流按已有 .2/tick 规则叠加实际推力，不把速度向量直接旋转到目标方向。目标高度、垂直速度反馈和速度阈值仍存在。

关键物理约定：

| 数据 | 当前含义 |
| --- | --- |
| `FlightPrediction.State.y` | 通常为女仆眼睛的世界 Y，而非脚底 Y；地面起跳预测有显式构造的起点 |
| `vx/vy/vz` | blocks/tick；主目标运动使用实际相邻 tick 的位置差 |
| `yaw/pitch` | 角度；负 pitch 是抬头，正 pitch 是俯冲 |
| 物理上下文 | 主线程通过 `getGravity()` 读取实际 gravity（含 noGravity），采集缓降剩余时间，冻结到 `PhysicsContext`；异常回退 .08；合法负/零值保留 |
| `FlightPrediction.step` | 受限姿态后调用 `VanillaElytra`：float 查表、速度 epsilon、原版滑翔/阻力、位移，最后附着火箭更新下一步速度；新火箭延后一个实体 tick 才推进 |
| `HeadingController` | 从本 tick 起始姿态限速应用 yaw/pitch；`SAFETY > PROPULSION > ROUTE`，低权限不能覆盖高权限；同 tick 不叠加转向额度 |
| 创造赶路 | 明确例外：`CreativeTransitPlanner` 计算当前步输入速度，主线程设置 `setDeltaMovement`，交战前缩回正常速度范围 |

源码：[FlightPrediction](../src/main/java/dev/winefox/skyfarer/flight/FlightPrediction.java)、[HeadingController](../src/main/java/dev/winefox/skyfarer/flight/HeadingController.java)、[ElytraFlightController](../src/main/java/dev/winefox/skyfarer/flight/ElytraFlightController.java)。

## 2. 每 tick 控制流程

```text
TLM Skyfarer 工作行为 -> SkyfarerRuntime.workTick -> acquire 请求
服务端 EntityTickEvent.Pre
  -> CommandManager.beforeMaidTick
       -> TransitFlight / Home recall 接管时，不运行普通战斗 tick
       -> 赶路交接完成时，把目标交给普通控制器
  -> AirCombatController.tick
       -> planningContext：tick / 逻辑阶段 / 目标 / 配置 revision
       -> tickFlight：采集实体状态、目标位移和当前火箭状态
       -> 工作资格、水中恢复、落地、续航和超范围检查
       -> 对应状态的方法
            -> AsyncPlanning：数值任务 + 不可变碰撞快照
            -> Worker：候选生成、预测、评分、通道判断
            -> PlannerSession：主线程领取并验证结果
            -> 主线程：转向、起跳、滑翔标志、使用物品、状态转移
       -> terrainSafety：解释已返回的规划结果
       -> validateImmediateMotion：真实世界当前步最后检查
  -> Minecraft / TLM 实体 tick 与 Spears 物品使用逻辑
       -> 原版移动、真实长矛伤害、命中回调
```

快照采集由服务端 tick 的公共预算调度，不是上述每次 API 调用都同步扫描完整世界。`AsyncPlanning` 保留部分旧调用形状，但传入的 live corridor/support/contact 回调不会作为对象交给 Worker；Worker 用 `CollisionSnapshot.Space` 实现查询。

`SkyfarerRuntime` 在主线程按女仆 UUID 持有 `AirCombatController`。女仆离开世界时释放控制器和指挥任务；`release(reason)` 在合法空中状态保留控制并转安全返航；地面/失去资格才关闭会话、清理滑翔并回到 `GROUNDED`。交接/卸载/替换使用显式 `dispose`，保留空中滑翔标志。恢复后的控制器可在后续工作 tick 再次采集任务。源码：[SkyfarerRuntime](../src/main/java/dev/winefox/skyfarer/runtime/SkyfarerRuntime.java)、[AirCombatController](../src/main/java/dev/winefox/skyfarer/ai/AirCombatController.java)。

## 3. 状态机与共同规则

### 3.1 实际枚举及主路径

[AirCombatState](../src/main/java/dev/winefox/skyfarer/ai/AirCombatState.java) 共有 17 个枚举值：

```text
GROUNDED -> TARGET_ACQUIRE -> TARGET_SELECTED -> TAKEOFF_PREP -> TAKEOFF
    ^            | 无目标                                         |
    +------------+                                                v
    |                                                           CLIMB
    |                                                             |
    |                           +---------------------------------+
    |                           v
    |           APPROACH <----> REPOSITION
    |              |
    |              v
    |       ATTACK_RUN -> DIVE -> SPEAR_CHARGE
    |              | 命中且二段攻击可行       |
    |              +-> CHAIN_ATTACK ---------+
    |                                        v
    |                                   ATTACK_EXIT
    |                                /       |       \
    |                     下一目标 /   无目标/返回请求  \ 曲线继续同向转弯
    |                           v            v
    |                       APPROACH   RETURN_TO_OWNER -> LANDING
    |                                           |              |
    +-------------------------------------------+--------------+

水中恢复：满足资格时从地面或托管飞行 -> WATER_ESCAPE
          出水后 -> startApproach / 原返航流程 / 落地释放

其他分支：LOITER_SEARCH；紧急机动在 ATTACK_EXIT 内执行
```

图表示主要逻辑边，不保证每条边隔一个完整 tick。`TARGET_SELECTED`、承诺攻击后调用 `attack()`、离场结束后的 `approach()` 等可以在同一个 tick 完成多个步骤；日志和回放需保留中间转移。

命名注意：

- `ASSESS_NEXT_ATTACK_ENTRY` 是进入 `APPROACH` 的诊断 reason，不是枚举状态。
- `PULL_UP` 不是当前状态。离场是 `ATTACK_EXIT`，可直线爬升、转弯爬升、转弯平飞、直线平飞或继续已选曲线。
- `AirCombatState.controlsAir()` 与 `AirCombatController.controlsAir()` 不同：运行时实际接管依据后者的 `managed` 标记。

### 3.2 全状态共同约束

**工作资格。** 主人须存在、存活、非旁观者且与女仆同维度；女仆不能关闭 AI、打开 GUI、坐下、睡觉、乘坐或被拴住。普通起飞还需地面、可充能长矛、可用鞘翅及合法推进或已认证无动力路线。水中逃逸入口是独立恢复路径，不以当前存在攻击目标为前提。

**托管期间。** 每 tick 清理 TLM 导航、`WALK_TARGET/PATH/LOOK_TARGET/TARGET_POS` 和地面移动输入；滑翔时设置 `FALL_FLYING` 姿态。这样地面跟随行为不会持续把空中攻击航向拉向主人。实际模型动画是客户端兼容问题，服务端姿态不能保证每个第三方模型都有鞘翅动画。

**目标与续航。** `ATTACK_RUN/DIVE/SPEAR_CHARGE/CHAIN_ATTACK` 锁定一轮攻击目标；`AttackPolicy.finishCycle()` 还包括 `ATTACK_EXIT`。燃料耗尽或最大滞空时间到达时先设置 `pendingReturn`，已承诺循环允许完成离场再返航。设备/工作资格失效、滑翔中断等则可更早转返航。

**落地与边界。** 托管飞行在起飞准备之外触地，立即 `release()`；若不是返航，reason 是 `GROUND_CONTACT_RECOVER`，不会假装保持空中攻击状态。超范围安全判断同时参考战斗中心距离与当前合法目标距离，转弯暂时远离中心不自动打断一次近目标攻击。

**异步等待。** `Pending` 表示尚无可用结果，不等于几何被证明无解。必需结果会退出本 tick 状态方法，随后运行即时安全检查；可选计算返回明确的临时值。状态、目标或配置变化会隔离旧任务，详见第 11 节。

## 4. 各状态的实现契约

除特别注明，以下状态都在服务端主线程运行。Worker 只执行所列纯规划，不拥有状态机。

### 4.1 `GROUNDED`

- **目的/进入：** 闲置入口；构造时和地面/失效释放后进入，把普通地面行为交还 TLM。
- **输入：** 工作资格、主人/工作区域、装备、地面状态、`retryAt`、扫描节拍。
- **规划/控制：** 不飞行、不向 Worker 请求全局路径。`acquire()` 满足条件后发起 `TARGET_ACQUIRE`；缺少装备等只写诊断原因。
- **退出：** 默认每 `scan_interval_ticks=10` ticks 尝试，释放后的 `launch_retry_ticks=60` ticks 内不能重新起飞。入水且有可用鞘翅和燃料/仍活动的火箭时可独立进入 `WATER_ESCAPE`。
- **失败：** 保持地面，不通过临时生成燃料或强制传送来制造可起飞条件。

### 4.2 `TARGET_ACQUIRE`

- **目的/进入：** 从地面选择合法目标；由 `acquire()` 进入。
- **输入：** 主线程实体列表、标记目标、战斗中心、可见性、上一目标和失败次数。
- **规划/控制：** `TargetEvaluator.scan()` 在主线程完成有限候选扫描/射线与战略排序，尚不承诺攻击几何；记录 scanned/in_range/valid 诊断。
- **退出/失败：** 有目标 -> `TARGET_SELECTED`；没有 -> `GROUNDED`。失败回合过多的目标被优先排除，但没有替代者时可重新尝试它。

### 4.3 `TARGET_SELECTED`

- **目的：** 将取得目标和接管起飞分开，保留可观察状态。
- **输入/控制：** 当前 `preferred`；下一次分支进入 `TAKEOFF_PREP`，该转移设置 `managed=true`、清零滞空计时和待返航原因。
- **规划：** 本状态无独立 Worker 任务；目标再验证在准备阶段进行。

### 4.4 `TAKEOFF_PREP`

- 主线程先检查鞘翅和真实站立跳跃净空，向推进层提交局部可持续起飞需求。合法 READY 激流优先，不把近目标当刹停终点，也不要求一次推进到远目标。
- 激流无解或有界等待耗尽后，搜索同战略层最多四个合法目标的 Launch Attack；不适合则普通起飞。提供器负责候选库存与物理差异，主线程只使用姿态/路线证书。
- Worker 预测使用跳起滑翔身体与实际 gravity；所有方案必须证明离地、接触/恢复及安全接管。无解保持地面或正常回退，不伪造飞行。
- 阶段计时回退不能反复重开耗尽的首选推进搜索，阻断后备方案。

### 4.5 `TAKEOFF`

- 激流分支由提供器完成普通跳跃、离地滑翔、当前状态重验与副手 READY 释放；推力和恢复段由同一预测控制律执行，然后进入 CLIMB。
- 其他分支重新验证离地状态及原计划。Launch Attack 通过当前 Rejoin 才启动原生 Spears；待定时间有界，失败回普通起飞。
- 普通方案由推进层执行已认证的附着推进或无推进起飞，前 6 ticks 保留离地引导并逐步检查实际净空。仍有起飞 timeout。

### 4.6 `WATER_ESCAPE`

- **目的：** 入水时恢复飞行，避免继承攻击/返航状态后永远卡住。
- **进入/输入：** 主线程通过真实 `maid.isInWaterOrBubble()`；保存 `getSwimManager().wantToSwim()` 及此前返航原因，停止充能、清理锁定目标和旧路线。
- **Worker：** 水中允许水体、仍拒绝固体/熔岩/边界/未知空间的 `SurfaceLaunchPlanner`。水中移动按 TLM `wantToSwim` 对应的当前速度位移及 `.9` 阻力预测；激流先经统一需求与自身水中预测认证；附着推力后备使用 SurfaceLaunchPlanner，已有火箭按剩余寿命评估，否则按提供器候选的所有寿命评估。
- **主线程：** 开启 wantToSwim、离开水底时起跳；最多向上查 32 格已加载水柱，求出水高度 +4；真实姿态/火箭/当前步碰撞均在主线程执行。
- **退出：** 出水后恢复旧游泳意愿；触地释放；空中重新滑翔后，原本返航则恢复返航，否则扫描并进入下一次攻击评估。
- **失败：** 资格或鞘翅失效、当前步变化导致碰撞、无通道、超时 `max(80, takeoff_timeout_ticks*2)` -> 释放并回退原游泳行为。这里不是穿墙/穿熔岩特权。

### 4.7 `CLIMB`

- **目的：** 完成紧凑离地段，取得下一次攻击需要的高度/速度，不持续抬高目标高度。
- **输入/Worker：** 起飞选定的固定 waypoint、剩余火箭、当前位置，随后使用普通 Glide 预测及能量评估。
- **主线程：** 前 6 ticks 按起飞计划的姿态直接离地，每步 live 检查；如果新方块/外力阻挡该步，停止专用离地段，转普通 `LAUNCH_RECOVERY` 引导。
- **退出：** 至少 8 ticks 后达到 waypoint 高度容差，或本状态超过 100 ticks -> `startApproach()`。若目标高于当前眼睛至少 1 格、距离足够前摇、与动量方向夹角 <25°，可提前开始上升攻击入口评估。
- **失败：** 共同的落地、设备、燃料和滑翔中断规则生效；不能因为目标高而无限加高爬升点。

### 4.8 `APPROACH`

- **目的：** 根据当前动量竞争直线、竖向坡面/速降与持续转弯路线，选出可实际接触并安全离场的攻击。
- **进入/输入：** 初次爬升完成、离场完成、返航途中恢复战斗、指挥镜交接或重定位完成。`startApproach()` 清掉旧 run/curve/recovery，保留合法 preferred，评估次要穿刺目标；同时记录本次是否从目标脚底以下开始进场。
- **Worker：** `Sweep` 生成两段三次曲线及动力学预测；`Rejoin` 用当前状态重新认证；`Slope/Lane/Glide` 生成与评估常规路线。
- **主线程：** 首先请求曲线候选，曲线候选仅在目标距离 6–64 格且滑翔时搜索。尚待结果的前 8 ticks 面向目标引导，普通入口保持当前高度；从下方开始的入口以 `attackHeight-min(3,spear_vertical_expansion)` 为准备高度，并贯穿本轮接近、重定位与低能量恢复，避免准备过程中又切换到目标上方巡航。它只影响准备引导，不能代替完整攻击认证。
- **地形例外：** 坡面采样至少 12 点且不满足原平地门槛时，取消上述低位空中准备并清除较低 Compact 目标，恢复已有地形爬升规则。连续上坡与孤立高处目标不能共用低位准备高度。
- **常规承诺条件：** look 和真实动量都可在到达前对正，剩余距离覆盖原生前摇；稳定高度/垂直速度或相应坡面曲线误差满足容差；入口、接触、出口走廊合法。见 `AttackPolicy.minimumEntry/canEnter`。
- **输出/退出：** 确认路线后停止旧 charge session，设置 `runTarget/runOrigin/runDirection` 或 `sweepPath`，立即开启原生长矛使用并 `passHistory.begin()`，进入 `ATTACK_RUN`；同 tick 可执行攻击逻辑。
- **失败/特殊：** 目标失效重扫；太近、朝向大偏差或常规走廊堵塞 -> `REPOSITION`；总 setup 超过 `attack_timeout_ticks*4` -> 记一次失败、换目标。合法锁定目标可以暂时被山体遮挡；并不要求整个 APPROACH 持续直视目标。

### 4.9 `REPOSITION`

- **目的：** 在固定局部区域内用爬升减速换取转向能力，恢复可用入口；不是继续向外高速飞到远端再绕圈。
- **进入/输入：** 入口太近、未稳定、夹角过大、通道堵塞或低能量攻击恢复；目标、采样坡度、当前动量、前摇/触及距离。
- **Worker：** `CompactTurnPlanner` 枚举制动 pitch `-65/-55/-40/-25°`，模拟固定 staging/inward 目标与局部半径约束；必要时评估所有烟花寿命的紧凑补能。
- **主线程：** 入口 staging 距离钳制在 18–34 格附近（还受 reposition 配置），局部半径通常 32–48 格；普通平地目标使用至少额外 9 格的进场高度，从下方开始的本轮入口沿用上节的准备高度。实时重算廉价方向反馈，不复用旧的首帧角度。
- **退出：** 每 tick 仍先尝试可用持续转弯攻击；否则当转回目标角度 <18° 且距离足够，立即回 `APPROACH`，不要求完整绕完 staging。100 ticks 后重新评估入口。
- **失败/特殊：** 低速无动力时禁止持续鼻尖高抬；只有真实低能量下降、水平速度 <.75、离地 <8 且目标高度未超出时请求恢复脉冲。普通 `manageEnergy()` 不在该状态自行补一个远距离巡航火箭。小角度末端修正缩小 15%，大角度仍保留控制能力。

### 4.10 `ATTACK_RUN`

- **目的：** 标记一次已承诺的攻击开始，锁定目标和命中统计。
- **输入：** 当前长矛参数、锁定目标运动、已选择的 line/lane 或 sweepPath、当前火箭状态。
- **Worker：** 常规 `Glide` 或曲线 `Sweep` 跟踪及预测；它不是一次完整结果回来以后盲飞到底。
- **主线程：** 每次攻击调用增加 `runTicks`，先看真实命中回调、目标是否仍可攻击；未命中且可继续时转 `DIVE`，已使用长矛又可很快进入 `SPEAR_CHARGE`。
- **退出/失败：** 主目标已被本轮真实命中 -> 二段追击或离场；目标死亡/失效 -> 离场。主人移动不会重算并取消已锁定的一次进场。

### 4.11 `DIVE`

- **目的：** 执行攻击接近；名字不是“只准向下飞”。同一分支执行下俯冲、上升坡线、平飞与横向持续转弯。
- **Worker：** 常规模式预测目标运动、姿态变化、碰撞和出口；曲线模式追踪两段连续曲线，并随剩余火箭寿命滚动验证。
- **主线程：** 普通常规接近以目标实际位移估算最多 12 ticks 的 lead，动态修正到接触；坡线由带符号 slope 控制。不能用 Entity 的负重力速度当静止地面目标的运动速度。
- **退出：** 达到长矛使用时机 -> `SPEAR_CHARGE`；命中 -> 二段/离场；经过目标、前摇窗口不再可用、超时、目标位移/纠偏过大或曲线失去安全续接 -> 离场。
- **特殊：** 普通路线的 passed 使用沿固定 `runDirection` 的剩余投影。曲线必须调用 `Path.passedContact()`：先确认沿曲线的剩余进度接近接触，再检查末端切线平面；不能仅凭“位于目标切线另一侧”就提前结束绕山曲线。

### 4.12 `SPEAR_CHARGE`

- **目的：** 飞行攻击中保留 Spears 原生使用过程，让其前摇、速度、攻击范围、伤害与冷却生效。攻击后保留充能的窗口适配另见第 9 节。
- **进入/输入：** 实际正在使用长矛，或计算的到达时间需要开始蓄力；武器 delay/use window/reach/margin，以及目标虚拟容差。
- **Worker：** 与 DIVE 同一 ATTACK 逻辑上下文，预测 `Aim.readyIn()` 之后才可接触，火箭不能让女仆在前摇完成前掠过目标。
- **主线程：** `startUsingItem/stopUsingItem` 和实际伤害保持原生；状态机不直接用预测 hit 扣血。
- **退出/失败：** 原生 charge window 超时、实际穿过、目标失效或安全通道失效 -> 离场。真实命中可以立即确认二段追击；未真实命中不会启动主动追逐第二目标。

### 4.13 `CHAIN_ATTACK`

- **目的：** 主目标已经命中后，利用尚在保持的长矛状态，在局部继续转向追击最多两个次目标。
- **进入：** 接触前已预评估 `chainCandidate`，主目标被真实命中、尚在 using item、没有 pendingReturn，且当前状态重新验证的 chain 计划有效。
- **输入/Worker：** 初次主目标位置 `chainCenter`、默认半径 6 格的次目标、剩余 charge、当前速度/火箭余量；`ChainAttackPlanner` 比较 5/8/10/12° 每 tick 的短程转弯并验证接触后恢复。
- **主线程：** 保持同一 charge session 和 UUID 去重，跟踪每个次目标的真实命中；不启动新的补能，允许此前火箭剩余推力继续作用。
- **退出：** 最多 `min(40, post_attack_charge_ticks)` ticks、累计行程 18 格、最多两个后续命中；charge 结束、无可行次目标或恢复走廊不成立 -> `ATTACK_EXIT`。
- **特殊：** 搜索和可见性/实体过滤仍在主线程；规划不会修改主目标战略优先级。默认 3 秒 charge 是上限之一，二段追击自身只有最多 40 ticks 的预算。

### 4.15 `ATTACK_EXIT`

- **目的：** 顺着已经可行的出口尽快脱离，并立即评估下一轮；不把“持续抬头”当成唯一动作。
- **输入：** 当前速度方向、固定 recoveryOrigin、经攻击验证的 laneExit 或 sweepPath、charge tail、下一目标/返航请求。
- **Worker：** `AttackExitPlanner` 评估直线/±35/±65/±85° 的爬升及平飞候选；`Glide` 滚动验证；有曲线时 `followSweep(true)` 保留原转弯趋势与末段切线。
- **主线程：** 曲线优先沿自身出口；坡线优先保留此前验证的出口；速降结束可采用近距离 +4 格爬升。所有出口用固定端点，不每 tick 把端点重新推到前方。
- **退出：** 接近端点、越过端点、累计行程达到上限，或在可用安全计划下超过 100 ticks，均可结算本轮并重扫。普通出口长度是 `max(breakaway_distance, clamp(speed/radians(turn_rate),8,32)*1.5)*2/3`；曲线等使用 `SkyfarerSettings.attackExitDistance() = breakaway_distance*2/3` 作为行程限制。
- **续战/返航：** 有 pendingReturn 则返航；否则刷新当前战斗中心并扫描。有目标 -> 同 tick `APPROACH`；无目标 -> 直接开始返航。这一常见出口不是先强制降落再索敌。
- **失败/特殊：** 曲线续接不安全时退到普通出口；等待必需 exit 结果时用廉价拉升反馈停止旧的俯冲指令，再做实时防碰。无特殊火箭需要时不为了名义能量储备继续向外补能。charge tail 不参与出口完成条件，也不延长行程上限。

### 4.16 `LOITER_SEARCH`

- **目的：** `startApproach()` 当时无可用目标时保留短暂的空中再扫描机会。
- **输入/Worker：** 记住的战斗中心、当前位置、速度和净空；普通 Glide/能量预测到移动弧形 waypoint。
- **主线程：** 按扫描节拍找目标，弧形 waypoint 根据当前动量与转向半径更新。
- **退出：** 找到目标 -> `APPROACH`；离地 <6 格、水平速度 <.6，或达到默认 `loiter_time_ticks=80` -> 返航。
- **限制：** 当前 `attackExit()` 无目标时直接返航，因此不是所有无目标离场都经历完整 loiter；返航仍可能空中重新加入战斗。

### 4.17 `RETURN_TO_OWNER`

- **目的：** 安全收束空中控制；名字虽为 OWNER，实际目的地用 `CombatAnchor.current()`，可为指挥目标附近、Home 或主人。
- **进入/输入：** 无敌人、超时/燃料/资格/装备/滑翔中断、指挥任务完成或赶路失败。开始返航即停止 charge、清理 preferred/run/secondary/curve。
- **Worker：** 方向偏差 >40° 且距离 >10 格时先 Compact 转弯，再 Glide 到落点上空；普通能量规则可为安全返航补能。
- **主线程：** 查找真实已加载、无液体且站立身体能容纳的落点。初次、每 20 ticks 或目的地变化 >12 格时重查；跟随模式主人不存在/不同维度则沿当前前向安全下降。
- **退出：** 有落点且水平距离 <10 格 -> `LANDING`；已落地释放。默认 `return_timeout_ticks=300` 后 Home 尝试在已加载安全落点 teleport；跟随调用 TLM `teleportToOwner`。
- **重新开战：** 尚未触地，仍能滑翔、有装备和燃料、工作合法、滞空预算足够，且返航原因属于 `NO_TARGET_AFTER_LOITER/ALIGNMENT_TIMEOUT/EXIT_PATH_TIMEOUT/COMBAT_AREA_EMERGENCY_RANGE/FLIGHT_LIMIT_OR_FUEL` 时可重扫，在硬范围 +8 内重新进场。并非所有返航原因都允许取消返航。

### 4.18 `LANDING`

- **目的：** 明确允许接触地面，把身体姿态恢复与地面行为交接完成。
- **输入/控制：** 已找到的落点；关闭长矛，只使用 HeadingController 朝落点下降，不调用普通能量补能。
- **规划：** 不启动新的攻击路径；主线程可按返航节拍更新落点。`validateImmediateMotion()` 特意跳过该状态，否则接近地面会被误判为必须抬头。
- **退出/失败：** 触地释放；返航总超时安全回收仍适用。软返航尚未触地时，如果重新看到合法目标、装备/推进/工作资格与滞空预算均满足 canResume，可恢复空战。Command 完成、Home 本地收尾、装备故障等硬返航不允许取消。

## 5. 目标、战斗中心与锁定

源码：[TargetEvaluator](../src/main/java/dev/winefox/skyfarer/ai/TargetEvaluator.java)、[TargetPriority](../src/main/java/dev/winefox/skyfarer/ai/TargetPriority.java)、[CombatAnchor](../src/main/java/dev/winefox/skyfarer/ai/CombatAnchor.java)、[AttackPolicy](../src/main/java/dev/winefox/skyfarer/ai/AttackPolicy.java)。

### 5.1 合法性不是可见性

`attackable()` 排除女仆自身、主人、所有玩家、死亡/已移除/旁观/无敌目标，并要求同维度和可用主人。已被该女仆合法指派的标记目标可越过普通盟友与 `canAttack` 过滤，因此友好生物可被标记攻击；玩家仍被硬性排除。

`valid()` 另外检查目标与战斗中心的范围；合法标记目标在女仆 64 格内有额外允许条件。`visible()` 以眼睛至目标包围盒中心或身体 85% 高处的碰撞射线判断，仅用于新目标选择等入口。已锁定攻击和 APPROACH 的目标合法性不持续要求此射线通畅。最终实际矛尖接触仍不能穿方块，详见第 9 节。

### 5.2 战略层级

候选来自女仆周围、指挥列表、持久战斗中心周围，以及 preferred 周围的已加载实体集合；去重后排序：

1. 标记目标优先。
2. 上一目标，或保护范围内目标优先；跟随保护半径为 16，Home 为 `16 + getRestrictRadius()`。
3. 同层幻翼优先。
4. 同层路线分数：`100 - 女仆距离 - 航向夹角*0.2 - 中心距离*0.25 + 可见性项 + 标记加成`。
5. 同层换目标须超过 `target_switch_threshold`，降低频繁抖动；未命中次数达到 `max_failed_passes` 的目标先避开，无替代者才重试。

上一目标在当前实现通过战略层级体现，`previous_target_bonus` 虽仍注册配置，`TargetEvaluator.score()` 当前未使用它。不要为“保持旧数值”把它擅自加回并改变排序。

### 5.3 跟随与 Home

战斗中心优先级为指挥订单 destination > Home restrict center > 同维度主人 > 女仆自身。普通控制器在地面扫描、离场结束或重新续战时更新中心，已锁定的一轮攻击期间使用快照中心。长途指挥赶路由独立订单控制，交战后才进入普通局部目标选择；不能用每 tick 主人位置替换 runDirection/runTarget。

## 6. 候选几何、曲线和坡面

### 6.1 两条互相竞争的攻击路线族

当前不是一个统一的全局 A* 或连续最优控制求解器，而是有界候选生成、离散姿态预测、反馈跟踪的组合：

| 路线族 | 实现 | 结果 |
| --- | --- | --- |
| 竖向速降/坡线/常规接触走廊 | `AttackLanePlanner`、`AttackLinePlanner`、`DiveProfile` | Lane 的 slope/flare/offset/entry/exit/梯度 |
| 横向弧线与持续绕山攻击 | `SweepingAttackPlanner` | C1 拼接的 entry/exit 三次曲线、接触时刻、碰撞与命中预测 |

曲线从当前实际速度方向构造起点切线；低速才退到目标方向。保留原来的 108 条浅角候选，另将直达和左右 `30/60/90/120°` 与接触仰角 `±50/±60/±70°`、两档起始控制柄组合，增加 108 条竖向候选，每个动力条件上限 216。初始上升切线接下降接触可形成越脊俯冲，初始下降切线接上升接触可形成先降后攻；它们与转弯共同搜索，不设固定陡角姿态。当前无动力候选不可用时，还可按真实库存 duration 生成拟点火候选（最大预测推进 ≤80 ticks）；这种候选不代表当前已有推力。它仍走当前状态 Rejoin、全部随机寿命与真实点火后才能承诺攻击。接触处 entry 末切线与 exit 起切线一致；陡角出口缩短接触控制柄，随后可收平、抬升并侧向转出。

竖向曲线以三维速度和局部路径高度误差跟踪，接触附近同时修正 yaw/pitch 指向目标，姿态范围 ±75°；进攻 pitch 速率仍为 6°/tick，陡角离场允许 14°/tick。以上只是候选指令，`VanillaElytra` 与每步身体扫掠仍决定能否执行。只有完整命中、前摇及安全离场成立的路线进入成本比较；旧普通路线必须预测到实际接触（miss ≤1e-8）才能压过已认证曲线。

`Path` 按控制多边形长度约每 `.75` 格采样，每段 8–160 步；先身体走廊过滤，再模拟原版动量/姿态/有限火箭。搜索、Rejoin、执行 follow 的进攻基准统一为 120 ticks，完整上限仍为 160。预测接触后扩展离场预算；只有接触后至少 16 ticks、火箭及点火延迟结束后的滑行窗口、出口行程与最低速度全部满足才返回 safe。硬上限耗尽直接失败，不允许接近截止才命中而省略完整恢复。Exit 从剩余行程重新分配预算。

成本含时间、紧通道、姿态变化平方、与当前转向趋势反向的代价。直达几何 simplicity 为 0、弧线/绕山为 1.5，保留小的简单性优势；不是几何直线能通就强制放弃曲线。控制器还用实际预测 miss、能量亏空、净空和预计时间与已有常规路线竞争。

初始搜索完成后默认隔 12 ticks 再发，pending 按下一 tick 领取。提交攻击前必须用当前状态完成 Rejoin/同等完整认证，可调整出口高度或验证库存推进。提交后 `TrajectoryCommitment` 保存路径，后台刷新使用时间对齐的有限证明，必要时从当前状态重验同一路径；另每 16 ticks 可选搜索明显更好候选，满足承诺窗口和切换成本才更换。接入和换路都不能绕过前摇、身体碰撞或完整 Exit，详见 [航迹连续性](../src/main/java/dev/winefox/skyfarer/flight/TrajectoryCommitment.java)。

源码：[SweepingAttackPlanner](../src/main/java/dev/winefox/skyfarer/flight/SweepingAttackPlanner.java)、[AttackLanePlanner](../src/main/java/dev/winefox/skyfarer/flight/AttackLanePlanner.java)、[AttackLinePlanner](../src/main/java/dev/winefox/skyfarer/flight/AttackLinePlanner.java)、[DiveProfile](../src/main/java/dev/winefox/skyfarer/flight/DiveProfile.java)。

### 6.2 曲线跟踪不是按几何直接移动实体

跟踪求女仆到离散曲线的最近投影，按速度前视 `clamp(speed*7,3,10)` 格；用速度相对切线的横向分量补偿鞘翅转向迟滞，目标足够近时将航向直接修正到目标。高度和目标垂直速度通过 `GlidePlanner.feedbackPitch` 转成 pitch；正常曲线 pitch 钳制 `[-65,55]`。

碰撞检查使用实际预测的身体轨迹，而不仅是绘制的理想曲线。几何曲线通过但实际动量飞不进去的候选仍会被拒绝。`Path.passedContact()` 同时约束路径进度和末端方向；仅末端切平面会错误拒绝一开始位于该平面另一侧的 U 形/绕山入口。

### 6.3 竖向剖面与 `runFlare` 约定

`runFlare` 目前同时承载模式哨兵，修改时需检查所有调用点：

| 值 | 当前含义 |
| --- | --- |
| `>=0` | 正常距离参数，距目标剩余值大于该距离才随 slope 改变高度，末端趋平 |
| `-1` | 有符号坡面直线：`height + remaining*slope`；允许上升和下降 |
| `-2` | 平地速降剖面：`height + slope*d²/(d+8)`，`d=max(0,remaining)`，末段渐平 |
| `-3` | 控制器标记当前选择独立 sweepPath；实际攻击由曲线跟踪，不按普通 flare 公式执行 |

普通入口要求剩余距离至少 `max(3, speed*(remainingWindup+2)+reach)`。look 与水平动量的允许夹角还受到达时间、`attack_turn_rate`、`max_turn_angle` 约束且最多 18°；这是普通入口规则，持续曲线入口不能套用该限制来强迫先对直。

瞄准参考点与真实碰撞目标分开：`TargetEvaluator.aimPoint()` 是包围盒中心 + `attack_aim_offset`（默认 .5 格）；控制器 `attackHeight()` 则取它与 `目标脚底 Y + attack_ground_clearance + 女仆眼高 + .15` 的较大值。额外 Lane offset 叠加在该高度上。虚拟长矛命中体积仍以目标真实包围盒上下扩展，不把这个瞄准 offset 写进实体位置或实际 hitbox。

### 6.4 地形采样不按方块名称分类

[SlopeSampler](../src/main/java/dev/winefox/skyfarer/flight/SlopeSampler.java) 以目标脚下高度为参考，在八个水平方向沿连通支撑面采样。默认半径 16、配置步长 .5 格；半径允许 3–32，实际采样步长限制 .25–.4 格，以免 70° 连续坡面相邻高度差超过支撑查询的 1.26 格窗口。相邻支撑高度从真实 collision shape 的顶面选择，包含雪层、半砖、楼梯等，不把整格方块占据当支撑高程。

每条射线遇到洞口或未知空间即停止该方向，不终止其他方向；近场拟合用 3 格内样本，广域拟合用全半径，输出两组加权平面梯度及样本数。近、远梯度都 <.16 且样本 >=12 才被判为平地。控制器层通常每 5 ticks、目标位移超过 .25 格或设置变化重新请求；底层异步缓存还有自己的有效性限制。

`AttackLinePlanner` 将沿路线的坡度和横坡梯度结合，候选包括地形坡度、当前直达坡度、水平、坡度 ±.1/±.2 及 ±50/60/70°；拟合与候选坡度限制为 ±tan(70°)。攻击段最短长度按坡度转换到水平投影，不把陡坡的水平距离当成整个三维长度。平坦地形上只要存在明显高差，也搜索有符号攻击线。接触高度额外偏移按 .25 格采样，出口可侧向转弯，抬升档为 0/3/6/10 格。成本是实际接入/攻击/离场长度、转向、未来能量亏空和离场转角，没有“上升攻击类型本身更贵”的固定罚分。能量亏空只参与成本，不能以单点机械能代替真实可达性。普通进攻承诺前由 `AttackCertificate` 完整验证新前摇、接触速度/射线、身体通道及离场；曲线由候选回放和 `Rejoin` 认证。

`DiveProfile.minimumDistance` 仍只约束原来的平滑收底剖面。APPROACH、REPOSITION 和补能准备改用 `attackDistance` 搜索准入，允许高差按 70° 投影进入候选搜索，不再先强迫飞远收平；原有前摇距离和最终证书保留。连续坡面仍保持原有地形净空准备，孤立高处目标保留下方进场准备。

局部坡面模型只能提出候选，不替代精确身体碰撞。绕山曲线不必与坡面拟合直线共线；陡坡或台阶没有一个全局单平面能代表全部通道。

## 7. 碰撞通道与紧急机动

### 7.1 真实体积扫掠

[FlightCorridor](../src/main/java/dev/winefox/skyfarer/flight/FlightCorridor.java) 将 block shape 数值盒按女仆半宽、身高、眼高和安全余量扩张，对眼点运动线段做碰撞，相当于检验身体沿轨迹的完整包络。`MaidTerrainSource` 在主线程复制已加载、可 entity-tick 区域内形状到 Snapshot；Worker 使用这些盒，不能自己拿 Level/VoxelShape 查询。

常规默认侧/竖余量为 `.35/.35`；无法通过时部分模式允许减为 `.04/.04`。曲线硬检查用 `.15/.12`，另以 `.5/.4` 判断是否宽敞并计成本；起飞使用 `.04/.01`。不能把这些余量当作实际实体尺寸，它们是身体外的额外距离。

世界边界、未加载或不更新实体的区块以及缺失快照都不能视为空气。普通路径拒绝液体体积；只有水逃逸允许水，熔岩仍阻挡。突出原方块格的形状由一格 halo 纳入。

### 7.2 规划模式是逐级放宽控制权

[GlidePlanner](../src/main/java/dev/winefox/skyfarer/flight/GlidePlanner.java) 先尝试保持攻击航向的竖向修正和余量收缩；再考虑改变通道高度；再尝试横向 ±25/±50°。更激烈选项在正常方案没有安全解时才考虑：

| Mode | 当前触发/用途 |
| --- | --- |
| `GLIDE` | 通常滑翔与追踪 |
| `RECOVERY` | 爬升、变化高度、坡面恢复；并非失败标志 |
| `TURN` | 无法仅调整高度通过时改变航向 |
| `PRECISION` | 无活动火箭且预测完全停止的水平撞击损伤 <=4，允许低速较快纠偏 |
| `AIRBRAKE` | 先尝试 -88° 抬头，仍需完整身体通道，低顶棚可拒绝 |
| `REVERSAL` | AIRBRAKE 无安全解后，尝试 180° 回头、pitch 0/-20 |
| `NO_CLEAR_PATH` | 没有完整安全候选，保留预测可存活 ticks 最长的控制方向，`safe=false` |

`ordinary()` 的含义是 `safe && 非 AIRBRAKE && 非 REVERSAL`；TURN/PRECISION/RECOVERY 也可能属于 ordinary。其基本代价约为 GLIDE 1、普通 RECOVERY 4、TURN 10、PRECISION 14、AIRBRAKE 30、REVERSAL 50；坡面恢复可降到 1。候选先分层，再按各层成本比较，不能把它当所有动作同一平面上的纯数值竞赛。

撞墙能量估算为 `max(0,(beforeHorizontalSpeed-afterHorizontalSpeed)*10-3)`。它控制低速纠偏策略，不允许为了省动作而主动撞墙；所有候选仍要通过扫掠。

### 7.3 时间范围与最后安全防线

普通 Glide 首 24 ticks 是硬安全窗口；`prediction_length_multiplier` 扩大的远端碰撞仍采样并增加风险成本，不能把无限无动力延伸最终落地当成禁止现在起飞。完整烟花寿命 + 后续滑翔的验证另有硬窗口，不因远端软惩罚而省略。

0.6.2.1 在状态分支前另有真实动量 TTC 检测：速度、转向角差决定 6–32 ticks 余量，危险保持 12 ticks；曲线使用动态控制律。独立紧急请求先验六个滑翔候选，无解才搜索全寿命烟花转向，执行前用当前状态复核，详见 [紧急避障](STABILITY_LAUNCH_0621.md#紧急避障)。陡角 Exit 俯仰上限为 14°/tick，预测与执行一致。

0.7.1 等待紧急 Worker 时不再仅抬头：保留可用的 12 tick 逃逸姿态，失效才比较最多六个姿态；全部以实时身体扫掠检查，不授权点火，也不取代完整逃逸认证。连旧姿态复核最多 84 步。安全控制可立即抢占路线/激流，相关动态墙体 + 12 tick Worker 停顿反例见航迹专题。guided 运行时仍由自身的完整推力/恢复复核守卫，避免漏算激流推力的 coast TTC 误抢占。

主线程 `validateImmediateMotion()` 对当前真实身体和实际世界检查本 tick，依次当前 pitch、-35/-65/-88，再必要时检查一次 180°/-20 回头。它不执行整套候选搜索，也不把未知空间当安全。正常 `LANDING` 跳过这条防线，否则着陆会被抬头回避。

快照有年龄，动态放方块、爆炸、其他 Mod 外力、区块加载状态变化都可能让旧预测失效；因此不能删除当前步 live guard 或最终伤害射线来追求“完全异步”。[CorridorProbe](../src/main/java/dev/winefox/skyfarer/safety/CorridorProbe.java) 仍是主线程实时适配器，与 Worker 的 `CollisionSnapshot.Space` 不可混用。

## 8. 统一推进与能量

`manageEnergy`/曲线跟随先评估 coast；飞控向 `FlightPropulsion` 提交需求与 Turn/Curve/Glide 路线，由 `PropulsionResolver` 权威选择。永燃独占时只允许虚拟推进或安全恢复，没有永燃时合法且通过全段认证的 READY 激流优先，烟花后备。普通等待有界、紧急不等待未准备资源。低速本身不能推翻已经可命中的滑翔曲线；pending 不等于 coast 失败。

持续速度保持由可选 Provider.sustain 接入，在当前路线下重验有界动力窗口，仅提交下一 tick。有限虚拟加速结束可继续供力，保留无动力恢复证明；推力、姿态和预测共用纯数值模型。详见 [0.7.3 仲裁与持续控制](PROPULSION_073.md)。

激流拥有推进期间的姿态与剩余路线认证；附着推力继续使用既有 Vanilla 积分器，预测参数只由推进服务提供。火箭完整随机寿命、冷却、爆炸惩罚、库存和点火延迟留在提供器。激流环境、装备、READY、可控时长与副手释放留在激流层。没有把两者视作同一种力。

完整需求矩阵、READY 生命周期与物理差异见 [0.7.0 架构](ARCHITECTURE_070.md)。`EnergyReserveEnvelope` 仍衡量整段未来需求；碰撞、接触和完整 Strike→Exit 是硬否决条件。预测伤害不能直接扣血。

## 9. 长矛实际命中与多目标连续攻击

[SpearCompat](../src/main/java/dev/winefox/skyfarer/compat/spears/SpearCompat.java) 读取当前 Spears 的组件，获取 delay、charge window、mob reach、margin 和速度门槛。mob reach 按该依赖的 mob 规则取攻击范围一半，伤害阈值转换到 blocks/tick；主状态机不能硬编码某一根矛的数值。

预测 `Aim` 是数值 DTO，包含目标运动、虚拟上下容差和前摇剩余时间。预测 hit 只用于评分和决策，真实 `recordSpearHit(UUID)` 才更新 [SpearPassHistory](../src/main/java/dev/winefox/skyfarer/ai/SpearPassHistory.java)。每轮未命中次数在 settle/finish 计入，实际命中清除该目标 misses；历史最多保留 256 个失败目标，默认两次未中就优先换目标。

[MaidSpearCollisions](../src/main/java/dev/winefox/skyfarer/compat/spears/MaidSpearCollisions.java) 只扩展本 Mod 女仆长矛查询的虚拟竖向范围，默认上下各 3 格，不改目标实体实际碰撞箱、不修改 Spears Mod 本身：

1. 先把 native hits 按 UUID 收集。
2. 真实矛线从 minReach 到 maxReach + 向前位移延伸；遇到方块则截断。
3. 找扩展竖向柱与矛线的交点，再从虚拟交点连接目标真实表面，阻挡则拒绝，防止隔地板/墙命中。
4. 与 native hits 合并去重；本轮命中历史进一步抑制同目标重复伤害。

`post_attack_charge_ticks=60` 是完成命中/正常掠过后可保留原使用动作的时间。`MaidSpearChargeMixin.wfs$withdrawalCharge()` 在有效 tail 内将传入原生 `usageTick` 的经过时间钳在可用窗口，保持同一 use session、原生伤害计算和冷却，同时延长本女仆的离场充能有效期；不重新开始一次使用。这允许出口上自然穿到其他目标，但不要求飞行继续 60 ticks 才离场，也不会延长 ATTACK_EXIT。主攻击和主动 `CHAIN_ATTACK` 仍自行检查实际 using ticks 是否超出原生窗口；主动左右追击另有短程预算，只有主目标真实命中后才能确认。

真实命中回调仅在原生 `pierce()` 成功且目标生命值加吸收值减少、或目标死亡时记录；只有命中反馈而没有实际伤害不会算成功。`SpearMovementMixin` 将上一完整 tick 测得位移乘 20 提供给 Spears 的每秒速率查询，修正服务端 `xo` 更新时序造成的零速度，不制造虚假的攻击速度或伤害数值。

修改碰撞扩展应同时检查 `SpearHitGeometry`、`SpearFriendlyFireMixin`、`MaidSpearChargeMixin`、`SpearMovementMixin` 和对应离线检查，不能只改 Aim 让模拟更容易命中而实机伤害仍不成立。

## 10. 指挥镜赶路及高速特例

源码：[CommandManager](../src/main/java/dev/winefox/skyfarer/command/CommandManager.java)、[TransitFlight](../src/main/java/dev/winefox/skyfarer/command/TransitFlight.java)、[CreativeTransitPlanner](../src/main/java/dev/winefox/skyfarer/command/CreativeTransitPlanner.java)、[DispatchPolicy](../src/main/java/dev/winefox/skyfarer/command/DispatchPolicy.java)。

### 10.1 与普通状态机的边界

`TransitFlight` 是订单专用控制器，不是 `AirCombatState` 的另一个枚举值。订单赶路时 `CommandManager.beforeMaidTick()` 返回接管，跳过 `AirCombatController`。已分配目标在到达交接半径前保持，不因途中发现别的怪而掉头；普通订单 48 格，创造订单 **3D 距离 32 格** 交接。

赶路免费推进，不写女仆库存。普通赶路也遵守永燃独占/激流优先；允许烟花后备时生成显式 Flight 0 的附着烟花，仍按有限寿命、真实起飞/局部预测和安全通道移动；它不是整个世界的全局寻路器，普通 Glide 请求每约 5 ticks 刷新并做当前步 live 检查。交接销毁免费火箭、关闭赶路会话，速度长度超过 1.7 则缩到 1.7，再交 `acceptCommand(target)`。之后战斗使用真实库存和正常伤害规则。

赶路失败关闭 transit，请求普通返航并设置 200 ticks 重试等待；创造女仆落地且目标仍远时可重新创建赶路控制器，不能一直保留“曾经已经到达”的状态。

### 10.2 创造高速赶路

默认倍率 2，范围 1–100；参考速度 `BASE_SPEED=1.7 blocks/tick`。默认世界 Y=200 的巡航高度同时受目标高度、剩余水平距离能支持的爬升以及维度高度上限限制，有顶维度用局部高度目标，不直接追求 Y=200。

水平距离到 64 格附近进入下降，目标为目标 Y+1.4；接近 32 格交接区时按剩余距离自动缩小速度，避免一 tick 穿过目标/交接窗口。朝向仍做可见姿态平滑；运动通过计划的实际速度输入推进，不把倍率用于战斗武器伤害。

**线程例外要保留准确：** `CreativeTransitPlanner.choose()` 是纯数学函数，但目前由主线程调用，传入本 tick live 身体扫掠；它对当前步最多尝试 12 次减半距离，确认证明每段真实位移可通过，之后才能设置实体速度。不存在把未知/未加载缺口作为 teleport 穿过的分支。没有直接通道时将速度缩回最多 1，转普通局部飞行；连续阻挡超过 100 ticks 失败。该极端高速 live 工作量仍需实机测量。

### 10.3 票据与 Home 回收

创造指挥由 `CommandTickets` 管理当前和前向通道的 entity-ticking 区块票据；不是 Worker 执行世界加载。地面可等初始票据就绪最多 100 ticks，空中不能冻结等待。

发现阶段由 `RemoteWakeScheduler` 保持每玩家每 10 ticks 最多 2 只、全服最多 8 个 wake、100 ticks 超时。未尝试者优先，已失败者在 200 ticks 冷却后按旧尝试顺序重试；上限不会把等待者永久标记为已尝试。generation 随标记增删、死亡和清理更新，旧 wake 被释放；成功后移交独立 Order/TransitFlight，其他同 owner 候选继续获得批次。registry 保存所有者和最近位置等事实，不保存 attempted；旧工作模式否定记录也允许醒来重新核实。完整日志和资格条件见 [配置调试参考](CONFIG_DEBUG_REFERENCE.md#42-发现与分配)。

创造订单仍有存活且已加载的有效创造标记时，`holdHome()` 暂阻 TLM 落地 Home 回收。标记结束/失效后，取消订单、关闭 Transit，Home 女仆在任务结束位置附近安全落地，释放 managed 控制并放行原生 SchedulePos；0.6.2.9 不再由 WineFox 周期调用 Home 传送或飞行返乡。原当前位置/Home recall 票据最长 200 ticks，回到限制区或超时释放；普通订单完成仍给原版地面 Home 回收约 45 ticks 机会。真实 TLM/Mixin、票据激活时序及第三方传送事件并非离线夹具可全部证明。

## 11. 异步 Planner 与实际飞控之间的关系

完整机制见 [ASYNC_PLANNING.md](ASYNC_PLANNING.md)。飞控修改至少遵守以下边界：

| 主线程持有/执行 | Worker 可持有/执行 |
| --- | --- |
| EntityMaid、目标实体、库存、Level、BlockState、VoxelShape，实际状态枚举 | 复制后的数字、UUID/目标身份、不可变记录、数值 Box/曲线 |
| 采集碰撞快照、提交/取消任务、检查实际资格和目标生存 | 请求范围内的通道数学、坡面拟合、候选生成与预测评分 |
| 结果校验、目标/阶段转移、姿态/速度/滑翔标志、烟花消耗和生成 | 输出控制/几何/trace，不修改女仆、不调用状态转移 |
| 最终真实攻击、当前步 live guard、区块票据与着陆点探测 | 用快照判断接触段遮挡，不访问外部世界回调 |

`planningPhase()` 合并 `APPROACH/REPOSITION -> ENTRY`、`ATTACK_RUN/DIVE/SPEAR_CHARGE -> ATTACK`、`TAKEOFF_PREP/TAKEOFF -> LAUNCH`；其余按枚举名。阶段、目标、配置 revision 或维度变化会取消旧 generation。把中间状态拆成不同 generation 会使可复用任务不断失效；把真正互斥任务并在一起又会错误套用旧结果。

当前结果有年龄、位置、姿态、速度、目标运动和请求兼容检查；曲线候选可以短暂保留为几何种子，但 `Rejoin` 的当前接入证书要求更严格。火箭预计/实际剩余寿命也属于请求有效性，不能使用无动力计划控制刚点燃的火箭。

等待规则不是所有函数都相同：

- 必需的 Lane、Launch、Exit 没结果会抛无堆栈 `PlannerSession.Pending`，上层等待下个 tick；不得主线程 `get/join` 或同步重算。
- 普通 Glide 没结果提供 O(1) feedback 姿态，但标记不安全；`terrainSafety()` 不把单纯 waiting 误当已证明的路径失败，最后靠 live 当前步检查。
- Compact 等待时使用保守制动参数和当前状态重算方向；曲线跟踪保留当前路径，使用时间对齐的有限证明并重算当前 command。Worker 负责滚动刷新，后缀不可用时主线程只对选中路径做当前严格数值重验，不重新搜索候选，也不读取全路线实时世界。
- Pulse 未完成返回无限成本/`async_pending`，禁止未经完整生命周期验证先点火。已完成 pulse 保存了各寿命对应的首步姿态，可在真实火箭产生的同 tick 使用。
- Slope 等待时临时返回零样本估计；零样本不能满足平地 >=12 样本条件，不能把“还没有数据”当“已证明平坦”。

调试轨迹从已有结果读取，不为渲染再运行一次全套 forecast。保留这条原则，否则开启 debug 会改变主线程和 Worker 压力，妨碍复现。

## 12. 调试、回归与当前限制

### 12.1 排查顺序

| 症状 | 先看 | 再看 |
| --- | --- | --- |
| 不起飞 | GROUNDED reason、装备/主人/姿态、retryAt | PREP 站立空间、launch pending、所有 duration 是否被拒绝 |
| 起飞后一直绕圈 | state/setupTicks、compactGoal 是否固定、候选 pending/age | staging/inward 切换、目标原点位移造成的请求失效、转向/升力预测 |
| 曲线刚选中就退出 | `CONTINUOUS_ATTACK` 后的 reason、曲线进度 | `passedContact()`、Rejoin 证书、真实 runTicks/前摇 |
| 目标前慢下来却不补能 | `energyDecision/pulseReview/cooldown/rocketTicks` | 完整火箭寿命的 miss/exit 拒绝、异步等待与过期计数 |
| 山坡前过早拉升 | `runFlare/laneStatus/curve_style`、实际黄通道 | true collision shape、坡面采样数、GLIDE vs AIRBRAKE/REVERSAL |
| 掠过没伤害 | native hit 回调、使用 ticks、武器实际门槛 | 接触射线被截断、最小触及距离、相对速度、虚拟容差与去重 |
| 总返回主人 | `returnReason/pendingReturn/combatAnchor` | Home/订单 anchor 优先级、标记层级、工作资格和滞空燃料限制 |
| 出口飞远 | 固定 recoveryOrigin、exitTravelled/limit、当前方向 | 旧出口是否被重置、误补火箭、Compact 的范围约束 |
| 主线程卡顿 | 实机 profile、active maids、快照指标 | 原生目标扫描/落点/实时碰撞与创造高速扫掠；不要把 workerMs 当主线程时间 |

调试窗口的状态/原因、entry 点、曲线类型、能量和异步状态应一起看。黄色通道是预测身体体积，蓝色指令/轨迹与拟合曲线不等同于实际可达轨迹；`attack_curve` 保存选中理想曲线点，`sweepTrace` 是实际动力学预测。曲线入口/方向来自当前路径，普通入口来自 Lane。具体颜色、按键、绑定和命令以 [CONFIG_DEBUG_REFERENCE.md](CONFIG_DEBUG_REFERENCE.md) 为准。

### 12.2 需要覆盖的验证链

与飞控有关的修改应按影响检查纯数学、真实生产控制器回放及异步调度，入口均收录于 [VALIDATION.md](VALIDATION.md)：

- 从地面索敌 -> 起飞 -> 入场 -> charge -> 实际接触回调 -> exit -> 续战/返航 -> 着陆，不能只验证某个入口几何函数返回非空。
- 平地/坡面、真实雪层盒/半砖盒、窄通道、水中恢复、低能量与不同烟花寿命。
- 连续绕山左右镜像、被暂时遮挡的锁定目标、曲线起点在末端平面另一侧，以及异步延迟后重新接入。
- 多轮攻击、目标消失、工作取消、燃料耗尽、飞行边界、回收后禁止继续充能。
- `verifyAsyncPlanning` 的线程边界/取消/陈旧结果，`verifyAsyncSweep` 的冷快照与几何查询，`verifyPlannerLoad` 的多会话公平性和上限。
- 指挥镜普通/创造、友好标记、900 格与高倍率交接，赶路免费推进在交接后确实结束。

离线回放运行生产控制器和实际共享线程池，世界/实体外部 API 由独立夹具实现，运动用生产预测公式推进。它不会启动 Minecraft，也不能替代引擎移动时序、原生 Spears 伤害、真实异步区块加载、其他 Mod 干预、客户端网络和模型动画的实机验证。

### 12.3 当前限制与不要误读的事实

- 本文记录实现和既有验证入口，不宣称每种自然地形都能命中；有限候选和有限已加载空间可以正确返回无安全路线。
- 曲线、坡面、普通 Glide、Compact 和 Chain 是组合控制，尚非统一全局最优路线求解器。不存在整维度导航图或保证最终可达的绕障搜索。
- 快照更新有间隔；实体碰撞、特殊方块动态逻辑、第三方运动实现和渲染仍需实机确认。安全 guard 降低旧结果风险，不等于全世界动态变化的数学保证。
- 0.7.0 已删除不可达枚举与三个无效配置，迁移依据见架构审计。
- `prediction_length_multiplier` 增长的是接入了 PredictionHorizon 的预测采样；不能声称所有 Planner、曲线长度、搜索半径和快照预算同时按该倍率放大。
- 主线程仍承担目标实体扫描、实际接触、有限高度/着陆探测和当前步真实碰撞，创造高倍率 live 扫掠是显著需要实机剖析的边界。合成负载数字不是 MC MSPT/GC 优化率。
- 用户负责 Minecraft 实机测试。当前工程维护规则是不安装/启动 Minecraft，仅构建与离线验证；构建与更新报告写 [buildHistory.md](../buildHistory.md)。


### 0.7.0 最终下降与紧急交接

LANDING 使用 LandingApproach 有界复核实际下降轨迹；仅有支撑、站立净空合格的预定触地可作为安全终点，不把地面接触一律当作应点火逃离的障碍。紧急路线仍完整认证，但初始脱险姿态只占用 8 tick，之后由当前恢复状态重新决策，保留实际活动推进预测。详见 [架构审计](ARCHITECTURE_070.md)。

暂时遮挡的已观察合法目标可在 loiter_time_ticks 内复用有界 CompactTurn 恢复视线；固定观察点、推进需求和实际路线保持一致，隐藏目标本身不进入攻击候选。可见记忆每 5 tick 最多刷新一次，过期/返航/释放清除。
