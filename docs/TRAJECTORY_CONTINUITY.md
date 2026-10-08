# 0.7.1 航迹连续性与控制权

本文描述当前本地实现。入口仍是 `AirCombatController`、`AsyncPlanning`、`FlightPropulsion`；新增的 `TrajectoryCommitment` 管理已选曲线及其有限证明，没有替换 Vanilla Elytra、SweepingAttackPlanner、AttackCertificate、Rejoin 或碰撞系统。

## 1. 证据与根因

只读检查了用户实例的 `E:\rlc\PCL\.minecraft\versions\1.21.1-NeoForge_21.1.250\logs\latest.log`。当时共 5,213 行，摘录与计数保存在 [trajectory-log-evidence.txt](../tools/trajectory-log-evidence.txt)。其中 199 次 `ASSESS_NEXT_ATTACK_ENTRY`、98 次 `CLIMB_BRAKE_TURN_IN`、17 次 `CURVE_NO_SAFE_CONTINUATION`，同时有 66 次 `EARLY_COLLISION_RECOVERY`。这些是事件计数，不是独立攻击次数或命中率；不能把每次大转向都认作控制冲突。

最直接的异常在日志 08:51:06.934–06.986：入口选择预计 **90 ticks** 后接触，下一 tick 的续接报告 `hit=false traceTicks=81`，随即离场。源码中候选/Rejoin 使用 120 tick 基准，而 `AsyncPlanning.follow` 只给 80 ticks。离线固定数值反例在第 93 tick 接触：旧 80 tick 调用失败，同路径 120 tick 调用成功。修复统一调用预算，保留 160 tick 硬上限及完整离场条件。

其他源码原因及处理：

| 原行为 | 当前处理 |
| --- | --- |
| 激流开始后，同 tick 的普通 `attack` 或 terrain 控制仍可覆盖航向；活动 motor 被当作“候选拒绝”后可能落入烟花后备 | `SAFETY > PROPULSION > ROUTE` 仲裁；活动推进返回继续拥有控制权；guided 时提前结束普通分支 |
| guided 恢复完成无条件 invalidate，再 startApproach，丢弃 REPOSITION 进度 | 回到原阶段、原固定目标和 Compact Goal；真正变化仍经兼容性/当前认证拒绝 |
| 初始候选 pending 的直接朝目标等待，反复覆盖已建立的紧凑转弯 | 8 tick 等待只用于初始 APPROACH；REPOSITION 沿已有 Goal 反馈 |
| Riptide Turn 只朝固定方位推，Firework Turn 使用 Compact 反馈 | Demand 显式携带同一 Goal/rate；Riptide 的逐 tick 预测与执行也使用 Compact 控制律，仍施加自身的 .2 推力 |
| follow pending 构造空 trace 的 `safe=true` 临时值 | 没有证明就没有 safe；有效时间后缀或选中路径的当前严格重验才能继续 |
| search 返回 `Search` 包装，而优先采集只识别 `Selection`；较慢几何任务 6 tick 就取消 | 正确解包 Search/Rejoin；优先保留选中通道；几何提案工作/seed/结果窗口为 24 ticks |

最后一项只扩大**候选提案**的完成机会，不延长执行证书，不允许用旧候选直接点火或攻击。

## 2. 路径与证明分离

`TrajectoryCommitment` 的路径身份、选择时刻、切换次数与异步 job 独立。`Proof` 包含模拟起始 tick、路径、Aim、实际附着推进剩余时间、gravity、rate、最低接触速度、Strike/Exit 阶段及完整 rollout。

每 tick 的执行顺序：

1. 收取同路径的后台 follow 结果，尝试匹配当前实际状态与旧 trace 在 `now - proof.tick` 的对应状态。
2. 原始证明年龄最多 **6 ticks**。位置差 ≤.65 格，速度 L1 差 ≤.25，yaw/pitch 差各 ≤6°；PhysicsContext、rocketDelay、按时间递减的真实火箭剩余寿命必须一致。
3. Aim 位置差 ≤.6 格、速度 L1 差 ≤.12，身体形状、reach、margin、readyIn 必须匹配时间推进后的值。已经越过预测接触时刻却没有实际伤害，不能拿原预测 hit 续期。
4. 无可用后缀时，只对**已经选中的一条路径**从当前 State/Aim 重验，最多 160 integration steps，使用 ≤20 tick 的不可变 cells；不搜索新候选、不访问实时世界、不授权新推进。
5. 当前重验失败立即拒绝。缺失必要数据请求采集并安全离场，不伪装成功。已通过硬身体/接触/离场检查的结果，不因仅用于宽敞度评分的 `.5/.4` 外层样本缺失而被作废；硬身体余量仍为 `.15/.12`。
6. 用当前实际状态和路径反馈计算当前指令；不重播 Worker 的旧首帧姿态。

读取、入邮箱、重复 accept 都不会重置证明时间。配置变化清证明；目标失效、阶段变为 Exit、推进改变、物理改变或外部位移都要求重新认证。Strike→Exit 保留路径几何并清除 Strike 证明。生命周期结束仍清理路径、会话和推进。

证书允许的短时间偏差不代表真实世界永不改变。每 tick 的实时 TTC/身体防线继续拥有最高优先级；快照刷新没有被当成动态方块的即时通知。

## 3. 可选改进与切换成本

有效攻击路线执行期间，距预计接触 >16 ticks 时，最多按 16 tick 节奏请求候选改进；pending 只轮询领取，不清除当前路径。已选路径至少保持 12 ticks，且当前重新认证的接触剩余 >12 ticks，才参加收益切换。紧急中断不受这些门槛限制。

新旧两条路线都以**当前相同状态**完成认证后比较：

