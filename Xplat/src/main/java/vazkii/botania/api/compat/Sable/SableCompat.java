package vazkii.botania.api.compat.Sable;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.ryanhcode.sable.companion.ClientSubLevelAccess;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

public class SableCompat {
    /**
     * @return true if the given position belongs to a Sable sub-level (i.e. it is stored in the plot
     *         grid), false when it is a regular block in the world or when Sable is not installed.
     */
    public static boolean isOnSubLevel(Level level, BlockPos pos) {
        return SableCompanion.INSTANCE.getContaining(level, pos) != null;
    }

    /**
     * Squared distance between two block positions, accounting for Sable sub-level poses: each point
     * is translated into world space via the pose of the sub-level containing it (or left as-is when
     * it is a regular world block / Sable is absent). Use this instead of a raw coordinate distance
     * whenever the two positions may live on different levels/sub-levels (e.g. flower <-> spreader
     * binding), since sub-level blocks are stored in plot-grid coordinates unrelated to world space.
     */
    public static double distanceSqr(Level level, Vec3i a, Vec3i b) {
        return SableCompanion.INSTANCE.distanceSquaredWithSubLevels(level,
                a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ());
    }

    /**
     * Feeds every loaded block-entity of sub-levels whose world bounds are within {@code radius} blocks
     * of {@code worldCenter} (a world-space point) to {@code consumer}. Block-entities living on a
     * sub-level are stored in plot-grid coordinates far from world space, so a normal world-space chunk
     * scan cannot see them; this walks the plot chunks of each nearby sub-level instead.
     *
     * <p>No-op when Sable is absent ({@code getAllIntersecting} returns an empty iterable). Used to make
     * flower&lt;-&gt;target auto-binding work across levels/sub-levels.
     */
    public static void forEachBlockEntityInNearbySubLevels(Level level, Vec3 worldCenter, double radius,
            BiConsumer<BlockPos, BlockEntity> consumer) {
        BoundingBox3d bounds = new BoundingBox3d(
                worldCenter.x - radius, worldCenter.y - radius, worldCenter.z - radius,
                worldCenter.x + radius, worldCenter.y + radius, worldCenter.z + radius);
        for (SubLevelAccess access : SableCompanion.INSTANCE.getAllIntersecting(level, bounds)) {
            // Only the main SubLevel implementation exposes the plot with its stored block-entities.
            if (!(access instanceof SubLevel sub)) {
                continue;
            }
            LevelPlot plot = sub.getPlot();
            for (PlotChunkHolder holder : plot.getLoadedChunks()) {
                LevelChunk chunk = holder.getChunk();
                if (chunk != null) {
                    chunk.getBlockEntities().forEach(consumer);
                }
            }
        }
    }

