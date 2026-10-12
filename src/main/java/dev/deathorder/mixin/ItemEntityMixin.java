package dev.deathorder.mixin;

import dev.deathorder.MinecraftInventory;
import dev.deathorder.Origin;
import dev.deathorder.SlotRestorer;
import dev.deathorder.TrackedDrop;
import java.util.Objects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
abstract class ItemEntityMixin implements TrackedDrop {
    @Unique private Origin deathOrder$origin;

    public Origin deathOrder$getOrigin() { return deathOrder$origin; }
    public void deathOrder$setOrigin(Origin origin) { deathOrder$origin = origin; }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void deathOrder$save(ValueOutput output, CallbackInfo ci) {
        if (deathOrder$origin != null) deathOrder$origin.save(output);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void deathOrder$load(ValueInput input, CallbackInfo ci) {
        deathOrder$origin = Origin.load(input);
    }

    @Inject(method = "restoreFrom", at = @At("TAIL"))
    private void deathOrder$copyAcrossDimensions(Entity previous, CallbackInfo ci) {
        if (previous instanceof TrackedDrop tracked) deathOrder$origin = tracked.deathOrder$getOrigin();
    }

    @Inject(method = "tryToMerge", at = @At("HEAD"), cancellable = true)
    private void deathOrder$keepOriginsSeparate(ItemEntity other, CallbackInfo ci) {
        if (!Objects.equals(deathOrder$origin, ((TrackedDrop) other).deathOrder$getOrigin())) ci.cancel();
    }

    // Runs inside vanilla's delay/target checks, retaining its pickup effects and events.
    @Redirect(method = "playerTouch", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Inventory;add(Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean deathOrder$insert(Inventory inventory, ItemStack stack, Player picker) {
        if (deathOrder$origin == null || !(picker instanceof ServerPlayer player)
                || !deathOrder$origin.owner().equals(player.getUUID())) return inventory.add(stack);

        int restored = SlotRestorer.restore(new MinecraftInventory(player), stack, deathOrder$origin.slot());
        boolean normal = !stack.isEmpty() && inventory.add(stack);
        if (restored > 0) {
            inventory.setChanged();
            player.inventoryMenu.broadcastChanges();
            if (player.containerMenu != player.inventoryMenu) player.containerMenu.broadcastChanges();
        }
        return restored > 0 || normal;
    }
}
