package dev.deathorder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Real transformed game methods and ItemStacks; world and network are test doubles. */
class MinecraftIntegrationTest {
    private static net.minecraft.core.HolderLookup.Provider lookup;
    @BeforeAll static void bootstrap() {
        Thread.currentThread().setContextClassLoader(MinecraftIntegrationTest.class.getClassLoader());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        lookup = net.minecraft.data.registries.VanillaRegistries.createWorldLookup();
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(lookup).forEach(pending -> pending.apply());
        assertTrue(TrackedDrop.class.isAssignableFrom(ItemEntity.class), "Mixin must be applied");
    }

    private record Fixture(ServerLevel level, ServerPlayer player, Inventory inventory) {}

    private Fixture fixture() throws Exception {
        ServerLevel level = mock(ServerLevel.class);
        when(level.registryAccess()).thenReturn(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        ServerPlayer player = mock(ServerPlayer.class);
        when(player.level()).thenReturn(level);
        when(player.getUUID()).thenReturn(UUID.randomUUID());
        Inventory inventory = new Inventory(player, new EntityEquipment());
        when(player.getInventory()).thenReturn(inventory);
        InventoryMenu menu = mock(InventoryMenu.class);
        var menuField = Player.class.getField("inventoryMenu");
        menuField.setAccessible(true);
        menuField.set(player, menu);
        player.containerMenu = menu;
        var randomField = net.minecraft.world.entity.Entity.class.getDeclaredField("random");
        randomField.setAccessible(true);
        randomField.set(player, RandomSource.create(263));
        var positionField = net.minecraft.world.entity.Entity.class.getDeclaredField("position");
        positionField.setAccessible(true);
        positionField.set(player, net.minecraft.world.phys.Vec3.ZERO);
        doCallRealMethod().when(player).createItemStackToDrop(any(ItemStack.class), anyBoolean(), anyBoolean());
        return new Fixture(level, player, inventory);
    }

    private ItemEntity drop(Fixture f, ItemStack stack, int slot) {
        ItemEntity entity = new ItemEntity(f.level, 0, 0, 0, stack);
        ((TrackedDrop) entity).deathOrder$setOrigin(new Origin(f.player.getUUID(), UUID.randomUUID(), slot));
        entity.setNoPickUpDelay();
        return entity;
    }

    @Test void capturesActualVanillaDropSequenceIncludingEquipment() throws Exception {
        Fixture f = fixture();
        f.inventory.setItem(0, new ItemStack(Items.DIAMOND_SWORD));
        f.inventory.setItem(4, new ItemStack(Items.COBBLESTONE, 32));
        f.inventory.setItem(36, new ItemStack(Items.DIAMOND_BOOTS));
        f.inventory.setItem(37, new ItemStack(Items.DIAMOND_LEGGINGS));
        f.inventory.setItem(38, new ItemStack(Items.DIAMOND_CHESTPLATE));
        f.inventory.setItem(39, new ItemStack(Items.DIAMOND_HELMET));
        f.inventory.setItem(40, new ItemStack(Items.SHIELD));
        List<ItemEntity> drops = new ArrayList<>();
        when(f.level.addFreshEntity(any())).thenAnswer(call -> {
            drops.add(call.getArgument(0)); return true;
        });
        f.inventory.dropAll();
        assertEquals(7, drops.size());
        assertTrue(f.inventory.isEmpty());
        UUID death = ((TrackedDrop) drops.getFirst()).deathOrder$getOrigin().death();
        for (ItemEntity entity : drops) {
            Origin origin = ((TrackedDrop) entity).deathOrder$getOrigin();
            assertNotNull(origin);
            assertEquals(f.player.getUUID(), origin.owner());
            assertEquals(death, origin.death());
            entity.setNoPickUpDelay();
            entity.playerTouch(f.player);
            assertTrue(entity.isRemoved());
            assertFalse(f.inventory.getItem(origin.slot()).isEmpty());
        }
        // A later ordinary drop is not marked with the old death.
        assertNull(((TrackedDrop) f.player.createItemStackToDrop(new ItemStack(Items.DIRT), true, false))
                .deathOrder$getOrigin());
    }

    @Test void preservesRealComponentsAndDisplacesTheOccupant() throws Exception {
        Fixture f = fixture();
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.setDamageValue(123);
        sword.enchant(lookup.lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS), 3);
        sword.set(DataComponents.CUSTOM_NAME, Component.literal("Espada recordada"));
        CompoundTag custom = new CompoundTag(); custom.putString("example", "nested data");
        sword.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
        ItemStack expected = sword.copy();
        f.inventory.setItem(0, new ItemStack(Items.APPLE, 17));
        ItemEntity entity = drop(f, sword, 0);
        entity.playerTouch(f.player);
        assertTrue(entity.isRemoved());
        assertTrue(ItemStack.isSameItemSameComponents(expected, f.inventory.getItem(0)));
        assertEquals(123, f.inventory.getItem(0).getDamageValue());
        assertEquals(17, f.inventory.getItem(1).getCount());
    }

