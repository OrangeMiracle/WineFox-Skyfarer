# 装备、生存与激流推进

源码版本 **0.6.2.7**，核对日期 2026-10-07。沿用 0.6.2.1 飞控，不引入 Funnel。源码为最终事实。

## 0.6.2.7 近目标起飞与可控时长推进

**日志因果。** 指定实例 latest.log/debug.log 为同一 0.6.2.6 会话，不能重复累计：8 次 RIPTIDE_NO_SAFE_PATH、3 次 NO_LAUNCH_PLAN；另有成功蓄力和真实释放的记录。latest.log 855–863、884–889 显示先拒绝激流候选，再 CORRIDOR_JUMP，刚滑翔即 NO_LAUNCH_PLAN 并进入安全返航。“约 10 格”来自用户复现，旧日志未记数值距离；新版 PROPULSION 补记 targetDistance。摘录：[riptide-metered-evidence.txt](../tools/riptide-metered-evidence.txt)。

源码中普通激流起飞误用了接近目标的约束：整段预测必须在敌人前方留 6 格，因此近目标在开始蓄力前就无解。普通起飞现在使用独立 departure 认证：地面净空、完整扫掠、爬升与末段可继续滑翔的状态，不把敌人位置当刹停平面，也不要求起飞立即达到敌人高度。取现有起飞高度 eye+6，支持有限的前/侧/后方向搜索；接近目标的非起飞推进仍保留目标余量检查。不是把 10 格换成另一个拒飞阈值。

无动力起飞原先只按未碰撞与代价判断，短暂跳起也可能通过；现在必须在预测末端保持高度、水平速度和下降余量。离地重验失败直接记录 LAUNCH_REVALIDATION_FAILED 并安全恢复，不再把失效计划留到下一 tick 报笼统 NO_LAUNCH_PLAN。激流起飞结束后，从当前真实动量重新搜索合法机会目标并认证 Rejoin / Strike→Exit；没有攻击证书就继续普通 CLIMB/APPROACH，推进期间不穿插长矛攻击。

**WineFox 扩展物理。** 主手长矛、副手三叉戟、至少 10 tick 真实蓄力、OFF_HAND 释放事件、雨/水资格、每次释放 1 耐久全部保留。区别在释放后的推进：不再一次叠加完整原版等级冲量，而是每个选定推进 tick 沿受限转向后的 look 叠加 **0.2 格/tick 的速度增量**，随后按原有 Vanilla Elytra / 水中 travel 模拟。预测与执行共用 float Mth 向量算法。仅同步授权的女仆释放进入此控制层，玩家三叉戟和普通 Spears 不受影响。

| 原版激流等级 | 最大推进 ticks | 20 TPS 下最大时长 |
| --- | --- | --- |
| I（原版效果强度 1.5） | 10 | 0.5 秒 |
| II（2.25） | 20 | 1 秒 |
| III（3.0） | 30 | 1.5 秒 |

以游戏最小时间步 **1 tick / 0.05 秒** 搜索 1 到上限的每个整数时长，不使用几个固定档位。上限由当前原版激流效果强度映射并限于 30 ticks；未承诺任意数据包自定义强度的等级语义。逐时长搜索，先选最短安全且满足高度/速度要求者，再在同一时长比较姿态代价；高等级不提高每 tick 推力。每个提案认证剩余前摇、所有推进 ticks、之后 12 ticks 无推进恢复、身体扫掠与目标余量。起飞最多 4 个方向×3 个俯仰×30 个时长/强度，空中 3 个俯仰；全部搜索在现有有界 Worker，主线程只重验选定轨迹。

推进只在真实释放回调开始，后续每个游戏 tick 至多施加一次，不补发漏掉的历史冲量。选定时长到期（或环境/物品/spin/路径失效）停止本次 WineFox 推力并清除其拥有的 spin；**不归零速度、不清 FALL_FLYING**。12 tick 恢复只是预测窗口，不是冷却；激流仍不读取火箭 cooldown，安全激流优先，无可用解再走烟花/普通飞控。活跃烟花与已承诺 Strike/Exit 仍排除插入激流。

