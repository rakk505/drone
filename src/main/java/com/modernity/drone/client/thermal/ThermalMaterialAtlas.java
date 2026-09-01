package com.modernity.drone.client.thermal;

import com.modernity.drone.DroneMod;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.textures.GpuTexture;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Builds the same-layout thermal material atlas used by the original renderer.
 *
 * <p>The RGB channels contain temperature, emissivity and material class. Alpha carries a remapped
 * copy of temperature so the final full-screen pass can identify and recover a classified sample
 * even after vanilla directional lighting changes RGB. Thermal mass remains available in the CPU
 * registry for cooling simulation. Transparent texels stay at zero, preserving cutout geometry.</p>
 */
public final class ThermalMaterialAtlas {
    public static final Identifier LOCATION =
            Identifier.fromNamespaceAndPath(DroneMod.MOD_ID, "thermal/material_atlas");

    private static @Nullable DynamicTexture texture;
    private static @Nullable GpuTexture sourceTexture;
    private static int classifiedBlocks;
    private static int filledSprites;

    private ThermalMaterialAtlas() {
    }

    /** Must be invoked on the render thread after the vanilla block atlas has been uploaded. */
    public static boolean ensureReady() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS)
                instanceof TextureAtlas blockAtlas)) {
            return false;
        }
        GpuTexture currentSource = blockAtlas.getTexture();
        if (currentSource == null || currentSource.isClosed()) {
            return false;
        }
        if (texture != null && sourceTexture == currentSource) {
            return true;
        }
        rebuild(minecraft, currentSource);
        return texture != null;
    }

    public static int classifiedBlockCount() {
        return classifiedBlocks;
    }

    public static int filledSpriteCount() {
        return filledSprites;
    }

    public static void close() {
        Minecraft minecraft = Minecraft.getInstance();
        if (texture != null) {
            minecraft.getTextureManager().release(LOCATION);
            texture = null;
        }
        sourceTexture = null;
        classifiedBlocks = 0;
        filledSprites = 0;
    }

    private static void rebuild(Minecraft minecraft, GpuTexture currentSource) {
        if (texture != null) {
            minecraft.getTextureManager().release(LOCATION);
            texture = null;
        }
        ThermalMaterialRegistry.clearCache();

        int atlasWidth = currentSource.getWidth(0);
        int atlasHeight = currentSource.getHeight(0);
        if (atlasWidth <= 0 || atlasHeight <= 0) {
            return;
        }

        NativeImage image = new NativeImage(NativeImage.Format.RGBA, atlasWidth, atlasHeight, false);
        image.fillRect(0, 0, atlasWidth, atlasHeight, 0);
        BlockStateModel missing = minecraft.getModelManager().getBlockStateModelSet().missingModel();
        RandomSource random = RandomSource.create(42L);
        int blockCount = 0;
        int spriteCount = 0;

        for (Block block : BuiltInRegistries.BLOCK) {
            ThermalProperties properties = ThermalMaterialRegistry.properties(block);
            if (properties == ThermalProperties.DEFAULT || properties == ThermalProperties.ATLAS_DEFAULT) {
                continue;
            }
            Set<TextureAtlasSprite> sprites = new HashSet<>();
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                BlockStateModel model = minecraft.getModelManager().getBlockStateModelSet().get(state);
                if (model == missing) {
                    continue;
                }
                List<BlockStateModelPart> parts = new ArrayList<>();
                random.setSeed(state.getSeed(net.minecraft.core.BlockPos.ZERO));
                model.collectParts(random, parts);
                collectSprites(parts, sprites);
                TextureAtlasSprite particle = model.particleMaterial().sprite();
                if (isBlockSprite(particle)) {
                    sprites.add(particle);
                }
            }

            boolean fillSolid = properties.materialType() == ThermalProperties.MATERIAL_GLASS
                    || block instanceof DoorBlock
                    || block instanceof TrapDoorBlock
                    || block.defaultBlockState().isSolidRender();
            int encoded = encode(properties);
            for (TextureAtlasSprite sprite : sprites) {
                fillSprite(image, sprite, encoded, fillSolid);
                spriteCount++;
            }
            blockCount++;
        }

        DynamicTexture generated = new DynamicTexture(
                () -> "FPV thermal material atlas", image);
        minecraft.getTextureManager().register(LOCATION, generated);
        texture = generated;
        sourceTexture = currentSource;
        classifiedBlocks = blockCount;
        filledSprites = spriteCount;
        DroneMod.LOGGER.info("Built FPV thermal material atlas {}x{} ({} blocks, {} sprite writes)",
                atlasWidth, atlasHeight, blockCount, spriteCount);
    }

    private static void collectSprites(List<BlockStateModelPart> parts, Set<TextureAtlasSprite> sprites) {
        for (BlockStateModelPart part : parts) {
            for (Direction direction : Direction.values()) {
                part.getQuads(direction).forEach(quad -> {
                    TextureAtlasSprite sprite = quad.materialInfo().sprite();
                    if (isBlockSprite(sprite)) sprites.add(sprite);
                });
            }
            part.getQuads(null).forEach(quad -> {
                TextureAtlasSprite sprite = quad.materialInfo().sprite();
                if (isBlockSprite(sprite)) sprites.add(sprite);
            });
        }
    }

    private static boolean isBlockSprite(@Nullable TextureAtlasSprite sprite) {
        return sprite != null && TextureAtlas.LOCATION_BLOCKS.equals(sprite.atlasLocation());
    }

    private static void fillSprite(NativeImage image, TextureAtlasSprite sprite, int color,
                                   boolean fillSolid) {
        int width = sprite.contents().width();
        int height = sprite.contents().height();
        int startX = Math.max(0, sprite.getX());
        int startY = Math.max(0, sprite.getY());
        int endX = Math.min(image.getWidth(), startX + width);
        int endY = Math.min(image.getHeight(), startY + height);
        for (int y = startY; y < endY; y++) {
            for (int x = startX; x < endX; x++) {
                int localX = x - startX;
                int localY = y - startY;
                if (fillSolid || !sprite.contents().isTransparent(0, localX, localY)) {
                    image.setPixel(x, y, color);
                }
            }
        }
    }

    private static int encode(ThermalProperties properties) {
        int red = channel(properties.temperature());
        int green = channel(properties.emissivity());
        int blue = channel(properties.materialType());
        int alpha = channel(0.10F + properties.temperature() * 0.85F);
        return ARGB.color(alpha, red, green, blue);
    }

    private static int channel(float value) {
        return Math.round(Math.max(0.0F, Math.min(1.0F, value)) * 255.0F);
    }
}
