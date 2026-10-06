package dev.winefox.skyfarer.flight;

/** Connected half-block samples; near and wide fits preserve a local slope at a crest or valley. */
public final class SlopeSampler {
    private SlopeSampler() {}
    @FunctionalInterface public interface Support { double height(double x,double z,double previousY); }
    public record Estimate(double nearX,double nearZ,double wideX,double wideZ,int samples) {}
    public static Estimate sample(double x,double y,double z,double radius,double step,Support support) {
        double[] near=new double[5],wide=new double[5]; int count=0;
        radius=Math.clamp(radius,3,32); step=Math.clamp(step,.25,.4);
        for(int ray=0;ray<8;ray++) {
            double a=ray*Math.PI/4,dx=Math.cos(a),dz=Math.sin(a),height=y;
            for(double distance=step;distance<=radius+1e-6;distance+=step) {
                double sx=dx*distance,sz=dz*distance;
                height=support.height(x+sx,z+sz,height);
                // A hole/unloaded column ends this connected profile, not all other directions.
                if(!Double.isFinite(height)) break;
                double weight=1/(1+distance*distance/(radius*radius));
                accumulate(wide,sx,sz,height-y,weight);
                if(distance<=3+1e-6) accumulate(near,sx,sz,height-y,weight);
                count++;
            }
        }
        double[] local=solve(near),broad=solve(wide);
        return new Estimate(local[0],local[1],broad[0],broad[1],count);
    }
    private static void accumulate(double[] fit,double x,double z,double y,double w) {
        fit[0]+=w*x*x; fit[1]+=w*x*z; fit[2]+=w*z*z; fit[3]+=w*x*y; fit[4]+=w*z*y;
    }
    private static double[] solve(double[] m) {
        double det=m[0]*m[2]-m[1]*m[1];
        if(det<1e-8) return new double[]{m[0]>1e-8?Math.clamp(m[3]/m[0],-AttackLinePlanner.MAX_GRADE,AttackLinePlanner.MAX_GRADE):0,
            m[2]>1e-8?Math.clamp(m[4]/m[2],-AttackLinePlanner.MAX_GRADE,AttackLinePlanner.MAX_GRADE):0};
        return new double[]{Math.clamp((m[3]*m[2]-m[4]*m[1])/det,-AttackLinePlanner.MAX_GRADE,AttackLinePlanner.MAX_GRADE),
            Math.clamp((m[4]*m[0]-m[3]*m[1])/det,-AttackLinePlanner.MAX_GRADE,AttackLinePlanner.MAX_GRADE)};
    }
}