    @Test void ownerIsolationAndNormalNewItemPickup() throws Exception {
        Fixture f = fixture();
        ItemEntity foreign = drop(f, new ItemStack(Items.COBBLESTONE, 5), 4);
        ((TrackedDrop) foreign).deathOrder$setOrigin(new Origin(UUID.randomUUID(), UUID.randomUUID(), 4));
        foreign.playerTouch(f.player);
        assertEquals(5, f.inventory.getItem(0).getCount());
        assertTrue(f.inventory.getItem(4).isEmpty());
        ItemEntity ordinary = new ItemEntity(f.level, 0, 0, 0, new ItemStack(Items.DIRT, 3));
        ordinary.setNoPickUpDelay(); ordinary.playerTouch(f.player);
        assertEquals(3, f.inventory.getItem(1).getCount());
    }

    @Test void partialPickupAndVanillaFallbackConserveCounts() throws Exception {
        Fixture f = fixture();
        for (int slot = 0; slot < 36; slot++) f.inventory.setItem(slot, new ItemStack(Items.DIRT, 64));
        f.inventory.setItem(4, new ItemStack(Items.COBBLESTONE, 60));
        ItemEntity entity = drop(f, new ItemStack(Items.COBBLESTONE, 15), 4);
        entity.playerTouch(f.player);
        assertEquals(64, f.inventory.getItem(4).getCount());
        assertEquals(11, entity.getItem().getCount());
        assertFalse(entity.isRemoved());
        f.inventory.getItem(4).shrink(20);
        entity.playerTouch(f.player);
        assertEquals(55, f.inventory.getItem(4).getCount());
        assertTrue(entity.isRemoved());

        f.inventory.setItem(4, new ItemStack(Items.DIRT, 64));
        f.inventory.setItem(8, new ItemStack(Items.COBBLESTONE, 62));
        ItemEntity fallback = drop(f, new ItemStack(Items.COBBLESTONE, 10), 4);
        fallback.playerTouch(f.player);
        assertEquals(64, f.inventory.getItem(8).getCount());
        assertEquals(8, fallback.getItem().getCount());
        assertEquals(Items.DIRT, f.inventory.getItem(4).getItem());
    }

    @Test void pickupDelayAndTargetChecksRemainActive() throws Exception {
        Fixture f = fixture();
        ItemEntity entity = drop(f, new ItemStack(Items.DIRT, 2), 4);
        entity.setPickUpDelay(40); entity.playerTouch(f.player);
        assertTrue(f.inventory.isEmpty());
        entity.setNoPickUpDelay(); entity.setTarget(UUID.randomUUID()); entity.playerTouch(f.player);
        assertTrue(f.inventory.isEmpty());
        entity.setTarget(f.player.getUUID()); entity.playerTouch(f.player);
        assertEquals(2, f.inventory.getItem(4).getCount());
    }

