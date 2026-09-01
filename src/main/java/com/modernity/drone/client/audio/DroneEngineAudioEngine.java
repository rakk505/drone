package com.modernity.drone.client.audio;

import com.modernity.drone.client.DroneControlClient;
import com.modernity.drone.entity.DroneEntity;
import java.nio.ShortBuffer;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.EXTEfx;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Positional motor audio from FPV Drones V1.1.4.
 *
 * <p>The stock sound system cannot reproduce the original's independently
 * filtered motor loop, so this deliberately maintains the same small OpenAL
 * source/filter pool and uploads the bundled {@code drone_engine.ogg} as mono
 * PCM.</p>
 */
public final class DroneEngineAudioEngine {
    private static final Logger LOGGER = LoggerFactory.getLogger(DroneEngineAudioEngine.class);
    private static final String LOG_PREFIX = "[FPV Engine Audio]";

    private static final int POOL_SIZE = 16;
    private static final float MAX_RANGE = 80.0F;
    private static final float BASE_GAIN = 1.2F;
    private static final float SOURCE_MAX_GAIN = 2.0F;
    private static final float THROTTLE_IDLE = 0.0F;
    private static final float LOW_PASS_START = 32.0F;
    private static final float LOW_PASS_GAIN_HF_MIN = 0.05F;
    private static final float PITCH_NEAR = 1.0F;
    private static final float PITCH_FAR = 0.75F;
    private static final float OCCLUSION_GAIN = 0.45F;
    private static final float OCCLUSION_HF = 0.18F;
    private static final float SMOOTHING = 0.18F;

    private static final DroneEngineAudioEngine INSTANCE = new DroneEngineAudioEngine();

    private final ArrayDeque<Integer> freeSources = new ArrayDeque<>(POOL_SIZE);
    private final ArrayDeque<Integer> freeFilters = new ArrayDeque<>(POOL_SIZE);
    private final Map<UUID, SourceSlot> activeSources = new HashMap<>();
    private final Map<UUID, SmoothState> smoothStates = new HashMap<>();

    private boolean initialized;
    private boolean decodeStarted;
    private boolean shutdownRequested;
    private boolean efxAvailable;
    private long nextDecodeAttemptMillis;
    private int engineBuffer;
    private volatile OggAudioLoader.DecodedOgg decodedAudio;

    private DroneEngineAudioEngine() {
    }

    public static DroneEngineAudioEngine get() {
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
        byte[] bytes = OggAudioLoader.readResourceBytes("drone_engine", LOGGER, LOG_PREFIX);
        // ClientStartedEvent may arrive while the initial resource reload is
        // still being applied in 26.2. Keep this retryable instead of
        // permanently decoding a missing resource.
        if (bytes == null) {
            return;
        }
        decodeStarted = true;
        Thread decoder = new Thread(
                () -> publishDecodedAudio(OggAudioLoader.decode(
                        "drone_engine", bytes, LOGGER, LOG_PREFIX)),
                "FPVDrone-Engine-AudioDecode"
        );
        decoder.setDaemon(true);
        decoder.start();
    }

    private synchronized void publishDecodedAudio(OggAudioLoader.DecodedOgg decoded) {
        if (shutdownRequested || initialized) {
            releaseDecodedAudio(decoded);
            return;
        }
        if (decoded == null) {
            // A transient resource/decode failure must not permanently disable
            // engine audio for the rest of the client process.
            decodeStarted = false;
            nextDecodeAttemptMillis = System.currentTimeMillis() + 1_000L;
            return;
        }
        decodedAudio = decoded;
    }

    private synchronized void initDecodedAudio() {
        if (shutdownRequested || initialized || ALC10.alcGetCurrentContext() == 0L) {
            return;
        }
        OggAudioLoader.DecodedOgg ready = decodedAudio;
        if (ready == null) {
            return;
        }
        // Claim ownership before touching OpenAL. A concurrent shutdown can
        // now either release the published decode or wait for this method,
        // but can never free the same native PCM twice.
        decodedAudio = null;

        engineBuffer = uploadPcm(ready);
        if (engineBuffer == 0) {
            decodeStarted = false;
            nextDecodeAttemptMillis = System.currentTimeMillis() + 1_000L;
            return;
        }

        for (int index = 0; index < POOL_SIZE; index++) {
            int source = AL10.alGenSources();
            AL10.alSourcef(source, AL10.AL_MIN_GAIN, 0.0F);
            AL10.alSourcef(source, AL10.AL_MAX_GAIN, SOURCE_MAX_GAIN);
            freeSources.add(source);
        }

        try {
            int testFilter = EXTEfx.alGenFilters();
            if (testFilter != 0) {
                EXTEfx.alDeleteFilters(testFilter);
                efxAvailable = true;
                for (int index = 0; index < POOL_SIZE; index++) {
                    int filter = EXTEfx.alGenFilters();
                    EXTEfx.alFilteri(filter, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
                    EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAIN, 1.0F);
                    EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAINHF, 1.0F);
                    freeFilters.add(filter);
                }
            }
        } catch (RuntimeException exception) {
            efxAvailable = false;
            LOGGER.debug("{} EFX low-pass filters unavailable: {}", LOG_PREFIX, exception.getMessage());
        }

