# WineFox: Skyfarer 0.7.0 架构审计

基线：本地干净提交 `172d9f5`（0.6.2.11），未使用远程覆盖。源码为最终事实。本页核对日期：2026-10-08。

## 推进边界

```text
AirCombatController / TransitFlight
  → PropulsionDemand + PropulsionRoute
  → FlightPropulsion
      → PropulsionCapability（资格、优先级、有界等待）
      → RiptidePropulsion / RiptideReadiness
      → FireworkProvider / FireworkManager
  → AsyncPlanning → PlanningTask + CollisionSnapshot
  → bounded Worker → mailbox → main-thread validate/apply
```

`PropulsionDemand` 表示目的、位置/高度、最低速度、曲线、目标运动、接触时限。
`PropulsionRoute` 表示转弯、曲线或滑翔所需的认证。上层不再传入按火箭 duration 编写的回调，也不再持有两个推进执行器。

`FlightPropulsion.Decision` 返回控制方式：guided 执行暂时接管姿态，attached 执行继续按飞行路线引导；waiting 表示有界准备/预测等待。这是控制权区别，不是给激流套用火箭模型。

底层 Vanilla 数值规划器仍显式包含 rocketTicks/rocketDelay。`projectedTicks`/`projectionDelay` 只描述已附着的 Vanilla 推力，不能表示激流；激流执行期由自己的每 tick 推力模拟与剩余路线重验接管，不把两种推力叠加。保留这些数学接口避免重写已验证的 Vanilla 积分器。

| 路径 | 统一边界 | 保留的专属机制 |
| --- | --- | --- |
| 装备、普通/创造指挥资格 | PropulsionCapability 实时快照 | Registry 不推断天气；只存最后位置 |
| 起飞、水面脱离 | FlightPropulsion 优先准备与起飞证书/执行 | 普通跳跃、滑翔尺寸、READY 释放、完整寿命及离地余量 |
| Launch Attack、Rejoin | 共同需求与已认证路线；提供器内部生成资助候选 | 原生前摇、接触、完整 Exit，不以预测代替伤害 |
| APPROACH/REPOSITION、空中恢复 | Turn/Glide 需求，经共同选择 | 完整可持续飞行下限；滑翔够用则不推进 |
| Strike/Exit | Curve 需求 | 目标运动、Spears 窗口、身体/射线和全段离场 |
| Emergency | 滑翔先行，READY 激流优先于新的火箭 | 紧急不等待未完成 READY；六个逃逸姿态、真实动量与完整逃逸 |
| Transit | 同一执行器、关闭与交接边界 | 既有免费短火箭运输策略仅限 Transit；创造高速运输仍是显式权限特例 |

火箭 inventory/duration/random lifetime/ignition delay/active entity/cooldown/explosion penalty 均在提供器及数值物理层。激流环境、确切副手、耐久、等级、READY、事件门、spin、声音、可控时长保留在激流提供器。上层不会自行创建火箭、遍历寿命或操作 READY。

## 保留的 READY 与激流规则

本地 pinned 1.21.1 TridentItem 源码的 release 门槛仍为 10 tick。WineFox 使用独立的 10 个服务端有效 tick 资源，不长期占用实体 usingItem。环境、三叉戟、主手/胸甲身份、Level 变化会失效；漏 tick 不补发离线 READY。显式 Transit/Combat 交接可保留资源，普通关闭/卸载清除。

释放经过 NeoForge stop 事件门和实际副手 ItemStack.releaseUsing 的同步授权回调；耐久、spin、声音与首 tick 冲量由该回调产生。主手 Spears 使用状态不被停止。玩家三叉戟行为不变。

I/II/III 的可控推力上限为 10/20/30 tick，每 tick 增量 0.2；等级扩大上限，短时长仍可被选中。起飞认证推力截止、24 tick 恢复与转向余量；普通恢复认证 12 tick 尾段；攻击需求认证完整 Strike→Exit。没有一发必须飞到远目标的要求。

## 已删除与依据

