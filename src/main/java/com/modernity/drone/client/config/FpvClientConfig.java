package com.modernity.drone.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.modernity.drone.DroneMod;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Client-side settings store. Controller and rate files retain the 1.1.4
 * filenames and schema so player calibration survives the port.
 */
public final class FpvClientConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final ControllerConfig CONTROLLER = new ControllerConfig();
    private static final DroneBuildConfig BUILD = new DroneBuildConfig();

    private static boolean loaded;
    private static boolean setupComplete;
    private static boolean forceKeyboardMouse;
    private static boolean stickOverlay;
    private static float cameraAngle = 25.0F;
    private static float fov = 120.0F;
    private static VideoResolution videoResolution = VideoResolution.RES_25;

    private FpvClientConfig() {
    }

    public static synchronized void initialize() {
        if (loaded) {
            return;
        }
        loaded = true;
        loadController();
        loadRates();
        loadClient();
        loadBuild();
    }

    public static ControllerConfig controller() {
        initialize();
        return CONTROLLER;
    }

    public static DroneBuildConfig build() {
        initialize();
        return BUILD;
    }

    public static boolean setupComplete() {
        initialize();
        return setupComplete;
    }

    public static void setSetupComplete(boolean value) {
        setupComplete = value;
        saveClient();
    }

    public static boolean forceKeyboardMouse() {
        initialize();
        return forceKeyboardMouse;
    }

    public static void setForceKeyboardMouse(boolean value) {
        forceKeyboardMouse = value;
        saveClient();
    }

    public static boolean stickOverlay() {
        initialize();
        return stickOverlay;
    }

    public static void setStickOverlay(boolean value) {
        stickOverlay = value;
        saveClient();
    }

    public static float cameraAngle() {
        initialize();
        return cameraAngle;
    }

    public static void setCameraAngle(float value) {
        // The settings slider exposes 0..90, while V1.1.4's in-flight
        // PageUp/PageDown adjustment deliberately permits -60..60.
        cameraAngle = clamp(value, -60.0F, 90.0F, 25.0F);
        saveClient();
    }

    public static float fov() {
        initialize();
        return fov;
    }

    public static void setFov(float value) {
        fov = clamp(value, 60.0F, 180.0F, 120.0F);
        saveClient();
    }

    public static VideoResolution videoResolution() {
        initialize();
        return videoResolution;
    }

    public static VideoResolution cycleVideoResolution() {
        videoResolution = videoResolution.next();
        saveClient();
        return videoResolution;
    }

    public static void setVideoResolution(VideoResolution value) {
        videoResolution = value == null ? VideoResolution.RES_25 : value;
        saveClient();
    }

    public static synchronized void saveController() {
        initialize();
        JsonObject root = new JsonObject();
        JsonObject channels = new JsonObject();
        channels.addProperty("throttle", CONTROLLER.channel(ControllerConfig.THROTTLE));
        channels.addProperty("yaw", CONTROLLER.channel(ControllerConfig.YAW));
        channels.addProperty("pitch", CONTROLLER.channel(ControllerConfig.PITCH));
        channels.addProperty("roll", CONTROLLER.channel(ControllerConfig.ROLL));
        channels.addProperty("armButton", CONTROLLER.armChannel());
        channels.addProperty("armChannel", CONTROLLER.armChannel());
        root.add("channels", channels);

        JsonObject arm = new JsonObject();
        arm.addProperty("channel", CONTROLLER.armChannel());
        arm.addProperty("isButton", CONTROLLER.armIsButton());
        arm.addProperty("invert", CONTROLLER.invertArm());
        root.add("arm", arm);

        JsonObject inversion = new JsonObject();
        inversion.addProperty("throttle", CONTROLLER.inverted(ControllerConfig.THROTTLE));
        inversion.addProperty("yaw", CONTROLLER.inverted(ControllerConfig.YAW));
        inversion.addProperty("pitch", CONTROLLER.inverted(ControllerConfig.PITCH));
        inversion.addProperty("roll", CONTROLLER.inverted(ControllerConfig.ROLL));
        root.add("inversion", inversion);
        root.addProperty("deadzone", CONTROLLER.deadzone());

        JsonArray ranges = new JsonArray();
        for (float value : CONTROLLER.axisRanges()) {
            ranges.add(value);
        }
        root.add("axisRange", ranges);

        JsonObject device = new JsonObject();
        device.addProperty("guid", CONTROLLER.preferredJoystickGuid());
        device.addProperty("name", CONTROLLER.preferredJoystickName());
        root.add("device", device);
        write("controller.json", root);
    }

    public static synchronized void saveRates() {
        initialize();
        JsonObject root = new JsonObject();
        root.add("rcRate", rateGroup(CONTROLLER::rcRate));
        root.add("superRate", rateGroup(CONTROLLER::superRate));
        root.add("expo", rateGroup(CONTROLLER::expo));
        write("rates.json", root);
    }

    public static synchronized void saveBuild() {
        initialize();
        JsonObject root = new JsonObject();
        root.addProperty("mass", BUILD.massGrams());
        root.addProperty("kv", BUILD.motorKv());
        root.addProperty("motorWidth", BUILD.motorWidthMm());
        root.addProperty("motorHeight", BUILD.motorHeightMm());
        root.addProperty("cells", BUILD.batteryCells());
        root.addProperty("diameter", BUILD.propDiameterInches());
        root.addProperty("pitch", BUILD.propPitchInches());
        root.addProperty("blades", BUILD.propBlades());
        root.addProperty("frameWidth", BUILD.frameWidthMm());
        root.addProperty("frameHeight", BUILD.frameHeightMm());
        root.addProperty("frameLength", BUILD.frameLengthMm());
        root.addProperty("showProCam", BUILD.showProCamera());
        root.addProperty("isHeroCam", BUILD.heroCamera());
        root.addProperty("isToothpick", BUILD.toothpick());
        root.addProperty("red", BUILD.red());
        root.addProperty("green", BUILD.green());
        root.addProperty("blue", BUILD.blue());
        write("build.json", root);
    }

    private static synchronized void saveClient() {
        if (!loaded) {
            return;
        }
        JsonObject root = new JsonObject();
        root.addProperty("setupComplete", setupComplete);
        root.addProperty("forceKbm", forceKeyboardMouse);
        root.addProperty("stickOverlay", stickOverlay);
        root.addProperty("defaultAngle", cameraAngle);
        root.addProperty("fov", fov);
        root.addProperty("videoResolution", videoResolution.name());
        write("client.json", root);
    }

    private static void loadController() {
        JsonObject root = read("controller.json");
        if (root == null) {
            return;
        }
        try {
            JsonObject channels = object(root, "channels");
            if (channels != null) {
                setInt(channels, "throttle", value -> CONTROLLER.setChannel(ControllerConfig.THROTTLE, value));
                setInt(channels, "yaw", value -> CONTROLLER.setChannel(ControllerConfig.YAW, value));
                setInt(channels, "pitch", value -> CONTROLLER.setChannel(ControllerConfig.PITCH, value));
                setInt(channels, "roll", value -> CONTROLLER.setChannel(ControllerConfig.ROLL, value));
                setInt(channels, "armChannel", CONTROLLER::setArmChannel);
                if (!channels.has("armChannel")) {
                    setInt(channels, "armButton", CONTROLLER::setArmChannel);
                }
            }
            JsonObject arm = object(root, "arm");
            if (arm != null) {
                setInt(arm, "channel", CONTROLLER::setArmChannel);
                setBoolean(arm, "isButton", CONTROLLER::setArmIsButton);
                setBoolean(arm, "invert", CONTROLLER::setInvertArm);
            }
            JsonObject inversion = object(root, "inversion");
            if (inversion != null) {
                setBoolean(inversion, "throttle", value -> CONTROLLER.setInverted(ControllerConfig.THROTTLE, value));
                setBoolean(inversion, "yaw", value -> CONTROLLER.setInverted(ControllerConfig.YAW, value));
                setBoolean(inversion, "pitch", value -> CONTROLLER.setInverted(ControllerConfig.PITCH, value));
                setBoolean(inversion, "roll", value -> CONTROLLER.setInverted(ControllerConfig.ROLL, value));
            }
            if (root.has("deadzone")) {
                CONTROLLER.setDeadzone(root.get("deadzone").getAsFloat());
            }
            if (root.has("axisRange")) {
                JsonArray values = root.getAsJsonArray("axisRange");
                if (values.size() == ControllerConfig.AXIS_COUNT * 2) {
                    float[] ranges = new float[values.size()];
                    for (int index = 0; index < values.size(); index++) {
                        ranges[index] = values.get(index).getAsFloat();
                    }
                    CONTROLLER.setAxisRanges(ranges);
                }
            }
            JsonObject device = object(root, "device");
            if (device != null) {
                if (device.has("guid")) CONTROLLER.setPreferredJoystickGuid(device.get("guid").getAsString());
                if (device.has("name")) CONTROLLER.setPreferredJoystickName(device.get("name").getAsString());
            }
        } catch (RuntimeException exception) {
            DroneMod.LOGGER.warn("Could not load FPV controller settings", exception);
        }
    }

    private static void loadRates() {
        JsonObject root = read("rates.json");
        if (root == null) {
            return;
        }
        try {
            loadRateGroup(root, "rcRate", CONTROLLER::setRcRate);
            loadRateGroup(root, "superRate", CONTROLLER::setSuperRate);
            loadRateGroup(root, "expo", CONTROLLER::setExpo);
        } catch (RuntimeException exception) {
            DroneMod.LOGGER.warn("Could not load FPV rate profile", exception);
        }
    }

    private static void loadClient() {
        JsonObject root = read("client.json");
        if (root == null) {
            // Import the compatible client-facing keys from the old combined file.
            root = read("fpvdrone.json");
        }
        if (root == null) {
            return;
        }
        try {
            setupComplete = booleanOr(root, "setupComplete", setupComplete);
            forceKeyboardMouse = booleanOr(root, "forceKbm", forceKeyboardMouse);
            stickOverlay = booleanOr(root, "stickOverlay", stickOverlay);
            cameraAngle = clamp(floatOr(root, "defaultAngle", cameraAngle), -60.0F, 90.0F, cameraAngle);
            fov = clamp(floatOr(root, "fov", fov), 60.0F, 180.0F, fov);
            if (root.has("videoResolution")) {
                videoResolution = VideoResolution.parse(root.get("videoResolution").getAsString());
            }
        } catch (RuntimeException exception) {
            DroneMod.LOGGER.warn("Could not load FPV client settings", exception);
        }
    }

    private static void loadBuild() {
        JsonObject root = read("build.json");
        if (root == null) {
            return;
        }
        try {
            if (root.has("mass")) BUILD.setMassGrams(root.get("mass").getAsFloat());
            if (root.has("kv")) BUILD.setMotorKv(root.get("kv").getAsInt());
            if (root.has("motorWidth")) BUILD.setMotorWidthMm(root.get("motorWidth").getAsFloat());
            if (root.has("motorHeight")) BUILD.setMotorHeightMm(root.get("motorHeight").getAsFloat());
            if (root.has("cells")) BUILD.setBatteryCells(root.get("cells").getAsInt());
            if (root.has("diameter")) BUILD.setPropDiameterInches(root.get("diameter").getAsFloat());
            if (root.has("pitch")) BUILD.setPropPitchInches(root.get("pitch").getAsFloat());
            if (root.has("blades")) BUILD.setPropBlades(root.get("blades").getAsInt());
            if (root.has("frameWidth")) BUILD.setFrameWidthMm(root.get("frameWidth").getAsFloat());
            if (root.has("frameHeight")) BUILD.setFrameHeightMm(root.get("frameHeight").getAsFloat());
            if (root.has("frameLength")) BUILD.setFrameLengthMm(root.get("frameLength").getAsFloat());
            BUILD.setShowProCamera(booleanOr(root, "showProCam", BUILD.showProCamera()));
            BUILD.setHeroCamera(booleanOr(root, "isHeroCam", BUILD.heroCamera()));
            BUILD.setToothpick(booleanOr(root, "isToothpick", BUILD.toothpick()));
            if (root.has("red")) BUILD.setRed(root.get("red").getAsInt());
            if (root.has("green")) BUILD.setGreen(root.get("green").getAsInt());
            if (root.has("blue")) BUILD.setBlue(root.get("blue").getAsInt());
        } catch (RuntimeException exception) {
            DroneMod.LOGGER.warn("Could not load FPV build settings", exception);
        }
    }

    private static JsonObject rateGroup(java.util.function.IntFunction<Float> getter) {
        JsonObject values = new JsonObject();
        values.addProperty("throttle", getter.apply(ControllerConfig.THROTTLE));
        values.addProperty("yaw", getter.apply(ControllerConfig.YAW));
        values.addProperty("pitch", getter.apply(ControllerConfig.PITCH));
        values.addProperty("roll", getter.apply(ControllerConfig.ROLL));
        return values;
    }

    private static void loadRateGroup(JsonObject root, String key, AxisValueSetter setter) {
        JsonObject group = object(root, key);
        if (group == null) return;
        if (group.has("throttle")) setter.set(ControllerConfig.THROTTLE, group.get("throttle").getAsFloat());
        if (group.has("yaw")) setter.set(ControllerConfig.YAW, group.get("yaw").getAsFloat());
        if (group.has("pitch")) setter.set(ControllerConfig.PITCH, group.get("pitch").getAsFloat());
        if (group.has("roll")) setter.set(ControllerConfig.ROLL, group.get("roll").getAsFloat());
    }

    private static JsonObject read(String fileName) {
        try {
            Path file = configDirectory().resolve(fileName);
            if (!Files.isRegularFile(file)) return null;
            JsonElement parsed = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        } catch (Exception exception) {
            DroneMod.LOGGER.warn("Could not read FPV settings file {}", fileName, exception);
            return null;
        }
    }

    private static void write(String fileName, JsonObject value) {
        try {
            Path directory = configDirectory();
            Files.createDirectories(directory);
            Files.writeString(directory.resolve(fileName), GSON.toJson(value), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            DroneMod.LOGGER.error("Could not save FPV settings file {}", fileName, exception);
        }
    }

    private static Path configDirectory() {
        return FMLPaths.CONFIGDIR.get().resolve("fpvdrone");
    }

    private static JsonObject object(JsonObject root, String name) {
        JsonElement value = root.get(name);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }

    private static boolean booleanOr(JsonObject root, String name, boolean fallback) {
        return root.has(name) ? root.get(name).getAsBoolean() : fallback;
    }

    private static float floatOr(JsonObject root, String name, float fallback) {
        return root.has(name) ? root.get(name).getAsFloat() : fallback;
    }

    private static void setInt(JsonObject root, String name, java.util.function.IntConsumer setter) {
        if (root.has(name)) setter.accept(root.get(name).getAsInt());
    }

    private static void setBoolean(JsonObject root, String name, java.util.function.Consumer<Boolean> setter) {
        if (root.has(name)) setter.accept(root.get(name).getAsBoolean());
    }

    private static float clamp(float value, float minimum, float maximum, float fallback) {
        return Float.isFinite(value) ? Math.max(minimum, Math.min(maximum, value)) : fallback;
    }

    @FunctionalInterface
    private interface AxisValueSetter {
        void set(int axis, float value);
    }

    public enum VideoResolution {
        RES_10(0.10F, "10%"),
        RES_15(0.15F, "15%"),
        RES_20(0.20F, "20%"),
        RES_25(0.25F, "25%"),
        RES_33(0.33F, "33%"),
        RES_50(0.50F, "50%");

        private final float scale;
        private final String displayName;

        VideoResolution(float scale, String displayName) {
            this.scale = scale;
            this.displayName = displayName;
        }

        public float scale() { return scale; }
        public String displayName() { return displayName; }

        public VideoResolution next() {
            VideoResolution[] values = values();
            return values[(ordinal() + 1) % values.length];
        }

        static VideoResolution parse(String value) {
            try {
                return valueOf(value);
            } catch (IllegalArgumentException ignored) {
                return RES_25;
            }
        }
    }
}
