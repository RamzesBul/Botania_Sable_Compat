/*
 * This class is distributed as part of the Botania Mod.
 * Get the Source Code in github:
 * https://github.com/Vazkii/Botania
 *
 * Botania is Open Source and distributed under the
 * Botania License: http://botaniamod.net/license.php
 */
package vazkii.botania.common.item.lens;

import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import vazkii.botania.api.compat.Sable.SableCompat;
import vazkii.botania.api.internal.ManaBurst;
import vazkii.botania.common.item.BotaniaItems;

public class InfluenceLens extends Lens {

	@Override
	public void updateBurst(ManaBurst burst, ItemStack stack) {
		Entity entity = burst.entity();
		if (!burst.isFake()) {
			double range = 3.5;
			AABB bounds = new AABB(entity.getX() - range, entity.getY() - range, entity.getZ() - range, entity.getX() + range, entity.getY() + range, entity.getZ() + range);
			var items = entity.level().getEntitiesOfClass(ItemEntity.class, bounds);
			var expOrbs = entity.level().getEntitiesOfClass(ExperienceOrb.class, bounds);
			var arrows = entity.level().getEntitiesOfClass(AbstractArrow.class, bounds);
			var fallingBlocks = entity.level().getEntitiesOfClass(FallingBlockEntity.class, bounds);
			var primedTnt = entity.level().getEntitiesOfClass(PrimedTnt.class, bounds);
			var bursts = entity.level().getEntitiesOfClass(ThrowableProjectile.class, bounds, Predicates.instanceOf(ManaBurst.class));

			// Sable answers the box above across coordinate frames, so a burst travelling in a sub-level's logical
			// space also picks up entities in the world and on other sub-levels. Its velocity is expressed in its
			// own frame though, so it has to be rotated to world space and then into whatever frame each entity
			// lives in, otherwise a rotated platform would push them along the wrong axis. Both transforms are
			// no-ops outside sub-levels, leaving regular-world behavior untouched.
			Vec3 worldMotion = SableCompat.transformDirectionFromSable(entity.level(), entity.getDeltaMovement(), entity.blockPosition());

			var concat = Iterables.concat(items, expOrbs, arrows, fallingBlocks, primedTnt, bursts);
			for (Entity movable : concat) {
				if (movable == burst) {
					continue;
				}

				if (movable instanceof ManaBurst otherBurst) {
					ItemStack lens = otherBurst.getSourceLens();
					if (!lens.isEmpty() && lens.is(BotaniaItems.INFLUENCE_LENS)) {
						continue;
					}
				}
				movable.setDeltaMovement(SableCompat.transformDirectionToSable(movable.level(), worldMotion, movable.blockPosition()));
			}
		}
	}

}
