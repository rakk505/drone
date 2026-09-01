package com.modernity.drone.client.osd;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Local persistence and import/export support for per-drone OSD layouts. */
public final class OsdLayoutStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(OsdLayoutStore.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter EXPORT_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final Map<UUID, OsdLayout> CACHE = new ConcurrentHashMap<>();

    private OsdLayoutStore() {
    }

    public static OsdLayout get(UUID droneId) {
        return CACHE.computeIfAbsent(droneId, OsdLayoutStore::load);
    }

    public static void put(UUID droneId, OsdLayout layout) {
        CACHE.put(droneId, layout.copy());
    }

    public static void save(UUID droneId, OsdLayout layout) {
        put(droneId, layout);
        try {
            Path target = layoutDirectory().resolve(droneId + ".json");
            Files.createDirectories(target.getParent());
            Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
            Files.writeString(temporary, encode(layout));
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            LOGGER.warn("Unable to save OSD layout for {}", droneId, exception);
        }
    }

    public static OsdLayout load(UUID droneId) {
        Path source = layoutDirectory().resolve(droneId + ".json");
        if (!Files.isRegularFile(source)) {
            return OsdLayout.defaults();
        }
        try {
            return decode(Files.readString(source));
        } catch (Exception exception) {
            LOGGER.warn("Unable to load OSD layout from {}", source, exception);
            return OsdLayout.defaults();
        }
    }

    /** Writes the same portable JSON used by the 1.1.4 OSD import/export workflow. */
    public static Path exportLayout(OsdLayout layout) throws IOException {
        return exportLayout(layout, "DRONE");
    }

    public static Path exportLayout(OsdLayout layout, String craftName) throws IOException {
        Path directory = exportDirectory();
        Files.createDirectories(directory);
        String safeName = craftName == null || craftName.isBlank()
                ? "DRONE"
                : craftName.replaceAll("[^a-zA-Z0-9_\\-]", "_").trim();
        Path target = directory.resolve(safeName + "_" + EXPORT_STAMP.format(LocalDateTime.now()) + ".json");
        Files.writeString(target, encode(layout));
        return target;
    }

    public static OsdLayout importLayout(Path source) throws IOException {
        return decode(Files.readString(source));
    }

    public static java.util.List<Path> listExports() {
        Path directory = exportDirectory();
        if (!Files.isDirectory(directory)) {
            return java.util.List.of();
        }
        try (var paths = Files.list(directory)) {
            return paths.filter(path -> path.getFileName().toString().endsWith(".json")).sorted().toList();
        } catch (IOException ignored) {
            return java.util.List.of();
        }
    }

    public static String encode(OsdLayout layout) {
        JsonObject root = new JsonObject();
        root.addProperty("gridCols", OsdLayout.GRID_COLUMNS);
        root.addProperty("gridRows", OsdLayout.GRID_ROWS);
        JsonArray elements = new JsonArray();
        for (OsdElement element : layout.all()) {
            JsonObject json = new JsonObject();
            json.addProperty("name", element.id());
            json.addProperty("x", element.x());
            json.addProperty("y", element.y());
            json.addProperty("visible", element.visible());
            if (element.customText() != null) {
                json.addProperty("customText", element.customText());
            }
            elements.add(json);
        }
        root.add("elements", elements);
        return GSON.toJson(root);
    }

    public static OsdLayout decode(String jsonText) {
        OsdLayout layout = new OsdLayout();
        JsonObject root = JsonParser.parseString(jsonText).getAsJsonObject();
        JsonArray elements = root.has("elements") && root.get("elements").isJsonArray()
                ? root.getAsJsonArray("elements")
                : new JsonArray();
        for (JsonElement value : elements) {
            if (!value.isJsonObject()) {
                continue;
            }
            JsonObject json = value.getAsJsonObject();
            if (!json.has("name")) {
                continue;
            }
            String id = json.get("name").getAsString();
            if (OsdElementRegistry.get(id) == null) {
                continue;
            }
            float x = number(json, "x", OsdElementRegistry.get(id).defaultX());
            float y = number(json, "y", OsdElementRegistry.get(id).defaultY());
            boolean visible = json.has("visible") && json.get("visible").getAsBoolean();
            String custom = json.has("customText") ? json.get("customText").getAsString() : null;
            layout.put(new OsdElement(id, x, y, visible, custom));
        }
        layout.resetMissingToDefaults();
        return layout;
    }

    public static void invalidate(UUID droneId) {
        CACHE.remove(droneId);
    }

    public static void clearCache() {
        CACHE.clear();
    }

    public static Path layoutDirectory() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config/drone/osd_layouts");
    }

    public static Path exportDirectory() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("cubeflight/osd_layouts");
    }

    private static float number(JsonObject json, String key, float fallback) {
        try {
            return json.has(key) ? json.get(key).getAsFloat() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }
}
