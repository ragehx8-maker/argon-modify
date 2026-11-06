package dev.lvstrng.argon.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

public class RotationUtils {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public static float getSensitivity() {
        if (mc.options == null || mc.options.getMouseSensitivity() == null) return 0.5f;
        return (float) (double) mc.options.getMouseSensitivity().getValue();
    }

    public static Vec3d getEyesPos(Entity entity) {
        if (entity == null) return Vec3d.ZERO;
        return new Vec3d(entity.getX(), entity.getY() + entity.getEyeHeight(entity.getPose()), entity.getZ());
    }

    public static Object getDirection(Entity from, Vec3d to) {
        if (from == null || to == null) return null;
        Vec3d eyes = getEyesPos(from);
        double diffX = to.x - eyes.x;
        double diffY = to.y - eyes.y;
        double diffZ = to.z - eyes.z;
        double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);
        float yaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0F;
        float pitch = (float) -Math.toDegrees(Math.atan2(diffY, diffXZ));
        return new float[]{yaw, pitch};
    }

    public static double getTotalDiff(Object rot1, Object rot2) {
        return 0.0;
    }

    public static Object getSmoothRotation(Object current, Object target, double speed) {
        return target;
    }

    public static double getAngleToRotation(Object rotation) {
        return 0.0;
    }
}
