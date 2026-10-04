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

**给酒狐一根长矛和几发烟花，然后获得一架需要喂饭的生物战斗机。**

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

版本交付记录见 [buildHistory.md](buildHistory.md)。

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

| 配置组 | 参数及默认值 | 单位 |
| --- | --- | --- |
| flight | max_turn_angle=120, dive_max_correction_angle=40 | 度 |
| flight | turn_rate=5, attack_turn_rate=2.5, pitch_rate=3, pull_up_pitch_rate=4 | 度/tick |
| flight | minimum_boost_speed=0.8 | 格/tick，能量储备速度基准 |
| flight | recovery_pitch_rate=8, energy_reserve=0.08 | 度/tick、单位质量能量指标 |
| flight | firework_cooldown_ticks=30, takeoff_timeout_ticks=60 | ticks |
| targeting | search_range=32, combat_range=40, hard_combat_range=56 | 格 |
| targeting | previous_target_bonus=30, target_switch_threshold=15 | 评分 |
| targeting | scan_interval_ticks=10 | ticks |
| combat | climb_height=14, attack_entry_distance=18, breakaway_distance=12, waypoint_tolerance=3, max_target_displacement=10 | 格 |
| combat | loiter_time_ticks=80, attack_timeout_ticks=100 | ticks |
| combat | approach_height=4, level_run_distance=14, reposition_distance=28, attack_ground_clearance=0.75 | 格 |
| combat | spear_vertical_expansion=3 | 上下各扩展的格数 |
| combat | max_failed_passes=2 | 连续未命中次数（整数） |
| combat | post_attack_charge_ticks=60 | ticks，默认 3 秒 |
| combat | secondary_target_radius=3 | 格，主目标附近的次要目标范围 |
| safety | emergency_teleport_range=96, forward_collision_check_distance=6 | 格 |
| safety | corridor_side_margin=0.35, corridor_vertical_margin=0.35 | 格，身体每侧余量 |
| safety | return_timeout_ticks=300, max_airborne_ticks=2400, launch_retry_ticks=60 | ticks |

所有 AI 设置通过 `SkyfarerSettings` 读取。实际进场窗口根据前摇/速度/可用转向时间计算；`max_turn_angle` 是外层限制，攻击入场和俯冲另有对齐要求。`combat_range` / `hard_combat_range` 分别是相对本轮活动中心的新目标/续追范围，不直接裁剪女仆的转弯航线。Home 模式用相同配置范围，以 Home 为中心。

## 已知限制

动画效果、实际命中率、复杂地形与多人网络仍需要用户实测。短期预测不等同于完整三维寻路；完全封闭水域会释放控制回到女仆原有游泳流程。YSM 2.6.5 使用模型已有的滑翔动画和鞘翅挂点，缺失动画或其他 YSM 版本保留身体倾斜回退。遥控器只访问同维度已加载实体，不强制加载区块。带爆炸效果的烟花保留原版效果。
