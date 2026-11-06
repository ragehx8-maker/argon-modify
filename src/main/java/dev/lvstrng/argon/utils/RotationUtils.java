package dev.lvstrng.argon.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class RotationUtils {

   // Target position par dekhne ke liye Yaw aur Pitch nikalne ka formula
   public static float[] getRotations(Vec3d targetPos) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player == null) return new float[]{mc.player.getYaw(), mc.player.getPitch()};

      Vec3d eyesPos = mc.player.getEyePos();
      double diffX = targetPos.x - eyesPos.x;
      double diffY = targetPos.y - eyesPos.y;
      double diffZ = targetPos.z - eyesPos.z;

      double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);

      float yaw = (float) (Math.atan2(diffZ, diffX) * (180.0 / Math.PI)) - 90.0F;
      float pitch = (float) -(Math.atan2(diffY, diffXZ) * (180.0 / Math.PI));

      // Smooth angle wrapping
      yaw = mc.player.getYaw() + MathHelper.wrapDegrees(yaw - mc.player.getYaw());
      pitch = MathHelper.clamp(pitch, -90.0F, 90.0F);

      return new float[]{yaw, pitch};
   }

   // Minecraft sensitivity GCD fix (Anti-cheat flag bachane ke liye)
   public static float applyGCD(float current, float previous) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.options == null) return current;
      
      float sensitivity = (float) mc.options.getMouseSensitivity().getValue();
      float f = sensitivity * 0.6F + 0.2F;
      float gcd = f * f * f * 1.2F;
      
      return current - (current - previous) % gcd;
   }
}
