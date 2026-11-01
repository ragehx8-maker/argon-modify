package dev.lvstrng.argon.module.modules.combat;

import dev.lvstrng.argon.event.events.TickListener;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.BooleanSetting;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.utils.EncryptedString;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.concurrent.ThreadLocalRandom;

public final class AutoWeb extends Module implements TickListener {

    private final NumberSetting chance = new NumberSetting(
            EncryptedString.of("Chance"), 10, 100, 100, 1);
    private final NumberSetting predictionTime = new NumberSetting(
            EncryptedString.of("Prediction Time"), 0.1, 2.0, 0.5, 0.1);
    private final NumberSetting aimSpeed = new NumberSetting(
            EncryptedString.of("Aim Speed"), 1.0, 50.0, 30.0, 1.0);
    private final BooleanSetting swapBack = new BooleanSetting(
            EncryptedString.of("SwapBack"), true);
    private final NumberSetting cooldown = new NumberSetting(
            EncryptedString.of("Cooldown"), 5, 40, 10, 1);

    private int   stage       = 0;
    private int   tickDelay   = 0;
    private int   cooldownTicks = 0;
    private int   cobSlot     = -1;
    private int   oldSlot     = -1;
    private BlockHitResult pendingHit  = null;
    private float savedYaw    = 0f;
    private float savedPitch  = 0f;
    private float targetYaw   = 0f;
    private float targetPitch = 0f;

