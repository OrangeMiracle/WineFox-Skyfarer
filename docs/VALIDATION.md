# 验证、离线回放与构建

当前版本 **0.7.1**。本次按用户要求仅编译、打包、定向验证和少量生产控制器离线回放，**未运行全量 build/check，未启动 Minecraft**。下文 0.7.0 的完整验证保留为历史证据，不能替代当前版本全量验证。

## 0. 0.7.1 定向范围

实现与实机日志因果见 [航迹连续性](TRAJECTORY_CONTINUITY.md)。本轮直接相关的七项验证为 `verifyTrajectoryContinuity`、`verifyTrajectoryStress`、`verifyAsyncPlanning`、`verifyEmergencyReplay`、`verifyStabilityLaunch`、`verifyArchitecture`、`verifyRiptideDuration`。

- [trajectory-directed.log](../tools/trajectory-directed.log)：compileJava 与 Continuity、EmergencyReplay、StabilityLaunch、Architecture、RiptideDuration 成功。包含固定第 93 tick 接触路线的 80/120 tick 对照，原时间戳证明、状态偏移/物理/推进拒绝、切换成本、控制权、Compact 推进的完整预测/执行一致性。
- [trajectory-package.log](../tools/trajectory-package.log)：编译、Continuity、Stress 与 Jar 成功；同 tick guided 交接不再落入普通路径结束逻辑。历史加速压力完成 2 命中、安全 Exit、5 次激流释放、0 烟花。
- [trajectory-release.log](../tools/trajectory-release.log)：最终 compileJava、Continuity、AsyncPlanning、jar 成功。真实碰撞抢占立即撤销旧证明。最终四例的首次攻击/命中/安全离场分别为静止 4/27/53、移动 7/30/56、停顿 11/29/49 ticks；动态墙体在第 10 tick 加入并阻塞 Worker，第 45 tick 完成无碰撞逃逸观察。
- 四个新增真实控制器回放：雨中双推进库存/静止目标，双推进/移动目标，已在攻击中阻塞全部 Worker 12 ticks，以及同一停顿中新增墙体。检查硬身体碰撞、推进不叠加、实际命中代理与安全离场；前三例要求连续攻击至少 12 ticks，墙体例要求安全抢占并继续无碰撞飞行 35 ticks。
- 历史压力场景通过 `verifyTrajectoryStress` 保留 **5 ms/tick**、真实 Worker、仅激流、移动与静止双目标；要求两次命中、安全 Exit、不中途落地，保留全部硬碰撞断言。它是显式诊断入口，不接入常规 check。
- 紧急冷启动两个场景现在都可先转离墙体再取得完整滑翔证书，50 ticks 无碰撞、末态朝离墙方向移动、无烟花消耗。`verifyStabilityLaunch` 独立保留较近墙体的 coast 无解/完整烟花可救、全随机寿命与危险推进拒绝。

失败调查日志保留：`trajectory-safety-1` / `trajectory-stress-2` 曾出现 500 tick 无命中；`trajectory-preempt-1` 的新增墙体 + Worker 停顿在第 24 tick 碰撞，推动了实时等待转向修复。`trajectory-preempt-2` 是旧“必须点火”断言失败；`trajectory-final-code-2` 中 Continuity、Stress、AsyncPlanning 已通过，随后新增的初态必须推进假设失败，该假设被数值检查证伪，测试改为上述完整脱险检查，并保留独立的推进必需反例。另有 `trajectory-final-code` 的一次方法参数编译错误，已修正。文件名不代表通过，不隐去中间失败。

逐 tick 数据与事件位于 `build/reports/trajectory-continuity/`，两例冷启动紧急事件仍在 `build/reports/controller-replay/emergency-*.events.log`。主动换路的收益门槛已做确定性检查，复杂场景中实际选中新路线的行为仍需实机观察；没有把离线 native hit 代理当成真实游戏伤害结果。

当前 Jar：[winefox_skyfarer-1.21.1-0.7.1.jar](../build/libs/winefox_skyfarer-1.21.1-0.7.1.jar)，583,748 bytes，SHA-256 `6A2910FB5323D27876E73AB30F976A881FA7A105453C5D635C5531F5C544838E`。Manifest/Mod 元数据均为 0.7.1，包含 TrajectoryCommitment，无离线检查或 Minecraft/TLM 替身；[校验记录](../tools/trajectory-artifact-check.json)。

## 1. 工作约束与入口

```powershell
# 完整验证，不带场景筛选
.\tools\build.ps1 -Stage v070-release-all -Tasks @('build','--continue')
# 定向诊断示例；不能当作全量发布证据
.\tools\build.ps1 -Stage targeted -Tasks verifyUnifiedPropulsion,verifyArchitecture
```

`build.ps1` 使用本机 JDK 21，并为 Windows 的 AF_UNIX 兼容问题选择 JDK TCP fallback。没有启动客户端/服务端任务。Replay 外部 API 夹具和验证类不进入 Mod Jar。

