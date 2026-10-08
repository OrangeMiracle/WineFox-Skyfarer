# 0.6.2.11 统一推进与独立 READY



核对：2026-10-08。基于当前本地 0.6.2.10 未提交工作区，保留移动目标拦截与此前 Home/恢复修复。没有 Funnel，没有启动 Minecraft。



## 源码审查发现



此前资格已经允许激流，但实际路径仍不统一：AttackLane 的 fuel 仍只认火箭；紧急转向只有 glide/rocket；曲线 Strike/Exit 补能直接调用 sweepPulse；manageEnergy 预热火箭、排除锁定目标和 ATTACK_EXIT；Transit 使用独立免费火箭起飞。实体 OFF_HAND 使用会话还会与主手 Spears 争用。问题不在单个评分常数。



现在 `PropulsionCapability.inspect` 是库存/环境能力入口，`PropulsionDemand` 描述阶段、方向/目标高度、最低速度、曲线、动态目标、转向率、接触时限；`PlanningTask.Propulsion` 是唯一激流 Worker 请求。原 `PlanningTask.Riptide` 及旧 AsyncPlanning 激流入口已删除。`PropulsionCapability.decide` 在 coast 不足后统一先尝试激流，再执行火箭专属的完整证书回调；起飞和 Transit 通过同一 prefer/warm 入口衔接各自出发流程。没有把可控推力冒充全寿命火箭。



| 路径 | 本版接入 | 保留的硬约束 |

| --- | --- | --- |

| 装备/普通镜/创造镜 | 同一实时能力快照，最高可用时长上限覆盖较低等级的全部短候选 | 真实雨/水、可取出且可交换、耐久/附魔有效；干燥无烟花明确拒绝 |

| TAKEOFF / Launch Attack | 地面 READY → 普通跳跃 → 离地滑翔 → 重验释放；起飞后机会目标复用原 Launch Attack | 身体净空、推进截止/交接速度高度、24 tick 恢复及转向余量；不要求一发到远目标 |

| APPROACH / REPOSITION | 共用需求、预热、优先选择及火箭回退 | 当前滑翔足够不推进；已可用攻击入口不被无谓补能破坏 |

| Rejoin / moving intercept | 最多 12 个拦截几何种子可由激流资助；已有火箭资助几何也先比较可执行激流 | 1–30 tick 推进后继续完整原生接触/Strike→Exit 认证，主手 charge deadline |

| STRIKE / ATTACK_EXIT | 激流候选携带当前曲线；实际控制与预测共用 command | 全段 body sweep、现有生物 spin 反转排除、当前目标运动、接触射线、实际速度与离场；只缩短拥有的 spin |

| AIR_RECOVERY | 统一能力与候选，实际执行已认证恢复段 | 末速/高度、不超越目的地、下一轮可接管 |

| EMERGENCY | glide 优先；至多 6 个逃逸方向的可控激流；随后完整寿命火箭 | 当前动量、45°/20° 有限转向、全逃逸扫掠；不等待未准备好的资源来错失紧急火箭 |

| Transit / Combat 交接 | Transit 起飞及普通补能使用共同激流入口，READY 在显式交接保留 | 真实本地路线；取消 owned spin 不清滑翔；普通关闭/卸载清理资源 |



创造 Transit 的独立高速赶路仍由 CreativeTransitPlanner 管理；保留旧免费火箭后备契约，激流被选中时则消耗三叉戟的真实耐久。并未把免费运输扩展成战斗内免费推进。Transit 的激流活体检查采用当前位置 128 格局部范围，战斗沿用 CombatAnchor 边界；都仍受已加载地形、有限时域及每 tick 重验约束。



剩余直接火箭入口限于**已通过前置共同选择的出发/水面流程**、紧急 firework 专属证书和火箭执行器。remaining life、点火延迟、cooldown、全随机寿命遍历、爆炸属性、库存扣除本来就是火箭专属数据，不以通用布尔值代替。正在燃烧的火箭不叠加激流，因为两种推力混合尚无证书。



## 10 tick 与实体 item-use 的边界



本地 pinned Minecraft 1.21.1 `TridentItem.releaseUsing` 明确检查 `getUseDuration - remaining >= 10`，且冲刺主体只处理 Player。NeoForge 的 `LivingEntity.releaseUsingItem` 在物品回调前执行 `EventHooks.onUseItemStop`。相关源码在 `build/equipment-reference/world_item_TridentItem.java`、`build/physics-reference/world_entity_LivingEntity.java`，依赖来源见 [物理文档](VANILLA_PHYSICS.md)。



WineFox 有意扩展为**自己的 10 个有效服务端 tick 资源**，不声称这是 vanilla 的实际按住右键计时：



