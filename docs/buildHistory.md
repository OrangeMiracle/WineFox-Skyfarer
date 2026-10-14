# Build History

## 当前交付状态

当前工程为 **0.7.6**，基于本地 0.7.5 修复 Home 回收后重复远程任务的接单/起飞交接。仅编译、五项定向验证和打包，未运行全量 build/check/verify，未启动或安装 Minecraft。

## 0.7.6 — 名称与封面更新（2026-10-14）

- 中文名改为「车万女仆: 骑空士」，英文名改为「Touhou Little Maid: Skyfarer」。NeoForge 列表使用固定英文名，介绍含中文名，资源包名称提供中英文翻译。
- 发行包改为 `touhou_little_maid_skyfarer-1.21.1-0.7.6.jar`；保留原 Mod ID、注册 ID 和版本以兼容存档。
- 原样收录用户提供的 `Mod封面.png` 为 `mod_logo.png`，通过 `logoFile` 接入模组列表。
- 本轮仅编译与 Jar 资源检查；不运行全量验证，不启动 Minecraft。

## 0.7.6 — 重复远程接单与 Home 重载接触（2026-10-13）

- 最新日志实际显示第二次 generation=3 已成功发现、申请票据、加载并接单，随后取消赶路进入 transit_retry；不是 attempted 永久锁定或永燃资格丢失。首次完成 Home 回收及后续接单的完整证据见 [专题](REMOTE_RECALL_076.md)。
- 修正 `order.transit && controller != null` 的 else 误入战斗：重载后无旧控制器的远程订单不再提前 acceptCommand/开启 APPROACH 滑翔。真正交接时仍正常进入战斗。
- 针对日志中的 false-ground、未滑翔及残余速度，允许脚下有真实支撑的重载实体先由原版 travel 接触地面，最多 20 ticks。没有改写 OnGround、位置或惯性。虚拟起飞同步接收 Transit 上下文，与激流采用相同的局部 128 格范围；修复三轮回放中的 ANCHOR_REJECTED，仍严格认证物理和身体碰撞。
- Home 到达撤票前即时 observe 位置/维度。新增唤醒位置、接单拒绝原因去重和完整 COMMAND_TRANSIT_FAILED 诊断；已有 Wake 2/批、8/全服、超时/重试、公平性和有限 Home 票据均保留。
- compileJava、RemoteRecall、RemoteCometContracts、RemoteWake、RemoteComet、RemotePropulsion、jar 全部通过。三种装备的同三只 UUID 连续三次任务、Worker 阻塞、NBT、控制权及取消/释放检查通过。实际区块 IO、整合包 Mixin/传送和完整攻击伤害需实机确认；见 [验证](VALIDATION.md)。中间失败日志保留。

交付包：[winefox_skyfarer-1.21.1-0.7.6.jar](../build/libs/winefox_skyfarer-1.21.1-0.7.6.jar)，**664,570 bytes**；SHA-256 `A77628E2C954DAEFB75735C6B72D1F78DB78C1DA3E75855DEF115461032F4BF2`。版本与资源、四处生产类及测试隔离已核对；[包校验](../tools/remote-recall-artifact-check.json)。

## 0.7.5 — 远程赶路起飞与重载生命周期（2026-10-13）

- 先只读最新两份 crash report、latest/debug 日志，确认实际异常为 `NullPointerException`，`TransitFlight.tickFlight:71` 对空 `SurfaceLaunchPlanner.Plan` 调用 `yaw()`。日志中女仆已经加载、永燃已识别、订单已分配；不是据描述猜测 TLM 饰品初始化失败。
- 用原生产代码离线复现相同堆栈后，修正空起飞结果的 pending/拒绝分流；连续地面规划等待最多 100 游戏 ticks，包含 Worker 停顿。失败走统一订单 cancel，关闭会话和 Provider、释放滚动票据或进入既有有限 Home 回收宽限，保留 200 tick 重试。
- 同 UUID 新实体不复用旧 TransitFlight Source/Provider；虚拟跳跃切滑翔后重新捕获眼位、速度与姿态，再严格认证首个推力。后者由“已经到达但实际虚拟推力为零”的定向回归发现，不能冒充实机 NPE 的直接堆栈原因。
- `compileJava`、RemoteComet、RemoteCometContracts、RemoteWake、RemotePropulsion、`jar` 通过。5 只重建 Home 女仆、每批 2 只、实际 Worker 16 tick 停顿与约 480 格赶路、永燃实际供力/零耗材、取消/替换/超时均通过；TLM 原生 handler NBT 与无永燃的激流路径通过。
- 未改变来源优先级、Elytra 物理、碰撞检查或证书有效期，未禁止永燃参与远程命令。真实区块 IO/整合包事件与召回仍需实机；详见 [修复说明](REMOTE_WAKE_075.md) 和 [验证](VALIDATION.md)。中间失败记录保留。

交付包：[winefox_skyfarer-1.21.1-0.7.5.jar](../build/libs/winefox_skyfarer-1.21.1-0.7.5.jar)，**663,334 bytes**；SHA-256 `D0ABB8B5432A900FAD9809131DBFF37CBDC561BFD38091E34146940FE37A269D`。版本元数据与 Manifest 均为 0.7.5，包含三处生产修复，JSON 资源可解析，不含回放/外部 API 夹具；[包校验](../tools/remote-comet-artifact-check.json)。

## 0.7.4 — 彗星 Fullbright 与圣物掉落保护（2026-10-13）

- 用专属 Fullbright 粒子替换受环境光影响的小型 dust：双赤尾迹逐 tick 按实际位移补点、推力增强尺寸与寿命；白色 32 段完整圆环由单粒子绘制，12 ticks 扩散淡出、双面可见。复用原版图集与透明粒子批次，无动态光源/额外实体。
- 显示范围改为 96 格、保留最少设置的简化效果；至多 128 活动发送者，96 尾粒子 + 2 个圆环/客户端 tick，专属活粒子上限 2304。首包允许等待实体、环有短暂有界排队，UUID 隔离，停止/换世界/过期清理。修复输出从高峰降低后再次增推不能出环的门控问题，补充五秒聚合 tx/rx/创建/剔除诊断。
- 统一 CometRelicDrops 保护两种饰品 ItemEntity 的雷电、火焰/岩浆及爆炸伤害；物品默认 FIRE_RESISTANT。保留原版拾取、保存、进度、正常年龄行为，不采用全局无敌。
- 服务端原生 Glowing，普通暖金、永燃鲜红；客户端仅指定物品使用颜色覆盖，原版 outline buffer 实现穿墙高亮，不污染 scoreboard。标记存实体数据，重掉落/加载恢复，外部更换 ItemStack 恢复原 glow。
- 原雷击 HEAD 原子仪式与 TNT 余料 120 tick 保护保留，击杀进化不改；永燃推进物理、来源仲裁、Worker/安全证书均未改动。
- 编译与 CometRadiance、CometDrops、CometData、Architecture 定向验证通过；真实粒子渲染方法输出 Fullbright 顶点、环方向/扩散/寿命和预算检查通过，保护/仪式/数据回归通过。证据、限制和最终包见 [VALIDATION](VALIDATION.md)，实现细节见 [COMET_RADIANCE_074](COMET_RADIANCE_074.md)。实际夜间外观、客户端网络/模型兼容、区块重载与多人性能仍需用户实机确认。

交付包：`build/libs/winefox_skyfarer-1.21.1-0.7.4.jar`，662,808 bytes；SHA-256 `E7CA2EBA62B9C66EA7929DAF0FD075389F4E428E334B4B1997B07F555A8E30BC`。新类、Mixin、粒子 JSON 与版本元数据已核对入包，未夹带测试代码。

## 0.7.3 交付状态（历史）

当前工程为 **0.7.3**，基于本地 0.7.2 成果继续开发，没有覆盖或回滚本地改造。按本次要求仅编译、直接相关定向验证、少量生产控制器离线回放和打包；没有运行全量 build/check、启动 Minecraft 或安装到测试实例。旧版完整验证仅保留为历史证据。

### 0.7.3 动力来源权威、持续速度保持与赤色尾焰（2026-10-13）

- **实机根因**：latest.log 中永燃装备资格与 `reason=boosted attached=true` 同时出现；虚拟候选缺少快照/失败后原仲裁继续尝试耗材，三个执行入口还直接点火。只读 [证据摘录](../tools/propulsion-authority-evidence.txt) 与源码相互印证，旧 source 标签表示装备资格而非实际执行。
- **统一决策**：新增 PropulsionResolver 来源规则与 Provider 契约，所有需求/起飞/Rejoin/Emergency 经过统一来源选择、认证和执行。永燃为独占最高来源，任何失败或等待超时都不能降级烧烟花/使用激流；无永燃时保持合法且安全的 READY 激流先于烟花。普通之心仅保留一次真实烟花节能结算。资源提交处复核同一政策，包括生成事件回调中换装；来源变化取消旧执行并使异步/轨迹证明失效。
- **持续控制**：在现有有限推力认证器上增加共享比例速度反馈和爬升/阻力前馈，正常 12 tick 动力窗口 + 12 tick 无动力恢复、攻击 1 tick 动力 + 完整 Strike→Exit；仅执行已认证的下一 tick。最高加速度 0.2、加力速度界 2.4，保持真实惯性、原版 Elytra 和身体扫掠。有限虚拟加速结束即可恢复导航与供力，不再强制熄火等待；无动力余量仍认证。没有扩大 Worker、采集预算或证书寿命。
- **专属视觉**：实际虚拟冲量触发服务端限频状态包；客户端产生对称双赤尾流和沿推力方向向外扩散的白环。稳定同步每 4 ticks、环最短间隔 20 ticks、48 格裁剪、128 实体缓存与全客户端粒子预算，尊重粒子设置；原烟花/激流视觉未改。common 不链接客户端渲染，协议升为 4。诊断区分执行 source 与装备 eligible。
- **定向验证**：最终 [propulsion-authority-release.log](../tools/propulsion-authority-release.log) 编译、Authority、Architecture、jar 成功；700 tick 平飞/转弯/缓升速度 1.2416–1.2507，高度误差 ≤0.2406，预测与执行一致；实际单 Provider 219/220 ticks 供力，八女仆各 97–98/100 ticks 供力，保持现有预算与零耗材。新增墙体 + 阻塞 Worker 拒绝推力，特效几何/去重/限频通过，普通节能 1,000 次实际发射/600 次节能与激流优先回归通过。
- **控制器回放**：[replay2](../tools/propulsion-authority-replay2.log) 的全装备正常/16 tick 停顿场景分别 tick 134/130 命中并安全离场，全程零烟花、零耐久、无碰撞；另 [持续压力](../tools/propulsion-authority-final-checks.log) 保持 5 ms/tick 并停顿 Worker 16 ticks，在 tick 217 命中安全离场。没有把普通时序通过代替压力验证；历史失败保留。一次新回放夹具重载签名编译失败已修复，日志未删除。
- **文档与交付**：同步 README、索引、飞控、异步、物理、配置和 [仲裁专题](PROPULSION_073.md)、[验证记录](VALIDATION.md)。[winefox_skyfarer-1.21.1-0.7.3.jar](../build/libs/winefox_skyfarer-1.21.1-0.7.3.jar)，**652,971 bytes**，SHA-256 `F01EC0D3E9213195B1D16F5F34D9D48ED1E903316949E64AEAC2BE2D9E5538A2`；[校验](../tools/propulsion-authority-artifact-check.json)。未全量 verify/build/check，未启动或安装 Minecraft；模型位置、实际粒子画面和 TPS/FPS 仍需实机验证。

### 0.7.2 彗星饰品、有限虚拟推进与地面觉醒（2026-10-13）

- **原生饰品**：核对实际 TLM 1.5.3 API，通过 IMaidBauble / BaubleManager 注册两件饰品，只从女仆原生饰品栏识别。普通之心在实际烟花生成与库存提交成功后独立判定节能，默认 60%，可配置 50%–70%；预测不抽随机数，原激流优先级和物理认证保留。NeoForge additive GLM 按三种宝箱各自概率注入普通之心；永燃版不自然生成。
- **独立推进源**：增加 VIRTUAL_PROPULSION，复用统一需求/提供器、原版 Elytra、不可变异步快照与完整 Strike→Exit。有限低/中/高推力、每 tick 限幅，停止保留惯性；无需火箭实体、耗材或耐久。已有推进控制权优先，实时障碍/安全转向/卸装可抢占。选中计划缺失 cells 的局部预取与一次重验仍受既有全局/局部预算限制，未延长证明或放宽碰撞。
- **持久觉醒与奖励**：方式 A 以真实死亡及伤害来源确认击杀，进度随 ItemStack 数据转移和存档，受害者去重、原槽单件替换。隐藏「银翼的继承者」使用鞘翅图标，资格写入 overworld SavedData，离线后补发，完成后不重复授予。
- **方式 B 已按最新要求修正**：仪式只检测真实闪电附近的地面掉落物，无需女仆/佩戴。核对原版先生火后逐实体雷击的顺序，在非视觉闪电首次 tick HEAD、服务端主线程验证并转换。多个材料堆叠精确付款、余料保留；原之心实体直接换栈，避免生成否决造成半事务。一次雷击只尝试一次，产物与当地剩余材料限时防雷/火/爆炸并禁合堆；不额外破坏地形。成就归属为引雷玩家优先，其次可由服务器玩家档案确认的之心投掷者（包括离线）；不能确认时不奖励旁观者。原方式 A 保持不变。
- **资源与文案**：依据参考图制作两张 32×32 RGBA 物品纹理、item/generated 模型和中英文名称/含蓄 Tooltip；玩家 README 不公开隐藏仪式条件。同步索引、配置、飞控、物理、异步及 [饰品专题](COMET_HEARTS.md)，保存 [图像生成提示词](COMET_TEXTURE_PROMPTS.md)。
- **定向验证**：[comet-contracts-final.log](../tools/comet-contracts-final.log) 验证饰品、节能、物理/控制、多女仆预算及架构；[comet-flight4.log](../tools/comet-flight4.log) 两个无燃料生产控制器场景命中且安全离场，包含实际 Worker 16 tick 停顿恢复，并通过激流释放回归。真实数据 codec 与依赖契约通过。B 修正后的 [comet-ritual-correction.log](../tools/comet-ritual-correction.log) 最终编译、Ritual、Data、jar 成功，含 A 回归、材料事务、限时保护和归属测试；未全量运行。
- **限制与失败保留**：初始虚拟推进命中后碰撞推动上述选中 cells 修复；持续 5 ms/tick 调度压力仍曾 500 ticks 无命中（无碰撞），不以恢复正常节奏后的通过覆盖。真实 TLM/NeoForge/Mixin 的雷击/生火/拾取/爆炸顺序、Spears 原生伤害、成就弹窗与多女仆性能仍待实机确认。完整证据和中间失败见 [验证文档](VALIDATION.md)。
- **交付**：[winefox_skyfarer-1.21.1-0.7.2.jar](../build/libs/winefox_skyfarer-1.21.1-0.7.2.jar)，**621,031 bytes**，SHA-256 `2B2DE212B2FA25AEDDF83881A287A52782B81D14DF2AA5388CB2B356548738DB`。元数据、资源和仪式 Mixins 齐全，无测试夹具；[校验记录](../tools/comet-artifact-check.json)。

### 0.7.1 航迹承诺、控制仲裁与延迟安全（2026-10-08）

- **实机证据**：只读检查用户提供实例的 latest.log（5,213 行），确认黄色通道是已有动力学 trace，不天然等于攻击证书。08:51:06.934 的路线预计第 90 tick 接触，下一 tick 却以 `hit=false traceTicks=81` 退出。源码存在候选/Rejoin 120 tick 与 follow 80 tick 不一致；固定第 93 tick 接触反例证明差异。统一执行预算，160 tick 上限、身体/接触/完整离场条件不变。[摘录](tools/trajectory-log-evidence.txt)。
- **执行承诺**：新增 TrajectoryCommitment，把路径身份与异步 job、有限 Proof 分开。最多 6 tick 原时间戳后缀必须匹配真实 State/Aim、物理、推进倒计时与阶段；超龄/偏离时只重验选中路径，最多 160 步、≤20 tick cells，不访问世界或搜索新候选。删除空 trace 的 `safe=true` 等待占位。真实碰撞抢占立即撤销旧证明，目标/配置/物理/阶段变化仍要求重新认证。
- **控制权**：HeadingController 明确 `SAFETY > PROPULSION > ROUTE`；活动激流不再落入烟花后备，开始 guided 后同 tick 的普通攻击/结束判断和 terrain 控制不覆盖它。推进结束继续原阶段与 Compact Goal，删除无条件 invalidate/startApproach。重定位等待继续固定目标反馈；Riptide 与 Firework Turn 共用同一 Goal/rate，保留各自实际推力与寿命模型。
- **主动换路**：有效攻击期间可选改进每 16 tick 请求，pending 不撤销旧路线。新旧路线均从当前状态完整复核，12 tick 最短承诺窗口、接触剩余窗口、成本优势与姿态切换成本共同决定是否替换。换路不重置原生蓄力会话、攻击计时、目标或命中历史。
- **异步延迟**：修复 Search/Rejoin 包装导致选中通道未优先采集；几何提案/seed/在途工作窗口统一为 24 ticks，执行前仍需当前完整认证。选中路径与激流所用 cells 达 8 tick 请求刷新，普通背景仍 20 tick；原采集、队列和线程预算不变。安全 rollout 不再因仅用于宽敞度评分的外层缺失样本被错误丢弃，硬碰撞检查未放宽。
- **安全反例驱动**：新增“攻击途中动态墙体 + 实际 Worker 全部阻塞 12 ticks”最初仍发生碰撞，保留 [失败日志](tools/trajectory-preempt-1.log)。修复为最多一个旧姿态与六个新姿态、每个 12 tick 的实时身体扫掠等待（最多 84 步）；不授权点火，不冒充完整逃逸证书，保留最后当前步防线。早转向可使冷启动由烟花脱险变为滑翔脱险，测试检查完整证书/50 tick 无碰撞/末态离墙/真实消耗；独立数值检查继续保留 coast 无解而完整烟花可救的反例。
- **验证**：[trajectory-directed.log](tools/trajectory-directed.log) 的 Continuity、EmergencyReplay、StabilityLaunch、Architecture、RiptideDuration 通过；[trajectory-package.log](tools/trajectory-package.log) 的历史 5 ms/tick 连续双目标压力完成 2 命中、安全 Exit、5 次激流释放、0 烟花；[trajectory-release.log](tools/trajectory-release.log) 最终 compileJava、Continuity、AsyncPlanning、jar 成功。最终四例新增控制器回放：静止第 27 tick 命中、移动第 30 tick 命中；Worker 停顿例第 29 tick 命中且安全离场；动态墙体例安全抢占并飞到第 45 tick。均无硬身体碰撞。证明寿命/状态变化拒绝、收益门槛、控制权抢占、Compact 推进逐步一致性和异步生命周期也通过；未全量测试。
- **观测与文档**：HUD/日志增加预测来源、控制 owner、路径 id、证明年龄、换路次数和明确拒因，区分候选过期、证书不可用和安全抢占。更新 README、根/docs 索引、飞控、异步、物理、调试、验证，并新增 [航迹连续性专题](docs/TRAJECTORY_CONTINUITY.md)；无新配置。
- **交付**：[winefox_skyfarer-1.21.1-0.7.1.jar](build/libs/winefox_skyfarer-1.21.1-0.7.1.jar)，**583,748 bytes**，SHA-256 `6A2910FB5323D27876E73AB30F976A881FA7A105453C5D635C5531F5C544838E`。Manifest 与 Mod 元数据均为 0.7.1，含新承诺类，不含离线夹具或依赖替身；[校验记录](tools/trajectory-artifact-check.json)。
- **边界**：历史失败及本次调查失败都保留，普通时序通过没有替代 Worker 延迟验证。原生 Spears/Mixin/TLM 时序、目标急转、实际更优路线切换、动态世界、多女仆 MSPT 仍需实机确认；不宣称无限延迟或任意地形都安全，也没有实机命中率/性能提升数据。未启动或自动替换 Minecraft。

