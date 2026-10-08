# 0.6.2.9 空中补能与创造 Home 交接

核对日期 2026-10-07，基于当前本地 0.6.2.8；源码为最终事实。不引入 Funnel，不启动 Minecraft。

## 实机因果

指定实例的 latest.log / debug.log 为同一会话，不重复累计。[原始摘录](../tools/air-recovery-home-evidence.txt)。日志 848 行显示 REPOSITION、水平速度约 0.30、下降、净空约 6；1275 行也有 APPROACH 的实际激流前摇，因此不能断言旧版完全没有空中激流。真正遗漏的是 flyCompactTurn 的低能量分支只尝试烟花，manageEnergy 又跳过 REPOSITION；forecast pending 只预热烟花。普通 Riptide 接近候选仍以敌人作为前方余量边界，无法替代局部恢复任务。

966 行任务结束后直接 SPEAR_CHARGE→RETURN_TO_OWNER，之后在距 Home 很远的位置继续托管返航、甚至消耗激流。1018 行 COMMAND_HOME_RETURN success=false 恰在 200 tick recall 到期：旧回收只在地面尝试，空中超时不能解释为已经执行十次 TLM 传送而失败。订单与战斗票据确实撤销了，滞留的是返航飞控及它对 Home 日程的阻断。

## 空中恢复

APPROACH、完成 Exit 后的下一轮 APPROACH、REPOSITION 和相应异步等待路径，在存在合法下一目标、没有已承诺 Strike/Exit 或正在使用的物品时检查恢复。水平速度 <0.9 且 vy<0.05 才进入此检查；先预测局部 coast，足够就保留 coast，不直接点燃推进。

恢复目标取当前动量前方 40 格、eye+4 的短期飞行位置，最多保持 24 ticks 再刷新；这是认证用的局部延续方向，不是要求撞向或刹停于近处敌人。Riptide 候选继续逐 tick 搜索，预测完整前摇、推力和 12 tick coast，末端要求水平速度 ≥0.9、高度 ≥目标高度−1，全段碰撞/生物/战斗区域边界仍检查。最短通过者优先于火箭；环境/轨迹无解则交回已有烟花或正常安全飞控。没有删除烟花全随机寿命认证。

`Plan.minimumSpeed` 随证书保留，恢复请求使用独立 worker slot，普通接近请求不能混用缓存。恢复型激流在推力结束后**实际执行这 12 tick 无动力控制**再交接，截止清除本次 spin，末端再次检查实际速度/高度。不把此过程当火箭 cooldown，不修改普通玩家或长矛生命周期；普通空中接近型激流仍保留原先的预测窗口。

已有攻击与 Exit 的证书不被中途插入的激流推翻；只有 Exit 完成后再补能、重做下一轮攻击认证。良性返航（无目标/路径暂时失败/恢复了推进资格）也可在触地前重新扫描合法目标，推进资格包括激流；任务完成、装备故障、工作禁止或超飞行时间不会因此重新作战。短暂失雨留下的 pending fuel-return 在资格恢复且未超时后清除。

## TLM Home 的真实机制及交接

从实际依赖 `touhoulittlemaid-1.5.3-neoforge+mc1.21.1.jar` 反编译核对 SchedulePos 和 TeleportHelper，参考输出在 `build/tlm-home-reference`。SchedulePos 在实体 tickCount%40==0 时更新当前工作/休息/睡觉限制中心；超出限制且 canBrainMoving，距离超过 restrictRadius+4 且 WALK_TARGET 不是该中心时，最多尝试十次 TeleportHelper。否则设置走向中心的普通导航目标。它**不要求实体先卸载**，也不依赖玩家离开多少格。

TeleportHelper 在中心 X/Z ±3、Y+0..3 选点，要求非水实心地面并通过 randomTeleport 的站立/碰撞检查。canBrainMoving 仍受坐下、乘客、睡觉、拴绳限制；安全位置不足时不能保证立刻传送。

创造 Home 订单结束：先移除订单、关闭 TransitFlight，空中进入 COMMAND_HOME_LOCAL_LANDING，目标固定为结束任务时的附近地面；不朝 900 格外 Home 飞，也不调用原先的返乡超时传送。保留滑翔直至安全触地。地面则立即 dispose，managed=false、结束物品使用、清除 WineFox 导航记忆，允许 TLM 正常 tick。原 200 tick 临时当前位置/Home 票据宽限保留，成功回到限制区或期限到达时释放；它不再每 10 tick 主动调用 TeleportHelper。持续加载实体也走同一流程。

宽限期间禁止重新接管战斗，但 TLM 自己的 Home Schedule/Navigation 可执行；落地前仍由 WineFox 保持飞行安全。若 200 ticks 到期仍未回家，撤销票据并记录超时，之后由自然加载状态下的 TLM 行为决定；不永久加载区块，不承诺不可用地形一定能够返家。若空中宽限到期，本地安全落地仍会完成，不改成远程飞行返乡。

## 诊断与验证

- FLIGHT_DECISION：RETURN / LANDING / RECOVERY 的 reason、state、nextTarget、command、湿润、可用激流、rocket、energyDecision、速度。
- PROPULSION purpose=AIR_RECOVERY：候选等待/选择/拒绝，保留具体执行拒因。
- COMMAND_RELEASE reason=CREATIVE_COMMAND_COMPLETE：订单已移除、Transit 已关闭；COMMAND_HOME_CONTROL_RELEASE 确认 managed=false。
- TLM_HOME_SCHEDULE：每次 40 tick 日程机会记录 held、managed、ground、withinHome、canBrainMoving；held=false 说明原方法获准执行，不等同传送一定成功。
- COMMAND_HOME_RETURN 与 COMMAND_TICKET：回到限制区、宽限超时及实际撤票。

[最终定向构建](../tools/air-recovery-home-acceptance.log) 的 compileJava、verifyAirRecoveryHome、verifyRiptideDuration、verifyRiptideDeparture、verifyRiptideRelease、verifyRiptidePriority、verifyAttackPolicy、verifyHandoffStability、verifyRemoteWake、jar 通过。没有执行全量 verify。

新回放实际执行生产控制器：低速 APPROACH、REPOSITION、完成 Exit、良性 LANDING 有下一目标时空中激流恢复；干燥时拒绝激流并用烟花；900 格外 Home 持续加载场景在本地约 67 tick 落地并释放，已着地立即释放。新增恢复证书末速和墙体拒绝检查，原副手使用、起飞、短时长、交接与 wake 生命周期回归保持通过。

边界：实体/天气/世界由离线夹具提供；完整 CommandManager ServerTick、TLM SchedulePos 与 Mixin 的游戏内联调未运行。Home 生产控制器通过动态回放，订单连接通过编译与源码连接检查，TLM 行为依据实际依赖反编译源码；不能把这些写成已经实机完成传送。
