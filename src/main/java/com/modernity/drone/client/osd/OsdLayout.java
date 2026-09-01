package com.modernity.drone.client.osd;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A Betaflight-compatible 30 by 16 character-cell layout. */
public final class OsdLayout {
    public static final int GRID_COLUMNS = 30;
    public static final int GRID_ROWS = 16;

    private static final List<String> DEFAULT_VISIBLE = List.of(
            "RSSI_VALUE", "LINK_QUALITY", "MAIN_BATT_VOLTAGE", "AVG_CELL_VOLTAGE",
            "CURRENT_DRAW", "MAH_DRAWN", "COMPASS_BAR", "FLYMODE",
            "THROTTLE_POSITION", "CROSSHAIRS", "ARTIFICIAL_HORIZON", "WARNINGS",
            "DISARMED", "TIMER_1", "CRAFT_NAME", "PILOT_NAME"
    );

    private static final Map<String, float[]> DEFAULT_POSITIONS = Map.ofEntries(
            Map.entry("RSSI_VALUE", p(23, 2)), Map.entry("LINK_QUALITY", p(23, 3)),
            Map.entry("RSSI_DBM_VALUE", p(23, 1)), Map.entry("MAIN_BATT_VOLTAGE", p(1, 11)),
            Map.entry("AVG_CELL_VOLTAGE", p(1, 12)), Map.entry("CURRENT_DRAW", p(1, 10)),
            Map.entry("MAH_DRAWN", p(1, 9)), Map.entry("BATT_REMAINING_PERCENT", p(12, 3)),
            Map.entry("POWER", p(23, 14)), Map.entry("BATT_REMAINING_CAPACITY", p(1, 11)),
            Map.entry("OSD_EFFICIENCY", p(1, 14)), Map.entry("MAIN_BATT_USAGE", p(8, 14)),
            Map.entry("ALTITUDE", p(23, 12)), Map.entry("GPS_SPEED", p(24, 10)),
            Map.entry("COMPASS_BAR", p(11, 2)), Map.entry("NUMERICAL_HEADING", p(14, 1)),
            Map.entry("FLYMODE", p(13.5F, 11)), Map.entry("THROTTLE_POSITION", p(1, 7)),
            Map.entry("FLIGHT_DIST", p(1, 9)), Map.entry("NUMERICAL_VARIO", p(25, 10)),
            Map.entry("G_FORCE", p(13, 10)), Map.entry("FLIP_ARROW", p(15, 8)),
            Map.entry("CROSSHAIRS", p(14, 7)), Map.entry("ARTIFICIAL_HORIZON", p(11, 4)),
            Map.entry("HORIZON_SIDEBARS", p(10, 5)), Map.entry("PITCH_ANGLE", p(23, 8)),
            Map.entry("ROLL_ANGLE", p(24, 9)), Map.entry("GPS_SATS", p(24, 2)),
            Map.entry("GPS_LAT", p(1, 14)), Map.entry("GPS_LON", p(1, 15)),
            Map.entry("HOME_DIR", p(23, 11)), Map.entry("HOME_DIST", p(23, 12)),
            Map.entry("CRAFT_NAME", p(7, 12)), Map.entry("PILOT_NAME", p(10.5F, 3)),
            Map.entry("WARNINGS", p(7.5F, 8)), Map.entry("DISARMED", p(11.5F, 6)),
            Map.entry("READY_MODE", p(13, 8)), Map.entry("TIMER_1", p(1, 3)),
            Map.entry("TIMER_2", p(1, 4)), Map.entry("REMAINING_TIME_ESTIMATE", p(23.5F, 13)),
            Map.entry("RTC_DATE_TIME", p(11, 15)), Map.entry("VTX_CHANNEL", p(8, 0)),
            Map.entry("LOG_STATUS", p(23, 4)), Map.entry("PID_RATE_PROFILE", p(1, 2)),
            Map.entry("ESC_TEMPERATURE", p(24, 11))
    );

    private final LinkedHashMap<String, OsdElement> elements = new LinkedHashMap<>();

    public static OsdLayout defaults() {
        OsdLayout layout = new OsdLayout();
        for (OsdElementDefinition definition : OsdElementRegistry.all()) {
            float[] position = DEFAULT_POSITIONS.getOrDefault(
                    definition.id(), p(definition.defaultX(), definition.defaultY())
            );
            layout.elements.put(definition.id(), new OsdElement(
                    definition.id(), position[0], position[1], DEFAULT_VISIBLE.contains(definition.id())
            ));
        }
        return layout;
    }

    public OsdLayout copy() {
        OsdLayout copy = new OsdLayout();
        elements.forEach((id, element) -> copy.elements.put(id, element.copy()));
        return copy;
    }

    public OsdElement get(String id) {
        return elements.get(id);
    }

    public OsdElement getElement(String id) { return get(id); }

    public Collection<OsdElement> all() {
        return List.copyOf(elements.values());
    }

    public Collection<OsdElement> getAllElements() { return all(); }

    public List<OsdElement> visible() {
        return elements.values().stream().filter(OsdElement::visible).toList();
    }

    public Collection<OsdElement> getVisibleElements() { return visible(); }

    public void put(OsdElement element) {
        OsdElementDefinition definition = OsdElementRegistry.get(element.id());
        if (definition == null) {
            return;
        }
        element.setPosition(element.x(), element.y());
        elements.put(element.id(), element);
    }

    public void setElement(String id, OsdElement element) {
        if (id.equals(element.id())) put(element);
    }

    public void resetMissingToDefaults() {
        OsdLayout defaults = defaults();
        for (OsdElementDefinition definition : OsdElementRegistry.all()) {
            elements.putIfAbsent(definition.id(), defaults.get(definition.id()).copy());
        }
    }

    public static float snapToHalf(float value) {
        return Math.round(value * 2.0F) / 2.0F;
    }

    private static float[] p(float x, float y) {
        return new float[]{x, y};
    }
}
