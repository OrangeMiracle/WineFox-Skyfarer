package dev.winefox.skyfarer.command;

import java.util.HashSet;
import java.util.Set;

/** Fixed-size lookahead, independent of journey length and old positions. */
public final class TicketWindow {
    public record Cell(int x,int z) {}
    private TicketWindow() {}
    public static Set<Cell> around(double x,double z,double dx,double dz) {
        return around(x,z,dx,dz,48);
    }
    public static Set<Cell> around(double x,double z,double dx,double dz,double lookahead) {
        double length=Math.hypot(dx,dz);if(length>1){dx/=length;dz/=length;}
        var result=new HashSet<Cell>();
        for(int distance=0;distance<=Math.ceil(Math.clamp(lookahead,48,480)/16)*16;distance+=16) {
            int cx=(int)Math.floor((x+dx*distance)/16),cz=(int)Math.floor((z+dz*distance)/16);
            for(int ox=-1;ox<=1;ox++)for(int oz=-1;oz<=1;oz++)result.add(new Cell(cx+ox,cz+oz));
        }
        return Set.copyOf(result);
    }
}
