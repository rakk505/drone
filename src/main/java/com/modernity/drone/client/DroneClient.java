package com.modernity.drone.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.modernity.drone.DroneMod;
import com.modernity.drone.client.config.FpvClientConfig;
import com.modernity.drone.client.gui.FpvSettingsScreen;
import com.modernity.drone.client.hud.DroneHudOverlay;
import com.modernity.drone.client.render.DroneEntityRenderer;
import com.modernity.drone.client.render.DroneOperatorEntityRenderer;
import com.modernity.drone.client.render.DroneOperatorModel;
import com.modernity.drone.client.video.FpvFrameProcessor;
import com.modernity.drone.network.GogglesChannelPayload;
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
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.lwjgl.glfw.GLFW;

@Mod(value = DroneMod.MOD_ID, dist = Dist.CLIENT)
public final class DroneClient {
    public static final KeyMapping.Category CONTROLS = new KeyMapping.Category(
            Identifier.fromNamespaceAndPath(DroneMod.MOD_ID, "controls")
    );

    /** V1.1.4 leaves FPV by disarming/removing goggles; this optional escape starts unbound. */
    public static final KeyMapping EXIT_VIEW = key("key.drone.toggle_view", GLFW.GLFW_KEY_UNKNOWN);
    public static final KeyMapping ARM = key("key.fpvdrone.arm_motors", GLFW.GLFW_KEY_N);
    public static final KeyMapping CYCLE_RESOLUTION = key("key.fpvdrone.cycle_resolution", GLFW.GLFW_KEY_F7);
    public static final KeyMapping THERMAL_TOGGLE = key("key.fpvdrone.thermal_toggle", GLFW.GLFW_KEY_END);
    public static final KeyMapping THERMAL_NUC = key("key.fpvdrone.thermal_nuc", GLFW.GLFW_KEY_U);
    public static final KeyMapping THERMAL_AGC_MODE = key("key.fpvdrone.thermal_agc_mode", GLFW.GLFW_KEY_G);
    public static final KeyMapping THERMAL_FOCUS_MODE = key("key.fpvdrone.thermal_focus_mode", GLFW.GLFW_KEY_HOME);
    public static final KeyMapping DROP_PAYLOAD = key("key.drone.drop_payload", GLFW.GLFW_KEY_B);
    public static final KeyMapping HOVER = key("key.drone.hover", GLFW.GLFW_KEY_H);
    public static final KeyMapping RETURN_HOME = key("key.drone.return_home", GLFW.GLFW_KEY_P);

    public DroneClient(IEventBus modEventBus, ModContainer container) {
        FpvClientConfig.initialize();
        DroneControlClient.setGogglesChannelRequestSender(direction ->
                ClientPacketDistributor.sendToServer(new GogglesChannelPayload(direction)));
        modEventBus.addListener(this::registerKeyMappings);
        modEventBus.addListener(this::registerLayerDefinitions);
        modEventBus.addListener(this::registerRenderers);
        modEventBus.addListener(this::registerRenderPipelines);
        modEventBus.addListener(this::registerGuiLayers);
        container.registerExtensionPoint(
                IConfigScreenFactory.class,
                (ignored, parent) -> new FpvSettingsScreen(parent)
        );
    }

    private static KeyMapping key(String translationKey, int glfwKey) {
        return new KeyMapping(
                translationKey,
                InputConstants.Type.KEYSYM,
                glfwKey,
                CONTROLS
        );
    }

    private void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.registerCategory(CONTROLS);
        event.register(EXIT_VIEW);
        event.register(ARM);
        event.register(CYCLE_RESOLUTION);
        event.register(THERMAL_TOGGLE);
        event.register(THERMAL_NUC);
        event.register(THERMAL_AGC_MODE);
        event.register(THERMAL_FOCUS_MODE);
        event.register(DROP_PAYLOAD);
        event.register(HOVER);
        event.register(RETURN_HOME);
    }

    private void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(DroneMod.DRONE_ENTITY.get(), DroneEntityRenderer::new);
        event.registerEntityRenderer(DroneMod.DROPPED_PAYLOAD_ENTITY.get(), PayloadEntityRenderer::new);
        event.registerEntityRenderer(DroneMod.DRONE_OPERATOR_ENTITY.get(), DroneOperatorEntityRenderer::new);
    }

    private void registerRenderPipelines(RegisterRenderPipelinesEvent event) {
        FpvFrameProcessor.registerPipelines(event);
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