    @Test void entitySaveLoadKeepsPendingOriginAndAllComponents() throws Exception {
        Fixture f = fixture();
        ItemStack stack = new ItemStack(Items.DIAMOND_SWORD);
        stack.setDamageValue(77);
        stack.enchant(lookup.lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .getOrThrow(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING), 2);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Persistente"));
        ItemEntity original = drop(f, stack, 7);
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, lookup);
        Method save = ItemEntity.class.getDeclaredMethod("addAdditionalSaveData", ValueOutput.class);
        save.setAccessible(true); save.invoke(original, output);
        ItemEntity loaded = new ItemEntity(f.level, 0, 0, 0, new ItemStack(Items.DIRT));
        Method load = ItemEntity.class.getDeclaredMethod("readAdditionalSaveData", ValueInput.class);
        load.setAccessible(true);
        load.invoke(loaded, TagValueInput.create(ProblemReporter.DISCARDING, lookup, output.buildResult()));
        assertEquals(((TrackedDrop) original).deathOrder$getOrigin(), ((TrackedDrop) loaded).deathOrder$getOrigin());
        assertTrue(ItemStack.isSameItemSameComponents(stack, loaded.getItem()));
        loaded.playerTouch(f.player);
        assertEquals(77, f.inventory.getItem(7).getDamageValue());
        assertTrue(loaded.isRemoved());
    }

    @Test void differentDeathsAndOrdinaryEntitiesCannotMerge() throws Exception {
        Fixture f = fixture();
        ItemEntity first = drop(f, new ItemStack(Items.DIRT, 5), 4);
        ItemEntity second = drop(f, new ItemStack(Items.DIRT, 8), 4);
        Method merge = ItemEntity.class.getDeclaredMethod("tryToMerge", ItemEntity.class);
        merge.setAccessible(true); merge.invoke(first, second);
        assertEquals(5, first.getItem().getCount());
        assertEquals(8, second.getItem().getCount());
        ((TrackedDrop) second).deathOrder$setOrigin(null);
        merge.invoke(first, second);
        assertEquals(5, first.getItem().getCount());
        ((TrackedDrop) second).deathOrder$setOrigin(((TrackedDrop) first).deathOrder$getOrigin());
        merge.invoke(first, second);
        assertTrue(first.isRemoved() || second.isRemoved());
        assertEquals(13, first.isRemoved() ? second.getItem().getCount() : first.getItem().getCount());
    }

    @Test void dimensionTransferCopiesOrigin() throws Exception {
        Fixture f = fixture();
        ItemEntity first = drop(f, new ItemStack(Items.DIRT, 5), 4);
        ItemEntity second = new ItemEntity(f.level, 0, 0, 0, new ItemStack(Items.DIRT, 5));
        second.restoreFrom(first);
        assertEquals(((TrackedDrop) first).deathOrder$getOrigin(), ((TrackedDrop) second).deathOrder$getOrigin());
    }

    @Test void malformedAndUnknownRecordsAreIgnored() throws Exception {
        Fixture f = fixture();
        TagValueOutput output = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        ValueOutput child = output.child("death_item_order:origin");
        child.putInt("version", 1); child.putInt("slot", 0); child.putString("owner", "invalid");
        assertNull(Origin.load(TagValueInput.create(ProblemReporter.DISCARDING, f.level.registryAccess(), output.buildResult())));
        child.putInt("version", 100);
        assertNull(Origin.load(TagValueInput.create(ProblemReporter.DISCARDING, f.level.registryAccess(), output.buildResult())));
    }

    @Test void realBindingCurseUsesNormalPickupInsteadOfRemovingArmor() throws Exception {
        Fixture f = fixture();
        ItemStack helmet = new ItemStack(Items.DIAMOND_HELMET);
        helmet.enchant(lookup.lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .getOrThrow(net.minecraft.world.item.enchantment.Enchantments.BINDING_CURSE), 1);
        f.inventory.setItem(39, helmet);
        ItemEntity entity = drop(f, new ItemStack(Items.IRON_HELMET), 39);
        entity.playerTouch(f.player);
        assertSame(helmet, f.inventory.getItem(39));
        assertEquals(Items.IRON_HELMET, f.inventory.getItem(0).getItem());
        assertTrue(entity.isRemoved());
    }

    @Test void consecutiveDeathCapturesDoNotRelabelOldDrops() throws Exception {
        Fixture f = fixture();
        List<ItemEntity> drops = new ArrayList<>();
        when(f.level.addFreshEntity(any())).thenAnswer(call -> { drops.add(call.getArgument(0)); return true; });
        f.inventory.setItem(4, new ItemStack(Items.DIRT, 5)); f.inventory.dropAll();
        Origin first = ((TrackedDrop) drops.getFirst()).deathOrder$getOrigin();
        f.inventory.setItem(4, new ItemStack(Items.COBBLESTONE, 8)); f.inventory.dropAll();
        Origin second = ((TrackedDrop) drops.getLast()).deathOrder$getOrigin();
        assertEquals(first.owner(), second.owner());
        assertNotEquals(first.death(), second.death());
        assertEquals(first, ((TrackedDrop) drops.getFirst()).deathOrder$getOrigin());
        drops.getLast().setNoPickUpDelay(); drops.getLast().playerTouch(f.player);
        drops.getFirst().setNoPickUpDelay(); drops.getFirst().playerTouch(f.player);
        assertEquals(5, f.inventory.getItem(4).getCount());
        assertEquals(Items.DIRT, f.inventory.getItem(4).getItem());
        assertEquals(8, f.inventory.getItem(0).getCount());
        assertEquals(Items.COBBLESTONE, f.inventory.getItem(0).getItem());
    }
}
