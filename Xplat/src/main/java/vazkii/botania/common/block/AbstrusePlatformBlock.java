/*
 * This class is distributed as part of the Botania Mod.
 * Get the Source Code in github:
 * https://github.com/Vazkii/Botania
 *
 * Botania is Open Source and distributed under the
 * Botania License: http://botaniamod.net/license.php
 *
 */

package vazkii.botania.common.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jetbrains.annotations.Nullable;

import vazkii.botania.api.compat.Sable.SableCompat;

public class AbstrusePlatformBlock extends PlatformBlock {
    public AbstrusePlatformBlock(Properties builder) {
        super(builder);
    }

    @Override
    public boolean hasDynamicShape() {
        // Disable the vanilla collision-shape cache. Sable's entity collision on a sub-level asks for
        // the shape through the two-argument state.getCollisionShape(level, pos), which returns the
        // cached (empty-context) shape and never reaches our context-aware override below. Marking the
        // shape dynamic makes that call delegate to getCollisionShape(state, world, pos, context) so we
        // can actually make the block passable on sub-levels.
        return true;
    }

    @Override
    public boolean testCollision(BlockPos pos, CollisionContext context) {
        if (context instanceof EntityCollisionContext entityCollisionContext) {
            Entity e = entityCollisionContext.getEntity();
            return (e == null || e.getY() > pos.getY() + 0.9 && !context.isDescending());
        }
        return true;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        // On a sub-level, Sable resolves entity collision through SubLevelEntityCollision, which asks
        // for the shape with an entity-less context (state.getCollisionShape(level, pos)). Our one-way
        // testCollision then can't see the entity and falls back to a full solid block, so the platform
        // becomes impassable from every side. Since the approach direction is unknowable without the
        // entity, we make the block passable on sub-levels so entities can walk through it. The sub-level
        // physics body is unaffected: it is baked with a SableCollisionContext (not an EntityCollisionContext)
        // and therefore still reports a full cube, so the sub-level keeps resting on the world normally.
        if (context instanceof EntityCollisionContext ecc && ecc.getEntity() == null && isOnSubLevel(world, pos)) {
            return Shapes.empty();
        }
        return super.getCollisionShape(state, world, pos, context);
    }

    private static boolean isOnSubLevel(BlockGetter world, BlockPos pos) {
        Level level = resolveLevel(world, pos);
        return level != null && SableCompat.isOnSubLevel(level, pos);
    }

    @Nullable
    private static Level resolveLevel(BlockGetter world, BlockPos pos) {
        if (world instanceof Level level) {
            return level;
        }
        // The entity-collision path passes a BlockGetter wrapper rather than the Level itself; recover
        // the Level from our own block entity, which always lives in the shared (plot-grid) level.
        BlockEntity be = world.getBlockEntity(pos);
        return be != null ? be.getLevel() : null;
    }
}