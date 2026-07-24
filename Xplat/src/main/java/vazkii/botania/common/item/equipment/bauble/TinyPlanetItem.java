/*
 * This class is distributed as part of the Botania Mod.
 * Get the Source Code in github:
 * https://github.com/Vazkii/Botania
 *
 * Botania is Open Source and distributed under the
 * Botania License: http://botaniamod.net/license.php
 */
package vazkii.botania.common.item.equipment.bauble;

import com.google.common.base.Predicates;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import vazkii.botania.api.compat.Sable.SableCompat;
import vazkii.botania.api.internal.ManaBurst;
import vazkii.botania.api.mana.TinyPlanetExcempt;
import vazkii.botania.client.render.AccessoryRenderRegistry;
import vazkii.botania.client.render.AccessoryRenderer;
import vazkii.botania.common.block.BotaniaBlocks;
import vazkii.botania.common.proxy.Proxy;

import java.util.List;

public class TinyPlanetItem extends BaubleItem {

	public TinyPlanetItem(Properties props) {
		super(props);
		Proxy.INSTANCE.runOnClient(() -> () -> AccessoryRenderRegistry.register(this, new Renderer()));
	}

	@Override
	public void onWornTick(ItemStack stack, LivingEntity living) {
		double x = living.getX();
		double y = living.getY() + living.getEyeHeight();
		double z = living.getZ();

		applyEffect(living.level(), x, y, z);
	}

	public static class Renderer implements AccessoryRenderer {
		@Override
		public void doRender(HumanoidModel<?> bipedModel, ItemStack stack, LivingEntity living, PoseStack ms, MultiBufferSource buffers, int light, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
			bipedModel.head.translateAndRotate(ms);
			ms.translate(-0.25, -0.4, 0);
			ms.scale(0.5F, -0.5F, -0.5F);
			Minecraft.getInstance().getBlockRenderer().renderSingleBlock(BotaniaBlocks.TINY_PLANET.defaultBlockState(), ms, buffers, light, OverlayTexture.NO_OVERLAY);
		}
	}

	public static void applyEffect(Level world, double x, double y, double z) {
		int range = 8;
		// Search around the planet's WORLD position: a planet on a Sable sub-level has plot-grid coords (~2e7),
		// but bursts to pull are found by world proximity (Sable's entity getter includes sub-level bursts near a
		// world-space box). Identity for a planet in the regular world / a worn planet. See the per-burst frame
		// handling below for why the raw plot-grid centre must not be used directly.
		Vec3 planetWorld = SableCompat.transformFromSable(world, new Vec3(x, y, z));
		List<ThrowableProjectile> entities = world.getEntitiesOfClass(ThrowableProjectile.class,
				new AABB(planetWorld.x - range, planetWorld.y - range, planetWorld.z - range,
						planetWorld.x + range, planetWorld.y + range, planetWorld.z + range),
				Predicates.instanceOf(ManaBurst.class));
		for (ThrowableProjectile entity : entities) {
			ManaBurst burst = (ManaBurst) entity;
			ItemStack lens = burst.getSourceLens();
			if (lens != null && lens.getItem() instanceof TinyPlanetExcempt excempt && !excempt.shouldPull(lens)) {
				continue;
			}

			int orbitTime = burst.getOrbitTime();
			if (orbitTime == 0) {
				burst.setMinManaLoss(burst.getMinManaLoss() * 3);
			}

			float radius = Math.min(7.5F, (Math.max(40, orbitTime) - 40) / 40F + 1.5F);
			int angle = orbitTime % 360;

			// Express the planet centre in the BURST's own coordinate frame, then build the orbit target there, so
			// the target and the burst's position are always in the same frame. Otherwise a planet in the world
			// (or another sub-level) and a burst on a sub-level are ~2e7 apart, so setDeltaMovement gets a huge
			// vector - which teleports the burst and blows up its hit-detection AABB (Sable aborts it). For a
			// burst on the same sub-level this round-trips back to the plot-grid centre (unchanged behaviour); for
			// a world burst / worn planet it is the world centre. Kept in double precision (float's ULP is ~2
			// blocks at plot-grid magnitude, which would quantise the orbit offset into visible jumps).
			Vec3 center = SableCompat.toSableLocalFrame(world, planetWorld, entity.blockPosition());
			double xTarget = center.x + Math.cos(angle * 10 * Math.PI / 180F) * radius;
			double yTarget = center.y;
			double zTarget = center.z + Math.sin(angle * 10 * Math.PI / 180F) * radius;

			Vec3 targetVec = new Vec3(xTarget, yTarget, zTarget);
			Vec3 currentVec = entity.position();
			Vec3 moveVector = targetVec.subtract(currentVec);

			entity.setDeltaMovement(moveVector);

			burst.setOrbitTime(burst.getOrbitTime() + 1);
		}
	}

}
