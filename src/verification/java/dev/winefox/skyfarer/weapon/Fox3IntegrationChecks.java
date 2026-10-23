package dev.winefox.skyfarer.weapon;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.winefox.skyfarer.config.SkyfarerConfig;
import dev.winefox.skyfarer.config.SkyfarerSettings;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;

/** Actual NeoForge configuration and compiled integration contracts, without starting Minecraft. */
public final class Fox3IntegrationChecks {
    private static final String ROOT = "dev/winefox/skyfarer/";
    private static final String PANEL = ROOT + "runtime/DebugPanelManager";
    private static final String WATCH = ROOT + "runtime/DebugWatchManager";
    private static final String SYSTEM = ROOT + "weapon/Fox3System";
    private static final String NBT = "net/minecraft/nbt/CompoundTag";
    private static int assertions;

    public static void main(String[] args) throws Exception {
        config();
        debugAuthorization();
        triggerBoundaries();
        hooksAndIsolation();
        unloadLifecycle();
        locales();
        System.out.println("Fox3 integration: " + assertions + " real config, authorization control-flow, per-maid UI, queued network, lifecycle/renderer and localization checks passed.");
    }

    private static void config() throws Exception {
        // This deliberately uses the real ModConfigSpec, not the replay config fixture.
        require(!SkyfarerConfig.SPEC.isLoaded(), "isolated config must start unloaded");
        require(SkyfarerSettings.fox3Enabled() && !SkyfarerSettings.fox3ConsumeFireworks(), "unloaded defaults must be enabled and free");
        var expected = new LinkedHashMap<String,double[]>();
        expected.put("min_monsters", new double[]{8, 1, 128, 1});
        expected.put("cooldown_ticks", new double[]{1200, 20, 72000, 1});
        expected.put("chance", new double[]{.5, 0, 1, 0});
        expected.put("waves", new double[]{4, 1, 16, 1});
        expected.put("wave_interval_ticks", new double[]{12, 1, 100, 1});
        expected.put("missiles_per_wave", new double[]{2, 1, 4, 1});
        expected.put("damage", new double[]{5, 0, 100, 0});
        expected.put("blast_radius", new double[]{2, .25, 8, 0});
        for (var entry : expected.entrySet()) {
            String key = "fox3_" + entry.getKey(); double[] e = entry.getValue();
            var parameter = SkyfarerConfig.PARAMETERS.get(key);
            require(parameter != null && parameter.category().equals("fox3") && parameter.initial() == e[0]
                && parameter.min() == e[1] && parameter.max() == e[2] && parameter.integral() == (e[3] == 1), key + " public contract");
            var value = SkyfarerConfig.VALUES.get(key);
            require(value.getPath().equals(List.of("fox3", entry.getKey())), key + " actual TOML path");
            require(value.getDefault() == e[0] && SkyfarerSettings.get(key) == e[0], key + " backing/default getter");
            var spec = value.getSpec();
            require(spec.test(e[1]) && spec.test(e[2]) && !spec.test(e[1] - 1) && !spec.test(e[2] + 1), key + " real range validator");
        }
        require(SkyfarerConfig.FOX3_ENABLED.getPath().equals(List.of("fox3", "enabled")) && SkyfarerConfig.FOX3_ENABLED.getDefault(), "enabled boolean TOML/default");
        require(SkyfarerConfig.FOX3_CONSUME_FIREWORKS.getPath().equals(List.of("fox3", "consume_fireworks")) && !SkyfarerConfig.FOX3_CONSUME_FIREWORKS.getDefault(), "exact requested fox3.consume_fireworks=false");
        require(SkyfarerConfig.FOX3_CONSUME_FIREWORKS.getSpec().test(Boolean.TRUE)
            && !SkyfarerConfig.FOX3_CONSUME_FIREWORKS.getSpec().test(1), "ammunition flag is a real boolean");
        var memory = CommentedConfig.inMemory(); SkyfarerConfig.SPEC.correct(memory);
        int[] saves = {0};
        SkyfarerConfig.SPEC.acceptConfig(offlineLoadedConfig(memory, saves));
        try {
            for (var entry : expected.entrySet()) {
                String key = "fox3_" + entry.getKey(); double[] e = entry.getValue();
                long revision = SkyfarerSettings.revision();
                SkyfarerSettings.set(key, e[2]);
                require(SkyfarerSettings.get(key) == e[2] && SkyfarerSettings.revision() > revision, key + " live command update");
                require(((Number)memory.get("fox3." + entry.getKey())).doubleValue() == e[2], key + " updates actual TOML entry");
                rejects(() -> SkyfarerSettings.set(key, e[2] + 1), key + " rejects above max");
                rejects(() -> SkyfarerSettings.set(key, Double.NaN), key + " rejects nonfinite");
                if (e[3] == 1) rejects(() -> SkyfarerSettings.set(key, e[1] + .5), key + " rejects fractional count/ticks");
                SkyfarerSettings.reset(key); require(SkyfarerSettings.get(key) == e[0], key + " per-key reset");
            }
            memory.set("fox3.enabled", false); memory.set("fox3.consume_fireworks", true);
            SkyfarerConfig.SPEC.afterReload(); SkyfarerSettings.reloaded();
            require(!SkyfarerSettings.fox3Enabled() && SkyfarerSettings.fox3ConsumeFireworks(), "reload picks up both booleans");
            SkyfarerSettings.reset("all");
            require(SkyfarerSettings.fox3Enabled() && !SkyfarerSettings.fox3ConsumeFireworks() && saves[0] > 0, "reset all restores free defaults and saves");
            require(SkyfarerSettings.fox3Waves() == 4 && SkyfarerSettings.fox3MissilesPerWave() == 2
                && SkyfarerSettings.fox3WaveIntervalTicks() == 12 && SkyfarerSettings.fox3MinMonsters() == 8
                && SkyfarerSettings.fox3CooldownTicks() == 1200 && SkyfarerSettings.fox3Chance() == .5
                && SkyfarerSettings.fox3Damage() == 5 && SkyfarerSettings.fox3BlastRadius() == 2, "typed Fox3 consumers read configured defaults");
        } finally { SkyfarerConfig.SPEC.acceptConfig(null); }
    }

