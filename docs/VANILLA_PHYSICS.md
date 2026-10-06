# 1.21.1 物理预测与路线能量包络

核对环境：Minecraft 1.21.1、NeoForge 21.1.250，2026-10-05。本文属于当前实现；修改预测器、实体采集或版本时同步核对。入口见 [PROJECT_INDEX](PROJECT_INDEX.md)，线程协议见 [ASYNC_PLANNING](ASYNC_PLANNING.md)。

## 源码来源

本次直接读取 Gradle NeoFormRuntime 的 `sourcesWithNeoForge_bf8a098aa15d3c26eebcffae53826fd9f2fd0b4b_output.zip`，其元数据指向 NeoForge **21.1.250** 的 sources.jar。源码包 SHA-256：`77D78D1C5DF62C64859B12E1ADAA479B12F0477A73FE338D1C7B9FA3A2FCFE63`。本机只读缓存位于 `~/.gradle/caches/neoformruntime/intermediate_results/`；摘取的核对文件在可再生成的 `build/physics-reference/`，不打包、不依赖游戏客户端启动。

核对入口：

| 对应版本方法 | 需要保持的事实 |
| --- | --- |
| `Entity.getGravity()` / `LivingEntity.getDefaultGravity()` | noGravity 时为 0，否则通过真实 gravity 属性/覆盖实现；属性默认 .08，1.21.1 范围 **−1 到 1**，负重力不能当获取失败 |
| `LivingEntity.aiStep()` | travel 前将绝对值小于 .003 的速度分量归零 |
| `LivingEntity.travel(Vec3)` | 下降/零垂直速度且有 SLOW_FALLING 时取 min(gravity,.01)；流体分支先于滑翔 |
| `Entity.calculateViewVector(float,float)`、`Mth.sin/cos` | float 度数转弧度、65536 项 float 查表；look 分量的乘法也是 float；不能用 double sin/cos 替代 |
| `FireworkRocketEntity` 构造与 `tick()` | lifetime 为 `10*(1+Flight)+nextInt(6)+nextInt(7)`；推进在 `life++` 和 `life>lifetime` 爆炸前执行 |
| `ServerLevel.tick`、`EntityTickList.add/forEach` | 迭代使用稳定的插入序 map；女仆 tick 中新建火箭不进入本轮迭代，下 tick 才首次推进 |

没有动态空气阻力属性。地面 friction、流体阻力等不能冒充滑翔阻力。`VanillaElytra` 保留 float 常量 `.99F/.98F`，不建立伪 drag API。

## 单步顺序

`FlightPrediction.step` 先执行本项目受限转向，然后由 `VanillaElytra.step` 执行自由飞行数学：

1. 朝向转 float；应用 aiStep 的 .003 归零。
2. 按 float 角度及原版 Mth 查表求 look；记录更新前水平速度 `H` 与水平 look 长度 `L`。
3. `lift=cos(pitchRadians)^2*min(1,lookLength/.4)`，其中这一处 cos 是原版的 `Math.cos(double)`。
4. `vy += effectiveGravity*(-1+lift*.75)`。
5. 若更新后的 vy<0 且 L>0：`d=-vy*.1*lift`，按水平 look 加水平速度，vy 加 d（下沉转换）。
6. 若 float pitchRadians<0 且 L>0：`d=H*(-Mth.sin(pitchRadians))*.04`，减水平 look 方向的 d，vy 加 `3.2*d`（拉升转换）。
7. 水平速度向水平 look×**更新前 H** 趋近 .1。
8. 乘以 double 扩展后的 `.99F/.98F/.99F`；将此速度加入位置。
9. 若本 tick 火箭会更新：按原式 `v += look*.1 + (look*1.5-v)*.5` 更新下一步速度。火箭不倒过来改变已完成的位移。

`RocketProfile.active/remaining` 分开表达真实余下推进次数和 0/1 tick 的启动延迟。新火箭的所有 12 个随机寿命都包括一次启动等待；活动火箭仍读取真实 `max(0,lifetime-life+1)`。延迟不吞掉一次推进，也不把 lifetime 改成固定冷却。库存、消耗、爆炸效果选择和全寿命验证保持原机制。

free-flight 模拟不重现撞墙后的移动裁剪/伤害：每一步继续调用原 swept-volume 检查，碰撞即拒绝。流体和未知空间依然拒绝；专用水面逃逸保留 TLM wantToSwim 的独立模型。落地、失去鞘翅资格、外力、未知第三方 travel Mixin、非本控制器管理的火箭和引擎实体 tick 被其他 Mod 重排，都必须由运行时安全防线及实机确认处理。