    /**
     * Feeds every loaded block-entity of the regular world (excluding plot-grid chunks, which belong to
     * sub-levels) within {@code radius} blocks of {@code worldCenter} to {@code consumer}. Lets a flower
     * that itself sits on a sub-level discover targets in the surrounding world.
     */
    public static void forEachWorldBlockEntityNear(Level level, Vec3 worldCenter, double radius,
            BiConsumer<BlockPos, BlockEntity> consumer) {
        int r = Mth.ceil(radius);
        int minChunkX = Mth.floor(worldCenter.x - r) >> 4;
        int maxChunkX = Mth.floor(worldCenter.x + r) >> 4;
        int minChunkZ = Mth.floor(worldCenter.z - r) >> 4;
        int maxChunkZ = Mth.floor(worldCenter.z + r) >> 4;
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                // Sub-level (plot-grid) chunks are handled by forEachBlockEntityInNearbySubLevels.
                if (SableCompanion.INSTANCE.isInPlotGrid(level, cx, cz)) {
                    continue;
                }
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk != null) {
                    chunk.getBlockEntities().forEach(consumer);
                }
            }
        }
    }

    /**
     * @return true if the given position is on a sub-level, or any sub-level is within {@code radius}
     *         blocks of its world position. Used to gate the periodic re-binding of flowers to Sable
     *         contexts, so regular-world behavior is left unchanged.
     */
    public static boolean hasSubLevelContext(Level level, BlockPos pos, double radius) {
        if (isOnSubLevel(level, pos)) {
            return true;
        }
        Vec3 worldCenter = transformFromSable(level, Vec3.atCenterOf(pos));
        BoundingBox3d bounds = new BoundingBox3d(
                worldCenter.x - radius, worldCenter.y - radius, worldCenter.z - radius,
                worldCenter.x + radius, worldCenter.y + radius, worldCenter.z + radius);
        return SableCompanion.INSTANCE.getAllIntersecting(level, bounds).iterator().hasNext();
    }

    /**
     * Resolves the flower's 3x3 fluid-scan cells in world space and returns the plot-grid positions of the
     * cells that are physically occupied by <em>other</em> sub-levels than the one holding the flower.
     *
     * <p>A fluid generator (e.g. Hydroangeas) scans the 3x3 around itself with {@code level.getFluidState(pos)}.
     * On a sub-level those {@code pos} are plot-grid coords, so the scan sees water on the flower's own
     * sub-level (its blocks share the level's chunks at those coords) but never water on a neighbouring
     * sub-level, whose blocks live at unrelated plot-grid coords even when it is physically adjacent in the
     * world. This maps each of the 9 cells into world space (via the flower sub-level's pose, or straight
     * world coords when the flower is a regular-world block) and, for every other sub-level overlapping that
     * area, inverse-transforms the cell into its plot grid. The returned positions can be fed to the same
     * {@code level.getFluidState}/{@code pickupBlock} calls unchanged, since sub-level blocks are stored in the
     * level's chunks at their plot-grid coords.
     *
     * @return the extra plot-grid positions to also scan; empty when Sable is absent or no other sub-level is
     *         adjacent.
     */
    public static List<BlockPos> fluidScanPositionsOnOtherSubLevels(Level level, BlockPos flowerPos) {
        // The fluid scan is a flat 3x3 in the flower's X/Z plane (no vertical spread).
        return blockScanPositionsOnOtherSubLevels(level, flowerPos, 1, 0);
    }

    /**
     * Resolves a box-shaped block scan ({@code center} ± {@code rangeH} horizontally, ± {@code rangeV}
     * vertically, in the scanner's own frame) in world space and returns the plot-grid positions of the cells
     * that are physically occupied by <em>other</em> sub-levels than the one holding {@code center}.
     *
     * <p>Block-scanning game logic (fluid generators, the Alfheim portal's pylon search, ...) walks a box of
     * {@code pos} around itself and queries {@code level.getBlockState(pos)} / {@code getBlockEntity(pos)}. On a
     * sub-level those {@code pos} are plot-grid coords, so the scan sees blocks on its own sub-level (they share
     * the level's chunks at those coords) but never blocks on a neighbouring sub-level, whose blocks live at
     * unrelated plot-grid coords even when physically adjacent in the world. This maps each cell into world
     * space (via {@code center}'s sub-level pose, or straight world coords when {@code center} is a
     * regular-world block) and, for every other sub-level overlapping that area, inverse-transforms the cell
     * into its plot grid. The returned positions can be fed to the same {@code level.getBlockState} /
     * {@code getBlockEntity} / {@code pos.below()} calls unchanged, since sub-level blocks are stored in the
     * level's chunks at their plot-grid coords (and {@code below()} is the sub-level's own local down).
     *
     * @return the extra plot-grid positions to also scan; empty when Sable is absent or no other sub-level is
     *         adjacent.
     */
    public static List<BlockPos> blockScanPositionsOnOtherSubLevels(Level level, BlockPos center, int rangeH, int rangeV) {
        return crossLevelScanPositions(level, center, rangeH, rangeV, false);
    }

    /**
     * Like {@link #blockScanPositionsOnOtherSubLevels}, but when {@code center} itself sits on a sub-level this
     * also returns the <em>regular-world</em> cells of the box (as world coords), so a sub-level scanner can see
     * blocks in the surrounding world too. Use this when the search must work in both directions across the
     * world/sub-level boundary (e.g. the Kekimurus eating a cake in the world next to its sub-level, or on a
     * neighbouring sub-level).
     */
    public static List<BlockPos> blockScanPositionsOnOtherLevels(Level level, BlockPos center, int rangeH, int rangeV) {
        return crossLevelScanPositions(level, center, rangeH, rangeV, true);
    }

    private static List<BlockPos> crossLevelScanPositions(Level level, BlockPos center, int rangeH, int rangeV, boolean includeWorld) {
        SubLevelAccess own = SableCompanion.INSTANCE.getContaining(level, center);

        // World-space centers of every scanned cell (rotated with the scanner's sub-level if it is on one).
        List<Vec3> worldCells = new ArrayList<>();
        for (int dx = -rangeH; dx <= rangeH; dx++) {
            for (int dy = -rangeV; dy <= rangeV; dy++) {
                for (int dz = -rangeH; dz <= rangeH; dz++) {
                    Vec3 cellCenter = Vec3.atCenterOf(center.offset(dx, dy, dz));
                    worldCells.add(own != null ? own.logicalPose().transformPosition(cellCenter) : cellCenter);
                }
            }
        }

        Vec3 centerWorld = own != null
                ? own.logicalPose().transformPosition(Vec3.atCenterOf(center))
                : Vec3.atCenterOf(center);
        // Generous enough to reach any sub-level whose blocks could fall within the (possibly rotated) box.
        double radius = Math.max(rangeH, rangeV) + 2.0;
        BoundingBox3d worldBox = new BoundingBox3d(
                centerWorld.x - radius, centerWorld.y - radius, centerWorld.z - radius,
                centerWorld.x + radius, centerWorld.y + radius, centerWorld.z + radius);

        // Collect the other sub-levels overlapping the box once, then resolve each cell against them.
        List<SubLevel> others = new ArrayList<>();
        for (SubLevelAccess access : SableCompanion.INSTANCE.getAllIntersecting(level, worldBox)) {
            if (access != own && access instanceof SubLevel sub && sub.getPlot().getBoundingBox() != null) {
                others.add(sub);
            }
        }

        List<BlockPos> result = new ArrayList<>();
        for (Vec3 worldCell : worldCells) {
            BlockPos resolved = null;
            for (SubLevel sub : others) {
                Vector3d localPos = sub.logicalPose().transformPositionInverse(new Vector3d(worldCell.x, worldCell.y, worldCell.z));
                if (sub.getPlot().getBoundingBox().contains(localPos)) {
                    resolved = BlockPos.containing(localPos.x, localPos.y, localPos.z);
                    break;
                }
            }
            if (resolved != null) {
                result.add(resolved);
            } else if (includeWorld && own != null) {
                // Cell is over the regular world; the scanner is on a sub-level, so its own-frame scan does not
                // cover the world - add the world position so world blocks get seen too.
                result.add(BlockPos.containing(worldCell.x, worldCell.y, worldCell.z));
            }
        }
        return result;
    }

    /**
     * Decides whether a burst that is still travelling in the local space of the sub-level containing
     * {@code sourcePos} has now left that sub-level's world bounds, and if so returns the world-space
     * position and velocity to continue with.
     *
     * <p>A real burst starts life on the sub-level (logical coords) so it moves together with the
     * (possibly moving) sub-level and can hit pools on it. Once it leaves the sub-level's bounds it would
     * otherwise freeze in unloaded plot chunks, so we project it into world space to finish its flight.
     *
     * @return {@code [worldPosition, worldVelocity]} when the burst should be projected out, or
     *         {@code null} when it is still over the sub-level (or {@code sourcePos} is not on a sub-level).
     */
    /**
     * Recomputes the world bounding box of the sub-level containing {@code pos} from its current pose.
     * Sable's raycast override finds the sub-levels a ray crosses via {@code getAllIntersecting}, which
     * tests each sub-level's cached world bounding box. On a fast-moving sub-level that box can lag the
     * current pose by a tick, so a burst-scan raycast started mid-tick (e.g. the spreader's receiver check)
     * fails to find the sub-level and misses the pool. Refreshing the box right before the scan avoids that.
     */
    public static void refreshSubLevelBounds(Level level, BlockPos pos) {
        if (SableCompanion.INSTANCE.getContaining(level, pos) instanceof SubLevel subLevel) {
            subLevel.updateBoundingBox();
        }
    }

    @Nullable
    public static Vec3[] projectBurstOutOfSubLevel(Level level, BlockPos sourcePos, Vec3 localPos, Vec3 localVel) {
        if (!(SableCompanion.INSTANCE.getContaining(level, sourcePos) instanceof SubLevel subLevel)) {
            return null;
        }
        // Compare the burst's own (logical/plot-grid) position to the plot's logical bounds. Both are
        // independent of the sub-level's pose, so a fast-moving sub-level cannot desync the check.
        // Comparing the world position to the world bounding box could: they are recomputed from the pose
        // each tick, and a one-tick pose lag on a fast sub-level pushes the point out of the box, projecting
        // the burst before it reached the pool.
        BoundingBox3ic plotBounds = subLevel.getPlot().getBoundingBox();
        if (plotBounds == null || plotBounds.contains(new Vector3d(localPos.x, localPos.y, localPos.z))) {
            return null; // still inside the sub-level (or bounds unknown: stay local)
        }
        Pose3dc pose = subLevel.logicalPose();
        Vec3 worldPos = pose.transformPosition(localPos);
        Vector3d worldVel = pose.orientation().transform(new Vector3d(localVel.x, localVel.y, localVel.z));
        return new Vec3[] { worldPos, new Vec3(worldVel.x, worldVel.y, worldVel.z) };
    }

    public static BlockPos transformFromSable(Level level, BlockPos pos, BlockPos root) {
        SubLevelAccess subLevelAccess = SableCompanion.INSTANCE.getContaining(level, root);
        if (subLevelAccess == null)
            return pos;
        Pose3dc pose = subLevelAccess.logicalPose();
        return BlockPos.containing(pose.transformPosition(Vec3.atCenterOf(pos)));
    }

    public static BlockPos transformFromSable(Level level, BlockPos pos) {
        return transformFromSable(level, pos, pos);
    }

    public static Vec3 transformFromSable(Level level, Vec3 pos, Vec3 root) {
        SubLevelAccess subLevelAccess = SableCompanion.INSTANCE.getContaining(level, root);
        if (subLevelAccess == null)
            return pos;
        Pose3dc pose = subLevelAccess.logicalPose();
        return pose.transformPosition(pos);
    }

    public static Vec3 transformFromSable(Level level, Vec3 pos) {
        return transformFromSable(level, pos, pos);
    }

    /**
     * Expresses a world-space point in the local (plot-grid) coordinate frame of the sub-level containing
     * {@code frameAnchor}. Returns {@code worldPoint} unchanged when {@code frameAnchor} is a regular-world
     * block (or Sable is absent) — the inverse of {@link #transformFromSable(Level, Vec3, Vec3)}.
     *
     * <p>Used by the Alfheim portal pylon beam so its particles can be spawned in the <em>portal's</em> frame:
     * the portal center is fixed there, so a particle bound to that sub-level keeps flying toward the current
     * portal position even as the sub-level moves/rotates, instead of chasing where the portal used to be.
     */
    public static Vec3 toSableLocalFrame(Level level, Vec3 worldPoint, BlockPos frameAnchor) {
        SubLevelAccess sub = SableCompanion.INSTANCE.getContaining(level, frameAnchor);
        return sub == null ? worldPoint : sub.logicalPose().transformPositionInverse(worldPoint);
    }

    /**
     * @return whether {@code targetPos} lies within {@code range} (Chebyshev / cube distance) of
     *         {@code anchorPos} when both are measured in {@code anchorPos}'s coordinate frame. {@code targetPos}
     *         may live on a different sub-level: it is transformed to world space and then into
     *         {@code anchorPos}'s frame, so the check tracks the two sub-levels' current poses. When both are in
     *         the same frame (regular world, or the same sub-level) this reduces to the plain cube test used by
     *         the vanilla scan, so it does not change same-frame behavior.
     *
     * <p>Used to break/restore the Alfheim portal &lt;-&gt; pylon link when their sub-levels drift apart or back
     * together.
     */
    public static boolean isWithinRange(Level level, BlockPos anchorPos, BlockPos targetPos, int range) {
        Vec3 targetWorld = transformFromSable(level, Vec3.atCenterOf(targetPos));
        Vec3 targetInAnchorFrame = toSableLocalFrame(level, targetWorld, anchorPos);
        Vec3 anchorCenter = Vec3.atCenterOf(anchorPos);
        double dx = Math.abs(targetInAnchorFrame.x - anchorCenter.x);
        double dy = Math.abs(targetInAnchorFrame.y - anchorCenter.y);
        double dz = Math.abs(targetInAnchorFrame.z - anchorCenter.z);
        // +0.5 tolerance keeps cube-edge pylons included despite rotation / rounding.
        return Math.max(dx, Math.max(dy, dz)) <= range + 0.5;
    }

    public static Quaternionf transformCameraOrientation(EntityRenderDispatcher instance, BlockEntity entity) {
        SubLevelAccess subLevel = SableCompanion.INSTANCE.getContaining(entity);

        if (subLevel == null) {
            return instance.cameraOrientation();
        }

        Quaternionf subLevelOrientation = new Quaternionf(subLevel.logicalPose().orientation());
        return new Quaternionf(subLevelOrientation).conjugate().mul(instance.cameraOrientation());
    }

    public static Quaternionf transformCameraOrientation(EntityRenderDispatcher instance, Entity entity) {
        SubLevelAccess subLevel = SableCompanion.INSTANCE.getContaining(entity);

        if (subLevel == null) {
            return instance.cameraOrientation();
        }

        Quaternionf subLevelOrientation = new Quaternionf(subLevel.logicalPose().orientation());
        return new Quaternionf(subLevelOrientation).conjugate().mul(instance.cameraOrientation());
    }

    /**
     * Positions the given {@link PoseStack} at the sub-level block {@code pos}, applying the exact
     * render pose (interpolated with the current frame's partial-tick) that Sable uses to render the
     * sub-level's blocks. After this call, geometry defined in the block's local 0..1 coordinates
     * (e.g. a {@link net.minecraft.world.phys.shapes.VoxelShape}) renders locked to the moving
     * sub-level block, with no offset or jitter.
     *
     * <p>Mirrors Sable's own block-decal rendering: origin at {@code renderPose.transformPosition(pos)}
     * followed by the render orientation.
     *
     * @return true if {@code pos} belongs to a (client) sub-level and the pose was applied, false
     *         otherwise (in which case the caller should apply the regular camera-relative translation).
     */
    public static boolean applySubLevelPose(PoseStack ms, Level level, BlockPos pos, Vec3 cameraPos) {
        SubLevelAccess subLevelAccess = SableCompanion.INSTANCE.getContaining(level, pos);
        if (!(subLevelAccess instanceof ClientSubLevelAccess clientSubLevel)) {
            return false;
        }
        Pose3dc renderPose = clientSubLevel.renderPose();
        Vec3 projectedPos = renderPose.transformPosition(new Vec3(pos.getX(), pos.getY(), pos.getZ()));

        ms.translate(projectedPos.x - cameraPos.x, projectedPos.y - cameraPos.y, projectedPos.z - cameraPos.z);
        ms.mulPose(new Quaternionf(renderPose.orientation()));
        return true;
    }

    public static AABB transformFromSable(Level level, AABB globalRenderBox) {
        SubLevelAccess subLevelAccess = SableCompanion.INSTANCE.getContaining(level, globalRenderBox.getCenter());
        if (subLevelAccess != null) {
            BoundingBox3d bb = new BoundingBox3d(globalRenderBox);
            globalRenderBox = bb.transform(subLevelAccess.logicalPose(), bb).toMojang();
        }
        return globalRenderBox;
    }
}
