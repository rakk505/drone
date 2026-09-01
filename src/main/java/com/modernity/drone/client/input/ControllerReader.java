package com.modernity.drone.client.input;

import com.modernity.drone.client.config.ControllerConfig;
import com.modernity.drone.client.config.FpvClientConfig;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWGamepadState;

/** Polls either a calibrated GLFW radio/gamepad or the V1.1.4 keyboard/mouse fallback. */
public final class ControllerReader {
    private static final ControllerReader INSTANCE = new ControllerReader();
    private static final int MAX_RAW_AXES = 16;
    private static final int MAX_BUTTONS = 32;
    private static final float MOUSE_INPUT_SCALE = 0.067F;

    private final float[] axes = new float[MAX_RAW_AXES];
    private final boolean[] buttons = new boolean[MAX_BUTTONS];
    private final float[] captureAxes = new float[MAX_RAW_AXES];
    private final boolean[] captureButtons = new boolean[MAX_BUTTONS];
    private final float[] rangeMinimum = new float[ControllerConfig.AXIS_COUNT];
    private final float[] rangeMaximum = new float[ControllerConfig.AXIS_COUNT];

    private int connectedJoystick = -1;
    private int actualAxisCount;
    private int actualButtonCount;
    private long nextDetectionNanos;
    private long lastPollNanos;
    private double lastCursorX;
    private double lastCursorY;
    private boolean cursorInitialized;
    private float keyboardThrottle = -1.0F;
    private float keyboardYaw;
    private float throttle = -1.0F;
    private float yaw;
    private float pitch;
    private float roll;
    private boolean lastArmState;
    private boolean rangeCapture;
    private boolean inputCapture;

    private ControllerReader() {
        Arrays.fill(rangeMinimum, Float.POSITIVE_INFINITY);
        Arrays.fill(rangeMaximum, Float.NEGATIVE_INFINITY);
    }

    public static ControllerReader get() {
        return INSTANCE;
    }

    public void poll(long window, boolean flightActive, double mouseSensitivity) {
        long now = System.nanoTime();
        float dt = lastPollNanos == 0L ? 0.016F : Math.min((now - lastPollNanos) / 1_000_000_000.0F, 0.05F);
        lastPollNanos = now;
        if (connectedJoystick >= 0 && !GLFW.glfwJoystickPresent(connectedJoystick)) {
            // Match the old callback path: falling back to KBM must not snap the
            // throttle/yaw to stale values when a radio is unplugged in flight.
            keyboardThrottle = throttle;
            keyboardYaw = yaw;
            connectedJoystick = -1;
            actualAxisCount = 0;
            actualButtonCount = 0;
            lastArmState = false;
        }
        if (connectedJoystick < 0) {
            if (now >= nextDetectionNanos) {
                detectJoystick();
                nextDetectionNanos = now + 1_000_000_000L;
            }
        }

        boolean physical = connectedJoystick >= 0 && !FpvClientConfig.forceKeyboardMouse();
        if (physical) {
            readJoystick();
            ControllerConfig config = FpvClientConfig.controller();
            throttle = processedAxis(config.channel(ControllerConfig.THROTTLE), config.inverted(ControllerConfig.THROTTLE));
            yaw = processedAxis(config.channel(ControllerConfig.YAW), config.inverted(ControllerConfig.YAW));
            pitch = processedAxis(config.channel(ControllerConfig.PITCH), config.inverted(ControllerConfig.PITCH));
            roll = processedAxis(config.channel(ControllerConfig.ROLL), config.inverted(ControllerConfig.ROLL));
            cursorInitialized = false;
        } else {
            updateKeyboard(window, dt);
            throttle = keyboardThrottle;
            yaw = keyboardYaw;
            updateMouse(window, flightActive, mouseSensitivity);
        }

        if (rangeCapture && physical) {
            for (int axis = 0; axis < Math.min(actualAxisCount, ControllerConfig.AXIS_COUNT); axis++) {
                rangeMinimum[axis] = Math.min(rangeMinimum[axis], axes[axis]);
                rangeMaximum[axis] = Math.max(rangeMaximum[axis], axes[axis]);
            }
        }
    }