    public AutoWeb() {
        super(EncryptedString.of("Auto Web"),
                EncryptedString.of("Silent cobweb macro"),
                -1, Category.COMBAT);
        addSettings(chance, predictionTime, aimSpeed, swapBack, cooldown);
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        resetState();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        // Rotation restore karo agar mid-execution disable hua
        if (stage > 0 && mc.player != null && mc.getNetworkHandler() != null) {
            sendRotationPacket(savedYaw, savedPitch);
            if (swapBack.getValue() && oldSlot != -1) {
                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(oldSlot));
                mc.player.getInventory().setSelectedSlot(oldSlot);
            }
        }
        resetState();
        super.onDisable();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null)
            return;

        // Cooldown countdown
        if (cooldownTicks > 0) {
            cooldownTicks--;
            return;
        }

        // Stage 0 — try start
        if (stage == 0) {
            tryInit();
            return;
        }

        if (tickDelay > 0) { tickDelay--; return; }

        switch (stage) {

            // Stage 1 — rotation packet bhejo
            case 1 -> {
                sendRotationPacket(targetYaw, targetPitch);
                stage = 2;
                tickDelay = 1; // 1 tick wait — server rotation process kare
            }

            // Stage 2 — slot switch + rotation confirm
            case 2 -> {
                sendRotationPacket(targetYaw, targetPitch); // dobara bhejo confirm ke liye
                mc.getNetworkHandler().sendPacket(
                        new UpdateSelectedSlotC2SPacket(cobSlot));
                stage = 3;
                tickDelay = 1;
            }

            // Stage 3 — place karo
            case 3 -> {
                sendRotationPacket(targetYaw, targetPitch); // ensure rotation sahi hai
                mc.getNetworkHandler().sendPacket(
                        new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, pendingHit, 0));
                if (ThreadLocalRandom.current().nextInt(100) < 70)
                    mc.player.swingHand(Hand.MAIN_HAND);
                stage = 4;
                tickDelay = 1;
            }

            // Stage 4 — restore
            case 4 -> {
                if (swapBack.getValue()) {
                    mc.getNetworkHandler().sendPacket(
                            new UpdateSelectedSlotC2SPacket(oldSlot));
                    mc.player.getInventory().setSelectedSlot(oldSlot);
                }
                sendRotationPacket(savedYaw, savedPitch);
                cooldownTicks = (int) cooldown.getValue();
                resetState();
                // Auto-off nahi — module ON rehta hai, cooldown ke baad phir try karega
            }
        }
    }

    private void tryInit() {
        if (ThreadLocalRandom.current().nextInt(100) >= (int) chance.getValue())
            return;

        PlayerEntity target = getTarget();
        if (target == null) return;

        int slot = getCobwebSlot();
        if (slot == -1) return;

        double predTime = predictionTime.getValue();
        Vec3d vel = target.getVelocity();
        Vec3d predicted = new Vec3d(
                target.getX() + vel.x * predTime,
                target.getY() + vel.y * predTime,
                target.getZ() + vel.z * predTime);

        BlockPos targetBlock = BlockPos.ofFloored(predicted);

        // Block validity
        if (mc.world.getBlockState(targetBlock).getBlock() == Blocks.COBWEB) return;
        if (!mc.world.getBlockState(targetBlock).getFluidState().isEmpty()) return;
        if (!mc.world.getBlockState(targetBlock).isAir()
                && !mc.world.getBlockState(targetBlock).isReplaceable()) return;

        BlockPos placeOn = targetBlock.down();
        if (mc.world.getBlockState(placeOn).isAir()) {
            placeOn = target.getBlockPos().down();
            targetBlock = placeOn.up();
        }
        if (mc.world.getBlockState(placeOn).isAir()) return;

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        Vec3d hitVec = new Vec3d(
                placeOn.getX() + 0.5 + (rng.nextDouble() * 0.4 - 0.2),
                placeOn.getY() + 1.0,
                placeOn.getZ() + 0.5 + (rng.nextDouble() * 0.4 - 0.2));

        if (mc.player.getEyePos().distanceTo(hitVec) > 4.5) return;

        pendingHit  = new BlockHitResult(hitVec, Direction.UP, placeOn, false);
        cobSlot     = slot;
        oldSlot     = mc.player.getInventory().getSelectedSlot();
        savedYaw    = mc.player.getYaw();
        savedPitch  = mc.player.getPitch();

        float[] rots = computeRotations(hitVec);
        float maxDelta = (float) (aimSpeed.getValue() * 3.5);
        targetYaw   = savedYaw + MathHelper.clamp(
                MathHelper.wrapDegrees(rots[0] - savedYaw), -maxDelta, maxDelta);
        targetPitch = MathHelper.clamp(
                savedPitch + MathHelper.clamp(rots[1] - savedPitch, -maxDelta, maxDelta),
                -89f, 89f);

        stage     = 1;
        tickDelay = 1;
    }

    /**
     * Server ko rotation packet bhejta hai.
     * setYaw/setPitch NAHI karte — screen nahi hilega.
     * setBodyYaw/setHeadYaw bhi NAHI — stutter nahi aayega.
     * Sirf server ko packet jaata hai.
     */
    private void sendRotationPacket(float yaw, float pitch) {
        mc.getNetworkHandler().sendPacket(
                new PlayerMoveC2SPacket.LookAndOnGround(
                        yaw, pitch,
                        mc.player.isOnGround(),
                        mc.player.horizontalCollision));
    }

    private PlayerEntity getTarget() {
        if (mc.crosshairTarget instanceof EntityHitResult ehr
                && ehr.getEntity() instanceof PlayerEntity p
                && p != mc.player && !p.isDead() && !p.isSpectator()) {
            return p;
        }
        PlayerEntity closest = null;
        double minDist = 5.0;
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || player.isDead() || player.isSpectator()) continue;
            double dist = mc.player.distanceTo(player);
            if (dist < minDist) { minDist = dist; closest = player; }
        }
        return closest;
    }

    private int getCobwebSlot() {
        for (int i = 0; i < 9; i++)
            if (mc.player.getInventory().getStack(i).getItem() == Items.COBWEB) return i;
        return -1;
    }

    private float[] computeRotations(Vec3d vec) {
        Vec3d d     = vec.subtract(mc.player.getEyePos());
        double dist = Math.hypot(d.x, d.z);
        float yaw   = (float) Math.toDegrees(Math.atan2(d.z, d.x)) - 90f;
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, dist));
        return new float[]{
                MathHelper.wrapDegrees(yaw),
                MathHelper.clamp(pitch, -89f, 89f)};
    }

    private void resetState() {
        stage      = 0;
        tickDelay  = 0;
        cobSlot    = -1;
        oldSlot    = -1;
        pendingHit = null;
    }
            }
