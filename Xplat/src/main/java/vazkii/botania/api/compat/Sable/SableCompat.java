package vazkii.botania.api.compat.Sable;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.ryanhcode.sable.companion.ClientSubLevelAccess;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
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
import org.joml.Quaternionf;

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
