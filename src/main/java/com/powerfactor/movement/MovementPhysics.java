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
        double distance = toAnchor.length();
        length = Math.max(2.0, length - (reel ? 0.45 * Math.clamp(reelMultiplier, 1, 1.6) : 0));
        if (distance < 1e-6) return new Cable(velocity.limited(2.2), length, false);
        Vector normal = toAnchor.normalized();
        boolean taut = distance >= length - 0.15;
        if (taut) {
            double radial = velocity.dot(normal);
            if (radial < 0) velocity = velocity.add(normal.scale(-radial));
            double stretch = Math.max(0, distance - length);
            velocity = velocity.add(normal.scale(Math.min(0.45, stretch * 0.22 + Math.max(0, normal.y) * 0.08)));
            if (!reel) velocity = new Vector(velocity.x * 1.045, velocity.y, velocity.z * 1.045);
        }
        if (reel) velocity = velocity.add(normal.scale(0.13 * reelMultiplier));
        return new Cable(velocity.limited(reel ? 2.2 : 2.4), length, taut);
    }

    /** Complements native collision-aware gliding: controlled turns, dive speed, stall and airbrake. */
    public static Vector glide(Vector velocity, double yawDegrees, double pitchDegrees, boolean brake) {
        double speed = Math.hypot(velocity.x, velocity.z);
        double yaw = Math.toRadians(yawDegrees), pitch = Math.toRadians(Math.clamp(pitchDegrees, -85, 85));
        double turn = brake ? 0.055 : 0.025;
        double vx = velocity.x + (-Math.sin(yaw) * speed - velocity.x) * turn;
        double vz = velocity.z + (Math.cos(yaw) * speed - velocity.z) * turn;
        double vy = velocity.y;
        if (speed < 0.22) vy -= 0.025;
        else vy += Math.min(0.014, speed * speed * 0.006) * Math.max(0, Math.cos(pitch));
        if (pitch > 0.15 && !brake) {
            double dive = Math.sin(pitch) * 0.022;
            vx -= Math.sin(yaw) * dive; vz += Math.cos(yaw) * dive;
        }
        if (brake) { vx *= 0.90; vz *= 0.90; vy = Math.min(vy, -0.08); }
        return new Vector(vx, Math.max(-1.8, vy), vz).limited(2.4);
    }
    private MovementPhysics() {}
}
