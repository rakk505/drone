package com.modernity.drone.client.osd;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The complete 45-element registry shipped by FPVtoMinecraft 1.1.4. */
public final class OsdElementRegistry {
    private static final Map<String, OsdElementDefinition> ELEMENTS = new LinkedHashMap<>();
    private static final Map<String, List<OsdElementDefinition>> BY_CATEGORY = new LinkedHashMap<>();

    private OsdElementRegistry() {
    }

    private static void add(String id, String name, String category, String preview,
                            float x, float y, int width, int height, boolean live, boolean custom) {
        OsdElementDefinition definition = new OsdElementDefinition(
                id, name, category, preview, x, y, width, height, live, custom
        );
        ELEMENTS.put(id, definition);
        BY_CATEGORY.computeIfAbsent(category, ignored -> new ArrayList<>()).add(definition);
    }

    public static OsdElementDefinition get(String id) {
        return ELEMENTS.get(id);
    }

    public static Collection<OsdElementDefinition> all() {
        return Collections.unmodifiableCollection(ELEMENTS.values());
    }

    public static Collection<OsdElementDefinition> getAll() { return all(); }

    public static Map<String, List<OsdElementDefinition>> byCategory() {
        LinkedHashMap<String, List<OsdElementDefinition>> result = new LinkedHashMap<>();
        BY_CATEGORY.forEach((category, entries) -> result.put(category, List.copyOf(entries)));
        return Collections.unmodifiableMap(result);
    }

    public static Map<String, List<OsdElementDefinition>> getByCategory() { return byCategory(); }

