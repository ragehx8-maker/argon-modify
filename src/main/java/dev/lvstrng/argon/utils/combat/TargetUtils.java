package dev.lvstrng.argon.utils.combat;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

public final class TargetUtils {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private TargetUtils() {}

    public static boolean isValidTarget(Entity entity) {
        if (entity == null || !entity.isAlive()) return false;
        if (entity == mc.player) return false;
        if (entity instanceof LivingEntity && ((LivingEntity) entity).isDead()) return false;
        return true;
    }

    public static Vec3d getEyesPos(Entity entity) {
        return new Vec3d(entity.getX(), entity.getEyeY(), entity.getZ());
    }
}
