package com.modernity.drone.item;

import com.modernity.drone.client.ClientItemState;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** Item with the reference mod's hold-Shift usage hint. */
public final class ShiftTooltipItem extends Item {
    private final String usageKey;

    public ShiftTooltipItem(Properties properties, String usageKey) {
        super(properties);
        this.usageKey = usageKey;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable(ClientItemState.shiftDown()
                        ? usageKey : "item.fpvdrone.hold_shift")
                .withStyle(ClientItemState.shiftDown() ? ChatFormatting.GRAY : ChatFormatting.DARK_GRAY));
    }
}
