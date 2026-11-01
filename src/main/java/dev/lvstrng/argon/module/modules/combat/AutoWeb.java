package dev.lvstrng.argon.module.modules.combat;

import dev.lvstrng.argon.event.events.PacketSendListener;
import dev.lvstrng.argon.event.events.TickListener;
import dev.lvstrng.argon.imixin.IServerboundMovePlayerPacket;
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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.concurrent.ThreadLocalRandom;

public final class AutoWeb extends Module implements TickListener, PacketSendListener {

    private final NumberSetting chance = new NumberSetting(
            EncryptedString.of("Chance"), 10, 100, 100, 1);
    private final NumberSetting predictionTime = new NumberSetting(
            EncryptedString.of("Prediction Time"), 0.1, 2.0, 0.5, 0.1);
    private final NumberSetting aimSpeed = new NumberSetting(
            EncryptedString.of("Aim Speed"), 1.0, 50.0, 30.0, 1.0);
    private final BooleanSetting swapBack = new BooleanSetting(
            EncryptedString.of("SwapBack"), true);

    // State machine
    private int     stage        = 0;
    private int     tickDelay    = 0;
    private int     cobSlot      = -1;
    private int     oldSlot      = -1;
    private BlockHitResult pendingHit = null;

    // Silent rotation
    private boolean shouldInject = false;
    private float   injectYaw    = 0f;
    private float   injectPitch  = 0f;

    // Saved rotation
    private float   savedYaw     = 0f;
    private float   savedPitch   = 0f;
    private float   targetYaw    = 0f;
    private float   targetPitch  = 0f;

    public AutoWeb() {
        super(EncryptedString.of("Auto Web"),
                EncryptedString.of("Silent cobweb macro — press key once"),
                -1, Category.COMBAT);
        addSettings(chance, predictionTime, aimSpeed, swapBack);
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        eventManager.add(PacketSendListener.class, this);
        super.onEnable();

        // Macro: onEnable pe seedha start
        if (!init()) {
            // Conditions meet nahi hui — off ho jao
            disable();
        }
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        eventManager.remove(PacketSendListener.class, this);
        resetState();
        super.onDisable();
    }

    // ── SILENT ROTATION ────────────────────────────────────────────────────
    // PlayerMoveC2SPacket ko intercept karke yaw/pitch inject karte hain
    // mc.player.yaw/pitch kabhi nahi badlta — screen silent rehta hai
    @Override
    public void onPacketSend(PacketSendListener.PacketSendEvent event) {
        if (!shouldInject) return;
        if (!(event.getPacket() instanceof PlayerMoveC2SPacket)) return;

        IServerboundMovePlayerPacket packet =
                (IServerboundMovePlayerPacket) event.getPacket();
        packet.setYRot(injectYaw);
        packet.setXRot(injectPitch);
        packet.setHasRot(true);
    }

    // ── INIT — onEnable pe chalta hai ──────────────────────────────────────
    private boolean init() {
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null)
            return false;

        if (ThreadLocalRandom.current().nextInt(100) >= (int) chance.getValue())
            return false;

        PlayerEntity target = getTarget();
        if (target == null) return false;

        int slot = getCobwebSlot();
        if (slot == -1) return false;

        // Prediction
        double predTime = predictionTime.getValue();
        Vec3d vel = target.getVelocity();
        Vec3d predicted = new Vec3d(
                target.getX() + vel.x * predTime,
                target.getY() + vel.y * predTime,
                target.getZ() + vel.z * predTime);

        BlockPos targetBlock = BlockPos.ofFloored(predicted);

        // Block validity check
        if (mc.world.getBlockState(targetBlock).getBlock() == Blocks.COBWEB) return false;
        if (!mc.world.getBlockState(targetBlock).getFluidState().isEmpty()) return false;
        if (!mc.world.getBlockState(targetBlock).isAir()
                && !mc.world.getBlockState(targetBlock).isReplaceable()) return false;