    static {
        add("RSSI_VALUE", "RSSI Value", "Signal", "\uE00199", 27, 0, 3, 1, true, false);
        add("LINK_QUALITY", "Link Quality", "Signal", "\uE07B9:100", 21, 0, 6, 1, true, false);
        add("RSSI_DBM_VALUE", "RSSI dBm", "Signal", "\uE001-80", 23, 1, 4, 1, true, false);

        add("MAIN_BATT_VOLTAGE", "Main Batt Voltage", "Battery", "\uE09016.8\uE006", 12, 2, 6, 1, true, false);
        add("AVG_CELL_VOLTAGE", "Avg Cell Voltage", "Battery", "\uE0904.20\uE006", 12, 1, 6, 1, true, false);
        add("CURRENT_DRAW", "Current Draw", "Battery", "\uE09A 42.5\uE09A", 1, 13, 7, 1, true, false);
        add("MAH_DRAWN", "mAh Drawn", "Battery", "\uE0071245", 1, 12, 5, 1, true, false);
        add("BATT_REMAINING_PERCENT", "Batt %", "Battery", "\uE09072%", 12, 3, 4, 1, true, false);
        add("POWER", "Power", "Battery", "W125W", 23, 14, 4, 1, true, false);
        add("BATT_REMAINING_CAPACITY", "Batt Remaining", "Battery", "\uE007955", 1, 11, 4, 1, true, false);
        add("OSD_EFFICIENCY", "Efficiency", "Battery", "123\uE007/\uE00C", 1, 14, 6, 1, true, false);
        add("MAIN_BATT_USAGE", "Batt Usage", "Battery", "\uE08A\uE08B\uE08B\uE08B\uE08B\uE08B\uE08B\uE08B\uE08D\uE08D\uE08D\uE08E", 8, 14, 12, 1, false, false);

        add("ALTITUDE", "Altitude", "Flight", "\uE07F399.7\uE00C", 23, 12, 7, 1, true, false);
        add("GPS_SPEED", "GPS Speed", "Flight", "\uE070 42\uE09E", 24, 10, 5, 1, true, false);
        add("COMPASS_BAR", "Compass Bar", "Flight", "\uE01B\uE01D\uE01C\uE01D\uE018\uE01D\uE01C\uE01D\uE01A", 11, 0, 9, 1, true, true);
        add("NUMERICAL_HEADING", "Heading", "Flight", "\uE068045", 14, 1, 4, 1, true, false);
        add("FLYMODE", "Fly Mode", "Flight", "ACRO", 13, 11, 4, 1, false, false);
        add("THROTTLE_POSITION", "Throttle", "Flight", "\uE004 69", 1, 4, 4, 1, true, false);
        add("FLIGHT_DIST", "Flight Dist", "Flight", "\uE071653\uE00C", 1, 9, 5, 1, true, false);
        add("NUMERICAL_VARIO", "Vario", "Flight", "\uE0758.7\uE09F", 26, 10, 5, 1, true, false);
        add("G_FORCE", "G-Force", "Flight", "1.0G", 13, 10, 4, 1, false, false);
        add("FLIP_ARROW", "Flip Arrow", "Flight", "\uE064", 15, 8, 1, 1, false, false);

        add("CROSSHAIRS", "Crosshairs", "Attitude", "\uE072\uE073\uE074", 14, 7, 3, 1, false, true);
        add("ARTIFICIAL_HORIZON", "Artificial Horizon", "Attitude", "", 11, 4, 9, 9, true, true);
        add("HORIZON_SIDEBARS", "Horizon Sidebars", "Attitude", "", 10, 5, 11, 7, true, true);
        add("PITCH_ANGLE", "Pitch Angle", "Attitude", "\uE015 -12.5", 24, 8, 7, 1, true, false);
        add("ROLL_ANGLE", "Roll Angle", "Attitude", "\uE014  5.2", 24, 9, 6, 1, true, false);

        add("GPS_SATS", "GPS Sats", "GPS", "\uE01E\uE01F14", 24, 2, 4, 1, true, false);
        add("GPS_LAT", "GPS Lat", "GPS", "\uE089-55.7520000", 1, 14, 12, 1, false, false);
        add("GPS_LON", "GPS Lon", "GPS", "\uE098 037.6175000", 1, 15, 13, 1, false, false);
        add("HOME_DIR", "Home Direction", "GPS", "\uE068", 14, 3, 1, 1, false, false);
        add("HOME_DIST", "Home Distance", "GPS", "\uE011120\uE00C", 23, 13, 5, 1, true, false);

        add("CRAFT_NAME", "Craft Name", "Info", "CRAFT", 10, 12, 10, 1, true, false);
        add("PILOT_NAME", "Pilot Name", "Info", "PILOT", 10, 13, 10, 1, true, false);

        add("WARNINGS", "Warnings", "Warnings", "    WARNINGS    ", 11, 7, 16, 1, true, false);
        add("DISARMED", "Disarmed", "Warnings", "DISARMED", 11, 8, 8, 1, true, false);
        add("READY_MODE", "Ready Mode", "Warnings", "READY", 13, 8, 5, 1, false, false);

        add("TIMER_1", "Timer 1", "Timers", "\uE09C04:32", 1, 0, 6, 1, true, false);
        add("TIMER_2", "Timer 2", "Timers", "\uE09B04:32", 1, 1, 6, 1, true, false);
        add("REMAINING_TIME_ESTIMATE", "Remaining Time", "Timers", "01:13", 24, 13, 5, 1, true, false);
        add("RTC_DATE_TIME", "RTC Date/Time", "Timers", "2025-10-25 12:00:00", 20, 15, 19, 1, true, false);

        add("VTX_CHANNEL", "VTX Channel", "System", "R:2:200", 8, 0, 7, 1, false, false);
        add("LOG_STATUS", "Log Status", "System", "\uE010 16", 27, 2, 4, 1, true, false);
        add("PID_RATE_PROFILE", "PID/Rate Profile", "Profile", "1-1", 1, 2, 3, 1, false, false);
        add("ESC_TEMPERATURE", "ESC Temp", "Hardware", "\uE07A45\uE00E", 24, 11, 4, 1, false, false);
    }
}
