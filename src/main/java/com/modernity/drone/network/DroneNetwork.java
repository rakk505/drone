package com.modernity.drone.network;

import com.modernity.drone.entity.DroneEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
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
}