### 0.7.0 推进边界收束与架构清理（2026-10-08）

- **统一边界**：AirCombatController / TransitFlight 以 PropulsionDemand + PropulsionRoute 调用 FlightPropulsion，资格由 PropulsionCapability 统一。上层不再持有具体推进执行器、遍历火箭时长或提交火箭专属评分闭包；合法 READY 激流先于新的烟花。Riptide 的环境/装备/耐久/等级/时长/释放/spin，Firework 的库存/随机寿命/点火延迟/活动实体/冷却/爆炸属性继续各守其职。显式运输免费火箭政策保留在提供器内，不扩散到战斗。
- **清理**：删除无生产调用的 LaunchPlanner、旧坐标 RiptidePlanner choose 重载、无证书 FireworkManager boost 重载、重复 PropulsionAvailability、Transit 独立火箭实现和无消费者 lastRequest、不可达 BREAKAWAY/ABORT/EMERGENCY 枚举及分支、无效包装器/评分转发、常量 previous_bonus 调试字段。逐项依据见 [架构审计](docs/ARCHITECTURE_070.md)，仍被生产读取的规划器/兼容钩子不删除。
- **配置迁移**：删除 `previous_target_bonus`、`climb_height`、`forward_collision_check_distance`；旧值不再读取，也不继续出现在 `/wfs config`，NeoForge 可按新 Spec 校正旧键，无需映射到其他参数。保留的 52 个数值参数均有生产消费者，见 [清单](docs/config-consumers-070.tsv)。
- **性能/线程**：提供器按当前 tick 复用候选时长、执行重新验证库存；复用 Exit 临时映射，避免重复 Level WeakReference；移除 Transit 重复请求。保持快照/有界 worker/mailbox/主线程重验，未扩大全服预算。新增 verifyArchitecture 检查编译后上层（含 lambda）依赖与 worker DTO 边界，不宣称未经 profiling 的性能提升。
- **全量回放发现并修复**：曲线 coast 尚 pending 或完整命中/Exit 已可行时，不因名义低速误点火扰乱航线；最终下降通过有限身体扫掠、落点支撑/站立净空认证，预定触地不再被误判成必须点火逃离的墙。紧急方案保留完整寿命/逃逸证书，初始 8 tick 脱险后交回当前恢复决策，避免长期独占控制而向外飞或耗尽速度；已返航且推进无证书时允许认证就近降落。
- **遮挡回归**：上坡离场后，临时不可见目标曾被误判为战斗结束。仅对已观察、仍合法目标保留最后可见位置，在既有 loiter_time_ticks 内寻找视线；隐藏目标仍不能进入攻击，超时/死亡/返航/卸载清除。未引入新配置。
- **测试维护**：Controller 回放聚合全部 21 场失败并逐例释放会话；Launch Attack 可以跳过 CLIMB，生命周期据真实 TAKEOFF→ATTACK_RUN 验证。更新已过时的坡面样本数、燃料恢复、READY API 与旧激流 choose 测试，未放松碰撞、命中、落地或资源释放断言。调查失败保留在 tools/v070-*.log 等日志中，只有最终全量结果作为发布证据。
- **新旧回归冲突**：保留 AirRecoveryHome 已验证的未触地软返航恢复能力；旧 Controller 无条件禁止 LANDING 复战的断言改为识别仍未完成且重新看到合法目标的情形。任务已完成/取消、硬返航和实际触地仍约束释放；高度限额按作战地形计算，仍为 55 格，修正落点夹具的向下搜索范围。
- **完整验证**：[v070-release-all.log](tools/v070-release-all.log) 的 `BUILD SUCCESSFUL in 6m 46s`；完整编译/build/check，43/43 verify 全执行通过（49 个任务，45 执行、4 已是最新）。Controller 21/21、Command/Transit 7/7、五类移动目标、60 个起飞寿命场景、18,000 步 Vanilla 差分、Riptide/Firework/统一推进、竖向/紧急/Strike→Exit、remote wake/Home、装备/地面/生命周期/调试/兼容全通过。未带场景筛选，未跳过失败测试。
- **产物**：[winefox_skyfarer-1.21.1-0.7.0.jar](build/libs/winefox_skyfarer-1.21.1-0.7.0.jar)，567,644 bytes，SHA-256 `157B2457B289ED9F02D67C3BBAAFF851FD53335FFBBDE7162DD2928DD884313A`。全部当前版本元数据为 0.7.0，Jar 不含删除类或离线夹具；[校验记录](tools/v070-artifact-check.json)。README、根与 docs PROJECT_INDEX、推进/飞控/异步/配置/验证文档均同步。
- **实机边界**：验证使用真实生产规划/控制器和原版物理差分，外部 Entity/Level、伤害/动画/事件采用离线夹具；真实 TLM/NeoForge/Mixin 时序、复杂动态世界、第三方 travel、多人 MSPT/GC 仍需实机确认。历史 5 ms/tick 加速压力失败不被本轮标准时序回放抹除。未启动 Minecraft、未自动替换测试实例 Jar。

### 0.6.2.11 统一推进能力与独立 Riptide READY（2026-10-08）

- **审查结果**：资格已支持激流，但 AttackLane fuel、曲线 Strike/Exit、紧急恢复、部分预热和 Transit 仍有火箭专属入口；实体 OFF_HAND use session 阻断主手 Spears。完整审计矩阵见 [统一推进说明](docs/PROPULSION_06211.md)。
- **结构**：PropulsionCapability 统一实时能力、预热与优先选择；PropulsionDemand 携带阶段/高度/速度/曲线/Aim/接触时限；唯一 PlanningTask.Propulsion 使用不可变数值快照。删除旧 PlanningTask.Riptide 和旧 AsyncPlanning 激流入口。相同需求先评估 coast，再选择可执行 Riptide、后备 Firework；火箭全随机寿命/点火延迟/cooldown 保持独立硬约束。
- **READY**：确认 1.21.1 TridentItem 原版 10 tick 门槛及 Player 限定。WineFox 用独立的 10 tick 资源预备/保持/消费/再充能，失雨、三叉戟/装备/Level 变化或释放控制时清除；显式 Transit/Combat 交接共享。没有永久 startUsingItem，也不会停止主手长矛。实际释放通过 NeoForge stop 事件与副手 ItemStack.releaseUsing 的同步授权回调，消费耐久、生成 spin/推力/声音；这是明确的 WineFox 生命周期扩展，非伪称原版实体蓄力。
- **飞行阶段**：可控激流覆盖起飞、接近、重新定位、完整攻击/离场、空中恢复、紧急逃逸与 Transit；最多 12 个当前动量拦截种子可用激流资助，继续验证真实接触/前摇时限和 Exit。推进中更新移动 Aim，原生命中后转离场。创造高速赶路与原免费火箭后备契约保留，选中激流时消耗实际三叉戟耐久；不将运输特权带入战斗。
- **修复接入问题**：新增任务明确捕获起飞滑翔身体尺寸；确定拒绝立即允许后备，避免 Transit 每次重开等待而饿死起飞；候选 pending 时继续曲线转向，不把 pending 当 coast 失败，也不使用遗漏推力的 coast TTC 去取消已经认证的激流。
- **验证**：[propulsion-acceptance.log](tools/propulsion-acceptance.log) 中 compileJava、10 项定向 verify（UnifiedPropulsion、RiptideRelease、RiptidePriority、RiptideDeparture、RiptideDuration、RemotePropulsion、AirRecoveryHome、MovingAttack、LaunchAttackReplay、EquipmentLifecycle）与 jar 成功。另 [propulsion-review-checks.log](tools/propulsion-review-checks.log) 的 AsyncPlanning、EmergencyReplay、HandoffStability 通过，共 13 项相关 verify，没有执行全量 42 项或 build/check。
- **专项结果**：6/10/32/60 格雨中起飞完整完成，干燥/释放否决均有烟花回退；普通/创造 Home 无库存烟花均实际释放激流、到达并保持滑翔交接。墙前纯滑翔无解时，7 tick 激流完成整段安全恢复，0 烟花。静止/小幅/矿车/横移/改向首次攻击为 6/5/8/7/10 tick，全部命中安全离场，最大距离 34.83 格。追加 [两目标连续回放](tools/propulsion-continuous-second.log) 实际 controller 2 次命中、7 次激流、0 烟花、安全离场且无中途落地。
- **验证边界**：外部物品事件、实体与环境是离线夹具，Spears 命中为原生接触代理，非 Minecraft 实机。连续两目标最初使用加速到 5 ms/tick 的 Worker 调度回放发生碰撞；改用与既有移动目标基准相同的 20 ms/tick 后通过，未改动生产安全阈值。此加速压力失败保留于 propulsion-continuous.log，不将其记作通过，亦不宣称任意 Worker 延迟下保证安全。完整 CommandManager/TLM/Mixin 运行仍需用户实机核查。
- **交付**：[winefox_skyfarer-1.21.1-0.6.2.11.jar](build/libs/winefox_skyfarer-1.21.1-0.6.2.11.jar)，556,942 bytes，SHA-256 `C026B3F7CB540388E3874E0B092527DE0F291B910E20E7F141B6061207914507`。Manifest 与 Mod 元数据同为 0.6.2.11，包含新统一推进/READY 类，不含旧激流任务或离线夹具。README、相关 docs、推进诊断与验证说明同步。

### 0.6.2.10 移动目标未来拦截与有界重定位（2026-10-07）

- **证据**：实例确认 0.6.2.9；日志出现重复无解/等待及 AIR_RECOVERY 后距离 49.905→80.583→83.377 格。旧日志不足以标识矿车目标或逐候选拒因，不将推测当事实。源码及同组修改前回放确认当前位置几何与未来接触失配、移动/静止缓存混用、异步接入窗口损失和补能向外延伸。[日志摘录](tools/moving-target-evidence.txt)。
- **拦截**：按实际目标位移、女仆速度和原生前摇生成三组提前量，最多 648 曲线；近接触持续更新方向/俯仰。当前状态、目标运动及有效数值快照完整重验选中路径，移动曲线可省去额外 Worker 往返；无解再走有界 Rejoin。原 Rejoin/AttackCertificate 缓存的严格门槛保持，未放松 Vanilla、身体扫掠、原生 Spears 接触、速度/前摇或全随机火箭寿命认证。
- **有界与时序**：重定位中心为提前量有界的拦截点，48 格局部转向半径不随外飘位置扩张；补能朝内或沿局部切向。已认证攻击优先，初始候选等待保留武器机会，激流也遵守不破坏可用入口的规则。移动无解每 3 ticks 重搜，反向刷新；静止/运动缓存分开。
- **离场**：追加山地回放发现慢速 Exit 只重算 24 ticks，误判原本完整认证的安全离场；现按剩余长度分配完整预算，保留 160 tick 上限/逐步碰撞认证。此前失败记录见 moving-target-package-initial.log；修复后两个镜像均在 tick 53 命中，在 138/139 落地。未重写返航或紧急飞控。
- **诊断**：ATTACK_SEARCH 限频记录运动、距离、提前量、前摇、重试、推进来源、几何/无接触/离场拒绝与当前重验结果；ATTACK_CONTINUATION_REJECT 记录执行阶段状态。详见 [专项说明](docs/MOVING_ATTACK_06210.md)。
- **比较**：旧版往返/横移/改向在 600 ticks 内均无首次攻击，最远 98.90/103.49/107.92 格；本版首次攻击 8/7/9 ticks，命中 24/23/30 ticks，最远 35.06/33.88/34.83 格。静止 6→6，小幅移动 16→5 ticks。五场均命中并安全离场/落地；[CSV](tools/moving-target-comparison.csv)。离线 tick 间隔同为 20 ms，真实生产控制器与异步执行器，但外部实体/矿车轨迹、伤害为夹具/接触代理，非 Minecraft 实机效果保证。
- **验收**：[moving-target-final.log](tools/moving-target-final.log) 中 compileJava、verifyMovingAttack、verifySweepingAttacks、verifyAsyncPlanning、verifyAirRecoveryHome、verifySweepingReplay、jar 成功；另 verifyAsyncSweep 在 [moving-target-acceptance.log](tools/moving-target-acceptance.log) 通过（仅获取候选的测试，不将其打印的失败 review 当执行安全证明）。包括当前运动重验、缺失 cells、墙体、边界、原生前摇拒绝、异步生命周期、干燥烟花回退、既有 Home 本地落地。没有执行全量 41 项 verify/build/check，未启动 Minecraft。
- **交付**：[winefox_skyfarer-1.21.1-0.6.2.10.jar](build/libs/winefox_skyfarer-1.21.1-0.6.2.10.jar)，537,649 bytes，SHA-256 `F169548959F11057928DE6B08128705DDF087C8324E636850D5346262A7A9CF3`。Manifest/Mod 元数据统一 0.6.2.10；Jar 不含回放夹具或模拟 Minecraft/TLM 类。README、相关 docs 与配置诊断说明同步；未安装到测试实例。

### 0.6.2.9 空中恢复推进与创造 Home 控制权交接（2026-10-07）

- **日志与源码**：0.6.2.8 实机既有 APPROACH 激流，也有低速 REPOSITION；不能将问题描述为完全没有空中激流。真正缺口为紧凑转向仅用烟花、能量 pending 仅预热火箭、复战资格仅认火箭。Home 任务结束后 COMMAND_COMPLETE 开始朝 Home 托管飞行，recall 等落地而在 200 ticks 空中超时。证据见 [air-recovery-home-evidence.txt](tools/air-recovery-home-evidence.txt)。
- **恢复**：coast 优先，不足时认证独立局部 AIR_RECOVERY 激流候选，末速 ≥0.9、扫掠/实体/边界检查、真实前摇和推力；实际执行 12 tick coast 恢复再交接。接近、重新定位、完成 Exit 后和等待路径均接入，已承诺 Strike/Exit 不插入推进。无合法激流继续火箭/安全飞控，保留无级最短时长、雨水资格及独立冷却。良性返航/落地可在有合法下一目标和恢复资源时重新作战，任务完成/装备安全/超时仍不可强行续战。
- **Home**：实际 TLM 1.5.3 SchedulePos 每 40 ticks 对超出限制范围且可移动的女仆决定走回或最多十次安全传送，完全不要求卸载。创造任务结束先移除订单/关闭 Transit，空中就近安全落地；地面 dispose、managed=false、清理 WineFox 路径后放行 TLM。删除 WineFox 每 10 tick 主动传送循环，保留原 200 tick 有界票据宽限，成功或到期释放。不擅自飞越 900 格返乡，不改写 TLM Home 系统。
- **诊断**：FLIGHT_DECISION 提供 RETURN/LANDING/RECOVERY 原因、下一目标、订单、推进资格与速度；PROPULSION 区分 AIR_RECOVERY；COMMAND_RELEASE 标记 CREATIVE_COMMAND_COMPLETE；COMMAND_HOME_CONTROL_RELEASE、TLM_HOME_SCHEDULE held/canBrainMoving/managed 与 COMMAND_HOME_RETURN/COMMAND_TICKET 分别显示控制权、日程机会、结果和撤票。
- **验证**：[air-recovery-home-acceptance.log](tools/air-recovery-home-acceptance.log) 中 compileJava、verifyAirRecoveryHome、verifyRiptideDuration、verifyRiptideDeparture、verifyRiptideRelease、verifyRiptidePriority、verifyAttackPolicy、verifyHandoffStability、verifyRemoteWake、jar 成功。八项定向回归；未执行全部 40 项 verify/build/check。新增生产控制器回放从低速 APPROACH/REPOSITION/完成 Exit/良性 LANDING 恢复空战；900 格 Home 持续加载时在任务当地 67 ticks 落地并释放，已着地立即释放。实体和世界为离线夹具，完整 TLM/Mixin/订单 ServerTick 未实机执行，不声称已验证实际传送成功。
- **交付**：[winefox_skyfarer-1.21.1-0.6.2.9.jar](build/libs/winefox_skyfarer-1.21.1-0.6.2.9.jar)，530,870 bytes，SHA-256 `F4F59DA7EC7F6B822B3FE8B325F747EC91B4D5D7A85B983F3E3E02F575B14E27`。README、相关 docs、元数据和版本索引同步；未安装到实例，未启动 Minecraft。[详细机制](docs/AIR_RECOVERY_HOME_0629.md)。

### 0.6.2.8 可持续激流起飞、备用分支与远程资格（2026-10-07）

- **实机定位**：latest.log/debug.log 确认 0.6.2.7，同一会话不重复累计。距离约 9.52 格时实际完成 5 tick 激流，但立即 CORRIDOR_REVERSAL，后续低速触地；代码只在预测恢复末端检查 ≥0.55 水平速度，实际未执行恢复就交接。另有同距离 EXECUTION_REJECTED→WAITING 重复；远程日志出现 loaded discovery、wings READY/fireworks=false、NO_LEGAL_TARGET，旧 loaded 资格失败返回被静默丢弃。摘录 [riptide-sustainable-evidence.txt](tools/riptide-sustainable-evidence.txt)。没有将所有远目标无合法目标日志一概归因于距离或激流预算。
- **可持续起飞**：推进截止及 24 tick 实际无动力恢复后均检查高度、水平速度 ≥0.9、下降余量，预测末端还验证至少一侧 60° 转向＋下一次蓄力余量；全程 Vanilla travel/身体扫掠。恢复期间不插入追敌/长矛，真实交接状态不足记录 HANDOFF_NOT_READY。仍逐 tick 从短到长选推进，不强迫固定 9 tick；6/10/32/60 格控制器回放自然选中 9 tick，交接后 40 ticks 未触地，近目标可再用 4 tick 补能。远目标起飞只证明当地出发，数值 256/900 格同样不要求一发抵达。
- **备用分支**：本次起飞记住激流候选耗尽，fallback 的异步 Pending 不再重启失败激流并饿死后续普通/烟花起飞。实际开始使用被否决的回放证明烟花分支最终执行。增加开始使用、物品交换、实体/anchor、snapshot/trajectory 细分拒因。
- **远程资格**：初次分配及地面订单核对统一 PropulsionAvailability，合法湿润环境＋可用激流可以无烟花接单；普通/创造镜、loaded/wake 同用，不读取 registry 天气。无推进明确区分环境无效和无可用物品，已有地面订单不继续发呆；空中不因失雨解除滑翔，完成后的 Home recall 保留。COMMAND_ASSIGN 输出推进来源，loaded 资格失败补日志。TransitFlight 原有免费赶路、正常/创造交接与票据限额保持不变，未把 Riptide 冒充火箭物理。
- **定向验证**：[riptide-sustainable-acceptance.log](tools/riptide-sustainable-acceptance.log) 中 compileJava、verifyRiptideDuration、verifyRiptideDeparture、verifyRiptideRelease、verifyRiptidePriority、verifyRemotePropulsion、verifyRemoteWake、verifyLaunchAttackReplay、jar 全部成功。包括短推进最小性、墙/overshoot、真实副手使用及事件否决、无火箭冷却限制、普通/创造 Home 零烟花资格与 TransitFlight 交接，以及既有唤醒公平性/NBT/票据生命周期和 Launch Attack 回归。没有执行全量 39 项 verify 或 build/check。
- **边界**：实体/天气/加载由离线夹具提供，生产选择器/执行器/调度器/TransitFlight 被实际执行；完整 CommandManager ServerTick 与真实 Minecraft/Mixin/第三方场景未启动，CommandManager 使用编译和资格调用连接检查。不能把离线通过当作已完成实机复现。
- **交付**：[winefox_skyfarer-1.21.1-0.6.2.8.jar](build/libs/winefox_skyfarer-1.21.1-0.6.2.8.jar)，528,513 bytes，SHA-256 `3378A568769A9315BB03DB607AFE0DA390674BDE5465BC54019495345627F27F`；Jar Manifest 与 Mod 元数据均为 0.6.2.8。README、docs、版本索引及诊断说明同步；未自动安装或启动 Minecraft。

