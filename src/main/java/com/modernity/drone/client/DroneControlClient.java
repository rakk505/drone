package com.modernity.drone.client;

import com.modernity.drone.client.config.ControllerConfig;
import com.modernity.drone.client.config.DroneConfig;
import com.modernity.drone.client.config.FpvClientConfig;
import com.modernity.drone.client.gui.FpvGuiEvents;
import com.modernity.drone.client.input.ControllerReader;
import com.modernity.drone.entity.DroneEntity;
import com.modernity.drone.flight.DroneFlightConfig;
import com.modernity.drone.flight.DroneKind;
import com.modernity.drone.item.FpvGogglesItem;
import com.modernity.drone.item.RemoteControlItem;
import com.modernity.drone.network.DroneControlPayload;
import com.modernity.drone.network.DroneConfigPayload;
import com.modernity.drone.network.DroneViewPayload;
import java.util.Optional;
import java.util.UUID;
import java.util.function.IntConsumer;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Owns the local FPV session and translates the V1.1.4 input contract into the
 * compact server control payload used by this port.
 */
public final class DroneControlClient {
    private static final int ACTIVATION_GRACE_TICKS = 40;
    private static final int STATUS_COLOR = 0xFFAA36;
    private static final ControllerReader CONTROLS = ControllerReader.get();

    private static boolean active;
    private static int droneId = -1;
    private static @Nullable UUID droneUuid;
    private static int pendingDroneId = -1;
    private static @Nullable UUID pendingDroneUuid;
    private static int pendingTicks;
    private static @Nullable UUID suppressedDroneUuid;
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
    private static boolean previousPageUp;
    private static boolean previousPageDown;
    private static @Nullable IntConsumer gogglesChannelRequestSender;

    private DroneControlClient() {
    }

    /**
     * Compatibility entry point for interaction handlers. Actual FPV entry is
     * still governed by the equipped, linked goggles just like V1.1.4.
     */
    public static void requestActivation(DroneEntity drone) {
        pendingDroneId = drone.getId();
        pendingDroneUuid = drone.getUUID();
        pendingTicks = ACTIVATION_GRACE_TICKS;
        if (!drone.getUUID().equals(suppressedDroneUuid)) {
            suppressedDroneUuid = null;
        }
    }

    public static void tick(Minecraft minecraft) {
        FpvClientConfig.initialize();
        CONTROLS.poll(
                minecraft.getWindow().handle(),
                active && minecraft.gui.screen() == null,
                minecraft.options.sensitivity().get()
        );

        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null) {
            if (active || pendingDroneId >= 0) {
                clear(false);
            }
            FpvGuiEvents.resetSessionPrompt();
            return;
        }

        FpvGuiEvents.maybeShowFirstRun(minecraft);
        if (!FpvClientConfig.setupComplete()) {
            return;
        }
        handleCameraFeatureKeys(minecraft);

        Optional<UUID> linkedGoggles = linkedGogglesDrone(player);
        if (linkedGoggles.isEmpty()) {
            suppressedDroneUuid = null;
            if (active) {
                clear(false);
            }
            agePendingRequest();
            return;
        }

        UUID linkedId = linkedGoggles.get();
        if (active && droneUuid != null && !droneUuid.equals(linkedId)) {
            clear(false);
        }

        if (!active) {
            if (linkedId.equals(suppressedDroneUuid)) {
                agePendingRequest();
                return;
            }
            DroneEntity candidate = findDroneByUuid(level, linkedId);
            if (candidate == null) {
                candidate = resolvePending(level, linkedId);
            }
            if (candidate != null && candidate.hasBattery()) {
                begin(minecraft, candidate);
            } else {
                agePendingRequest();
            }
            return;
        }

        if (sessionLevel != level) {
            clear(false);
            return;
        }

