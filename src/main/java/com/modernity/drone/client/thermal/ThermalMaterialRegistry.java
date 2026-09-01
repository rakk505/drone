package com.modernity.drone.client.thermal;

import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Material table used by the thermal atlas and world heat overlays.
 *
 * <p>V1.1.4 registered a few hundred vanilla blocks individually.  Doing the lookup by registry
 * path keeps that table useful for newer vanilla woods, stones and copper variants while retaining
 * the original temperatures, emissivities, material classes and thermal masses.</p>
 */
public final class ThermalMaterialRegistry {
    private static final Map<Block, ThermalProperties> CACHE = new IdentityHashMap<>();

    private ThermalMaterialRegistry() {
    }

    public static ThermalProperties properties(BlockState state) {
        if (state == null || state.isAir()) {
            return ThermalProperties.ATLAS_DEFAULT;
        }
        return properties(state.getBlock());
    }

    public static ThermalProperties properties(Block block) {
        return CACHE.computeIfAbsent(block, ThermalMaterialRegistry::classify);
    }

    public static boolean isExplicitlyClassified(BlockState state) {
        ThermalProperties properties = properties(state);
        return properties != ThermalProperties.DEFAULT && properties != ThermalProperties.ATLAS_DEFAULT;
    }

    public static boolean isHeatSource(BlockState state) {
        return properties(state).temperature() >= 0.75F;
    }

    public static void clearCache() {
        CACHE.clear();
    }

