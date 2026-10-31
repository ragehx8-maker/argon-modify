package dev.lvstrng.argon.module.modules.combat;

import dev.lvstrng.argon.event.events.AttackListener;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.utils.EncryptedString;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.lang.reflect.Constructor;
import java.util.concurrent.ThreadLocalRandom;

public final class HitCob extends Module implements AttackListener {

    private final NumberSetting chance  = new NumberSetting(EncryptedString.of("Chance"),  2,   100, 35,  1);
    private final NumberSetting reach   = new NumberSetting(EncryptedString.of("Reach"),   2.8, 3.8, 3.0, 0.05);
    private final NumberSetting delay   = new NumberSetting(EncryptedString.of("Delay"),   200, 800, 350, 10);
    private final NumberSetting predict = new NumberSetting(EncryptedString.of("Predict"), 0,   4,   2,   1);

    private boolean pending        = false;
    private int     stage          = 0;
    private int     tickDelay      = 0;
    private int     cobSlot        = -1;
    private int     oldSlot        = -1;
    private BlockHitResult pendingHit = null;
    private float   targetYaw      = 0f;
    private float   targetPitch    = 0f;
    private float   savedYaw       = 0f;
    private float   savedPitch     = 0f;
    private float   midYaw         = 0f;
    private float   midPitch       = 0f;
    private float   jitterYaw      = 0f;
    private float   jitterPitch    = 0f;
    private long    lastPlaceTime  = 0L;
    private boolean tickRegistered = false;

    public HitCob() {
        super(EncryptedString.of("Hit Cob"),
              EncryptedString.of("Silent cobweb at player feet"),
              -1, Category.COMBAT);
        addSettings(chance, reach, delay, predict);
    }

    @Override
    public void onEnable() {
        mc.eventManager.register(AttackListener.class, this);
        if (!tickRegistered) {
            ClientTickEvents.END_CLIENT_TICK.register(client -> {
                if (isEnabled()) onTick();
            });
            tickRegistered = true;
        }
        super.onEnable();
    }

    @Override
    public void onDisable() {
        mc.eventManager.unregister(AttackListener.class, this);
        resetState();
        super.onDisable();
    }

    @Override
    public void onAttack(AttackListener.AttackEvent event) {
        if (!isEnabled()) return;
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) return;
        if (pending) return;

        long baseDelay  = delay.getValue().longValue();
        long noiseRange = (long) (baseDelay * 0.15);
        long effectiveDelay = baseDelay
                + ThreadLocalRandom.current().nextLong(-noiseRange, noiseRange + 1);
        if (System.currentTimeMillis() - lastPlaceTime < effectiveDelay) return;

        if (!(mc.targetedEntity instanceof PlayerEntity target)) return;
        if (target == mc.player) return;
        if (target.isDead() || target.isSpectator()) return;

        if (ThreadLocalRandom.current().nextInt(100) >= chance.getValue().intValue()) return;

        int slot = getCobwebSlot();
        if (slot == -1) return;

        BlockPos feet = resolveTargetBlock(target);
        if (mc.world.getBlockState(feet).getBlock() == Blocks.COBWEB) return;
        if (!mc.world.getBlockState(feet).getFluidState().isEmpty()) return;
        if (!mc.world.getBlockState(feet).isAir()
                && !mc.world.getBlockState(feet).getMaterial().isReplaceable()) return;

        BlockPos placeOn = feet.down();
        if (mc.world.getBlockState(placeOn).isAir()) return;

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double offX = (rng.nextDouble() * 0.6) - 0.3;
        double offZ = (rng.nextDouble() * 0.6) - 0.3;
        Vec3d hitVec = new Vec3d(
                placeOn.getX() + 0.5 + offX,
                placeOn.getY() + 1.0,
                placeOn.getZ() + 0.5 + offZ);

        if (mc.player.getEyePos().distanceTo(hitVec) > reach.getValue().doubleValue()) return;

        pendingHit  = new BlockHitResult(hitVec, Direction.UP, placeOn, false);
        cobSlot     = slot;
        oldSlot     = mc.player.getInventory().getSelectedSlot();
        savedYaw    = mc.player.getYaw();
        savedPitch  = mc.player.getPitch();

        float[] rots = computeRotations(hitVec);
        targetYaw   = rots[0];
        targetPitch = rots[1];

        jitterYaw   = (rng.nextFloat() * 3f) - 1.5f;
        jitterPitch = (rng.nextFloat() * 2f) - 1.0f;

        midYaw   = savedYaw   + lerpAngle(savedYaw,   targetYaw,   0.5f);
        midPitch = savedPitch + (targetPitch - savedPitch) * 0.5f;

        double vx = target.getVelocity().x;
        double vz = target.getVelocity().z;
        double horizSpeed = Math.sqrt(vx * vx + vz * vz);

