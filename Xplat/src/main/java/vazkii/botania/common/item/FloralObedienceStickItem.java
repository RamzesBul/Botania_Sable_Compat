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
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import vazkii.botania.api.block_entity.BindableSpecialFlowerBlockEntity;
import vazkii.botania.api.block_entity.FunctionalFlowerBlockEntity;
import vazkii.botania.api.block_entity.GeneratingFlowerBlockEntity;
import vazkii.botania.api.compat.Sable.SableCompat;
import vazkii.botania.api.mana.ManaCollector;
import vazkii.botania.api.mana.ManaPool;
import vazkii.botania.api.mana.ManaReceiver;
import vazkii.botania.common.helper.MathHelper;

import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;

public class FloralObedienceStickItem extends Item {

	public FloralObedienceStickItem(Properties props) {
		super(props);
	}

	@Override
	public InteractionResult useOn(UseOnContext ctx) {
		Level world = ctx.getLevel();
		BlockPos pos = ctx.getClickedPos();
		return applyStick(world, pos)
				? InteractionResult.sidedSuccess(world.isClientSide())
				: InteractionResult.PASS;
	}

	public static boolean applyStick(Level world, BlockPos pos) {
		var receiver = ManaReceiver.LOOKUP.find(world, pos, null);
		if (receiver instanceof ManaPool || receiver instanceof ManaCollector) {
			int range = receiver instanceof ManaPool ? FunctionalFlowerBlockEntity.LINK_RANGE : GeneratingFlowerBlockEntity.LINK_RANGE;

			double rangeSqr = (double) range * range;
			// A flower reached through one of the cross-level scans below can also show up in the local scan when it
			// shares the clicked block's sub-level, and binding it twice would draw the particle beam twice.
			Set<BlockPos> bound = new HashSet<>();

			BiConsumer<BlockPos, BlockEntity> bind = (candidatePos, blockEntity) -> {
				if (blockEntity instanceof BindableSpecialFlowerBlockEntity<?> bindable
						&& bindable.wouldBeValidBinding(pos) && bound.add(candidatePos.immutable())) {
					bindable.setBindingPos(pos);
					WandOfTheForestItem.doParticleBeamWithOffset(world, candidatePos, pos);
				}
			};

			// 1) Local scan in the clicked block's own coordinate space (same world, or the same sub-level plot). Both
			// positions share a frame here, so the plain coordinate distance is exact and spares a sub-level lookup on
			// each of the thousands of cells.
			for (BlockPos iterPos : MathHelper.aroundPosClosed(pos, range)) {
				if (MathHelper.distSqr(iterPos, pos) > rangeSqr) {
					continue;
				}
				BlockEntity blockEntity = world.getBlockEntity(iterPos);
				if (blockEntity != null) {
					bind.accept(iterPos, blockEntity);
				}
			}

			// 2) Cross-level scan, mirroring BindableSpecialFlowerBlockEntity#getClosestMatchingBlockEntity: flowers on
			// nearby sub-levels, plus - when the pool itself sits on one - flowers in the surrounding world. Blocks on a
			// sub-level are stored at plot-grid coordinates unrelated to world space, so the box above cannot reach them
			// no matter how close the platform physically is. No-ops when Sable is absent.
			BiConsumer<BlockPos, BlockEntity> bindAcrossLevels = (candidatePos, blockEntity) -> {
				// The two positions are in different coordinate spaces here, where a raw distance means nothing.
				if (SableCompat.distanceSqr(world, pos, candidatePos) <= rangeSqr) {
					bind.accept(candidatePos, blockEntity);
				}
			};
			Vec3 worldCenter = SableCompat.transformFromSable(world, Vec3.atCenterOf(pos));
			SableCompat.forEachBlockEntityInNearbySubLevels(world, worldCenter, range, bindAcrossLevels);
			if (SableCompat.isOnSubLevel(world, pos)) {
				SableCompat.forEachWorldBlockEntityNear(world, worldCenter, range, bindAcrossLevels);
			}

			return true;
		}
		if (world.getBlockEntity(pos) instanceof BindableSpecialFlowerBlockEntity<?> bindableFlower) {
			if (bindableFlower.getBindingPos() == null) {
				bindableFlower.attemptAutoBinding();
			} else {
				bindableFlower.setBindingPos(null);
			}
			return true;
		}

		return false;
	}
}
