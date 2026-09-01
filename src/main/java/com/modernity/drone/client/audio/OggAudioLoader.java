package com.modernity.drone.client.audio;

import com.modernity.drone.DroneMod;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.libc.LibCStdlib;
import org.slf4j.Logger;

/** Shared native OGG loader used by the two V1.1.4 OpenAL engines. */
final class OggAudioLoader {
    private OggAudioLoader() {
    }

    static byte[] readResourceBytes(String soundName, Logger logger, String logPrefix) {
        Identifier location = Identifier.fromNamespaceAndPath(
                DroneMod.MOD_ID,
                "sounds/" + soundName + ".ogg"
        );
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(location);
        if (resource.isEmpty()) {
            logger.warn("{} Resource not found: {}", logPrefix, location);
            return null;
        }

        try (InputStream stream = resource.get().open()) {
            return stream.readAllBytes();
        } catch (Exception exception) {
            logger.warn("{} Read failed for {}: {}", logPrefix, soundName, exception.getMessage());
            return null;
        }
    }

    static DecodedOgg decode(String soundName, byte[] bytes, Logger logger, String logPrefix) {
        if (bytes == null) {
            return null;
        }

        ByteBuffer nativeData = MemoryUtil.memAlloc(bytes.length);
        try {
            nativeData.put(bytes).flip();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer channels = stack.mallocInt(1);
                IntBuffer sampleRate = stack.mallocInt(1);
                ShortBuffer pcm = STBVorbis.stb_vorbis_decode_memory(nativeData, channels, sampleRate);
                if (pcm == null) {
                    logger.warn("{} STBVorbis decode failed: {}", logPrefix, soundName);
                    return null;
                }

                try {
                    int channelCount = channels.get(0);
                    int rate = sampleRate.get(0);
                    if (channelCount <= 0 || rate <= 0) {
                        logger.warn("{} Invalid OGG metadata for {}: channels={} rate={}",
                                logPrefix, soundName, channelCount, rate);
                        return null;
                    }

                    // stb_vorbis_decode_memory allocates its result with the C
                    // runtime's malloc. Minecraft configures MemoryUtil to use
                    // jemalloc, so passing that result to MemoryUtil.memFree is
                    // an allocator mismatch and can crash the whole JVM. Copy
                    // every decode into our own MemoryUtil allocation, leaving
                    // one uniform ownership contract for the OpenAL uploaders.
                    // V1.1.4 intentionally keeps the first channel because
                    // positioned OpenAL sources must be mono.
                    int frames = pcm.remaining() / channelCount;
                    if (frames <= 0) {
                        logger.warn("{} OGG contains no audio frames: {}", logPrefix, soundName);
                        return null;
                    }
                    ShortBuffer mono = MemoryUtil.memAllocShort(frames);
                    boolean completed = false;
                    try {
                        for (int frame = 0; frame < frames; frame++) {
                            mono.put(frame, pcm.get(frame * channelCount));
                        }
                        mono.position(0).limit(frames);
                        completed = true;
                        return new DecodedOgg(mono, rate);
                    } finally {
                        if (!completed) {
                            MemoryUtil.memFree(mono);
                        }
                    }
                } finally {
                    LibCStdlib.free(pcm);
                }
            }
        } catch (Exception exception) {
            logger.warn("{} Decode error for {}: {}", logPrefix, soundName, exception.getMessage());
            return null;
        } finally {
            MemoryUtil.memFree(nativeData);
        }
    }

    record DecodedOgg(ShortBuffer pcm, int sampleRate) {
    }
}
