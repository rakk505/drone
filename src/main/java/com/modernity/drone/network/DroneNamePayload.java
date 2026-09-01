package com.modernity.drone.network;

import com.modernity.drone.DroneMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Updates just the user-facing airframe name without trusting stale client-side flight settings. */
public record DroneNamePayload(int entityId, String name) implements CustomPacketPayload {
    public static final Type<DroneNamePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(DroneMod.MOD_ID, "drone_name")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, DroneNamePayload> STREAM_CODEC =
            CustomPacketPayload.codec(DroneNamePayload::write, DroneNamePayload::new);

    public DroneNamePayload {
        name = name == null || name.isBlank() ? "KINDER"
                : name.substring(0, Math.min(20, name.length()));
    }

    private DroneNamePayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readUtf(20));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(entityId);
        buffer.writeUtf(name, 20);
    }

    @Override
    public Type<DroneNamePayload> type() {
        return TYPE;
    }
}
