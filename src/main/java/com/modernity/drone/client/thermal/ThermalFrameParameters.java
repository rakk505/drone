package com.modernity.drone.client.thermal;

/** Uniform-ready state for a future renderer-level hookup of the copied shader suite. */
public record ThermalFrameParameters(
        boolean active,
        int sensorWidth,
        int sensorHeight,
        int paletteIndex,
        int agcMode,
        float manualAgcMinimum,
        float manualAgcMaximum,
        int focusMode,
        float focusDistance,
        boolean nucFrozen,
        float fixedPatternNoiseReduction
) {
    public static ThermalFrameParameters capture(ThermalState state) {
        return new ThermalFrameParameters(
                state.active(),
                ThermalResources.SENSOR_WIDTH,
                ThermalResources.SENSOR_HEIGHT,
                state.palette().ordinal(),
                state.agcMode().ordinal(),
                state.manualAgcMinimum(),
                state.manualAgcMaximum(),
                state.focusMode().ordinal(),
                state.manualFocusDistance(),
                state.nuc().frozen(),
                state.nuc().fpnReduction()
        );
    }
}