**快照与诊断。** 实机还记录两次起飞释放后第 8 tick 的 PATH_OR_SNAPSHOT_REJECTED，旧日志不足以确认具体是轨迹还是快照。源码存在蓄力期间消耗快照有效期、执行走廊未提前刷新的风险；现在仅为选定路线触及的 cells 提前排队刷新，沿用全服/会话采集与队列上限，仍拒绝缺失/超过 20 tick 的快照。新拒因区分 SNAPSHOT_MISSING 与 TRAJECTORY_REJECTED；生物/作战范围拒绝另列。PROPULSION/RIPTIDE 补记 purpose、目标距离、duration/maxDuration、thrustElapsed；持续推进每 5 ticks 采样。

**限定验证。** `tools/riptide-metered-acceptance.log` 记录 compileJava、verifyRiptideDuration、verifyRiptideDeparture、verifyRiptideRelease、verifyRiptidePriority、verifyEquipmentLifecycle、verifyEquipmentPropulsion、verifyLaunchAttackReplay 与 jar。回放覆盖 6/10/32 格目标普通激流起飞，5 ticks 实际推进且后续仍飞行；不适合上升 Launch Attack 的近目标仍可普通起飞；干燥时使用烟花。III 在不同需求下选出 1/2/3/4/12/19 ticks；逐一验证不存在更短可行候选，较长方案撞墙/overshoot 时选择较短方案。19 tick 连续实际推进跨越前摇后的快照刷新周期，推进次数等于 duration，截止保留动量。保留副手、事件否决、失雨/换手取消与攻击回归。外部实体 tick、雨状态、JumpControl 和物品事件由离线夹具提供，不等同于实际游戏与第三方 Mod 联调；未启动 Minecraft、未全量 verify。

## 0.6.2.6 激流使用与释放审计

读取指定实例最新 latest.log/debug.log（加载 0.6.2.5），两者记录同一批事件：28 次 RIPTIDE_NO_SAFE_PATH，只有一次进入 LAUNCH_WINDUP。latest.log 819/824/825 行显示 12:15:15.902 开始、16.401 跳跃、16.454 已 RELEASED，向上速度约 3.152；826 行随后路径重验失败并返航。因此不能将这份日志解释为“始终没有蓄力或冲量”。此前日志没有 hand/remainingUseTicks，不能追认全部使用细节，也不能从 PATH_REJECTED 区分碰撞、快照或生物扫掠。摘录：[riptide-release-evidence.txt](../tools/riptide-release-evidence.txt)。没有放宽安全拒绝以强行释放。

源码确认旧执行器通过 stopUsingItem 后直接 push/spin，遗漏了 LivingEntity.releaseUsingItem → NeoForge onUseItemStop → ItemStack.releaseUsing → TridentItem.releaseUsing。0.6.2.6 改走这条实际释放链；原版 TridentItem 的冲刺分支只接受 Player，新增 MaidRiptideReleaseMixin 仅为当前已认证、同步授权的女仆释放适配同样冲量/耐久/spin，普通三叉戟或外部直接释放不获得推进。ThreadLocal 授权在 finally 移除，事件否决或回调未到达不会事后补冲量，不创建假玩家。

明确 startUsingItem(OFF_HAND)，开始后检查实际 used hand、useItem 与副手堆栈身份；蓄力全程保持同一会话。跳跃/释放读取 getTicksUsingItem（use duration−remaining），不以世界时间替代使用计时；停滞超过 40 ticks 才取消本次请求。至少 10 ticks 后正常跳跃，离地滑翔、路径重验后释放。主手 Spear startCharge 原有“不在使用物品”门槛与 stopCharge 的长矛身份门槛保留，不能覆盖或终止副手激流。释放后 spin 计时独立，正常结束与路径中断分开记录，中断不能标为 LAUNCH_COMPLETE。

