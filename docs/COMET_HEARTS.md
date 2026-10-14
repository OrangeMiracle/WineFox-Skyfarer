# 彗星饰品与虚拟推进（0.7.4）

两件物品使用 **TLM 1.5.3 原生女仆饰品栏**。`SkyfarerExtension.bindMaidBauble` 向实际依赖的 `BaubleManager` 绑定 `IMaidBauble`；`CometBaubles` 只检查 `EntityMaid.getMaidBauble()`。背包、副手、玩家装备、普通胸甲及 Curios 都不授予效果。飞行效果仅用于骑空士任务；永燃之心存在时普通之心不再提供节能或觉醒进度。

## 物品与数据

- `winefox_skyfarer:comet_heart`：RARE，单件堆叠。成功的实际烟花发射默认有 60% 概率返还该次燃料；SERVER 配置 `accessories.comet_firework_saving_chance` 范围为 0.5–0.7。生成成功、库存提交成功之后才抽取一次随机数。候选、预测、冷却拒绝、生成事件取消均不抽奖。生成事件改变了燃料时撤销火箭；原烟花数据、推进实体、真实寿命与完整认证不变。原有免费 Transit 发射不重复抽取节能随机数。
- `winefox_skyfarer:everburning_comet_heart`：EPIC，单件堆叠，提供独立 `VIRTUAL_PROPULSION`。没有自然战利品来源或公开合成配方。
- 普通之心使用 NeoForge additive Global Loot Modifier，林地府邸、远古城市、末地城宝箱分别 2%、3%、5%；`replace: false`，没有覆盖原版表。
- 觉醒进度放在原版 `CUSTOM_DATA` 的命名空间键中，随 `ItemStack` 编解码、复制与佩戴者交换保存；保留其他组件。两种隐藏路径的完整条件仅维护于实现和定向用例，不在玩家文案中公开。

## 服务端事务边界

`CometDeathMixin` 在实际 `LivingEntity.die` 的尾部并确认 `dead` 后调用服务端计数，避开可取消死亡事件；使用 `DamageSource.getEntity()` 而非最后攻击者推测归属。核对 Spears 1.8.0 字节码后，原生非玩家 Charge 伤害的 `mobAttack(攻击者)` 满足这一契约。受害实体持久标记保证一次死亡不重复计数；同一时刻只修改一件正在佩戴的普通之心。

`CometLightningMixin` 在真实、非视觉闪电首次 `tick` 的 HEAD 调用 `CometRitual`。实际 1.21.1 源码中 `spawnFire` 早于 NeoForge 逐实体雷击事件，因此不能等女仆或物品的 `thunderHit` 再付款。仪式只检查雷击点三格球内的地面 `ItemEntity`，不要求女仆、装备或天气状态；不改动原版命中列表和取消事件。

每个闪电只尝试一次。普通之心和材料按实体 ID 稳定选择，允许材料来自多个堆叠；先准备全部剩余栈和产物，再在服务端主线程重验实体存活、物品引用与世界，连续提交。直接把原之心掉落实体的栈替换为觉醒栈，保留 UUID、投掷者、拾取规则和物品组件；没有材料已扣除而新实体生成被否决的间隙。只丢弃已经扣空的材料实体，多余数量保留，失败不部分付款。后续闪光与重复事件不重试，主线程提交期间拾取不能并发插入。

两种圣物掉落物现在均永久免疫雷电、火焰（含岩浆）和爆炸伤害；成功仪式的余料 TNT 仍获得 **120 tick** 临时保护，并暂时禁止合堆，避免原版随后的雷击/火焰消灭剩余材料。临时截止时间写入实体数据，重载不延长；TNT 到期恢复原行为，圣物仍受统一保护。仪式沿用原生雷电声画，不额外生成闪电、爆炸或地形破坏。

`CometRewards` 是 overworld `SavedData` 的玩家资格账本。击杀觉醒归属于女仆主人；掉落物仪式优先使用 `LightningBolt.getCause()` 的引雷玩家，否则读取之心原版持久 `thrower` UUID，只有服务器玩家档案缓存确认其为玩家时才归属给投掷者。该路径可识别离线投掷者；无法可靠确认时仍生成物品，但不向附近旁观者发成就。资格随存档保留，上线后补发隐藏 `silver_wing_successor`；已完成时不再次 award，可应对玩家数据与世界数据的不同保存时序。图标为原版鞘翅，隐藏、正常 toast、不在聊天公开广播。离线验证不等于完整模组事件总线中的实机测试。

