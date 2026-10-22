package dev.winefox.skyfarer.weapon;

import java.util.*;

/** Executes the production wave clock and shared admission ledger, without a Minecraft world. */
public final class Fox3SalvoChecks {
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    public static void main(String[] args) {
        var salvo = new Fox3Salvo(4, 12, 100);
        var waves = new ArrayList<Long>();
        for (long tick = 100; tick < 300; tick++) {
            if (salvo.due(tick)) { waves.add(tick); salvo.fired(tick); }
        }
        require(waves.equals(List.of(100L, 112L, 124L, 136L)), "four waves at exact 12-tick intervals: " + waves);
        require(waves.size() * 2 == 8 && !salvo.active(300), "eight rockets then complete");
        var deferred = new Fox3Salvo(4, 12, 0);
        deferred.fired(0); deferred.fired(40);
        require(!deferred.due(40) && !deferred.due(51) && deferred.due(52), "budget deferral caused catch-up burst");
        require(!new Fox3Salvo(16, 100, 0).active(1801), "deferred salvo never expires");
        require(!Fox3Salvo.automaticDue(1199, 0, 1200) && Fox3Salvo.automaticDue(1200, 0, 1200), "cooldown boundary");
        for (long tick = 1201; tick < 2400; tick++) require(!Fox3Salvo.automaticDue(tick, 1200, 1200), "probability failure rerolled per tick");
        require(!Fox3Salvo.automaticDue(100, 500, 1200), "clock rollback should reinitialize, not fire");
        bounds(); concurrent();
        System.out.println("Fox3 salvo: exact 4x2 waves, no catch-up, finite deferral, cooldown boundaries; 64-maid/500-tick admission, search and release bounds passed");
    }
    private static void bounds() {
        for (int[] pair : List.of(new int[]{0,12}, new int[]{17,12}, new int[]{4,0}, new int[]{4,101})) {
            try { new Fox3Salvo(pair[0], pair[1], 0); throw new AssertionError("invalid salvo bounds accepted"); }
            catch (IllegalArgumentException expected) { }
        }
    }
    private static void concurrent() {
        var budget = new Fox3Budget();
        var maids = new ArrayList<UUID>();
        for (int i = 0; i < 64; i++) maids.add(new UUID(0, i));
        record Flight(UUID maid, long expires) {}
        var live = new ArrayList<Flight>();
        var counts = new HashMap<UUID, Integer>();
        int peak = 0;
        for (long tick = 0; tick < 500; tick++) {
            for (var it = live.iterator(); it.hasNext();) {
                Flight flight = it.next();
                if (flight.expires <= tick) { budget.release(flight.maid); counts.merge(flight.maid, -1, Integer::sum); it.remove(); }
            }
            int admitted = 0, scans = 0;
            for (UUID maid : maids) {
                if (budget.search(tick)) scans++;
                for (int shot = 0; shot < 4; shot++) if (budget.reserve(maid, tick)) {
                    admitted++; live.add(new Flight(maid, tick + 80)); counts.merge(maid, 1, Integer::sum);
                }
            }
            require(admitted <= 16 && scans == 8, "unbounded per-tick work");
            require(budget.activeCount() == live.size() && live.size() <= 128, "global active accounting");
            for (int count : counts.values()) require(count <= 16, "per-maid active cap");
            peak = Math.max(peak, live.size());
        }
        require(peak == 128, "pressure scenario did not exercise global cap");
        for (Flight flight : live) budget.release(flight.maid);
        for (UUID maid : maids) budget.release(maid); // duplicate cleanup is safe
        require(budget.activeCount() == 0, "unload leaked active slots");
        budget.clear(); require(budget.reserve(maids.getFirst(), 0), "restart retained prior epoch");
    }
}
