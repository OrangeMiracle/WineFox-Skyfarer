# 配置、指挥、调试与兼容接口

0.6.2.11：推进资格、需求与激流候选统一；独立 10 tick 预蓄力保持 READY，不占实体 item-use；攻击/离场/紧急与 Transit 接入同一能力层。当前规则与历史覆盖关系见 [统一推进与 READY](PROPULSION_06211.md)。下列旧版本段落保留为历史，涉及实体副手持续使用、禁止 Strike/Exit 激流及旧 PlanningTask.Riptide 的描述均已被替代。

0.6.2.10：移动目标使用三组未来拦截点，当前状态重验 Strike→Exit 后提交；准备区围绕预计拦截点保持有界，补能不再向外延伸或抢占可用攻击入口。机制、诊断、比较回放与限制见 [移动目标拦截](MOVING_ATTACK_06210.md)。

0.6.2.9 既有变更：空中恢复使用独立的速度证书与实际 12 tick 恢复段；创造 Home 任务就近落地后释放控制，由 TLM 日程返回。源码因果、接口及验证边界见 [空中恢复与 Home 交接](AIR_RECOVERY_HOME_0629.md)。

0.6.2.8 未新增配置开关。RIPTIDE 的 RECOVERY_BEGIN 表示推力/spin 截止但起飞恢复尚未完成；COMPLETED 在恢复及实际交接检查通过后才记录，否则 HANDOFF_NOT_READY。EXECUTION_REJECTED 附带 snapshot/trajectory/entity/装备交换/开始使用等具体原因。COMMAND_ASSIGN 增加 propulsion；地面订单和已加载候选补记 qualification_failed，缺资源区分 no_firework_riptide_environment_invalid / no_firework_or_usable_riptide。保留 PROPULSION 的 targetDistance、purpose、duration，以及 RIPTIDE 的 duration/maxDuration、departure、thrustElapsed；后者在无推力恢复期仍为释放后的 elapsed，不代表仍在施加推力。详见 [装备与生存](EQUIPMENT_SURVIVAL.md)。