## 2. 三类证据

1. 生产编译检查实际 Minecraft/NeoForge/TLM/Spears API。
2. 数值检查使用生产物理/规划函数；Vanilla physics 差分验证实际依赖方程和固定随机种子。
3. Controller replay 编译真实控制器、提供器和 worker，对天气、实体/库存、世界碰撞和原生伤害使用离线夹具。它不是 Minecraft 内的端到端实测。

## 3. 全部验证任务

以下 44 项来自当前 build.gradle；每项均由 check 依赖。ControllerReplay 不设置 wfs.replayCase 时覆盖全部 21 场景。本轮没有执行这份全量清单。独立 `verifyTrajectoryStress` 不在其中。

- `verifyScanCadence`
- `verifyFlightMath`
- `verifyFlightPrediction`
- `verifyVanillaPhysics`
- `verifyAttackEnvelope`
- `verifyAttackPolicy`
- `verifySpearIntercept`
- `verifyMaidCombat`
- `verifyGlideCorridor`
- `verifyTerrainCombat`
- `verifySurfaceLaunch`
- `verifyYsmCompatibility`
- `verifyCombatRecovery`
- `verifySorties`
- `verifyControllerReplay`
- `verifyCommandDispatch`
- `verifyDispatchReplay`
- `verifyRemoteWake`
- `verifyVerticalAttacks`
- `verifyStabilityLaunch`
- `verifyHandoffStability`
- `verifyLaunchAttackReplay`
- `verifyEmergencyReplay`
- `verifyDebugTools`
- `verifyCombatEntries`
- `verifySweepingAttacks`
- `verifySweepingReplay`
- `verifyAsyncPlanning`
- `verifyAsyncSweep`
- `verifyPlannerLoad`
- `verifyEquipmentPropulsion`
- `verifyEquipmentLifecycle`
- `verifyEquipmentFixes`
- `verifyGroundMelee`
- `verifyRiptidePriority`
- `verifyRiptideRelease`
- `verifyRiptideDuration`
- `verifyRiptideDeparture`
- `verifyRemotePropulsion`
- `verifyAirRecoveryHome`
- `verifyMovingAttack`
- `verifyUnifiedPropulsion`
- `verifyArchitecture`
- `verifyTrajectoryContinuity`

## 4. 回放范围

Controller 全生命周期：平地近/远/后方、短/最长火箭、Home/Follow 连续三目标、坡地、小目标、目标消失、工作取消、雪层/半砖/混合碰撞、冰面惯性、32 格坡面、洞穴遮挡、山顶、上坡和高柱。Dispatch 包含普通远程、900 格运输、创造倍率、友好标记、交接与收尾。

移动目标覆盖静止、小范围、矿车往返、横移和改向；竖向覆盖陡升/俯冲/越脊/下降再上攻；紧急验证真实侧向动量、提前检测、滑翔无解但激流/火箭可救和危险推进拒绝。

装备/激流验证 Unbreakable、备用装备、低耐久返航、TLM 导航台阶和普通 Spears 内外 reach、对话去重、真实淋雨、READY 独立准备、副手真实释放、耐久、事件否决、短时长与可持续起飞、连续空战和远程仅激流资格。

## 5. 异步与架构检查

verifyAsyncPlanning/AsyncSweep/PlannerLoad 覆盖关闭、取消、generation、陈旧结果、Entity/Level 替换、队列/采集上限、多会话负载和快照纯度。

新增 verifyArchitecture 扫描生产字节码（包含上层 lambda），禁止 AirCombatController/TransitFlight 直接调用具体推进实现、寿命 accessor 或 RocketProfile；检查 worker 任务字段不能包含游戏对象或函数回调；确认三个无效配置/不可达状态已删除，有效配置仍保留。

## 6. 报告与诊断