debuglog 的 RIPTIDE 记录 selectedHand/item、usedHand、startTick/tick、charge/remaining、sameUse、using、wet、enchantLevel/strength、released/spin/velocity。CHARGING 每 5 ticks 一次；RELEASE_REQUEST 记录完整蓄力和环境，RELEASE_CALLBACK 记录实际速度增量及 spinStarted，之后才有 RELEASED。END 带具体取消原因；STATE_TRANSITION 记录前后状态与原因。RiptideUseAuditMixin 只观察 stopUsingItem，输出有界调用栈及 releaseScope，以定位本 Mod、原版或外部取消者，不拦截取消。日志关闭时不采集调用栈。

定向证据：`tools/riptide-release-acceptance.log`。verifyRiptideRelease 使用实际控制器/异步规划/释放适配与外部生命周期夹具，覆盖主手矛＋副手激流、真实计时延迟 4 ticks 仍持续蓄力、开始/释放事件否决、错误 hand 与失雨取消、冲量仅一次、spin 正常结束后重新开始主手长矛会话；verifyEquipmentPropulsion 核对真实依赖调用链及 1500 冲量差分；verifyRiptidePriority、verifyEquipmentLifecycle、verifyLaunchAttackReplay 保持通过。编译与 jar 成功。实际游戏的 Mixin 应用、第三方事件和网络表现仍需实机确认；夹具不等同于 Minecraft 启动测试。

## 0.6.2.4 日志定位

测试实例 debug.log 确认运行 0.6.2.3，6818 行记录实际加载 `E:/rlc/PCL/.minecraft/versions/1.21.1-NeoForge_21.1.250/config/winefox_skyfarer-server.toml`。本轮读取该文件第 237 行为 `extra_elytra_items = []`，没有其他同名配置文件。按统一身份判定，此时指定的 Cataclysm 胸甲会返回 **NOT_ELYTRA_EQUIVALENT**，与是否 Unbreakable 无关。日志未记录当时的胸甲堆栈，不能追认全部历史堆栈组件；未推测是谁或哪个操作清空配置。本轮仅在实际配置内补入用户指定 ID，原文件保存至 `build/diagnostics/winefox_skyfarer-server.before-0624.toml`，其余配置逐字核对未改。

原代码将所有 `ensureWings=false` 统称为 ELYTRA_LOW_DURABILITY，并在离地时接管飞控。debug.log 7847、7859 等行重复出现 GROUNDED→RETURN_TO_OWNER，紧接着 glide=false、LANDING，说明普通地面移动的短暂离地也触发了接管；MobAirControlMixin 随后阻止 Navigation/MoveControl.tick，TlmAirControlMixin 清除地面路径。修复后只有已托管飞行或真实滑翔才进入装备返航，普通跳跃/下台阶保留地面导航。保留了真实空中低耐久安全返航。

关键日志摘录见 `tools/equipment-qualification-evidence.txt`。本轮未启动 Minecraft，台阶运动回放和真实 TLM 调用检查不能代替完整游戏寻路测试。

## 鞘翅与地面战斗

`WingInventory` 的剩余耐久门槛是 **20**；19 禁止主动起飞，20 可以。不可损坏的兼容装备按无限耐久处理。`SkyfarerEquipment.elytra()` 只判断身份与该物品自己的 `canElytraFly`，不能把耐久 20 门槛塞进这个方法，否则高空恢复会误认为已经失去滑翔能力。

`DataComponents.UNBREAKABLE` 优先于耐久数值和可损坏行为；即使内部 maxDamage=1、damage=1 或其他行为仍报告可损坏，也不会触发低耐久拒飞/返航。没有可消耗耐久（不可损坏或 maxDamage≤0）的非空装备同样不受 20 门槛影响；普通可损坏物品仍按 maxDamage−damage 判断。“耐久/Unbreaking”附魔不是“无法破坏/Unbreakable”，不能免除此门槛。所有情况仍要先满足配置身份与物品自身滑翔能力。