### 0.6.2.7 近目标起飞与可控时长激流（2026-10-07）

- **日志与因果**：先读取指定实例 latest.log/debug.log，确认为 0.6.2.6。相同会话有 8 次候选 RIPTIDE_NO_SAFE_PATH、3 次 NO_LAUNCH_PLAN，后者紧接普通 CORRIDOR_JUMP / glide=true 后发生；成功释放记录仍有真实 OFF_HAND 蓄力与冲量，不是再次丢失 release。约 10 格来自用户复现，旧日志无数值距离，不冒充已量出现场距离。源码将普通起飞也限制在敌人前方 6 格，原固定强冲量近目标无解。见 [证据摘录](tools/riptide-metered-evidence.txt)。
- **认证修复**：普通 departure 使用净空、eye+6 高度和末段可继续飞行状态，独立于敌人距离/高度；保留身体与生物扫掠、有限前/侧/后方向。接近目标推进仍保留 overshoot 余量，攻击仍在推进结束后由实际动量重做 Rejoin / Strike→Exit，无攻击解继续普通飞控。无动力起飞必须具有末段速度/高度，不能把短暂跳跃当作可持续起飞；离地重验无解明确进入安全恢复，不遗留空计划等下一 tick 才报错。
- **WineFox 扩展**：保留 10 tick 实际蓄力、明确副手、原生 releaseUsing 事件和单次 1 耐久，在受控女仆层按每 tick 0.2 的 look 方向速度增量推进，之后仍走现有 Vanilla 物理。原版强度 I/II/III 对应最大 10/20/30 ticks（0.5/1/1.5 秒），从 1 到上限逐 tick 搜索，先选最短可行时间，再比较姿态成本。高等级不强制长推进，也不增加每 tick 推力。预测覆盖所有推进和 12 ticks 无动力恢复，不把 spin 动画时长误作实际持续推力。玩家三叉戟与普通 Spears 不改。
- **执行与预算**：真实回调只启动一次，后续每 tick 最多加力一次；到期或环境/物品/spin/路径变化停止本次推力及其拥有的 spin，保留速度与 FALL_FLYING，不补历史冲量、不套火箭冷却。普通/紧急飞控接续处理变化。已有释放后第 8 tick 快照/轨迹混合拒因不能追认为单一根因；修复已选路线未提前刷新风险，仅将实际触及 cells（有界 512）在过期前排队刷新，原全服采集/队列上限不变，仍拒绝缺数据。诊断补充距离、用途、时长、推进进度，并区分快照、轨迹和生物扫掠拒绝。
- **限定验收**：[riptide-metered-acceptance.log](tools/riptide-metered-acceptance.log) 中 compileJava、verifyRiptideDuration、verifyRiptideDeparture、verifyRiptideRelease、verifyRiptidePriority、verifyEquipmentLifecycle、verifyEquipmentPropulsion、verifyLaunchAttackReplay、jar 全部通过。6/10/32 格目标均以 5 ticks 实际推进完成起飞，截止后 12 ticks 仍在约 10.85 格高度飞行；干燥环境无激流释放并消耗一枚烟花。III 选择 1/2/3/4/12/19 ticks，逐一排除更短可行选项；19 tick 完整执行有 19 次冲量、一次释放、跨快照刷新周期且截止保留动量。另 [riptide-duration-obstacles.log](tools/riptide-duration-obstacles.log) 独立验证更长方案撞墙与 overshoot 的短时长补救。未执行全量 verify。
- **边界与交付**：外部实体 tick、JumpControl、物品事件和雨状态使用离线夹具；编译/本地原版源码及字节码不等同于完整 Minecraft/Mixin 实机联调。原版冲量 helper 的 1500 向量差分继续用于公式核对，不声称本版完整推进仍等同玩家原版激流。产物 [winefox_skyfarer-1.21.1-0.6.2.7.jar](build/libs/winefox_skyfarer-1.21.1-0.6.2.7.jar)，526,410 bytes，SHA-256 `3B3069D539AC8B5E0372A79F895677DD9B295A3D4352C6CBB34EF55298F24756`。README、docs、版本索引同步；未安装到实例，未启动 Minecraft。

### 0.6.2.6 副手激流真实蓄力与释放入口（2026-10-07）

- **实机证据**：指定实例 latest.log/debug.log 确认 0.6.2.5。28 次 RIPTIDE_NO_SAFE_PATH 尚未开始使用；唯一一次 LAUNCH_WINDUP 于 12:15:15.902，16.401 跳跃、16.454 已 RELEASED 且 vy≈3.152，16.852 路径重验失败后返航。不能把它解释成始终点按右键。旧日志缺少手与使用剩余计时，也不能区分所有路径拒绝细因。摘录 [riptide-release-evidence.txt](tools/riptide-release-evidence.txt)，保留安全拒绝，不凭表象降低门槛。
- **真实源码缺口**：旧执行器 stopUsingItem 后直接施加冲量/spin，没有走物品 releaseUsing 入口。现调用 LivingEntity.releaseUsingItem，经过 NeoForge onUseItemStop 与 ItemStack.releaseUsing；TridentItem 原版仅处理 Player，因此通过 MaidRiptideReleaseMixin 为同步授权且重新核对身份/环境/charge 的女仆适配同样冲量。无授权、事件否决、回调缺失都不补发推进；不创建假玩家。授权 finally 清理、实际释放只执行一次。
- **副手与计时**：显式 OFF_HAND，检查 useItem、used hand、实际副手一致；跳跃与释放采用 getTicksUsingItem，不再使用世界时间精确命中第 10 tick 的假设。持续蓄力直到至少 10 ticks，停滞超过 40 ticks 取消；保留正常跳跃→滑翔→完整路径重验。主手长矛原有使用保护不变，结束后可开始独立主手会话。
- **诊断与状态**：输出 item/hand、开始 tick、累计/剩余 charge、同一堆栈、环境、附魔等级/强度、释放回调、实际 impulse/spin。CHARGING 每 5 ticks；取消写明原因，stopUsingItem 的只读审计记录有界调用栈，状态切换单独记录。修复路径中断也标为 LAUNCH_COMPLETE 的误导，只有 spin 正常结束才进入成功分支。
- **验证**：[riptide-release-acceptance.log](tools/riptide-release-acceptance.log) 的 compileJava、verifyRiptideRelease、verifyEquipmentPropulsion、verifyRiptidePriority、verifyEquipmentLifecycle、verifyLaunchAttackReplay、jar 成功。覆盖完整主矛副戟起飞与释放、真实使用计时延迟、主手不覆盖、副手结束后长矛恢复、开始/释放事件否决、失雨与手被覆盖取消、重复/无授权回调拒绝；保留 1500 原版冲量差分、优先级、环境/碰撞拒绝与普通 Launch Attack 回归。外部事件/实体 tick/JumpControl 用夹具，生产 Mixin 与原版入口用编译/字节码核对；不声称验证了实机 Mixin 应用和第三方事件交互。未全量 verify 或启动 Minecraft。
- **交付**：[winefox_skyfarer-1.21.1-0.6.2.6.jar](build/libs/winefox_skyfarer-1.21.1-0.6.2.6.jar)，524,792 bytes，SHA-256 `48EC41A0B4910545955D3B8D87619C02FDEFD924E8C06F078A2B543EF1517176`。版本元数据与 README/docs 同步；未安装到测试实例。

### 0.6.2.5 激流优先、独立生命周期与起飞推进（2026-10-07）

- **源码定位**：雨环境已使用原版 isInWaterOrRain，含屋顶/高度图/生物群系条件；强度读取 EnchantmentHelper，副手和可用背包均可发现三叉戟。真正阻断在后续链路：起飞资格硬要求烟花，激流 begin 排除地面，空中选择还受火箭对齐和代价竞争压制，optional 冷请求可直接落到烟花，取消后另加 20 tick 人为冷却。本次修复这些入口与执行条件，没有将原因归咎于雨判断或仅调高评分。
- **候选与执行**：coast 足够时不推进；需补能且无活跃火箭时，先在最多三种可用强度中选择安全激流，再考虑烟花。激流不读取火箭 cooldown，不保留额外 pulse cooldown，遵守至少 10 tick 前摇和原生 20 tick spin；12 tick 恢复只用于完整预测。空中/地面异步等待分别有 12/40 次边界，无证书仍退让。主线程按实际堆栈、环境、身体/生物扫掠和最新速度重验，拒绝碰撞、越过目标及原生接触反转风险；已承诺 Strike/Exit 不插入激流。
- **起飞与攻击**：地面完整预转向→三叉戟前摇→正常 JumpControl 跳跃→离地开启滑翔→重验并叠加原版激流冲量。没有伪造地面释放的抬升。水中走现有 WATER_ESCAPE，优先同一激流选择器。spin 后从当前状态重新选合法机会目标，并通过 Rejoin 与 Strike→Exit 才进入起飞攻击，否则继续普通飞控；无激流解则烟花、再到经过预测的普通起飞。
- **配置与诊断**：默认额外 Elytra 列表包含 cataclysm:ignitium_elytra_chestplate，reset all 恢复该列表，已保存显式列表仍保留。纯 ID 兼容，无硬前置或赋予普通胸甲飞行能力。PROPULSION 记录实际湿润、强度、火箭状态及选中/等待/拒因，RIPTIDE 记录前摇、跳跃、释放及执行重验失败。
- **定向验证**：[riptide-priority-acceptance.log](tools/riptide-priority-acceptance.log) 中 compileJava、verifyRiptidePriority、verifyEquipmentPropulsion、verifyEquipmentLifecycle、jar 通过；[riptide-launch-regression.log](tools/riptide-launch-regression.log) 中 verifyStabilityLaunch、verifyLaunchAttackReplay 通过。实际选择器在火箭 cooldown 内选中激流；雨中背包三叉戟在有/无烟花两种完整起飞均跳跃、滑翔、实际释放且未耗烟花；1500 原生冲量差分、环境与 spin 门槛、墙体/overshoot 拒绝和默认 ID 识别通过。普通机会目标起飞命中、Exit、无解回退也通过。
- **测试发现与边界**：初轮回放目标超出已有战略半径，修正场景而未放宽规则；随后发现起飞预测未计入地面可先完成转向，加入 ground aim 后保持原碰撞断言通过。此前失败日志不是通过证据。外部跳跃、实体 tick、库存由离线夹具提供，雨条件与使用机制核对本地 Minecraft 1.21.1 源码/字节码；未运行真实游戏或第三方 Mod 堆栈。
- **交付**：[winefox_skyfarer-1.21.1-0.6.2.5.jar](build/libs/winefox_skyfarer-1.21.1-0.6.2.5.jar)，518,177 bytes，SHA-256 `C8B3289E89FCD6DF999EB514C1CE0404172BEE241528776654EC2C94F3CD7E7E`。README、装备/配置/物理/异步/飞控/验证文档和项目索引同步；未自动安装或启动 Minecraft。

### 0.6.2.4 装备拒因与地面导航接管修复（2026-10-07）

- **日志事实**：读取指定实例 latest.log/debug.log。debug.log 确认加载 0.6.2.3，第 6818 行指定实际配置为实例 config/winefox_skyfarer-server.toml；本轮该配置第 237 行为 extra_elytra_items=[]。指定胸甲按当前配置会先被身份门拒绝，不是 Unbreakable 失效。未记录历史堆栈，未推测配置为何为空。此前 WINGS_UNFIT 日志被持久对话事件去重挡住，打开 debug 后仍没有完整资格证据。见 [日志摘录](tools/equipment-qualification-evidence.txt)。
- **统一资格**：WingInventory.qualification 返回 READY / EMPTY_CHEST / NOT_ELYTRA_EQUIVALENT / LOW_DURABILITY / ITEM_CANNOT_GLIDE；当前胸甲、备用搜索、起飞与远程调度共用。移除笼统 ELYTRA_LOW_DURABILITY 标签；换装后重验实际胸甲，非耐久失败采用通用装备提示。未增加 Cataclysm 特例或改变耐久阈值。
- **诊断**：独立 EQUIPMENT_CHECK/SPARE/RESULT，输出 ID、实际列表与匹配、damage/maxDamage、damageable、组件存在性、remaining、canFly、备用槽拒因、替换与最终资格、长矛/烟花和地面/滑翔状态。首次、变化、每 200 ticks 采样，不依赖气泡去重；地面拒绝输出具体 reason。
- **导航根因**：此前装备失败后的任何离地状态都会接管飞控，日志多次 GROUNDED→RETURN_TO_OWNER、glide=false 与其吻合；已有 Mixin 随后暂停导航/MoveControl 并清除路径。现在普通跳跃/下台阶不接管，保留 TLM MoveToTargetSink、Navigation、MaidMoveControl、JumpControl；只有托管飞行或真实滑翔进入安全返航。追同一目标保留动态 WalkTarget，后撤小幅变动不重设，不直接写地面 velocity。
- **实例配置**：仅把用户指定 cataclysm:ignitium_elytra_chestplate 加到实际加载配置，原文件备份在 build/diagnostics/winefox_skyfarer-server.before-0624.toml。逐字对比确认其余内容未改；未安装新 Jar 或启动 Minecraft。
- **定向验证**：[equipment-qualification-acceptance.log](tools/equipment-qualification-acceptance.log) 中 compileJava、verifyEquipmentFixes、verifyGroundMelee、jar 通过。覆盖空列表准确拒因、列表内 Unbreakable、普通低耐久、备用诊断、限频、高空保旗；实际控制器/地面行为执行一格台阶回放，不接管、不清目标、不覆盖动量，落地后继续接近命中；真实 TLM 导航注册和 JumpControl 调用检查。没有全量 verify。首轮夹具在落地碰撞钳位前检测高度而失败，修正夹具顺序后通过，未放宽飞控/导航断言。
- **边界与交付**：外部跳跃/台阶碰撞由夹具提供，未运行完整 Minecraft/TLM 寻路或真实第三方堆栈；不足部分由新版诊断补齐。产物 [winefox_skyfarer-1.21.1-0.6.2.4.jar](build/libs/winefox_skyfarer-1.21.1-0.6.2.4.jar)，514,799 bytes，SHA-256 `A912EFF83E9921E23D6A2777C25F6DA3FBBE45982B0BD58CD9E19D24D145FED8`。README、装备/配置/验证说明及当前版本索引同步。

### 0.6.2.3 兼容翼耐久、普通长矛距离与克制提示（2026-10-07）

- **范围**：核对本地源码、docs、Minecraft 1.21.1、TLM 1.5.3、Spears 1.8.0 与实例 Cataclysm 3.33；没有覆盖本地工作、拉取远程、添加第三方硬依赖或修改依赖 Jar。
- **耐久**：显式优先 UNBREAKABLE component；不可损坏或 maxDamage≤0 的非空兼容装备免除 20 阈值，普通可损坏装备仍执行 19/20 边界。身份列表和物品自身 canElytraFly 始终独立校验。Cataclysm 正常默认组件本应设置 UNBREAKABLE，旧标准 isDamageableItem 路径也应放行，因此不能声称已复现真实胸甲误判根因；新增事件级 WINGS_UNFIT 记录 ID、识别/飞行能力和实际组件/耐久，便于后续定位实机堆栈差异。
- **普通长矛**：按原生 PiercingWeapon.stab 的完整 ATTACK_RANGE 认证普通接触，放弃笼统 TLM 距离判断；瞄准、身体相交、原生遮挡通过后才挥矛。按用户补充，将交互属性高于 TLM 默认 2 的部分及 TLM 好感度距离档位，加入服务端地面骑空士的实际 stab 最大范围。新增 MaidSpearMeleeMixin 只修改普通左键 collector 的最大值，预测用同一加成，保留最小盲区、伤害、友军过滤与遮挡；普通标准矛 2–4.5 格，停止检查预留 0.2 格，不依赖移动延伸。敌人逼近时以 WalkTarget 后撤，仍在有效范围时可边退边打，盲区内不空挥；未增加 charge 或地面冲刺。
- **提示**：每个故障的禁止起飞/返航/落地事件保存到女仆 NBT，实体/飞控重建不重置；装备恢复正常持续 40 ticks 后才允许下一故障重新提示。每只 200 ticks 冷却，同 owner 同维度 40 ticks 间隔，UUID 分散首次提示；只保留当前待发事件，取消过时提示，不在玩家聊天刷屏。
- **限定验证**：[equipment-fixes-acceptance.log](tools/equipment-fixes-acceptance.log) 的 compileJava、verifyEquipmentFixes、verifyGroundMelee、jar 全部成功。覆盖不可损坏极小内部耐久/无耐久/普通低耐久、备用交换、空中返航或降落保持滑翔、实际地面行为接近/后撤/好感度与交互加成/盲区，以及持久故障去重、修复后新事件、20 只同 owner 错峰且最终全部提示。真实 MC AABB 几何与依赖字节码检查普通 stab/collector 描述符和加成调用连接。未执行全量 verify、build/check 或启动 Minecraft。
- **验证修正与边界**：早期回放夹具缺少 ATTACK_SPEED；修正后高空测试把主人正上方立即进入 LANDING 误当成失败，改为接受既有 RETURN_TO_OWNER 或 LANDING，同时仍强制滑翔和飞控保持。最终日志才是通过证据。外部实体/库存/原生伤害使用夹具，Mixin 在真实游戏中的应用、第三方堆栈、TLM 寻路及气泡显示仍需实机确认。
- **交付**：[winefox_skyfarer-1.21.1-0.6.2.3.jar](build/libs/winefox_skyfarer-1.21.1-0.6.2.3.jar)，508,413 bytes、263 条目，SHA-256 `19A00F2E105061F14EBF2E7750CC2687D3BA6CE226D2F8E320F6C70171FBAF8D`。Manifest/Mod 元数据均为 0.6.2.3，新普通长矛 Mixin 已列入配置；无测试/回放/模拟依赖类及嵌套 Jar。README、装备/配置/验证文档、项目索引同步，git diff --check 通过。未自动安装。

### 0.6.2.2 装备生存、地面 fallback 与激流辅助推进（2026-10-07）

