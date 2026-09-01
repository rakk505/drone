package com.modernity.drone.client.gui;

import com.modernity.drone.client.config.ControllerConfig;
import com.modernity.drone.client.config.FpvClientConfig;
import com.modernity.drone.client.input.ControllerReader;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** Device selection, channel mapping, inversion, deadzone, and calibration. */
public final class ControllerSettingsScreen extends FpvScreen {
    private final ControllerReader reader = ControllerReader.get();
    private @Nullable Button deviceButton;
    private @Nullable Button armButton;
    private @Nullable Button rangeButton;
    private boolean waitingForArm;

    public ControllerSettingsScreen(Screen previous) {
        super(Component.literal("Controller & Channel Mapping"), previous);
    }

    @Override
    protected void init() {
        ControllerConfig config = FpvClientConfig.controller();
        int left = centerX() - 155;
        deviceButton = addRenderableWidget(Button.builder(deviceLabel(), button -> cycleDevice())
                .bounds(left, 42, 310, 20).build());
        addRenderableWidget(Button.builder(inputModeLabel(), button -> {
            FpvClientConfig.setForceKeyboardMouse(!FpvClientConfig.forceKeyboardMouse());
            button.setMessage(inputModeLabel());
        }).bounds(left, 68, 152, 20).build());
        armButton = addRenderableWidget(Button.builder(armLabel(), button -> {
            waitingForArm = true;
            reader.beginInputCapture();
            button.setMessage(Component.literal("Move arm switch / press button…"));
        }).bounds(left + 158, 68, 106, 20).build());
        addRenderableWidget(Button.builder(armInvertLabel(), button -> {
            config.setInvertArm(!config.invertArm());
            FpvClientConfig.saveController();
            button.setMessage(armInvertLabel());
        }).bounds(left + 268, 68, 42, 20).build());

        addAxisRow(ControllerConfig.THROTTLE, "Throttle", left, 100);
        addAxisRow(ControllerConfig.YAW, "Yaw", left + 158, 100);
        addAxisRow(ControllerConfig.PITCH, "Pitch", left, 128);
        addAxisRow(ControllerConfig.ROLL, "Roll", left + 158, 128);

        addRenderableWidget(new ValueSlider(left, 160, 310, "Deadzone", config.deadzone(), 0.0, 0.30,
                value -> { config.setDeadzone((float) value); FpvClientConfig.saveController(); },
                value -> String.format(Locale.ROOT, "%.2f", value)));
        rangeButton = addRenderableWidget(Button.builder(rangeLabel(), button -> toggleRangeCapture())
                .bounds(left, 188, 152, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), button -> returnToPrevious())
                .bounds(left + 158, 188, 152, 20).build());
    }

    private void addAxisRow(int function, String name, int x, int y) {
        ControllerConfig config = FpvClientConfig.controller();
        addRenderableWidget(Button.builder(axisLabel(name, function), button -> {
            int count = Math.max(1, Math.min(ControllerConfig.AXIS_COUNT, Math.max(reader.axisCount(), 4)));
            config.setChannel(function, (config.channel(function) + 1) % count);
            FpvClientConfig.saveController();
            button.setMessage(axisLabel(name, function));
        }).bounds(x, y, 106, 20).build());
        addRenderableWidget(Button.builder(invertLabel(function), button -> {
            config.setInverted(function, !config.inverted(function));
            FpvClientConfig.saveController();
            button.setMessage(invertLabel(function));
        }).bounds(x + 110, y, 42, 20).build());
    }

    private void cycleDevice() {
        List<ControllerReader.Device> devices = reader.availableDevices();
        if (devices.isEmpty()) {
            reader.selectDevice(null);
        } else {
            ControllerReader.Device selected = reader.selectedDevice();
            int current = selected == null ? -1 : devices.indexOf(selected);
            reader.selectDevice(devices.get((current + 1) % devices.size()));
        }
        if (deviceButton != null) deviceButton.setMessage(deviceLabel());
    }

    private void toggleRangeCapture() {
        if (reader.isCapturingRange()) {
            int axes = reader.finishRangeCapture();
            if (rangeButton != null) rangeButton.setMessage(Component.literal("Calibrated " + axes + " axes"));
        } else {
            reader.beginRangeCapture();
            if (rangeButton != null) rangeButton.setMessage(Component.literal("Finish range calibration"));
        }
    }

    @Override
    public void tick() {
        if (!waitingForArm) return;
        ControllerReader.DetectedInput input = reader.detectInput();
        if (input == null) return;
        ControllerConfig config = FpvClientConfig.controller();
        config.setArmChannel(input.index());
        config.setArmIsButton(input.button());
        config.setInvertArm(!input.button() && input.negative());
        FpvClientConfig.saveController();
        waitingForArm = false;
        if (armButton != null) armButton.setMessage(armLabel());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int count = Math.min(reader.axisCount(), 4);
        int y = Math.min(height - 24, 216);
        for (int axis = 0; axis < count; axis++) {
            int x = centerX() - 154 + axis * 78;
            int filled = Math.round((reader.rawAxis(axis) + 1.0F) * 37.0F);
            graphics.fill(x, y, x + 74, y + 5, 0xFF26322C);
            graphics.fill(x, y, x + Math.max(1, filled), y + 5, 0xFF68F28B);
            graphics.text(font, "A" + (axis + 1), x, y - 10, MUTED);
        }
    }

    @Override
    public void removed() {
        reader.cancelCapture();
        FpvClientConfig.saveController();
    }

    private Component deviceLabel() {
        ControllerReader.Device device = reader.selectedDevice();
        if (device != null) return Component.literal("Controller: " + device.label());
        String saved = FpvClientConfig.controller().preferredJoystickName();
        return Component.literal(saved.isBlank()
                ? "Controller: none selected"
                : "Controller: " + saved + " (disconnected)");
    }

    private static Component inputModeLabel() {
        return Component.literal(FpvClientConfig.forceKeyboardMouse() ? "Input: Keyboard & Mouse" : "Input: Auto / Radio");
    }

    private static Component axisLabel(String name, int function) {
        return Component.literal(name + ": axis " + (FpvClientConfig.controller().channel(function) + 1));
    }

    private static Component invertLabel(int function) {
        return Component.literal(FpvClientConfig.controller().inverted(function) ? "INV" : "NOR");
    }

    private static Component armLabel() {
        ControllerConfig config = FpvClientConfig.controller();
        return Component.literal("Arm: " + (config.armIsButton() ? "button " : "axis ") + (config.armChannel() + 1));
    }

    private static Component armInvertLabel() {
        return Component.literal(FpvClientConfig.controller().invertArm() ? "INV" : "NOR");
    }

    private Component rangeLabel() {
        return Component.literal(reader.isCapturingRange() ? "Finish range calibration" : "Calibrate stick range");
    }
}
