package com.simibubi.create.content.curiosities.zapper;

import java.util.List;

import javax.annotation.Nonnull;

import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.AllTags.AllBlockTags;
import com.simibubi.create.CreateClient;
import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.utility.BlockHelper;
import com.simibubi.create.foundation.utility.Lang;
import com.simibubi.create.foundation.utility.NBTProcessors;
import com.simibubi.create.lib.extensions.ItemExtensions;
import com.simibubi.create.lib.item.EntitySwingListenerItem;
import com.simibubi.create.lib.utility.Constants.NBT;
import com.tterrag.registrate.fabric.EnvExecutor;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.fml.DistExecutor;


public abstract class ZapperItem extends Item implements EntitySwingListenerItem, ItemExtensions {

	public ZapperItem(Properties properties) {
		super(properties.stacksTo(1));
	}

	@Override
	@Environment(EnvType.CLIENT)
	public void appendHoverText(ItemStack stack, Level worldIn, List<Component> tooltip, TooltipFlag flagIn) {
		if (stack.hasTag() && stack.getTag()
			.contains("BlockUsed")) {
			String usedblock = NbtUtils.readBlockState(stack.getTag()
				.getCompound("BlockUsed"))
				.getBlock()
				.getDescriptionId();
			ItemDescription.add(tooltip,
				Lang.translate("terrainzapper.usingBlock",
					new TranslatableComponent(usedblock).withStyle(ChatFormatting.GRAY))
					.withStyle(ChatFormatting.DARK_GRAY));
		}
	}

