package dev.deathorder;

import java.util.UUID;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** A pending record belongs to the physical drop, never to a copy of its contents. */
public record Origin(UUID owner, UUID death, int slot) {
    public void save(ValueOutput output) {
        ValueOutput child = output.child("death_item_order:origin");
        child.putInt("version", 1);
        child.putString("owner", owner.toString());
        child.putString("death", death.toString());
        child.putInt("slot", slot);
    }

    public static Origin load(ValueInput input) {
        return input.child("death_item_order:origin").map(child -> {
            if (child.getIntOr("version", 0) != 1) return null;
            int slot = child.getIntOr("slot", -1);
            if (slot < 0 || slot > 40) return null;
            try {
                return new Origin(UUID.fromString(child.getStringOr("owner", "")),
                        UUID.fromString(child.getStringOr("death", "")), slot);
            } catch (IllegalArgumentException malformed) {
                return null;
            }
        }).orElse(null);
    }
}
