package com.modernity.drone.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.modernity.drone.DroneMod;
import com.modernity.drone.client.hud.DroneHudOverlay;
import com.modernity.drone.client.render.DroneEntityRenderer;
import com.modernity.drone.client.render.DroneOperatorEntityRenderer;
import com.modernity.drone.client.render.DroneOperatorModel;
import net.minecraft.client.KeyMapping;
import com.modernity.drone.client.render.PayloadEntityRenderer;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

@Mod(value = DroneMod.MOD_ID, dist = Dist.CLIENT)
public final class DroneClient {
    public static final KeyMapping.Category CONTROLS = new KeyMapping.Category(
            Identifier.fromNamespaceAndPath(DroneMod.MOD_ID, "controls")
    );

    public static final KeyMapping EXIT_VIEW = key("key.drone.toggle_view", GLFW.GLFW_KEY_G);
    public static final KeyMapping ARM = key("key.drone.arm", GLFW.GLFW_KEY_N);
    public static final KeyMapping THROTTLE_UP = key("key.drone.throttle_up", GLFW.GLFW_KEY_SPACE);
    public static final KeyMapping THROTTLE_DOWN = key("key.drone.throttle_down", GLFW.GLFW_KEY_LEFT_SHIFT);
    public static final KeyMapping PITCH_FORWARD = key("key.drone.pitch_forward", GLFW.GLFW_KEY_W);
    public static final KeyMapping PITCH_BACK = key("key.drone.pitch_back", GLFW.GLFW_KEY_S);
    public static final KeyMapping ROLL_LEFT = key("key.drone.roll_left", GLFW.GLFW_KEY_A);
    public static final KeyMapping ROLL_RIGHT = key("key.drone.roll_right", GLFW.GLFW_KEY_D);
    public static final KeyMapping YAW_LEFT = key("key.drone.yaw_left", GLFW.GLFW_KEY_Z);
    public static final KeyMapping YAW_RIGHT = key("key.drone.yaw_right", GLFW.GLFW_KEY_X);
    public static final KeyMapping DROP_PAYLOAD = key("key.drone.drop_payload", GLFW.GLFW_KEY_B);
    public static final KeyMapping HOVER = key("key.drone.hover", GLFW.GLFW_KEY_H);
    public static final KeyMapping RETURN_HOME = key("key.drone.return_home", GLFW.GLFW_KEY_P);

    public DroneClient(IEventBus modEventBus, ModContainer container) {
        modEventBus.addListener(this::registerKeyMappings);
        modEventBus.addListener(this::registerLayerDefinitions);
        modEventBus.addListener(this::registerRenderers);
        modEventBus.addListener(this::registerGuiLayers);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    private static KeyMapping key(String translationKey, int glfwKey) {
        return new KeyMapping(
                translationKey,
                KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM,
                glfwKey,
                CONTROLS
        );
    }

    private void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.registerCategory(CONTROLS);
        event.register(EXIT_VIEW);
        event.register(ARM);
        event.register(THROTTLE_UP);
        event.register(THROTTLE_DOWN);
        event.register(PITCH_FORWARD);
        event.register(PITCH_BACK);
        event.register(ROLL_LEFT);
        event.register(ROLL_RIGHT);
        event.register(YAW_LEFT);
        event.register(YAW_RIGHT);
        event.register(DROP_PAYLOAD);
        event.register(HOVER);
        event.register(RETURN_HOME);
    }

    private void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(DroneMod.DRONE_ENTITY.get(), DroneEntityRenderer::new);
        event.registerEntityRenderer(DroneMod.DROPPED_PAYLOAD_ENTITY.get(), PayloadEntityRenderer::new);
        event.registerEntityRenderer(DroneMod.DRONE_OPERATOR_ENTITY.get(), DroneOperatorEntityRenderer::new);
    }

    private void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(DroneOperatorModel.LAYER_LOCATION, DroneOperatorModel::createBodyLayer);
    }

    private void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
                VanillaGuiLayers.CROSSHAIR,
                Identifier.fromNamespaceAndPath(DroneMod.MOD_ID, "flight_hud"),
                new DroneHudOverlay()
        );
    }
}
