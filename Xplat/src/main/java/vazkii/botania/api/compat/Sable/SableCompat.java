package vazkii.botania.api.compat.Sable;

import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

public class SableCompat {
    /**
     * @return true if the given position belongs to a Sable sub-level (i.e. it is stored in the plot
     *         grid), false when it is a regular block in the world or when Sable is not installed.
     */
    public static boolean isOnSubLevel(Level level, BlockPos pos) {
        return SableCompanion.INSTANCE.getContaining(level, pos) != null;
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

    public static AABB transformFromSable(Level level, AABB globalRenderBox) {
        SubLevelAccess subLevelAccess = SableCompanion.INSTANCE.getContaining(level, globalRenderBox.getCenter());
        if (subLevelAccess != null) {
            BoundingBox3d bb = new BoundingBox3d(globalRenderBox);
            globalRenderBox = bb.transform(subLevelAccess.logicalPose(), bb).toMojang();
        }
        return globalRenderBox;
    }
}
