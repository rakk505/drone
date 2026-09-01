package com.modernity.drone.network;

import com.modernity.drone.DroneMod;
import com.modernity.drone.flight.DroneFlightConfig;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Synchronizes the selected airframe's editable Betaflight configuration. */
public record DroneConfigPayload(
        int entityId,
        float yawRcRate,
        float pitchRcRate,
        float rollRcRate,
        float yawSuperRate,
        float pitchSuperRate,
        float rollSuperRate,
        float yawExpo,
        float pitchExpo,
        float rollExpo,
        float motorKv,
        float propDiameterInches,
        float propPitchInches,
        float dragCoefficient,
        float thrustMultiplier,
        boolean flightMode3d,
        String droneName
) implements CustomPacketPayload {
    public static final Type<DroneConfigPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(DroneMod.MOD_ID, "drone_config")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, DroneConfigPayload> STREAM_CODEC =
            CustomPacketPayload.codec(DroneConfigPayload::write, DroneConfigPayload::new);

    private DroneConfigPayload(RegistryFriendlyByteBuf buffer) {
        this(
                buffer.readVarInt(),
                buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
                buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
                buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
                buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
                buffer.readFloat(), buffer.readFloat(), buffer.readBoolean(), buffer.readUtf(20)
        );
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(entityId);
        buffer.writeFloat(yawRcRate); buffer.writeFloat(pitchRcRate); buffer.writeFloat(rollRcRate);
        buffer.writeFloat(yawSuperRate); buffer.writeFloat(pitchSuperRate); buffer.writeFloat(rollSuperRate);
        buffer.writeFloat(yawExpo); buffer.writeFloat(pitchExpo); buffer.writeFloat(rollExpo);
        buffer.writeFloat(motorKv); buffer.writeFloat(propDiameterInches); buffer.writeFloat(propPitchInches);
        buffer.writeFloat(dragCoefficient); buffer.writeFloat(thrustMultiplier);
        buffer.writeBoolean(flightMode3d);
        buffer.writeUtf(droneName == null ? "KINDER" : droneName, 20);
    }

    public DroneFlightConfig config() {
        return new DroneFlightConfig(
                yawRcRate, pitchRcRate, rollRcRate,
                yawSuperRate, pitchSuperRate, rollSuperRate,
                yawExpo, pitchExpo, rollExpo,
                motorKv, propDiameterInches, propPitchInches,
                dragCoefficient, thrustMultiplier, flightMode3d, droneName
        );
    }

    @Override
    public Type<DroneConfigPayload> type() {
        return TYPE;
    }
}