核对测试实例 Cataclysm 3.33 源码：该胸甲自身允许滑翔、不在 elytraFlightTick 消耗耐久，正常默认组件注册会设置 UNBREAKABLE；旧版的标准 isDamageableItem 路径也应放行。没有引用 Cataclysm 类或修改依赖。

`WingInventory.qualification` 统一输出 READY / EMPTY_CHEST / NOT_ELYTRA_EQUIVALENT / LOW_DURABILITY / ITEM_CANNOT_GLIDE，供当前胸甲、备用搜索、起飞与指挥任务共用。身份失败不再称为低耐久。备用搜索记录每个非空槽的资格和交换拒绝原因，换装后再次核对实际胸甲资格。

开启 `/wfs debuglog on` 后，`EquipmentDiagnostics` 输出 EQUIPMENT_CHECK（item、listMatch、equivalent、damage/maxDamage、damageable、Unbreakable/MAX_DAMAGE/DAMAGE component 是否存在、remaining、canFly、实际列表）、EQUIPMENT_SPARE（slot、同样的堆栈字段、拒绝/接受原因）、EQUIPMENT_RESULT（替换结果、最终 equipmentQualified/reason、长矛/烟花、地面/滑翔状态）。首次、判定快照变化和每 200 ticks 输出，不与持久对话去重共用开关；关 debug 后清除采样记录。控制器地面拒绝也输出精确 reason，而不是仅写死低耐久。非耐久失败使用装备不合适的气泡台词。

`ensureWings()` 在控制器每 tick、起飞前和指挥任务资格/赶路入口运行。胸甲不适合继续飞行时，在 TLM 当前可用库存中选择剩余耐久至少 20、自己允许滑翔的备用。使用 TLM 的可修改库存交换：旧胸甲回到备用原槽，备用进入胸甲栏；不能提取或不能存入旧装备的槽跳过，不丢弃物品。

无备用时，地面保持 `GROUNDED`，取消起飞；高空立即终止主动攻击并进入 `RETURN_TO_OWNER`，保留 `FALL_FLYING`。创造/普通指挥赶路先撤销订单及相应票据，交由现有安全返航控制器接管。到地面后才正常清滑翔标志。当前装备已实际损坏、第三方撤销飞行能力时无法凭配置创造滑翔能力。

`GroundSpearCombat` 使用既有战略目标筛选、TLM `WalkTarget`、视线、攻击冷却和普通 `doHurtTarget`；不启动右键或 charge。主手长矛仍是工作模式资格，翅膀/烟花属于起飞检查，坐下、睡觉、无主人等工作禁止条件继续生效。

Spears 1.8.0 普通 `PiercingWeapon.stab` 使用完整 `ATTACK_RANGE`（标准长矛为 **2–4.5 格**），不能复用 KineticWeapon 的 mob 半程、速度门槛或空战 Strike 范围。TLM 的默认 ENTITY_INTERACTION_RANGE=2，近战额外读取好感度距离档位。本版保留长矛自身基础范围，增加 `max(0,交互距离−2)+max(0,TLM 好感度距离加成)`。例如好感度距离加成为 2 时，标准长矛普通刺击范围是 2–6.5 格。不是每个好感度点都加距离，仍按 TLM 原有档位。

`MaidSpearMeleeMixin` 仅为服务端、着地且未滑翔的骑空士修改普通 stab 的实际 collector 最大值；最小盲区、原生命中/伤害、遮挡及友军过滤保留。地面行为用相同加成，瞄准后检查身体与射线相交、原生 collector 遮挡，最大值额外预留 0.2 格且不依赖移动延伸；没有接触就继续寻路，不空挥。敌人进入范围内侧时，用普通地面寻路后撤到有效区间约 65% 处；仍能刺中时边撤边打，完全进入盲区时先后撤。寻路负责实际地形，不能保证被围堵时一定能退开。

