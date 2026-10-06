package dev.winefox.skyfarer.async;

import dev.winefox.skyfarer.flight.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

/** No Level, BlockState, VoxelShape, entity, or live callback crosses this boundary. */
public record CollisionSnapshot(Map<Long,Cell> cells,double width,double height,double eye,boolean waterEscape,
    double minX,double minZ,double maxX,double maxZ) {
    public CollisionSnapshot { cells=Map.copyOf(cells); }
    public record Cell(List<FlightCorridor.Box> solid,List<FlightCorridor.Box> fluid,long water,boolean loaded) {
        public Cell {solid=List.copyOf(solid);fluid=List.copyOf(fluid);}
    }
    // 26/12/26 signed cell coordinates: includes the entire vanilla world border.
    public static long key(int x,int y,int z){return ((long)x&0x3ffffffL)<<38|((long)z&0x3ffffffL)<<12|((long)y&0xfffL);}
    public static int x(long key){return (int)(key>>38);}
    public static int y(long key){return (int)(key<<52>>52);}
    public static int z(long key){return (int)(key<<26>>38);}
    public static int cell(double coordinate){return (int)Math.floor(coordinate/4);}
    public Space space(AtomicBoolean cancelled){return new Space(this,cancelled);}
    public record Bounds(double x,double z,double radius) {}
    /** Worker-owned query cursor; the shared snapshot itself remains immutable. */
    public static final class Space implements GlidePlanner.Corridor,SurfaceLaunchPlanner.Water {
        private final CollisionSnapshot snapshot;
        private final AtomicBoolean cancelled;
        private final LinkedHashSet<Long> missing=new LinkedHashSet<>();
        private Bounds bounds;
        private boolean exploratory;
        private Map<Long,Cell> coverage;
        /** Selected funnel proofs retain only the numeric cells they actually depended on. */
        public Space proofSpace(){var result=new Space(snapshot,cancelled);result.bounds=bounds;result.exploratory=exploratory;result.coverage=new HashMap<>();return result;}
        public Map<Long,Cell> coverage(){return coverage==null?Map.of():Map.copyOf(coverage);}
        public void explore(){exploratory=true;}
        public void navigation(Bounds bounds){this.bounds=bounds;}
        public Space(CollisionSnapshot snapshot,AtomicBoolean cancelled){this.snapshot=snapshot;this.cancelled=cancelled;}
        public Set<Long> missing(){return Set.copyOf(missing);}
        private Cell get(int x,int y,int z){
            if(cancelled.get()||Thread.currentThread().isInterrupted())throw new CancellationException();
            long key=key(x,y,z);var c=snapshot.cells.get(key);
            if(coverage!=null&&c!=null)coverage.put(key,c);
            if(c==null&&missing.size()<192)missing.add(key);
            return c;
        }
        @Override public boolean clear(FlightPrediction.State a,FlightPrediction.State b,double side,double vertical){
            if(bounds!=null&&Math.hypot(b.x()-bounds.x,b.z()-bounds.z)>bounds.radius)return false;
            double r=snapshot.width*.5+side;
            if(Math.min(a.x(),b.x())-r<snapshot.minX||Math.max(a.x(),b.x())+r>snapshot.maxX
                ||Math.min(a.z(),b.z())-r<snapshot.minZ||Math.max(a.z(),b.z())+r>snapshot.maxZ)return false;
            int x0=cell(Math.min(a.x(),b.x())-r),x1=cell(Math.max(a.x(),b.x())+r);
            int z0=cell(Math.min(a.z(),b.z())-r),z1=cell(Math.max(a.z(),b.z())+r);
            int y0=cell(Math.min(a.y(),b.y())-snapshot.eye-vertical),y1=cell(Math.max(a.y(),b.y())+snapshot.height-snapshot.eye+vertical);
            boolean known=true;
            for(int x=x0;x<=x1;x++)for(int y=y0;y<=y1;y++)for(int z=z0;z<=z1;z++){
                var c=get(x,y,z);if(c==null){known=false;continue;}if(!c.loaded)return false;
                if(intersects(c.solid,a,b,side,vertical)||!snapshot.waterEscape&&intersects(c.fluid,a,b,side,vertical))return false;
            }
            return known||exploratory;
        }
        private boolean intersects(List<FlightCorridor.Box> boxes,FlightPrediction.State a,FlightPrediction.State b,double side,double vertical){
            for(var box:boxes)if(FlightCorridor.intersects(box,a.x(),a.y(),a.z(),b.x(),b.y(),b.z(),snapshot.width*.5,snapshot.height,snapshot.eye,side,vertical))return true;
            return false;
        }
        public boolean segment(double ax,double ay,double az,double bx,double by,double bz,double side,double vertical){
            int steps=Math.max(1,(int)Math.ceil(Math.sqrt((bx-ax)*(bx-ax)+(by-ay)*(by-ay)+(bz-az)*(bz-az))/2));
            var previous=point(ax,ay,az);
            boolean clear=true;
            for(int i=1;i<=steps;i++){double t=i/(double)steps;var next=point(ax+(bx-ax)*t,ay+(by-ay)*t,az+(bz-az)*t);if(!clear(previous,next,side,vertical))clear=false;previous=next;}return clear;
        }
        public boolean ray(double ax,double ay,double az,double bx,double by,double bz){
            int steps=Math.max(1,(int)Math.ceil(Math.sqrt((bx-ax)*(bx-ax)+(by-ay)*(by-ay)+(bz-az)*(bz-az))));
            for(int i=0;i<=steps;i++){
                double t=i/(double)steps;var c=get(cell(ax+(bx-ax)*t),cell(ay+(by-ay)*t),cell(az+(bz-az)*t));if(c==null){if(exploratory)continue;return false;}if(!c.loaded)return false;
                for(var box:c.solid)if(FlightCorridor.intersects(box,ax,ay,az,bx,by,bz,0,0,0,0,0))return false;
            }return true;
        }
        @Override public boolean wet(FlightPrediction.State s){
            int x=(int)Math.floor(s.x()),y=(int)Math.floor(s.y()-.35),z=(int)Math.floor(s.z());var c=get(Math.floorDiv(x,4),Math.floorDiv(y,4),Math.floorDiv(z,4));
            return c!=null&&(c.water&(1L<<((x&3)*16+(y&3)*4+(z&3))))!=0;
        }
        public double support(double x,double z,double y){
            var boxes=new ArrayList<FlightCorridor.Box>();
            for(int cy=cell(y-3);cy<=cell(y+2);cy++){var c=get(cell(x),cy,cell(z));if(c==null||!c.loaded)return Double.NaN;boxes.addAll(c.solid);}
            return SupportSurface.nearest(boxes,x,z,y,1.26,snapshot.height);
        }
    }
    public static FlightPrediction.State point(double x,double y,double z){return new FlightPrediction.State(x,y,z,0,0,0,0,0);}
}
