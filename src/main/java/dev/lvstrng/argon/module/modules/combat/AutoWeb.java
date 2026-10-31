package dev.lvstrng.argon.module.modules.combat;

import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.KeybindSetting;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.utils.EncryptedString;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.ThreadLocalRandom;

public final class AutoWeb extends Module {
    private final BooleanSetting onKeyMode = new BooleanSetting(EncryptedString.of("On Key Mode"), true);
    private final KeybindSetting activationKey = new KeybindSetting(EncryptedString.of("Activation Key"), -1, false);
    private final NumberSetting chance = new NumberSetting(EncryptedString.of("Chance"), 10, 100, 100, 1);
    private final NumberSetting predictionTime = new NumberSetting(EncryptedString.of("Prediction Time"), 0.1, 2.0, 0.5, 0.1);
    private final NumberSetting aimSpeed = new NumberSetting(EncryptedString.of("Aim Speed"), 1.0, 50.0, 30.0, 1.0);
    private final BooleanSetting silentRotation = new BooleanSetting(EncryptedString.of("Silent Rotation"), true);
    private final BooleanSetting swapBack = new BooleanSetting(EncryptedString.of("SwapBack"), true);

    private long lastPlaceTime = 0;
    private boolean wasPressed = false;

    public AutoWeb() {
        super(EncryptedString.of("Auto Web"),
                EncryptedString.of("Mochi style macro auto web with single press key trigger and smooth bypass"),
                -1,
                Category.COMBAT);

        addSettings(onKeyMode, activationKey, chance, predictionTime, aimSpeed, silentRotation, swapBack);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null)
            return;

        if (onKeyMode.getValue()) {
            if (activationKey.getKeyCode() != -1) {
                boolean isPressed = GLFW.glfwGetKey(mc.getWindow().getHandle(), activationKey.getKeyCode()) == GLFW.GLFW_PRESS;
                
                if (isPressed && !wasPressed) {
                    if (mc.targetedEntity instanceof PlayerEntity player) {
                        tryPlaceWeb(player);
                    }
                }
                wasPressed = isPressed;
            }
        } else {
            if (mc.targetedEntity instanceof PlayerEntity player && mc.options.attackKey.isPressed()) {
                tryPlaceWeb(player);
            }
        }
    }

    private void tryPlaceWeb(PlayerEntity player) {
        if (System.currentTimeMillis() - lastPlaceTime < 400)
            return;

        if (ThreadLocalRandom.current().nextInt(100) >= chance.getValueInt())
            return;

        int cobwebSlot = getCobwebSlot();
        if (cobwebSlot == -1)
            return;

        Vec3d velocity = player.getVelocity();
        double predTime = predictionTime.getValue();
        Vec3d playerPos = new Vec3d(player.getX(), player.getY(), player.getZ());
        Vec3d predictedPos = playerPos.add(velocity.x * predTime, velocity.y * predTime, velocity.z * predTime);
        
        BlockPos targetBlock = BlockPos.ofFloored(predictedPos);
        BlockPos placePos = targetBlock.down();

        if (!mc.world.getBlockState(placePos).isSolid()) {
            placePos = player.getBlockPos().down();
        }

        Vec3d myPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        double placeDist = myPos.distanceTo(Vec3d.ofCenter(placePos));
        if (placeDist > 4.5)
            return;

        int oldSlot = mc.player.getInventory().getSelectedSlot();
        Vec3d vec3d3 = Vec3d.ofCenter(placePos);
        Vec3d vec3d4 = vec3d3.add(0.0, 0.5, 0.0);
        float[] targetRots = computeRotations(vec3d4);

        float originalYaw = mc.player.getYaw();
        float originalPitch = mc.player.getPitch();

        float yawDiff = MathHelper.wrapDegrees(targetRots[0] - originalYaw);
        float pitchDiff = targetRots[1] - originalPitch;
        
        double maxDelta = aimSpeed.getValue() * 3.5; 
        float smoothedYaw = originalYaw + MathHelper.clamp(yawDiff, (float)-maxDelta, (float)maxDelta);
        float smoothedPitch = MathHelper.clamp(originalPitch + MathHelper.clamp(pitchDiff, (float)-maxDelta, (float)maxDelta), -89.0F, 89.0F);

        if (silentRotation.getValue()) {
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(smoothedYaw, smoothedPitch, mc.player.isOnGround(), false));
        }

        mc.player.getInventory().setSelectedSlot(cobwebSlot);
        BlockHitResult blockHitResult = new BlockHitResult(vec3d4, Direction.UP, placePos, false);
        if (mc.interactionManager != null) {
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, blockHitResult);
        }

        if (swapBack.getValue()) {
            mc.player.getInventory().setSelectedSlot(oldSlot);
        }

        if (silentRotation.getValue()) {
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(originalYaw, originalPitch, mc.player.isOnGround(), false));
        }

        lastPlaceTime = System.currentTimeMillis();
    }

    private int getCobwebSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() == Items.COBWEB) {
                return i;
            }
        }
        return -1;
    }

    private float[] computeRotations(Vec3d vec3d) {
        Vec3d diff = vec3d.subtract(mc.player.getEyePos());
        double dist = Math.hypot(diff.x, diff.z);
        float yaw = (float) Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0F;
        float pitch = (float) (-Math.toDegrees(Math.atan2(diff.y, dist)));
        return new float[]{MathHelper.wrapDegrees(yaw), MathHelper.clamp(pitch, -89.0F, 89.0F)};
    }
}