## PhysicsContext 生命周期

`PhysicsContext` 是只包含 gravity 和剩余缓降 ticks 的不可变 DTO。`MaidTerrainSource` 在主线程经 `PhysicsProvider` 读取 `maid.getGravity()` 和缓降剩余时间；考虑实体效果在 travel 前递减，因此有限缓降冻结为 `max(0,duration-1)`，无限效果单独保留。模拟逐 tick 递减 DTO，不重新访问 Entity/Level。

Provider 按 **Entity 引用、Level 引用、dimension key、真实游戏 tick** 绑定。同一 tick 多次规划入口只读取一次；首次进入、释放后重入、实体替换、同名维度的新 Level、跨维度与时钟变化都重新捕获。轻量主线程每 tick 采集可反映 gravity/noGravity/效果变化，禁止在每个 prediction tick 读取世界。读取抛 RuntimeException 或返回 null 时使用 vanilla .08/无缓降；非有限 gravity 单独回退 .08，合法 0 和负重力保留。

`PlannerSession.begin` 在 Level 实例/dimension 改变时清理地形快照，在物理规则变化时取消旧 generation。自然缓降倒计时按任务年龄比较，不导致每 tick 取消全部任务；异常刷新/提前移除会失效。close 释放 Provider 引用。实体离开世界的既有 runtime release 仍负责整个控制器生命周期，不依赖传送门事件。

## Heuristic 与认证

0.6.2 新增 [Attack Funnel / Capture](ATTACK_FUNNELS.md) 组织层，继续复用本文件所述物理。目标相对合成状态的正向模拟证明窗口存在，交换曲线几何后必须重新正向验证；绝不倒放动力学。Capture 从实际状态先测 coast，必要时验证真实库存每种 duration 的全部随机寿命及 0/8/16 ticks 点火时机。接入之前没有充能，未来 Rejoin 必须重新保留完整原生前摇；最终攻击仍由当前状态证书授权。Window 的位置/速度等容差是搜索门槛，不是整个连续状态域的数学证明。

`E=g*(eyeY-requiredY)+.5*speed²` 只用于排序、补能、候选价值评估；不宣称守恒。`EnergyReserveEnvelope` 沿实际模拟轨迹记录 tick、阶段、未来高度需求、最低速度与 reserve，结果取整段最差 reserve。曲线使用采样路径的剩余最高高度，普通路线纳入当前剖面、接触高度和出口高度；不使用女仆脚下谷底或世界绝对 Y 作为能量信用。

无动力候选无法完成整段离场时，还会按真实可用库存 duration 生成明确的拟点火候选。State 的实际火箭仍为零；任务的 ignition 标志表示模拟提案的首 tick 启动等待，不是给实体虚构推力。候选使用该 duration 最长寿命，随后 Rejoin 用当前状态检查该物品全部 12 个寿命，实际点火成功后才承诺攻击；普通/拟点火缓存分 slot 隔离。

几何候选保持现有两族：Lane 的 Approach→Strike→Exit，以及两段 C1 曲线；未引入替代原语义的全局寻路。`AttackLinePlanner` 的能量只参与成本，几何通过不意味着能飞到。

普通路线在承诺 ATTACK_RUN 前请求 Worker 的 `AttackCertificate`，验证新一轮完整前摇、真实预测接触速度、接触射线、逐步身体碰撞和有限火箭结束后的离场。上限 160 ticks，未接触或未完成出口就拒绝。曲线候选及 Rejoin 同样必须命中并完成恢复，不能因到达时域上限就把部分轨迹判成功；接触接近搜索期限时允许在 160 上限内延长恢复验证。

离场认证沿既有有界离场语义检查行程、至少接触后 16 ticks、火箭后 16 次无动力移动、最低水平速度（至少 .3，且不低于请求的速度门槛）。曲线完成出口行程后，后续有限火箭/无动力验证沿出口末切线方向继续，避免追逐已掠过的 Bezier 终点；这些额外步仍受同一身体扫掠和导航边界限制。出口高度继续作为导向与能量需求，完成仍按既有有界行程，不强制抵达一个已掠过的 waypoint；身体净空由全程扫掠硬性验证。ATTACK_EXIT 的状态转换、有限行程、charge tail、伤害与 swept volume 算法本身保持原逻辑。负 reserve 可提示需要补能；若实际动力学已经完成要求，不会仅因这个近似指标为负而宣布不可达。正 reserve 也不能覆盖任何物理拒绝。

