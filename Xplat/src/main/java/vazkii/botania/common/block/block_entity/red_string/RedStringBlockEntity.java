/*
 * This class is distributed as part of the Botania Mod.
 * Get the Source Code in github:
 * https://github.com/Vazkii/Botania
 *
 * Botania is Open Source and distributed under the
 * Botania License: http://botaniamod.net/license.php
 */
package vazkii.botania.common.block.block_entity.red_string;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import org.jetbrains.annotations.Nullable;

import vazkii.botania.api.block.Bound;
import vazkii.botania.api.compat.Sable.SableCompat;

import java.util.Objects;

public abstract class RedStringBlockEntity extends BlockEntity implements Bound {
	@Nullable
	private BlockPos binding;

	protected RedStringBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	public static void commonTick(Level level, BlockPos pos_, BlockState state, RedStringBlockEntity self) {
		Direction dir = self.getOrientation();
		int range = self.getRange();
		BlockPos currBinding = self.getBinding();
		self.setBinding(null);

		for (int i = 0; i < range; i++) {
			pos_ = pos_.relative(dir);
			// Resolve each stepped cell to whichever level physically occupies it, so the binding ray can leave
			// the block's own sub-level and reach an inventory in the surrounding world (or on a neighbouring
			// sub-level) that sits in front of it. Sub-level blocks are real chunks at their plot-grid coords, so
			// the resolved position feeds the block/inventory lookups (and is stored as the binding) unchanged.
			// A no-op when the block is a regular-world block, leaving same-level binding untouched.
			BlockPos resolved = SableCompat.resolveCellAcrossLevels(level, self.getBlockPos(), pos_);
			if (level.isEmptyBlock(resolved)) {
				continue;
			}

			BlockEntity tile = level.getBlockEntity(resolved);
			if (tile instanceof RedStringBlockEntity) {
				continue;
			}

			if (self.acceptBlock(resolved)) {
				self.setBinding(resolved);
				if (!Objects.equals(currBinding, resolved)) {
					self.onBound(resolved);
				}
				return;
			}
		}
		if (!level.isClientSide() && !Objects.equals(currBinding, self.binding)) {
			self.onBound(self.binding);
		}
	}

	public int getRange() {
		return 8;
	}

	public abstract boolean acceptBlock(BlockPos pos);

	public void onBound(@Nullable BlockPos pos) {}

	@Nullable
	@Override
	public BlockPos getBinding() {
		return binding;
	}

	public void setBinding(@Nullable BlockPos binding) {
		this.binding = binding;
	}

	public Direction getOrientation() {
		return getBlockState().getValue(BlockStateProperties.FACING);
	}

	public BlockState getStateAtBinding() {
		BlockPos binding = getBinding();
		return binding == null ? Blocks.AIR.defaultBlockState() : level.getBlockState(binding);
	}

}
