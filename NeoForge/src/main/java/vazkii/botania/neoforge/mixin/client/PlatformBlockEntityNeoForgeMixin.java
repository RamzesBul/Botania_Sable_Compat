package vazkii.botania.neoforge.mixin.client;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;

import net.neoforged.neoforge.client.model.data.ModelData;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import vazkii.botania.common.block.block_entity.PlatformBlockEntity;
import vazkii.botania.neoforge.client.NeoForgePlatformModel;

@Mixin(PlatformBlockEntity.class)
public abstract class PlatformBlockEntityNeoForgeMixin extends BlockEntity {

    private PlatformBlockEntityNeoForgeMixin() {
        super(null, null, null); // never called; required because we extend BlockEntity
    }

    public ModelData getModelData() {
        PlatformBlockEntity self = (PlatformBlockEntity) (Object) this;
        return ModelData.builder()
                .with(NeoForgePlatformModel.PROPERTY, new PlatformBlockEntity.PlatformData(self))
                .build();
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void botania$refreshModelDataOnCamoChange(CompoundTag cmp, HolderLookup.Provider registries, CallbackInfo ci) {
        this.requestModelDataUpdate();
    }
}