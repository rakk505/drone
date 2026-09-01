package com.modernity.drone.client.audio;

import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Layered close/distant explosion playback matching FPV Drones V1.1.4. */
public final class ExplosionAudioEngine {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExplosionAudioEngine.class);
    private static final String LOG_PREFIX = "[FPV Audio]";

    private static final int POOL_SIZE = 16;
    private static final float MIN_AUDIBLE_GAIN = 0.01F;
    private static final float MERGE_RADIUS = 5.0F;
    private static final long MERGE_WINDOW_MILLIS = 200L;
    private static final float MAX_GAIN_BOOST = 2.0F;
    private static final float EXPLOSION_BOOST = 1.6F;
    private static final float MAX_RANGE = 128.0F;

    private static final String[] OGG_NAMES = {
            "bomblet_close0",
            "bomblet_close1",
            "bomblet_distant0",
            "bomblet_distant1",
            "ambience_indoors0",
            "ambience_indoors_distant0"
    };
    private static final ExplosionAudioEngine INSTANCE = new ExplosionAudioEngine();

    private final int[] poolSources = new int[POOL_SIZE];
    private final ArrayDeque<Integer> freeSources = new ArrayDeque<>(POOL_SIZE);
    private final List<Voice> activeVoices = new ArrayList<>();
    private final int[] closeBuffers = new int[2];
    private final int[] distantBuffers = new int[2];
    private final int[] indoorsBuffers = new int[2];

    private boolean initialized;
    private boolean decodeStarted;
    private boolean shutdownRequested;
    private long nextDecodeAttemptMillis;
    private volatile OggAudioLoader.DecodedOgg[] decodedAudio;

    private ExplosionAudioEngine() {
    }

    public static ExplosionAudioEngine get() {
        return INSTANCE;
    }

    public void initIfNeeded() {
        startBackgroundDecodeIfNeeded();
        initDecodedAudio();
    }

    private synchronized void startBackgroundDecodeIfNeeded() {
        if (shutdownRequested || decodeStarted) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now < nextDecodeAttemptMillis) {
            return;
        }
        nextDecodeAttemptMillis = now + 1_000L;

        byte[][] bytes = new byte[OGG_NAMES.length][];
        for (int index = 0; index < OGG_NAMES.length; index++) {
            bytes[index] = OggAudioLoader.readResourceBytes(OGG_NAMES[index], LOGGER, LOG_PREFIX);
            if (bytes[index] == null) {
                return;
            }
        }
        decodeStarted = true;

        Thread decoder = new Thread(() -> {
            OggAudioLoader.DecodedOgg[] result = new OggAudioLoader.DecodedOgg[OGG_NAMES.length];
            for (int index = 0; index < OGG_NAMES.length; index++) {
                result[index] = OggAudioLoader.decode(OGG_NAMES[index], bytes[index], LOGGER, LOG_PREFIX);
                if (result[index] == null) {
                    break;
                }
            }
            publishDecodedAudio(result);
        }, "FPVDrone-Explosion-AudioDecode");
        decoder.setDaemon(true);
        decoder.start();
    }

    private synchronized void publishDecodedAudio(OggAudioLoader.DecodedOgg[] decoded) {
        if (shutdownRequested || initialized || !allDecoded(decoded)) {
            releaseDecodedAudio(decoded);
            if (!shutdownRequested && !initialized) {
                decodeStarted = false;
                nextDecodeAttemptMillis = System.currentTimeMillis() + 1_000L;
            }
            return;
        }
        decodedAudio = decoded;
    }

    private synchronized void initDecodedAudio() {
        if (shutdownRequested || initialized || ALC10.alcGetCurrentContext() == 0L) {
            return;
        }
        OggAudioLoader.DecodedOgg[] ready = decodedAudio;
        if (ready == null) {
            return;
        }
        // Atomically transfer ownership from the decoder thread. Each entry
        // is then either consumed by uploadPcm or released in the finally.
        decodedAudio = null;

        try {
            closeBuffers[0] = takeAndUpload(ready, 0);
            closeBuffers[1] = takeAndUpload(ready, 1);
            distantBuffers[0] = takeAndUpload(ready, 2);
            distantBuffers[1] = takeAndUpload(ready, 3);
            indoorsBuffers[0] = takeAndUpload(ready, 4);
            indoorsBuffers[1] = takeAndUpload(ready, 5);
        } finally {
            releaseDecodedAudio(ready);
        }

        for (int index = 0; index < POOL_SIZE; index++) {
            poolSources[index] = AL10.alGenSources();
            AL10.alSourcef(poolSources[index], AL10.AL_MIN_GAIN, 0.0F);
            freeSources.add(poolSources[index]);
        }
        initialized = true;
        LOGGER.info(
                "{} init complete — buffers close={}/{} distant={}/{} indoors={}/{}",
                LOG_PREFIX,
                closeBuffers[0], closeBuffers[1],
                distantBuffers[0], distantBuffers[1],
                indoorsBuffers[0], indoorsBuffers[1]
        );
    }

    /**
     * Plays the variants selected by the server so every nearby client hears
     * the same blast layers.
     */
    public void playExplosion(Vec3 worldPosition, boolean indoors,
                              int closeVariant, int distantVariant, float power) {
        initIfNeeded();
        if (!initialized) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.gameRenderer == null) {
            return;
        }
        updateListener(minecraft);

        Vec3 listenerPosition = minecraft.gameRenderer.mainCamera().position();
        float distance = (float) listenerPosition.distanceTo(worldPosition);
        Gains gains = computeGains(distance, indoors);
        if (gains.close <= 0.0F && gains.distant <= 0.0F) {
            return;
        }

        float master = minecraft.options.getSoundSourceVolume(SoundSource.MASTER)
                * minecraft.options.getSoundSourceVolume(SoundSource.BLOCKS);
        long now = System.currentTimeMillis();
        for (Voice candidate : activeVoices) {
            if (now - candidate.startMillis <= MERGE_WINDOW_MILLIS
                    && candidate.worldPosition.distanceTo(worldPosition) <= MERGE_RADIUS) {
                candidate.gainBoost = Math.min(candidate.gainBoost + power / 5.0F, MAX_GAIN_BOOST);
                return;
            }
        }

        int closeIndex = Math.max(0, Math.min(closeVariant, closeBuffers.length - 1));
        int distantIndex = Math.max(0, Math.min(distantVariant, distantBuffers.length - 1));
        int closeBuffer = indoors ? indoorsBuffers[0] : closeBuffers[closeIndex];
        int distantBuffer = indoors ? indoorsBuffers[1] : distantBuffers[distantIndex];
        Voice voice = new Voice(worldPosition, indoors);

        if (gains.close >= MIN_AUDIBLE_GAIN && closeBuffer != 0) {
            int source = allocateSource();
            if (source >= 0) {
                setupSource(source, closeBuffer, worldPosition, gains.close * master * EXPLOSION_BOOST);
                voice.closeSourceId = source;
            }
        }
        if (gains.distant >= MIN_AUDIBLE_GAIN && distantBuffer != 0) {
            int source = allocateSource();
            if (source >= 0) {
                setupSource(source, distantBuffer, worldPosition, gains.distant * master * EXPLOSION_BOOST);
                voice.distantSourceId = source;
            }
        }

        if (voice.closeSourceId >= 0 || voice.distantSourceId >= 0) {
            activeVoices.add(voice);
            if (voice.closeSourceId >= 0) {
                AL10.alSourcePlay(voice.closeSourceId);
            }
            if (voice.distantSourceId >= 0) {
                AL10.alSourcePlay(voice.distantSourceId);
            }
        }
    }

    /** Called once at the end of every client tick. */
    public void tick() {
        initIfNeeded();
        if (!initialized) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gameRenderer == null) {
            return;
        }
        updateListener(minecraft);
        if (activeVoices.isEmpty()) {
            return;
        }

        Vec3 listenerPosition = minecraft.gameRenderer.mainCamera().position();
        float master = minecraft.options.getSoundSourceVolume(SoundSource.MASTER)
                * minecraft.options.getSoundSourceVolume(SoundSource.BLOCKS);
        Iterator<Voice> iterator = activeVoices.iterator();
        while (iterator.hasNext()) {
            Voice voice = iterator.next();
            float distance = (float) listenerPosition.distanceTo(voice.worldPosition);
            Gains gains = computeGains(distance, voice.indoors);
            float closeGain = gains.close * master * voice.gainBoost * EXPLOSION_BOOST;
            float distantGain = gains.distant * master * voice.gainBoost * EXPLOSION_BOOST;

            if (voice.closeSourceId >= 0) {
                if (sourceFinished(voice.closeSourceId) || closeGain < MIN_AUDIBLE_GAIN) {
                    releaseSource(voice.closeSourceId);
                    voice.closeSourceId = -1;
                } else {
                    positionSource(voice.closeSourceId, voice.worldPosition);
                    AL10.alSourcef(voice.closeSourceId, AL10.AL_GAIN, closeGain);
                }
            }
            if (voice.distantSourceId >= 0) {
                if (sourceFinished(voice.distantSourceId) || distantGain < MIN_AUDIBLE_GAIN) {
                    releaseSource(voice.distantSourceId);
                    voice.distantSourceId = -1;
                } else {
                    positionSource(voice.distantSourceId, voice.worldPosition);
                    AL10.alSourcef(voice.distantSourceId, AL10.AL_GAIN, distantGain);
                }
            }
            if (voice.closeSourceId < 0 && voice.distantSourceId < 0) {
                iterator.remove();
            }
        }
    }

    public void onDisconnect() {
        if (initialized) {
            stopAndClearAllVoices();
        }
    }

    public synchronized void shutdown() {
        shutdownRequested = true;

        // A background decode can complete before an OpenAL context appears.
        // Release that pending result instead of leaking it at client exit.
        OggAudioLoader.DecodedOgg[] pending = decodedAudio;
        decodedAudio = null;
        releaseDecodedAudio(pending);

        if (initialized) {
            stopAndClearAllVoices();
        }
        // Include objects allocated immediately before an initialization
        // failure; those exist even though initialized was never committed.
        for (int index = 0; index < poolSources.length; index++) {
            int source = poolSources[index];
            if (source != 0) {
                AL10.alDeleteSources(source);
                poolSources[index] = 0;
            }
        }
        deleteBuffers(closeBuffers);
        deleteBuffers(distantBuffers);
        deleteBuffers(indoorsBuffers);
        freeSources.clear();
        initialized = false;
        decodeStarted = false;
    }

    private static Gains computeGains(float distance, boolean indoors) {
        float fullRadius = indoors ? 8.0F : 32.0F;
        float attenuation = distance <= fullRadius
                ? 1.0F
                : Math.max(0.0F, 1.0F - (distance - fullRadius) / (MAX_RANGE - fullRadius));

        if (indoors) {
            if (distance < 8.0F) {
                return new Gains(attenuation, 0.0F);
            }
            if (distance < 16.0F) {
                float interpolation = (distance - 8.0F) / 8.0F;
                return new Gains((1.0F - interpolation) * attenuation, interpolation * attenuation);
            }
            if (distance < 64.0F) {
                return new Gains(0.0F, attenuation);
            }
            if (distance < MAX_RANGE) {
                float interpolation = (distance - 64.0F) / 64.0F;
                return new Gains(0.0F, (1.0F - interpolation) * attenuation);
            }
            return Gains.SILENT;
        }

        if (distance < 32.0F) {
            return new Gains(attenuation, 0.0F);
        }
        if (distance < 64.0F) {
            float interpolation = (distance - 32.0F) / 32.0F;
            return new Gains((1.0F - interpolation) * attenuation, interpolation * attenuation);
        }
        if (distance < MAX_RANGE) {
            float interpolation = (distance - 64.0F) / 64.0F;
            return new Gains(0.0F, (1.0F - interpolation) * attenuation);
        }
        return Gains.SILENT;
    }

    private int allocateSource() {
        if (!freeSources.isEmpty()) {
            return freeSources.remove();
        }

        Voice oldest = null;
        for (Voice voice : activeVoices) {
            if ((voice.closeSourceId >= 0 || voice.distantSourceId >= 0)
                    && (oldest == null || voice.startMillis < oldest.startMillis)) {
                oldest = voice;
            }
        }
        if (oldest != null) {
            if (oldest.closeSourceId >= 0) {
                releaseSource(oldest.closeSourceId);
                oldest.closeSourceId = -1;
            }
            if (oldest.distantSourceId >= 0) {
                releaseSource(oldest.distantSourceId);
                oldest.distantSourceId = -1;
            }
            activeVoices.remove(oldest);
        }
        return freeSources.isEmpty() ? -1 : freeSources.remove();
    }

    private static void setupSource(int source, int buffer, Vec3 position, float gain) {
        AL10.alSourcei(source, AL10.AL_BUFFER, buffer);
        positionSource(source, position);
        AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_FALSE);
        AL10.alSourcef(source, AL10.AL_ROLLOFF_FACTOR, 0.0F);
        AL10.alSourcef(source, AL10.AL_MIN_GAIN, 0.0F);
        AL10.alSourcef(source, AL10.AL_MAX_GAIN, 2.0F);
        AL10.alSourcef(source, AL10.AL_GAIN, gain);
        AL10.alSourcei(source, AL10.AL_LOOPING, AL10.AL_FALSE);
    }

    private static void positionSource(int source, Vec3 position) {
        AL10.alSource3f(source, AL10.AL_POSITION,
                (float) position.x(), (float) position.y(), (float) position.z());
    }

    private static boolean sourceFinished(int source) {
        return AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING;
    }

    private void releaseSource(int source) {
        AL10.alSourceStop(source);
        AL10.alSourcei(source, AL10.AL_BUFFER, 0);
        freeSources.add(source);
    }

    private void stopAndClearAllVoices() {
        for (Voice voice : activeVoices) {
            if (voice.closeSourceId >= 0) {
                releaseSource(voice.closeSourceId);
            }
            if (voice.distantSourceId >= 0) {
                releaseSource(voice.distantSourceId);
            }
        }
        activeVoices.clear();
    }

    private static void updateListener(Minecraft minecraft) {
        Camera camera = minecraft.gameRenderer.mainCamera();
        Vec3 position = camera.position();
        AL10.alListener3f(AL10.AL_POSITION,
                (float) position.x(), (float) position.y(), (float) position.z());
        AL10.alListener3f(AL10.AL_VELOCITY, 0.0F, 0.0F, 0.0F);
        Vector3fc look = camera.forwardVector();
        Vector3fc up = camera.upVector();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer orientation = stack.mallocFloat(6);
            orientation.put(look.x()).put(look.y()).put(look.z());
            orientation.put(up.x()).put(up.y()).put(up.z());
            orientation.flip();
            AL10.alListenerfv(AL10.AL_ORIENTATION, orientation);
        }
    }

    private static int uploadPcm(OggAudioLoader.DecodedOgg decoded) {
        if (decoded == null || decoded.pcm() == null) {
            return 0;
        }
        ShortBuffer pcm = decoded.pcm();
        try {
            int buffer = AL10.alGenBuffers();
            AL10.alBufferData(buffer, AL10.AL_FORMAT_MONO16, pcm, decoded.sampleRate());
            return buffer;
        } finally {
            MemoryUtil.memFree(pcm);
        }
    }

    private static int takeAndUpload(OggAudioLoader.DecodedOgg[] decoded, int index) {
        OggAudioLoader.DecodedOgg sample = decoded[index];
        decoded[index] = null;
        return uploadPcm(sample);
    }

    private static boolean allDecoded(OggAudioLoader.DecodedOgg[] decoded) {
        if (decoded == null || decoded.length != OGG_NAMES.length) {
            return false;
        }
        for (OggAudioLoader.DecodedOgg sample : decoded) {
            if (sample == null || sample.pcm() == null) {
                return false;
            }
        }
        return true;
    }

    private static void releaseDecodedAudio(OggAudioLoader.DecodedOgg[] decoded) {
        if (decoded == null) {
            return;
        }
        for (int index = 0; index < decoded.length; index++) {
            OggAudioLoader.DecodedOgg sample = decoded[index];
            decoded[index] = null;
            if (sample != null && sample.pcm() != null) {
                MemoryUtil.memFree(sample.pcm());
            }
        }
    }

    private static void deleteBuffers(int[] buffers) {
        for (int index = 0; index < buffers.length; index++) {
            if (buffers[index] != 0) {
                AL10.alDeleteBuffers(buffers[index]);
                buffers[index] = 0;
            }
        }
    }

    private record Gains(float close, float distant) {
        private static final Gains SILENT = new Gains(0.0F, 0.0F);
    }

    private static final class Voice {
        private final Vec3 worldPosition;
        private final boolean indoors;
        private final long startMillis = System.currentTimeMillis();
        private int closeSourceId = -1;
        private int distantSourceId = -1;
        private float gainBoost = 1.0F;

        private Voice(Vec3 worldPosition, boolean indoors) {
            this.worldPosition = worldPosition;
            this.indoors = indoors;
        }
    }
}
