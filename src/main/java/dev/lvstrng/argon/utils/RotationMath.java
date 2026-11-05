package dev.lvstrng.argon.utils;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class RotationMath {
    public static final float PITCH_LIMIT = 90.0f;

    private RotationMath() {}

    public static float[] anglesTo(Vec3d eye, Vec3d point, float currentYaw) {
        return anglesTo(eye.x, eye.y, eye.z, point.x, point.y, point.z, currentYaw);
    }

    public static float[] anglesTo(double eyeX, double eyeY, double eyeZ, double x, double y, double z, float currentYaw) {
        double deltaX = x - eyeX;
        double deltaY = y - eyeY;
        double deltaZ = z - eyeZ;
        double horizontal = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        float yaw = (float) Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90.0f;
        float pitch = (float) (-Math.toDegrees(Math.atan2(deltaY, horizontal)));
        return new float[]{nearestYaw(yaw, currentYaw), clampPitch(pitch)};
    }

    public static float nearestYaw(float yaw, float reference) {
        return reference + MathHelper.wrapDegrees(yaw - reference);
    }

    public static float yawDelta(float from, float to) {
        return MathHelper.wrapDegrees(to - from);
    }

    public static double axisDelta(double current, double target, boolean yawAxis) {
        return yawAxis ? MathHelper.wrapDegrees((float) (target - current)) : target - current;
    }

    public static float clampPitch(float pitch) {
        return MathHelper.clamp(pitch, -PITCH_LIMIT, PITCH_LIMIT);
    }

    public static Vec3d direction(float yaw, float pitch) {
        float yawRad = (float) Math.toRadians(yaw);
        float pitchRad = (float) Math.toRadians(pitch);
        double x = -Math.sin(yawRad) * Math.cos(pitchRad);
        double y = -Math.sin(pitchRad);
        double z = Math.cos(yawRad) * Math.cos(pitchRad);
        return new Vec3d(x, y, z).normalize();
    }
}
