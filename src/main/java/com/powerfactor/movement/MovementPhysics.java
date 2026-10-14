package com.powerfactor.movement;

/** Shared bounded velocity, cable tension and aerodynamic steering for movement equipment. */
public final class MovementPhysics {
    public record Vector(double x, double y, double z) {
        public static final Vector ZERO = new Vector(0, 0, 0);
        public Vector {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) throw new IllegalArgumentException("Non-finite motion");
        }
        public Vector add(Vector v) { return new Vector(x + v.x, y + v.y, z + v.z); }
        public Vector scale(double scale) { return new Vector(x * scale, y * scale, z * scale); }
        public double dot(Vector v) { return x * v.x + y * v.y + z * v.z; }
        public double length() { return Math.sqrt(dot(this)); }
        public Vector normalized() { double l = length(); return l < 1e-8 ? ZERO : scale(1 / l); }
        public Vector limited(double maximum) { double l = length(); return l > maximum ? scale(maximum / l) : this; }
    }
    public record Cable(Vector velocity, double length, boolean taut) {}
    public record Drive(Vector velocity, double yaw) {}
    /** Blocks/tick, at Minecraft's fixed 20 Hz. These are original tuning values, not JC engine constants. */
    public static final double MAX_FLIGHT_SPEED = 5.5, MAX_REEL_SPEED = 3.4;

    /** Traction preserves inertia, damps lateral slip and brakes before changing drive direction. */
    public static Drive drive(Vector velocity, double yawDegrees, double throttle, double steer, boolean brake, boolean boost, boolean ground) {
        throttle = Math.clamp(throttle, -1, 1); steer = Math.clamp(steer, -1, 1);
        double yaw = Math.toRadians(yawDegrees), sx = -Math.sin(yaw), sz = Math.cos(yaw);
        double forward = velocity.x * sx + velocity.z * sz;
        double lateral = velocity.x * sz - velocity.z * sx;
        if (ground && Math.abs(forward) > 0.015) yawDegrees += steer * 3.2 * (0.2 + Math.min(1, Math.abs(forward) / 0.8)) * Math.signum(forward);
        forward *= brake && ground ? 0.66 : ground ? 0.985 : 0.996;
        lateral *= ground ? 0.76 : 0.99;
        if (ground && !brake && throttle != 0) {
            if (forward * throttle < -0.05) forward *= 0.72;
            double acceleration = boost && throttle > 0 ? 0.045 : 0.032;
            if (throttle < 0 && forward > -0.4) forward = Math.max(-0.4, forward + throttle * acceleration);
            else if (throttle > 0 && forward < (boost ? 2.2 : 1.25)) forward = Math.min(boost ? 2.2 : 1.25, forward + throttle * acceleration);
            forward = Math.clamp(forward, -0.4, 2.2);
        }
        yaw = Math.toRadians(yawDegrees); sx = -Math.sin(yaw); sz = Math.cos(yaw);
        return new Drive(new Vector(sx * forward + sz * lateral, Math.max(-2.8, velocity.y - 0.08), sz * forward - sx * lateral), yawDegrees % 360);
    }
    public static Cable cable(Vector velocity, Vector toAnchor, double length, boolean reel, double reelMultiplier) {
        if (!Double.isFinite(length) || !Double.isFinite(reelMultiplier)) throw new IllegalArgumentException("Non-finite cable input");
        reelMultiplier = Math.clamp(reelMultiplier, 1, 1.6);
        double distance = toAnchor.length();
        // A motor pulls a slack line immediately. Shrink by the motor's actual closing speed,
        // rather than a slow fixed spool rate which fights the player's accumulated momentum.
        double radial = distance < 1e-6 ? 0 : velocity.dot(toAnchor.normalized());
        double reelSpeed = MAX_REEL_SPEED * (.8 + .2 * reelMultiplier);
        length = Math.max(2.0, length - (reel ? Math.max(.9 * reelMultiplier, Math.min(reelSpeed, radial)) : 0));
        if (distance < 1e-6) return new Cable(velocity.limited(MAX_FLIGHT_SPEED), length, false);
        Vector normal = toAnchor.normalized();
        boolean taut = distance >= length - 0.15;
        if (taut && !reel) {
            if (radial < 0) velocity = velocity.add(normal.scale(-radial));
            double stretch = Math.max(0, distance - length);
            velocity = velocity.add(normal.scale(Math.min(0.38, stretch * 0.2)));
        }
        if (reel) {
            double pull = Math.min(.28 * reelMultiplier, Math.max(0, reelSpeed - radial));
            // Keep tangent velocity: releasing a fast fly-by carries that speed into the wingsuit.
            velocity = velocity.add(normal.scale(pull));
        }
        return new Cable(velocity.limited(MAX_FLIGHT_SPEED), length, taut);
    }

    /** Complete flight step, replacing Elytra aerodynamics while retaining native movement/collisions. */
    public static Vector glide(Vector velocity, double yawDegrees, double pitchDegrees, boolean brake) {
        if (!Double.isFinite(yawDegrees) || !Double.isFinite(pitchDegrees)) throw new IllegalArgumentException("Non-finite flight input");
        velocity = velocity.limited(MAX_FLIGHT_SPEED);
        double speed = Math.hypot(velocity.x, velocity.z);
        double yaw = Math.toRadians(yawDegrees), pitch = Math.toRadians(Math.clamp(pitchDegrees, -80, 85));
        double dx = -Math.sin(yaw), dz = Math.cos(yaw);
        double lift = Math.pow(Math.cos(pitch), 2) * Math.min(1, speed * speed / .25);
        double vy = velocity.y + .06 * (-1 + .88 * lift);
        double vx = velocity.x, vz = velocity.z;
        if (vy < 0 && speed > .02) {
            double recovery = -vy * .12 * lift;
            vx += dx * recovery; vz += dz * recovery; vy += recovery;
        }
        if (pitch < 0 && speed > .02) {
            double climb = speed * -Math.sin(pitch) * .045;
            vx -= dx * climb; vz -= dz * climb; vy += climb * 2.6;
        }
        if (pitch > 0 && !brake) {
            // Dive into the airstream; gravity supplies speed, never a powered forward thrust.
            vy += (-speed * Math.sin(pitch) - vy) * .035;
        }
        double horizontal = Math.hypot(vx, vz);
        if (horizontal > 1e-6) {
            double heading = Math.atan2(-vx, vz);
            double difference = Math.atan2(Math.sin(yaw-heading), Math.cos(yaw-heading));
            // Bound turn rate, preserving speed through ordinary banks instead of lerp-braking.
            heading += Math.clamp(difference * .16, -.065, .065);
            vx = -Math.sin(heading) * horizontal; vz = Math.cos(heading) * horizontal;
        }
        double drag = brake ? .9 : .997;
        if (brake) vy = Math.min(vy, -.08);
        return new Vector(vx * drag, vy * .995, vz * drag).limited(MAX_FLIGHT_SPEED);
    }
    private MovementPhysics() {}
}
