package com.modernity.drone.item;

import com.modernity.drone.DroneMod;
import com.modernity.drone.client.ClientItemState;
import com.modernity.drone.entity.DroneEntity;
import com.modernity.drone.flight.BatteryData;
import com.modernity.drone.flight.DroneKind;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;

/** Placeable V1.1.4 9-inch FPV airframe. */
public final class DroneItem extends Item {
    public static final int PLACEMENT_RANGE = 10;
    private final boolean thermal;

    public DroneItem(Properties properties, boolean thermal) {
        super(properties);
        this.thermal = thermal;
    }

    public boolean thermal() {
        return thermal;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        BlockPos clicked = context.getClickedPos();
        if (player == null
                || !context.getLevel().getBlockState(clicked).blocksMotion()
                || player.position().distanceTo(Vec3.atCenterOf(clicked)) > PLACEMENT_RANGE) {
            if (player != null && !context.getLevel().isClientSide()) {
                player.sendOverlayMessage(Component.translatable(
                        player.position().distanceTo(Vec3.atCenterOf(clicked)) > PLACEMENT_RANGE
                                ? "message.fpvdrone.too_far"
                                : "message.fpvdrone.invalid_placement").withColor(0xFFAA36));
            }
            return InteractionResult.FAIL;
        }
        if (!(context.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;

        DroneEntity drone = DroneMod.DRONE_ENTITY.get().create(level, EntitySpawnReason.SPAWN_ITEM_USE);
        if (drone == null) return InteractionResult.FAIL;
        BlockPos spawn = clicked.above();
        drone.snapTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5,
                player.getYRot(), 0.0F);
        drone.configurePlacedDrone(DroneKind.MOSQUITO, player);
        drone.setThermal(thermal);
        drone.loadPayloadFromItem(context.getItemInHand());
        if (!level.noCollision(drone) || !level.addFreshEntity(drone)) return InteractionResult.FAIL;
        if (!player.hasInfiniteMaterials()) context.getItemInHand().shrink(1);
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        CompoundTag tag = StackData.copy(stack);
        if (!tag.getBooleanOr("HasBattery", false)) return false;
        return tag.getCompound("BatteryData").map(BatteryData::load).map(data -> !data.isFullCharge()).orElse(false);
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        BatteryData data = StackData.copy(stack).getCompound("BatteryData")
                .map(BatteryData::load).orElseGet(BatteryData::defaults);
        return Math.round(13.0F * data.remainingMah() / data.capacityMah());
    }

    @Override
    public int getBarColor(ItemStack stack) {
        float ratio = getBarWidth(stack) / 13.0F;
        return Mth.hsvToArgb(ratio / 3.0F, 1.0F, 1.0F, 255);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable(ClientItemState.shiftDown()
                        ? "item.fpvdrone.drone.usage" : "item.fpvdrone.hold_shift")
                .withStyle(ClientItemState.shiftDown() ? ChatFormatting.GRAY : ChatFormatting.DARK_GRAY));
        CompoundTag tag = StackData.copy(stack);
        if (tag.getBooleanOr("HasBattery", false)) {
            BatteryData data = tag.getCompound("BatteryData").map(BatteryData::load).orElseGet(BatteryData::defaults);
            tooltip.accept(Component.translatable("item.fpvdrone.drone.battery_info", data.cells() + "S",
                    data.remainingMah(), data.capacityMah(), String.format(java.util.Locale.ROOT, "%.1f", data.voltage()))
                    .withColor(0xFFAA36));
        } else {
            tooltip.accept(Component.translatable("item.fpvdrone.drone.no_battery").withColor(0xFFAA36));
        }
        if (tag.getBooleanOr("HasRpg", false)) {
            tooltip.accept(Component.translatable("item.fpvdrone.drone.has_rpg").withColor(0xFF5555));
        }
    }
}
