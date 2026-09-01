package com.modernity.drone.client.osd;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Renders the V1.1.4 MAX7456 display as a strict 30x16 character grid. */
public final class OsdRenderer {
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int OUTLINE_COLOR = 0xFF000000;
    private static final FontDescription.Resource OSD_FONT = new FontDescription.Resource(
            Identifier.fromNamespaceAndPath("fpvdrone", "osd")
    );
    private static final DateTimeFormatter CLOCK_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final char[] COMPASS = new char[]{
            '\uE01B', '\uE01D', '\uE01C', '\uE01D', '\uE018', '\uE01D', '\uE01C', '\uE01D',
            '\uE01A', '\uE01D', '\uE01C', '\uE01D', '\uE019', '\uE01D', '\uE01C', '\uE01D',
            '\uE01B', '\uE01D', '\uE01C', '\uE01D', '\uE018', '\uE01D', '\uE01C', '\uE01D'
    };

    private final Max7456CellBuffer cells = new Max7456CellBuffer();
    private Max7456CellBuffer frozenCells;
    private long sessionStartedMillis;
    private long armedStartedMillis;
    private long armedElapsedMillis;
    private long sessionElapsedMillis;
    private boolean previouslyArmed;
    private int logSessionNumber;
    private long lastHorizonUpdateMillis;
    private final int[] horizonY = new int[9];
    private final int[] horizonSub = new int[9];
    private final int[] horizonRow = new int[9];

    public void beginSession(long nowMillis) {
        sessionStartedMillis = nowMillis;
        sessionElapsedMillis = 0L;
        armedStartedMillis = 0L;
        armedElapsedMillis = 0L;
        previouslyArmed = false;
        frozenCells = null;
        lastHorizonUpdateMillis = 0L;
    }

    public void endSession() {
        frozenCells = null;
        previouslyArmed = false;
    }

    public void render(GuiGraphicsExtractor graphics, OsdLayout layout, OsdTelemetry telemetry,
                       BootStateManager.Phase bootPhase) {
        GridGeometry grid = GridGeometry.fit(graphics.guiWidth(), graphics.guiHeight());
        if (bootPhase == BootStateManager.Phase.LOGO) {
            renderBoot(graphics, grid);
            return;
        }
        updateTimers(telemetry);
        if (telemetry.noSignal() && frozenCells != null) {
            copy(frozenCells, cells);
            overwriteWarningElements(layout, telemetry);
        } else {
            buildCells(layout, telemetry);
            if (!telemetry.noSignal()) {
                frozenCells = cells.copy();
            }
        }
        drawCells(graphics, grid, cells);
    }

    private void buildCells(OsdLayout layout, OsdTelemetry telemetry) {
        cells.clear();
        for (OsdElement element : layout.visible()) {
            if (isBackground(element.id())) {
                writeElement(element, telemetry);
            }
        }
        for (OsdElement element : layout.visible()) {
            if (!isBackground(element.id()) && !isForeground(element.id())) {
                writeElement(element, telemetry);
            }
        }
        for (OsdElement element : layout.visible()) {
            if (isForeground(element.id())) {
                writeElement(element, telemetry);
            }
        }
    }

    private void overwriteWarningElements(OsdLayout layout, OsdTelemetry telemetry) {
        for (String id : new String[]{"WARNINGS", "DISARMED"}) {
            OsdElement element = layout.get(id);
            OsdElementDefinition definition = OsdElementRegistry.get(id);
            if (element == null || definition == null || !element.visible()) {
                continue;
            }
            cells.erase((int) Math.floor(element.x()), (int) Math.floor(element.y()),
                    definition.widthCells(), definition.heightCells());
            writeElement(element, telemetry);
        }
    }

