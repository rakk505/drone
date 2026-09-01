package com.modernity.drone.network;

import com.modernity.drone.DroneMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server confirmation for the selected analogue-video channel. */
public record GogglesChannelChangedPayload(int channel) implements CustomPacketPayload {
    public static final Type<GogglesChannelChangedPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(DroneMod.MOD_ID, "goggles_channel_changed")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, GogglesChannelChangedPayload> STREAM_CODEC =
            CustomPacketPayload.codec(GogglesChannelChangedPayload::write, GogglesChannelChangedPayload::new);

    private GogglesChannelChangedPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readUnsignedByte());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeByte(Math.max(1, Math.min(8, channel)));
    }

    @Override
    public Type<GogglesChannelChangedPayload> type() {
        return TYPE;
    }
}