0.6.2.6 的 RIPTIDE 诊断增加 selectedHand/item、usedHand、startTick、charge/remaining、sameUse、wet、enchantLevel、实际 RELEASE_CALLBACK impulse/spinStarted，以及明确的 END 拒因。CHARGING 每 5 ticks 采样；STATE_TRANSITION 与 USE_STOP 的有界调用栈说明切换和取消来源。原生事件否决或回调缺失记录 RELEASE_VETOED_OR_CALLBACK_MISSING，不静默补发冲量。详见 [释放审计](EQUIPMENT_SURVIVAL.md#0626-激流使用与释放审计)。

0.6.2.2 新增 equipment.extra_elytra_items（字符串列表，0.6.2.5 默认含 Cataclysm 胸甲 ID），示例：["cataclysm:ignitium_elytra_chestplate"]。只识别身份，仍要求物品自己的 canElytraFly；未安装或拼错的 ID 忽略。SERVER 配置 reload 生效，reset all 恢复默认列表，数值 set 命令不编辑列表。WINGS_REPLACED 与 RIPTIDE 生命周期事件加入 debuglog。0.6.2.3 明确 UNBREAKABLE 优先，装备提示按持久事件去重及双层冷却限频。0.6.2.4 用独立的 EQUIPMENT_CHECK / EQUIPMENT_SPARE / EQUIPMENT_RESULT 代替受对话去重影响的 WINGS_UNFIT；首次、变化、每 200 ticks 记录实际列表、item ID、listMatch/equivalent、damage/maxDamage、damageable、UNBREAKABLE/MAX_DAMAGE/DAMAGE component、remaining、canFly、备用槽拒因和最终 equipmentQualified/reason。兼容 ID 必须填写日志实际加载的文件，不是仅保留注释示例。完整用法见 [装备与生存](EQUIPMENT_SURVIVAL.md)。
核对版本：**0.6.2.7，2026-10-07**，飞控基于 0.6.1。本文面向维护者，描述当前源码事实；源码变化时必须同步维护。总入口见 [PROJECT_INDEX.md](PROJECT_INDEX.md)，飞控细节见 [FLIGHT_CONTROL_OVERVIEW.md](FLIGHT_CONTROL_OVERVIEW.md)。本文中的 tick 是游戏逻辑 tick，20 TPS 时 20 ticks = 1 秒；格/tick 不是格/秒。

0.6.2.5 将额外鞘翅列表默认值设为 `["cataclysm:ignitium_elytra_chestplate"]`，没有 Cataclysm 时安全忽略，不提供额外飞行能力；已有配置中的显式列表（包括空列表）继续保留，`reset all` 恢复此默认列表。新增 `PROPULSION` 日志记录候选选择、证书等待/拒绝、实际湿润条件、可用强度、火箭冷却与 active 状态；`RIPTIDE` 记录地面前摇、跳跃、实际释放及路径重验拒绝，可区分环境、库存、规划和执行阶段。

0.6.1 未新增可调物理伪属性：gravity/缓降从实体冻结，滑翔阻力使用 1.21.1 常量。`energy_reserve` / `attack_energy_reserve` 是路线最差预测储备的补能门槛，不是轨迹可达性开关。HUD `energyReserve` 在普通攻击证书/曲线回放返回后显示最差 reserve；`ATTACK_PHYSICS_VETO:<reason>` 表示完整物理认证拒绝，需结合 `contact_missed`、`exit_incomplete`、`sweep_<tick>` 与异步等待判断。详细每步 envelope 目前是 Worker 结果 DTO，未新增网络包或 HUD 曲线。

0.6.2.1 没有新增可调数值键。`EMERGENCY_DETECTED` 输出 TTC、margin、速度与航向差；`EMERGENCY_WAIT` 输出临撞等待原因与紧急队列；`EMERGENCY_CERTIFIED` 区分滑翔/烟花和认证时域。`FLIGHT_LIFECYCLE` / `FALL_FLYING_CLEAR` 记录释放/清旗来源、状态、速度、高度及目标/订单。`TRANSIT_WAITING_FIRST_TICK` 是安全的未初始化调试快照。详情见 [稳定性与崩溃分析](STABILITY_LAUNCH_0621.md)。

## 1. 配置的入口与生效方式

定义源为 [SkyfarerConfig](../src/main/java/dev/winefox/skyfarer/config/SkyfarerConfig.java)，统一读取与写入入口为 [SkyfarerSettings](../src/main/java/dev/winefox/skyfarer/config/SkyfarerSettings.java)。[WineFoxSkyfarer](../src/main/java/dev/winefox/skyfarer/WineFoxSkyfarer.java) 注册的是 NeoForge **SERVER** 配置；没有单独的客户端飞控配置。常规服务端配置文件名为 `winefox_skyfarer-server.toml`，由 NeoForge 管理其存档配置位置。

- TOML 使用 `flight`、`targeting`、`combat`、`safety`、`command`、`debug`、`equipment` 分组；命令使用不带组名的键，例如 `turn_rate`。
- 数值都声明为 `DoubleValue`。`ticks`、`attempts` 单位的键在 `/wfs config set` 中额外要求整数；其余值允许小数。运行时 `ticks()` 转为 int，因此不要把手工文件中的小数 tick 当作受支持的细粒度调度。
- 未载入配置时读取定义的默认值；写入要求配置已载入，并校验有限数、上下限及整数要求。
- 数值 `set` / `reset`、全量 reset、配置 reload 会提升 settings revision，让异步 session 淘汰旧参数任务。`debuglog` 单独开关只影响日志并保存，不递增规划配置版本。
- 命令修改会校验 `combat_range <= hard_combat_range < emergency_teleport_range`；扩大范围时先调整外层。配置读取还有钳制：Settings 的 combat 不大于 hard，hard 不大于 emergency−1。**实际 Home 范围另会扩大**，见后文，不能将这条设置约束理解成所有实际路径的绝对边界。
- `reset all` 恢复所有数值默认值、默认额外鞘翅列表并关闭 debuglog。旧存档已设置的数值和列表不会因为新版本默认值变化自动被改写。
- 工作线程不直接读取 Settings/NeoForge 配置；主线程构造数值任务，`PredictionHorizon` 在 worker 中使用任务冻结的倍率上下文。

### 1.1 完整数值配置表

表中 C = [AirCombatController](../src/main/java/dev/winefox/skyfarer/ai/AirCombatController.java)，T = [TargetEvaluator](../src/main/java/dev/winefox/skyfarer/ai/TargetEvaluator.java)。C 会把有关数值传给异步任务，Worker 中对应的 Planner 不重新访问配置。范围是注册表范围；“遗留”行不应作为当前调优入口。

| 组 / 键 | 默认值 | 允许范围 | 单位 | 当前作用与消费者 |
| --- | ---: | --- | --- | --- |
| flight.max_turn_angle | 120 | 10–180 | 度 | C 的普通攻击入口外层转向门槛；HUD 显示。不是所有曲线候选的统一硬上限。 |
| flight.turn_rate | 5 | 0.1–20 | 度/tick | C 普通航向、CompactTurn、曲线 sweepRate 基准；HeadingController。紧急/特定策略有额外倍率或最低值。 |
| flight.attack_turn_rate | 2.5 | 0.1–10 | 度/tick | C 普通攻击姿态/进场窗口；曲线速度取与 turn_rate 的较大值；HeadingController。 |
| flight.dive_max_correction_angle | 40 | 5–90 | 度 | C 固定攻击线的偏转退出条件；T 次要目标方向修正上限还取 min(25, 此值)。 |
| flight.pitch_rate | 3 | 0.1–15 | 度/tick | C / HeadingController 普通俯仰调整基准。 |
| flight.pull_up_pitch_rate | 4 | 0.1–20 | 度/tick | C / HeadingController 普通拉升基准；不等于所有 ATTACK_EXIT 策略的固定俯仰。 |
| flight.recovery_pitch_rate | 8 | 1–30 | 度/tick | C 恢复姿态、异步离场等待及 recovery 请求。 |
| flight.energy_reserve | 0.08 | 0–1 | specific energy 指标 | C 普通 coast/pulse 能量评估储备阈值；不是实际能量单位或每 tick 推力。 |
| flight.minimum_boost_speed | 0.8 | 0.1–3 | 格/tick | C 普通飞行未来低速预测阈值，不是强制速度设置。 |
| flight.attack_minimum_boost_speed | 0.4 | 0.1–2 | 格/tick | C 精细攻击补能基准，实际最低值还不低于 Spears minimumDamageSpeed + 0.05。 |
| flight.attack_energy_reserve | 0.02 | 0–1 | specific energy 指标 | C 攻击/坡面汇入补能储备阈值。 |
| flight.slope_pitch_rate | 6 | 1–20 | 度/tick | C `runFlare < 0` 的攻击/恢复俯仰速率。 |
| flight.slope_pull_up_pitch_rate | 10 | 1–30 | 度/tick | 同上情形的恢复俯仰速率。 |
| flight.firework_cooldown_ticks | 30 | 10–200 | ticks | FireworkManager 两次真实发射间隔；已有活动火箭时也拒绝再次发射。 |
| flight.takeoff_timeout_ticks | 60 | 20–200 | ticks | C TAKEOFF 超时；水面恢复另用 max(80, 2×此值)。不是所有 TransitFlight 阶段的通用 timeout。 |
| flight.prediction_length_multiplier | 1 | 1–10 | 倍率 | PredictionHorizon 延长真实预测步数，影响多个 Planner 和创造票据前视；不是仅把黄色线画长。 |
| targeting.search_range | 32 | 4–96 | 格 | T 本地/上一候选附近索敌查询半径；作战中心和指令目标还有独立查询。 |
| targeting.combat_range | 40 | 8–128 | 格 | T 常规新目标相对活动中心范围基准；实际不小于 protectedRadius。 |
| targeting.hard_combat_range | 56 | 12–192 | 格 | T 已追击目标范围基准；实际不小于 effective combat + 16。 |
| targeting.previous_target_bonus | 30 | 0–200 | 分数 | **遗留，无当前行为消费者**。上次目标现由战略层级保留，不再累加此值；DebugSnapshot previous_bonus 固定 0。 |
| targeting.target_switch_threshold | 15 | 0–100 | 分数 | T 同战略层且同幻翼级别的换目标迟滞。 |
| targeting.scan_interval_ticks | 10 | 1–100 | ticks | C 常规 acquire/飞行/返航恢复索敌 ScanCadence。 |
| combat.climb_height | 14 | 6–48 | 格 | **遗留，对当前行为无效**。arcPoint() 只在 preferred 非空分支读取它，但唯一调用处 LOITER_SEARCH 已先把非空 preferred 转入 APPROACH；实际走到 arcPoint 时不会执行该读取。 |
| combat.attack_entry_distance | 18 | 8–48 | 格 | C 攻击入口距离参考；会结合前摇、速度和其他最小距离计算。 |
| combat.breakaway_distance | 12 | 6–40 | 格 | C 离场原始参考。Settings.attackExitDistance() = 此值×2/3，默认 8；Exit 长度另结合速度/转向，不能视为固定离场时间。 |
| combat.loiter_time_ticks | 80 | 0–600 | ticks | C 无目标盘旋期限，之后返航。 |
| combat.waypoint_tolerance | 3 | 1–8 | 格 | C 爬升高度到达容差。 |
| combat.attack_timeout_ticks | 100 | 20–300 | ticks | C 单轮攻击时限；APPROACH setup 的重选时限使用 4×此值。 |
| combat.max_target_displacement | 10 | 2–32 | 格 | C 已承诺攻击目标相对锁定点允许位移，超出进入恢复。 |
| combat.approach_height | 4 | 2–12 | 格 | C/Lane 地形入口高度参考；不是始终固定高于目标 4 格。 |
| combat.level_run_distance | 14 | 6–24 | 格 | C/Lane 平飞/收平/低顶航线参考。 |
| combat.reposition_distance | 28 | 16–48 | 格 | C 重定位入口距离参考；可用前摇与路线约束还会改变实际入口。 |
| combat.attack_ground_clearance | 0.75 | 0.4–2 | 格 | C 相对目标脚高的攻击最低眼位参考，叠加女仆 eyeHeight；不是世界地形统一抬高一格。 |
| combat.attack_aim_offset | 0.5 | 0–3 | 目标中心向上格数 | T aimPoint 与 C 攻击瞄准；不修改实体真实碰撞箱。 |
| combat.spear_vertical_expansion | 3 | 0–8 | 上下各扩展格数 | C 预测接触 + MaidSpearCollisions 虚拟命中容差；0 关闭，真实方块射线仍检查。 |
| combat.max_failed_passes | 2 | 1–20 | 次 | C/SpearPassHistory 同一目标连续失败规避阈值；无其他选择时可重试，不永久拉黑。 |
| combat.post_attack_charge_ticks | 60 | 0–200 | ticks | C 命中/掠过后的 Charge 保留；0 关闭。独立于离场完成，不强制飞满 3 秒。 |
| combat.secondary_target_radius | 6 | 0–12 | 格 | T/C 多穿、CHAIN_ATTACK 的次要目标范围；0 禁用候选，不改变主目标战略排序。 |
| combat.slope_sample_radius | 16 | 3–32 | 格 | C 提交近/远坡面采样的半径上限。 |
| combat.slope_sample_step | 0.5 | 0.25–1 | 格 | C 提交坡面采样间隔；Sampler 实际截到 .25–.4 以连续跟踪陡坡，越小纯计算与快照请求成本越高。 |
| safety.emergency_teleport_range | 96 | 32–512 | 格 | C 远离活动中心恢复阈值基准；实际范围会与 T.hardRange + 16 取大值。 |
| safety.forward_collision_check_distance | 6 | 2–24 | 格 | **遗留，无消费者**。当前动态预测走廊不读取此值。 |
| safety.corridor_side_margin | 0.35 | 0.05–1 | 格 | C 普通 Glide 请求身体横向余量；不同紧急/创造/接触策略可有独立余量。 |
| safety.corridor_vertical_margin | 0.35 | 0.05–1 | 格 | C 普通 Glide 请求身体上下余量；不是所有检测统一膨胀值。 |
| safety.return_timeout_ticks | 300 | 60–1200 | ticks | C 返航超过时限尝试安全落点传送/Home 恢复。 |
| safety.max_airborne_ticks | 2400 | 200–12000 | ticks | C 空战最长持续期与返航重入条件。 |
| safety.launch_retry_ticks | 60 | 20–400 | ticks | C release 后重新 acquire 的等待期。 |
| command.command_mark_range | 256 | 16–512 | 格 | CommandManager 服务端准星标记射线最长距离；遇未加载块/实体前的方块缩短。 |
| command.command_home_range | 128 | 16–512 | 格 | CommandManager/DispatchPolicy 生存镜 Home 候选相对玩家三维距离，及未加载候选发现范围。 |
| command.creative_command_range | 1000 | 0–Double.MAX_VALUE | 格，0 无限 | CommandManager/DispatchPolicy 创造镜所有候选女仆相对玩家三维距离。无人为正数上限，仍要求 finite double。 |
| command.creative_transit_speed_multiplier | 2 | 1–100 | 倍率 | TransitFlight / CreativeTransitPlanner 仅创造赶路；CommandTickets 预加载前视也随速度扩展。 |
| command.creative_transit_height | 200 | −64–1024 | 世界 Y | TransitFlight 创造巡航请求高度，受目标高度、剩余水平航程和维度上界约束。 |
| command.command_mark_bonus | 150 | 0–1000 | 分数 | T 路线评分及 DispatchPolicy 分配评分；标记最高层级另由 boolean 实现，不靠此数值压过其他层。 |
| debug.sync_interval_ticks | 5 | 1–40 | ticks | DebugWatchManager 全体绑定快照同步周期；指挥镜标记同步仍固定 5 ticks。 |
| debug.log_interval_ticks | 100 | 20–1200 | ticks | C / CommandManager 周期日志间隔；状态变化及部分诊断另有触发，不是所有日志总速率上限。 |

另外 `debug.debuglog=false` 是 BooleanValue，开启条件通过 Settings.debugLogging 读取。没有线程数、队列长度、快照预算的公开 TOML 键；这些当前是异步系统代码常量。不能向玩家建议不存在的“planner_threads”或“snapshot_budget”参数。

### 1.2 实际范围与排序不能只看配置表

[TargetPriority](../src/main/java/dev/winefox/skyfarer/ai/TargetPriority.java) / T 的当前公式：

```text
protectedRadius = 16 + (Home ? max(0, maid.getRestrictRadius()) : 0)
effectiveCombat = max(Settings.combatRange(), protectedRadius)
effectiveHard   = max(Settings.hardCombatRange(), effectiveCombat + 16)

优先顺序：marked > (previous OR distanceToAnchor <= protectedRadius) > ordinary
同层：phantom > other；再比较 route score；同层换目标还应用 threshold
```

标记目标还可走 `maid.distanceTo(target) <= 64` 的单独有效性规则。初次自动扫描仍检查至少一个目标身体点可见；已承诺攻击是否可继续由控制器及碰撞预测处理，不能因为短时遮挡就把“可见性”当作全路线通行约束。失败目标按 pass history 规避；没有替代目标则重新允许该目标。

## 2. `/wfs` 命令与权限

权威实现：[SkyfarerCommand](../src/main/java/dev/winefox/skyfarer/command/SkyfarerCommand.java)。根命令只输出帮助；`debuglog` 与整个 `config` 子树要求权限等级 **2**，没有 `/wfs debug bind`、`/wfs reload`、`/wfs planner` 或传送子命令。

```text
/wfs
/wfs debuglog                  # 等同 status
/wfs debuglog on|off|status
/wfs config list               # 数值配置 + debuglog 状态
/wfs config get <parameter>
/wfs config get debuglog
/wfs config set <parameter> <double>
/wfs config set debuglog <true|false>
/wfs config reset <parameter>
/wfs config reset debuglog
/wfs config reset all
```

`parameter` 补全来自配置注册表。非法参数/范围由服务端返回 Brigadier 错误；不是直接接受客户端字符串执行配置代码。参数名称在所有组中必须保持唯一。

## 3. 四种物品与工作模式

注册源：[WineFoxSkyfarer](../src/main/java/dev/winefox/skyfarer/WineFoxSkyfarer.java)。均 `stacksTo(1)`，位于 `TOOLS_AND_UTILITIES`，当前资源目录没有配方。

| ID（前缀 winefox_skyfarer:） | 中文名 | 入口 / 作用 |
| --- | --- | --- |
| maid_remote | 女仆遥控器 | 右键自己的女仆写入 ItemStack CustomData 绑定；右键空气打开工作模式面板。 |
| debug_stick | 骑空士调试棒 | 右键女仆切换观察；右键空气打开远程调试列表。 |
| command_spyglass | 骑空士指挥镜 | 左键切换目标标记，右键原版 Spyglass 缩放。 |
| creative_command_spyglass | 创造骑空士指挥镜 | 同上，创造权限和远程赶路/临时票据策略不同。 |

[SkyfarerTask](../src/main/java/dev/winefox/skyfarer/compat/tlm/SkyfarerTask.java) ID 为 `winefox_skyfarer:skyfarer`，实现 TLM `IAttackTask`，注册优先级 4 的 Brain behavior；创建列表保持可修改，因为 TLM 会追加日程行为。启用条件由 [SkyfarerEquipment](../src/main/java/dev/winefox/skyfarer/compat/tlm/SkyfarerEquipment.java) 决定：主手支持冲锋且有使用时长的长矛、胸甲栏**原版 `Items.ELYTRA` 且可滑翔**、可用库存至少一枚 `Items.FIREWORK_ROCKET`。当前不是所有声称支持 `canElytraFly` 的自定义胸甲都能启用工作。

### 遥控器与调试面板不是同一种远程能力

[MaidRemoteItem](../src/main/java/dev/winefox/skyfarer/item/MaidRemoteItem.java) / [RemoteSessions](../src/main/java/dev/winefox/skyfarer/runtime/RemoteSessions.java)：

- 遥控器绑定存储在 `DataComponents.CUSTOM_DATA` 的 `winefox_maid` 内，包含 uuid/name/dimension；只允许本人所属女仆，**没有 OP 越权绑定分支**。
- 打开/提交只查玩家当前维度已加载且存活的实体，不替遥控器强加载区块。
- 服务端 nonce session 有效 1200 ticks，成功修改续期；两次提交间隔至少 4 ticks。提交时复验持有同一绑定物品、维度、主人、实体和当前任务隐藏/启用条件。
- 面板来自 TLM `getNotHiddenTaskList`，最多 512 个选项；任务名不是任意可执行代码。切换调用真实 `maid.setTask`。

## 4. 指挥镜的服务端权威与生命周期

源：[CommandManager](../src/main/java/dev/winefox/skyfarer/command/CommandManager.java)、[DispatchPolicy](../src/main/java/dev/winefox/skyfarer/command/DispatchPolicy.java)、[MarkQueue](../src/main/java/dev/winefox/skyfarer/command/MarkQueue.java)、[CommandClient](../src/main/java/dev/winefox/skyfarer/client/CommandClient.java)。这些管理映射与实体逻辑均在服务端主线程；不能从 Planner worker 调用。

### 4.1 标记

客户端捕获 attack 键/左键并取消普通挥击；发送的 `ToggleCommandMarkPayload` **不含目标 UUID 或位置**。服务端自行确认玩家存活、非旁观、实际手持指挥镜；创造镜还要求 `player.isCreative()`。四 tick 限流后，沿服务端眼位与视线 pick：

1. 限制在已加载区块，按 2 格前探缩短最大距离，不为射线生成新区块。
2. 方块 `COLLIDER` 截断视线，忽略流体。
3. 只选活着、非旁观、非无敌、非 Player、非 EntityMaid 的 LivingEntity，身体 box 膨胀 0.1，取最近射线相交者。两种镜共用服务端 pick，所有女仆均在选取前排除，与归属、工作、Home/Follow 或敌友无关；不影响调试棒、女仆遥控器和远程调试。

每玩家一个同维度 `Session`，列表为最多五个 UUID 的 LinkedHashMap，值记录**添加时**是否创造标记。第六个移除最早加入者；再次点击已存在目标取消，不能用另一镜直接“升级颜色”，需取消再添加。编号仅是列表顺序，绝不作为调度优先级。登出、死亡、换维度清除会话；指令不持久化到存档。

标记客户端同步固定每 5 ticks；收到后超过 3 秒不再渲染，避免网络失联留下旧标记。服务端目标卸载**不等于死亡**：保存最后位置/尺寸/seen，不简单按总时间丢弃；若最后已知块已经 entity-ticking 且实体持续缺席超过 100 ticks，才清理这条陈旧记录。真正死亡事件会去掉标记。

友好生物可标记，`TargetEvaluator.attackable` 中 designated 可绕过通常 allied/canAttack 限制；仍排除女仆自身、主人和所有 Player，并要求主人存在且同维度、目标活着/非无敌。不要通过直接 `hurt()` 另打一遍标记目标。

### 4.2 发现与分配

[SkyfarerMaidRegistry](../src/main/java/dev/winefox/skyfarer/command/SkyfarerMaidRegistry.java) 是 overworld DataStorage 中的 SavedData `winefox_skyfarer_maids`。按 owner 分组记录 `id, owner, dimension, BlockPos, known, home, skyfarer, name`。EntityJoin、女仆每 20 ticks、离开世界时更新；死亡或 DISCARDED 删除。它不是女仆实体的序列化替代品。

- 从 TLM `MaidWorldData.getInfos(owner)` seed 时，未知记录使用 `known=false`；TLM 未提供的 Home/任务事实不能据此猜测为真实。加载后再次核实当前实体资格。
- 每 10 ticks 检查待派遣记录；每玩家每轮最多发起 2 次唤醒，全球最多 8 个 wake，各 100 ticks 到期。[RemoteWakeScheduler](../src/main/java/dev/winefox/skyfarer/command/RemoteWakeScheduler.java) 先处理从未尝试者，再按上次尝试顺序轮转；达到上限时不把未入队者标为 attempted。失败记录从尝试起冷却 200 ticks 后可重试，同一任务不会永久跳过它。
- 标记增删、自然失效、死亡都刷新 revision/generation 并撤销旧 wake；任务空、创造目标卸载、登出、换维度和关服释放未接单票据。成功接单将发现票据移交订单的滚动窗口，生存订单直接释放；一只接单不终止其他候选的调度。回 Home 后下一任务重新建立尝试历史。
- 持久化的 `skyfarer=false` 不再永久否决发现。旧/未知记录可以在范围内分批醒来核实，只有当前实体的主人、工作及装备符合条件才分配；命令和 attempted 均不存入 registry。TLM seed 忽略无位置记录。
- 同维度、本人所属、当前骑空士、存活、有 AI、非坐下/睡眠/乘客/拴绳/打开 GUI，并有合格鞘翅、矛及可用推进才可初次接单。推进为真实水/雨环境下可用激流三叉戟，或库存烟花；两种镜共用实时资格，不从 registry 推测雨况。地面再次核对，空中不会因为失雨直接清除滑翔。
- 生存镜：跟随女仆无额外 Home 半径门槛；Home 默认玩家三维距离 ≤128。创造镜：**跟随/Home 都受 1000 格默认三维范围限制**；0 为无限。已接任务不会仅因玩家后来移远被范围条件撤销。
- 分配对每只女仆独立评分：`markBonus − distance×0.04 − headingAngle×0.25 + previous?20:0 + route`；64 格内且目标实际加载时 route 取 TargetEvaluator.score×0.1。不强制五个标记一一分配，不保证均匀分摊。
- 普通战斗扫描另使用最高标记战略层；该层内部仍按幻翼/路线/失败策略选择。标记列表变化会在非赶路、非承诺攻击的安全点重评；不能打断正在完成的刺击动作。

### 4.3 赶路、票据与回家

[TransitFlight](../src/main/java/dev/winefox/skyfarer/command/TransitFlight.java) 使用自己的异步规划 session，不复用战斗控制器状态。生存指令三维距离 ≤48 格交接，创造指令 ≤32 格交接。在赶路完成前固定分配目标，不因沿途普通怪物或新标记转头。

创造模式通过 [CreativeTransitPlanner](../src/main/java/dev/winefox/skyfarer/command/CreativeTransitPlanner.java) 提供独立高速移动：基准 `BASE_SPEED=1.7`，倍率 1–100，目标高度受 cruiseHeight 公式及维度顶棚限制；水平距离 64 格开始下降，接近交接范围缩小速度。**此阶段确实会设置移动速度**；“所有飞行只改朝向、不改速度”仅适用于常规战斗，不适用于创造赶路。赶路不消耗背包烟花；出发时必须有可用激流或烟花，不能把无烟花等同无推进。交接移除免费推进并限制速度。

创造每 tick 真正移动前在主线程核验当前身体整段扫掠；最多 12 次逐次半速尝试，失败回局部飞控。此真实即时检查不是异步几何结论的可省略副本，尤其 100 倍速可能跨过多个块。

[CommandTickets](../src/main/java/dev/winefox/skyfarer/command/CommandTickets.java) 正式注册 `TicketController` **`winefox_skyfarer:command_flight`**，以女仆 UUID 持有 `forceChunk(..., add, true)` 票据，最后的 true 表示 entity ticking：

- [TicketWindow](../src/main/java/dev/winefox/skyfarer/command/TicketWindow.java) 沿当前/未来方向每 16 格铺 3×3 区块窗口；lookahead clamp 为 48–480 格。不是加载整段旅程，也不是只加载单一中心区块。
- 创造前视结合 `48×predictionMultiplier` 与 `clamp(1.7×speedMultiplier×4+32,48,480)`。旧窗口不再需要的块立即撤票。
- 生存仅使用候选唤醒票据；真正接单后释放，不维持无限远飞行加载。
- 初始创造起飞在地面最多等 100 ticks 让票据覆盖区域成为 entity-ticking；已在空中时不通过冻结实体等待。
- 注册时的恢复回调删除旧实体/方块票据；命令会话不跨重启恢复。取消、离开、退出与关服负责释放窗口。

Home 保留由 [CommandHomeRecallMixin](../src/main/java/dev/winefox/skyfarer/mixin/CommandHomeRecallMixin.java) 在 TLM `SchedulePos.tick` 入口实现。托管空中滑翔时暂停日程，以免任务结束后的 Home 回收切断高空控制；地面时仅当 Home 女仆持有创造订单，且同一玩家会话中**至少有一个同时满足“创造标记仍在、目标存活、目标实体及其区块已加载”**的目标时，才暂停此日程 tick。不是三个条件的逻辑 OR，也不是永久禁用 TLM 安全回收。

有效创造目标全失效时 cancel 先移除订单、关闭 TransitFlight，为离开工作范围的 Home 女仆建立 RECALL。0.6.2.9 改为任务结束位置附近安全落地，地面立即 dispose、managed=false、清除 WineFox 路径记忆，放行 TLM SchedulePos；不再朝 Home 飞行，也不再每 10 ticks 主动调用 TeleportHelper。保留当前位置块 + Home 3×3、最多 200 ticks 的原票据宽限，回到限制范围或超时即释放。TLM 自身每 40 tick 的 Home 日程也适用于一直加载的女仆；是否传送由其移动资格、现有导航目标和安全落点决定。日志 COMMAND_HOME_CONTROL_RELEASE、TLM_HOME_SCHEDULE held=false、COMMAND_HOME_RETURN 与 COMMAND_TICKET 分别记录释放/日程机会/完成或超时/撤票。宽限到期仍未回家不保证传送成功；若还在空中则继续本地安全落地，不永久持票。普通 teleportToOwner 与有效创造任务的 Transit 重建保持不变。

## 5. Debug 模式、HUD 与远程调试面板

**debuglog 与调试棒绑定相互独立。** 绑定 HUD 不要求打开日志，打开日志不会自动绑定女仆。

### 5.1 观察生命周期与图例

源：[DebugWatchManager](../src/main/java/dev/winefox/skyfarer/runtime/DebugWatchManager.java)、[DebugSnapshot](../src/main/java/dev/winefox/skyfarer/runtime/DebugSnapshot.java)、[ClientDebugState](../src/main/java/dev/winefox/skyfarer/client/debug/ClientDebugState.java)、[AirCombatDebugRenderer](../src/main/java/dev/winefox/skyfarer/client/debug/AirCombatDebugRenderer.java)。

- 服务器每玩家保存 `LinkedHashMap<maid UUID, last CompoundTag>`；可绑定多只女仆，当前**没有公开绑定数量上限**。不是持久化 SavedData，退出/关服清空。
- 近距绑定只要求本人所属或权限等级 2；不强制必须骑空士。未有控制器时显示 GROUNDED 快照。
- 客户端只显示当前选中一只的左上 HUD，默认 ←/→ 可改键；有 GUI 打开或窗口失焦时不切换。全部绑定女仆仍保留各自世界线条。
- 女仆卸载后保留最后收到的快照，名称蓝色，后缀“（已卸载）”金黄；恢复加载后继续实时同步。快照可能包括旧目标/旧预测线，**是最后观测，不是后台继续推演**。跨维度 HUD 可查看，世界线条只绘制当前维度；观察本身不加载女仆区块。
- 绑定权限在同步时重新确认，归属改变会移除。空 payload 清全部，带 remove UUID 移除一只。

| 显示 | 当前来源与含义 |
| --- | --- |
| 红线 | 女仆到本轮实际 run target；尚未锁定不会凭候选画红线。 |
| 蓝线 | `AirRoute` 引导点折线，非精确物理轨迹。 |
| 青绿线 | `attack_curve` 拟合攻击 Bezier 折线，包含进入/接触/离场几何。 |
| 黄色通道 | 已有预测 trace 对应身体 + 安全余量；4 条纵边 + 每 6 个截面及末端的框；不画整张三角网格。 |
| 绿色 1 格立方框 | `attack_entry`，中心引出 2 格 `entry_direction` 线。 |
| 亮绿粗四角框 | 当前 HUD 女仆；独立 SELECTED RenderType 无深度测试，只写颜色；线宽是普通基准的 3 倍。 |

黄色框半径会按横截面方向投影 AABB，不能把画面宽度当作女仆身体宽度本身；截面上/下高使用实际 body/eye 与预测余量。它读取已完成的后台预测，debug 渲染不再次跑 Planner。F1 隐藏 GUI 时也隐藏这些世界辅助显示；打开调试面板时隐藏 HUD。

HUD 当前字段包含名称/UUID 前缀/维度/选中序号、状态、原因/曲线类型、扫描统计、异步规划、锁定目标、候选及分数、速度/离地、Charge 时长/前摇/射程、俯仰/目标距离/相对中心高度、转角/搜索/作战范围。

异步行来自 `AsyncPlanning.detail()` / `PlannerSession.metrics()`：waiting/ready、generation、缓存/请求/任务状态。当前字符串里有两个 `cache=`：前一个是该 session 缓存结果条数，后一个（`queue=` 之后）是全服累计 cache hits。`queue` 是共享待排任务，`stale` 包含被 invalidate 取消的任务，`failed` 是 worker 异常累计，`captureMs` 与 `workerMs` 是**累计**时间。不要解读成上一 tick 的 MSPT 或每只女仆独占耗时。

### 5.2 调试管理面板的权限与传送

源：[DebugPanelManager](../src/main/java/dev/winefox/skyfarer/runtime/DebugPanelManager.java)、[DebugMaidScreen](../src/main/java/dev/winefox/skyfarer/client/gui/DebugMaidScreen.java)。右键空气打开自己的 owner 列表：名称、UUID、最近位置、维度、同维度三维距离 `(Nm)`；跨维度显示 `(None)`。有绑定勾选和“玩家 → 女仆 / 女仆 → 玩家”按钮。可以分页、刷新、按玩家名/UUID 搜索及下拉切换 owner。

- 普通玩家只管理自己的女仆；权限等级 2 才可搜索/管理其他 owner。**本人女仆的调试传送当前不要求创造或 OP**，这是现有权限事实，不应把它误写为仅管理员工具。
- 数据列表来自本模组 registry + TLM seed + 当前已加载女仆 + 本次绑定缓存；当前没有未知历史实体的全世界扫描。列出面板会枚举各维度已加载实体以刷新该 owner 记录，不加载其余地形。
- 动作要求持有调试棒、正确 session nonce、未过期、owner 合法；session 有效 12000 ticks，交互续期。每玩家每 tick 最多处理 20 条动作；查询字符串最多 64 字符、owner 结果最多 512、页大小 1–20。
- 显式传送才注册临时 **`winefox_skyfarer:debug_remote`** entity-ticking 票据：女仆最后记录位置 3×3，100 ticks 超时；每玩家最多一个待处理传送。
- 传送前重新验证女仆实际归属/存活，找身体可容纳、无液体、已加载且位于世界边界内的位置；在中心半径 2–5、上下 0/±1…±4 查找，优先脚下有支撑，允许最后空气候选。跨维度尊重 `CommonHooks.onTravelToDimension`。
- 采用原版命令式 `teleportTo(ServerLevel,...)`，不走 TLM 门户用的 changeDimension；移动前退出载具并卸下乘客。带女仆过来会先取消订单/唤醒/回收及飞控；成功后清速度和 fallDistance、刷新 registry。
- 完成、失效、权限变化、登出、关服释放临时票据。记录存在不保证能加载实体；超时会明确显示记录可能失效。

### 5.3 指挥标记视觉

普通标记颜色 `0xFF8070`，创造 `0xE58CFF`；四角框 + 头顶 `▼` + 1–5 编号。客户端从相机向目标中心做方块射线，遮挡时仅画透视 `▼`，隐藏框及编号。新标记使用 0.35 秒三次缓动，由 1.8 倍框缩到 1 倍；重复同步不重新触发动画。服务端仅向标记者发送该列表；成功音效 `EXPERIENCE_ORB_PICKUP`（0.8 音量/1.4 音高），取消 `UI_BUTTON_CLICK`（0.8/0.8）。

### 5.4 日志与排查

`/wfs debuglog on` 会产生 `[WFS debug]` 状态、扫描/阻断、能量/火箭决定、任务分配、赶路、回收等日志。周期由 log_interval_ticks 控制，转移与部分 sweep 诊断有独立事件/节流；大量女仆开启后会增加日志量。

异步任务失败另有 `[WFS] Async planner failed; result discarded: ...` 错误，不依赖 debuglog。错误从主线程 Source.failed 输出，同一异常字符串按 200 epoch 限频；发生 worker 异常并不允许回退成主线程重算。定位补能先看 `energyDecision`、`pulseReview`、`cooldown`；定位远程生命周期看 `COMMAND_ASSIGN`、`COMMAND_TRANSIT`、`COMMAND_RELEASE`、`COMMAND_HOME_RETURN`，同时检查实际 chunk entity-ticking 状态。

远程唤醒新增 `REMOTE_WAKE owner=... maid=... generation=... stage=... reason=... attempted=... active=...`：`candidate_discovered / candidate_rejected` 仅在原因变化时记录；随后有 `wake_queued`、`ticket_created / ticket_failed`、`entity_loaded`、`qualification_failed / assignment_accepted`、`timeout`、`released`。失败原因区分 owner、dimension、work_mode、sitting、sleeping、passenger、leashed、gui_open、wings_资格、spear、no_firework_riptide_environment_invalid、no_firework_or_usable_riptide 等；`attempted_until` 为本次重试冷却时刻。`released reason=transferred_to_order` 表示 wake 槽已释放、票据归订单管理；实际撤票看 `COMMAND_TICKET stage=released`。`COMMAND_TICKET stage=replace` 记录 wanted/held/success，创建失败的块不会被伪记为已持有；空或未完整创建的票据窗口不判定 ready。

## 6. 网络与兼容边界

[SkyfarerNetwork](../src/main/java/dev/winefox/skyfarer/network/SkyfarerNetwork.java) 使用协议版本字符串 **`3`**，所有 handler 通过 `context.enqueueWork` 转交对应游戏主线程。客户端消费者由 `SkyfarerClient` 安装；common 网络类不直接引用 Screen/Minecraft，保持 dedicated server 可加载。

| 方向 / Payload | 权威边界 |
| --- | --- |
| C→S ToggleCommandMarkPayload | 无目标字段，仅表达点击意图；服务器 raycast、手持与创造权限检查。 |
| C→S SetRemoteMaidTaskPayload | nonce + task ID；RemoteSessions 复验实际绑定/归属/任务条件。 |
| C→S DebugPanelActionPayload | NBT 请求；DebugPanelManager 校验 nonce、持物、owner、分页/速率、实际实体。 |
| S→C AirCombatDebugPayload | 观察快照/移除/清空；客户端只渲染。 |
| S→C CommandMarksPayload | 标记者独占列表，不授予客户端伤害或调度权。 |
| S→C OpenRemoteTaskPayload / DebugPanelPayload | 服务端当前允许内容；客户端 GUI 不是权限来源。 |

### 6.1 TLM 与 Spears Mixin

入口清单：[通用/客户端 mixin 配置](../src/main/resources/winefox_skyfarer.mixins.json)。通用配置 `required=true`、`defaultRequire=1`，升级依赖后不能默认方法签名仍匹配。

| 类 / 组 | 当前职责与必须保留的限制 |
| --- | --- |
| TlmFollowOwnerMixin | 空控期间阻止普通/载具跟随 Brain 的启动，不改变地面普通跟随。 |
| MobAirControlMixin | 只对正在空控的 EntityMaid 跳过 PathNavigation / MoveControl / LookControl tick，避免重写飞控姿态。 |
| TlmAirControlMixin | FALL_FLYING 女仆尺寸高 0.6、眼高 0.4；空控禁用 ground Brain 请求并清理移动记忆；TLM 安全 teleportToOwner 成功后释放控制，**不取消其安全传送**。 |
| MaidFlightPoseMixin | 空中受控滑翔时保留 FALL_FLYING，水中只在 water escape 活跃时保留。 |
| CommandHomeRecallMixin | 有效创造订单或托管空中滑翔暂缓 Home 日程，见 §4.3。 |
| FlightFlagAuditMixin | debuglog 开启时观察 setSharedFlag(7,false) 的调用栈与现场，不取消原版清旗。 |
| SpearFriendlyFireMixin | 对骑空士攻击者使用 TargetEvaluator.attackable 过滤，普通友军保护/指挥友好目标规则统一。 |
| SpearMovementMixin | Spears 使用上一完成 tick 的 measuredMovement×20；不伪造攻击速度/伤害。 |
| MaidSpearChargeMixin | 保留同一 use session 的离场 Charge；合并竖向容差命中；调用原生 pierce 后核验实际血量/吸收减少或死亡，才记录命中。 |
| MaidSpearCollisions | 只在女仆有效 spearAttack 中扩展虚拟查询，按 UUID 与 nativeHits 去重；最终射线截断和虚拟接触到真实表面射线都须无墙。不会修改目标尺寸或其他玩家长矛规则。 |
| TlmSpearItemPoseMixin | TLM HeldItem layer 使用 Spears 原生 holdUpAnimation 变换，修复举矛视觉。 |
| MaidFlightRenderMixin | 通用女仆 renderer 入口加一次身体局部飞行旋转，涵盖 Bedrock/Gecko/YSM 委托；不是给模型凭空新增骨骼动画。 |
| CommandSpyglassModelMixin | 把两种指挥镜接入原版望远镜手持模型处理。 |
| EntityFlightAccessor / FireworkLifetimeAccessor / MaidWorldDataAccessor | 分别暴露滑翔标记、真实火箭 life/lifetime、TLM 女仆 owner 索引；不要把 accessor 对象跨线程传给 Planner。 |

伤害扩展不 fork 或编辑 Spears mod；所有伤害仍由其 KineticWeapon / SpearUser 执行，去重集合防止同次攻击原生与扩展各伤一次。带爆炸星烟花只是选择评分增加 1000，不是禁止使用；其原版爆炸效果仍存在。

### 6.2 可选 YSM 适配

入口：[YsmMixinPlugin](../src/main/java/dev/winefox/skyfarer/compat/ysm/YsmMixinPlugin.java)、[YsmFlightCompat](../src/main/java/dev/winefox/skyfarer/client/YsmFlightCompat.java)、[可选 mixin 配置](../src/main/resources/winefox_skyfarer.ysm.mixins.json)。只在 **`yes_steve_model` 精确版本 `2.6.5-neoforge+mc1.21.1`** 且检查到预期混淆签名时应用；其他版本或缺失不猜测兼容。配置 required=false，适配 mixin 全部 client-only。

- 模型必须实际拥有非空 `elytra_fly`；对骑空士、非乘客、真实 ENTITY render state、滑翔状态选择该动画，并让它优先于自定义主控制器。
- 原生 YSM 动画已经旋转 Root 时，renderer 撤销一次通用 fallback 身体旋转，避免叠加 180°。无支持动画时保留通用倾斜回退。
- 使用模型 ElytraLocator 挂点链补绘原版鞘翅层；需要胸甲 stack 可飞、实体可见、挂点存在。不保证任意模型都有正确挂点或适合原版纹理。
- 补充 `elytra_rot_x/y/z` 查询：玩家保留已有平滑值，女仆从 ElytraModel 的实际翼角计算；返回角度值。
- 混淆 API 与模型资产效果需固定依赖字节码检查和实机视觉检查共同确认。不能把“通过离线字节码校验”描述成所有 YSM 模型都视觉正常。

## 7. 修改本域时的检查清单

1. 增删配置：同时改定义、消费者、命令帮助/语言文本、本文表格；确认是否提升 revision、是否真正进入 worker DTO；删除遗留项须评估已有配置兼容。
2. 改指挥：联查 CommandManager、DispatchPolicy、TargetEvaluator、ticket 生命周期；验证友好目标、主人排除、标记变化/死亡/卸载/登出/跨维度及回家失败分支。
3. 改 Debug：区分观测缓存与真实世界状态；不借 UI 请求授予额外 owner 权限，不因查看面板就无限加载世界；测多绑定/跨维度/卸载后的最后快照。
4. 改预测图：保持蓝色引导、青绿几何、黄色物理扫掠三者语义；不能在渲染或 debugSnapshot 中重新执行重规划。
5. 改 Mixin/依赖：核对实际字节码、方法描述符、环境限定和 fallback；不要只依据旧 API 名称改写。
6. 离线验证入口按 [PROJECT_INDEX.md](PROJECT_INDEX.md) 的验证导航查阅；与本域直接相关的 [build.gradle](../build.gradle) 任务是 `verifyCommandDispatch`、`verifyDispatchReplay`、`verifyDebugTools`、`verifyYsmCompatibility`，飞控衔接还需 `verifyControllerReplay`。真实菜单渲染、实体追踪范围、动态 chunk ticket 时序、高倍率网络体验只能由用户实机测试确认。

本文记录当前边界：观察列表未设置硬数量上限；列表刷新仍会枚举已加载实体；注册表可能含过时坐标；创造回收有明确失败到期；异步 HUD 是累计统计而非真实性能基准。上述都是维护者应知道的限制，不表示已获得该场景的实测故障结论。

### 0.6.2.10 攻击搜索诊断

ATTACK_SEARCH 每女仆通常 20 ticks 限频（commit 另记），显示目标实际位移、提前量、距离、重试数、候选统计和 Rejoin 原因。ATTACK_CONTINUATION_REJECT 区分执行阶段丢失的接触/离场。noContact 表示未认证接触，包含接触前碰撞，不能一律当成射程不足。字段含义见 [移动目标专项](MOVING_ATTACK_06210.md#诊断)。
