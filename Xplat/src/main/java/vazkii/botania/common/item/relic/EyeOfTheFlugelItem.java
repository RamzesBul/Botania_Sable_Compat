/*
 * This class is distributed as part of the Botania Mod.
 * Get the Source Code in github:
 * https://github.com/Vazkii/Botania
 *
 * Botania is Open Source and distributed under the
 * Botania License: http://botaniamod.net/license.php
 */
package vazkii.botania.common.item.relic;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

import vazkii.botania.api.compat.Sable.SableCompat;
import vazkii.botania.api.item.CoordBoundItem;
import vazkii.botania.api.item.Relic;
import vazkii.botania.api.mana.ManaItemHandler;
import vazkii.botania.client.fx.WispParticleData;
import vazkii.botania.common.component.BotaniaDataComponents;
import vazkii.botania.common.handler.BotaniaSounds;
import vazkii.botania.common.helper.DataComponentHelper;
import vazkii.botania.common.helper.MathHelper;
import vazkii.botania.network.clientbound.FlugelEyeEffectPacket;
import vazkii.botania.xplat.XplatAbstractions;

import java.util.*;

import static vazkii.botania.api.BotaniaAPI.botaniaRL;

public class EyeOfTheFlugelItem extends RelicItem {

	public EyeOfTheFlugelItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level world = context.getLevel();
		BlockPos pos = context.getClickedPos();
		Player player = context.getPlayer();

		if (player != null && player.isSecondaryUseActive()) {
			if (world.isClientSide) {
				for (int i = 0; i < 10; i++) {
					// Deliberately double: a block on a Sable sub-level sits at plot-grid coordinates around 2e7,
					// where a float's ULP is about two blocks, so rounding these down to float would collapse the
					// whole random spread into a single corner of the block.
					double x1 = pos.getX() + Math.random();
					double y1 = pos.getY() + 1;
					double z1 = pos.getZ() + Math.random();
					WispParticleData data = WispParticleData.wisp((float) Math.random() * 0.5F, (float) Math.random(), (float) Math.random(), (float) Math.random(), 1);
					world.addParticle(data, x1, y1, z1, 0, 0.05F - (float) Math.random() * 0.05F, 0);
				}
			} else {
				ItemStack stack = context.getItemInHand();
				Map<ResourceLocation, BlockPos> boundPositions = new HashMap<>(stack.getOrDefault(
						BotaniaDataComponents.BOUND_POSITIONS, Collections.emptyMap()));
				boundPositions.put(world.dimension().location(), pos);
				stack.set(BotaniaDataComponents.BOUND_POSITIONS, boundPositions);
				setSubLevelAnchor(stack, world, pos);
				world.playSound(null, player.getX(), player.getY(), player.getZ(), BotaniaSounds.EYE_OF_THE_FLUGEL_BIND, SoundSource.PLAYERS, 1F, 1F);
			}

			return InteractionResult.sidedSuccess(world.isClientSide());
		}