- **本地基线**：开始时工作区干净，HEAD bdea091（0.6.2.1）。阅读当前 docs 并核对源码与本地 TLM/原版依赖；未从远程覆盖文件，没有修改依赖 Jar。
- **装备**：剩余耐久 <20 时，从当前可用库存交换真正可滑翔且耐久至少 20 的备用，旧装备回原槽；不合适/拒收旧装备的槽跳过。空中无备用立即终止主动空战并返航，保留 FALL_FLYING；地面禁止起飞并使用现有长矛普通近战。指挥赶路也执行检查并安全移交。工作模式启用只要求长矛，地面 fallback 不被缺翼资格挡住。
- **对话**：TLM 原生头顶文字气泡提示地面装备故障、空中返航和落地转地战，同一故障去重；中英本地化同步。
- **兼容配置**：新增 SERVER equipment.extra_elytra_items，默认 []，按注册 ID 识别，如 cataclysm:ignitium_elytra_chestplate；未知/未安装 ID 忽略，无第三方硬依赖。物品自身 canElytraFly 仍是硬条件，普通胸甲入列表也不能飞。
- **激流**：核对 MC 1.21.1 TridentItem、Enchantments、Entity/Level 环境条件和 LivingEntity spin；10 tick 前摇，I/II/III 真实强度 1.5/2.25/3，float 查表归一化后叠加当前速度，1 耐久、20 tick spin。副手/背包选取，使用环境在开始/释放重新检查，屋顶下不能借全局雨天发动。原版释放仅支持 Player，采用小型 LivingEntity accessor 适配女仆，不生成投射物或额外伤害。
- **预测与范围**：coast 足够时不补能；未锁定攻击阶段可比较安全激流和原有全寿命烟花。最多 3 强度 × 3 姿态，完整认证 10+20+12 ticks，墙体、转向、越过目标、生物触碰与活动火箭约束保留。真实执行前/期间从当前状态重验已捕获快照，过期/缺数据拒绝；后台无 Entity/Level/物品引用。激流不插入已承诺 Strike/Exit，完成后重新认证；普通地面起飞仍沿用烟花，激流是雨中空中与水面恢复的辅助方式。
- **定向验证**：tools/equipment-acceptance-final.log 的 compileJava、verifyEquipmentPropulsion、verifyEquipmentLifecycle、verifyHandoffStability 和 jar 成功。包含 1,500 组独立 Mth 向量差分、42 tick 完整路径、碰撞/越过目标拒绝；实际库存交换算法、配置身份/能力分离、19/20 边界；实际控制器空中换装/无备用返航保旗/落地地战/气泡去重；实际激流管理器湿润资格、前摇失雨取消、冷却和过期快照拒绝/重建、释放推进/耐久/spin。
- **证据边界**：库存、TLM、世界由隔离夹具提供，实际近战/雨水环境调用另检查真实类字节码。首次尝试离线注册物品触发未初始化 NeoForge LoadingModList，改用隔离物品夹具，未启动游戏或修改依赖。真实第三方装备、气泡/动画/网络及 TLM 寻路留待实机。未跑 build/check 或全量 verify。
- **文档与交付**：README、项目索引、飞控/异步/物理/配置/验证文档与 EQUIPMENT_SURVIVAL.md 同步。产物 [winefox_skyfarer-1.21.1-0.6.2.2.jar](build/libs/winefox_skyfarer-1.21.1-0.6.2.2.jar)，499,111 bytes、259 条目，SHA-256 `5519BA48356F14F64D82EBE3E7A025ED79B5AC3D5FB5C07A962FE646D4FA45E6`；Manifest/Mod 元数据一致，包含 LivingRiptideAccessor，无测试/回放/模拟依赖类；客户端/服务端同时更新，未自动安装或启动 Minecraft。

### 0.6.2.1 提前避障、高空交接与 Launch Attack（2026-10-05）

- **本地基线**：当前 HEAD 为 `deaebca`（dev to 0.6.1.1），开始时工作区干净、无 stash。阅读当前 docs 并核对生产调用链；没有 fetch/pull、覆盖远程内容或再次回滚。保留 0.6.1 系物理、坡线/曲线、AttackCertificate/Rejoin 和完整 Strike→Exit 架构。
- **提前避障**：真实速度向量与期望航向偏差决定 6–32 ticks 动力学预警时域；危险保持 12 ticks，独立紧急 mailbox 不随 ATTACK/EXIT/REPOSITION 切换失效。六个大角度出口先验证滑翔，无解才尝试最多三种库存烟花 duration；`EMERGENCY_BOOST_TURN` 验证全部随机寿命、启动延迟、活动火箭、身体扫掠和至少 36 ticks/燃尽后 20 ticks 的完整逃逸。执行前用当前状态与已捕获不可变 cells 复核，不凭陈旧速度点火；候选搜索仍在共享 Worker，采集/队列上限保留。陡角曲线离场俯仰上限提高至 14°/tick，预测和执行一致。
- **高空生命周期**：合法空中 release 转安全返航；Transit/Combat 移交保留 FALL_FLYING，卸载/替换使用显式 dispose。相同 UUID 的新实体不能复用旧实体控制器，重载合法滑翔可重新接管；托管时外部清旗会记录并尝试合法恢复。Home 空中继续恢复、暂缓日程，地面才尝试回收传送，原 200 ticks 票据上限与释放保留。新增 FLIGHT_LIFECYCLE 和 FALL_FLYING_CLEAR 调用栈及 state/velocity/altitude/target/order；不覆盖原版资格判定。
- **真实崩溃**：用户指定实例最新 `crash-2026-10-05_22.59.23-server.txt`，并交叉读取 latest/debug.log。属于本 Mod：ServerTick.Post 的 DebugWatchManager 在新赶路订单首个实体 tick 前调用 TransitFlight.debug，planning 尚空。修复为只写 TRANSIT_WAITING_FIRST_TICK，不初始化/重复预测；close 清除旧规划与状态。栈不属于 Worker；审核后 Worker 仍只持有不可变数值任务/快照，不持有 Entity/Level/Source。详细证据见 [STABILITY_LAUNCH_0621](docs/STABILITY_LAUNCH_0621.md)。
- **Launch Attack**：地面转向后跳起，复用 SweepingAttackPlanner 的 30/50/70° 上升候选、原生 Spears 前摇/接触、可选真实库存烟花和完整离场认证。最多四个合法同战略层目标，每目标 27 条曲线；标记/保护区/上一目标/敌对层优先，不让普通顺手目标越级。起跳前预热滑翔体积 Rejoin，离地后重验当前状态与目标；待定/无解有界回退普通起飞。目标的地面残留重力速度不当成实际下落，候选不沿用旧锁定实体的 motion。
- **回放中修正的问题**：首轮 Launch 的恒定姿态 TTC 错把动态曲线的安全爬升判成撞山，改为按同一曲线控制律预测。测试要求起飞阶段直接接触并完成 Exit，不把后续普通攻击计作成功。紧急冷快照首轮错过救援窗口，现优先采集已由 live guard 发现危险的动量通道，仍受原全服预算约束。中间失败日志保留，不作为交付证据。
- **限定验证**：[v0621-acceptance](tools/v0621-acceptance.log) 中编译及 verifyStabilityLaunch、verifyHandoffStability、verifyEmergencyReplay、verifyLaunchAttackReplay、verifyVerticalAttacks 全部通过；[v0621-launch-direct](tools/v0621-launch-direct.log) 的两个 verifyCombatEntries 进场至落地通过。最终 [v0621-delivery](tools/v0621-delivery.log) 再次编译、跑受后续改动影响的交接/Launch/紧急回放并打包成功（57s）。未运行 build/check 或全量 verify，未启动 Minecraft。
- **具体证据**：冷快照 14 格墙第 0 tick 发现 TTC=7/margin=31，第 3 tick 点一枚 Flight 0，完整 43 ticks 逃逸无身体碰撞；20 格墙仅滑翔、消耗 0。顶棚使烟花恶化时拒绝，活动火箭不叠加。80 格高空交接/释放/清旗恢复/重载不丢合法滑翔，tick 前和 close 后 debug 复现不崩溃。山脚锁定 A 后选择同层山腰 B，起飞直接接触、完成 Exit，显式标记优先和友方排除通过；无解普通 CLIMB 回退通过。竖向下刺/转弯上刺仍约 61.14°/−51.91°，contact tick 17/13。
- **边界**：离线回放使用生产控制器/调度/数学与外部实体世界夹具，Spears 接触由其参数/时序条件模拟；没有把它当成实际游戏 damage、真实 TLM Home 日程、NeoForge 读盘或第三方 Mod 实测。实际 Minecraft 清旗 hook 签名另做字节码核对。未重跑 0.6.1.1 全套 remote wake、全量性能或所有游戏场景；最新坠落的所有外部清旗来源仍须新日志实机确认。
- **交付**：[winefox_skyfarer-1.21.1-0.6.2.1.jar](build/libs/winefox_skyfarer-1.21.1-0.6.2.1.jar)，478,713 bytes，SHA-256：`B982D96AF9A516BEEA8F7F8F84D180ABA953BE93F266134B3CF1598A2C2C95C2`。252 个条目；Manifest/Mod 元数据统一 0.6.2.1，包含 FlightFlagAuditMixin，无 Funnel、验证/回放夹具或模拟 Minecraft/TLM 类。README、相关 docs、验证入口和本记录同步；客户端与服务端请同时更新。

### 0.6.1.1 竖向候选、女仆标记排除与公平唤醒（2026-10-05）

- **基线**：开始时 status/diff/stash 均为空，HEAD 实为 `6c5266d`（0.6.3），并非已准备的 0.6.1。向用户说明差异后，用户明确选择本地 `606f79d`；以该提交恢复工作区，再进行本轮修改。没有 fetch/pull、远程覆盖或重写提交历史。0.6.2/0.6.3 的 Funnel 代码、接入、检查与专属文档不再属于当前基线；原提交仍保留。
- **飞控**：复用 VanillaElytra、AttackCertificate/Rejoin、Lane/slope、SweepingAttackPlanner、EnergyReserveEnvelope 与完整 Strike→Exit 认证。保留 108 条浅角曲线，新增 108 条 ±50/60/70° 与左右转弯组合；起点仍取三维实际动量，支持越脊后下刺与先降后上攻的几何搜索。曲线姿态允许 ±75°，陡角离场允许 9°/tick 收平/拉起；完整物理、身体通道、前摇和烟花寿命仍是硬条件。普通路线必须实际预测接触才能在评分中压过已认证曲线。
- **坡线与准备**：拟合和候选坡度扩展到 ±tan(70°)，采样步长上限 .4 格，避免连续陡坡跨出支撑高度窗口；平地高差也可搜索有符号攻击线，最短攻击段按三维长度换算水平投影，出口增加 10 格抬升候选。APPROACH/REPOSITION/补能准备的搜索准入不再强制满足平滑收底俯冲距离；真正收底剖面保留原距离条件，所有进攻仍须最终证书。
- **标记**：普通/创造镜共用的服务端 pick 谓词直接排除所有 EntityMaid，不看主人、工作模式、Home/Follow 或敌友；调试棒、女仆遥控器、远程调试的选取逻辑保持。
- **远程调度**：原 Session.attempted 对一次失败永久封锁到下一次点击，且持久化的已知非骑空士记录会直接跳过；改为生产 RemoteWakeScheduler 管理有界 wake，未尝试者优先、按旧尝试顺序轮转，失败从发起起 200 ticks 后可再试。保留每玩家每 10 ticks 2 只、全服 8 个、100 ticks 超时。增删/死亡/自然清理标记刷新 generation；任务结束、创造目标卸载、登出、离开和关服释放，接单将票据交给独立 Order/TransitFlight，不阻断同 owner 其他候选。存档角色只作提示，加载后重查真实资格。回 Home 后下一任务可重新唤醒。
- **票据与诊断**：尊重 forceChunk 返回值，不把失败的块记为持有；空或未完整创建的窗口不能 ready。增加 REMOTE_WAKE 的 discovered/rejected、queued、ticket、loaded、qualification、assignment、timeout/released 与 attempted/generation，COMMAND_TICKET 记录实际增减窗口。日志区分每只女仆 UUID、owner 与失败原因。
- **限定验证**：`tools/baseline-delivery.log` 编译、verifyRemoteWake、verifyCommandDispatch 与 jar 成功；`tools/baseline-vertical-final.log` 的 verifyVerticalAttacks、verifyCombatEntries、verifySweepingReplay 成功。陡角实际接触约 61.14° 下刺和 −51.91° 转弯上攻，接触 tick 17/13；上攻覆盖全部 12 种剩余推进寿命，碰撞/射线拒绝保留。低能量/上升进场分别命中于 19/90 tick，128/139 tick 落地；左右遮挡山腰均 53 tick 命中，142/139 tick 落地。未跑 build/check 或全量 verify。
- **唤醒覆盖与边界**：生产调度器与实际 registry NBT 往返覆盖两只卸载 Home、13 候选批次、第二次任务、存档重建含旧/未知角色、首批失败不饿死后续、同 generation 冷却重试、全局 cap、创建/资格失败、换代/关服释放。外部区块加载、接单和 Home 返回用夹具模拟；真实 NeoForge 读盘、TLM 传送落点和第三方交互仍由实机确认。中间测试反射触发未转换的 TLM 类错误，改为包内直接调用实际 NBT 入口，未修改依赖或启动游戏。
- **交付**：[winefox_skyfarer-1.21.1-0.6.1.1.jar](build/libs/winefox_skyfarer-1.21.1-0.6.1.1.jar)，451,525 bytes，SHA-256：`F13490DD31B4914B01E6D798CBEB2EFCF50352647B11EA12324619177BE8A465`。243 个条目，Manifest/Mod 元数据统一 0.6.1.1；无 Funnel、验证/回放夹具或模拟 Minecraft/TLM 类。相关 README/docs 同步；客户端与服务端同时替换旧包。

### 0.6.1 原版物理预测与完整路线储备（2026-10-05）

- **实现依据**：先完整核对 docs 与实际控制器、Lane/曲线、物理/烟花、异步快照/生命周期、碰撞/离场及回放，再读取本机 NeoFormRuntime 的 Minecraft 1.21.1 / NeoForge 21.1.250 mapped 源码。新增 `docs/VANILLA_PHYSICS.md` 记录源码包 SHA-256、方法入口、精确公式、执行顺序与适用边界；未以 Wiki 或经验代替对应版本实现。
- **纯数值物理**：`VanillaElytra` 复现 float 角度与 Mth 查表、.003 速度归零、原重力/缓降分支、下沉转换、拉升项、更新前水平速度的耦合、float 阻力与先移动后火箭推进。实际飞行仍由 Minecraft 执行，普通战斗不写速度来制造命中。没有伪空气阻力属性。
- **冻结上下文**：主线程 `PhysicsProvider` 绑定实体/Level 引用、dimension 与真实 tick，读取 getGravity/noGravity 和缓降剩余时间。首次、重入、换实体或同名维度的新 Level 重新捕获；异常回退默认 .08。合法负/零重力保留。Worker 只使用数值 `PhysicsContext`，不逐 prediction tick 读取世界；自然效果倒计时不让所有任务反复失效，效果刷新/物理变化会取消旧 generation。关闭释放引用。
- **能量与认证**：`EnergyReserveEnvelope` 记录整段 Approach→Strike→Exit 的未来高度需求、最低速度与最差 reserve。普通线候选的能量亏空只评分，不能宣布可达；承诺进攻前由异步 `AttackCertificate` 检查新前摇、实际接触速度/射线、逐步身体通道及完整离场。曲线候选/Rejoin 同样要求完整模拟，未选中候选仅累计最差值，选中后才保存逐步样本。
- **必要的保护边界补充**：旧曲线预测在时域耗尽时可把部分恢复段当成功，与完整路线要求不符；现在要求离场行程、最低速度、接触后至少 16 ticks 和火箭燃尽后 16 次无动力移动，最多 160 ticks，超限拒绝。ATTACK_EXIT 的有界行程、charge tail、伤害、碰撞算法不变；出口高度仍是导向/能量需求，不新增必须悬停抵达出口坐标的条件。普通证书采用 Rejoin 同级的严格姿态/年龄验收；Level 身份和物理变化加入旧 generation 失效范围，线程池/队列/预算不变。
- **火箭时序修复**：mapped EntityTickList 在迭代期间使用稳定 map，女仆 tick 新建的火箭下 tick 才开始推进。独立 `rocketDelay` 保留全部随机推进次数；起飞/滑翔/紧凑转弯/曲线与赶路均冻结该阶段，四类回放夹具同步。真实库存、duration、全部 12 个随机寿命、活动 life/lifetime 和消耗逻辑保持。补能预测最后不足 8 ticks 不额外启动 24-tick 搜索，但逐步扫掠不省略，修复启动等待导致低顶棚短脉冲被远期墙体误拒绝的问题。
- **回归过程**：中间日志 `tools/physics-final.log` 曾因出口高度过约束、短脉冲窗口和回放衔接失败，不能作为交付证据。没有放宽原有控制器限时、碰撞或命中断言；新增包络检查区分“出口高度需求拉低 heuristic”与“实际出口碰撞导致物理 veto”。此前仅给较短 horizon 的反例现在允许有界延长恢复，新增测试改为火箭燃尽/恢复确实超过 160-tick 硬上限的反例。
- **认证等待衔接**：首轮 `tools/v061-release.log` 的 24 项检查中，23 项通过，创造 100 倍速赶路交接后出现非计划着地。检查发现新 AttackCertificate 的 Pending 异常会跳过当 tick 的引导/补能；改为返回明确未认证状态，让普通控制继续更新，仍禁止承诺攻击。新增冷证书不打断控制的回归，并为赶路失败保留姿态/速度/事件日志。该失败日志保留，不当作交付证据。后续详细回放还定位到低速离场因 20° 对齐 heuristic 拒绝安全补能，最终耗尽高度；ATTACK_EXIT 现在允许申请转向中的全寿命脉冲，由真实物理和碰撞验证决定，不改变离场距离/完成条件。例外限定为无新火箭预测不安全、速度 <.65、下降且距地 <4 格，避免扩大普通补能范围。新增 120° 低空恢复的全寿命数值反例，赶路 100 倍速场景继续断言不得非计划着地。
- **完整出口与库存候选衔接**：完整出口认证使原有“先取得无动力候选、再考虑库存”的入口漏掉可补能路线。现在可按真实库存生成带 ignition 标志的几何提案（最大预测推进 ≤80 ticks），分 slot 缓存，实际接入仍重验全部随机寿命并成功提取物品。出口行程完成后的火箭/滑翔验证沿末切线继续，避免与实际有界离场不一致地绕一个已完成的终点盘旋；所有额外步仍检查碰撞和导航边界。新增低速无动力不可行、拟点火候选及全部寿命认证的反例。
- **文档/元数据**：README、PROJECT_INDEX、飞控总览、异步协议、配置调试、验证文档均更新。Gradle/Manifest/Mod 元数据统一为 0.6.1，修正旧的 0.6.0/0.5.4 不一致。
- **向上进场的候选衔接**：`v061-verified`、`v061-final`、`v061-complete` 中曾出现命中但没有从下方上升攻击的失败，不能当作发布成功。等待引导/恢复高度调整不能稳定解决且影响坡面回放，均已撤回。最终补充无动力几何存在但当前 Rejoin 明确失败时的拟点火几何搜索，避免旧候选堵住可补能路线；仍保留 Rejoin 等待与全部寿命硬认证。紧凑转弯点火后的当 tick 预测同步刷新启动延迟。原有向上攻击、限时和碰撞断言未放宽。
- **替代路线认证并存**：Rejoin slot 按不可变 Path 身份区分，避免无动力提案的重试取消拟点火替代路线；新增 slot 隔离回归。仍使用同一控制池、每女仆 24-slot/总排队 64 的硬上限、generation、严格接触年龄与姿态门槛。
- **上升准备段衔接**：局部瞬时高度调整不能稳定保留上升入口。现按每次 startApproach 的起始高度确定上升准备意图，贯穿等待、接近、重定位与低能量恢复，以接触扩展范围下部作为准备高度；下一次进场重新判断。低速入口的禁补能距离 heuristic 限于已达到攻击高度的情况，接入失败可下一 tick 重验。攻击仍由完整数值认证授权，不改变碰撞/伤害/离场断言；平飞与俯冲准备规则保持。
- **连续地形边界**：`v061-validation` 的向上入口与其余 22 项检查通过，但连续上坡 Controller 场景暴露低位准备与山坡支撑高度冲突。坡面已有至少 12 个采样且非平地时，恢复原地形爬升并清除低位 Compact 目标；针对性复测同时通过向上入口与连续上坡。最终全量结果以最后发布记录为准。
- **最终验证**：`tools/build.ps1 -Stage v061-acceptance -Tasks @('build','--continue')`，未筛选场景，[完整日志](tools/v061-acceptance.log) 为 **BUILD SUCCESSFUL in 8m 28s**，30 项构建任务（27 executed / 3 up-to-date），全部 **24 个 verify 任务执行并通过**（22 个既有任务 + 2 个新增任务）。包括 18,000 步独立原版物理差分、完整路线包络与物理否决、21 个 Controller 生命周期场景、7 个 Dispatch 场景、2 个特殊进场和 2 个镜像曲线回放；162 组飞行、144 组攻击/蓄力 +48 组走廊、72 组地形、60 组水/冰/雪起飞、144 组长矛接触，以及并发/队列/stale/生命周期/API/兼容性检查全部通过。向上入口 hit=81 / landed=165 / upward=true；连续上坡 attack=38 / landed=377；创造 100 倍速调度 hit=263 / landed=332。没有放宽原断言，也没有启动游戏。
- **负载证据边界**：24 会话 /100 ticks 合成调度 ready=1992，main p50=.356 ms、p95=2.181 ms、max=29.595 ms；所有会话得到服务，队列≤64、每女仆任务≤24、cell≤768，停止清理通过。日志的 worker CPU=1071.395 ms 实际是累积任务墙钟时间；这些数字不是实机 TPS/MSPT、GC 或优化前后对比。
- **交付产物**：[winefox_skyfarer-1.21.1-0.6.1.jar](build/libs/winefox_skyfarer-1.21.1-0.6.1.jar)，**441,334 bytes**，SHA-256：`8AF73486107245D337454AB7C05352B7C27A67C6ACDA17098E8CB2D128F547E3`。已核对 Manifest/Mod 元数据均为 0.6.1、239 个条目、新增物理模块、两份 Mixin 配置和中英文资源；不含离线夹具、验证类、模拟 Minecraft/TLM 类或嵌套依赖 Jar。`git diff --check` 通过；本轮交付文档本地链接已校核。客户端与服务端请同时更新。
- **实机边界**：未安装或启动 Minecraft。独立差分使用真实依赖 Mth/Vec3 与源码转写 oracle，并非启动原生实体 travel；生命周期回放的外部 API 仍是夹具。实际 gravity/缓降、魂符跨维度重入、第三方 travel Mixin/外部火箭/外力、真实碰撞及多女仆 MSPT/GC、客户端和原生 Spears 交互仍需游戏内确认。

