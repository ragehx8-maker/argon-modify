package dev.lvstrng.argon.module.modules.combat;

import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.utils.EncryptedString;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.concurrent.ThreadLocalRandom;

public final class AutoWeb extends Module {
    private final NumberSetting chance = new NumberSetting(EncryptedString.of("Chance"), 10, 100, 100, 1);
    private final NumberSetting predictionTime = new NumberSetting(EncryptedString.of("Prediction Time"), 0.1, 2.0, 0.5, 0.1);
    private final NumberSetting aimSpeed = new NumberSetting(EncryptedString.of("Aim Speed"), 1.0, 50.0, 30.0, 1.0);
    private final BooleanSetting swapBack = new BooleanSetting(EncryptedString.of("SwapBack"), true);

    public AutoWeb() {
        super(EncryptedString.of("Auto Web"),
                EncryptedString.of("Silent client-side view with visible enemy head rotation macro"),
                -1,
                Category.COMBAT);

        addSettings(chance, predictionTime, aimSpeed, swapBack);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        tryPlaceWeb();
        toggle(); // Macro style: ek baar key dabane par chal kar auto-off ho jayega
    }

    @Override
    public void onDisable() {
        super.onDisable();
    }

    private void tryPlaceWeb() {
        if (mc.player == null || mc.world == null)
            return;

        PlayerEntity target = getTarget();
        if (target == null)
            return;

        if (ThreadLocalRandom.current().nextInt(100) >= chance.getValueInt())
            return;

        int cobwebSlot = getCobwebSlot();
        if (cobwebSlot == -1)
            return;

        Vec3d velocity = target.getVelocity();
        double predTime = predictionTime.getValue();
        Vec3d playerPos = new Vec3d(target.getX(), target.getY(), target.getZ());
        Vec3d predictedPos = playerPos.add(velocity.x * predTime, velocity.y * predTime, velocity.z * predTime);
        
        BlockPos targetBlock = BlockPos.ofFloored(predictedPos);
        BlockPos placePos = targetBlock.down();

        if (!mc.world.getBlockState(placePos).isSolid()) {
            placePos = target.getBlockPos().down();
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
        
        float maxDelta = (float) (aimSpeed.getValue() * 3.5); 
        float smoothedYaw = originalYaw + MathHelper.clamp(yawDiff, -maxDelta, maxDelta);
        float smoothedPitch = MathHelper.clamp(originalPitch + MathHelper.clamp(pitchDiff, -maxDelta, maxDelta), -89.0F, 89.0F);

        // 1. Temporarily set player rotation for successful raycast interaction
        mc.player.setYaw(smoothedYaw);
        mc.player.setPitch(smoothedPitch);
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(smoothedYaw, smoothedPitch, mc.player.isOnGround(), false));

        // 2. Select cobweb slot and notify server via packet
        mc.player.getInventory().setSelectedSlot(cobwebSlot);
        mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(cobwebSlot));

        // 3. Place the cobweb block & swing hand so server accepts the placement
        BlockHitResult blockHitResult = new BlockHitResult(vec3d4, Direction.UP, placePos, false);
        if (mc.interactionManager != null) {
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, blockHitResult);
        }
        mc.player.swingHand(Hand.MAIN_HAND);

        // 4. Swap back to old slot and notify server
        if (swapBack.getValue()) {
            mc.player.getInventory().setSelectedSlot(oldSlot);
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(oldSlot));
        }

        // 5. Immediately restore client rotation so your screen remains silent
        mc.player.setYaw(originalYaw);
        mc.player.setPitch(originalPitch);
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(originalYaw, originalPitch, mc.player.isOnGround(), false));
    }

    private PlayerEntity getTarget() {
        if (mc.targetedEntity instanceof PlayerEntity player) {
            return player;
        }
        PlayerEntity closest = null;
        double minDist = 5.0;
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;
            double dist = mc.player.distanceTo(player);
            if (dist < minDist) {
                minDist = dist;
                closest = player;
            }
        }
        return closest;
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
