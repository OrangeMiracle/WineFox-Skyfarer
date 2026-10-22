# 装备、生存与激流推进

当前版本 **0.7.7**，以下为当前装备规则；推进与 READY 的契约见 [飞控总览](FLIGHT_CONTROL_OVERVIEW.md)。Fox3 武器弹药独立于飞行动力，见下节。历史问题、日志与迭代证据保留在 Git 历史和 [buildHistory](buildHistory.md) 中。

## Fox3 武器与推进资源

Fox3 默认 `fox3.consume_fireworks=false`，免费发射，不查询或消耗库存。改为 `true` 后，每枚实际成功生成的导弹从女仆可用库存扣除一枚普通烟花火箭；不要求烟火之星、颜色或特殊爆炸组件，Fox3 始终自行生成橙色视觉。实体生成失败或回调导致弹药改变时取消该枚，不复制烟花。

普通彗星之心的推进节能概率、永燃彗星之心的免费虚拟推进及指挥赶路的免费推进均只适用于原有飞行动力。它们不减免 Fox3 的弹药，不成为 Fox3 的动力来源；开启武器耗材后，装备永燃之心也必须支付每枚一枚烟花。发射不会抢占长矛使用或女仆航向。`fox3.enabled` 只关闭/开启自动发射，调试棒仍可手动测试；手动也遵守当前弹药规则。完整参数见 [配置参考](CONFIG_DEBUG_REFERENCE.md#fox3-配置)。

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


## 激流与资格

装备与普通/创造指挥通过 `PropulsionCapability` 实时检查合法雨/水中的可用激流或烟花；保存的远程注册表不推断天气。起飞、空中恢复、攻击离场、紧急和 Transit 共用 `FlightPropulsion`。

READY 独立准备 10 tick；副手释放不占用/取消主手 Spears Charge。I/II/III 提供最大 10/20/30 tick 的可控推力，安全起飞和接管余量优先于简单选择最短时间。真实环境、原生物品释放事件、耐久、声音与 spin 仍被验证。

离线测试范围与实机边界见 [验证](VALIDATION.md)。
