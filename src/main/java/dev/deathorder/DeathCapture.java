package dev.deathorder;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** The vanilla drop sequence is synchronous on the server thread. */
public final class DeathCapture {
    private record Capture(Player player, Map<ItemStack, Origin> slots) {}
    private static final ThreadLocal<Capture> CURRENT = new ThreadLocal<>();

    private DeathCapture() {}

    public static void begin(Inventory inventory) {
        if (inventory.player.level().isClientSide()) return;
        UUID death = UUID.randomUUID();
        Map<ItemStack, Origin> slots = new IdentityHashMap<>();
        for (int slot = 0; slot <= 40; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty()) slots.put(stack, new Origin(inventory.player.getUUID(), death, slot));
        }
        CURRENT.set(new Capture(inventory.player, slots));
    }

    public static void tag(Object entity, ItemStack stack, ItemEntity drop) {
        Capture capture = CURRENT.get();
        if (capture == null || entity != capture.player || drop == null) return;
        Origin origin = capture.slots.get(stack);
        if (origin != null) ((TrackedDrop) drop).deathOrder$setOrigin(origin);
    }

    public static void end() { CURRENT.remove(); }
}