    /** ILoadedConfig is sealed; use NeoForge's actual package-private record and real save path.
     * A null disk path keeps this in memory; the container's absent event bus ends reload delivery. */
    private static IConfigSpec.ILoadedConfig offlineLoadedConfig(CommentedConfig memory, int[] saves) throws Exception {
        var infoType = net.neoforged.neoforgespi.language.IModInfo.class;
        var info = (net.neoforged.neoforgespi.language.IModInfo)java.lang.reflect.Proxy.newProxyInstance(
            infoType.getClassLoader(), new Class<?>[]{infoType}, (proxy, method, args) -> method.getName().equals("getModId") ? "fox3_offline_checks" : null);
        var container = new net.neoforged.fml.ModContainer(info) {
            @Override public net.neoforged.bus.api.IEventBus getEventBus() { saves[0]++; return null; }
        };
        var configConstructor = net.neoforged.fml.config.ModConfig.class.getDeclaredConstructor(
            net.neoforged.fml.config.ModConfig.Type.class, IConfigSpec.class, net.neoforged.fml.ModContainer.class,
            String.class, java.util.concurrent.locks.ReentrantLock.class);
        configConstructor.setAccessible(true);
        var config = configConstructor.newInstance(net.neoforged.fml.config.ModConfig.Type.SERVER, SkyfarerConfig.SPEC,
            container, "fox3-offline.toml", new java.util.concurrent.locks.ReentrantLock());
        var loadedConstructor = Class.forName("net.neoforged.fml.config.LoadedConfig").getDeclaredConstructor(
            CommentedConfig.class, Path.class, net.neoforged.fml.config.ModConfig.class);
        loadedConstructor.setAccessible(true);
        return (IConfigSpec.ILoadedConfig)loadedConstructor.newInstance(memory, null, config);
    }