        BlockPos placeOn = targetBlock.down();
        // Fallback agar neeche solid nahi
        if (mc.world.getBlockState(placeOn).isAir()) {
            placeOn = target.getBlockPos().down();
            targetBlock = placeOn.up();
        }
        if (mc.world.getBlockState(placeOn).isAir()) return false;

        // Reach check
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double offX = (rng.nextDouble() * 0.4) - 0.2;
        double offZ = (rng.nextDouble() * 0.4) - 0.2;
        Vec3d hitVec = new Vec3d(
                placeOn.getX() + 0.5 + offX,
                placeOn.getY() + 1.0,
                placeOn.getZ() + 0.5 + offZ);

        if (mc.player.getEyePos().distanceTo(hitVec) > 4.5) return false;

        // Store everything
        pendingHit = new BlockHitResult(hitVec, Direction.UP, placeOn, false);
        cobSlot    = slot;
        oldSlot    = mc.player.getInventory().getSelectedSlot();
        savedYaw   = mc.player.getYaw();
        savedPitch = mc.player.getPitch();

        float[] rots = computeRotations(hitVec);
        targetYaw   = rots[0];
        targetPitch = rots[1];

        // Aim speed se smooth rotation
        float maxDelta = (float) (aimSpeed.getValue() * 3.5);
        float yawDiff  = MathHelper.wrapDegrees(targetYaw - savedYaw);
        float pitchDiff = targetPitch - savedPitch;
        injectYaw   = savedYaw   + MathHelper.clamp(yawDiff,   -maxDelta, maxDelta);
        injectPitch = MathHelper.clamp(
                savedPitch + MathHelper.clamp(pitchDiff, -maxDelta, maxDelta),
                -89f, 89f);

        shouldInject = true;
        stage = 1;
        tickDelay = 1;
        return true;
    }

    // ── TICK STATE MACHINE ─────────────────────────────────────────────────
    @Override
    public void onTick() {
        if (stage == 0) return;
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) {
            disable(); return;
        }
        if (tickDelay > 0) { tickDelay--; return; }

        switch (stage) {

            // Stage 1 — rotation inject ho rahi hai (1 tick)
            case 1 -> {
                // Rotation already inject ho rahi hai via PacketSendListener
                // Cobweb slot select karo
                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(cobSlot));
                stage = 2;
                tickDelay = 1;
            }

            // Stage 2 — cobweb place karo
            case 2 -> {
                // Direct server packet — client raycast bypass
                mc.getNetworkHandler().sendPacket(
                        new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, pendingHit, 0));
                mc.player.swingHand(Hand.MAIN_HAND);
                stage = 3;
                tickDelay = 1;
            }

            // Stage 3 — restore slot + rotation, disable
            case 3 -> {
                if (swapBack.getValue()) {
                    mc.getNetworkHandler().sendPacket(
                            new UpdateSelectedSlotC2SPacket(oldSlot));
                    mc.player.getInventory().setSelectedSlot(oldSlot);
                }
                // Rotation restore — ek aur packet
                injectYaw   = savedYaw;
                injectPitch = savedPitch;
                shouldInject = false;
                // Macro: kaam khatam — off ho jao
                disable();
            }
        }
    }

    // ── HELPERS ────────────────────────────────────────────────────────────

    private PlayerEntity getTarget() {
        // Pehle crosshair target
        if (mc.crosshairTarget != null
                && mc.crosshairTarget instanceof net.minecraft.util.hit.EntityHitResult ehr
                && ehr.getEntity() instanceof PlayerEntity p
                && p != mc.player && !p.isDead() && !p.isSpectator()) {
            return p;
        }
        // Fallback: nearest player
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
        stage        = 0;
        tickDelay    = 0;
        cobSlot      = -1;
        oldSlot      = -1;
        pendingHit   = null;
        shouldInject = false;
    }

    private void disable() {
        resetState();
        if (isEnabled()) toggle();
    }
            }
