package dev.lvstrng.argon.module.modules.combat;

import dev.lvstrng.argon.managers.CustomRotator;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.Category;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import java.util.concurrent.ThreadLocalRandom;

public class HitWeb extends Module {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static int previousSlot = -1;
    
    // Chance percentage (1.0 se 100.0 tak)
    public static double chance = 100.0; 

    public HitWeb() {
        // Aapke client ke base Module constructor ke mutabiq arguments order
        super("HitWeb", Category.COMBAT);
    }

    public static void onTick() {
        if (mc.player == null || mc.world == null) return;

        PlayerEntity target = getNearestTarget();
        if (target != null) {
            if (!checkChance()) return;

            Vec3d targetFeetPos = new Vec3d(target.getX(), target.getY(), target.getZ());

            CustomRotator.faceTarget(targetFeetPos);
            placeCobwebAndSwitchBack(targetFeetPos);
        } else {
            CustomRotator.reset();
            resetSlot();
        }
    }

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
            if (dist < minDistance && dist <= 16.0) {
                minDistance = dist;
                closest = player;
            }
        }
        return closest;
    }

    private static void placeCobwebAndSwitchBack(Vec3d pos) {
        if (mc.player == null) return;

        int webSlot = findCobwebSlot();
        if (webSlot == -1) return;

        if (previousSlot == -1) {
            previousSlot = mc.player.getInventory().selectedSlot;
        }

        // Inventory slot set karne ke liye standard method ya assignment
        mc.player.getInventory().selectedSlot = webSlot;

        BlockPos blockPos = new BlockPos((int)pos.x, (int)pos.y, (int)pos.z);

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