| 删除项 | 确认依据与替代 |
| --- | --- |
| LaunchPlanner | 无生产调用；旧固定 26 tick 起飞搜索已由 SurfaceLaunchPlanner/LaunchAttackPlanner 取代。旧专用测试移除，实际起飞测试保留 |
| RiptidePlanner 的旧坐标/elapsed choose 重载 | 仅旧验证调用；把 elapsed=10 同时解释为起飞的隐式入口已被带 Phase 的 PropulsionDemand 替代；测试迁移到当前 READY 入口 |
| FireworkManager 无证书 boost()/boost(wantedTicks) | 无生产调用；会绕过完整路线证明，保留必须提供证书的执行入口 |
| PropulsionAvailability | 只转发能力快照的重复外壳；资格与拒因合入 PropulsionCapability，普通/创造调度调用同步迁移 |
| Transit 独立火箭实体/寿命实现、lastRequest | 火箭收归 FireworkManager 的运输策略；lastRequest 只有写入，无消费者 |
| BREAKAWAY / ABORT / EMERGENCY 枚举及分支 | 没有任何实际 transition 进入；beginBreakaway 实际进入 ATTACK_EXIT，紧急机动是该状态内的认证策略。状态不持久化，不按 ordinal 传输 |
| arcPoint 有目标分支及 climbHeight/previousTargetBonus 包装器 | 唯一调用点 LOITER 已先处理非空目标；包装器无有效行为消费者 |
| RocketProfile.score / AsyncPlanning.pulseCost | 旧期望时长评分及无调用转发已被完整路线证书取代；原版寿命边界与统计寿命仍供物理回放使用 |
| DebugSnapshot.previous_bonus | 固定写入 0、网络/客户端/测试均无读取者的历史调试字段 |
| RiptideReadiness.item | 无调用者；确切装备仍在 readiness 内部校验 |

## 配置审计与迁移

删除 `targeting.previous_target_bonus`、`combat.climb_height`、`safety.forward_collision_check_distance`。
前两者分别已被战略分层/当前飞行高度取代，第三项从未被当前动态碰撞预测读取。
它们不再出现在 `/wfs config`，旧 TOML 值不再读取，NeoForge 按新 Spec 校正/保存时可移除旧键。无需把旧值迁移到其他参数；升级前可保留自己的配置备份。

[52 个保留数值参数的消费者清单](config-consumers-070.tsv) 记录生产读取位置；Boolean debuglog 与额外物品列表另由 Settings/装备入口读取。其余参数逐项检查直接读取、Settings 包装器的生产调用者以及调试/命令消费者后保留。尤其 max_turn_angle、dive_max_correction_angle、firework_cooldown_ticks、spear_vertical_expansion 和 prediction_length_multiplier 都实际影响行为，不因为名称较旧而删除。兼容鞘翅列表默认含 Cataclysm ID，仍无硬依赖且必须由物品自身提供滑翔能力。

## 全项目审查范围与保留决定

- AI/flight：保留直线/坡面与 Sweeping 两个有消费者的候选族、ChainAttack、Strike/Exit 证书和安全落地。没有重新引入 Funnel。
- async：任务是封闭数值 DTO；live corridor 回调仅留主线程，传给 worker 的是数值 Bounds。保留双队列、generation、取消、mailbox、未知地形拒绝、当前状态重验和全服预算。
- command/runtime：保留 wake 的 generation/attempted/轮转、票据上限、loaded Home 日程交还、实体实例替换与卸载清理。没有把 saved record 当实时 Entity。
- equipment/Spears：保留 Unbreakable 与真实 damageable 判定、备用更换、空中返航、普通左键内外 reach、TLM 导航/跳跃、事件去重和 owner 限频。
- network/client/debug：服务端授权、nonce/owner/物品校验、快照生命周期和客户端渲染保留；调试观察不能创建 planner 或提交额外工作。
- mixin/compat：按 mixins JSON、服务注册与事件订阅检查，缺少 Java 调用并不代表死类。TLM/Spears 钩子、可选 YSM 版本门和默认可选物品列表保留。

## 性能与验证边界

- 火箭候选时长在执行器的当前 tick 内复用，释放时重新验证/提取真实库存；不跨 tick 保留资格结论。
- Glide 替代 Exit 的临时 map 改为提供器持有并按请求清理；READY 不再每 tick 新建同一 Level 的 WeakReference。
- 去除 Transit 同一恢复决策先 prefer 再 request 的重复提交。既有 slot 去重、缓存、快照和队列预算没有扩大。
- 新 verifyArchitecture 对编译后的上层方法（包括 lambda）检查提供器依赖，检查 worker DTO 字段以及配置/状态清理。

