package com.modernity.drone.network;

import com.modernity.drone.entity.DroneEntity;
import com.modernity.drone.entity.DroneLinkManager;
import com.modernity.drone.entity.DroneViewSessions;
import com.modernity.drone.item.FpvGogglesItem;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class DroneNetwork {
    private DroneNetwork() {
    }

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("2").playToServer(
                DroneControlPayload.TYPE,
                DroneControlPayload.STREAM_CODEC,
                DroneNetwork::handleControl
        ).playToServer(
                DroneViewPayload.TYPE,
                DroneViewPayload.STREAM_CODEC,
                DroneNetwork::handleViewSession
        ).playToServer(
                GogglesChannelPayload.TYPE,
                GogglesChannelPayload.STREAM_CODEC,
                DroneNetwork::handleGogglesChannel
        ).playToServer(
                DroneConfigPayload.TYPE,
                DroneConfigPayload.STREAM_CODEC,
                DroneNetwork::handleDroneConfig
        ).playToServer(
                DroneNamePayload.TYPE,
                DroneNamePayload.STREAM_CODEC,
                DroneNetwork::handleDroneName
        ).playToClient(
                ExplosionSoundPayload.TYPE,
                ExplosionSoundPayload.STREAM_CODEC,
                DroneNetwork::handleExplosionSound
        ).playToClient(
                GogglesChannelChangedPayload.TYPE,
                GogglesChannelChangedPayload.STREAM_CODEC,
                DroneNetwork::handleGogglesChannelChanged
        );
    }

    private static void handleControl(DroneControlPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        Entity entity = player.level().getEntity(payload.entityId());
        if (entity instanceof DroneEntity droneEntity) {
            droneEntity.acceptPilotInput(player, payload);
        }
    }

    private static void handleViewSession(DroneViewPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (payload.active()) {
            DroneViewSessions.start(player, payload.droneId());
        } else {
            DroneViewSessions.stop(player);
        }
    }

    private static void handleGogglesChannel(GogglesChannelPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        ItemStack goggles = player.getItemBySlot(EquipmentSlot.HEAD);
        if (!(goggles.getItem() instanceof FpvGogglesItem)) {
            goggles = player.getMainHandItem();
            if (!(goggles.getItem() instanceof FpvGogglesItem)) {
                goggles = player.getOffhandItem();
                if (!(goggles.getItem() instanceof FpvGogglesItem)) return;
            }
        }

        int current = FpvGogglesItem.getChannel(goggles);
        Map<Integer, UUID> links = FpvGogglesItem.getLinkedDrones(goggles);
        if (links.size() < 2) return;

        TreeSet<Integer> available = new TreeSet<>();
        for (Map.Entry<Integer, UUID> entry : links.entrySet()) {
            Entity entity = player.level().getEntityInAnyDimension(entry.getValue());
            if (entry.getKey() == current || entity instanceof DroneEntity drone && drone.hasBattery()) {
                available.add(entry.getKey());
            }
        }
        if (available.size() < 2) return;

        Integer selected = payload.direction() > 0 ? available.higher(current) : available.lower(current);
        if (selected == null) selected = payload.direction() > 0 ? available.first() : available.last();
        if (selected == current) return;

        FpvGogglesItem.getLinkedDroneId(goggles).ifPresent(oldId -> {
            Entity oldEntity = player.level().getEntityInAnyDimension(oldId);
            if (oldEntity instanceof DroneEntity oldDrone) oldDrone.endPilot(player);
        });
        UUID newDroneId = links.get(selected);
        FpvGogglesItem.selectChannel(goggles, selected);
        DroneLinkManager.link(newDroneId, player.getUUID());
        Entity newEntity = player.level().getEntityInAnyDimension(newDroneId);
        if (newEntity instanceof DroneEntity newDrone) {
            newDrone.beginPilot(player);
            DroneViewSessions.start(player, newDroneId);
        }
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                player, new GogglesChannelChangedPayload(selected)
        );
    }

    private static void handleDroneConfig(DroneConfigPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        Entity entity = player.level().getEntity(payload.entityId());
        if (!(entity instanceof DroneEntity drone)) return;
        if (drone.canPlayerControl(player)) drone.setFlightConfig(payload.config());
    }

    private static void handleDroneName(DroneNamePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        Entity entity = player.level().getEntity(payload.entityId());
        if (!(entity instanceof DroneEntity drone)) return;
        boolean activePilot = drone.canPlayerControl(player);
        boolean nearbyOwner = player.distanceToSqr(drone) <= 100.0
                && (drone.getOwnerUuid() == null || drone.getOwnerUuid().equals(player.getUUID()));
        if (!activePilot && !nearbyOwner) return;

        var current = drone.getFlightConfig();
        drone.setFlightConfig(new com.modernity.drone.flight.DroneFlightConfig(
                current.yawRcRate(), current.pitchRcRate(), current.rollRcRate(),
                current.yawSuperRate(), current.pitchSuperRate(), current.rollSuperRate(),
                current.yawExpo(), current.pitchExpo(), current.rollExpo(),
                current.motorKv(), current.propDiameterInches(), current.propPitchInches(),
                current.dragCoefficient(), current.thrustMultiplier(), current.flightMode3d(), payload.name()
        ));
    }

    private static void handleExplosionSound(ExplosionSoundPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!context.player().level().isClientSide()) return;
            try {
                Class<?> audio = Class.forName("com.modernity.drone.client.audio.FpvAudioEngine");
                audio.getMethod("playExplosion", net.minecraft.world.phys.Vec3.class,
                                boolean.class, int.class, int.class, float.class)
                        .invoke(null, payload.position(), payload.indoors(), payload.closeVariant(),
                                payload.distantVariant(), payload.power());
            } catch (ReflectiveOperationException exception) {
                com.modernity.drone.DroneMod.LOGGER.warn("Unable to play FPV explosion audio", exception);
            }
        });
    }

    private static void handleGogglesChannelChanged(GogglesChannelChangedPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> context.player().sendOverlayMessage(
                Component.translatable("item.fpvdrone.fpv_goggles.channel_changed", "R" + payload.channel())
                        .withColor(0xFF9436)
        ));
    }
}
