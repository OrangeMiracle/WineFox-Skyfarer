# 0.7.5：永燃 Home 女仆远程唤醒崩溃

本次以本地 0.7.4 源码为基线，只修复日志证实的赶路起飞空方案访问，以及定向回归发现的姿态交接问题。保留永燃女仆远程资格、统一来源仲裁、真实碰撞认证和既有创造高速赶路特例。

## 实机证据与边界

只读实例 `E:\rlc\PCL\.minecraft\versions\1.21.1-NeoForge_21.1.250`，检查了最新相关 `crash-2026-10-13_21.43.05-server.txt`、上一份 `21.42.04` 报告以及 `logs/latest.log`、`logs/debug.log`。两次都是服务端实体 tick 崩溃：

```text
java.lang.NullPointerException:
Cannot invoke "SurfaceLaunchPlanner$Plan.yaw()" because "this.launch" is null
TransitFlight.tickFlight(TransitFlight.java:71)
TransitFlight.tick(TransitFlight.java:35)
CommandManager.beforeMaidTick(CommandManager.java:324)
SkyfarerRuntime.tick(SkyfarerRuntime.java:33)
```

以上行号是 **0.7.4**。完整类名、时间和日志行号见 [证据摘录](../tools/remote-comet-crash-evidence.txt)。最新日志中：

| 时间 | 已发生的事实 |
| --- | --- |
| 21:43:05.314–.326 | 发现两只 unloaded 女仆，分别建立 wake 票据 |
| .388–.426 | 第一只加载完成，装备判定 READY、VIRTUAL_PROPULSION，分配 483.60 格创造赶路 |
| .440–.443 | 第二只同样识别永燃并分配 479.56 格赶路；两个 wake 均成功移交订单 |
| .816 | 地面赶路起飞代码对空 `launch` 调用 `yaw()`，服务器异常 |

日志已经证明饰品可读取、资格已恢复且订单已分配。它不支持把此异常归因于 TLM 未初始化或 Worker 直接访问实体。TLM 1.5.3 的实际依赖也确认：`maidBauble` 在构造器中创建，`readAdditionalSaveData` 从 `MaidBaubleInventory` 恢复，`getMaidBauble()` 直接返回该字段；见 [API 摘录](../tools/remote-comet-tlm-api.txt)。

实机当时为何没有可用的虚拟候选，旧日志没有足够信息。不能把测试中的天花板条件当成原存档的地形结论。可以确定的根因是：**合法的“没有认证起飞方案”返回值被调用者当作非空使用**。永燃独占推进会正确排除烟花方案，暴露了这个旧假设。

## 修复契约

- `TransitFlight` 仅对非空认证起飞方案转向、跳跃。结果为空且 `planning.waiting()` 时留在地面等待；无待决结果时返回 `NO_CERTIFIED_LAUNCH`，由订单层收束，不吞掉异常或伪造安全方案。
- 地面规划等待从首次执行赶路 tick 起按 Level 游戏时间累计，最多 100 ticks；包括 `Pending` 和 Provider 等待，超时返回 `LAUNCH_WAIT_TIMEOUT`。这是赶路规划预算，与此前订单等待初始区块票据 ready 的最多 100 ticks 是不同阶段。
- 赶路失败调用统一 `cancel`：移除订单、关闭 Provider/PlannerSession、释放滚动通道票据。已经离开 Home 范围的创造女仆沿用当前位置与 Home 区块的最多 200 tick 回收宽限，落地释放空中控制，返抵或超时撤票。保留目标标记与 200 tick 重试冷却，不永久持有失败订单。
- `TransitFlight` 遇到同 UUID 的不同 Java 实体实例，先关闭旧会话及 Provider，再捕获新实体。Level/维度变化仍由既有 `PlannerSession.begin` 失效机制处理，未放宽 generation 或证书年龄。
- `VirtualPropulsion.tick` 从跳跃切入 `FALL_FLYING` 后，重新读取真实眼位、速度、姿态和 PhysicsContext，再做当前完整认证与 live 身体检查。此前调用者在切姿态前捕获站立眼位，回归出现首个飞行 tick `SNAPSHOT_MISSING:1`、虚拟推力从未执行；创造高速特例仍可能让女仆到达，单测“到达”会漏掉此问题。现在批量回放同时要求每只女仆实际执行虚拟推力。

