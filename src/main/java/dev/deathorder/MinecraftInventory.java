package dev.deathorder;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

public final class MinecraftInventory implements SlotRestorer.Access<ItemStack> {
    private final Inventory inventory;
    private final ServerPlayer player;

    public MinecraftInventory(ServerPlayer player) {
        this.player = player;
        this.inventory = player.getInventory();
    }

    public ItemStack get(int slot) { return inventory.getItem(slot); }
    public void set(int slot, ItemStack stack) { inventory.setItem(slot, stack); }
    public boolean empty(ItemStack stack) { return stack.isEmpty(); }
    public boolean compatible(ItemStack a, ItemStack b) { return ItemStack.isSameItemSameComponents(a, b); }
    public int count(ItemStack stack) { return stack.getCount(); }
    public int limit(int slot, ItemStack stack) {
        return slot >= 36 && slot <= 39 ? Math.min(1, stack.getMaxStackSize()) : stack.getMaxStackSize();
    }
    public ItemStack split(ItemStack stack, int count) { return stack.split(count); }
    public void grow(ItemStack stack, int count) { stack.grow(count); }
    public void shrink(ItemStack stack, int count) { stack.shrink(count); }
    public boolean movable(int slot, ItemStack stack) {
        return slot < 36 || slot == 40 || player.isCreative()
                || !EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE);
    }
}
