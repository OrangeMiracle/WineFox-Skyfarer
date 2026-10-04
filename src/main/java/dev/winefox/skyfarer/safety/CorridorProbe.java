package dev.winefox.skyfarer.safety;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import dev.winefox.skyfarer.flight.FlightCorridor;
import dev.winefox.skyfarer.flight.FlightPrediction;
import dev.winefox.skyfarer.flight.GlidePlanner;
import dev.winefox.skyfarer.flight.SupportSurface;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Per-tick lazy snapshot. Reads already loaded blocks only; nearby blocks outside the sweep do not matter. */
public final class CorridorProbe implements GlidePlanner.Corridor {
    private final EntityMaid maid;
    private final CollisionContext context;
    private final double overrideWidth,overrideHeight,overrideEye;
    private final boolean waterEscape;
    private final Map<BlockPos, List<FlightCorridor.Box>> blocks = new HashMap<>();
    private final Map<BlockPos, List<FlightCorridor.Box>> cells = new HashMap<>();
    public CorridorProbe(EntityMaid maid) { this(maid,Double.NaN,Double.NaN,Double.NaN); }
    public CorridorProbe(EntityMaid maid,double width,double height,double eye) {
        this(maid,width,height,eye,false);
    }
    public CorridorProbe(EntityMaid maid,double width,double height,double eye,boolean waterEscape) {
        this.maid=maid; context=CollisionContext.of(maid); overrideWidth=width; overrideHeight=height; overrideEye=eye;
        this.waterEscape=waterEscape;
    }
    @Override public boolean clear(FlightPrediction.State from, FlightPrediction.State to, double side, double vertical) {
        return clear(new Vec3(from.x(), from.y(), from.z()), new Vec3(to.x(), to.y(), to.z()), side, vertical);
    }
    public boolean clear(Vec3 from, Vec3 to, double side, double vertical) {
        double width=Double.isFinite(overrideWidth)?overrideWidth:maid.getBbWidth();
        double eye=Double.isFinite(overrideEye)?overrideEye:maid.getEyeHeight(), height=Double.isFinite(overrideHeight)?overrideHeight:maid.getBbHeight();
        double radius = width * 0.5 + side;
        double minX = Math.min(from.x,to.x)-radius, maxX = Math.max(from.x,to.x)+radius;
        double minZ = Math.min(from.z,to.z)-radius, maxZ = Math.max(from.z,to.z)+radius;
        var border = maid.level().getWorldBorder();
        if (minX < border.getMinX() || maxX > border.getMaxX() || minZ < border.getMinZ() || maxZ > border.getMaxZ()) return false;
        double minY = Math.min(from.y,to.y)-eye-vertical;
        double maxY = Math.max(from.y,to.y)+height-eye+vertical;
        // Loaded terrain can be outside entity simulation range. Do not plan into that shell.
        if(maid.level() instanceof ServerLevel server) {
            for(int cx=(int)Math.floor(minX/16);cx<=(int)Math.floor(maxX/16);cx++)
                for(int cz=(int)Math.floor(minZ/16);cz<=(int)Math.floor(maxZ/16);cz++)
                    if(!server.isPositionEntityTicking(new BlockPos(cx*16,(int)to.y,cz*16))) return false;
        }
        // Repeated candidate trajectories query cached 4^3 spatial cells, not hundreds of air blocks.
        for (int x=cell(minX); x<=cell(maxX); x++)
            for (int z=cell(minZ); z<=cell(maxZ); z++)
                for (int y=cell(minY); y<=cell(maxY); y++) {
                    var shapes=cellShapes(x,y,z);
                    if (shapes==null) return false;
                    for (var box : shapes) if (FlightCorridor.intersects(box, from.x,from.y,from.z,to.x,to.y,to.z,
                        width*.5, height, eye, side, vertical)) return false;
                }
        return true;
    }
    private static int cell(double coordinate) { return (int)Math.floor(coordinate/4); }
    private List<FlightCorridor.Box> cellShapes(int cx,int cy,int cz) {
        BlockPos key=new BlockPos(cx,cy,cz);
        var result=cells.get(key);
        if(result!=null) return result;
        result=new ArrayList<>();
        // One-block halo includes fences and shapes extending beyond their owning block.
        for(int x=cx*4-1;x<=cx*4+4;x++) for(int z=cz*4-1;z<=cz*4+4;z++) for(int y=cy*4-1;y<=cy*4+4;y++) {
            BlockPos pos=new BlockPos(x,y,z);
            var shapes=blockShapes(pos);
            if(shapes==null) return null;
            result.addAll(shapes);
        }
        cells.put(key,result); return result;
    }
    private List<FlightCorridor.Box> blockShapes(BlockPos pos) {
        var shapes=blocks.get(pos);
        if(shapes!=null) return shapes;
        if(blocks.size()>=24000 || !maid.level().hasChunkAt(pos)) return null;
        var state=maid.level().getBlockState(pos);
        shapes=new ArrayList<>();
        int x=pos.getX(),y=pos.getY(),z=pos.getZ();
        for(var box:state.getCollisionShape(maid.level(),pos,context).toAabbs())
            shapes.add(new FlightCorridor.Box(x+box.minX,y+box.minY,z+box.minZ,x+box.maxX,y+box.maxY,z+box.maxZ));
        var fluid=state.getFluidState();
        if(!fluid.isEmpty() && !(waterEscape && fluid.is(FluidTags.WATER)))
            shapes.add(new FlightCorridor.Box(x,y,z,x+1,y+fluid.getHeight(maid.level(),pos),z+1));
        blocks.put(pos,shapes);return shapes;
    }
    public boolean lane(Vec3 entry, Vec3 contact, Vec3 exit, double side, double vertical) {
        // Split long diagonal queries to keep the inspected volume a tube rather than a large cuboid.
        return segment(entry, contact, side, vertical) && segment(contact, exit, side, vertical);
    }
    /** Nearest support surface to target feet; a roof above the room is not a ground sample. */
    public double supportNear(double x,double z,double referenceY) {
        return supportNear(x,z,referenceY,2.51);
    }
    public double supportSample(double x,double z,double referenceY) {
        // Adjacent stair tops are allowed, but a roof two blocks overhead cannot become the ground.
        return supportNear(x,z,referenceY,1.26);
    }
    private double supportNear(double x,double z,double referenceY,double maximumStep) {
        // Sample only the column and a one-block halo for protruding shapes, not entire 4^3 cells.
        var column=new ArrayList<FlightCorridor.Box>();
        int bx=(int)Math.floor(x),bz=(int)Math.floor(z);
        for(int px=bx-1;px<=bx+1;px++) for(int pz=bz-1;pz<=bz+1;pz++)
            for(int y=(int)Math.floor(referenceY-3);y<=Math.floor(referenceY+2);y++) {
                var shapes=blockShapes(new BlockPos(px,y,pz));
                if(shapes==null) return Double.NaN;
                for(var box:shapes) if(x>=box.minX() && x<=box.maxX() && z>=box.minZ() && z<=box.maxZ()) column.add(box);
            }
        return SupportSurface.nearest(column,x,z,referenceY,maximumStep,maid.getBbHeight());
    }
    /** Follow connected local support in one-block steps, including slopes steeper than 40 degrees. */
    public double supportAlong(double x,double z,double referenceY,double dx,double dz) {
        double surface=referenceY;
        for(int step=1;step<=3;step++) {
            surface=supportNear(x+dx*step,z+dz*step,surface);
            if(!Double.isFinite(surface)) return Double.NaN;
        }
        return surface;
    }
    public boolean segment(Vec3 from, Vec3 to, double side, double vertical) {
        int steps = Math.max(1, (int)Math.ceil(from.distanceTo(to)/2));
        Vec3 last = from;
        for (int i=1; i<=steps; i++) {
            Vec3 next = from.lerp(to, i/(double)steps);
            if (!clear(last,next,side,vertical)) return false;
            last=next;
        }
        return true;
    }
}