    private static void debugAuthorization() throws Exception {
        var panel = read(PANEL); var action = method(panel, "action");
        int route = callIndex(action, c -> c.owner.equals(PANEL) && c.name.equals("launchFox3"));
        require(route >= 0, "remote action reaches manual Fox3 route");
        guard(action, route, c -> c.owner.equals(PANEL) && c.name.equals("holding"), true, "held debug stick");
        guard(action, route, c -> c.owner.equals(NBT) && c.name.equals("hasUUID") && constantBefore(c, "session"), true, "session nonce exists");
        guard(action, route, c -> c.owner.equals("java/util/UUID") && c.name.equals("equals"), true, "nonce and owner equality gates");
        guard(action, route, c -> c.owner.equals(NBT) && c.name.equals("hasUUID") && constantBefore(c, "maid"), true, "maid UUID required");
        guard(action, route, c -> c.owner.equals(WATCH) && c.name.equals("allowed"), true, "registry owner permission");
        guard(action, route, c -> c.owner.equals("java/lang/String") && c.name.equals("equals") && constantBefore(c, "fox3"), true, "explicit Fox3 action");
        fieldGate(action, route, "expires", Opcodes.GETFIELD, "session expiry fails closed");
        fieldGate(action, route, "requests", Opcodes.PUTFIELD, "request budget fails closed");
        requireDominates(action, route, i -> i instanceof FieldInsnNode f && f.name.equals("requests") && f.getOpcode() == Opcodes.PUTFIELD, "request budget updated before launch");
        requireDominates(action, route, i -> i instanceof MethodInsnNode c && c.name.equals("find") && c.owner.endsWith("/SkyfarerMaidRegistry"), "requested UUID resolved through registry");

        var launch = method(panel, "launchFox3"); int fire = callIndex(launch, c -> c.owner.equals(SYSTEM) && c.name.equals("manualLaunch"));
        require(fire >= 0, "manual system call exists");
        requireDominates(launch, fire, i -> i instanceof MethodInsnNode c && c.owner.equals(WATCH) && c.name.equals("loaded"), "loaded-only lookup before firing");
        guard(launch, fire, c -> c.name.equals("isRemoved"), false, "removed entity rejected");
        guard(launch, fire, c -> c.name.equals("isAlive"), true, "dead entity rejected");
        guard(launch, fire, c -> c.owner.equals("java/util/UUID") && c.name.equals("equals"), true, "live ownership equality");
        guard(launch, fire, c -> c.owner.equals(WATCH) && c.name.equals("allowed"), true, "live ownership permission");
        require(!calls(launch, c -> c.name.equals("forceChunk") || c.name.equals("startTeleport") || c.name.equals("setTarget")), "manual launch cannot wake/teleport or mutate combat target");
        require(!calls(method(read(WATCH), "loaded"), c -> c.name.equals("getChunk") || c.name.equals("forceChunk")), "lookup does not load chunks");

        var ui = read(ROOT + "client/gui/DebugMaidScreen");
        var button = ui.methods.stream().filter(m -> hasConstant(m, "fox3") && calls(m, c -> c.name.equals("request"))).findFirst().orElseThrow();
        int request = callIndex(button, c -> c.name.equals("request"));
        requireDominates(button, request, i -> i instanceof MethodInsnNode c && c.owner.equals(NBT) && c.name.equals("getUUID") && constantBefore(c, "id"), "button takes the captured maid row's UUID");
        var send = method(ui, "request"); int transmit = callIndex(send, c -> c.name.equals("sendToServer"));
        requireDominates(send, transmit, i -> i instanceof MethodInsnNode c && c.name.equals("session"), "UI forwards current session nonce");
        require(hasConstant(send, "maid") && calls(send, c -> c.name.equals("putUUID")), "UI includes requested maid UUID");
        require(hasConstant(method(ui, "init"), "loaded"), "unloaded rows have client availability gating");
        var network = read(ROOT + "network/SkyfarerNetwork");
        var handler = network.methods.stream().filter(m -> calls(m, c -> c.owner.equals(PANEL) && c.name.equals("action"))).findFirst().orElseThrow();
        require(network.methods.stream().anyMatch(m -> calls(m, c -> c.name.equals("enqueueWork")) && handles(m).stream().anyMatch(h -> h.getOwner().equals(network.name) && h.getName().equals(handler.name))), "remote Fox3 dispatch executes through queued server work");
    }