地面追敌沿用 TLM 已注册的 MoveToTargetSink → MaidPathNavigation → MaidMoveControl → JumpControl，不直接写 velocity 或自建台阶路径。追同一实体保留动态 EntityTracker/WalkTarget；后撤目标变化不到 0.5 格不重设。普通跳跃期间不瞄准/重写移动目标，也不清除路径或接管飞控；进入真实命中距离后才停止当前追击路径。

装备阻止起飞、空中返航、落地切换分别通过 TLM `ChatBubbleManager.addTextChatBubble` 提示。`EquipmentNoticePolicy` 的故障事件、待发事件与冷却保存到女仆 persistent NBT，控制器重建/实体重载不重置。同一故障每种事件最多一次，恢复可用状态持续 40 ticks 后才为下一次故障重新启用；每只至少间隔 200 ticks。每个维度同一主人至少间隔 40 ticks，并按 UUID 错开首次提示 1–40 ticks，拥挤时保留一个当前待发事件，过时提示丢弃。20 TPS 下分别为 10 秒、2 秒；不增加玩家聊天消息。

## 可选鞘翅物品

SERVER 配置 `winefox_skyfarer-server.toml` 新增：

```toml
[equipment]
extra_elytra_items = ["cataclysm:ignitium_elytra_chestplate"]
```

0.6.2.5 默认包含 `cataclysm:ignitium_elytra_chestplate`。已有配置（包括用户主动设置的空列表）不会强制覆盖；`/wfs config reset all` 恢复含此 ID 的默认列表，用户仍可增删。配置只按注册 ID 识别身份；没有反射、第三方类引用或硬前置。拼错、无效或未安装 Mod 的 ID 不匹配物品，安全忽略；仍必须通过该物品自己的 `ItemStack.canElytraFly(maid)`，普通胸甲入列表不会获得飞行能力。修改文件并重载 SERVER 配置生效，数值 `set` 命令不编辑列表。

## 0.6.2.2–0.6.2.6 激流的原版依据与接入（历史行为）

对照本地 Minecraft 1.21.1 / NeoForge 21.1.250 mapped 源码的 `TridentItem.releaseUsing/use`、`Enchantments`、`EnchantmentHelper`、`Entity.isInRain`、`Level.isRainingAt`、`Player.startAutoSpinAttack` 和 `LivingEntity.checkAutoSpinAttack`。来源归档与 SHA 见 [物理说明](VANILLA_PHYSICS.md)。TLM 1.5.3 的三叉戟任务没有可复用的激流释放流程。

- 需至少 **10 tick** 使用时间，三叉戟耐久不能到 `maxDamage−1`。开始和释放时均重新检查湿润环境。
- `isInWaterOrRain()` 使用实际水中状态或脚部/头部位置的 `isRainingAt`；后者检查天空可见、降水高度图及生物群系降水。全局下雨但有屋顶、或落雪，都不会冒充可激流的雨水。
- `EnchantmentHelper.getTridentSpinAttackStrength(stack, maid)` 读取真实附魔效果，原版 I/II/III 为 **1.5 / 2.25 / 3.0**。按原版 float `Mth` 查表、归一化 look 和乘法顺序，将推进向量**加到**当前速度，不替换动量。
- 达到使用时间并通过释放事件后，在三叉戟 releaseUsing 回调内消耗 1 耐久，设置原版 LivingEntity 的 **20 tick** spin 字段/标志并叠加冲量、播放声音；原生 releaseUsingItem 最后停止使用物品。原版冲刺分支仅接受 Player，故以受控释放 Mixin 和小型 accessor 适配女仆，不创建假玩家、不生成三叉戟投射物、不另造激流伤害。
- 原版触碰生物会终止 spin 并反转速度，碰墙也终止。候选及每 tick 重验排除 spin 段内现有生物的扫掠箱（含主人/友军）；外界后来移动导致碰撞，下一 tick 用真实速度恢复，不能保证动态环境永不改变。