### 工程知识文档整理（2026-10-05，未重新发版）

- 新增 `docs/PROJECT_INDEX.md` 作为新会话首读入口，并在前部明确“文档属于实现的一部分；事实变化必须同步更新；源码为最终权威”的长期规则。README 增加入口链接，保持玩家介绍与维护文档分工。
- 新增 `FLIGHT_CONTROL_OVERVIEW.md`、`ASYNC_PLANNING.md`、`CONFIG_DEBUG_REFERENCE.md`、`VALIDATION.md`，按当前源码整理状态迁移、实际调用链、线程/快照边界、配置消费者、指挥/调试/兼容接口及验证证据；保留枚举、原因字符串、未生效参数与已接入逻辑明确区分。
- 核对发现 **Gradle/Manifest 为 0.6.0，但 `src/main/resources/META-INF/neoforge.mods.toml` 仍为 0.5.4，且构建没有替换**。上一条交付记录的“已核对版本元数据”未发现这处差异；当前知识文档列为已知问题，后续发版需要核对两处版本，本次不修改生产代码或重建 Jar。
- 修正 README 对 Home 实际范围的简化描述。异步统计按真实实现解释：`workerNanos` 是各任务墙钟耗时累计，负载报告中 `worker CPU` 的字样不能当作 CPU profiler 数据；文档明确合成基准与实机 MSPT/GC 的边界。
- 本次为文档维护，未运行编译/游戏或冒称重跑行为验证；验证依据为已存在的 `tools/v060-release.log`。文档校核通过：194 个本地链接/锚点有效，55 个数值配置及 `debuglog`、20 个实际状态、22 个验证任务均有对应说明。交付 Mod 仍为上一版产物。

### 0.6.0 主线程快照与共享异步规划（2026-10-04）

- **架构**：新增 `async` 包，将原控制器中的重计算接入 `MaidTerrainSource → CollisionSnapshot → PlanningTask / PlannerExecutor → PlannerSession → 主线程执行`。后台执行滑翔预测、火箭完整随机寿命验证、坡面分析、入口/曲线候选生成与评分、连续追击和离场搜索。主线程只采集世界数据、领取并校验结果、执行实体与物品操作，以及运行当前实际位移的安全检查。
- **线程边界**：任务类型是封闭的 sealed DTO 集合；状态、目标、物理参数、导航范围和配置倍率均冻结为数值。世界碰撞形状在主线程转换为不可变数值包围盒，支持真实雪层/半砖等形状。后台不持有实体、世界、BlockState、VoxelShape 或外部读取世界的回调。快照源和邮箱有所属线程检查；配置倍率通过每个任务独立的快照上下文读取。
- **线程池与背压**：全服共用 2–4 个守护工作线程，控制预测与几何候选分开排队，总排队上限 64；每女仆最多 24 个待处理任务。队列满时退让，不在主线程补算或等待 Future。任务以原子取消标记协作中止，排队任务可以移除；目标/阶段变化、卸载、释放和关服会取消或清理任务。异常结果丢弃，主线程限频记录错误。
- **采集预算与复用**：按需采集 `4×4×4` 单元及一格形状边缘，空区段快速跳过。全服每 tick 最多 256 单元、21,600 次方块读取，并在累计采集耗时约 2 ms 后停止继续采集；单元不可拆分，因此时间预算是软上限，且不包含整个 Mod 的全部主线程工作。活跃女仆轮转分配；每女仆缓存最多 768 单元，20 ticks 后分批刷新、80 ticks 后过期。未知或未加载空间不能作为安全通道执行；探索计算只负责请求所需数据，优先预取选中走廊及邻近单元。
- **结果有效性**：generation 隔离目标 UUID、逻辑阶段、配置版本及维度；应用前检查任务参数、目标运动、年龄和女仆位置/航向/速度。普通结果最多复用 4 ticks，坡面分析最多 20 ticks，候选几何短暂保留后必须重新验证接入。曲线接入的姿态和年龄校验更严格。控制结果滚动预计算；恢复原点的小幅移动使用有界容差，避免女仆每 tick 移动导致补能结果永远失效。
- **等待期间与状态衔接**：等待候选时保持平缓的转向指令，等待离场搜索时停止沿用俯冲姿态；即时真实身体扫掠作为最后安全防线。已验证的火箭起始姿态可直接随实际推进生效，避免新火箭与旧姿态失配。连续转弯回放还暴露了旧“末端切线平面 = 已掠过”的问题：弧线起点可能在平面另一侧，导致刚承诺攻击就立即退出；现在结合曲线进度判断，并增加针对性回归。
- **分配与调试**：复用不变的快照映射；候选比较时不为每条被淘汰路线创建完整逐 tick 轨迹，只保留最终选中路线。曲线投影减少临时向量，火箭寿命评估整批提交。调试通道读取已完成的预测，不重复运行规划；HUD 增加异步等待、generation、缓存、队列、累计采集/工作线程耗时、失效和错误计数，debuglog 默认关闭。
- **验证范围**：新增 `verifyAsyncPlanning`、`verifyAsyncSweep`、`verifyPlannerLoad`。检查不可变快照、主线程访问边界、取消与陈旧结果、配置隔离、队列限额、服务重启、冷快照获取以及 24 会话公平性。原生产控制器生命周期、复杂地形和指挥镜赶路回放使用实际线程池及离线物理夹具，不以同步执行器代替。最终 `build --continue` 成功，22 项验证全部通过，日志 `tools/v060-release.log`。
- **全流程结果**：21 个控制器生命周期场景全部通过；跟随连续作战命中 3 次后第 300 tick 落地，最长烟花场景第 91 tick 开始攻击、最终命中并在第 155 tick 落地。目标消失和工作取消均零误击。两侧遮挡山腰曲线均第 53 tick 命中，分别第 136/134 tick 落地；2 个低能量/上升进场和 7 个远程赶路交接场景也通过，包含 900 格与创造 100 倍速。赶路库存消耗为零。逐 tick 记录位于 `build/reports/controller-replay/`。
- **合成调度负载**：24 会话、100 ticks，主线程调度 p50 0.443 ms / p95 2.035 ms / 最大 28.024 ms，工作线程累计计算 998.610 ms；所有会话获得采集机会，队列、任务和缓存均未超限，关服清理通过。报告 `build/reports/async-planner-load.txt`。此数据包含冷启动/JIT及合成空碰撞源，不是 MC 世界性能基准，也不是 GC 测量或优化前后对比。
- **实际边界**：没有实机 MSPT 或 GC 前后对比，不把合成负载数据当成服务器提速幅度。当前 tick 实际位移扫掠、最终伤害射线、目标扫描及着陆查询保留主线程；创造 100 倍速的实际长距离扫掠仍需实机评估。README 已更新架构、限制与验证入口。
- **交付**：`build/libs/winefox_skyfarer-1.21.1-0.6.0.jar`，422,235 bytes；SHA-256：`0769B7F6B3420D5B2CDE4E43DAE65FDF7184CE9589C722A48F6514334224C92A`。已核对版本元数据及 36 个异步模块类，Jar 不含离线回放、验证类或依赖模拟类。客户端与服务端请同时更新。

### 0.5.4 复杂地形持续转弯攻击（2026-10-04）

- **问题与入口**：原攻击入口要求大致对准目标，并主要沿固定水平朝向构建竖向俯冲/坡面路线，容易提前排除山外持续转弯的机会。现在在 APPROACH 与重定位的安全评估点增加有界候选搜索，无需先对准目标 25° 内；选定曲线后由独立曲线追踪继续接近，不再套用固定攻击直线的横向偏差退出条件。
- **几何候选**：新增 `SweepingAttackPlanner`，每轮生成直线、左/右弧线、左/右持续转弯切入共五类、最多 108 条候选，枚举接触方向、起始手柄长度、接触高度及左右身体偏置。两段三次 Bezier 连接进场与离场，起点切线参考当前三维速度，接触点两侧位置及导数相同（C1 连续）；离场继续向侧面弯出，不强制回到原攻击直线。不同初始高度自然形成爬升、平飞或下降的曲线。身体轨迹与长矛接触分开验证，可以从目标旁边擦过。
- **动态评分与硬约束**：先检查采样曲线的实际身体通道，再用原版鞘翅/有限火箭方程闭环回放接近、实际射程接触与后续离场。只接受预测可命中且后续身体扫掠安全的候选；评分计入时间、净空、实际转向幅度、反转当前转向趋势的成本，直线仅有 1.5 的简单性优势。原有竖向俯冲/坡面路线继续以动态命中、储备及净空参与比较，不以几何上有直线就强制优先。正常战斗只控制朝向，不直接改变速度。
- **遮挡与实际伤害**：首次自动索敌仍要求目标可见，原有地下洞穴过滤保持；已锁定目标在曲线进场中允许暂时被山肩遮挡。候选只访问已加载、实体可更新范围内的真实碰撞形状，不按方块名或目标周围障碍数量排斥路线。最终预测接触需同时满足原生蓄力、速度、长矛范围及通向目标身体的射线畅通；实际伤害仍交给 Spears，原有扩展命中去重不变。
- **执行与离场**：曲线跟踪补偿鞘翅速度方向相对视线的转弯滞后，仅在长矛最终接触附近做直指目标的修正，避免提前切向山体。修正预测与实际每 tick 俯仰速率不一致；在承诺攻击时开始蓄力，避免预测接触早于实际就绪。命中后顺势沿曲线出口离场；需要补能时验证库存火箭的全部随机寿命及无动力恢复段，冷却与消耗保持。保留原有 Charge 尾段与安全二段追击；没有次要机会时沿曲线离场。目标大幅移动、超时或通道改变时回到既有恢复逻辑。
- **预算与调试**：每 12 ticks 最多搜索一次，目标距离限定在 6–64 格；候选碰撞缓存独立于本 tick 实际移动缓存，避免试探耗尽缓存后影响紧急避障。调试棒新增青绿色拟合曲线，显示完整进场/接触/离场；黄色线框通道仍显示当前物理预测身体体积，蓝色引导线和绿色入口方块保持。HUD 原因行增加曲线类型；支持现有多女仆绑定、缓存及卸载显示。
- **离线回归**：新增 `verifySweepingAttacks`，覆盖五类候选、不同初始高度/三维切线、C1 连接、遮挡接近、最终接触射线拒绝、身体阻塞/未加载拒绝。新增 `verifySweepingReplay`，用实际控制器回放“高山壁 + 遮挡山肩 + 山腰实体台阶”的左右镜像场景；目标起初不可见但已锁定，两侧均第 54 tick 命中，持续向外离场，分别第 134/137 tick 落地，全程身体扫掠无碰撞。原有目标消失/工作取消回放改为在 DIVE 或 SPEAR_CHARGE 时触发，以覆盖同 tick 进入蓄力的路径，保持零误击断言。21 个原生命周期、2 个低能量/上升进场、7 个赶路生命周期及其他回归全部通过。
- **构建与边界**：完整 `build` 成功，19 项验证全部通过；日志 `tools/v054-final.log`，新场景逐帧与事件日志在 `build/reports/controller-replay/sweeping-hill-*.tsv` / `.events.log`。测试使用合成地形与外部 API 离线夹具，不是截图世界的精确复原；真实帧率、地形覆盖及渲染效果待用户实测。README 已同步行为、调试图例与验证入口。未安装或启动 Minecraft。
- **交付**：`build/libs/winefox_skyfarer-1.21.1-0.5.4.jar`，336,935 bytes；SHA-256：`F5D89D9B0C4DFC8ACD84D6CC9605C07F1804A73655394FE5FB950FD31A1845B6`。客户端与服务端请同时更新。

### 0.5.3 攻击补能、标记优先与创造高速赶路（2026-10-04）

- **日志定位**：只读检查用户游戏目录的 `latest.log` / `debug.log`。18:11:36 的 DIVE 水平速度约 0.288、能量储备约 −0.709、没有活动火箭；18:12:16.560 山峰目标进场时水平速度约 0.351、储备约 −0.344，随后出现 `LOW_ENERGY_RECOVERY_TURN`。说明攻击确实在低能量状态下拖延后转恢复，但旧日志不足以单独确定每一次火箭拒绝的原因，不能全部归因于冷却。离线复现及源码检查发现：预测器在预计刺中后仍继续攻击线，实际控制器却会立即转入离场；因此原定离场与完整火箭寿命的预测不一致，会错误否决部分攻击补能。
- **补能修正**：轨迹评估和火箭预测在预计长矛接触时切换到离场航线，同时尊重原生蓄力就绪时间。仍在较远处、低闭合速度且无动力预测不能命中的攻击，会提前评估补能；若原离场无法容纳完整推进周期，尝试直线/左右转弯的替代离场，保持原攻击接触线。进入已承诺攻击时提前开始原生蓄力，避免火箭缩短接近时间后长矛尚未就绪。保持原冷却、消耗、火箭寿命选择及身体碰撞硬约束，正常战斗没有直接改写速度。调试日志补充 `energyDecision`、`pulseReview`、`cooldown`、接触距离/高度和坡度，后续可区分冷却、方向未对齐、碰撞预测、离场或命中预测拒绝。
- **由下往上攻击**：起飞脱离结束后，如果目标在上方、航向和进入距离允许，可以直接汇入攻击，不再必须先升到目标上方。不是强制每轮爬升攻击；仍根据当前动量、入口、长矛就绪和完整离场可行性选择。新增实际控制器空中进场回放，明确检查女仆低于目标时处于上升攻击姿态，并最终命中、降落。
- **标记最高层级与新指令**：标记成为独立最高战略层级，高于上一目标/主人或 Home 保护范围及普通目标；各层继续幻翼优先、路线择优和既有失败换目标。标记列表变化会在当前已承诺攻击结束后重新分配；增加逐女仆 tick 的安全交接，避免周期调度错过短暂离场阶段。远程赶路仍固定当前目标；已到达的目标再次远离时可从空中重新进入赶路，无需先落地。每只女仆独立选线，不按标记序号强制扎堆。
- **创造召唤范围**：新增 `command.creative_command_range=1000`，对跟随与 Home 女仆都生效，以玩家与女仆的三维距离计算；设置 `0` 表示无限距离，正数不设人为距离上限。未加载注册表候选在添加唤醒票据前也检查范围，实际加载后再核实归属与资格；已接受任务不会因玩家后来移远而中途取消。生存镜范围规则保持原样。
- **创造高速赶路**：新增 `command.creative_transit_speed_multiplier=2`，允许 1–100；`command.creative_transit_height=200`，允许 −64–1024，实际高度受维度顶棚、目标高度和短途航程约束。创造赶路单独按倍率设置移动速度，尽量直线高空前进，水平距离约 64 格开始向目标下降，三维距离 32 格交接战斗前将速度限制到正常范围并移除免费推进。临近目标自动减速；每段移动逐步检查完整身体通道和实体可更新区块，障碍/加载延迟时缩短步长或回退局部避障，不穿过未检查区间。当前与前方票据窗口按速度扩展、最多 480 格，离开即释放。赶路不消耗背包烟花；生存镜仍使用原物理推进与 48 格交接，正常战斗不应用倍率。
- **配置命令**：`/wfs config set creative_command_range 1000`；无限距离用 `/wfs config set creative_command_range 0`；`/wfs config set creative_transit_speed_multiplier 2`；`/wfs config set creative_transit_height 200`。已有调试显示及默认关闭的 debuglog 保持。
- **验证**：完整 `build` 成功，17 项离线验证全部通过。包含 21 个真实控制器地面起飞到攻击/返航/降落回放、2 个针对性空中进场回放，以及 7 个赶路到攻击/降落回放。低能量山峰案例第 16 tick 命中、第 122 tick 落地；下方上升案例第 18 tick 命中、第 118 tick 落地。900 格创造赶路 2 倍/100 倍案例分别在第 315/38 tick 交接，并继续命中、降落，赶路库存消耗均为零。补充标记分层、召唤边界、指令刷新、100 倍扫掠碰撞/未加载边界、临近目标减速及票据上限检查。构建日志：`tools/v053-final.log`；针对性回放：`tools/v053-entry-check.log`。这些是合成离线场景，不是用户录制世界的逐帧重演。
- **交付**：`build/libs/winefox_skyfarer-1.21.1-0.5.3.jar`，318,965 bytes；SHA-256：`30A297BECCE8C9647E1D424D7B6DE78E2099A588D41772C89AD4312863B0D5B8`。未安装或启动 Minecraft；实际新标记调度、真实区块加载时序和高速网络同步效果仍由用户游戏内测试，客户端与服务端请同时更新。

### 0.5.2 多女仆调试与远程管理（2026-10-04）

