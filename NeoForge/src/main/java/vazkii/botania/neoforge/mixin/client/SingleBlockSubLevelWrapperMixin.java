package vazkii.botania.neoforge.mixin.client;

import dev.ryanhcode.sable.sublevel.render.vanilla.SingleBlockSubLevelWrapper;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;

import net.neoforged.neoforge.client.model.data.ModelData;

import org.spongepowered.asm.mixin.Mixin;

@Mixin(SingleBlockSubLevelWrapper.class)
public abstract class SingleBlockSubLevelWrapperMixin {

    public ModelData getModelData(BlockPos pos) {
        ClientLevel level = ((SingleBlockSubLevelWrapper) (Object) this).getLevel();
        return level != null ? level.getModelData(pos) : ModelData.EMPTY;
    }
}