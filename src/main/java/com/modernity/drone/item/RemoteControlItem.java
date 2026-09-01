package com.modernity.drone.item;

import com.modernity.drone.entity.DroneEntity;
import com.modernity.drone.entity.DroneLinkManager;
import com.modernity.drone.client.ClientItemState;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

public final class RemoteControlItem extends Item {
    private static final String LINKED_DRONE = "LinkedDroneUUID";
    private static final String VIDEO_CHANNEL = "VideoChannel";

    public RemoteControlItem(Properties properties) {
        super(properties);
    }

    public static Optional<UUID> getLinkedDroneId(ItemStack stack) {
        return StackData.copy(stack).read(LINKED_DRONE, UUIDUtil.LENIENT_CODEC);
    }

    public static void setLinkedDroneId(ItemStack stack, UUID id) {
        StackData.update(stack, tag -> tag.store(LINKED_DRONE, UUIDUtil.CODEC, id));
    }

    public static void clearLink(ItemStack stack) {
        StackData.update(stack, tag -> tag.remove(LINKED_DRONE));
    }

    public static boolean isLinked(ItemStack stack) {
        return getLinkedDroneId(stack).isPresent();
    }

    public static boolean playerHasLinkedRemote(Player player, UUID droneId) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.getItem() instanceof RemoteControlItem
                    && getLinkedDroneId(stack).filter(droneId::equals).isPresent()) return true;
        }
        ItemStack offhand = player.getOffhandItem();
        return offhand.getItem() instanceof RemoteControlItem
                && getLinkedDroneId(offhand).filter(droneId::equals).isPresent();
    }

    public static void setChannel(ItemStack stack, int channel) {
        StackData.update(stack, tag -> tag.putByte(VIDEO_CHANNEL, (byte) Math.max(1, Math.min(8, channel))));
    }

    public static int getChannel(ItemStack stack) {
        return Math.max(1, Math.min(8, StackData.copy(stack).getByteOr(VIDEO_CHANNEL, (byte) 1)));
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
        if (!(entity instanceof Player player)) return;
        getLinkedDroneId(stack).ifPresent(id -> {
            if (!DroneLinkManager.isLinked(id)) {
                Entity found = level.getEntityInAnyDimension(id);
                if (found instanceof DroneEntity drone && drone.isAlive()) {
                    DroneLinkManager.link(id, player.getUUID());
                } else if (found != null) {
                    clearLink(stack);
                }
            }
        });
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable(ClientItemState.shiftDown()
                        ? "item.fpvdrone.remote_control.usage" : "item.fpvdrone.hold_shift")
                .withStyle(ClientItemState.shiftDown() ? ChatFormatting.GRAY : ChatFormatting.DARK_GRAY));
        tooltip.accept(Component.translatable(isLinked(stack)
                        ? "item.fpvdrone.remote_control.linked"
                        : "item.fpvdrone.remote_control.not_linked",
                isLinked(stack) ? "R" + getChannel(stack) : "").withColor(0xFFAA36));
    }
}