    private static void hooksAndIsolation() throws Exception {
        var main = read(ROOT + "WineFoxSkyfarer");
        require(main.methods.stream().anyMatch(m -> field(m, ROOT + "weapon/Fox3Entities", "TYPES")), "missile entity type registry connected to mod entry");
        var client = read(ROOT + "client/SkyfarerClient");
        require(client.methods.stream().anyMatch(m -> field(m, ROOT + "weapon/Fox3Entities", "MISSILE") && calls(m, c -> c.name.equals("registerEntityRenderer"))), "client registers missile renderer");
        require(client.methods.stream().flatMap(m -> handles(m).stream()).anyMatch(h -> h.getOwner().equals(ROOT + "client/Fox3MissileRenderer")), "tail missile renderer is registered");
        var listeners = main.methods.stream().flatMap(m -> handles(m).stream()).toList();
        require(listeners.stream().anyMatch(h -> h.getOwner().equals(SYSTEM) && h.getName().equals("tick") && h.getDesc().contains("EntityTickEvent$Post")), "POST entity tick drives independent weapon after flight control");
        require(listeners.stream().anyMatch(h -> h.getOwner().equals(SYSTEM) && h.getName().equals("leave") && h.getDesc().contains("EntityLeaveLevelEvent")), "entity leave listener cleans weapon state");
        require(main.methods.stream().anyMatch(m -> m.desc.contains("ServerStoppingEvent") && calls(m, c -> c.owner.equals(SYSTEM) && c.name.equals("clear"))), "server stopping clears weapon state");
        var registered = read(ROOT + "weapon/Fox3Entities");
        require(registered.methods.stream().anyMatch(m -> calls(m, c -> c.name.equals("noSave")) && calls(m, c -> c.name.equals("noSummon"))), "missiles are ephemeral and not externally summonable");
        try (var paths = Files.list(Path.of("build/classes/java/main/" + ROOT + "weapon"))) {
            for (var path : paths.filter(p -> p.toString().endsWith(".class")).toList()) {
                var node = new ClassNode(); new ClassReader(Files.readAllBytes(path)).accept(node, 0);
                for (var m : node.methods) {
                    require(!calls(m, c -> c.owner.startsWith("net/minecraft/client/") || c.owner.startsWith(ROOT + "client/")), node.name + " server-side isolation");
                    require(!calls(m, c -> c.owner.endsWith("/TargetEvaluator") || c.owner.endsWith("/FireworkManager")
                        || c.owner.endsWith("/FlightPropulsion") || c.owner.endsWith("/PropulsionResolver")
                        || c.owner.equals("java/lang/Thread") || c.owner.startsWith("java/util/concurrent/Executors")), node.name + " independent targeting, resources and bounded threading");
                    require(!calls(m, c -> c.owner.equals("net/minecraft/world/entity/projectile/FireworkRocketEntity") && c.name.equals("tick")), "native unfiltered firework damage must never run");
                }
            }
        }
    }