    private void writeElement(OsdElement element, OsdTelemetry telemetry) {
        OsdElementDefinition definition = OsdElementRegistry.get(element.id());
        if (definition == null) {
            return;
        }
        switch (element.id()) {
            case "ARTIFICIAL_HORIZON" -> writeHorizon(element, telemetry);
            case "HORIZON_SIDEBARS" -> writeSidebars();
            case "COMPASS_BAR" -> writeCompass(element, telemetry.headingDegrees());
            case "CROSSHAIRS" -> cells.write(element.x(), element.y(), "\uE072\uE073\uE074");
            case "WARNINGS", "DISARMED", "CRAFT_NAME", "PILOT_NAME" ->
                    writeCentered(element, definition, textFor(element, telemetry));
            default -> cells.write(element.x(), element.y(), textFor(element, telemetry));
        }
    }

    private String textFor(OsdElement element, OsdTelemetry telemetry) {
        int batteryPercent = telemetry.batteryPercent();
        char battery = batterySymbol(batteryPercent);
        return switch (element.id()) {
            case "RSSI_VALUE" -> "\uE001" + Math.round(telemetry.signal() * 100.0F);
            case "LINK_QUALITY" -> "\uE07B9:" + Math.round(telemetry.signal() * 100.0F);
            case "RSSI_DBM_VALUE" -> "\uE001" + (int) (-25.0F - (1.0F - telemetry.signal()) * 80.0F);
            case "MAIN_BATT_VOLTAGE" -> telemetry.drone().hasBattery()
                    ? battery + String.format(Locale.ROOT, "%.1f", telemetry.totalVoltage()) + "\uE006"
                    : "\uE090-.--\uE006";
            case "AVG_CELL_VOLTAGE" -> telemetry.drone().hasBattery()
                    ? battery + String.format(Locale.ROOT, "%.2f", telemetry.cellVoltage()) + "\uE006"
                    : "\uE090-.--\uE006";
            case "CURRENT_DRAW" -> telemetry.drone().hasBattery()
                    ? "\uE09A " + String.format(Locale.ROOT, "%.1f", telemetry.currentAmps()) + "\uE09A"
                    : "\uE09A -.-\uE09A";
            case "MAH_DRAWN" -> telemetry.drone().hasBattery() ? "\uE007" + telemetry.usedMah() : "\uE007---";
            case "BATT_REMAINING_PERCENT" -> telemetry.drone().hasBattery()
                    ? "" + battery + batteryPercent + "%" : "\uE090--%";
            case "POWER" -> telemetry.drone().hasBattery()
                    ? "W" + String.format(Locale.ROOT, "%.0f", telemetry.watts()) + "W" : "W---W";
            case "BATT_REMAINING_CAPACITY" -> telemetry.drone().hasBattery()
                    ? "\uE007" + telemetry.remainingMah() : "\uE007---";
            case "OSD_EFFICIENCY" -> telemetry.drone().hasBattery() && telemetry.distance() >= 1.0
                    ? (int) (telemetry.usedMah() / telemetry.distance()) + "\uE007/\uE00C" : "---\uE007/\uE00C";
            case "MAIN_BATT_USAGE" -> batteryBar(telemetry.batteryFraction());
            case "ALTITUDE" -> "\uE07F" + String.format(Locale.ROOT, "%.1f", telemetry.drone().getY()) + "\uE00C";
            case "GPS_SPEED" -> "\uE070 " + String.format(Locale.ROOT, "%.0f", telemetry.speedMetersPerSecond() * 3.6F) + "\uE09E";
            case "NUMERICAL_HEADING" -> "\uE068" + String.format(Locale.ROOT, "%03.0f", telemetry.headingDegrees());
            case "FLYMODE" -> "ACRO";
            case "THROTTLE_POSITION" -> "\uE004 " + Math.round(telemetry.throttle() * 100.0F);
            case "FLIGHT_DIST" -> "\uE071" + (int) telemetry.distance() + "\uE00C";
            case "NUMERICAL_VARIO" -> (telemetry.verticalSpeedMetersPerSecond() >= 0.0F ? "\uE075" : "\uE076")
                    + String.format(Locale.ROOT, "%.1f", Math.abs(telemetry.verticalSpeedMetersPerSecond())) + "\uE09F";
            case "G_FORCE" -> "1.0G";
            case "FLIP_ARROW" -> flipArrow(telemetry.rollDegrees());
            case "PITCH_ANGLE" -> "\uE015" + String.format(Locale.ROOT, "%5.1f", telemetry.pitchDegrees());
            case "ROLL_ANGLE" -> "\uE014" + String.format(Locale.ROOT, "%5.1f", telemetry.rollDegrees());
            case "GPS_SATS" -> "\uE01E\uE01F14";
            case "GPS_LAT" -> "\uE089" + String.format(Locale.ROOT, "%+.7f", telemetry.drone().getX());
            case "GPS_LON" -> "\uE098 " + String.format(Locale.ROOT, "%+.7f", telemetry.drone().getZ());
            case "HOME_DIR" -> homeDirection(telemetry);
            case "HOME_DIST" -> "\uE011" + (int) horizontalHomeDistance(telemetry) + "\uE00C";
            case "CRAFT_NAME" -> telemetry.drone().getDroneName().toUpperCase(Locale.ROOT);
            case "PILOT_NAME" -> customOr(element, telemetry.pilotName());
            case "WARNINGS" -> warning(telemetry);
            case "DISARMED" -> disarmed(telemetry);
            case "READY_MODE" -> telemetry.drone().isArmed() ? "" : "READY";
            case "TIMER_1" -> "\uE09C" + duration(armedElapsedMillis);
            case "TIMER_2" -> "\uE09B" + duration(sessionElapsedMillis);
            case "REMAINING_TIME_ESTIMATE" -> remainingTime(telemetry);
            case "RTC_DATE_TIME" -> CLOCK_FORMAT.format(LocalDateTime.now());
            case "VTX_CHANNEL" -> "R:" + telemetry.drone().getVideoChannel() + ":200";
            case "LOG_STATUS" -> "\uE010 " + logSessionNumber;
            case "PID_RATE_PROFILE" -> "1-1";
            case "ESC_TEMPERATURE" -> "\uE07A" + Math.round(35.0F + telemetry.currentAmps() * 0.25F) + "\uE00E";
            default -> OsdElementRegistry.get(element.id()).previewText();
        };
    }

