package com.modernity.drone.network;

import com.modernity.drone.DroneMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/** Parameters for the reference mod's layered close/distant explosion audio. */
public record ExplosionSoundPayload(
        Vec3 position,
        boolean indoors,
        int closeVariant,
        int distantVariant,
        float power
) implements CustomPacketPayload {
    public static final Type<ExplosionSoundPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(DroneMod.MOD_ID, "explosion_sound")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, ExplosionSoundPayload> STREAM_CODEC =
            CustomPacketPayload.codec(ExplosionSoundPayload::write, ExplosionSoundPayload::new);

    private ExplosionSoundPayload(RegistryFriendlyByteBuf buffer) {
        this(new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()),
                buffer.readBoolean(), buffer.readByte(), buffer.readByte(), buffer.readFloat());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeDouble(position.x);
        buffer.writeDouble(position.y);
        buffer.writeDouble(position.z);
        buffer.writeBoolean(indoors);
        buffer.writeByte(closeVariant);
        buffer.writeByte(distantVariant);
        buffer.writeFloat(power);
    }

    @Override
    public Type<ExplosionSoundPayload> type() {
        return TYPE;
    }
}