最后一项是离线回归发现的第二个缺陷，并非实机 NPE 堆栈直接证明的原因。没有修改真实 Elytra 方程、碰撞容差、未知空间拒绝或来源优先级。

## 定向验证

执行 `compileJava, verifyRemoteComet, verifyRemoteCometContracts, verifyRemoteWake, verifyRemotePropulsion, jar`；未运行全量 build/check/verify，未启动或安装 Minecraft。最终 [运行日志](../tools/remote-comet-release.log)。

- **先复现后修复**：新回放在未修改的生产 `TransitFlight` 上触发相同 NPE 和 `:71`，见 [失败记录](../tools/remote-comet-reproduce.log)。修复后，同一无认证起飞场景于 tick 7 安全拒绝，未跳跃、未消耗烟花、会话归零。
- **永燃批量唤醒**：5 只 Home 女仆复制饰品数据并重建同 UUID 新实例，每批 2 只，实际 Worker 全部阻塞 16 ticks；约 480 格创造赶路均到达并保留滑翔交接。永燃与普通同时装备，普通进度保留，每只实际虚拟推力大于零，烟花消耗、节能 RNG、火箭实体均为零，每步检查身体碰撞，会话最终归零。
- **取消/延迟**：pending 中替换实体关闭旧会话；重复 close 安全；阻塞所有 Worker 超过 100 游戏 ticks 后退出等待、无未认证跳跃、无耗材降级、无遗留 PlannerSession。
- **真实依赖契约**：实际 TLM `BaubleItemHandler` 的序列化、反序列化、饰品绑定缓存、槽 0/17 与进度独立性通过。测试使用已注册的原版物品代替 Mod 注册 ID，并只初始化所需 TLM 绑定，不模拟完整 Mod 启动。ASM 检查生产失败分支确实走 cancel、close、release/有限 recall，以及登出/离开/停服清理路径。
- **原远程路径**：既有 wake 检查覆盖双 Home/第二任务、13 候选公平分批、重试、generation、注册表 NBT、全服上限与清理；未装备永燃的正常/创造激流 Home 女仆均完成赶路，干燥环境仍拒绝资格。

[directed](../tools/remote-comet-directed.log) 与 [trace](../tools/remote-comet-trace.log) 保留了首个虚拟推力缺失的中间失败；[handoff](../tools/remote-comet-handoff.log) 记录姿态修复后的通过。最终交付以 release 日志为准。

**验证边界**：回放运行真实 TransitFlight、统一 Provider、PlannerSession、Worker 和 RemoteWakeScheduler；世界、实体重建、tick 与票据后端为夹具。清理调用链还有生产字节码检查，但没有实际 NeoForge `forceChunk`/磁盘区块 IO、完整事件总线或整合包中的端到端执行。用户仍需按原步骤复测单只/多只卸载 Home 女仆、取消标记/登出后的回收，以及低 TPS 或复杂起飞地形。

## 排查新增日志

打开 `/wfs debuglog on` 后，`TRANSIT_LAUNCH_REJECTED` 记录 maid、`NO_CERTIFIED_LAUNCH`/`LAUNCH_WAIT_TIMEOUT`、Resolver 原因和规划状态。随后应看到 `COMMAND_RELEASE` 与撤票，或有限 `COMMAND_HOME_RETURN` 回收。`REMOTE_WAKE released reason=transferred_to_order` 只表示唤醒槽移交，不代表订单票据已经撤销。