## 推进与控制权

统一 `PropulsionResolver` 是来源权威：永燃之心独占时仅允许虚拟推进或无动力安全恢复，认证失败不回退到耗材。无永燃时保留合法 READY 激流先于烟花的顺序。所有执行入口通过 `FlightPropulsion → PropulsionCapability → Resolver → Provider`；装备政策变化撤销旧执行与证书。持续控制与动力参数见 [0.7.3 专题](PROPULSION_073.md)；当前 Fullbright 特效、掉落保护与暖金/鲜红原生描边见 [0.7.4 专题](COMET_RADIANCE_074.md)。

`ThrustProfile` 是纯数值力模型。历史名称 `RiptidePlanner` 现在同时认证两种有限逐 tick 推力，明确保留来源和曲线；它没有把永燃之心伪装成三叉戟等级或火箭寿命。

| 契约 | 当前实现 |
| --- | --- |
| 候选强度 | 有限加速仍为 0.04 / 0.10 / 0.20、1–30 ticks；持续保持使用误差反馈与有界前馈，最高 0.20 blocks/tick² |
| 连续限幅 | 实际加力 `min(请求强度, max(0, 2.4 - 当前三维速度))`；高速俯冲已有动量不被截断，停止加力也不归零速度 |
| 每 tick 次序 | 共用受限 Heading → 同一 float look 冲量 → 原版 Elytra travel；没有额外位移、烟花实体、spin、耐久或物品使用会话 |
| TAKEOFF | 真实 JumpControl 起跳，空中实际开始滑翔后重验并推进；证明包含截止高度/速度、24 tick 无动力恢复及转向余量 |
| 空中阶段 | APPROACH、REPOSITION、AIR_RECOVERY、EMERGENCY、TRANSIT 共用需求；加速结束交回路线，保留恢复认证；持续保持每 tick 独立认证并执行 |
| STRIKE / REJOIN / EXIT | 同一 Sweeping path、移动 Aim、接触窗口、身体通道与完整 Strike→Exit；不允许凭短时安全冲量进入无离场路线 |
| 抢占 | 卸下饰品、任务/目标失效、真实下一步身体阻挡、快照认证失败、SAFETY 控制权均可立即停止；每 tick 至多一次冲量 |
| 线程和性能 | 仍使用共享有界执行池、不可变快照、现有采集总预算和每会话 768-cell 上限；仅主线程读取饰品/实体/世界 |

当前状态重验仍要求最多 20 tick 的 cells。为解决运动中的提案持续落在未采集单元前方的问题，`PlannerSession` 对该**已选中方案**的缺失单元请求一圈邻居，通过原本的全局/局部/时间预算 pump，至多重验一次；没有增加搜索、放宽未知地形或延长证书寿命。实际虚拟推力另有下一步实时身体扫掠。持续闭环在普通需求下证明 12 tick 动力 + 12 tick 无动力；攻击则证明 1 tick 动力与完整接触/Exit，每次只提交下一 tick。

## 资源与验证

两张物品图基于 `tools/素材参考`，通过 imagegen 生成，再用 `tools/export-comet-textures.ps1` 最近邻导出为 **32×32 RGBA PNG**。模型使用原版 `item/generated`，中英文物品名、含蓄 Tooltip 和成就文本齐全。[生成提示词](COMET_TEXTURE_PROMPTS.md)。

定向入口：`verifyCometContracts`、`verifyCometData`、`verifyCometReplay`、`verifyCometRitual`，另检查直接相关的 `verifyRiptideRelease` 和 `verifyArchitecture`。方式 B 修正后仅重跑编译、Ritual、Data 和打包；Ritual 同时保留方式 A 的归属、去重和转移进度回归。结果、失败调查及尚需实机验证的边界见 [VALIDATION](VALIDATION.md)。持续严重调度压力下的攻击效率不作无限时保证；安全认证始终可以拒绝推进。

0.7.4 定向入口新增 `verifyCometRadiance`（真实粒子顶点/寿命、限频与依赖契约）和 `verifyCometDrops`（统一保护、发光生命周期及原有仪式/击杀回归），并运行 `verifyCometData`、`verifyArchitecture`。没有运行全量验证。
