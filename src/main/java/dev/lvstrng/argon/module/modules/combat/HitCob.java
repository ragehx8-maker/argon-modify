package dev.lvstrng.argon.module.modules.combat;

import dev.lvstrng.argon.event.events.AttackListener;
import dev.lvstrng.argon.module.Category;
import dev.lvstrng.argon.module.Module;
import dev.lvstrng.argon.module.setting.NumberSetting;
import dev.lvstrng.argon.utils.EncryptedString;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
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

import java.util.concurrent.ThreadLocalRandom;

public final class HitCob extends Module implements AttackListener {
	private final NumberSetting chance = new NumberSetting(EncryptedString.of("Chance"), 2, 100, 50, 1);
	private long lastPlaceTime = 0;

	public HitCob() {
		super(EncryptedString.of("Hit Cob"),
				EncryptedString.of("Automatically places cobweb at player feet silently"),
				-1,
				Category.COMBAT);

		addSettings(chance);
	}

	@Override
	public void onEnable() {
		eventManager.add(AttackListener.class, this);
		super.onEnable();
	}

	@Override
	public void onDisable() {
		eventManager.remove(AttackListener.class, this);
		super.onDisable();
	}

	@Override
	public void onAttack(AttackEvent event) {
		tryPlaceCobweb(event.entity);
	}

	// Tick method taaki TriggerBot ya normal targeting ke waqt bhi seamlessly kaam kare
	@Override
	public void onTick() {
		if (mc.player == null || mc.world == null)
			return;

		if (mc.targetedEntity instanceof PlayerEntity player && mc.player.isUsingItem() == false) {
			// Agar player left click kar raha hai ya targeted hai aur attack range mein hai
			if (mc.options.attackKey.isPressed()) {
				tryPlaceCobweb(player);
			}
		}
		super.onTick();
	}

	private void tryPlaceCobweb(Entity targetEntity) {
		if (mc.world == null || mc.player == null)
			return;

		// Cooldown check taaki spam hone par kick na ho (500ms delay)
		if (System.currentTimeMillis() - lastPlaceTime < 500)
			return;

		if (targetEntity instanceof PlayerEntity player) {
			if (ThreadLocalRandom.current().nextInt(100) >= chance.getValueInt())
				return;

			int cobwebSlot = getCobwebSlot();
			if (cobwebSlot == -1)
				return;

			BlockPos blockPos = player.getBlockPos();
			if (mc.world.getBlockState(blockPos).getBlock() == Blocks.COBWEB || mc.world.getBlockState(blockPos).getBlock() == Blocks.WATER)
				return;

			Vec3d playerPos = new Vec3d(player.getX(), player.getY(), player.getZ());
			Vec3d myPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());

			double distance = myPos.distanceTo(Vec3d.ofCenter(blockPos));
			if (distance > 4.5)
				return;

			BlockPos placePos = blockPos.down();
			if (!mc.world.getBlockState(placePos).isSolid())
				return;

			double placeDist = myPos.distanceTo(Vec3d.ofCenter(placePos));
			if (placeDist > 4.5)
				return;

			int oldSlot = mc.player.getInventory().getSelectedSlot();
			Vec3d vec3d3 = Vec3d.ofCenter(placePos);
			Vec3d vec3d4 = vec3d3.add(0.0, 0.5, 0.0);
			float[] rots = computeRotations(vec3d4);

			float originalYaw = mc.player.getYaw();
			float originalPitch = mc.player.getPitch();

			// Silent rotation packet bhejo
			mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(rots[0], rots[1], mc.player.isOnGround(), false));

			mc.player.getInventory().setSelectedSlot(cobwebSlot);
			BlockHitResult blockHitResult = new BlockHitResult(vec3d4, Direction.UP, placePos, false);
			if (mc.interactionManager != null) {
				mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, blockHitResult);
			}
			mc.player.getInventory().setSelectedSlot(oldSlot);

			// Original rotation wapas set karo
			mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(originalYaw, originalPitch, mc.player.isOnGround(), false));

			lastPlaceTime = System.currentTimeMillis();
		}
	}

	private int getCobwebSlot() {
		for (int i = 0; i < 9; i++) {
			ItemStack stack = mc.player.getInventory().getStack(i);
			if (!stack.isEmpty() && (stack.getItem() == Items.COBWEB)) {
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