    private void updateTimers(OsdTelemetry telemetry) {
        sessionElapsedMillis = Math.max(0L, telemetry.nowMillis() - sessionStartedMillis);
        boolean armed = telemetry.drone().isArmed();
        if (armed && !previouslyArmed) {
            armedStartedMillis = telemetry.nowMillis();
            logSessionNumber++;
        }
        if (armed) {
            armedElapsedMillis = Math.max(0L, telemetry.nowMillis() - armedStartedMillis);
        }
        previouslyArmed = armed;
    }

    private void writeHorizon(OsdElement element, OsdTelemetry telemetry) {
        if (telemetry.nowMillis() - lastHorizonUpdateMillis >= 166L || lastHorizonUpdateMillis == 0L) {
            lastHorizonUpdateMillis = telemetry.nowMillis();
            int roll = Mth.clamp((int) (telemetry.rollDegrees() * 10.0F), -400, 400);
            int pitch = Mth.clamp((int) (telemetry.pitchDegrees() * 10.0F), -200, 200);
            pitch = pitch * 25 / 200 - 41;
            for (int index = 0; index < 9; index++) {
                int x = index - 4;
                int y = -roll * x / 64 - pitch;
                horizonY[index] = y;
                if (y >= 0 && y <= 81) {
                    horizonSub[index] = y % 9;
                    horizonRow[index] = y / 9;
                }
            }
        }
        int column = Math.round(element.x());
        int row = Math.round(element.y());
        for (int index = 0; index < 9; index++) {
            if (horizonY[index] >= 0 && horizonY[index] <= 81) {
                cells.write(column + index, row + horizonRow[index], (char) (0xE080 + horizonSub[index]));
            }
        }
    }

    private void writeSidebars() {
        for (int index = 0; index < 7; index++) {
            int row = 5 + index;
            cells.write(10, row, index == 3 ? '\uE003' : '\uE013');
            cells.write(19, row, index == 3 ? '\uE002' : '\uE013');
        }
    }

