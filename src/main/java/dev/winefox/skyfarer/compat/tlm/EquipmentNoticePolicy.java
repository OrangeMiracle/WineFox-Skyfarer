package dev.winefox.skyfarer.compat.tlm;

/** Persist these values on the maid, not on a short-lived flight controller. One pending message. */
public final class EquipmentNoticePolicy {
    public static final int GROUND=1, RETURN=2, LANDED=4;
    public static final int COOLDOWN=200, NORMAL_DEBOUNCE=40, OWNER_INTERVAL=40;
    public int seen,pending;
    public long normalSince=-1,lastSpoken=-COOLDOWN,due;
    public void observe(long now,boolean ready,boolean ground,int stagger) {
        if(now<lastSpoken){lastSpoken=now;due=now+COOLDOWN;}
        if(ready) {
            pending=0;
            if(normalSince<0||now<normalSince)normalSince=now;
            if(now-normalSince>=NORMAL_DEBOUNCE)seen=0;
            return;
        }
        normalSince=-1;
        int event=ground?((seen&RETURN)!=0?LANDED:GROUND):RETURN;
        if((seen&event)==0) {
            seen|=event;pending=event;
            due=Math.max(now+Math.clamp(stagger,1,40),lastSpoken+COOLDOWN);
        } else if(pending!=event)pending=0; // Never deliver an old "landed" line after taking off again.
    }
    public int readyEvent(long now){return pending!=0&&now>=due?pending:0;}
    public void spoken(long now){pending=0;lastSpoken=now;}
    public static String name(int event){return switch(event){case GROUND->"ground";case RETURN->"return";case LANDED->"landed";default->throw new IllegalArgumentException("event");};}
}
