package com.modernity.drone.entity;

import com.modernity.drone.DroneMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.TriState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import com.modernity.drone.item.FpvGogglesItem;

/** Keeps the pilot body's inventory inert while its view is attached to a drone. */
@EventBusSubscriber(modid = DroneMod.MOD_ID)
public final class DroneGameplayEvents {
    private DroneGameplayEvents() {
    }

    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        if (isPiloting(event.getPlayer())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onItemPickup(ItemEntityPickupEvent.Pre event) {
        if (isPiloting(event.getPlayer())) event.setCanPickup(TriState.FALSE);
    }

    @SubscribeEvent
    public static void onExperiencePickup(PlayerXpEvent.PickupXp event) {
        if (isPiloting(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) DroneViewSessions.tick(player);
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            stopPiloting(player);
            DroneViewSessions.stop(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        stopPiloting(player);
        DroneViewSessions.stop(player);

        // V1.1.4 never leaves FPV goggles equipped across a disconnect. This
        // also prevents an immediate camera takeover while the world rejoins.
        ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
        if (helmet.getItem() instanceof FpvGogglesItem) {
            ItemStack goggles = helmet.copy();
            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            if (!player.addItem(goggles)) player.drop(goggles, false);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        DroneLinkManager.clear();
        VillagerPanicHandler.clearCache();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        DroneViewSessions.stopAll(event.getServer());
    }

    private static boolean isPiloting(Player player) {
        return player instanceof ServerPlayer serverPlayer && DroneViewSessions.isActive(serverPlayer)
                || findPilotedDrone(player) != null;
    }

    private static void stopPiloting(ServerPlayer player) {
        DroneEntity drone = findPilotedDrone(player);
        if (drone != null) drone.endPilot(player);
    }

    private static DroneEntity findPilotedDrone(Player player) {
        if (!(player.level() instanceof ServerLevel level)) return null;
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof DroneEntity drone && drone.isPilotedBy(player)) return drone;
        }
        return null;
    }
}
