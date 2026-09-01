package com.modernity.drone.item;

import com.geckolib.animatable.GeoItem;
import com.geckolib.animatable.client.GeoRenderProvider;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.renderer.GeoArmorRenderer;
import com.geckolib.util.GeckoLibUtil;
import com.modernity.drone.client.render.FpvGogglesArmorRenderer;
import com.modernity.drone.client.ClientItemState;
import com.modernity.drone.entity.DroneEntity;
import com.modernity.drone.entity.DroneLinkManager;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.Level;

public final class FpvGogglesItem extends Item implements GeoItem {
    private static final String LINKED_DRONE = "LinkedDroneUUID";
    private static final String VIDEO_CHANNEL = "VideoChannel";
    private static final String LINKED_DRONES = "LinkedDrones";
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    public FpvGogglesItem(Properties properties) {
        super(properties);
        GeoItem.registerSyncedAnimatable(this);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // The goggles are a static wearable model.
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private GeoArmorRenderer<?, ?> armorRenderer;

            @Override
            public GeoArmorRenderer<?, ?> getGeoArmorRenderer(ItemStack stack, EquipmentSlot slot) {
                if (armorRenderer == null) armorRenderer = new FpvGogglesArmorRenderer();
                return armorRenderer;
            }
        });
    }

    public boolean canEquip(ItemStack stack, EquipmentSlot slot, LivingEntity entity) {
        if (slot != EquipmentSlot.HEAD) return false;
        Optional<UUID> linked = getLinkedDroneId(stack);
        if (linked.isEmpty()) return false;
        if (!(entity.level() instanceof ServerLevel serverLevel)) {
            // A remote client may not have the linked craft in its entity set.
            // The authoritative battery/link check is repeated on the server.
            return true;
        }
        Entity found = serverLevel.getEntityInAnyDimension(linked.get());
        if (found instanceof DroneEntity drone) return drone.hasBattery();
        return DroneLinkManager.isLinked(linked.get());
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        Optional<UUID> linked = getLinkedDroneId(stack);
        if (linked.isEmpty()) {
            player.sendOverlayMessage(Component.translatable("item.fpvdrone.fpv_goggles.not_linked").withColor(0xFFAA36));
            return InteractionResult.FAIL;
        }
        if (level instanceof ServerLevel serverLevel) {
            Entity found = serverLevel.getEntityInAnyDimension(linked.get());
            if (found instanceof DroneEntity drone) {
                if (!drone.hasBattery()) {
                    player.sendOverlayMessage(Component.translatable("item.fpvdrone.fpv_goggles.no_battery").withColor(0xFFAA36));
                    return InteractionResult.FAIL;
                }
            } else if (!DroneLinkManager.isLinked(linked.get())) {
                player.sendOverlayMessage(Component.translatable("item.fpvdrone.fpv_goggles.drone_not_found").withColor(0xFFAA36));
                return InteractionResult.FAIL;
            }
        }
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        return equippable == null ? InteractionResult.FAIL : equippable.swapWithEquipmentSlot(stack, player);
    }

    public static Optional<UUID> getLinkedDroneId(ItemStack stack) {
        return StackData.copy(stack).read(LINKED_DRONE, UUIDUtil.LENIENT_CODEC);
    }

    public static void setLinkedDroneId(ItemStack stack, UUID id) {
        StackData.update(stack, tag -> tag.store(LINKED_DRONE, UUIDUtil.CODEC, id));
    }

    public static void setChannel(ItemStack stack, int channel) {
        StackData.update(stack, tag -> tag.putByte(VIDEO_CHANNEL, (byte) clampChannel(channel)));
    }

    public static int getChannel(ItemStack stack) {
        return clampChannel(StackData.copy(stack).getByteOr(VIDEO_CHANNEL, (byte) 1));
    }

    public static Map<Integer, UUID> getLinkedDrones(ItemStack stack) {
        CompoundTag mapTag = StackData.copy(stack).getCompoundOrEmpty(LINKED_DRONES);
        Map<Integer, UUID> result = new TreeMap<>();
        for (String key : mapTag.keySet()) {
            try {
                int channel = clampChannel(Integer.parseInt(key));
                result.put(channel, UUID.fromString(mapTag.getStringOr(key, "")));
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (result.isEmpty()) {
            getLinkedDroneId(stack).ifPresent(id -> result.put(getChannel(stack), id));
        }
        return result;
    }

    public static void linkDroneOnChannel(ItemStack stack, int channel, UUID droneId) {
        int clamped = clampChannel(channel);
        Map<Integer, UUID> links = getLinkedDrones(stack);
        links.put(clamped, droneId);
        StackData.update(stack, root -> {
            CompoundTag map = new CompoundTag();
            links.forEach((key, value) -> map.putString(Integer.toString(key), value.toString()));
            root.put(LINKED_DRONES, map);
            root.store(LINKED_DRONE, UUIDUtil.CODEC, droneId);
            root.putByte(VIDEO_CHANNEL, (byte) clamped);
        });
    }

    private static void saveLinkedDrones(ItemStack stack, Map<Integer, UUID> links) {
        StackData.update(stack, root -> {
            CompoundTag map = new CompoundTag();
            links.forEach((channel, droneId) -> map.putString(Integer.toString(channel), droneId.toString()));
            root.put(LINKED_DRONES, map);
        });
    }

    public static Optional<UUID> getDroneOnChannel(ItemStack stack, int channel) {
        return Optional.ofNullable(getLinkedDrones(stack).get(clampChannel(channel)));
    }

    public static java.util.List<Integer> getAllLinkedChannels(ItemStack stack) {
        return new ArrayList<>(getLinkedDrones(stack).keySet());
    }

    public static void selectChannel(ItemStack stack, int channel) {
        int clamped = clampChannel(channel);
        getDroneOnChannel(stack, clamped).ifPresent(id -> StackData.update(stack, root -> {
            root.putByte(VIDEO_CHANNEL, (byte) clamped);
            root.store(LINKED_DRONE, UUIDUtil.CODEC, id);
        }));
    }

    public static void clearLink(ItemStack stack) {
        StackData.update(stack, tag -> {
            tag.remove(LINKED_DRONE);
            tag.remove("LinkedChannels");
            tag.remove(LINKED_DRONES);
        });
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
        if (!(entity instanceof Player player)) return;
        Map<Integer, UUID> links = getLinkedDrones(stack);
        boolean changed = false;
        Iterator<Map.Entry<Integer, UUID>> iterator = links.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, UUID> entry = iterator.next();
            UUID droneId = entry.getValue();
            if (DroneLinkManager.isLinked(droneId)) continue;
            Entity found = level.getEntityInAnyDimension(droneId);
            if (found instanceof DroneEntity drone && drone.isAlive()) {
                DroneLinkManager.link(droneId, player.getUUID());
            } else {
                iterator.remove();
                changed = true;
            }
        }

        if (changed) {
            if (links.isEmpty()) {
                clearLink(stack);
            } else {
                saveLinkedDrones(stack, links);
                if (!links.containsKey(getChannel(stack))) {
                    Map.Entry<Integer, UUID> first = new TreeMap<>(links).firstEntry();
                    selectChannel(stack, first.getKey());
                }
            }
        }

        if (slot == EquipmentSlot.HEAD && getLinkedDroneId(stack).isEmpty()) {
            ItemStack goggles = stack.copy();
            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            if (!player.addItem(goggles)) player.drop(goggles, false);
            player.sendOverlayMessage(Component.translatable(
                    "fpvdrone.goggles.equip_blocked.no_link").withColor(0xFFAA36));
        }
    }

    private static int clampChannel(int channel) {
        return Math.max(1, Math.min(8, channel));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable(ClientItemState.shiftDown()
                        ? "item.fpvdrone.fpv_goggles.usage" : "item.fpvdrone.hold_shift")
                .withStyle(ClientItemState.shiftDown() ? ChatFormatting.GRAY : ChatFormatting.DARK_GRAY));
        java.util.List<Integer> channels = getAllLinkedChannels(stack);
        if (channels.isEmpty()) {
            tooltip.accept(Component.translatable("item.fpvdrone.fpv_goggles.not_linked").withColor(0xFFAA36));
        } else {
            String joined = channels.stream().map(ch -> "R" + ch).collect(java.util.stream.Collectors.joining(", "));
            tooltip.accept(Component.translatable("item.fpvdrone.fpv_goggles.linked", joined).withColor(0xFFAA36));
            getLinkedDroneId(stack).ifPresent(id -> {
                int power = ClientItemState.linkedDronePower(id);
                tooltip.accept(Component.translatable(power > 0
                                ? "item.fpvdrone.fpv_goggles.ready"
                                : power == 0
                                        ? "item.fpvdrone.fpv_goggles.no_battery"
                                        : "item.fpvdrone.fpv_goggles.drone_not_found")
                        .withColor(0xFFAA36));
            });
        }
    }
}
