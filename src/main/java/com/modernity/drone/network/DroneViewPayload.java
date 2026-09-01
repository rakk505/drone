package com.modernity.drone.network;

import com.modernity.drone.DroneMod;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Opens or closes the server's remote chunk-streaming view for one drone. */
public record DroneViewPayload(UUID droneId, boolean active) implements CustomPacketPayload {
    public static final Type<DroneViewPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(DroneMod.MOD_ID, "view_session")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, DroneViewPayload> STREAM_CODEC =
            CustomPacketPayload.codec(DroneViewPayload::write, DroneViewPayload::new);

    private DroneViewPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readUUID(), buffer.readBoolean());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(droneId);
        buffer.writeBoolean(active);
    }

    @Override
    public Type<DroneViewPayload> type() {
        return TYPE;
    }
}
