package dev.winefox.skyfarer.ai;

/** Lexicographic strategy: protected area/previous target, then phantom, then route quality. */
public final class TargetPriority {
    private TargetPriority() {}
    public record Rank(boolean strategic, boolean phantom, double route) implements Comparable<Rank> {
        @Override public int compareTo(Rank other) {
            int tier=Boolean.compare(strategic,other.strategic);
            if(tier!=0) return tier;
            int type=Boolean.compare(phantom,other.phantom);
            return type!=0?type:Double.compare(route,other.route);
        }
        public boolean sameLayer(Rank other) { return strategic==other.strategic && phantom==other.phantom; }
    }
    public static double protectedRadius(boolean home,double workRadius) { return 16+(home?Math.max(0,workRadius):0); }
    public static Rank rank(boolean previous,double distance,double radius,boolean phantom,double route) {
        return new Rank(previous || distance<=radius,phantom,route);
    }
}
