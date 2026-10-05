package dev.winefox.skyfarer.flight;

/** Vanilla Elytra/attached-firework equations used only for prediction, never to move an entity. */
public final class FlightPrediction {
    private FlightPrediction() {}
    public record State(double x, double y, double z, double vx, double vy, double vz, double yaw, double pitch, PhysicsContext physics, int rocketDelay) {
        public State(double x,double y,double z,double vx,double vy,double vz,double yaw,double pitch) { this(x,y,z,vx,vy,vz,yaw,pitch,null,0); }
        public State(double x,double y,double z,double vx,double vy,double vz,double yaw,double pitch,PhysicsContext physics) { this(x,y,z,vx,vy,vz,yaw,pitch,physics,0); }
        public State withPhysics(PhysicsContext context) { return new State(x,y,z,vx,vy,vz,yaw,pitch,context,rocketDelay); }
        public State withRocketDelay(int ticks) { return new State(x,y,z,vx,vy,vz,yaw,pitch,physics,Math.clamp(ticks,0,1)); }
    }
    public record Choice(double pitch, double minimumY, double cost) {}
    /** Native Spears segment: half mob reach, real margin and forward-motion extension. */
    public record Aim(double x, double bottom, double z, double halfWidth, double height,
        double vx, double vy, double vz, double minReach, double maxReach, double margin, int readyIn) {
        public Aim after(int ticks) { return new Aim(x+vx*ticks,bottom+vy*ticks,z+vz*ticks,halfWidth,height,
            vx,vy,vz,minReach,maxReach,margin,Math.max(0,readyIn-ticks)); }
        public double missSquared(State s, int tick) {
            double pitch = Math.toRadians(s.pitch), yaw = Math.toRadians(s.yaw);
            double dx = -Math.sin(yaw) * Math.cos(pitch), dy = -Math.sin(pitch), dz = Math.cos(yaw) * Math.cos(pitch);
            double cx = x + vx * tick, cy = bottom + vy * tick + height * 0.5, cz = z + vz * tick;
            double far = maxReach + Math.max(0, s.vx * dx + s.vy * dy + s.vz * dz);
            // A tall virtual hitbox can intersect the segment away from its center projection.
            if(FlightCorridor.intersects(new FlightCorridor.Box(cx-halfWidth-margin,bottom+vy*tick-margin,cz-halfWidth-margin,
                cx+halfWidth+margin,bottom+vy*tick+height+margin,cz+halfWidth+margin),
                s.x+dx*minReach,s.y+dy*minReach,s.z+dz*minReach,s.x+dx*far,s.y+dy*far,s.z+dz*far,0,0,0,0,0)) return 0;
            double t = Math.clamp((cx - s.x) * dx + (cy - s.y) * dy + (cz - s.z) * dz, minReach, Math.max(minReach, far));
            double ex = Math.max(0, Math.abs(s.x + dx * t - cx) - halfWidth - margin);
            double ey = Math.max(0, Math.abs(s.y + dy * t - cy) - height * 0.5 - margin);
            double ez = Math.max(0, Math.abs(s.z + dz * t - cz) - halfWidth - margin);
            return ex * ex + ey * ey + ez * ez;
        }
    }
    public static double turn(double current, double desired, double rate) {
        double delta = (desired - current) % 360;
        if (delta >= 180) delta -= 360;
        if (delta < -180) delta += 360;
        return current + Math.clamp(delta, -rate, rate);
    }
    public static State step(State s, double yaw, double pitch, double yawRate, double pitchRate, double pullRate, double gravity, boolean rocket) {
        yaw = turn(s.yaw, yaw, yawRate);
        pitch = turn(s.pitch, pitch, pitch < s.pitch ? pullRate : pitchRate);
        return VanillaElytra.step(s,yaw,pitch,gravity,rocket);
    }
    public static double pathHeight(double remaining, double levelHeight, double slope, double flareDistance) {
        if(flareDistance==-2) { double d=Math.max(0,remaining); return levelHeight+slope*d*d/(d+8); }
        if(flareDistance<0) return levelHeight+remaining*slope;
        return levelHeight + Math.max(0, remaining - flareDistance) * slope;
    }
    public static double pathVerticalSpeed(State s,double remaining,double slope,double flareDistance) {
        if(flareDistance==-2) { double d=Math.max(0,remaining); return -Math.hypot(s.vx,s.vz)*slope*d*(d+16)/((d+8)*(d+8)); }
        return flareDistance<0 || remaining>flareDistance ? -Math.hypot(s.vx,s.vz)*slope : 0;
    }
    public static Choice choose(State initial, double yaw, double levelHeight, double distance, double slope,
        double flareDistance, double floorEyeY, int rocketTicks, double yawRate, double pitchRate, double pullRate, double gravity) {
        return choose(initial, yaw, levelHeight, distance, slope, flareDistance, floorEyeY, rocketTicks,
            yawRate, pitchRate, pullRate, gravity, null);
    }
    public static Choice choose(State initial, double yaw, double levelHeight, double distance, double slope,
        double flareDistance, double floorEyeY, int rocketTicks, double yawRate, double pitchRate, double pullRate, double gravity, Aim aim) {
        double best = Double.POSITIVE_INFINITY, bestPitch = -35, bestMinimum = initial.y;
        double dx = -Math.sin(Math.toRadians(yaw)), dz = Math.cos(Math.toRadians(yaw));
        for (double candidate = flareDistance<0?-75:-40; candidate <= (flareDistance<0?80:50); candidate += flareDistance<0?5:1) {
            State s = initial; double cost = 0, minimum = s.y, bestMiss = Double.POSITIVE_INFINITY;
            // Receding horizon: long constant-pitch forecasts falsely demand early leveling.
            // Strong rocket response needs a shorter horizon; expiry is simulated explicitly.
            int horizon = rocketTicks > 8 ? 8 : 10;
            for (int tick = 0; tick < horizon; tick++) {
                if (aim != null && tick >= aim.readyIn) {
                    // Item use happens before movement. Evaluate the newly steered look at this position.
                    State aimed = new State(s.x, s.y, s.z, s.vx, s.vy, s.vz, turn(s.yaw, yaw, yawRate),
                        turn(s.pitch, candidate, candidate < s.pitch ? pullRate : pitchRate));
                    bestMiss = Math.min(bestMiss, aim.missSquared(aimed, tick));
                }
                s = step(s, yaw, candidate, yawRate, pitchRate, pullRate, gravity, RocketProfile.active(tick,rocketTicks,initial));
                minimum = Math.min(minimum, s.y);
                double remaining = distance - ((s.x - initial.x) * dx + (s.z - initial.z) * dz);
                double goal = pathHeight(remaining, levelHeight, slope, flareDistance);
                double error = s.y - goal;
                double verticalError=s.vy-(flareDistance<0?pathVerticalSpeed(s,remaining,slope,flareDistance):0);
                cost += error * error * (error < 0 ? 3 : 1) + verticalError * verticalError * (remaining < flareDistance ? 10 : 2);
                if (Double.isFinite(floorEyeY) && s.y < floorEyeY) cost += 100000 * Math.pow(floorEyeY - s.y + 0.1, 2);
            }
            // Only influence the near-contact window; the ground penalty remains dominant.
            if (aim != null && Double.isFinite(bestMiss)
                && distance < Math.max(2, Math.hypot(initial.vx, initial.vz) * 3) + aim.maxReach)
                cost += 80 * bestMiss;
            double firstPitch = turn(initial.pitch, candidate, candidate < initial.pitch ? pullRate : pitchRate);
            cost += 0.01 * Math.pow(firstPitch - initial.pitch, 2);
            if (cost < best) { best = cost; bestPitch = candidate; bestMinimum = minimum; }
        }
        return new Choice(bestPitch, bestMinimum, best);
    }
}
