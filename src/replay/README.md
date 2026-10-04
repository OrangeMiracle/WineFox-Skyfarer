# 生产控制器离线回放

运行 `tools/build.ps1 -Stage controller-replay -Tasks @('verifyControllerReplay')`，或执行完整 `build`。

`compileControllerReplay` 直接编译 `src/main/java` 中的 AirCombatController、全部 AI 决策、飞行规划器、HeadingController、FireworkManager、配置与 DebugSnapshot。测试不拷贝控制器实现、不替换状态转换、不直接赋值控制器私有状态，也不启动 Minecraft。

`src/replay/java` 中的同名外部 API 是仅供独立编译任务使用的适配夹具：实体/库存、世界时钟、加载边界、方块盒、烟花实体、滑翔标志和长矛接口。身体碰撞仍使用生产 FlightCorridor，逐 tick 位移由生产 FlightPrediction 的原版鞘翅方程推进，烟花时长来自实际 FireworkManager 的选择。起跳使用初速度与重力。测试命中需要前摇、使用长矛、实际矛线接触和无遮挡，随后由夹具调用生产命中回调。原生 Spears 伤害、网络同步、真实世界加载及客户端动画不在此夹具的验证范围内。

每个场景从地面实体开始，由控制器自己索敌、起飞、选入口、攻击、离场、续战和降落。目标消失及工作模式取消只改变外部输入。日志同时记录同一 tick 内的中间状态，避免漏掉瞬时交接。非降落触地、身体碰撞、长期无命中进展、过高/过远、借助紧急传送、降落后重新补能/攻击都会失败。

逐 tick TSV 和状态日志位于 `build/reports/controller-replay/`。验证夹具、这些报告和 Minecraft/TLM/Spears 的模拟 API 均不属于 main source set，不进入交付 Jar。真实 NeoForge/TLM/Spears API 兼容性仍由 `compileJava` 验证；游戏行为由用户实测确认。

`verifyDispatchReplay` 额外直接运行生产 `TransitFlight`，覆盖 180/256/900 格赶路、反向起飞、雪层，以及 48 格处向生产 `AirCombatController` 交接、命中、离场和降落。验证赶路不消耗库存、免费推进在交接时结束、没有非计划触地。夹具保留原版烟花物品默认 Flight 1，生产赶路代码必须显式设置 Flight 0 才与短脉冲预测一致。此回放的世界预先提供加载地形；NeoForge 异步区块加载、玩家网络输入和显示仍需实机确认。
