# 0.7.6：Home 回收后的重复远程任务

基于当前本地 0.7.5 修改；没有回滚此前成果。先读取实机 latest/debug 日志，再沿实际接单、Home 回收和赶路源码定位。此次不是一次成功任务永久耗尽了 wake 资格。

## 证据：第二次已经醒来，失败在接单后的起飞

只读实例 `E:\rlc\PCL\.minecraft\versions\1.21.1-NeoForge_21.1.250\logs`，最新会话于 2026-10-13 22:11:55 结束。两份日志对应摘录见 [remote-recall-evidence.txt](../tools/remote-recall-evidence.txt)。以下是 latest.log 的原始行号：

| 时间 / 行号 | 事实 |
| --- | --- |
| 22:10:18，635–657 | 两只 unloaded 女仆分批获得票据、加载并接单；均为 VIRTUAL_PROPULSION |
| 22:10:27–28，749–770 | `e45d295c…` 完成任务，订单/控制释放，TLM Home 回收成功，票据释放并发生 ENTITY_LEAVE |
| 22:10:51，775–798 | 新 generation=3，两个候选 attempted=0；重新申请票据、entity_loaded、COMMAND_ASSIGN 和 assignment_accepted 全部成功 |
| 22:10:51，785/793、788/796 | 两只均为 `ground=false glide=false`，却在 `transit=true` 接单之后立即 `GROUNDED → APPROACH` |
| 22:10:51，799–825 | 原版 travel 清除落地滑翔；两份订单迅速关闭并撤票，回到 Home 一带卸载 |
| 22:10:51 之后，827 起 | 明确 `transit_retry`；generation 持续变化，冷却结束后仍可再次接单 |
| 22:11:12，922–928；22:11:21，1029 | 同一 `e45d295c…` 后续再次接单且到达交接范围，证明它并未被永久移出候选集 |

因此，registry、票据重新申请、attempted 与 generation 在这次日志中都已经支持多次发现/加载/分配。`has_order` 仅表示当时已有订单，`transit_retry` 是起飞失败后的 200 tick 冷却。另一只 `57d5cb3e…` 多次有 `NO_SAFE_VIRTUAL_PROPULSION`，旧日志不足以定位每条认证失败的具体地形原因，不能把所有安全拒绝都视为同一个调度故障。

## 实际缺陷与修复

1. **接单控制权分支错误。** `CommandManager.assign` 旧代码把 `order.transit && controller != null` 合为一个 if。当重载实体没有已有控制器时，赶路订单落入 else 的 `ensure().acceptCommand(target)`；后者看到 `!onGround` 就会启用滑翔并进入 APPROACH，与日志严格吻合。现在先判断订单是否赶路：赶路只关闭已有战斗控制器，绝不构造/启动新的战斗控制；进入交接范围后才 `acceptCommand`。
2. **重载时未着地不等于适合开启滑翔。** 日志记录 Home 回收后仍有向下和水平速度。实际 TLM `SchedulePos → TeleportHelper → LivingEntity.randomTeleport → Entity.teleportTo/moveTo` 不负责把地面接触和速度重置成静止状态；Entity NBT 也保存/恢复 `OnGround` 与 Motion，见 [实际依赖摘录](../tools/remote-recall-native-api.txt)。Transit 现在对未滑翔、向下、脚下 0.5 格内有实际支撑、且无自身起飞方案的实体，短暂交回原版 standing travel 处理接触，最多 20 游戏 ticks。之后仍未接触则 `GROUND_CONTACT_TIMEOUT`。不设置 OnGround、不瞬移、不冻结速度、不把未知空间当支撑；真正离地的重载实体仍可立即进入滑翔。
3. **赶路起飞的范围语义遗漏。** 三轮回归的第二轮发现 `ANCHOR_REJECTED`：虚拟 TAKEOFF 没收到 Transit 上下文，以数百格外的战斗目标为圆心校验本地起飞转向，而激流已使用本地赶路窗口。`FlightPropulsion.context` 现在同时传给两个 Provider；虚拟远程起飞采用同一当前位置半径 128 的局部范围。仍保留完整预测、身体 swept-volume、CollisionSnapshot、当前步检查和永燃独占规则。这是回归发现的独立缺陷，不声称旧实机日志已证明这个具体拒绝。
4. **回收位置的及时记录。** Home 回收成功、撤掉最后票据之前立即 observe 当前实体的维度/坐标/Home/工作事实，避免依赖下一次周期采样。原 EntityJoin、20 tick 观察及卸载记录均保留。现有日志并未证明 registry 数据丢失，此项收紧的是撤票前交接顺序。