这些检查全部在共享 Worker 上运行。普通 steering 的 24-tick 安全窗口仍是短期控制提示，不能替代 AttackCertificate。普通攻击证书使用 Rejoin 同级的 2-tick/2.5 格/8°/.35 速度差验收，并继续保留 generation、参数、实际火箭和主线程当前步检查。

证书未返回时拒绝承诺进攻，同时继续更新普通姿态与补能；不因新的认证等待而跳过当前控制并沿用旧下降指令。这一异步 API 契约由冷快照验证覆盖，远程赶路交接继续由完整回放检查。

无动力几何候选存在但当前状态 Rejoin 已明确失败时，也继续搜索拟点火几何；不能让过时的候选挡住另一条可补能路线。Rejoin 仍在等待时不承诺执行，任何新候选仍重验全部寿命。已取得几何但接入失败时下一 tick 可重新检查，几何 seed 仍为 12 ticks。紧凑转弯点火后，同 tick 的后续预测重新带入真实启动延迟。

完整物理认证要求先建立可持续的接近段。若一次进场开始时女仆眼高低于目标脚底，会在该次等待、接近、重定位和低能量恢复中保留上升进场引导：以攻击高度减去 `min(3, spear_vertical_expansion)` 为准备高度，不在中途改为目标上方的巡航高度。新一轮进场重新判断；正常平飞/俯冲的高度规则保持。这只是准备段目标，不能授权攻击、跳过碰撞或保证任意上升路线可达。保护低速入口的“不补烟花”距离 heuristic 只在已达到攻击高度时适用，不能把低处短时滑翔安全当成足以上升到目标。既有 `upward-entry` 与坡面/完整生命周期回放覆盖这些衔接。

该低位准备适用于孤立高处接触：已有坡面采样至少 12 点且不满足原平地门槛时，恢复原有地形爬升高度并清除较低的 Compact 目标。连续山坡不能用目标脚底以下的空中准备高度穿行；不削弱真实碰撞检查来迁就该准备策略。

Rejoin 邮箱 slot 按不可变 Path 身份隔离，避免反复查询无动力提案时取消正在计算的补能替代路线。现有每女仆 24 个任务、共享队列 64、任务年龄/姿态校验和 generation 取消规则不变；异步回归明确检查两条提案不会占用同一 slot。

离场补能有一项必要调整：无新火箭预测不安全、水平速度 <.65、下降且距地 <4 格时，20° 朝向 heuristic 不能盖过已经包含受限转向的全寿命物理认证。否则即使存在安全恢复火箭，也可能等对齐直到耗尽高度。ATTACK_EXIT 可提交转向中的完整 pulse 检查；库存、CD、碰撞和全部寿命通过后才执行，安全且速度充足/上升的出口仍不额外补能。120° 低空恢复的数值反例及创造 100 倍速交接的生产回放覆盖此边界。

未选中的曲线只累计最差 reserve，不分配逐步 Sample/Trace；选中后才重新生成完整不可变包络。有限 pulse+coast 的最后不足 8 ticks 继续执行已有反馈，不再额外发起 24-tick 姿态搜索，避免启动延迟新增的最后一步把认证窗口意外延长到远处墙体；已承诺窗口内每一步的扫掠仍执行。

## 验证

`verifyVanillaPhysics` 使用真实依赖 Mth/Vec3 的独立源码转写 oracle，对生产实现做 18,000 步差分，覆盖俯冲/拉升、高低速度、查表/float、epsilon、负/零重力、缓降到期、活动火箭及启动延迟。它不以生产 step 同时作为 expected 和 actual。

`verifyAttackEnvelope` 覆盖高低攻击、悬崖高位、绝对高度平移、长 Approach、未点燃库存、不同剩余寿命、完整随机寿命、接触后出口不足、时域耗尽和物理否决。`verifyAsyncPlanning` 增加同 dimension 不同 Level、物理变化 stale result 检查。四类生命周期回放的火箭夹具按 EntityTickList 延迟首次推进。完整执行记录与实机边界见 [VALIDATION](VALIDATION.md) 与 [buildHistory](../buildHistory.md)。
