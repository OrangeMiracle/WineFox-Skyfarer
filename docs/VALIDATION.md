# 验证、离线回放与构建

当前版本 **0.7.7**。本次编译、六项 Fox3 定向检查和打包均已通过；未执行全量 build/check/verify，未启动或安装 Minecraft。以下历史章节保留当时的结果，不等于当前版本重新运行了它们。

## 0.7.7 Fox3 定向验证

实现入口见 [飞控总览第 13 节](FLIGHT_CONTROL_OVERVIEW.md#13-fox3-独立制导武器)，预算见 [异步架构第 11 节](ASYNC_PLANNING.md#11-fox3-主线程轻量武器预算)。本轮针对性任务不挂接完整 `check`，也不运行 Minecraft 客户端或服务端：

| 定向入口 | 验证对象与证据边界 |
| --- | --- |
| `verifyFox3Salvo` | 执行生产波钟/共享预算账本：默认 4×2、12 tick 间隔、延迟不补射、有限截止、冷却边界、64 女仆×500 tick 的活动/发射/搜索上限及移除回收 |
| `verifyFox3Guidance` | 执行生产向量制导：双侧外张爬升、连续翻转与追踪的阶段转向上限/曲率、固定与移动目标、正后方和零向量；不以数值回放代替真实画面 |
| `verifyFox3Ammo` | 真实 mapped ItemStack/组件和 NeoForge ItemStackHandler：免费零库存读取、普通/装饰烟花、仅 3 发库存尝试 8 发、生成否决、回调更换库存与实际提取拒绝 |
| `verifyFox3Geometry` | 生产 Fox3Impact/Fox3Blast 配合真实 Vec3/AABB：薄墙/实体扫掠、最近命中和初始重叠、球形半径、遮挡/保护/UUID 去重及配置伤害；世界碰撞形状与伤害事件仍需实机 |
| `verifyFox3Integration` | 实际 NeoForge 配置默认/范围/命令/reload/reset 与编译字节码控制流：nonce、持物、UUID/owner/权限、手动绕过自动条件但保留安全/付费门控、主线程派发、原版 renderer 和双语资源；核对真实依赖的区块卸载 `setRemoved`→tracking-end→`onRemovedFromLevel` 调用链 |
| `verifyFox3Targets` | 生产独立目标过滤、选择、安全门控和手动拒绝：玩家/女仆/有主实体/双向盟友/失效实体排除、主人附近优先、战区备选、八目标分散、主飞控目标不读写、UUID 隔离及承诺攻击禁止启动；实际导弹卸载钩子/重复/重入/异常后的唯一释放。真实 MC/TLM 测试子类用离线属性探针，并跳过实体构造器 |

最终 [fox3-release.log](../tools/fox3-release.log) 的 `compileJava`、上述六项 `verifyFox3*` 和 `jar` 全部通过：**BUILD SUCCESSFUL in 25s**，12 个任务（9 执行、3 已是最新）。Integration **534** 项断言，Targets 与实际生命周期 **67** 项断言；Salvo 的 64 女仆×500 ticks 检查触及全服 128 枚上限且全部释放。编译仅保留现有客户端事件订阅注解的两条弃用警告。

```powershell
.\tools\build.ps1 -Stage fox3-release -Tasks compileJava,verifyFox3Salvo,verifyFox3Guidance,verifyFox3Geometry,verifyFox3Ammo,verifyFox3Integration,verifyFox3Targets,jar
```

本轮核对真实依赖后发现区块卸载直接 `setRemoved`，绕过 `remove`，因此补齐 `onRemovedFromLevel` 与共享 `releaseOnce`；实际钩子的重复、重入及异常后再次释放通过。爆炸的 `finally` 保证第三方伤害/事件回调异常时仍丢弃导弹，不留下活动名额。

早期失败日志原样保留，不记为成功：[fox3-directed.log](../tools/fox3-directed.log) 的 sealed `ILoadedConfig` 测试夹具编译错误；[fox3-directed2.log](../tools/fox3-directed2.log) 中前五项通过、Targets 因未应用依赖 AT 失败；[fox3-targets.log](../tools/fox3-targets.log) 的 Windows javaagent 非 ASCII 路径问题、[fox3-targets2.log](../tools/fox3-targets2.log) 的离线 registry 初始化问题；[fox3-targets3.log](../tools/fox3-targets3.log) 中 Targets 64 项先通过、Integration 旧断言未适配免费模式跳过库存读取而失败。最终检查保留并验证免费分支零库存访问及付费分支不可绕过弹药要求，没有为通过测试撤销这些边界。

交付包：[touhou_little_maid_skyfarer-1.21.1-0.7.7.jar](../build/libs/touhou_little_maid_skyfarer-1.21.1-0.7.7.jar)，**2,813,755 bytes**，SHA-256 `4D35665CABE2938704EE231583A48BBA9FE81652026D0FF07F244B40145F6779`。Mod 元数据与 Manifest 同为 0.7.7；10 个 Fox3 生产类、JSON 资源、离线测试/javaagent/外部夹具排除均已核对，见 [产物校验](../tools/fox3-artifact-check.json)。

实机仍需确认：上爬→翻转→追踪的双弧线观感、白尾长度/橙色爆炸、复杂动态地形与碰撞形状、实体追踪/卸载/网络插值、伤害事件与整合包交互、多人多女仆实际 MSPT/FPS。静态控制流检查不是实际点击面板，几何/数值检查不是完整 Minecraft 端到端测试。

Targets 的普通 Java 进程没有游戏的 AccessTransformer，测试专用 javaagent 只读取并应用 TLM 自己声明的 38 条访问规则；离线引导临时允许 TLM 静态 TYPE 的 holder 创建后恢复 registry bookkeeping。此适配不改写依赖 Jar、不进入交付 Mod Jar，也不启动游戏或实际服务器世界；不能据此宣称完整 TLM/Mixin 与 NeoForge 事件总线已经实机通过。

## 0.7.6 Home 回收后的重复召唤

[实现、实机日志与限制](REMOTE_RECALL_076.md)、[最终运行日志](../tools/remote-recall-release.log)。

- `verifyRemoteRecall`：同三只 UUID，永燃/激流/普通烟花各三次约 480 格任务；保存式重建、原日志 false-ground/残余速度、第二轮 Worker 阻塞 16 ticks、滑翔交接与 Home/卸载边界。每轮无遗留 session/适配器票据，实际推进成立、无库存消耗、飞行无身体碰撞。20 tick 接触等待到期及真正离地重载不冻结也通过。
- `verifyRemoteCometContracts`：符号执行实际编译的接单控制权分支四种组合；TLM handler NBT；生产取消、撤票/有限 Home 宽限与 Home 到达立即观察调用链。
- `verifyRemoteWake`：两只 Home 三次任务、战斗→Home 位置的真实 SavedData 往返、13 候选公平分批、attempt/generation/超时/上限与释放。
- `verifyRemoteComet`、`verifyRemotePropulsion`：上一轮空方案崩溃、五只永燃、100 tick Worker 阻塞、旧实体 session 关闭与无永燃的正常/创造激流赶路回归通过。

保留编译夹具字段错误、未修复代码提前滑翔断言失败、第二轮 ANCHOR_REJECTED 和随后通过的全部日志，详情见专题。TLM 传送、原版接触与区块/实体 IO 由离线适配器提供；未运行整合包、未执行完整 Combat 伤害回放，不把这些结果声称为真实端到端游戏测试。

交付包：[winefox_skyfarer-1.21.1-0.7.6.jar](../build/libs/winefox_skyfarer-1.21.1-0.7.6.jar)，**664,570 bytes**；SHA-256 `A77628E2C954DAEFB75735C6B72D1F78DB78C1DA3E75855DEF115461032F4BF2`。版本与资源、四处生产类及测试隔离已核对；[包校验](../tools/remote-recall-artifact-check.json)。

## 0.7.5 永燃远程唤醒

[根因与回归边界](REMOTE_WAKE_075.md)、[真实异常摘录](../tools/remote-comet-crash-evidence.txt)、[最终定向运行日志](../tools/remote-comet-release.log)。

- 先在未修改的生产代码复现 `NullPointerException / TransitFlight.tickFlight:71`；修复后无认证方案安全拒绝，PlannerSession 释放。
- `verifyRemoteComet`：5 只重建的 Home 女仆每批 2 只，Worker 停顿 16 ticks 后完成约 480 格创造赶路与滑翔交接；永燃推力确实执行、普通饰品进度保留、零烟花/火箭/节能 RNG、无身体碰撞。另查 pending 取消、同 UUID 新实体及超过 100 ticks 的 Worker 阻塞退出。
- `verifyRemoteCometContracts`：实际 TLM 饰品栏 NBT 往返与绑定缓存，槽位/进度独立保存；生产字节码确认失败→cancel→close/撤票或有限 Home 宽限，以及登出/离开/停服清理。
- `verifyRemoteWake`：双 Home、第二次任务、13 候选分批、公平重试/generation/注册表 NBT/上限/清理。`verifyRemotePropulsion`：无永燃、无烟花的正常/创造 Home 激流赶路及干燥环境拒绝。
- 保留 [原异常复现失败](../tools/remote-comet-reproduce.log)、[首次修复后推力缺失失败](../tools/remote-comet-directed.log)、[姿态诊断](../tools/remote-comet-trace.log) 与 [姿态修复后的回归](../tools/remote-comet-handoff.log)，未弱化“必须实际虚拟推进”的断言。

TLM 持久化检查使用已注册原版物品代替 Mod ID；回放的实体重建、世界和票据后端为夹具，不能宣称实际 NeoForge 区块 IO/事件总线已端到端通过。真实卸载/召回、标记取消/登出后的区块回收与整合包兼容仍需实机复测。

交付包：[winefox_skyfarer-1.21.1-0.7.5.jar](../build/libs/winefox_skyfarer-1.21.1-0.7.5.jar)，**663,334 bytes**；SHA-256 `D0ABB8B5432A900FAD9809131DBFF37CBDC561BFD38091E34146940FE37A269D`。版本元数据与 Manifest 均为 0.7.5，包含三处生产修复，JSON 资源可解析，不含回放/外部 API 夹具；[包校验](../tools/remote-comet-artifact-check.json)。

## 0.7.4 Fullbright 与圣物掉落

详见 [实现与诊断](COMET_RADIANCE_074.md)。[comet-radiance-directed.log](../tools/comet-radiance-directed.log) 的 compileJava、CometRadiance、CometDrops、CometData、Architecture 全部成功。

- 实际生产粒子 render 输出的每个光照 UV 均为 `0xF000F0`，不需要 Level 光照数据；白环在 3 个轴向、相机两侧都保持正确平面/朝向，半径扩散、alpha 淡出，12 tick 寿命；双层尾焰 14–20 tick 寿命。使用 UV 夹具与真实 VertexConsumer API，没有创建游戏、服务器或 OpenGL。资源 JSON 引用的原版 PNG 可读取，两项 Provider 已注册。
- 推进门控在初次加速、减推后再增推、停止后重启于 ticks `[0,40,66]` 发环；重复同 tick 不产生事件。既有门控用例仍为 200 ticks / 49 包 / 3 环。128 个发送者模拟 200 ticks，始终不超过每 tick 96 尾粒子及 2 个单粒子圆环；这不是实机 FPS 基准。
- 两种物品从 tick 0 至 24000 持续拒绝雷击/火焰/爆炸，普通 TNT 与其他伤害不获永久免疫；恢复/重掉落发光，保留进度、UUID 与原 ItemStack，不覆盖原先已有 glow。夹具测试不代替真实 ItemEntity 区块 IO。
- 生产雷击事务回归涵盖不足材料、被拾取、范围外、视觉闪电、重复闪电、离线归属与去重奖励；32+40 只耗 64，余 8，原实体转化且数据保留。原版源码/字节码确认仪式入口早于生火，雷击经过 hurt；余料 120 tick 到期，重载不续期。真实 ItemStack codec/SavedData 持久化与击杀进化回归通过。
- 最终打包与调整后的粒子定向检查记录于 [comet-radiance-release.log](../tools/comet-radiance-release.log)。夜间最终外观、资源包/光影兼容、真实客户端收包、跨区块重载与多女仆性能仍需实机验证。

交付包：`build/libs/winefox_skyfarer-1.21.1-0.7.4.jar`（662,808 bytes），SHA-256 `E7CA2EBA62B9C66EA7929DAF0FD075389F4E428E334B4B1997B07F555A8E30BC`。新增 Provider/Fullbright/掉落策略、Mixin 与粒子资源均已入包，没有离线夹具或 Minecraft 类；[包校验](../tools/comet-radiance-artifact-check.json)。

## 0.7.3 权威仲裁、持续动力与特效

实现与实机证据见 [动力仲裁专题](PROPULSION_073.md)。共执行六项直接相关验证入口：PropulsionAuthority、PropulsionAuthorityReplay、PropulsionPressure、CometContracts、RiptideRelease、Architecture；并未执行其他 verify 或全量 check。

- [propulsion-authority-release.log](../tools/propulsion-authority-release.log)：最终 compileJava、verifyPropulsionAuthority、verifyArchitecture、jar 成功。覆盖所有 Phase 的独占来源/等待超时/紧急不降级、旧直接点火口拒绝、生成回调中换装不扣库存、旧来源取消、同 tick 冲量去重、真实普通节能与激流兼容。架构检查遍历生产字节码，保证唯一点火入口在 Resolver Provider、上层不访问具体动力装备、Worker DTO 无实时对象、服务端特效不链接客户端类。
- **持续控制**：700 tick 纯物理平飞→转弯→缓升测试，每 tick 推力与独立执行顺序完全一致；稳态水平速度 1.2416–1.2507 格/tick，最大高度误差 0.2406 格。实际 Provider 的 220 tick 快照/实体回放供力 219 ticks，最低速度 1.1898、高度误差 0.0698；冷快照首 tick 不授权推力。之后阻塞全部 Worker 并添加当前步墙体，拒绝继续加力、不清空原动量。
- **多女仆**：八个实际 Provider 同时保持速度 100 ticks，各自 97–98 ticks 取得当前证书；始终不超过 768 cells / 2 pending jobs，无烟花实体或耗材。此结果证明有界性和该场景不饥饿，不是实机 TPS/FPS 基准。
- **粒子**：生产数值 gate 检查初次加力、显著增力、停止后重启，共触发 3 次环、200 ticks 内 49 次状态包，重复同 tick 不重复触发。检查双尾流对称、白环正交/向外扩散、近垂直与零速度姿态、全客户端 96 尾粒/64 环粒预算及重置。客户端实际 ParticleEngine API 已编译通过，未启动客户端渲染预览。
- [propulsion-authority-replay2.log](../tools/propulsion-authority-replay2.log)：两个完整生产控制器回放同时装备两种饰品、合法激流和 64 烟花。正常例 tick 134 命中；Worker 16 tick 停顿后恢复正常节奏的例子 tick 130 命中；均安全离场、无身体碰撞，逐 tick 检查零烟花消耗、零三叉戟耐久、零节能 RNG、无推进源叠加。随后 CometContracts 与 RiptideRelease 通过，包括完整移动 Strike→Exit、前摇截止与碰撞拒绝、卸装/安全/新增障碍抢占。
- [propulsion-authority-final-checks.log](../tools/propulsion-authority-final-checks.log)：另跑一例**全程保持 5 ms/tick**、所有 Worker 停顿 16 ticks 的混合装备压力回放，在 tick 217 命中并安全离场、无碰撞和耗材。没有在这个压力用例中恢复普通节奏；没有修改硬碰撞或命中断言。这不抹除下方 0.7.2 曾经 500 ticks 无命中的历史记录，也不证明任意延迟都能成功。

中间失败保留于 `propulsion-authority-replay.log`：新增混合装备回放的重载签名遗漏导致夹具编译失败；修正签名后同组通过。`propulsion-authority-compile` / `directed` / `package` 是本轮逐步验证日志，最终交付以 release 日志与产物校验为准。

当前 Jar：[winefox_skyfarer-1.21.1-0.7.3.jar](../build/libs/winefox_skyfarer-1.21.1-0.7.3.jar)，**652,971 bytes**，SHA-256 `F01EC0D3E9213195B1D16F5F34D9D48ED1E903316949E64AEAC2BE2D9E5538A2`。版本/资源检查通过，含来源仲裁、持续控制、客户端粒子与协议 Payload，不含外部 API 夹具；[校验记录](../tools/propulsion-authority-artifact-check.json)。

仍需实机：不同女仆模型的尾焰位置、粒子可见度、网络延迟下的启停表现、复杂地形与移动目标、多人/多女仆真实 MSPT 和 FPS。现有创造高速赶路特权保留；新粒子仅在实际虚拟推进时触发。没有自动安装或启动 Minecraft。

## 历史：0.7.2 彗星饰品与掉落物仪式

- [comet-contracts-final.log](../tools/comet-contracts-final.log)：compileJava、verifyCometContracts、verifyCometData、verifyArchitecture、jar 成功。原生饰品栏、效果互斥、固定随机流的 1,000 次成功发射/600 次节能、生成否决不抽奖、各推进阶段认证、移动 Strike→Exit、限幅/停止、卸装与实时墙体抢占、八女仆共享预算均通过。固定随机流检查概率阈值和调用次数，并非统计抽样结论。
- [comet-flight4.log](../tools/comet-flight4.log)：verifyCometReplay 与 verifyRiptideRelease 成功。真实生产控制器的无燃料永燃场景分别在 tick 360、408 命中并安全离场，无身体碰撞、无推进实体和耗材。第二例先阻塞全部 Worker 16 ticks，再恢复 15 ms/tick 的正常离线节奏；激流独立准备/释放、主手长矛、否决与失效回归通过。
- [comet-data3.log](../tools/comet-data3.log)：实际依赖的 ItemStack codec 序列化/复制、CUSTOM_DATA 保留、SavedData 离线资格重载/去重、TLM/Spears/雷击依赖字节码契约及战利品/成就资源通过。该任务仅初始化静态注册表，不启动 Minecraft 实例。
- **方式 B 修正后的最终验证**：[comet-ritual-correction.log](../tools/comet-ritual-correction.log) 的 compileJava、verifyCometRitual、verifyCometData、jar 全部通过。覆盖无女仆的地面仪式、多堆付款与余料、组件/实体身份保留、材料不足/移除/范围外、非服务端/非主线程/视觉闪电/后续闪光拒绝、重复事件、限时伤害保护及到期/重载。奖励检查引雷玩家优先、已确认离线投掷者、未知归属不奖励旁观者；同一入口回归原方式 A 的真实伤害归属、去重和转移进度。实际 LightningBolt 字节码确认原版生火早于实体雷击事件，成品包含首次 tick 入口与物品保护 Mixin。

失败记录没有删除：`comet-contracts.log` 的首次控制器场景命中后碰撞，调查发现选中方案逐 tick 追赶缺失单元；以原采集预算内的邻域预取和单次当前状态复核修复，最终上述两例通过。`comet-replay2.log` 的持续 **5 ms/tick** 压力在 500 ticks 内未命中（未碰撞），仍是攻击效率限制；短暂 Worker 停顿后恢复的通过结果不能取代它。早期 `comet-compile` / `comet-compile2` 是构造器与夹具 API 编译修正，`comet-focused3` / `comet-data2` 是静态验证缺少 LoadingModList/语言资源的初始化失败，均保留。

最终 Jar：[winefox_skyfarer-1.21.1-0.7.2.jar](../build/libs/winefox_skyfarer-1.21.1-0.7.2.jar)，**621,031 bytes**，SHA-256 `2B2DE212B2FA25AEDDF83881A287A52782B81D14DF2AA5388CB2B356548738DB`。元数据统一为 0.7.2，JSON 全部可解析，包含仪式/物品保护/投掷者 Mixin 和虚拟推进，不含回放夹具或 Minecraft/TLM 替身；两张不同的 32×32 RGBA 纹理已检查。[产物校验](../tools/comet-artifact-check.json)。

边界：离线实体/世界适配器并不执行完整 NeoForge 事件总线和 Mixin 应用。自然雷电/引雷、原版生火及附近爆炸、实际拾取与重载、离线成就补发、原生 Spears 击杀和多女仆 MSPT 仍需实机核查。没有启动游戏、安装 Jar 或宣称任意调度延迟下都有攻击效率保证。方式 B 的最后修正没有重跑无关飞控或全量验证。

## 历史：0.7.1 定向范围

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

## 3. 基础验证任务与专项入口

以下 44 项为由 `check` 依赖的基础验证任务。ControllerReplay 不设置 wfs.replayCase 时覆盖全部 21 场景。本轮没有执行这份全量清单。独立 `verifyTrajectoryStress`、后续彗星/远程专项及本页 0.7.7 的 Fox3 定向入口不在此基础清单中；完整注册以 [build.gradle](../build.gradle) 为准。

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