    private static void triggerBoundaries() throws Exception {
        var system = read(SYSTEM); var tick = method(system, "tick");
        int roll = callIndex(tick, c -> c.name.equals("nextDouble"));
        require(roll >= 0, "automatic probability roll exists");
        requireDominates(tick, roll, i -> i instanceof MethodInsnNode c && c.owner.equals(NBT) && c.name.equals("putLong"), "cooldown stored before probability draw on both outcomes");
        require(hasConstant(tick, "wfs_fox3_last_check"), "automatic cooldown persists on this maid");
        var manual = method(system, "manualLaunch");
        require(!calls(manual, c -> c.name.equals("getPersistentData") || c.name.equals("putLong") || c.name.equals("getLong")
            || c.name.equals("nextDouble") || c.name.equals("automaticDue") || c.name.equals("fox3MinMonsters") || c.name.equals("fox3Chance")), "manual trigger bypasses automatic lottery/count/cooldown without mutating it");
        int start = callIndex(manual, c -> c.owner.equals(SYSTEM) && c.name.equals("start"));
        guard(manual, start, c -> c.owner.equals(SYSTEM) && c.name.equals("safeFlight"), true, "manual trigger still requires safe flight");
        int safe = callIndex(manual, c -> c.owner.equals(SYSTEM) && c.name.equals("safeFlight"));
        var previous = manual.instructions.get(safe).getPrevious(); while (previous != null && previous.getOpcode() < 0) previous = previous.getPrevious();
        require(previous != null && previous.getOpcode() == Opcodes.ICONST_1, "manual launch uses starting=true, enforcing committed-attack gate");
        require(calls(method(system, "safeFlight"), c -> c.name.equals("locksRunTarget")), "starting gate consults actual committed attack phase");
        require(dev.winefox.skyfarer.ai.AirCombatState.DIVE.locksRunTarget()
            && dev.winefox.skyfarer.ai.AirCombatState.SPEAR_CHARGE.locksRunTarget(), "dive/spear charge are committed attack phases");
        int consume = callIndex(manual, c -> c.name.equals("fox3ConsumeFireworks"));
        int ammo = callIndex(manual, c -> c.owner.endsWith("/Fox3Ammo") && c.name.equals("available"));
        int inventory = callIndex(manual, c -> c.name.equals("getAvailableInv"));
        require(consume >= 0 && ammo >= 0 && inventory >= 0 && !reachable(manual, 0, start, consume), "manual launch always reads ammunition configuration");
        int paid = booleanEdge(manual, consume, true), free = booleanEdge(manual, consume, false);
        require(reachable(manual, free, start, -1) && !reachable(manual, free, ammo, -1) && !reachable(manual, free, inventory, -1), "free manual launch bypasses both inventory access and ammo check");
        require(reachable(manual, paid, ammo, -1) && !reachable(manual, paid, start, ammo), "paid manual launch must pass ammo availability");
        require(reachable(manual, booleanEdge(manual, ammo, true), start, -1)
            && !reachable(manual, booleanEdge(manual, ammo, false), start, -1), "paid ammunition rejection cannot reach launch");
    }

    private static void locales() throws Exception {
        Path path = Path.of("src/main/resources/assets/winefox_skyfarer/lang");
        JsonObject zh = JsonParser.parseString(Files.readString(path.resolve("zh_cn.json"))).getAsJsonObject();
        JsonObject en = JsonParser.parseString(Files.readString(path.resolve("en_us.json"))).getAsJsonObject();
        var keys = new TreeSet<String>(); for (var key : zh.keySet()) if (key.contains(".fox3.")) keys.add(key);
        var english = new TreeSet<String>(); for (var key : en.keySet()) if (key.contains(".fox3.")) english.add(key);
        require(keys.equals(english), "Fox3 locale keys match");
        for (var key : keys) require(!zh.get(key).getAsString().isBlank() && !en.get(key).getAsString().isBlank(), key + " localized");
        require(zh.get("message.winefox_skyfarer.fox3.launch").getAsString().equals("Fox3，导弹离仓！")
            && zh.get("message.winefox_skyfarer.fox3.launch_alt").getAsString().equals("Fox3，狐狐导弹已发射！"), "requested announcements exact");
        require(zh.get("screen.winefox_skyfarer.fox3.launch").getAsString().equals("立即发射 Fox3"), "requested button exact");
        for (var m : read(SYSTEM).methods) for (var instruction : m.instructions)
            if (instruction instanceof LdcInsnNode ldc && ldc.cst instanceof String text && text.startsWith("message.winefox_skyfarer.fox3."))
                require(keys.contains(text), "system result key is localized: " + text);
    }

