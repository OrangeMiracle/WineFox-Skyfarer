# 车万女仆: 骑空士

**Touhou Little Maid: Skyfarer**

> 酒狐会种田、会做饭、会施法、会打架
> 所以会飞，应该也很合理吧？

**车万女仆: 骑空士** 是一个为 **Touhou Little Maid** 制作的 NeoForge 附属模组，为女仆新增工作模式 **「骑空士」**。

装备好 **鞘翅、长矛和推进装备（永燃彗星之心、雨中/水中的激流三叉戟或烟花）** 后，女仆将不再满足于站在地上和怪物互殴。

她会：

- 使用鞘翅与经过认证的推进自主起飞
- 搜索并选择适合攻击的目标
- 从空中建立攻击航线
- 高速俯冲并使用长矛发动攻击
- 命中后立即脱离，而不是贴着目标刮痧
- 拉升、重新调整航向，然后开始下一轮攻击
- 在没有敌人后返回主人附近并降落
- 追着天上的幻翼打……至少我们的目标是这样

简单来说：

**给酒狐一根长矛和几发烟花，然后获得一架需要喂蛋糕的生物战斗机。**

如果你想问为什么酒狐连这个都会：

建议不要深究。

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.250
- Touhou Little Maid 1.5.3
- Backported Spears 1.8.0

## 许可证

本项目按 GNU Lesser General Public License v3.0 only（LGPL-3.0-only）授权，完整条款见 [LICENSE](LICENSE)。


## 编译与安装

维护请先阅读 [工程总索引](docs/PROJECT_INDEX.md)；飞控、异步规划、配置调试与验证说明统一维护在 `docs/`。修改实现时须同步更新相关工程文档。

```powershell
.\gradlew.bat build
```

## 配置项详解

| 配置键 | 默认 | 范围 | 用途 |
| --- | ---: | ---: | --- |
| `command_mark_range` | 256 | 16–512 | 指挥镜最大标记距离 |
| `command_home_range` | 128 | 16–512 | 生存镜呼叫 Home 女仆的距离 |
| `creative_command_range` | 1000 | ≥0，0 表示无限 | 创造镜呼叫距离，以玩家为中心；已接受任务不会因玩家移动而取消 |
| `creative_transit_speed_multiplier` | 2 | 1–100 | 创造赶路速度倍率，临近目标和障碍时自动减速 |
| `creative_transit_height` | 200 | −64–1024 | 创造赶路参考世界 Y 坐标，实际受维度高度及航程限制 |
| `command_mark_bonus` | 150 | 0–1000 | 标记目标在原有路线评分中的加分 |
| `prediction_length_multiplier` | 1 | 1–10 | 实际轨迹预测时域倍率，位于 `flight` 分类 |

以上键也可通过 `/wfs config set <键> <值>` 调整。实际标记还受服务器已加载区块限制；不会为了射线标记生成一路新区块。注册表按主人分别记录，旧存档从 TLM 的女仆信息初始化，再以每次最多 2 位、同时最多 8 位的短暂唤醒核实真实归属、任务和 Home 状态。

例如 `/wfs config set prediction_length_multiplier 2` 延长预测至 2 倍，`/wfs config reset prediction_length_multiplier` 恢复 1 倍。普通滑翔的基准为 24 ticks，10 倍为最多 240 ticks；起飞、紧凑转向使用各自原有基准。倍率扩展实际碰撞采样和候选路线评分，黄色通道随之延长，遇到障碍或未加载边界会提前截止。现有近段及当前火箭周期仍执行硬性安全检查，新增远段以递减权重评估风险，不把尚未决定的后续补能当成永远不会发生。倍率指预测时间，不保证实际空间距离正好同比增加。更高倍率增加计算与调试数据量；创造调度的前方加载窗口也按倍率扩展，最多 480 格，旧窗口仍会释放。

```text
/wfs config list
/wfs config get <parameter>
/wfs config set <parameter> <value>
/wfs config reset <parameter>
/wfs config reset all
```

数值范围与整数 tick 校验由服务器执行。`reset all` 会将所有配置项恢复为默认值。
要求 `combat_range <= hard_combat_range < emergency_teleport_range`，扩大作战范围时先调整外层范围。

