# WineFox: Skyfarer

> 酒狐会种田、会做饭、会施法、会打架
> 所以会飞，应该也很合理吧？

**WineFox: Skyfarer** 是一个为 **Touhou Little Maid** 制作的 NeoForge 附属模组，为女仆新增工作模式 **「骑空士」**。

装备好 **鞘翅、烟花火箭和长矛** 后，女仆将不再满足于站在地上和怪物互殴。

她会：

- 使用鞘翅与烟花自主起飞
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

## Current Features

- 新工作模式：**骑空士 / Skyfarer**
- 鞘翅自主飞行
- 烟花推进
- 长矛高速俯冲攻击
- 一击脱离式空战 AI
- 目标选择与攻击航线规划
- 可配置转向速度、转向角度、索敌范围等参数
- `/wfs` 游戏内实时调参

## Philosophy

**彗影破空，长矛贯下**

## 许可证

本项目按 GNU Lesser General Public License v3.0 only（LGPL-3.0-only）授权，完整条款见 [LICENSE](LICENSE)。


## 编译与安装

```powershell
.\gradlew.bat build
```

## 配置项详解

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
| targeting | `previous_target_bonus`（30） | 评分 | 旧版给上次攻击目标增加的评分；为兼容保留，当前目标优先级改用分层规则，此项不再影响评分。 |
| targeting | `target_switch_threshold`（15） | 评分 | 同一目标优先级层级中，新目标评分至少高出当前目标此数值才切换；越大越倾向保留当前目标。 |
| targeting | `scan_interval_ticks`（10） | ticks | 两次常规索敌扫描之间的间隔。 |
| combat | `climb_height`（14） | 格 | 旧版搜索盘旋高度参数，当前版本飞控不再使用，保留仅为兼容已有配置。 |
| combat | `attack_entry_distance`（18） | 格 | 规划攻击入口时使用的距离参考；实际入口还会根据速度、长矛前摇和可用转向时间调整。 |
| combat | `breakaway_distance`（12） | 格 | 攻击通过目标后，离场航线沿飞行方向延伸的参考距离。 |
| combat | `loiter_time_ticks`（80） | ticks | 没有找到目标时继续盘旋索敌的最长时间；到时开始返航。 |
| combat | `waypoint_tolerance`（3） | 格 | 爬升阶段接近目标高度多少格以内时，可开始转入攻击进场。 |
| combat | `attack_timeout_ticks`（100） | ticks | 单次攻击航线允许持续的最长时间；超时后放弃该次攻击并离场。 |
| combat | `max_target_displacement`（10） | 格 | 攻击开始后目标相对锁定位置可移动的最大距离；超过时中止当前攻击航线。 |
| combat | `approach_height`（4） | 格 | 攻击进场点相对目标攻击高度的抬高量。 |
| combat | `level_run_distance`（14） | 格 | 平飞攻击航线中，接近目标前保留的平飞距离。 |
| combat | `reposition_distance`（28） | 格 | 攻击对齐不合适时，先拉开并重新汇入航线所用的基础距离。 |
| combat | `attack_ground_clearance`（0.75） | 格 | 攻击航线与目标脚下地面的最低净空，避免俯冲时过度贴近地面。 |
| combat | `spear_vertical_expansion`（3） | 格（上下各） | 骑空士长矛判定额外扩展目标的竖向范围；不改变目标真实碰撞箱，设为 0 可关闭。 |
| combat | `max_failed_passes`（2） | 次 | 连续攻击同一目标未命中的次数上限；达到后有其他目标时会尝试换目标。 |
| combat | `post_attack_charge_ticks`（60） | ticks | 攻击通过目标后继续保持长矛 Charge 的时长，可用于离场路线上的连续穿刺；设为 0 可关闭。 |
| combat | `secondary_target_radius`（3） | 格 | 主目标周围寻找多穿机会的半径；只影响次要目标路线，不参与主目标选择。 |
| safety | `emergency_teleport_range`（96） | 格 | 女仆远离主人或 Home 作战中心时触发紧急返航/安全传送的距离阈值，也限制可设置的续追范围。 |
| safety | `forward_collision_check_distance`（6） | 格 | 旧版前方碰撞检查距离；为兼容配置保留，当前动态碰撞走廊规划不使用此值。 |
| safety | `corridor_side_margin`（0.35） | 格 | 飞行身体碰撞走廊左右两侧额外保留的安全余量。 |
| safety | `corridor_vertical_margin`（0.35） | 格 | 飞行身体碰撞走廊上方和下方额外保留的安全余量。 |
| safety | `return_timeout_ticks`（300） | ticks | 返航允许持续的最长时间；超时会尝试使用对应的安全落点/传送恢复。 |
| safety | `max_airborne_ticks`（2400） | ticks | 一次空战任务允许连续飞行的最长时间；到时结束空战并返航。 |
| safety | `launch_retry_ticks`（60） | ticks | 起飞失败或结束后，等待多久再允许重新尝试起飞。 |

所有 AI 设置通过 `SkyfarerSettings` 读取。实际进场窗口根据前摇/速度/可用转向时间计算；`max_turn_angle` 是外层限制，攻击入场和俯冲另有对齐要求。`combat_range` / `hard_combat_range` 分别是相对本轮活动中心的新目标/续追范围，不直接裁剪女仆的转弯航线。Home 模式用相同配置范围，以 Home 为中心。

## 已知限制

动画效果、实际命中率、复杂地形与多人网络仍需要更多实测。短期预测不等同于完整三维寻路；完全封闭水域会释放控制回到女仆原有游泳流程。YSM 2.6.5 使用模型已有的滑翔动画和鞘翅挂点，缺失动画或其他 YSM 版本保留身体倾斜回退。遥控器只访问同维度已加载实体，不强制加载区块。带爆炸效果的烟花保留原版效果。