Wake Scheduler 和票据上限没有修改：每玩家每 10 ticks 最多 2 次、全服最多 8 个 wake、100 tick 超时、200 tick 重试；Home 回收仍为最多 200 ticks 的有限宽限。没有持久化 attempted，也没有永久强加载。

## 新诊断

仅在 debuglog 开启时输出：

- `REMOTE_WAKE stage=ticket_location`：本次唤醒实际使用的维度/位置，可直接核对是否指向 Home。
- `REMOTE_WAKE stage=assignment_rejected`：资格、承诺攻击或没有可接标记的拒绝；同一会话内原因变化才输出，generation 更新清空诊断去重，不再依赖碰巧命中 `%40` 的 tick。
- `COMMAND_TRANSIT_FAILED`：在关闭控制器前输出 generation、目标、位置、速度、ground/glide、retryAt 和 flight/provider/proof 详情。区分 `JUMP_BODY_BLOCKED`、`GLIDE_REJECTED`、`LAUNCH_RECERTIFICATION_FAILED`、`LAUNCH_PROVIDER_REJECTED`、`NO_SAFE_GLIDE`、水中失败与等待超时，避免只剩泛化的 ORDER_CANCELLED。
- `COMMAND_HOME_RETURN`：新增当前 pos/dimension；与后续 ticket_location 对照。

`TRANSIT_LAUNCH_REJECTED` 原有日志保留。`REMOTE_WAKE released reason=transferred_to_order` 仍仅表示 wake 移交，真正撤票看 `COMMAND_TICKET stage=released`。

## 验证与限制

最终 [remote-recall-release.log](../tools/remote-recall-release.log)：compileJava、verifyRemoteRecall、verifyRemoteCometContracts、verifyRemoteWake、verifyRemoteComet、verifyRemotePropulsion、jar 全部通过；无全量 build/check/verify，无 Minecraft 启动/安装。

- **三次连续任务**：同三只 UUID（永燃、激流、普通烟花）各完成三轮约 480 格赶路、滑翔交接、Home 位置恢复与卸载；每轮新实体、保留饰品/三叉戟数据，均从日志中的 false-ground 和残余下落/水平速度出发。第二轮阻塞全部真实 Worker 16 ticks。永燃确实输出推力且不生成火箭，激流实际释放，赶路无库存消耗；飞行逐步检查身体碰撞，会话/适配器票据/订单均清空。
- **等待边界**：故意不执行原版接触更新时，在 20 ticks 到期后安全退出；真正离地 12 格的实体不被等待落地逻辑冻结。
- **实际编译分支**：符号执行生产 CommandManager.assign 尾部分支的四种 transit/controller-present 组合，验证赶路从不 ensure/accept 战斗、本地作战仍接受命令、已有控制只交接一次。不是在测试里重写一份选择规则。
- **持久化/调度**：实际 TLM handler NBT、两只 Home 三次任务、战斗位置→Home 位置→SavedData 往返、13 候选公平调度、generation、超时重试、全服上限与清理通过。生产字节码检查 Home 交接观察及既有取消/登出/离开/停服链路。
- **之前修复回归**：五只永燃分批唤醒、100 tick Worker 阻塞退出、无解起飞安全拒绝、同 UUID 重建关闭旧 session、无永燃的普通/创造激流赶路均通过。

中间失败保留：[reproduce](../tools/remote-recall-reproduce.log) 是新测试夹具火箭字段名称写错；[red](../tools/remote-recall-red.log) 在未修改的 Transit 上复现“脚下有支撑却提前滑翔”；[directed](../tools/remote-recall-directed.log) 第一轮通过、第二轮 ANCHOR_REJECTED；[context](../tools/remote-recall-context.log) 修复上下文后通过。最终以 release 为准。

离线回放运行真实调度器、Transit、Provider、Worker 和物理预测；世界、native travel 接触、TLM Home 传送、实体 IO 与票据后端是适配器，不是启动整合包的端到端测试。此次没有执行攻击伤害回放，战斗完成作为 Home 回收的外部边界。实际区块写回、Mixin 应用、复杂 Home 地形及其他 Mod 干预仍需用户复测，不能以“无限次都必然起飞”覆盖真实安全拒绝。
