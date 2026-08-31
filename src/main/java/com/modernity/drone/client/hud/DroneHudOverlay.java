package com.modernity.drone.client.hud;

import com.modernity.drone.client.DroneControlClient;
import com.modernity.drone.entity.DroneEntity;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.gui.GuiLayer;

public final class DroneHudOverlay implements GuiLayer {
    private static final int PANEL = 0xA0101818;
    private static final int TEXT = 0xFFE4F4E8;
    private static final int GOOD = 0xFF68F28B;
    private static final int WARN = 0xFFFFC04A;
    private static final int DANGER = 0xFFFF5353;

    @Override
    public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        DroneEntity drone = DroneControlClient.currentDrone();
        Minecraft minecraft = Minecraft.getInstance();
        if (drone == null || minecraft.player == null) {
            return;
        }

        Font font = minecraft.font;
        int x = 8;
        int y = 8;
        int width = 116;
        int height = 70;
        graphics.fill(x - 4, y - 4, x + width, y + height, PANEL);

        int statusColor = drone.isArmed() ? DANGER : GOOD;
        graphics.text(font, Component.translatable(
                drone.isArmed() ? "hud.drone.armed" : "hud.drone.disarmed"), x, y, statusColor, true);
        graphics.text(font, Component.translatable(
                "hud.drone.signal", Math.round(drone.signalQuality() * 100.0F)), x, y + 11,
                telemetryColor(drone.signalQuality()), true);
        graphics.text(font, Component.translatable(
                "hud.drone.battery", Math.round(drone.batteryFraction() * 100.0F)), x, y + 22,
                telemetryColor(drone.batteryFraction()), true);
        graphics.text(font, Component.translatable(
                "hud.drone.altitude", String.format(java.util.Locale.ROOT, "%.1f", drone.getY())), x, y + 33, TEXT, true);
        graphics.text(font, Component.translatable(
                "hud.drone.speed", String.format(java.util.Locale.ROOT, "%.1f", drone.getDeltaMovement().length() * 20.0)), x, y + 44, TEXT, true);
        graphics.text(font, Component.translatable(
                "hud.drone.payloads", drone.payloadsLoaded()), x, y + 55, TEXT, true);

        String mode = DroneControlClient.returnHome()
                ? "RTH"
                : DroneControlClient.hoverMode() ? "HOVER" : "MANUAL";
        String throttle = "THR " + Math.round(DroneControlClient.requestedThrottle() * 100.0F) + "%";
        graphics.centeredText(font, mode + "  " + throttle, graphics.guiWidth() / 2, graphics.guiHeight() - 34, WARN);
    }

    private static int telemetryColor(float fraction) {
        if (fraction < 0.15F) {
            return DANGER;
        }
        if (fraction < 0.35F) {
            return WARN;
        }
        return GOOD;
    }
}
