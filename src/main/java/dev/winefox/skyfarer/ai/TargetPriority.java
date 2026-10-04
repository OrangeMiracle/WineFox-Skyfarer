package dev.winefox.skyfarer.ai;

/** Marked targets first, then protected area/previous target; phantom and route quality within each tier. */
public final class TargetPriority {
    private TargetPriority() {}
    public record Rank(boolean marked,boolean strategic, boolean phantom, double route) implements Comparable<Rank> {
        public Rank(boolean strategic,boolean phantom,double route){this(false,strategic,phantom,route);}
        @Override public int compareTo(Rank other) {
            int command=Boolean.compare(marked,other.marked);if(command!=0)return command;
            int tier=Boolean.compare(strategic,other.strategic);
            if(tier!=0) return tier;
            int type=Boolean.compare(phantom,other.phantom);
            return type!=0?type:Double.compare(route,other.route);
        }
        public boolean sameLayer(Rank other) { return marked==other.marked && (marked||strategic==other.strategic) && phantom==other.phantom; }
    }
    public static double protectedRadius(boolean home,double workRadius) { return 16+(home?Math.max(0,workRadius):0); }
    public static Rank rank(boolean previous,double distance,double radius,boolean phantom,double route) {
        return new Rank(previous || distance<=radius,phantom,route);
    }
    public static Rank rank(boolean marked,boolean previous,double distance,double radius,boolean phantom,double route){return new Rank(marked,marked||previous||distance<=radius,phantom,route);}
}