这些是可检查的分配/调用变化，不宣称未经 profiler 测量的 CPU/MSPT 提升。全量验证结果见 [VALIDATION](VALIDATION.md)；Minecraft 真实时序、第三方 travel 修改、原生伤害/客户端动画与多人长时间负载仍需实机验证。本次不启动 Minecraft。

## 连续作战回归修复

全量回放暴露慢速曲线被多余推进扰乱：pending 以及低速/名义能量不足会触发烟花，即使完整滑翔接触与 Exit 已可行。曲线现在等待实际证书，并只在路线不安全或无法接触时请求推进；没有降低碰撞、命中或离场阈值。普通巡航能量储备仍保留。Controller 回放逐例释放会话，并聚合失败以覆盖全部 21 场景，不降低断言。


## 落地与紧急控制的回放修复

固定前视 TTC 曾把最终下降的预定触地误判成墙体碰撞，继而在 LANDING 中点燃火箭或长时间拉起。LandingApproach 以实际最终下降控制律进行 32 tick 有界预测：身体扫掠必须通畅，接触参考地面时必须有真实支撑、无液体且站立身体容得下，才把触地作为合法终点。墙体、未知区块和无支撑仍拒绝；不会全局关闭落地避障。

紧急方案的完整认证覆盖火箭所有可能寿命及后续滑翔，但认证时域不再等同于连续独占飞控的时长。执行初始 8 tick 脱险姿态后，交回当前状态的恢复决策，以实时速度重新选择路线；已附着火箭仍由真实剩余寿命参与后续预测。这样避免固定逃逸航向一直飞出战区，或把一段勉强安全的滑翔执行到能量耗尽才重新规划。

回放生命周期允许两条合法路径：普通 TAKEOFF→CLIMB，以及完整认证的 TAKEOFF→ATTACK_RUN（Launch Attack）。后者不再因为未访问 CLIMB 被判作未完成起飞；意外触地、命中、落地和释放断言继续保留。

返航阶段若紧急推进无可执行证书，可用同一 LandingApproach 认证就近下降；仅已决定返航的状态可进入，不把仍有目标的普通空战改为逐轮落地。落地后正常释放，由 TLM 恢复地面/Home 行为。


上坡回放发现紧急离场后的临时遮挡会被当作战斗结束。仅对实际看见过、仍合法存活的目标保存最后可见位置；持续追踪时每 5 tick 最多复核一次视线，不依赖重新选目标。在既有 loiter_time_ticks 内，复用 CompactTurnPlanner 到最后可见区域外侧的固定观察点建立高度，点位距观察中心 32 格、整体规划限制在 48 格，不随重试向外扩张。目标重新可见后才恢复攻击选择。

观察恢复使用统一 Turn 推进需求：完整认证 coast/激流/火箭和转弯轨迹，低能量下降时不必等到距地面小于 8 格才申请补能；不会沿固定航向冲入目标后方更高坡面，也不拿 Home/主人方向否决当前观察航点。记忆超时、目标失效、返航/卸载均清除，洞穴内从未可见的目标仍不能触发起飞。保留 0.6.2.9 的软返航恢复能力：即使进入 LANDING，只要尚未触地且 canResume 的目标/装备/推进/工作/时间条件成立，仍可恢复空战。Command/Home 收尾、装备故障等硬返航不取消。

回放夹具的 landingNear 与生产保持相同的有限向下搜索范围。异常爬升的 55 格限额改为相对起点/目标作战高度计算，避免把平地、山腰场景都限制在固定世界 Y=55；平地限额不变，完整扫掠、首次攻击时限、战区半径、命中、落地和资源释放断言继续保留。

Controller 的早期断言曾无条件禁止 LANDING 恢复空战，与现有 AirRecoveryHome 专项和 canResume 行为冲突。现只允许任务仍未完成、未触地且看到合法目标时重新进入攻击路径；完成/取消任务后重新点火仍失败，最终命中/安全离场/落地仍必须完成。没有为了旧断言恢复强制每战落地行为。
