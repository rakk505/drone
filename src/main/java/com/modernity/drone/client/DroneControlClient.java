package com.modernity.drone.client;

import com.modernity.drone.DroneMod;
import com.modernity.drone.entity.DroneEntity;
import com.modernity.drone.flight.DroneKind;
import com.modernity.drone.network.DroneControlPayload;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

public final class DroneControlClient {
    private static final int ACTIVATION_GRACE_TICKS = 40;
    private static final int MISSING_ENTITY_GRACE_TICKS = 20;
    private static final float MOSQUITO_THROTTLE_STEP = 0.025F;

    private static boolean active;
    private static int droneId = -1;
    private static int pendingDroneId = -1;
    private static int pendingTicks;
    private static int suppressedDroneId = -1;
    private static int missingTicks;
    private static boolean requestedArmed;
    private static boolean hoverMode;
    private static boolean returnHome;
    private static float throttle;
    private static @Nullable Entity previousCameraEntity;
    private static @Nullable CameraType previousCameraType;
    private static @Nullable ClientInput previousPlayerInput;
    private static @Nullable ClientLevel sessionLevel;
    private static float previousPlayerYaw;
    private static float previousPlayerPitch;

    private DroneControlClient() {
    }

    public static void requestActivation(DroneEntity drone) {
        pendingDroneId = drone.getId();
        pendingTicks = ACTIVATION_GRACE_TICKS;
        suppressedDroneId = -1;
    }

    public static void tick(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null) {
            clear(false);
            return;
        }

        if (DroneClient.EXIT_VIEW.consumeClick()) {
            if (active) {
                clear(true);
                return;
            }
            suppressedDroneId = -1;
            DroneEntity candidate = findPilotedDrone(level, player, -1);
            if (candidate != null) {
                begin(minecraft, candidate);
            }
            return;
        }

        if (!active) {
            DroneEntity candidate = resolvePending(level);
            if (candidate == null && pendingDroneId < 0) {
                candidate = findPilotedDrone(level, player, suppressedDroneId);
            }
            if (candidate != null && candidate.isPilotedBy(player) && hasControllerFor(player, candidate.kind())) {
                begin(minecraft, candidate);
            } else if (pendingDroneId >= 0 && --pendingTicks <= 0) {
                pendingDroneId = -1;
            }
            return;
        }

        if (sessionLevel != level || minecraft.gui.screen() != null) {
            clear(true);
            return;
        }

        DroneEntity drone = resolve(level, droneId);
        if (drone == null || !drone.isPilotedBy(player)) {
            if (++missingTicks > MISSING_ENTITY_GRACE_TICKS) {
                clear(false);
            }
            return;
        }
        missingTicks = 0;
        if (!hasControllerFor(player, drone.kind())) {
            clear(true);
            return;
        }