		return InteractionResult.PASS;
	}

	@Override
	public void onUseTick(Level level, LivingEntity livingEntity, ItemStack stack, int remainingUseDuration) {
		if (level.isClientSide) {
			float x = (float) (livingEntity.getX() - Math.random() * livingEntity.getBbWidth());
			float y = (float) (livingEntity.getY() + Math.random());
			float z = (float) (livingEntity.getZ() - Math.random() * livingEntity.getBbWidth());
			WispParticleData data = WispParticleData.wisp((float) Math.random() * 0.7F, (float) Math.random(), (float) Math.random(), (float) Math.random(), 1);
			level.addParticle(data, x, y, z, 0, 0.05F + (float) Math.random() * 0.05F, 0);
		}
	}

	@Nullable
	public static BlockPos getBoundPosInDimension(ItemStack stack, Level level) {
		return stack.getOrDefault(BotaniaDataComponents.BOUND_POSITIONS, Map.<ResourceLocation, BlockPos>of())
				.get(level.dimension().location());
	}

	@Nullable
	private static UUID getSubLevelAnchor(ItemStack stack, Level level) {
		return stack.getOrDefault(BotaniaDataComponents.BOUND_SUB_LEVEL_ANCHORS, Map.<ResourceLocation, UUID>of())
				.get(level.dimension().location());
	}

	private static void setBoundPosInDimension(ItemStack stack, Level level, BlockPos pos) {
		if (pos.equals(getBoundPosInDimension(stack, level))) {
			// Written on every tick otherwise, and this one is persisted.
			return;
		}
		Map<ResourceLocation, BlockPos> boundPositions = new HashMap<>(stack.getOrDefault(
				BotaniaDataComponents.BOUND_POSITIONS, Collections.emptyMap()));
		boundPositions.put(level.dimension().location(), pos);
		stack.set(BotaniaDataComponents.BOUND_POSITIONS, boundPositions);
	}

	/**
	 * Points the binding for the current dimension at a fresh Sable tracking point. The bound {@link BlockPos} alone
	 * cannot survive a sub-level: its plot-grid coordinates are re-assigned every time the platform is assembled, and a
	 * world position stops meaning anything the moment those blocks are assembled into one. The tracking point is what
	 * Sable carries through both, and the position is refreshed from it in {@link #inventoryTick}. Registered for
	 * regular-world blocks as well, since whether a platform will later be built around one cannot be known here. Any
	 * point the binding held before is dropped, as nothing would resolve it any more.
	 */
	private static void setSubLevelAnchor(ItemStack stack, Level level, BlockPos pos) {
		ResourceLocation dimension = level.dimension().location();
		Map<ResourceLocation, UUID> anchors = new HashMap<>(stack.getOrDefault(
				BotaniaDataComponents.BOUND_SUB_LEVEL_ANCHORS, Collections.emptyMap()));

		UUID previous = anchors.remove(dimension);
		if (previous != null) {
			SableCompat.removeSubLevelAnchor(level, previous);
		}

		UUID anchor = SableCompat.createSubLevelAnchor(level, pos);
		if (anchor != null) {
			anchors.put(dimension, anchor);
		}

		if (anchors.isEmpty()) {
			stack.remove(BotaniaDataComponents.BOUND_SUB_LEVEL_ANCHORS);
		} else {
			stack.set(BotaniaDataComponents.BOUND_SUB_LEVEL_ANCHORS, anchors);
		}
	}

	@Override
	public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
		super.inventoryTick(stack, level, entity, slotId, isSelected);
		// we can't access the level while building the tooltip, so the best we can do is figuring it out ahead of time
		ResourceLocation dimension = level.dimension().location();
		ResourceLocation knownDimension = stack.get(BotaniaDataComponents.LOCAL_DIMENSION);
		if (!Objects.equals(dimension, knownDimension)) {
			stack.set(BotaniaDataComponents.LOCAL_DIMENSION, dimension);
		}
		// Re-home a sub-level binding onto wherever its tracking point ended up: the plot-grid coordinates of a
		// sub-level's blocks are re-assigned every time it is assembled, so the stored position goes stale as soon as
		// the platform is rebuilt, while the tracking point follows the structure. While the platform is taken apart the
		// point sits in the regular world and the binding reads as a plain world one - but the point is deliberately
		// kept, since that is exactly what Sable re-homes into the new plot when those blocks are assembled again.
		if (!level.isClientSide()) {
			UUID anchor = getSubLevelAnchor(stack, level);
			if (anchor != null) {
				SableCompat.SubLevelAnchor resolved = SableCompat.resolveSubLevelAnchor(level, anchor);
				// A null resolve means the sub-level cannot be reached right now; leave the last known position alone.
				if (resolved != null) {
					setBoundPosInDimension(stack, level, resolved.localPos() != null
							? resolved.localPos()
							: BlockPos.containing(resolved.worldPos()));
				}
			}
		}

		// Cached for the tooltip only. A binding on a Sable sub-level is stored in that sub-level's plot-grid
		// coordinates, which would read as nonsense there, so cache where the bound block currently is in the world
		// instead. Block granularity keeps this from re-syncing every tick while a sub-level drifts.
		BlockPos boundPos = getBoundPosInDimension(stack, level);
		BlockPos displayPos = boundPos == null ? null : SableCompat.transformFromSable(level, boundPos);
		BlockPos knownBoundPos = stack.get(BotaniaDataComponents.LOCAL_BOUND_POSITION);
		if (!Objects.equals(displayPos, knownBoundPos)) {
			DataComponentHelper.setOptional(stack, BotaniaDataComponents.LOCAL_BOUND_POSITION, displayPos);
		}
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
		return ItemUtils.startUsingInstantly(level, player, usedHand);
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity livingEntity) {
		if (level.isClientSide()) {
			return stack;
		}
		BlockPos loc = getBoundPosInDimension(stack, level);

		// The binding is resolved through its Sable tracking point rather than through the stored position, which may be
		// a tick behind the blocks being assembled or dismantled. The point resolves either to a plot-grid position on
		// the sub-level currently holding it, or to a plain world one while those blocks are not assembled. When it
		// cannot be resolved at all, fall through on the stored position and let the guard below judge it.
		UUID anchor = getSubLevelAnchor(stack, level);
		if (anchor != null) {
			SableCompat.SubLevelAnchor resolved = SableCompat.resolveSubLevelAnchor(level, anchor);
			if (resolved != null) {
				loc = resolved.localPos() != null ? resolved.localPos() : BlockPos.containing(resolved.worldPos());
			}
		}

		if (loc == null) {
			return stack;
		}

		// The binding may sit on a Sable sub-level, in which case it is stored in that sub-level's plot-grid
		// coordinates: unrelated to world space, but stable as the platform moves. If the sub-level is gone or
		// unloaded there is no pose to resolve them with, and using them raw would throw the player millions of
		// blocks away - refuse instead, without charging any mana.
		if (SableCompat.isPlotGridPos(level, loc) && !SableCompat.isOnSubLevel(level, loc)) {
			if (livingEntity instanceof Player player) {
				player.displayClientMessage(Component.translatable("botaniamisc.flugelSubLevelMissing"), true);
			}
			return stack;
		}

		// Resolved through the sub-level's current pose, so both the cost and the destination follow the platform.
		// The 1.5 offset is applied before the transform, i.e. in the sub-level's own frame, so the player lands
		// above the bound block as the platform sees it rather than as the world does. No-op for a world binding.
		Vec3 costPos = toWorldSpace(level, loc, 0.5);
		Vec3 target = toWorldSpace(level, loc, 1.5);

		int cost = (int) (MathHelper.pointDistanceSpace(costPos.x, costPos.y, costPos.z,
				livingEntity.getX(), livingEntity.getY(), livingEntity.getZ()) * 10);

		if (!(livingEntity instanceof Player player) || ManaItemHandler.instance().requestManaExact(stack, player, cost, true)) {
			moveParticlesAndSound(livingEntity);
			Vec3 sourcePos = livingEntity.position();
			livingEntity.teleportTo(target.x, target.y, target.z);
			level.gameEvent(livingEntity, GameEvent.TELEPORT, sourcePos);
			moveParticlesAndSound(livingEntity);
		}

		return stack;
	}

	/**
	 * Translates a point expressed in the coordinate frame of {@code loc} - the frame of the Sable sub-level holding
	 * the bound block, or plain world coordinates - into world space, offsetting {@code dy} blocks up along that
	 * frame's own vertical. Returns the plain world point when {@code loc} is a regular-world block.
	 */
	private static Vec3 toWorldSpace(Level level, BlockPos loc, double dy) {
		Vec3 local = new Vec3(loc.getX() + 0.5, loc.getY() + dy, loc.getZ() + 0.5);
		return SableCompat.transformFromSable(level, local, Vec3.atCenterOf(loc));
	}

	private static void moveParticlesAndSound(Entity entity) {
		XplatAbstractions.INSTANCE.sendToTracking(entity, new FlugelEyeEffectPacket(entity.getId()));
		entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
				BotaniaSounds.EYE_OF_THE_FLUGEL_TELEPORT, SoundSource.PLAYERS, 1F, 1F);
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity entity) {
		return 40;
	}

	@Override
	public UseAnim getUseAnimation(ItemStack stack) {
		return UseAnim.BOW;
	}

	public record CoordBoundItemImpl(ItemStack stack, Level level) implements CoordBoundItem {

		@Nullable
		@Override
		public BlockPos getBinding() {
			return getBoundPosInDimension(stack, level);
		}
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flags) {
		super.appendHoverText(stack, context, tooltip, flags);

		if (context.registries() == null) {
			return;
		}

		ResourceLocation dimension = stack.get(BotaniaDataComponents.LOCAL_DIMENSION);
		if (dimension == null) {
			return;
		}
		BlockPos binding = stack.get(BotaniaDataComponents.LOCAL_BOUND_POSITION);
		Component worldText = Component.literal(dimension.toString()).withStyle(ChatFormatting.GREEN);

		if (binding == null) {
			tooltip.add(Component.translatable("botaniamisc.flugelUnbound", worldText).withStyle(ChatFormatting.GRAY));
		} else {
			Component bindingText = Component.literal("[").withStyle(ChatFormatting.WHITE)
					.append(Component.literal(Integer.toString(binding.getX())).withStyle(ChatFormatting.GOLD))
					.append(", ")
					.append(Component.literal(Integer.toString(binding.getY())).withStyle(ChatFormatting.GOLD))
					.append(", ")
					.append(Component.literal(Integer.toString(binding.getZ())).withStyle(ChatFormatting.GOLD))
					.append("]");

			tooltip.add(Component.translatable("botaniamisc.flugelBound", bindingText, worldText).withStyle(ChatFormatting.GRAY));
		}
	}

	public static Relic makeRelic(ItemStack stack) {
		return new RelicImpl(stack, botaniaRL("challenge/eye_of_the_flugel"));
	}

}