- **多绑定与卸载保留**：每位玩家维护独立、有序的女仆 UUID 列表和逐只快照。近距离右键女仆切换绑定，新增绑定自动选中；左上角只显示当前女仆，默认方向键 ← / → 循环切换，可在游戏按键设置中修改。收起调试棒不清除显示，其他女仆的同步也不会抢走当前选择。实体卸载后保留最后位置、状态及预测数据，在蓝色名称后附金黄色“（已卸载）”；重新加载时更新同一 UUID 的信息。跨维度仍可查看 HUD。取消绑定只删除该女仆，退出连接清空本次观察列表。
- **预测与高亮**：当前维度内所有绑定女仆继续显示各自红色目标线、蓝色航线及黄色碰撞通道。当前 HUD 选中女仆增加亮绿色四角框，独立绘制批次关闭深度测试，线宽按原版线宽的 3 倍随窗口缩放；可穿墙。实体不在客户端追踪范围时使用服务端坐标，卸载时使用最后快照。
- **远程面板**：右键空气打开，默认列出自己的所有女仆，不限骑空士工作模式。每行显示名称、完整 UUID、坐标、维度、距离、绑定复选框及两个传送按钮；跨维度距离为 `(None)`，未加载记录注明最后记录。支持分页、刷新、玩家下拉列表和按玩家名/UUID 搜索。保持已有权限规则：普通玩家管理自己的女仆，OP 2 可切换其他玩家；操作包由服务端会话和真实所有者校验。面板关闭后异步结果不会重新弹出窗口。
- **数据来源**：为原有轻量注册表补充名称，兼容旧记录；从当前依赖 TLM 1.5.3 的 `MaidWorldData.getInfos(UUID)` 引入未加载女仆，通过只读 `infos` Accessor 获取离线所有者集合，玩家名优先来自在线资料或本地缓存。实际检查发现无女仆时 `getInfos` 可返回 null，已处理。当前记录缺失但仍在调试缓存中的女仆也能从面板取消绑定。
- **双向传送**：使用已核对的原版命令式 `teleportTo(ServerLevel, …)`，支持跨维度；TLM 只覆盖传送门式 `changeDimension`，没有覆盖此命令方法，无需绕过其传送门逻辑。独立 `debug_remote` TicketController 临时加载女仆记录区块周围 3×3 区域，每位玩家只允许一个等待中的传送，最多 100 ticks；在实际实体加载后再校验 UUID/所有者并寻找无碰撞、无液体落点，优先有支撑位置，飞行中的对象可使用邻近空中位置。传送、失败、退出、关服前均释放票据，重启回调也清理旧票据。移动女仆前释放当前空战/创造调度与待回家状态，但不修改其 Home 区域或长期工作设置，后续仍遵守原 AI 行为。
- **攻击入口**：只为控制器已接受的攻击走廊记录入口与切线方向。坡面使用实际汇入点；俯冲和普通进场使用通过检查的起始位置及后续方向。用边长一格的绿色线框立方体标出，中心引出两格方向线，贯穿该次进场/攻击；新一轮重新规划，不改飞控姿态、路线评分或能量决策。
- **验证**：完整 `build` 成功，16 项验证全部通过，包含原有 18 个真实控制器全生命周期回放、4 个远程调度回放，以及新增多绑定、循环切换、卸载快照、重载更新、单独解绑、退出清理、TLM 数据字段和真实跨维度 API 字节码检查。新增服务端公共类无客户端 GUI/渲染类调用检查。构建日志：`tools/v052-final.log`；调试检查日志：`tools/v052-debug-check.log`。
- **交付**：`build/libs/winefox_skyfarer-1.21.1-0.5.2.jar`，309,228 bytes；SHA-256：`1708636A41F35208E9A678128E4EAEA44E510E7A0D0A175021D81600DCD674AF`。未安装或启动 Minecraft，实机界面、渲染与区块/传送事件时序待用户测试；客户端与服务端需同时更新到此版本（网络协议升为 3）。

### 0.5.1 标记反馈、友好目标、创造驻留与预测倍率（2026-10-04）

- **显示与音效**：头顶符号改为 `▼`；生存镜标记使用更亮的红色 `#FF8070`，创造镜整体使用亮紫色 `#E58CFF`，包含方框、符号和编号。服务端为每个标记同步来源类型，颜色不随玩家后来切换手持物变化。新增目标的方框在约 0.35 秒内从 1.8 倍大小收缩至正常大小；动画时间按 UUID 保存，周期同步不会重新播放。成功标记使用原版经验球拾取音，取消使用按钮点击音，仅通知操作者；无效点击不播放成功音。穿墙仍只显示头顶符号。
- **友好目标**：移除标记射线对友好生物的排除，显式标记能够越过女仆的盟友/普通攻击偏好过滤；选敌和长矛扩展命中共用同一判定。原生 Spears 的可被命中、无敌、同载具等检查继续执行，没有修改 Spears Jar 或增加独立伤害调用。标记授权查询与候选列表分离，避免递归调用。未标记/已取消标记的友好生物恢复保护，仍禁止攻击自身、主人及玩家。
- **落地回家根因**：重新检查 TLM 1.5.3 的 `SchedulePos.tick(EntityMaid)`：每 40 ticks 可以执行超出 Home 范围的回收，之前仅在实际空中控制时阻断。落地后回收恢复，但订单的 `transit=false` 没有恢复，导致传回远处后仍被当作已经抵达战场。现在专门阻断有效创造任务期间的 Home 日程移动/回收，不更改 Home 坐标，也不全局禁用 TLM 的回家功能。订单存在且仍有至少一个“创造镜标记、存活、服务端实际已加载”的目标时，即使落地也保留任务；未加载快照不算有效驻留目标。
- **重返与收尾**：若被外部机制移回远处，地面女仆在超过 48 格时创建新的赶路控制器，避免复用已经结束的起飞计时。中途落地、短期无法起飞或战斗烟花耗尽不会单独解除有效创造驻留。目标全部失效、取消或卸载后，停止订单并使用实际依赖提供的 `TeleportHelper.teleportToRestrictCenter` 执行安全回收；原生日程依赖实体 tick，无法保证女仆已经卸载后还能执行，所以先保留当前位置与 Home 周边必要票据，再尝试回收，成功后释放。失败每 10 ticks 重试、最多 200 ticks；若 Home 周围无安全位置，不强行传入方块，结束临时票据并记录调试结果。退出/维度变化/关服同样清理收尾。
- **预测倍率**：此前没有该参数。新增 `flight.prediction_length_multiplier=1`，允许 1–10，可用 `/wfs config set prediction_length_multiplier 2` 调整。普通滑翔基准 24 ticks，10 倍最多 240 ticks；紧凑转向、起飞/水面恢复按各自基准扩展。增加真实身体碰撞查询和候选路线评分，黄色通道同步展示更远预测，不是单纯拉长线段。倍率表示预测时间，空间距离随速度、转向和碰撞截止变化。地形缓存预算及创造任务前方窗口随倍率扩展；前方窗口从基准 48 格至最大 480 格，保持滚动释放。
- **高倍率边界**：离线检查发现，把整段 10 倍预测都视为此后永不补能的硬承诺，会否决安全短脉冲起飞。修正为保留原近段和本次火箭寿命加恢复段的硬约束，新增远段按递减权重计入未来风险。未决定的下一次补能不会被当作确定缺失；也不会虚构未来免费能量。近处碰撞仍强制拒绝，实际障碍/未加载边界会截断通道。默认 1 倍保持现有控制窗口。
- **验证**：完整 `build` 成功，15 项离线验证任务通过。18 个原有完整控制器场景与默认倍率回归保持；新增友好标记生物的 180 格完整赶路、攻击、离场、降落场景，交接/命中/落地为 107/134/150 ticks，赶路烟花库存消耗为零。检查覆盖取消标记恢复友好保护、主人/自身排除、创造驻留四条件全部 16 种组合、实际 Home 注入入口与传送 API 签名、1/10 倍的 24/240 次真实采样、10 倍安全起飞与近处硬碰撞拒绝、有界远程票据窗口。构建日志：`tools/v051-final.log`。
- **验证边界与交付**：未安装或启动 Minecraft。显示效果、真实 Home Mixin 注入、区块卸载和传送事件时序由用户实测；离线检查不代替这些验证。产物 `build/libs/winefox_skyfarer-1.21.1-0.5.1.jar`，276,693 bytes；SHA-256：`66767EB5B45937F3E0216DBCDBA3E39CE41183788CDB4B8D5C2936ECDDC41926`。

### 0.5.0 指挥镜、远程调度与碰撞通道（2026-10-04）

- **物品与操作**：新增 `winefox_skyfarer:command_spyglass`（骑空士指挥镜）、`winefox_skyfarer:creative_command_spyglass`（创造骑空士指挥镜），工具创造物品栏可取，无合成配方。继承原版 `SpyglassItem` 的缩放能力、声音、使用动作和统计；补齐原版 ItemRenderer 对望远镜物品的模型识别，保留背包图标和手持立体模型。按用户确认采用左键切换标记、右键始终缩放；开镜期间也能左键。服务端根据持有物、玩家姿态和视线自行验证，不接受客户端指定实体或所有者。
- **五目标列表**：每位玩家的两种镜子共用最多五个标记，重复标记取消，第六个按 FIFO 移除最早项。标记序号只是显示顺序。私有同步红橙四角框、头顶空心菱形与编号；被方块遮挡时仅保留菱形。标记射线停止于遮挡/未加载区域，不为搜索目标生成新区块；玩家、女仆及盟友不能被标记。死亡、失效和玩家换维度/退出会清理，未加载目标不会被固定时间上限错误当成死亡。
- **呼叫与选择**：生存镜呼叫自己的跟随骑空士及默认 128 格内 Home 骑空士；创造镜要求创造模式，可呼叫同维度任意距离的自有 Home 骑空士。坐下、睡眠、无 AI、骑乘、拴绳和打开 GUI 时不调度，装备仍需满足战斗要求。每位女仆分别评估距离、动量方向、原有路线评分和上一目标，加入标记加分；近处战斗继续保留原战略层级、幻翼同层优先、失败换目标和同轮命中去重。远程途中固定自己的目标，进入 48 格后交接现有攻击控制器；完成一项后可以继续其他标记，不强制先落地。
- **远程滑翔**：独立 TransitFlight 只作短距离局部碰撞预测，每五 ticks 更新常规计划并逐 tick 检查下一步。远程推进使用实际烟花实体，不扣背包库存，不改写速度；显式设置 Flight 0，与 11–22 ticks 的脉冲预测一致（原版物品默认实际为 Flight 1，已核对并修正）。进入战斗时移除免费推进，恢复原消耗逻辑。支持原有身体走廊起飞与水面恢复思路，异常路径回退降落；起飞前为新票据的实体更新就绪状态提供有限等待，不悬停冻结空中实体。
- **轻量注册表与区块**：SavedData 按主人记录女仆 UUID、维度、位置、已核实标志、Home 和骑空士状态，加载/定期更新/离开时刷新；旧存档从 `MaidWorldData` 引入未知记录，再有限唤醒核实真实实体。每轮最多唤醒两位、同时最多八位，失败唤醒会释放。正式注册 `TicketController`，按女仆 UUID 加实体可更新票据。创造调度仅维持当前位置和前方最多 48 格的窗口（含预测边界邻格），旧窗口离开即释放，不积累整条旅程；生存调度不保留发现用的加载票据。取消、失效、离开、玩家退出与关服清理；重启验证回调清除旧指令票据，不恢复旧任务。
- **Home 收尾**：没有后续任务时在战斗区域降落，恢复 TLM 地面逻辑。已检查依赖 `SchedulePos.tick`：原生工作区域回收每 40 ticks 检查并尊重 `canBrainMoving`；创造远程任务落地后至多保留当前窗口 45 ticks，给原生回收机会，然后释放。没有修改 Home 工作中心。
- **黄色通道**：现有 HUD、红色目标线和蓝色引导线保留。实际滑翔、紧凑转向、起飞/水面恢复及远程赶路的选中预测轨迹可同步给当前调试观察者；以四条纵向黄线和每六个预测点的截面框表现身体尺寸与实际安全余量。仅调试观察时生成可视化数据，不铺满三角面。
- **新增配置**：`command.command_mark_range=256`（16–512）、`command.command_home_range=128`（16–512）、`command.command_mark_bonus=150`（0–1000），也可通过 `/wfs config set` 调整。debuglog 仍默认关闭；开启后增加 COMMAND_ASSIGN、COMMAND_TRANSIT 和 COMMAND_RELEASE 记录。网络协议升级为 2，客户端与服务端应同时更新。
- **验证**：15 项离线验证任务全部通过，包括原有 18 个生产控制器完整生命周期场景和新增三组生产远程赶路→战斗交接→命中→离场→降落回放。180 格正向场景在 92/122/146 ticks 交接/命中/落地；900 格反向雪层场景为 611/637/648；256 格侧向场景为 161/195/218。赶路库存消耗均为零，交接时免费火箭均已结束，无非计划触地。另验证五项 FIFO/切换、玩家列表隔离、128/48 格边界、航向/上一目标评分、负坐标票据窗口与长距离有界性、预测轨迹首步一致性，以及实际 NeoForge/TLM 字节码中的望远镜模型/缩放与 Home 回收挂钩。
- **验证边界**：没有安装或运行 Minecraft。离线回放替换外部实体/世界 API，不能证明实际网络输入、客户端画面或 NeoForge 异步区块加载的运行表现；这些仍由用户实测。夹具与 Minecraft 模拟 API 未打进交付 Jar。构建日志：`tools/v050-release.log`；回放说明：`src/replay/README.md`。
- **产物**：`build/libs/winefox_skyfarer-1.21.1-0.5.0.jar`，270,036 bytes；SHA-256：`F5FF8E7AEDF9CDBF09680F51E63A9D89795E56C867F6CA7249F3BAFD71449BF9`。最终 `build` 成功（24 秒），Jar 内语言/模型/Mixin JSON 校验通过，未包含回放夹具。

### 0.4.7 半高地形、可见目标与瞄准偏移（2026-10-04）

- **日志依据**：只读检查指定游戏实例 `latest.log` 中 10:34:54–10:39:15 的飞控记录。共见 20 次 `COMPACT_TURN_REASSESS`、19 次 `TOO_CLOSE_EXTEND_AGAIN`、17 次 `UNSTABLE_ENTRY_EXTEND_AGAIN`；10:35:08 刚结束重定位后补入一发约 24 ticks 的火箭，随即重新进入入口不稳定的重定位。日志没有逐方块碰撞形状，不能单凭雪地表现断言存在高度取整错误。
- **半高形状修正**：核对发现原实现已经读取 `getCollisionShape(...).toAabbs()` 和射线实际命中坐标，没有把半砖/雪层统一取整到一格。实际缺陷在支撑面选择：它可能把被薄层覆盖的底面当成最近支撑。现在从真实形状中选取未被覆盖、上方能容纳身体的顶面；覆盖下半砖、上半砖下不足身体高度的夹缝、阶梯和各类分数高度。不硬编码方块名称，保留实际小数坐标；独立测试验证 1/8 至 7/8 高度、上下台阶和屋顶不会被错误当作脚下支撑。
- **扩大采样**：`slope_sample_radius` 默认从 6 改为 **16**，上限从 12 改为 **32**，移除采样器内部旧的 12 格截断。支撑面查询改为窄列加一格形状外伸余量，不为每个采样点填充整块 4³ 空间缓存；继续与碰撞走廊共享实际方块形状，不加载新区块。近处与远处坡面拟合仍分别保留。
- **减少重定位犹豫**：重定位途中一旦已有足够距离和对齐机会，即可同 tick 评估攻击，不必完成预设外侧入口点的绕行。正常进场滑翔仍安全、已有可用速度时，若点火会因新的火箭速度/前摇/俯冲距离预算使入口变得过近，则不为名义储备补这一发火箭；真正不安全的低能量恢复仍走原安全流程。没有扩大现有作战区域、取消碰撞检查或强制锁定命中。
- **不可见目标**：新主目标获取与保留、二段攻击新目标选择加入真实身体可见性检查。中心或上部身体任一点可见即可参与，避免矮遮挡露头的怪物被整体排除。完全不可见的洞穴怪物不能靠上次目标加权或失败重试回退重新成为候选。已锁定攻击的基础有效性仍与可见性分开，短暂遮挡不会直接撤销整轮攻击；身体走廊仍负责安全判断。原分层优先级与失败换目标规则不变。
- **瞄准偏移**：新增 `combat.attack_aim_offset=0.5`，范围 0–3 格，可执行 `/wfs config set attack_aim_offset 0.5`。进场、拦截和接触高度使用目标碰撞箱中心加此偏移，并继续保留最低地面净空。这个参数不会移动/扩大怪物碰撞箱，不改变原生长矛判定、女仆竖向扩展和同轮去重。
- **轻微平滑**：普通重定位最后不足 12° 的朝向修正幅度减少 15%；普通姿态变化的软成本增加 15%。预测与执行使用同一调整，大幅转向速率及紧急避障能力保留。这里的 15% 是控制系数调整，不宣称视觉观感能精确量化为 15%。
- **诊断**：默认关闭的 debuglog 和现有 HUD 布局保持。重定位日志额外记录实际入口距离、对齐角度、入口阶段、固定高度、目标脚部与采样支撑面的精确 Y、近/远坡度、采样数、半径和瞄准偏移，便于下一次实测定位剩余犹豫原因。
- **完整回放**：保留 0.4.6 的 12 个从起飞到降落的生产控制器场景，新增雪薄层、混合分数高度、半砖、起跳前带侧向滑动惯性、32 格采样、地面与地下目标并存六个场景，共 **18 个**。新增场景均在第 78 tick 进入攻击并命中：混合半高地形第 225 tick 落地，冰面惯性第 228 tick 落地，其余第 230 tick 落地。另验证只有洞穴怪物时连续 120 ticks 不起飞/不耗烟花、隐藏的历史优先目标不能重新被选中、矮遮挡露头仍可选择，以及 offset 0/0.5/1 的实时设置。场景只代表夹具几何与物理下的结果，不能替代用户雪原/冰面现场或所有模组方块实测。
- **构建验证**：`tools/build.ps1 -Stage v047-final -Tasks @('build')` 成功，全部 **13 项**离线验证任务通过，包括原有陡坡、夹层、表面起飞、长矛去重及 YSM 接口检查。日志见 [v047-final.log](tools/v047-final.log)，逐 tick 场景记录仍在 `build/reports/controller-replay/`。真实依赖编译通过，Jar 不含离线夹具、验证类或依赖 API 模拟类。未安装或启动 Minecraft，未改动游戏实例。
- **已有配置**：新增 offset 会使用 0.5 默认值；已有 `slope_sample_radius` 数值会保留。使用 `/wfs config set slope_sample_radius 16` 可切换到新的默认范围，最大可设为 32；无需重置全部配置。
- **交付文件**：[winefox_skyfarer-1.21.1-0.4.7.jar](build/libs/winefox_skyfarer-1.21.1-0.4.7.jar)，219,442 字节。SHA-256：`116E0C712DCCB4A2E02CE9E4662A9FD7C734C65D1535F85A4F294BACC88D9176`。


### 0.4.6 固定攻击入口与完整状态机回放（2026-10-04）

