# 0.6.2.1 稳定性与 Launch Attack

基于本地 `deaebca`（0.6.1.1），继续使用 0.6.1 的 Vanilla prediction、AttackCertificate/Rejoin、坡线和曲线、EnergyReserveEnvelope、Strike→Exit。没有恢复 Funnel，没有远程覆盖或回滚。源码是最终权威。

## 紧急避障

`EmergencyAvoidancePlanner` 从真实三维速度出发，预警时域为 `clamp(ceil(4 + speed*3 + 航向偏差/max(3,turnRate)), 6, 32)` ticks。航向偏差取水平速度方向与期望 look 的夹角；预测逐步经过原版转向/滑翔方程及身体扫掠。已承诺的 Sweep/Launch 曲线使用同一动态姿态和 Strike→Exit 控制律，避免用恒定俯仰误拒合法路线。

危险至少保持 12 ticks，独立紧急 mailbox 的 phase 固定为 EMERGENCY，ATTACK/EXIT/REPOSITION 变化不会取消它。共用原有线程池、全服采集预算与队列上限；每只女仆另有一个紧急请求 slot。先采集实时防线发现危险的动量通道，避免冷快照把反应时间浪费在重新发现墙体上。

候选只有六个：相对速度方向 ±90°/-20°、±135°/-35°、180°/-20°、原方向/-75°；yaw/pitch 速率上限 45/20° 每 tick。先验证无新增烟花（包含已有火箭真实剩余推进）的完整逃逸；无解才检查最多三种可提取库存 duration。新烟花必须覆盖全部 12 种寿命与 ignition delay=1；最大推进超过 64 ticks 拒绝，整段至少 36 ticks，或最大寿命+delay+20。前 8 ticks 按逃逸姿态，其后用原高度+6 的反馈俯仰恢复。

Worker 的结果只是提案。应用前在主线程**只用已经采集的不可变 cells**从当前状态重验；拟点火时先重验当前六个滑翔出口，仍无安全解才认证所选烟花姿态全部寿命。该有限重验不采集新世界方块、不等待 Worker、不在队列满时同步执行整套搜索；缺 cells 只排队补充并拒绝。真实点火仍受活动火箭、CD、提取物品和实体生成结果约束。执行期间逐步 live 检查，待证书时用抬头滑翔制动，不凭待定方案点火。

`EMERGENCY_BOOST_TURN` 是诊断/计划类型，不是普通转弯状态。通过后临时独占逃逸控制，结束后回到原有恢复/返航。陡角曲线的离场俯仰上限由 9 提高至 14°/tick；预测和实际控制共用该值，完整离场与补能认证仍生效。

## 高空生命周期

- `release(reason)`：合法存活、未移除、无骑乘/入水且有可用鞘翅的空中女仆进入安全返航，保留或恢复滑翔；地面/已失去飞行资格才清理滑翔。
- `handoffToTransit()` 与 `TransitFlight.handoff()`：清理旧规划和旧推进，保留空中滑翔；赶路交战前仍按原规则限制速度至 1.7。
- `dispose(reason, preserveGlide)`：明确的卸载/替换/关服资源清理。UUID 相同但实体实例变化时释放旧控制器；旧实体 leave 事件不能释放新实例控制器。
- 新控制器遇到已有合法空中滑翔会接管安全返航。托管期间发现 flag 丢失，记录并尝试合法恢复；不绕过原版装备、漂浮、骑乘等资格。
- Home 任务结束不再直接在空中释放/传送。托管滑翔期间暂缓 TLM Home 日程；RECALL 在地面重试安全传送，最多保留原 200 ticks 票据。到期释放票据，空中飞控继续正常恢复，不永久加载。
- TLM 的成功安全传送重置旧路线；若仍在合法空中位置，保留恢复控制。实际进入地面后交还地面 AI。

