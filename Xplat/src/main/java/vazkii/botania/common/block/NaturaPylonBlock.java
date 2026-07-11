/*
 * This class is distributed as part of the Botania Mod.
 * Get the Source Code in github:
 * https://github.com/Vazkii/Botania
 *
 * Botania is Open Source and distributed under the
 * Botania License: http://botaniamod.net/license.php
 */

package vazkii.botania.common.block;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import vazkii.botania.api.compat.Sable.SableCompat;
import vazkii.botania.api.state.BotaniaStateProperties;
import vazkii.botania.api.state.enums.AlfheimPortalState;
import vazkii.botania.client.fx.SparkleParticleData;
import vazkii.botania.client.fx.WispParticleData;
import vazkii.botania.common.block.block_entity.AlfheimPortalBlockEntity;
import vazkii.botania.common.block.block_entity.PylonBlockEntity;
import vazkii.botania.common.block.mana.ManaPoolBlock;
import vazkii.botania.xplat.BotaniaConfig;

import java.util.Random;

public class NaturaPylonBlock extends PylonBlock {

	public NaturaPylonBlock(Properties builder) {
		super(builder);
	}

	@Override
	protected void clientTick(Level level, BlockPos worldPosition, BlockState state, PylonBlockEntity self) {

		super.clientTick(level, worldPosition, state, self);

		if (self.activated && self.centerPos != null) {
			BlockState centerState = level.getBlockState(self.centerPos);
			if (!centerState.is(BotaniaBlocks.ELVEN_GATEWAY_CORE)
					|| centerState.getValue(BotaniaStateProperties.ALFPORTAL_STATE) == AlfheimPortalState.OFF
					|| !(level.getBlockState(worldPosition.below()).getBlock() instanceof ManaPoolBlock)) {
				self.activated = false;
				return;
			}

			// Break the beam once the pylon drifts out of range of the portal (their sub-levels moved apart).
			// Keep `activated` so the beam lights up again by itself when back within range.
			if (!SableCompat.isWithinRange(level, self.centerPos, worldPosition, AlfheimPortalBlockEntity.PYLON_SEARCH_RANGE)) {
				return;
			}

			if (BotaniaConfig.client().elfPortalParticlesEnabled()) {
				double worldTime = level.getGameTime() * 0.2
						+ new Random(state.getSeed(worldPosition)).nextDouble(2 * Math.PI);

				RandomSource rng = level.getRandom();
				double radius = 0.75 + rng.nextDouble() * 0.05;
				double x = worldPosition.getX() + 0.5 + Math.cos(worldTime) * radius;
				double y = worldPosition.getY();
				double z = worldPosition.getZ() + 0.5 + Math.sin(worldTime) * radius;

				WispParticleData upwardSpiralData = WispParticleData.wisp(rng.nextFloat() * 0.1f + 0.25f,
						rng.nextFloat() * 0.25f, rng.nextFloat() * 0.25f + 0.75f, rng.nextFloat() * 0.25f);
				level.addParticle(upwardSpiralData, x, y, z,
						rng.nextDouble() * 0.005, rng.nextDouble() * 0.015 + 0.075, rng.nextDouble() * 0.005);

				if (level.getRandom().nextInt(3) == 0) {
					// Point already in the portal's own frame (centerPos + jitter).
					Vec3 targetInPortalFrame = new Vec3(
							self.centerPos.getX() + 0.25 + 0.5 * rng.nextDouble(),
							self.centerPos.getY() + 0.25 + 0.5 * rng.nextDouble(),
							self.centerPos.getZ() + 0.25 + 0.5 * rng.nextDouble());
					Vec3 ourLocal = new Vec3(x, y + 0.25, z);

					// The pylon and the portal may sit on different (possibly moving) sub-levels. Spawn the wisp
					// in the PORTAL's coordinate frame: the portal center is fixed there, so once Sable binds the
					// particle to the portal's sub-level (spawning it in that plot grid) it keeps heading toward
					// the current portal position as the sub-level moves, instead of chasing where the portal was.
					// The beam's origin still follows the pylon because a fresh particle spawns at its current
					// spot each tick. When the portal is a regular-world block the point stays in world space and
					// the particle simply flies to the (static) portal.
					Vec3 ourWorld = SableCompat.transformFromSable(level, ourLocal, Vec3.atCenterOf(worldPosition));
					Vec3 ourInPortalFrame = SableCompat.toSableLocalFrame(level, ourWorld, self.centerPos);
					Vec3 movementVector = targetInPortalFrame.subtract(ourInPortalFrame).scale(0.04);

					WispParticleData towardsPortalData = WispParticleData.wispNoClip(rng.nextFloat() * 0.1f + 0.25f,
							rng.nextFloat() * 0.25f, rng.nextFloat() * 0.25f + 0.75f, rng.nextFloat() * 0.25f);
					level.addParticle(towardsPortalData, ourInPortalFrame.x, ourInPortalFrame.y, ourInPortalFrame.z,
							movementVector.x, movementVector.y, movementVector.z);
				}
			}
		}
	}

	@Override
	public void addRandomParticle(Level level, BlockPos pos) {
		RandomSource rng = level.getRandom();
		level.addParticle(SparkleParticleData.sparkle(rng.nextFloat(), 0.5f, 1.0f, 0.5f, 2),
				pos.getX() + rng.nextDouble(),
				pos.getY() + rng.nextDouble() * 1.3,
				pos.getZ() + rng.nextDouble(),
				0, 0, 0);
	}

	public float getEnchantPowerBonus(BlockState state, LevelReader world, BlockPos pos) {
		return 15f;
	}
}
