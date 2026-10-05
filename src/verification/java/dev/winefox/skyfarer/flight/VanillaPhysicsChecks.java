package dev.winefox.skyfarer.flight;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import java.util.Random;

/** Differential oracle transcribed from the mapped 1.21.1 travel block; uses REAL Mth/Vec3. */
public final class VanillaPhysicsChecks {
    private static void require(boolean b,String m){if(!b)throw new AssertionError(m);}
    private static void near(double a,double b,String m){require(Math.abs(a-b)<1e-11,m+": "+a+" != "+b);}
    public static void main(String[] args) throws Exception {
        var random=new Random(211250);
        int steps=0;
        for(double gravity:new double[]{-.08,0,.01,.08,.2})for(int slow:new int[]{0,9,Integer.MAX_VALUE})
            for(int life:new int[]{0,1,7,22,42})for(double initialSpeed:new double[]{.001, .3,1.7,4}) {
                var actual=new FlightPrediction.State(0,160,0,.0029,random.nextDouble()-.5,initialSpeed,0,0,new PhysicsContext(gravity,slow));
                var expected=actual;
                for(int tick=0;tick<60;tick++) {
                    double yaw=(float)(random.nextDouble()*720-360),pitch=(float)(random.nextDouble()*180-90);
                    actual=FlightPrediction.step(actual,yaw,pitch,360,180,180,gravity,tick<life);
                    // travel receives the actual float angles after bounded steering;
                    // wrapped angles are not bit-identical LUT inputs to yaw +/- 360.
                    expected=reference(expected,actual.yaw(),actual.pitch(),tick<life);
                    near(actual.x(),expected.x(),"x");near(actual.y(),expected.y(),"y");near(actual.z(),expected.z(),"z");
                    near(actual.vx(),expected.vx(),"vx");near(actual.vy(),expected.vy(),"vy");near(actual.vz(),expected.vz(),"vz");steps++;
                }
            }
        for(double pitch:new double[]{-90,-89.999,0,89.999,90}){
            var s=new FlightPrediction.State(0,50,0,.0029,-.0029,.003,0,pitch,PhysicsContext.VANILLA);
            var a=FlightPrediction.step(s,0,pitch,0,0,0,.08,false);var b=reference(s,0,pitch,false);
            near(a.vy(),b.vy(),"vertical epsilon boundary");
        }
        var s=new FlightPrediction.State(0,50,0,0,0,1,0,0);
        var coast=FlightPrediction.step(s,0,0,0,0,0,.08,false);
        var rocket=FlightPrediction.step(s,0,0,0,0,0,.08,true);
        near(coast.z(),rocket.z(),"rocket must not alter this tick displacement");require(rocket.vz()>coast.vz(),"rocket next velocity");
        var newborn=s.withRocketDelay(1);
        var first=FlightPrediction.step(newborn,0,0,0,0,0,.08,true);
        near(first.vz(),coast.vz(),"new entity is absent from current EntityTickList iteration");
        for(int life=RocketProfile.minimumTicks(1);life<=RocketProfile.maximumTicks(1);life++) {
            int impulses=0;
            for(int tick=0;tick<life+2;tick++)if(RocketProfile.active(tick,life,newborn))impulses++;
            require(impulses==life,"startup delay changed random lifetime");
            require(RocketProfile.remaining(1,life,newborn)==life,"delay consumed a live tick");
        }
        context();
        System.out.println(steps+" mapped 1.21.1 differential physics steps passed; dive/pull-up, drag, float lookup, epsilon, negative/zero gravity, slow-fall expiry and rocket order.");
    }
    private static FlightPrediction.State reference(FlightPrediction.State s,double yaw,double pitch,boolean rocket) {
        PhysicsContext context=s.physics()==null?PhysicsContext.VANILLA:s.physics();
        Vec3 v=new Vec3(Math.abs(s.vx())<.003?0:s.vx(),Math.abs(s.vy())<.003?0:s.vy(),Math.abs(s.vz())<.003?0:s.vz());
        float f=(float)pitch*(float)(Math.PI/180.0), a=-(float)yaw*(float)(Math.PI/180.0);
        Vec3 look=new Vec3((double)(Mth.sin(a)*Mth.cos(f)),(double)(-Mth.sin(f)),(double)(Mth.cos(a)*Mth.cos(f)));
        double d0=context.gravity();if(v.y<=0&&context.slowFallingTicks()>0)d0=Math.min(d0,.01);
        double d1=Math.sqrt(look.x*look.x+look.z*look.z),d3=v.horizontalDistance(),d4=look.length(),d5=Math.cos((double)f);
        d5=d5*d5*Math.min(1.0,d4/.4);
        v=v.add(0,d0*(-1+d5*.75),0);
        if(v.y<0&&d1>0){double d6=v.y*-.1*d5;v=v.add(look.x*d6/d1,d6,look.z*d6/d1);}
        if(f<0&&d1>0){double d10=d3*(double)(-Mth.sin(f))*.04;v=v.add(-look.x*d10/d1,d10*3.2,-look.z*d10/d1);}
        if(d1>0)v=v.add((look.x/d1*d3-v.x)*.1,0,(look.z/d1*d3-v.z)*.1);
        v=v.multiply(.99F,.98F,.99F);double x=s.x()+v.x,y=s.y()+v.y,z=s.z()+v.z;
        if(rocket)v=v.add(look.x*.1+(look.x*1.5-v.x)*.5,look.y*.1+(look.y*1.5-v.y)*.5,look.z*.1+(look.z*1.5-v.z)*.5);
        return new FlightPrediction.State(x,y,z,v.x,v.y,v.z,yaw,pitch,context.nextTick());
    }
    private static void context() throws Exception {
        require(new PhysicsContext(Double.NaN,0).equals(PhysicsContext.VANILLA),"NaN fallback");
        require(new PhysicsContext(Double.POSITIVE_INFINITY,0).equals(PhysicsContext.VANILLA),"infinity fallback");
        var provider=new PhysicsProvider();Object entity=new Object(),level=new Object();int[] reads={0};
        var read=(java.util.function.Supplier<PhysicsContext>)()->{reads[0]++;return new PhysicsContext(.04,0);};
        require(provider.capture(entity,level,"a",1,read).gravity()==.04,"initial capture");
        provider.capture(entity,level,"a",1,read);require(reads[0]==1,"duplicate planning entry re-read");
        provider.capture(entity,new Object(),"a",1,read);require(reads[0]==2,"same dimension Level replacement");
        provider.capture(new Object(),level,"b",1,read);require(reads[0]==3,"respawn/dimension recapture");
        require(provider.capture(entity,level,"a",2,()->{throw new IllegalStateException();}).equals(PhysicsContext.VANILLA),"throw fallback");
        require(provider.capture(entity,level,"a",3,()->null).equals(PhysicsContext.VANILLA),"null fallback");
        var failed=new java.util.concurrent.atomic.AtomicBoolean();
        Thread worker=new Thread(()->{try{provider.capture(entity,level,"a",4,read);}catch(IllegalStateException expected){failed.set(true);}});
        worker.start();worker.join();require(failed.get(),"worker allowed physics capture");
        provider.clear();provider.capture(entity,level,"a",3,read);require(reads[0]==4,"release/rejoin recapture");
    }
}
