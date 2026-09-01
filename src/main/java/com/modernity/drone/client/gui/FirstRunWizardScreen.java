package com.modernity.drone.client.gui;

import com.modernity.drone.client.config.ControllerConfig;
import com.modernity.drone.client.config.FpvClientConfig;
import com.modernity.drone.client.input.ControllerReader;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** First-run calibration flow compatible with the original mod's setup order. */
public final class FirstRunWizardScreen extends FpvScreen {
    private final ControllerReader reader = ControllerReader.get();
    private Page page = Page.WELCOME;
    private String status = "";
    private boolean mappingDetected;

    public FirstRunWizardScreen(@Nullable Screen previous) {
        super(Component.literal("Welcome to Minecraft FPV"), previous);
    }

    @Override
    protected void init() {
        int x = centerX() - 110;
        switch (page) {
            case WELCOME -> {
                addRenderableWidget(Button.builder(Component.literal("I have a radio controller"), button -> show(Page.DEVICE))
                        .bounds(x, 92, 220, 20).build());
                addRenderableWidget(Button.builder(Component.literal("Use keyboard & mouse"), button -> {
                    FpvClientConfig.setForceKeyboardMouse(true);
                    show(Page.COMPLETE);
                }).bounds(x, 120, 220, 20).build());
            }
            case DEVICE -> addDeviceButtons(x);
            case CENTER -> addRenderableWidget(Button.builder(Component.literal("Sticks centered — continue"), button -> {
                show(Page.MAP_THROTTLE);
                reader.beginInputCapture();
            }).bounds(x, 118, 220, 20).build());
            case MAP_THROTTLE, MAP_YAW, MAP_PITCH, MAP_ROLL -> {
                if (mappingDetected) {
                    addRenderableWidget(Button.builder(Component.literal("Stick centered — continue"), button -> nextMappingPage())
                            .bounds(x, 118, 220, 20).build());
                }
            }
            case RANGE -> addRenderableWidget(Button.builder(Component.literal("Finish stick calibration"), button -> {
                int calibrated = reader.finishRangeCapture();
                show(Page.VERIFY);
                status = calibrated + " axes calibrated";
            }).bounds(x, 118, 220, 20).build());
            case VERIFY -> addRenderableWidget(Button.builder(Component.literal("Controls look correct — continue"), button -> show(Page.ARM))
                    .bounds(x, 118, 220, 20).build());
            case ARM -> addRenderableWidget(Button.builder(Component.literal("Detect arm switch / button"), button -> {
                status = "Move the arm switch or press its button now";
                reader.beginInputCapture();
            }).bounds(x, 118, 220, 20).build());
            case COMPLETE -> addRenderableWidget(Button.builder(Component.literal("Done"), button -> finish())
                    .bounds(x, 118, 220, 20).build());
        }
        if (page != Page.WELCOME && page != Page.COMPLETE) {
            addRenderableWidget(Button.builder(Component.literal("Back"), button -> goBack())
                    .bounds(centerX() - 75, height - 34, 150, 20).build());
        }
    }

