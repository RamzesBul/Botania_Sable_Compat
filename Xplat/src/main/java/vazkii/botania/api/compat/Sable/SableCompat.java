package vazkii.botania.api.compat.Sable;

import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class SableCompat {
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

    public static AABB transformFromSable(Level level, AABB globalRenderBox) {
        SubLevelAccess subLevelAccess = SableCompanion.INSTANCE.getContaining(level, globalRenderBox.getCenter());
        if (subLevelAccess != null) {
            BoundingBox3d bb = new BoundingBox3d(globalRenderBox);
            globalRenderBox = bb.transform(subLevelAccess.logicalPose(), bb).toMojang();
        }
        return globalRenderBox;
    }
}
