package com.modernity.drone.item;

import java.util.function.Consumer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Small compatibility layer for the custom NBT used by the 1.20.1 release. */
public final class StackData {
    private StackData() {
    }

    public static CompoundTag copy(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    public static void update(ItemStack stack, Consumer<CompoundTag> writer) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, writer);
    }
}
