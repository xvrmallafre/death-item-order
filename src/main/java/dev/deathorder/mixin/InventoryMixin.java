package dev.deathorder.mixin;

import dev.deathorder.DeathCapture;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Inventory.class)
abstract class InventoryMixin {
    @Inject(method = "dropAll", at = @At("HEAD"))
    private void deathOrder$begin(CallbackInfo ci) {
        DeathCapture.begin((Inventory) (Object) this);
    }

    @Inject(method = "dropAll", at = @At("RETURN"))
    private void deathOrder$end(CallbackInfo ci) { DeathCapture.end(); }
}
