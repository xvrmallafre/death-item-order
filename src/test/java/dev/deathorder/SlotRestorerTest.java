package dev.deathorder;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class SlotRestorerTest {
    private static final class Stack {
        final String components;
        int count;
        Stack(String components, int count) { this.components = components; this.count = count; }
    }

    private static final class Bag implements SlotRestorer.Access<Stack> {
        final Stack[] slots = new Stack[41];
        boolean boundArmor;
        Bag() { for (int i = 0; i < slots.length; i++) slots[i] = new Stack("empty", 0); }
        public Stack get(int slot) { return slots[slot]; }
        public void set(int slot, Stack stack) { slots[slot] = stack; }
        public boolean empty(Stack stack) { return stack.count == 0; }
        public boolean compatible(Stack a, Stack b) { return a.components.equals(b.components); }
        public int count(Stack stack) { return stack.count; }
        public int limit(int slot, Stack stack) { return slot >= 36 && slot <= 39 ? 1 : 64; }
        public Stack split(Stack stack, int count) { stack.count -= count; return new Stack(stack.components, count); }
        public void grow(Stack stack, int count) { stack.count += count; }
        public void shrink(Stack stack, int count) { stack.count -= count; }
        public boolean movable(int slot, Stack stack) { return !boundArmor || slot < 36 || slot == 40; }
        void fill() { for (int i = 0; i < 36; i++) slots[i] = new Stack("occupied" + i, 64); }
        Map<String, Integer> quantities(Stack incoming) {
            Map<String, Integer> result = new HashMap<>();
            for (Stack stack : slots) if (!empty(stack)) result.merge(stack.components, stack.count, Integer::sum);
            if (!empty(incoming)) result.merge(incoming.components, incoming.count, Integer::sum);
            return result;
        }
    }

    @Test void restoresEveryMainArmorAndOffhandSlot() {
        for (int slot = 0; slot <= 40; slot++) {
            Bag bag = new Bag();
            Stack drop = new Stack("sword:damage=42,name=custom,enchantments,data", 1);
            assertEquals(1, SlotRestorer.restore(bag, drop, slot));
            assertEquals(0, drop.count);
            assertEquals(drop.components, bag.get(slot).components);
            assertEquals(1, bag.get(slot).count);
        }
    }

    @Test void displacesOccupantWithoutChangingItsData() {
        Bag bag = new Bag();
        Stack occupant = new Stack("valuable:custom-data", 47);
        bag.set(0, occupant);
        assertEquals(10, SlotRestorer.restore(bag, new Stack("blocks", 10), 0));
        assertSame(occupant, bag.get(1));
        assertEquals(47, bag.get(1).count);
        assertEquals(10, bag.get(0).count);
    }

    @Test void fullInventoryLeavesEverythingForVanilla() {
        Bag bag = new Bag(); bag.fill();
        Stack drop = new Stack("sword", 1);
        Map<String, Integer> before = bag.quantities(drop);
        assertEquals(0, SlotRestorer.restore(bag, drop, 0));
        assertEquals(before, bag.quantities(drop));
        assertEquals(1, drop.count);
    }

    @Test void fullMainInventoryStillAcceptsEmptyArmorAndOffhand() {
        for (int slot = 36; slot <= 40; slot++) {
            Bag bag = new Bag(); bag.fill();
            assertEquals(1, SlotRestorer.restore(bag, new Stack("equipment", 1), slot));
        }
    }

    @Test void progressiveRecoveryMergesOnlyCompatibleComponents() {
        Bag bag = new Bag();
        SlotRestorer.restore(bag, new Stack("blocks:data=A", 17), 4);
        SlotRestorer.restore(bag, new Stack("blocks:data=A", 21), 4);
        assertEquals(38, bag.get(4).count);
        SlotRestorer.restore(bag, new Stack("blocks:data=B", 8), 4);
        assertEquals("blocks:data=B", bag.get(4).components);
        assertEquals(38, bag.get(0).count);
    }

    @Test void keepsOverflowInPhysicalDrop() {
        Bag bag = new Bag(); bag.set(4, new Stack("blocks", 60));
        Stack drop = new Stack("blocks", 15);
        assertEquals(4, SlotRestorer.restore(bag, drop, 4));
        assertEquals(64, bag.get(4).count);
        assertEquals(11, drop.count);
    }

    @Test void bindingCursePreventsDisplacingArmor() {
        Bag bag = new Bag(); bag.boundArmor = true;
        bag.set(39, new Stack("bound-helmet", 1));
        Stack drop = new Stack("old-helmet", 1);
        assertEquals(0, SlotRestorer.restore(bag, drop, 39));
        assertEquals("bound-helmet", bag.get(39).components);
    }

    @Test void armorAcceptsOnlyOneItem() {
        Bag bag = new Bag(); Stack drop = new Stack("helmet", 3);
        assertEquals(1, SlotRestorer.restore(bag, drop, 39));
        assertEquals(2, drop.count);
    }

    @Test void invalidSlotsAndEmptyDropsDoNothing() {
        Bag bag = new Bag(); Stack drop = new Stack("blocks", 3);
        assertEquals(0, SlotRestorer.restore(bag, drop, -1));
        assertEquals(0, SlotRestorer.restore(bag, drop, 41));
        assertEquals(0, SlotRestorer.restore(bag, new Stack("blocks", 0), 0));
        assertEquals(3, drop.count);
    }

    @Test void distinctDeathsRetainDistinctOriginsEvenForSameSlot() {
        var owner = java.util.UUID.randomUUID();
        assertNotEquals(new Origin(owner, java.util.UUID.randomUUID(), 0),
                new Origin(owner, java.util.UUID.randomUUID(), 0));
    }

    @Test void randomizedTransfersConserveAllQuantitiesAndComponents() {
        Random random = new Random(263);
        for (int trial = 0; trial < 10000; trial++) {
            Bag bag = new Bag(); bag.boundArmor = random.nextBoolean();
            for (int slot = 0; slot < 41; slot++) {
                if (random.nextBoolean()) bag.set(slot, new Stack("data" + random.nextInt(5),
                        1 + random.nextInt(slot >= 36 && slot <= 39 ? 1 : 64)));
            }
            Stack incoming = new Stack("data" + random.nextInt(5), 1 + random.nextInt(64));
            Map<String, Integer> before = bag.quantities(incoming);
            int oldCount = incoming.count;
            int moved = SlotRestorer.restore(bag, incoming, random.nextInt(41));
            assertEquals(oldCount - incoming.count, moved);
            assertEquals(before, bag.quantities(incoming));
            for (int slot = 0; slot < 41; slot++) {
                assertTrue(bag.get(slot).count >= 0);
                assertTrue(bag.get(slot).count <= bag.limit(slot, bag.get(slot)));
            }
        }
    }
}