| 配置组 | 配置项（默认值） | 单位 | 作用 |
| --- | --- | --- | --- |
| flight | `max_turn_angle`（120） | 度 | 限制攻击进场时可接受的最大水平转向角，避免选择需要大幅掉头的航线。 |
| flight | `dive_max_correction_angle`（40） | 度 | 限制攻击途中相对既定攻击方向的修正幅度；超出时放弃本次攻击并离场。 |
| flight | `turn_rate`（5） | 度/tick | 普通飞行时每 tick 最多转动的水平角度；数值越大，转弯越快。 |
| flight | `attack_turn_rate`（2.5） | 度/tick | 攻击和举矛阶段每 tick 最多转动的水平角度。 |
| flight | `pitch_rate`（3） | 度/tick | 普通飞行时每 tick 最多调整的俯仰角度。 |
| flight | `pull_up_pitch_rate`（4） | 度/tick | 普通拉升时每 tick 最多调整的俯仰角度。 |
| flight | `recovery_pitch_rate`（8） | 度/tick | 紧急恢复阶段拉升时的俯仰调整速度。 |
| flight | `slope_pitch_rate`（6） | 度/tick | 坡面攻击期间每 tick 最多调整的俯仰角度，用于跟随上坡或下坡航线。 |
| flight | `slope_pull_up_pitch_rate`（10） | 度/tick | 坡面攻击需要快速拉起时的俯仰调整速度。 |
| flight | `minimum_boost_speed`（0.8） | 格/tick | 普通飞行补能预测使用的最低目标速度；预计滑翔后低于此速度时，飞控会评估是否需要烟花。 |
| flight | `energy_reserve`（0.08） | 能量指标 | 普通飞行保留的最低预测能量；设得越高，飞控越早考虑补充烟花推进。 |
| flight | `attack_minimum_boost_speed`（0.4） | 格/tick | 攻击航线补能预测使用的最低速度基准，实际还会考虑长矛的伤害速度要求。 |
| flight | `attack_energy_reserve`（0.02） | 能量指标 | 攻击阶段保留的最低预测能量；较低的值允许飞控牺牲部分速度，以换取更灵活的攻击角度。 |
| flight | `firework_cooldown_ticks`（30） | ticks | 两次烟花推进之间必须间隔的最短时间。 |
| flight | `takeoff_timeout_ticks`（60） | ticks | 起飞阶段允许的最长时间；超时仍未完成起飞时会中止起飞并返航。 |
| targeting | `search_range`（32） | 格 | 女仆周围的常规索敌半径；作战中心和已锁定目标还会按其他范围规则查询。 |
| targeting | `combat_range`（40） | 格 | 相对主人或 Home 作战中心，允许选择新目标的范围。 |
| targeting | `hard_combat_range`（56） | 格 | 相对作战中心继续追击已选目标的范围；实际值还受紧急返航范围约束，并至少比新目标范围多 16 格。 |
| targeting | `target_switch_threshold`（15） | 评分 | 同一目标优先级层级中，新目标评分至少高出当前目标此数值才切换；越大越倾向保留当前目标。 |
| targeting | `scan_interval_ticks`（10） | ticks | 两次常规索敌扫描之间的间隔。 |
| combat | `attack_entry_distance`（18） | 格 | 规划攻击入口时使用的距离参考；实际入口还会根据速度、长矛前摇和可用转向时间调整。 |
| combat | `breakaway_distance`（12） | 格 | 离场距离的原始参考值；实际规划取旧计算结果的 2/3（基础航线参考距离为 8 格），并结合速度和转向半径确定上限。 |
| combat | `loiter_time_ticks`（80） | ticks | 没有找到目标时继续盘旋索敌的最长时间；到时开始返航。 |
| combat | `waypoint_tolerance`（3） | 格 | 爬升阶段接近目标高度多少格以内时，可开始转入攻击进场。 |
| combat | `attack_timeout_ticks`（100） | ticks | 单次攻击航线允许持续的最长时间；超时后放弃该次攻击并离场。 |
| combat | `max_target_displacement`（10） | 格 | 攻击开始后目标相对锁定位置可移动的最大距离；超过时中止当前攻击航线。 |
| combat | `approach_height`（4） | 格 | 普通/坡面进场的抬高参考；平地曲线俯冲准备时取至少 9 格，具体路线仍受碰撞和可用机械能限制。 |
| combat | `level_run_distance`（14） | 格 | 保留的平飞/低顶攻击路线参考；平地曲线俯冲使用连续收平曲线，坡面使用坡度路线。 |
| combat | `reposition_distance`（28） | 格 | 重定位入口距离的参考上限；实际还需预留前摇及俯冲距离，入口上限为 `min(34, 此值 + 6)`。先汇入固定入口区域，再朝目标转入；普通入口转向半径为 32–48 格，低能量恢复和返航另按目的地距离预留余量。所有转向仍受作战/加载边界限制，高度不随重试累加。 |
| combat | `attack_ground_clearance`（0.75） | 格 | 攻击航线与目标脚下地面的最低净空，避免俯冲时过度贴近地面。 |
| combat | `attack_aim_offset`（0.5） | 格 | 相对目标碰撞箱中心向上的瞄准偏移，可调 0–3。实际接触航线仍保留最低地面净空；不移动目标碰撞箱或扩大伤害范围。 |
| combat | `spear_vertical_expansion`（3） | 格（上下各） | 骑空士长矛判定额外扩展目标的竖向范围；不改变目标真实碰撞箱，设为 0 可关闭。 |
| combat | `max_failed_passes`（2） | 次 | 连续攻击同一目标未命中的次数上限；达到后有其他目标时会尝试换目标。 |
| combat | `post_attack_charge_ticks`（60） | ticks | 攻击后保留 Charge 的最长时长；二段追击在此时间内最多使用 40 ticks、18 格航程，最多主动追击两个次要目标。不阻塞离场或下次进场评估；新主攻击重新举矛。设为 0 关闭保留及二段追击。 |
| combat | `secondary_target_radius`（6） | 格 | 原多穿与二段追击的候选半径。第二、第三目标都须处于主目标锁定位置周围，不逐个向外扩大；不参与主目标选择。已有配置保留原值。 |
| combat | `slope_sample_radius`（16） | 格 | 坡面八方向采样的最大水平半径，可调范围 3–32；同时拟合近处和完整范围的坡度，读取真实碰撞形状的小数高度。已有配置保留原值，可用 `/wfs config set slope_sample_radius 16` 更新。 |
| combat | `slope_sample_step`（0.5） | 格 | 相邻坡面采样点的水平间距，可调范围 0.25–1；越小采样越密、计算量越大。 |
| safety | `emergency_teleport_range`（96） | 格 | 女仆远离主人或 Home 作战中心时触发紧急返航/安全传送的距离阈值，也限制可设置的续追范围。 |
| safety | `corridor_side_margin`（0.35） | 格 | 飞行身体碰撞走廊左右两侧额外保留的安全余量。 |
| safety | `corridor_vertical_margin`（0.35） | 格 | 飞行身体碰撞走廊上方和下方额外保留的安全余量。 |
| safety | `return_timeout_ticks`（300） | ticks | 返航允许持续的最长时间；超时会尝试使用对应的安全落点/传送恢复。 |
| safety | `max_airborne_ticks`（2400） | ticks | 一次空战任务允许连续飞行的最长时间；到时结束空战并返航。 |
| safety | `launch_retry_ticks`（60） | ticks | 起飞失败或结束后，等待多久再允许重新尝试起飞。 |

所有 AI 设置通过 `SkyfarerSettings` 读取。实际进场窗口根据前摇/速度/可用转向时间计算；`max_turn_angle` 是外层限制，攻击入场和俯冲另有对齐要求。`combat_range` / `hard_combat_range` 分别是相对本轮活动中心的新目标/续追范围的基础配置，不直接裁剪女仆的转弯航线。Home 模式以工作中心为中心，保护半径为工作范围加 16 格；实际 combat 范围不小于保护半径，hard 范围不小于实际 combat 范围加 16 格。

## 已知限制

动画效果、实际命中率、复杂地形与多人网络仍需要更多实测。短期预测不等同于完整三维寻路；完全封闭水域会释放控制回到女仆原有游泳流程。YSM 2.6.5 使用模型已有的滑翔动画和鞘翅挂点，缺失动画或其他 YSM 版本保留身体倾斜回退。遥控器只访问同维度已加载实体，不强制加载区块。带爆炸效果的烟花保留原版效果。