    private void updateKeyboard(long window, float dt) {
        boolean up = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_W) == GLFW.GLFW_PRESS;
        boolean down = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_S) == GLFW.GLFW_PRESS;
        if (up != down) {
            keyboardThrottle += (up ? 0.4F : -0.4F) * dt;
        }
        keyboardThrottle = Mth.clamp(keyboardThrottle, -1.0F, 1.0F);

        boolean left = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_A) == GLFW.GLFW_PRESS;
        boolean right = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_D) == GLFW.GLFW_PRESS;
        if (left != right) {
            keyboardYaw = moveToward(keyboardYaw, left ? -1.0F : 1.0F, 2.0F * dt);
        } else {
            keyboardYaw = moveToward(keyboardYaw, 0.0F, 3.5F * dt);
        }
    }

    private void updateMouse(long window, boolean flightActive, double mouseSensitivity) {
        double[] x = new double[1];
        double[] y = new double[1];
        GLFW.glfwGetCursorPos(window, x, y);
        if (!cursorInitialized || !flightActive) {
            lastCursorX = x[0];
            lastCursorY = y[0];
            cursorInitialized = flightActive;
            pitch = 0.0F;
            roll = 0.0F;
            return;
        }

        double dx = x[0] - lastCursorX;
        double dy = y[0] - lastCursorY;
        lastCursorX = x[0];
        lastCursorY = y[0];
        double sensitivity = mouseSensitivity * 0.6 + 0.2;
        double scaled = sensitivity * sensitivity * sensitivity * 8.0 * 0.15 * 10.0 * MOUSE_INPUT_SCALE;
        roll = Mth.clamp((float) (dx * scaled), -1.0F, 1.0F);
        pitch = Mth.clamp((float) (dy * scaled), -1.0F, 1.0F);
    }

    private void detectJoystick() {
        String preferredGuid = FpvClientConfig.controller().preferredJoystickGuid();
        if (preferredGuid.isBlank()) {
            return;
        }
        List<Device> devices = availableDevices();
        if (devices.isEmpty()) {
            return;
        }
        devices.stream()
                .filter(device -> preferredGuid.equals(device.guid()))
                .findFirst()
                .ifPresent(this::connectRememberedDevice);
    }

    private void connectRememberedDevice(Device device) {
        connectedJoystick = device.jid();
        actualAxisCount = 0;
        actualButtonCount = 0;
        lastArmState = false;
    }

    private void readJoystick() {
        Arrays.fill(axes, 0.0F);
        Arrays.fill(buttons, false);
        boolean readGamepad = false;
        if (GLFW.glfwJoystickIsGamepad(connectedJoystick)) {
            GLFWGamepadState state = GLFWGamepadState.calloc();
            try {
                if (GLFW.glfwGetGamepadState(connectedJoystick, state)) {
                    FloatBuffer sourceAxes = state.axes();
                    ByteBuffer sourceButtons = state.buttons();
                    actualAxisCount = Math.min(sourceAxes.remaining(), axes.length);
                    actualButtonCount = Math.min(sourceButtons.remaining(), buttons.length);
                    for (int index = 0; index < actualAxisCount; index++) axes[index] = sourceAxes.get(index);
                    for (int index = 0; index < actualButtonCount; index++) buttons[index] = sourceButtons.get(index) == GLFW.GLFW_PRESS;
                    readGamepad = true;
                }
            } finally {
                state.free();
            }
        }
        if (!readGamepad) {
            FloatBuffer sourceAxes = GLFW.glfwGetJoystickAxes(connectedJoystick);
            ByteBuffer sourceButtons = GLFW.glfwGetJoystickButtons(connectedJoystick);
            actualAxisCount = sourceAxes == null ? 0 : Math.min(sourceAxes.remaining(), axes.length);
            actualButtonCount = sourceButtons == null ? 0 : Math.min(sourceButtons.remaining(), buttons.length);
            if (sourceAxes != null) {
                for (int index = 0; index < actualAxisCount; index++) axes[index] = sourceAxes.get(index);
            }
            if (sourceButtons != null) {
                for (int index = 0; index < actualButtonCount; index++) buttons[index] = sourceButtons.get(index) == GLFW.GLFW_PRESS;
            }
        }
    }

    private float processedAxis(int axis, boolean invert) {
        if (axis < 0 || axis >= actualAxisCount) return 0.0F;
        ControllerConfig config = FpvClientConfig.controller();
        float minimum = config.axisMin(axis);
        float maximum = config.axisMax(axis);
        if (maximum - minimum < 0.001F) {
            minimum = -1.0F;
            maximum = 1.0F;
        }
        float normalized = (axes[axis] - minimum) / (maximum - minimum) * 2.0F - 1.0F;
        normalized = Mth.clamp(invert ? -normalized : normalized, -1.0F, 1.0F);
        float deadzone = config.deadzone();
        if (Math.abs(normalized) < deadzone) return 0.0F;
        return Math.copySign((Math.abs(normalized) - deadzone) / (1.0F - deadzone), normalized);
    }

    public boolean consumeArmToggle() {
        if (!isPhysicalControllerConnected() || FpvClientConfig.forceKeyboardMouse()) {
            lastArmState = false;
            return false;
        }
        ControllerConfig config = FpvClientConfig.controller();
        boolean state;
        int channel = config.armChannel();
        if (config.armIsButton()) {
            state = channel < actualButtonCount && buttons[channel];
        } else {
            state = channel < actualAxisCount && axes[channel] > 0.5F;
        }
        if (config.invertArm()) state = !state;
        boolean toggled = state && !lastArmState;
        lastArmState = state;
        return toggled;
    }

    public List<Device> availableDevices() {
        List<Device> devices = new ArrayList<>();
        for (int jid = GLFW.GLFW_JOYSTICK_1; jid <= GLFW.GLFW_JOYSTICK_LAST; jid++) {
            if (!GLFW.glfwJoystickPresent(jid)) continue;
            String name = GLFW.glfwGetJoystickName(jid);
            String guid = GLFW.glfwGetJoystickGUID(jid);
            devices.add(new Device(jid, name == null ? "Controller " + (jid + 1) : name, guid == null ? "" : guid,
                    GLFW.glfwJoystickIsGamepad(jid)));
        }
        return List.copyOf(devices);
    }

    public void selectDevice(@Nullable Device device) {
        connectedJoystick = device == null ? -1 : device.jid();
        actualAxisCount = 0;
        actualButtonCount = 0;
        lastArmState = false;
        ControllerConfig config = FpvClientConfig.controller();
        config.setPreferredJoystickGuid(device == null ? "" : device.guid());
        config.setPreferredJoystickName(device == null ? "" : device.name());
        FpvClientConfig.saveController();
    }

    public @Nullable Device selectedDevice() {
        if (connectedJoystick < 0 || !GLFW.glfwJoystickPresent(connectedJoystick)) return null;
        String name = GLFW.glfwGetJoystickName(connectedJoystick);
        String guid = GLFW.glfwGetJoystickGUID(connectedJoystick);
        return new Device(connectedJoystick, name == null ? "Controller " + (connectedJoystick + 1) : name,
                guid == null ? "" : guid, GLFW.glfwJoystickIsGamepad(connectedJoystick));
    }

    public boolean isPhysicalControllerConnected() {
        return connectedJoystick >= 0 && GLFW.glfwJoystickPresent(connectedJoystick);
    }

    public int axisCount() { return actualAxisCount; }
    public int buttonCount() { return actualButtonCount; }
    public float rawAxis(int axis) { return axis >= 0 && axis < actualAxisCount ? axes[axis] : 0.0F; }
    public boolean button(int button) { return button >= 0 && button < actualButtonCount && buttons[button]; }
    public float throttle() { return throttle; }
    public float yaw() { return yaw; }
    public float pitch() { return pitch; }
    public float roll() { return roll; }

    public void resetKeyboard() {
        keyboardThrottle = -1.0F;
        keyboardYaw = 0.0F;
        pitch = 0.0F;
        roll = 0.0F;
        cursorInitialized = false;
    }

    public void beginRangeCapture() {
        Arrays.fill(rangeMinimum, Float.POSITIVE_INFINITY);
        Arrays.fill(rangeMaximum, Float.NEGATIVE_INFINITY);
        rangeCapture = true;
    }

    public int finishRangeCapture() {
        rangeCapture = false;
        int calibrated = 0;
        ControllerConfig config = FpvClientConfig.controller();
        for (int axis = 0; axis < ControllerConfig.AXIS_COUNT; axis++) {
            if (Float.isFinite(rangeMinimum[axis]) && Float.isFinite(rangeMaximum[axis])
                    && rangeMaximum[axis] - rangeMinimum[axis] >= 0.20F) {
                config.setAxisRange(axis, rangeMinimum[axis], rangeMaximum[axis]);
                calibrated++;
            }
        }
        FpvClientConfig.saveController();
        return calibrated;
    }

    public boolean isCapturingRange() {
        return rangeCapture;
    }

    public void beginInputCapture() {
        System.arraycopy(axes, 0, captureAxes, 0, axes.length);
        System.arraycopy(buttons, 0, captureButtons, 0, buttons.length);
        inputCapture = true;
    }

    public @Nullable DetectedInput detectInput() {
        if (!inputCapture) return null;
        for (int button = 0; button < actualButtonCount; button++) {
            if (buttons[button] && !captureButtons[button]) {
                inputCapture = false;
                return new DetectedInput(button, true, false);
            }
        }
        int bestAxis = -1;
        float bestDelta = 0.55F;
        for (int axis = 0; axis < actualAxisCount; axis++) {
            float delta = axes[axis] - captureAxes[axis];
            if (Math.abs(delta) > Math.abs(bestDelta)) {
                bestAxis = axis;
                bestDelta = delta;
            }
        }
        if (bestAxis >= 0) {
            inputCapture = false;
            return new DetectedInput(bestAxis, false, bestDelta < 0.0F);
        }
        return null;
    }

    public void cancelCapture() {
        inputCapture = false;
        rangeCapture = false;
    }

    private static float moveToward(float value, float target, float maximumDelta) {
        float difference = target - value;
        return Math.abs(difference) <= maximumDelta ? target : value + Math.copySign(maximumDelta, difference);
    }

    public record Device(int jid, String name, String guid, boolean gamepad) {
        public String label() {
            return name + (gamepad ? " (gamepad)" : " (joystick)");
        }
    }

    public record DetectedInput(int index, boolean button, boolean negative) {
    }
}
