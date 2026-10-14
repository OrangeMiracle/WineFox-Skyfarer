# 0.7.4 彗星光效与圣物掉落保护

本轮基于当前本地 0.7.3 修改；不改变推进来源、真实推力、Elytra 预测、碰撞认证、Worker 或进化成本。源码仍为最终事实。

## 审查发现与证据边界

`VirtualPropulsion.apply → CometThrustEffects.emit → CometThrustPayload → enqueueWork → CometThrustParticles.accept` 原链路只来自实际已执行推力。控制器在 EntityTick.Pre 提交，EntityTick.Post 的 finish 仅在该 tick 没有提交时发送停止，不会在正常推进后无条件清空。

旧客户端使用原版 DustParticle：环境光会影响亮度，尺寸小、巡航尾焰每两 tick 才生成，1.25 格/tick 时两次发射间隔约 2.5 格；48 格外与 MINIMAL 设置完全不显示。环由稀疏 dust 点构成。实体尚未收到时，首个推进包和环事件直接丢弃；服务器的环门控还一直记住上次峰值，下降后再升至相近峰值可能不能触发。

本次读取用户 logs/latest.log（2026-10-13 实机记录），确认运行时选择了 VIRTUAL_PROPULSION，但旧版本没有 tx/rx/粒子生成计数。不能把这一日志当成客户端已收到并渲染粒子的证据。新增诊断用于下次实机逐段定位。

## Fullbright 粒子和连续尾迹

- `CometParticles` 只注册 `comet_tail` / `comet_ring` 两个 SimpleParticleType。客户端的 RegisterParticleProvidersEvent 注册 Provider；粒子 JSON 复用原版 `minecraft:generic_0` 白色粒子精灵，参与正常图集重载。
- `CometGlowParticle` 使用原版透明粒子批次，所有光照 UV 固定为 `LightTexture.FULL_BRIGHT = 0xF000F0`。不采样环境光、不增加动态光源、后处理或服务端实体。柔和外层与亮色内核产生轻量辉光观感，并非 Bloom。
- 双尾焰位于身体后方约 0.55 格、左右各 0.38 格；按当前速度建立正交基，零速回退 look。ALL 每 tick 沿两侧上次位置至当前的位置补点，约 0.3 格间距、每侧至多 6 点；转弯留下原位置的轨迹。超过 4.5 格的离散跳变只发当前点，不跨传送画线。推力增大时粒子半径从 0.20 增至 0.34，寿命从 14 增至 20 ticks；两层分别为赤红和浅红内核。
- 白环是一个粒子绘制的 32 段圆环、两层柔边，共 64 个 quad。固定在触发时的推力正交平面，半径 0.5 → 2.15 格，12 ticks 快速淡出；按相机一侧选择顶点绕序，两侧均可见。取精灵中心列保持段与段之间的亮度连续。近乎侧面观察自然呈细线。
- 仅实际启动/显著增力触发环；同一女仆至少间隔 20 ticks，停推后至少 6 ticks 才视作重启。环参考输出跟踪上次加速后的低谷，修复减推后重新增推不再出环的问题。

## 网络、生命周期与上限

沿用协议 4 和原有实体 ID + UUID + 输出 byte + ring bit 数据格式，不增加逐粒网络广播。通常每四 tick 一次推进状态包；启动、停止或环事件立即发送。

客户端最多保留 128 个发送者。首包先于实体到达时，保存至最多 10 ticks 等待；环最多排队 6 ticks。停止包立即移除对应 UUID，已存在实体 UUID 冲突的旧包直接忽略。世界切换清空缓存；失活、TTL 过期清理。TTL 只是显示同步宽限，绝不参与物理认证。

以相机为中心 96 格显示；是否实际收到远处女仆仍受原版实体追踪范围约束。ALL 每侧至多 6 点，DECREASED 每侧至多 3 点，MINIMAL 隔 tick 每侧 1 点，均保留白环。全客户端每 tick 最多 **96 个尾粒子 + 2 个环粒子**，有限环预算按 32 段计费。预留尾焰成对预算，并轮换女仆处理顺序；最拥挤时允许减少轨迹样本/省略过期环。专属 ParticleGroup 上限 2304 个活粒子，尾粒寿命最多 20 ticks。服务器不搜索附近粒子观察者，只通过现有 tracking 分发。

