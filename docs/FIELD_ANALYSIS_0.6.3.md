# 0.6.3 实机日志分析与修复依据

输入：用户指定的 `E:\rlc\PCL\.minecraft\versions\1.21.1-NeoForge_21.1.250\logs\latest.log`（597,362 bytes）与 `debug.log`（1,977,730 bytes），文件最后写入时间 2026-10-05 20:58:58。先读取日志、相关 docs 与源码，再进行修改。两份日志中的同一 WFS 事件不重复计数。本文时间沿用日志原文，不换算时区。

完整 WFS 过滤对照：latest 与 debug 各 1,754 行，debug 独有 WFS 事件 0 行，`[WFS] Async planner failed` 0 行。

## 日志事实

- 可见 WFS 转换中，`COMPACT_TURN_ALIGNED` 97 次，`ASSESS_NEXT_ATTACK_ENTRY` 91 次，`ATTACK_CORRIDOR_BLOCKED` 72 次，`FUNNEL_CAPTURE` 34 次，`TURN_REASSESS` 27 次。存在频繁的进入/退出/再对准；这些统计不能单独证明每次转换都错误。
- 女仆 UUID 前缀 `f2bf9a69`：20:58:46.429 位于 Y=86.570，vy=-0.129，离地 23.570，状态 APPROACH / TURN_REASSESS，`energyDecision=not_requested pulseReview=async_pending cooldown=0`；20:58:51.429 已降至 Y=70.837，vy=-0.123，水平速度约 .328，状态 REPOSITION / FUNNEL_CAPTURE_PENDING，离地 7.837。期间反复出现 FUNNEL_CAPTURE、TURN_REASSESS、CORRIDOR_AIRBRAKE / ATTACK_EXIT。
- 女仆 `57d5cb3e` 在 20:58:44.129 的 Capture 等待状态 Y=114.904、vy=-.174，20:58:49.181 已到 Y=78.745、vy=-.296，物理认证仍为 `ATTACK_PHYSICS_VETO:async_pending`；20:58:50.679 接地。末段不能解释为“烟花还在冷却”：20:58:49.181 明确 cooldown=0。
- 起飞：`f2bf9a69` 在 20:56:44.379 因 `NO_LAUNCH_CORRIDOR` 返回地面。20:56:44.828 已分配 10.232 格外的明确标记，20:56:46.179 地面 preferred 已正确指向该标记，但直到 20:56:47.378 才 TARGET_SELECTED。20:56:47.829 又因 `NO_LAUNCH_CORRIDOR` 拒绝。传播成功与真实净空拒绝必须区分。
- 日志同时记录 16 次 `JUMP_BODY_BLOCKED`，以及多次成功的 TAKEOFF→CLIMB / SURFACE_LAUNCH_ROCKET。不能把所有不起飞都当作队列问题，不能为缩短响应时间删除净空检查。
- 可见末段异步累计 stale 从约 1511 增至 1880，failed=0。旧日志只打印控制 session 的 gen，未打印地图 gen；stale 也不等于全部 ticket cancellation。因此不能据此声称地图每次都由 phase 变化失效。0.6.3 增加 funnelGen、cancelled、submitted。

## 源码确认的原因

| 问题 | 0.6.2 实际路径 | 0.6.3 处理 |
| --- | --- | --- |
| 静止窗口闪烁 | PlannerSession 正地图在提交后 40 ticks 硬到期；没有预刷新。refreshFunnels 每 tick 先清空，Pending 返回 null 后清掉 Capture。证明 cells 还会被无关搜索驱逐 | 未满额地图每 40 ticks 后台补充，仍返回通过覆盖检查的旧地图；同 Input 只补空槽，已有合法窗口保持身份，满额地图不重复搜索。证明 cells 优先刷新和保留，仍受 80-tick 新鲜度限制 |
| 细壳 | 一个 witness，沿理想 Path 的固定半径调试框，无轨迹群包络 | 每个选中窗口最多 27 个相邻运动状态完整模拟；只包络成功的 Strike+Exit 轨迹，17 个截面记录几何和状态范围 |
| 垂直窗口稀少 | witness pitch=0、vy=-.05；主要 Strike tangent 水平；四个不同 yaw 可占满结果 | 显式正负坡度 Strike 和竖向 witness；直线/左右弧，保留可行俯冲/上升家族，再按方向补齐 |
| 捕获绕圈 | nearest 与控制反馈都追初始 entry；Capture.first 未用于实际执行；失败后切到 FUNNEL_ALIGN / 普通 Glide | 当前位置/动量/yaw/pitch 比较前段截面，Worker 对每窗口最多三个截面试完整方案；执行认证首步；无解时保留捕获反馈与其他局部认证机会 |
| 下降不补能 | REPOSITION 的普通 manageEnergy 直接返回；失败 Capture 仍写 capture_coast；FUNNEL_ALIGN 也不调用补能；普通恢复还有 clearance<4/6 限制 | Funnel Capture 每次先试 coast，失败立即进入可用库存 pulse 评估，与谷底净空无关；reserve 参考下一截面。等待/拒绝不再误报 coast，也不把“无法完成攻击”误当成局部碰撞强制切 ATTACK_EXIT |
| 标记后仍等待 | acceptCommand 在地面只赋 preferred；acquire 仍受旧 retryAt 和 scan cadence；扫描可能按过去失败记录换目标 | 合法明确命令唤醒下一 tick 的 acquire，并优先保留命令目标；装备/主人/AI/着地与起飞通道认证保留 |
| Maid 可被标记 | 服务端 pick 谓词只排除 Player，没有排除 EntityMaid | normal/creative 共用射线谓词直接排除 EntityMaid 及其子类，不涉及 Debug 棒或遥控器 |

APPROACH / REPOSITION 原本已经共用 `ENTRY` 控制阶段，地图原本也已经使用固定 `TARGET_FUNNELS` session。本次没有把两者误当成尚未实现的改进。ATTACK_EXIT 原有有限行程和完整出口认证保留；日志支持的主要下降通路是出口后重新对准、地图空窗及 Capture 失败反馈的组合，而不是一个被日志明确命名的“spiral descent”状态。

## 证据的边界

旧日志每约 100 ticks 打印一次诊断，且 energy 行仅在 flightRequest 非空时输出；Capture 通路通常没有该 request。因此无法从旧日志还原每次 pulse 的全部随机寿命拒绝理由，也不能把 `async_pending` 等同于已完成的 physics veto。0.6.3 无论有无 flightRequest 都打印 energyDecision/pulseReview/cooldown，并在 DebugSnapshot 输出同样的 Capture 状态。

回归及最终产物见 [VALIDATION](VALIDATION.md) 与 [buildHistory](../buildHistory.md)。未启动 Minecraft；不能宣称已在用户原存档测得首次攻击延迟下降。应由用户复测同一山腰、静止标记和多女仆场景，观察地图连续性、捕获耗时及实际 MSPT。
