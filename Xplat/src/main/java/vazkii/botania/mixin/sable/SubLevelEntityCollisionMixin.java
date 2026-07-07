package vazkii.botania.mixin.sable;

import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import vazkii.botania.common.block.AbstrusePlatformBlock;

@Mixin(SubLevelEntityCollision.class)
public class SubLevelEntityCollisionMixin {

    @Redirect(
            method = "getSubLevelEntityCollisionShape",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/shapes/VoxelShape;"
            )
    )
    private static VoxelShape botania$abstrusePlatformOneWay(BlockState state, BlockGetter level, BlockPos pos,
                                                             Entity entity, Vector3dc boundsCenter, Pose3dc subLevelPose) {
        if (!(state.getBlock() instanceof AbstrusePlatformBlock)) {
            return state.getCollisionShape(level, pos);
        }

        // World-space feet of the entity -> local (plot-grid) space, mirroring Sable's scaffolding branch.
        Vector3d feet = new Vector3d(boundsCenter.x(), boundsCenter.y(), boundsCenter.z())
                .sub(0.0, entity.getBoundingBox().getYsize() / 2.0, 0.0);
        double feetLocalY = subLevelPose.transformPositionInverse(feet).y();

        if (entity.isShiftKeyDown()) {
            return Shapes.empty();
        }

        boolean solidTop = feetLocalY - pos.getY() > 0.95;
        return solidTop ? Shapes.block() : Shapes.empty();
    }
}