        minecraft.setCameraEntity(drone);
        minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        sampleActions(drone.kind());
        sendControl(drone);
    }

    private static void sampleActions(DroneKind kind) {
        while (DroneClient.ARM.consumeClick()) {
            requestedArmed = !requestedArmed;
            if (!requestedArmed) {
                throttle = kind == DroneKind.PAYLOAD ? 0.5F : 0.0F;
            }
        }
        if (kind == DroneKind.PAYLOAD) {
            while (DroneClient.HOVER.consumeClick()) {
                hoverMode = !hoverMode;
                if (hoverMode) {
                    returnHome = false;
                }
            }
            while (DroneClient.RETURN_HOME.consumeClick()) {
                returnHome = !returnHome;
                if (returnHome) {
                    hoverMode = false;
                }
            }
        } else {
            hoverMode = false;
            returnHome = false;
        }
    }

    private static void sendControl(DroneEntity drone) {
        float pitch = axis(DroneClient.PITCH_FORWARD.isDown(), DroneClient.PITCH_BACK.isDown());
        float roll = axis(DroneClient.ROLL_RIGHT.isDown(), DroneClient.ROLL_LEFT.isDown());
        float yaw = axis(DroneClient.YAW_RIGHT.isDown(), DroneClient.YAW_LEFT.isDown());
        float vertical = axis(DroneClient.THROTTLE_UP.isDown(), DroneClient.THROTTLE_DOWN.isDown());
        if (drone.kind() == DroneKind.PAYLOAD) {
            throttle = 0.5F + vertical * 0.5F;
        } else {
            throttle = Mth.clamp(throttle + vertical * MOSQUITO_THROTTLE_STEP, 0.0F, 1.0F);
        }

        byte actions = 0;
        if (requestedArmed) {
            actions |= DroneControlPayload.ARMED;
        }
        if (hoverMode) {
            actions |= DroneControlPayload.HOVER;
        }
        if (returnHome) {
            actions |= DroneControlPayload.RETURN_HOME;
        }
        if (DroneClient.DROP_PAYLOAD.consumeClick()) {
            actions |= DroneControlPayload.DROP;
        }
        ClientPacketDistributor.sendToServer(new DroneControlPayload(
                drone.getId(), roll, pitch, yaw, throttle, actions
        ));
    }

    private static float axis(boolean positive, boolean negative) {
        return positive == negative ? 0.0F : positive ? 1.0F : -1.0F;
    }

    private static void begin(Minecraft minecraft, DroneEntity drone) {
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }
        active = true;
        droneId = drone.getId();
        pendingDroneId = -1;
        pendingTicks = 0;
        suppressedDroneId = -1;
        missingTicks = 0;
        requestedArmed = drone.isArmed();
        hoverMode = false;
        returnHome = false;
        throttle = drone.kind() == DroneKind.PAYLOAD ? 0.5F : 0.0F;
        previousCameraEntity = minecraft.getCameraEntity();
        previousCameraType = minecraft.options.getCameraType();
        previousPlayerInput = player.input;
        previousPlayerYaw = player.getYRot();
        previousPlayerPitch = player.getXRot();
        sessionLevel = minecraft.level;
        player.input = new ClientInput();
        minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        minecraft.setCameraEntity(drone);
    }

    public static void clear(boolean suppressReentry) {
        Minecraft minecraft = Minecraft.getInstance();
        int oldDroneId = droneId;
        sendExitFailsafe(minecraft);
        if (minecraft.player != null) {
            minecraft.player.setYRot(previousPlayerYaw);
            minecraft.player.setXRot(previousPlayerPitch);
            if (previousPlayerInput != null) {
                minecraft.player.input = previousPlayerInput;
            }
        }
        if (previousCameraEntity != null
                && minecraft.level != null
                && previousCameraEntity.level() == minecraft.level
                && !previousCameraEntity.isRemoved()) {
            minecraft.setCameraEntity(previousCameraEntity);
        } else if (minecraft.player != null) {
            minecraft.setCameraEntity(minecraft.player);
        } else {
            minecraft.setCameraEntity(null);
        }
        if (previousCameraType != null) {
            minecraft.options.setCameraType(previousCameraType);
        }

        active = false;
        droneId = -1;
        pendingDroneId = -1;
        pendingTicks = 0;
        suppressedDroneId = suppressReentry ? oldDroneId : -1;
        missingTicks = 0;
        requestedArmed = false;
        hoverMode = false;
        returnHome = false;
        throttle = 0.0F;
        previousCameraEntity = null;
        previousCameraType = null;
        previousPlayerInput = null;
        sessionLevel = null;
    }

    private static void sendExitFailsafe(Minecraft minecraft) {
        if (!active || minecraft.level == null || minecraft.player == null) {
            return;
        }
        DroneEntity drone = resolve(minecraft.level, droneId);
        if (drone == null || !hasControllerFor(minecraft.player, drone.kind())) {
            return;
        }
        byte actions = 0;
        float safeThrottle = 0.0F;
        if (drone.kind() == DroneKind.PAYLOAD && requestedArmed) {
            actions = (byte) (DroneControlPayload.ARMED | DroneControlPayload.RETURN_HOME);
            safeThrottle = 0.5F;
        }
        ClientPacketDistributor.sendToServer(new DroneControlPayload(
                drone.getId(), 0.0F, 0.0F, 0.0F, safeThrottle, actions
        ));
    }

    public static boolean isActive() {
        return active;
    }

    public static @Nullable DroneEntity currentDrone() {
        Minecraft minecraft = Minecraft.getInstance();
        return active && minecraft.level != null ? resolve(minecraft.level, droneId) : null;
    }

    public static float requestedThrottle() {
        return throttle;
    }

    public static boolean hoverMode() {
        return hoverMode;
    }

    public static boolean returnHome() {
        return returnHome;
    }

    public static boolean isControllerFor(DroneEntity drone, ItemStack stack) {
        Item expected = drone.kind() == DroneKind.MOSQUITO
                ? DroneMod.FPV_CONTROLLER.get()
                : DroneMod.DJI_CONTROLLER.get();
        return stack.getItem() == expected;
    }

    private static boolean hasControllerFor(LocalPlayer player, DroneKind kind) {
        Item expected = kind == DroneKind.MOSQUITO
                ? DroneMod.FPV_CONTROLLER.get()
                : DroneMod.DJI_CONTROLLER.get();
        return player.getMainHandItem().getItem() == expected
                || player.getOffhandItem().getItem() == expected;
    }

    private static @Nullable DroneEntity resolvePending(ClientLevel level) {
        return pendingDroneId < 0 ? null : resolve(level, pendingDroneId);
    }

    private static @Nullable DroneEntity resolve(ClientLevel level, int id) {
        Entity entity = level.getEntity(id);
        return entity instanceof DroneEntity drone && drone.isAlive() ? drone : null;
    }

    private static @Nullable DroneEntity findPilotedDrone(ClientLevel level, LocalPlayer player, int excludedId) {
        DroneEntity closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof DroneEntity drone)
                    || drone.getId() == excludedId
                    || !drone.isPilotedBy(player)
                    || !hasControllerFor(player, drone.kind())) {
                continue;
            }
            double distance = player.distanceToSqr(drone);
            if (distance < closestDistance) {
                closest = drone;
                closestDistance = distance;
            }
        }
        return closest;
    }
}
