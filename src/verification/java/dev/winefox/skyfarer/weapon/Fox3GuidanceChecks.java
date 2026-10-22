package dev.winefox.skyfarer.weapon;

import dev.winefox.skyfarer.weapon.Fox3Guidance.Vector;

/** Exercises the production guidance directly; no Minecraft runtime or fixtures are needed. */
public final class Fox3GuidanceChecks {
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

    public static void main(String[] args) {
        mirroredClimb();
        boundedTurns();
        phaseTransitions();
        interception();
        System.out.println("Fox3 guidance passed: mirrored outward 12-tick climb; continuous 12-degree rollover then 6-degree tracking, radius >=5; bounded irreversible phases; fixed/moving target interception; exact U-turn and zero-vector stability");
    }

    private static void mirroredClimb() {
        for (double heading = 0; heading < Math.PI * 2; heading += Math.PI / 8) {
            double forwardX = Math.sin(heading), forwardZ = Math.cos(heading);
            Vector left = Fox3Guidance.launchVelocity(forwardX, forwardZ, -1);
            Vector right = Fox3Guidance.launchVelocity(forwardX, forwardZ, 1);
            Vector lp = new Vector(0, 0, 0), rp = lp;
            Vector lv = left, rv = right;
            Vector sideways = new Vector(-forwardZ, 0, forwardX);
            for (int age = 1; age <= Fox3Guidance.CLIMB_TICKS; age++) {
                lv = Fox3Guidance.step(lv, Fox3Guidance.climbDirection(left, age), Fox3Guidance.Phase.CLIMB);
                rv = Fox3Guidance.step(rv, Fox3Guidance.climbDirection(right, age), Fox3Guidance.Phase.CLIMB);
                lp = lp.add(lv); rp = rp.add(rv);
                check(lv.y() > 0 && rv.y() > 0, "climb stopped rising");
                check(lp.dot(sideways) < 0 && rp.dot(sideways) > 0, "launch sides converged");
                check(Math.abs(lp.y() - rp.y()) < 1e-8, "mirrored vertical climb differs");
            }
            check(lp.add(rp.scale(-1)).length() > 10, "double launch did not visibly fan out");
            check(lp.y() > 8 && rp.y() > 8, "climb arc too shallow");
        }
    }

    private static void boundedTurns() {
        var random = new java.util.Random(773);
        Vector velocity = new Vector(0, Fox3Guidance.LAUNCH_SPEED, 0);
        for (int step = 0; step < 10000; step++) {
            Vector aim = step % 7 == 0 ? velocity.scale(-1) : new Vector(random.nextGaussian(), random.nextGaussian(), random.nextGaussian());
            var phase = step % 3 == 0 ? Fox3Guidance.Phase.ROLLOVER : Fox3Guidance.Phase.TRACK;
            Vector next = Fox3Guidance.step(velocity, aim, phase);
            double angle = Math.acos(Math.clamp(velocity.unit().dot(next.unit()), -1, 1));
            double limit = phase == Fox3Guidance.Phase.ROLLOVER ? Fox3Guidance.ROLLOVER_TURN_RADIANS : Fox3Guidance.MAX_TURN_RADIANS;
            check(angle <= limit + 1e-10, "turn exceeds phase angular cap");
            check(angle < 1e-9 || next.length() / (2 * Math.sin(angle / 2)) >= Fox3Guidance.MIN_TURN_RADIUS - 1e-7, "turn radius below five blocks");
            check(next.length() <= Fox3Guidance.MAX_SPEED + 1e-9 && next.length() >= Fox3Guidance.LAUNCH_SPEED, "speed bound");
            velocity = next;
        }
        Vector stable = Fox3Guidance.step(new Vector(0, 0, 0), new Vector(0, 0, 0));
        check(Double.isFinite(stable.length()) && stable.length() > 0, "zero input poisoned flight");
    }

    private static void phaseTransitions() {
        Vector up = new Vector(0, 1, 0), down = new Vector(0, -1, 0);
        check(Fox3Guidance.nextPhase(Fox3Guidance.Phase.CLIMB, 12, up, down) == Fox3Guidance.Phase.CLIMB, "climb ended early");
        check(Fox3Guidance.nextPhase(Fox3Guidance.Phase.CLIMB, 13, up, down) == Fox3Guidance.Phase.ROLLOVER, "missing rollover");
        check(Fox3Guidance.nextPhase(Fox3Guidance.Phase.ROLLOVER, 15, down, down) == Fox3Guidance.Phase.ROLLOVER, "rollover minimum omitted");
        check(Fox3Guidance.nextPhase(Fox3Guidance.Phase.ROLLOVER, 16, down, down) == Fox3Guidance.Phase.TRACK, "aligned downward rollover never completed");
        check(Fox3Guidance.nextPhase(Fox3Guidance.Phase.ROLLOVER, 36, up, down) == Fox3Guidance.Phase.TRACK, "rollover deadline not enforced");
        check(Fox3Guidance.nextPhase(Fox3Guidance.Phase.TRACK, 100, up, down) == Fox3Guidance.Phase.TRACK, "retarget can reenter aggressive rollover");
        Vector cruise = up.scale(Fox3Guidance.MAX_SPEED);
        double rollAngle = Math.acos(cruise.unit().dot(Fox3Guidance.step(cruise, down, Fox3Guidance.Phase.ROLLOVER).unit()));
        double trackAngle = Math.acos(cruise.unit().dot(Fox3Guidance.step(cruise, down, Fox3Guidance.Phase.TRACK).unit()));
        check(rollAngle > trackAngle * 1.9, "rollover not visibly tighter than tracking");
    }

    private static void interception() {
        int hits = 0;
        for (double drift : new double[]{0, 0.10, -0.10}) for (int side : new int[]{-1, 1}) {
            Vector launch = Fox3Guidance.launchVelocity(0, 1, side);
            Vector velocity = launch, position = new Vector(side * 0.7, 20, 0);
            Vector rolloverPoint = new Vector(8, 1, 30);
            var phase = Fox3Guidance.Phase.CLIMB;
            boolean contact = false;
            for (int age = 1; age <= Fox3Guidance.MAX_LIFE_TICKS; age++) {
                Vector target = new Vector(8 + drift * age, 1, 30);
                Vector rolloverDirection = rolloverPoint.add(position.scale(-1));
                phase = Fox3Guidance.nextPhase(phase, age, velocity, rolloverDirection);
                Vector desired = phase == Fox3Guidance.Phase.CLIMB ? Fox3Guidance.climbDirection(launch, age)
                    : phase == Fox3Guidance.Phase.ROLLOVER ? rolloverDirection : target.add(position.scale(-1));
                Vector nextVelocity = Fox3Guidance.step(velocity, desired, phase);
                Vector next = position.add(nextVelocity);
                check(Math.abs(next.add(position.scale(-1)).length() - nextVelocity.length()) < 1e-9, "trajectory teleported");
                Vector segment = next.add(position.scale(-1));
                double fraction = Math.clamp(target.add(position.scale(-1)).dot(segment) / segment.dot(segment), 0, 1);
                double miss = target.add(position.add(segment.scale(fraction)).scale(-1)).length();
                if (miss <= 0.7) { contact = true; break; }
                velocity = nextVelocity; position = next;
            }
            check(contact, "failed bounded swoop/intercept for side " + side + " drift " + drift);
            hits++;
        }
        check(hits == 6, "not all trajectories ran");
    }
}