- `RiptideReadiness` 绑定实体、Level、确切三叉戟和强度、主手及胸甲身份；同 tick 不重复累计，漏 tick 不补发离线准备。

- 10 tick 达到 `RIPTIDE_READY`，环境和物品有效时保持。释放消耗一次，下一 tick 重新准备，可在当前 burst 期间累计；没有火箭 cooldown。仍不在已有 spin/推进期间重复点火。

- 失雨/出水、物品失效或移除、装备替换、Level 变化及控制释放清除。显式 Transit/Combat 交接共享资源；卸载没有保存 READY。

- READY **不调用 startUsingItem**，不长期占用 OFF_HAND，也不停止主手。释放通过 NeoForge stop 事件门 → 该副手 ItemStack.releaseUsing → TridentItem 的受同步授权女仆回调。传入的已准备 10 tick 来自 WineFox 资源，而非伪称实体 remainingUseTicks。

- 回调同时核对 ThreadLocal、实体/副手堆栈身份、雨/水、附魔强度、READY 和门槛；消耗 1 耐久，启动选定时长 spin、首 tick 推力与声音。事件否决无补发。授权 finally 移除，外部/重复调用不能点火。

- 玩家三叉戟、主手 Spears 原生使用/伤害保持自身生命周期。WineFox 不用预测命中扣血。正常消耗后的 spin 截止保留动量和 FALL_FLYING。



I/II/III 仍分别允许最多 10/20/30 tick，每 tick 0.2 速度增量；等级只扩大时长上限，因此选择最高可用 cap 并不强迫长推进。无物理解时允许火箭后备。



## 证书与等待



READY 候选直接从 release 时刻预测，不再虚构额外 10 tick 空中 coast；departure 与 readiness 分开标记。Worker 只收到不可变数值、曲线及快照。主线程释放前和执行中重新认证选中路线，未知/过期地形拒绝；原预算不扩大。



曲线推进持续更新当前目标 Aim，推进尾段重新模拟 Spears 接触、剩余 charge 时限与 Exit。不能因为局部推力段安全就授权攻击。原生命中时结束这段拥有的推进并转 Exit；目标失效时取消后重走原安全状态机。



候选 pending 不等于 coast 失败；两种提供器可预热，但等待期间继续当前曲线转向。普通/起飞等待最多 11/39 次；确定的环境、实体扫掠、释放事件拒绝立即允许后备，不把拒绝无限循环成新的等待。紧急不等待。激流自己的逐 tick 认证包含实际推力，不能用遗漏推力的 coast TTC 直接取消它；如果自己的当前证书失效，停止推力并交回现有紧急恢复。



## 验证与诊断



`RIPTIDE_RESOURCE` 记录 READY/失效转换；`PROPULSION` 记录 phase、当前环境/强度、时长、等待/拒绝与独立火箭 cooldown；`RIPTIDE` 记录 READY_RESERVED、READY_CHECK、LAUNCH_JUMP、CERTIFIED_READY、READY_RELEASE_CALLBACK（冲量/spin）、RECOVERY_BEGIN、END。日志中的 `charge/remaining/usedHand` 是实体当前使用信息，可能属于主手长矛；`prepared` 才是 WineFox 资源。装备诊断另输出统一 propulsion source。



定向证据见 [propulsion-acceptance.log](../tools/propulsion-acceptance.log) 与 [propulsion-review-checks.log](../tools/propulsion-review-checks.log)。新 `verifyUnifiedPropulsion` 覆盖阶段矩阵、短时长最小性、静止/横移完整接触与 Exit、过期 charge、封闭走廊、连续两次空中释放及纯滑翔无解但 7 tick 激流可逃出的实际 motor 回放；释放测试覆盖主矛持续使用、10 tick/保持/失效/再充能、同 tick 真实回调、耐久和事件否决。



真实生产 controller/worker 的移动目标回归覆盖静止、小范围、矿车往返、横移和改向；外部实体、天气、事件和 Spears 伤害是离线夹具/接触代理，不是游戏内端到端测试。普通/创造远程测试真实执行能力门、wake 调度器、Transit 与交接；CommandManager 全服务器事件仍由源码连接检查与生产编译覆盖。不启动 Minecraft，不宣称已经实机验收。


追加 [两目标连续空战回放](../tools/propulsion-continuous-second.log)：2 次命中，7 次激流释放，0 烟花，无中途落地，安全离场。该测试使用与移动目标基准相同的 20 ms/tick 离线 Worker 调度；最初加速到 5 ms/tick 的版本出现碰撞，失败日志保留，不代表这一压力条件已修复。不将常规回归通过外推为任意 Worker 延迟下的安全保证。