        initialized = true;
        LOGGER.info("{} init complete — buffer={} efx={}", LOG_PREFIX, engineBuffer, efxAvailable);
    }

    /** Called once at the end of every client tick. */
    public void tick() {
        initIfNeeded();
        if (!initialized || engineBuffer == 0) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.gameRenderer == null) {
            return;
        }

        Vec3 listenerPosition = minecraft.gameRenderer.mainCamera().position();
        float master = minecraft.options.getSoundSourceVolume(SoundSource.MASTER)
                * minecraft.options.getSoundSourceVolume(SoundSource.BLOCKS);
        DroneEntity localDrone = DroneControlClient.currentDrone();
        UUID localDroneId = localDrone == null ? null : localDrone.getUUID();
        Map<UUID, DroneEntity> armedDrones = new HashMap<>();
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof DroneEntity drone && drone.areMotorsArmed()) {
                armedDrones.put(drone.getUUID(), drone);
            }
        }

        for (Map.Entry<UUID, DroneEntity> entry : armedDrones.entrySet()) {
            UUID id = entry.getKey();
            DroneEntity drone = entry.getValue();
            Vec3 dronePosition = drone.position();
            float distance = (float) listenerPosition.distanceTo(dronePosition);
            if (distance >= MAX_RANGE) {
                releaseIfPresent(id);
                continue;
            }

            float throttleFactor = THROTTLE_IDLE + drone.getSyncedThrottle();
            float targetGain = id.equals(localDroneId)
                    ? 0.0F
                    : computeGain(distance) * BASE_GAIN * throttleFactor * master;
            float targetGainHf = computeGainHf(distance);
            float targetPitch = computePitch(distance);
            if (isOccluded(minecraft.level, listenerPosition, dronePosition)) {
                targetGain *= OCCLUSION_GAIN;
                targetGainHf *= OCCLUSION_HF;
            }

            SourceSlot slot = activeSources.get(id);
            if (slot == null) {
                createLoop(id, dronePosition, targetGainHf, targetPitch);
            } else {
                SmoothState smooth = smoothStates.get(id);
                smooth.gain += (targetGain - smooth.gain) * SMOOTHING;
                smooth.gainHf += (targetGainHf - smooth.gainHf) * SMOOTHING;
                smooth.pitch += (targetPitch - smooth.pitch) * SMOOTHING;
                positionSource(slot.sourceId, dronePosition);
                AL10.alSourcef(slot.sourceId, AL10.AL_GAIN, smooth.gain);
                AL10.alSourcef(slot.sourceId, AL10.AL_PITCH, smooth.pitch);
                applyFilter(slot, smooth.gainHf);
            }
        }

        Iterator<Map.Entry<UUID, SourceSlot>> iterator = activeSources.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, SourceSlot> entry = iterator.next();
            if (armedDrones.containsKey(entry.getKey())) {
                continue;
            }
            releaseSlot(entry.getValue());
            smoothStates.remove(entry.getKey());
            iterator.remove();
        }
    }

    public void onDisconnect() {
        if (initialized) {
            stopAndClearAll();
        }
    }

    public synchronized void shutdown() {
        shutdownRequested = true;

        // Decoding may finish before OpenAL becomes available, or while the
        // client is stopping. Dispose any result that has not been uploaded.
        OggAudioLoader.DecodedOgg pending = decodedAudio;
        decodedAudio = null;
        releaseDecodedAudio(pending);

        if (initialized) {
            stopAndClearAll();
        }
        // Clean partially-created OpenAL objects too. Initialization is not a
        // transaction: a device/extension failure can happen after the buffer
        // or some pool entries have already been allocated.
        if (engineBuffer != 0) {
            AL10.alDeleteBuffers(engineBuffer);
            engineBuffer = 0;
        }
        while (!freeSources.isEmpty()) {
            AL10.alDeleteSources(freeSources.remove());
        }
        while (!freeFilters.isEmpty()) {
            EXTEfx.alDeleteFilters(freeFilters.remove());
        }
        initialized = false;
        efxAvailable = false;
        decodeStarted = false;
    }

    private void createLoop(UUID id, Vec3 position, float targetGainHf, float targetPitch) {
        int source = allocateSource();
        if (source < 0) {
            return;
        }
        int filter = efxAvailable && !freeFilters.isEmpty() ? freeFilters.remove() : -1;
        SourceSlot slot = new SourceSlot(source, filter);
        SmoothState smooth = new SmoothState(0.0F, targetGainHf, targetPitch);

        AL10.alSourcei(source, AL10.AL_BUFFER, engineBuffer);
        positionSource(source, position);
        AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_FALSE);
        AL10.alSourcef(source, AL10.AL_ROLLOFF_FACTOR, 0.0F);
        AL10.alSourcef(source, AL10.AL_MIN_GAIN, 0.0F);
        AL10.alSourcef(source, AL10.AL_MAX_GAIN, SOURCE_MAX_GAIN);
        AL10.alSourcef(source, AL10.AL_GAIN, 0.0F);
        AL10.alSourcef(source, AL10.AL_PITCH, targetPitch);
        AL10.alSourcei(source, AL10.AL_LOOPING, AL10.AL_TRUE);
        applyFilter(slot, targetGainHf);
        AL10.alSourcePlay(source);

        activeSources.put(id, slot);
        smoothStates.put(id, smooth);
    }

    private void applyFilter(SourceSlot slot, float gainHf) {
        if (efxAvailable && slot.filterId >= 0) {
            EXTEfx.alFilterf(slot.filterId, EXTEfx.AL_LOWPASS_GAINHF, gainHf);
            AL10.alSourcei(slot.sourceId, EXTEfx.AL_DIRECT_FILTER, slot.filterId);
        }
    }

    private static void positionSource(int source, Vec3 position) {
        AL10.alSource3f(source, AL10.AL_POSITION,
                (float) position.x(), (float) position.y(), (float) position.z());
    }

    private static float computeGain(float distance) {
        return distance >= MAX_RANGE ? 0.0F : 1.0F - distance / MAX_RANGE;
    }

    private static float computeGainHf(float distance) {
        if (distance <= LOW_PASS_START) {
            return 1.0F;
        }
        if (distance >= MAX_RANGE) {
            return LOW_PASS_GAIN_HF_MIN;
        }
        float interpolation = (distance - LOW_PASS_START) / (MAX_RANGE - LOW_PASS_START);
        return 1.0F - interpolation * (1.0F - LOW_PASS_GAIN_HF_MIN);
    }

    private static float computePitch(float distance) {
        float interpolation = Math.min(distance / MAX_RANGE, 1.0F);
        return PITCH_NEAR + interpolation * (PITCH_FAR - PITCH_NEAR);
    }

    private static boolean isOccluded(Level level, Vec3 from, Vec3 to) {
        HitResult result = level.clip(new ClipContext(
                from,
                to,
                ClipContext.Block.VISUAL,
                ClipContext.Fluid.NONE,
                CollisionContext.empty()
        ));
        return result.getType() == HitResult.Type.BLOCK;
    }

    private int allocateSource() {
        if (!freeSources.isEmpty()) {
            return freeSources.remove();
        }
        LOGGER.warn("{} Source pool exhausted — cannot play drone engine", LOG_PREFIX);
        return -1;
    }

    private void releaseIfPresent(UUID id) {
        SourceSlot slot = activeSources.remove(id);
        smoothStates.remove(id);
        if (slot != null) {
            releaseSlot(slot);
        }
    }

    private void releaseSlot(SourceSlot slot) {
        if (efxAvailable && slot.filterId >= 0) {
            AL10.alSourcei(slot.sourceId, EXTEfx.AL_DIRECT_FILTER, 0);
        }
        AL10.alSourceStop(slot.sourceId);
        AL10.alSourcei(slot.sourceId, AL10.AL_BUFFER, 0);
        freeSources.add(slot.sourceId);
        if (efxAvailable && slot.filterId >= 0) {
            freeFilters.add(slot.filterId);
        }
    }

    private void stopAndClearAll() {
        for (SourceSlot slot : activeSources.values()) {
            releaseSlot(slot);
        }
        activeSources.clear();
        smoothStates.clear();
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

    private static void releaseDecodedAudio(OggAudioLoader.DecodedOgg decoded) {
        if (decoded != null && decoded.pcm() != null) {
            MemoryUtil.memFree(decoded.pcm());
        }
    }

    private record SourceSlot(int sourceId, int filterId) {
    }

    private static final class SmoothState {
        private float gain;
        private float gainHf;
        private float pitch;

        private SmoothState(float gain, float gainHf, float pitch) {
            this.gain = gain;
            this.gainHf = gainHf;
            this.pitch = pitch;
        }
    }
}
