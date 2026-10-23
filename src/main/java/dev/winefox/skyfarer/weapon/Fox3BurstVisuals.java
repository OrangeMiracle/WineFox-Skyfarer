package dev.winefox.skyfarer.weapon;

/** Bounded, presentation-only burst math shared by rendering and offline checks. */
public final class Fox3BurstVisuals {
    public static final byte CREEPER_EVENT = 17;
    public static final byte FOX_EVENT = 18;
    public static final int LIFE = 40, EXPAND = 8, HOLD = 22, MAX_ACTIVE = 16;
    public static final float RADIUS = 3.0f;
    public static byte event(boolean fox) { return fox ? FOX_EVENT : CREEPER_EVENT; }
    public static float expansion(float age) {
        float t = Math.clamp(age / EXPAND, 0, 1);
        return 1 - (1-t)*(1-t)*(1-t);
    }
    public static float fade(float age) {
        return Math.clamp((LIFE-age)/(LIFE-HOLD), 0, 1);
    }
    public static float scatter(float age) { return 1-fade(age); }
    public static float radius(float age) { return RADIUS*expansion(age)+.3f*scatter(age); }
    public static float lift(float age) { return RADIUS*expansion(age)+.25f*scatter(age); }
    // Only a minority twinkle. The readable hold never loses whole facial features.
    public static float sparkle(int point,float age) {
        return point%7==0 ? .78f+.22f*(float)Math.sin(age*1.8+point) : 1;
    }
    private Fox3BurstVisuals() {}
}