    private void writeCompass(OsdElement element, float heading) {
        int offset = ((int) heading + 360) % 360 * 16 / 360 % 16;
        int column = (int) Math.floor(element.x());
        int row = (int) Math.floor(element.y());
        float subX = element.x() - column;
        float subY = element.y() - row;
        for (int index = 0; index < 9; index++) {
            cells.write(column + index, row, COMPASS[offset + index], subX, subY);
        }
    }

    private void writeCentered(OsdElement element, OsdElementDefinition definition, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        float offset = Math.max(0.0F, (definition.widthCells() - text.length()) / 2.0F);
        cells.write(element.x() + offset, element.y(), text);
    }

    private void renderBoot(GuiGraphicsExtractor graphics, GridGeometry grid) {
        cells.clear();
        int logoX = 3;
        int logoY = 3;
        for (int row = 0; row < 5; row++) {
            for (int column = 0; column < 24; column++) {
                cells.write(logoX + column, logoY + row, (char) (0xE0A0 + row * 24 + column));
            }
        }
        cells.write(23, 8, "V1.0.0");
        cells.write(7, 10, "MENU:THR MID");
        cells.write(11, 11, "+ YAW LEFT");
        cells.write(11, 12, "+ PITCH UP");
        drawCells(graphics, grid, cells);
    }

    private static void drawCells(GuiGraphicsExtractor graphics, GridGeometry grid, Max7456CellBuffer buffer) {
        Font font = Minecraft.getInstance().font;
        for (int row = 0; row < Max7456CellBuffer.ROWS; row++) {
            for (int column = 0; column < Max7456CellBuffer.COLUMNS; column++) {
                char value = buffer.value(column, row);
                if (value == Max7456CellBuffer.EMPTY) {
                    continue;
                }
                float x = grid.x() + (column + buffer.offsetX(column, row)) * grid.cellWidth();
                float y = grid.y() + (row + buffer.offsetY(column, row)) * grid.cellHeight();
                drawGlyph(graphics, font, value, x, y, grid.cellWidth(), grid.cellHeight());
            }
        }
    }

    private static void drawGlyph(GuiGraphicsExtractor graphics, Font font, char value,
                                  float x, float y, float cellWidth, float cellHeight) {
        Component glyph = Component.literal(String.valueOf(value))
                .withStyle(style -> style.withFont(OSD_FONT));
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(cellWidth / 12.0F, cellHeight / 18.0F);
        graphics.text(font, glyph, -1, 0, OUTLINE_COLOR, false);
        graphics.text(font, glyph, 1, 0, OUTLINE_COLOR, false);
        graphics.text(font, glyph, 0, -1, OUTLINE_COLOR, false);
        graphics.text(font, glyph, 0, 1, OUTLINE_COLOR, false);
        graphics.text(font, glyph, 0, 0, TEXT_COLOR, false);
        graphics.pose().popMatrix();
    }

    private static void copy(Max7456CellBuffer source, Max7456CellBuffer target) {
        target.clear();
        for (int row = 0; row < Max7456CellBuffer.ROWS; row++) {
            for (int column = 0; column < Max7456CellBuffer.COLUMNS; column++) {
                char value = source.value(column, row);
                if (value != Max7456CellBuffer.EMPTY) {
                    target.write(column, row, value, source.offsetX(column, row), source.offsetY(column, row));
                }
            }
        }
    }

    private static boolean isBackground(String id) {
        return "HORIZON_SIDEBARS".equals(id) || "CRAFT_NAME".equals(id) || "PILOT_NAME".equals(id);
    }

    private static boolean isForeground(String id) {
        return "CROSSHAIRS".equals(id);
    }

    private static String customOr(OsdElement element, String fallback) {
        return element.customText() == null || element.customText().isBlank()
                ? fallback.toUpperCase(Locale.ROOT)
                : element.customText().toUpperCase(Locale.ROOT);
    }

