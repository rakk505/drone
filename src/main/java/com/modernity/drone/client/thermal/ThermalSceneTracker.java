package com.modernity.drone.client.thermal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Collects a bounded set of nearby exposed blocks for material-accurate thermal rendering. */
public final class ThermalSceneTracker {
    private static final int HORIZONTAL_RADIUS = 18;
    private static final int VERTICAL_RADIUS = 12;
    private static final int RESCAN_INTERVAL_TICKS = 8;
    private static final int MAX_RENDERED_BLOCKS = 4096;
    private static final ThermalSceneTracker INSTANCE = new ThermalSceneTracker();

    private List<ThermalBlock> blocks = List.of();
    private @Nullable ClientLevel observedLevel;
    private BlockPos lastCenter = BlockPos.ZERO;
    private int ticksUntilScan;

    private ThermalSceneTracker() {
    }

    public static ThermalSceneTracker get() {
        return INSTANCE;
    }

    public void tick(Minecraft minecraft) {
        if (!ThermalState.get().active() || minecraft.level == null) {
            if (observedLevel != minecraft.level) clear();
            return;
        }
        ClientLevel level = minecraft.level;
        Entity cameraEntity = minecraft.getCameraEntity();
        if (cameraEntity == null) {
            return;
        }
        BlockPos center = cameraEntity.blockPosition();
        boolean changedLevel = observedLevel != level;
        boolean moved = center.distSqr(lastCenter) >= 16.0;
        if (!changedLevel && !moved && ticksUntilScan-- > 0) {
            return;
        }
        observedLevel = level;
        lastCenter = center;
        ticksUntilScan = RESCAN_INTERVAL_TICKS;
        scan(level, center);
    }

    public List<ThermalBlock> snapshot() {
        return blocks;
    }

    public void clear() {
        blocks = List.of();
        observedLevel = null;
        lastCenter = BlockPos.ZERO;
        ticksUntilScan = 0;
    }

    private void scan(ClientLevel level, BlockPos center) {
        int minY = Math.max(level.getMinY(), center.getY() - VERTICAL_RADIUS);
        int maxY = Math.min(level.getMaxY(), center.getY() + VERTICAL_RADIUS);
        ArrayList<ThermalBlock> found = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos neighbor = new BlockPos.MutableBlockPos();

        for (int x = center.getX() - HORIZONTAL_RADIUS; x <= center.getX() + HORIZONTAL_RADIUS; x++) {
            for (int z = center.getZ() - HORIZONTAL_RADIUS; z <= center.getZ() + HORIZONTAL_RADIUS; z++) {
                cursor.set(x, minY, z);
                if (!level.isLoaded(cursor)) continue;
                for (int y = minY; y <= maxY; y++) {
                    cursor.set(x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    if (state.isAir()
                            || state.getRenderShape() == RenderShape.INVISIBLE
                            || !ThermalMaterialRegistry.isExplicitlyClassified(state)
                            || !isExposed(level, cursor, neighbor)) {
                        continue;
                    }
                    found.add(new ThermalBlock(cursor.immutable(), state));
                }
            }
        }

        if (found.size() > MAX_RENDERED_BLOCKS) {
            found.sort(Comparator.comparingDouble(block -> block.pos().distSqr(center)));
            found.subList(MAX_RENDERED_BLOCKS, found.size()).clear();
        }
        blocks = List.copyOf(found);
    }

    private static boolean isExposed(ClientLevel level, BlockPos pos, BlockPos.MutableBlockPos neighbor) {
        for (Direction direction : Direction.values()) {
            neighbor.setWithOffset(pos, direction);
            BlockState adjacent = level.getBlockState(neighbor);
            if (!adjacent.isSolidRender()) {
                return true;
            }
        }
        return false;
    }

    public record ThermalBlock(BlockPos pos, BlockState state) {
    }
}