通过现有 `/wfs debuglog on` 启用 `debug.debuglog` 后，每约 100 ticks 输出聚合记录：

- `[WFS comet-fx server] tx/rings/stops/currentPower`：实际推力后的状态包提交；tx 不代表一定存在追踪玩家。
- `[WFS comet-fx client] rx/active/spawned/awaitingEntity/distanceCulled/budgetLimited/providerMissing`：已收包、活动发送者、创建提交、缺失实体、距离剔除、预算限制、未注册 Provider。计数为本世界累计值；spawned 是粒子创建提交数，不是 GPU 可见像素数量。

正常状态应有 tx、rx、spawned 增长且 providerMissing=0。若 tx 增而 rx 不增，检查实体追踪/客户端版本/网络；若 rx 增而 spawned 不增，检查其他计数；若已创建仍不可见，再检查相机、第三方模型偏移、资源包或光影渲染。

## 统一掉落保护与原版彩色 Glowing

`CometRelicDrops` 按 ItemStack 物品类型识别两枚圣物，不依赖女仆、装备栏、进度、堆叠数或仪式状态。`ItemEntity.hurt` 的 HEAD 在伤害扣除前调用同一策略：两枚圣物始终拒绝 LIGHTNING_BOLT、IS_FIRE 和 IS_EXPLOSION；原版 IS_FIRE 包含 in_fire/on_fire/lava/campfire/hot_floor/fireball。物品默认带 FIRE_RESISTANT 组件，以复用原版 fireImmune 行为。没有设置全局 Invulnerable，也没有取消真实 LightningBolt 事件。

进入世界/区块加载时与 ItemEntity.tick 开头，服务端设置原生 Glowing 标志；原版同步实体 flags 并保存 Glowing NBT。维护标记与原先 glow 值只在实体的 NeoForgeData 中，不写进饰品 ItemStack。外部 Mod 若把同一实体换成普通物品，恢复原 glow，不将圣物效果传给新物品。

客户端 `CometOutlineMixin` 仅对这两类 ItemEntity 的 `Entity.getTeamColor` 返回暖金 `#FFBE45` / 鲜红 `#FF303C`。原版 LevelRenderer/OutlineBufferSource 负责穿墙描边；不创建 scoreboard team，也不改变其他实体或物品本身。拾取、年龄/正常消失、投掷者、所有者、进度和其他组件保持原行为。

仪式的 `LightningBolt.tick HEAD` 未变：先完成服务端原子兑换，再进入原版 spawnFire / thunderHit。圣物永久保护与 TNT 余料的 120 tick 临时保护共用伤害类别；余料的临时禁止合堆和保存期限仍保留，重载不延长，也不把所有 TNT 变成免疫物品。

## 验证范围

执行 `compileJava`、`verifyCometRadiance`、`verifyCometDrops`、`verifyCometData`、`verifyArchitecture` 与 `jar`；没有运行 build/check/全量 verify，也没有启动 Minecraft。

Radiance 使用真实映射 Minecraft 的粒子构造、tick 和 render，将顶点送入离线 VertexConsumer；仅 sprite UV 用夹具，不创建 ClientLevel、OpenGL、游戏或服务器。验证所有顶点 Fullbright、三个轴向/两侧绕序、环半径/透明度/生命周期、尾焰寿命、资源图集引用、Provider 注册、原版 damage tag 和 outline 调用点，以及 128 发送者/200 ticks 的有界预算。Drops 使用生产保护策略与生产仪式，外界由离线 world/entity 夹具提供，检查非指定伤害、正常物品、发光恢复、重新掉落、物品数据、既有 glow、服务器权威与仪式/击杀回归。Data 使用真实 ItemStack codec 和 SavedData，验证持久化及原版雷电生火次序等依赖契约。

日志见 [VALIDATION](VALIDATION.md)。这些验证不等于已观察真实夜间画面、网络往返或区块重载。仍需实机检查默认/YSM 模型的背部锚点、夜间 32/64/96 格可见性、原版/资源包/光影的描边、多女仆 FPS/TPS，以及掉落免疫、跨区块重载和拾取。只需正常启动用户自己的游戏进行观察，本次开发没有自动启动。