```text
candidate.cost + max(6, incumbent.cost × .20)
               + .08 × 首步 yaw 差 + .05 × 首步 pitch 差
    < incumbent.cost
```

切换只更新路径、当前证明、接触倒计时和出口端点；不重开主手 use session，不重置 runTicks、锁定目标、pass history 或已经发生的命中。新旧比较过程中如果发现旧路线的当前严格复核失败，也必须中断旧路线。

这是有限候选加反馈的滚动控制，不是全局 MPC 优化器。设计参考了 [Apollo TrajectoryStitcher](https://apollo.baidu.com/docs/apollo/9.0/classapollo_1_1planning_1_1TrajectoryStitcher.html) 的时间/状态对齐思路，以及 [Autoware 行为路径规划](https://autowarefoundation.github.io/autoware_universe/main/planning/behavior_path_planner/autoware_behavior_path_planner/) 的规划模块与执行职责分离；未引入这些框架或照搬车辆模型。

## 4. 推进和安全抢占

`HeadingController.beginTick` 保存实际起始角；所有同 tick 指令仍从同一个起点限速。ROUTE 不能覆盖 PROPULSION，SAFETY 可以覆盖二者；同权限仍允许状态方法提交最终指令。guided 交接显式释放拥有权。正常路线能量管理不再因空闲的另一个提供器重新定义正在执行的方向。

活动激流不落入烟花后备；活动烟花不能被当成可立即停止的推力。安全取消只停止 WineFox 拥有的激流控制，保留真实动量；火箭继续按实际剩余寿命预测。Riptide 仍逐 tick 完整复核自己的推力/滑行及实体碰撞，不能用漏掉激流推力的 coast-only TTC 误取消。

本次专门复现了**已在攻击中出现墙体，同时阻塞两个实际 Worker 池 12 ticks**：最初补丁仍在第 24 tick 撞墙，见 [trajectory-preempt-1.log](../tools/trajectory-preempt-1.log)。旧等待姿态只抬头，来不及转离墙面。因此新增 `EmergencyAvoidancePlanner.holding`：

- 每 tick 用实时身体扫掠，复核上一等待姿态的未来 12 ticks；合法则保持。
- 否则最多比较六个固定逃逸姿态，选完整 12 tick 清晰者，或可存活步数最多者；连旧姿态复核合计最多 84 步。
- 不新增推力、不宣称完整脱险认证；完整紧急方案仍由 Worker 提案并经当前状态全寿命复核。最后一层当前步 live guard 仍运行。

较早转离障碍可能使原先需要点火的冷启动回放改为滑翔脱险。紧急回放因此按完整证书、50 tick 无碰撞、末态远离墙面和消耗与实际点火一致来判定；独立 `verifyStabilityLaunch` 继续保留“coast 无解、完整火箭可救”的数值反例及危险推进拒绝。

## 5. 有界异步与观测

全局线程数、队列、每 session pending、采集 2 ms/256 cells/21,600 reads 等预算均未扩大。选中通道在 cell 年龄达到 8 ticks 时请求优先刷新，普通背景仍在 20 ticks 请求；采集统一受原预算限制。当前单路径复核严格只使用 ≤20 tick cells。几何 24 tick 窗口与 follow 6 tick 证明窗口相互独立，generation/Level/config/目标身份的真实改变仍取消工作。

黄色通道由 `DebugSnapshot.corridor` 绘制保存的动力学 trace，**从来不是单凭颜色就可判定的攻击证书**。当前增加：

| prediction_kind | 含义 |
| --- | --- |
| `COMMITTED_SUFFIX` | 已承诺曲线当前用于跟踪的证明后缀 |
| `GUIDED_ROUTE` | 活动 Riptide 刚完成当前重验的推力/滑行通道 |
| `GLIDE_FORECAST` / `PATH_FORECAST` | 后台完成的普通滑翔/路径预测，不自动等于承诺攻击 |
| `CANDIDATE` | 后台搜索候选，仍需当前接入认证 |
| `PROOF_UNAVAILABLE` / `NONE` | 无可显示的相应 trace |

HUD 增加控制 owner、来源与 `trajectory_status`，日志增加 `TRAJECTORY`（id、proofAge、switches、reason、拒绝的低优先级指令数）及 `TRAJECTORY_SWITCH`。未承诺的后台 trace 仍只展示 8 tick 内缓存；过期、generation 更新和状态交接都能使黄色线消失。debug 不触发计算或采集。诊断时应同时看状态、来源、continuation reason、实际 owner，不能用线长判断执行质量。

## 6. 定向验证与实机边界

本次没有运行 `build/check` 或全量 verify，也没有启动 Minecraft。定向任务及日志见 [VALIDATION](VALIDATION.md)。新增固定检查覆盖：93 tick 接触时域、证明有限寿命/时间对齐、物理/推进/外力拒绝、切换收益门槛、控制权抢占、Compact 推进预测/执行一致性。

实际生产控制器配合离线世界，覆盖双推进库存下静止/移动目标、12 tick Worker 停顿时继续攻击、动态墙体与停顿同时出现时紧急离场。历史 5 ms/tick、仅激流、两目标连续作战重新完成两次命中和安全 Exit；硬身体碰撞与中途落地断言保留。历史失败日志没有删除，也不以普通时序通过替代延迟验证。

仍需实机观察双提供器切换频率、显著更好路线的实际切换、矿车反向/目标急变、复杂动态方块、实际 Spears/Mixin/原生实体 tick 次序及多女仆负载。证明在缺失快照时仍会被拒绝；新短程安全等待并不保证任意速度、任意地形或无限 Worker 延迟均能脱险。没有实机命中率或性能提升的测量结论。
