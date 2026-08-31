package com.modernity.drone;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue PAYLOAD_BLOCK_DAMAGE = BUILDER
            .comment("Allow explosive drone payloads to damage blocks.")
            .define("payloadBlockDamage", false);
    public static final ModConfigSpec.DoubleValue MOSQUITO_CONTROL_RANGE = BUILDER
            .comment("Maximum clear-line control range of the FPV drone, in blocks.")
            .defineInRange("mosquitoControlRange", 350.0, 32.0, 2000.0);
    public static final ModConfigSpec.DoubleValue PAYLOAD_CONTROL_RANGE = BUILDER
            .comment("Maximum clear-line control range of the stabilized payload drone, in blocks.")
            .defineInRange("payloadControlRange", 800.0, 64.0, 4000.0);
    public static final ModConfigSpec.IntValue CONTROL_TIMEOUT_TICKS = BUILDER
            .comment("Ticks without a valid pilot input before the server activates failsafe behavior.")
            .defineInRange("controlTimeoutTicks", 12, 4, 100);

    static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }
}
