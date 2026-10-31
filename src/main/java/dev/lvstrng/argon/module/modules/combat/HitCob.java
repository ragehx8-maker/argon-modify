package dev.lvstrng.argon.module.modules.combat;

import dev.lvstrng.argon.event.events.AttackListener;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.utils.EncryptedString;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
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

    private final NumberSetting chance = new NumberSetting(EncryptedString.of("Chance"), 2, 100, 35, 1);
    private final NumberSetting reach = new NumberSetting(EncryptedString.of("Reach"), 3.0, 4.0, 3.1, 0.05);
    private final NumberSetting delay = new NumberSetting(EncryptedString.of("Delay"), 100, 600, 300, 10);

    private BlockPos pendingPlace = null;
    private BlockHitResult pendingHit = null;
    private boolean pending = false;
    private int stage = 0;
    private int tickDelay = 0;
    private int cobSlot = -1;
    private int oldSlot = -1;
    private float targetYaw, targetPitch, startYaw, startPitch;
    private float savedYaw, savedPitch, savedBody, savedHead;
    private long lastTime = 0L;
    private boolean listenerRegistered = false;

    public HitCob() {
        super(EncryptedString.of("Hit Cob"),
                EncryptedString.of("Silent cobweb at player feet"),
                -1,
                Category.COMBAT);
        addSettings(chance, reach, delay);
    }

    @Override
    public void onEnable() {
        eventManager.add(AttackListener.class, this);
        if (!listenerRegistered) {
            ClientTickEvents.END_CLIENT_TICK.register(client -> {
                if (this.isEnabled()) onTick();
            });
            listenerRegistered = true;
        }
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(AttackListener.class, this);
        stage = 0;
        pending = false;
        pendingPlace = null;
        pendingHit = null;
        super.onDisable();
    }

    @Override
    public void onAttack(AttackEvent event) {
        if (!this.isEnabled()) return;
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) return;
        if (pending) return;
        if (System.currentTimeMillis() - lastTime < delay.getValue()) return;

        if (!(mc.targetedEntity instanceof PlayerEntity player)) return;
        if (player == mc.player || player.isDead() || player.isSpectator()) return;
        if (ThreadLocalRandom.current().nextInt(100) >= chance.getValueInt()) return;

        int slot = getCobwebSlot();
        if (slot == -1) return;

        BlockPos feet = player.getBlockPos();
        if (mc.world.getBlockState(feet).getBlock() == Blocks.COBWEB) return;
        if (mc.world.getBlockState(feet).getBlock() == Blocks.WATER) return;

        BlockPos placePos = feet.down();
        if (mc.world.getBlockState(placePos).isAir()) return;
        if (!mc.world.getBlockState(feet).isAir() && !mc.world.getBlockState(feet).isReplaceable()) return;

        Vec3d hitVec = Vec3d.ofCenter(placePos).add(0.0, 0.5, 0.0);
        if (mc.player.getEyePos().distanceTo(hitVec) > reach.getValue()) return;

        pendingPlace = placePos;
        pendingHit = new BlockHitResult(hitVec, Direction.UP, placePos, false);
        cobSlot = slot;
        oldSlot = mc.player.getInventory().getSelectedSlot();

        float[] rot = computeRotations(hitVec);
        targetYaw = rot[0];
        targetPitch = rot[1];
        startYaw = savedYaw = mc.player.getYaw();
        startPitch = savedPitch = mc.player.getPitch();
        savedBody = mc.player.bodyYaw;
        savedHead = mc.player.headYaw;

        pending = true;
        stage = 1;
        tickDelay = 1;
        lastTime = System.currentTimeMillis();
    }

    private void onTick() {
        if (!this.isEnabled()) return;
        if (!pending || mc.player == null || mc.getNetworkHandler() == null) return;

        if (tickDelay > 0) {
            tickDelay--;
            return;
        }

        switch (stage) {
            case 1 -> {
                float y = lerpAngle(startYaw, targetYaw, 0.45f);
                float p = startPitch + (targetPitch - startPitch) * 0.45f;
                applyRotation(y, p);
                stage = 2;
                tickDelay = 1;
            }
            case 2 -> {
                applyRotation(targetYaw, targetPitch);
                sendSlot(cobSlot);
                stage = 3;
                tickDelay = 1;
            }
            case 3 -> {
                mc.getNetworkHandler().sendPacket(
                        new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, pendingHit, 0));
                mc.player.swingHand(Hand.MAIN_HAND);
                stage = 4;
                tickDelay = 1;
            }
            case 4 -> {
                if (oldSlot >= 0) sendSlot(oldSlot);
                applyRotation(savedYaw, savedPitch);
                stage = 0;
                pending = false;
                pendingPlace = null;
                pendingHit = null;
            }
            default -> {
                stage = 0;
                pending = false;
            }
        }
    }

    private void applyRotation(float yaw, float pitch) {
        mc.player.setYaw(yaw);
        mc.player.setPitch(pitch);
        mc.player.bodyYaw = yaw;
        mc.player.headYaw = yaw;
        mc.player.prevYaw = yaw;
        mc.player.prevPitch = pitch;
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
                yaw, pitch, mc.player.isOnGround(), mc.player.horizontalCollision));
    }

    private void sendSlot(int slot) {
        try {
            Constructor<?> c = UpdateSelectedSlotC2SPacket.class.getConstructors()[0];
            Object pkt = c.getParameterCount() == 2
                    ? c.newInstance(slot, true)
                    : c.newInstance(slot);
            mc.getNetworkHandler().sendPacket((UpdateSelectedSlotC2SPacket) pkt);
        } catch (Exception e) {
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        }
    }

    private float lerpAngle(float from, float to, float t) {
        float diff = ((to - from + 540.0f) % 360.0f) - 180.0f;
        return from + diff * t;
    }

    private int getCobwebSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() == Items.COBWEB) return i;
        }
        return -1;
    }

    private float[] computeRotations(Vec3d vec) {
        Vec3d diff = vec.subtract(mc.player.getEyePos());
        double dist = Math.hypot(diff.x, diff.z);
        float yaw = (float) Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0f;
        float pitch = (float) (-Math.toDegrees(Math.atan2(diff.y, dist)));
        return new float[]{MathHelper.wrapDegrees(yaw), MathHelper.clamp(pitch, -89.0f, 89.0f)};
    }
}
