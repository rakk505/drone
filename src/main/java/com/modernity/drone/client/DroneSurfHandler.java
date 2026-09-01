package com.modernity.drone.client;

import com.modernity.drone.entity.DroneEntity;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

/** Carries a player standing on a moving drone and resolves shallow intersections. */
public final class DroneSurfHandler {
    private static final double MAX_CARRY_DISTANCE_SQUARED = 100.0;
    private static final double FEET_SEARCH_BELOW = 0.25;
    private static final double FEET_ON_TOP_TOLERANCE = 0.15;
    private static final double STEP_UP_HEIGHT = 0.6;

    private static UUID lastSupportingDroneId;
    private static double lastDroneX;
    private static double lastDroneY;
    private static double lastDroneZ;

    private DroneSurfHandler() {
    }

    public static void tick(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null || player.noPhysics
                || player.isFallFlying() || player.getAbilities().flying) {
            reset();
            return;
        }

        DroneEntity sticky = lastSupportingDroneId == null
                ? null : findDrone(level, lastSupportingDroneId);
        if (sticky != null) {
            double dx = sticky.getX() - lastDroneX;
            double dy = sticky.getY() - lastDroneY;
            double dz = sticky.getZ() - lastDroneZ;
            double lengthSquared = dx * dx + dy * dy + dz * dz;
            if (lengthSquared > 1.0E-8 && lengthSquared <= MAX_CARRY_DISTANCE_SQUARED) {
                player.setPos(player.getX() + dx, player.getY() + dy, player.getZ() + dz);
            }
            lastDroneX = sticky.getX();
            lastDroneY = sticky.getY();
            lastDroneZ = sticky.getZ();
        }

        DroneEntity intersecting = findIntersectingDrone(player);
        if (intersecting != null) resolveIntersection(player, intersecting);

        DroneEntity support = findSupportDrone(player);
        if (support == null) {
            lastSupportingDroneId = null;
            return;
        }
        UUID supportId = support.getUUID();
        if (!supportId.equals(lastSupportingDroneId)) {
            lastDroneX = support.getX();
            lastDroneY = support.getY();
            lastDroneZ = support.getZ();
        }
        lastSupportingDroneId = supportId;
        player.fallDistance = 0.0;
    }

    public static void reset() {
        lastSupportingDroneId = null;
    }

    private static DroneEntity findDrone(ClientLevel level, UUID id) {
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof DroneEntity drone
                    && drone.isAlive() && drone.isPickable() && drone.getUUID().equals(id)) {
                return drone;
            }
        }
        return null;
    }

    private static DroneEntity findIntersectingDrone(LocalPlayer player) {
        List<DroneEntity> candidates = player.level().getEntitiesOfClass(
                DroneEntity.class,
                player.getBoundingBox(),
                drone -> drone.isAlive() && drone.isPickable()
        );
        DroneEntity best = null;
        double bestTop = Double.NEGATIVE_INFINITY;
        for (DroneEntity drone : candidates) {
            double top = drone.getBoundingBox().maxY;
            if (top > bestTop) {
                bestTop = top;
                best = drone;
            }
        }
        return best;
    }

    private static void resolveIntersection(LocalPlayer player, DroneEntity drone) {
        AABB droneBox = drone.getBoundingBox();
        AABB playerBox = player.getBoundingBox();
        double top = droneBox.maxY;
        double lift = top - player.getY();
        if (lift > 0.0 && lift <= STEP_UP_HEIGHT) {
            player.setPos(player.getX(), top, player.getZ());
            player.fallDistance = 0.0;
            return;
        }

        double negativeX = playerBox.maxX - droneBox.minX;
        double positiveX = droneBox.maxX - playerBox.minX;
        double negativeZ = playerBox.maxZ - droneBox.minZ;
        double positiveZ = droneBox.maxZ - playerBox.minZ;
        double smallest = Double.POSITIVE_INFINITY;
        double pushX = 0.0;
        double pushZ = 0.0;
        if (negativeX > 0.0 && negativeX < smallest) {
            smallest = negativeX;
            pushX = -negativeX;
        }
        if (positiveX > 0.0 && positiveX < smallest) {
            smallest = positiveX;
            pushX = positiveX;
            pushZ = 0.0;
        }
        if (negativeZ > 0.0 && negativeZ < smallest) {
            smallest = negativeZ;
            pushX = 0.0;
            pushZ = -negativeZ;
        }
        if (positiveZ > 0.0 && positiveZ < smallest) {
            smallest = positiveZ;
            pushX = 0.0;
            pushZ = positiveZ;
        }
        if (Double.isFinite(smallest) && smallest > 0.0) {
            player.setPos(
                    player.getX() + pushX + Math.signum(pushX) * 0.001,
                    player.getY(),
                    player.getZ() + pushZ + Math.signum(pushZ) * 0.001
            );
        }
    }

    private static DroneEntity findSupportDrone(LocalPlayer player) {
        double feetY = player.getY();
        AABB box = player.getBoundingBox();
        AABB search = new AABB(
                box.minX, feetY - FEET_SEARCH_BELOW, box.minZ,
                box.maxX, feetY + FEET_ON_TOP_TOLERANCE, box.maxZ
        );
        List<DroneEntity> candidates = player.level().getEntitiesOfClass(
                DroneEntity.class,
                search,
                drone -> drone.isAlive() && drone.isPickable()
        );
        DroneEntity best = null;
        double bestTop = Double.NEGATIVE_INFINITY;
        for (DroneEntity drone : candidates) {
            double top = drone.getBoundingBox().maxY;
            if (top >= feetY - FEET_SEARCH_BELOW
                    && top <= feetY + FEET_ON_TOP_TOLERANCE
                    && top > bestTop) {
                bestTop = top;
                best = drone;
            }
        }
        return best;
    }
}