构建日志在 tools/*.log；逐 tick 控制器轨迹位于 build/reports/controller-replay/*.tsv 与 *.events.log。先读失败任务、最后有效状态和拒因，再判断是地形缺失、证书 pending、目标运动、物理无解还是实际碰撞。

`baseline-070.log` 记录未经修改基线首先失败于旧坡面样本数断言。后续 baseline-070-all 与 refactor-070-full 是调查日志，期间发生源码/测试接口迁移，不作为最终发布证据；其中失败均保留。

`refactor-070-home.log` 是定向修复证据：原 Home 连续场景 450 tick 无命中，修复后完成三次命中并正常落地；两目标激流连续攻击完成安全 Exit。原因是曲线 coast pending 被低速条件误作推进需求，额外烟花改变了已认证进攻航线。没有放宽碰撞或命中条件。

旧测试更新：坡面最大采样步长已是 0.4；恢复推进资格后 FLIGHT_LIMIT_OR_FUEL 可恢复且仍受滞空时间约束；独立 READY 不调用 LivingEntity.releaseUsingItem；旧带前摇的坐标激流入口由当前 Demand 取代，立即近墙拒绝反例继续保留。不会恢复错误旧行为来满足过时断言。Launch Attack 可直接进入 ATTACK_RUN，生命周期不再强制经过 CLIMB；攻击、碰撞、落地、资源释放断言保留。

遮挡搜索回归覆盖：持续追踪刷新可见记忆、隐藏目标不能直接攻击、超时和死亡清除；`v070-uphill-side.log` 为筛选上坡场景的定向证据，不当作全量结果。之前带 acceptance/release 名称的失败日志同样保留；文件名不代表验证成功。

落点夹具按生产有限搜索深度取支撑；异常爬升限额相对作战高度计算，仍为 55 格，平地不变。不会用固定世界 Y 上限误拒山腰目标的合法恢复。

现有 AirRecoveryHome 明确覆盖 LANDING 的合法软返航恢复；Controller 旧无条件禁复战断言已同步，只有任务未完成、未触地且重新看到合法目标才允许重新攻击。硬返航/完成任务的释放约束继续由 Home/Command/Controller 回放检验。

## 7. 历史 0.7.0 完整构建证据

0.7.0 当时的最终发布证据为 [v070-release-all.log](../tools/v070-release-all.log)：`BUILD SUCCESSFUL in 6m 46s`。当时完整 build/check 执行并通过全部 **43 项 verify**（49 个任务，45 执行、4 已是最新），无场景筛选、无失败或跳过的 verify。它不代表 0.7.1 本轮执行了全量验证。

- **Controller：21/21** 完整生命周期回放通过，包含 Home/Follow 连续命中、不同随机火箭寿命、取消/消失、部分方块、坡面、山顶与高柱。上坡本轮首次攻击 330 tick、落地 446 tick，仍符合原有攻击时限和战区约束。
- **Command/Transit：7/7** 调度全周期通过，含 900 格、创造倍率和友好标记；RemoteWake 通过多候选分批、第二轮任务、存档重建、失败重试、票据上限与释放。
- 激流 READY/副手释放、各推进阶段、两目标连续命中/安全 Exit、真实紧急恢复、干燥 fallback 全部通过；仅激流 Home 普通/创造远程交接通过。AirRecoveryHome 的合法软返航恢复与已加载 Home 控制释放通过。
- 18,000 步 pinned 1.21.1 原版物理差分、60 个地面/水面/冰雪起飞寿命场景、移动/竖向攻击、Strike/Exit、装备/地面 fallback、异步/生命周期/调试/可选兼容均通过，详见本页完整任务表与日志。

发布 Jar：[winefox_skyfarer-1.21.1-0.7.0.jar](../build/libs/winefox_skyfarer-1.21.1-0.7.0.jar)，**567,644 bytes**，SHA-256 `157B2457B289ED9F02D67C3BBAAFF851FD53335FFBBDE7162DD2928DD884313A`。Manifest 与 neoforge.mods.toml 均为 0.7.0；不含测试夹具、Minecraft/TLM 替身、已删除 LaunchPlanner/PropulsionAvailability。逐项校验与场景结果见 [artifact-check](../tools/v070-artifact-check.json)。

此前调查中的失败日志保留，尤其 v070-complete 中误禁 LANDING 软恢复的回归已撤销；当前发布只对应上面的成功构建。没有启动 Minecraft、没有把 Jar 安装到测试实例。历史 5 ms/tick 加速压力失败仍是已记录边界，标准回放通过不等同于任何 Worker 延迟下都安全。

## 8. 必须实机确认的事项

- gravity/noGravity、缓降到期、魂符收起后跨维度放出、同名 Level 重建以及其他 Mod 修改 travel 或实体 tick 顺序时的真实行为。
- 真正 NeoForge/TLM/Mixin 生命周期、服务端关服/重新开世界、实体卸载重载/跨维度、玩家退出时序。
- 创造指挥镜的未加载女仆唤醒、滚动票据、目标区块卸载、Home 回收、真实传送的安全落点和资源释放。
- 客户端网络、望远镜左键/缩放、标记动效音效、Debug 多女仆切换、穿墙框、HUD、预测走廊/曲线渲染。
- TLM 原生模型和可选 YSM 模型的实际鞘翅动画/装备显示；接口匹配不能保证每个模型提供动画。
- 原生 Spears 伤害、友好保护、附魔/第三方模组交互、扩展命中是否符合用户体验。
- 真实复杂世界与模组碰撞形状、动态方块、液体、活动目标、外部击退、网络和 chunk 延迟。
- 不同 CPU、女仆数量、预测倍率和创造高速赶路下的 MSPT、队列等待、GC/分配、内存与加载开销。应使用真正 profiling 做前后对比，不能由合成负载推导提升百分比。

离线通过是可重复的工程门槛，不是上述实机行为已经确认的替代证据。收到用户实测问题后，应保留日志、参数、场景和 Mod/依赖版本，再决定补充哪一个可复现的离线反例。
