/*
 * This class is distributed as part of the Botania Mod.
 * Get the Source Code in github:
 * https://github.com/Vazkii/Botania
 *
 * Botania is Open Source and distributed under the
 * Botania License: http://botaniamod.net/license.php
 */

package vazkii.botania.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;

import vazkii.botania.api.compat.Sable.SableCompat;

// [VanillaCopy] ItemPickupParticle, but with BlockPos as target instead of an entity
public class FlowerItemPickupParticle extends Particle {
	private static final int LIFE_TIME = 3;
	private final RenderBuffers renderBuffers;
	private final Entity itemEntity;
	private final BlockPos target;
	private int life;
	private final EntityRenderDispatcher entityRenderDispatcher;
	// Botania: target doesn't move, so no need for position updates (I can already hear Sable noises, though)

	public FlowerItemPickupParticle(EntityRenderDispatcher entityRenderDispatcher, RenderBuffers buffers,
			ClientLevel level, Entity itemEntity, BlockPos target, boolean onFire) {
		this(entityRenderDispatcher, buffers, level, itemEntity, target, onFire, itemEntity.getDeltaMovement());
	}

	private FlowerItemPickupParticle(EntityRenderDispatcher entityRenderDispatcher, RenderBuffers buffers,
			ClientLevel level, Entity itemEntity, BlockPos target, boolean onFire, Vec3 speedVector) {
		super(level, itemEntity.getX(), itemEntity.getY(), itemEntity.getZ(), speedVector.x, speedVector.y, speedVector.z);
		this.renderBuffers = buffers;
		this.itemEntity = this.getSafeCopy(itemEntity, onFire);
		this.target = target;
		this.entityRenderDispatcher = entityRenderDispatcher;
	}

	private Entity getSafeCopy(Entity entity, boolean onFire) {
		if (!(entity instanceof ItemEntity item)) {
			return entity;
		}
		ItemEntity copy = item.copy();
		if (onFire) {
			copy.setSharedFlagOnFire(true);
		}
		return copy;
	}

	@Override
	public ParticleRenderType getRenderType() {
		return ParticleRenderType.CUSTOM;
	}

	@Override
	public void render(VertexConsumer buffer, Camera renderInfo, float partialTicks) {
		float time = ((float) this.life + partialTicks) / LIFE_TIME;
		time *= time;
		Vec3 targetPos = target.getCenter().add(level.getBlockState(target).getOffset(level, target));
		// Both endpoints may be a sub-level's plot-grid coordinates (far from world space); project them to world
		// via the sub-level's per-frame render pose so the animation flies in the right place instead of vanishing
		// off at ~2e7. No-op in the regular world.
		Vec3 itemStart = SableCompat.transformFromSableRender(level,
				new Vec3(this.itemEntity.getX(), this.itemEntity.getY(), this.itemEntity.getZ()),
				this.itemEntity.blockPosition(), partialTicks);
		targetPos = SableCompat.transformFromSableRender(level, targetPos, target, partialTicks);
		double xx = Mth.lerp(time, itemStart.x, targetPos.x);
		double yy = Mth.lerp(time, itemStart.y, targetPos.y);
		double zz = Mth.lerp(time, itemStart.z, targetPos.z);
		MultiBufferSource.BufferSource source = this.renderBuffers.bufferSource();
		Vec3 pos = renderInfo.getPosition();
		this.entityRenderDispatcher
				.render(
						this.itemEntity,
						xx - pos.x(),
						yy - pos.y(),
						zz - pos.z(),
						this.itemEntity.getYRot(),
						partialTicks,
						new PoseStack(),
						source,
						this.entityRenderDispatcher.getPackedLightCoords(this.itemEntity, partialTicks)
				);
		source.endBatch();
	}

	@Override
	public void tick() {
		this.life++;
		if (this.life == LIFE_TIME) {
			this.remove();
		}
	}
}
