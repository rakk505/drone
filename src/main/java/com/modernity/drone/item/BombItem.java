package com.modernity.drone.item;

import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

public final class BombItem extends Item {
    private final int tier;

    public BombItem(Properties properties, int tier) {
        super(properties);
        this.tier = Math.max(1, Math.min(3, tier));
    }

    public int tier() {
        return tier;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("item.fpvdrone.bomb.tier", tier).withColor(0xFFAA36));
    }
}