        DroneEntity drone = resolveActive(level);
        if (drone == null) {
            // Keep the goggles session alive on its last good video frame. V1.1.4 remains in
            // NO SIGNAL after a craft is destroyed or unloaded until the goggles are removed or
            // their channel changes; tearing the session down here loses that behavior.
            if (missingTicks < Integer.MAX_VALUE) missingTicks++;
            return;
        }
        missingTicks = 0;

        // The view exists as soon as goggles are equipped. A linked remote is
        // only required for arming/control, mirroring the original status flow.
        minecraft.setCameraEntity(drone);
        if (!drone.hasBattery()) {
            requestedArmed = false;
        }

        boolean screenOpen = minecraft.gui.screen() != null;
        if (!screenOpen) {
            sampleActions(player, drone);
        }
        if (hasLinkedRemote(player, drone)) {
            sendControl(drone, screenOpen);
        }
    }

    private static void sampleActions(LocalPlayer player, DroneEntity drone) {
        boolean toggleArm = false;
        while (DroneClient.ARM.consumeClick()) {
            toggleArm = !toggleArm;
        }
        if (CONTROLS.consumeArmToggle()) {
            toggleArm = !toggleArm;
        }
        if (toggleArm) {
            if (!hasLinkedRemote(player, drone)) {
                status(player, Component.translatable("fpvdrone.status.connect_remote"));
            } else if (!requestedArmed && !drone.hasBattery()) {
                status(player, Component.translatable("fpvdrone.status.no_battery"));
            } else {
                requestedArmed = !requestedArmed;
            }
        }

        if (drone.kind() == DroneKind.PAYLOAD) {
            while (DroneClient.HOVER.consumeClick()) {
                hoverMode = !hoverMode;
                if (hoverMode) returnHome = false;
            }
            while (DroneClient.RETURN_HOME.consumeClick()) {
                returnHome = !returnHome;
                if (returnHome) hoverMode = false;
            }
        } else {
            hoverMode = false;
            returnHome = false;
        }
    }

    private static void sendControl(DroneEntity drone, boolean neutralizeAttitude) {
        float roll = neutralizeAttitude ? 0.0F : CONTROLS.roll();
        float pitch = neutralizeAttitude ? 0.0F : CONTROLS.pitch();
        float yaw = neutralizeAttitude ? 0.0F : CONTROLS.yaw();
        throttle = Mth.clamp((CONTROLS.throttle() + 1.0F) * 0.5F, 0.0F, 1.0F);

        byte actions = 0;
        if (requestedArmed) actions |= DroneControlPayload.ARMED;
        if (hoverMode) actions |= DroneControlPayload.HOVER;
        if (returnHome) actions |= DroneControlPayload.RETURN_HOME;
        if (!neutralizeAttitude && DroneClient.DROP_PAYLOAD.consumeClick()) {
            actions |= DroneControlPayload.DROP;
        }
        ClientPacketDistributor.sendToServer(new DroneControlPayload(
                drone.getId(), roll, pitch, yaw, throttle, actions
        ));
    }

    private static void handleCameraFeatureKeys(Minecraft minecraft) {
        if (!active || minecraft.player == null || minecraft.gui.screen() != null) {
            previousPageUp = false;
            previousPageDown = false;
            return;
        }
        long window = minecraft.getWindow().handle();
        boolean pageUp = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_PAGE_UP) == GLFW.GLFW_PRESS;
        boolean pageDown = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_PAGE_DOWN) == GLFW.GLFW_PRESS;
        if (pageUp && !previousPageUp) {
            float angle = Math.min(60.0F, FpvClientConfig.cameraAngle() + 1.0F);
            FpvClientConfig.setCameraAngle(angle);
            status(minecraft.player, Component.literal("Camera Angle: " + (int) angle + "°"));
        }
        if (pageDown && !previousPageDown) {
            float angle = Math.max(-60.0F, FpvClientConfig.cameraAngle() - 1.0F);
            FpvClientConfig.setCameraAngle(angle);
            status(minecraft.player, Component.literal("Camera Angle: " + (int) angle + "°"));
        }
        previousPageUp = pageUp;
        previousPageDown = pageDown;

        while (DroneClient.EXIT_VIEW.consumeClick()) {
            suppressedDroneUuid = droneUuid;
            clear(true);
        }
    }

    private static void status(LocalPlayer player, Component message) {
        player.sendOverlayMessage(message.copy().withColor(STATUS_COLOR));
    }

    private static void begin(Minecraft minecraft, DroneEntity drone) {
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) return;

        active = true;
        droneId = drone.getId();
        droneUuid = drone.getUUID();
        pendingDroneId = -1;
        pendingDroneUuid = null;
        pendingTicks = 0;
        suppressedDroneUuid = null;
        missingTicks = 0;
        requestedArmed = drone.isArmed();
        hoverMode = false;
        returnHome = false;
        CONTROLS.resetKeyboard();
        throttle = 0.0F;
        previousCameraEntity = minecraft.getCameraEntity();
        previousCameraType = minecraft.options.getCameraType();
        previousPlayerInput = player.input;
        previousPlayerYaw = player.getYRot();
        previousPlayerPitch = player.getXRot();
        sessionLevel = minecraft.level;
        player.input = new ClientInput();
        minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        minecraft.setCameraEntity(drone);
        ClientPacketDistributor.sendToServer(new DroneViewPayload(drone.getUUID(), true));
    }

    public static void clear(boolean suppressReentry) {
        Minecraft minecraft = Minecraft.getInstance();
        UUID clearedDroneUuid = droneUuid;
        sendExitFailsafe(minecraft);
        if (active && clearedDroneUuid != null && minecraft.getConnection() != null) {
            ClientPacketDistributor.sendToServer(new DroneViewPayload(clearedDroneUuid, false));
        }
        if (minecraft.player != null) {
            minecraft.player.setYRot(previousPlayerYaw);
            minecraft.player.setXRot(previousPlayerPitch);
            if (previousPlayerInput != null) minecraft.player.input = previousPlayerInput;
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
        if (previousCameraType != null) minecraft.options.setCameraType(previousCameraType);

        active = false;
        droneId = -1;
        droneUuid = null;
        pendingDroneId = -1;
        pendingDroneUuid = null;
        pendingTicks = 0;
        suppressedDroneUuid = suppressReentry ? clearedDroneUuid : null;
        missingTicks = 0;
        requestedArmed = false;
        hoverMode = false;
        returnHome = false;
        throttle = 0.0F;
        previousCameraEntity = null;
        previousCameraType = null;
        previousPlayerInput = null;
        sessionLevel = null;
        previousPageUp = false;
        previousPageDown = false;
        CONTROLS.resetKeyboard();
    }

    private static void sendExitFailsafe(Minecraft minecraft) {
        if (!active || minecraft.level == null || minecraft.player == null) return;
        DroneEntity drone = resolveActive(minecraft.level);
        if (drone == null || !hasLinkedRemote(minecraft.player, drone)) return;
        ClientPacketDistributor.sendToServer(new DroneControlPayload(
                drone.getId(), 0.0F, 0.0F, 0.0F, 0.0F, (byte) 0
        ));
    }

    public static boolean isActive() {
        return active;
    }

    public static @Nullable DroneEntity currentDrone() {
        Minecraft minecraft = Minecraft.getInstance();
        return active && minecraft.level != null ? resolveActive(minecraft.level) : null;
    }

    public static @Nullable UUID activeDroneUuid() {
        return active ? droneUuid : null;
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

    public static ControllerReader controllerReader() {
        return CONTROLS;
    }

    /** Rate value exposed for the entity/config synchronization layer. */
    public static float configuredRateDegreesPerSecond(int axis, float stickInput) {
        ControllerConfig config = FpvClientConfig.controller();
        return ControllerConfig.betaflightRate(
                stickInput,
                config.rcRate(axis),
                config.superRate(axis),
                config.expo(axis)
        );
    }

    /**
     * Applies a complete Betaflight/propulsion profile to the goggles-linked
     * airframe. The server validates ownership, ranges, and the active link.
     */
    public static boolean sendDroneConfiguration(DroneConfig config) {
        return config != null && sendDroneConfiguration(config.toFlightConfig());
    }

    /** Sends a complete, already synchronized server-neutral profile. */
    public static boolean sendDroneConfiguration(DroneFlightConfig config) {
        DroneEntity drone = currentDrone();
        if (drone == null || config == null) return false;
        ClientPacketDistributor.sendToServer(new DroneConfigPayload(
                drone.getId(),
                config.yawRcRate(),
                config.pitchRcRate(),
                config.rollRcRate(),
                config.yawSuperRate(),
                config.pitchSuperRate(),
                config.rollSuperRate(),
                config.yawExpo(),
                config.pitchExpo(),
                config.rollExpo(),
                config.motorKv(),
                config.propDiameterInches(),
                config.propPitchInches(),
                config.dragCoefficient(),
                config.thrustMultiplier(),
                config.flightMode3d(),
                config.droneName()
        ));
        return true;
    }

    /** Installs the transport used by the Shift+wheel goggles channel hook. */
    public static void setGogglesChannelRequestSender(@Nullable IntConsumer sender) {
        gogglesChannelRequestSender = sender;
    }

    public static boolean requestGogglesChannelStep(int direction) {
        Minecraft minecraft = Minecraft.getInstance();
        if (direction == 0 || minecraft.player == null || gogglesChannelRequestSender == null) return false;
        ItemStack goggles = minecraft.player.getItemBySlot(EquipmentSlot.HEAD);
        if (!(goggles.getItem() instanceof FpvGogglesItem)) {
            goggles = minecraft.player.getMainHandItem();
            if (!(goggles.getItem() instanceof FpvGogglesItem)) return false;
        }
        gogglesChannelRequestSender.accept(direction > 0 ? 1 : -1);
        return true;
    }

    public static boolean isControllerFor(DroneEntity drone, ItemStack stack) {
        return stack.getItem() instanceof RemoteControlItem
                && RemoteControlItem.getLinkedDroneId(stack).filter(drone.getUUID()::equals).isPresent();
    }

    private static boolean hasLinkedRemote(LocalPlayer player, DroneEntity drone) {
        return RemoteControlItem.playerHasLinkedRemote(player, drone.getUUID());
    }

    private static Optional<UUID> linkedGogglesDrone(LocalPlayer player) {
        ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
        return helmet.getItem() instanceof FpvGogglesItem
                ? FpvGogglesItem.getLinkedDroneId(helmet)
                : Optional.empty();
    }

    private static void agePendingRequest() {
        if (pendingDroneId >= 0 && --pendingTicks <= 0) {
            pendingDroneId = -1;
            pendingDroneUuid = null;
        }
    }

    private static @Nullable DroneEntity resolvePending(ClientLevel level, UUID linkedId) {
        if (pendingDroneId < 0 || pendingDroneUuid == null || !pendingDroneUuid.equals(linkedId)) return null;
        return resolve(level, pendingDroneId);
    }

    private static @Nullable DroneEntity resolveActive(ClientLevel level) {
        DroneEntity byId = resolve(level, droneId);
        if (byId != null && (droneUuid == null || droneUuid.equals(byId.getUUID()))) return byId;
        DroneEntity byUuid = droneUuid == null ? null : findDroneByUuid(level, droneUuid);
        if (byUuid != null) droneId = byUuid.getId();
        return byUuid;
    }

    private static @Nullable DroneEntity resolve(ClientLevel level, int id) {
        Entity entity = level.getEntity(id);
        return entity instanceof DroneEntity drone && drone.isAlive() ? drone : null;
    }

    private static @Nullable DroneEntity findDroneByUuid(ClientLevel level, UUID id) {
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof DroneEntity drone && drone.isAlive() && drone.getUUID().equals(id)) {
                return drone;
            }
        }
        return null;
    }
}
