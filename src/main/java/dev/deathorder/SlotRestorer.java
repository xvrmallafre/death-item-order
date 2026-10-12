package dev.deathorder;

/** Moves quantities; the caller retains ownership of the unaccepted remainder. */
public final class SlotRestorer {
    public interface Access<S> {
        S get(int slot);
        void set(int slot, S stack);
        boolean empty(S stack);
        boolean compatible(S first, S second);
        int count(S stack);
        int limit(int slot, S stack);
        S split(S stack, int count);
        void grow(S stack, int count);
        void shrink(S stack, int count);
        boolean movable(int slot, S stack);
    }

    private SlotRestorer() {}

    public static <S> int restore(Access<S> inventory, S incoming, int originalSlot) {
        if (originalSlot < 0 || originalSlot > 40 || inventory.empty(incoming)) return 0;
        S existing = inventory.get(originalSlot);
        if (!inventory.empty(existing) && !inventory.compatible(existing, incoming)) {
            if (!inventory.movable(originalSlot, existing)) return 0;
            int free = -1;
            // Only ordinary inventory slots may receive the displaced object.
            for (int slot = 0; slot < 36; slot++) {
                if (slot != originalSlot && inventory.empty(inventory.get(slot))
                        && inventory.count(existing) <= inventory.limit(slot, existing)) {
                    free = slot;
                    break;
                }
            }
            if (free == -1) return 0;
            // Check capacity before changing anything, including the occupant.
            int accepted = Math.min(inventory.count(incoming), inventory.limit(originalSlot, incoming));
            if (accepted <= 0) return 0;
            inventory.set(free, existing);
            inventory.set(originalSlot, inventory.split(incoming, accepted));
            return accepted;
        }

        int occupied = inventory.empty(existing) ? 0 : inventory.count(existing);
        int accepted = Math.min(inventory.count(incoming),
                Math.max(0, inventory.limit(originalSlot, incoming) - occupied));
        if (accepted == 0) return 0;
        if (inventory.empty(existing)) {
            inventory.set(originalSlot, inventory.split(incoming, accepted));
        } else {
            inventory.grow(existing, accepted);
            inventory.shrink(incoming, accepted);
        }
        return accepted;
    }
}
