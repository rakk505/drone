package com.modernity.drone.flight;

import java.util.Objects;
import net.minecraft.nbt.CompoundTag;

/** The editable LiPo pack metadata and defaults shipped in V1.1.4. */
public final class BatteryData {
    public static final int DEFAULT_CELLS = 6;
    public static final int DEFAULT_CAPACITY_MAH = 3300;
    public static final int DEFAULT_C_RATING = 75;
    private static final float VOLTAGE_PER_CELL = 3.7F;
    private static final float WEIGHT_PER_MAH = 0.1429F;

    private int cells = DEFAULT_CELLS;
    private int capacityMah = DEFAULT_CAPACITY_MAH;
    private int cRating = DEFAULT_C_RATING;
    private int remainingMah = -1;

    public BatteryData() {
    }

    public BatteryData(int cells, int capacityMah, int cRating) {
        setCells(cells);
        setCapacityMah(capacityMah);
        setCRating(cRating);
    }

    public static BatteryData defaults() {
        return new BatteryData();
    }

    public int cells() { return cells; }
    public int capacityMah() { return capacityMah; }
    public int cRating() { return cRating; }
    public int remainingMah() { return remainingMah < 0 ? capacityMah : remainingMah; }
    public float voltage() { return cells * VOLTAGE_PER_CELL; }
    public float weightGrams() { return capacityMah * WEIGHT_PER_MAH; }
    public boolean isFullCharge() { return remainingMah < 0; }

    public void setCells(int value) { cells = clamp(value, 1, 8); }
    public void setCapacityMah(int value) {
        capacityMah = clamp(value, 300, 8000);
        if (remainingMah >= 0) remainingMah = Math.min(remainingMah, capacityMah);
    }
    public void setCRating(int value) { cRating = clamp(value, 45, 150); }
    public void setRemainingMah(int value) { remainingMah = clamp(value, 0, capacityMah); }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("cells", cells);
        tag.putInt("capacityMah", capacityMah);
        tag.putInt("cRating", cRating);
        if (remainingMah >= 0) tag.putInt("remainingMah", remainingMah);
        return tag;
    }

    public static BatteryData load(CompoundTag tag) {
        BatteryData data = defaults();
        data.setCells(tag.getIntOr("cells", DEFAULT_CELLS));
        data.setCapacityMah(tag.getIntOr("capacityMah", DEFAULT_CAPACITY_MAH));
        data.setCRating(tag.getIntOr("cRating", DEFAULT_C_RATING));
        if (tag.contains("remainingMah")) data.setRemainingMah(tag.getIntOr("remainingMah", data.capacityMah));
        return data;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof BatteryData data)) return false;
        return cells == data.cells && capacityMah == data.capacityMah
                && cRating == data.cRating && remainingMah == data.remainingMah;
    }

    @Override
    public int hashCode() {
        return Objects.hash(cells, capacityMah, cRating, remainingMah);
    }
}
