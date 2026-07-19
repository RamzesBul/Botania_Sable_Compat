/*
 * This class is distributed as part of the Botania Mod.
 * Get the Source Code in github:
 * https://github.com/Vazkii/Botania
 *
 * Botania is Open Source and distributed under the
 * Botania License: http://botaniamod.net/license.php
 */
package vazkii.botania.common.item.rod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

import vazkii.botania.api.block.Avatar;
import vazkii.botania.api.compat.Sable.SableCompat;
import vazkii.botania.api.item.AvatarWieldable;
import vazkii.botania.api.mana.ManaItemHandler;
import vazkii.botania.api.mana.ManaReceiver;
import vazkii.botania.client.fx.WispParticleData;
import vazkii.botania.client.lib.ResourcesLib;
import vazkii.botania.common.handler.BotaniaSounds;
import vazkii.botania.common.helper.MathHelper;
import vazkii.botania.common.lib.BotaniaTags;
import vazkii.botania.network.clientbound.RodOfThePlentifulMantleEffectPacket;
import vazkii.botania.xplat.XplatAbstractions;

import java.util.Random;

public class PlentifulMantleRodItem extends Item {

	private static final ResourceLocation AVATAR_OVERLAY = ResourceLocation.parse(ResourcesLib.MODEL_AVATAR_DIVINING);

	public static final int COST = 3000;
	public static final byte RANGE_PROFICIENCY = 20;
	public static final byte RANGE_DEFAULT = 15;
	public static final byte RANGE_AVATAR = 18;
	public static final int COOLDOWN_AVATAR = 200;

	public PlentifulMantleRodItem(Properties props) {
		super(props);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (ManaItemHandler.instance().requestManaExactForTool(stack, player, COST, true)) {
			if (!level.isClientSide()) {
				byte range = ManaItemHandler.instance().hasProficiency(player, stack) ? RANGE_PROFICIENCY : RANGE_DEFAULT;
				XplatAbstractions.instance().sendToPlayer(player,
						new RodOfThePlentifulMantleEffectPacket(player.blockPosition(), range, true));
				player.gameEvent(GameEvent.ITEM_INTERACT_FINISH);
				player.getCooldowns().addCooldown(this, 20);
			}
			player.playSound(BotaniaSounds.ROD_OF_THE_PLENTIFUL_MANTLE, 1, 1);
			return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
		}

		return InteractionResultHolder.pass(stack);
	}

	// TODO: technically it's more accurate to determine ore positions serverside,
	//  since clients might not know about enclosed blocks in the presence of anti-xray measures
	//  (but then we'd have to figure out how to transfer potentially a LOT of data in a network packet)
	public static void doHighlight(Level level, BlockPos centerPos, int range, long seedXor) {
		// When a sub-level is near the scan, resolve each cell to whichever level physically occupies it (the world,
		// the avatar's own platform, or a neighbouring sub-level) so ores on sub-levels are highlighted too. Skipped
		// entirely when no sub-level is around, keeping the plain-world path (and the hand-held rod) unchanged.
		boolean crossLevel = SableCompat.hasSubLevelContext(level, centerPos, range);
		for (BlockPos pos : MathHelper.aroundPosClosed(centerPos, range)) {
			BlockPos resolved = crossLevel ? SableCompat.resolveCellAcrossLevels(level, centerPos, pos) : pos;
			BlockState state = level.getBlockState(resolved);

			Block block = state.getBlock();
			if (state.is(BotaniaTags.Blocks.ROD_OF_THE_PLENTIFUL_MANTLE_HIGHLIGHTED)) {
				Random rand = new Random(BuiltInRegistries.BLOCK.getId(block) ^ seedXor);
				WispParticleData data = WispParticleData.wisp(0.25F,
						rand.nextFloat(), rand.nextFloat(), rand.nextFloat(),
						8, false);
				// Spawn at the resolved cell's own coords: for a sub-level cell these are plot-grid coords, and
				// Sable's particle handling kicks the particle into world space and tracks it with the sub-level
				// (the same path used by flower particles), which positions it correctly. World cells are unchanged.
				// The in-block offset is added in double precision: a sub-level cell's coords are ~2e7, where a
				// float's ULP is ~2 blocks, so computing `int + nextFloat()` in float (int+float promotes to float)
				// would quantise the fraction - and even the integer - away, snapping every wisp to a block seam.
				level.addParticle(data, true,
						resolved.getX() + (double) level.random.nextFloat(),
						resolved.getY() + (double) level.random.nextFloat(),
						resolved.getZ() + (double) level.random.nextFloat(),
						0, 0, 0);
			}
		}
	}

	public record AvatarBehavior(ItemStack rod, Avatar avatar) implements AvatarWieldable {
		@Override
		public void onAvatarUpdate(ServerLevel level, BlockPos pos, ManaReceiver receiver) {
			if (receiver.getCurrentMana() >= COST && avatar.isEnabled()
					&& getTimeSinceLastActivation(level) >= COOLDOWN_AVATAR) {
				// The avatar highlights ores in the surrounding world (like the hand-held rod). Its own position is
				// in plot-grid coords when it sits on a sub-level, so both the effect target and the scan centre
				// must be its world position. Identity in the regular world, so vanilla behaviour is unchanged.
				BlockPos worldPos = SableCompat.transformFromSable(level, pos);
				XplatAbstractions.instance().sendToNear(level, worldPos,
						new RodOfThePlentifulMantleEffectPacket(worldPos, RANGE_AVATAR, false));
				receiver.receiveMana(-COST);
				setLastActivationTime(level);
			}
		}

		@Override
		public ResourceLocation getOverlayResource() {
			return AVATAR_OVERLAY;
		}
	}
}
