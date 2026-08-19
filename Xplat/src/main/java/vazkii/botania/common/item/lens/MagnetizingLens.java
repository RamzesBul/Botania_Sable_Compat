/*
 * This class is distributed as part of the Botania Mod.
 * Get the Source Code in github:
 * https://github.com/Vazkii/Botania
 *
 * Botania is Open Source and distributed under the
 * Botania License: http://botaniamod.net/license.php
 */
package vazkii.botania.common.item.lens;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import vazkii.botania.api.compat.Sable.SableCompat;
import vazkii.botania.api.internal.ManaBurst;
import vazkii.botania.api.mana.ManaReceiver;
import vazkii.botania.common.helper.MathHelper;

import java.util.Optional;
import java.util.function.Predicate;

public class MagnetizingLens extends Lens {

	@Override
	public void updateBurst(ManaBurst burst, ItemStack stack) {
		Entity entity = burst.entity();
		BlockPos basePos = entity.blockPosition();
		boolean magnetized = burst.getMagnetizedPos() != null;
		int range = 3;

		Optional<GlobalPos> source = burst.getBurstSourcePosition();
		final boolean sourceless = source.isEmpty() || !burst.isBurstSourceDimension(entity.level());

		Predicate<BlockPos> predicate = pos -> {
			var receiver = ManaReceiver.LOOKUP.find(entity.level(), pos, null);
			return receiver != null
					// Sable-aware distance: the candidate and the burst's source may sit on different levels, where a
					// raw coordinate distance means nothing. Same value as before when they share a frame.
					&& (sourceless || SableCompat.distanceSqr(entity.level(), pos, source.get().pos()) > 9)
					&& receiver.canReceiveManaFromBursts()
					&& !receiver.isFull();
		};

		// todo: clean this logic up
		BlockPos target = null;
		if (magnetized) {
			target = burst.getMagnetizedPos();
			if (!predicate.test(target)) {
				target = null;
				burst.setMagnetizePos(null);
				magnetized = false;
			}
		}

		if (!magnetized) {
			// A retained burst travels in the plot-grid frame of the sub-level that fired it, so this box scans that
			// sub-level's own blocks; a burst in the world scans world blocks. Either way it only ever sees the frame
			// the burst is currently in.
			for (BlockPos pos : MathHelper.aroundPosClosed(basePos, range)) {
				target = pos;
				if (predicate.test(target)) {
					break;
				}
				target = null;
			}

			// Nothing in the burst's own frame: look at the levels physically overlapping the same box - the plot-grid
			// positions of neighbouring sub-levels, plus the surrounding world when the burst itself is on one. Gated on
			// a sub-level actually being in reach, since this builds the whole cell list and runs every tick.
			if (target == null && SableCompat.hasSubLevelContext(entity.level(), basePos, range)) {
				for (BlockPos pos : SableCompat.blockScanPositionsOnOtherLevels(entity.level(), basePos, range, range)) {
					target = pos;
					if (predicate.test(target)) {
						break;
					}
					target = null;
				}
			}
		}

		if (target == null) {
			return;
		}

		Vec3 burstVec = entity.position();
		// The target may live in another frame than the burst, and the steering below - like setDeltaMovement - only
		// makes sense in the burst's own one. The 0.1 downward aim is applied first, in the target's frame, since it is
		// about aiming slightly below the center of that block. Both transforms are no-ops within a single frame.
		Vec3 targetWorld = SableCompat.transformFromSable(entity.level(), Vec3.atCenterOf(target).add(0, -0.1, 0));
		Vec3 tileVec = SableCompat.toSableLocalFrame(entity.level(), targetWorld, entity.blockPosition());
		Vec3 motionVec = entity.getDeltaMovement();

		Vec3 normalMotionVec = motionVec.normalize();
		Vec3 magnetVec = tileVec.subtract(burstVec).normalize();
		Vec3 differenceVec = normalMotionVec.subtract(magnetVec).scale(motionVec.length() * 0.1);

		Vec3 finalMotionVec = motionVec.subtract(differenceVec);
		if (!magnetized) {
			finalMotionVec = finalMotionVec.scale(0.75);
			burst.setMagnetizePos(target.immutable());
		}

		entity.setDeltaMovement(finalMotionVec);
	}

}
