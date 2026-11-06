package dev.lvstrng.argon.managers;

import dev.lvstrng.argon.utils.RotationUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

@Environment(EnvType.CLIENT)
public class CustomRotator {
   private static boolean active = false;
   private static float targetYaw = 0.0F;
   private static float targetPitch = 0.0F;

   // Target position set karne ke liye (Rotation calculate karta hai)
   public static void faceTarget(Vec3d pos) {
      float[] rots = RotationUtils.getRotations(pos);
      targetYaw = rots[0];
      targetPitch = rots[1];
      active = true;
   }

   // Rotation band karne ke liye
   public static void reset() {
      active = false;
   }

   public static boolean isRotating() {
      return active;
   }

   public static float getTargetYaw() {
      return targetYaw;
   }

   public static float getTargetPitch() {
      return targetPitch;
   }
}
