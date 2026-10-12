package dev.deathorder.mixin;

import dev.deathorder.DeathCapture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {
    @Inject(method = "createItemStackToDrop", at = @At("RETURN"))
    private void deathOrder$tag(ItemStack stack, boolean scatter, boolean setThrower,
                               CallbackInfoReturnable<ItemEntity> cir) {
        DeathCapture.tag(this, stack, cir.getReturnValue());
    }
}
