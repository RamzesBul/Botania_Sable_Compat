/*
 * This class is distributed as part of the Botania Mod.
 * Get the Source Code in github:
 * https://github.com/Vazkii/Botania
 *
 * Botania is Open Source and distributed under the
 * Botania License: http://botaniamod.net/license.php
 */
package vazkii.botania.common.block.block_entity.flower.functional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

import vazkii.botania.api.block.Wandable;
import vazkii.botania.api.block_entity.FunctionalFlowerBlockEntity;
import vazkii.botania.api.block_entity.RadiusDescriptor;
import vazkii.botania.api.compat.Sable.SableCompat;
import vazkii.botania.client.fx.WispParticleData;
import vazkii.botania.common.block.block_entity.BotaniaBlockEntities;
import vazkii.botania.common.helper.NbtHelper;
import vazkii.botania.common.internal_caps.ItemLifetime;

import java.util.List;

public class DaffomillBlockEntity extends FunctionalFlowerBlockEntity implements Wandable {
	private static final String TAG_WIND_TICKS = "windTicks";

	private int windTicks = 0;

	public DaffomillBlockEntity(BlockPos pos, BlockState state) {
		super(BotaniaBlockEntities.DAFFOMILL, pos, state);
	}

	@Override
	public void tickFlower() {
		super.tickFlower();

		if (isPowered()) {
			return;
		}

		var orientation = getOrientation();
		if (level.isClientSide() && level.getRandom().nextInt(4) == 0) {
			WispParticleData data = WispParticleData.wisp(0.25F + (float) Math.random() * 0.15F, 0.05F, 0.05F, 0.05F);
			emitParticle(data, Math.random(), Math.random(), Math.random(), orientation.getStepX() * 0.1F, orientation.getStepY() * 0.1F, orientation.getStepZ() * 0.1F);
		}

		if (windTicks == 0 && getMana() > 0) {
			windTicks = 20;
			addMana(-1);
			// send wind ticks refresh
			markForImmediateSync();
		}

		if (windTicks > 0) {
			// aabbForOrientation() is the wind box in the flower's own frame (plot-grid coords, oriented by its
			// local facing). Items are world-space entities even when riding a sub-level, so search the box's
			// world-space bounds, then confirm each hit against the exact (possibly rotated) local box, and push
			// along the local facing rotated into world space. All three steps are no-ops in the regular world.
			AABB axis = aabbForOrientation();

			if (axis != null) {
				AABB worldBox = SableCompat.transformFromSable(getLevel(), axis);
				List<ItemEntity> items = getLevel().getEntitiesOfClass(ItemEntity.class, worldBox,
						itemEntity -> ItemLifetime.canInteractWithImmediate(this, itemEntity));
				Vec3 push = SableCompat.transformDirectionFromSable(getLevel(),
						new Vec3(orientation.getStepX(), orientation.getStepY(), orientation.getStepZ()), getEffectivePos());
				double v = 0.05;
				for (ItemEntity item : items) {
					Vec3 local = SableCompat.toSableLocalFrame(getLevel(), item.position(), getEffectivePos());
					if (!axis.contains(local)) {
						continue;
					}
					item.setDeltaMovement(item.getDeltaMovement().add(push.scale(v)));
				}
			}

			windTicks--;
			markForPersisting();
		}
	}

	public Direction getOrientation() {
		return getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
	}

	@Nullable
	private AABB aabbForOrientation() {
		int x = getEffectivePos().getX();
		int y = getEffectivePos().getY();
		int z = getEffectivePos().getZ();
		int w = 2;
		int h = 3;
		int l = 16;

		return switch (getOrientation()) {
			case NORTH -> new AABB(x - w, y - h, z - l, x + w + 1, y + h, z);
			case SOUTH -> new AABB(x - w, y - h, z + 1, x + w + 1, y + h, z + l + 1);
			case WEST -> new AABB(x - l, y - h, z - w, x, y + h, z + w + 1);
			case EAST -> new AABB(x + 1, y - h, z - w, x + l + 1, y + h, z + w + 1);
			default -> null;
		};
	}

	@Override
	public boolean onUsedByWand(@Nullable Player player, ItemStack wand, Direction side) {
		if (player == null || !player.isShiftKeyDown()) {
			return false;
		}

		if (!player.level().isClientSide()) {
			// TODO: should this make some kind of sound and/or indicate the new orientation more clearly?
			level.setBlock(getBlockPos(),
					getBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, getOrientation().getClockWise()),
					Block.UPDATE_CLIENTS);
		}

		return true;
	}

	@Override
	public RadiusDescriptor getRadius() {
		AABB aabb = aabbForOrientation();
		aabb = new AABB(aabb.minX, getEffectivePos().getY(), aabb.minZ, aabb.maxX, aabb.maxY, aabb.maxZ);
		return new RadiusDescriptor.Rectangle(getEffectivePos(), aabb);
	}

	@Override
	public int getColor() {
		return 0xD8BA00;
	}

	@Override
	public int getMaxMana() {
		return 100;
	}

	@Override
	protected void saveAdditional(CompoundTag cmp, HolderLookup.Provider registries) {
		super.saveAdditional(cmp, registries);

		cmp.putInt(TAG_WIND_TICKS, windTicks);
	}

	@Override
	protected void loadAdditional(CompoundTag cmp, HolderLookup.Provider registries) {
		super.loadAdditional(cmp, registries);

		windTicks = cmp.getInt(TAG_WIND_TICKS);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		var tag = super.getUpdateTag(registries);
		NbtHelper.putVarInt(tag, TAG_WIND_TICKS, windTicks);
		return tag;
	}
}