- **日志与旧版复现**：只读检查指定游戏实例的最新日志。可见 `REPOSITION -> APPROACH: COMPACT_TURN_ALIGNED` 后立即再次 `UNSTABLE_ENTRY_EXTEND_AGAIN`，以及 LANDING 中仍执行 `COMPACT_CLIMB_PULSE`、高度约 Y=313 的异常。旧版通过的回放是分段物理检查，没有证明真实控制器各阶段能够衔接。这次新增完整控制器夹具后，先在未修复逻辑上复现近距离目标场景：没有进入攻击，高度超过 55 格上限而失败。原始证据保留在 [v046-baseline.log](tools/v046-baseline.log)、[逐 tick 轨迹](tools/v046-baseline-flat-near.tsv) 与 [状态日志](tools/v046-baseline-flat-near.events.log)。
- **入口矛盾与高度累加**：原重定位一直朝怪物中心收拢，入场条件却要求足够的前摇/俯冲距离；同时重试不断以当前高度再加爬升量。现在先确定目标周围的固定入口区域，抵达或已取得足够外侧距离时再向内转入。预测中的外侧/内侧切换与实际控制采用同一规则。入口高度以目标和接近高度为基准，不随重试累计抬高；仍验证身体走廊、烟花寿命和加载/作战边界。普通入口转向允许 32–48 格的局部走廊，不再用过小的固定半径否决本来可到达的入口。
- **有限爬升减速**：剩余火箭不再单独触发持续陡仰；只有固定高度目标尚未满足且确实需要转向时，才将动能换成高度。慢速转向允许更快调整朝向。`COMPACT_TURN_ALIGNED` 同时检查朝向和当前速度下的实际入场距离，不能只对准中心就宣布准备完成。单次重定位最多 100 ticks 后重新评估。
- **完整交接**：进入重定位当 tick 就计算并应用新航线，避免“离场 → 进场 → 重定位”仍沿用上一阶段的计划。离场到达空间/航程上限时立即评估下一路线，不再为追逐已在身后的旧出口而继续盘旋；Charge 保留时间不控制离场长度。低能量攻击/离场若无法安全使用直线脉冲，则尝试朝固定局部目标恢复的完整转向脉冲，只有全部可能火箭寿命均安全才发射，并立即终止旧阶段的后续处理。没有改写实体速度、凭空增加推进或修改 Spears 本体。
- **返航和降落**：返航转向高度固定相对于主人/Home，走廊包含真正的返航目的地及转向余量，避免目的地在本身半径之外。提前识别低能量下降；无地面采样得到的 NaN 不再视作必须补能的低净空。LANDING 不再进入转向爬升补能或重新接敌路径。无敌人且能量不足时提前返航，避免低速索敌盘旋到触地。
- **回放方法**：`verifyControllerReplay` 在独立 classpath 上直接编译并运行生产控制器、AI、规划器、HeadingController、FireworkManager、配置及调试快照。只用夹具替换外部世界/实体/库存/武器 API；测试不赋值控制器状态。每 tick 执行真实控制器决策和鞘翅物理，保留同 tick 的中间转换，命中需通过前摇、速度、矛线相交和无遮挡检查。新增失败条件包括无攻击/命中进展、异常升高/飞远、非降落触地、身体碰撞、依赖紧急传送，以及降落后重新攻击/补能。方法与范围见 [回放说明](src/replay/README.md)。

完整控制器回放结果如下。时间均为模拟 tick（20 ticks = 1 秒），高度为相对起飞地面的最高脚部高度，半径为距离初始作战中心的最大水平距离；“首次攻击”指进入攻击流程，并非第一次造成伤害。

| 场景 | 首次攻击 | 命中目标数 | 落地 tick | 最高高度 | 最大半径 | 消耗烟花 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 近距离目标 | 78 | 1 | 207 | 14.71 | 30.56 | 2 |
| 普通距离目标 | 79 | 1 | 229 | 14.71 | 34.89 | 2 |
| 背向目标、长推进 | 101 | 1 | 230 | 14.71 | 31.31 | 2 |
| Home 模式连续三目标 | 77 | 3 | 421 | 14.71 | 48.43 | 4 |
| 最短推进 | 138 | 1 | 244 | 22.28 | 41.97 | 3 |
| Flight=3、最长随机推进 | 111 | 1 | 411 | 14.71 | 46.81 | 3 |
| 40 格外目标 | 156 | 1 | 220 | 45.49 | 61.98 | 2 |
| 跟随模式连续三目标 | 79 | 3 | 660 | 14.71 | 46.89 | 7 |
| 台阶坡面完整流程 | 71 | 1 | 247 | 14.71 | 59.54 | 4 |
| 小碰撞箱目标 | 79 | 1 | 229 | 14.71 | 34.89 | 2 |
| 攻击中目标消失 | 79 | 0（预期） | 124 | 14.71 | 30.56 | 1 |
| 攻击中取消工作模式 | 79 | 0（预期） | 123 | 14.71 | 30.56 | 1 |

- **验证结果与限制**：`tools/build.ps1 -Stage v046-final -Tasks @('build')` 成功，全部 **13 项**离线验证通过，包括以上 **12 个完整流程**及原有分段攻击、陡坡、夹层、冰雪/水中起飞、命中去重和 YSM 接口检查。日志见 [v046-final.log](tools/v046-final.log)，各场景逐 tick 记录及转换日志位于 `build/reports/controller-replay/`。完整流程夹具没有执行原生 Spears 伤害、真实世界加载/网络或客户端渲染；分段水中/冰雪检查也不等同于水中完整任务回放，因此不宣称已验证所有实机地形或命中率。
- **兼容与交付**：调试 HUD、默认关闭的 debuglog、原有目标优先级及长矛命中去重保留。无需重置配置；`reposition_distance` 现在参与固定入口距离上限计算，README 已同步说明。交付 Jar 不含离线夹具或模拟的 Minecraft/TLM/Spears 类。未安装或启动 Minecraft，未改动用户游戏实例。
- **交付文件**：[winefox_skyfarer-1.21.1-0.4.6.jar](build/libs/winefox_skyfarer-1.21.1-0.4.6.jar)，217,052 字节。SHA-256：`59730EF4611E46F15C86D73FA985CB712306640FFBCDFF45EF8685F77DE50FC4`。


### 0.4.5 紧凑重定位、曲线俯冲与二段追击（2026-10-04）

- **日志证据**：只读分析用户指定实例的 `latest.log`（记录时间 2026-10-03 23:25–23:29）。共出现 50 条 `ENERGY_PULSE`、11 次 `COMBAT_AREA_EMERGENCY_RANGE` 转换、5 条有效命中记录。23:27:43 命中后，23:27:44 离场期间补入约 40 ticks 火箭，随后进入重定位并连续补能，最终远离原战区。日志未给出明确的实体卸载事件；不能把返航传送造成的单 tick 大位移当成真实飞行速度。
- **移除回归来源**：删除 0.4.4 沿旧朝向持续保持到烟花熄灭的 `ENERGY_RECOVERY` 路径。它曾让状态已经切换为进场，实际却仍沿旧方向加速。取消重定位“先直飞 28 格，重试再加 8 格”的做法；Charge 不再豁免作战区域检查，也不再把应急范围扩大到数百格。
- **紧凑重定位**：`REPOSITION` 改为在固定水平范围内朝目标爬升减速、转向；有速度时把动能换成高度，已有烟花尚未结束时允许更陡地向上转移推力。低速无推进时限制过度抬头，避免强求爬升导致掉速。对准目标并具备入场距离后重新评估攻击；单次重定位超过 55 ticks 也会重新评估。正常计划受 `min(reposition_distance, 32)` 格半径约束，碰撞紧急恢复仍优先。返航掉头使用相同控制方式，不再通过直线补能扩大转弯。
- **烟花与边界**：普通补能要求与实际目标方向对齐；离场不再为维持名义巡航速度反复加速。低能量下降需要推进时，仍从真实库存选择、消费烟花，并验证所有随机推进时长及熄灭后滑翔。补能预测同时限制向作战区外扩张。按当前依赖源码确认 `ServerLevel.isPositionEntityTicking(BlockPos)`，身体走廊现在同时检查实体模拟范围与已加载方块，拒绝进入“地形仍加载、实体已不再更新”的外围区块，不强制加载新区块。
- **平地曲线俯冲**：近处及较远坡度都较小时，优先使用随目标距离连续降低、在接触点平滑收平的下降曲线，替代提前保留 14 格水平冲刺段。准备高度取至少 9 格，并根据实际速度、落差、重力及转姿时间计算入口距离。高速高落差却距离不足时先减速重定位；不靠攻击前加速强行补救。屋顶或身体走廊不允许曲线时保留旧路线回退。山坡仍由现有近/远坡面拟合与路线成本选择，不强制套用平地曲线。
- **二段攻击**：主攻击过程中每 4 ticks 在接近接触点时预测主目标接触状态，再评估次要目标及其离场空间；这些预测不会让女仆提前偏离主目标。真正命中主目标后，以当前速度、姿态、目标状态和走廊再次确认，成功才进入 `CHAIN_ATTACK`。保持同一 Charge，在左右有限转向中追踪第二个目标；命中后可评估第三个目标。未命中主目标或没有可行后续路线时直接离场，曲线俯冲/二段结束优先尝试安全爬升。
- **限制与去重**：第二、第三目标都限定在主目标锁定位置周围的 `secondary_target_radius`（默认 6 格），不沿目标链向外扩大搜索。最多主动追击两个次要目标，共享最多 40 ticks、18 格实际航程，且受原生 Charge 窗口和配置保留时间限制。二段中不额外发射追击加速烟花；能量或离场条件不足时立即结束追击，之后由恢复流程判断是否需要烟花。沿用同一轮 Spears 命中去重与主目标失败结算，不重置 Charge 来重复伤害同一目标，不改 Spears Mod。原主目标战略层级、同层幻翼优先和失败换目标规则保留。
- **诊断与配置**：HUD 布局不变，二段期间红线指向正在追击的次要目标；日志可出现 `COMPACT_TURN_ALIGNED`、`CURVED_DIVE` 对应航线、`PRIMARY_HIT_CHAIN_CONFIRMED`、`CHAIN_NEXT`、`CHAIN_RECOVERY_REQUIRED`。调试日志默认关闭。不新增必须手动迁移的配置，`secondary_target_radius` 的已有值仍保留；`post_attack_charge_ticks=0` 同时关闭二段追击。README 更新现有参数含义，构建记录仍统一保存在本文件。
- **验证范围**：新增 24 组不同速度、90°以上转向及 0/12/38/47 ticks 剩余推进的逐 tick 转向回放，检查水平范围、对准、碰撞及动能转高度；覆盖低能量有限烟花与封闭走廊拒绝。新增 9 组不同速度/高度曲线俯冲接触及收平回放、左右侧向追击和第三目标机会、能量不足/时间不足/空间封闭时拒绝追击。替换会奖励旧直线补能行为的 0.4.4 检查；保留旧攻击、坡面、冰雪/水中起飞、YSM 与命中去重回归。离线场景不代表实机命中率或全部地形已验证。
- **构建结果**：`tools/build.ps1 -Stage v045-regression -Tasks @('build')` 成功，全部 12 项离线验证任务通过，日志见 [v045-regression.log](tools/v045-regression.log)。Jar 内版本为 `0.4.5`，包含紧凑转向/曲线/追击类，不含已删除的旧补能类、离线验证类或依赖 Mod 本体。未安装或启动 Minecraft，游戏实例仅用于只读查看日志。
- **交付文件**：[winefox_skyfarer-1.21.1-0.4.5.jar](build/libs/winefox_skyfarer-1.21.1-0.4.5.jar)，215,939 字节。SHA-256：`7D1CD28DA7EC2DCBD1E8BD295E4FE7949B9FF34CAEACAF1C54839E70B1C32742`。


### 0.4.4 连续进攻、补能恢复与坡面细采样

- **缩短离场**：离场长度取旧公式 `max(breakaway_distance, clamp(speed / radians(turnRate), 8, 32) × 1.5)` 的 **2/3**。默认配置下公式最大值从 48 格降至 32 格，基础攻击航线的出口参考距离从 12 格降至 8 格。直线、侧向、爬升及原攻击走廊出口都受三维端点距离限制；到达出口或走完距离预算后，只要已脱离紧急机动，就立即进入 `ASSESS_NEXT_ATTACK_ENTRY`。仍允许必要的避障恢复优先于距离限制。
- **Charge 与离场解耦**：确认旧逻辑会在已到出口但 60 ticks Charge 尚未结束时，反复生成新的离场航点。现已移除这项延长和等待条件。剩余 Charge 可以随下一次进场继续保留，沿途仍可多穿；开始新攻击时重新启动原生举矛前摇。离场结束先结算本轮失败次数，保留同一 Charge 的命中去重记录；迟到的有效命中仍能清除失败记录，避免重复结算或重复伤害。
- **低能量补能**：普通滑翔预测不理想时不再提前跳过烟花评估，进场期间也不再仅因转向角超过 20° 就跳过所有能量检查。原航线无法安全推进时，非攻击锁定阶段可尝试沿当前朝向或动量方向、平飞或小幅爬升的恢复路线。每个库存烟花候选继续验证全部随机推进时长与停止推进后的滑翔。推进期间保持经过验证的恢复路线，可安全汇回目标方向时提前恢复正常规划；切换阶段后不会复用旧目标方向提前结束恢复，落水会清除旧恢复路线。精确攻击阶段保留较低的补能基准与原有命中/碰撞限制。
- **坡面采样**：由旧的四方向、逐格延伸到 3 格，改为八方向、默认间隔 0.5 格、半径 6 格，最多 96 个支撑面样本。沿相邻支撑面连续采样，分别拟合近 3 格与全范围坡面，比较两者生成的有效路线；增加坡度候选，并将攻击接触高度候选间隔从 0.5 格缩至 0.25 格。坡面估计最多缓存 5 ticks，目标移动或采样配置变化会提前失效；身体碰撞走廊仍实时验证。缺失支撑面或未加载区域会截断对应采样方向，不会当作平地继续延伸。
- **多穿范围**：`secondary_target_radius` 默认从 3 改为 **6 格**，只影响主目标周围的次要目标，原有战略层级、同层幻翼优先、路线择优及失败换目标机制保留。已有配置文件中的 3 不会被自动覆盖，可执行 `/wfs config set secondary_target_radius 6`。
- **新增配置**：`slope_sample_radius=6`（3–12 格）、`slope_sample_step=0.5`（0.25–1 格）。新项自动补入已有配置；调试日志仍默认关闭，HUD 布局不变。README 仅更新静态参数说明。
- **验证范围**：新增离场长度缩放/三维封顶、Charge 与离场独立结算及命中去重、连续两次未命中、斜向平面与半格台阶拟合、近远坡度差异、缺失采样、低速 90°/180° 转向补能回放，以及封闭走廊拒绝推进检查。冰雪/水面起飞、YSM 钩子、旧有坡面攻击与飞行预测回归均保留。最终构建结果与交付校验见下方。
- **构建结果**：`tools/build.ps1 -Stage v044-final -Tasks @('build')` 成功，全部 11 项离线验证任务通过，日志见 [v044-final.log](tools/v044-final.log)。未安装或启动 Minecraft，未修改用户游戏实例。Jar 内版本为 `0.4.4`，含新增规划/采样类，不包含离线验证类或依赖 Mod 本体。
- **交付文件**：[winefox_skyfarer-1.21.1-0.4.4.jar](build/libs/winefox_skyfarer-1.21.1-0.4.4.jar)，202,684 字节。SHA-256：`7E1DAD76BFF77C4B389151B1C2F5054A0EDB3777B64ED73134454477CF360394`。


### 0.4.3 地表起飞、落水恢复与 YSM 滑翔

- 起飞按实际跳跃后的速度与高度重新验证，包含冰面残留侧向动量、雪层碰撞形状。初始离地使用短暂的快速俯仰/转向控制与真实库存烟花，优先获得地面净空；低屋顶仍可选择侧向出口。离地前六个 tick 不套用普通巡航的避障流程，每 tick 仍检查实际身体的下一步扫掠；方块改变或外力使路径失效时立即恢复普通紧急规划。
- 起飞烟花选择检查该库存时长的全部 12 个随机寿命，以及停止加速后的 16 ticks 滑翔。没有要求先达到巡航水平速度才使用起飞火箭，避免低速、陡直离地被错误拒绝。位移与推进仍由原版跳跃、鞘翅和附着烟花处理。
- 当前依赖 `EntityMaid` 确实继承 `isInWaterOrBubble()`，并提供 `getSwimManager()` / `setWantToSwim(boolean)`。落水先进入 `WATER_ESCAPE`，原生游泳起跳脱离水底后开启滑翔并使用火箭；水中预测使用本版女仆实际的移动后 0.9 阻力，再切回鞘翅物理。恢复走廊只放行水，不放行实体方块、岩浆、世界边界或未加载区块。恢复成功后继续索敌或原先的返航；没有可行出口、装备/命令改变、超时时释放控制并恢复原来的游泳意图，避免无限卡在空战状态。
- 从实际 YSM 2.6.5 Jar 核对：女仆主动画状态没有 `elytra_fly`，原生鞘翅层仅支持玩家。现在在模型包含非空 `elytra_fly` 动画时由女仆主动画控制器播放；自定义主控制器也不会用站立/跳跃动作覆盖它。持物等其他控制器照常运行。模型缺少该动画时保留原有身体倾斜。
- 原生 YSM 动画已有 Root 的飞行旋转，适配时抵消公共渲染入口的额外倾斜，避免重复旋转。`ysm.elytra_rot_x/y/z` 对女仆读取原版 `ElytraModel` 的翼角，原有玩家查询不变。鞘翅外观按 YSM 的 `ElytraLocator` 骨骼链渲染真实胸部装备，保留发光附魔；模型没有挂点或挂点被缩为零时尊重模型设置。
- YSM 为可选客户端适配，精确限定已核对的 `2.6.5-neoforge+mc1.21.1`；未安装或其他版本保留姿态回退，服务端不加载 YSM 客户端类。`tools/ysm-reference.jar` 仅用于编译接口检查，不打包到成品、不作为测试客户端安装。
- 新增 60 组冰面惯性、六种雪面高度、水深及火箭时长回放，覆盖每个可能寿命的碰撞与离水检查；另检验低屋顶侧向出口与封闭水域拒绝危险推进。对五个可选 YSM 钩子核对实际 Jar 方法签名与调用点。全部原有回归保留，仍未安装或启动 Minecraft。

### 0.4.2 陡坡攻击与机动余量

- 读取新提供的 `主世界复杂地形.zip` 和 `开阔夹层10x10x2.zip`。主世界日志中有攻击转入急刹/反向的记录，完成攻击后的出口明显偏向直线爬升。日志不能单独证明每一次掠过的原因；离线回放复现了长程预测不断推迟近程俯仰修正导致的下坡掠过问题。
- 坡度采样沿每格相邻支撑面逐步延伸，避免陡坡采样点超出旧的 ±2.51 格竖向窗口后被误算成平地。候选坡度上限从 `0.8` 提升至 `1.5`，增加接触高度和侧向出口候选；侧向出口高度使用完整局部坡面的横向分量，不再沿用直线进场的爬升量。候选仍须通过实际身体走廊检查。
- 坡面攻击默认俯仰修正 `6°/tick`，向上修正 `10°/tick`；更加重视近程跟踪动作，减少“预测稍后会修正、实际一直不修正”的拖延。近距离提高原生长矛接触误差的权重，保持身体碰撞为硬约束。
- 库存中存在烟花不再把进场速度强行提升到 `1.7`。实际绑定火箭仍按其快速加速预留前摇距离；无推进时按实际速度选择较近入口和举矛时机。攻击阶段单独采用较低的能量储备和速度基准，并读取武器原生非玩家伤害速度门槛，允许牺牲速度换取角度与转弯余量。
- 攻击预测在真正穿过目标后接上选定的离场走廊，停止无限延长攻击坡线。侧向转弯的过程中保留沿山坡前进产生的净空需求，转弯与爬升可以同时发生。命中后的实际控制优先保持经过动态验证的坡面出口；出口略在侧后方也不再仅因前向点积为负被丢弃。移除“有剩余火箭就优先爬升”的固定奖励。
- 每种烟花继续检查全部 12 个随机结束时间和熄灭后滑翔。对于尚未到达的上坡目标，剩余机械能以接触/离场所需高度为参考，不以途中较低坡段为参考；需要爬升补能时不会错误地只选最短烟花。若推进会让女仆在前摇/瞄准完成前越过目标，则拒绝该烟花候选。
- 按原版水平速度损失公式估计撞墙伤害上限：`max(0, 10 × 水平速度损失 − 3)`。无推进且损失全部当前水平速度的伤害上限不超过 4 时，在极限急刹/反向之前增加有限速率修正候选，最大姿态速率 `12°/tick`。这些候选仍检查完整 24 ticks 身体扫掠；低速不会被当作允许穿墙或触地的理由，已无温和恢复通道时仍可紧急机动。
- 自动补入的新参数：`attack_minimum_boost_speed=0.4`、`attack_energy_reserve=0.02`、`slope_pitch_rate=6`、`slope_pull_up_pitch_rate=10`。前两项只控制攻击/坡面汇入的能量基准，原有巡航配置保留。无需重置配置，可用 `/wfs config set` 调整；调试日志仍默认关闭。
- 新增 36 组约 37°、45°、51° 上下坡台阶地形回放，包含低速 `0.45`、不同初速/剩余推进、真实前摇、被地形截断的矛尖接触、身体碰撞和命中后无推进离场过渡。另检查陡坡采样上限、速度损失伤害、低速有限速率避障。保留全部旧版回归。数值场景通过不代表复杂主世界地形中的实机命中率已验证。