`RiptidePlanner` 最多采样 3 种背包/副手推进强度、每种 3 个俯仰候选，认证 **10 前摇 + 20 spin + 12 恢复 tick** 的身体扫掠、受限转向、原版滑翔/水中移动、目的地余量和末段速度。空中 coast 足够时不补能；需要推进时，**安全可执行 Riptide 优先于 Firework**，取消激流与火箭成本比较及火箭专用航向门槛。激流自身完整模拟负责转弯安全。活动烟花仍会排除激流，因为未认证两种推进同时叠加；火箭 cooldown 不影响激流。

候选异步等待时，空中最多等待 11 个决策 tick，地面最多 39 个决策 tick，避免每次后台尚未完成就先点火；超出预算、物理无解或执行重验失败时允许烟花后备。未承诺 Strike 的 APPROACH 不再因请求含 aim 而完全跳过激流；已承诺 Strike/Exit 仍不能随意插入另一套推进。

三叉戟可来自副手或 TLM 当前可用背包；选中后与原副手交换，保留长矛主手。TLM getAvailableBackpackInv 包含当前开放的女仆库存槽，非仅外置背包。按 EnchantmentHelper 读取真实强度，排除不可取出/无法交换/即将损坏物品。激流结束后三叉戟保留在副手。**没有人工冷却，也不读取火箭 cooldown**；仍不得打断使用中的物品或原生 20 tick spin。12 tick 恢复属于安全预测窗口，不额外阻止下一次决策。取消未释放使用后，只要重新符合条件即可再次规划。环境变化、物品变化、前摇不足或预测不安全均拒绝释放且不消耗耐久；已释放速度和滑翔标志不撤销。

起飞资格不再先要求烟花。TAKEOFF_PREP 优先认证激流：**地面预转向 → 地面至少 10 tick 前摇 → 原有 JumpControl 起跳 → 实际离地后开启 Elytra → 当前状态再次认证 → 激流释放**。地面前摇与跳跃身体空间在主线程检查，离地以后的激流/旋转/恢复路径使用滑翔尺寸数值快照，Worker 不读取实体。选择时计入地面自由转向，释放后仍遵守有动量的受限转向。此顺序使用原版合法的空中释放，未伪造地面冲量，也不执行仅地面释放才有的 1.1999999 格抬升。

起飞无安全激流则尝试完整寿命认证的烟花，再尝试有完整滑翔预测的 coast 起飞；无解仍退出起飞。水中沿用 WATER_ESCAPE 离开水底/滑翔路径，在原来入口同样优先激流，既有水中移动模型不变。激流结束后丢弃旧证书；起飞完成可从现有战略合法目标尝试 Launch Attack，并从真实当前动量重做 Rejoin 与 Strike→Exit 认证，无证书则正常 CLIMB/APPROACH。旋转尚未结束时不会使用长矛攻击。

后台只有数值 `PlanningTask.Riptide`（按强度、水/雨与起飞模式分 slot）、不可变 trace 与碰撞快照，不持有物品、实体或 Level。执行前/期间重验单一路线，缺数据拒绝；共享线程池/快照/任务上限不变。debuglog 的 PROPULSION 记录环境、可用强度、候选等待/预算耗尽/物理拒绝/执行拒绝/选择结果及独立的 rocketCooldown；RIPTIDE 记录 WINDUP、LAUNCH_WINDUP、LAUNCH_JUMP、RELEASED、PATH_REJECTED、END。

## 定向验证与边界

