/*
 * This class is distributed as part of the Botania Mod.
 * Get the Source Code in github:
 * https://github.com/Vazkii/Botania
 *
 * Botania is Open Source and distributed under the
 * Botania License: http://botaniamod.net/license.php
 */
package vazkii.botania.common.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

import vazkii.botania.api.block.Bound;
import vazkii.botania.api.compat.Sable.SableCompat;
import vazkii.botania.api.item.CoordBoundItem;
import vazkii.botania.api.mana.ManaBarTooltip;
import vazkii.botania.api.mana.ManaItem;
import vazkii.botania.api.mana.ManaPool;
import vazkii.botania.api.mana.ManaReceiver;
import vazkii.botania.common.annotations.SoftImplement;
import vazkii.botania.common.component.BotaniaDataComponents;
import vazkii.botania.common.handler.BotaniaSounds;
import vazkii.botania.common.helper.DataComponentHelper;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class ManaMirrorItem extends Item {

	private static final DummyPool fallbackPool = new DummyPool();

	public ManaMirrorItem(Properties props) {
		super(props);
	}

	@Override
	public boolean isBarVisible(ItemStack stack) {
		return stack.has(BotaniaDataComponents.MANA_POOL_POS);
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		var manaItem = ManaItem.LOOKUP.find(stack);
		return Math.round(13 * ManaBarTooltip.getFractionForDisplay(manaItem));
	}

	@Override
	public int getBarColor(ItemStack stack) {
		var manaItem = ManaItem.LOOKUP.find(stack);
		return Mth.hsvToRgb(ManaBarTooltip.getFractionForDisplay(manaItem) / 3.0F, 1.0F, 1.0F);
	}

	@Override
	public void inventoryTick(ItemStack stack, Level world, Entity entity, int slot, boolean selected) {
		if (world.isClientSide) {
			return;
		}

		rehomeBinding(world.getServer(), stack);

		ManaPool pool = getManaPool(world.getServer(), stack);
		if (!(pool instanceof DummyPool)) {
			if (pool != null) {
				backfillAnchor(stack, pool);
				pool.receiveMana(getManaBacklog(stack));
				clearManaBacklog(stack);
				setMana(stack, pool.getCurrentMana());
				setMaxMana(stack, pool.getMaxMana());
			} else if (stack.has(BotaniaDataComponents.MANA)) {
				setMana(stack, 0);
				setMaxMana(stack, 0);
			}
		}
	}

	@Override
	public InteractionResult useOn(UseOnContext ctx) {
		Level world = ctx.getLevel();
		Player player = ctx.getPlayer();

		if (player != null && player.isSecondaryUseActive()) {
			var receiver = ManaReceiver.LOOKUP.find(world, ctx.getClickedPos(), null);
			if (receiver instanceof ManaPool pool) {
				if (!world.isClientSide) {
					bindPool(ctx.getItemInHand(), pool);
					world.playSound(null, player.getX(), player.getY(), player.getZ(), BotaniaSounds.DING, SoundSource.PLAYERS, 1F, 1F);
				}
				return InteractionResult.sidedSuccess(world.isClientSide());
			}
		}

		return InteractionResult.PASS;
	}

	protected static void setMana(ItemStack stack, int mana) {
		DataComponentHelper.setIntNonZero(stack, BotaniaDataComponents.MANA, mana);
	}

	protected static void setMaxMana(ItemStack stack, int maxMana) {
		DataComponentHelper.setIntNonNegative(stack, BotaniaDataComponents.MAX_MANA, maxMana);
	}

	protected static int getManaBacklog(ItemStack stack) {
		return stack.getOrDefault(BotaniaDataComponents.MANA_BACKLOG, 0);
	}

	protected static void clearManaBacklog(ItemStack stack) {
		stack.set(BotaniaDataComponents.MANA_BACKLOG, 0);
	}

	public void bindPool(ItemStack stack, ManaPool pool) {
		Level poolLevel = pool.getManaReceiverLevel();
		BlockPos poolPos = pool.getManaReceiverPos();

		// Released before the new binding overwrites the old one, since the point has to be dropped through the
		// level of the dimension it was registered in, which is the one the previous binding still names.
		removeAnchor(stack, poolLevel.getServer());

		GlobalPos pos = GlobalPos.of(poolLevel.dimension(), poolPos);
		stack.set(BotaniaDataComponents.MANA_POOL_POS, pos);
		setAnchor(stack, poolLevel, poolPos);
	}

	/**
	 * Backs the binding with a fresh Sable tracking point. The bound position alone cannot survive a sub-level: its
	 * plot-grid coordinates are re-assigned every time the platform is assembled, and a world position stops meaning
	 * anything the moment those blocks are assembled into one. The tracking point is what Sable carries through both,
	 * and the position is refreshed from it in {@link #rehomeBinding}. Registered for regular-world pools as well,
	 * since whether a platform will later be built around one cannot be known here.
	 */
	private static void setAnchor(ItemStack stack, Level level, BlockPos pos) {
		DataComponentHelper.setOptional(stack, BotaniaDataComponents.BOUND_SUB_LEVEL_ANCHOR,
				SableCompat.createSubLevelAnchor(level, pos));
	}

	private static void removeAnchor(ItemStack stack, @Nullable MinecraftServer server) {
		UUID anchor = stack.remove(BotaniaDataComponents.BOUND_SUB_LEVEL_ANCHOR);
		GlobalPos bound = getBoundPos(stack);
		if (anchor == null || bound == null || server == null) {
			return;
		}
		ServerLevel level = server.getLevel(bound.dimension());
		if (level != null) {
			SableCompat.removeSubLevelAnchor(level, anchor);
		}
	}

	/**
	 * Points the binding at wherever its tracking point ended up. Resolved against the bound dimension rather than
	 * the one the holder is currently in: unlike the Eye of the Flügel, a mirror holds a single binding that may well
	 * name another dimension, and tracking points are stored per level. While the platform is taken apart the point
	 * sits in the regular world and the binding reads as a plain world one - but the point is deliberately kept,
	 * since that is exactly what Sable re-homes into the new plot when those blocks are assembled again.
	 */
	private static void rehomeBinding(@Nullable MinecraftServer server, ItemStack stack) {
		UUID anchor = stack.get(BotaniaDataComponents.BOUND_SUB_LEVEL_ANCHOR);
		GlobalPos bound = getBoundPos(stack);
		if (anchor == null || bound == null || server == null) {
			return;
		}
		ServerLevel level = server.getLevel(bound.dimension());
		if (level == null) {
			return;
		}

		SableCompat.SubLevelAnchor resolved = SableCompat.resolveSubLevelAnchor(level, anchor);
		// A null resolve means the sub-level cannot be reached right now; leave the last known position alone.
		if (resolved == null) {
			return;
		}

		BlockPos pos = resolved.localPos() != null
				? resolved.localPos()
				: BlockPos.containing(resolved.worldPos());
		if (!pos.equals(bound.pos())) {
			// Written on every tick otherwise, and this one is persisted and synced.
			stack.set(BotaniaDataComponents.MANA_POOL_POS, GlobalPos.of(bound.dimension(), pos));
		}
	}

	/**
	 * Gives a mirror bound before sub-level anchors existed one now, so an existing binding survives the next
	 * assembly instead of having to be redone. Deliberately gated on a pool that actually resolved: registering one
	 * from a position that no longer holds a pool would leak a fresh tracking point every tick.
	 */
	private static void backfillAnchor(ItemStack stack, ManaPool pool) {
		if (!stack.has(BotaniaDataComponents.BOUND_SUB_LEVEL_ANCHOR)) {
			setAnchor(stack, pool.getManaReceiverLevel(), pool.getManaReceiverPos());
		}
	}

	@Nullable
	private static GlobalPos getBoundPos(ItemStack stack) {
		return stack.get(BotaniaDataComponents.MANA_POOL_POS);
	}

	@Nullable
	private ManaPool getManaPool(@Nullable MinecraftServer server, ItemStack stack) {
		if (server == null) {
			return fallbackPool;
		}

		GlobalPos pos = getBoundPos(stack);
		if (pos == null) {
			return null;
		}

		ResourceKey<Level> type = pos.dimension();
		Level world = server.getLevel(type);
		if (world != null) {
			if (world.hasChunkAt(pos.pos())) {
				var receiver = ManaReceiver.LOOKUP.find(world, pos.pos(), null);
				if (receiver instanceof ManaPool pool) {
					return pool;
				}
			} else {
				return fallbackPool;
			}
		}

		return null;
	}

	@SoftImplement("IItemExtension")
	public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
		return slotChanged || reequipAnimation(oldStack, newStack);
	}

	@SoftImplement("FabricItem")
	public boolean allowComponentsUpdateAnimation(Player player, InteractionHand hand, ItemStack oldStack, ItemStack newStack) {
		return reequipAnimation(oldStack, newStack);
	}

	private boolean reequipAnimation(ItemStack before, ItemStack after) {
		return !Objects.equals(getBoundPos(before), getBoundPos(after));
	}

	private static class DummyPool implements ManaPool {

		@Override
		public boolean isFull() {
			return false;
		}

		@Override
		public void receiveMana(int mana) {}

		@Override
		public boolean canReceiveManaFromBursts() {
			return false;
		}

		@Override
		@UnknownNullability
		public Level getManaReceiverLevel() {
			return null;
		}

		@Override
		public BlockPos getManaReceiverPos() {
			return Bound.UNBOUND_POS;
		}

		@Override
		public int getCurrentMana() {
			return 0;
		}

		@Override
		public int getMaxMana() {
			return 0;
		}

		@Override
		public boolean isOutputtingPower() {
			return false;
		}

	}

	public record CoordBoundItemImpl(ItemStack stack, Level level) implements CoordBoundItem {
		@Nullable
		@Override
		public BlockPos getBinding() {
			GlobalPos pos = getBoundPos(stack);
			if (pos == null) {
				return null;
			}

			if (pos.dimension() == level.dimension()) {
				return pos.pos();
			}

			return null;
		}
	}

	@Override
	public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
		return Optional.of(ManaBarTooltip.fromManaItem(stack));
	}

}
