package dev.winefox.skyfarer.flight;

/**
 * MC 1.21.1 / NeoForge 21.1.250: LivingEntity.aiStep/travel,
 * Entity.calculateViewVector and FireworkRocketEntity.tick. See docs/VANILLA_PHYSICS.md.
 * Free flight only: the caller must reject swept collisions, fluids and unknown cells.
 */
public final class VanillaElytra {
    private VanillaElytra() {}
    private static final float[] SIN = new float[65536];
    static { for (int i=0; i<SIN.length; i++) SIN[i]=(float)Math.sin((double)i*Math.PI*2.0/65536.0); }
    static float sin(float a) { return SIN[(int)(a*10430.378F)&65535]; }
    static float cos(float a) { return SIN[(int)(a*10430.378F+16384.0F)&65535]; }
    public record Look(double x,double y,double z) {}
    public static Look look(double yaw,double pitch) {
        float p=(float)pitch*(float)(Math.PI/180.0), a=-(float)yaw*(float)(Math.PI/180.0);
        return new Look(sin(a)*cos(p),-sin(p),cos(a)*cos(p));
    }
    public static FlightPrediction.State step(FlightPrediction.State s,double yaw,double pitch,double fallbackGravity,boolean rocket) {
        yaw=(float)yaw; pitch=(float)pitch;
        PhysicsContext physics=s.physics()==null?new PhysicsContext(fallbackGravity,0):s.physics();
        // LivingEntity.aiStep snaps components before travel (not after drag).
        double vx=Math.abs(s.vx())<.003?0:s.vx(), vy=Math.abs(s.vy())<.003?0:s.vy(), vz=Math.abs(s.vz())<.003?0:s.vz();
        var look=look(yaw,pitch);double lx=look.x,ly=look.y,lz=look.z;
        float p=(float)pitch*(float)(Math.PI/180.0);
        double h=Math.sqrt(lx*lx+lz*lz), speed=Math.sqrt(vx*vx+vz*vz), length=Math.sqrt(lx*lx+ly*ly+lz*lz);
        double lift=Math.cos((double)p);lift=lift*lift*Math.min(1.0,length/.4);
        vy+=physics.travelGravity(vy)*(-1.0+lift*.75);
        if(vy<0 && h>0) { double d=vy*-.1*lift;vx+=lx*d/h;vy+=d;vz+=lz*d/h; }
        if(p<0 && h>0) { double d=speed*(double)(-sin(p))*.04;vx+=-lx*d/h;vy+=d*3.2;vz+=-lz*d/h; }
        if(h>0) { vx+=(lx/h*speed-vx)*.1;vz+=(lz/h*speed-vz)*.1; }
        vx*=(double).99F;vy*=(double).98F;vz*=(double).99F;
        double x=s.x()+vx,y=s.y()+vy,z=s.z()+vz;
        // Our managed rocket is appended after its maid in EntityTickList. It updates
        // velocity after maid movement; thrust affects NEXT displacement. Keep vanilla order.
        if(rocket && s.rocketDelay()==0) { vx+=lx*.1+(lx*1.5-vx)*.5;vy+=ly*.1+(ly*1.5-vy)*.5;vz+=lz*.1+(lz*1.5-vz)*.5; }
        return new FlightPrediction.State(x,y,z,vx,vy,vz,yaw,pitch,s.physics()==null?null:physics.nextTick(),Math.max(0,s.rocketDelay()-1));
    }
}