0.6.2.5 当前证据：`tools/riptide-priority-acceptance.log`，compileJava、verifyRiptidePriority、verifyEquipmentPropulsion、verifyEquipmentLifecycle、jar 全部通过。新增实际异步选择器在火箭冷却中选择激流；完整控制器从雨中地面、背包取出激流 III，经过前摇/原生跳跃入口/滑翔实际执行释放，有烟花与无烟花两种场景均零烟花消耗；环境拒绝、原生旋转约束、取消后无人工冷却；默认兼容 ID 身份/能力分离及未安装安全忽略。保留 1,500 组真实 Mth 向量差分、I/II/III 强度、撞墙/overshoot 拒绝、失雨取消与过期快照拒绝。测试中的物品/世界/TLM 生命周期为隔离夹具；真实环境 API 和生产选择入口另检查依赖字节码。没有启动 Minecraft、全量 verify 或声称已完成实机雨天测试。

以下为历史交付证据：

0.6.2.4 当前证据：`tools/equipment-qualification-acceptance.log`，compileJava、verifyEquipmentFixes、verifyGroundMelee、jar 通过。新增空列表精确拒因、列表内 Unbreakable 通过、物品能力拒绝、备用逐槽日志及最终结果、持续故障诊断不受对话去重影响；实际控制器与地面攻击行为贯穿一格台阶的上升/下降/落地回放，断言始终不接管飞控、不改动导航动量、不换 WalkTarget，落地后继续接近并命中。跳跃和台阶碰撞由外部夹具提供，真实 TLM 的 MoveToTargetSink 注册及 MaidMoveControl→JumpControl 调用另外核对依赖字节码；没有运行真实世界寻路或第三方胸甲实例。

以下为 0.6.2.3 的交付记录；本轮复用并扩展相同两项定向任务：

0.6.2.3 当前证据：`tools/equipment-fixes-acceptance.log`，只运行 compileJava、verifyEquipmentFixes、verifyGroundMelee、jar，全部通过。

- `verifyEquipmentFixes`：实际耐久/库存逻辑的 Unbreakable 极小内部耐久、无可消耗耐久、普通 19/20 和备用交换；实际控制器高空无备用返航/降落保持滑翔；实际地面行为走到接触范围、盲区后撤、边撤边打、好感度/交互属性加成；实际提示适配器持续故障、实体 NBT 重建、修复后新故障、200 tick 冷却、20 只同 owner 错峰且都得到提示。外部实体/物品/世界和原生伤害调用使用隔离夹具。
- `verifyGroundMelee`：真实 MC AABB/Vec3 的普通接触、射线起点在箱内、加成范围与近身盲区；真实 Spears 普通 stab 的 collector 描述符，以及已编译 Mixin、好感度调用、预测检查和 doHurtTarget 连接。没有启动 Mixin 游戏环境，不将接口校核当成实机命中/寻路验证。

以下为 0.6.2.2 历史验证，本轮未重跑与修复无关的激流/交接范围：

`verifyEquipmentPropulsion`：真实依赖 `Mth` 的 1,500 组独立激流向量差分、42 tick 完整轨迹、墙体/越过目标拒绝，以及实际 MC 天空/高度图判断和地面普通攻击调用连接检查。

`verifyEquipmentLifecycle`：实际 `WingInventory` 的 19/20 边界、交换守恒、不合适备用、拒收旧装备、配置识别/缺失 ID/普通胸甲无飞行能力；实际 AirCombatController 的禁止起飞、空中备用交换、无备用返航不清旗、落地 fallback 和气泡去重；实际 RiptidePropulsion + 异步快照的湿润条件、前摇中失去雨水、冷却重试、释放推进/耐久/spin。物品、TLM、世界由隔离夹具提供，不会打进 Jar。

0.6.2.2 同时跑高空交接相关 `verifyHandoffStability`。两轮均不跑全量 verify，不启动 Minecraft。真实第三方胸甲能力、TLM 寻路/气泡渲染及 Minecraft 网络动画还需用户实机确认；离线证据不冒充这些实机结果。
