package dev.lvstrng.argon.module.modules.combat;

import dev.lvstrng.argon.managers.CustomRotator;
import dev.lvstrng.argon.utils.RotationUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import java.util.concurrent.ThreadLocalRandom;

public class HitWeb {
   private static final MinecraftClient mc = MinecraftClient.getInstance();
   private static int previousSlot = -1;
   
   // Chance percentage (1.0 se 100.0 tak, e.g., 20.0 matlab 20% chances par hi lagega)
   public static double chance = 100.0; 

   public static void onTick() {
       if (mc.player == null || mc.world == null) return;

       PlayerEntity target = getNearestTarget();
       if (target != null) {
           // Chance check (agar random chance fail hua toh web nahi lagega)
           if (!checkChance()) return;

           Vec3d targetFeetPos = target.getPos();

           // 1. Silent Rotation activate karna (CustomRotator ke zariye)
           CustomRotator.faceTarget(targetFeetPos);

           // 2. Hotbar se Cobweb nikal kar place karna aur sword wapis lana
           placeCobwebAndSwitchBack(targetFeetPos);
       } else {
           CustomRotator.reset();
           resetSlot();
       }
   }

   // Random chance validator (Orchard jaisa)
   private static boolean checkChance() {
       if (chance >= 100.0) return true;
       if (chance <= 0.0) return false;
       double randomValue = ThreadLocalRandom.current().nextDouble(100.0);
       return randomValue < chance;
   }

   private static PlayerEntity getNearestTarget() {
       PlayerEntity closest = null;
       double minDistance = Double.MAX_VALUE;
       for (PlayerEntity player : mc.world.getPlayers()) {
           if (player == mc.player || player.isDead()) continue;
           double dist = mc.player.squaredDistanceTo(player);
           if (dist < minDistance && dist <= 16.0) { // Range 4 blocks (4^2 = 16)
               minDistance = dist;
               closest = player;
           }
       }
       return closest;
   }

   private static void placeCobwebAndSwitchBack(Vec3d pos) {
       if (mc.player == null) return;

       // Hotbar mein Cobweb dhoondhna
       int webSlot = findCobwebSlot();
       if (webSlot == -1) return; // Agar cobweb nahi hai toh kuch mat karo

       // Agar pehle se slot save nahi hai, toh current slot (sword) save kar lo
       if (previousSlot == -1) {
           previousSlot = mc.player.getInventory().selectedSlot;
       }

       // Cobweb wale slot par switch karo
       mc.player.getInventory().selectedSlot = webSlot;

       // Feet ke niche block place karne ki position
       BlockPos blockPos = new BlockPos((int)pos.x, (int)pos.y, (int)pos.z);
       // (Yahan aapka interaction packet ya block place logic execute hoga)

       // Kaam hote hi turant wapis sword (previousSlot) par switch ho jao
       if (previousSlot != -1) {
           mc.player.getInventory().selectedSlot = previousSlot;
           previousSlot = -1;
       }
       
       CustomRotator.reset();
   }

   private static int findCobwebSlot() {
       for (int i = 0; i < 9; i++) {
           ItemStack stack = mc.player.getInventory().getStack(i);
           if (stack.getItem() == Items.COBWEB) {
               return i;
           }
       }
       return -1;
   }

   private static void resetSlot() {
       if (previousSlot != -1 && mc.player != null) {
           mc.player.getInventory().selectedSlot = previousSlot;
           previousSlot = -1;
       }
   }
}
