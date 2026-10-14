package dev.winefox.skyfarer.flight;

/** Pure visual layout and budgets, shared by the client emitter and offline checks. */
public final class ThrustVisuals {
    public static final int TRAIL_BUDGET=96,RING_BUDGET=64,RING_POINTS=32;
    public static final int MAX_TRACKED=128,PACKET_TTL=10,RING_TTL=6;
    /** Bridge only nearby successive samples: never draw a line across a teleport. */
    public static int samples(double distanceSquared,int limit){
        return !Double.isFinite(distanceSquared)||distanceSquared>20.25?1:Math.clamp((int)Math.ceil(Math.sqrt(distanceSquared)/.3),1,limit);
    }
    public static float fade(float progress){return Math.clamp((1-progress)/.7f,0,1);}
    public static double ringRadius(float progress){return .5+1.65*progress*(2-progress);}
    public record Vector(double x,double y,double z){
        public Vector scale(double n){return new Vector(x*n,y*n,z*n);}
        public Vector add(Vector b){return new Vector(x+b.x,y+b.y,z+b.z);}
        public double dot(Vector b){return x*b.x+y*b.y+z*b.z;}
        public Vector cross(Vector b){return new Vector(y*b.z-z*b.y,z*b.x-x*b.z,x*b.y-y*b.x);}
        public Vector unit(){return scale(1/Math.sqrt(dot(this)));}
    }
    public record Frame(Vector forward,Vector right,Vector up){
        public Vector radial(double angle){return right.scale(Math.cos(angle)).add(up.scale(Math.sin(angle)));}
    }
    public static Frame frame(double vx,double vy,double vz,double yaw,double pitch){
        var direction=new Vector(vx,vy,vz);
        if(direction.dot(direction)<.04){double y=Math.toRadians(yaw),p=Math.toRadians(pitch);direction=new Vector(-Math.sin(y)*Math.cos(p),-Math.sin(p),Math.cos(y)*Math.cos(p));}
        var forward=direction.unit();var right=forward.cross(new Vector(0,1,0));
        right=right.dot(right)<.001?forward.cross(new Vector(0,0,1)).unit():right.unit();return new Frame(forward,right,right.cross(forward).unit());
    }
    public static final class Budget {
        private long tick=Long.MIN_VALUE;
        private int trail,ring;
        public boolean take(long now,int amount,boolean burst){
            if(tick!=now){tick=now;trail=TRAIL_BUDGET;ring=RING_BUDGET;}
            if(amount<1||amount>(burst?ring:trail))return false;
            if(burst)ring-=amount;else trail-=amount;return true;
        }
    }
}
