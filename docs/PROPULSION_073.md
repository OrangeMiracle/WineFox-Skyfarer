# 0.7.3 动力源仲裁与持续速度保持

> 本专题保存 0.7.3 的动力架构与当时光效参数；0.7.4 已替换下述原版 dust 光效，当前 Fullbright、距离、环几何与诊断参数见 [彗星光效与掉落保护](COMET_RADIANCE_074.md)。动力架构仍适用。

当前实现以本地源码和 Minecraft 1.21.1 / NeoForge 21.1.250 / TLM 1.5.3 依赖为准。没有启动游戏或修改依赖 Jar。

## 问题证据

用户提供的 latest.log 中，20:06:47.639 和 20:06:55.438 都出现 `purpose=AIR_RECOVERY source=VIRTUAL_PROPULSION virtual=SNAPSHOT_MISSING:1 reason=boosted attached=true`（字段原顺序见 [日志摘录](../tools/propulsion-authority-evidence.txt)）。旧 `source` 表示装备资格，并非正在执行的动力。源码中永燃候选 pending 超时或失败后会继续尝试激流/烟花，起飞、Rejoin、Emergency 还有直接 `FireworkManager.boost` 入口。旧有限虚拟加速段之后强制执行无动力恢复，也会形成补能间隔。

## 权威入口

```text
AirCombatController / TransitFlight
  → FlightPropulsion：需求、路线和控制器生命周期
  → PropulsionCapability → PropulsionResolver：来源规则、等待、独占装备
  → Provider：实际资格 → 当前物理认证 → 执行
```

`PropulsionResolver.RULES` 的优先级为虚拟 300、激流 200、烟花 100。永燃规则为 exclusive：佩戴时不公布激流/烟花候选，虚拟认证失败、缺快照、等待超时或紧急需求都不能降级消耗物品。没有永燃时仍先认证合法且 READY 的激流，不能执行才考虑烟花。普通之心仅修改一次成功烟花发射后的节能结算，没有独立动力来源；与永燃同戴不结算。

`Provider` 提供 `offer`、`warm` 和可选 `sustain` 契约；注册 Provider 和来源规则不需要改写上层状态机。Context 只存在于主线程，含实时适配器的 Context 不进入 Worker。实际 Worker 仍只接收不可变 `PlanningTask`、数值 Demand 和 CollisionSnapshot。

起飞、普通请求、Rejoin 和 Emergency 执行现在全部经同一 Resolver。生产代码只有 Resolver 的烟花 Provider 可调用 `boost`，由编译后字节码验证守护。`FireworkManager` 和激流原生释放入口还在提交资源前复查同一来源政策；烟花生成事件回调期间若装备变化，会撤销新实体，既不扣燃料也不抽节能随机数。没有在预测阶段扣物品。

独占装备变化撤销活动 Provider、解绑自己创建的火箭、清空等待，并使异步结果及当前轨迹证明失效。保留路线身份，由新状态重新认证。激流拒绝接收虚拟力模型的计划。原有已消费燃料不会倒退退款。安全控制权、真实身体碰撞、目标/装备失效和未知地形仍优先于推进。

## 持续输出与预测

`VirtualFlightControl` 为当前需求建立一个候选，`VirtualPropulsion.sustain` 只执行其通过认证的下一 tick，不占住上层状态机。正常阶段每次认证 12 tick 闭环推力和 12 tick 无动力后续；Strike/Exit 则认证 1 tick 推力，再证明完整接触与离场。后者没有用短时碰撞通过代替攻击认证。首次起飞和需要较大能量的恢复仍复用已有有限加速搜索。

闭环力模型在 `ThrustProfile` 中，预测和执行共享：

- 目标三维速度为 `clamp(minimumSpeed + 0.2, 1.2, 1.8)`。
- 请求加速度为 `clamp(0.025 + 0.05 × max(vy, 0) + 0.18 × (目标速度 − 当前速度), 0, 0.2)`。
- 最终推力同时受 `max(0, 2.4 − 当前速度)` 限制，高速俯冲已有动量不会被截断。
- 姿态使用现有高度/垂直速度反馈，转弯使用 Compact Goal，攻击使用已有 Sweeping path；先受限转向、沿 float look 加力，再走真实 Vanilla Elytra。没有锁 Y、抵消重力、直接旋转速度或改写位置。

低误差时自然降为小推力，减速需求或达到限速时可以归零；没有积分状态在断推后积累。有限加速结束即可交回路线并接入速度保持；认证仍保留无动力恢复余量，但不强制执行一段熄火等待。每 tick 至多一次虚拟冲量，包括持续控制与有限加速之间的交接。

地形快照仍有原来的年龄与预算，当前认证只对一个候选最多重试一次。普通巡航总预测最多 25 个状态，攻击继续使用原完整路线的有界预算。最终还有真实世界下一步身体扫掠，Worker 阻塞或旧快照不能覆盖它。八女仆共享执行池、每会话 768 cells / 2 pending jobs 限制不变。

持续保持接入正常 Transit 和战斗的导航/转弯/攻击/离场需求。实际降落不主动保持巡航速度；已有创造指挥镜高速赶路属于此前独立的显式运输特权，未改称虚拟推进，也不触发永燃特效。

## 特效与性能边界

只有主线程实际施加虚拟冲量后，才向跟踪该实体的客户端发送 `CometThrustPayload`。稳定输出每 4 ticks 更新一次，启停立即通知；实体 tick 结束发现没有输出即发停止，客户端另有 6 tick 超时。网络协议版本更新为 4；common 代码不链接 Minecraft 客户端类。

客户端沿当前运动方向建立正交坐标系（近零速退回 look）。背后左右各一条赤色 dust 尾流，每两 ticks 各 1 粒，高输出各 2 粒；残留粒子自然构成弯曲尾迹，寿命 8–14 ticks。实际重新加力或相比上次环显著增大输出可触发白环：16 粒位于垂直于实际 look 推力方向的平面，径向外扩，10 tick 消散，至少间隔 20 ticks。

客户端最多保存 128 个活跃实体，只在 48 格内显示；每客户端每 tick 尾焰最多 96 粒、气流环最多 64 粒。最少粒子设置不显示，减少设置降低数量。世界切换、实体 UUID 不匹配、移除或超时清理缓存；服务端 gate 使用弱实体键并在停服清理。没有服务器粒子实体、新线程或逐粒广播。

日志现在将 `source`（实际执行）和 `eligible`（装备资格）分开；持续输出记录 `VIRTUAL_SPEED_HOLD` / `CERTIFIED_SPEED_HOLD`，调试通道显示这次已认证的推力轨迹。

## 验证

定向入口：`verifyPropulsionAuthority`、`verifyPropulsionAuthorityReplay`、`verifyPropulsionPressure`，以及直接相关的 CometContracts、RiptideRelease、Architecture。没有挂到全量 `check`。日志、产物与保留的失败见 [VALIDATION](VALIDATION.md)。粒子外观、第三方模型位置、真实 TPS/FPS 和动态地形效果仍需用户实机确认，离线通过不等于无限负载下的保证。
