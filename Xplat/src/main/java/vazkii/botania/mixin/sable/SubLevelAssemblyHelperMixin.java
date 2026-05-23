package vazkii.botania.mixin.sable;

import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vazkii.botania.common.entity.SparkBaseEntity;

import java.util.stream.StreamSupport;
import java.util.List;

@Mixin(SubLevelAssemblyHelper.class)
public abstract class SubLevelAssemblyHelperMixin {

    @Unique
    private static <BlockPos> BlockPos botania$findUsingStream(Iterable<BlockPos> iterable, BlockPos target) {
        return StreamSupport.stream(iterable.spliterator(), false)
                .filter(e -> e.equals(target))
                .findFirst()
                .orElse(null);
    }

    @Inject(method = "assembleBlocks", at = @At("RETURN"))
    private static void botaniaAssembleEntity(ServerLevel level,
                                              BlockPos anchor,
                                              Iterable<BlockPos> blocks,
                                              BoundingBox3ic bounds,
                                              CallbackInfoReturnable<ServerSubLevel> cir,
                                              @Local(name = "transform") final SubLevelAssemblyHelper.AssemblyTransform transform) {
        final List<Entity> entities = level.getEntitiesOfClass(Entity.class, bounds.toAABB().inflate(1.0));

        for (final Entity entity : entities) {
            if (entity instanceof final SparkBaseEntity sparkEntity) {
                if (botania$findUsingStream(blocks, sparkEntity.getAttachPos()) != null) {
                    sparkEntity.setPos(transform.apply(sparkEntity.position()));
                    sparkEntity.setSubLevel();
                }
            }
        }
    }
}