    private void addDeviceButtons(int x) {
        List<ControllerReader.Device> devices = reader.availableDevices();
        if (devices.isEmpty()) {
            status = "No controller detected. Connect it, then retry.";
            addRenderableWidget(Button.builder(Component.literal("Retry"), button -> rebuild())
                    .bounds(x, 112, 106, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Use keyboard"), button -> {
                FpvClientConfig.setForceKeyboardMouse(true);
                show(Page.COMPLETE);
            }).bounds(x + 114, 112, 106, 20).build());
            return;
        }
        int shown = Math.min(4, devices.size());
        for (int index = 0; index < shown; index++) {
            ControllerReader.Device device = devices.get(index);
            addRenderableWidget(Button.builder(Component.literal(device.label()), button -> {
                reader.selectDevice(device);
                FpvClientConfig.setForceKeyboardMouse(false);
                show(Page.CENTER);
            }).bounds(x, 68 + index * 25, 220, 20).build());
        }
    }

    @Override
    public void tick() {
        if (isMappingPage() && !mappingDetected) {
            ControllerReader.DetectedInput input = reader.detectInput();
            if (input == null) return;
            if (input.button()) {
                status = "Move a stick axis, not a button";
                reader.beginInputCapture();
                return;
            }
            int function = switch (page) {
                case MAP_THROTTLE -> ControllerConfig.THROTTLE;
                case MAP_YAW -> ControllerConfig.YAW;
                case MAP_PITCH -> ControllerConfig.PITCH;
                case MAP_ROLL -> ControllerConfig.ROLL;
                default -> throw new IllegalStateException("Not mapping an axis");
            };
            ControllerConfig config = FpvClientConfig.controller();
            config.setChannel(function, input.index());
            config.setInverted(function, input.negative());
            FpvClientConfig.saveController();
            mappingDetected = true;
            status = "Axis " + (input.index() + 1) + " detected; recenter the stick";
            rebuild();
            return;
        }
        if (page != Page.ARM) return;
        ControllerReader.DetectedInput input = reader.detectInput();
        if (input == null) return;
        ControllerConfig config = FpvClientConfig.controller();
        config.setArmChannel(input.index());
        config.setArmIsButton(input.button());
        config.setInvertArm(!input.button() && input.negative());
        FpvClientConfig.saveController();
        show(Page.COMPLETE);
        status = "Arm input saved";
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, heading(), centerX(), 48, TEXT);
        graphics.centeredText(font, hint(), centerX(), 62, MUTED);
        if (!status.isBlank()) graphics.centeredText(font, status, centerX(), 150, ACCENT);
        if (page == Page.CENTER || isMappingPage() || page == Page.RANGE || page == Page.VERIFY || page == Page.ARM) {
            drawSticks(graphics, centerX(), 173);
        }
    }

    private void drawSticks(GuiGraphicsExtractor graphics, int center, int top) {
        int size = 42;
        drawStick(graphics, center - 58, top, size, reader.yaw(), reader.throttle());
        drawStick(graphics, center + 16, top, size, reader.roll(), -reader.pitch());
    }

    private static void drawStick(GuiGraphicsExtractor graphics, int x, int y, int size, float horizontal, float vertical) {
        graphics.fill(x, y, x + size, y + size, 0xB0080D0B);
        graphics.fill(x, y, x + size, y + 1, PANEL_EDGE);
        graphics.fill(x, y + size - 1, x + size, y + size, PANEL_EDGE);
        graphics.fill(x + size / 2, y, x + size / 2 + 1, y + size, 0x505C7567);
        graphics.fill(x, y + size / 2, x + size, y + size / 2 + 1, 0x505C7567);
        int px = x + size / 2 + Math.round(horizontal * (size / 2 - 3));
        int py = y + size / 2 - Math.round(vertical * (size / 2 - 3));
        graphics.fill(px - 2, py - 2, px + 3, py + 3, ACCENT);
    }

    private String heading() {
        return switch (page) {
            case WELCOME -> "Choose your setup path";
            case DEVICE -> "Choose a controller";
            case CENTER -> "Center both sticks";
            case MAP_THROTTLE -> "Push the left stick up";
            case MAP_YAW -> "Push the left stick right";
            case MAP_PITCH -> "Push the right stick up";
            case MAP_ROLL -> "Push the right stick right";
            case RANGE -> "Move every stick through its full range";
            case VERIFY -> "Verify the mapped controls";
            case ARM -> "Choose the arm control";
            case COMPLETE -> "Setup complete — you're ready to fly";
        };
    }

    private String hint() {
        return switch (page) {
            case WELCOME -> "Radio transmitters, gamepads, and keyboard/mouse are supported";
            case DEVICE -> "The controller GUID is saved, so reconnect order does not matter";
            case CENTER -> "Release the sticks before continuing";
            case MAP_THROTTLE, MAP_YAW, MAP_PITCH, MAP_ROLL -> mappingDetected
                    ? "Return the stick to center"
                    : "The moved axis and its direction are detected automatically";
            case RANGE -> "Sweep both sticks in circles, then finish";
            case VERIFY -> "Both boxes below show the live calibrated stick positions";
            case ARM -> "A button or a two-position axis may be used";
            case COMPLETE -> FpvClientConfig.forceKeyboardMouse()
                    ? "W/S throttle · A/D yaw · mouse pitch/roll · N arm"
                    : "Use the calibrated sticks and arm switch";
        };
    }

    private Page previousPage() {
        return switch (page) {
            case DEVICE -> Page.WELCOME;
            case CENTER -> Page.DEVICE;
            case MAP_THROTTLE -> Page.CENTER;
            case MAP_YAW -> Page.MAP_THROTTLE;
            case MAP_PITCH -> Page.MAP_YAW;
            case MAP_ROLL -> Page.MAP_PITCH;
            case RANGE -> Page.MAP_ROLL;
            case VERIFY -> Page.RANGE;
            case ARM -> Page.VERIFY;
            default -> Page.WELCOME;
        };
    }

    private boolean isMappingPage() {
        return page == Page.MAP_THROTTLE
                || page == Page.MAP_YAW
                || page == Page.MAP_PITCH
                || page == Page.MAP_ROLL;
    }

    private void goBack() {
        Page previous = previousPage();
        show(previous);
        if (isMappingPage()) reader.beginInputCapture();
        else if (page == Page.RANGE) reader.beginRangeCapture();
    }

    private void nextMappingPage() {
        Page next = switch (page) {
            case MAP_THROTTLE -> Page.MAP_YAW;
            case MAP_YAW -> Page.MAP_PITCH;
            case MAP_PITCH -> Page.MAP_ROLL;
            case MAP_ROLL -> Page.RANGE;
            default -> throw new IllegalStateException("Not mapping an axis");
        };
        show(next);
        if (next == Page.RANGE) reader.beginRangeCapture();
        else reader.beginInputCapture();
    }

    private void show(Page next) {
        reader.cancelCapture();
        page = next;
        mappingDetected = false;
        status = "";
        rebuild();
    }

    private void finish() {
        FpvClientConfig.setSetupComplete(true);
        FpvClientConfig.saveController();
        FpvClientConfig.saveRates();
        returnToPrevious();
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    @Override
    public void removed() {
        reader.cancelCapture();
    }

    private enum Page {
        WELCOME, DEVICE, CENTER,
        MAP_THROTTLE, MAP_YAW, MAP_PITCH, MAP_ROLL,
        RANGE, VERIFY, ARM, COMPLETE
    }
}