    private static void unloadLifecycle() throws Exception {
        String managerName = "net/minecraft/world/level/entity/PersistentEntitySectionManager";
        var manager = dependency(managerName);
        var unload = method(manager, "unloadEntity");
        require(calls(unload, c -> c.owner.equals("net/minecraft/world/level/entity/EntityAccess") && c.name.equals("setRemoved"))
            && !calls(unload, c -> c.name.equals("remove")), "actual chunk unload calls setRemoved directly, bypassing Entity.remove overrides");
        var removal = method(dependency("net/minecraft/world/entity/Entity"), "setRemoved");
        require((removal.access & Opcodes.ACC_FINAL) != 0 && calls(removal, c -> c.name.equals("onRemove")), "final native setRemoved delegates to level callback");
        require(calls(method(dependency(managerName + "$Callback"), "onRemove"), c -> c.owner.equals(managerName) && c.name.equals("stopTracking")), "native removal callback ends entity tracking");
        require(calls(method(manager, "stopTracking"), c -> c.owner.equals("net/minecraft/world/level/entity/LevelCallback") && c.name.equals("onTrackingEnd")), "native stopTracking dispatches tracking-end callback");
        var levelCallbacks = dependency("net/minecraft/server/level/ServerLevel$EntityCallbacks");
        var trackingEnd = levelCallbacks.methods.stream().filter(m -> m.name.equals("onTrackingEnd") && m.desc.equals("(Lnet/minecraft/world/entity/Entity;)V")).findFirst().orElseThrow();
        require(calls(trackingEnd, c -> c.owner.equals("net/minecraft/world/entity/Entity") && c.name.equals("onRemovedFromLevel")), "actual server tracking end invokes unload-safe entity hook");
        var missile = read(ROOT + "weapon/Fox3MissileEntity");
        require(calls(method(missile, "onRemovedFromLevel"), c -> c.owner.equals(missile.name) && c.name.equals("releaseOnce")), "missile unload hook releases activity budget");
        require(calls(method(missile, "remove"), c -> c.owner.equals(missile.name) && c.name.equals("releaseOnce")), "explicit missile discard shares exactly-once release");
    }