`FLIGHT_LIFECYCLE` 记录操作、原因、state/managed、glide、velocity、绝对高度、已知 clearance、target/order。`FlightFlagAuditMixin` 在原版 `Entity.setSharedFlag(7,false)` 前观察真实清除调用栈，输出 `FALL_FLYING_CLEAR`，覆盖本 Mod、TLM 和原版调用者；只记录，不取消原版失效判定。此功能受现有 debuglog 控制。

## Launch Attack

`TAKEOFF_PREP` 在真实站立身体跳起扫掠之后，先查询 `TargetEvaluator.launchTargets`。候选必须满足现有 valid/visible、marked、protected/previous、phantom 优先层；最多四只，同层内由实际起飞路线评分选择，不能用易命中覆盖战略优先级。已有锁定目标只是候选之一。实体筛选在主线程，Worker 只收到 UUID/Aim 数值。

每个目标枚举 30/50/70° 上升、0/±30° 起飞偏航、0/±60° 出口，最多 27 条曲线。地面先自由转向，不套空中动量限制；起跳后使用预期 jump 状态、滑翔体积和真实重力，整条曲线交给现有 SweepingAttackPlanner。无动力和最多三种库存 duration 竞争；新烟花每一种随机寿命都必须满足真实前摇、Spears 接触/速度/射线、身体扫掠、完整 Exit 与燃尽后恢复。没有手动造成伤害。

准备阶段最多 40 次候选查询，选中后在地面预热专用滑翔体积的 Rejoin slot；额外等待有界。真正离地后再次校验目标战略资格和当前 Rejoin，成功才点火、原生蓄力并进入 ATTACK_RUN。空中等待最多三 ticks；目标改变、证书失效或无解则退回 SurfaceLaunch 的普通 TAKEOFF→CLIMB→APPROACH。特殊跳跃效果/外力导致实际离地状态变化时同样需要当前证书，不能直接使用地面提案。

## 最新崩溃证据

用户指定实例：`E:\rlc\PCL\.minecraft\versions\1.21.1-NeoForge_21.1.250`。读取最新 `crash-reports/crash-2026-10-05_22.59.23-server.txt`、`logs/latest.log` 与 `logs/debug.log`，没有启动游戏。

异常属于本 Mod，服务端线程调用栈为：

```text
NullPointerException: this.planning is null
TransitFlight.debug(TransitFlight.java:141)
CommandManager.debug(CommandManager.java:71)
DebugWatchManager.update / tick
ServerTick.Post
```

日志在 22:59:23 显示创造任务目标结束、Home 回收、随后分配新的普通赶路订单；同一个 Post 阶段的 debug watch 在第一次实体 tick 初始化 planning 之前读取了新 TransitFlight。修复为未初始化时仅写 `TRANSIT_WAITING_FIRST_TICK`；close 清除 planning/cached/fastMotion，debug 不创建规划器。回归直接调用生产 TransitFlight，在 tick 前与 close 后读取 debug。

该栈没有 Worker 异常，附近异步 failed=0；不能据此宣称其他所有崩溃或实机坠落均已定位。核对 sealed PlanningTask、Ticket 与快照，Worker 仍只持有数值/不可变 DTO，不持有 Session、Source、Entity 或 Level；卸载取消并释放主线程 mailbox。新 Emergency/LaunchAttack 也遵守此边界。

## 验证边界

定向任务：`verifyStabilityLaunch`、`verifyHandoffStability`、`verifyEmergencyReplay`、`verifyLaunchAttackReplay`、`verifyVerticalAttacks`，另做两个 `verifyCombatEntries` 进场回放及编译/打包。具体成功日志与交付哈希见 [buildHistory](../buildHistory.md)。中间失败不作成功证据。

回放运行生产控制器、规划器和异步调度，外部实体/世界/库存为离线夹具；碰撞用真实扫掠数学，接触用原生 Spears 的参数/时序条件模拟，不是启动真实游戏验证伤害。新 flag 审计入口另核对实际 Minecraft 字节码签名。动态地形、外部 Mod 力/火箭、极端服务器延迟和真实 TLM/NeoForge 加载时序仍需实机确认。未跑全量 verify，未启动 Minecraft。