    private static String warning(OsdTelemetry telemetry) {
        if (telemetry.noSignal()) {
            return blink("NO SIGNAL", telemetry.nowMillis());
        }
        if (telemetry.signal() < 0.15F) {
            return blink("RSSI LOW", telemetry.nowMillis());
        }
        if (telemetry.signal() < 0.30F) {
            return blink("LINK QUALITY", telemetry.nowMillis());
        }
        if (telemetry.drone().hasBattery() && telemetry.batteryPercent() <= 10) {
            return blink("LAND NOW", telemetry.nowMillis());
        }
        if (telemetry.drone().hasBattery() && telemetry.batteryPercent() <= 20) {
            return blink("LOW BATTERY", telemetry.nowMillis());
        }
        return "";
    }

    private String disarmed(OsdTelemetry telemetry) {
        if (!telemetry.drone().isArmed()) {
            return "DISARMED";
        }
        return armedElapsedMillis < 3_000L ? blink("ARMED", telemetry.nowMillis()) : "";
    }

    private static String blink(String text, long nowMillis) {
        return nowMillis / 250L % 2L == 0L ? text : "";
    }

    private static char batterySymbol(int percent) {
        if (percent >= 83) return '\uE090';
        if (percent >= 67) return '\uE091';
        if (percent >= 50) return '\uE092';
        if (percent >= 33) return '\uE093';
        if (percent >= 17) return '\uE094';
        if (percent > 0) return '\uE095';
        return '\uE096';
    }

    private static String batteryBar(float fraction) {
        int full = Mth.clamp(Math.round(fraction * 10.0F), 0, 10);
        StringBuilder result = new StringBuilder(12).append('\uE08A');
        for (int index = 0; index < 10; index++) {
            result.append(index < full ? '\uE08B' : '\uE08D');
        }
        return result.append('\uE08E').toString();
    }

    private static String flipArrow(float rollDegrees) {
        float normalized = (Mth.wrapDegrees(rollDegrees) + 360.0F) % 360.0F;
        int index = ((int) ((normalized + 11.25F) / 22.5F)) % 16;
        return String.valueOf((char) (0xE060 + index));
    }

    private static String homeDirection(OsdTelemetry telemetry) {
        Vec3 home = telemetry.home();
        if (home == null) {
            return "\uE068";
        }
        double dx = home.x - telemetry.drone().getX();
        double dz = home.z - telemetry.drone().getZ();
        float bearing = (float) Math.toDegrees(Math.atan2(-dx, dz));
        if (bearing < 0.0F) bearing += 360.0F;
        float relative = (bearing - telemetry.headingDegrees() + 360.0F) % 360.0F;
        relative = (360.0F - relative) % 360.0F;
        int index = ((int) ((relative + 11.25F) / 22.5F) + 8) % 16;
        return String.valueOf((char) (0xE060 + index));
    }

    private static double horizontalHomeDistance(OsdTelemetry telemetry) {
        Vec3 home = telemetry.home();
        if (home == null) return 0.0;
        return Math.hypot(telemetry.drone().getX() - home.x, telemetry.drone().getZ() - home.z);
    }

    private static String remainingTime(OsdTelemetry telemetry) {
        if (!telemetry.drone().hasBattery() || telemetry.currentAmps() < 0.5F) {
            return "--:--";
        }
        int seconds = (int) ((telemetry.remainingMah() / 1000.0F / telemetry.currentAmps()) * 3600.0F);
        return duration(Math.max(0, seconds) * 1000L);
    }

    private static String duration(long millis) {
        int seconds = (int) (millis / 1000L);
        return String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
    }

    private record GridGeometry(float x, float y, float cellWidth, float cellHeight) {
        private static GridGeometry fit(int screenWidth, int screenHeight) {
            float scale = Math.min(screenWidth / 360.0F, screenHeight / 288.0F);
            scale = Math.max(0.35F, scale);
            float cellWidth = 12.0F * scale;
            float cellHeight = 18.0F * scale;
            return new GridGeometry(
                    Math.round((screenWidth - cellWidth * OsdLayout.GRID_COLUMNS) / 2.0F),
                    Math.round((screenHeight - cellHeight * OsdLayout.GRID_ROWS) / 2.0F),
                    cellWidth,
                    cellHeight
            );
        }
    }
}