    // For a boolean guard, prove the accepted edge can reach the sensitive call and the rejected
    // edge cannot. Merely finding an authorization method somewhere in the class is insufficient.
    private static void guard(MethodNode method, int target, Predicate<MethodInsnNode> wanted, boolean accepted, String reason) {
        for (int index = 0; index < method.instructions.size(); index++) {
            var i = method.instructions.get(index);
            if (!(i instanceof MethodInsnNode call) || !wanted.test(call) || reachable(method, 0, target, index)) continue;
            int next = index + 1; while (next < method.instructions.size() && method.instructions.get(next).getOpcode() < 0) next++;
            if (!(method.instructions.get(next) instanceof JumpInsnNode jump) || (jump.getOpcode() != Opcodes.IFEQ && jump.getOpcode() != Opcodes.IFNE)) continue;
            boolean jumpWhenAccepted = jump.getOpcode() == Opcodes.IFNE ? accepted : !accepted;
            int yes = jumpWhenAccepted ? method.instructions.indexOf(jump.label) : next + 1;
            int no = jumpWhenAccepted ? next + 1 : method.instructions.indexOf(jump.label);
            if (reachable(method, yes, target, -1) && !reachable(method, no, target, -1)) { require(true, reason); return; }
        }
        throw new AssertionError(reason + ": a mandatory fail-closed control-flow gate is missing");
    }
    private static void requireDominates(MethodNode method, int target, Predicate<AbstractInsnNode> wanted, String reason) {
        require(target >= 0, reason + " sensitive call missing");
        for (int i = 0; i < method.instructions.size(); i++) if (wanted.test(method.instructions.get(i)) && !reachable(method, 0, target, i)) { require(true, reason); return; }
        throw new AssertionError(reason + ": check does not dominate sensitive call");
    }
    private static void fieldGate(MethodNode method, int target, String name, int opcode, String reason) {
        for (int i = 0; i < method.instructions.size(); i++) {
            if (!(method.instructions.get(i) instanceof FieldInsnNode field) || !field.name.equals(name) || field.getOpcode() != opcode || reachable(method, 0, target, i)) continue;
            for (int j = i + 1; j < Math.min(method.instructions.size(), i + 8); j++) {
                if (!(method.instructions.get(j) instanceof JumpInsnNode branch)) continue;
                boolean jump = reachable(method, method.instructions.indexOf(branch.label), target, -1), fall = reachable(method, j + 1, target, -1);
                if (jump != fall) { require(true, reason); return; }
                break;
            }
        }
        throw new AssertionError(reason + ": no fail-closed branch after field read/update");
    }
    private static int booleanEdge(MethodNode method, int call, boolean value) {
        int next = call + 1; while (next < method.instructions.size() && method.instructions.get(next).getOpcode() < 0) next++;
        if (!(method.instructions.get(next) instanceof JumpInsnNode jump) || (jump.getOpcode() != Opcodes.IFEQ && jump.getOpcode() != Opcodes.IFNE))
            throw new AssertionError("expected a direct boolean branch after " + method.instructions.get(call));
        boolean taken = jump.getOpcode() == Opcodes.IFNE ? value : !value;
        return taken ? method.instructions.indexOf(jump.label) : next + 1;
    }
    private static boolean reachable(MethodNode m, int start, int target, int removed) {
        var pending = new ArrayDeque<Integer>(); var seen = new BitSet(); pending.add(start);
        while (!pending.isEmpty()) {
            int i = pending.removeFirst(); if (i < 0 || i >= m.instructions.size() || i == removed || seen.get(i)) continue;
            if (i == target) return true; seen.set(i); var op = m.instructions.get(i);
            if (op instanceof JumpInsnNode jump) { pending.add(m.instructions.indexOf(jump.label)); if (op.getOpcode() == Opcodes.GOTO) continue; }
            if (op instanceof TableSwitchInsnNode sw) { pending.add(m.instructions.indexOf(sw.dflt)); for (var label : sw.labels) pending.add(m.instructions.indexOf(label)); continue; }
            if (op instanceof LookupSwitchInsnNode sw) { pending.add(m.instructions.indexOf(sw.dflt)); for (var label : sw.labels) pending.add(m.instructions.indexOf(label)); continue; }
            if (op.getOpcode() == Opcodes.ATHROW || op.getOpcode() >= Opcodes.IRETURN && op.getOpcode() <= Opcodes.RETURN) continue;
            pending.add(i + 1);
        }
        return false;
    }
    private static boolean constantBefore(AbstractInsnNode i, String value) { for (int n = 0; n < 4 && i != null; n++, i = i.getPrevious()) if (i instanceof LdcInsnNode c && value.equals(c.cst)) return true; return false; }
    private static boolean hasConstant(MethodNode m, String value) { for (var i : m.instructions) if (i instanceof LdcInsnNode c && value.equals(c.cst)) return true; return false; }
    private static boolean field(MethodNode m, String owner, String name) { for (var i : m.instructions) if (i instanceof FieldInsnNode f && f.owner.equals(owner) && f.name.equals(name)) return true; return false; }
    private static List<Handle> handles(MethodNode m) { var result = new ArrayList<Handle>(); for (var i : m.instructions) if (i instanceof InvokeDynamicInsnNode dynamic) for (var arg : dynamic.bsmArgs) if (arg instanceof Handle h) result.add(h); return result; }
    private static int callIndex(MethodNode m, Predicate<MethodInsnNode> wanted) { for (int i = 0; i < m.instructions.size(); i++) if (m.instructions.get(i) instanceof MethodInsnNode c && wanted.test(c)) return i; return -1; }
    private static boolean calls(MethodNode m, Predicate<MethodInsnNode> wanted) { return callIndex(m, wanted) >= 0; }
    private static MethodNode method(ClassNode type, String name) { return type.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow(); }
    private static ClassNode read(String name) throws Exception { var node = new ClassNode(); new ClassReader(Files.readAllBytes(Path.of("build/classes/java/main/" + name + ".class"))).accept(node, 0); return node; }
    private static ClassNode dependency(String name) throws Exception { try (var input = Fox3IntegrationChecks.class.getClassLoader().getResourceAsStream(name + ".class")) { require(input != null, "actual dependency class present: " + name); var node = new ClassNode(); new ClassReader(input).accept(node, 0); return node; } }
    private static void rejects(Runnable action, String message) { try { action.run(); } catch (IllegalArgumentException expected) { require(true, message); return; } throw new AssertionError(message); }
    private static void require(boolean ok, String message) { assertions++; if (!ok) throw new AssertionError(message); }
}
