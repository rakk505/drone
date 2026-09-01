package com.modernity.drone.client.signal;

import java.util.Locale;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;

/** RF attenuation values used by the sampled line-of-sight model. */
public final class BlockAttenuation {
    private BlockAttenuation() {
    }

    public static float forState(BlockState state) {
        if (state == null || state.isAir()) {
            return 0.0F;
        }
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().toLowerCase(Locale.ROOT);
        if (contains(id, "torch", "lantern", "flower", "grass", "vine", "sapling", "mushroom")) {
            return 0.0F;
        }
        if (contains(id, "leaves", "glass", "pane", "bars", "fence")) {
            return id.contains("leaves") ? 1.0F : 0.5F;
        }
        if (contains(id, "water", "ice")) {
            return 2.0F;
        }
        if (contains(id, "log", "wood", "planks", "bamboo")) {
            return id.contains("planks") ? 4.0F : 3.0F;
        }
        if (contains(id, "obsidian", "ancient_debris")) {
            return 20.0F;
        }
        if (contains(id, "reinforced_deepslate")) {
            return 15.0F;
        }
        if (contains(id, "deepslate", "netherite")) {
            return 12.0F;
        }
        if (contains(id, "iron", "gold", "diamond", "emerald", "copper")) {
            return id.contains("copper") ? 7.0F : 10.0F;
        }
        if (contains(id, "stone", "brick", "concrete", "terracotta", "ore")) {
            return 8.0F;
        }
        if (!state.blocksMotion()) {
            return 1.0F;
        }
        return 6.0F;
    }

    private static boolean contains(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