    private static ThermalProperties classify(Block block) {
        String id = BuiltInRegistries.BLOCK.getKey(block).getPath().toLowerCase(Locale.ROOT);

        if (contains(id, "lava")) return p(1.00F, 1.00F, ThermalProperties.MATERIAL_FIRE, 0.90F);
        if (contains(id, "soul_campfire")) return p(0.82F, 1.00F, ThermalProperties.MATERIAL_FIRE, 0.30F);
        if (contains(id, "campfire")) return p(0.90F, 1.00F, ThermalProperties.MATERIAL_FIRE, 0.30F);
        if (contains(id, "soul_fire", "soul_torch", "soul_wall_torch"))
            return p(0.82F, 1.00F, ThermalProperties.MATERIAL_FIRE, 0.10F);
        if (contains(id, "redstone_torch", "redstone_wall_torch"))
            return p(0.28F, 0.90F, ThermalProperties.MATERIAL_NORMAL, 0.20F);
        if (contains(id, "redstone_lamp"))
            return p(0.28F, 0.90F, ThermalProperties.MATERIAL_NORMAL, 0.20F);
        if (contains(id, "fire", "torch", "wall_torch"))
            return p(0.90F, 1.00F, ThermalProperties.MATERIAL_FIRE, 0.10F);
        if (contains(id, "magma")) return p(0.88F, 0.95F, ThermalProperties.MATERIAL_NORMAL, 0.90F);
        if (contains(id, "furnace", "smoker", "blast_furnace"))
            return p(0.78F, 0.90F, ThermalProperties.MATERIAL_NORMAL, 0.80F);

        if (contains(id, "redstone_wire", "repeater", "comparator"))
            return p(0.18F, 0.90F, ThermalProperties.MATERIAL_NORMAL, 0.30F);

        if (contains(id, "water")) return p(0.14F, 0.96F, ThermalProperties.MATERIAL_WATER, 0.95F);
        if (contains(id, "glass", "pane")) return p(0.12F, 0.05F, ThermalProperties.MATERIAL_GLASS, 0.40F);
        if (contains(id, "iron", "chain", "anvil", "rail", "cauldron", "hopper"))
            return p(0.05F, 0.35F, ThermalProperties.MATERIAL_METAL, 0.90F);
        if (contains(id, "gold", "copper")) return p(0.06F, 0.35F, ThermalProperties.MATERIAL_METAL, 0.85F);

        if (contains(id, "powder_snow", "snow"))
            return p(0.04F, 0.98F, ThermalProperties.MATERIAL_NORMAL, 0.10F);
        if (contains(id, "blue_ice")) return p(0.01F, 0.96F, ThermalProperties.MATERIAL_NORMAL, 0.90F);
        if (contains(id, "packed_ice")) return p(0.02F, 0.96F, ThermalProperties.MATERIAL_NORMAL, 0.80F);
        if (contains(id, "ice")) return p(0.03F, 0.96F, ThermalProperties.MATERIAL_NORMAL, 0.60F);

        if (contains(id, "leaves", "azalea")) return p(0.07F, 0.95F, ThermalProperties.MATERIAL_NORMAL, 0.10F);
        if (contains(id, "grass", "moss", "mycelium", "podzol"))
            return p(0.08F, 0.93F, ThermalProperties.MATERIAL_NORMAL, 0.30F);
        if (contains(id, "rooted_dirt", "coarse_dirt", "dirt", "mud"))
            return p(0.07F, 0.92F, ThermalProperties.MATERIAL_NORMAL, 0.40F);
        if (contains(id, "soul_sand", "soul_soil"))
            return p(0.35F, 0.90F, ThermalProperties.MATERIAL_NORMAL, 0.30F);
        if (contains(id, "sand")) return p(0.07F, 0.90F, ThermalProperties.MATERIAL_NORMAL, 0.20F);
        if (contains(id, "gravel")) return p(0.05F, 0.90F, ThermalProperties.MATERIAL_NORMAL, 0.40F);

        if (contains(id, "log", "wood", "stem", "hyphae"))
            return p(0.55F, 0.90F, ThermalProperties.MATERIAL_NORMAL, 0.40F);
        if (contains(id, "planks", "door", "trapdoor", "bamboo_mosaic"))
            return p(0.53F, 0.90F, ThermalProperties.MATERIAL_NORMAL, 0.30F);
        if (contains(id, "wool", "carpet")) return p(0.52F, 0.90F, ThermalProperties.MATERIAL_NORMAL, 0.20F);
        if (contains(id, "hay_block")) return p(0.50F, 0.90F, ThermalProperties.MATERIAL_NORMAL, 0.20F);

        if (contains(id, "bedrock")) return p(0.20F, 0.90F, ThermalProperties.MATERIAL_NORMAL, 1.00F);
        if (contains(id, "obsidian")) return p(0.30F, 0.90F, ThermalProperties.MATERIAL_NORMAL, 0.95F);
        if (contains(id, "deepslate")) return p(0.45F, 0.92F, ThermalProperties.MATERIAL_NORMAL, 0.90F);
        if (contains(id, "brick")) return p(0.50F, 0.93F, ThermalProperties.MATERIAL_NORMAL, 0.70F);
        if (contains(id, "concrete", "terracotta"))
            return p(0.46F, 0.92F, ThermalProperties.MATERIAL_NORMAL, 0.70F);
        if (contains(id, "sandstone")) return p(0.45F, 0.90F, ThermalProperties.MATERIAL_NORMAL, 0.60F);
        if (contains(id, "stone")) return p(0.48F, 0.92F, ThermalProperties.MATERIAL_NORMAL, 0.80F);
        if (contains(id, "netherrack")) return p(0.30F, 0.90F, ThermalProperties.MATERIAL_NORMAL, 0.50F);
        if (contains(id, "basalt", "blackstone"))
            return p(0.33F, 0.90F, ThermalProperties.MATERIAL_NORMAL, 0.80F);
        if (contains(id, "end_stone", "purpur"))
            return p(0.06F, 0.90F, ThermalProperties.MATERIAL_NORMAL, 0.80F);
        if (contains(id, "nether_wart_block", "warped_wart_block"))
            return p(0.35F, 0.95F, ThermalProperties.MATERIAL_NORMAL, 0.30F);
        return ThermalProperties.DEFAULT;
    }

    private static ThermalProperties p(float temperature, float emissivity, float material, float mass) {
        return new ThermalProperties(temperature, emissivity, material, mass);
    }

    private static boolean contains(String id, String... values) {
        for (String value : values) {
            if (id.contains(value)) return true;
        }
        return false;
    }
}
