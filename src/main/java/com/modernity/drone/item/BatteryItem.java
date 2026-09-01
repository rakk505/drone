package com.modernity.drone.item;

import com.modernity.drone.flight.BatteryData;
import com.modernity.drone.client.ClientItemState;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

public final class BatteryItem extends Item {
    private static final String KEY = "BatteryData";

    public BatteryItem(Properties properties) {
        super(properties);
    }

    public static BatteryData getBatteryData(ItemStack stack) {
        CompoundTag root = StackData.copy(stack);
        return root.getCompound(KEY).map(BatteryData::load).orElseGet(BatteryData::defaults);
    }

    public static void setBatteryData(ItemStack stack, BatteryData data) {
        StackData.update(stack, tag -> tag.put(KEY, data.save()));
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return !getBatteryData(stack).isFullCharge();
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        BatteryData data = getBatteryData(stack);
        return Math.round(13.0F * data.remainingMah() / data.capacityMah());
    }

    @Override
    public int getBarColor(ItemStack stack) {
        BatteryData data = getBatteryData(stack);
        float ratio = (float) data.remainingMah() / data.capacityMah();
        int red = 255;
        int green = (int) (85.0F + 63.0F * ratio);
        int blue = (int) (85.0F - 31.0F * ratio);
        return red << 16 | green << 8 | blue;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable(ClientItemState.shiftDown()
                        ? "item.fpvdrone.battery.usage" : "item.fpvdrone.hold_shift")
                .withStyle(ClientItemState.shiftDown() ? ChatFormatting.GRAY : ChatFormatting.DARK_GRAY));
        BatteryData data = getBatteryData(stack);
        tooltip.accept(Component.literal(String.format(java.util.Locale.ROOT,
                "%dS • %d/%dmAh • %.1fV • %dC",
                data.cells(), data.remainingMah(), data.capacityMah(), data.voltage(), data.cRating()))
                .withColor(getBarColor(stack)));
    }
}
