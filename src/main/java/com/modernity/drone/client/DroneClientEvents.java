package com.modernity.drone.client;

import com.modernity.drone.DroneMod;
import com.modernity.drone.entity.DroneEntity;
import com.modernity.drone.flight.DroneKind;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

@EventBusSubscriber(modid = DroneMod.MOD_ID, value = Dist.CLIENT)
public final class DroneClientEvents {
    private DroneClientEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        DroneControlClient.tick(Minecraft.getInstance());
    }

    @SubscribeEvent
    public static void onUse(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null
                || !(minecraft.hitResult instanceof EntityHitResult hit)
                || !(hit.getEntity() instanceof DroneEntity drone)
                || !DroneControlClient.isControllerFor(
                        drone,
                        minecraft.player.getItemInHand(event.getHand()))) {
            return;
        }
        DroneControlClient.requestActivation(drone);
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        DroneEntity drone = DroneControlClient.currentDrone();
        if (drone == null) {
            return;
        }
        // The FPV camera is rigidly attached to the airframe. The payload drone has a
        // stabilized gimbal, so its camera deliberately keeps a level horizon.
        event.setRoll(drone.kind() == DroneKind.MOSQUITO
                ? -drone.rollDegrees((float) event.getPartialTick())
                : 0.0F);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        DroneControlClient.clear(false);
    }
}