### 0.4.1 坡面攻击、空中续战与分层索敌

- 分别检查了 `开阔地形+开阔夹层5x5.zip`、`开阔夹层10x10x2.zip`、`主世界复杂地形.zip` 中的 `latest.log` 和 `debug.log`。这些文件确认加载 0.4.0，但没有 `[WFS debug]` 飞控记录，因此不能据此断定实测返航的具体原因。下次测试前可执行 `/wfs debuglog on`；默认开关仍为关，不自动修改测试实例。
- 起飞拆为站立身体起跳净空和滑翔身体出口两个检查，不再要求头顶四格无遮挡。搜索前、侧、后方的低空出口；起跳后及最初爬升阶段允许更快的姿态调整，并使用已有急刹/反向预测。真实烟花发射仍验证库存中该时长的随机寿命与滑翔恢复路径；不会改速度、穿墙或在地面伪造飞行。
- 沿坡面攻击根据目标脚边附近支撑面估算局部坡度，生成平飞、上坡、下坡及当前动量方向附近的候选线。在候选线上选择接近当前轨迹、且满足前摇距离的汇入点，检查汇入、刺击和离场净空；按距离、转向、预计补能需求择优，没有单独的“爬升攻击/俯冲攻击更贵”惩罚。
- 坡面线使用有正负方向的高度变化，穿过目标前后继续沿坡面运动，不再套用固定 14 格平飞段或要求入场接近零升降速度。允许更陡的俯冲姿态；爬升航线需要推进时，可在验证安全后补充真实烟花。平坦地形继续使用已经验证的平飞进场流程。近距离地形变化与紧急恢复仍可中止不可行攻击，有限候选规划不等于全地图三维寻路。
- 修正瞄准预测：竖向扩展命中区很高时，矛尖线段与它的交点未必落在中心投影处。现在先检测线段与盒子的真实相交，再对未相交路线计算误差；实际伤害路径、竖向扩展尺寸、每轮去重和原生长矛伤害逻辑不变。
- 索敌始终包含已加载的本轮作战中心周围，旧目标死亡或脱离过远不会让中心敌人漏出查询。对齐/重定位超时先记录一次失败并重新选敌；无目标或距离造成的返航中继续按索敌间隔查询，可在降落前重新进入攻击。装备失效、任务切换、坐下、无燃料、滑翔中断和飞行总时长上限仍保留返航，不用续战覆盖用户命令。
- 目标排序：① 上次攻击目标与保护区域内目标同层，高于其他目标；② 同层中幻翼优先；③ 原有距离、动量转角、可见性等路线评分与同层切换阈值；④ 默认连续两次失败后避选，有其他目标时尝试替代目标，没有替代者时仍可重试。失败避选是防止反复卡住的最终约束，不让高层目标永久占位。
- 跟随模式保护主人周围 16 格；Home 模式读取 TLM `getRestrictRadius()`，保护半径为实际工作半径 + 16 格。有效作战/追击范围随之扩大，飞行紧急边界也预留相应空间；不强制加载区块。`previous_target_bonus` 兼容保留，但上次目标现通过层级表达，不再把额外分数叠加进路线分。`target_switch_threshold` 仅在相同战略层且幻翼类别相同时生效。
- 没有需要手动重置的新配置。日志的索敌摘要增加实际保护半径，坡面航线包含坡度和路线成本。调试 HUD 继续使用原布局，作战半径数值显示实际生效的范围。
- 新验证：36 组上/下坡台阶地形、不同初速与烟花剩余时长的闭环攻击，包含身体扫掠、长矛前摇与被地形截断的矛尖接触检查；两格高低屋顶、正前方有墙而侧向开放的起飞回放；战略/幻翼层级、Home 半径和允许/禁止空中重新接敌的规则。全部旧版飞行、夹层、拦截、多穿、失败计数与去重检查一并通过。未启动 Minecraft。

### 0.4.0 飞行走廊、能量与 ATTACK_EXIT

- 日志中观察到 27 次 `PREDICTED_COLLISION_PULL_UP`，其中有刚举矛就拉升的情况。旧控制器把前方采样到的最高方块统一当成地面，屋顶也会抬高整条攻击线；安全检查又假设后续一直保持当前俯仰。现在移除这两条路径。
- 锁定前检查“入口 → 主目标 → 出口”的完整扫掠体，出口允许延迟爬升或侧向转出。飞行中用原版鞘翅方程预测实际惯性与后续姿态调整，逐段检测方块真实碰撞形状。附近但不进入走廊的方块不增加避障代价；薄柱、台阶、围栏、地板、屋顶和液体都会进入空间检查。不强制加载区块。
- 正常左右/上下余量均为 `0.35` 格，基于实际滑翔碰撞体；紧迫情况下允许缩至 `0.04` 格，但仍不允许身体穿过方块。小坡优先调整同一攻击方向的可行高度，保持目标和举矛；只有普通路线不可行时才中止攻击并执行紧急恢复。
- 补能依据预计的 `g × 相对高度 + 速度平方 / 2` 及后续滑翔损耗，不再仅凭低速或高度不足发射烟花。这是原版运动下的可用能量指标，不假设 MC 严格遵守现实能量守恒。航线高度仍是软目标，身体碰撞是硬约束，所有实际运动仍由原版处理，没有直接设置速度或位置。
- 从库存按实际推进时长挑选烟花，评估每种烟花全部 12 个随机结束时间及熄灭后至少 16 ticks 的滑翔，包含途中重新规划；过长脉冲若无法安全恢复则拒绝使用。发射后改用该实体的真实剩余寿命。仍消耗真实物品、遵守冷却、不叠加绑定火箭，优先无爆炸效果烟花；起飞仍使用单独的启动脉冲。
- 原来的固定 `PULL_UP` 改为 `ATTACK_EXIT`：可选择直线爬升、转弯爬升、转弯平飞，也可先直线滑出屋顶再恢复。选择考虑净空、当前惯性、能量和下一次攻击方向，不围绕主人选离场点；一次选定的出口固定在世界坐标中，抵达后才重选。新恢复俯仰速率默认 `8°/tick`。
- 保留攻击后 60 ticks 的同一次 Charge，以及主/次目标命中去重。离场时的爬升/转向不再要求先结束举矛；有前方次要目标时增加大转向的代价，安全允许时保留多穿方向。
- 优先普通平滑路线，再考虑接近 90° 抬头急刹，只有抬头路线也不通时才考虑 180° 反向。两者均通过实际惯性模拟，不瞬间改写速度；如果已经来不及规避则记录 `NO_CLEAR_PATH`，不假称有安全解。
- 新配置自动补入：`recovery_pitch_rate=8`、`energy_reserve=0.08`、`corridor_side_margin=0.35`、`corridor_vertical_margin=0.35`。`minimum_boost_speed` 现在定义能量储备中的速度基准，而非立即点火阈值。旧 `forward_collision_check_distance` 保留兼容，但不再驱动原有固定俯仰检查。
- 调试日志新增规划模式、可行性、预测无碰撞 ticks、能量储备、真实火箭剩余 ticks、出口类型和攻击走廊结果，仍默认关闭。HUD 仅按既有格式显示新的状态/航点名称。
- 新回归：48 组平台/小坡闭环回放，144 组新规划器的攻击/前摇/拦截回放，三种撤离选择、薄柱扫掠、屋顶、邻近障碍、抬头与反向优先级、不可逃脱惯性场景、短/长烟花安全筛选。另保留旧版全部检查。回放不代替实机命中、复杂地形与性能测试。

### 0.3.2 小目标与多穿

- 骑空士女仆接管空战、正在使用 Charge spear 时，目标的虚拟命中盒向上/向下各扩展默认 3 格。只改变这一女仆的查询，不修改实体真实碰撞盒、玩家/其他使用者、Spears Jar 或水平射程。保留正常横向余量、最小射程、移动延长、友方过滤；地形射线会截断射程，并检查虚拟接触点到真实表面之间的遮挡。
- 正常命中与扩展命中按实体 UUID 合并后，只进入同一个原生 `KineticWeapon.usageTick -> SpearUser.pierce` 流程。前摇、相对速度、伤害、附魔与接触冷却仍由 Spears 处理。每个敌人在本轮攻击加撤离中最多成功受伤一次，避免正常/扩展判定重叠和撤离重复伤害；下一轮可重新命中。
- 连续默认两次攻击同一主目标而未造成实际伤害时，下轮优先选择另一个合法主目标，不受旧目标加分/保留阈值阻碍。命中次要目标不算主目标成功；实际伤害（含吸收生命）会清除该目标的失败记录。无其他合法目标时继续尝试现有目标。撤离后也查询上一战斗区域内仍加载的实体，避免女仆飞远后漏掉附近替代目标；不强制加载区块。
- 掠过主目标，或已命中并击杀它后，保持同一次 Charge 默认 60 ticks（20 TPS 下 3 秒）。为这段撤离单独保留原生有效伤害时窗，不重新举矛、跳过前摇或清空接触冷却。直线撤离期间可穿到路上的其他合法敌人；碰撞风险、落地、装备失效、主人指令及任务切换仍会终止。为新增撤离距离与回转临时预留飞行边界，避免刚完成多穿就被旧边界扯回主人。
- 主目标确定后，独立查看默认 3 格内的次要敌人；仅在转向幅度和地形允许时调整进场。攻击中只有通往次要目标的直线仍穿过主目标命中列时才采用，多穿机会不会加入主目标评分、改变主目标锁定或强迫危险转弯。
- 新配置自动补入，不需要重置已有数值。设置 `spear_vertical_expansion=0` 可关闭扩展，`post_attack_charge_ticks=0` 可关闭撤离 Charge，`secondary_target_radius=0` 可关闭多穿规划。
- 验证：小碰撞箱与上下边界、水平/纵向射程、墙体截断、每轮去重、主/次目标伤害记录、两次失败后避选、撤离前摇/伤害时窗、主目标保护与飞行边界检查，以及原有 162 个飞行和 144 个拦截数值场景。核对了实际 Spears Jar 的新增 Mixin 调用点签名；没有安装或启动 Minecraft，实机 Mixin 应用和战斗效果由用户测试。

### 0.3.1 攻击周期与 Home 修正（历史）

- 实测日志两次在完成攻击、拉升后约 0.15 秒被 `OWNER_HARD_RANGE` 强制返航。旧代码把目标的 56 格追击范围也当成女仆的飞行边界，还会把脱离航点裁剪回主人附近。现在目标范围只约束索敌/续追，转弯航点保留实际所需空间。
- Home 开启时，索敌评分、作战范围和返航落点统一围绕 `getRestrictCenter()`；否则以主人为中心。每轮开始时记录中心，完成掠过、脱离、拉升后才重新读取，主人移动不会中途扯回航线。已锁定目标不因距离主人变化而失效；死亡、不可攻击、偏移过大、碰撞风险等仍可中止。仍要求主人在线且在同维度。
- 燃料用完或飞行总时长到限时，已开始的攻击周期先完成恢复，再返航；装备失效、任务切换、坐下等指令立即处理。女仆同时远离活动中心和战斗目标超过 `emergency_teleport_range` 时仍强制返航，返航超时保留安全传送。Home 模式只尝试传送到已加载、可站立的 Home 附近落点；不替换 TLM 自身的区块安全机制。
- 拉升高度改为目标附近所需进场高度，避免每轮再叠加一段爬升。恢复后先评估下一次进场，不再无条件再向外飞 28 格；合适时继续攻击当前目标。需要拉远时仍使用固定世界航点，取消三次尝试就返航的限制，保留总进场超时与紧急范围保护。
- 进场不再硬性要求距离至少 18 格、偏角小于 8°。按真实长矛前摇、火箭加速后的速度、剩余转向时间决定机会；高度/升降速度允许范围也适度放宽。举矛时机预留途中补烟花的加速；低空低速、即将补烟花的进场也按加速后的前摇距离检查。前摇来不及或航向明显不合适时仍重新拉开。`attack_entry_distance` 作为计划进场距离参考，不再是不可突破的最小距离。
- 拦截速度取目标连续 tick 的实际位置变化，避免地面怪物残留的向下重力速度拉低预测点。接触前持续修正横向瞄准；短期俯仰预测加入真实非玩家矛长、最小射程、速度延长与碰撞盒余量的射线误差，地面安全代价仍优先。伤害、前摇与接触冷却继续由 Spears 原生代码处理。
- 没有新增配置项，不需要重置配置。编译检查包含攻击周期/范围/重复拉升回归、原有 162 个飞行场景和 144 个不同速度、火箭时长、前摇、横向惯性及移动目标的矛尖拦截场景。没有启动 Minecraft；数值相交不代表已验证实机伤害、复杂地形或网络表现。

### 0.3.0 飞行修正（历史）

- 修复 `MaidSwimManager.updatePose` 每 tick 将滑翔姿态覆盖为站立的问题。保持空中 `FALL_FLYING` 和匹配的碰撞盒/眼高；真正接触地面时仍正常退出滑翔。
- 身体旋转移到女仆公共渲染入口，覆盖 Bedrock、Gecko 以及委托给 YSM 的渲染分支；不依赖模型是否调用原来的硬编码旋转钩子。标准模型的持矛骨骼动作保留。
- 重定位改为固定世界坐标的脱离航点：先拉开距离，再转向目标。为转弯预留边界空间，加入独立进场计时和重试上限；不再每 tick 把转弯点重新放在女仆身边。
- 进场检查朝向、实际运动方向、垂直速度和高度。低空进场保持攻击高度；较高进场先下降，在目标前默认 14 格进入平飞段。使用包含原版升力、重力、转向惯性和火箭推进的短期预测选择俯仰角。
- 保留目标脚下的安全高度约束，即使它只站在虚空中的单个方块上；沿预测路径检查滑翔碰撞盒，提前拉升。低速、低空且没有足够升力时允许补充短烟花。
- 从库存中的可用烟花按所需推进时长选型，优先无爆炸效果的烟花；起飞、长距离补速和紧急修正的需求不同。发射后读取该枚火箭实际随机寿命和已运行时间，逐 tick 预测剩余推进及结束后的惯性。
- 新参数自动补入 SERVER 配置：`approach_height=4`、`level_run_distance=14`、`reposition_distance=28`、`attack_ground_clearance=0.75`，单位均为格。现有参数和用户修改保留，不需要 `reset all`。
- 验证：Java 编译、索敌计时/Charge 时序、162 个带推进/无推进数值场景、3 个背向目标转入场景。没有启动 Minecraft；数学回放不等于已验证实机碰撞、伤害或动画。

### 已有功能

- 飞行流程为接近/按需重定位 → 锁定 → 俯冲 Charge → `ATTACK_EXIT` 选择爬升或侧向离场 → 重新评估目标；没有目标才盘旋搜索并返航，装备失效与紧急安全条件仍可中止攻击。
- 飞行时同步 `FALL_FLYING` 姿态、0.6 格滑翔高度和 0.4 格眼高；身体使用玩家式鞘翅旋转，Bedrock 持物层补上 Spears 的 Charge 变换。YSM 分支使用统一身体旋转，不重写自定义骨骼动画。
- 攻击调用真实 `startUsingItem(MAIN_HAND)`，依据 Spears 前摇提前举枪。命中、伤害、速度条件和接触冷却仍由 Spears 处理。修正非玩家物品回调发生在移动前、导致 Spears 读取当前 tick 位移为零的问题，只向它提供女仆上一 tick 的实际位移。
- 调试棒改为屏幕左上角 HUD，换手或收起调试棒也保持显示；每名玩家只观察最后右键绑定的女仆，再次右键同一女仆关闭。实体卸载、死亡、换维度或离开工作模式时清除。
- 红线指向本轮硬锁定目标（`ATTACK_RUN` 开始至脱离结束；候选阶段没有红线），蓝线随当前飞行引导点及剩余航点更新，默认每 5 ticks 同步。蓝线是控制器的计划航线，不是历史运动轨迹。
- 入场距离考虑高度、速度与长矛前摇；通过完整身体走廊、原版运动预测和能量储备选择姿态与烟花。所有控制只调整朝向并使用真实烟花，不直接改写飞行速度。
- 主人和其他玩家明确排除在攻击候选及 Spears 命中之外。返航全程停止 Charge，寻找 Home 或主人附近的无液体、可站立落点，保留超时安全传送。

## 兼容实现

- TLM：通过 `@LittleMaidExtension` / `ILittleMaid.addMaidTask` 注册 `IAttackTask`；不改动其任务注册表。装备库存使用 `getAvailableInv(false)`。遥控器从 `TaskManager` 获取任务，服务端校验主人、绑定、会话、维度、实体及任务可用性后调用 `maid.setTask`。
- Spears：从实际 Jar 核对 `Spears.KINETIC_WEAPON` / `KineticWeapon`，使用真实 Charge 和原版 `ItemStack.onUseTick -> KineticWeapon.usageTick`。Mixin 适配空中女仆的实际位移、友方过滤、竖向虚拟命中盒、同轮去重和定时撤离伤害时窗；不修改 Spears Jar，不增加另一套伤害调用。TLM 持物姿态使用原生 Charge 变换。
- 非玩家鞘翅：最小 `EntityFlightAccessor` 暴露受保护的 `setSharedFlag`，仅在离地且装备有效后开启 fall-flying。速度、重力、升力、耐久仍由原版/NeoForge `LivingEntity` 处理。烟花使用接受 `LivingEntity` 的 `FireworkRocketEntity` 构造方法，并真实提取库存物品。
- TLM 控制权：只在骑空士接管期间跳过普通 FollowOwner/跟随载具，以及地面导航、MoveControl、LookControl 的 tick；清除残留移动记忆。落地后释放控制。没有假装坐下、骑乘或开启 Home Mode。
- 安全传送：不取消 `EntityMaid.teleportToOwner` 本身，因此 TLM 区块安全调用保留；传送成功后释放本 Mod 控制。

API 核对来源：指定实例中的 TLM 1.5.3 / Spears 1.8.0 Jar、NeoForge 21.1.250 开发源码，以及 [TLM 官方扩展文档](https://github.com/TartaricAcid/TouhouLittleMaid/wiki/%E6%B7%BB%E5%8A%A0%E6%96%B0%E7%9A%84%E5%B7%A5%E4%BD%9C%E6%A8%A1%E5%BC%8F%EF%BC%88%E4%B8%8A%EF%BC%89)。
