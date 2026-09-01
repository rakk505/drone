package com.modernity.drone.client.hud;

import com.modernity.drone.client.DroneControlClient;
import com.modernity.drone.client.config.FpvClientConfig;
import com.modernity.drone.client.input.ControllerReader;
import com.modernity.drone.client.osd.BootStateManager;
import com.modernity.drone.client.osd.OsdLayout;
import com.modernity.drone.client.osd.OsdLayoutStore;
import com.modernity.drone.client.osd.OsdRenderer;
import com.modernity.drone.client.osd.OsdTelemetry;
import com.modernity.drone.client.signal.SignalSettings;
import com.modernity.drone.client.signal.SignalState;
import com.modernity.drone.client.thermal.ThermalOverlayRenderer;
import com.modernity.drone.client.thermal.ThermalState;
import com.modernity.drone.client.video.VideoFeedState;
import com.modernity.drone.client.video.VideoOverlayRenderer;
import com.modernity.drone.client.video.VideoResolution;
import com.modernity.drone.entity.DroneEntity;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.gui.GuiLayer;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public final class DroneHudOverlay implements GuiLayer {
    private final OsdRenderer osdRenderer = new OsdRenderer();
    private final BootStateManager boot = new BootStateManager();
    private @Nullable UUID activeDroneId;
    private @Nullable Vec3 pilotOrigin;
    private @Nullable OsdTelemetry lastTelemetry;
    private boolean previousBatteryState;
    private boolean holdingLostSignal;
    private long lastSignalTick = Long.MIN_VALUE;

    @Override
    public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        DroneEntity drone = DroneControlClient.currentDrone();
        Minecraft minecraft = Minecraft.getInstance();
        if (drone == null || minecraft.player == null) {
            if (minecraft.player != null
                    && DroneControlClient.isActive()
                    && activeDroneId != null
                    && activeDroneId.equals(DroneControlClient.activeDroneUuid())
                    && lastTelemetry != null) {
                renderLostSignal(graphics, minecraft, System.currentTimeMillis());
                return;
            }
            endSession();
            return;
        }
        long now = System.currentTimeMillis();
        beginSessionIfNeeded(drone, minecraft, now);
        updateBatteryTransition(drone, now);

        SignalState signal = SignalState.get();
        if (holdingLostSignal) {
            signal.begin(pilotOrigin == null ? minecraft.player.position() : pilotOrigin);
            signal.setSettings(SignalSettings.fromConfig());
            lastSignalTick = Long.MIN_VALUE;
            holdingLostSignal = false;
        }
        if (minecraft.level != null && minecraft.level.getGameTime() != lastSignalTick) {
            lastSignalTick = minecraft.level.getGameTime();
            signal.update(minecraft.level, drone.position());
        }
        float signalStrength = signal.inGracePeriod()
                ? signal.strength()
                : Math.min(signal.strength(), Math.max(0.0F, drone.signalQuality()));
        boolean noSignal = signal.controlBlocked() || !drone.hasBattery();

        VideoFeedState video = VideoFeedState.get();
        syncConfiguredResolution(video);
        VideoFeedState.FrameDecision videoDecision = video.update(
                true,
                minecraft.player.isCreative(),
                signal.distance(),
                noSignal ? 0.0F : signal.freezeStrength(),
                signal.settings(),
                now
        );
        VideoOverlayRenderer.render(graphics, videoDecision, signalStrength, now);

        ThermalState thermal = ThermalState.get();
        thermal.setAutomaticEnabled(drone.isThermal());
        thermal.synchronizeKeyState(now);
        ThermalOverlayRenderer.render(graphics, thermal, drone.batteryFraction(), now);

        OsdLayout layout = OsdLayoutStore.get(drone.getUUID());
        OsdTelemetry telemetry = OsdTelemetry.capture(
                drone,
                minecraft.player,
                pilotOrigin,
                signalStrength,
                signal.distance(),
                noSignal,
                now
        );
        lastTelemetry = telemetry;
        osdRenderer.render(graphics, layout, telemetry, boot.phase(now));
        if (FpvClientConfig.stickOverlay() && drone.isArmed()) {
            renderSticks(graphics);
        }
    }

    private void beginSessionIfNeeded(DroneEntity drone, Minecraft minecraft, long nowMillis) {
        if (drone.getUUID().equals(activeDroneId)) {
            return;
        }
        activeDroneId = drone.getUUID();
        pilotOrigin = minecraft.player == null ? drone.position() : minecraft.player.position();
        previousBatteryState = drone.hasBattery();
        lastSignalTick = Long.MIN_VALUE;
        SignalState.get().begin(pilotOrigin);
        SignalState.get().setSettings(SignalSettings.fromConfig());
        VideoFeedState.get().begin(activeDroneId, nowMillis);
        osdRenderer.beginSession(nowMillis);
        if (previousBatteryState) {
            boot.start(nowMillis);
        }
    }

    private void updateBatteryTransition(DroneEntity drone, long nowMillis) {
        boolean hasBattery = drone.hasBattery();
        if (hasBattery && !previousBatteryState) {
            boot.start(nowMillis);
        } else if (!hasBattery && previousBatteryState) {
            boot.reset();
        }
        previousBatteryState = hasBattery;
    }

    private void endSession() {
        if (activeDroneId == null) {
            return;
        }
        activeDroneId = null;
        pilotOrigin = null;
        lastTelemetry = null;
        previousBatteryState = false;
        holdingLostSignal = false;
        lastSignalTick = Long.MIN_VALUE;
        boot.reset();
        osdRenderer.endSession();
        SignalState.get().reset();
        VideoFeedState.get().end();
        ThermalState.get().setAutomaticEnabled(false);
    }

    private void renderLostSignal(GuiGraphicsExtractor graphics, Minecraft minecraft, long nowMillis) {
        holdingLostSignal = true;
        SignalState signal = SignalState.get();
        signal.forceNoSignal();
        VideoFeedState video = VideoFeedState.get();
        VideoFeedState.FrameDecision videoDecision = video.update(
                true,
                minecraft.player.isCreative(),
                signal.distance(),
                0.0F,
                signal.settings(),
                nowMillis
        );
        VideoOverlayRenderer.render(graphics, videoDecision, 0.0F, nowMillis);

        ThermalState thermal = ThermalState.get();
        thermal.synchronizeKeyState(nowMillis);
        ThermalOverlayRenderer.render(graphics, thermal, lastTelemetry.batteryFraction(), nowMillis);

        OsdLayout layout = OsdLayoutStore.get(activeDroneId);
        osdRenderer.render(graphics, layout, lastTelemetry.asNoSignal(nowMillis), boot.phase(nowMillis));
    }

    private static void syncConfiguredResolution(VideoFeedState video) {
        try {
            video.setResolution(VideoResolution.valueOf(FpvClientConfig.videoResolution().name()));
        } catch (IllegalArgumentException ignored) {
            video.setResolution(VideoResolution.RES_25);
        }
    }

    private static void renderSticks(GuiGraphicsExtractor graphics) {
        ControllerReader controls = ControllerReader.get();
        int centerX = graphics.guiWidth() / 2;
        var player = Minecraft.getInstance().player;
        int centerY = player != null && (player.isCreative() || player.isSpectator())
                ? graphics.guiHeight() - 40
                : graphics.guiHeight() - 80;
        drawStick(graphics, centerX - 30, centerY, controls.yaw(), controls.throttle());
        drawStick(graphics, centerX + 30, centerY, controls.roll(), controls.pitch());
    }

    private static void drawStick(GuiGraphicsExtractor graphics, int x, int y, float horizontal, float vertical) {
        int size = 20;
        int color = 0xCCFFFFFF;
        graphics.verticalLine(x, y - size, y + size, color);
        graphics.horizontalLine(x - size, x + size, y, color);
        int dotX = x + Math.round(MthClamp.clamp(horizontal) * size);
        int dotY = y - Math.round(MthClamp.clamp(vertical) * size);
        graphics.outline(dotX - 2, dotY - 2, 5, 5, 0xFFFFFFFF);
    }

    /** Kept local to avoid coupling the HUD's normalized input contract to Minecraft mappings. */
    private static final class MthClamp {
        private static float clamp(float value) {
            return Math.max(-1.0F, Math.min(1.0F, value));
        }
    }
}
