package dev.winefox.skyfarer.flight;

/** Swept axis-aligned body against exact block collision boxes, expressed around the eye. */
public final class FlightCorridor {
    private FlightCorridor() {}
    public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {}
    public static boolean intersects(Box b, double ax, double ay, double az, double bx, double by, double bz,
        double halfWidth, double height, double eyeHeight, double side, double vertical) {
        double[] low = {b.minX - halfWidth - side, b.minY - (height - eyeHeight) - vertical, b.minZ - halfWidth - side};
        double[] high = {b.maxX + halfWidth + side, b.maxY + eyeHeight + vertical, b.maxZ + halfWidth + side};
        double[] origin = {ax, ay, az}, delta = {bx-ax, by-ay, bz-az};
        double near = 0, far = 1;
        for (int i = 0; i < 3; i++) {
            if (Math.abs(delta[i]) < 1e-9) {
                if (origin[i] <= low[i] || origin[i] >= high[i]) return false;
            } else {
                double a = (low[i]-origin[i])/delta[i], c = (high[i]-origin[i])/delta[i];
                near = Math.max(near, Math.min(a,c)); far = Math.min(far, Math.max(a,c));
                if (near >= far) return false;
            }
        }
        return far > 0 && near < 1;
    }
}
