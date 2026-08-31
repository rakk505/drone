package com.modernity.drone.network;

import com.modernity.drone.DroneMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record DroneControlPayload(
        int entityId,
        float roll,
        float pitch,
        float yaw,
        float throttle,
        byte actions
) implements CustomPacketPayload {
    public static final byte ARMED = 1;
    public static final byte DROP = 1 << 1;
    public static final byte HOVER = 1 << 2;
    public static final byte RETURN_HOME = 1 << 3;
    public static final Type<DroneControlPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(DroneMod.MOD_ID, "control")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, DroneControlPayload> STREAM_CODEC =
            CustomPacketPayload.codec(DroneControlPayload::write, DroneControlPayload::new);

    private DroneControlPayload(RegistryFriendlyByteBuf buffer) {
        this(
                buffer.readVarInt(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readByte()
        );
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(entityId);
        buffer.writeFloat(roll);
        buffer.writeFloat(pitch);
        buffer.writeFloat(yaw);
        buffer.writeFloat(throttle);
        buffer.writeByte(actions);
    }

    @Override
    public Type<DroneControlPayload> type() {
        return TYPE;
    }
}
