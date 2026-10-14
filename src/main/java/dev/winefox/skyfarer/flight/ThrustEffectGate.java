package dev.winefox.skyfarer.flight;

/** Numeric server gate. Visual events follow actual impulses, never candidate search. */
public final class ThrustEffectGate {
    public record Frame(int power,boolean ring) {}
    private long seen=Long.MIN_VALUE,lastSend=-1000,lastRing=-1000,lastPower=-1000;
    private float output,ringOutput;
    public Frame actual(long tick,float force){
        if(seen==tick)return null;seen=tick;
        force=Float.isFinite(force)?Math.clamp(force,0,.2f):0;
        boolean active=force>.001f;
        // Track the post-burst trough, not the previous peak: a later real increase
        // must still produce a ring after the first high-output launch.
        if(active)ringOutput=Math.min(ringOutput,force);
        boolean ring=active&&tick-lastRing>=20&&force>=.02f&&(tick-lastPower>=6||force-ringOutput>=.05f);
        if(active)lastPower=tick;
        if(ring){lastRing=tick;ringOutput=force;}
        boolean changed=(force>.001f)!=(output>.001f);output=force;
        if(!changed&&!ring&&(!active||tick-lastSend<4))return null;
        lastSend=tick;return new Frame(active?Math.max(1,Math.round(force/.2f*255)):0,ring);
    }
    public Frame finish(long tick){return seen==tick?null:actual(tick,0);}
}
