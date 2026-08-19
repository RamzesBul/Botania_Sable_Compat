package vazkii.botania.api.compat.Sable;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.ryanhcode.sable.companion.ClientSubLevelAccess;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import dev.ryanhcode.sable.sublevel.tracking_points.SubLevelTrackingPointSavedData;
import dev.ryanhcode.sable.sublevel.tracking_points.TrackingPoint;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
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
import java.util.UUID;
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
     * @return true if the given position lies in the plot grid, i.e. it is a sub-level's storage coordinate rather
     *         than a world one. Unlike {@link #isOnSubLevel(Level, BlockPos)} this does not require the sub-level to
     *         still exist, so the two together tell a live sub-level block apart from a stale coordinate left behind
     *         by a sub-level that has since been unloaded or taken apart.
     */
    public static boolean isPlotGridPos(Level level, BlockPos pos) {
        return SableCompanion.INSTANCE.isInPlotGrid(level, pos.getX() >> 4, pos.getZ() >> 4);
    }

    /**
     * A resolved Sable tracking point: where it currently is in the world, plus its position in the plot grid of the
     * sub-level holding it - or {@code null} for a point that presently lives in the regular world, whether because it
     * was placed there or because the sub-level it was on has been taken apart.
     */
    public record SubLevelAnchor(Vec3 worldPos, @Nullable BlockPos localPos) {}

    /**
     * Registers a Sable tracking point at the center of {@code pos}. Sable keeps such a point with the blocks around
     * it in both directions: it is carried into the plot when those blocks are assembled into a sub-level (whose
     * plot-grid coordinates are re-assigned on every assembly, so a plain position would go stale), projected back
     * into world space when that sub-level is taken apart, and it survives unloading and saving.
     *
     * <p>Deliberately registered for regular-world positions too, not just for sub-level ones: a point is the only
     * thing that follows the blocks when a player assembles a platform <em>around</em> an already-remembered block, and
     * whether that will happen cannot be known when the position is first remembered.
     *
     * @return the id the point can be resolved with later, or {@code null} when the level is not a server one or Sable
     *         is absent.
     */
    @Nullable
    public static UUID createSubLevelAnchor(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        SubLevelTrackingPointSavedData data = SubLevelTrackingPointSavedData.getOrLoad(serverLevel);
        if (SableCompanion.INSTANCE.getContaining(level, pos) instanceof ServerSubLevel subLevel) {
            return data.generateTrackingPoint(Vec3.atCenterOf(pos), subLevel);
        }
        // Same shape Sable itself gives a point that has just been projected out of a dismantled sub-level.
        Vec3 center = Vec3.atCenterOf(pos);
        UUID anchor = UUID.randomUUID();
        data.setTrackingPoint(anchor, new TrackingPoint(false, null, null,
                new Vector3d(center.x, center.y, center.z), null));
        return anchor;
    }

    /**
     * Drops a tracking point created by {@link #createSubLevelAnchor}. Tracking points live in the level's saved data,
     * so one that is no longer referenced must be removed explicitly.
     */
    public static void removeSubLevelAnchor(Level level, UUID anchor) {
        if (level instanceof ServerLevel serverLevel) {
            SubLevelTrackingPointSavedData.getOrLoad(serverLevel).removeTrackingPoint(anchor);
        }
    }

    /**
     * @return where the tracking point {@code anchor} currently is, or {@code null} when it cannot be resolved right
     *         now - the point is unknown, or the sub-level holding it is neither loaded nor recoverable from storage.
     *         Always {@code null} on the client, where tracking points do not exist.
     */
    @Nullable
    public static SubLevelAnchor resolveSubLevelAnchor(Level level, UUID anchor) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        var taken = SubLevelTrackingPointSavedData.getOrLoad(serverLevel).take(anchor, false);
        if (taken == null) {
            return null;
        }
        Vec3 worldPos = new Vec3(taken.position().x(), taken.position().y(), taken.position().z());
        Vector3d local = taken.localAnchor();
        return new SubLevelAnchor(worldPos, local == null ? null : BlockPos.containing(local.x, local.y, local.z));
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
     * Continuous-position variant of {@link #distanceSqr(Level, Vec3i, Vec3i)}: the world-space squared
     * distance between two exact points, each translated via the pose of the sub-level containing it (or left
     * as-is for regular-world points). Use when either endpoint is a plot-grid coordinate that must be compared
     * against a world position (e.g. the Spectranthemum's teleport cost from its bound block to a nearby item).
     */
    public static double distanceSqr(Level level, Vec3 a, Vec3 b) {
        return SableCompanion.INSTANCE.distanceSquaredWithSubLevels(level, a, b);
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
     * @return true if any sub-level intersects the world-space box spanned by {@code cells} (expanded by one
     *         block), false when the box is empty or no sub-level is near it.
     *
     * <p>Used to decide whether a scan whose result depends on sub-level geometry must be repeated: a sub-level
     * block occupies no world cell (in world coordinates it reads as air), so a check that compares world block
     * states along a path can never notice it move, appear or disappear. Callers that cache such a scan (e.g. the
     * Mana Spreader caching which receiver its burst hit) use this to keep re-running it while a sub-level is in
     * reach, letting the real collision decide again each time instead of trusting a stale result.
     */
    public static boolean anySubLevelNear(Level level, List<BlockPos> cells) {
        if (cells.isEmpty()) {
            return false;
        }
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos cell : cells) {
            minX = Math.min(minX, cell.getX());
            minY = Math.min(minY, cell.getY());
            minZ = Math.min(minZ, cell.getZ());
            maxX = Math.max(maxX, cell.getX());
            maxY = Math.max(maxY, cell.getY());
            maxZ = Math.max(maxZ, cell.getZ());
        }
        BoundingBox3d bounds = new BoundingBox3d(minX - 1, minY - 1, minZ - 1, maxX + 2, maxY + 2, maxZ + 2);
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
        return crossLevelScanPositions(level, center, rangeH, rangeV, rangeV, false);
    }

    /**
     * Like {@link #blockScanPositionsOnOtherSubLevels}, but when {@code center} itself sits on a sub-level this
     * also returns the <em>regular-world</em> cells of the box (as world coords), so a sub-level scanner can see
     * blocks in the surrounding world too. Use this when the search must work in both directions across the
     * world/sub-level boundary (e.g. the Kekimurus eating a cake in the world next to its sub-level, or on a
     * neighbouring sub-level).
     */
    public static List<BlockPos> blockScanPositionsOnOtherLevels(Level level, BlockPos center, int rangeH, int rangeV) {
        return blockScanPositionsOnOtherLevels(level, center, rangeH, rangeV, rangeV);
    }

    /**
     * As {@link #blockScanPositionsOnOtherLevels(Level, BlockPos, int, int)}, but with an asymmetric vertical
     * box ({@code rangeDown} below and {@code rangeUp} above {@code center}), matching
     * {@link vazkii.botania.common.helper.MathHelper#aroundPosClosed(BlockPos, int, int, int)}. Used by the
     * Munchdew, whose leaf search is tall and one-sided ({@code 0} down, {@code RANGE_Y} up).
     */
    public static List<BlockPos> blockScanPositionsOnOtherLevels(Level level, BlockPos center, int rangeH, int rangeDown, int rangeUp) {
        return crossLevelScanPositions(level, center, rangeH, rangeDown, rangeUp, true);
    }

    private static List<BlockPos> crossLevelScanPositions(Level level, BlockPos center, int rangeH, int rangeDown, int rangeUp, boolean includeWorld) {
        SubLevelAccess own = SableCompanion.INSTANCE.getContaining(level, center);

        // World-space centers of every scanned cell (rotated with the scanner's sub-level if it is on one).
        List<Vec3> worldCells = new ArrayList<>();
        for (int dx = -rangeH; dx <= rangeH; dx++) {
            for (int dy = -rangeDown; dy <= rangeUp; dy++) {
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
        double radius = Math.max(rangeH, Math.max(rangeDown, rangeUp)) + 2.0;
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

    /**
     * Whether {@code point} lies in the plot slot of the sub-level containing {@code sourcePos}, i.e. whether
     * it is expressed in that sub-level's logical (plot-grid) coordinates rather than in world ones. Sable
     * keeps an entity in the sub-level's local space only while its type is in {@code #sable:retain_in_sub_level};
     * otherwise it relocates the entity to world space as it is added to the level. This tells the two apart
     * without guessing, since world coordinates can never fall inside a plot slot.
     */
    public static boolean isInSubLevelFrame(Level level, BlockPos sourcePos, Vec3 point) {
        return SableCompanion.INSTANCE.getContaining(level, sourcePos) instanceof SubLevel subLevel
                && subLevel.getPlot().contains(point);
    }

    /**
     * Whether {@code localPoint} (logical coords) is still within the built bounds of the sub-level containing
     * {@code sourcePos}. Compares logical position to logical bounds: both are independent of the sub-level's
     * pose, so a fast-moving sub-level cannot desync the check the way a world-space comparison would.
     * {@code true} when {@code sourcePos} is not on a sub-level, or its bounds are not known yet.
     */
    public static boolean isOverSubLevel(Level level, BlockPos sourcePos, Vec3 localPoint) {
        if (!(SableCompanion.INSTANCE.getContaining(level, sourcePos) instanceof SubLevel subLevel)) {
            return true;
        }
        BoundingBox3ic bounds = subLevel.getPlot().getBoundingBox();
        return bounds == null || bounds.contains(new Vector3d(localPoint.x, localPoint.y, localPoint.z));
    }

    /**
     * Hands {@code entity} to Sable to be kicked out of the sub-level containing {@code sourcePos}: its position,
     * velocity and facing are transformed from the sub-level's logical space into world space, and the sub-level's
     * own velocity is added on, so an entity leaving a moving platform keeps the platform's momentum.
     */
    public static void kickOutOfSubLevel(Level level, BlockPos sourcePos, Entity entity) {
        if (SableCompanion.INSTANCE.getContaining(level, sourcePos) instanceof SubLevel subLevel) {
            EntitySubLevelUtil.kickEntity(subLevel, entity);
        }
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
     * Client render-time variant of {@link #transformFromSable(Level, Vec3, Vec3)}: translates {@code pos} into
     * world space via the sub-level's interpolated {@code renderPose} (the exact per-frame pose its blocks
     * render with) instead of the tick-synced {@code logicalPose}. Returns {@code pos} unchanged when
     * {@code root} is a regular-world block or the sub-level is not a client one. Use for smooth per-frame
     * effects that must line up with the (possibly moving) sub-level, e.g. the Hopperhock item-pickup animation.
     */
    public static Vec3 transformFromSableRender(Level level, Vec3 pos, BlockPos root, float partialTick) {
        SubLevelAccess subLevelAccess = SableCompanion.INSTANCE.getContaining(level, root);
        if (!(subLevelAccess instanceof ClientSubLevelAccess clientSubLevel)) {
            return pos;
        }
        return clientSubLevel.renderPose(partialTick).transformPosition(pos);
    }

    /**
     * Resolves a single scan cell given in the scanner's own frame ({@code anchor}'s sub-level, or the world)
     * into the storage position of whichever level physically occupies that world cell: the plot-grid position
     * of the sub-level covering it (the scanner's own or a neighbouring one), or the world position when no
     * sub-level covers it. Lets a column/point scan cross the world&lt;-&gt;sub-level boundary while still being
     * fed to plain {@code level.getBlockState}/grow calls, since sub-level blocks are real chunks of the level.
     *
     * <p>Unlike {@link #blockScanPositionsOnOtherLevels}, this includes the scanner's <em>own</em> sub-level, so
     * a cell that stays over it round-trips back to its original plot-grid position (no behavior change on the
     * own level).
     */
    public static BlockPos resolveCellAcrossLevels(Level level, BlockPos anchor, BlockPos localCell) {
        SubLevelAccess own = SableCompanion.INSTANCE.getContaining(level, anchor);
        Vec3 worldCell = own != null
                ? own.logicalPose().transformPosition(Vec3.atCenterOf(localCell))
                : Vec3.atCenterOf(localCell);

        BoundingBox3d box = new BoundingBox3d(
                worldCell.x - 0.5, worldCell.y - 0.5, worldCell.z - 0.5,
                worldCell.x + 0.5, worldCell.y + 0.5, worldCell.z + 0.5);
        for (SubLevelAccess access : SableCompanion.INSTANCE.getAllIntersecting(level, box)) {
            if (!(access instanceof SubLevel sub) || sub.getPlot().getBoundingBox() == null) {
                continue;
            }
            Vector3d localPos = sub.logicalPose().transformPositionInverse(new Vector3d(worldCell.x, worldCell.y, worldCell.z));
            if (sub.getPlot().getBoundingBox().contains(localPos)) {
                return BlockPos.containing(localPos.x, localPos.y, localPos.z);
            }
        }
        return BlockPos.containing(worldCell.x, worldCell.y, worldCell.z);
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
     * Rotates a direction vector from the local frame of the sub-level containing {@code anchor} into world
     * space, applying only the sub-level's orientation (no translation). Returns {@code dir} unchanged when
     * {@code anchor} is a regular-world block (or Sable is absent).
     *
     * <p>Unlike {@link #transformFromSable(Level, Vec3, Vec3)}, which moves a <em>point</em>, this moves a
     * <em>direction</em>: use it for effects whose facing rotates with the sub-level, e.g. the Daffomill's wind
     * push, whose {@code Direction.getStep*()} vector is expressed in the flower's local facing and must be
     * rotated to world space so items are blown the way the (possibly rotated) flower actually points.
     */
    public static Vec3 transformDirectionFromSable(Level level, Vec3 dir, BlockPos anchor) {
        SubLevelAccess sub = SableCompanion.INSTANCE.getContaining(level, anchor);
        if (sub == null) {
            return dir;
        }
        Vector3d out = sub.logicalPose().orientation().transform(new Vector3d(dir.x, dir.y, dir.z));
        return new Vec3(out.x, out.y, out.z);
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