        pending   = true;
        stage     = 1;
        tickDelay = (horizSpeed < 0.1 ? 1 : 2) + rng.nextInt(2);
    }

    private BlockPos resolveTargetBlock(PlayerEntity player) {
        int steps = predict.getValue().intValue();
        if (steps == 0) return player.getBlockPos();

        double vx = player.getVelocity().x;
        double vy = player.getVelocity().y;
        double vz = player.getVelocity().z;
        if (Math.sqrt(vx * vx + vz * vz) < 0.08) return player.getBlockPos();

        double x = player.getX(), y = player.getY(), z = player.getZ();
        BlockPos last = player.getBlockPos();

        for (int i = 0; i < steps; i++) {
            vy = (vy - 0.08) * 0.98;
            vx *= 0.91;
            vz *= 0.91;
            x += vx; y += vy; z += vz;
            BlockPos next = BlockPos.ofFloored(x, y, z);
            if (mc.world != null) {
                net.minecraft.block.BlockState s = mc.world.getBlockState(next);
                if (!s.isAir() && !s.getMaterial().isReplaceable()) break;
            }
            last = next;
        }
        return last;
    }

    private void onTick() {
        if (!pending) return;
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) {
            resetState(); return;
        }
        if (tickDelay > 0) { tickDelay--; return; }

        ThreadLocalRandom rng = ThreadLocalRandom.current();

        switch (stage) {
            case 1 -> {
                float y1 = savedYaw   + lerpAngle(savedYaw,   targetYaw,   0.30f);
                float p1 = savedPitch + (targetPitch - savedPitch) * 0.30f;
                applyRotation(y1, p1);
                stage = 2; tickDelay = 1;
            }
            case 2 -> {
                applyRotation(midYaw, midPitch);
                stage = 3; tickDelay = 1;
            }
            case 3 -> {
                applyRotation(
                        MathHelper.wrapDegrees(targetYaw   + jitterYaw),
                        MathHelper.clamp(targetPitch + jitterPitch, -89f, 89f));
                sendSlot(cobSlot);
                stage = 4; tickDelay = 1;
            }
            case 4 -> {
                mc.getNetworkHandler().sendPacket(
                        new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, pendingHit, 0));
                if (rng.nextInt(100) < 70) mc.player.swingHand(Hand.MAIN_HAND);
                stage = 5; tickDelay = 1;
            }
            case 5 -> {
                sendSlot(oldSlot);
                applyRotation(savedYaw, savedPitch);
                lastPlaceTime = System.currentTimeMillis();
                resetState();
            }
        }
    }

    private void resetState() {
        pending     = false;
        stage       = 0;
        tickDelay   = 0;
        cobSlot     = -1;
        oldSlot     = -1;
        pendingHit  = null;
        jitterYaw   = 0f;
        jitterPitch = 0f;
    }

    private void applyRotation(float yaw, float pitch) {
        mc.player.setYaw(yaw);
        mc.player.setPitch(pitch);
        mc.player.setBodyYaw(yaw);
        mc.player.setHeadYaw(yaw);
        mc.getNetworkHandler().sendPacket(
                new PlayerMoveC2SPacket.LookAndOnGround(
                        yaw, pitch,
                        mc.player.isOnGround(),
                        mc.player.horizontalCollision));
    }

    private void sendSlot(int slot) {
        try {
            Constructor<?>[] ctors = UpdateSelectedSlotC2SPacket.class.getDeclaredConstructors();
            Constructor<?> chosen = null;
            for (Constructor<?> c : ctors) {
                if (c.getParameterCount() == 1 || c.getParameterCount() == 2) {
                    chosen = c;
                    if (c.getParameterCount() == 1) break;
                }
            }
            if (chosen != null) {
                chosen.setAccessible(true);
                UpdateSelectedSlotC2SPacket pkt = chosen.getParameterCount() == 2
                        ? (UpdateSelectedSlotC2SPacket) chosen.newInstance(slot, 0)
                        : (UpdateSelectedSlotC2SPacket) chosen.newInstance(slot);
                mc.getNetworkHandler().sendPacket(pkt);
                return;
            }
        } catch (Exception ignored) {}
        mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
    }

    private float lerpAngle(float from, float to, float t) {
        return (((to - from + 540f) % 360f) - 180f) * t;
    }

    private int getCobwebSlot() {
        for (int i = 0; i < 9; i++)
            if (mc.player.getInventory().getStack(i).getItem() == Items.COBWEB) return i;
        return -1;
    }

    private float[] computeRotations(Vec3d vec) {
        Vec3d d    = vec.subtract(mc.player.getEyePos());
        double dist = Math.hypot(d.x, d.z);
        float yaw   = (float) Math.toDegrees(Math.atan2(d.z, d.x)) - 90f;
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, dist));
        return new float[]{
                MathHelper.wrapDegrees(yaw),
                MathHelper.clamp(pitch, -89f, 89f)};
    }
                }
