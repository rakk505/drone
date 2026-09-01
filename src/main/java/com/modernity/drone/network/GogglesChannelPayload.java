package com.modernity.drone.network;

import com.modernity.drone.DroneMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Requests the next or previous powered video channel while goggles are worn. */
public record GogglesChannelPayload(int direction) implements CustomPacketPayload {
    public static final Type<GogglesChannelPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(DroneMod.MOD_ID, "change_goggles_channel")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, GogglesChannelPayload> STREAM_CODEC =
            CustomPacketPayload.codec(GogglesChannelPayload::write, GogglesChannelPayload::new);

    private GogglesChannelPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readByte());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeByte(direction > 0 ? 1 : -1);
    }

    @Override
    public Type<GogglesChannelPayload> type() {
        return TYPE;
    }
}
