package dev.lvstrng.argon.utils.rotation;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

public class RotationUtils {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    // Line 36 sensitivity casting error fix
    public static float getSensitivity() {
        if (mc.options == null || mc.options.getMouseSensitivity() == null) return 0.5f;
        return (float) (double) mc.options.getMouseSensitivity().getValue();
    }

    public static Vec3d getEyesPos(Entity entity) {
        if (entity == null) return Vec3d.ZERO;
        return new Vec3d(entity.getX(), entity.getY() + entity.getEyeHeight(entity.getPose()), entity.getZ());
    }

    public static Rotation getDirection(Entity from, Vec3d to) {
        if (from == null || to == null) return new Rotation(0.0f, 0.0f);
        Vec3d eyes = getEyesPos(from);
        double diffX = to.x - eyes.x;
        double diffY = to.y - eyes.y;
        double diffZ = to.z - eyes.z;
        double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);
        float yaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0F;
        float pitch = (float) -Math.toDegrees(Math.atan2(diffY, diffXZ));
        return new Rotation(yaw, pitch);
    }

    public static double getTotalDiff(Rotation rot1, Rotation rot2) {
        if (rot1 == null || rot2 == null) return 0.0;
        float yawDiff = Math.abs(rot1.getYaw() - rot2.getYaw());
        float pitchDiff = Math.abs(rot1.getPitch() - rot2.getPitch());
        return yawDiff + pitchDiff;
    }

    public static Rotation getSmoothRotation(Rotation current, Rotation target, double speed) {
        if (current == null || target == null) return target;
        float yaw = current.getYaw() + (target.getYaw() - current.getYaw()) * (float) speed;
        float pitch = current.getPitch() + (target.getPitch() - current.getPitch()) * (float) speed;
        return new Rotation(yaw, pitch);
    }

    public static double getAngleToRotation(Rotation rotation) {
        if (rotation == null || mc.player == null) return 0.0;
        Rotation currentRot = new Rotation(mc.player.getYaw(), mc.player.getPitch());
        return getTotalDiff(currentRot, rotation);
    }
}