	@Override
	public boolean create$shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
		boolean differentBlock = false;
		if (oldStack.hasTag() && newStack.hasTag() && oldStack.getTag()
			.contains("BlockUsed")
			&& newStack.getTag()
				.contains("BlockUsed"))
			differentBlock = NbtUtils.readBlockState(oldStack.getTag()
				.getCompound("BlockUsed")) != NbtUtils.readBlockState(
					newStack.getTag()
						.getCompound("BlockUsed"));
		return slotChanged || !isZapper(newStack) || differentBlock;
	}

	public boolean isZapper(ItemStack newStack) {
		return newStack.getItem() instanceof ZapperItem;
	}

	@Nonnull
	@Override
	public InteractionResult useOn(UseOnContext context) {
		// Shift -> open GUI
		if (context.getPlayer() != null && context.getPlayer()
			.isShiftKeyDown()) {
			if (context.getLevel().isClientSide) {
				EnvExecutor.runWhenOn(EnvType.CLIENT, () -> () -> {
					openHandgunGUI(context.getItemInHand(), context.getHand() == InteractionHand.OFF_HAND);
				});
				context.getPlayer()
					.getCooldowns()
					.addCooldown(context.getItemInHand()
						.getItem(), 10);
			}
			return InteractionResult.SUCCESS;
		}
		return super.useOn(context);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
		ItemStack item = player.getItemInHand(hand);
		CompoundTag nbt = item.getOrCreateTag();
		boolean mainHand = hand == InteractionHand.MAIN_HAND;

		// Shift -> Open GUI
		if (player.isShiftKeyDown()) {
			if (world.isClientSide) {
				EnvExecutor.runWhenOn(EnvType.CLIENT, () -> () -> {
					openHandgunGUI(item, hand == InteractionHand.OFF_HAND);
				});
				player.getCooldowns()
					.addCooldown(item.getItem(), 10);
			}
			return new InteractionResultHolder<>(InteractionResult.SUCCESS, item);
		}

		if (ShootableGadgetItemMethods.shouldSwap(player, item, hand, this::isZapper))
			return new InteractionResultHolder<>(InteractionResult.FAIL, item);

		// Check if can be used
		Component msg = validateUsage(item);
		if (msg != null) {
			AllSoundEvents.DENY.play(world, player, player.blockPosition());
			player.displayClientMessage(msg.plainCopy()
				.withStyle(ChatFormatting.RED), true);
			return new InteractionResultHolder<>(InteractionResult.FAIL, item);
		}

		BlockState stateToUse = Blocks.AIR.defaultBlockState();
		if (nbt.contains("BlockUsed"))
			stateToUse = NbtUtils.readBlockState(nbt.getCompound("BlockUsed"));
		stateToUse = BlockHelper.setZeroAge(stateToUse);
		CompoundTag data = null;
		if (AllBlockTags.SAFE_NBT.matches(stateToUse) && nbt.contains("BlockData", NBT.TAG_COMPOUND)) {
			data = nbt.getCompound("BlockData");
		}

		// Raytrace - Find the target
		Vec3 start = player.position()
			.add(0, player.getEyeHeight(), 0);
		Vec3 range = player.getLookAngle()
			.scale(getZappingRange(item));
		BlockHitResult raytrace = world
			.clip(new ClipContext(start, start.add(range), Block.OUTLINE, Fluid.NONE, player));
		BlockPos pos = raytrace.getBlockPos();
		BlockState stateReplaced = world.getBlockState(pos);

		// No target
		if (pos == null || stateReplaced.getBlock() == Blocks.AIR) {
			ShootableGadgetItemMethods.applyCooldown(player, item, hand, this::isZapper, getCooldownDelay(item));
			return new InteractionResultHolder<>(InteractionResult.SUCCESS, item);
		}

		// Find exact position of gun barrel for VFX
		Vec3 barrelPos = ShootableGadgetItemMethods.getGunBarrelVec(player, mainHand, new Vec3(.35f, -0.1f, 1));

		// Client side
		if (world.isClientSide) {
			CreateClient.ZAPPER_RENDER_HANDLER.dontAnimateItem(hand);
			return new InteractionResultHolder<>(InteractionResult.SUCCESS, item);
		}

		// Server side
		if (activate(world, player, item, stateToUse, raytrace, data)) {
			ShootableGadgetItemMethods.applyCooldown(player, item, hand, this::isZapper, getCooldownDelay(item));
			ShootableGadgetItemMethods.sendPackets(player,
				b -> new ZapperBeamPacket(barrelPos, raytrace.getLocation(), hand, b));
		}

		return new InteractionResultHolder<>(InteractionResult.SUCCESS, item);
	}

	public Component validateUsage(ItemStack item) {
		CompoundTag tag = item.getOrCreateTag();
		if (!canActivateWithoutSelectedBlock(item) && !tag.contains("BlockUsed"))
			return Lang.createTranslationTextComponent("terrainzapper.leftClickToSet");
		return null;
	}

	protected abstract boolean activate(Level world, Player player, ItemStack item, BlockState stateToUse,
		BlockHitResult raytrace, CompoundTag data);

	@Environment(EnvType.CLIENT)
	protected abstract void openHandgunGUI(ItemStack item, boolean b);

	protected abstract int getCooldownDelay(ItemStack item);

	protected abstract int getZappingRange(ItemStack stack);

	protected boolean canActivateWithoutSelectedBlock(ItemStack stack) {
		return false;
	}

	@Override
	public boolean onEntitySwing(ItemStack stack, LivingEntity entity) {
		return true;
	}

	@Override
	public boolean canAttackBlock(BlockState state, Level worldIn, BlockPos pos, Player player) {
		return false;
	}

	@Override
	public UseAnim getUseAnimation(ItemStack stack) {
		return UseAnim.NONE;
	}

	public static void setTileData(Level world, BlockPos pos, BlockState state, CompoundTag data, Player player) {
		if (data != null && AllBlockTags.SAFE_NBT.matches(state)) {
			BlockEntity tile = world.getBlockEntity(pos);
			if (tile != null) {
				data = NBTProcessors.process(tile, data, !player.isCreative());
				if (data == null)
					return;
				data.putInt("x", pos.getX());
				data.putInt("y", pos.getY());
				data.putInt("z", pos.getZ());
				tile.load(state, data);
			}
		}
	}

}
